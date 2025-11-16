package example.model.dto;

import io.github.m4gshm.meta.Meta.Props;
import lombok.*;
import lombok.experimental.SuperBuilder;
import io.github.m4gshm.meta.Meta;

import java.util.List;

import static io.github.m4gshm.meta.Meta.Content.FULL;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Meta(properties = @Props(FULL), builder = @Meta.Builder(generateMeta = true))
public class AnyReadableWritableBean {

    @Getter
    @Setter
    private int readWrite;

    @Setter
    private int onlyWrite;

    @Getter
    private int accumulated;

    @Getter
    @Setter
    private List<String> appendable;

    public void setAccumulate(int add) {
        accumulated += add;
    }
}
