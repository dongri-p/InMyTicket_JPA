package com.example.dongri.inmyticket.api.dto;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 생성 API(회원가입/공연장/회차/예매/결제) 공통 응답: 201 Created + Location 헤더 + 생성된 리소스 id.
// 결과는 상태 코드로 전달하고 안내 문구는 화면(프론트) 몫이라 message는 두지 않음
@Getter
@AllArgsConstructor
public class CreateResourceResponse {

    private Long id;

    // POST /api/v1/reservations -> Location: /api/v1/reservations/{id}
    public static ResponseEntity<CreateResourceResponse> created(Long id) {
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri())
                .body(new CreateResourceResponse(id));
    }
}
