package metagen;

public interface Read<T, V> {
    V get(T bean);
}
