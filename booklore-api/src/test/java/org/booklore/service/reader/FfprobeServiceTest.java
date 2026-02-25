package org.booklore.service.reader;

import org.booklore.util.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FfprobeServiceTest {

    @Mock
    FileService fileService;

    @InjectMocks
    FfprobeService ffprobeService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(fileService.getToolsFfprobePath()).thenReturn(tempDir.resolve("tools/ffprobe").toString());
    }

    @Test
    void getFfprobeBinary_returnsExistingBinary() throws IOException {
        Path toolsDir = tempDir.resolve("tools/ffprobe");
        Files.createDirectories(toolsDir);

        String expectedName = getExpectedBinaryName();
        Path binaryPath = toolsDir.resolve(expectedName);
        Files.writeString(binaryPath, "fake-binary");

        Path result = ffprobeService.getFfprobeBinary();

        assertThat(result).isNotNull();
        assertThat(result.getFileName().toString()).isEqualTo(expectedName);
        assertThat(result).exists();
    }

    @Test
    void getFfprobeBinary_createsDirectoryIfMissing() throws IOException {
        Path toolsDir = tempDir.resolve("tools/ffprobe");

        String expectedName = getExpectedBinaryName();
        Path binaryPath = toolsDir.resolve(expectedName);
        Files.createDirectories(toolsDir);
        Files.writeString(binaryPath, "fake-binary");

        Path result = ffprobeService.getFfprobeBinary();

        assertThat(result).isNotNull();
        assertThat(toolsDir).exists();
    }

    @Test
    void getFfprobeBinary_returnsNullOnFailure() {
        when(fileService.getToolsFfprobePath()).thenReturn("/nonexistent\0path");

        Path result = ffprobeService.getFfprobeBinary();

        assertThat(result).isNull();
    }

    @Test
    void getFfprobeBinary_setsExecutablePermission() throws IOException {
        Path toolsDir = tempDir.resolve("tools/ffprobe");
        Files.createDirectories(toolsDir);

        String expectedName = getExpectedBinaryName();
        Path binaryPath = toolsDir.resolve(expectedName);
        Files.writeString(binaryPath, "fake-binary");

        Path result = ffprobeService.getFfprobeBinary();

        assertThat(result).isNotNull();
        assertThat(result.toFile().canExecute()).isTrue();
    }

    private String getExpectedBinaryName() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();

        if (osName.contains("mac") || osName.contains("darwin")) {
            if (osArch.contains("arm") || osArch.contains("aarch64")) {
                return "ffprobe-darwin-arm64";
            }
            return "ffprobe-darwin-64";
        } else if (osName.contains("linux")) {
            if (osArch.contains("arm64") || osArch.contains("aarch64")) {
                return "ffprobe-linux-arm64";
            }
            return "ffprobe-linux-64";
        }
        throw new IllegalStateException("Unsupported OS for test: " + osName);
    }
}
