package example.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CompanyEntityTest {
    @Test
    public void testAccessFields() {
        var fields = Arrays.stream(CompanyEntityMeta.Column.class.getDeclaredFields()).map(Field::getName).collect(toSet());
        assertFalse(fields.contains("getter"));
        assertFalse(fields.contains("setter"));
        assertTrue(fields.contains("builderSetter"));
    }
}
