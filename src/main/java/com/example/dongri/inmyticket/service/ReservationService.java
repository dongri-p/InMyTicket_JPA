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

    // 환불이 필요한 예약인지 확인 (결제가 완료된 예약이면 취소 전 PG 환불 통신이 필요함)
    // 외부 PG 통신을 트리거하기 전에 반드시 소유자 검증부터 해서, 타인의 reservationId로 남의 결제 환불을 유발하지 못하게 함
    public boolean hasCompletedPayment(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));
        reservation.assertOwner(memberId);
        return reservation.getPayment() != null;
    }

    // 예매 취소하기
    @Transactional
    public void cancel(Long memberId, Long reservationId) {
        performCancel(memberId, reservationId, true);
    }

    // PaymentService.processCancel() 전용 진입점.
    // hasCompletedPayment()로 환불 필요 여부를 확인한 시점과 여기서 실제 락을 잡는 시점 사이에
    // 결제가 새로 완료되는 경쟁 상황이 생길 수 있음 - 그 사이 결제가 생겼는데 PG 환불 통신을
    // 안 거쳤다면(refundHandled=false), 조용히 취소하지 않고 재시도를 요구한다.
    @Transactional
    void cancelAfterRefundCheck(Long memberId, Long reservationId, boolean refundHandled) {
        performCancel(memberId, reservationId, refundHandled);
    }

    private void performCancel(Long memberId, Long reservationId, boolean refundHandled) {

        Reservation reservation = reservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));

        reservation.assertOwner(memberId);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 예약입니다.");
        }

        Payment payment = reservation.getPayment();
        if (payment != null && !refundHandled) {
            throw new IllegalStateException("결제 상태가 변경되었습니다. 취소를 다시 시도해주세요.");
        }

        List<Seat> seats = lockReservedSeats(reservation);

        // 공연 시작 이후에는 취소 불가
        LocalDateTime now = LocalDateTime.now();
        boolean alreadyStarted = seats.stream()
                .map(Seat::getSchedule)
                .filter(Objects::nonNull)
                .anyMatch(s -> !s.getStartTime().isAfter(now));
        if (alreadyStarted) {
            throw new IllegalStateException("공연이 이미 시작되어 예약을 취소할 수 없습니다.");
        }

        releaseSeatsAndRestoreCounts(seats);

        // 결제가 완료된 예약이면 결제도 취소 처리
        if (payment != null) {
            payment.cancel();
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
    }

    // 결제 없이 일정 시간 이상 방치된 PENDING 예약들을 찾아 좌석/잔여석을 회수 (스케줄러 전용, 소유자 검증/공연 시작 제한 없음)
    @Transactional
    public int expireStalePendingReservations(LocalDateTime cutoff) {

        List<Reservation> candidates = reservationRepository.findByStatusAndReservedAtBefore(ReservationStatus.PENDING, cutoff);

        int expiredCount = 0;
        for (Reservation candidate : candidates) {
            // 후보 조회는 락 없이 했으므로, 실제 처리 직전에 다시 락을 걸고 상태를 재확인 (그 사이 결제/취소가 먼저 끝났을 수 있음)
            Reservation reservation = reservationRepository.findByIdWithLock(candidate.getId())
                    .orElse(null);
            if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
                continue;
            }

            releaseSeatsAndRestoreCounts(lockReservedSeats(reservation));
            reservation.setStatus(ReservationStatus.CANCELLED);
            expiredCount++;
        }

        return expiredCount;
    }

    // 예약에 속한 좌석들을 한 번에 비관적 락으로 조회
    private List<Seat> lockReservedSeats(Reservation reservation) {
        List<Long> seatIds = reservation.getTickets().stream()
                .map(ticket -> ticket.getSeat().getId())
                .collect(Collectors.toList());
        List<Seat> seats = seatRepository.findByIdInWithLock(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new IllegalArgumentException("존재하지 않는 좌석이 포함되어 있습니다. reservationId=" + reservation.getId());
        }
        return seats;
    }

    // 좌석을 다시 예매 가능 상태로 되돌리고, 잔여 좌석 수 복구
    private void releaseSeatsAndRestoreCounts(List<Seat> seats) {
        for (Seat seat : seats) {
            seat.release();

            if (seat.getSchedule() != null) {
                int updated = scheduleRepository.incrementAvailableSeatCount(seat.getSchedule().getId());
                if (updated == 0) {
                    throw new IllegalStateException("잔여 좌석 수 복구에 실패했습니다. scheduleId=" + seat.getSchedule().getId());
                }
            }
        }
    }
}
