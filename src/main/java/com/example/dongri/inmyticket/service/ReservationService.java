package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.BusinessException;
import com.example.dongri.inmyticket.domain.ErrorCode;
import com.example.dongri.inmyticket.api.dto.ReservationListDto;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Payment;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
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

    // 좌석 1개 예매 - 좌석 목록 버전에 위임
    @Transactional
    public Long reserve(Long memberId, Long seatId) {
        return reserve(memberId, List.of(seatId));
    }

    // 티켓 예매하기 (같은 회차 좌석 여러 개를 예약 1건으로 묶음)
    @Transactional
    public Long reserve(Long memberId, List<Long> seatIds) {

        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SEAT_SELECTION);
        }

        // 0. 이미 팔린 좌석이 섞여 있으면 락을 기다리지 않고 바로 거절.
        // 예매 오픈 때 한 좌석에 몰린 요청이 전부 락 앞에 줄을 서서, 어차피 실패할 요청도 앞 요청이 끝나길 하나씩 기다렸음.
        // 락 없는 읽기라 여기를 통과해도 아래 락 조회에서 다시 확인하므로 정확성은 락이 지킴 (여기선 확실히 실패할 요청만 걸러냄)
        if (seatRepository.countNotAvailable(seatIds, SeatStatus.AVAILABLE) > 0) {
            throw new BusinessException(ErrorCode.SEAT_ALREADY_RESERVED);
        }

        // 1. 엔티티 조회 (회원은 일반 조회, 좌석은 비관적 락 조회)
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // 좌석들을 id 오름차순으로 한 번에 잠금 - 겹치는 좌석을 동시에 고른 두 요청도 같은 순서로 락을 잡아 데드락 없음
        List<Seat> seats = seatRepository.findByIdInWithLock(seatIds);
        if (seats.size() != seatIds.size()) {
            throw new BusinessException(ErrorCode.SEAT_NOT_FOUND);
        }

        // 한 예약은 한 회차만 - 회차가 섞이면 결제·취소·공연 시작 판단 기준이 모호해짐
        // (같은 영속성 컨텍스트라 같은 회차는 같은 객체로 조회됨)
        Schedule schedule = seats.get(0).getSchedule();
        if (!seats.stream().allMatch(seat -> seat.getSchedule() == schedule)) {
            throw new BusinessException(ErrorCode.MIXED_SCHEDULE_SEATS);
        }

        // 공연이 이미 시작된 회차는 예매 불가 (취소 제한과 대칭 - 그렇지 않으면 취소도 안 되는 예약이 생김)
        if (schedule != null && !schedule.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.SHOW_ALREADY_STARTED, "공연이 이미 시작되어 예매할 수 없습니다.");
        }

        // 2. 예매 생성 (좌석 하나라도 이미 예약됐으면 예외 -> 전체 롤백)
        Reservation reservation = Reservation.createReservation(member, seats);

        // 3. 회차의 잔여 좌석 수를 원자적으로 감소 (동시 예매 시 lost-update 방지)
        if (schedule != null) {
            int updated = scheduleRepository.decreaseAvailableSeatCount(schedule.getId(), seats.size());
            if (updated == 0) {
                throw new BusinessException(ErrorCode.NOT_ENOUGH_SEATS);
            }
        }

        // 4. 저장
        reservationRepository.save(reservation);

        return reservation.getId();
    }

    // 내 예매 목록 조회 (읽기 전용이라 클래스 기본 @Transactional(readOnly = true) 그대로 사용)
    public List<ReservationListDto> findMyReservations(Long memberId) {
        return reservationRepository.findWithDetailsByMemberId(memberId).stream()
                .map(ReservationListDto::new)
                .collect(Collectors.toList());
    }

    // 예매 단건 조회 - 예매 생성 응답의 Location이 가리키는 곳. 본인 예약만 조회 가능
    public ReservationListDto findMyReservation(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findWithDetailsById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        reservation.assertOwner(memberId);
        return new ReservationListDto(reservation);
    }

    // PaymentService.processPayment() 전용 진입점.
    // 긴 PG 통신을 시작하기 전에 예약을 PROCESSING으로 전환해, 그 사이 자동만료 스케줄러(PENDING만 대상)가
    // 결제 진행 중인 예약의 좌석을 회수해버리는 경쟁을 원천 차단한다.
    @Transactional
    public void beginPaymentProcessing(Long memberId, Long reservationId, int amount) {
        Reservation reservation = reservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        reservation.assertOwner(memberId);

        // 결제창 금액은 프론트가 정하므로 조작될 수 있음 -> PG 승인 전에 예약 금액과 같은지 확인
        if (reservation.getTotalPrice() != amount) {
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 같은 결제 요청이 겹쳐 들어온 경우(더블클릭, 응답 전 재시도)는 '결제 불가'가 아니라 '진행 중'으로 알려줌
        // -> 화면은 실패 대신 잠시 뒤 결과를 확인하면 됨 (끝난 뒤 같은 요청은 PaymentService가 기존 결제를 돌려줌)
        if (reservation.getStatus() == ReservationStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PROCESSING);
        }
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_PAYABLE);
        }

        // reserve()/performCancel()과 대칭 - 공연 시작 이후에는 결제도 확정할 수 없어야
        // "시작 이후 확정된, 취소도 안 되는 예약"이 생기지 않는다
        if (reservation.isShowStarted()) {
            throw new BusinessException(ErrorCode.SHOW_ALREADY_STARTED, "공연이 이미 시작되어 결제를 진행할 수 없습니다.");
        }

        reservation.setStatus(ReservationStatus.PROCESSING);
        reservation.setProcessingStartedAt(LocalDateTime.now());
    }

    // PG 통신 실패 시 PROCESSING으로 전환했던 예약을 PENDING으로 되돌려 재시도/자동만료가 가능하게 한다.
    @Transactional
    public void revertProcessingToPending(Long reservationId) {
        reservationRepository.findByIdWithLock(reservationId)
                .filter(reservation -> reservation.getStatus() == ReservationStatus.PROCESSING)
                .ifPresent(this::revertToPending);
    }

    // PG 통신 도중 서버가 죽거나 재배포되면 processPayment()의 catch(revertProcessingToPending)가 실행되지 않아
    // 예약이 PROCESSING에 영구히 갇힘 - 취소(PROCESSING 거부)도 자동만료(PENDING만 대상)도 닿지 않아 좌석이 묶임.
    // 결제 시작 후 cutoff가 지나도록 PROCESSING인 예약은 승인되지 않은 것으로 보고 PENDING으로 되돌려
    // 재결제 또는 기존 자동만료 흐름을 타게 함 (스케줄러 전용).
    // 한계: 실제 PG라면 되돌리기 전에 PG 거래 조회 API로 승인 여부를 확인해야 함. 이 프로젝트의 PG는
    // 시뮬레이션이라 조회할 거래 상태가 없으므로 "승인 기록(Payment)이 없으면 미승인"으로 간주함.
    @Transactional
    public int recoverStuckProcessingReservations(LocalDateTime cutoff) {

        List<Long> candidateIds =
                reservationRepository.findIdsByStatusAndProcessingStartedAtBefore(ReservationStatus.PROCESSING, cutoff);
        if (candidateIds.isEmpty()) {
            return 0;
        }

        // 후보 조회는 락 없이 했으므로, 락을 잡은 뒤 상태를 재확인 (그 사이 승인/되돌리기가 끝났을 수 있음)
        // 후보를 id로만 받았기 때문에 여기서 엔티티가 처음 로딩되어, 재확인이 캐시된 옛 상태가 아닌 최신 상태로 이뤄짐
        List<Reservation> lockedReservations = reservationRepository.findByIdInWithLock(candidateIds);

        int recoveredCount = 0;
        for (Reservation reservation : lockedReservations) {
            if (reservation.getStatus() != ReservationStatus.PROCESSING
                    || !reservation.getProcessingStartedAt().isBefore(cutoff)) {
                continue;
            }
            revertToPending(reservation);
            recoveredCount++;
        }

        return recoveredCount;
    }

    private void revertToPending(Reservation reservation) {
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setProcessingStartedAt(null);
    }

    // 환불이 필요한 예약인지 확인 (결제가 완료된 예약이면 취소 전 PG 환불 통신이 필요함)
    // 외부 PG 통신을 트리거하기 전에 반드시 소유자 검증부터 해서, 타인의 reservationId로 남의 결제 환불을 유발하지 못하게 함.
    // 공연 시작 이후 취소는 어차피 performCancel()에서 거부되므로, 되돌릴 수 없는 PG 환불 통신을
    // 먼저 내보내지 않도록 여기서도 미리 막는다(환불은 나갔는데 취소는 거부되는 상황 방지).
    // 환불할 결제가 있으면 그 결제(paymentKey·결제한 PG를 환불 요청에 사용)를, 없으면 빈 값을 반환
    public Optional<Payment> findPaymentToRefund(Long memberId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        reservation.assertOwner(memberId);
        if (reservation.isShowStarted()) {
            throw new BusinessException(ErrorCode.SHOW_ALREADY_STARTED, "공연이 이미 시작되어 예약을 취소할 수 없습니다.");
        }
        return Optional.ofNullable(reservation.getPayment());
    }

    // 예매 취소하기 (PG 환불 통신 없이 즉시 DB 상태만 취소 처리함)
    // 주의: 결제 완료된 예약을 이 메서드로 취소하면 실제 PG 환불 없이 조용히 CANCELLED 처리됨.
    // 컨트롤러 등 운영 경로에서는 반드시 PaymentService.processCancel()을 통해야 하며,
    // 이 메서드는 결제 자체가 없는 케이스를 다루는 테스트/내부 용도로만 사용할 것.
    @Transactional
    void cancelWithoutRefundCheck(Long memberId, Long reservationId) {
        performCancel(memberId, reservationId, true);
    }

    // PaymentService.processCancel() 전용 진입점.
    // findPaymentToRefund()로 환불 필요 여부를 확인한 시점과 여기서 실제 락을 잡는 시점 사이에
    // 결제가 새로 완료되는 경쟁 상황이 생길 수 있음 - 그 사이 결제가 생겼는데 PG 환불 통신을
    // 안 거쳤다면(refundHandled=false), 조용히 취소하지 않고 재시도를 요구한다.
    @Transactional
    void cancelAfterRefundCheck(Long memberId, Long reservationId, boolean refundHandled) {
        performCancel(memberId, reservationId, refundHandled);
    }

    private void performCancel(Long memberId, Long reservationId, boolean refundHandled) {

        Reservation reservation = reservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        reservation.assertOwner(memberId);

        if (reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_CANCELLED);
        }

        // 결제 승인 통신이 진행 중(PROCESSING)인 예약을 취소해버리면, 이후 PG 통신이 성공해도
        // approve()가 상태 불일치로 거부해 "PG는 성공했는데 결제 기록이 없는" 상황이 생길 수 있음.
        // 이 락을 잡은 시점에 PROCESSING이면 취소를 거부해 결제 결과가 확정될 때까지 기다리게 함.
        if (reservation.getStatus() == ReservationStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.PAYMENT_IN_PROGRESS);
        }

        Payment payment = reservation.getPayment();
        if (payment != null && !refundHandled) {
            throw new BusinessException(ErrorCode.PAYMENT_STATE_CHANGED);
        }

        // 공연 시작 이후에는 취소 불가 (findPaymentToRefund()에서도 조기 검사하지만, 그 사이 시간이
        // 흘러 공연이 막 시작됐을 수 있으므로 락을 잡은 시점에 다시 확인)
        if (reservation.isShowStarted()) {
            throw new BusinessException(ErrorCode.SHOW_ALREADY_STARTED, "공연이 이미 시작되어 예약을 취소할 수 없습니다.");
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

        List<Long> candidateIds = reservationRepository.findIdsByStatusAndReservedAtBefore(ReservationStatus.PENDING, cutoff);
        if (candidateIds.isEmpty()) {
            return 0;
        }

        // 후보 조회는 락 없이 했으므로, 실제 처리 직전에 한 번에 락을 걸고 상태를 재확인
        // (그 사이 결제/취소가 먼저 끝났을 수 있음). 후보마다 개별 조회하던 N+1을
        // IN 절 일괄 조회로 대체 (cancel 흐름의 좌석 락과 동일한 패턴)
        // 후보를 id로만 받았기 때문에 여기서 엔티티가 처음 로딩되어, 재확인이 캐시된 옛 상태가 아닌 최신 상태로 이뤄짐
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
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "예약의 좌석 정보가 올바르지 않습니다. reservationId=" + reservation.getId());
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
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "잔여 좌석 수 복구에 실패했습니다. scheduleId=" + seat.getSchedule().getId());
                }
            }
        }
    }
}
