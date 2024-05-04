package meta;

/**
 * Read property accessor.
 *
 * @param <T> bean type
 * @param <V> property type
 */
public interface CheckedRead<T, V, E extends Throwable> {
    V get(T bean) throws E;
}
