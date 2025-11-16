package example;

import lombok.Data;
import io.github.m4gshm.meta.Meta;

public class External {

    @Meta
    @Data
    public static class Internal {
        private final int sum;
    }
}
