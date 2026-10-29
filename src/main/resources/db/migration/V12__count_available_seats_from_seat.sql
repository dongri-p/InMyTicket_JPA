-- 회차 잔여석을 schedule.available_seat_count 카운터 대신 좌석 상태에서 바로 셈.
-- 카운터가 있으면 예매·취소마다 회차 행을 같이 잠가서, 1000명이 한 회차 20석을 노리면 서로 다른 좌석을 산 예매끼리도
-- 그 행 하나 앞에 줄을 섰음 (UPDATE schedule 락 대기 평균 88~120ms).
-- 회차 목록의 잔여석 count는 (schedule_id, status) 인덱스만 읽고 끝나도록 인덱스 추가
CREATE INDEX idx_seat_schedule_status ON seat (schedule_id, status);
ALTER TABLE schedule DROP COLUMN available_seat_count;
