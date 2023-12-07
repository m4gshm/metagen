package example;

import example.model.Model;
import example.model.UserEntity;
import example.model.UserEntityMeta;
import lombok.SneakyThrows;
import matador.MetaModel;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.Set;

import static example.model.UserEntityMeta.Fields.address;
import static example.model.UserEntityMeta.Fields.age;
import static example.model.UserEntityMeta.Fields.id;
import static example.model.UserEntityMeta.Fields.name;
import static example.model.UserEntityMeta.Fields.values;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class UserEntityTest {
    @Test
    public void userEntityFieldsEnum() {
        var metaModel = Model.instance.of(UserEntity.class);
        assertEquals(UserEntityMeta.class, metaModel.getClass());

        assertArrayEquals(UserEntityMeta.Fields.values(), metaModel.fields());
        assertArrayEquals(UserEntityMeta.Params.values(), metaModel.parameters());
    }

    @Test
    @SneakyThrows
    public void noEntityClassInModel() {
        var type = Class.forName("example.model.Entity");
        var metaModel = Model.instance.of(type);
        assertNull(metaModel);
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

