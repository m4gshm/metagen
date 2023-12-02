package example;

import example.model.UserEntity;
import example.model.UserEntityMeta;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserEntityTest {
    @Test
    public void paramTypeTest() {
        assertEquals(Long.class, UserEntityMeta.Params.ID.type);
    }

    @Test
    public void fieldTypeTest() {
        assertEquals(Integer.class, UserEntityMeta.Fields.age.type);
        assertEquals(Serializable.class, UserEntityMeta.Fields.id.type);
        assertEquals(UserEntity.Address.class, UserEntityMeta.Fields.address.type);
    }
}
