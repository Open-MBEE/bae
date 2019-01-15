package gov.nasa.jpl.ae.util.distributions;

public class SimpleSample<T> implements Sample<T> {
    protected T value;
    protected double weight;

    public SimpleSample(T value, double weight) {
        this.value = value;
        this.weight = weight;
    }

    @Override public T value() {
        return value;
    }

    @Override public double weight() {
        return weight;
    }

    @Override public String toString() {
        return this.getClass().getSimpleName() + "(" + value + ", " + weight + ")" ;
    }
}
