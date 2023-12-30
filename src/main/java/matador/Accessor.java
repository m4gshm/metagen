package matador;

public interface Accessor<T> {

    Object get(T bean);

    void set(T bean, Object value);
}
