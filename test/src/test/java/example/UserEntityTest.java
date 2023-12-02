package example;

import example.model.UserEntityMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserEntityTest {
    @Test
    public void paramTypeTest() {
        assertEquals(Long.class, UserEntityMeta.Params.ID.type);
    }
}
