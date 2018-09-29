package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;

/**
 * Created by dank on 7/12/17.
 */
public class UniformInteger extends AbstractIntegerDistribution<UniformIntegerDistribution> {

    public UniformInteger( int lower, int upper) throws NumberIsTooLargeException {
        d = new UniformIntegerDistribution( Distribution.random, lower, upper);
    }

}
