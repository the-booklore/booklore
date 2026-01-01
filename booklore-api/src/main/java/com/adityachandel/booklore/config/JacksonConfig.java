package com.adityachandel.booklore.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Jackson configuration for Komga API clean mode.
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();
        
        // Register the custom serializer modifier
        mapper.setSerializerFactory(
            mapper.getSerializerFactory().withSerializerModifier(new BeanSerializerModifier() {
                @Override
                public List<BeanPropertyWriter> changeProperties(
                        com.fasterxml.jackson.databind.SerializationConfig config,
                        com.fasterxml.jackson.databind.BeanDescription beanDesc,
                        List<BeanPropertyWriter> beanProperties) {
                    
                    // Wrap each property writer with our custom logic
                    return beanProperties.stream()
                        .map(KomgaCleanBeanPropertyWriter::new)
                        .collect(Collectors.toList());
                }
            })
        );
        
        return mapper;
    }
}
