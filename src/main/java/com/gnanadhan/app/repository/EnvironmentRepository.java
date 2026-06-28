package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, Long> {
    List<Environment> findByProjectIdOrderBySequenceAsc(Long projectId);
    Optional<Environment> findByNameAndProjectId(String name, Long projectId);
    boolean existsByNameAndProjectId(String name, Long projectId);
}
