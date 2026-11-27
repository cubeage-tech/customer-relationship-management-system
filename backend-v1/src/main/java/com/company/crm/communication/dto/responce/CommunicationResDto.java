package com.company.crm.communication.dto.responce;

import com.company.crm.ai.dto.AiDtos.SentimentTag;

import java.time.LocalDateTime;

/** One timeline entry (FR-5.6). */
public record CommunicationResDto(
        Long id,
        Long customerId,
        Long leadId,
        String channel,
        String direction,
        String status,
        String subject,
        String body,
        String counterpart,
        String errorMessage,
        String callOutcome,
        Integer callDurationSeconds,
        LocalDateTime occurredAt,
        /** Staff member who logged/sent it; null for inbound messages and automatic alerts. */
        String createdByName,
        /** FR-AI-6.1: the AI's reading of an inbound message; null until classified. */
        SentimentTag sentiment
) {}
