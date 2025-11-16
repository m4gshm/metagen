package example.spring.properties;

import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Props;
import io.github.m4gshm.meta.processor.utils.spring.SpringPropertyDashForm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static io.github.m4gshm.meta.Meta.ConstantNameStrategy.UPPER_SNAKE_CASE;

@Data
@ConfigurationProperties("service")
@Meta(properties = @Props(constantName = UPPER_SNAKE_CASE, nameValueCustomizer = SpringPropertyDashForm.class))
public class Properties {

    boolean enabled;
    String connectionType;
    boolean cronSchedulerEnabled;

}
