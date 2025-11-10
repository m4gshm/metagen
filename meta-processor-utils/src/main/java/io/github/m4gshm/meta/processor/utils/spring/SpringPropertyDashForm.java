package io.github.m4gshm.meta.processor.utils.spring;

import org.springframework.boot.context.properties.bind.DataObjectPropertyName;

import java.util.function.Function;

public class SpringPropertyDashForm implements Function<String, String> {

    @Override
    public String apply(String s) {
        return DataObjectPropertyName.toDashedForm(s);
    }
}
