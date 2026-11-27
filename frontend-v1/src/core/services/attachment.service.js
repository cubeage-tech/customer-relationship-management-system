import ApiService from './api.service';
import ServerUrl from '../constants/serverUrl.constant';

/** DB-E16: the attachment endpoints for a record — kind = 'quotation' | 'ticket'. */
export const attachmentBase = (kind, id) => (kind === 'ticket' ? ServerUrl.ticketAttachments(id) : ServerUrl.quotationAttachments(id));

export const listAttachments = (base) => ApiService.getAttachments(base);
export const uploadAttachment = (base, file) => ApiService.uploadAttachment(base, file);
export const deleteAttachment = (base, attachmentId) => ApiService.deleteAttachment(base, attachmentId);

/** Downloads the file, or (view = true) opens a PDF/image in a new tab via a blob URL (auth needs the bearer header). */
export const openAttachment = async (base, file, view = false) => {
  const response = await ApiService.downloadAttachment(base, file.id, view);
  const url = window.URL.createObjectURL(new Blob([response.data], { type: file.contentType }));
  if (view) {
    window.open(url, '_blank', 'noopener');
    setTimeout(() => window.URL.revokeObjectURL(url), 60000);
    return;
  }
  const link = document.createElement('a');
  link.href = url;
  link.download = file.fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};
