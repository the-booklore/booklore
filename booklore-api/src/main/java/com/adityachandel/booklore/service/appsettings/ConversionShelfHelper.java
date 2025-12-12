package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.Shelf;
import com.adityachandel.booklore.model.dto.request.ShelfCreateRequest;
import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import com.adityachandel.booklore.model.dto.settings.KoboSettings;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.model.enums.IconType;
import com.adityachandel.booklore.model.enums.ShelfType;
import com.adityachandel.booklore.service.ShelfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversionShelfHelper {
    private final ShelfService shelfService;
    private final AuthenticationService authenticationService;

    public void ensureKoboConversionShelfExists() {
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

    public void removeKoboConversionShelfIfExists() {
        Shelf conversionShelf = shelfService.getUserKoboConversionShelf();
        if(conversionShelf != null) {
            shelfService.deleteShelf(conversionShelf.getId());
        }
    }


    public void handleConversionShelf(AppSettingKey key,Object val){
        if(key== AppSettingKey.KOBO_SETTINGS && val instanceof KoboSettings){
            boolean persistConversion = ((KoboSettings) val).isPersistConversion();
            if(persistConversion){
                this.ensureKoboConversionShelfExists();
            }else {
                this.removeKoboConversionShelfIfExists();
            }
        }
    }
}
