package example.model.dto;

import lombok.Builder;
import metagen.Meta;

import java.util.Set;

@Builder(toBuilder = true)
@Meta
public record User(String name, String age, Set<String> tags) {
}
