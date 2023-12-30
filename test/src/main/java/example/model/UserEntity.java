package example.model;


import example.IdAware;
import lombok.*;
import lombok.experimental.SuperBuilder;
import matador.Meta;

import javax.persistence.Column;
import javax.persistence.Embedded;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static lombok.AccessLevel.NONE;

@Meta
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
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

//    @RequiredArgsConstructor
//    public enum DBColumns {
//
//        NAME(userEntity -> userEntity.name, ), AG_E, POSTAL_CODE, CITY, STREET;
//
//        private final Function<UserEntity, Object> getter;
//        public final BiConsumer<UserEntity, Object> setter;

//    }

    @Meta
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String postalCode;
        private String city;
        private String street;
    }

}
