package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.Random;
import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.random.RandomGenerator;

/**
 * Created by dank on 7/12/17.
 */
public class UniformInteger extends UniformIntegerDistribution implements Distribution {
    public UniformInteger( int lower, int upper) throws NumberIsTooLargeException {
        super( Distribution.random, lower, upper);
    }
}
