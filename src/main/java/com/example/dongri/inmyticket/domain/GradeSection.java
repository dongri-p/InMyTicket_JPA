package com.example.dongri.inmyticket.domain;

import java.util.ArrayList;
import java.util.List;

// 회차 좌석 배치의 한 구역: 이 등급 좌석을 몇 석, 얼마에. 회차 등록 시 목록 순서대로 1번 좌석부터 이어서 배치
public record GradeSection(SeatGrade grade, int seatCount, int price) {

    // 등급을 따로 정하지 않고 회차를 등록할 때 쓰는 기본 배치 (앞에서부터 VIP 20%, R 30%, S 30%, 나머지 A)
    // 좌석 수가 적어 0석이 되는 등급은 빼므로, 좌석 1석이면 A석 하나만 생김
    public static List<GradeSection> defaults(int totalSeatCount) {
        int vip = totalSeatCount * 2 / 10;
        int r = totalSeatCount * 3 / 10;
        int s = totalSeatCount * 3 / 10;
        int a = totalSeatCount - vip - r - s;

        List<GradeSection> sections = new ArrayList<>();
        addIfAny(sections, SeatGrade.VIP, vip, 150000);
        addIfAny(sections, SeatGrade.R, r, 120000);
        addIfAny(sections, SeatGrade.S, s, 90000);
        addIfAny(sections, SeatGrade.A, a, 60000);
        return sections;
    }

    private static void addIfAny(List<GradeSection> sections, SeatGrade grade, int count, int price) {
        if (count > 0) {
            sections.add(new GradeSection(grade, count, price));
        }
    }
}
