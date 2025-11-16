package io.github.m4gshm.meta;

/**
 * Read, write property accessors that can throws an Exception.
 *
 * @param <T> bean type
 * @param <V> property type
 * @param <RE> read value exception type
 * @param <WE> write value exception type
 */
public interface CheckedReadWrite<T, V, RE extends Exception, WE extends Exception>
        extends CheckedRead<T, V, RE>, CheckedWrite<T, V, WE> {
}
