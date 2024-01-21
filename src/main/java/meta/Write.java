package meta;

public interface Write<T, V> {
    void set(T bean, V value);
}
