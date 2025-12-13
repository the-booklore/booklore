package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Shelf;
import com.adityachandel.booklore.model.dto.request.ShelfCreateRequest;
import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import com.adityachandel.booklore.model.dto.settings.KoboSettings;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.model.enums.IconType;
import com.adityachandel.booklore.model.enums.ShelfType;
import com.adityachandel.booklore.service.ShelfService;
import com.adityachandel.booklore.config.security.service.AuthenticationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConversionShelfEventListener {
    private final ApplicationContext applicationContext;

    @EventListener
    public void onConversionShelfEvent(ConversionShelfEvent event) {
        AppSettingKey key = event.getKey();
        Object val = event.getVal();
        if (key == AppSettingKey.KOBO_SETTINGS && val instanceof LinkedHashMap<?,?>) {
            Object raw = ((LinkedHashMap) val).get("persistConversion");
            boolean persistConversion = false;
            if (raw instanceof Boolean) {
                persistConversion = (Boolean) raw;
            } else if (raw != null) {
                persistConversion = Boolean.parseBoolean(String.valueOf(raw));
            }

            if (persistConversion) {
                ensureKoboConversionShelfExists();
            } else {
                removeKoboConversionShelfIfExists();
            }
        }
    }

    private void ensureKoboConversionShelfExists() {
        AuthenticationService authenticationService = applicationContext.getBean(AuthenticationService.class);
        ShelfService shelfService = applicationContext.getBean(ShelfService.class);

        BookLoreUser user = authenticationService.getAuthenticatedUser();
        Optional<ShelfEntity> shelf = shelfService.getShelf(user.getId(), ShelfType.CONVERSION.getName());
        if (shelf.isEmpty()) {
            shelfService.createShelf(
                    ShelfCreateRequest.builder()
                            .name(ShelfType.CONVERSION.getName())
                            .icon(ShelfType.CONVERSION.getIcon())
                            .iconType(IconType.PRIME_NG)
                            .build()
            );
        }
    }

    private void removeKoboConversionShelfIfExists() {
        ShelfService shelfService = applicationContext.getBean(ShelfService.class);
        Shelf conversionShelf = shelfService.getUserKoboConversionShelf();
        if (conversionShelf != null) {
            shelfService.deleteShelf(conversionShelf.getId());
        }
    }
}
