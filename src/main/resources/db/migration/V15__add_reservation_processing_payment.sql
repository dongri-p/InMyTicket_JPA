-- 결제 승인 요청 중인 결제 키와 PG. 승인 결과를 모른 채 끝났을 때(타임아웃, 서버 재시작) PG에 승인 여부를 묻는 데 씀
ALTER TABLE reservation ADD COLUMN processing_payment_key VARCHAR(255) NULL;
ALTER TABLE reservation ADD COLUMN processing_pg_provider VARCHAR(20) NULL;
