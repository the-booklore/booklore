# Transloco i18n Audit — Untranslated Components

**Generated:** 2026-02-09
**Last updated:** 2026-02-10
**Already translated:** 41 components (all settings tabs, login, dashboard settings, nav, settings dialogs, shared components, layout, dashboard, library creator, app root)

---

## ~~A. Root / App Component~~ DONE

Keys in `app` namespace.

| # | Component | Path | Strings |
|---|-----------|------|---------|
| ~~1~~ | ~~AppComponent~~ | ~~`app.component.html`~~ | ~~DONE~~ |

## B. features/book/

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 2 | AddPhysicalBookDialog | `features/book/components/add-physical-book-dialog/` | Dialog header, form labels (Library, Title, ISBN, Authors, Description, Publisher, etc.), placeholders, validation messages, buttons |
| 3 | AdditionalFileUploader | `features/book/components/additional-file-uploader/` | "Upload Additional File", status labels, drag-and-drop text, buttons |
| 4 | BookBrowser | `features/book/components/book-browser/` | "All Books", "Unshelved Books", "selected", tooltips, empty/error states, display settings labels, search placeholders |
| 5 | BookCard | `features/book/components/book-browser/book-card/` | alt text, format display |
| 6 | BookFilter | `features/book/components/book-browser/book-filter/` | "Filters", "Showing first 100 items", note text |
| 7 | BookTable | `features/book/components/book-browser/book-table/` | "Book Cover" alt, "Status:", "Locked"/"Unlocked" |
| 8 | LockUnlockMetadataDialog | `features/book/components/book-browser/lock-unlock-metadata-dialog/` | Dialog header, "selected", "Reset"/"Lock All"/"Unlock All"/"Save"/"Saving..." |
| 9 | MultiSortPopover | `features/book/components/book-browser/sorting/multi-sort-popover/` | "Sort Order", "Remove" tooltip, "Add sort field..." placeholder |
| 10 | BookFileAttacher | `features/book/components/book-file-attacher/` | "Attach File(s) to Another Book", field labels, warning messages, buttons |
| 11 | BookReviews | `features/book/components/book-reviews/` | Loading/empty states, action tooltips, "Show Spoiler" |
| 12 | BookSearcher | `features/book/components/book-searcher/` | Search placeholder, "No results found", "by" prefix, aria-labels |
| 13 | BookSender | `features/book/components/book-sender/` | "Send Book", form labels (From, To, Format, Subject), "Primary" badge, file size warning |
| 14 | SeriesPage | `features/book/components/series-page/` | "Series Details" tab, metadata labels, "Show less"/"Show more", loading/empty states, "selected", tooltips |
| 15 | ShelfAssigner | `features/book/components/shelf-assigner/` | "Assign Books to Shelves", empty state, buttons |
| 16 | ShelfCreator | `features/book/components/shelf-creator/` | "Create New Shelf", form labels, validation messages, buttons |
| 17 | ShelfEditDialog | `features/book/components/shelf-edit-dialog/` | "Edit Shelf", form labels, buttons |

## C. features/bookdrop/

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 18 | BookdropBulkEditDialog | `features/bookdrop/component/bookdrop-bulk-edit-dialog/` | Section headers, "Mode:" label, helper text, buttons |
| 19 | BookdropFileMetadataPicker | `features/bookdrop/component/bookdrop-file-metadata-picker/` | "File Data"/"Fetched Data", cover messages, tooltips |
| 20 | BookdropFileReview | `features/bookdrop/component/bookdrop-file-review/` | "Review Bookdrop Files", description, buttons, loading/empty states, pagination |
| 21 | BookdropFilesWidget | `features/bookdrop/component/bookdrop-files-widget/` | "Pending Bookdrop Files", "Last updated:", "Review" |
| 22 | BookdropFinalizeResultDialog | `features/bookdrop/component/bookdrop-finalize-result-dialog/` | "Import Summary", stat labels, "Processed at" |
| 23 | BookdropPatternExtractDialog | `features/bookdrop/component/bookdrop-pattern-extract-dialog/` | Description, "Pattern"/"Preview", "Available Placeholders", "Common Patterns", buttons |

## ~~D. features/dashboard/~~ DONE

Keys in `dashboard.main` and `dashboard.scroller` namespaces.

| # | Component | Path | Strings |
|---|-----------|------|---------|
| ~~24~~ | ~~DashboardScroller~~ | ~~`features/dashboard/components/dashboard-scroller/`~~ | ~~DONE~~ |
| ~~25~~ | ~~MainDashboard~~ | ~~`features/dashboard/components/main-dashboard/`~~ | ~~DONE~~ |

## ~~E. features/library-creator/~~ DONE

Keys in `libraryCreator.creator` and `libraryCreator.loading` namespaces.

| # | Component | Path | Strings |
|---|-----------|------|---------|
| ~~26~~ | ~~LibraryCreator~~ | ~~`features/library-creator/`~~ | ~~DONE~~ |
| ~~27~~ | ~~LibraryLoading~~ | ~~`features/library-creator/library-loading/`~~ | ~~DONE~~ |

## F. features/metadata/

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 28 | BookMetadataCenter | `features/metadata/component/book-metadata-center/` | "Book Details", tab labels ("Book Details", "Edit Metadata", "Search Metadata", "Sidecar") |
| 29 | BookReadingSessions | `features/metadata/component/book-metadata-center/book-reading-sessions/` | Loading/empty states, table headers (Session, Type, Duration, Progress, etc.) |
| 30 | MetadataEditor | `features/metadata/component/book-metadata-center/metadata-editor/` | Extensive field labels, section headers, button labels, tooltips |
| 31 | MetadataPicker | `features/metadata/component/book-metadata-center/metadata-picker/` | "Current"/"Fetched" columns, section headers, "N/A", buttons, tooltips |
| 32 | MetadataSearcher | `features/metadata/component/book-metadata-center/metadata-searcher/` | "Providers", form labels, "Search Results", status messages |
| 33 | MetadataTabs | `features/metadata/component/book-metadata-center/metadata-viewer/metadata-tabs/` | Tab labels, file section titles, "Download All", reader tooltips |
| 34 | MetadataViewer | `features/metadata/component/book-metadata-center/metadata-viewer/` | All metadata labels, "Synopsis", "Show less"/"Show more", badges, action buttons |
| 35 | SidecarViewer | `features/metadata/component/book-metadata-center/sidecar-viewer/` | "Sidecar Metadata", description, buttons, empty state |
| 36 | CoverSearch | `features/metadata/component/cover-search/` | Dialog header, form labels, loading/empty/results states, buttons |
| 37 | MetadataManager | `features/metadata/component/metadata-manager/` | "Metadata Manager", description, table headers, dialog content, action buttons |
| 38 | MetadataAdvancedFetchOptions | `features/metadata/component/metadata-options-dialog/metadata-advanced-fetch-options/` | Table headers, "Set All:", "Provider-Specific Fields", footer options |
| 39 | MetadataFetchOptions | `features/metadata/component/metadata-options-dialog/metadata-fetch-options/` | "Start Refresh" button |

## G. features/readers/audiobook-player/

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 40 | AudiobookPlayer | `features/readers/audiobook-player/` | "Loading audiobook...", "Untitled", "Unknown Author", tooltips, navigation, "Add Bookmark", "Sleep Timer", sidebar labels |

## H. features/readers/cbx-reader/

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 41 | CbxReader | `features/readers/cbx-reader/` | "Slideshow" badge, "Continue to Next Book", empty/loading states |
| 42 | CbxNoteDialog | `features/readers/cbx-reader/dialogs/cbx-note-dialog` | "Edit Note"/"Add Note", form labels, buttons |
| 43 | CbxShortcutsHelp | `features/readers/cbx-reader/dialogs/cbx-shortcuts-help` | "Keyboard Shortcuts", shortcut descriptions |
| 44 | CbxFooter | `features/readers/cbx-reader/layout/footer/` | Navigation labels, "Page", "Go", "of" |
| 45 | CbxHeader | `features/readers/cbx-reader/layout/header/` | "Contents", bookmark/note/slideshow/fullscreen/settings/close labels |
| 46 | CbxQuickSettings | `features/readers/cbx-reader/layout/quick-settings/` | "Fit Mode", "Scroll Mode", "Page View", layout labels, "Background" |
| 47 | CbxSidebar | `features/readers/cbx-reader/layout/sidebar/` | Tab labels, empty states, search placeholder |

## I. features/readers/ebook-reader/

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 48 | EbookReader | `features/readers/ebook-reader/` | "Loading book..." |
| 49 | EbookMetadataDialog | `features/readers/ebook-reader/dialogs/metadata-dialog` | "Book Information", section headers, all field labels, "Unknown", "reviews" |
| 50 | EbookNoteDialog | `features/readers/ebook-reader/dialogs/note-dialog` | "Edit Note"/"Add Note", form labels, buttons |
| 51 | EbookSettingsDialog | `features/readers/ebook-reader/dialogs/settings-dialog` | Tab labels, all settings labels (Theme, Typography, Layout sections) |
| 52 | EbookFooter | `features/readers/ebook-reader/layout/footer/` | Navigation labels, "Location", "Time Left...", "Page", "Section" |
| 53 | EbookHeader | `features/readers/ebook-reader/layout/header/header` | "Chapters", bookmark/search/notes/settings/close labels |
| 54 | EbookQuickSettings | `features/readers/ebook-reader/layout/header/quick-settings` | "Dark Mode", "Font Size", "Line Spacing", "More Settings" |
| 55 | EbookLeftSidebar | `features/readers/ebook-reader/layout/panel/` | Tab labels, search placeholder, empty/loading states |
| 56 | EbookSidebar | `features/readers/ebook-reader/layout/sidebar/` | Tab labels, empty states, aria-labels |
| 57 | EbookSelectionPopup | `features/readers/ebook-reader/shared/selection-popup` | "Copy Text", "Search in book", "Annotate", "Add Note", "Delete Annotation" |

## J. features/readers/pdf-reader/

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 58 | PdfReader | `features/readers/pdf-reader/` | "Close PDF Reader" title |

## K. features/stats/ — Library Stats

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 59 | LibraryStats | `features/stats/component/library-stats/` | "Library Statistics", "Select a library", summary labels, chart config header, size category labels, loading/empty states |
| 60 | BookFormatsChart | `features/stats/.../charts/book-formats-chart/` | Title, description, empty state |
| 61 | LanguageChart | `features/stats/.../charts/language-chart/` | Title, description, empty state |
| 62 | MetadataScoreChart | `features/stats/.../charts/metadata-score-chart/` | Title, description, empty state |
| 63 | PageCountChart | `features/stats/.../charts/page-count-chart/` | Title, description, empty state |
| 64 | PublicationTimelineChart | `features/stats/.../charts/publication-timeline-chart/` | Title, description, insight labels, empty state |
| 65 | PublicationTrendChart | `features/stats/.../charts/publication-trend-chart/` | Title, description, insight labels, empty state |
| 66 | ReadingJourneyChart | `features/stats/.../charts/reading-journey-chart/` | Title, description, insight labels, empty state |
| 67 | TopItemsChart | `features/stats/.../charts/top-items-chart/` | Title prefix, description, "shown" badge, empty state |
| 68 | AuthorUniverseChart | `features/stats/.../charts/author-universe-chart/` | Title, description, legend note, empty state |

## L. features/stats/ — User Stats

| # | Component | Path | Strings |
|---|-----------|------|---------|
| 69 | UserStats | `features/stats/component/user-stats/` | "'s Reading Statistics", dialog header, buttons |
| 70 | ReadingSessionHeatmap | `features/stats/.../charts/reading-session-heatmap/` | Title, description, navigation titles |
| 71 | ReadingSessionTimeline | `features/stats/.../charts/reading-session-timeline/` | Title, description, "Week" label, navigation, placeholders |
| 72 | GenreStatsChart | `features/stats/.../charts/genre-stats-chart/` | Title, description |
| 73 | CompletionTimelineChart | `features/stats/.../charts/completion-timeline-chart/` | Title, description, navigation titles |
| 74 | FavoriteDaysChart | `features/stats/.../charts/favorite-days-chart/` | Title, description, placeholders |
| 75 | PeakHoursChart | `features/stats/.../charts/peak-hours-chart/` | Title, description, placeholders |
| 76 | PersonalRatingChart | `features/stats/.../charts/personal-rating-chart/` | Title, description |
| 77 | ReadStatusChart | `features/stats/.../charts/read-status-chart/` | Title, description |
| 78 | ReadingDnaChart | `features/stats/.../charts/reading-dna-chart/` | Title, description, "Your Reading Personality Traits" |
| 79 | ReadingHabitsChart | `features/stats/.../charts/reading-habits-chart/` | Title, description, "Your Reading Habit Patterns" |
| 80 | ReadingHeatmapChart | `features/stats/.../charts/reading-heatmap-chart/` | Title, description |
| 81 | ReadingProgressChart | `features/stats/.../charts/reading-progress-chart/` | Title, description |
| 82 | RatingTasteChart | `features/stats/.../charts/rating-taste-chart/` | Title, description, quadrant labels, taste profile, empty state |
| 83 | ReadingBacklogChart | `features/stats/.../charts/reading-backlog-chart/` | Title, description, stat labels, insight labels, empty state |
| 84 | SeriesProgressChart | `features/stats/.../charts/series-progress-chart/` | Title, description, stat labels, filter/sort options, search placeholder, pagination, empty state |

## ~~M. features/settings/ — Untranslated Dialogs~~ DONE

All 4 settings dialogs have been translated:
- FontUploadDialog — keys in `settingsReader.fonts.upload`
- UserProfileDialog — keys in `settingsProfile`
- CreateEmailProviderDialog — keys in `settingsEmail.provider.create`
- CreateEmailRecipientDialog — keys in `settingsEmail.recipient.create`

## ~~N. shared/components/~~ DONE

Keys in `shared.bookUploader`, `shared.changePassword`, `shared.directoryPicker`, `shared.setup` namespaces.

| # | Component | Path | Strings |
|---|-----------|------|---------|
| ~~89~~ | ~~BookUploader~~ | ~~`shared/components/book-uploader/`~~ | ~~DONE~~ |
| ~~90~~ | ~~ChangePassword~~ | ~~`shared/components/change-password/`~~ | ~~DONE~~ |
| ~~91~~ | ~~DirectoryPicker~~ | ~~`shared/components/directory-picker/`~~ | ~~DONE~~ |
| ~~92~~ | ~~Setup~~ | ~~`shared/components/setup/`~~ | ~~DONE~~ |

## ~~O. shared/layout/~~ DONE

Keys in `layout.topbar`, `layout.menu`, `layout.changelog`, `layout.theme`, `layout.uploadDialog` namespaces.

| # | Component | Path | Strings |
|---|-----------|------|---------|
| ~~93~~ | ~~AppTopbar~~ | ~~`shared/layout/component/layout-topbar/`~~ | ~~DONE~~ |
| ~~94~~ | ~~AppMenu~~ | ~~`shared/layout/component/layout-menu/`~~ | ~~DONE~~ |
| ~~95~~ | ~~VersionChangelogDialog~~ | ~~`shared/layout/component/layout-menu/version-changelog-dialog/`~~ | ~~DONE~~ |
| ~~96~~ | ~~ThemeConfigurator~~ | ~~`shared/layout/component/theme-configurator/`~~ | ~~DONE~~ |
| ~~97~~ | ~~UploadDialog~~ | ~~`shared/layout/component/theme-configurator/upload-dialog/`~~ | ~~DONE~~ |

---

## Programmatic Strings (TS files needing translation)

Toast messages, confirmation dialogs, and dynamic labels in TypeScript files.

### Services

| # | File | Strings |
|---|------|---------|
| 1 | `shared/service/settings-helper.service.ts` | Toasts: "Settings Saved", "Error", save error detail |
| 2 | `features/book/service/book-menu.service.ts` | Confirmation dialogs for read status, age rating, content rating, unshelve, reset progress; toast summaries |
| 3 | `features/book/service/book.service.ts` | Toasts: delete/create/upload/attach success and failure messages |
| 4 | `features/book/service/library-shelf-menu.service.ts` | Confirmation dialogs for library/shelf operations; toast messages |
| 5 | `features/book/components/book-browser/table-column-preference.service.ts` | Column headers (Read, Title, Authors, etc.); "Preferences Saved" toast |
| 6 | `features/book/components/book-browser/cover-scale-preference.service.ts` | Toasts: "Cover Size Saved", "Save Failed" |
| 7 | `features/book/components/book-browser/filters/sidebar-filter-toggle-pref.service.ts` | Toast: "Save Failed" |
| 8 | `features/settings/task-management/task-helper.service.ts` | Toasts: task scheduled/running/failed messages |
| 9 | `features/settings/reader-preferences/reader-preferences.service.ts` | Toast: "Preferences Updated" |

### Components (TS-only strings)

| # | File | Strings |
|---|------|---------|
| ~~10~~ | ~~`shared/components/change-password/change-password.component.ts`~~ | ~~DONE~~ |
| 11 | `core/security/oidc-callback/oidc-callback.component.ts` | Toast: "OIDC Login Failed" |
| ~~12~~ | ~~`shared/components/book-uploader/book-uploader.component.ts`~~ | ~~DONE~~ |
| ~~13~~ | ~~`features/settings/custom-fonts/font-upload-dialog/font-upload-dialog.component.ts`~~ | ~~DONE~~ |
| 14 | `features/readers/cbx-reader/cbx-reader.component.ts` | Toast: error messages |
| ~~15~~ | ~~`features/settings/email-v2/create-email-provider-dialog/create-email-provider-dialog.component.ts`~~ | ~~DONE~~ |
| ~~16~~ | ~~`features/settings/email-v2/create-email-recipient-dialog/create-email-recipient-dialog.component.ts`~~ | ~~DONE~~ |
| ~~17~~ | ~~`features/library-creator/library-creator.component.ts`~~ | ~~DONE~~ |
| 18 | `features/book/components/book-browser/book-browser.component.ts` | Confirmation dialogs: deletion, cover operations |
| 19 | `features/book/components/book-browser/book-card/book-card.component.ts` | Confirmation dialogs: book/file deletion |
| 20 | `features/book/components/book-notes/book-notes-component.ts` | Confirmation dialog: "Confirm Deletion" |
| 21 | `features/book/components/book-reviews/book-reviews.component.ts` | Confirmation dialogs: delete all/single review |
| 22 | `features/book/components/series-page/series-page.component.ts` | Confirmation dialogs: deletion, cover operations |
| 23 | `features/bookdrop/component/bookdrop-file-review/bookdrop-file-review.component.ts` | Confirmation dialogs: reset/finalize/delete; dialog headers |
| 24 | `features/bookdrop/component/bookdrop-file-metadata-picker/bookdrop-file-metadata-picker.component.ts` | Confirmation dialog: "Reset Metadata Changes?" |
| 25 | `features/metadata/component/book-metadata-center/metadata-viewer/metadata-viewer.component.ts` | Confirmation dialogs: delete file, reset |
| 26 | `shared/components/live-notification-box/live-notification-box.component.ts` | Default message: "No recent notifications..." |
| 27 | `shared/components/metadata-progress-widget/metadata-progress-widget-component.ts` | Status messages: stalled, cancelled |
| ~~28~~ | ~~`features/settings/user-profile-dialog/user-profile-dialog.component.ts`~~ | ~~DONE~~ |
| ~~29~~ | ~~`shared/layout/component/layout-topbar/app.topbar.component.ts`~~ | ~~DONE~~ |
| ~~30~~ | ~~`shared/layout/component/layout-menu/app.menu.component.ts`~~ | ~~DONE~~ |

---

## Summary

| Category | Count |
|----------|-------|
| Already translated | 41 |
| **Untranslated HTML templates** | **79** |
| **Untranslated TS-only strings** | **21** |
| Skipped (no user-facing strings) | 6 |

### By feature area

| Area | HTML components | Notes |
|------|----------------|-------|
| Stats (library + user) | 26 | Largest group — all chart components |
| Readers (ebook + cbx + audio + pdf) | 18 | Second largest |
| Book features | 16 | Browser, shelves, reviews, search, sender |
| Metadata | 12 | Editor, viewer, picker, searcher, manager |
| Bookdrop | 6 | File review, widgets, dialogs |
| ~~Settings dialogs~~ | ~~0~~ | ~~DONE — all 4 translated~~ |
| ~~Shared components~~ | ~~0~~ | ~~DONE — all 4 translated~~ |
| ~~Layout~~ | ~~0~~ | ~~DONE — all 5 translated~~ |
| ~~Dashboard~~ | ~~0~~ | ~~DONE — both translated~~ |
| ~~Library creator~~ | ~~0~~ | ~~DONE — both translated~~ |
| ~~App root~~ | ~~0~~ | ~~DONE — translated~~ |
