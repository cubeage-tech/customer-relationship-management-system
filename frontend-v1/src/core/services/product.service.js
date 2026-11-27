import ApiService from './api.service';

export const listProducts = () => ApiService.getProducts();

export const createProduct = (data) => ApiService.createProduct(data);

export const updateProduct = (id, data) => ApiService.updateProduct(id, data);

export const deactivateProduct = (id) => ApiService.deactivateProduct(id);

export const activateProduct = (id) => ApiService.activateProduct(id);
