package example.model.dto;

import lombok.Builder;
import matador.Meta;

@Builder(toBuilder = true)
@Meta
public record User(String name, String age) {
}
