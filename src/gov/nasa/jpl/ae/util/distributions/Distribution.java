package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.solver.HasDomain;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Random;
import org.apache.commons.math3.random.JDKRandomGenerator;

/**
 * Created by dank on 6/29/17.
 */
public interface Distribution<T> {

    public static JDKRandomGenerator random = new JDKRandomGenerator( (int)Random.getSeed() );

    double probability(T t);
    double pdf(T t);
    Sample<T> sample();
    double cumulativeProbability(T t);
    Class<T> getType();
}
