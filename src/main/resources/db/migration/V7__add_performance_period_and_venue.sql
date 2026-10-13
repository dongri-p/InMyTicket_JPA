-- KOPIS 공연 기간(prfpdfrom~prfpdto)과 공연장명(fcltynm). 공연 기간 밖 회차 등록을 막고 상세 화면에 보여주기 위해 추가.
ALTER TABLE performance ADD COLUMN start_date DATE;
ALTER TABLE performance ADD COLUMN end_date DATE;
ALTER TABLE performance ADD COLUMN venue_name VARCHAR(255);
