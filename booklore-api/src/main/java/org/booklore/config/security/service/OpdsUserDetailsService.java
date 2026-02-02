package org.booklore.config.security.service;

import org.booklore.config.security.userdetails.OpdsUserDetails;
import org.booklore.exception.ApiError;
import org.booklore.mapper.OpdsUserV2Mapper;
import org.booklore.model.dto.OpdsUserV2;
import org.booklore.model.entity.OpdsUserV2Entity;
import org.booklore.repository.OpdsUserV2Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class OpdsUserDetailsService implements UserDetailsService {

    private final OpdsUserV2Repository opdsUserV2Repository;
    private final OpdsUserV2Mapper opdsUserV2Mapper;

    @Override
    public OpdsUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        OpdsUserV2Entity userV2 = opdsUserV2Repository.findByUsername(username)
                .orElseThrow(() -> ApiError.USER_NOT_FOUND.createException(username));
        OpdsUserV2 mappedCredential = opdsUserV2Mapper.toDto(userV2);
        return new OpdsUserDetails(mappedCredential);
    }
}