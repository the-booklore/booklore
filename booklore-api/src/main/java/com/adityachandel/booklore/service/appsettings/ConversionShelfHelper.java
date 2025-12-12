package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversionShelfHelper {
    private final ApplicationEventPublisher eventPublisher;

    public void handleConversionShelf(AppSettingKey key, Object val) {
        eventPublisher.publishEvent(new ConversionShelfEvent(this, key, val));
    }
}
