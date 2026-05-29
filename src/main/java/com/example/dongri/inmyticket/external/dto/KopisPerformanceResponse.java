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
}
