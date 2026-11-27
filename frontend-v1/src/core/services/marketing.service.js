import ApiService from './api.service';

/** FR-7.2: customer segments. */
export const listSegments = () => ApiService.getSegments();
export const getSegmentCustomers = (id) => ApiService.getSegmentCustomers(id);
export const createSegment = (data) => ApiService.createSegment(data);
export const updateSegment = (id, data) => ApiService.updateSegment(id, data);
export const deleteSegment = (id) => ApiService.deleteSegment(id);

/** FR-7.3: promotions and redemptions. */
export const listPromotions = () => ApiService.getPromotions();
export const createPromotion = (data) => ApiService.createPromotion(data);
export const updatePromotion = (id, data) => ApiService.updatePromotion(id, data);
export const listPromotionRedemptions = (id) => ApiService.getPromotionRedemptions(id);
export const redeemPromotion = (id, data) => ApiService.redeemPromotion(id, data);
export const listCustomerPromotions = (customerId) => ApiService.getCustomerPromotions(customerId);
