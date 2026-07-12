package com.example.dongri.inmyticket.domain;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import org.springframework.security.access.AccessDeniedException;

import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Reservation {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private int totalPrice;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private LocalDateTime reservedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL)
    private List<Ticket> tickets = new ArrayList<>();

    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Payment payment;

    public static Reservation createReservation(Member member, Seat seat) {
        Reservation reservation = new Reservation();
        reservation.setMember(member);

        seat.reserve();
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setReservedAt(java.time.LocalDateTime.now());
        reservation.setTotalPrice(seat.getPrice());

        Ticket ticket = new Ticket();
        ticket.setReservation(reservation);
        ticket.setSeat(seat);
        reservation.getTickets().add(ticket);

        return reservation;
    }

    // 본인 예약이 아니면 접근 거부 (취소/결제 승인에서 공통으로 사용)
    public void assertOwner(Long memberId) {
        if (!this.member.getId().equals(memberId)) {
            throw new AccessDeniedException("본인의 예약만 처리할 수 있습니다.");
        }
    }

    // 예약된 좌석이 속한 공연이 이미 시작됐는지 여부 (취소/결제 승인에서 공통으로 사용)
    public boolean isShowStarted() {
        LocalDateTime now = LocalDateTime.now();
        return tickets.stream()
                .map(ticket -> ticket.getSeat().getSchedule())
                .filter(Objects::nonNull)
                .anyMatch(schedule -> !schedule.getStartTime().isAfter(now));
    }
}
