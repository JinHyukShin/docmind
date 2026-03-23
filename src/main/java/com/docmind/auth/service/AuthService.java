package com.docmind.auth.service;

import com.docmind.auth.dto.LoginRequest;
import com.docmind.auth.dto.RefreshRequest;
import com.docmind.auth.dto.SignupRequest;
import com.docmind.auth.dto.TokenResponse;
import com.docmind.auth.entity.RefreshToken;
import com.docmind.auth.entity.User;
import com.docmind.auth.repository.RefreshTokenRepository;
import com.docmind.auth.repository.UserRepository;
import com.docmind.global.exception.BusinessException;
import com.docmind.global.exception.ErrorCode;
import com.docmind.global.security.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtProvider jwtProvider,
                       PasswordEncoder passwordEncoder,
                       @Value("${jwt.access-token-expiry-ms}") long accessTokenExpiryMs,
                       @Value("${jwt.refresh-token-expiry-ms}") long refreshTokenExpiryMs) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProvider = jwtProvider;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenExpiryMs = accessTokenExpiryMs;
        this.refreshTokenExpiryMs = refreshTokenExpiryMs;
    }

    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE, "Email already exists");
        }

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(request.email(), passwordHash, request.name());
        userRepository.save(user);

        return issueTokens(user);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Account is disabled");
        }

        return issueTokens(user);
    }

    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token"));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token expired");
        }

        // Rotation: delete old, issue new
        refreshTokenRepository.delete(stored);
        return issueTokens(stored.getUser());
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(refreshTokenRepository::delete);
    }

    public User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found"));
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(
                user.getId().toString(),
                Map.of("role", user.getRole(), "email", user.getEmail())
        );
        String refreshToken = jwtProvider.createRefreshToken(user.getId().toString());

        RefreshToken entity = RefreshToken.create(
                user,
                refreshToken,
                Instant.now().plusMillis(refreshTokenExpiryMs)
        );
        refreshTokenRepository.save(entity);

        return new TokenResponse(accessToken, refreshToken, accessTokenExpiryMs / 1000);
    }
}
