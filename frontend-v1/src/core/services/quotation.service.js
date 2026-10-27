import ApiService from './api.service';

export const listQuotations = (params) => ApiService.getQuotations(params);

export const getQuotation = (id) => ApiService.getQuotation(id);

export const createQuotation = (data) => ApiService.createQuotation(data);

export const updateQuotation = (id, data) => ApiService.updateQuotation(id, data);

export const sendQuotation = (id) => ApiService.sendQuotation(id);

export const setQuotationCustomerStatus = (id, status) =>
  ApiService.setQuotationCustomerStatus(id, { status });

export const approveQuotationDiscount = (id) => ApiService.approveQuotationDiscount(id);

export const rejectQuotationDiscount = (id, reason) =>
  ApiService.rejectQuotationDiscount(id, { reason });

const saveBlob = (data, fileName) => {
  const url = window.URL.createObjectURL(new Blob([data], { type: 'application/pdf' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

/** NAV-M05: the quotation PDF as a Blob, for the in-app preview. */
export const fetchQuotationPdf = async (id) => {
  const response = await ApiService.downloadQuotationPdf(id);
  return new Blob([response.data], { type: 'application/pdf' });
};

/** Triggers a browser download of the quotation PDF (FR-4.4). */
export const downloadQuotationPdf = async (id, quotationNumber) => {
  const response = await ApiService.downloadQuotationPdf(id);
  saveBlob(response.data, `${quotationNumber || `quotation-${id}`}.pdf`);
};

/** FR-4.5: the customer's view of a sent quotation (public link; opening it marks it viewed). */
export const getCustomerQuotation = (token) => ApiService.getPublicQuotation(token);

export const downloadCustomerQuotationPdf = async (token, quotationNumber) => {
  const response = await ApiService.downloadPublicQuotationPdf(token);
  saveBlob(response.data, `${quotationNumber || 'quotation'}.pdf`);
};

/** NAV-2: the customer accepts ('accept') or declines ('decline') from the view link, with an optional comment. */
export const respondToQuotation = (token, decision, comment) => ApiService.respondToQuotation(token, decision, comment);
