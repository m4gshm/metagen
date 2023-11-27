package matador;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Bean {
    private String name;
    private List<Property> properties;

    @Data
    @Builder
    public static final class Property {
        private String name;
        private boolean setter;
        private boolean getter;
        private boolean field;
    }
}
