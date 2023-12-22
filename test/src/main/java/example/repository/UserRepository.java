package example.repository;

import example.model.UserEntity;
import matador.Meta;
import org.springframework.data.repository.CrudRepository;

@Meta
public interface UserRepository extends CrudRepository<UserEntity, Long> {
}
