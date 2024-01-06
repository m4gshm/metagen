package example;

import lombok.Data;
import matador.Meta;

public class External {

    @Meta
    @Data
    public static class Internal {
        private final int sum;
    }
}
