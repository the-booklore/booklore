package com.adityachandel.booklore.service;

import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.mapper.BookMapper;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.*;
import com.adityachandel.booklore.model.entity.BookEntity;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.model.entity.ShelfEntity;
import com.adityachandel.booklore.repository.BookOpdsRepository;
import com.adityachandel.booklore.repository.ShelfRepository;
import com.adityachandel.booklore.repository.UserRepository;
import com.adityachandel.booklore.service.library.LibraryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class OpdsBookServiceTest {

    @Mock private BookOpdsRepository bookOpdsRepository;
    @Mock private BookMapper bookMapper;
    @Mock private UserRepository userRepository;
    @Mock private BookLoreUserTransformer bookLoreUserTransformer;
    @Mock private ShelfRepository shelfRepository;
    @Mock private LibraryService libraryService;

    @InjectMocks private OpdsBookService opdsBookService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    private OpdsUserDetails legacyUserDetails() {
        OpdsUser opdsUser = new OpdsUser();
        opdsUser.setUsername("legacy");
        opdsUser.setPassword(null);
        return new OpdsUserDetails(null, opdsUser);
    }

    private OpdsUserDetails v2UserDetails(Long userId, boolean isAdmin, Set<Long> libraryIds) {
        OpdsUserV2 v2 = OpdsUserV2.builder().userId(userId).username("v2user").build();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(entity));
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(isAdmin);
        when(perms.isCanAccessOpds()).thenReturn(true);

        List<Library> libraries = new ArrayList<>();
        for (Long id : libraryIds) {
            libraries.add(Library.builder().id(id).name("Lib" + id).build());
        }
        when(user.getAssignedLibraries()).thenReturn(libraries);

        return new OpdsUserDetails(v2, null);
    }

    @Test
    void getAccessibleLibraries_returnsAllLibraries_whenNoUserDetails() {
        List<Library> libraries = List.of(Library.builder().id(1L).name("Lib1").build());
        when(libraryService.getAllLibraries()).thenReturn(libraries);

        List<Library> result = opdsBookService.getAccessibleLibraries(null);

        assertThat(result).isEqualTo(libraries);
    }

    @Test
    void getAccessibleLibraries_returnsAssignedLibraries_forNonAdmin() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of(2L, 3L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        List<Library> assigned = List.of(Library.builder().id(2L).build());
        when(user.getAssignedLibraries()).thenReturn(assigned);

        List<Library> result = opdsBookService.getAccessibleLibraries(details);

        assertThat(result).isEqualTo(assigned);
    }

    @Test
    void getAccessibleLibraries_returnsAllLibraries_forAdmin() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        List<Library> allLibs = List.of(Library.builder().id(1L).build());
        when(libraryService.getAllLibraries()).thenReturn(allLibs);

        List<Library> result = opdsBookService.getAccessibleLibraries(details);

        assertThat(result).isEqualTo(allLibs);
    }

    @Test
    void getBooksPage_legacyUser_delegatesToLegacyMethod() {
        OpdsUserDetails details = legacyUserDetails();
        when(bookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findRecentBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByLibraryIds(anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByShelfId(anyLong(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByMetadataSearch(anyString(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findAllWithMetadataByIds(anyList())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndShelfId(anyList(), anyLong())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIds(anyList())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of());

        opdsBookService.getBooksPage(details, "q", 1L, 2L, 0, 10);
    }

    @Test
    void getBooksPage_v2User_delegatesToV2Method() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        when(bookOpdsRepository.findBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findRecentBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByLibraryIds(anySet(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByShelfId(anyLong(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findBookIdsByMetadataSearch(anyString(), any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findAllWithMetadataByIds(anyList())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithMetadataByIdsAndShelfId(anyList(), anyLong())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIds(anyList())).thenReturn(List.of());
        when(bookOpdsRepository.findAllWithFullMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of());

        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(com.adityachandel.booklore.model.entity.UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(true);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(true);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(true);

        ShelfEntity shelf = mock(ShelfEntity.class);
        BookLoreUserEntity shelfUser = mock(BookLoreUserEntity.class);
        when(shelfUser.getId()).thenReturn(1L);
        when(shelf.getUser()).thenReturn(shelfUser);
        when(shelfRepository.findById(anyLong())).thenReturn(Optional.of(shelf));

        opdsBookService.getBooksPage(details, "q", 1L, 2L, 0, 10);
    }

    @Test
    void getRecentBooksPage_returnsRecentBooks_forLegacyUser() {
        OpdsUserDetails details = legacyUserDetails();
        when(bookOpdsRepository.findRecentBookIds(any())).thenReturn(Page.empty());
        when(bookOpdsRepository.findAllWithMetadataByIds(anyList())).thenReturn(List.of());

        opdsBookService.getRecentBooksPage(details, 0, 10);
    }

    @Test
    void getRecentBooksPage_appliesBookFilters_forNonAdminV2User() {
        OpdsUserDetails details = v2UserDetails(2L, false, Set.of(1L, 2L));
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        when(userRepository.findById(2L)).thenReturn(Optional.of(entity));
        BookLoreUser user = mock(BookLoreUser.class);
        when(bookLoreUserTransformer.toDTO(entity)).thenReturn(user);
        BookLoreUser.UserPermissions perms = mock(BookLoreUser.UserPermissions.class);
        when(user.getPermissions()).thenReturn(perms);
        when(perms.isAdmin()).thenReturn(false);
        List<Library> libs = List.of(Library.builder().id(1L).build());
        when(user.getAssignedLibraries()).thenReturn(libs);

        Book book = Book.builder().id(1L).shelves(Set.of(Shelf.builder().userId(2L).build())).build();
        BookEntity bookEntity = mock(BookEntity.class);
        when(bookEntity.getId()).thenReturn(1L);

        when(bookOpdsRepository.findRecentBookIdsByLibraryIds(anySet(), any())).thenReturn(new PageImpl<>(List.of(1L)));
        when(bookOpdsRepository.findAllWithMetadataByIdsAndLibraryIds(anyList(), anySet())).thenReturn(List.of(bookEntity));
        when(bookMapper.toBook(bookEntity)).thenReturn(book);

        Page<Book> result = opdsBookService.getRecentBooksPage(details, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getShelves()).allMatch(shelf -> shelf.getUserId().equals(2L));
    }

    @Test
    void getLibraryName_returnsName_whenFound() {
        List<Library> libs = List.of(Library.builder().id(1L).name("Lib1").build());
        when(libraryService.getAllLibraries()).thenReturn(libs);

        String name = opdsBookService.getLibraryName(1L);

        assertThat(name).isEqualTo("Lib1");
    }

    @Test
    void getLibraryName_returnsDefault_whenNotFound() {
        when(libraryService.getAllLibraries()).thenReturn(List.of());

        String name = opdsBookService.getLibraryName(99L);

        assertThat(name).isEqualTo("Library Books");
    }

    @Test
    void getShelfName_returnsShelfName_whenFound() {
        ShelfEntity shelf = mock(ShelfEntity.class);
        when(shelf.getName()).thenReturn("Shelf1");
        when(shelfRepository.findById(1L)).thenReturn(Optional.of(shelf));

        String name = opdsBookService.getShelfName(1L);

        assertThat(name).isEqualTo("Shelf1 - Shelf");
    }

    @Test
    void getShelfName_returnsDefault_whenNotFound() {
        when(shelfRepository.findById(1L)).thenReturn(Optional.empty());

        String name = opdsBookService.getShelfName(1L);

        assertThat(name).isEqualTo("Shelf Books");
    }

    @Test
    void getUserShelves_returnsShelves() {
        List<ShelfEntity> shelves = List.of(mock(ShelfEntity.class));
        when(shelfRepository.findByUserId(1L)).thenReturn(shelves);

        List<ShelfEntity> result = opdsBookService.getUserShelves(1L);

        assertThat(result).isEqualTo(shelves);
    }

    @Test
    void getRandomBooks_returnsBooks_whenLibrariesAccessible() {
        OpdsUserDetails details = v2UserDetails(1L, true, Set.of(1L));
        OpdsBookService spy = Mockito.spy(opdsBookService);
        List<Library> libs = List.of(Library.builder().id(1L).build());
        doReturn(libs).when(spy).getAccessibleLibraries(details);

        when(bookOpdsRepository.findRandomBookIdsByLibraryIds(anyList())).thenReturn(List.of(1L, 2L));
        BookEntity entity = mock(BookEntity.class);
        when(bookOpdsRepository.findAllWithMetadataByIds(anyList())).thenReturn(List.of(entity));
        Book book = Book.builder().id(1L).build();
        when(bookMapper.toBook(entity)).thenReturn(book);

        List<Book> result = spy.getRandomBooks(details, 1);

        assertThat(result).hasSize(1);
    }

    @Test
    void getRandomBooks_returnsEmpty_whenNoLibraries() {
        OpdsUserDetails details = v2UserDetails(1L, false, Set.of());
        OpdsBookService spy = Mockito.spy(opdsBookService);
        doReturn(List.of()).when(spy).getAccessibleLibraries(details);

        List<Book> result = spy.getRandomBooks(details, 1);

        assertThat(result).isEmpty();
    }

    @Test
    void getBooksPageForV2User_throwsForbidden_whenNoPermission() {
        OpdsUserV2 v2 = OpdsUserV2.builder().userId(1L).build();
        BookLoreUserEntity entity = mock(BookLoreUserEntity.class);
        var permissionsEntity = mock(com.adityachandel.booklore.model.entity.UserPermissionsEntity.class);
        when(permissionsEntity.isPermissionAccessOpds()).thenReturn(false);
        when(permissionsEntity.isPermissionAdmin()).thenReturn(false);
        when(entity.getPermissions()).thenReturn(permissionsEntity);
        when(userRepository.findById(1L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() ->
                opdsBookService.getBooksPage(v2UserDetails(1L, false, Set.of()), null, null, null, 0, 10)
        ).hasMessageContaining("You are not allowed to access this resource");
    }

}
