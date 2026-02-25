package org.booklore.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

class UnrarHelperIntegrationTest {

    private static final Path RAR5_CBR = Path.of("src/test/resources/cbx/test-rar5.cbr");

    @BeforeAll
    static void checkUnrarAvailable() {
        assumeThat(UnrarHelper.isAvailable())
                .as("unrar binary must be on PATH to run these tests")
                .isTrue();
    }

    @Test
    void listEntries_returnsAllEntriesFromRar5Archive() throws IOException {
        List<String> entries = UnrarHelper.listEntries(RAR5_CBR);

        assertThat(entries).containsExactly(
                "ComicInfo.xml",
                "page_001.jpg",
                "page_002.jpg",
                "page_003.jpg"
        );
    }

    @Test
    void extractEntryBytes_extractsComicInfoXml() throws IOException {
        byte[] bytes = UnrarHelper.extractEntryBytes(RAR5_CBR, "ComicInfo.xml");
        String xml = new String(bytes);

        assertThat(xml).contains("<Title>Test RAR5 Comic</Title>");
        assertThat(xml).contains("<Series>RAR5 Test Series</Series>");
        assertThat(xml).contains("<Writer>Test Author</Writer>");
    }

    @Test
    void extractEntry_streamsImageToOutputStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        UnrarHelper.extractEntry(RAR5_CBR, "page_001.jpg", out);

        byte[] imageBytes = out.toByteArray();
        assertThat(imageBytes).hasSizeGreaterThan(0);
        assertThat(imageBytes[0]).isEqualTo((byte) 0xFF);
        assertThat(imageBytes[1]).isEqualTo((byte) 0xD8);
    }

    @Test
    void extractAll_extractsAllFilesToDirectory(@TempDir Path tempDir) throws IOException {
        UnrarHelper.extractAll(RAR5_CBR, tempDir);

        assertThat(tempDir.resolve("ComicInfo.xml")).exists();
        assertThat(tempDir.resolve("page_001.jpg")).exists();
        assertThat(tempDir.resolve("page_002.jpg")).exists();
        assertThat(tempDir.resolve("page_003.jpg")).exists();

        String xml = Files.readString(tempDir.resolve("ComicInfo.xml"));
        assertThat(xml).contains("<Title>Test RAR5 Comic</Title>");
    }

    @Test
    void listEntries_throwsForNonExistentFile() {
        Path bogus = Path.of("/tmp/does-not-exist.cbr");

        assertThatThrownBy(() -> UnrarHelper.listEntries(bogus))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("unrar list failed");
    }

    @Test
    void extractEntryBytes_throwsForNonExistentEntry() {
        assertThatThrownBy(() -> UnrarHelper.extractEntryBytes(RAR5_CBR, "no-such-file.jpg"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("unrar extract failed");
    }
}
