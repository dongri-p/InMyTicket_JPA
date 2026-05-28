package com.example.dongri.inmyticket.external.dto;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@XmlRootElement(name = "db")
public class KopisPerformanceResponse {
    
    // 공공데이터API의 XML 태그명과 일치시켜야함!!
    private String mt20id;
    private String prfnm;
    private String prfpdfrom;
    private String prfpdto;
    private String fcltynm;
    private String genrenm;
    private String prfstate;

    @XmlElement(name = "mt20id")
    public void setMt20id(String mt20id) { this.mt20id = mt20id; }

    @XmlElement(name = "prfnm")
    public void setPrfnm(String prfnm) { this.prfnm = prfnm; }

    @XmlElement(name = "prfpdfrom")
    public void setPrfpdfrom(String prfpdfrom) { this.prfpdfrom = prfpdfrom; }

    @XmlElement(name = "prfpdto")
    public void setPrfpdto(String prfpdto) { this.prfpdto = prfpdto; }

    @XmlElement(name = "fcltynm")
    public void setFcltynm(String fcltynm) { this.fcltynm = fcltynm; }

    @XmlElement(name = "genrenm")
    public void setGenrenm(String genrenm) { this.genrenm = genrenm; }

    @XmlElement(name = "prfstate")
    public void setPrfstate(String prfstate) { this.prfstate = prfstate; }
}
