-- 취소 수수료 정책(CancelFeePolicy): 결제를 취소할 때 뗀 수수료와 실제로 돌려준 금액.
-- 이전에 취소된 결제는 모두 전액 환불이었으므로 수수료 0, 환불액 = 결제 금액으로 채움
ALTER TABLE payment ADD COLUMN cancel_fee INT NULL, ADD COLUMN refunded_amount INT NULL;
UPDATE payment SET cancel_fee = 0, refunded_amount = amount WHERE status = 'CANCELED';
