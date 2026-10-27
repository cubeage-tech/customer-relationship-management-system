import ApiService from './api.service';

/** FR-8.1/8.2: targets with live attainment. */
export const listSalesTargets = () => ApiService.getSalesTargets();
export const createSalesTarget = (data) => ApiService.createSalesTarget(data);
export const updateSalesTarget = (id, data) => ApiService.updateSalesTarget(id, data);
export const deleteSalesTarget = (id) => ApiService.deleteSalesTarget(id);

/** FR-8.3: field visits and per-salesperson activity counts ({ from, to } as YYYY-MM-DD). */
export const logVisit = (data) => ApiService.logVisit(data);
export const listVisits = (params) => ApiService.getVisits(params);
export const getActivitySummary = (params) => ApiService.getActivitySummary(params);

/** FR-8.4: attendance. location = { latitude, longitude } or undefined. */
export const getMyAttendance = () => ApiService.getMyAttendance();
export const checkIn = (location) => ApiService.checkIn(location);
export const checkOut = (location) => ApiService.checkOut(location);
export const getAttendance = (params) => ApiService.getAttendance(params);

/** FR-8.5 / AC-8.2: commissions and their rules. */
export const listCommissions = (params) => ApiService.getCommissions(params);
export const approveCommission = (id) => ApiService.approveCommission(id);
export const markCommissionPaid = (id) => ApiService.markCommissionPaid(id);
export const listCommissionRules = () => ApiService.getCommissionRules();
export const createCommissionRule = (data) => ApiService.createCommissionRule(data);
export const updateCommissionRule = (id, data) => ApiService.updateCommissionRule(id, data);
export const deleteCommissionRule = (id) => ApiService.deleteCommissionRule(id);

/** The device location if the user allows it (resolves undefined otherwise, never rejects). */
export const currentLocation = () =>
  new Promise((resolve) => {
    if (!navigator.geolocation) return resolve(undefined);
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ latitude: Number(pos.coords.latitude.toFixed(6)), longitude: Number(pos.coords.longitude.toFixed(6)) }),
      () => resolve(undefined),
      { enableHighAccuracy: true, timeout: 8000, maximumAge: 60000 },
    );
  });
