package example.model.dto;

import lombok.Getter;
import lombok.Setter;
import matador.Meta;

@Meta
public class OnlyWritableBean {

    @Setter
    private int onlyWrite;

    @Getter
    private int accumulated;

    public void setAccumulate(int add) {
        accumulated += add;
    }
}
