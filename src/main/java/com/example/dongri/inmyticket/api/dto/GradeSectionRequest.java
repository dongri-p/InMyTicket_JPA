package com.example.dongri.inmyticket.api.dto;

import com.example.dongri.inmyticket.domain.GradeSection;
import com.example.dongri.inmyticket.domain.SeatGrade;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// 회차 등록 요청의 등급 구역 하나 (예: VIP 40석 150,000원)
@Data
public class GradeSectionRequest {

    @NotNull
    private SeatGrade grade;

    @Min(1)
    private int seatCount;

    // 0원(초대석)까지 허용, 상한은 잘못 입력한 자릿수를 막는 정도
    @Min(0)
    @Max(10000000)
    private int price;

    public GradeSection toSection() {
        return new GradeSection(grade, seatCount, price);
    }
}
