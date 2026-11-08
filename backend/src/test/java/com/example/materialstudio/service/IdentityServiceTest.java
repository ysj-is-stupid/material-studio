package com.example.materialstudio.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.materialstudio.mapper.IdentityMapper;
import com.example.materialstudio.web.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityServiceTest {
    @Test
    void switchesBetweenPersistentIsolatedDemoIdentities() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        Map<String, String> identities = new HashMap<>();
        doAnswer(call -> {
            identities.put(call.getArgument(1), call.getArgument(0));
            return null;
        }).when(mapper).insert(anyString(), anyString());
        when(mapper.findIdByHash(anyString())).thenAnswer(call -> identities.get(call.getArgument(0)));
        IdentityService service = new IdentityService(mapper);
        Map<String, Cookie> cookies = new HashMap<>();

        IdentityService.CreatedIdentity a = service.ensure(request(cookies));
        addCookies(cookies, a);
        assertEquals("A", a.slot());
        assertEquals(a.viewerId(), service.requireViewerId(request(cookies)));

        IdentityService.CreatedIdentity b = service.switchTo(request(cookies), "B");
        addCookies(cookies, b);
        assertNotEquals(a.viewerId(), b.viewerId());
        assertEquals("B", b.slot());
        assertEquals(b.viewerId(), service.requireViewerId(request(cookies)));

        IdentityService.CreatedIdentity restored = service.switchTo(request(cookies), "A");
        addCookies(cookies, restored);
        assertEquals(a.viewerId(), restored.viewerId());
        assertEquals(2, identities.size());
    }

    @Test
    void rejectsUnknownSlotAndDoesNotFallBackToOtherIdentity() {
        IdentityMapper mapper = mock(IdentityMapper.class);
        IdentityService service = new IdentityService(mapper);
        Map<String, Cookie> cookies = new HashMap<>();
        cookies.put(IdentityService.ACTIVE_COOKIE_NAME, new Cookie(IdentityService.ACTIVE_COOKIE_NAME, "B"));
        cookies.put(IdentityService.COOKIE_NAME, new Cookie(IdentityService.COOKIE_NAME, "a-token"));
        assertNull(service.findViewerId(request(cookies)));
        assertThrows(ApiException.class, () -> service.switchTo(request(cookies), "C"));
    }

    private HttpServletRequest request(Map<String, Cookie> cookies) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(cookies.values().toArray(Cookie[]::new));
        return request;
    }

    private void addCookies(Map<String, Cookie> cookies, IdentityService.CreatedIdentity result) {
        for (String header : result.setCookies()) {
            String pair = header.split(";", 2)[0];
            String[] parts = pair.split("=", 2);
            cookies.put(parts[0], new Cookie(parts[0], parts[1]));
        }
    }
}
