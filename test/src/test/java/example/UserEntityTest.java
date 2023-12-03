package example;

import example.model.UserEntity;
import example.model.UserEntityMeta;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Set;

import static example.model.UserEntityMeta.Fields.*;
import static example.model.UserEntityMeta.Params;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserEntityTest {
    @Test
    public void paramType() {
        assertEquals(Long.class, UserEntityMeta.Params.ID.type);
    }

    @Test
    public void fieldType() {
        assertEquals(Integer.class, age.type);
        assertEquals(Serializable.class, id.type);
        assertEquals(UserEntity.Address.class, address.type);
    }

    @Test
    public void fieldsCompleteness() {
        var expected = Set.of(age, id, name, address);
        assertEquals(expected.size(), values().length);
        assertEquals(expected, Set.of(values()));
    }
}

