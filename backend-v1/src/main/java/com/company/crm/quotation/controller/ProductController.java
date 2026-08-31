package com.company.crm.quotation.controller;

import com.company.crm.common.response.Response;
import com.company.crm.quotation.dto.request.ProductReqDto;
import com.company.crm.quotation.dto.response.ProductResDto;
import com.company.crm.quotation.service.ProductService;
import com.company.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** FR-4.2: product/service price list. Viewable by anyone who can build a quotation; managed by admin/sales_manager. */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER', 'SALES_EXECUTIVE')")
    public Response<List<ProductResDto>> listProducts(@AuthenticationPrincipal User currentUser) {
        return Response.ok(productService.listProducts(currentUser));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<ProductResDto> createProduct(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ProductReqDto dto) {
        return Response.ok("Product created", productService.createProduct(currentUser, dto));
    }

    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<ProductResDto> updateProduct(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long productId,
            @Valid @RequestBody ProductReqDto dto) {
        return Response.ok("Product updated", productService.updateProduct(currentUser, productId, dto));
    }

    @PatchMapping("/{productId}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<ProductResDto> deactivateProduct(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long productId) {
        return Response.ok("Product deactivated", productService.setActive(currentUser, productId, false));
    }

    @PatchMapping("/{productId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SALES_MANAGER')")
    public Response<ProductResDto> activateProduct(
            @AuthenticationPrincipal User currentUser,
            @PathVariable Long productId) {
        return Response.ok("Product activated", productService.setActive(currentUser, productId, true));
    }
}
