package com.example.dongri.inmyticket.api.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateReservationRequest {

    // 같은 회차 좌석 여러 개를 한 예약으로 묶어 예매 (한 번에 최대 4석)
    @NotEmpty(message = "좌석을 선택해주세요.")
    @Size(max = 4, message = "한 번에 최대 4석까지 예매할 수 있습니다.")
    private List<@NotNull Long> seatIds;
}
