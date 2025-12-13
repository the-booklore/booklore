package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Shelf;
import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.model.enums.ShelfType;
import com.adityachandel.booklore.service.ShelfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.Optional;

import static org.mockito.Mockito.*;

class ConversionShelfEventListenerTest {

    private ApplicationContext applicationContext;
    private AuthenticationService authenticationService;
    private ShelfService shelfService;
    private ConversionShelfEventListener listener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        applicationContext = mock(ApplicationContext.class);
        authenticationService = mock(AuthenticationService.class);
        shelfService = mock(ShelfService.class);

        when(applicationContext.getBean(AuthenticationService.class)).thenReturn(authenticationService);
        when(applicationContext.getBean(ShelfService.class)).thenReturn(shelfService);

        listener = new ConversionShelfEventListener(applicationContext);
    }

    @Test
    void whenPersistConversionTrue_andShelfMissing_shouldCreateShelf() {
        LinkedHashMap<String, Object> kobo = new LinkedHashMap<>();
        kobo.put("persistConversion", true);

        BookLoreUser user = BookLoreUser.builder().id(10L).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);
        when(shelfService.getShelf(user.getId(), ShelfType.CONVERSION.getName())).thenReturn(Optional.empty());

        ConversionShelfEvent event = new ConversionShelfEvent(this, AppSettingKey.KOBO_SETTINGS, kobo);
        listener.onConversionShelfEvent(event);
        verify(shelfService).createShelf(any());
    }

    @Test
    void whenPersistConversionFalse_andShelfExists_shouldDeleteShelf() {
        LinkedHashMap<String, Object> kobo = new LinkedHashMap<>();
        kobo.put("persistConversion", false);

        com.adityachandel.booklore.model.dto.Shelf conversionShelf = com.adityachandel.booklore.model.dto.Shelf.builder().id(55L).build();
        when(shelfService.getUserKoboConversionShelf()).thenReturn(conversionShelf);

        ConversionShelfEvent event = new ConversionShelfEvent(this, AppSettingKey.KOBO_SETTINGS, kobo);

        listener.onConversionShelfEvent(event);

        verify(shelfService).deleteShelf(55L);
    }

    @Test
    void whenPersistConversionMissing_shouldNotCrashOrAct() {
        LinkedHashMap<String, Object> kobo = new LinkedHashMap<>();
        // deliberately do NOT put persistConversion key

        // make auth service return a user to ensure no NPE
        BookLoreUser user = BookLoreUser.builder().id(10L).build();
        when(authenticationService.getAuthenticatedUser()).thenReturn(user);

        ConversionShelfEvent event = new ConversionShelfEvent(this, AppSettingKey.KOBO_SETTINGS, kobo);
        listener.onConversionShelfEvent(event);

        // ensure no shelf create/delete interactions
        verify(shelfService, never()).createShelf(any());
        verify(shelfService, never()).deleteShelf(anyLong());
    }

}
