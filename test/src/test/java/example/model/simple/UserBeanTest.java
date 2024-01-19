package example.model.simple;

import example.model.simple.UserBeanMeta.IdAwareParam;
import example.model.simple.UserBeanMeta.Param;
import example.model.simple.UserBeanMeta.Prop;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserBeanTest {
    @Test
    public void testProps() {
        assertEquals(Set.of(Prop.age, Prop.id, Prop.name, Prop.address, Prop.tags), Set.copyOf(Prop.values()));
    }

    @Test
    public void testParams() {
        assertEquals(Long.class, IdAwareParam.T);
        assertEquals(Long.class, Param.ID);
    }
}