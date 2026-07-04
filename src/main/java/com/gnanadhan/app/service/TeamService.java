package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.MemberProjectRequest;
import com.gnanadhan.app.dto.TeamDbConnectionRequest;
import com.gnanadhan.app.dto.TeamMemberRequest;
import com.gnanadhan.app.dto.TeamRequest;
import com.gnanadhan.app.dto.TeamResponse;
import com.gnanadhan.app.dto.UpdateTeamRoleRequest;
import com.gnanadhan.app.entity.DbConnection;
import com.gnanadhan.app.entity.MemberProjectAssignment;
import com.gnanadhan.app.entity.Team;
import com.gnanadhan.app.entity.TeamDbConnection;
import com.gnanadhan.app.entity.TeamMember;
import com.gnanadhan.app.entity.TeamRole;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.exception.UnauthorizedException;
import com.gnanadhan.app.mapper.TeamMapper;
import com.gnanadhan.app.repository.DbConnectionRepository;
import com.gnanadhan.app.repository.MemberProjectAssignmentRepository;
import com.gnanadhan.app.repository.TeamDbConnectionRepository;
import com.gnanadhan.app.repository.TeamMemberRepository;
import com.gnanadhan.app.repository.TeamRepository;
import com.gnanadhan.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final MemberProjectAssignmentRepository memberProjectAssignmentRepository;
    private final UserRepository userRepository;
    private final DbConnectionRepository dbConnectionRepository;
    private final TeamMapper teamMapper;
    private final DbConnectionService dbConnectionService;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));
    }

    private boolean isGlobalAdmin(User user) {
        String role = user.getRole().getName();
        return "SUPER_ADMIN".equals(role) || "ADMIN".equals(role) || "DEVOPS_ADMIN".equals(role);
    }

    private void checkManageTeam(Long teamId, User currentUser) {
        if (isGlobalAdmin(currentUser)) return;
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, currentUser.getId())
                .orElseThrow(() -> new UnauthorizedException("You do not have permission to manage this team"));
        if (member.getTeamRole() != TeamRole.TEAM_OWNER && member.getTeamRole() != TeamRole.TEAM_ADMIN) {
            throw new UnauthorizedException("Only TEAM_OWNER or TEAM_ADMIN can manage this team");
        }
    }

    private void checkOwnerOnly(Long teamId, User currentUser) {
        if (isGlobalAdmin(currentUser)) return;
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, currentUser.getId())
                .orElseThrow(() -> new UnauthorizedException("You do not have permission to perform this action"));
        if (member.getTeamRole() != TeamRole.TEAM_OWNER) {
            throw new UnauthorizedException("Only TEAM_OWNER can perform this action");
        }
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

        TeamMember member = TeamMember.builder()
                .team(savedTeam)
                .user(currentUser)
                .teamRole(TeamRole.TEAM_OWNER)
                .addedBy(currentUser)
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
    public TeamResponse updateTeam(Long id, TeamRequest request) {
        User currentUser = getCurrentUser();
        checkManageTeam(id, currentUser);

        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        if (!team.getName().equals(request.getName()) && teamRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Team with this name already exists");
        }

        team.setName(request.getName());
        team.setDescription(request.getDescription());
        
        Team updatedTeam = teamRepository.save(team);
        return teamMapper.toResponse(updatedTeam);
    }

    @Transactional
    public void deleteTeam(Long id) {
        User currentUser = getCurrentUser();
        if (!isGlobalAdmin(currentUser)) {
            checkOwnerOnly(id, currentUser);
        }

        Team team = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
        
        teamRepository.delete(team);
    }

    @Transactional
    public void addMemberToTeam(Long teamId, TeamMemberRequest request) {
        User currentUser = getCurrentUser();
        checkManageTeam(teamId, currentUser);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new IllegalArgumentException("User is already a member of this team");
        }

        TeamRole role = TeamRole.TEAM_MEMBER;
        if (request.getTeamRole() != null) {
            try {
                role = TeamRole.valueOf(request.getTeamRole());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid team role");
            }
            if (role == TeamRole.TEAM_OWNER) {
                checkOwnerOnly(teamId, currentUser);
            }
        }

        TeamMember member = TeamMember.builder()
                .team(team)
                .user(user)
                .teamRole(role)
                .addedBy(currentUser)
                .build();
        teamMemberRepository.save(member);
    }

    @Transactional
    public void updateMemberRole(Long teamId, Long userId, UpdateTeamRoleRequest request) {
        User currentUser = getCurrentUser();
        
        TeamMember targetMember = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this team"));

        TeamRole newRole;
        try {
            newRole = TeamRole.valueOf(request.getTeamRole());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid team role");
        }
        
        TeamRole currentRole = targetMember.getTeamRole();
        if (currentRole == newRole) return;

        if (currentUser.getId().equals(userId)) {
            throw new IllegalArgumentException("Users cannot change their own role");
        }

        if (newRole == TeamRole.TEAM_OWNER || currentRole == TeamRole.TEAM_OWNER) {
            checkOwnerOnly(teamId, currentUser);
        } else {
            checkManageTeam(teamId, currentUser);
        }

        if (currentRole == TeamRole.TEAM_OWNER) {
            long ownerCount = teamMemberRepository.countByTeamIdAndTeamRole(teamId, TeamRole.TEAM_OWNER);
            if (ownerCount <= 1) {
                throw new IllegalArgumentException("Cannot downgrade the last TEAM_OWNER");
            }
        }

        targetMember.setTeamRole(newRole);
        teamMemberRepository.save(targetMember);
    }

    @Transactional
    public void removeMemberFromTeam(Long teamId, Long userId) {
        User currentUser = getCurrentUser();
        
        TeamMember targetMember = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this team"));

        if (targetMember.getTeamRole() == TeamRole.TEAM_OWNER) {
            checkOwnerOnly(teamId, currentUser);
            long ownerCount = teamMemberRepository.countByTeamIdAndTeamRole(teamId, TeamRole.TEAM_OWNER);
            if (ownerCount <= 1) {
                throw new IllegalArgumentException("Cannot remove the last TEAM_OWNER");
            }
        } else {
            checkManageTeam(teamId, currentUser);
        }

        memberProjectAssignmentRepository.deleteByTeamMemberId(targetMember.getId());
        teamMemberRepository.delete(targetMember);
    }

    @Transactional
    public void addDbConnectionToTeam(Long teamId, TeamDbConnectionRequest request) {
        User currentUser = getCurrentUser();
        checkManageTeam(teamId, currentUser);

        String reqPermission = request.getPermissionLevel();
        if (reqPermission == null || (!reqPermission.equals("READ") && !reqPermission.equals("WRITE") && !reqPermission.equals("ADMIN"))) {
            throw new IllegalArgumentException("Permission level must be READ, WRITE, or ADMIN");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        DbConnection dbConnection = dbConnectionRepository.findById(request.getDbConnectionId())
                .orElseThrow(() -> new ResourceNotFoundException("DbConnection not found"));

        String userDbPermission = dbConnectionService.determinePermission(dbConnection, currentUser);
        if (!"ADMIN".equals(userDbPermission)) {
            throw new UnauthorizedException("You must have ADMIN access to this database connection to assign it to a team.");
        }

        TeamDbConnection teamDbConnection = teamDbConnectionRepository
                .findByTeamIdAndDbConnectionId(teamId, dbConnection.getId())
                .orElse(null);

        if (teamDbConnection != null) {
            teamDbConnection.setPermissionLevel(reqPermission);
        } else {
            teamDbConnection = TeamDbConnection.builder()
                    .team(team)
                    .dbConnection(dbConnection)
                    .permissionLevel(reqPermission)
                    .build();
        }
        
        teamDbConnectionRepository.save(teamDbConnection);
    }

    @Transactional
    public void removeDbConnectionFromTeam(Long teamId, Long connectionId) {
        User currentUser = getCurrentUser();
        checkManageTeam(teamId, currentUser);

        TeamDbConnection teamDbConnection = teamDbConnectionRepository
                .findByTeamIdAndDbConnectionId(teamId, connectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Database connection is not assigned to this team"));
        
        teamDbConnectionRepository.delete(teamDbConnection);
    }

    @Transactional
    public void assignProjectToMember(Long teamId, Long userId, MemberProjectRequest request) {
        User currentUser = getCurrentUser();
        checkManageTeam(teamId, currentUser);

        String reqPermission = request.getPermission();
        if (reqPermission == null || (!reqPermission.equals("READ") && !reqPermission.equals("WRITE") && !reqPermission.equals("ADMIN"))) {
            throw new IllegalArgumentException("Permission level must be READ, WRITE, or ADMIN");
        }

        TeamMember targetMember = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this team"));

        // Validate project is assigned to team
        TeamDbConnection teamDbConnection = teamDbConnectionRepository
                .findByTeamIdAndDbConnectionId(teamId, request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Database connection must be assigned to the team first"));

        MemberProjectAssignment assignment = memberProjectAssignmentRepository
                .findByTeamMemberIdAndProjectId(targetMember.getId(), request.getProjectId())
                .orElse(null);

        if (assignment != null) {
            assignment.setPermission(reqPermission);
        } else {
            assignment = MemberProjectAssignment.builder()
                    .teamMember(targetMember)
                    .project(teamDbConnection.getDbConnection())
                    .permission(reqPermission)
                    .build();
        }

        memberProjectAssignmentRepository.save(assignment);
    }

    @Transactional
    public void removeProjectFromMember(Long teamId, Long userId, Long projectId) {
        User currentUser = getCurrentUser();
        checkManageTeam(teamId, currentUser);

        TeamMember targetMember = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this team"));

        memberProjectAssignmentRepository.deleteByTeamMemberIdAndProjectId(targetMember.getId(), projectId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMemberProjects(Long teamId, Long userId) {
        TeamMember targetMember = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of this team"));

        return memberProjectAssignmentRepository.findByTeamMemberId(targetMember.getId()).stream()
                .map(assignment -> Map.of(
                        "projectId", (Object) assignment.getProject().getId(),
                        "name", assignment.getProject().getName(),
                        "environment", assignment.getProject().getEnvironment().getName(),
                        "permission", assignment.getPermission(),
                        "assignedAt", assignment.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTeamMembers(Long teamId) {
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(tm -> Map.of(
                        "userId", (Object) tm.getUser().getId(),
                        "email", tm.getUser().getEmail(),
                        "role", tm.getUser().getRole().getName(),
                        "teamRole", tm.getTeamRole().name(),
                        "joinedAt", tm.getCreatedAt(),
                        "addedBy", tm.getAddedBy() != null ? tm.getAddedBy().getEmail() : "System"
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTeamDbConnections(Long teamId) {
        return teamDbConnectionRepository.findByTeamId(teamId).stream()
                .map(tc -> Map.of(
                        "connectionId", (Object) tc.getDbConnection().getId(),
                        "name", tc.getDbConnection().getName(),
                        "environment", tc.getDbConnection().getEnvironment().getName(),
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
