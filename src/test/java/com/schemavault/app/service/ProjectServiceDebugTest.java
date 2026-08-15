package com.schemavault.app.service;

import com.schemavault.app.dto.ProjectRequest;
import com.schemavault.app.entity.Role;
import com.schemavault.app.entity.User;
import com.schemavault.app.repository.ProjectRepository;
import com.schemavault.app.repository.RoleRepository;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    public void testCreateProject() {
        Role role = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().name("SUPER_ADMIN").build()));

        String email = "test_" + System.currentTimeMillis() + "@test.com";
        User user = userRepository.save(User.builder().email(email).password("pass").role(role).build());

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
