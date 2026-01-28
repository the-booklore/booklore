package com.adityachandel.booklore.config;

import com.adityachandel.booklore.context.KomgaCleanContext;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.std.NullSerializer;

import java.util.Collection;

/**
 * Custom BeanPropertyWriter that handles clean mode filtering.
 * When clean mode is enabled:
 * - Fields ending with "Lock" are excluded
 * - Null values are excluded
 * - Empty arrays/collections are excluded
 */
public class KomgaCleanBeanPropertyWriter extends BeanPropertyWriter {

    protected KomgaCleanBeanPropertyWriter(BeanPropertyWriter base) {
        super(base);
        // we need this to report "null" values when clean mode is NOT used
        super.assignNullSerializer(NullSerializer.instance);
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
        if (KomgaCleanContext.isCleanMode()) {
            String propertyName = getName();
            
            // Exclude properties ending with "Lock"
            if (propertyName.endsWith("Lock")) {
                return;
            }
            
            // Exclude null values
            Object value = get(bean);
            if (value == null) {
                return;
            }
            
            // Exclude empty collections/arrays
            if (value instanceof Collection && ((Collection<?>) value).isEmpty()) {
                return;
            }
        } else  {
            String propertyName = getName();
            // Not in clean mode: ensure Lock properties that are null are emitted as false
            if (propertyName.endsWith("Lock")) {
                Object value = get(bean);
                if (value == null) {
                    gen.writeFieldName(propertyName);
                    gen.writeBoolean(false);
                    return;
                }
            }
        }
        
        // Default behavior where everything is returned
        super.serializeAsField(bean, gen, prov);
    }
}
