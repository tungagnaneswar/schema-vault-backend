package com.gnanadhan.app.service;

import com.gnanadhan.app.dto.ProjectRequest;
import com.gnanadhan.app.dto.ProjectResponse;
import com.gnanadhan.app.entity.Project;
import com.gnanadhan.app.entity.User;
import com.gnanadhan.app.exception.ResourceNotFoundException;
import com.gnanadhan.app.mapper.ProjectMapper;
import com.gnanadhan.app.repository.ProjectRepository;
import com.gnanadhan.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMapper projectMapper;

    public ProjectResponse createProject(ProjectRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (projectRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Project name already exists");
        }

        Project project = projectMapper.toEntity(request);
        project.setCreatedBy(user);

        Project savedProject = projectRepository.save(project);
        return projectMapper.toResponse(savedProject);
    }

    public List<ProjectResponse> getAllProjects() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<Project> projects;
        if (user.getRole().getName().equals("SUPER_ADMIN")) {
            projects = projectRepository.findAll();
        } else {
            projects = projectRepository.findByCreatedById(user.getId());
        }

        return projects.stream()
                .map(projectMapper::toResponse)
                .collect(Collectors.toList());
    }

    public ProjectResponse getProjectById(Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!user.getRole().getName().equals("SUPER_ADMIN") && !project.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to access this project");
        }

        return projectMapper.toResponse(project);
    }

    public void deleteProject(Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!user.getRole().getName().equals("SUPER_ADMIN") && !project.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You do not have permission to delete this project");
        }

        projectRepository.delete(project);
    }
}
