// 예매 오픈 순간 부하 테스트: VUS명이 한 회차(OPEN_SCHEDULE)에 동시에 들어와 좌석 화면을 열고 빈 좌석 하나를 예매 (겹치면 한 번 더)
//  - QUEUE=1 : 대기열 운영 중인 회차 (회차의 booking_open_at을 방금 전으로 둔 상태). 줄 서기 -> 1초마다 순번 확인 -> 입장하면 좌석 화면
//  - QUEUE=0 : 대기열 없이 바로 좌석 화면 (booking_open_at = NULL)
// 결과: 좌석 조회·예매 응답 시간, 사람마다 "들어와서 결과(성공/매진)를 받기까지" 걸린 시간, 대기 시간
// 실행 예: docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e VUS=1000 -e OPEN_SCHEDULE=30 -e QUEUE=1 grafana/k6:1.0.0 run - < perf/k6/open.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const VUS = Number(__ENV.VUS || 1000);
const OPEN_SCHEDULE = Number(__ENV.OPEN_SCHEDULE || 0);
const QUEUE = __ENV.QUEUE === '1';
const PASSWORD = __ENV.PASSWORD || 'PerfAdmin1234!';
const MEMBER_OFFSET = Number(__ENV.MEMBER_OFFSET || 0);
const POLL_SECONDS = Number(__ENV.POLL_SECONDS || 1);

const reserved = new Counter('reserve_success');
const soldOut = new Counter('sold_out');
const failed = new Counter('reserve_failed');
const timeToResult = new Trend('time_to_result', true);
const queueWait = new Trend('queue_wait', true);

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
  scenarios: {
    open: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '10m' },
  },
  setupTimeout: '10m',
  summaryTrendStats: ['avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    // 태그별 지표를 요약에 따로 보이게 하려는 threshold (목표값 아님)
    'http_req_duration{api:seats}': ['p(95)<100000'],
    'http_req_duration{api:reserve}': ['p(95)<100000'],
    'http_req_duration{api:queue}': ['p(95)<100000'],
  },
};

export function setup() {
  if (!OPEN_SCHEDULE) throw new Error('OPEN_SCHEDULE에 회차 id를 넣어 주세요');
  const tokens = [];
  for (let i = 1; i <= VUS; i++) {
    const res = http.post(`${BASE_URL}/api/v1/members/login`,
      JSON.stringify({ loginId: `perf${MEMBER_OFFSET + i}`, password: PASSWORD }), { headers: JSON_HEADERS });
    tokens.push(res.json('accessToken'));
  }
  return { tokens };
}

function auth(token, api) {
  return { headers: Object.assign({ Authorization: `Bearer ${token}` }, JSON_HEADERS), tags: { api } };
}

// 좌석 화면처럼 전체 좌석을 페이지마다 받음
function availableSeats(token) {
  const free = [];
  for (let page = 0; page < 10; page++) {
    const r = http.get(`${BASE_URL}/api/v1/schedules/${OPEN_SCHEDULE}/seats?page=${page}&size=100`, auth(token, 'seats'));
    if (r.status !== 200) return free;
    const data = r.json('data');
    data.filter((s) => s.status === 'AVAILABLE').forEach((s) => free.push(s.seatId));
    if (data.length < 100) break;
  }
  return free;
}

function waitInQueue(token) {
  const started = Date.now();
  let state = http.post(`${BASE_URL}/api/v1/schedules/${OPEN_SCHEDULE}/queue`, null, auth(token, 'queue')).json('state');
  while (state === 'WAITING') {
    sleep(POLL_SECONDS);
    state = http.get(`${BASE_URL}/api/v1/schedules/${OPEN_SCHEDULE}/queue/me`, auth(token, 'queue')).json('state');
  }
  queueWait.add(Date.now() - started);
  check(state, { 'admitted': (s) => s === 'ADMITTED' });
}

export default function (data) {
  const started = Date.now();
  const token = data.tokens[exec.vu.idInTest - 1];
  if (QUEUE) waitInQueue(token);

  for (let attempt = 0; attempt < 2; attempt++) {
    const free = availableSeats(token);
    if (free.length === 0) {
      soldOut.add(1);
      break;
    }
    const seatId = free[Math.floor(Math.random() * free.length)];
    const r = http.post(`${BASE_URL}/api/v1/reservations`, JSON.stringify({ seatIds: [seatId] }), auth(token, 'reserve'));
    if (r.status === 201) {
      reserved.add(1);
      break;
    }
    if (r.status !== 409) {
      failed.add(1);
      console.warn(`reserve ${r.status} ${r.error || r.body}`);
      break;
    }
    // 409: 고르는 사이 다른 사람이 먼저 산 좌석 -> 좌석을 새로 받아 한 번 더
  }
  timeToResult.add(Date.now() - started);
}
