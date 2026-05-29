package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.AuthRequest;
import com.gnanadhan.app.dto.AuthResponse;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.UnauthorizedException;
import com.gnanadhan.app.repository.UserRepository;
import com.gnanadhan.app.repository.RoleRepository;
import com.gnanadhan.app.entity.Role;
import com.gnanadhan.app.dto.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.gnanadhan.app.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse login(AuthRequest authRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(authRequest.getEmail(), authRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String accessToken = tokenProvider.generateToken(authentication);
        String refreshToken = tokenProvider.generateRefreshToken(authentication);

        User user = userRepository.findByEmail(authRequest.getEmail())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .email(user.getEmail())
                .role(user.getRole().getName())
                .build();
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        Role userRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(userRole)
                .isActive(true)
                .build();

        userRepository.save(user);

        AuthRequest loginRequest = new AuthRequest();
        loginRequest.setEmail(request.getEmail());
        loginRequest.setPassword(request.getPassword());
        return login(loginRequest);
    }

    public AuthResponse refreshToken(String refreshToken) {
        if (tokenProvider.validateToken(refreshToken)) {
            String username = tokenProvider.getUsernameFromToken(refreshToken);
            
            User user = userRepository.findByEmail(username)
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            String newAccessToken = tokenProvider.generateTokenFromUsername(username);

            return AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(refreshToken) // Reusing the same refresh token until it expires
                    .email(user.getEmail())
                    .role(user.getRole().getName())
                    .build();
        }
        throw new UnauthorizedException("Invalid refresh token");
    }

    public void forgotPassword(String email) {
        // Mocking the OTP flow. In a real app, generate OTP, save to DB, and send via Email.
        boolean exists = userRepository.existsByEmail(email);
        if (exists) {
            log.info("Mock OTP generated for {}: 123456", email);
        } else {
            log.warn("Forgot password requested for non-existent email: {}", email);
        }
    }
}
