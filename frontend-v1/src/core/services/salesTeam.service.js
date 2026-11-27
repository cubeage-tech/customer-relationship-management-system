import ApiService from './api.service';

export const listSalesTeams = () => ApiService.getSalesTeams();

export const createSalesTeam = ({ name, managerId }) => ApiService.createSalesTeam({ name, managerId });

export const updateSalesTeam = (id, { name, managerId }) => ApiService.updateSalesTeam(id, { name, managerId });

export const setSalesTeamMembers = (id, userIds) => ApiService.setSalesTeamMembers(id, { userIds });

export const deleteSalesTeam = (id) => ApiService.deleteSalesTeam(id);
