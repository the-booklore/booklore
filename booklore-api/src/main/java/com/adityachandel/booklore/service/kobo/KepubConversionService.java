package com.adityachandel.booklore.service.kobo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KepubConversionService {

    private static final String KEPUBIFY_BINARY_MACOS_ARM64 = "/bin/kepubify-darwin-arm64";
    private static final String KEPUBIFY_BINARY_LINUX_X64 = "/bin/kepubify-linux-64bit";

    public File convertEpubToKepub(File epubFile, File tempDir) throws IOException, InterruptedException {
        validateInputs(epubFile);

        Path kepubifyBinary = setupKepubifyBinary(tempDir);
        File outputFile = executeKepubifyConversion(epubFile, tempDir, kepubifyBinary);

        log.info("Successfully converted {} to {} (size: {} bytes)", epubFile.getName(), outputFile.getName(), outputFile.length());
        return outputFile;
    }

    private void validateInputs(File epubFile) {
        if (epubFile == null || !epubFile.isFile() || !epubFile.getName().endsWith(".epub")) {
            throw new IllegalArgumentException("Invalid EPUB file: " + epubFile);
        }
    }

    private Path setupKepubifyBinary(File tempDir) throws IOException {
        Path tempKepubify = tempDir.toPath().resolve("kepubify");
        String resourcePath = getKepubifyResourcePath();

        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(in, tempKepubify, StandardCopyOption.REPLACE_EXISTING);
        }
        tempKepubify.toFile().setExecutable(true);
        return tempKepubify;
    }

    private String getKepubifyResourcePath() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        log.debug("Detected OS: {} ({})", osName, osArch);

        if (osName.contains("mac") || osName.contains("darwin")) {
            return KEPUBIFY_BINARY_MACOS_ARM64;
        } else if (osName.contains("linux")) {
            return KEPUBIFY_BINARY_LINUX_X64;
        } else {
            throw new IllegalStateException("Unsupported operating system: " + osName);
        }
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
