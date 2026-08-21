package com.company.crm.plan.service;

import com.company.crm.common.enums.SubscriptionPlan;
import com.company.crm.common.exception.ApiException;
import com.company.crm.plan.dto.request.UpdatePlanPriceReqDto;
import com.company.crm.plan.dto.response.PlanPriceResDto;
import com.company.crm.plan.entity.PlanPrice;
import com.company.crm.plan.mapper.PlanPriceMapper;
import com.company.crm.plan.repository.PlanPriceRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanPriceService {

    private final PlanPriceRepository planPriceRepository;
    private final PlanPriceMapper planPriceMapper;

    public List<PlanPriceResDto> listPrices() {
        return planPriceRepository.findAll().stream()
                .map(planPriceMapper::toDto)
                .toList();
    }

    @Transactional
    public PlanPriceResDto updatePrice(User superAdmin, String rawPlan, UpdatePlanPriceReqDto dto) {
        SubscriptionPlan plan;
        try {
            plan = SubscriptionPlan.fromDbValue(rawPlan);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown plan: " + rawPlan);
        }

        PlanPrice planPrice = planPriceRepository.findByPlan(plan)
                .orElseThrow(() -> ApiException.notFound("No price configured for plan: " + rawPlan));

        planPrice.setMonthlyPrice(dto.getMonthlyPrice());
        planPrice.setAnnualPrice(dto.getAnnualPrice());
        if (dto.getCurrency() != null && !dto.getCurrency().isBlank()) {
            planPrice.setCurrency(dto.getCurrency().toUpperCase());
        }
        planPrice.setUpdatedBy(superAdmin);

        return planPriceMapper.toDto(planPriceRepository.save(planPrice));
    }
}
