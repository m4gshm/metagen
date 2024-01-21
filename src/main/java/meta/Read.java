package meta;

public interface Read<T, V> {
    V get(T bean);
}
