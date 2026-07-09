package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Payment;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;

import lombok.RequiredArgsConstructor;


@Service
@Transactional(readOnly = true)

@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final SeatRepository seatRepository;
    private final ScheduleRepository scheduleRepository;

    // 티켓 예매하기
    @Transactional
    public Long reserve(Long memberId, Long seatId) {

        // 1. 엔티티 조회 (회원은 일반 조회, 좌석은 비관적 락 조회)
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. id=" + memberId));

        Seat seat = seatRepository.findByIdWithLock(seatId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 좌석입니다. id=" + seatId));

        // 2. 예매 생성
        Reservation reservation = Reservation.createReservation(member, seat);

        // 3. 회차의 잔여 좌석 수를 원자적으로 감소 (동시 예매 시 lost-update 방지)
        if (seat.getSchedule() != null) {
            int updated = scheduleRepository.decrementAvailableSeatCount(seat.getSchedule().getId());
            if (updated == 0) {
                throw new IllegalStateException("잔여 좌석이 없습니다. scheduleId=" + seat.getSchedule().getId());
            }
        }

        // 4. 저장
        reservationRepository.save(reservation);

        return reservation.getId();
    }

    // 예매 취소하기
    @Transactional
    public void cancel(Long memberId, Long reservationId) {

        Reservation reservation = reservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));

        reservation.assertOwner(memberId);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 예약입니다.");
        }

        // 좌석을 다시 예매 가능 상태로 되돌리고, 잔여 좌석 수 복구 (티켓 수만큼 반복 조회하지 않도록 한 번에 락 조회)
        List<Long> seatIds = reservation.getTickets().stream()
                .map(ticket -> ticket.getSeat().getId())
                .collect(Collectors.toList());
        List<Seat> seats = seatRepository.findByIdInWithLock(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 좌석이 포함되어 있습니다. reservationId=" + reservationId);
        }

        // 공연 시작 이후에는 취소 불가
        LocalDateTime now = LocalDateTime.now();
        boolean alreadyStarted = seats.stream()
                .map(Seat::getSchedule)
                .filter(Objects::nonNull)
                .anyMatch(s -> !s.getStartTime().isAfter(now));
        if (alreadyStarted) {
            throw new IllegalStateException("공연이 이미 시작되어 예약을 취소할 수 없습니다.");
        }

        for (Seat seat : seats) {
            seat.release();

            if (seat.getSchedule() != null) {
                int updated = scheduleRepository.incrementAvailableSeatCount(seat.getSchedule().getId());
                if (updated == 0) {
                    throw new IllegalStateException("잔여 좌석 수 복구에 실패했습니다. scheduleId=" + seat.getSchedule().getId());
                }
            }
        }

        // 결제가 완료된 예약이면 결제도 취소 처리
        Payment payment = reservation.getPayment();
        if (payment != null) {
            payment.cancel();
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
    }
}
