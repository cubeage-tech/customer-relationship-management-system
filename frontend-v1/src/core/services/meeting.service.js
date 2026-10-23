import ApiService from './api.service';

/** FR-5.5a: a customer's ({ customerId }) or lead's ({ leadId }) meetings, or my upcoming ones ({}). */
export const listMeetings = (params) => ApiService.getMeetings(params);

export const scheduleMeeting = (data) => ApiService.scheduleMeeting(data);

export const updateMeeting = (id, data) => ApiService.updateMeeting(id, data);

export const cancelMeeting = (id) => ApiService.cancelMeeting(id);

/** FR-5.5c: download one meeting as .ics (opens in the user's calendar app). */
export const downloadMeetingIcs = async (id, title) => {
  const response = await ApiService.downloadMeetingIcs(id);
  const url = window.URL.createObjectURL(new Blob([response.data], { type: 'text/calendar' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `${(title || 'meeting').replace(/[^\w-]+/g, '-')}.ics`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

/** FR-5.5c: my private calendar feed URL ({ url }). */
export const getCalendarFeed = () => ApiService.getCalendarFeed();

export const resetCalendarFeed = () => ApiService.resetCalendarFeed();
