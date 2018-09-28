package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.Random;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;

public class UniformReal extends UniformRealDistribution implements Distribution {
    public UniformReal( int lower, int upper) throws NumberIsTooLargeException {
        super( Distribution.random, lower, upper);
    }
}
