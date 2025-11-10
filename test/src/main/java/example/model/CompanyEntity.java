package example.model;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import io.github.m4gshm.meta.Meta;
import io.github.m4gshm.meta.Meta.Methods.Content;
import io.github.m4gshm.meta.jpa.customizer.JpaColumns;

import static io.github.m4gshm.meta.jpa.customizer.JpaColumns.OPT_CLASS_NAME;
import static io.github.m4gshm.meta.jpa.customizer.JpaColumns.OPT_GENERATED_COLUMN_NAME_POST_PROCESS;
import static io.github.m4gshm.meta.jpa.customizer.JpaColumns.OPT_IMPLEMENTS;
import static lombok.AccessLevel.NONE;

@Builder
@Meta(
        builder = @Meta.Builder(generateMeta = true),
        customizers = @Meta.Extend(
                value = JpaColumns.class,
                opts = {
                        @Meta.Extend.Opt(key = OPT_CLASS_NAME, value = "Column"),
                        @Meta.Extend.Opt(key = OPT_IMPLEMENTS, value = "io.github.m4gshm.meta.jpa.Column"),
                        @Meta.Extend.Opt(key = OPT_GENERATED_COLUMN_NAME_POST_PROCESS, value = "toUpperCase"),
                }),
        methods = @Meta.Methods(Content.NAME)
)
@javax.persistence.Entity
@Getter(NONE)
@Setter(NONE)
public class CompanyEntity {

    Integer id;
    String name;
    String address;
}
