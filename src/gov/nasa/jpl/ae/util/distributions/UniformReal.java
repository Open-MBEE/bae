package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;

public class UniformReal extends AbstractRealDistribution<UniformRealDistribution> {

    public UniformReal( int lower, int upper) throws NumberIsTooLargeException {
        d = new UniformRealDistribution( Distribution.random, lower, upper);
    }

}
