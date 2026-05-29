package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByName(String name);
    List<Project> findByCreatedById(Long userId);
    boolean existsByName(String name);
}
