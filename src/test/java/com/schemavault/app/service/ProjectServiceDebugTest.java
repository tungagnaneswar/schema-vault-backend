package com.schemavault.app.service;

import com.schemavault.app.dto.ProjectRequest;
import com.schemavault.app.entity.User;
import com.schemavault.app.entity.Role;
import com.schemavault.app.repository.ProjectRepository;
import com.schemavault.app.repository.UserRepository;
import com.schemavault.app.service.ProjectService;
import com.schemavault.app.service.security.CurrentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test") // if there is a test profile, otherwise default
public class ProjectServiceDebugTest {

    @Autowired
    private ProjectService projectService;

    @MockBean
    private CurrentUserService currentUserService;

    @Autowired
    private ProjectRepository projectRepository;

    @Test
    public void testCreateProject() {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        Role role = new Role();
        role.setName("SUPER_ADMIN");
        user.setRole(role);

        when(currentUserService.getCurrentUser()).thenReturn(user);

        ProjectRequest req = new ProjectRequest();
        req.setName("Test Project " + System.currentTimeMillis());
        req.setDescription("Desc");
        req.setCreateDefaultEnvironments(true);

        try {
            projectService.createProject(req);
            System.out.println("SUCCESSFULLY CREATED PROJECT");
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
}
