package com.adityachandel.booklore.service.user;

import com.adityachandel.booklore.model.dto.settings.UserSettingKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultUserSettingsProviderTest {

    private DefaultUserSettingsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new DefaultUserSettingsProvider();
        provider.init();
    }

    @Test
    void getDefaultValue_customThemes_returnsEmptyList() {
        Object defaultValue = provider.getDefaultValue(UserSettingKey.CUSTOM_THEMES);

        assertThat(defaultValue).isInstanceOf(List.class);
        assertThat((List<?>) defaultValue).isEmpty();
    }

    @Test
    void getAllKeys_containsCustomThemes() {
        Set<UserSettingKey> keys = provider.getAllKeys();

        assertThat(keys).contains(UserSettingKey.CUSTOM_THEMES);
    }

    @Test
    void getDefaultValue_allRegisteredKeys_returnNonNullSuppliers() {
        Set<UserSettingKey> keys = provider.getAllKeys();

        for (UserSettingKey key : keys) {
            provider.getDefaultValue(key);
        }
    }

    @Test
    void getDefaultValue_unregisteredKey_throwsException() {
        DefaultUserSettingsProvider uninitializedProvider = new DefaultUserSettingsProvider();

        assertThatThrownBy(() -> uninitializedProvider.getDefaultValue(UserSettingKey.CUSTOM_THEMES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No default value defined for key");
    }
}
