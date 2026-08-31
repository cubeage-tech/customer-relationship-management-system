package com.company.crm.quotation.service;

import com.company.crm.common.exception.ApiException;
import com.company.crm.quotation.dto.request.ProductReqDto;
import com.company.crm.quotation.dto.response.ProductResDto;
import com.company.crm.quotation.entity.Product;
import com.company.crm.quotation.mapper.ProductMapper;
import com.company.crm.quotation.repository.ProductRepository;
import com.company.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** FR-4.2: the product/service price list used to populate quotation line items. */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @Transactional(readOnly = true)
    public List<ProductResDto> listProducts(User currentUser) {
        return productRepository.findByTenantId(requireTenantId(currentUser)).stream()
                .map(productMapper::toDto)
                .toList();
    }

    @Transactional
    public ProductResDto createProduct(User currentUser, ProductReqDto dto) {
        Product product = new Product();
        product.setTenant(currentUser.getTenant());
        applyFields(product, dto);
        return productMapper.toDto(productRepository.save(product));
    }

    @Transactional
    public ProductResDto updateProduct(User currentUser, Long productId, ProductReqDto dto) {
        Product product = findProduct(currentUser, productId);
        applyFields(product, dto);
        return productMapper.toDto(productRepository.save(product));
    }

    @Transactional
    public ProductResDto setActive(User currentUser, Long productId, boolean active) {
        Product product = findProduct(currentUser, productId);
        product.setActive(active);
        return productMapper.toDto(productRepository.save(product));
    }

    private void applyFields(Product product, ProductReqDto dto) {
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setUnitPrice(dto.getUnitPrice());
    }

    private Product findProduct(User currentUser, Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (!product.getTenant().getId().equals(requireTenantId(currentUser))) {
            throw ApiException.notFound("Product not found");
        }
        return product;
    }

    private Long requireTenantId(User currentUser) {
        if (currentUser.getTenant() == null) {
            throw ApiException.forbidden("Products are scoped to a tenant");
        }
        return currentUser.getTenant().getId();
    }
}
