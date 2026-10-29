// 쓰기 흐름 부하 테스트. MODE로 두 가지를 고름
//  - hot  : 예매 오픈 순간. VUS명이 한 회차의 앞쪽 SEATS개 좌석을 동시에 노림 (좌석 락 경합). READERS명은 그동안 같은 회차 좌석 현황을 계속 새로고침
//  - flow : 평소 예매. 각자 다른 회차에서 빈 좌석 하나 골라 예매 -> 데모 결제(PG 1.5초)
// perf/seed-large-data.sql로 넣은 데이터 기준. hot은 실행할 때마다 좌석이 팔리므로 HOT_SCHEDULE을 바꿔 가며 돌림
// 실행 예: docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e MODE=hot -e VUS=200 grafana/k6:1.0.0 run - < perf/k6/book.js
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const MODE = __ENV.MODE || 'flow';
const VUS = Number(__ENV.VUS || 100);
const READERS = Number(__ENV.READERS || 0);
const PASSWORD = __ENV.PASSWORD || 'PerfAdmin1234!';
const HOT_SCHEDULE = Number(__ENV.HOT_SCHEDULE || 0);
// SPREAD=1이면 hot에서 사람마다 서로 다른 좌석을 고름 (좌석은 안 겹치고 같은 회차만 공유 - 회차 단위 경합만 보려고)
const SPREAD = __ENV.SPREAD === '1';
const SEATS = Number(__ENV.SEATS || 20);
// 다른 테스트에서 쓴 회원과 겹치지 않게 시작 번호를 바꿀 수 있게 함 (perfN)
const MEMBER_OFFSET = Number(__ENV.MEMBER_OFFSET || 0);

const reserved = new Counter('reserve_success');
const conflicted = new Counter('reserve_conflict');
const paid = new Counter('pay_success');

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const TRENDS = ['avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'];

const scenarios = MODE === 'hot'
  ? {
      // 모든 VU가 동시에 딱 한 번 예매 요청
      hot: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '2m', exec: 'hot' },
    }
  : {
      flow: { executor: 'constant-vus', vus: VUS, duration: __ENV.DURATION || '60s', exec: 'flow' },
    };
if (READERS > 0) {
  scenarios.readers = { executor: 'constant-vus', vus: READERS, duration: __ENV.READ_DURATION || '20s', exec: 'read' };
}

export const options = {
  scenarios,
  setupTimeout: '5m',
  summaryTrendStats: TRENDS,
  thresholds: {
    // 태그별 지표를 요약에 따로 보이게 하려는 threshold (목표값 아님)
    'http_req_duration{api:reserve}': ['p(95)<100000'],
    'http_req_duration{api:pay}': ['p(95)<100000'],
    'http_req_duration{api:seats}': ['p(95)<100000'],
  },
};

function login(i) {
  const res = http.post(`${BASE_URL}/api/v1/members/login`,
    JSON.stringify({ loginId: `perf${i}`, password: PASSWORD }), { headers: JSON_HEADERS });
  check(res, { 'login 200': (r) => r.status === 200 });
  return res.json('accessToken');
}

function auth(token, api) {
  return { headers: Object.assign({ Authorization: `Bearer ${token}` }, JSON_HEADERS), tags: { api } };
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// 하루 이상 남은 회차만 (공연이 시작된 회차는 예매가 막힘)
function isFuture(scheduleId) {
  const r = http.get(`${BASE_URL}/api/v1/schedules/${scheduleId}`, { tags: { api: 'setup' } });
  return r.status === 200 && new Date(`${r.json('startTime')}+09:00`).getTime() > Date.now() + 86400000;
}

export function setup() {
  const tokens = [];
  for (let i = 1; i <= VUS; i++) tokens.push(login(MEMBER_OFFSET + i));

  if (MODE === 'hot') {
    if (!HOT_SCHEDULE || !isFuture(HOT_SCHEDULE)) throw new Error('HOT_SCHEDULE에 시작 전 회차 id를 넣어 주세요');
    // 앞쪽이 이미 다 팔린 회차도 있어서 빈 좌석이 SEATS개 모일 때까지 다음 페이지도 봄
    const targets = [];
    for (let page = 0; targets.length < SEATS; page++) {
      const seats = http.get(`${BASE_URL}/api/v1/schedules/${HOT_SCHEDULE}/seats?page=${page}&size=100`).json('data');
      if (seats.length === 0) break;
      seats.filter((s) => s.status === 'AVAILABLE').forEach((s) => targets.push(s.seatId));
    }
    if (targets.length === 0) throw new Error(`회차 ${HOT_SCHEDULE}에 빈 좌석이 없습니다`);
    return { tokens, targets: targets.slice(0, SEATS) };
  }

  const schedules = [];
  while (schedules.length < 300) {
    const id = 1 + Math.floor(Math.random() * 10000);
    if (isFuture(id)) schedules.push(id);
  }
  return { tokens, schedules };
}

function reserve(token, seatIds) {
  const r = http.post(`${BASE_URL}/api/v1/reservations`, JSON.stringify({ seatIds }), auth(token, 'reserve'));
  if (r.status === 201) {
    reserved.add(1);
    return r.json('id');
  }
  // 다른 사람이 먼저 잡은 좌석이면 409 (정상적인 실패)
  if (r.status === 409) {
    conflicted.add(1);
    return null;
  }
  check(r, { 'reserve 201 or 409': () => false });
  console.warn(`reserve ${r.status} ${r.error || r.body}`);
  return null;
}

// __VU는 시나리오를 가리지 않고 매기는 번호라 READERS가 있으면 VUS를 넘어감 -> 시나리오 안에서의 반복 번호(0..VUS-1)로 회원을 고름
export function hot(data) {
  const i = exec.scenario.iterationInTest;
  const token = data.tokens[i];
  reserve(token, [SPREAD ? data.targets[i % data.targets.length] : pick(data.targets)]);
}

export function flow(data) {
  const token = data.tokens[exec.vu.idInTest % data.tokens.length];
  const scheduleId = pick(data.schedules);

  const seats = http.get(`${BASE_URL}/api/v1/schedules/${scheduleId}/seats?page=0&size=100`, { tags: { api: 'seats' } });
  check(seats, { 'seats 200': (x) => x.status === 200 });
  const available = (seats.json('data') || []).filter((s) => s.status === 'AVAILABLE');
  if (available.length === 0) return;
  const seat = pick(available);

  const reservationId = reserve(token, [seat.seatId]);
  if (!reservationId) return;

  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const pay = http.post(`${BASE_URL}/api/v1/payments`, JSON.stringify({
    reservationId,
    provider: 'DEMO',
    paymentKey: `perf-${suffix}`,
    orderId: `imt-${reservationId}-${suffix}`,
    amount: seat.price,
  }), auth(token, 'pay'));
  if (check(pay, { 'pay 201': (x) => x.status === 201 })) paid.add(1);
}

// 좌석 현황 새로고침 (hot이면 경합 중인 그 회차, flow면 아무 회차)
export function read(data) {
  const scheduleId = MODE === 'hot' ? HOT_SCHEDULE : pick(data.schedules);
  const r = http.get(`${BASE_URL}/api/v1/schedules/${scheduleId}/seats?page=0&size=100`, { tags: { api: 'seats' } });
  check(r, { 'seats 200': (x) => x.status === 200 });
}
