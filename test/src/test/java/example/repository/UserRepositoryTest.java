package example.repository;

import example.model.UserEntity;
import matador.Typed;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;

import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class UserRepositoryTest {

    @Test
    public void extendedInterfacesParams() {
        var values = UserRepositoryMeta.instance.parametersOf(CrudRepository.class);
        var params = Stream.of(values).collect(toMap(Typed::name, Typed::type));
        assertEquals(UserEntity.class, params.get("T"));
        assertEquals(Long.class, params.get("ID"));
    }
}
