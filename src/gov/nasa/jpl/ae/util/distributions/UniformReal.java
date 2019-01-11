package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.exception.NumberIsTooLargeException;

import java.util.TreeMap;

public class UniformReal extends AbstractRealDistribution<UniformRealDistribution> {

    public UniformReal( double lower, double upper) throws NumberIsTooLargeException {
        d = new UniformRealDistribution( Distribution.random, lower, upper);
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
