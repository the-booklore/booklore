package org.booklore.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Jackson configuration for Komga API clean mode.
 */
@Configuration
public class JacksonConfig {

    public static final String KOMGA_CLEAN_OBJECT_MAPPER = "komgaCleanObjectMapper";

    @Bean(name = KOMGA_CLEAN_OBJECT_MAPPER)
    public ObjectMapper komgaCleanObjectMapper() {
        ObjectMapper mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules()
                .build();

        // Register the custom serializer modifier on this dedicated mapper only
        mapper.setSerializerFactory(
            mapper.getSerializerFactory().withSerializerModifier(new BeanSerializerModifier() {
                @Override
                public List<BeanPropertyWriter> changeProperties(
                        com.fasterxml.jackson.databind.SerializationConfig config,
                        com.fasterxml.jackson.databind.BeanDescription beanDesc,
                        List<BeanPropertyWriter> beanProperties) {

                    return beanProperties.stream()
                        .map(KomgaCleanBeanPropertyWriter::new)
                        .collect(Collectors.toList());
                }
            })
        );

        return mapper;
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .build();
    }
}
