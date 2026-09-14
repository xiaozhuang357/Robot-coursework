package com.lineguard.repository;

import com.lineguard.domain.LineSegment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LineSegmentRepository extends JpaRepository<LineSegment, String> {
}

