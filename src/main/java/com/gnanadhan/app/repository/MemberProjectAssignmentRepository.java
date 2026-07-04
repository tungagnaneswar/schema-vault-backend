package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.MemberProjectAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberProjectAssignmentRepository extends JpaRepository<MemberProjectAssignment, Long> {
    
    List<MemberProjectAssignment> findByTeamMemberId(Long teamMemberId);
    
    Optional<MemberProjectAssignment> findByTeamMemberIdAndProjectId(Long teamMemberId, Long projectId);
    
    void deleteByTeamMemberIdAndProjectId(Long teamMemberId, Long projectId);
    
    void deleteByTeamMemberId(Long teamMemberId);
    
    void deleteByTeamMemberIdIn(List<Long> teamMemberIds);
}
