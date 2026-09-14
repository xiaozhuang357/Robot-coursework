package com.lineguard.repository;

import com.lineguard.domain.InspectionPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectionPointRepository extends JpaRepository<InspectionPoint, String> {
    List<InspectionPoint> findByLineSegmentIdOrderByPositionMeters(String lineSegmentId);
}

