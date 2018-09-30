package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.Call;
import gov.nasa.jpl.ae.event.Expression;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Pair;

import java.util.*;

public class FunctionOfDistributions<T> implements Distribution<T> {
    /**
     * The function call or constructor that produces this distribution
     */
    Call call;
    /**
     * a true distribution that we may not have.
     */
    Distribution distribution = null;
    /**
     * An approximate distribution
     */
    Distribution approximation = null;
    /**
     * An upperbound on the error of the approximation
     */
    Expression<Double> error = null;

    /**
     * The random variables on which this distribution depends.  We use these
     * to determine independence when combining in expressions with multiple
     * random variables.
     */
    Set<Variable> randomVariables = null;


    // Sampling Members
    //
    // Sampling can be done without remembering the samples and just updating the
    // combinedValue, which would be a mean in many cases.

    /**
     * The set of samples that approximate the distribution.
     */
    SampleDistribution<T> samples = null;

    /**
     * Whether to store samples, as opposed to just keeping a running average in
     * the combinedValue
     */
    boolean recordingSamples = true;
    /**
     * Whether to update the combinedValue; this must be true if recordingSamples is false.
     */
    boolean combiningSamples = true;
    /**
     * The sum of the weights of the samples, used to update the combinedValue
     */
    double totalWeight = 0.0;
    /**
     * The expected value or mean of the samples that is updated after each sample
     */
    Double combinedValue = null;

    public Class<T> type = null;

    @Override public double probability( T t ) {
        if ( distribution != null ) {
            return distribution.probability( t );
        }
        // TODO  -- throw error?
        return 0;
    }

    @Override public double pdf( T t ) {
        if ( distribution != null ) {
            return distribution.pdf( t );
        }
        // TODO  -- throw error?
        return 0;
    }

    /**
     * Sample this distribution and record and/or combine the samples according
     * the recordSamples and combiningSamples flags.
     *
     * @return the Sample
     */
    @Override public Sample<T> sample() {
        return sample(null);
    }
    /**
     * Sample this distribution and record and/or combine the samples according
     * the recordSamples and combiningSamples flags.  If we have a clean
     * distribution, then we can sample that directly.  Otherwise, we need to
     * dig into the call's arguments, sample within them, and recompute the
     * call.  We pass and return a SampleChain in order to provide context and
     * sample efficiently.
     *
     * @param sampleChain a chain of samples from recursive calls to this
     *                    function for the arguments of call.
     * @return the Sample
     */
    public Sample<T> sample(SampleChain sampleChain) {
        Sample<T> p = tryToSample(sampleChain);
        if ( recordingSamples ) {
            if ( samples == null ) samples = new SampleDistribution<T>();
            samples.add( p );
        }
        if ( combiningSamples ) {
            if ( getType() == null ) {
                type = (Class<T>)p.value().getClass();
            }
            if ( combinedValue == null && Number.class.isAssignableFrom( getType() ) ) {
                combinedValue = ((Number)p.value()).doubleValue();
            }
            if ( getType() == Double.class ) {
                Double cd = (totalWeight*((Double)combinedValue) +
                             p.weight() * ((Double)p.value())) /
                            (totalWeight + p.weight());
                combinedValue = cd;
            } else if (getType() == Integer.class) {
                Double cd = (totalWeight*((Double)combinedValue) +
                             p.weight() * ((Double)p.value())) /
                            (totalWeight + p.weight());
                combinedValue = cd;
            } else {
                // TODO -- FIXME!!! -- what about boolean samples? or from a discrete set?
            }
            totalWeight += p.weight();
        }
        return p;
    }

    /**
     * If we have a clean distribution, then we can sample that directly.  Otherwise,
     * we need to dig into the call's arguments, sample within them, and recompute the
     * call.  We pass and return a SampleChain in order to provide context and
     * sample efficiently.
     *
     * @return a Sample
     */
    protected Sample<T> tryToSample(SampleChain sampleChain) {
        Object r = null;
        try {
            r = call.evaluate( true );
        } catch ( Throwable t ) {
            // ignore
        }
        if ( r == null) return null;
        T t = null;
        Double w = null;
        if ( getType() == null || getType().isInstance( r ) ) {
            t = (T)r;
            w = 1.0;
            return new SimpleSample<>(t, w);
        }

        Distribution<?> d = null;
        try {
            d = Expression.evaluate( r, Distribution.class, true );
        } catch ( Throwable e ) {
            // ignore
        }
        if ( d instanceof FunctionOfDistributions ) {
            if ( d != this ) {
                Debug.error(true, false,
                            "FunctionOfDistributions.tryToSample(): call returned a different distribution than this one!");
            }
        } else if ( d instanceof SampleDistribution ) {
            return (Sample<T>)d.sample();
            //return new SimpleSample<T>( d, 1.0 );
        } else if ( d != null ) {
            this.distribution = d;
            if ( d != null ) {
                Object o = d.sample();
                if ( getType() == null || getType().isInstance( o ) ) {
                    t = (T)o;
                }
                w = pdf( t );
                return new SimpleSample<>(t, w);
            }
        }

        // sample in call
        return sampleCall(sampleChain);
    }

    protected boolean updateArgumentForSampling( Object arg,
                                                 Set<Variable> varsToResample,
                                                 SampleChain sampleChain ) {
        if ( arg == null ) return false;
        Set<Variable> randomVars = getRandomVars(arg);
        // TODO
        return false;
    }

    public boolean isVarRandom(Variable v) {
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
    }

    public Set<Variable> getRandomVars( Collection<?> c ) {
        if ( c == null ) return null;
        return getRandomVars( c.toArray() );
    }
    public Set<Variable> getRandomVars( Object[] a ) {
        if ( a == null ) return null;
        Set<Variable> vars = new LinkedHashSet<>();
        for ( int i = 0; i < a.length; ++i ) {
            Set<Variable> s = getRandomVars( a[i] );
            if ( s != null ) {
                if ( vars.size() >= s.size() ) {
                    vars.addAll( s );
                } else {
                    s.addAll( vars );
                    vars = s;
                }
            }
        }
        return vars;
    }
    public Set<Variable> getRandomVars( Map map ) {
        if ( map == null ) return null;
        Set<Variable> vars1 = getRandomVars( map.keySet() );
        Set<Variable> vars2 =  getRandomVars( map.values() );
        if ( vars1 == null && vars2 == null ) {
            return new LinkedHashSet<>();
        }
        if ( vars1 == null ) return vars2;
        if ( vars2 == null ) return vars1;
        if ( vars1.size() >= vars2.size() ) {
            vars1.addAll( vars2 );
            return vars1;
        }
        vars2.addAll( vars1 );
        return vars2;
    }
    private Set<Variable> getRandomVars( Object o ) {
        if ( o == null ) return null;

        // Check if an array
        if ( o.getClass().isArray() ) {
            return getRandomVars( (Object[])o );
        }

        // Check if a random variable.
        if ( o instanceof Variable ) {
            Variable v = (Variable)o;
            if ( isVarRandom( v ) ) {
                Set<Variable> randomVars = new LinkedHashSet<>();
                randomVars.add( v );
                return randomVars;
            }
            return getRandomVars( v.getValue( false ) );
        }

        // Check if a Collection
        if ( o instanceof Collection ) {
            return getRandomVars( (Collection)o );
        }

        // Check if a Map
        if ( o instanceof Map ) {
            return getRandomVars( (Map)o );
        }

        return new LinkedHashSet<>();
    }

    /**
     * Check the evaluatedArguments of the call.  If they are distributions, sample
     * them reusing any already sampled variables in the SampleChain.  Temporarily replace the
     * arguments with the corresponding samples and re-evaluate the call.
     * <p>
     * If the
     * arguments do not share any of the same random variables with the SampleChain,
     * then the arguments may be sampled directly; else, the random variables in the
     * arguments and their expressions must be replaced with their sampled values.
     * </p>
     * After sampling the arguments, check them again.  An argument that was sampled directly may
     * need to be resampled for a random variable that was sampled afterwards.
     * <p>
     * Alternatively, check and see what variables show up multiple times and mark
     * those to be sampled.
     * </p>
     *
     * @param sampleChain the SampleChain
     *
     * @return a possibly new sampleChain updated with the new sample(s) or some
     * other Sample if this is the first.
     */
    protected Sample<T> sampleCall(SampleChain sampleChain) {
        Map<Variable, Variable> substitutions = new LinkedHashMap<>();

        // Walk through the arguments and find which variables need to be sampled.
        // Those variables are the ones that are already in the sample chain or
        // show up in more than one argument.
        Set<Variable> varsToResample = sampleChain.getVariables();
        Object[] evalArgs = call.getEvaluatedArguments();
        for ( int i = 0; i < call.getArguments().size(); ++i ) {
            Object arg = call.getArgument( i );
            Object evaluatedArg = evalArgs != null && evalArgs.length > i ? evalArgs[ i ] : null;

            Set<Variable> rVars = getRandomVars( arg );

        }

        // Save away the current evaluated arguments that will be replaced.
        // Make sure to set stale.

        // Go ahead and sample the variables that haven't been sampled and replace
        // the variables with values in all of the arguments.

        // Walk through the arguments and check to see if they have random
        // variables that must be replaced.  If they do not, just sample the
        // argument directly.  If the argument is one of the random variables,
        // replace it with its sampled value.  If a FunctionOfDistributions,
        // call recursively with the updated sampleChain.
        Object[] evalArgs = call.getEvaluatedArguments();
        for ( int i = 0; i < call.getArguments().size(); ++i ) {
            Object arg = call.getArgument( i );
            Object evaluatedArg = evalArgs != null && evalArgs.length > i ? evalArgs[ i ] : null;
            if ( evaluatedArg != null ) {
                boolean updated = updateArgumentForSampling( evaluatedArg, varsToResample, sampleChain );
            }
        }

        // Replace the arguments with their original values.  Make sure to set
        // stale or add back the returnValue.

        return null;
    }

    @Override public double cumulativeProbability( T t ) {
        if ( distribution != null ) {
            return distribution.cumulativeProbability( t );
        }
        if ( approximation != null ) {
            if ( samples != null && !samples.isEmpty() ) {
                if ( CompareUtils.compare(error, samples.error) <= 0 ) {
                    return samples.cumulativeProbability( t );
                }
            }
            return approximation.cumulativeProbability( t );
        }
        if ( samples != null && !samples.isEmpty() ) {
            return samples.cumulativeProbability( t );
        }
        return 0;
    }

    @Override public Class<T> getType() {
        return type;
    }
}
