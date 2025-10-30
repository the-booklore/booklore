package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.settings.AppSettingKey;
import com.adityachandel.booklore.model.dto.settings.AppSettings;
import com.adityachandel.booklore.model.dto.settings.SettingRequest;
import com.adityachandel.booklore.service.appsettings.AppSettingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "App Settings", description = "Endpoints for retrieving and updating application settings")
@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/settings")
public class AppSettingController {

    private final AppSettingService appSettingService;

    @Operation(summary = "Get application settings", description = "Retrieve all application settings.")
    @ApiResponse(responseCode = "200", description = "Application settings returned successfully")
    @GetMapping
    public AppSettings getAppSettings() {
        return appSettingService.getAppSettings();
    }

    @Operation(summary = "Update application settings", description = "Update one or more application settings.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settings updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PutMapping
    public void updateSettings(
            @Parameter(description = "List of settings to update") @RequestBody List<SettingRequest> settingRequests) throws JsonProcessingException {
        for (SettingRequest settingRequest : settingRequests) {
            AppSettingKey key = AppSettingKey.valueOf(settingRequest.getName());
            appSettingService.updateSetting(key, settingRequest.getValue());
        }
    }
}