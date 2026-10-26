import ApiService from './api.service';

/** ARC-9: active custom fields of a record type ('customer' | 'lead' | 'opportunity'), every industry's. */
export const listCustomFields = (entityType) => ApiService.getCustomFields(entityType);

/** ARC-9 (admin): every custom field, including switched-off ones. */
export const listAllCustomFields = () => ApiService.getAllCustomFields();

export const createCustomField = (data) => ApiService.createCustomField(data);

export const updateCustomField = (id, data) => ApiService.updateCustomField(id, data);

export const listCustomFieldTemplates = () => ApiService.getCustomFieldTemplates();

export const applyCustomFieldTemplate = (industry) => ApiService.applyCustomFieldTemplate(industry);

/** A record's fields (those of its industry) and values: { industry, fields, values }. */
export const getCustomFieldValues = (entityType, id) => ApiService.getCustomFieldValues(entityType, id);

export const saveCustomFieldValues = (entityType, id, values) => ApiService.saveCustomFieldValues(entityType, id, values);
