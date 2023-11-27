package example.model.dto;

import example.model.dto.UserMeta.BuilderMeta;
import example.model.dto.UserMeta.Prop;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserTest {
    @Test
    public void completenessTest() {
        assertEquals(Set.of(Prop.age, Prop.name, Prop.tags), Set.copyOf(Prop.values()));
    }

    @Test
    public void typeTest() {
        assertEquals(String.class, Prop.age.type());
        assertEquals(String.class, Prop.name.type());
        assertEquals(Set.class, Prop.tags.type());
    }

    @Test
    public void builderTest() {
        var builder = User.builder();

        BuilderMeta.age.set(builder, "age");
        BuilderMeta.name.set(builder, "name");
        BuilderMeta.tags.set(builder, Set.of("tag1"));

        var user = builder.build();

        assertEquals("age", Prop.age.get(user));
        assertEquals("name", Prop.name.get(user));
        assertEquals(Set.of("tag1"), Prop.tags.get(user));
    }
}
