package com.adityachandel.booklore.model.dto.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserSettingKeyTest {

    @Test
    void customThemes_hasCorrectDbKey() {
        assertThat(UserSettingKey.CUSTOM_THEMES.getDbKey()).isEqualTo("customThemes");
    }

    @Test
    void customThemes_isJsonType() {
        assertThat(UserSettingKey.CUSTOM_THEMES.isJson()).isTrue();
    }

    @Test
    void fromDbKey_customThemes_returnsCorrectKey() {
        UserSettingKey key = UserSettingKey.fromDbKey("customThemes");
        assertThat(key).isEqualTo(UserSettingKey.CUSTOM_THEMES);
    }

    @Test
    void fromDbKey_unknownKey_throwsException() {
        assertThatThrownBy(() -> UserSettingKey.fromDbKey("unknownKey"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown setting key: unknownKey");
    }

    @Test
    void toString_customThemes_returnsDbKey() {
        assertThat(UserSettingKey.CUSTOM_THEMES.toString()).isEqualTo("customThemes");
    }

    @Test
    void allSettingKeys_haveNonNullDbKey() {
        for (UserSettingKey key : UserSettingKey.values()) {
            assertThat(key.getDbKey()).isNotNull();
            assertThat(key.getDbKey()).isNotEmpty();
        }
    }
}
