package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.Random;
import org.apache.commons.math3.distribution.ExponentialDistribution;

public class Exponential extends ExponentialDistribution implements Distribution {
    /**
     * Create an exponential distribution with the given mean.
     *
     * @param mean mean of this distribution.
     */
    public Exponential( double mean ) {
        super( Distribution.random, mean );
    }
}
