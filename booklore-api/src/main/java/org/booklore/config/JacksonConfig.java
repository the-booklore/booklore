package org.booklore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

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
        return JsonMapper.builder()
                .findAndAddModules()
                .serializerFactory(
                    tools.jackson.databind.ser.BeanSerializerFactory.instance
                        .withSerializerModifier(new ValueSerializerModifier() {
                            @Override
                            public List<BeanPropertyWriter> changeProperties(
                                    tools.jackson.databind.SerializationConfig config,
                                    tools.jackson.databind.BeanDescription.Supplier beanDescSupplier,
                                    List<BeanPropertyWriter> beanProperties) {

                                return beanProperties.stream()
                                    .map(KomgaCleanBeanPropertyWriter::new)
                                    .collect(Collectors.toList());
                            }
                        })
                )
                .build();
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
