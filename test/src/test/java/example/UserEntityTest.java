package example;

import example.model.UserEntity;
import example.model.UserEntityMeta;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserEntityTest {
    @Test
    public void paramType() {
        assertEquals(Long.class, UserEntityMeta.Params.ID.type);
    }

    @Test
    public void fieldType() {
        assertEquals(Integer.class, UserEntityMeta.Fields.age.type);
        assertEquals(Serializable.class, UserEntityMeta.Fields.id.type);
        assertEquals(UserEntity.Address.class, UserEntityMeta.Fields.address.type);
    }

    @Test
    public void fieldsСompleteness() {
        assertEquals(4, UserEntityMeta.Fields.values().length);
    }
}

