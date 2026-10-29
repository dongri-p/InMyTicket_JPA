-- 회차별 예매 오픈 시각. 비어 있으면(기존 회차 포함) 등록하자마자 예매할 수 있음
ALTER TABLE schedule ADD COLUMN booking_open_at DATETIME(6) NULL;
