package com.healthlife.nutrition.repository;

import com.healthlife.nutrition.entity.Food;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, UUID> {
    Optional<Food> findByBarcode(String barcode);

    List<Food> findByNameContainingIgnoreCase(String name);

    Page<Food> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Food> findByUserId(UUID userId);
}
