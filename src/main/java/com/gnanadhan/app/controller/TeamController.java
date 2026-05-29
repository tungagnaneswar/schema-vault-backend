package com.gnanadhan.app.controller;

import com.gnanadhan.app.dto.CreateTeamMemberRequest;
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
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DEVOPS_ADMIN')")
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

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DEVOPS_ADMIN')")
    public ResponseEntity<?> addMemberToTeam(@PathVariable Long id, @Valid @RequestBody TeamMemberRequest request) {
        teamService.addMemberToTeam(id, request);
        return ResponseEntity.ok(Map.of("message", "Member added successfully"));
    }

    @PostMapping("/{id}/members/create")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DEVOPS_ADMIN')")
    public ResponseEntity<?> createTeamMember(@PathVariable Long id, @Valid @RequestBody CreateTeamMemberRequest request) {
        teamService.createTeamMember(id, request);
        return ResponseEntity.ok(Map.of("message", "Member created and added successfully"));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DEVOPS_ADMIN')")
    public ResponseEntity<?> removeMemberFromTeam(@PathVariable Long id, @PathVariable Long userId) {
        teamService.removeMemberFromTeam(id, userId);
        return ResponseEntity.ok(Map.of("message", "Member removed successfully"));
    }

    @PostMapping("/{id}/connections")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DEVOPS_ADMIN')")
    public ResponseEntity<?> addDbConnectionToTeam(@PathVariable Long id, @Valid @RequestBody TeamDbConnectionRequest request) {
        teamService.addDbConnectionToTeam(id, request);
        return ResponseEntity.ok(Map.of("message", "Database connection assigned successfully"));
    }

    @DeleteMapping("/{id}/connections/{connectionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DEVOPS_ADMIN')")
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DEVOPS_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAvailableUsers() {
        return ResponseEntity.ok(teamService.getAvailableUsers());
    }
}
