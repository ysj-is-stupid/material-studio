package com.example.materialstudio.service;

import com.example.materialstudio.mapper.AssetMapper;
import com.example.materialstudio.mapper.TemplateMapper;
import com.example.materialstudio.model.AssetRow;
import com.example.materialstudio.model.TemplateRow;
import com.example.materialstudio.web.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AssetService {
    private final AssetMapper assets;
    private final TemplateMapper templates;
    private final StorageService storage;
    private final ObjectMapper json;

    public AssetService(AssetMapper assets, TemplateMapper templates, StorageService storage, ObjectMapper json) {
        this.assets = assets;
        this.templates = templates;
        this.storage = storage;
        this.json = json;
    }

    public AssetRow upload(String userId, String templateId, MultipartFile file) throws IOException {
        TemplateRow template = templates.findById(templateId);
        if (template == null) throw new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "模板不存在");
        if (file.isEmpty()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_FILE", "请选择图片");
        if (file.getSize() > template.maxFileSizeBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "图片超过模板大小限制");
        }
        byte[] bytes = file.getBytes();
        String actualMime = detectMime(bytes);
        List<String> allowed = json.readValue(template.acceptedMimeTypes, new TypeReference<>() {});
        if (actualMime == null || !allowed.contains(actualMime)
                || !actualMime.equals(file.getContentType())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_IMAGE_TYPE", "请上传模板支持的 JPEG 或 PNG 图片");
        }
        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException malformedImage) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_IMAGE", "图片文件无法读取");
        }
        if (decoded == null || decoded.getWidth() < 1 || decoded.getHeight() < 1) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_IMAGE", "图片文件无法读取");
        }

        String id = UUID.randomUUID().toString();
        String relative = "uploads/" + id + ("image/png".equals(actualMime) ? ".png" : ".jpg");
        Path destination = storage.resolve(relative);
        Path temporary = storage.resolve("uploads/" + id + ".part");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            AssetRow asset = new AssetRow();
            asset.id = id;
            asset.userId = userId;
            asset.templateId = templateId;
            asset.storedPath = relative;
            asset.mimeType = actualMime;
            asset.sizeBytes = bytes.length;
            assets.insert(asset);
            return asset;
        } catch (IOException | RuntimeException error) {
            Files.deleteIfExists(temporary);
            Files.deleteIfExists(destination);
            throw error;
        }
    }

    public AssetRow owned(String id, String userId) {
        AssetRow asset = assets.findOwned(id, userId);
        if (asset == null) throw new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "素材不存在");
        return asset;
    }

    private static String detectMime(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                && bytes[2] == 'N' && bytes[3] == 'G') return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        return null;
    }
}
