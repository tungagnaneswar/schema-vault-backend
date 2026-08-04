package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.ProjectRequest;
import com.gnanadhan.app.dto.ProjectResponse;
import com.gnanadhan.app.entity.Project;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.mapper.ProjectMapper;
import com.gnanadhan.app.repository.ProjectRepository;
import com.gnanadhan.app.entity.Environment;
import com.gnanadhan.app.service.security.CurrentUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;
    private final ProjectMapper projectMapper;

    @Transactional
    public ProjectResponse createProject(ProjectRequest request) {
        User user = currentUserService.getCurrentUser();

        if (projectRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Project name already exists");
        }

        Project project = projectMapper.toEntity(request);
        project.setCreatedBy(user);

        Project savedProject = projectRepository.save(project);

        if (request.isCreateDefaultEnvironments()) {
            java.util.List<Environment> defaults = new java.util.ArrayList<>(java.util.Arrays.asList(
                Environment.builder().name("Development").sequence(1).project(savedProject).build(),
                Environment.builder().name("QA").sequence(2).project(savedProject).build(),
                Environment.builder().name("Staging").sequence(3).project(savedProject).build(),
                Environment.builder().name("Production").sequence(4).project(savedProject).build()
            ));
            savedProject.setEnvironments(defaults);
            savedProject = projectRepository.save(savedProject);
            log.info("User {} created default environments for project ID: {}", user.getEmail(), savedProject.getId());
        }

        log.info("User {} created project '{}' (ID: {})", user.getEmail(), savedProject.getName(), savedProject.getId());
        return projectMapper.toResponse(savedProject);
    }

    @Transactional(readOnly = true)
    public Page<ProjectResponse> getAllProjects(Pageable pageable) {
        User user = currentUserService.getCurrentUser();

        if (user.getRole().getName().equals("SUPER_ADMIN")) {
            return projectRepository.findAllProjectSummaries(pageable);
        } else {
            return projectRepository.findProjectSummariesByUserId(user.getId(), pageable);
        }
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id) {
        User user = currentUserService.getCurrentUser();

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!user.getRole().getName().equals("SUPER_ADMIN") && !project.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to access this project");
        }

        return projectMapper.toResponse(project);
    }

    @Transactional
    public void deleteProject(Long id) {
        User user = currentUserService.getCurrentUser();

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!user.getRole().getName().equals("SUPER_ADMIN") && !project.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to delete this project");
        }

        projectRepository.delete(project);
        log.info("User {} deleted project ID: {}", user.getEmail(), id);
    }

    @Transactional(readOnly = true)
    public void checkProjectAccess(Project project) {
        User user = currentUserService.getCurrentUser();

        if (!user.getRole().getName().equals("SUPER_ADMIN") && !project.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to access this project");
        }
    }
}
