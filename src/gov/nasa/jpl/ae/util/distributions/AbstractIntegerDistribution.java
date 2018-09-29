package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.IntegerDistribution;

public class AbstractIntegerDistribution<D extends IntegerDistribution> implements Distribution<Integer> {
    D d;

    //    public abstract Distribution<Double> plus( Distribution<?> otherDist );
    //    public abstract Distribution<Double> minus( Distribution<?> otherDist );
    //    public abstract Distribution<Double> times( Distribution<?> otherDist );
    //    public abstract Distribution<Double> dividedBy( Distribution<?> otherDist );

    @Override public double probability( Integer t ) {
        return d.probability( t );
    }

    @Override public Integer sample() {
        return d.sample();
    }

    @Override public double cumulativeProbability( Integer t ) {
        return d.cumulativeProbability( (Integer)t );
    }

    @Override public Class<Integer> getType() {
        return Integer.class;
    }
}
