package example;

import example.model.Model;
import example.model.UserEntity;
import example.model.UserEntityMeta;
import example.model.UserEntityMeta.BuilderMeta;
import lombok.SneakyThrows;
import matador.Meta;
import matador.SuperParametersAware;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static example.model.UserEntityMeta.Prop.*;
import static org.junit.jupiter.api.Assertions.*;

public class UserEntityTest {
    @Test
    public void userEntityFieldsEnum() {
        var metaModel = Model.instance.of(UserEntity.class);
        assertEquals(UserEntityMeta.class, metaModel.getClass());

        assertEquals(UserEntityMeta.Prop.values(), metaModel.properties());
        assertEquals(UserEntityMeta.Param.values(), metaModel.parameters());
        assertEquals(UserEntityMeta.EntityParam.values(), metaModel.parametersOf(Meta.Parameters.Inherited.Super.class));
        assertEquals(UserEntityMeta.EntityParam.values(), ((SuperParametersAware) metaModel).superParameters());
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
        assertSame(address, UserEntityMeta.Prop.address.get(bean));
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
        var expected = Set.of(age, id, name, address, legalAddress);
        assertEquals(expected.size(), values().size());
        assertEquals(expected, Set.copyOf(values()));
    }

    @Test
    public void userEntityBuilderCompleteness() {
        var values = BuilderMeta.values();
        var expected = Set.of(BuilderMeta.age, BuilderMeta.id, BuilderMeta.name,
                BuilderMeta.address, BuilderMeta.legalAddress);
        assertEquals(expected.size(), values.size());
        assertEquals(expected, Set.copyOf(values));
    }

    @Test
    public void userEntityBuilderPopulate() {
        var address = UserEntity.Address.builder().build();

        var builder = UserEntity.builder();
        BuilderMeta.id.set(builder, 1L);
        BuilderMeta.age.set(builder, 20);
        BuilderMeta.name.set(builder, "name");
        BuilderMeta.address.set(builder, address);

        var user = builder.build();
        assertEquals(1L, user.getId());
        assertEquals(20, user.getAge());
        assertEquals("name", user.getName());
        assertSame(address, user.address);
    }

    @Test
    @SneakyThrows
    public void jpaAnnotations() {
//        UserEntity.class.getMethod(id.name()).getAnnotation(Column.class);
//        select(UserEntityMeta.Columns).from(UserEntityMeta.Annotations.Table.name);
    }
}

