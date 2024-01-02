package matador;

public interface Read<T, V> {
    V get(T bean);
}
