-- 결제 진행(PROCESSING) 전환 시각. PG 통신 중 서버가 죽어 PROCESSING에 고착된 예약을
-- ReservationExpirationScheduler가 찾아 PENDING으로 복구하는 기준으로 사용.
ALTER TABLE reservation ADD COLUMN processing_started_at DATETIME(6);
