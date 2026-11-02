// 연결 폭주 테스트: VUS명이 동시에 연결을 열고 가벼운 조회를 한 번씩 함. 서버의 연결 대기열(server.tomcat.accept-count)을 넘으면 어떻게 되는지 봄
// 결과: 성공·연결 거절 건수, 연결에 걸린 시간. Linux에서는 함께 서버 컨테이너의 /proc/net/netstat ListenOverflows를 볼 것
// 실행 예: docker run --rm -i --network <서버와 같은 네트워크> -e BASE_URL=http://<서버 컨테이너>:8081 -e VUS=1000 grafana/k6:1.0.0 run - < perf/k6/connect.js
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';
const BASE_URL = __ENV.BASE_URL;
const refused = new Counter('conn_refused');
const otherFail = new Counter('other_fail');
const ok = new Counter('ok');
const connecting = new Trend('connecting_ms', true);
const total = new Trend('total_ms', true);
export const options = {
  scenarios: { storm: { executor: 'per-vu-iterations', vus: Number(__ENV.VUS || 1000), iterations: 1, maxDuration: '3m' } },
  summaryTrendStats: ['p(50)', 'p(95)', 'max'],
  thresholds: { conn_refused: ['count>=0'], other_fail: ['count>=0'], ok: ['count>=0'] },
};
export default function () {
  const r = http.get(`${BASE_URL}/api/v1/performances?size=1`, { timeout: '60s' });
  connecting.add(r.timings.connecting);
  total.add(r.timings.duration + r.timings.blocked + r.timings.connecting);
  if (r.status === 200) ok.add(1);
  else if ((r.error || '').includes('refused')) refused.add(1);
  else { otherFail.add(1); console.warn(`fail ${r.status} ${r.error}`); }
}
