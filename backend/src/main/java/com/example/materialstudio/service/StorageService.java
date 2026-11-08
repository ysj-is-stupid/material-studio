package com.example.materialstudio.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StorageService {
    private final Path root;

    public StorageService(@Value("${app.storage-root}") String storageRoot) throws IOException {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("uploads"));
        Files.createDirectories(root.resolve("results"));
    }

    public Path resolve(String relative) {
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid storage path");
        return path;
    }
}
