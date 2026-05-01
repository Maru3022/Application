package com.healthlife.nutrition.repository;

import com.healthlife.nutrition.entity.Food;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, UUID> {
    Optional<Food> findByBarcode(String barcode);

    List<Food> findByNameContainingIgnoreCase(String name);

    List<Food> findByUserId(UUID userId);
}
