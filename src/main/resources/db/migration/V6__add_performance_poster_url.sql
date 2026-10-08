-- KOPIS 공연 포스터 이미지 URL. 공연 목록을 포스터 카드로 보여주기 위해 추가.
ALTER TABLE performance ADD COLUMN poster_url VARCHAR(255);
