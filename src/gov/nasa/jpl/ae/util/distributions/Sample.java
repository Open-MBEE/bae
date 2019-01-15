package gov.nasa.jpl.ae.util.distributions;

public interface Sample<T> {
    T value();
    double weight();
}
