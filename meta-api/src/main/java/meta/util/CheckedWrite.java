package meta.util;

/**
 * Write property accessor that can throws an Exception.
 * @param <T> bean type
 * @param <V> property type
 */
public interface CheckedWrite<T, V, E extends Exception> {
    void set(T bean, V value) throws E;
}
