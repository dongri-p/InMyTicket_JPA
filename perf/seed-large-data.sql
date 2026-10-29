-- 성능 측정용 대량 데이터 (로컬 MySQL 전용 — 운영 DB에 절대 실행하지 말 것)
-- 전제: 앱을 prod 프로필로 한 번 띄워 Flyway 스키마 + admin 회원(member_id 1)만 있는 빈 DB
-- 규모: 회원 10만 / 공연장 40 / 공연 10만 / 회차 1만 / 좌석 약 350만(회차당 200~500석, VIP/R/S/A) / 예매·티켓 약 117만 / 결제 약 93만
-- 모든 회원 비밀번호는 admin과 같음 (admin의 BCrypt 해시를 그대로 복사)
-- 실행: docker exec -i <컨테이너> mysql -uroot -p<비번> <DB> < perf/seed-large-data.sql

SET SESSION cte_max_recursion_depth = 1000000;
SET foreign_key_checks = 0;
SET unique_checks = 0;

-- 1 ~ 100만 숫자 테이블 (행 생성용)
DROP TABLE IF EXISTS perf_seq;
CREATE TABLE perf_seq (n INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO perf_seq
WITH RECURSIVE s(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 1000000)
SELECT n FROM s;

-- 회원 10만 (member_id 2 ~ 100001)
SET @pw = (SELECT password FROM member WHERE member_id = 1);
INSERT INTO member (member_id, login_id, password, name, email, role)
SELECT n + 1, CONCAT('perf', n), @pw, CONCAT('회원', n), CONCAT('perf', n, '@perf.test'), 'USER'
FROM perf_seq WHERE n <= 100000;

-- 공연장 40개: 200 / 300 / 400 / 500석
INSERT INTO hall (hall_id, name, address, total_seats)
SELECT n, CONCAT('공연장 ', n), CONCAT('주소 ', n), 200 + (n % 4) * 100
FROM perf_seq WHERE n <= 40;

-- 공연 10만: 2025-01-01 ~ 2027-12-31 사이 시작, 기간 0~59일 (종료·진행·예정이 섞이도록)
-- 포스터·기간·지역을 모두 채움 (비어 있으면 기동 시 backfill이 KOPIS를 호출함)
INSERT INTO performance (performance_id, api_id, title, artist, description, category, status,
                         poster_url, start_date, end_date, venue_name, area, last_updated_at)
SELECT n,
       CONCAT('PERF', LPAD(n, 7, '0')),
       CONCAT(ELT(1 + n % 10, '레미제라블', '오페라의 유령', '햄릿', '지킬앤하이드', '라이온킹',
                  '캣츠', '시카고', '맘마미아', '위키드', '노트르담 드 파리'), ' ', n),
       CONCAT('출연진 ', n % 500),
       '성능 측정용 공연',
       ELT(1 + (n * 7) % 9, '대중음악', '뮤지컬', '연극', '서양음악(클래식)', '한국음악(국악)',
           '무용(서양/한국무용)', '대중무용', '서커스/마술', '복합'),
       '공연예정',
       CONCAT('https://example.com/poster/', n, '.jpg'),
       DATE_ADD('2025-01-01', INTERVAL (n * 37) % 1095 DAY),
       DATE_ADD(DATE_ADD('2025-01-01', INTERVAL (n * 37) % 1095 DAY), INTERVAL n % 60 DAY),
       CONCAT('공연장 ', 1 + n % 40),
       ELT(1 + (n * 13) % 17, '서울특별시', '경기도', '인천광역시', '대전광역시', '세종특별자치시',
           '충청남도', '충청북도', '강원특별자치도', '부산광역시', '대구광역시', '울산광역시',
           '경상북도', '경상남도', '광주광역시', '전라남도', '전북특별자치도', '제주특별자치도'),
       NOW()
FROM perf_seq WHERE n <= 100000;

-- 회차 1만: 공연 2,500개에 4회차씩, 시작 시각은 지금 기준 -30일 ~ +90일
INSERT INTO schedule (schedule_id, performance_id, hall_id, start_time, total_seat_count)
SELECT s.n,
       ((s.n - 1) DIV 4) * 7919 % 100000 + 1,
       h.hall_id,
       DATE_ADD(DATE_SUB(DATE_FORMAT(NOW(), '%Y-%m-%d %H:00:00'), INTERVAL 30 DAY), INTERVAL (s.n * 131) % 2880 HOUR),
       h.total_seats
FROM perf_seq s JOIN hall h ON h.hall_id = 1 + s.n % 40
WHERE s.n <= 10000;

-- 좌석 약 350만: 앞 10% VIP, 다음 20% R, 다음 30% S, 나머지 A
INSERT INTO seat (schedule_id, grade, seat_number, price, status, version)
SELECT sch.schedule_id,
       CASE WHEN q.n <= sch.total_seat_count * 0.1 THEN 'VIP'
            WHEN q.n <= sch.total_seat_count * 0.3 THEN 'R'
            WHEN q.n <= sch.total_seat_count * 0.6 THEN 'S'
            ELSE 'A' END,
       q.n,
       CASE WHEN q.n <= sch.total_seat_count * 0.1 THEN 170000
            WHEN q.n <= sch.total_seat_count * 0.3 THEN 150000
            WHEN q.n <= sch.total_seat_count * 0.6 THEN 120000
            ELSE 90000 END,
       'AVAILABLE',
       0
FROM schedule sch JOIN perf_seq q ON q.n <= sch.total_seat_count
ORDER BY sch.schedule_id, q.n;

-- 예매: 좌석 3개 중 1개꼴(약 117만), 80% 결제 완료 / 20% 취소, 지난 1년 사이 예매
-- reservation_id = seat_id DIV 3 이라 좌석과 1:1, 회원당 평균 약 12건
INSERT INTO reservation (reservation_id, member_id, total_price, status, reserved_at)
SELECT seat_id DIV 3,
       2 + (seat_id DIV 3) % 100000,
       price,
       IF((seat_id DIV 3) % 5 = 0, 'CANCELLED', 'CONFIRMED'),
       DATE_SUB(NOW(), INTERVAL (seat_id DIV 3) % 525600 MINUTE)
FROM seat WHERE seat_id % 3 = 0;

INSERT INTO ticket (ticket_id, reservation_id, seat_id)
SELECT seat_id DIV 3, seat_id DIV 3, seat_id
FROM seat WHERE seat_id % 3 = 0;

INSERT INTO payment (reservation_id, amount, status, payment_key, paid_at, pg_provider)
SELECT reservation_id, total_price, 'COMPLETED', CONCAT('perf-', reservation_id),
       DATE_ADD(reserved_at, INTERVAL 1 MINUTE), 'DEMO'
FROM reservation WHERE status = 'CONFIRMED' AND member_id > 1;

-- 결제 완료된 예매의 좌석은 RESERVED (회차 잔여석은 좌석 상태에서 세므로 따로 맞출 값 없음)
UPDATE seat s JOIN ticket t ON t.seat_id = s.seat_id
              JOIN reservation r ON r.reservation_id = t.reservation_id
SET s.status = 'RESERVED'
WHERE r.status = 'CONFIRMED';

DROP TABLE perf_seq;
SET foreign_key_checks = 1;
SET unique_checks = 1;
ANALYZE TABLE member, hall, performance, schedule, seat, reservation, ticket, payment;

SELECT 'member' t, COUNT(*) c FROM member UNION ALL
SELECT 'performance', COUNT(*) FROM performance UNION ALL
SELECT 'schedule', COUNT(*) FROM schedule UNION ALL
SELECT 'seat', COUNT(*) FROM seat UNION ALL
SELECT 'reservation', COUNT(*) FROM reservation UNION ALL
SELECT 'ticket', COUNT(*) FROM ticket UNION ALL
SELECT 'payment', COUNT(*) FROM payment;
