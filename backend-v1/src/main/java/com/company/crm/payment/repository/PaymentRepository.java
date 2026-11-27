package com.company.crm.payment.repository;

import com.company.crm.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    /**
     * Row-locks the payment (SELECT ... FOR UPDATE) until the transaction ends. /verify and the
     * provider webhook usually arrive within a second of each other; the lock makes the second
     * one wait and then see PAID, so a payment can never activate the subscription twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.gatewayOrderId = :gatewayOrderId")
    Optional<Payment> findForUpdateByGatewayOrderId(@Param("gatewayOrderId") String gatewayOrderId);
}
