package meta.util;

/**
 * Metadata element interface.
 * @param <T> the element type.
 */
public interface Typed<T> {

    String name();

    Class<T> type();
}
