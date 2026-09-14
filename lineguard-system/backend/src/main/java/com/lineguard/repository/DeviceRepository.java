package com.lineguard.repository;

import com.lineguard.domain.Device;
import com.lineguard.domain.DomainTypes.DeviceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, String> {
    Optional<Device> findByType(DeviceType type);
}

