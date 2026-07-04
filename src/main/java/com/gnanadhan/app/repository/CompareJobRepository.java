package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.CompareJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface CompareJobRepository extends JpaRepository<CompareJob, Long> {

    @EntityGraph(attributePaths = {"sourceSnapshot.connection.environment", "targetSnapshot.connection.environment"})
    @Query("SELECT j FROM CompareJob j WHERE j.id = :id")
    Optional<CompareJob> findByIdWithSnapshots(@Param("id") Long id);
    
    @EntityGraph(attributePaths = {"sourceSnapshot.connection", "targetSnapshot.connection", "createdBy"})
    Page<CompareJob> findByProjectIdOrderByStartedAtDesc(Long projectId, Pageable pageable);
    @EntityGraph(attributePaths = {"sourceSnapshot.connection.environment", "targetSnapshot.connection.environment", "createdBy", "project"})
    Page<CompareJob> findAllByOrderByStartedAtDesc(Pageable pageable);
}
