package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.Pair;
import org.apache.commons.math3.distribution.RealDistribution;

public class AbstractRealDistribution<D extends RealDistribution> extends AbstractDistribution<Double> {
    D d;

//    public abstract Distribution<Double> plus( Distribution<?> otherDist );
//    public abstract Distribution<Double> minus( Distribution<?> otherDist );
//    public abstract Distribution<Double> times( Distribution<?> otherDist );ß
//    public abstract Distribution<Double> dividedBy( Distribution<?> otherDist );

    @Override public double probability( Double t ) {
        return d.probability( t );
    }

    @Override public double pdf( Double dubl ) {
        double p = d.density( dubl );
        return p;
    }

    @Override public Sample<Double> sample() {
        Double ds = d.sample();
        double w = 1.0; //pdf( ds );
        //return new Pair(ds, w);
        return new SimpleSample<>( ds, 1.0 );
    }

    @Override public Sample<Double> sample( Distribution<Double> bias ) {
        Sample<Double> s = bias.sample();
        double w = pdf( s.value() ) / bias.pdf( s.value() );
        return new SimpleSample<>( s.value(), w );
    }

    @Override public double cumulativeProbability( Double t ) {
        return d.cumulativeProbability( (Double)t );
    }

    @Override public Class<Double> getType() {
        return Double.class;
    }

}
