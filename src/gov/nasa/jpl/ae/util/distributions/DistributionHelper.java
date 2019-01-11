package gov.nasa.jpl.ae.util.distributions;

import com.google.errorprone.annotations.Var;
import gov.nasa.jpl.ae.event.*;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.mbee.util.*;
import org.apache.commons.math3.distribution.*;

import java.lang.reflect.Method;
import java.util.*;

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

    public static boolean isDistribution(Object o) {
        Distribution d = getDistribution( o );
        return d != null;
//        if (o == null) {
//            return false;
//        }
//        if ( o instanceof Distribution ) {
//            return true;
//        }
//        if (o instanceof RealDistribution || o instanceof IntegerDistribution
//                        || o instanceof MultivariateRealDistribution
//                        || o instanceof org.apache.commons.math3.distribution.EnumeratedDistribution ) {
//            return true;
//        }
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
        Object result = null;
        if ( o1 instanceof Distribution ) {
            result = compare( (Distribution<?>)o1, o2, i);
        } else if ( o2 instanceof Distribution ) {
            result = compare( o1, (Distribution<?>)o2, i);
        } else {
            result = TimeVaryingMap.doesInequalityHold( o1, o2, i );
        }
        if ( result == null ) {
            result = new FunctionOfDistributions<>();
            FunctionOfDistributions d = ( (FunctionOfDistributions)result );
            d.call = new DistributionFunctionCall( null, Functions.class, "compare", new Object[] {o1, o2, i}, Boolean.class );
            d.type = Boolean.class;
        }
        return result;
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

    protected static Method ifThenElseMethod = null;
    protected static Method getIfThenElseMethod() {
        if ( ifThenElseMethod != null ) return ifThenElseMethod;
        try {
        ifThenElseMethod =
            Functions.class.getMethod( "ifThenElse",
                                       new Class[] {Object.class, Object.class, Object.class} );
        } catch ( NoSuchMethodException e ) {
            e.printStackTrace();
        }
        return ifThenElseMethod;
    }

    public static Distribution ifThenElse( Object condition, Object thenT, Object elseT ) {
        FunctionOfDistributions d = new FunctionOfDistributions<>();
        Class<?> thenType = (Class<?>)ClassUtils.getType( thenT );
        Class<?> elseType = (Class<?>)ClassUtils.getType( elseT );
        Class type = ClassUtils.dominantTypeClass( thenType, elseType );
        DistributionFunctionCall call =
                new DistributionFunctionCall(null, getIfThenElseMethod(),
                                              new Object[] {condition, thenT, elseT},
                                              type );
        d.call = call;
        return d;
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
    public static Distribution plus( //Object arg1, Object arg2,
                                     Distribution d1, Distribution d2) {
        if ( d1 instanceof Normal && d2 instanceof Normal ) {
            Normal n1 = (Normal)d1;
            Normal n2 = (Normal)d2;
            Normal s = new Normal( n1.d.getMean() + n2.d.getMean(),
                                   Math.sqrt( n1.d.getNumericalVariance() +
                                              n2.d.getNumericalVariance() ) );
            return s;
        }
        if ( d1 == null || d2 == null ) return null;
        FunctionOfDistributions d = new FunctionOfDistributions<>();
        DistributionFunctionCall call =
                new DistributionFunctionCall( null,
                                              Functions.class, "plus",
                                              new Object[] {d1, d2},
                                              d1.getType() );
        d.call = call;
        return d;
    }

    public static Object plus( //Object arg1, Object arg2,
                               Distribution d1, Object o2 ) {
        if ( o2 instanceof Distribution ) {
            return plus( //arg1, arg2,
                         d1, (Distribution<?>)o2);
        }
        if ( o2 == null ) return null;
        if ( d1 instanceof Normal && o2 instanceof Number ) {
            Normal n1 = (Normal)d1;
            Number n2 = (Number)o2;
            Normal s = new Normal( n1.d.getMean() + n2.doubleValue(),
                                   n1.d.getStandardDeviation() );
            return s;
        }
        FunctionOfDistributions d = new FunctionOfDistributions<>();
        DistributionFunctionCall call =
                new DistributionFunctionCall( null,
                                              Functions.class, "plus",
                                              new Object[] {d1, o2},
                                              d1.getType() );
        d.call = call;
        return d;
    }

    /*
    public static Object plus( Object arg1, Object arg2, Object val1, Object val2 ) {
        Variable v1 = null;
        Variable v2 = null;
        Object a1 = arg1;
        Object a2 = arg2;
        try {
            v1 = Expression.evaluate( arg1, Variable.class, false, false );
            if ( v1 != null ) a1 = v1;
        } catch (Throwable t) {}
        try {
            v2 = Expression.evaluate( arg2, Variable.class, false, false );
            if ( v2 != null ) a2 = v2;
        } catch (Throwable t) {}

        boolean isDist1 = arg1 instanceof Distribution;
        boolean isDist2 = arg2 instanceof Distribution;
        boolean isNorm1 = arg1 instanceof Normal;
        boolean isNorm2 = arg2 instanceof Normal;
        boolean isNum1 = arg1 instanceof Number;
        boolean isNum2 = arg2 instanceof Number;


    }
*/
    public static Object plus( //Object arg1, Object arg2,
                               Object value1, Object value2 ) {
        if ( value1 instanceof Distribution ) {
            return plus( //arg1, arg2,
                         (Distribution<?>)value1, value2);
        }
        if ( value2 instanceof Distribution ) {
            return plus( //arg2, arg1,
                         (Distribution<?>)value2, value1);
        }
        return null;  // TODO -- error
    }



//    public static Distribution minus(Distribution d1, Distribution d2) {
//        return minus( null, null, d1, d2 );
//    }
    public static Distribution minus(//Object arg1, Object arg2,
                                     Distribution d1, Distribution d2) {
        if ( d1 == null || d2 == null ) return null;
        return plus( //arg1, arg2,
                     d1, negative(d2) );
    }

    /**
     * Compute the product of two independent random variables according to their distributions.
     * <p>
     * Z = X * Y
     * </p>
     *
     * @param d1
     * @param d2
     * @return
     */
    public static Distribution times(Distribution d1, Distribution d2) {
        if ( d1 == null || d2 == null ) return null;
        FunctionOfDistributions d = new FunctionOfDistributions<>();
        DistributionFunctionCall call =
                new DistributionFunctionCall( null,
                                              Functions.class, "times",
                                              new Object[] {d1, d2},
                                              d1.getType() );
        d.call = call;
        return d;
    }

    public static Object times( Distribution d1, Object o2 ) {
        if ( o2 instanceof Distribution ) {
            return times( d1, (Distribution<?>)o2);
        }
        if ( o2 == null ) return null;
        if ( d1 instanceof Normal && o2 instanceof Number ) {
            Normal n1 = (Normal)d1;
            Number n2 = (Number)o2;
            Normal s = new Normal( n1.d.getMean() * n2.doubleValue(),
                                   n1.d.getStandardDeviation() *
                                   Math.abs(n2.doubleValue()));
            return s;
        }
        FunctionOfDistributions d = new FunctionOfDistributions<>();
        DistributionFunctionCall call =
                new DistributionFunctionCall( null,
                                              Functions.class, "times",
                                              new Object[] {d1, o2},
                                              d1.getType() );
        d.call = call;
        return d;
    }

    public static Object times( Object o1, Object o2 ) {
        if ( o1 instanceof Distribution ) {
            return times( (Distribution<?>)o1, o2);
        }
        if ( o2 instanceof Distribution ) {
            return times( (Distribution<?>)o2, o1);
        }
        return null;  // TODO -- error
    }


    /**
     * Compute the quotient of two independent random variables according to their distributions.
     * <p>
     * Z = X / Y
     * </p>
     * <p>
     * TODO<br>
     * The ratio of two Normal distributions each with mean, zero, is the Cauchy distribution.
     * If not zero, there is a long formula for a Gaussian ration distribution.  There are formulas
     * for ratios of other distributions and an integral for the general case.
     *
     * @param d1
     * @param d2
     * @return
     */
    public static Distribution divide(Distribution d1, Distribution d2) {
        if ( d1 == null || d2 == null ) return null;
        FunctionOfDistributions d = new FunctionOfDistributions<>();
        DistributionFunctionCall call =
                new DistributionFunctionCall( null,
                                              Functions.class, "divide",
                                              new Object[] {d1, d2},
                                              d1.getType() );
        d.call = call;
        return d;
    }

    public static Object divide( Distribution d1, Object o2 ) {
        if ( d1 == null || o2 == null ) return null;
        if ( o2 instanceof Distribution ) {
            return divide( d1, (Distribution<?>)o2);
        }
        Class<?> dType = d1.getType();
        Class<?> oType = (Class<?>)ClassUtils.getType( o2 );
        Class type = ClassUtils.dominantTypeClass( dType, oType );

        FunctionOfDistributions d = new FunctionOfDistributions<>();
        DistributionFunctionCall call =
                new DistributionFunctionCall( null,
                                              Functions.class, "divide",
                                              new Object[] {d1, o2},
                                              type );
        d.call = call;
        return d;
    }

    public static Object divide( Object o1, Distribution d2 ) {
        if ( o1 == null || d2 == null ) return null;
        if ( o1 instanceof Distribution ) {
            return divide( (Distribution<?>)o1, d2);
        }
        Class<?> oType = (Class<?>)ClassUtils.getType( o1 );
        Class<?> dType = d2.getType();
        Class type = ClassUtils.dominantTypeClass( oType, dType );

        FunctionOfDistributions d = new FunctionOfDistributions<>();
        DistributionFunctionCall call =
                new DistributionFunctionCall( null,
                                              Functions.class, "divide",
                                              new Object[] {o1, d2},
                                              type );
        d.call = call;
        return d;
    }


    public static Object divide( Object o1, Object o2 ) {
        if ( o1 instanceof Distribution ) {
            return divide( (Distribution<?>)o1, o2);
        }
        if ( o2 instanceof Distribution ) {
            return divide( o1, (Distribution<?>)o2);
        }
        return null;  // TODO -- error
    }

    public static Distribution floor(Distribution d1) {
        if ( d1 == null ) return null;
        FunctionOfDistributions d = new FunctionOfDistributions<>();
        DistributionFunctionCall call =
                new DistributionFunctionCall( null,
                                              Functions.class, "floor",
                                              new Object[] {d1},
                                              d1.getType() );
        d.call = call;
        return d;
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

    public static Double combineValues(Class type, Sample p, Double combinedValue, double totalWeight) {
        if ( p == null ) return null;
        if ( type == null && p.value() != null ) {
            type = p.value().getClass();
        }
        Number numVal = null;
        if ( p.value() instanceof Number ) {
            numVal = (Number)p.value();
        } else {
            try {
                numVal = Expression
                        .evaluate( p.value(), Number.class, false, false );
            } catch ( Throwable t ) {}
        }
        if ( combinedValue == null && numVal != null ) {
            combinedValue = numVal.doubleValue();
        } else if ( p.value() instanceof Number ) {
            Double cd = (totalWeight * combinedValue +
                         p.weight() * numVal.doubleValue()) /
                        (totalWeight + p.weight());
            combinedValue = cd;
        } else if ( p.value() instanceof Boolean ) {
            if ( combinedValue == null ) combinedValue = 0.0;
            Double cd = (totalWeight * combinedValue +
                         p.weight() * (((Boolean)p.value()) ? 1.0 : 0.0)) /
                        (totalWeight + p.weight());
            combinedValue = cd;
        } else {
            String value = p == null ? null : "" + p.value();
            String n = value == null || p.value() == null ? null : p.value().getClass().getSimpleName();
            Debug.error( "Can't combine value " + value + " of type " + n );
            // TODO -- FIXME!!! -- what about samples from a discrete set? Strings?
        }
        totalWeight += p.weight();
        return combinedValue;
    }


    public static Distribution getDistribution( Object o ) {
        Distribution<?> d = null;
        try {
            d = Expression.evaluate( o, Distribution.class, true );
        } catch ( Throwable e ) {
            // ignore
        }
        return d;
    }

    public static Set<Variable> getRandomVars( Collection<?> c, boolean deep ) {
        if ( c == null ) return null;
        return getRandomVars( c.toArray(), deep );
    }
    public static Set<Variable> getRandomVars( Object[] a, boolean deep ) {
        if ( a == null ) return null;
        Set<Variable> vars = new LinkedHashSet<>();
        for ( int i = 0; i < a.length; ++i ) {
            Set<Variable> s = getRandomVars( a[i], deep );
            vars = Utils.addAll( vars, s );
        }
        return vars;
    }
    public static Set<Variable> getRandomVars( Map map, boolean deep ) {
        if ( map == null ) return null;
        Set<Variable> vars1 = getRandomVars( map.keySet(), deep );
        Set<Variable> vars2 =  getRandomVars( map.values(), deep );
        if ( vars1 == null && vars2 == null ) {
            return new LinkedHashSet<>();
        }
        vars1 = Utils.addAll(vars1, vars2);
        return vars1;
    }
    public static Set<Variable> getRandomVars( Variable v, boolean deep ) {
        if ( v == null ) return null;
        Set<Variable> randomVars = new LinkedHashSet<>();
        if ( isVarRandom( v ) ) {
            randomVars.add( v );
            if ( !deep ) return randomVars;
            Object val = v.getValue( false );
            if ( val instanceof FunctionOfDistributions ) {
                Set<Variable> moreVars =
                        ( (FunctionOfDistributions)val ).getRandomVariables();
                randomVars = Utils.addAll( randomVars, moreVars );
            }
            return randomVars;
        }
        if ( !deep ) return randomVars;
        return getRandomVars( v.getValue( false ), deep );
    }

    protected static Set<Variable> getRandomVariables( Call call, boolean deep ) {
        Set<Variable> varsInArgs = null;
        if ( call.getObject() != null ) {
            varsInArgs = DistributionHelper.getRandomVars( call.getObject(), deep );
        }
        varsInArgs = Utils.addAll( varsInArgs, getRandomVars( call.getArguments(), deep ) );
        if ( deep ) {
            varsInArgs = Utils.addAll( varsInArgs, getRandomVars( call.getEvaluatedObject(), deep ) );
            varsInArgs = Utils.addAll( varsInArgs, getRandomVars( call.getEvaluatedArguments(), deep ) );
        }
        if ( varsInArgs == null ) return new LinkedHashSet<>();
        return varsInArgs;
    }

    public static Set<Variable> getRandomVars( Object o, boolean deep ) {
        if ( o == null ) return null;

        Set<Variable> randomVars = new LinkedHashSet<>();

        // Check if an array
        if ( o.getClass().isArray() ) {
            return getRandomVars( (Object[])o, deep );
        }

        // Check if a random variable.
        if ( o instanceof Variable ) {
            Variable v = (Variable)o;
            return getRandomVars( v, deep );
        }

        if ( o instanceof Expression ) {
            return getRandomVars( ( (Expression)o ).getExpression(), deep );
        }

        // Check if a Collection
        if ( o instanceof Collection ) {
            return getRandomVars( (Collection)o, deep );
        }

        // Check if a Map
        if ( o instanceof Map ) {
            return getRandomVars( (Map)o, deep );
        }

        if ( o instanceof Call ) {
            return getRandomVariables( (Call)o, deep );
        }

        if ( o instanceof HasParameters ) {
            Set<Parameter<?>> params =
                    ( (HasParameters)o ).getParameters( deep, null );
            return getRandomVars( params, deep );
        }

        if ( o instanceof Wraps ) {
            return getRandomVars( ( (Wraps)o ).getValue(false), deep );
        }
        return new LinkedHashSet<>();
    }

    public static boolean isVarRandom(Variable v) {
        if ( v == null ) return false;
        if ( v.getType() != null &&
             Distribution.class.isAssignableFrom( v.getType() ) ) {
            return true;
        }
        if ( v.hasValue() ) {
            if ( v.getValue( false ) instanceof Distribution ) {
                return true;
            }
        }
        return false;
    }

    public static Variable getRandomVar( Object object ) {
        Variable v = null;
        if ( object == null ) return null;
        if ( object instanceof Variable ) {
            v = (Variable)object;
        } else if ( object instanceof Distribution ) {
            Object owner = ( (Distribution)object ).getOwner();
            if ( owner instanceof Variable ) {
                v = (Variable)owner;
            }
        }
        if ( v == null ) {
            try {
                v = Expression.evaluate( object, Variable.class, true, false );
            } catch ( Throwable t ) {
            }
        }
        if ( isVarRandom( v ) ) {
            return v;
        }
        return null;
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
