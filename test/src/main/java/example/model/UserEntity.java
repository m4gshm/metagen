package example.model;


import example.IdAware;
import io.github.m4gshm.meta.Meta.Extend;
import io.github.m4gshm.meta.Meta.Methods;
import io.github.m4gshm.meta.Meta.Params;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Methods.Content;
import io.github.m4gshm.meta.Meta.Props;
import io.github.m4gshm.meta.jpa.customizer.JpaColumns;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;

import static io.github.m4gshm.meta.Meta.Extend.*;
import static lombok.AccessLevel.NONE;
import static lombok.AccessLevel.PUBLIC;
import static io.github.m4gshm.meta.Meta.Content.FULL;
import static io.github.m4gshm.meta.jpa.customizer.JpaColumns.OPT_CLASS_NAME;
import static io.github.m4gshm.meta.jpa.customizer.JpaColumns.OPT_GENERATED_COLUMN_NAME_POST_PROCESS;
import static io.github.m4gshm.meta.jpa.customizer.JpaColumns.OPT_IMPLEMENTS;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Meta(
        builder = @Meta.Builder(generateMeta = true),
        customizers = @Extend(
                value = JpaColumns.class,
                opts = {
                        @Opt(key = OPT_CLASS_NAME, value = "Column"),
                        @Opt(key = OPT_IMPLEMENTS, value = "io.github.m4gshm.meta.jpa.Column"),
                        @Opt(key = OPT_GENERATED_COLUMN_NAME_POST_PROCESS, value = "toUpperCase"),
                }),
        properties = @Props(FULL),
        methods = @Methods(Content.NAME),
        params = @Params(FULL)
)
@javax.persistence.Entity
public class UserEntity extends Entity<Long> implements IdAware<Long> {

    @Getter(NONE)
    @Setter(NONE)
    @Embedded
    public Address address; // public field
    @Column(name = "NAME")
    String name;
    @Column(name = "AG_E")
    Integer age;
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "postalCode", column = @Column(name = "LEGAL_POSTAL_CODE")),
            @AttributeOverride(name = "city", column = @Column(name = "LEGAL_CITY")),
            @AttributeOverride(name = "street", column = @Column(name = "LEGAL_STREET")),
    })
    private Address legalAddress;
    @JpaColumns.Exclude
    private Tag[] tags;

    @Meta(properties = @Props(FULL))
    @Data
    @Builder(access = PUBLIC, toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String postalCode;
        private String city;
        private String street;
    }

    @Data
    @Builder(access = PUBLIC, toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Tag {
        private String tagValue;
    }
}
