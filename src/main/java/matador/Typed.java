package matador;

public interface Typed<T> {

    String name();

    Class<T> type();
}
