package com.adityachandel.booklore.mapper.custom;

import com.adityachandel.booklore.mapper.LibraryMapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.UserSettingEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class BookLoreUserTransformerTest {

    private BookLoreUserTransformer transformer;

    @Mock
    private LibraryMapper libraryMapper;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        transformer = new BookLoreUserTransformer(objectMapper, libraryMapper);
    }

    @Test
    void toDTO_withCustomThemes_deserializesCorrectly() {
        String customThemesJson = """
            [
                {
                    "id": "custom-123",
                    "name": "my-theme",
                    "label": "My Theme",
                    "light": {"fg": "#000000", "bg": "#ffffff", "link": "#0066cc"},
                    "dark": {"fg": "#e0e0e0", "bg": "#222222", "link": "#77bbee"}
                }
            ]
            """;

        BookLoreUserEntity userEntity = createUserEntityWithSettings(
                createSetting("customThemes", customThemesJson)
        );

        BookLoreUser user = transformer.toDTO(userEntity);

        assertThat(user.getUserSettings().getCustomThemes()).hasSize(1);
        BookLoreUser.UserSettings.CustomTheme theme = user.getUserSettings().getCustomThemes().getFirst();
        assertThat(theme.getId()).isEqualTo("custom-123");
        assertThat(theme.getName()).isEqualTo("my-theme");
        assertThat(theme.getLabel()).isEqualTo("My Theme");
        assertThat(theme.getLight().getFg()).isEqualTo("#000000");
        assertThat(theme.getLight().getBg()).isEqualTo("#ffffff");
        assertThat(theme.getLight().getLink()).isEqualTo("#0066cc");
        assertThat(theme.getDark().getFg()).isEqualTo("#e0e0e0");
        assertThat(theme.getDark().getBg()).isEqualTo("#222222");
        assertThat(theme.getDark().getLink()).isEqualTo("#77bbee");
    }

    @Test
    void toDTO_withEmptyCustomThemes_returnsEmptyList() {
        String customThemesJson = "[]";

        BookLoreUserEntity userEntity = createUserEntityWithSettings(
                createSetting("customThemes", customThemesJson)
        );

        BookLoreUser user = transformer.toDTO(userEntity);

        assertThat(user.getUserSettings().getCustomThemes()).isEmpty();
    }

    @Test
    void toDTO_withMultipleCustomThemes_deserializesAll() {
        String customThemesJson = """
            [
                {"id": "custom-1", "name": "theme-1", "label": "Theme 1", "light": {"fg": "#000", "bg": "#fff", "link": "#00f"}, "dark": {"fg": "#fff", "bg": "#000", "link": "#0ff"}},
                {"id": "custom-2", "name": "theme-2", "label": "Theme 2", "light": {"fg": "#111", "bg": "#eee", "link": "#00f"}, "dark": {"fg": "#eee", "bg": "#111", "link": "#0ff"}}
            ]
            """;

        BookLoreUserEntity userEntity = createUserEntityWithSettings(
                createSetting("customThemes", customThemesJson)
        );

        BookLoreUser user = transformer.toDTO(userEntity);

        assertThat(user.getUserSettings().getCustomThemes()).hasSize(2);
        assertThat(user.getUserSettings().getCustomThemes().get(0).getId()).isEqualTo("custom-1");
        assertThat(user.getUserSettings().getCustomThemes().get(1).getId()).isEqualTo("custom-2");
    }

    @Test
    void toDTO_withNoCustomThemesSetting_returnsNullCustomThemes() {
        BookLoreUserEntity userEntity = createUserEntityWithSettings();

        BookLoreUser user = transformer.toDTO(userEntity);

        assertThat(user.getUserSettings().getCustomThemes()).isNull();
    }

    @Test
    void toDTO_withInvalidCustomThemesJson_handlesGracefully() {
        String invalidJson = "not valid json";

        BookLoreUserEntity userEntity = createUserEntityWithSettings(
                createSetting("customThemes", invalidJson)
        );

        BookLoreUser user = transformer.toDTO(userEntity);

        assertThat(user.getUserSettings().getCustomThemes()).isNull();
    }

    @Test
    void toDTO_withUnknownSettingKey_handlesGracefully() {
        BookLoreUserEntity userEntity = createUserEntityWithSettings(
                createSetting("unknownKey", "someValue")
        );

        BookLoreUser user = transformer.toDTO(userEntity);
        assertThat(user).isNotNull();
    }

    private BookLoreUserEntity createUserEntityWithSettings(UserSettingEntity... settings) {
        BookLoreUserEntity userEntity = new BookLoreUserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("testuser");
        userEntity.setName("Test User");

        Set<UserSettingEntity> settingSet = new HashSet<>(List.of(settings));
        userEntity.setSettings(settingSet);

        return userEntity;
    }

    private UserSettingEntity createSetting(String key, String value) {
        UserSettingEntity setting = new UserSettingEntity();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
