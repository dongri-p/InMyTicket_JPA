# 성능 측정

대량 데이터를 넣고 k6로 응답 시간을 잰 스크립트와 결과입니다. 조회 흐름은 [docs/problem-solving.md 13장](../docs/problem-solving.md#13-데이터를-늘리자-공연-목록이-다른-api까지-느리게-만든-문제), 예매·결제는 [14장](../docs/problem-solving.md#14-결제가-몰리자-좌석-조회까지-느려진-문제-쓰기-부하), 트랜잭션 부가 명령과 풀 크기는 [15장](../docs/problem-solving.md#15-쿼리보다-트랜잭션-부가-명령이-더-많던-문제), 한 좌석 경합은 [16장](../docs/problem-solving.md#16-한-좌석에-몰린-요청이-락-앞에-한-줄로-서던-문제), 회차 잔여석 카운터는 [17장](../docs/problem-solving.md#17-서로-다른-좌석을-산-예매끼리도-회차-행-앞에-줄을-서던-문제), 예매 오픈과 대기열은 [19장](../docs/problem-solving.md#19-예매-오픈-순간-1000명이-몰릴-때-오픈-시각과-대기열)에 과정과 해석이 있습니다.

로컬 전용입니다. 시드 SQL은 운영 DB에 절대 실행하지 마세요.

## 다시 돌려 보기

1. MySQL 8.0 컨테이너와 빈 DB

   ```bash
   docker run -d --name inmyticket-mysql-test -p 3307:3306 -e MYSQL_ROOT_PASSWORD=testroot mysql:8.0
   docker exec inmyticket-mysql-test mysql -uroot -ptestroot -e "CREATE DATABASE inmyticket_perf DEFAULT CHARACTER SET utf8mb4"
   ```

2. 앱을 운영 프로필로 띄워 Flyway 스키마와 admin 계정을 만듭니다 (KOPIS 키는 아무 값이나 넣으면 동기화만 실패하고 뜹니다)

   ```bash
   ./gradlew bootJar
   SPRING_PROFILES_ACTIVE=prod \
   SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3307/inmyticket_perf?serverTimezone=Asia/Seoul&characterEncoding=UTF-8' \
   DB_USERNAME=root DB_PASSWORD=testroot \
   JWT_SECRET=perf-test-only-secret-key-at-least-32-characters \
   ADMIN_LOGIN_ID=admin ADMIN_PASSWORD='PerfAdmin1234!' \
   KOPIS_API_KEY=dummy TOSS_SECRET_KEY=dummy \
   JAVA_TOOL_OPTIONS='-Duser.timezone=Asia/Seoul' \
   java -jar build/libs/inmyticket-0.0.1-SNAPSHOT.jar --server.port=8081
   ```

3. 앱을 끄고 데이터를 넣은 뒤(약 9분) 다시 띄웁니다. 회원 `perf1` ~ `perf100000`의 비밀번호는 admin과 같습니다.

   ```bash
   docker exec -i inmyticket-mysql-test mysql -uroot -ptestroot --default-character-set=utf8mb4 inmyticket_perf < perf/seed-large-data.sql
   ```

4. k6 (Docker 이미지라 설치 없이 실행)

   ```bash
   docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e VUS=100 -e DURATION=60s \
     grafana/k6:1.0.0 run - < perf/k6/browse.js
   ```

5. 예매·결제 (`book.js`). `hot`은 실행할 때마다 좌석이 팔리므로 시작 전 회차 id(`HOT_SCHEDULE`)를 바꿔 가며 돌립니다.

   ```bash
   # 예매 오픈: 1000명이 회차 14의 빈 좌석 앞쪽 20개를 동시에 예매, 20명은 좌석 현황 새로고침
   docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e MODE=hot -e VUS=1000 -e SEATS=20      -e HOT_SCHEDULE=14 -e READERS=20 -e READ_DURATION=15s grafana/k6:1.0.0 run - < perf/k6/book.js
   # 회차 단위 경합만: 200명이 회차 14의 빈 좌석 200개를 한 명에 하나씩 (좌석은 안 겹침, 모두 성공)
   docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e MODE=hot -e VUS=200 -e SEATS=200 -e SPREAD=1 \
     -e HOT_SCHEDULE=14 -e READERS=20 -e READ_DURATION=15s grafana/k6:1.0.0 run - < perf/k6/book.js
   # 평소 예매: 300명이 빈 좌석 예매 -> 데모 결제(1.5초)
   docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e MODE=flow -e VUS=300 -e DURATION=60s      grafana/k6:1.0.0 run - < perf/k6/book.js
   ```

6. 예매 오픈 (`open.js`). 대기열을 재려면 Redis(`docker run -d --name inmyticket-redis -p 127.0.0.1:6379:6379 redis:7.4-alpine`)를 띄우고, 회차의 오픈 시각을 방금 전으로 둡니다. 성능 DB의 `now()`는 UTC라 `convert_tz`로 한국 시각을 넣습니다. 대기열 없이 재려면 `booking_open_at`을 NULL로 두고 `QUEUE=0`.

   ```bash
   docker exec inmyticket-mysql-test mysql -uroot -ptestroot inmyticket_perf -e "update schedule set booking_open_at = convert_tz(now(),'+00:00','+09:00') - interval 1 minute where schedule_id=230"
   docker run --rm -i -e BASE_URL=http://host.docker.internal:8081 -e VUS=1000 -e OPEN_SCHEDULE=230 -e QUEUE=1 grafana/k6:1.0.0 run - < perf/k6/open.js
   ```

## 결과 파일

[results/](results/)는 k6 요약 출력 원본입니다. 파일 이름 뒤 숫자가 동시 사용자 수이고, 시간을 정해 돌린 측정은 모두 60초입니다.

| 파일 | 시점 |
| --- | --- |
| `k6-baseline-100.txt` | 처음 |
| `k6-v10-100.txt` | 예매 상태 컬럼 + 인덱스 (V10) |
| `k6-v11-100.txt` | 인덱스에 장르 추가 (V11) |
| `k6-cache-10.txt`, `k6-cache-100.txt`, `k6-cache-300.txt` | 전체 건수 30초 캐시 후 |
| `k6-hot-200.txt`, `k6-hot-1000.txt` | 예매 오픈 (Java 17, 플랫폼 스레드) |
| `k6-flow-100.txt`, `k6-flow-300.txt` | 평소 예매·결제 (Java 17, 플랫폼 스레드) |
| `k6-flow-300-platform.txt` | 평소 예매·결제 (Java 21, 플랫폼 스레드) |
| `k6-flow-300-virtual.txt`, `k6-hot-1000-virtual.txt` | Java 21, 가상 스레드 |
| `k6-tx-before-100.txt`, `k6-tx-autocommit-100.txt`, `k6-tx-readonly-100.txt`, `k6-tx-final-100.txt` | 조회 100명: 트랜잭션 부가 명령 줄이기 전·후 (끝에 명령별 실행 횟수) |
| `k6-flow-300-pool10.txt`, `k6-flow-300-pool20.txt`, `k6-flow-300-pool40.txt` | 예매·결제 300명: 커넥션 풀 크기별 |
| `k6-hotseat-300-before-*.txt`, `k6-hotseat-300-precheck-*.txt` | 300명 → 좌석 1개: 팔린 좌석 먼저 거절 전·후 (`SEATS=1`) |
| `k6-hot-1000-precheck.txt`, `k6-hot-1000-reorder.txt` | 1000명 → 20석: 먼저 거절 후 / 회차 잔여석 감소를 맨 끝으로 옮긴 후 |
| `k6-spread-200-counter-*.txt`, `k6-spread-200-seatcount-*.txt` | 200명 → 같은 회차 다른 좌석 200개 (`SPREAD=1`): 회차 잔여석 카운터 / 좌석 상태에서 세기 (끝에 DB 문장별 시간·중복 판매 확인) |
| `k6-hot-1000-counter-*.txt`, `k6-hot-1000-seatcount-*.txt` | 1000명 → 20석: 같은 비교 |
| `k6-browse-100-counter*.txt`, `k6-browse-100-seatcount*.txt` | 조회 100명: 같은 비교 (`seatcount`, `-2`는 회차 조회·count가 트랜잭션 두 번이던 때, `-3`, `-4`는 합친 뒤) |
| `k6-open-1000-noqueue-*.txt`, `k6-open-1000-queue20.txt` | 예매 오픈 1000명 → 367석: 대기열 없음 / 대기열 20명/초 (첫 측정, 16명 미입장) |
| `k6-open-1000-queue20-race.txt`, `k6-open-1000-queue20-atomic.txt` | 같은 측정: 입장 누락 수정 전(`NOT_IN_QUEUE` 2명) / 수정 후(1000명 모두 입장) |
