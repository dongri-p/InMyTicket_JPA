package com.example.dongri.inmyticket.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;

import com.example.dongri.inmyticket.api.dto.CreateResourceResponse;
import com.example.dongri.inmyticket.api.dto.ErrorResponse;
import com.example.dongri.inmyticket.domain.ErrorCode;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

// Swagger UI(/api/docs) 설정. 로그인 API로 받은 토큰을 'Authorize'에 넣으면 회원 API도 바로 호출해볼 수 있음
@Configuration
public class OpenApiConfig {

    private static final String JWT = "JWT";

    @Bean
    public OpenAPI openAPI() {
        Components components = new Components().addSecuritySchemes(JWT, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT"));
        ModelConverters.getInstance().read(ErrorResponse.class).forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("InMyTicket API")
                        .version("v1")
                        .description(description()))
                .components(components)
                .addSecurityItem(new SecurityRequirement().addList(JWT));
    }

    // springdoc은 ResponseEntity를 돌려주는 메서드를 모두 200으로 그림.
    // 생성 API(CreateResourceResponse.created)는 실제로 201 + Location이라 문서도 그렇게 고치고,
    // 에러는 GlobalExceptionHandler가 공통으로 만들어서 각 API에 같은 에러 응답 형식을 붙임
    @Bean
    public OperationCustomizer responseCustomizer() {
        return (operation, handlerMethod) -> {
            if (returnsCreateResourceResponse(handlerMethod.getMethod().getGenericReturnType())) {
                ApiResponse ok = operation.getResponses().remove("200");
                operation.getResponses().addApiResponse("201", ok
                        .description("생성됨. Location 헤더가 생성된 리소스의 단건 조회 주소")
                        .addHeaderObject("Location", new Header().schema(new StringSchema())));
            }
            operation.getResponses().addApiResponse("default", new ApiResponse()
                    .description("에러. code 목록은 문서 상단 표 참고")
                    .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
            return operation;
        };
    }

    private boolean returnsCreateResourceResponse(Type returnType) {
        return returnType instanceof ParameterizedType type
                && type.getRawType() == ResponseEntity.class
                && type.getActualTypeArguments()[0] == CreateResourceResponse.class;
    }

    // 에러 코드 표는 ErrorCode enum에서 만들어서 코드를 추가해도 문서가 따로 놀지 않게 함
    private String description() {
        String errorCodes = Arrays.stream(ErrorCode.values())
                .map(code -> "| `" + code.name() + "` | " + code.getStatus() + " | " + code.getMessage() + " |")
                .collect(Collectors.joining("\n"));
        return """
                공연 좌석 예매 API.

                - 생성은 201 + `Location`, 취소·동기화는 204, 목록은 `{count, totalCount, data}`
                - 회원 API는 `POST /api/v1/members/login`으로 받은 `accessToken`을 Authorize에 넣고 호출합니다. 공연·회차 조회는 토큰 없이 됩니다.
                - 에러는 `{status, code, message}`이고, 화면은 `code`로 분기합니다.

                | code | status | message |
                | --- | --- | --- |
                """ + errorCodes;
    }
}
