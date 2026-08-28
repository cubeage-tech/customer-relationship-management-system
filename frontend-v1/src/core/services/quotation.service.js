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

/** Triggers a browser download of the quotation PDF (FR-4.4). */
export const downloadQuotationPdf = async (id, quotationNumber) => {
  const response = await ApiService.downloadQuotationPdf(id);
  const url = window.URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = `${quotationNumber || `quotation-${id}`}.pdf`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};
