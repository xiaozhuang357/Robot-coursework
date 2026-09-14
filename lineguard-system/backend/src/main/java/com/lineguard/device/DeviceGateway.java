package com.lineguard.device;

import java.util.UUID;

/**
 * 管理平台与设备执行端之间的边界。真实设备接入时替换该实现即可。
 */
public interface DeviceGateway {
    void startOrResume(UUID taskId);
    void stop(UUID taskId);
    void stopAll();
}

