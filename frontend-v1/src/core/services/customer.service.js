import ApiService from './api.service';

export const listCustomers = (params) => ApiService.getCustomers(params);

export const getCustomer = (id) => ApiService.getCustomer(id);

/** FR-1.3: won deals and customer-approved quotations, newest first. */
export const getCustomerPurchases = (id) => ApiService.getCustomerPurchases(id);

/** FR-1.5: documents attached to a customer. */
export const listCustomerDocuments = (id) => ApiService.getCustomerDocuments(id);

export const uploadCustomerDocument = (id, file) => ApiService.uploadCustomerDocument(id, file);

export const deleteCustomerDocument = (id, documentId) => ApiService.deleteCustomerDocument(id, documentId);

/** Downloads the file, or (view = true) opens a PDF/image in a new tab. Auth needs the bearer header, so it goes through a blob URL. */
export const openCustomerDocument = async (id, doc, view = false) => {
  const response = await ApiService.downloadCustomerDocument(id, doc.id, view);
  const url = window.URL.createObjectURL(new Blob([response.data], { type: doc.contentType }));
  if (view) {
    window.open(url, '_blank', 'noopener');
    setTimeout(() => window.URL.revokeObjectURL(url), 60000);
    return;
  }
  const link = document.createElement('a');
  link.href = url;
  link.download = doc.fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export const createCustomer = (data) => ApiService.createCustomer(data);

export const updateCustomer = (id, data) => ApiService.updateCustomer(id, data);

export const archiveCustomer = (id) => ApiService.archiveCustomer(id);

export const restoreCustomer = (id) => ApiService.restoreCustomer(id);

export const addCustomerContact = (customerId, data) => ApiService.addCustomerContact(customerId, data);

export const updateCustomerContact = (customerId, contactId, data) =>
  ApiService.updateCustomerContact(customerId, contactId, data);

export const deleteCustomerContact = (customerId, contactId) =>
  ApiService.deleteCustomerContact(customerId, contactId);
