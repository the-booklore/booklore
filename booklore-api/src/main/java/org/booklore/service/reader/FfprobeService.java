package org.booklore.service.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.booklore.util.FileService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@AllArgsConstructor
public class FfprobeService {

    private static final String FFPROBE_GITHUB_BASE_URL = "https://github.com/booklore-app/booklore-tools/raw/main/ffprobe/";

    private static final String BIN_DARWIN_ARM64 = "ffprobe-darwin-arm64";
    private static final String BIN_DARWIN_X64 = "ffprobe-darwin-64";
    private static final String BIN_LINUX_X64 = "ffprobe-linux-64";
    private static final String BIN_LINUX_ARM64 = "ffprobe-linux-arm64";

    private final FileService fileService;

    public Path getFfprobeBinary() {
        try {
            return setupFfprobeBinary();
        } catch (Exception e) {
            log.warn("Failed to set up ffprobe binary: {}", e.getMessage());
            return null;
        }
    }

    private Path setupFfprobeBinary() throws Exception {
        String binaryName = getFfprobeBinaryName();
        Path toolsDir = Paths.get(fileService.getToolsFfprobePath());
        if (!Files.exists(toolsDir)) {
            Files.createDirectories(toolsDir);
        }
        Path binaryPath = toolsDir.resolve(binaryName);

        if (!Files.exists(binaryPath)) {
            String downloadUrl = FFPROBE_GITHUB_BASE_URL + binaryName;
            log.info("Downloading ffprobe binary '{}' from {}", binaryName, downloadUrl);
            try (InputStream in = java.net.URI.create(downloadUrl).toURL().openStream()) {
                Files.copy(in, binaryPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!binaryPath.toFile().setExecutable(true)) {
                log.warn("Failed to set executable permission for '{}'", binaryPath.toAbsolutePath());
            }
            log.info("Downloaded ffprobe binary to {}", binaryPath.toAbsolutePath());
        } else {
            if (!binaryPath.toFile().setExecutable(true)) {
                log.warn("Failed to set executable permission for '{}'", binaryPath.toAbsolutePath());
            }
            log.debug("Using existing ffprobe binary at {}", binaryPath.toAbsolutePath());
        }
        return binaryPath;
    }

    private String getFfprobeBinaryName() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        log.debug("Detected OS: {} ({})", osName, osArch);

        if (osName.contains("mac") || osName.contains("darwin")) {
            if (osArch.contains("arm") || osArch.contains("aarch64")) {
                return BIN_DARWIN_ARM64;
            } else {
                return BIN_DARWIN_X64;
            }
        } else if (osName.contains("linux")) {
            if (osArch.contains("arm64") || osArch.contains("aarch64")) {
                return BIN_LINUX_ARM64;
            } else if (osArch.contains("64")) {
                return BIN_LINUX_X64;
            }
        }
        throw new IllegalStateException("Unsupported operating system or architecture: " + osName + " / " + osArch);
    }
}
