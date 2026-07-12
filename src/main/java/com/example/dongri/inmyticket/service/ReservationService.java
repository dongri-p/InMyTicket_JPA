package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.List;
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

        // 공연이 이미 시작된 회차는 예매 불가 (취소 제한과 대칭 - 그렇지 않으면 취소도 안 되는 예약이 생김)
        if (seat.getSchedule() != null && !seat.getSchedule().getStartTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("공연이 이미 시작되어 예매할 수 없습니다.");
        }

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

    // PaymentService.processPayment() 전용 진입점.
    // 긴 PG 통신을 시작하기 전에 예약을 PROCESSING으로 전환해, 그 사이 자동만료 스케줄러(PENDING만 대상)가
    // 결제 진행 중인 예약의 좌석을 회수해버리는 경쟁을 원천 차단한다.
    @Transactional
    public void beginPaymentProcessing(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));

        reservation.assertOwner(memberId);

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException("결제를 시작할 수 없는 예약 상태입니다. status=" + reservation.getStatus());
        }

        // reserve()/performCancel()과 대칭 - 공연 시작 이후에는 결제도 확정할 수 없어야
        // "시작 이후 확정된, 취소도 안 되는 예약"이 생기지 않는다
        if (reservation.isShowStarted()) {
            throw new IllegalStateException("공연이 이미 시작되어 결제를 진행할 수 없습니다.");
        }

        reservation.setStatus(ReservationStatus.PROCESSING);
    }

    // PG 통신 실패 시 PROCESSING으로 전환했던 예약을 PENDING으로 되돌려 재시도/자동만료가 가능하게 한다.
    @Transactional
    public void revertProcessingToPending(Long reservationId) {
        reservationRepository.findByIdWithLock(reservationId)
                .filter(reservation -> reservation.getStatus() == ReservationStatus.PROCESSING)
                .ifPresent(reservation -> reservation.setStatus(ReservationStatus.PENDING));
    }

    // 환불이 필요한 예약인지 확인 (결제가 완료된 예약이면 취소 전 PG 환불 통신이 필요함)
    // 외부 PG 통신을 트리거하기 전에 반드시 소유자 검증부터 해서, 타인의 reservationId로 남의 결제 환불을 유발하지 못하게 함.
    // 공연 시작 이후 취소는 어차피 performCancel()에서 거부되므로, 되돌릴 수 없는 PG 환불 통신을
    // 먼저 내보내지 않도록 여기서도 미리 막는다(환불은 나갔는데 취소는 거부되는 상황 방지).
    public boolean hasCompletedPayment(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));
        reservation.assertOwner(memberId);
        if (reservation.isShowStarted()) {
            throw new IllegalStateException("공연이 이미 시작되어 예약을 취소할 수 없습니다.");
        }
        return reservation.getPayment() != null;
    }

    // 예매 취소하기 (PG 환불 통신 없이 즉시 DB 상태만 취소 처리함)
    // 주의: 결제 완료된 예약을 이 메서드로 취소하면 실제 PG 환불 없이 조용히 CANCELLED 처리됨.
    // 컨트롤러 등 운영 경로에서는 반드시 PaymentService.processCancel()을 통해야 하며,
    // 이 메서드는 결제 자체가 없는 케이스를 다루는 테스트/내부 용도로만 사용할 것.
    @Transactional
    public void cancelWithoutRefundCheck(Long memberId, Long reservationId) {
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

        // 결제 승인 통신이 진행 중(PROCESSING)인 예약을 취소해버리면, 이후 PG 통신이 성공해도
        // approve()가 상태 불일치로 거부해 "PG는 성공했는데 결제 기록이 없는" 상황이 생길 수 있음.
        // 이 락을 잡은 시점에 PROCESSING이면 취소를 거부해 결제 결과가 확정될 때까지 기다리게 함.
        if (reservation.getStatus() == ReservationStatus.PROCESSING) {
            throw new IllegalStateException("결제가 진행 중이라 취소할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }

        Payment payment = reservation.getPayment();
        if (payment != null && !refundHandled) {
            throw new IllegalStateException("결제 상태가 변경되었습니다. 취소를 다시 시도해주세요.");
        }

        // 공연 시작 이후에는 취소 불가 (hasCompletedPayment()에서도 조기 검사하지만, 그 사이 시간이
        // 흘러 공연이 막 시작됐을 수 있으므로 락을 잡은 시점에 다시 확인)
        if (reservation.isShowStarted()) {
            throw new IllegalStateException("공연이 이미 시작되어 예약을 취소할 수 없습니다.");
        }

        List<Seat> seats = lockReservedSeats(reservation);

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
        if (candidates.isEmpty()) {
            return 0;
        }

        // 후보 조회는 락 없이 했으므로, 실제 처리 직전에 한 번에 락을 걸고 상태를 재확인
        // (그 사이 결제/취소가 먼저 끝났을 수 있음). 후보마다 개별 조회하던 N+1을
        // IN 절 일괄 조회로 대체 (cancel 흐름의 좌석 락과 동일한 패턴)
        List<Long> candidateIds = candidates.stream().map(Reservation::getId).collect(Collectors.toList());
        List<Reservation> lockedReservations = reservationRepository.findByIdInWithLock(candidateIds);

        int expiredCount = 0;
        for (Reservation reservation : lockedReservations) {
            if (reservation.getStatus() != ReservationStatus.PENDING) {
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
