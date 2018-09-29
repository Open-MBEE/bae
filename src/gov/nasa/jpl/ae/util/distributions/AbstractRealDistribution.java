package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.RealDistribution;

public class AbstractRealDistribution<D extends RealDistribution> implements Distribution<Double> {
    D d;

//    public abstract Distribution<Double> plus( Distribution<?> otherDist );
//    public abstract Distribution<Double> minus( Distribution<?> otherDist );
//    public abstract Distribution<Double> times( Distribution<?> otherDist );
//    public abstract Distribution<Double> dividedBy( Distribution<?> otherDist );

    @Override public double probability( Double t ) {
        return d.probability( t );
    }

    @Override public Double sample() {
        return d.sample();
    }

    @Override public double cumulativeProbability( Double t ) {
        return d.cumulativeProbability( (Double)t );
    }

    @Override public Class<Double> getType() {
        return Double.class;
    }

}
