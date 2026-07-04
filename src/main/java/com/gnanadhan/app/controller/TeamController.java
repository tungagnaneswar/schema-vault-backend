package com.gnanadhan.app.controller;

import com.gnanadhan.app.dto.CreateTeamMemberRequest;
import com.gnanadhan.app.dto.MemberProjectRequest;
import com.gnanadhan.app.dto.TeamDbConnectionRequest;
import com.gnanadhan.app.dto.TeamMemberRequest;
import com.gnanadhan.app.dto.TeamRequest;
import com.gnanadhan.app.dto.TeamResponse;
import com.gnanadhan.app.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public ResponseEntity<TeamResponse> createTeam(@Valid @RequestBody TeamRequest request) {
        return new ResponseEntity<>(teamService.createTeam(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<TeamResponse>> getAllTeams() {
        return ResponseEntity.ok(teamService.getAllTeams());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeamResponse> getTeamById(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeamResponse> updateTeam(@PathVariable Long id, @Valid @RequestBody TeamRequest request) {
        return ResponseEntity.ok(teamService.updateTeam(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.ok(Map.of("message", "Team deleted successfully"));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<?> addMemberToTeam(@PathVariable Long id, @Valid @RequestBody TeamMemberRequest request) {
        teamService.addMemberToTeam(id, request);
        return ResponseEntity.ok(Map.of("message", "Member added successfully"));
    }

    @PatchMapping("/{id}/members/{userId}/role")
    public ResponseEntity<?> updateMemberRole(@PathVariable Long id, @PathVariable Long userId, @Valid @RequestBody com.gnanadhan.app.dto.UpdateTeamRoleRequest request) {
        teamService.updateMemberRole(id, userId, request);
        return ResponseEntity.ok(Map.of("message", "Member role updated successfully"));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ResponseEntity<?> removeMemberFromTeam(@PathVariable Long id, @PathVariable Long userId) {
        teamService.removeMemberFromTeam(id, userId);
        return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
    }

    @GetMapping("/{id}/members/{userId}/projects")
    public ResponseEntity<List<Map<String, Object>>> getMemberProjects(@PathVariable Long id, @PathVariable Long userId) {
        return ResponseEntity.ok(teamService.getMemberProjects(id, userId));
    }

    @PostMapping("/{id}/members/{userId}/projects")
    public ResponseEntity<?> assignProjectToMember(@PathVariable Long id, @PathVariable Long userId, @Valid @RequestBody MemberProjectRequest request) {
        teamService.assignProjectToMember(id, userId, request);
        return ResponseEntity.ok(Map.of("message", "Project assigned successfully"));
    }

    @DeleteMapping("/{id}/members/{userId}/projects/{projectId}")
    public ResponseEntity<?> removeProjectFromMember(@PathVariable Long id, @PathVariable Long userId, @PathVariable Long projectId) {
        teamService.removeProjectFromMember(id, userId, projectId);
        return ResponseEntity.ok(Map.of("message", "Project removed successfully"));
    }

    @PostMapping("/{id}/connections")
    public ResponseEntity<?> addDbConnectionToTeam(@PathVariable Long id, @Valid @RequestBody TeamDbConnectionRequest request) {
        teamService.addDbConnectionToTeam(id, request);
        return ResponseEntity.ok(Map.of("message", "Database connection assigned successfully"));
    }

    @DeleteMapping("/{id}/connections/{connectionId}")
    public ResponseEntity<?> removeDbConnectionFromTeam(@PathVariable Long id, @PathVariable Long connectionId) {
        teamService.removeDbConnectionFromTeam(id, connectionId);
        return ResponseEntity.ok(Map.of("message", "Database connection removed successfully"));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<Map<String, Object>>> getTeamMembers(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamMembers(id));
    }

    @GetMapping("/{id}/connections")
    public ResponseEntity<List<Map<String, Object>>> getTeamDbConnections(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamDbConnections(id));
    }

    @GetMapping("/users/available")
    public ResponseEntity<List<Map<String, Object>>> getAvailableUsers() {
        return ResponseEntity.ok(teamService.getAvailableUsers());
    }
}
