package com.example.dongri.inmyticket.external.dto;

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
    
    @XmlElement(name = "genrenm")
    private String genrenm;  
    
    @XmlElement(name = "prfstate")
    private String prfstate;

    @XmlElement(name = "poster")
    private String poster;

    // KOPIS는 포스터를 http://로 내려주는데, 운영 화면은 HTTPS라 그대로 쓰면 mixed content가 됨.
    // KOPIS 이미지 서버가 https도 지원하므로 저장 전에 https로 바꿔둔다 (목록 sync와 상세 backfill 모두 이 getter를 거침)
    public String getPoster() {
        if (poster != null && poster.startsWith("http://")) {
            return "https://" + poster.substring("http://".length());
        }
        return poster;
    }
}
