package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.TeamDbConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamDbConnectionRepository extends JpaRepository<TeamDbConnection, Long> {
    List<TeamDbConnection> findByTeamId(Long teamId);
    List<TeamDbConnection> findByDbConnectionId(Long dbConnectionId);
    Optional<TeamDbConnection> findByTeamIdAndDbConnectionId(Long teamId, Long dbConnectionId);
    boolean existsByTeamIdAndDbConnectionId(Long teamId, Long dbConnectionId);
}
