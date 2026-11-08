package com.example.materialstudio.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.materialstudio.mapper.HealthMapper;
import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void reportsDatabaseAfterProbe() {
        HealthMapper mapper = mock(HealthMapper.class);
        when(mapper.checkDatabase()).thenReturn(1);
        assertEquals("UP", new HealthController(mapper).health().get("database"));
    }
}
