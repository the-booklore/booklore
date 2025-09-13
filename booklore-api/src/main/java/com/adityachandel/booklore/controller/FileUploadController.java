package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.Book;
import com.adityachandel.booklore.service.upload.FileUploadService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@AllArgsConstructor
@RestController
@RequestMapping("/api/v1/files")
public class FileUploadController {

    private final FileUploadService fileUploadService;

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canUpload()")
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, @RequestParam("libraryId") long libraryId, @RequestParam("pathId") long pathId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is missing.");
        }
        fileUploadService.uploadFile(file, libraryId, pathId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@securityUtil.isAdmin() or @securityUtil.canUpload()")
    @PostMapping(value = "/upload/bookdrop", consumes = "multipart/form-data")
    public ResponseEntity<Book> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is missing.");
        }
        return ResponseEntity.ok(fileUploadService.uploadFileBookDrop(file));
    }
}
