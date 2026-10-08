package com.example.dongri.inmyticket.domain;

import java.util.List;

// 화면 장르 메뉴 -> KOPIS 장르명(Performance.category) 묶음.
// KOPIS 장르는 9개로 잘게 나뉘어 있어 화면에선 비슷한 것끼리 묶어서 보여준다.
public enum GenreGroup {

    CONCERT(List.of("대중음악")),
    MUSICAL_PLAY(List.of("뮤지컬", "연극")),
    CLASSIC(List.of("서양음악(클래식)", "한국음악(국악)")),
    DANCE(List.of("무용(서양/한국무용)", "대중무용")),
    ETC(List.of("서커스/마술", "복합"));

    private final List<String> categories;

    GenreGroup(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getCategories() {
        return categories;
    }
}
