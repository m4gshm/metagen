package meta;

/**
 * Read property accessor.
 * @param <T> bean type
 * @param <V> property type
 */
public interface Read<T, V> {
    V get(T bean);
}
