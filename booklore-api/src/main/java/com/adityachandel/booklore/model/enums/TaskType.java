package com.adityachandel.booklore.model.enums;

import lombok.Getter;

public enum TaskType {

    CLEAR_CBX_CACHE(false, false),
    CLEAR_PDF_CACHE(false, false),
    RE_SCAN_LIBRARY(false, true),
    REFRESH_METADATA(false, true);

    @Getter
    private final boolean parallel;

    @Getter
    private final boolean async;

    TaskType(boolean parallel, boolean async) {
        this.parallel = parallel;
        this.async = async;
    }
}