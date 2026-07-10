-- KOPIS 동기화 시 같은 mt20id가 배치 내 중복이거나 동기화 API가 짧은 간격으로
-- 두 번 호출되어도 같은 apiId로 공연이 중복 저장되지 않도록 DB 레벨에서 방지.
ALTER TABLE performance ADD CONSTRAINT uk_performance_api_id UNIQUE (api_id);
