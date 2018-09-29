package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.TimeVaryingMap;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Utils;
import org.apache.commons.math3.analysis.function.Gaussian;
import org.apache.commons.math3.distribution.*;

import static gov.nasa.jpl.ae.event.TimeVaryingMap.Inequality.EQ;

/**
 * Created by dank on 6/29/17.
 */
/*
TODO: Given a pseudo fixed rate, go through the variables and calculate the rate
Delay until next fault will be variables in K

Fixed size array, not worrying about more than 100 faults

Need to program a function called:
    - plus
    - minus
    - integrate
(df1) Duration from fault is the sample

tf1 = 0 + df1
tf2 = tf1 + df2
 */
public class DistributionHelper {
    // Anonymous class

    public static boolean isDistribution(Object distribution) {
        if (distribution == null) {
            return false;
        }
        if ( distribution instanceof Distribution ) {
            return true;
        }
        return distribution instanceof RealDistribution || distribution instanceof IntegerDistribution
                        || distribution instanceof MultivariateRealDistribution
                        || distribution instanceof EnumeratedDistribution;
    }

    /**
     * This compares the distributions as discrete distributions by finding the probability of success for every
     * k for each distributions and sums their probabilities. It will return a boolean distribution with the summed
     * probability.
     *
     * @param o1
     * @param o2
     * @return
     */
    public static BooleanDistribution equals(Object o1, Object o2) {
        if ( true ) {
            Object d =compare( o1, o2, EQ );
            if ( d instanceof BooleanDistribution ) {
                return (BooleanDistribution)d;
            }
            //return null;
        }

        BooleanDistribution d = null;
        if (o1 instanceof AbstractIntegerDistribution) {
            if (o2 instanceof AbstractIntegerDistribution) {

                BinomialDistribution bd1 = (BinomialDistribution) o1;
                BinomialDistribution bd2 = (BinomialDistribution) o2;

                int trial1 = bd1.getNumberOfTrials();
                int trial2 = bd2.getNumberOfTrials();

                int maxTrial = Math.max(trial1, trial2);
                int sum = 0;

                for (int i = 0; i < maxTrial; ++i) {
                    sum += bd1.probability(i) * bd2.probability(i);
                }

                d = new BooleanDistribution(sum);
                return d;

            } else if (o2 instanceof AbstractRealDistribution) {

            } else if (o2 instanceof MultivariateRealDistribution) {
            } else if (o2 instanceof EnumeratedDistribution) {
            } else {
                // o2 is an integer
                Pair<Boolean, Integer> result = ClassUtils.coerce(o2, Integer.class, false);
                if (result.first) {
                    d = new BooleanDistribution(((BinomialDistribution) o1).probability(result.second));
                }
            }
        } else if (o1 instanceof RealDistribution) {  // TODO - below looks wrong

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
        } else if (o1 instanceof MultivariateRealDistribution) {  // TODO - below looks wrong
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
        } else if (o1 instanceof EnumeratedDistribution) {  // TODO - below looks wrong
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
        } else if (o1 == null) {

        } else {
            // o1 is an integer o
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
                return new BooleanDistribution(o1.equals(o2) ? 1.0 : 0.0);
            }
        }
        //return d;
        return null;
    }


    public static Distribution negative(Distribution d ) {
        if(d == null) return null;
        if(d instanceof NormalDistribution){
            double mu = ((NormalDistribution) d).getMean();
            double sigma = ((NormalDistribution) d).getStandardDeviation();
            return new Normal(-mu,sigma);
        }
        return null;
    }

    public static Object compare( Object o1, Object o2, TimeVaryingMap.Inequality i ) {
        if ( o1 instanceof Distribution ) {
            return compare( (Distribution<?>)o1, o2, i);
        }
        if ( o2 instanceof Distribution ) {
            return compare( o1, (Distribution<?>)o2, i);
        }
        return TimeVaryingMap.doesInequalityHold( o1, o2, i );
    }
    public static BooleanDistribution compare( Object o1, Distribution<?> d2,
                                               TimeVaryingMap.Inequality i ) {
        TimeVaryingMap.Inequality commutedI = TimeVaryingMap.commute( i );
        return compare( d2, o1, commutedI );
    }
    public static BooleanDistribution compare( Distribution<?> d1, Object o2,
                                               TimeVaryingMap.Inequality i ) {
        Distribution d2 = null;
        if ( o2 instanceof Distribution ) {
            d2 = (Distribution)o2;
            return compare(d1, d2, i);
        } else if ( o2 instanceof Number ) {
            if ( d1 instanceof AbstractRealDistribution ) {
                double n = ((Number)o2).doubleValue();
                return compareReal( (AbstractRealDistribution)d1, n, i );
            }
            if ( d1 instanceof AbstractIntegerDistribution ) {
                return compareInteger( (AbstractIntegerDistribution)d1, (Number)o2, i );
            }
        }
        return null;
    }

    /**
     * Compute the sum of two independent random variables according to their distributions.
     * <p>
     * Z = X + Y
     * </p><p>
     * <ul>
     *     <li>In general, f_Z(z)=integral(f_X(z-y)*f_Y(y)*dy)</li>
     *     <li>Normal(m1, v1) + Normal(m2, v2) = Normal(m1+m2, v1+v2)</li>
     *     <li>Binomial(m1, p) + Binomial(m2, p) = Binomial(m1+m2, p)</li>
     *     <li>Geometric_1(p) + Geometric_2(p) + ... + Geometric_k(p) = NegativeBinomial(p, k)</li>
     *     <li>Uniform(a1,b1)+ Uniform(a2,b2) = triangle((a1+a2,0), ((a1+b1+a2+b2)/2, 2/(b1+b2-a1-a2)), (b1+b2,0)) // yes, I made that up</li>
     *     <li>Exp(m) + Exp(m) = z*m^2*e^(-m*z)</li>
     * </ul>
     * </p>
     *
     * @param d1
     * @param d2
     * @return
     */
    public static Distribution plus(Distribution d1, Distribution d2) {
        if ( d1 == null || d2 == null ) return null;

        return null;
    }
    public static Distribution minus(Distribution d1, Distribution d2) {
        if ( d1 == null || d2 == null ) return null;
        return plus( d1, negative(d2) );
    }
    public static BooleanDistribution compare( Distribution<?> d1,
                                               Distribution<?> d2,
                                               TimeVaryingMap.Inequality i ) {
        if ( d1 == null || d2 == null ) return null;
        Double p = null;
        Distribution<?> d3 = minus(d1, d2);
        if ( d3 == null ) return null;
        return compare(d3, (Double)0.0, i);
    }

    public static BooleanDistribution compareInteger( AbstractIntegerDistribution d,
                                                      Number n,
                                                      TimeVaryingMap.Inequality i ) {
        if ( d == null || n == null ) return null;
        Double p = null;
        Integer t = n.intValue();
        if ( !Utils.valuesEqual( t.doubleValue(), n.doubleValue() ) ) {
            p = 0.0;
        } else {
            switch ( i ) {
                case EQ:
                    p = d.probability( t );
                    break;
                case NEQ:
                    p = 1.0 - d.probability( t );
                    break;
                case LT:
                    p = d.cumulativeProbability( t ) - d.probability( t );
                    break;
                case LTE:
                    p = d.cumulativeProbability( t );
                    break;
                case GT:
                    p = 1.0 - d.cumulativeProbability( t );
                    break;
                case GTE:
                    p = 1.0 - d.cumulativeProbability( t ) + d.probability( t );
                    break;
                default:
                    // TODO -- error
            }
        }
        if ( p == null ) return null;
        return new BooleanDistribution( p );
    }

    public static BooleanDistribution compareReal( AbstractRealDistribution d,
                                                   Double n,
                                                   TimeVaryingMap.Inequality i ) {
        if ( d == null || n == null ) return null;
        Double p = null;
        switch(i) {
            case EQ:
                p = d.probability( n );
                break;
            case NEQ:
                p = 1.0 - d.probability( n );
                break;
            case LT:
            case LTE:
                p = d.cumulativeProbability( n );
                break;
            case GT:
            case GTE:
                p = 1.0 - d.cumulativeProbability( n );
                break;
            default:
                // TODO -- error
        };
        if ( p == null ) return null;
        return new BooleanDistribution( p );
    }


    public static Double test() {
        BinomialDistribution o1 = new BinomialDistribution(5, .5);
        BinomialDistribution o2 = new BinomialDistribution(8, .3);
        //        System.out.println(equals(o1, o2).getProbabilityOfSuccess());
        return equals(o1, o2).probability();
        //        return 1.2;
    }


    public static void main(String args[]) {
        test();
    }

}
