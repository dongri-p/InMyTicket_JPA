-- 공연 목록의 정렬·필터 기준(예매 상태 그룹)을 컬럼으로 저장.
-- 이전에는 요청마다 공연 전체를 읽으며 행마다 회차 exists 서브쿼리로 그룹을 계산하고 정렬해서,
-- 공연 10만 건에서 첫 페이지 20건에도 150~280ms가 걸리고 인덱스를 탈 수 없었음.
-- booking_group: 0 예매중(시작 전 회차 있음) / 1 오픈예정(종료일이 오늘 이후거나 모름) / 2 공연종료
-- last_schedule_at: 가장 늦은 회차 시각. 이 시각이 지나면 예매중이 아니게 되므로 스케줄러가 그룹을 내려 줌
ALTER TABLE performance
    ADD COLUMN last_schedule_at DATETIME(6),
    ADD COLUMN booking_group INT NOT NULL DEFAULT 1;

UPDATE performance p
SET p.last_schedule_at = (SELECT MAX(s.start_time) FROM schedule s WHERE s.performance_id = p.performance_id);

-- NOW()는 DB 서버 시간대 기준이라 앱(KST)과 다를 수 있음. 어긋난 공연은 기동 후 스케줄러가 1분 안에 바로잡음
UPDATE performance
SET booking_group = CASE
    WHEN last_schedule_at > NOW() THEN 0
    WHEN end_date IS NULL OR end_date >= CURDATE() THEN 1
    ELSE 2
END;

CREATE INDEX idx_performance_booking_group ON performance (booking_group, performance_id);
