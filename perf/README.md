# 성능 측정

대량 데이터를 넣고 k6로 조회 흐름의 응답 시간을 잰 스크립트와 결과입니다. 과정과 해석은 [docs/problem-solving.md 13장](../docs/problem-solving.md#13-데이터를-늘리자-공연-목록이-다른-api까지-느리게-만든-문제)에 있습니다.

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

## 결과 파일

[results/](results/)는 k6 요약 출력 원본입니다. 모두 100명·60초이고, 파일 이름 뒤 숫자가 동시 사용자 수입니다.

| 파일 | 시점 |
| --- | --- |
| `k6-baseline-100.txt` | 처음 |
| `k6-v10-100.txt` | 예매 상태 컬럼 + 인덱스 (V10) |
| `k6-v11-100.txt` | 인덱스에 장르 추가 (V11) |
| `k6-cache-10.txt`, `k6-cache-100.txt`, `k6-cache-300.txt` | 전체 건수 30초 캐시 후 |
