import ApiService from './api.service';

/**
 * Module 9 reports. params = { from, to (YYYY-MM-DD), industry, teamId, userId } — every filter is
 * optional and the backend keeps results within the caller's data scope (FR-9.6).
 */
export const getHeadline = (params) => ApiService.getAnalyticsHeadline(params);
export const getSalesReport = (params) => ApiService.getSalesReport(params);
export const getCustomerReport = (params) => ApiService.getCustomerReport(params);
export const getRevenueReport = (params) => ApiService.getRevenueReport(params);
export const getTeamPerformance = (params) => ApiService.getTeamPerformance(params);
export const getReportFilterOptions = () => ApiService.getReportFilterOptions();
/** REP-SVC (params also take priority, technicianId) and REP-CMP. */
export const getServiceReport = (params) => ApiService.getServiceReport(params);
export const getCampaignReport = (params) => ApiService.getCampaignReport(params);

/** FR-9.7: downloads a report as format = 'pdf' | 'xlsx', with the same filters as on screen. */
export const exportReport = async (report, format, params) => {
  const response = await ApiService.exportReport(report, { ...params, format });
  const disposition = response.headers?.['content-disposition'] || '';
  // The header may be hidden by CORS; the fallback matches the server's naming.
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] || `${report}-report-${params.from}-to-${params.to}.${format}`;
  const url = window.URL.createObjectURL(new Blob([response.data], { type: response.headers?.['content-type'] }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

/** REP-SCH: scheduled report emails. */
export const listReportSchedules = () => ApiService.getReportSchedules();
export const createReportSchedule = (data) => ApiService.createReportSchedule(data);
export const updateReportSchedule = (id, data) => ApiService.updateReportSchedule(id, data);
export const deleteReportSchedule = (id) => ApiService.deleteReportSchedule(id);
export const sendReportSchedule = (id) => ApiService.sendReportSchedule(id);

/** FR-9.1: the caller's dashboard tiles ({ widgets: [...keys], period }). */
export const getDashboardPreferences = () => ApiService.getDashboardPreferences();
export const saveDashboardPreferences = (data) => ApiService.saveDashboardPreferences(data);
