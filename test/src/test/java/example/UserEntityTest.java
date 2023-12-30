package example;

import example.model.Model;
import example.model.UserEntity;
import example.model.UserEntityMeta;
import lombok.SneakyThrows;
import matador.Meta;
import matador.SuperParametersAware;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static example.model.UserEntityMeta.Props.*;
import static org.junit.jupiter.api.Assertions.*;

public class UserEntityTest {
    @Test
    public void userEntityFieldsEnum() {
        var metaModel = Model.instance.of(UserEntity.class);
        assertEquals(UserEntityMeta.class, metaModel.getClass());

        assertArrayEquals(UserEntityMeta.Props.values(), metaModel.properties());
        assertArrayEquals(UserEntityMeta.Params.values(), metaModel.parameters());
        assertArrayEquals(UserEntityMeta.EntityParams.values(), metaModel.parametersOf(Meta.Parameters.Inherited.Super.class));
        assertArrayEquals(UserEntityMeta.EntityParams.values(), ((SuperParametersAware) metaModel).superParameters());
    }

    @Test
    public void userEntityFieldsReadByGetters() {
        var metaModel = Model.instance.of(UserEntity.class);
        assertEquals(UserEntityMeta.class, metaModel.getClass());

        var address = UserEntity.Address.builder()
                .postalCode("123")
                .build();
        var bean = UserEntity.builder().id(1L).age(20).name("Bob").address(address).build();
        assertEquals(1L, id.get(bean));
        assertEquals(20, age.get(bean));
        assertEquals("Bob", name.get(bean));
        assertSame(address, UserEntityMeta.Props.address.get(bean));
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
        assertEquals(Long.class, id.type);
        assertEquals(UserEntity.Address.class, address.type);
    }

    @Test
    public void fieldsCompleteness() {
        var expected = Set.of(age, id, name, address);
        assertEquals(expected.size(), values().length);
        assertEquals(expected, Set.of(values()));
    }

    @Test
    @SneakyThrows
    public void jpaAnnotations() {
//        UserEntity.class.getMethod(id.name()).getAnnotation(Column.class);
//        select(UserEntityMeta.Columns).from(UserEntityMeta.Annotations.Table.name);
    }
}

