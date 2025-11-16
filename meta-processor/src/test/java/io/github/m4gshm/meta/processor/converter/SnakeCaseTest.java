package io.github.m4gshm.meta.processor.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SnakeCaseTest {

    private final SnakeCase lowerLevelConverter = new SnakeCase.Lower();
    private final SnakeCase upperLevelConverter = new SnakeCase.Upper();

    @Test
    public void camelToSnakeCaseConvert() {
        assertEquals("any_java_name", lowerLevelConverter.apply("anyJavaName"));
    }

    @Test
    public void camelToUpperSnakeCaseConvert() {
        assertEquals("ANY_JAVA_NAME", upperLevelConverter.apply("anyJavaName"));
    }

    @Test
    public void pascalToSnakeCaseConvert() {
        assertEquals("ANY_JAVA_NAME", upperLevelConverter.apply("AnyJavaName"));
    }

    @Test
    public void upperToUpperSnakeCaseConvert() {
        assertEquals("ID", upperLevelConverter.apply("ID"));
    }

    @Test
    public void upperSnakeCaseToUpperSnakeCaseConvert() {
        assertEquals("ANY_UPPER", upperLevelConverter.apply("ANY_UPPER"));
    }
}
