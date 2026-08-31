import ApiService from './api.service';

export const listCustomers = (params) => ApiService.getCustomers(params);

export const getCustomer = (id) => ApiService.getCustomer(id);

export const createCustomer = (data) => ApiService.createCustomer(data);

export const updateCustomer = (id, data) => ApiService.updateCustomer(id, data);

export const archiveCustomer = (id) => ApiService.archiveCustomer(id);

export const restoreCustomer = (id) => ApiService.restoreCustomer(id);

export const addCustomerContact = (customerId, data) => ApiService.addCustomerContact(customerId, data);

export const updateCustomerContact = (customerId, contactId, data) =>
  ApiService.updateCustomerContact(customerId, contactId, data);

export const deleteCustomerContact = (customerId, contactId) =>
  ApiService.deleteCustomerContact(customerId, contactId);
