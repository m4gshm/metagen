package matador;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Bean {
    private String name;
    private List<Property> properties;
    private Output output;

    @Data
    @Builder
    public static final class Property {
        private String name;
        private boolean setter;
        private boolean getter;
        private boolean field;
    }


    @Data
    @Builder
    public static final class Output {
        private String name;
        private String package_;

        public String getPackage() {
            return package_;
        }

        public void setPackage(String value) {
            this.package_ = value;
        }
    }
}
