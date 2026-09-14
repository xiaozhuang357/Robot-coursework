package com.lineguard.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lineguard.storage")
public class StorageProperties {

    private String imageRoot = "./runtime-data/images";

    public String getImageRoot() { return imageRoot; }
    public void setImageRoot(String imageRoot) { this.imageRoot = imageRoot; }
}

