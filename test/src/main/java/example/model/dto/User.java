package example.model.dto;

import lombok.Builder;
import metagen.Meta;
import metagen.customizer.JpaColumns;

import java.util.Set;

@Builder(toBuilder = true)
@Meta(builder = @Meta.Builder(generateMeta = true), customizers = @Meta.Extend(JpaColumns.class))
public record User(String name, String age, Set<String> tags) {
}
