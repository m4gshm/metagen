package meta.util;

/**
 * Read, write property accessor.
 * @param <T> bean type
 * @param <V> property type
 */
public interface ReadWrite<T, V> extends Read<T, V>, Write<T, V> {

}
