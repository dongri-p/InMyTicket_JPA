-- KOPIS 공연 지역(area, 예: 서울특별시). 공연 목록 지역 필터에 사용. 기존 공연은 기동 시 backfill로 채움.
ALTER TABLE performance ADD COLUMN area VARCHAR(50);
