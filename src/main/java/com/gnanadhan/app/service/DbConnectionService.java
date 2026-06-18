package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.DbConnectionRequest;
import com.gnanadhan.app.dto.DbConnectionResponse;
import com.gnanadhan.app.dto.DbConnectionUpdateRequest;
import com.gnanadhan.app.entity.DbConnection;
import com.gnanadhan.app.entity.TeamDbConnection;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.mapper.DbConnectionMapper;
import com.gnanadhan.app.repository.DbConnectionRepository;
import com.gnanadhan.app.repository.ProjectRepository;
import com.gnanadhan.app.repository.TeamDbConnectionRepository;
import com.gnanadhan.app.repository.TeamMemberRepository;
import com.gnanadhan.app.repository.UserRepository;
import com.gnanadhan.app.service.security.SecretManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DbConnectionService {

    private final DbConnectionRepository repository;
    private final ProjectRepository projectRepository;
    private final TeamDbConnectionRepository teamDbConnectionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final SecretManager secretManager;
    private final DbConnectionMapper mapper;

    public DbConnectionResponse createConnection(DbConnectionRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        var project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!user.getRole().getName().equals("SUPER_ADMIN") && !project.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to create a connection in this project");
        }

        DbConnection entity = mapper.toEntity(request);
        entity.setEncryptedPassword(secretManager.encrypt(request.getPassword()));
        entity.setCreatedBy(user);
        entity.setProject(project);

        // Test connection before saving
        testConnection(request.getHost(), request.getPort(), request.getDatabaseName(), request.getUsername(), request.getPassword());

        DbConnection saved = repository.save(entity);
        return mapper.toResponse(saved);
    }

    public List<DbConnectionResponse> getAllConnections() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<DbConnection> connections;
        String roleName = user.getRole().getName();
        if (roleName.equals("SUPER_ADMIN")) {
            connections = repository.findAll();
        } else {
            connections = repository.findAccessibleConnections(user.getId());
        }

        return connections.stream()
                .map(conn -> {
                    DbConnectionResponse res = mapper.toResponse(conn);
                    res.setPermissionLevel(determinePermission(conn, user));
                    return res;
                })
                .collect(Collectors.toList());
    }

    public DbConnectionResponse getConnectionById(Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DbConnection connection = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));

        String roleName = user.getRole().getName();
        if (!roleName.equals("SUPER_ADMIN")) {
            boolean hasAccess = repository.findAccessibleConnections(user.getId()).stream()
                    .anyMatch(c -> c.getId().equals(id));
            if (!hasAccess) {
                throw new AccessDeniedException("You do not have permission to access this connection");
            }
        }

        DbConnectionResponse res = mapper.toResponse(connection);
        res.setPermissionLevel(determinePermission(connection, user));
        return res;
    }

    public DbConnectionResponse updateConnection(Long id, DbConnectionUpdateRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DbConnection existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));

        String perm = determinePermission(existing, user);
        if (!perm.equals("ADMIN") && !perm.equals("WRITE")) {
            throw new AccessDeniedException("You do not have permission to update this connection");
        }

        existing.setName(request.getName());
        existing.setHost(request.getHost());
        existing.setPort(request.getPort());
        existing.setDatabaseName(request.getDatabaseName());
        existing.setUsername(request.getUsername());
        existing.setEnvironment(request.getEnvironment());

        // Update password only if a new one is provided
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            testConnection(request.getHost(), request.getPort(), request.getDatabaseName(), request.getUsername(), request.getPassword());
            existing.setEncryptedPassword(secretManager.encrypt(request.getPassword()));
        } else {
            // Test with existing decrypted password
            String plainPassword = secretManager.decrypt(existing.getEncryptedPassword());
            testConnection(existing.getHost(), existing.getPort(), existing.getDatabaseName(), existing.getUsername(), plainPassword);
        }

        DbConnection saved = repository.save(existing);
        DbConnectionResponse res = mapper.toResponse(saved);
        res.setPermissionLevel(perm);
        return res;
    }

    public void deleteConnection(Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        DbConnection connection = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));

        String perm = determinePermission(connection, user);
        if (!perm.equals("ADMIN")) {
            throw new AccessDeniedException("You do not have permission to delete this connection");
        }

        repository.deleteById(id);
    }

    public void testSavedConnection(Long id) {
        DbConnection connection = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Connection not found"));
        String plainPassword = secretManager.decrypt(connection.getEncryptedPassword());
        testConnection(connection.getHost(), connection.getPort(), connection.getDatabaseName(), connection.getUsername(), plainPassword);
    }

    private void testConnection(String host, int port, String database, String username, String password) {
        String url = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            if (conn.isValid(5)) {
                log.info("Connection test successful for jdbc:postgresql://{}:{}/{}", host, port, database);
            } else {
                throw new RuntimeException("Connection is not valid");
            }
        } catch (Exception e) {
            log.error("Database connection failed", e);
            throw new RuntimeException("Database connection failed: " + e.getMessage());
        }
    }

    private String determinePermission(DbConnection connection, User user) {
        if (user.getRole().getName().equals("SUPER_ADMIN") || connection.getCreatedBy().getId().equals(user.getId())) {
            return "ADMIN";
        }
        List<TeamDbConnection> teamConns = teamDbConnectionRepository.findByDbConnectionId(connection.getId());
        boolean hasWrite = false;
        boolean hasRead = false;
        for (TeamDbConnection tc : teamConns) {
            boolean isMember = teamMemberRepository.existsByTeamIdAndUserId(tc.getTeam().getId(), user.getId());
            if (isMember) {
                if (tc.getPermissionLevel() != null) {
                    if (tc.getPermissionLevel().contains("ADMIN")) return "ADMIN";
                    if (tc.getPermissionLevel().contains("WRITE")) hasWrite = true;
                    if (tc.getPermissionLevel().contains("READ")) hasRead = true;
                }
            }
        }
        if (hasWrite) return "WRITE";
        if (hasRead) return "READ";
        return "NONE";
    }
}
