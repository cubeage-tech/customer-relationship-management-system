import ApiService from './api.service';

/** AC-4.2: company branding printed on quotation PDFs. */
export const getBranding = () => ApiService.getBranding();

export const updateBranding = (data) => ApiService.updateBranding(data);

export const uploadBrandingLogo = (file) => ApiService.uploadBrandingLogo(file);

export const removeBrandingLogo = () => ApiService.removeBrandingLogo();

/** Object URL for the current logo (caller revokes it), or null when there is none. */
export const getBrandingLogoUrl = () =>
  ApiService.getBrandingLogo()
    .then((res) => window.URL.createObjectURL(res.data))
    .catch(() => null);
