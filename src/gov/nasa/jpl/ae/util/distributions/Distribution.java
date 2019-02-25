package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.HasOwner;
import gov.nasa.jpl.ae.solver.HasDomain;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Random;
import gov.nasa.jpl.mbee.util.Wraps;
import org.apache.commons.math3.random.JDKRandomGenerator;

/**
 * Created by dank on 6/29/17.
 */
public interface Distribution<T> extends HasOwner, Wraps<T> {  // TODO -- implements Domain or HasDomain

    public static JDKRandomGenerator random = new JDKRandomGenerator( (int)Random.getSeed() );
    public static void reset() {
        random.setSeed( (int)Random.getSeed() );
    }

    double probability(T t);
    double pdf(T t);
    Sample<T> sample();
    Sample<T> sample( Distribution<T> bias );
    double cumulativeProbability(T t);
    Class<T> getType();
    Number mean();
    Double variance();
    Double supportLowerBound();
    Double supportUpperBound();
    Double biasSupportFactor( Distribution<T> bias );
    // TODO -- add these below and maybe
    // T expectedValue();
    //double cdf(T t);
    //T quantile(double p);
}
