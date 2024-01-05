package example.model;


import example.IdAware;
import lombok.*;
import lombok.experimental.SuperBuilder;
import matador.Meta;

import javax.persistence.Column;
import javax.persistence.Embedded;

import static lombok.AccessLevel.NONE;
import static lombok.AccessLevel.PUBLIC;

@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@Meta(builder = @Meta.Builder(detect = true))
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

}
