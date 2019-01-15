package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Utils;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.List;

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
        if ( bias != null ) {
            return sample(bias);
        }
        Double ds = d.sample();
        double w = 1.0; //pdf( ds );
        //return new Pair(ds, w);
        return new SimpleSample<>( ds, 1.0 );
    }

//    @Override public Sample<Double> sample( Distribution<Double> bias ) {
//        if ( bias == null ) return null;
//        Sample<Double> s = bias.sample();
//        double w = pdf( s.value() ) / bias.pdf( s.value() );
////        List<Pair<Double, Double>> ranges = supportSubtract( this, bias );
////        if ( !Utils.isNullOrEmpty(ranges ) ) {
//        double supportFactor = supportFactor( this, bias );
//        w *= supportFactor;
////        }
//        return new SimpleSample<>( s.value(), w );
//    }

    @Override public double cumulativeProbability( Double t ) {
        return d.cumulativeProbability( (Double)t );
    }

    @Override public Class<Double> getType() {
        return Double.class;
    }

    @Override public Double mean() {
        return d.getNumericalMean();
    }

    @Override public Double variance() {
        return d.getNumericalVariance();
    }

    @Override public Double supportLowerBound() {
        return d.getSupportLowerBound();
    }

    @Override public Double supportUpperBound() {
        return d.getSupportUpperBound();
    }

}
