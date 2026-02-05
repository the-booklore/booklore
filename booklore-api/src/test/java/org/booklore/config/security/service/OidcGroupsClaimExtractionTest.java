package org.booklore.config.security.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OidcGroupsClaimExtractionTest {

    @Test
    void extractGroupsFromValue_ShouldHandleSimpleList() {
        List<String> groupsList = List.of("admin", "user");
        
        List<String> result = DynamicOidcJwtProcessor.extractGroupsFromValue(groupsList, "groups");
        
        assertEquals(2, result.size());
        assertTrue(result.contains("admin"));
        assertTrue(result.contains("user"));
    }
    
    @Test
    void extractGroupsFromValue_ShouldHandleCommaSeparatedString() {
        String groupsString = "admin,user,reader";
        
        List<String> result = DynamicOidcJwtProcessor.extractGroupsFromValue(groupsString, "groups");
        
        assertEquals(3, result.size());
        assertTrue(result.contains("admin"));
        assertTrue(result.contains("user"));
        assertTrue(result.contains("reader"));
    }
    
    @Test
    void extractGroupsFromValue_ShouldHandleJsonArrayString() {
        String jsonArray = "[\"admin\", \"user\", \"reader\"]";
        
        List<String> result = DynamicOidcJwtProcessor.extractGroupsFromValue(jsonArray, "groups");
        
        assertEquals(3, result.size());
        assertTrue(result.contains("admin"));
        assertTrue(result.contains("user"));
        assertTrue(result.contains("reader"));
    }
    
    @Test
    void extractGroupsFromValue_ShouldHandleEmptyJsonArrayString() {
        String emptyJsonArray = "[]";
        
        List<String> result = DynamicOidcJwtProcessor.extractGroupsFromValue(emptyJsonArray, "groups");
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    void extractGroupsFromValue_ShouldHandleSingleString() {
        String singleGroup = "admin";
        
        List<String> result = DynamicOidcJwtProcessor.extractGroupsFromValue(singleGroup, "groups");
        
        assertEquals(1, result.size());
        assertEquals("admin", result.get(0));
    }
    
    @Test
    void extractGroupsFromValue_ShouldHandleNullValue() {
        List<String> result = DynamicOidcJwtProcessor.extractGroupsFromValue(null, "groups");
        
        assertTrue(result.isEmpty());
    }
    
    @Test
    void extractGroupsFromValue_ShouldHandleJsonArrayWithSpaces() {
        String jsonArray = "[\"admin\", \"user\", \"reader\"]";
        
        List<String> result = DynamicOidcJwtProcessor.extractGroupsFromValue(jsonArray, "groups");
        
        assertEquals(3, result.size());
        assertTrue(result.contains("admin"));
        assertTrue(result.contains("user"));
        assertTrue(result.contains("reader"));
    }
    
    @Test
    void extractGroupsFromValue_ShouldHandleComplexJsonArray() {
        String jsonArray = "[\"admin\", \"power-user\", \"reader\"]";
        
        List<String> result = DynamicOidcJwtProcessor.extractGroupsFromValue(jsonArray, "groups");
        
        assertEquals(3, result.size());
        assertTrue(result.contains("admin"));
        assertTrue(result.contains("power-user"));
        assertTrue(result.contains("reader"));
    }
}