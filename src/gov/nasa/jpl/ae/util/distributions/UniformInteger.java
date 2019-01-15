package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.UniformIntegerDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;

import java.util.LinkedHashMap;
import java.util.TreeMap;

/**
 * Created by dank on 7/12/17.
 */
public class UniformInteger extends AbstractIntegerDistribution<UniformIntegerDistribution> {

    public UniformInteger( int lower, int upper) throws NumberIsTooLargeException {
        d = new UniformIntegerDistribution( Distribution.random, lower, upper);
        parameters = new TreeMap<>();
        parameters.put("lowerBound", lower);
        parameters.put("upperBound", upper);
    }

    @Override public String toString() {
        return this.getClass().getSimpleName() +
               "(" + d.getSupportLowerBound() + ", " + d.getSupportUpperBound() +
               (bias == null ? "" : ", " + bias) + ")" ;
    }
}
