package com.adityachandel.booklore.config.security.filter;

import com.adityachandel.booklore.config.security.JwtUtils;
import com.adityachandel.booklore.config.security.userdetails.UserAuthenticationDetails;
import com.adityachandel.booklore.mapper.custom.BookLoreUserTransformer;
import com.adityachandel.booklore.model.dto.BookLoreUser;
import com.adityachandel.booklore.model.entity.BookLoreUserEntity;
import com.adityachandel.booklore.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@AllArgsConstructor
@Component
public class CoverJwtFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final BookLoreUserTransformer bookLoreUserTransformer;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/media/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String token = request.getParameter("token");
        if (token != null && jwtUtils.validateToken(token)) {
            Long userId = jwtUtils.extractUserId(token);
            BookLoreUserEntity bookLoreUserEntity = userRepository.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found"));
            BookLoreUser bookLoreUser = bookLoreUserTransformer.toDTO(bookLoreUserEntity);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(bookLoreUser, null, null);
            authentication.setDetails(new UserAuthenticationDetails(request, bookLoreUser.getId()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid token");
        }
    }
}