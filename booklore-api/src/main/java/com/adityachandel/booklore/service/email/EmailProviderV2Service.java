package com.adityachandel.booklore.service.email;

import com.adityachandel.booklore.config.security.service.AuthenticationService;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.EmailProviderV2Mapper;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.dto.EmailProviderV2;
import com.adityachandel.booklore.model.dto.request.CreateEmailProviderRequest;
import com.adityachandel.booklore.model.entity.EmailProviderV2Entity;
import com.adityachandel.booklore.model.entity.UserEmailProviderPreferenceEntity;
import com.adityachandel.booklore.repository.EmailProviderV2Repository;
import com.adityachandel.booklore.repository.UserEmailProviderPreferenceRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@AllArgsConstructor
public class EmailProviderV2Service {

    private final EmailProviderV2Repository repository;
    private final UserEmailProviderPreferenceRepository preferenceRepository;
    private final EmailProviderV2Mapper mapper;
    private final AuthenticationService authService;

    public List<EmailProviderV2> getEmailProviders() {
        BookLoreUser user = authService.getAuthenticatedUser();
        List<EmailProviderV2Entity> userProviders = repository.findAllByUserId(user.getId());
        if (!user.getPermissions().isAdmin()) {
            List<EmailProviderV2Entity> sharedProviders = repository.findAllBySharedTrueAndAdmin();
            userProviders.addAll(sharedProviders);
        }

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        return userProviders.stream()
                .map(entity -> mapper.toDTO(entity, defaultProviderId))
                .toList();
    }

    public EmailProviderV2 getEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity entity = repository.findAccessibleProvider(id, user.getId())
                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        return mapper.toDTO(entity, defaultProviderId);
    }

    @Transactional
    public EmailProviderV2 createEmailProvider(CreateEmailProviderRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity entity = mapper.toEntity(request);
        entity.setUserId(user.getId());
        entity.setShared(user.getPermissions().isAdmin() && request.isShared());
        EmailProviderV2Entity savedEntity = repository.save(entity);

        if (preferenceRepository.findByUserId(user.getId()).isEmpty()) {
            setDefaultProviderForUser(user.getId(), savedEntity.getId());
        }

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        return mapper.toDTO(savedEntity, defaultProviderId);
    }

    @Transactional
    public EmailProviderV2 updateEmailProvider(Long id, CreateEmailProviderRequest request) {
        BookLoreUser user = authService.getAuthenticatedUser();
        EmailProviderV2Entity existingProvider = repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));

        mapper.updateEntityFromRequest(request, existingProvider);
        if (user.getPermissions().isAdmin()) {
            existingProvider.setShared(request.isShared());
        }
        EmailProviderV2Entity updatedEntity = repository.save(existingProvider);

        Long defaultProviderId = getDefaultProviderIdForUser(user.getId());
        return mapper.toDTO(updatedEntity, defaultProviderId);
    }

    @Transactional
    public void setDefaultEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        // Verify user has access to this provider
        repository.findAccessibleProvider(id, user.getId())
                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));

        setDefaultProviderForUser(user.getId(), id);
    }

    @Transactional
    public void deleteEmailProvider(Long id) {
        BookLoreUser user = authService.getAuthenticatedUser();
        repository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiError.EMAIL_PROVIDER_NOT_FOUND.createException(id));

        List<UserEmailProviderPreferenceEntity> preferencesUsingProvider =
                preferenceRepository.findAll().stream()
                        .filter(pref -> pref.getDefaultProviderId().equals(id))
                        .toList();

        for (UserEmailProviderPreferenceEntity preference : preferencesUsingProvider) {
            List<EmailProviderV2Entity> availableProviders = getAccessibleProvidersForUser(preference.getUserId());
            availableProviders.removeIf(p -> p.getId().equals(id));

            if (!availableProviders.isEmpty()) {
                EmailProviderV2Entity newDefault = availableProviders.get(ThreadLocalRandom.current().nextInt(availableProviders.size()));
                preference.setDefaultProviderId(newDefault.getId());
                preferenceRepository.save(preference);
            } else {
                preferenceRepository.delete(preference);
            }
        }

        repository.deleteById(id);
    }

    private Long getDefaultProviderIdForUser(Long userId) {
        return preferenceRepository.findByUserId(userId)
                .map(UserEmailProviderPreferenceEntity::getDefaultProviderId)
                .orElse(null);
    }

    private void setDefaultProviderForUser(Long userId, Long providerId) {
        UserEmailProviderPreferenceEntity preference = preferenceRepository.findByUserId(userId)
                .orElse(UserEmailProviderPreferenceEntity.builder()
                        .userId(userId)
                        .build());
        preference.setDefaultProviderId(providerId);
        preferenceRepository.save(preference);
    }

    private List<EmailProviderV2Entity> getAccessibleProvidersForUser(Long userId) {
        List<EmailProviderV2Entity> providers = repository.findAllByUserId(userId);
        providers.addAll(repository.findAllBySharedTrueAndAdmin());
        return providers;
    }
}