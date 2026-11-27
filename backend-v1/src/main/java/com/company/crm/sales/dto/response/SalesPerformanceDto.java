package com.company.crm.sales.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * FR-3.5: open, won and lost opportunity count and value per salesperson (opportunity owner) and per
 * sales team (the owner's team), within the caller's data scope.
 */
public record SalesPerformanceDto(List<Row> byOwner, List<Row> byTeam) {

    /** One salesperson or team; {@code id} is null for "Unassigned" / "No team". */
    public record Row(Long id, String name,
                      long openCount, BigDecimal openValue,
                      long wonCount, BigDecimal wonValue,
                      long lostCount, BigDecimal lostValue) {}
}
