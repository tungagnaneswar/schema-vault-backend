package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.AuditLogResponse;
import com.gnanadhan.app.dto.PageResponse;
import com.gnanadhan.app.dto.UserResponse;
import com.gnanadhan.app.entity.AuditLog;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.repository.AuditLogRepository;
import com.gnanadhan.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    // ── Users ────────────────────────────────────────────────────────────────

    public PageResponse<UserResponse> getAllUsers(int page, int size) {
        int pageNum = Math.max(0, page);
        int sizeNum = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(pageNum, sizeNum);
        Page<User> userPage = userRepository.findAllByOrderByCreatedAtDesc(pageable);

        List<UserResponse> content = userPage.getContent().stream()
                .map(this::toUserResponse)
                .toList();

        return PageResponse.<UserResponse>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }

    @Transactional
    public UserResponse toggleUserActive(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setIsActive(!user.getIsActive());
        userRepository.save(user);
        return toUserResponse(user);
    }

    // ── Audit Logs ───────────────────────────────────────────────────────────

    public PageResponse<AuditLogResponse> getAllLogs(int page, int size) {
        int pageNum = Math.max(0, page);
        int sizeNum = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(pageNum, sizeNum);
        Page<AuditLog> logPage = auditLogRepository.findAllByOrderByTimestampDesc(pageable);

        List<AuditLogResponse> content = logPage.getContent().stream()
                .map(this::toLogResponse)
                .toList();

        return PageResponse.<AuditLogResponse>builder()
                .content(content)
                .page(logPage.getNumber())
                .size(logPage.getSize())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .first(logPage.isFirst())
                .last(logPage.isLast())
                .build();
    }

    // ── Mappers ──────────────────────────────────────────────────────────────

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().getName())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private AuditLogResponse toLogResponse(AuditLog log) {
        String email = log.getUser() != null ? log.getUser().getEmail() : "System";
        return AuditLogResponse.builder()
                .id(log.getId())
                .userEmail(email)
                .action(log.getAction())
                .ipAddress(log.getIpAddress())
                .deviceInfo(log.getDeviceInfo())
                .timestamp(log.getTimestamp())
                .details(log.getDetails())
                .build();
    }
}
