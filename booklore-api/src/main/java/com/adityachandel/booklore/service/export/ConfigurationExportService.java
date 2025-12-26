package com.adityachandel.booklore.service.export;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Sort;
import com.adityachandel.booklore.model.dto.export.ConfigurationExportDTO;
import com.adityachandel.booklore.model.dto.export.ConfigurationExportDTO.*;
import com.adityachandel.booklore.model.dto.export.ImportOptions;
import com.adityachandel.booklore.model.entity.*;
import com.adityachandel.booklore.model.enums.IconType;
import com.adityachandel.booklore.model.enums.SortDirection;
import com.adityachandel.booklore.repository.MagicShelfRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.repository.UserSettingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationExportService {

    private final ShelfRepository shelfRepository;
    private final MagicShelfRepository magicShelfRepository;
    private final UserSettingRepository userSettingRepository;
    private final UserRepository userRepository;
    private final AuthenticationService authenticationService;
    private final ObjectMapper objectMapper;

    /**
     * Export user configuration as JSON string
     */
    public String exportConfiguration() {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        // Export shelves
        List<ShelfExportDTO> shelves = shelfRepository.findByUserId(userId)
                .stream()
                .map(this::mapToShelfExport)
                .collect(Collectors.toList());

        // Export magic shelves
        List<MagicShelfExportDTO> magicShelves = magicShelfRepository.findAllByUserId(userId)
                .stream()
                .map(this::mapToMagicShelfExport)
                .collect(Collectors.toList());

        // Export user settings (filtered)
        List<SettingExportDTO> settings = userSettingRepository.findByUserId(userId)
                .stream()
                .filter(this::isExportableSetting)
                .map(this::mapToSettingExport)
                .collect(Collectors.toList());

        ConfigurationExportDTO export = ConfigurationExportDTO.builder()
                .version("1.0")
                .exportedAt(Instant.now())
                .username(user.getUsername())
                .shelves(shelves)
                .magicShelves(magicShelves)
                .settings(settings)
                .build();

        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize configuration", e);
            throw ApiError.INTERNAL_SERVER_ERROR.createException("Failed to export configuration");
        }
    }

    /**
     * Import configuration from JSON string
     */
    @Transactional
    public void importConfiguration(String jsonConfig, ImportOptions options) {
        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Long userId = user.getId();

        try {
            ConfigurationExportDTO importData = objectMapper.readValue(jsonConfig, ConfigurationExportDTO.class);

            // Validate version
            if (!importData.getVersion().equals("1.0")) {
                throw ApiError.INVALID_INPUT.createException("Unsupported configuration version: " + importData.getVersion());
            }

            // Import based on options
            if (options.isImportShelves()) {
                importShelves(importData.getShelves(), userId, options);
            }

            if (options.isImportMagicShelves()) {
                importMagicShelves(importData.getMagicShelves(), userId, options);
            }

            if (options.isImportSettings()) {
                importSettings(importData.getSettings(), userId, options);
            }

            log.info("Configuration imported successfully for user: {}", userId);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse configuration JSON", e);
            throw ApiError.INVALID_INPUT.createException("Invalid configuration format");
        }
    }

    private void importShelves(List<ShelfExportDTO> shelves, Long userId, ImportOptions options) {
        if (shelves == null) return;

        BookLoreUserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        for (ShelfExportDTO shelfDto : shelves) {
            // Check if shelf exists
            ShelfEntity existing = shelfRepository.findByUserIdAndName(userId, shelfDto.getName()).orElse(null);

            if (existing != null) {
                if (options.isSkipExisting()) {
                    continue;
                }
                if (options.isOverwrite()) {
                    // Update existing shelf (don't modify book mappings)
                    existing.setIcon(shelfDto.getIcon());
                    if (shelfDto.getIconType() != null) {
                        existing.setIconType(IconType.valueOf(shelfDto.getIconType()));
                    }
                    if (shelfDto.getSort() != null) {
                        existing.setSort(mapToSort(shelfDto.getSort()));
                    }
                    shelfRepository.save(existing);
                }
            } else {
                // Create new shelf
                ShelfEntity newShelf = ShelfEntity.builder()
                        .user(userEntity)
                        .name(shelfDto.getName())
                        .icon(shelfDto.getIcon())
                        .iconType(shelfDto.getIconType() != null ? IconType.valueOf(shelfDto.getIconType()) : IconType.PRIME_NG)
                        .sort(shelfDto.getSort() != null ? mapToSort(shelfDto.getSort()) : null)
                        .build();

                shelfRepository.save(newShelf);
            }
        }
    }

    private void importMagicShelves(List<MagicShelfExportDTO> magicShelves, Long userId, ImportOptions options) {
        if (magicShelves == null) return;

        for (MagicShelfExportDTO msDto : magicShelves) {
            MagicShelfEntity existing = magicShelfRepository.findByUserIdAndName(userId, msDto.getName()).orElse(null);

            if (existing != null) {
                if (options.isSkipExisting()) {
                    continue;
                }
                if (options.isOverwrite()) {
                    existing.setIcon(msDto.getIcon());
                    if (msDto.getIconType() != null) {
                        existing.setIconType(IconType.valueOf(msDto.getIconType()));
                    }
                    existing.setFilterJson(msDto.getFilterJson());
                    existing.setPublic(msDto.isPublic());
                    magicShelfRepository.save(existing);
                }
            } else {
                MagicShelfEntity newMs = MagicShelfEntity.builder()
                        .userId(userId)
                        .name(msDto.getName())
                        .icon(msDto.getIcon())
                        .iconType(msDto.getIconType() != null ? IconType.valueOf(msDto.getIconType()) : IconType.PRIME_NG)
                        .filterJson(msDto.getFilterJson())
                        .isPublic(msDto.isPublic())
                        .build();

                magicShelfRepository.save(newMs);
            }
        }
    }

    private void importSettings(List<SettingExportDTO> settings, Long userId, ImportOptions options) {
        if (settings == null) return;

        BookLoreUserEntity userEntity = userRepository.findById(userId)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(userId));

        for (SettingExportDTO setting : settings) {
            if (!isImportableSetting(setting.getKey())) {
                continue;
            }

            UserSettingEntity existing = userSettingRepository.findByUserIdAndSettingKey(userId, setting.getKey()).orElse(null);

            if (existing != null) {
                if (options.isOverwrite()) {
                    existing.setSettingValue(setting.getValue());
                    userSettingRepository.save(existing);
                }
            } else {
                UserSettingEntity newSetting = UserSettingEntity.builder()
                        .user(userEntity)
                        .settingKey(setting.getKey())
                        .settingValue(setting.getValue())
                        .build();

                userSettingRepository.save(newSetting);
            }
        }
    }

    // Mapping helper methods

    private ShelfExportDTO mapToShelfExport(ShelfEntity entity) {
        return ShelfExportDTO.builder()
                .name(entity.getName())
                .icon(entity.getIcon())
                .iconType(entity.getIconType().name())
                .sort(entity.getSort() != null ? mapToSortDTO(entity.getSort()) : null)
                .books(entity.getBookEntities() != null ? entity.getBookEntities().stream()
                        .map(book -> book.getId() + ":" + book.getFileName())
                        .collect(Collectors.toList()) : List.of())
                .build();
    }

    private MagicShelfExportDTO mapToMagicShelfExport(MagicShelfEntity entity) {
        return MagicShelfExportDTO.builder()
                .name(entity.getName())
                .icon(entity.getIcon())
                .iconType(entity.getIconType().name())
                .isPublic(entity.isPublic())
                .filterJson(entity.getFilterJson())
                .build();
    }

    private SettingExportDTO mapToSettingExport(UserSettingEntity entity) {
        return SettingExportDTO.builder()
                .key(entity.getSettingKey())
                .value(entity.getSettingValue())
                .build();
    }

    private SortDTO mapToSortDTO(Sort sort) {
        if (sort == null) return null;
        return SortDTO.builder()
                .field(sort.getField())
                .direction(sort.getDirection() != null ? sort.getDirection().name() : null)
                .build();
    }

    private Sort mapToSort(SortDTO dto) {
        if (dto == null) return null;
        Sort sort = new Sort();
        sort.setField(dto.getField());
        if (dto.getDirection() != null) {
            sort.setDirection(SortDirection.valueOf(dto.getDirection()));
        }
        return sort;
    }

    /**
     * Settings that should NOT be exported (sensitive, system, etc.)
     */
    private boolean isExportableSetting(UserSettingEntity setting) {
        String key = setting.getSettingKey();
        // Skip sensitive settings
        if (key.startsWith("password") || key.startsWith("token") || key.startsWith("secret")) {
            return false;
        }
        // Skip system settings
        if (key.startsWith("system.") || key.startsWith("internal.")) {
            return false;
        }
        return true;
    }

    private boolean isImportableSetting(String key) {
        return !key.startsWith("system.") && !key.startsWith("internal.");
    }
}
