package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.DbConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DbConnectionRepository extends JpaRepository<DbConnection, Long> {
    List<DbConnection> findByCreatedById(Long userId);

    @EntityGraph(attributePaths = {"environment", "environment.project", "createdBy"})
    Page<DbConnection> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"environment", "environment.project", "createdBy"})
    @Query("SELECT DISTINCT c FROM DbConnection c LEFT JOIN TeamDbConnection tc ON c.id = tc.dbConnection.id LEFT JOIN TeamMember tm ON tc.team.id = tm.team.id WHERE c.createdBy.id = :userId OR tm.user.id = :userId")
    Page<DbConnection> findAccessibleConnections(@Param("userId") Long userId, Pageable pageable);
}
