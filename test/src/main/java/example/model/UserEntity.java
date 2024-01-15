package example.model;


import example.IdAware;
import lombok.*;
import lombok.experimental.SuperBuilder;
import metagen.Meta;
import metagen.customizer.JpaColumns;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;

import static lombok.AccessLevel.NONE;
import static lombok.AccessLevel.PUBLIC;
import static metagen.customizer.JpaColumns.OPT_CLASS_NAME;
import static metagen.customizer.JpaColumns.OPT_IMPLEMENTS;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Meta(
        builder = @Meta.Builder(generateMeta = true),
        customizers = @Meta.Extend(
                value = JpaColumns.class,
                opts = {
                        @Meta.Extend.Opt(key = OPT_CLASS_NAME, value = "Column"),
                        @Meta.Extend.Opt(key = OPT_IMPLEMENTS, value = "metagen.jpa.Column"),
                })
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
    private Tag[] tags;

    @Meta
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
    public static class LegalAddress {
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
