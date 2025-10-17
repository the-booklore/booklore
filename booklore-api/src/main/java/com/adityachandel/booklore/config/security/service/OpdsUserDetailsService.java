package com.adityachandel.booklore.config.security.service;

import com.adityachandel.booklore.config.security.userdetails.OpdsUserDetails;
import com.adityachandel.booklore.exception.ApiError;
import com.adityachandel.booklore.mapper.OpdsUserV2Mapper;
import com.adityachandel.booklore.model.dto.OpdsUserV2;
import com.adityachandel.booklore.model.entity.OpdsUserV2Entity;
import com.adityachandel.booklore.repository.OpdsUserV2Repository;
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
        return new OpdsUserDetails(mappedCredential, null);
    }
}