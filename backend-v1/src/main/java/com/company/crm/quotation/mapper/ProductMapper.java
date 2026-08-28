package com.company.crm.quotation.mapper;

import com.company.crm.quotation.dto.response.ProductResDto;
import com.company.crm.quotation.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class ProductMapper {

    public ProductResDto toDto(Product product) {
        return new ProductResDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getUnitPrice(),
                product.isActive()
        );
    }
}
