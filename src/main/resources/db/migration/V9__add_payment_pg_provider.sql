-- 결제를 처리한 PG(TOSS: 토스페이먼츠 테스트 결제, DEMO: 인증 없는 데모 결제). 취소 시 같은 PG로 환불하기 위해 저장.
-- 이 컬럼 이전의 결제는 모두 데모(시뮬레이션) 결제였으므로 DEMO로 채움.
ALTER TABLE payment ADD COLUMN pg_provider VARCHAR(20) NOT NULL DEFAULT 'DEMO';
