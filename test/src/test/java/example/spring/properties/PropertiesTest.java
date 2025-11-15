package example.spring.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertiesTest {

    @Test
    public void testProps() {
        assertEquals("enabled", PropertiesMeta.Prop.ENABLED);
        assertEquals("connection-type", PropertiesMeta.Prop.CONNECTION_TYPE);
        assertEquals("cron-scheduler-enabled", PropertiesMeta.Prop.CRON_SCHEDULER_ENABLED);
    }

}
