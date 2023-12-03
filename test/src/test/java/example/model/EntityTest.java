package example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityTest {
    @Test
    public void paramType() {
        assertEquals(Long.class, UserEntityMeta.Params.ID.type);
    }
}
