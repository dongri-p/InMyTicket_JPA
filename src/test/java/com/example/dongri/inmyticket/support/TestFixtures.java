package com.example.dongri.inmyticket.support;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;

import java.time.LocalDateTime;
import java.util.UUID;

// 여러 테스트 파일에 반복되던 회원/좌석 생성 보일러플레이트를 공통화
public class TestFixtures {

    private static final int DEFAULT_SEAT_PRICE = 150000;

    private TestFixtures() {
    }

    public static Member createAndSaveMember(MemberRepository memberRepository, String namePrefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = new Member();
        member.setLoginId(namePrefix + suffix);
        member.setPassword("password123");
        member.setName(namePrefix + "테스터");
        member.setEmail(namePrefix + suffix + "@test.com");
        memberRepository.save(member);
        return member;
    }

    // 좌석 1개짜리 회차를 공연 시작 하루 뒤로 만들어 저장하고, 그 좌석을 반환
    public static Seat createAndSaveAvailableSeat(ScheduleRepository scheduleRepository) {
        return createAndSaveAvailableSeat(scheduleRepository, LocalDateTime.now().plusDays(1));
    }

    public static Seat createAndSaveAvailableSeat(ScheduleRepository scheduleRepository, LocalDateTime startTime) {
        Schedule schedule = new Schedule();
        schedule.setStartTime(startTime);
        schedule.setTotalSeatCount(1);
        schedule.setAvailableSeatCount(1);

        Seat seat = new Seat();
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setPrice(DEFAULT_SEAT_PRICE);
        schedule.addSeat(seat);

        scheduleRepository.save(schedule);
        return seat;
    }
}
