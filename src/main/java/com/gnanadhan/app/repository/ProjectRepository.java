package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.Project;
import com.gnanadhan.app.dto.ProjectResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByName(String name);
    boolean existsByName(String name);

    @Query("SELECT new com.gnanadhan.app.dto.ProjectResponse(p.id, p.name, p.description, u.id, u.email, p.createdAt, p.updatedAt, CAST(COUNT(c.id) AS int)) " +
           "FROM Project p " +
           "JOIN p.createdBy u " +
           "LEFT JOIN p.environments e " +
           "LEFT JOIN e.connections c " +
           "GROUP BY p.id, p.name, p.description, u.id, u.email, p.createdAt, p.updatedAt")
    Page<ProjectResponse> findAllProjectSummaries(Pageable pageable);

    @Query("SELECT new com.gnanadhan.app.dto.ProjectResponse(p.id, p.name, p.description, u.id, u.email, p.createdAt, p.updatedAt, CAST(COUNT(c.id) AS int)) " +
           "FROM Project p " +
           "JOIN p.createdBy u " +
           "LEFT JOIN p.environments e " +
           "LEFT JOIN e.connections c " +
           "WHERE u.id = :userId " +
           "GROUP BY p.id, p.name, p.description, u.id, u.email, p.createdAt, p.updatedAt")
    Page<ProjectResponse> findProjectSummariesByUserId(@Param("userId") Long userId, Pageable pageable);
}
