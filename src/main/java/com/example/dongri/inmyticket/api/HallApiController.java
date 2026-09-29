package com.example.dongri.inmyticket.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreateHallRequest;
import com.example.dongri.inmyticket.api.dto.CreateResourceResponse;
import com.example.dongri.inmyticket.service.HallService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HallApiController {

    private final HallService hallService;

    // 관리자 기능. 공연장 등록 API (회차 등록 전에 공연장이 먼저 있어야 함)
    @PostMapping("/api/v1/halls")
    public CreateResourceResponse saveHall(@Validated @RequestBody CreateHallRequest request) {
        Long id = hallService.saveHall(request.getName(), request.getAddress(), request.getTotalSeats());
        return new CreateResourceResponse(id, "공연장 등록이 완료되었습니다.");
    }
}
