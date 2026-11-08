package com.example.materialstudio.web;

import com.example.materialstudio.mapper.TemplateMapper;
import com.example.materialstudio.model.TemplateRow;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TemplateController {
    private final TemplateMapper mapper;
    private final ObjectMapper json;

    public TemplateController(TemplateMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @GetMapping("/api/templates")
    public Map<String, Object> list(@RequestParam(required = false) String category) {
        var all = mapper.findAll();
        var categories = new LinkedHashSet<String>();
        all.forEach(row -> categories.add(row.category));
        List<TemplateView> items = all.stream()
                .filter(row -> category == null || category.isBlank() || row.category.equals(category))
                .map(this::view).toList();
        return Map.of("categories", categories, "items", items);
    }

    @GetMapping("/api/templates/{id}")
    public TemplateView detail(@PathVariable String id) {
        return view(required(id));
    }

    @GetMapping("/api/templates/{id}/cover")
    public ResponseEntity<ClassPathResource> cover(@PathVariable String id) {
        TemplateRow template = required(id);
        if (!template.coverPath.matches("demo/[a-z-]+\\.svg")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COVER_NOT_FOUND", "示例封面不存在");
        }
        ClassPathResource resource = new ClassPathResource(template.coverPath);
        if (!resource.exists()) throw new ApiException(HttpStatus.NOT_FOUND, "COVER_NOT_FOUND", "示例封面不存在");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("image/svg+xml")).body(resource);
    }

    private TemplateRow required(String id) {
        TemplateRow row = mapper.findById(id);
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "TEMPLATE_NOT_FOUND", "模板不存在");
        return row;
    }

    private TemplateView view(TemplateRow row) {
        try {
            List<String> types = json.readValue(row.acceptedMimeTypes, new TypeReference<>() {});
            return new TemplateView(row.id, row.title, row.category, row.description,
                    "/api/templates/" + row.id + "/cover", row.requiredImageCount,
                    types, row.maxFileSizeBytes);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid template MIME types", e);
        }
    }

    public record TemplateView(String id, String title, String category, String description,
                               String coverUrl, int requiredImageCount,
                               List<String> acceptedMimeTypes, int maxFileSizeBytes) {}
}
