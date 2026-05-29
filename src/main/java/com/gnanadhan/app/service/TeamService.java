package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.TeamDbConnectionRequest;
import com.gnanadhan.app.dto.TeamMemberRequest;
import com.gnanadhan.app.dto.TeamRequest;
import com.gnanadhan.app.dto.TeamResponse;
import com.gnanadhan.app.entity.DbConnection;
import com.gnanadhan.app.entity.Role;
import com.gnanadhan.app.dto.CreateTeamMemberRequest;
import com.gnanadhan.app.entity.Team;
import com.gnanadhan.app.entity.TeamDbConnection;
import com.gnanadhan.app.entity.TeamMember;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.mapper.TeamMapper;
import com.gnanadhan.app.repository.DbConnectionRepository;
import com.gnanadhan.app.repository.RoleRepository;
import com.gnanadhan.app.repository.TeamDbConnectionRepository;
import com.gnanadhan.app.repository.TeamMemberRepository;
import com.gnanadhan.app.repository.TeamRepository;
import com.gnanadhan.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamDbConnectionRepository teamDbConnectionRepository;
    private final UserRepository userRepository;
    private final DbConnectionRepository dbConnectionRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TeamMapper teamMapper;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    @Transactional
    public TeamResponse createTeam(TeamRequest request) {
        if (teamRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Team with this name already exists");
        }

        User currentUser = getCurrentUser();
        Team team = teamMapper.toEntity(request);
        team.setCreatedBy(currentUser);
        Team savedTeam = teamRepository.save(team);

        // Optional: auto-add the creator to the team
        TeamMember member = TeamMember.builder()
                .team(savedTeam)
                .user(currentUser)
                .build();
        teamMemberRepository.save(member);

        return teamMapper.toResponse(savedTeam);
    }

    public List<TeamResponse> getAllTeams() {
        return teamRepository.findAll().stream()
                .map(teamMapper::toResponse)
                .collect(Collectors.toList());
    }

    public TeamResponse getTeamById(Long id) {
        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
        return teamMapper.toResponse(team);
    }

    @Transactional
    public void addMemberToTeam(Long teamId, TeamMemberRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new IllegalArgumentException("User is already a member of this team");
        }

        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .build();
        teamMemberRepository.save(member);
    }

    @Transactional
    public void createTeamMember(Long teamId, CreateTeamMemberRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("User with this email already exists");
        }

        Role role = roleRepository.findByName(request.getRoleName())
                .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + request.getRoleName()));

        User newUser = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .isActive(true)
                .build();
        User savedUser = userRepository.save(newUser);

        TeamMember member = TeamMember.builder()
                .team(team)
                .user(savedUser)
                .build();
        teamMemberRepository.save(member);
    }

    @Transactional
    public void removeMemberFromTeam(Long teamId, Long userId) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this team"));
        
        teamMemberRepository.delete(member);
    }

    @Transactional
    public void addDbConnectionToTeam(Long teamId, TeamDbConnectionRequest request) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        DbConnection dbConnection = dbConnectionRepository.findById(request.getDbConnectionId())
                .orElseThrow(() -> new ResourceNotFoundException("DbConnection not found"));

        TeamDbConnection teamDbConnection = teamDbConnectionRepository
                .findByTeamIdAndDbConnectionId(teamId, dbConnection.getId())
                .orElse(null);

        if (teamDbConnection != null) {
            teamDbConnection.setPermissionLevel(request.getPermissionLevel());
        } else {
            teamDbConnection = TeamDbConnection.builder()
                    .team(team)
                    .dbConnection(dbConnection)
                    .permissionLevel(request.getPermissionLevel())
                    .build();
        }
        
        teamDbConnectionRepository.save(teamDbConnection);
    }

    @Transactional
    public void removeDbConnectionFromTeam(Long teamId, Long connectionId) {
        TeamDbConnection teamDbConnection = teamDbConnectionRepository
                .findByTeamIdAndDbConnectionId(teamId, connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Database connection is not assigned to this team"));
        
        teamDbConnectionRepository.delete(teamDbConnection);
    }

    // Helper methods for fetching members and connections
    public List<Map<String, Object>> getTeamMembers(Long teamId) {
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(tm -> Map.of(
                        "userId", (Object) tm.getUser().getId(),
                        "email", tm.getUser().getEmail(),
                        "role", tm.getUser().getRole().getName(),
                        "joinedAt", tm.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTeamDbConnections(Long teamId) {
        return teamDbConnectionRepository.findByTeamId(teamId).stream()
                .map(tc -> Map.of(
                        "connectionId", (Object) tc.getDbConnection().getId(),
                        "name", tc.getDbConnection().getName(),
                        "environment", tc.getDbConnection().getEnvironment(),
                        "permissionLevel", tc.getPermissionLevel(),
                        "assignedAt", tc.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAvailableUsers() {
        return userRepository.findAll().stream()
                .filter(User::getIsActive)
                .map(u -> Map.of(
                        "id", (Object) u.getId(),
                        "email", u.getEmail(),
                        "role", u.getRole().getName()
                ))
                .collect(Collectors.toList());
    }
}
