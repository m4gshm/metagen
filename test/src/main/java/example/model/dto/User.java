package example.model.dto;

import lombok.Builder;
import meta.Meta;
import meta.jpa.customizer.JpaColumns;

import java.util.Set;

import static meta.jpa.customizer.JpaColumns.OPT_GENERATED_COLUMN_NAME_POST_PROCESS;

@Builder(toBuilder = true)
@Meta(builder = @Meta.Builder(generateMeta = true), customizers = @Meta.Extend(value = JpaColumns.class, opts = {
        @Meta.Extend.Opt(key = OPT_GENERATED_COLUMN_NAME_POST_PROCESS, value = "toUpperCase"),
}))
public record User(String name, String age, Set<String> tags, @Meta.Exclude String version) {
}
