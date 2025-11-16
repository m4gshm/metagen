package example.model.dto;

import io.github.m4gshm.meta.Meta.Extend.Opt;
import lombok.Builder;
import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Extend;
import io.github.m4gshm.meta.Meta.Props;
import io.github.m4gshm.meta.jpa.customizer.JpaColumns;

import java.util.Set;

import static io.github.m4gshm.meta.Meta.Content.FULL;
import static io.github.m4gshm.meta.jpa.customizer.JpaColumns.OPT_GENERATED_COLUMN_NAME_POST_PROCESS;

@Builder(toBuilder = true)
@Meta(
        properties = @Props(FULL), builder = @Meta.Builder(generateMeta = true),
        customizers = @Extend(value = JpaColumns.class, opts = {
                @Opt(key = OPT_GENERATED_COLUMN_NAME_POST_PROCESS, value = "toUpperCase"),
        })
)
public record User(String name, String age, Set<String> tags, @Meta.Exclude String version) {
}
