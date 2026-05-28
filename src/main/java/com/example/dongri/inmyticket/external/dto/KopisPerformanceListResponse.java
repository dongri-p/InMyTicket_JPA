package com.example.dongri.inmyticket.external.dto;

import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@XmlRootElement(name = "dbs") // XML의 최상단 <dbs> 태그와 매핑
public class KopisPerformanceListResponse {

    private List<KopisPerformanceResponse> performances;

    @XmlElement(name = "db") // <dbs> 안의 여러 <db>  태그들을 리스트로 바인딩
    public void setPerformances(List<KopisPerformanceResponse> performances) {
        this.performances = performances;
    }
    
}
