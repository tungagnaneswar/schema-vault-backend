package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    Optional<Team> findByName(String name);
    boolean existsByName(String name);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT t FROM Team t LEFT JOIN TeamMember tm ON t.id = tm.team.id WHERE t.createdBy.id = :userId OR tm.user.id = :userId")
    List<Team> findAccessibleTeams(@org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(DISTINCT t) FROM Team t LEFT JOIN TeamMember tm ON t.id = tm.team.id WHERE t.createdBy.id = :userId OR tm.user.id = :userId")
    long countAccessibleTeams(@org.springframework.data.repository.query.Param("userId") Long userId);
}
