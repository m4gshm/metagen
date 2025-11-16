package example.repository;

import example.model.UserEntity;
import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Methods;
import io.github.m4gshm.meta.Meta.Params;
import io.github.m4gshm.meta.Meta.Props;
import org.springframework.data.repository.CrudRepository;

import static io.github.m4gshm.meta.Meta.Content.FULL;
import static io.github.m4gshm.meta.Meta.Methods.Content.NAME;


@Meta(methods = @Methods(NAME), properties = @Props(FULL), params = @Params(FULL))
public interface UserRepository extends CrudRepository<UserEntity, Long> {
}
