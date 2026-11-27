import ApiService from './api.service';

/** FR-1.6: the tenant's customer industry list (active only unless includeInactive). */
export const listIndustries = (includeInactive = false) => ApiService.getIndustries(includeInactive);

/** FR-1.6: add an industry (admin). */
export const createIndustry = (data) => ApiService.createIndustry(data);

/** FR-1.6: rename or (de)activate an industry (admin). */
export const updateIndustry = (code, data) => ApiService.updateIndustry(code, data);
