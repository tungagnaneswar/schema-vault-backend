package com.gnanadhan.app.aspect;

import com.gnanadhan.app.entity.AuditLog;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.repository.AuditLogRepository;
import com.gnanadhan.app.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLoggingAspect {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) && !within(com.gnanadhan.app.controller.AuthController)")
    public void controllerMethods() {}

    @AfterReturning(pointcut = "controllerMethods()")
    public void logAfter(JoinPoint joinPoint) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                return;
            }

            String username = authentication.getName();
            Optional<User> userOpt = userRepository.findByEmail(username);

            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String ipAddress = request.getRemoteAddr();
            String userAgent = request.getHeader("User-Agent");
            String action = request.getMethod() + " " + request.getRequestURI();

            AuditLog logEntry = AuditLog.builder()
                    .user(userOpt.orElse(null))
                    .action(action)
                    .ipAddress(ipAddress)
                    .deviceInfo(userAgent)
                    .details("Executed: " + joinPoint.getSignature().toShortString())
                    .build();

            auditLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to save audit log", e);
        }
    }
}
