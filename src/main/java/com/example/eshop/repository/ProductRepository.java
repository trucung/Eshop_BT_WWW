package com.example.eshop.repository;

import com.example.eshop.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsByCategoryId(Long categoryId);

    /** Tìm theo tên (không phân biệt hoa thường) và/hoặc theo danh mục; tham số null = bỏ qua điều kiện. */
    @Query("""
           SELECT p FROM Product p
           WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
             AND (:categoryId IS NULL OR p.category.id = :categoryId)
           """)
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("categoryId") Long categoryId,
                         Pageable pageable);
}


