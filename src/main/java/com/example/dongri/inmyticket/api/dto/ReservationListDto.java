package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;

import lombok.Getter;

@Getter
public class ReservationListDto {

    private Long reservationId;
    private String status;
    private int totalPrice;
    private LocalDateTime reservedAt;
    private boolean paid;
    private String performanceTitle;
    private LocalDateTime scheduleStartTime;
    private List<SeatInfo> seats;

    public ReservationListDto(Reservation reservation) {
        this.reservationId = reservation.getId();
        this.status = reservation.getStatus().name();
        this.totalPrice = reservation.getTotalPrice();
        this.reservedAt = reservation.getReservedAt();
        this.paid = reservation.getPayment() != null;

        Schedule schedule = reservation.getTickets().get(0).getSeat().getSchedule();
        this.performanceTitle = schedule.getPerformance().getTitle();
        this.scheduleStartTime = schedule.getStartTime();

        this.seats = reservation.getTickets().stream()
                .map(ticket -> new SeatInfo(ticket.getSeat()))
                .collect(Collectors.toList());
    }

    @Getter
    public static class SeatInfo {
        private String grade;
        private int seatNumber;

        public SeatInfo(Seat seat) {
            this.grade = seat.getGrade();
            this.seatNumber = seat.getSeatNumber();
        }
    }
}
