package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.ExponentialDistribution;

public class Exponential extends AbstractRealDistribution<ExponentialDistribution> {
    //protected ExponentialDistribution d;
    /**
     * Create an exponential distribution with the given mean.
     *
     * @param mean mean of this distribution.
     */
    public Exponential( double mean ) {
        d = new ExponentialDistribution( Distribution.random, mean );
    }
}
