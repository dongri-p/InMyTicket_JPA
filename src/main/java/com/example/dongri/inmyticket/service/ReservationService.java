package com.example.dongri.inmyticket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;

import lombok.RequiredArgsConstructor;


@Service
@Transactional(readOnly = true)

@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;
    private final SeatRepository seatRepository;

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

        // 3. 저장
        reservationRepository.save(reservation);

        return reservation.getId();
    }

    
    
}
