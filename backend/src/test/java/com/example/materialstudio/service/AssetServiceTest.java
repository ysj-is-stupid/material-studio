package com.example.materialstudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.materialstudio.mapper.AssetMapper;
import com.example.materialstudio.mapper.TemplateMapper;
import com.example.materialstudio.model.AssetRow;
import com.example.materialstudio.model.TemplateRow;
import com.example.materialstudio.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class AssetServiceTest {
    @TempDir Path tempDir;

    @Test
    void rejectsFileClaimingToBePngWhenItsBytesAreNotAnImage() throws Exception {
        AssetMapper assets = mock(AssetMapper.class);
        TemplateMapper templates = mock(TemplateMapper.class);
        TemplateRow template = new TemplateRow();
        template.id = "pet-portrait";
        template.maxFileSizeBytes = 5_242_880;
        template.acceptedMimeTypes = "[\"image/png\"]";
        when(templates.findById(template.id)).thenReturn(template);
        AssetService service = new AssetService(assets, templates,
                new StorageService(tempDir.toString()), new ObjectMapper());
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png",
                "not an image".getBytes());

        ApiException error = assertThrows(ApiException.class,
                () -> service.upload("viewer-1", template.id, fake));

        assertEquals("INVALID_IMAGE_TYPE", error.code);
        verify(assets, never()).insert(org.mockito.ArgumentMatchers.any(AssetRow.class));
    }
}
