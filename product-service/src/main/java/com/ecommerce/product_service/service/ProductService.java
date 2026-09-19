package com.ecommerce.product_service.service;

import com.ecommerce.product_service.dto.ProductRequestDTO;
import com.ecommerce.product_service.dto.ProductResponseDTO;

import java.util.List;

public interface ProductService {
    ProductResponseDTO createProduct(ProductRequestDTO requestDTO);
    List<ProductResponseDTO> getAllsProducts();

    ProductResponseDTO updateProduct(String id, ProductRequestDTO requestDTO);
    ProductResponseDTO getProductById(String id);
    void deleteProduct(String id);

}
