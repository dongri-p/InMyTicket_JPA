package com.example.dongri.inmyticket.external.dto;

import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@XmlRootElement(name = "dbs") // XML의 최상단 <dbs> 태그와 매핑
@XmlAccessorType(XmlAccessType.FIELD)
public class KopisPerformanceListResponse {

    @XmlElement(name = "db")
    private List<KopisPerformanceResponse> performances;
    
}
