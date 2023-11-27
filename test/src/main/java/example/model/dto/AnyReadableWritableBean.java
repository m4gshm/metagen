package example.model.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;
import meta.Meta;

import java.util.List;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Meta(builder = @Meta.Builder(generateMeta = true))
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
