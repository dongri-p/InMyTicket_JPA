package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatGrade;

import lombok.Getter;

@Getter
public class ReservationListDto {

    private Long reservationId;
    private String status;
    private int totalPrice;
    private LocalDateTime reservedAt;
    private boolean paid;
    // 취소된 결제의 수수료·환불액 (결제가 없거나 아직 취소 전이면 null)
    private Integer cancelFee;
    private Integer refundedAmount;
    private String performanceTitle;
    private LocalDateTime scheduleStartTime;
    private List<SeatInfo> seats;

    public ReservationListDto(Reservation reservation) {
        this.reservationId = reservation.getId();
        this.status = reservation.getStatus().name();
        this.totalPrice = reservation.getTotalPrice();
        this.reservedAt = reservation.getReservedAt();
        this.paid = reservation.getPayment() != null;
        if (reservation.getPayment() != null) {
            this.cancelFee = reservation.getPayment().getCancelFee();
            this.refundedAmount = reservation.getPayment().getRefundedAmount();
        }

        Schedule schedule = reservation.getTickets().get(0).getSeat().getSchedule();
        this.performanceTitle = schedule.getPerformance().getTitle();
        this.scheduleStartTime = schedule.getStartTime();

        this.seats = reservation.getTickets().stream()
                .map(ticket -> new SeatInfo(ticket.getSeat()))
                .collect(Collectors.toList());
    }

    @Getter
    public static class SeatInfo {
        private SeatGrade grade;
        private int seatNumber;

        public SeatInfo(Seat seat) {
            this.grade = seat.getGrade();
            this.seatNumber = seat.getSeatNumber();
        }
    }
}
