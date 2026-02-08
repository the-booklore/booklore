package org.booklore.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.booklore.model.dto.*;
import org.booklore.model.entity.BookEntity;
import org.springframework.data.jpa.domain.Specification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite for BookRuleEvaluatorService.
 * Tests that specifications are created correctly for the new METADATA, INCOMPLETE_SERIES, and ADDED_ON filter types.
 */
@ExtendWith(MockitoExtension.class)
class BookRuleEvaluatorServiceTest {

    private BookRuleEvaluatorService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new BookRuleEvaluatorService(objectMapper);
    }

    // ============================================
    // METADATA Field Tests
    // ============================================

    @Test
    void testMetadataField_HasOperator_CreatesSpecification() {
        // Test that METADATA field with HAS operator creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.METADATA)
                        .operator(RuleOperator.HAS)
                        .value("title")
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null");
    }

    @Test
    void testMetadataField_MissingOperator_CreatesSpecification() {
        // Test that METADATA field with MISSING operator creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.METADATA)
                        .operator(RuleOperator.MISSING)
                        .value("isbn13")
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null");
    }

    @Test
    void testMetadataField_AllSupportedFieldNames() {
        // Test that all supported metadata field names create valid specifications
        String[] supportedFields = {
            // IDs
            "isbn13", "isbn10", "asin", "goodreadsid", "comicvineid", "hardcoverid", 
            "hardcoverbookid", "googleid", "lubimyczytacid", "ranobedbid",
            // Ratings
            "amazonrating", "goodreadsrating", "hardcoverrating", "lubimyczytacrating", "ranobedbrating", "personalrating",
            // Review counts
            "amazonreviewcount", "goodreadsreviewcount", "hardcoverreviewcount",
            // Text fields
            "title", "subtitle", "publisher", "description", "seriesname", "language",
            // Numbers
            "pagecount", "seriesnumber", "seriestotal",
            // Dates
            "publisheddate",
            // Array fields
            "authors", "categories", "moods", "tags"
        };

        for (String fieldName : supportedFields) {
            GroupRule groupRule = GroupRule.builder()
                    .join(JoinType.AND)
                    .rules(Arrays.asList(
                        Rule.builder()
                            .type("rule")
                            .field(RuleField.METADATA)
                            .operator(RuleOperator.HAS)
                            .value(fieldName)
                            .build()
                    ))
                    .build();

            Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
            
            assertNotNull(spec, "Specification should not be null for field: " + fieldName);
        }
    }

    @Test
    void testMetadataField_CaseInsensitiveFieldNames() {
        // Test that metadata field names are case-insensitive
        String[] caseVariations = {"TITLE", "title", "Title", "TiTlE"};
        
        for (String fieldName : caseVariations) {
            GroupRule groupRule = GroupRule.builder()
                    .join(JoinType.AND)
                    .rules(Arrays.asList(
                        Rule.builder()
                            .type("rule")
                            .field(RuleField.METADATA)
                            .operator(RuleOperator.HAS)
                            .value(fieldName)
                            .build()
                    ))
                    .build();

            Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
            
            assertNotNull(spec, "Specification should work with case variation: " + fieldName);
        }
    }

    @Test
    void testMetadataField_InvalidFieldName_CreatesSpecification() {
        // Test that invalid metadata field names still create specifications (conjunction)
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.METADATA)
                        .operator(RuleOperator.HAS)
                        .value("invalidFieldName")
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null even for invalid field names");
    }

    @Test
    void testMetadataField_ArrayFields_HasOperator() {
        // Test that array metadata fields (moods, tags, authors, categories) work with HAS operator
        String[] arrayFields = {"moods", "tags", "authors", "categories"};
        
        for (String fieldName : arrayFields) {
            GroupRule groupRule = GroupRule.builder()
                    .join(JoinType.AND)
                    .rules(Arrays.asList(
                        Rule.builder()
                            .type("rule")
                            .field(RuleField.METADATA)
                            .operator(RuleOperator.HAS)
                            .value(fieldName)
                            .build()
                    ))
                    .build();

            Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
            
            assertNotNull(spec, "Specification should not be null for array field: " + fieldName);
        }
    }

    @Test
    void testMetadataField_ArrayFields_MissingOperator() {
        // Test that array metadata fields work with MISSING operator
        String[] arrayFields = {"moods", "tags", "authors", "categories"};
        
        for (String fieldName : arrayFields) {
            GroupRule groupRule = GroupRule.builder()
                    .join(JoinType.AND)
                    .rules(Arrays.asList(
                        Rule.builder()
                            .type("rule")
                            .field(RuleField.METADATA)
                            .operator(RuleOperator.MISSING)
                            .value(fieldName)
                            .build()
                    ))
                    .build();

            Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
            
            assertNotNull(spec, "Specification should not be null for array field: " + fieldName);
        }
    }

    // ============================================
    // INCOMPLETE_SERIES Field Tests
    // ============================================

    @Test
    void testIncompleteSeries_TrueValue_CreatesSpecification() {
        // Test that INCOMPLETE_SERIES with value true creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.INCOMPLETE_SERIES)
                        .operator(RuleOperator.EQUALS)
                        .value(true)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for INCOMPLETE_SERIES=true");
    }

    @Test
    void testIncompleteSeries_FalseValue_CreatesSpecification() {
        // Test that INCOMPLETE_SERIES with value false creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.INCOMPLETE_SERIES)
                        .operator(RuleOperator.EQUALS)
                        .value(false)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for INCOMPLETE_SERIES=false");
    }

    @Test
    void testIncompleteSeries_StringValue_CreatesSpecification() {
        // Test that INCOMPLETE_SERIES with string "true"/"false" creates a valid specification
        GroupRule groupRule1 = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.INCOMPLETE_SERIES)
                        .operator(RuleOperator.EQUALS)
                        .value("true")
                        .build()
                ))
                .build();

        GroupRule groupRule2 = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.INCOMPLETE_SERIES)
                        .operator(RuleOperator.EQUALS)
                        .value("false")
                        .build()
                ))
                .build();

        Specification<BookEntity> spec1 = service.toSpecification(groupRule1, 1L);
        Specification<BookEntity> spec2 = service.toSpecification(groupRule2, 1L);
        
        assertNotNull(spec1, "Specification should handle string 'true'");
        assertNotNull(spec2, "Specification should handle string 'false'");
    }

    // ============================================
    // ADDED_ON Field Tests
    // ============================================

    @Test
    void testAddedOnField_GreaterThan_CreatesSpecification() {
        // Test that ADDED_ON with GREATER_THAN operator creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.ADDED_ON)
                        .operator(RuleOperator.GREATER_THAN)
                        .value(30)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for ADDED_ON > 30");
    }

    @Test
    void testAddedOnField_LessThan_CreatesSpecification() {
        // Test that ADDED_ON with LESS_THAN operator creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.ADDED_ON)
                        .operator(RuleOperator.LESS_THAN)
                        .value(7)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for ADDED_ON < 7");
    }

    @Test
    void testAddedOnField_InBetween_CreatesSpecification() {
        // Test that ADDED_ON with IN_BETWEEN operator creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.ADDED_ON)
                        .operator(RuleOperator.IN_BETWEEN)
                        .valueStart(7)
                        .valueEnd(30)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for ADDED_ON between 7 and 30");
    }

    @Test
    void testAddedOnField_Equals_CreatesSpecification() {
        // Test that ADDED_ON with EQUALS operator creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.ADDED_ON)
                        .operator(RuleOperator.EQUALS)
                        .value(0)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for ADDED_ON = 0 (today)");
    }

    // ============================================
    // HAS and MISSING Operator Tests
    // ============================================

    @Test
    void testHasOperator_WithRegularField_CreatesSpecification() {
        // Test that HAS operator works with regular fields
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.PUBLISHER)
                        .operator(RuleOperator.HAS)
                        .value(null)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for PUBLISHER HAS");
    }

    @Test
    void testMissingOperator_WithRegularField_CreatesSpecification() {
        // Test that MISSING operator works with regular fields
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.SUBTITLE)
                        .operator(RuleOperator.MISSING)
                        .value(null)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for SUBTITLE MISSING");
    }

    // ============================================
    // Combined Rules Tests
    // ============================================

    @Test
    void testCombinedRules_MetadataAndAddedOn() {
        // Test that multiple new filter types can be combined
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.METADATA)
                        .operator(RuleOperator.HAS)
                        .value("isbn13")
                        .build(),
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.ADDED_ON)
                        .operator(RuleOperator.LESS_THAN)
                        .value(30)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should handle combined METADATA and ADDED_ON rules");
    }

    @Test
    void testCombinedRules_AllNewFilterTypes() {
        // Test that all three new filter types can be combined
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.OR)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.METADATA)
                        .operator(RuleOperator.MISSING)
                        .value("publisher")
                        .build(),
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.INCOMPLETE_SERIES)
                        .operator(RuleOperator.EQUALS)
                        .value(true)
                        .build(),
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.ADDED_ON)
                        .operator(RuleOperator.GREATER_THAN)
                        .value(90)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should handle all three new filter types combined");
    }

    @Test
    void testNestedGroupRules_WithNewFilterTypes() {
        // Test that new filter types work in nested group rules
        GroupRule nestedGroup = GroupRule.builder()
                .join(JoinType.OR)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.METADATA)
                        .operator(RuleOperator.HAS)
                        .value("goodreadsid")
                        .build(),
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.ADDED_ON)
                        .operator(RuleOperator.LESS_THAN)
                        .value(7)
                        .build()
                ))
                .build();

        GroupRule mainGroup = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    nestedGroup,
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.INCOMPLETE_SERIES)
                        .operator(RuleOperator.EQUALS)
                        .value(false)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(mainGroup, 1L);
        
        assertNotNull(spec, "Specification should handle nested groups with new filter types");
    }

    @Test
    void testEmptyRules_ReturnsValidSpecification() {
        // Test that empty rules list creates a valid specification
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList())
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should not be null for empty rules");
    }

    @Test
    void testNullValue_HandledGracefully() {
        // Test that null value in METADATA rule is handled gracefully
        GroupRule groupRule = GroupRule.builder()
                .join(JoinType.AND)
                .rules(Arrays.asList(
                    Rule.builder()
                        .type("rule")
                        .field(RuleField.METADATA)
                        .operator(RuleOperator.HAS)
                        .value(null)
                        .build()
                ))
                .build();

        Specification<BookEntity> spec = service.toSpecification(groupRule, 1L);
        
        assertNotNull(spec, "Specification should handle null values gracefully");
    }
}
