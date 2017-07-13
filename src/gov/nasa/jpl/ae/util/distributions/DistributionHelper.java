package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.Pair;
import org.apache.commons.math3.distribution.*;

/**
 * Created by dank on 6/29/17.
 */
public class DistributionHelper {
    // Anonymous class

    public static boolean isDistribution(Object distribution) {
        if (distribution == null) {
            return false;
        }
        return distribution instanceof RealDistribution || distribution instanceof IntegerDistribution
                        || distribution instanceof MultivariateRealDistribution
                        || distribution instanceof EnumeratedDistribution;
    }

    public static BooleanDistribution equals(Object o1, Object o2) {

        BooleanDistribution d = null;
        if (o1 instanceof IntegerDistribution) {

            if (o2 instanceof IntegerDistribution) {
            } else if (o2 instanceof RealDistribution) {
            } else if (o2 instanceof MultivariateRealDistribution) {
            } else if (o2 instanceof EnumeratedDistribution) {
            } else {
                // o2 is an integer
                Pair<Boolean, Integer> result = ClassUtils.coerce(o2, Integer.class, false);
                if (result.first) {
                    d = new BooleanDistribution(((BinomialDistribution) o1).probability(result.second));
                }
            }
        } else if (o1 instanceof RealDistribution) {

            if (o2 instanceof IntegerDistribution) {
            } else if (o2 instanceof RealDistribution) {
            } else if (o2 instanceof MultivariateRealDistribution) {
            } else if (o2 instanceof EnumeratedDistribution) {
            } else {
                Pair<Boolean, Integer> result = ClassUtils.coerce(o2, Integer.class, false);
                if (result.first) {
                    d = new BooleanDistribution(((RealDistribution) o1).probability(result.second));
                }
            }
        } else if (o1 instanceof MultivariateRealDistribution) {
            if (o2 instanceof IntegerDistribution) {
            } else if (o2 instanceof RealDistribution) {
            } else if (o2 instanceof MultivariateRealDistribution) {
            } else if (o2 instanceof EnumeratedDistribution) {
            } else {
                // TODO: To find the density of MultivariateRealDistributions you need to have a list of doubles
                //  that are passed into the constructor.
                Pair<Boolean, Integer> result = ClassUtils.coerce(o2, Integer.class, false);
                if (result.first) {
//                    d = new BooleanDistribution(((MultivariateRealDistribution) o1).density(result.second));
                }
            }
        } else if (o1 instanceof EnumeratedDistribution) {
            if (o2 instanceof IntegerDistribution) {
            } else if (o2 instanceof RealDistribution) {
            } else if (o2 instanceof MultivariateRealDistribution) {
            } else if (o2 instanceof EnumeratedDistribution) {
            } else {
                // o2 is an integer
                // TODO: Need to figure out what kind of probability that this would be.
                //  EnumaredDistribution is a Discrete Distribution with <Key, Probability> pairs
/*                Pair<Boolean, Integer> result = ClassUtils.coerce(o2, Integer.class, false);
                if (result.first) {
                    // p = ((EnumeratedDistribution) o1).(result.second);
                    for(Pair<Object, Double> o : o2){

                    }
                }*/
            }
        } else {
            // o1 is an integer
            if (o2 instanceof IntegerDistribution) {
                // o2 is an integer
                Pair<Boolean, Integer> result = ClassUtils.coerce(o1, Integer.class, false);
                if (result.first) {
                    d = new BooleanDistribution(((BinomialDistribution) o2).probability(result.second));
                }
            } else if (o2 instanceof RealDistribution) {
            } else if (o2 instanceof MultivariateRealDistribution) {
            } else if (o2 instanceof EnumeratedDistribution) {
            } else {
                // o2 is an integer
                return new BooleanDistribution(o1.equals(o2)? 1.0 : 0.0);
            }
        }
        return d;
    }

    public static void main(String args[]) {

    }
}
