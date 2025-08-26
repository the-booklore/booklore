package com.adityachandel.booklore.service.migration;

import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import com.adityachandel.booklore.service.appsettings.SettingPersistenceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataProviderMigrationService implements ApplicationRunner {
    
    private final SettingPersistenceHelper settingPersistenceHelper;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        migrateMetadataProviderSettings();
        migrateQuickBookMatchSettings();
    }
    
    private void migrateMetadataProviderSettings() {
        try {
            // Try to trigger settings loading which will handle migration via the enhanced error handling
            settingPersistenceHelper.getJsonSetting(
                settingPersistenceHelper.appSettingsRepository.findAll().stream()
                    .collect(java.util.stream.Collectors.toMap(entity -> entity.getName(), entity -> entity.getVal())),
                AppSettingKey.METADATA_PROVIDER_SETTINGS,
                com.adityachandel.booklore.model.dto.settings.MetadataProviderSettings.class,
                settingPersistenceHelper.getDefaultMetadataProviderSettings(),
                true
            );
            log.info("Metadata provider settings migration completed successfully");
        } catch (Exception e) {
            log.warn("Metadata provider settings migration encountered an issue: {}", e.getMessage());
            // Force reset by deleting the corrupted setting
            var corruptedSetting = settingPersistenceHelper.appSettingsRepository.findByName(AppSettingKey.METADATA_PROVIDER_SETTINGS.toString());
            if (corruptedSetting != null) {
                settingPersistenceHelper.appSettingsRepository.delete(corruptedSetting);
                log.info("Deleted corrupted metadata provider settings - will be recreated with defaults");
            }
        }
    }
    
    private void migrateQuickBookMatchSettings() {
        try {
            // Try to trigger settings loading which will handle migration via the enhanced error handling
            settingPersistenceHelper.getJsonSetting(
                settingPersistenceHelper.appSettingsRepository.findAll().stream()
                    .collect(java.util.stream.Collectors.toMap(entity -> entity.getName(), entity -> entity.getVal())),
                AppSettingKey.QUICK_BOOK_MATCH,
                com.adityachandel.booklore.model.dto.request.MetadataRefreshOptions.class,
                settingPersistenceHelper.getDefaultMetadataRefreshOptions(),
                true
            );
            log.info("Quick book match settings migration completed successfully");
        } catch (Exception e) {
            log.warn("Quick book match settings migration encountered an issue: {}", e.getMessage());
            // Force reset by deleting the corrupted setting
            var corruptedSetting = settingPersistenceHelper.appSettingsRepository.findByName(AppSettingKey.QUICK_BOOK_MATCH.toString());
            if (corruptedSetting != null) {
                settingPersistenceHelper.appSettingsRepository.delete(corruptedSetting);
                log.info("Deleted corrupted quick book match settings - will be recreated with defaults");
            }
        }
    }
}
