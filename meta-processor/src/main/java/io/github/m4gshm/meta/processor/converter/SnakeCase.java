package io.github.m4gshm.meta.processor.converter;

import io.github.m4gshm.meta.processor.NameConverter;
import lombok.RequiredArgsConstructor;

import static java.lang.Character.isLowerCase;
import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;


@RequiredArgsConstructor
public class SnakeCase implements NameConverter {
    private final boolean upCase;

    @Override
    public String apply(String name) {
        if (name == null) {
            return null;
        }
        var result = new StringBuilder();
        for (var i = 0; i < name.length(); ++i) {
            var c = name.charAt(i);
            if (isUpperCase(c) && i > 0 && isLowerCase(name.charAt(i - 1))) {
                result.append('_');
            }
            var aCase = this.upCase ? toUpperCase(c) : toLowerCase(c);
            result.append(aCase);
        }
        return result.toString();
    }

    public static class Upper extends SnakeCase {
        public Upper() {
            super(true);
        }
    }

    public static class Lower extends SnakeCase {
        public Lower() {
            super(false);
        }
    }
}
