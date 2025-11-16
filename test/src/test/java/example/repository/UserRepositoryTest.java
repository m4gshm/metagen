package example.repository;

import example.model.UserEntity;
import example.repository.UserRepositoryMeta.Method;
import io.github.m4gshm.meta.Typed;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;

import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;


public class UserRepositoryTest {

    @Test
    public void extendedInterfacesParams() {
        var values = UserRepositoryMeta.instance.parametersOf(CrudRepository.class);
        var params = values.stream().collect(toMap(t -> t, Typed::type));
        assertEquals(UserEntity.class, params.get(UserRepositoryMeta.CrudRepositoryParam.T));
        assertEquals(Long.class, params.get(UserRepositoryMeta.CrudRepositoryParam.ID));
    }

    @Test
    public void methods() {
        var values = Method.values();
        assertEquals(11, values.size());
        assertEquals(String.class, Method.findAll.getClass());
    }
}
