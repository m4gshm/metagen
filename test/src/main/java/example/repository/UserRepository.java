package example.repository;

import example.model.UserEntity;
import meta.Meta;
import meta.Meta.Methods;
import org.springframework.data.repository.CrudRepository;

import static meta.Meta.Methods.EnumType.NAME;


@Meta(methods = @Methods(NAME))
public interface UserRepository extends CrudRepository<UserEntity, Long> {
}
