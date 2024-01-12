package example;

import lombok.Data;
import metagen.Meta;

public class External {

    @Meta
    @Data
    public static class Internal {
        private final int sum;
    }
}
