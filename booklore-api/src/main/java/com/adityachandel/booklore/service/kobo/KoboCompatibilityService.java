package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.model.dto.settings.KoboSettings;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.enums.BookFileType;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KoboCompatibilityService {

    private final AppSettingService appSettingService;

    public boolean isBookSupportedForKobo(BookEntity book) {
        if (book == null) {
            throw new IllegalArgumentException("Book cannot be null");
        }
        
        BookFileType bookType = book.getBookType();
        if (bookType == null) {
            return false;
        }
        
        if (bookType == BookFileType.EPUB) {
            return true;
        }
        
        if (bookType == BookFileType.CBX) {
            return isCbxConversionEnabled() && meetsCbxConversionSizeLimit(book);
        }
        
        return false;
    }

    public boolean isCbxConversionEnabled() {
        try {
            KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
            return koboSettings != null && koboSettings.isConvertCbxToEpub();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean meetsCbxConversionSizeLimit(BookEntity book) {
        if (book == null || book.getBookType() != BookFileType.CBX) {
            return false;
        }
        
        try {
            KoboSettings koboSettings = appSettingService.getAppSettings().getKoboSettings();
            if (koboSettings == null) {
                return false;
            }
            
            long fileSizeKb = book.getFileSizeKb() != null ? book.getFileSizeKb() : 0;
            long limitKb = (long) koboSettings.getConversionLimitInMbForCbx() * 1024;
            
            return fileSizeKb <= limitKb;
        } catch (Exception e) {
            return false;
        }
    }
}