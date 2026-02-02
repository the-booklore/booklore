package org.booklore.service.security;

import org.booklore.model.entity.JwtSecretEntity;
import org.booklore.repository.JwtSecretRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class JwtSecretService {

    private final JwtSecretRepository jwtSecretRepository;
    private String cachedSecret;

    public JwtSecretService(JwtSecretRepository jwtSecretRepository) {
        this.jwtSecretRepository = jwtSecretRepository;
    }

    @PostConstruct
    @Transactional
    public void initializeSecret() {
        cachedSecret = jwtSecretRepository.findLatestSecret().orElseGet(this::generateAndStoreNewSecret);
    }

    private String generateAndStoreNewSecret() {
        String newSecret = generateRandomSecret();
        JwtSecretEntity secretEntity = new JwtSecretEntity(newSecret);
        jwtSecretRepository.save(secretEntity);
        return newSecret;
    }

    private String generateRandomSecret() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    public String getSecret() {
        return cachedSecret;
    }
}
