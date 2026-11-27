import ApiService from './api.service';

/** FR-5.5a: a customer's ({ customerId }) or lead's ({ leadId }) meetings, or my upcoming ones ({}). */
export const listMeetings = (params) => ApiService.getMeetings(params);

export const scheduleMeeting = (data) => ApiService.scheduleMeeting(data);

export const updateMeeting = (id, data) => ApiService.updateMeeting(id, data);

export const cancelMeeting = (id) => ApiService.cancelMeeting(id);

/** FR-AI-7.1: a meeting's recordings, each with its consent record. */
export const listMeetingRecordings = (meetingId) => ApiService.listMeetingRecordings(meetingId);

/** FR-AI-7.1: fields = { consentConfirmed, consentGivenBy, consentMethod, consentNote?, durationSeconds? } — refused without consent. */
export const uploadMeetingRecording = (meetingId, file, fields) => ApiService.uploadMeetingRecording(meetingId, file, fields);

/** FR-AI-7.1: the audio as a blob response (each play is audited). */
export const getMeetingRecordingAudio = (meetingId, recordingId) => ApiService.getMeetingRecordingAudio(meetingId, recordingId);

/** FR-AI-7.2: the meeting's transcript (with consent) and AI summary. */
export const getMeetingConversation = (meetingId) => ApiService.getMeetingConversation(meetingId);

/** FR-AI-7.2: { text, source: browser|upload|pasted, recordingId? | consentConfirmed, consentGivenBy, consentMethod }. */
export const saveMeetingTranscript = (meetingId, data) => ApiService.saveMeetingTranscript(meetingId, data);

export const deleteMeetingTranscript = (meetingId, reason) => ApiService.deleteMeetingTranscript(meetingId, reason);

/** FR-AI-7.2: the AI summary → { overview, keyPoints, decisions, concerns, customerSentiment, nextSteps }. */
export const summarizeMeeting = (meetingId) => ApiService.summarizeMeeting(meetingId);

export const deleteMeetingRecording = (meetingId, recordingId, reason) => ApiService.deleteMeetingRecording(meetingId, recordingId, reason);

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
