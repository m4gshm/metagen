package example.repository;

import example.model.UserEntity;
import meta.Meta;
import meta.Meta.Methods;
import meta.Meta.Params;
import meta.Meta.Props;
import org.springframework.data.repository.CrudRepository;

import static meta.Meta.Content.FULL;
import static meta.Meta.Methods.Content.NAME;


@Meta(methods = @Methods(NAME), properties = @Props(FULL), params = @Params(FULL))
public interface UserRepository extends CrudRepository<UserEntity, Long> {
}
