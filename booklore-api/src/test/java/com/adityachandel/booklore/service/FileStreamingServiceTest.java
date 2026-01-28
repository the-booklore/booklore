package com.adityachandel.booklore.service;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileStreamingServiceTest {

    private FileStreamingService fileStreamingService;

    @TempDir
    Path tempDir;

    Path testFile;
    byte[] testContent;

    @BeforeEach
    void setUp() throws IOException {
        fileStreamingService = new FileStreamingService();

        // Create a test file with known content
        testContent = new byte[10000];
        for (int i = 0; i < testContent.length; i++) {
            testContent[i] = (byte) (i % 256);
        }
        testFile = tempDir.resolve("test.bin");
        Files.write(testFile, testContent);
    }

    // ==================== streamWithRangeSupport - Full file tests ====================

    @Test
    void streamWithRangeSupport_noRangeHeader_streamsFullFile() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn(null);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "application/octet-stream", request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).setContentLengthLong(testContent.length);
        verify(response).setHeader("Accept-Ranges", "bytes");
        verify(response).setContentType("application/octet-stream");
        assertArrayEquals(testContent, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_fileNotFound_sends404() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        Path nonexistent = tempDir.resolve("nonexistent.bin");

        fileStreamingService.streamWithRangeSupport(nonexistent, "audio/mp4", request, response);

        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
    }

    // ==================== streamWithRangeSupport - Range request tests ====================

    @Test
    void streamWithRangeSupport_fullRange_streamsPartialContent() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=0-99");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(100);
        verify(response).setHeader("Content-Range", "bytes 0-99/" + testContent.length);

        byte[] expected = new byte[100];
        System.arraycopy(testContent, 0, expected, 0, 100);
        assertArrayEquals(expected, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_openEndedRange_streamsToEnd() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=9900-");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(100);
        verify(response).setHeader("Content-Range", "bytes 9900-9999/" + testContent.length);

        byte[] expected = new byte[100];
        System.arraycopy(testContent, 9900, expected, 0, 100);
        assertArrayEquals(expected, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_suffixRange_streamsLastNBytes() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=-500");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(500);
        verify(response).setHeader("Content-Range", "bytes 9500-9999/" + testContent.length);

        byte[] expected = new byte[500];
        System.arraycopy(testContent, 9500, expected, 0, 500);
        assertArrayEquals(expected, outputStream.toByteArray());
    }

    @Test
    void streamWithRangeSupport_invalidRange_sends416() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("Range")).thenReturn("bytes=50000-60000"); // Beyond file size

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
        verify(response).setHeader("Content-Range", "bytes */" + testContent.length);
    }

    @Test
    void streamWithRangeSupport_rangeEndBeyondFileSize_clampsToFileSize() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=9990-99999"); // End beyond file
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(10); // 9990 to 9999
        verify(response).setHeader("Content-Range", "bytes 9990-9999/" + testContent.length);
    }

    @Test
    void streamWithRangeSupport_setsCacheControlHeader() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn(null);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setHeader("Cache-Control", "public, max-age=3600");
    }

    // ==================== parseRange tests ====================

    @Test
    void parseRange_fullRange_parsesCorrectly() {
        var result = fileStreamingService.parseRange("bytes=100-199", 1000);

        assertNotNull(result);
        assertEquals(100, result.start());
        assertEquals(199, result.end());
    }

    @Test
    void parseRange_openEndedRange_parsesCorrectly() {
        var result = fileStreamingService.parseRange("bytes=500-", 1000);

        assertNotNull(result);
        assertEquals(500, result.start());
        assertEquals(999, result.end());
    }

    @Test
    void parseRange_suffixRange_parsesCorrectly() {
        var result = fileStreamingService.parseRange("bytes=-200", 1000);

        assertNotNull(result);
        assertEquals(800, result.start());
        assertEquals(999, result.end());
    }

    @Test
    void parseRange_suffixRangeLargerThanFile_startsAtZero() {
        var result = fileStreamingService.parseRange("bytes=-2000", 1000);

        assertNotNull(result);
        assertEquals(0, result.start());
        assertEquals(999, result.end());
    }

    @Test
    void parseRange_clampsEndToFileSize() {
        var result = fileStreamingService.parseRange("bytes=900-2000", 1000);

        assertNotNull(result);
        assertEquals(900, result.start());
        assertEquals(999, result.end());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid",
            "bytes",
            "bytes=",
            "bytes=abc-def",
            "bytes=100-50", // End before start
            "characters=0-100"
    })
    void parseRange_invalidFormats_returnsNull(String rangeHeader) {
        var result = fileStreamingService.parseRange(rangeHeader, 1000);
        assertNull(result);
    }

    @Test
    void parseRange_startBeyondFileSize_returnsNull() {
        var result = fileStreamingService.parseRange("bytes=2000-3000", 1000);
        assertNull(result);
    }

    @Test
    void parseRange_multipleRanges_usesFirstOnly() {
        var result = fileStreamingService.parseRange("bytes=0-99, 200-299", 1000);

        assertNotNull(result);
        assertEquals(0, result.start());
        assertEquals(99, result.end());
    }

    @Test
    void parseRange_withLeadingTrailingWhitespace_parsesCorrectly() {
        // Leading/trailing whitespace around the range spec is trimmed
        var result = fileStreamingService.parseRange("bytes=100-199", 1000);

        assertNotNull(result);
        assertEquals(100, result.start());
        assertEquals(199, result.end());
    }

    @Test
    void parseRange_withWhitespaceAroundComma_parsesFirstRange() {
        var result = fileStreamingService.parseRange("bytes= 100-199 , 300-399", 1000);

        assertNotNull(result);
        assertEquals(100, result.start());
        assertEquals(199, result.end());
    }

    // ==================== isClientDisconnect tests ====================

    @Test
    void isClientDisconnect_socketTimeoutException_returnsTrue() {
        assertTrue(fileStreamingService.isClientDisconnect(new SocketTimeoutException("timeout")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Connection reset",
            "Broken pipe",
            "connection was aborted",
            "An established connection was aborted",
            "SocketTimeout occurred",
            "Connection timed out"
    })
    void isClientDisconnect_knownMessages_returnsTrue(String message) {
        assertTrue(fileStreamingService.isClientDisconnect(new IOException(message)));
    }

    @Test
    void isClientDisconnect_unknownMessage_returnsFalse() {
        assertFalse(fileStreamingService.isClientDisconnect(new IOException("Unknown error")));
    }

    @Test
    void isClientDisconnect_nullMessage_checksClassName() {
        // Create an IOException with null message but class name containing "Timeout"
        IOException timeoutException = new SocketTimeoutException();
        assertTrue(fileStreamingService.isClientDisconnect(timeoutException));
    }

    @Test
    void isClientDisconnect_regularIOException_returnsFalse() {
        assertFalse(fileStreamingService.isClientDisconnect(new IOException("Read failed")));
    }

    // ==================== Edge cases ====================

    @Test
    void streamWithRangeSupport_emptyFile_handlesCorrectly() throws IOException {
        Path emptyFile = tempDir.resolve("empty.bin");
        Files.createFile(emptyFile);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn(null);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        // This should handle empty file gracefully - the stream will be empty
        fileStreamingService.streamWithRangeSupport(emptyFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_OK);
        verify(response).setContentLengthLong(0);
    }

    @Test
    void streamWithRangeSupport_singleByteRange_streamsOneByte() throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=50-50");
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        verify(response).setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        verify(response).setContentLengthLong(1);
        assertEquals(1, outputStream.size());
        assertEquals(testContent[50], outputStream.toByteArray()[0]);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "0, 9999",
            "5000, 5000",
            "9999, 9999"
    })
    void streamWithRangeSupport_variousValidRanges_streamsCorrectContent(int start, int end) throws IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ServletOutputStream servletOutputStream = createServletOutputStream(outputStream);

        when(request.getHeader("Range")).thenReturn("bytes=" + start + "-" + end);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        fileStreamingService.streamWithRangeSupport(testFile, "audio/mp4", request, response);

        int expectedLength = end - start + 1;
        assertEquals(expectedLength, outputStream.size());

        byte[] expected = new byte[expectedLength];
        System.arraycopy(testContent, start, expected, 0, expectedLength);
        assertArrayEquals(expected, outputStream.toByteArray());
    }

    // Helper method to create a mock ServletOutputStream
    private ServletOutputStream createServletOutputStream(ByteArrayOutputStream outputStream) {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
            }

            @Override
            public void write(int b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                outputStream.write(b, off, len);
            }
        };
    }
}
