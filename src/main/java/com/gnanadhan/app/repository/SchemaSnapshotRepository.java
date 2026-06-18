package com.gnanadhan.app.repository;

import com.gnanadhan.app.entity.SchemaSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchemaSnapshotRepository extends JpaRepository<SchemaSnapshot, Long> {
    Page<SchemaSnapshot> findByConnectionId(Long connectionId, Pageable pageable);
}
