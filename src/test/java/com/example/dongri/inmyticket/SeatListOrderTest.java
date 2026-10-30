package com.example.dongri.inmyticket;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.HallRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.service.ScheduleService;

// 좌석 목록은 예매 여부와 관계없이 좌석 번호 순서
// (순서를 안 주면 MySQL이 (schedule_id, status) 인덱스(V12)로 읽어 빈 좌석이 앞에 몰렸음 - 운영 화면에서 발견)
@SpringBootTest
public class SeatListOrderTest {

    @Autowired private ScheduleService scheduleService;
    @Autowired private SeatRepository seatRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private HallRepository hallRepository;

    @Test
    @DisplayName("예약된 좌석이 섞여 있어도 좌석 목록은 번호 순서이고, 페이지를 나눠도 이어진다")
    void seatsInNumberOrder() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Performance performance = new Performance();
        performance.setApiId("order-test-" + suffix);
        performance.setTitle("좌석순서공연" + suffix);
        performanceRepository.save(performance);
        Hall hall = new Hall();
        hall.setName("좌석순서홀" + suffix);
        hall.setTotalSeats(10);
        hallRepository.save(hall);
        Long scheduleId = scheduleService.saveSchedule(performance.getId(), hall.getId(),
                LocalDateTime.now().plusDays(3).withNano(0), 10);

        // 2, 3, 5, 8번을 예약 상태로
        List<Seat> seats = scheduleService.findSeatsBySchedule(scheduleId, PageRequest.of(0, 10)).getContent().stream()
                .filter(s -> List.of(2, 3, 5, 8).contains(s.getSeatNumber()))
                .toList();
        seats.forEach(s -> s.setStatus(SeatStatus.RESERVED));
        seatRepository.saveAll(seats);

        List<Integer> firstPage = numbers(scheduleId, 0, 6);
        List<Integer> secondPage = numbers(scheduleId, 1, 6);

        Assertions.assertEquals(List.of(1, 2, 3, 4, 5, 6), firstPage);
        Assertions.assertEquals(List.of(7, 8, 9, 10), secondPage);
    }

    private List<Integer> numbers(Long scheduleId, int page, int size) {
        return scheduleService.findSeatsBySchedule(scheduleId, PageRequest.of(page, size)).getContent().stream()
                .map(Seat::getSeatNumber)
                .toList();
    }
}
