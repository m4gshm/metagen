package example.model;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import meta.Meta;
import meta.Meta.Methods.Content;
import meta.jpa.customizer.JpaColumns;

import static lombok.AccessLevel.NONE;
import static meta.jpa.customizer.JpaColumns.*;

@Builder
@Meta(
        builder = @Meta.Builder(generateMeta = true),
        customizers = @Meta.Extend(
                value = JpaColumns.class,
                opts = {
                        @Meta.Extend.Opt(key = OPT_CLASS_NAME, value = "Column"),
                        @Meta.Extend.Opt(key = OPT_IMPLEMENTS, value = "meta.jpa.Column"),
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
