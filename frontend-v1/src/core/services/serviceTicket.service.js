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
