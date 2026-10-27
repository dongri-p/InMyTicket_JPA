package com.example.dongri.inmyticket.service;

import com.example.dongri.inmyticket.domain.ErrorCode;
import com.example.dongri.inmyticket.domain.BusinessException;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.repository.HallRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HallService {

    private final HallRepository hallRepository;

    @Transactional
    public Long saveHall(String name, String address, int totalSeats) {
        Hall hall = new Hall();
        hall.setName(name);
        hall.setAddress(address);
        hall.setTotalSeats(totalSeats);
        return hallRepository.save(hall).getId();
    }

    public Hall findHall(Long hallId) {
        return hallRepository.findById(hallId)
                .orElseThrow(() -> new BusinessException(ErrorCode.HALL_NOT_FOUND));
    }

    public List<Hall> findHalls() {
        return hallRepository.findAll(Sort.by("id"));
    }
}
