package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.BinomialDistribution;

/**
 * Created by dank on 6/29/17.
 */
public class BooleanDistribution implements Distribution<Boolean> {

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

    @Override public double cumulativeProbability( Boolean aBoolean ) {
        return d.cumulativeProbability( aBoolean ? 1 : 0 );
    }

    @Override public Boolean sample() {
        return d.sample() == 1;
    }

    @Override public Class<Boolean> getType() {
        return Boolean.class;
    }
}
