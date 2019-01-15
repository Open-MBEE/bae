package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.ExponentialDistribution;

import java.util.TreeMap;

public class Exponential extends AbstractRealDistribution<ExponentialDistribution> {
    //protected ExponentialDistribution d;
    /**
     * Create an exponential distribution with the given mean.
     *
     * @param mean mean of this distribution.
     */
    public Exponential( double mean ) {
        d = new ExponentialDistribution( Distribution.random, mean );
        parameters = new TreeMap<>();
        parameters.put("mean", mean);
    }
    public Exponential( double mean, Distribution<Double> bias) {
        d = new ExponentialDistribution( Distribution.random, mean );
        this.bias = bias;
    }
    @Override public String toString() {
        return this.getClass().getSimpleName() + "(" + d.getMean() +
               (bias == null ? "" : ", " + bias) + ")" ;
    }

}
