package example.model.dto;

import lombok.Builder;
import meta.Meta;
import meta.jpa.JpaColumns;

import java.util.Set;

@Builder(toBuilder = true)
@Meta(builder = @Meta.Builder(generateMeta = true), customizers = @Meta.Extend(JpaColumns.class))
public record User(String name, String age, Set<String> tags, @Meta.Exclude String version) {
}
