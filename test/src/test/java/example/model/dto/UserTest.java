package example.model.dto;

import example.model.dto.UserMeta.Prop;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserTest {
    @Test
    public void completenessTest() {
        var values = Prop.values();
        assertEquals(Set.of(Prop.age, Prop.name, Prop.tags), Set.copyOf(values));
    }

    @Test
    public void typeTest() {
        var values = Prop.values();
        assertEquals(String.class, Prop.age.type());
        assertEquals(String.class, Prop.name.type());
        assertEquals(Set.class, Prop.tags.type());
    }
}
