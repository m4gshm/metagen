package example.spring.properties;

import lombok.Data;
import meta.Meta;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DataObjectPropertyName;

import java.util.function.Function;

import static meta.Meta.Content.NAME;

@Data
@Meta(properties = @Meta.Props(value = NAME, nameConverter = Properties.SpringPropertyConverter.class))
@ConfigurationProperties("service")
public class Properties {

    boolean enabled;
    String connectionType;
    boolean cronSchedulerEnabled;

    public static class SpringPropertyConverter implements Function<String, String> {

        @Override
        public String apply(String s) {
            return DataObjectPropertyName.toDashedForm(s);
        }
    }

}
