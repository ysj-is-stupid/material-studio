package com.example.materialstudio.web;

import com.example.materialstudio.service.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    private final IdentityService identity;

    public IdentityController(IdentityService identity) {
        this.identity = identity;
    }

    @GetMapping("/api/identity")
    public ResponseEntity<IdentityView> identity(HttpServletRequest request) {
        return response(identity.ensure(request));
    }

    @PostMapping("/api/identity/switch")
    public ResponseEntity<IdentityView> switchIdentity(HttpServletRequest request,
                                                        @RequestBody SwitchRequest body) {
        return response(identity.switchTo(request, body.slot()));
    }

    private ResponseEntity<IdentityView> response(IdentityService.CreatedIdentity result) {
        return ResponseEntity.ok()
                .headers(headers -> result.setCookies().forEach(value -> headers.add(HttpHeaders.SET_COOKIE, value)))
                .body(new IdentityView(result.viewerId(), result.slot()));
    }

    public record IdentityView(String viewerId, String slot) {}
    public record SwitchRequest(String slot) {}
}
