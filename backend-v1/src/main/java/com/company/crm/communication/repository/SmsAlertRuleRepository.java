package com.company.crm.communication.repository;

import com.company.crm.communication.entity.SmsAlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SmsAlertRuleRepository extends JpaRepository<SmsAlertRule, SmsAlertRule.Key> {

    List<SmsAlertRule> findByTenantId(Long tenantId);
}
