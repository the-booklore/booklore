package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.export.ConfigurationExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Tag(name = "Configuration Export/Import", description = "Export and import library configuration")
@RestController
@RequestMapping("/api/v1/configuration")
@RequiredArgsConstructor
public class ConfigurationExportController {

    private final ConfigurationExportService exportService;

    @Operation(summary = "Export configuration as JSON", description = "Export shelves, magic shelves, and settings as JSON")
    @GetMapping("/export")
    public ResponseEntity<String> exportConfiguration() {
        String jsonConfig = exportService.exportConfiguration();

        String filename = "booklore-config-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) +
                ".json";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(jsonConfig);
    }

    @Operation(summary = "Import configuration from JSON", description = "Import shelves, magic shelves, and settings from JSON")
    @PostMapping("/import")
    public ResponseEntity<Void> importConfiguration(
            @RequestBody String jsonConfig,
            @Parameter(description = "Skip existing items") @RequestParam(defaultValue = "true") boolean skipExisting,
            @Parameter(description = "Overwrite existing items") @RequestParam(defaultValue = "false") boolean overwrite,
            @Parameter(description = "Import shelves") @RequestParam(defaultValue = "true") boolean importShelves,
            @Parameter(description = "Import magic shelves") @RequestParam(defaultValue = "true") boolean importMagicShelves,
            @Parameter(description = "Import settings") @RequestParam(defaultValue = "true") boolean importSettings
    ) {
        com.adityachandel.booklore.model.dto.export.ImportOptions options = com.adityachandel.booklore.model.dto.export.ImportOptions.builder()
                .skipExisting(skipExisting)
                .overwrite(overwrite)
                .importShelves(importShelves)
                .importMagicShelves(importMagicShelves)
                .importSettings(importSettings)
                .skipBookMappings(true)
                .build();

        exportService.importConfiguration(jsonConfig, options);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get configuration template", description = "Get empty template for configuration")
    @GetMapping("/template")
    public ResponseEntity<String> getTemplate() {
        String template = """
                {
                  "version": "1.0",
                  "shelves": [
                    {
                      "name": "My Shelf",
                      "icon": "pi-book",
                      "iconType": "PRIME_NG",
                      "sort": null,
                      "books": []
                    }
                  ],
                  "magicShelves": [
                    {
                      "name": "My Magic Shelf",
                      "icon": "pi-star",
                      "iconType": "PRIME_NG",
                      "isPublic": false,
                      "filterJson": "{\\"rules\\":[]}"
                    }
                  ],
                  "settings": [
                    {"key": "theme", "value": "dark"}
                  ]
                }
                """;
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(template);
    }
}
