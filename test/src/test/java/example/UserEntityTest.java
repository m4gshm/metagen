package example;

import example.model.UserEntity;
import example.model.UserEntityAddressMeta;
import example.model.UserEntityMeta;
import example.model.UserEntityMeta.BuilderMeta;
import example.model.UserEntityMeta.Column;
import lombok.SneakyThrows;
import meta.Meta;
import meta.SuperParametersAware;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static example.model.UserEntityMeta.Prop.*;
import static example.model._Model.instance;
import static org.junit.jupiter.api.Assertions.*;

public class UserEntityTest {
    @Test
    public void userEntityFieldsEnum() {
        var metaModel = instance.of(UserEntity.class);
        assertEquals(UserEntityMeta.class, metaModel.getClass());

        assertEquals(UserEntityMeta.Prop.values(), metaModel.properties());
        assertTrue(metaModel.parameters().isEmpty());
//        assertEquals(UserEntityMeta.Param.values(), metaModel.parameters());
        assertEquals(UserEntityMeta.EntityParam.values(), metaModel.parametersOf(Meta.Params.Inherited.Super.class));
        assertEquals(UserEntityMeta.EntityParam.values(), ((SuperParametersAware<?>) metaModel).superParameters());
    }

    @Test
    public void userEntityFieldsReadByGetters() {
        var metaModel = instance.of(UserEntity.class);
        assertEquals(UserEntityMeta.class, metaModel.getClass());

        var address = UserEntity.Address.builder()
                .postalCode("123")
                .build();
        var tags = new UserEntity.Tag[]{UserEntity.Tag.builder().tagValue("tag").build()};
        var bean = UserEntity.builder().id(1L).age(20).name("Bob").address(address).tags(tags).build();
        assertEquals(1L, id.get(bean));
        assertEquals(20, age.get(bean));
        assertEquals("Bob", name.get(bean));
        assertArrayEquals(tags, UserEntityMeta.Prop.tags.get(bean));
        assertSame(address, UserEntityMeta.Prop.address.get(bean));
        assertEquals("123", UserEntityAddressMeta.Prop.postalCode.get(address));
    }

    @Test
    @SneakyThrows
    public void noEntityClassInModel() {
        var type = Class.forName("example.model.Entity");
        var metaModel = instance.of(type);
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
        var expected = Set.of(age, id, name, address, legalAddress, tags);
        assertEquals(expected.size(), values().size());
        assertEquals(expected, Set.copyOf(values()));
    }

    @Test
    public void userEntityBuilderCompleteness() {
        var values = BuilderMeta.values();
        var expected = Set.of(BuilderMeta.age, BuilderMeta.id, BuilderMeta.name,
                BuilderMeta.address, BuilderMeta.legalAddress, BuilderMeta.tags);
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
        BuilderMeta.tags.set(builder, new UserEntity.Tag[0]);

        var user = builder.build();
        assertEquals(1L, user.getId());
        assertEquals(20, user.getAge());
        assertEquals("name", user.getName());
        assertSame(address, user.address);
    }

    @Test
    @SneakyThrows
    public void jpaAnnotations() {
        var src = UserEntity.builder().id(1L).age(20).name("Bob")
                .address(UserEntity.Address.builder()
                        .postalCode("123")
                        .city("Neb")
                        .build())
                .legalAddress(UserEntity.Address.builder()
                        .street("Centr. st")
                        .build())
//                .tags(new UserEntity.Tag[]{
//                        UserEntity.Tag.builder()
//                                .tagValue("tag")
//                                .build()
//                })
                .build();

        var dest = UserEntity.builder().build();

        Column.ID.set(dest, Column.ID.get(src));
        Column.AG_E.set(dest, Column.AG_E.get(src));
        Column.NAME.set(dest, Column.NAME.get(src));
        Column.CITY.set(dest, Column.CITY.get(src));
        Column.POSTAL_CODE.set(dest, Column.POSTAL_CODE.get(src));
        Column.LEGAL_STREET.set(dest, Column.LEGAL_STREET.get(src));

        var noTags = Column.values().stream().anyMatch(c -> c.name().equals("TAGS"));
        assertFalse(noTags);
        assertEquals(src, dest);
    }

    @Test
    public void userEntityMethods() {
        var values = UserEntityMeta.Method.values();
        assertEquals(13, values.size());
    }
}

