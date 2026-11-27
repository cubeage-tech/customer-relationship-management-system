import ApiService from './api.service';

/** FR-3.2: the tenant's sales stages in pipeline order (active only unless includeInactive). */
export const listPipelineStages = (includeInactive = false) => ApiService.getPipelineStages(includeInactive);

/** FR-3.2: add an open stage (admin). */
export const createPipelineStage = (data) => ApiService.createPipelineStage(data);

/** FR-3.2: rename or (de)activate a stage (admin). */
export const updatePipelineStage = (code, data) => ApiService.updatePipelineStage(code, data);

/** FR-3.2: reorder the open stages (admin). */
export const reorderPipelineStages = (codes) => ApiService.reorderPipelineStages(codes);
