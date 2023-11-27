package example.simple;


import example.simple.UserAddressMeta.Prop;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserAddressTest {
    @Test
    public void testProps() {
        assertEquals(Set.of(Prop.postalCode, Prop.city, Prop.fullAddress, Prop.street), Set.copyOf(Prop.values()));
    }
}