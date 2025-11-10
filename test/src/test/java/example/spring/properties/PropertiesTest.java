package example.spring.properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PropertiesTest {

    @Test
    public void testProps() {
        assertEquals("enabled", PropertiesMeta.Prop.enabled);
        assertEquals("connection-type", PropertiesMeta.Prop.connectionType);
        assertEquals("cron-scheduler-enabled", PropertiesMeta.Prop.cronSchedulerEnabled);
    }

}
