package example.model.dto;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static example.model.dto.AnyReadableWritableBeanMeta.Prop.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnyReadableWritableBeanTest {

    @Test
    @SneakyThrows
    public void test() {
        var bean = new AnyReadableWritableBean();
        bean.setAccumulate(1);
        accumulate.set(bean, 1);
        accumulate.set(bean, 1);
        assertEquals(3, accumulated.get(bean));

        onlyWrite.set(bean, 123);
        var onlyWriteField = bean.getClass().getDeclaredField("onlyWrite");
        onlyWriteField.setAccessible(true);
        assertEquals(123, onlyWriteField.get(bean));

        readWrite.set(bean, 50);

        assertEquals(50, readWrite.get(bean));
    }
}
