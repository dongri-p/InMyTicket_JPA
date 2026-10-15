package com.example.dongri.inmyticket.domain;

import java.util.List;

// 화면 지역 필터 -> KOPIS 지역명(Performance.area) 묶음.
// KOPIS 지역은 시·도 단위(15개 이상)라 칩으로 다 보여주기엔 많아서 권역으로 묶는다.
// 행정구역 이름이 바뀔 수 있어(실측: 광주·전남이 '전남광주통합특별시'로 내려옴) 옛 이름도 함께 둔다.
// 여기 없는 지역명이 새로 생기면 '전체'에서만 보이므로, 동기화 로그나 DB에서 area 값을 확인해 추가할 것
public enum RegionGroup {

    SEOUL(List.of("서울특별시")),
    GYEONGGI_INCHEON(List.of("경기도", "인천광역시")),
    CHUNGCHEONG_GANGWON(List.of("대전광역시", "세종특별자치시", "충청남도", "충청북도", "강원특별자치도", "강원도")),
    GYEONGSANG(List.of("부산광역시", "대구광역시", "울산광역시", "경상북도", "경상남도")),
    JEOLLA_JEJU(List.of("전남광주통합특별시", "광주광역시", "전라남도", "전북특별자치도", "전라북도", "제주특별자치도"));

    private final List<String> areas;

    RegionGroup(List<String> areas) {
        this.areas = areas;
    }

    public List<String> getAreas() {
        return areas;
    }
}
