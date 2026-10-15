package com.example.dongri.inmyticket.external.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@XmlRootElement(name = "db")
@XmlAccessorType(XmlAccessType.FIELD)
public class KopisPerformanceResponse {
    
    @XmlElement(name = "mt20id")
    private String mt20id;   
    
    @XmlElement(name = "prfnm")
    private String prfnm;    
    
    @XmlElement(name = "prfpdfrom")
    private String prfpdfrom; 
    
    @XmlElement(name = "prfpdto")
    private String prfpdto;   
    
    @XmlElement(name = "fcltynm")
    private String fcltynm;  

    @XmlElement(name = "area")
    private String area;
    
    @XmlElement(name = "genrenm")
    private String genrenm;  
    
    @XmlElement(name = "prfstate")
    private String prfstate;

    @XmlElement(name = "poster")
    private String poster;

    // KOPIS는 키 오류 등 요청이 실패해도 HTTP 200에 정상 응답과 같은 <dbs><db> 구조로 내려주고,
    // <db> 안에 공연 필드 대신 returncode/errmsg만 담는다 (실측: 등록 안 된 키 -> returncode 02, "SERVICE KEY IS NOT REGISTERED ERROR").
    // 정상 공연 응답에는 이 태그가 없으므로, 값이 있으면 공연이 아니라 에러 응답이다
    @XmlElement(name = "returncode")
    private String returncode;

    @XmlElement(name = "errmsg")
    private String errmsg;

    public boolean isError() {
        return returncode != null && !returncode.isBlank();
    }

    // KOPIS는 포스터를 http://로 내려주는데, 운영 화면은 HTTPS라 그대로 쓰면 mixed content가 됨.
    // KOPIS 이미지 서버가 https도 지원하므로 저장 전에 https로 바꿔둔다 (목록 sync와 상세 backfill 모두 이 getter를 거침)
    public String getPoster() {
        if (poster != null && poster.startsWith("http://")) {
            return "https://" + poster.substring("http://".length());
        }
        return poster;
    }

    // KOPIS 날짜는 "2026.11.07" 형식. 형식이 다르거나 비어 있으면 기간을 모르는 것으로 보고 null
    private static final DateTimeFormatter KOPIS_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    public LocalDate getStartDate() {
        return parseKopisDate(prfpdfrom);
    }

    public LocalDate getEndDate() {
        return parseKopisDate(prfpdto);
    }

    private static LocalDate parseKopisDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.strip(), KOPIS_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
