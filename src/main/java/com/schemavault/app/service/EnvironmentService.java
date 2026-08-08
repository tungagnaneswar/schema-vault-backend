package com.schemavault.app.service;

import com.schemavault.app.dto.EnvironmentRequest;
import com.schemavault.app.dto.EnvironmentResponse;
import com.schemavault.app.entity.Environment;
import com.schemavault.app.entity.Project;
import com.schemavault.app.mapper.EnvironmentMapper;
import com.schemavault.app.repository.EnvironmentRepository;
import com.schemavault.app.repository.ProjectRepository;
import com.schemavault.app.entity.User;
import com.schemavault.app.service.security.CurrentUserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final ProjectRepository projectRepository;
    private final EnvironmentMapper environmentMapper;
    private final ProjectService projectService;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> getEnvironmentsByProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        projectService.checkProjectAccess(project);

        return environmentRepository.findByProjectIdOrderBySequenceAsc(projectId).stream()
                .map(environmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public EnvironmentResponse createEnvironment(EnvironmentRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));

        projectService.checkProjectAccess(project); // Ensure user has access

        if (environmentRepository.existsByNameAndProjectId(request.getName(), project.getId())) {
            throw new IllegalArgumentException("Environment with this name already exists in the project");
        }

        Environment env = Environment.builder()
                .name(request.getName())
                .project(project)
                .sequence(request.getSequence() != null ? request.getSequence() : 0)
                .build();

        Environment saved = environmentRepository.save(env);
        User user = currentUserService.getCurrentUser();
        log.info("User {} created environment '{}' (ID: {}) in project ID: {}", user.getEmail(), saved.getName(),
                saved.getId(), project.getId());

        return environmentMapper.toResponse(saved);
    }

    @Transactional
    public void deleteEnvironment(Long id) {
        Environment env = environmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Environment not found"));

        projectService.checkProjectAccess(env.getProject());
        environmentRepository.delete(env);

        User user = currentUserService.getCurrentUser();
        log.info("User {} deleted environment ID: {} from project ID: {}", user.getEmail(), id,
                env.getProject().getId());
    }
}
