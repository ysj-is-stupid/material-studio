package com.example.materialstudio.web;

import com.example.materialstudio.model.AssetRow;
import com.example.materialstudio.service.AssetService;
import com.example.materialstudio.service.IdentityService;
import com.example.materialstudio.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AssetController {
    private final IdentityService identity;
    private final AssetService assets;
    private final StorageService storage;

    public AssetController(IdentityService identity, AssetService assets, StorageService storage) {
        this.identity = identity;
        this.assets = assets;
        this.storage = storage;
    }

    @PostMapping(path = "/api/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(HttpServletRequest request,
            @RequestParam String templateId, @RequestParam MultipartFile file) throws IOException {
        AssetRow asset = assets.upload(identity.requireViewerId(request), templateId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "assetId", asset.id, "previewUrl", "/api/assets/" + asset.id + "/preview",
                "mimeType", asset.mimeType, "sizeBytes", asset.sizeBytes));
    }

    @GetMapping("/api/assets/{id}/preview")
    public ResponseEntity<Resource> preview(HttpServletRequest request, @PathVariable String id) {
        AssetRow asset = assets.owned(id, identity.requireViewerId(request));
        var path = storage.resolve(asset.storedPath);
        if (!Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "素材文件不存在");
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(asset.mimeType))
                .body(new FileSystemResource(path));
    }
}
