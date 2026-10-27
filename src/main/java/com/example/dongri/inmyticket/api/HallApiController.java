package com.example.dongri.inmyticket.api;

import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreateHallRequest;
import com.example.dongri.inmyticket.api.dto.CreateResourceResponse;
import com.example.dongri.inmyticket.api.dto.HallListDto;
import com.example.dongri.inmyticket.api.dto.ListResult;
import com.example.dongri.inmyticket.service.HallService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class HallApiController {

    private final HallService hallService;

    // 관리자 기능. 공연장 등록 API (회차 등록 전에 공연장이 먼저 있어야 함)
    @PostMapping("/api/v1/halls")
    public ResponseEntity<CreateResourceResponse> saveHall(@Validated @RequestBody CreateHallRequest request) {
        Long id = hallService.saveHall(request.getName(), request.getAddress(), request.getTotalSeats());
        return CreateResourceResponse.created(id);
    }

    // 관리자 기능. 공연장 단건 조회 (공연장 등록 응답의 Location이 가리키는 곳)
    @GetMapping("/api/v1/halls/{hallId}")
    public HallListDto hall(@PathVariable Long hallId) {
        return new HallListDto(hallService.findHall(hallId));
    }

    // 관리자 기능. 회차 등록 화면에서 공연장을 고를 수 있도록 전체 목록 조회
    @GetMapping("/api/v1/halls")
    public ListResult<List<HallListDto>> halls() {
        List<HallListDto> collect = hallService.findHalls().stream()
                .map(HallListDto::new)
                .toList();
        return new ListResult<>(collect.size(), collect.size(), collect);
    }
}
