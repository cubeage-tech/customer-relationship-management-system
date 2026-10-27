package com.company.crm.salesforce.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.salesforce.dto.SalesForceDtos.AttendanceRes;
import com.company.crm.salesforce.dto.SalesForceDtos.LocationReq;
import com.company.crm.salesforce.dto.SalesForceDtos.MyAttendance;
import com.company.crm.salesforce.entity.AttendanceRecord;
import com.company.crm.salesforce.repository.AttendanceRecordRepository;
import com.company.crm.user.entity.User;
import com.company.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FR-8.4: attendance — each user checks in and out (from a phone or a desk), with the device's
 * location stored when they allow it. Managers see their team's attendance, the admin everyone's.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    /** FR-M.6: how long a check-in/out recorded offline may wait before syncing. */
    private static final int OFFLINE_HOURS = 48;

    private final AttendanceRecordRepository attendanceRepository;
    private final UserRepository userRepository;
    private final SalesForceScope scope;

    @Transactional(readOnly = true)
    public MyAttendance mine(User currentUser) {
        return new MyAttendance(
                attendanceRepository.findByUserIdAndCheckOutAtIsNull(currentUser.getId()).map(this::toDto).orElse(null),
                attendanceRepository.findTop30ByUserIdOrderByCheckInAtDesc(currentUser.getId()).stream().map(this::toDto).toList());
    }

    @Transactional
    public AttendanceRes checkIn(User currentUser, LocationReq location) {
        scope.tenantId(currentUser);
        validate(location);
        if (attendanceRepository.findByUserIdAndCheckOutAtIsNull(currentUser.getId()).isPresent()) {
            throw ApiException.conflict("You are already checked in — check out first");
        }
        AttendanceRecord r = new AttendanceRecord();
        r.setTenant(currentUser.getTenant());
        r.setUser(userRepository.findById(currentUser.getId()).orElseThrow());
        r.setCheckInAt(when(location));
        if (location != null) {
            r.setCheckInLat(location.getLatitude());
            r.setCheckInLng(location.getLongitude());
        }
        try {
            return toDto(attendanceRepository.saveAndFlush(r));
        } catch (DataIntegrityViolationException e) {
            // Two check-ins at the same moment (e.g. two devices): the unique index keeps one.
            throw ApiException.conflict("You are already checked in — check out first");
        }
    }

    @Transactional
    public AttendanceRes checkOut(User currentUser, LocationReq location) {
        validate(location);
        AttendanceRecord r = attendanceRepository.findByUserIdAndCheckOutAtIsNull(currentUser.getId())
                .orElseThrow(() -> ApiException.badRequest("You are not checked in"));
        LocalDateTime at = when(location);
        if (at.isBefore(r.getCheckInAt())) {
            throw ApiException.badRequest("Check-out can't be before check-in");
        }
        r.setCheckOutAt(at);
        if (location != null) {
            r.setCheckOutLat(location.getLatitude());
            r.setCheckOutLng(location.getLongitude());
        }
        return toDto(attendanceRepository.save(r));
    }

    /** Attendance in [from, to] of the people the caller may see. */
    @Transactional(readOnly = true)
    public List<AttendanceRes> report(User currentUser, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("The period must end on or after its start");
        }
        List<Long> ids = scope.visibleUsers(currentUser).stream().map(User::getId).toList();
        return attendanceRepository.findByTenantIdAndUserIdInAndCheckInAtGreaterThanEqualAndCheckInAtLessThanOrderByCheckInAtDesc(
                        scope.tenantId(currentUser), ids, from.atStartOfDay(), to.plusDays(1).atStartOfDay())
                .stream().map(this::toDto).toList();
    }

    private static void validate(LocationReq location) {
        if (location != null && (location.getLatitude() == null) != (location.getLongitude() == null)) {
            throw ApiException.badRequest("Give both latitude and longitude, or neither");
        }
        if (location != null && location.getAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            if (location.getAt().isAfter(now.plusMinutes(5))) {
                throw ApiException.badRequest("Attendance can't be recorded in the future");
            }
            if (location.getAt().isBefore(now.minusHours(OFFLINE_HOURS))) {
                throw ApiException.badRequest("Attendance recorded offline must sync within " + OFFLINE_HOURS + " hours");
            }
        }
    }

    /** FR-M.6: the time the app recorded it offline, or now. */
    private static LocalDateTime when(LocationReq location) {
        return location != null && location.getAt() != null ? location.getAt() : LocalDateTime.now();
    }

    private AttendanceRes toDto(AttendanceRecord r) {
        Double hours = r.getCheckOutAt() != null
                ? Math.round(Duration.between(r.getCheckInAt(), r.getCheckOutAt()).toMinutes() / 6.0) / 10.0 : null;
        return new AttendanceRes(r.getId(), r.getUser().getId(), r.getUser().getFullName(), r.getCheckInAt(), r.getCheckOutAt(),
                r.getCheckInLat(), r.getCheckInLng(), r.getCheckOutLat(), r.getCheckOutLng(), hours);
    }
}
