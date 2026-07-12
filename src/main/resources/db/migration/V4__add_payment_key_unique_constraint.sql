-- 같은 PG 결제 키(payment_key)로 서로 다른 예약을 각각 결제 승인할 수 있었던 문제 방지.
-- (재생 공격/키 재사용 방지 - 실제 PG 연동 시 결제 키는 거래마다 고유해야 함)
ALTER TABLE payment ADD CONSTRAINT uk_payment_payment_key UNIQUE (payment_key);
