package com.lineguard.storage;

import com.lineguard.domain.DomainTypes.DetectionQuality;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

@Service
public class InspectionImageStorage {

    private final Path imageRoot;

    public InspectionImageStorage(StorageProperties properties) {
        this.imageRoot = Path.of(properties.getImageRoot()).toAbsolutePath().normalize();
    }

    public String create(UUID taskId, String taskCode, String pointName,
                         double positionMeters, DetectionQuality quality, int captureIndex) {
        try {
            Files.createDirectories(imageRoot);
            String fileName = taskId + "-" + captureIndex + ".svg";
            String blur = quality == DetectionQuality.UNREADABLE
                    ? "filter=\"url(#blur)\" opacity=\"0.72\"" : "";
            String qualityText = quality == DetectionQuality.READABLE ? "图像质量：可判读" : "图像质量：模糊，请重新采集";
            String svg = """
                    <svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 1280 720">
                      <defs>
                        <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="0" stop-color="#102a43"/><stop offset="1" stop-color="#244f68"/>
                        </linearGradient>
                        <filter id="blur"><feGaussianBlur stdDeviation="13"/></filter>
                      </defs>
                      <rect width="1280" height="720" fill="url(#sky)"/>
                      <g %s>
                        <path d="M0 470 Q310 385 610 460 T1280 430" fill="none" stroke="#163a32" stroke-width="190"/>
                        <line x1="55" y1="305" x2="1225" y2="270" stroke="#d7e3e7" stroke-width="8"/>
                        <line x1="640" y1="288" x2="640" y2="610" stroke="#bed0d6" stroke-width="16"/>
                        <circle cx="640" cy="288" r="28" fill="#48cae4" stroke="#e8fbff" stroke-width="8"/>
                        <rect x="570" y="320" width="140" height="86" rx="24" fill="#0b7285" stroke="#9bf6ff" stroke-width="6"/>
                        <circle cx="595" cy="306" r="25" fill="#263238" stroke="#9bf6ff" stroke-width="7"/>
                        <circle cx="685" cy="303" r="25" fill="#263238" stroke="#9bf6ff" stroke-width="7"/>
                      </g>
                      <rect x="48" y="42" width="565" height="148" rx="18" fill="#071924" opacity="0.86"/>
                      <text x="80" y="92" fill="#9bf6ff" font-size="28" font-family="Microsoft YaHei, sans-serif">LineGuard 近距离巡检图像</text>
                      <text x="80" y="132" fill="#ffffff" font-size="22" font-family="Microsoft YaHei, sans-serif">%s · %s</text>
                      <text x="80" y="168" fill="#d4e8ee" font-size="20" font-family="Microsoft YaHei, sans-serif">检测位置 %.0f m · %s</text>
                    </svg>
                    """.formatted(blur, escape(taskCode), escape(pointName), positionMeters, qualityText);
            Files.writeString(imageRoot.resolve(fileName), svg, StandardCharsets.UTF_8);
            return fileName;
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存模拟检测图像", exception);
        }
    }

    public Resource load(String fileName) {
        try {
            Path target = imageRoot.resolve(fileName).normalize();
            if (!target.startsWith(imageRoot) || !Files.isRegularFile(target)) {
                throw new IllegalArgumentException("检测图像不存在");
            }
            return new UrlResource(target.toUri());
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取检测图像", exception);
        }
    }

    public void clear() {
        if (!Files.isDirectory(imageRoot)) {
            return;
        }
        try (var paths = Files.walk(imageRoot)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(imageRoot))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new IllegalStateException("无法清理模拟检测图像", exception);
                        }
                    });
        } catch (IOException exception) {
            throw new IllegalStateException("无法清理模拟检测图像", exception);
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

