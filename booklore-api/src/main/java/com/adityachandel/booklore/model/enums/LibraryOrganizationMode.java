package com.adityachandel.booklore.model.enums;

/**
 * Defines how a library organizes book files into folders.
 * This affects how files are grouped together as a single book.
 */
public enum LibraryOrganizationMode {

    /**
     * Each book has its own dedicated folder.
     * All files within a folder are treated as formats of the same book.
     * Simple and deterministic - no fuzzy matching needed.
     * <p>
     * Example:
     * <pre>
     * Library/
     * └── American Gods/
     *     ├── American Gods.epub
     *     ├── American Gods.m4b
     *     └── American Gods - 10th Anniversary.pdf
     * </pre>
     * All three files become one book.
     */
    BOOK_PER_FOLDER,

    /**
     * System automatically detects grouping using folder-centric fuzzy matching.
     * Uses folder name as reference, applies similarity matching for variations.
     * Handles series detection to keep numbered entries separate.
     * <p>
     * Use this when your library has mixed organization or you're unsure.
     */
    AUTO_DETECT
}
