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

    // PROCESSING으로 전환된 시각 - PG 통신 중 서버가 죽어 PROCESSING에 고착된 예약을 찾는 기준.
    // reservedAt(예약 시각)을 기준으로 삼으면 예약 직후가 아니라 늦게 결제를 시작한 진행 중인 건까지 되돌릴 수 있어 따로 둠
    private LocalDateTime processingStartedAt;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL)
    private List<Ticket> tickets = new ArrayList<>();

    @OneToOne(mappedBy = "reservation", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Payment payment;

    // 좌석마다 티켓 1장씩, 총액은 좌석 가격의 합
    // 좌석 하나라도 이미 예약됐으면 seat.reserve()에서 예외 -> 호출 트랜잭션 전체 롤백 (일부 좌석만 예약되지 않음)
    public static Reservation createReservation(Member member, List<Seat> seats) {
        Reservation reservation = new Reservation();
        reservation.setMember(member);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setReservedAt(LocalDateTime.now());

        int totalPrice = 0;
        for (Seat seat : seats) {
            seat.reserve();
            totalPrice += seat.getPrice();

            Ticket ticket = new Ticket();
            ticket.setReservation(reservation);
            ticket.setSeat(seat);
            reservation.getTickets().add(ticket);
        }
        reservation.setTotalPrice(totalPrice);

        return reservation;
    }

    // 본인 예약이 아니면 접근 거부 (취소/결제 승인에서 공통으로 사용)
    public void assertOwner(Long memberId) {
        if (!this.member.getId().equals(memberId)) {
            throw new OwnershipViolationException("본인의 예약만 처리할 수 있습니다.");
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
