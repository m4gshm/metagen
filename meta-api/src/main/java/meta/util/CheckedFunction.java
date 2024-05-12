package meta.util;

/**
 * A function with one argument that returns a result and can throw an exception.
 * @param <T> argument type.
 * @param <R> result type.
 * @param <E> exception type
 */
@FunctionalInterface
public interface CheckedFunction<T, R, E extends Throwable> {
    R apply(T t) throws E;
}
