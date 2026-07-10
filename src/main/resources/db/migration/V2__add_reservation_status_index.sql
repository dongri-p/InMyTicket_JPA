-- ReservationExpirationScheduler가 주기적으로 호출하는
-- findByStatusAndReservedAtBefore(status, cutoff) 쿼리를 위한 인덱스.
-- 예약 건수가 늘어날수록 이 쿼리가 매 실행마다 풀스캔될 위험을 방지.
CREATE INDEX idx_reservation_status_reserved_at ON reservation (status, reserved_at);
