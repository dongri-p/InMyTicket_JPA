// 조회 흐름 부하 테스트: 공연 목록 -> 상세 -> 회차 -> 좌석 -> 내 예매
// perf/seed-large-data.sql로 넣은 데이터 기준 (회차가 있는 공연 id = k * 7919 % 100000 + 1, k = 0..2499)
// 실행 예: docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e VUS=100 grafana/k6:1.0.0 run - < perf/k6/browse.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const VUS = Number(__ENV.VUS || 100);
const PASSWORD = __ENV.PASSWORD || 'PerfAdmin1234!';

export const options = {
  scenarios: {
    browse: { executor: 'constant-vus', vus: VUS, duration: __ENV.DURATION || '60s' },
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    // 엔드포인트별 지표를 요약에 따로 보이게 하려고 태그별 threshold를 걸어 둠 (목표값은 측정 후 조정)
    'http_req_duration{api:list}': ['p(95)<100000'],
    'http_req_duration{api:list_filter}': ['p(95)<100000'],
    'http_req_duration{api:detail}': ['p(95)<100000'],
    'http_req_duration{api:schedules}': ['p(95)<100000'],
    'http_req_duration{api:seats}': ['p(95)<100000'],
    'http_req_duration{api:my_reservations}': ['p(95)<100000'],
    http_req_failed: ['rate<0.01'],
  },
};

const GENRES = ['CONCERT', 'MUSICAL_PLAY', 'CLASSIC', 'DANCE', 'ETC'];
const STATUSES = ['ON_SALE', 'UPCOMING', 'ENDED'];

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

// VU마다 다른 회원으로 로그인 (perf1 ~ perfN)
export function setup() {
  const tokens = [];
  for (let i = 1; i <= VUS; i++) {
    const res = http.post(`${BASE_URL}/api/v1/members/login`,
      JSON.stringify({ loginId: `perf${i}`, password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json' } });
    check(res, { 'login 200': (r) => r.status === 200 });
    tokens.push(res.json('accessToken'));
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const auth = { headers: { Authorization: `Bearer ${token}` } };

  // 1) 공연 목록 첫 페이지 (절반은 필터 없이, 절반은 장르 + 예매 상태 필터)
  if (Math.random() < 0.5) {
    const r = http.get(`${BASE_URL}/api/v1/performances?size=20`, { tags: { api: 'list' } });
    check(r, { 'list 200': (x) => x.status === 200 });
  } else {
    const r = http.get(`${BASE_URL}/api/v1/performances?size=20&genre=${pick(GENRES)}&status=${pick(STATUSES)}`,
      { tags: { api: 'list_filter' } });
    check(r, { 'list_filter 200': (x) => x.status === 200 });
  }

  // 2) 회차가 있는 공연 하나를 골라 상세 -> 회차 -> 좌석
  const k = Math.floor(Math.random() * 2500);
  const performanceId = (k * 7919) % 100000 + 1;
  const detail = http.get(`${BASE_URL}/api/v1/performances/${performanceId}`, { tags: { api: 'detail' } });
  check(detail, { 'detail 200': (x) => x.status === 200 });

  const schedules = http.get(`${BASE_URL}/api/v1/performances/${performanceId}/schedules`, { tags: { api: 'schedules' } });
  check(schedules, { 'schedules 200': (x) => x.status === 200 });
  const list = schedules.json('data') || [];
  if (list.length > 0) {
    const scheduleId = pick(list).scheduleId;
    const seats = http.get(`${BASE_URL}/api/v1/schedules/${scheduleId}/seats?page=0&size=500`, { tags: { api: 'seats' } });
    check(seats, { 'seats 200': (x) => x.status === 200 });
  }

  // 3) 내 예매 목록
  const my = http.get(`${BASE_URL}/api/v1/reservations/me`, Object.assign({ tags: { api: 'my_reservations' } }, auth));
  check(my, { 'my 200': (x) => x.status === 200 });
}
