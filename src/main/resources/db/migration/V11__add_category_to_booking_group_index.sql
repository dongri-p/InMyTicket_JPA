-- 목록 첫 페이지마다 나가는 전체 건수 count에 장르 필터가 붙으면, (booking_group, performance_id) 인덱스로 그룹 전체를 찾은 뒤
-- 행마다 테이블에서 category를 확인해야 해서 공연 10만 건 기준 25~36ms가 걸렸음.
-- 인덱스 끝에 category를 붙이면 정렬 순서(booking_group, performance_id)는 그대로 쓰면서 count는 인덱스만 읽고 끝남 (11~17ms).
-- (category, booking_group) 인덱스를 따로 두는 방법은 옵티마이저가 고르지 않아서(FORCE INDEX 해야 탐) 기존 인덱스를 넓힘
DROP INDEX idx_performance_booking_group ON performance;
CREATE INDEX idx_performance_booking_group ON performance (booking_group, performance_id, category);
