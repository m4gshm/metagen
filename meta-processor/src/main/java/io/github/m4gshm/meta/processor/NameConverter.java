package io.github.m4gshm.meta.processor;

import io.github.m4gshm.meta.processor.converter.SnakeCase;

import java.util.function.Function;

public interface NameConverter extends Function<String, String> {
    NameConverter SNAKE_CASE = new SnakeCase.Lower();
    NameConverter UPPER_SNAKE_CASE = new SnakeCase.Upper();
}