package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.model.dto.KoreaderUser;
import com.adityachandel.booklore.service.koreader.KoreaderUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KoreaderUserControllerTest {

    @Mock
    private KoreaderUserService koreaderUserService;

    @InjectMocks
    private KoreaderUserController controller;

    private KoreaderUser user;

    @BeforeEach
    void setUp() {
        try (AutoCloseable mocks = MockitoAnnotations.openMocks(this)) {
            user = new KoreaderUser(1L, "testuser", "pass", "md5", true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void getCurrentUser_returnsUser() {
        when(koreaderUserService.getUser()).thenReturn(user);
        ResponseEntity<KoreaderUser> resp = controller.getCurrentUser();
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(user, resp.getBody());
    }

    @Test
    void upsertCurrentUser_returnsUser() {
        Map<String, String> userData = Map.of("username", "testuser", "password", "pass");
        when(koreaderUserService.upsertUser("testuser", "pass")).thenReturn(user);
        ResponseEntity<KoreaderUser> resp = controller.upsertCurrentUser(userData);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(user, resp.getBody());
    }

    @Test
    void updateSyncEnabled_returnsNoContent() {
        doNothing().when(koreaderUserService).toggleSync(true);
        ResponseEntity<Void> resp = controller.updateSyncEnabled(true);
        assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
        assertNull(resp.getBody());
    }
}
