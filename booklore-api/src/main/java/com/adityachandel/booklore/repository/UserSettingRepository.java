package com.adityachandel.booklore.repository;

import com.adityachandel.booklore.model.entity.UserSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSettingRepository extends JpaRepository<UserSettingEntity, Long> {
    long countBySettingKeyAndSettingValue(String settingKey, String settingValue);
}
