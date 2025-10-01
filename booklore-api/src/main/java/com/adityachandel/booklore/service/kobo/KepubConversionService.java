package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.util.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KepubConversionService {

    @Autowired
    private FileService fileService;

    private static final String KEPUBIFY_GITHUB_BASE_URL = "https://github.com/booklore-app/booklore-tools/raw/main/kepubify/";

    private static final String BIN_DARWIN_ARM64 = "kepubify-darwin-arm64";
    private static final String BIN_DARWIN_X64 = "kepubify-darwin-64bit";
    private static final String BIN_LINUX_X64 = "kepubify-linux-64bit";
    private static final String BIN_LINUX_X86 = "kepubify-linux-32bit";
    private static final String BIN_LINUX_ARM = "kepubify-linux-arm";
    private static final String BIN_LINUX_ARM64 = "kepubify-linux-arm64";

    public File convertEpubToKepub(File epubFile, File tempDir) throws IOException, InterruptedException {
        validateInputs(epubFile);

        Path kepubifyBinary = setupKepubifyBinary();
        File outputFile = executeKepubifyConversion(epubFile, tempDir, kepubifyBinary);

        log.info("Successfully converted {} to {} (size: {} bytes)", epubFile.getName(), outputFile.getName(), outputFile.length());
        return outputFile;
    }

    private void validateInputs(File epubFile) {
        if (epubFile == null || !epubFile.isFile() || !epubFile.getName().endsWith(".epub")) {
            throw new IllegalArgumentException("Invalid EPUB file: " + epubFile);
        }
    }

    private Path setupKepubifyBinary() throws IOException {
        String binaryName = getKepubifyBinaryName();
        String toolsDirPath = fileService.getToolsKepubifyPath();
        Path toolsDir = Paths.get(toolsDirPath);
        if (!Files.exists(toolsDir)) {
            Files.createDirectories(toolsDir);
        }
        Path binaryPath = toolsDir.resolve(binaryName);

        if (!Files.exists(binaryPath)) {
            String downloadUrl = KEPUBIFY_GITHUB_BASE_URL + binaryName;
            log.info("Downloading kepubify binary '{}' from {}", binaryName, downloadUrl);
            try (InputStream in = java.net.URI.create(downloadUrl).toURL().openStream()) {
                Files.copy(in, binaryPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!binaryPath.toFile().setExecutable(true)) {
                log.warn("Failed to set executable permission for '{}'", binaryPath.toAbsolutePath());
            }
            log.info("Downloaded kepubify binary to {}", binaryPath.toAbsolutePath());
        } else {
            if (!binaryPath.toFile().setExecutable(true)) {
                log.warn("Failed to set executable permission for '{}'", binaryPath.toAbsolutePath());
            }
            log.debug("Using existing kepubify binary at {}", binaryPath.toAbsolutePath());
        }
        return binaryPath;
    }

    private String getKepubifyBinaryName() {
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
            } else if (osArch.contains("arm")) {
                return BIN_LINUX_ARM;
            } else if (osArch.contains("64")) {
                return BIN_LINUX_X64;
            } else if (osArch.contains("86")) {
                return BIN_LINUX_X86;
            }
        }
        throw new IllegalStateException("Unsupported operating system or architecture: " + osName + " / " + osArch);
    }

    private File executeKepubifyConversion(File epubFile, File tempDir, Path kepubifyBinary) throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(kepubifyBinary.toAbsolutePath().toString(), "-o", tempDir.getAbsolutePath(), epubFile.getAbsolutePath());
        pb.directory(tempDir);

        log.info("Starting kepubify conversion for {} -> output dir: {}", epubFile.getAbsolutePath(), tempDir.getAbsolutePath());

        Process process = pb.start();

        String output = readProcessOutput(process.getInputStream());
        String error = readProcessOutput(process.getErrorStream());

        int exitCode = process.waitFor();
        logProcessResults(exitCode, output, error);

        if (exitCode != 0) {
            throw new IOException(String.format("Kepubify conversion failed with exit code: %d. Error: %s", exitCode, error));
        }

        return findOutputFile(tempDir);
    }

    private String readProcessOutput(InputStream inputStream) {
        return new BufferedReader(new InputStreamReader(inputStream))
                .lines()
                .collect(Collectors.joining("\n"));
    }

    private void logProcessResults(int exitCode, String output, String error) {
        log.debug("Kepubify process exited with code {}", exitCode);
        if (!output.isEmpty()) {
            log.debug("Kepubify stdout: {}", output);
        }
        if (!error.isEmpty()) {
            log.error("Kepubify stderr: {}", error);
        }
    }

    private File findOutputFile(File tempDir) throws IOException {
        File[] kepubFiles = tempDir.listFiles((dir, name) -> name.endsWith(".kepub.epub"));
        if (kepubFiles == null || kepubFiles.length == 0) {
            throw new IOException("Kepubify conversion completed but no .kepub.epub file was created in: " + tempDir.getAbsolutePath());
        }
        return kepubFiles[0];
    }
}
