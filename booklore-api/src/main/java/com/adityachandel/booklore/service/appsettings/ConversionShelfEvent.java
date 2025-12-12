package com.adityachandel.booklore.service.appsettings;

import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ConversionShelfEvent extends ApplicationEvent {
    private final AppSettingKey key;
    private final Object val;

    public ConversionShelfEvent(Object source, AppSettingKey key, Object val) {
        super(source);
        this.key = key;
        this.val = val;
    }
}

