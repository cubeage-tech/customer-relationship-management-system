import ApiService from './api.service';

export const listUsers = () => ApiService.getUsers();

export const createUser = (payload) => ApiService.createUser(payload);
