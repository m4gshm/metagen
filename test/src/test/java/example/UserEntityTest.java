package example;

import example.model.UserEntity;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Set;

import static example.model.UserEntityMeta.Fields.address;
import static example.model.UserEntityMeta.Fields.age;
import static example.model.UserEntityMeta.Fields.id;
import static example.model.UserEntityMeta.Fields.name;
import static example.model.UserEntityMeta.Fields.values;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserEntityTest {
    @Test
    public void paramType() {

//        var idAwareParams = Set.of(UserEntityMeta.inherits().get(IdAware.class).parameters());

//        assertEquals(Set.of(IdAwareMeta.Params.values()))

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

