package com.adityachandel.booklore.controller;

import com.adityachandel.booklore.service.book.BookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookMediaControllerTest {

    @Mock
    private BookService bookService;

    @InjectMocks
    private BookMediaController bookMediaController;

    @Test
    void getBackgroundImage_shouldReturnBadRequestWhenFilenameIsNull() {
        Resource resource = mock(Resource.class);
        when(bookService.getBackgroundImage()).thenReturn(resource);
        when(resource.exists()).thenReturn(true);
        when(resource.getFilename()).thenReturn(null);

        ResponseEntity<Resource> response = bookMediaController.getBackgroundImage();

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
