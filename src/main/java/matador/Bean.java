package matador;

import lombok.Builder;
import lombok.Data;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class Bean {
    private String name;
    private Set<Modifier> modifiers;
    private List<Property> properties;
    private List<Bean> types;
    private List<Param> typeParameters;
    private boolean isRecord;

    private Output output;
    private Meta meta;

    @Data
    @Builder
    public static final class Property {
        private String name;
        private boolean setter;
        private boolean getter;
        private boolean field;
        private TypeMirror type;
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

    @Data
    @Builder
    public static final class Param {
        private String name;
        private TypeMirror type;
    }
}
