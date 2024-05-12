package nolombok;


import nolombok.UserMeta.IdAwareParam;
import nolombok.UserMeta.Prop;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserTest {
    @Test
    public void testProps() {
        assertEquals(Set.of(Prop.age, Prop.id, Prop.name, Prop.address), Set.copyOf(Prop.values()));
    }

    @Test
    public void testParams() {
        assertEquals(Long.class, IdAwareParam.ID);
    }
}