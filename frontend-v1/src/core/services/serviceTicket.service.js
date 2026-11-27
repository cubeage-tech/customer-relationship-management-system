import ApiService from './api.service';

export const listTickets = (params) => ApiService.getTickets(params);

export const getTicketSummary = () => ApiService.getTicketSummary();

export const getTicket = (id) => ApiService.getTicket(id);

export const createTicket = (data) => ApiService.createTicket(data);

export const updateTicket = (id, data) => ApiService.updateTicket(id, data);

export const assignTicket = (id, technicianId) => ApiService.assignTicket(id, { technicianId });

export const changeTicketStatus = (id, status) => ApiService.changeTicketStatus(id, { status });

export const recordTicketFeedback = (id, score, comment) =>
  ApiService.recordTicketFeedback(id, { score, comment });

/** Service agent takes an unassigned ticket. Rejects with 409 if another agent got it first. */
export const claimTicket = (id) => ApiService.claimTicket(id);

/** Agent ticket list, paged server-side. scope: 'queue' (unassigned) | 'mine'. Resolves to a page envelope. */
export const listAgentTickets = ({ scope, status, priority, search, page, size }) =>
  ApiService.getTickets({ scope, status, priority, search, page, size });
