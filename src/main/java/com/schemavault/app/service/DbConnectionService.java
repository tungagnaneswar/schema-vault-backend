package com.schemavault.app.service;

import com.schemavault.app.dto.DbConnectionRequest;
import com.schemavault.app.dto.DbConnectionResponse;
import com.schemavault.app.dto.DbConnectionUpdateRequest;
import com.schemavault.app.entity.DatabaseEngine;
import com.schemavault.app.entity.DbConnection;
import com.schemavault.app.entity.TeamDbConnection;
import com.schemavault.app.entity.User;
import com.schemavault.app.exception.ResourceNotFoundException;
import com.schemavault.app.mapper.DbConnectionMapper;
import com.schemavault.app.repository.DbConnectionRepository;
import com.schemavault.app.repository.EnvironmentRepository;
import com.schemavault.app.entity.Environment;
import com.schemavault.app.entity.Project;
import com.schemavault.app.repository.TeamDbConnectionRepository;
import com.schemavault.app.repository.TeamMemberRepository;
import com.schemavault.app.repository.UserRepository;
import com.schemavault.app.service.security.SecretManager;
import com.schemavault.app.service.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbConnectionService {

    private final DbConnectionRepository repository;
    private final EnvironmentRepository environmentRepository;
    private final TeamDbConnectionRepository teamDbConnectionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final ConnectionTestingService connectionTestingService;
    private final SecretManager secretManager;
    private final DbConnectionMapper mapper;

    @Transactional
    public DbConnectionResponse createConnection(DbConnectionRequest request) {
        User user = currentUserService.getCurrentUser();

        Environment env = environmentRepository.findById(request.getEnvironmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found"));
        Project project = env.getProject();

        if (!user.getRole().getName().equals("SUPER_ADMIN") && !project.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to create a connection in this project");
        }

        DbConnection entity = mapper.toEntity(request);
        entity.setEncryptedPassword(secretManager.encrypt(request.getPassword()));
        entity.setCreatedBy(user);
        entity.setEnvironment(env);

        // Test connection before saving
        connectionTestingService.testConnection(request.getEngine(), request.getHost(), request.getPort(),
                request.getDatabaseName(), request.getUsername(), request.getPassword());

        DbConnection saved = repository.save(entity);
        log.info("User {} created database connection '{}' (ID: {}) in environment ID: {}", user.getEmail(),
                saved.getName(), saved.getId(), env.getId());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<DbConnectionResponse> getAllConnections(Pageable pageable) {
        User user = currentUserService.getCurrentUser();

        Page<DbConnection> connections;
        String roleName = user.getRole().getName();
        if (roleName.equals("SUPER_ADMIN")) {
            connections = repository.findAll(pageable);
        } else {
            connections = repository.findAccessibleConnections(user.getId(), pageable);
        }

        return connections.map(conn -> {
            DbConnectionResponse res = mapper.toResponse(conn);
            res.setPermissionLevel(determinePermission(conn, user));
            return res;
        });
    }

    @Transactional(readOnly = true)
    public DbConnectionResponse getConnectionById(Long id) {
        User user = currentUserService.getCurrentUser();

        DbConnection connection = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));

        String roleName = user.getRole().getName();
        if (!roleName.equals("SUPER_ADMIN")) {
            boolean hasAccess = repository
                    .findAccessibleConnections(user.getId(), org.springframework.data.domain.Pageable.unpaged())
                    .stream()
                    .anyMatch(c -> c.getId().equals(id));
            if (!hasAccess) {
                throw new AccessDeniedException("You do not have permission to access this connection");
            }
        }

        DbConnectionResponse res = mapper.toResponse(connection);
        res.setPermissionLevel(determinePermission(connection, user));
        return res;
    }

    @Transactional
    public DbConnectionResponse updateConnection(Long id, DbConnectionUpdateRequest request) {
        User user = currentUserService.getCurrentUser();

        DbConnection existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));

        String perm = determinePermission(existing, user);
        if (!perm.equals("ADMIN") && !perm.equals("WRITE")) {
            throw new AccessDeniedException("You do not have permission to update this connection");
        }

        Environment env = environmentRepository.findById(request.getEnvironmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Environment not found"));

        existing.setName(request.getName());
        existing.setHost(request.getHost());
        existing.setPort(request.getPort());
        existing.setDatabaseName(request.getDatabaseName());
        existing.setUsername(request.getUsername());
        existing.setEnvironment(env);
        existing.setEngine(request.getEngine());

        // Update password only if a new one is provided
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            connectionTestingService.testConnection(request.getEngine(), request.getHost(), request.getPort(),
                    request.getDatabaseName(), request.getUsername(), request.getPassword());
            existing.setEncryptedPassword(secretManager.encrypt(request.getPassword()));
        } else {
            // Test with existing decrypted password
            String plainPassword = secretManager.decrypt(existing.getEncryptedPassword());
            connectionTestingService.testConnection(existing.getEngine(), existing.getHost(), existing.getPort(),
                    existing.getDatabaseName(), existing.getUsername(), plainPassword);
        }

        DbConnection saved = repository.save(existing);
        log.info("User {} updated database connection '{}' (ID: {})", user.getEmail(), saved.getName(), saved.getId());

        DbConnectionResponse res = mapper.toResponse(saved);
        res.setPermissionLevel(perm);
        return res;
    }

    @Transactional
    public void deleteConnection(Long id) {
        User user = currentUserService.getCurrentUser();

        DbConnection connection = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));

        String perm = determinePermission(connection, user);
        if (!perm.equals("ADMIN")) {
            throw new AccessDeniedException("You do not have permission to delete this connection");
        }

        repository.deleteById(id);
        log.info("User {} deleted database connection ID: {}", user.getEmail(), id);
    }

    @Transactional(readOnly = true)
    public void testSavedConnection(Long id) {
        DbConnection connection = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        String plainPassword = secretManager.decrypt(connection.getEncryptedPassword());
        connectionTestingService.testConnection(connection.getEngine(), connection.getHost(), connection.getPort(),
                connection.getDatabaseName(), connection.getUsername(), plainPassword);
    }

    public String determinePermission(DbConnection connection, User user) {
        if (user.getRole().getName().equals("SUPER_ADMIN") ||
                connection.getCreatedBy().getId().equals(user.getId())) {
            return "ADMIN";
        }

        List<TeamDbConnection> teamConns = teamDbConnectionRepository.findByDbConnectionId(connection.getId());
        boolean hasWrite = false;
        boolean hasRead = false;

        for (TeamDbConnection tc : teamConns) {
            boolean isMember = teamMemberRepository.existsByTeamIdAndUserId(tc.getTeam().getId(), user.getId());
            if (isMember && tc.getPermissionLevel() != null) {
                if (tc.getPermissionLevel().contains("ADMIN"))
                    return "ADMIN";
                if (tc.getPermissionLevel().contains("WRITE"))
                    hasWrite = true;
                if (tc.getPermissionLevel().contains("READ"))
                    hasRead = true;
            }
        }

        if (hasWrite)
            return "WRITE";
        if (hasRead)
            return "READ";
        return "NONE";
    }
}
