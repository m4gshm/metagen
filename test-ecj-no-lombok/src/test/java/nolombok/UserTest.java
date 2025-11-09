package nolombok;


import nolombok.UserMeta.IdAwareParam;
import nolombok.UserMeta.Prop;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserTest {
    @Test
    public void testProps() {
        assertEquals(User.class, UserMeta.type);

        assertEquals(Set.of(Prop.age, Prop.id, Prop.name, Prop.address), Set.copyOf(Prop.values()));

        assertEquals("age", Prop.age);
        assertEquals("id", Prop.id);
        assertEquals("name", Prop.name);
        assertEquals("address", Prop.address);
        assertEquals("age", Prop.age);
    }

    @Test
    public void testParams() {
        assertEquals(Long.class, IdAwareParam.ID);
    }
}
