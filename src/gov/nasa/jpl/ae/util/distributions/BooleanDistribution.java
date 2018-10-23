package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.BinomialDistribution;

/**
 * Created by dank on 6/29/17.
 */
public class BooleanDistribution extends AbstractDistribution<Boolean> {

    protected BinomialDistribution d;

    public BooleanDistribution(double p) {
        d = new BinomialDistribution( Distribution.random, 1, p);
    }

    public double probability() {
        return probability( true );
    }

    @Override public double probability( Boolean aBoolean ) {
        return d.probability( aBoolean ? 1 : 0 );
    }

    @Override public double pdf( Boolean aBoolean ) {
        return probability( aBoolean );
    }

    @Override public double cumulativeProbability( Boolean aBoolean ) {
        return d.cumulativeProbability( aBoolean ? 1 : 0 );
    }

    @Override public Sample<Boolean> sample() {
        // we should try to never ... ???
        int s = d.sample();
        double w = 1.0;//d.probability( s );
        return new SimpleSample<>( s == 1, w );
    }

    @Override public Sample<Boolean> sample( Distribution<Boolean> bias ) {
        Sample<Boolean> ds = bias.sample();
        double w = pdf(ds.value()) / bias.pdf(ds.value());//d.probability( s );
        return new SimpleSample<>( ds.value(), w );
    }

    @Override public Class<Boolean> getType() {
        return Boolean.class;
    }

    @Override public Double mean() {
        return d.getNumericalMean();
    }

    @Override public Double variance() {
        return d.getNumericalVariance();
    }

    @Override public String toString() {
        return this.getClass().getSimpleName() + "(" + d.getProbabilityOfSuccess() + ")";
    }
}
