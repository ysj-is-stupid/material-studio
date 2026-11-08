package com.example.materialstudio.service;

import com.example.materialstudio.mapper.IdentityMapper;
import com.example.materialstudio.web.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    public static final String COOKIE_NAME = "material_viewer";
    public static final String B_COOKIE_NAME = "material_viewer_b";
    public static final String ACTIVE_COOKIE_NAME = "material_viewer_active";
    private final IdentityMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public IdentityService(IdentityMapper mapper) {
        this.mapper = mapper;
    }

    public String findViewerId(HttpServletRequest request) {
        String token = cookieValue(request, "B".equals(activeSlot(request)) ? B_COOKIE_NAME : COOKIE_NAME);
        return token == null ? null : mapper.findIdByHash(hash(token));
    }

    public String activeSlot(HttpServletRequest request) {
        return "B".equals(cookieValue(request, ACTIVE_COOKIE_NAME)) ? "B" : "A";
    }

    public String requireViewerId(HttpServletRequest request) {
        String viewerId = findViewerId(request);
        if (viewerId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "IDENTITY_REQUIRED", "请刷新页面以建立演示身份");
        }
        return viewerId;
    }

    public CreatedIdentity ensure(HttpServletRequest request) {
        String slot = activeSlot(request);
        String existing = findViewerId(request);
        if (existing != null) return new CreatedIdentity(existing, slot, List.of());
        return create(request, slot);
    }

    public CreatedIdentity switchTo(HttpServletRequest request, String slot) {
        if (!"A".equals(slot) && !"B".equals(slot)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_IDENTITY", "请选择演示用户 A 或 B");
        }
        String cookieName = "B".equals(slot) ? B_COOKIE_NAME : COOKIE_NAME;
        String token = cookieValue(request, cookieName);
        String existing = token == null ? null : mapper.findIdByHash(hash(token));
        if (existing != null) {
            return new CreatedIdentity(existing, slot, List.of(activeCookie(request, slot)));
        }
        return create(request, slot);
    }

    private CreatedIdentity create(HttpServletRequest request, String slot) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = UUID.randomUUID().toString();
        mapper.insert(id, hash(token));
        String cookieName = "B".equals(slot) ? B_COOKIE_NAME : COOKIE_NAME;
        String cookie = ResponseCookie.from(cookieName, token)
                .httpOnly(true).sameSite("Lax").secure(request.isSecure()).path("/")
                .maxAge(60L * 60 * 24 * 365).build().toString();
        return new CreatedIdentity(id, slot, List.of(cookie, activeCookie(request, slot)));
    }

    private String activeCookie(HttpServletRequest request, String slot) {
        return ResponseCookie.from(ACTIVE_COOKIE_NAME, slot)
                .httpOnly(true).sameSite("Lax").secure(request.isSecure()).path("/")
                .maxAge(60L * 60 * 24 * 365).build().toString();
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record CreatedIdentity(String viewerId, String slot, List<String> setCookies) {}
}
