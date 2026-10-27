package com.example.dongri.inmyticket.domain;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

// 공연 목록 '더 보기'용 커서: 직전 페이지 마지막 공연의 정렬 키 (예매 상태 그룹, id).
// 목록은 예매 상태 그룹 -> id 순이라 이 둘이 있으면 "그다음부터"를 OFFSET 없이 찾을 수 있음.
// 클라이언트가 내부 구조에 기대지 않도록 Base64 문자열로 주고받음
public record PerformanceCursor(int bookingGroup, long id) {

    public static PerformanceCursor after(BookingStatus bookingStatus, long id) {
        return new PerformanceCursor(bookingStatus.getCode(), id);
    }

    public String encode() {
        String raw = bookingGroup + ":" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    // 잘못된 커서는 요청 내용 오류(400)
    public static PerformanceCursor decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split(":");
            int bookingGroup = Integer.parseInt(parts[0]);
            if (parts.length != 2 || bookingGroup < 0 || bookingGroup > 2) {
                throw new IllegalArgumentException();
            }
            return new PerformanceCursor(bookingGroup, Long.parseLong(parts[1]));
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("커서 형식이 올바르지 않습니다.");
        }
    }
}
