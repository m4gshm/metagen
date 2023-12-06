package example.model;

import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EntityTest {
    @Test
    public void paramType() {
        assertEquals(Serializable.class, EntityMeta.Params.ID.type);
    }
}
