package com.adityachandel.booklore.model.enums;

import lombok.Getter;

public enum TaskType {

    CLEAR_CBX_CACHE(
            false,
            false,
            true,
            false,
            "Clear CBX Cache",
            "Clears temporarily extracted comic book files used by the reader."
    ),
    CLEAR_PDF_CACHE(
            false,
            false,
            true,
            false,
            "Clear PDF Cache",
            "Clears temporarily generated images used by the streaming PDF reader."
    ),
    REFRESH_LIBRARY_METADATA(
            false,
            true,
            false,
            false,
            "Refresh Metadata",
            "Re-reads book information (title, author, cover, etc.) from your files and updates the Booklore database."
    ),
    UPDATE_BOOK_RECOMMENDATIONS(
            false,
            true,
            true,
            false,
            "Update Book Recommendations",
            "Analyzes your library to generate personalized book recommendations based on the books you own."
    ),
    CLEANUP_DELETED_BOOKS(
            false,
            false,
            true,
            false,
            "Cleanup Deleted Books",
            "Permanently removes database entries for books you previously deleted from your libraries."
    ),
    SYNC_LIBRARY_FILES(
            false,
            false,
            true,
            false,
            "Sync Library Files",
            "Scans your library folders to detect new books and removes entries for files that no longer exist."
    ),
    CLEANUP_TEMP_METADATA(
            false,
            false,
            true,
            false,
            "Cleanup Temporary Metadata",
            "Removes temporary metadata files created during the bookdrop and manual metadata review processes."
    ),
    REFRESH_METADATA_MANUAL(
            false,
            true,
            false,
            true,
            "Refresh Metadata",
            "Updates metadata information for your selected books."
    );

    @Getter
    private final boolean parallel;

    @Getter
    private final boolean async;

    @Getter
    private final boolean cronSupported;

    @Getter
    private final boolean hiddenFromUI;

    @Getter
    private final String name;

    @Getter
    private final String description;

    TaskType(boolean parallel, boolean async, boolean cronSupported, boolean hiddenFromUI, String name, String description) {
        this.parallel = parallel;
        this.async = async;
        this.cronSupported = cronSupported;
        this.hiddenFromUI = hiddenFromUI;
        this.name = name;
        this.description = description;
    }
}