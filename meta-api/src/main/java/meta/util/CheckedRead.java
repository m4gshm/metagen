package meta.util;

/**
 * Read property accessor that can throws an Exception.
 *
 * @param <T> bean type
 * @param <V> property type
 * @param <E> exception type
 */
public interface CheckedRead<T, V, E extends Throwable> {
    V get(T bean) throws E;
}
