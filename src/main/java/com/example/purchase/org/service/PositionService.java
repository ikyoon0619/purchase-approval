package com.example.purchase.org.service;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.repository.PositionRepository;
import com.example.purchase.org.web.PositionForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PositionService {

    private final PositionRepository positionRepository;

    public List<Position> findAll() {
        return positionRepository.findAllByOrderByRankOrderAsc();
    }

    public Position get(Long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.POSITION_NOT_FOUND));
    }

    @Transactional
    public Long create(PositionForm form) {
        if (positionRepository.existsByName(form.getName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_POSITION_NAME);
        }
        return positionRepository.save(new Position(form.getName(), form.getRankOrder())).getId();
    }

    @Transactional
    public void update(Long id, PositionForm form) {
        Position position = get(id);
        if (!position.getName().equals(form.getName()) && positionRepository.existsByName(form.getName())) {
            throw new BusinessException(ErrorCode.DUPLICATE_POSITION_NAME);
        }
        position.update(form.getName(), form.getRankOrder());
    }
}
