package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.*;
import gov.nasa.jpl.ae.solver.Domain;
import gov.nasa.jpl.ae.solver.HasDomain;
import gov.nasa.jpl.ae.solver.SingleValueDomain;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.ae.util.LamportClock;
import gov.nasa.jpl.ae.util.UsesClock;
import gov.nasa.jpl.mbee.util.*;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class FunctionOfDistributions<T> extends AbstractDistribution<T>
        implements HasParameters, ParameterListener, HasDomain, Groundable,
                   UsesClock {

    public final static int maxSamplesDefault = 7000;
    public Integer _maxSamples = null;

    public boolean setMaxSamples( int maxSamples ) {
        if ( this._maxSamples == null || this._maxSamples != maxSamples ) {
            this._maxSamples = maxSamples;
//            if ( this.getCall() != null ) {
//                this.getCall().setStale( true );
//            }
//            if ( getOwner() instanceof LazyUpdate ) {
//                ((LazyUpdate)getOwner()).setStale( true );
//            }
            if ( getOwner() instanceof Parameter ) {
                ((Parameter)getOwner()).update();
            }
            System.out.println(
                    ". . . . . . . . . . . . . . . . . . . setting max samples to "
                    + maxSamples );
        }
        return true;
    }

    public int getMaxSamples() {
        if ( _maxSamples == null ) {
            if ( call instanceof Functions.GetMember || (call.getMember() != null && call.getMember().getName().equals( "getMember" ))) {
                if ( call.returnValue != null && call.returnValue instanceof FunctionOfDistributions ) {
                    //getArguments() != null && call.getArguments().size() >= 1
                    int s = ( (FunctionOfDistributions)call.returnValue )
                            .getMaxSamples();
                    if ( s >= 0 ) {
                        return s;
                    }
                }
            }
            return maxSamplesDefault;
        }
        return _maxSamples;
    }
    /**
     * The function call or constructor that produces this distribution
     */
    public Call call;
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
     * The random variables are swapped out with non-random sample values.  Those
     * values are in variables that are substituted in the call.  Those variables
     * are the keys, and the values are the corresponding substituted out random
     * variables.
     */
    Map<Variable, Variable> substitutions = null;

    /**
     * Whether to store samples, as opposed to just keeping a running average in
     * the combinedValue
     */
    boolean recordingSamples = false;
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

    boolean samplingConverged = false;

    public Class<T> type = null;

    protected long lastUpdated = LamportClock.tick();


    public FunctionOfDistributions() {
        Debug.breakpoint();
    }

    @Override
    public int compareTo( Object obj ) {
        if ( this == obj ) return 0;
        if ( !( obj instanceof FunctionOfDistributions ) ) {
            return super.compareTo( obj );
        }
        FunctionOfDistributions o = (FunctionOfDistributions)obj;
        if ( call == o.call ) return 0;
        if ( call == null ) return -1;
        if ( o.call == null ) return 1;
        int c = call.compareStructure( o.call );
        return c;
    }

    @Override public double probability( T t ) {
        return probability(t, null);
    }

    public double probability( T t, T unsampledSupportRangeDefault ) {
        double p = -1.0; // default bad value

        Distribution d = getDistribution( true );

        if ( d != null ) {
            p = d.probability( t );
            if ( p <= 0.0 && this.combinedValue != null && this.combinedValue > 0.0 ) {
                p = this.combinedValue;
            }
            if ( unsampledSupportRangeDefault != null && d instanceof SampleDistribution ) {
                Double q = ((SampleDistribution)d).biasSupportFactor();
                if ( q != null ) {
                    double unsampledP =
                            Utils.valuesLooselyEqual( unsampledSupportRangeDefault,
                                                      t, true, false )
                            ? 1.0 : 0.0;
                    p = p * q + unsampledP * ( 1.0 - q );
                }
            }
        }
//
//        // go for analytic
//        if ( d != null && d == distribution ) {
//            p = distribution.probability( t );
//        }
//
//        if ( d != approximation && samplingConverged &&
//             samples != null && !samples.isEmpty() ) {
//            return samples.probability( t );
//        }

        System.out.println( "~~~~~~~~   Returning probability of " + p +
                            " for  distribution " + d + " for call: " + call);
        return p;
    }

    public static long sampleBatchSize(long numSamplesGoal, long numSoFar) {
        if ( numSamplesGoal - numSoFar < numSoFar ) {
            return numSoFar;
        }
        if ( numSoFar == 0 ) {
            long firstSampleBatchSize = numSamplesGoal / 32;
            return firstSampleBatchSize;
        }
        return numSoFar * 2;
    }

    protected File combinedValuesFile = null;
    protected File sampleValuesFile = null;
    protected boolean writeCombined = true;
    protected boolean writeSamples = true;

    //protected long samplesTaken = 0;
    protected long totalSamples = 0;

    public double sampleLoop() {
        Double lastCombinedValue = null;
        int matchCount = 0;
        //long totalSamples = this.getSamples() == null ? 0 : this.getSamples().size();

        if ( writeCombined && combinedValuesFile == null ) {
            // HERE!! -- TODO
            //combinedValuesFile =
        }

        int totalFailedSamples = 0;
        int maxSamples = getMaxSamples();
        if ( getSamples() == null ) this.samples = new SampleDistribution<>();
        getSamples().recordCombinedValues = true;
        getSamples().recordingSamples = false;
        System.out.println( "xxxxxxxxx   Sampling " + this );
        long batchSize = sampleBatchSize(maxSamples, totalSamples);
        long batchLimit = totalSamples + batchSize;
        while ( !this.samplingConverged && totalSamples < batchLimit &&
                totalSamples < maxSamples && totalFailedSamples < 4 ) {
            Sample<T> s = null;
            try {
                s = sample();
            } catch ( Throwable e ) {
                e.printStackTrace();
            }
            if ( s == null ) {
                System.out.println( "xxxxxxxxx   Sample failed!" );
                ++totalFailedSamples;
                continue;
            }
            T v = s.value();
            boolean isBool = Boolean.class.equals( getType() ) || Boolean.class.isInstance( v );
            ++totalSamples;
            if ( totalSamples <= 10 || totalSamples % 10000 == 0) {
                System.out.println( totalSamples + " samples with combined value " + this.combinedValue );
                System.out.println( "latest sample: " + s );
            }
            if ( lastCombinedValue != null && Expression
                    .valuesEqual( lastCombinedValue, this.combinedValue ) ) {
                ++matchCount;
            } else {
                matchCount = 0;
            }
            lastCombinedValue = this.combinedValue;
//            if ( matchCount >= (isBool ?
//                                (combinedValue == 0.0 ||
//                                 combinedValue == 1.0 ? 1000000 : 10)
//                                       : 10) ) {
//                System.out.println( "~~~~~~~~   Sampling converged!!" );
//                this.samplingConverged = true;
//            }
        }

        // If batch did not complete the set, then make sure this gets called again.
        if ( totalSamples < maxSamples && getOwner() instanceof Parameter ) {
            ((Parameter)getOwner()).update();
        }


        if (this.combinedValue == null ) {
            System.out.println( "~~~~~~~~   No combined value!  " +
                         totalSamples + " samples for call: " + call);
            return -1.0;
        }
        System.out.println( "~~~~~~~~   Combined value of " + combinedValue +
                            " for " + totalSamples + " samples for call: " + call);
        return combinedValue;
    }

    public double sample( T t ) {
        sampleLoop();
        double v = samples.probability( t );
        System.out.println( "sample distribution: probability(t) = " + v );;
        // TODO  -- throw error?
        if (this.combinedValue == null ){
            return -1.0;
        }
//        if ( !Utils.valuesEqual( v, combinedValue ) ) {
//            System.out.println( "Sample probability of " + t + " is " + v +
//                                ", but the combinedValue is " + combinedValue );
//        }
        return v;
    }


    @Override public double pdf( T t ) {
        if ( distribution != null ) {
            return distribution.pdf( t );
        }
        // TODO  -- throw error?
        return 0;
    }


    public boolean isSamplingConverged() {
        return this.samplingConverged;
    }
    public boolean hasSamplingConverged() {
        //!(samples == null  && samples.isEmpty() && !samplingConverged)
        return this.samplingConverged;
    }

    protected Distribution getDistribution( boolean sampleIfNecessary ) {
        // prefer analytic solution
        if ( distribution != null ) {
            return distribution;
        }
        // prefer approximation to samples if sampling has not coverged
        if ( ( !hasSamplingConverged() || samples == null || samples.isEmpty() ) &&
             approximation != null ) {  // TODO -- also compare error
            return approximation;
        }
        // if approximation is null or not preferred, return samples
        if ( samples != null  && !samples.isEmpty() ) {
            if ( sampleIfNecessary ) {
                sampleLoop();
            }
            return samples;
        }
        // return approximation if there's nothing else
        if ( approximation != null ) {
            return approximation;
        }

        // if there's nothing, we need to sample
        if ( samples == null ) {
            samples = new SampleDistribution<>();
        }
        if ( sampleIfNecessary ) {
            sampleLoop();
        }
        return samples;
    }

    @Override public Number mean() {
        Distribution d = getDistribution(true);
        if ( d != null ) return d.mean();
        return null;
    }

    @Override public Double variance() {
        Distribution d = getDistribution(true );
        if ( d != null ) return d.variance();
        return null;
    }

    @Override public Double supportLowerBound() {
        Distribution d = getDistribution( false );
        if ( d == this || d == null ) {
            return null;
        }
        return d.supportLowerBound();
    }
    @Override public Double supportUpperBound() {
        return null;
    }

    /**
     * Sample this distribution and record and/or combine the samples according
     * the recordSamples and combiningSamples flags.
     *
     * @return the Sample
     */
    @Override public Sample<T> sample() {
        return sample((SampleChain)null);
    }

    /**
     * A sample of the return value of the function is used to generate a sample
     * chain that produces that value.  So if the function is x + y and the
     * sample of the return value is 5, we need to generate a sample of x and y
     * such that the sum is 5 with a weight of P( x and y | x + y = 5 )/bias.pdf(5).
     * Need to figure this out!
     * @param bias
     * @return
     */
    @Override public Sample<T> sample( Distribution<T> bias ) {
        // TODO
        return null;
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
        if ( p == null ) return null;
        //if ( recordingSamples ) {
            if ( samples == null ) samples = new SampleDistribution<T>();
            samples.add( p );
        //}
        if ( combiningSamples ) {
            Double n = DistributionHelper.combineValues( getType(), p,
                                                         combinedValue, totalWeight );
            if ( n != null ) {
                combinedValue = n;
                totalWeight += p.weight();
            }
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
        boolean callReturnsFunctionOfDistributions =
                call.returnValue instanceof FunctionOfDistributions;
        try {
            //if ( !callReturnsFunctionOfDistributions ) {
                r = call.evaluate( true );
            //}
            if (!callReturnsFunctionOfDistributions) {
                callReturnsFunctionOfDistributions = r instanceof FunctionOfDistributions;
            }
        } catch ( Throwable t ) {
            // ignore
        }
        if ( r == null) return null;
        T t = null;
        Double w = null;
        if ( ( getType() == null && !callReturnsFunctionOfDistributions ) ||
             ( getType() != null && getType().isInstance( r ) ) ) {
            t = (T)r;
            w = 1.0;
            return new SimpleSample<>(t, w);
        }

        Distribution<?> d = DistributionHelper.getDistribution( r );
        if ( d instanceof FunctionOfDistributions ) {
//            if ( d != this ) {
//                Debug.error(true, false,
//                            "FunctionOfDistributions.tryToSample(): call returned a different distribution than this one!");
//            }
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
        if ( sampleChain == null ) {
            sampleChain = new SampleChain();
        }
        Sample s = sampleCall(sampleChain);
        return sampleChain;
    }

    protected boolean substitute( Object o, Set<Variable> randomVars ) {
        if ( o == null || randomVars == null ) return false;
        boolean didSub = false;
        if ( o instanceof HasParameters ) {
            HasParameters hasP =(HasParameters)o;
            for ( Variable rv : randomVars ) {
                if ( rv instanceof Parameter ) {
                    Parameter rp = (Parameter)rv;
                    Variable v = substitutions.get( rv );
                    if ( v instanceof Parameter ) {
                        Parameter p = (Parameter)v;
                        boolean subbed = hasP.substitute( rp, p, true, null );
                        if ( subbed ) didSub = true;
                    }
                }
            }
        }
        return didSub;
    }

    protected boolean updateArgumentForSampling( Object arg,
                                                 Set<Variable> varsToSample,
                                                 SampleChain sampleChain ) {
        if ( arg == null ) return false;
        Set<Variable> randomVars = DistributionHelper.getRandomVars(arg, true);
        boolean gotSome = Utils.intersect( randomVars, varsToSample );
        boolean didUpdate = false;
        if ( gotSome ) {
            sampleAndCreateSubstitutionVars( randomVars, sampleChain );
            didUpdate = substitute( arg, randomVars );
        }
        return didUpdate;
    }

    protected void sampleAndCreateSubstitutionVars( Set<Variable> randomVars, SampleChain sampleChain ) {
        if ( substitutions == null ) new LinkedHashMap<>();
        //else substitutions.clear();
        for (Variable v : randomVars ) {
            Variable sub = substitutions.get( v );
            if ( sub != null ) continue;
            Sample s = (Sample)sampleChain.samplesByVariable.get( v );
            if ( s == null ) {
                Distribution d = DistributionHelper.getDistribution( v );
                if ( d != null ) {
                    s = d.sample();
                }
                if ( s instanceof SimpleSample ) {
                    s = new SampleInContext( s.value(), s.weight(), d, v );
                }
                if ( s != null ) {
                    sampleChain.add(s);
                }
            }
            if ( s != null ) {
                ParameterListener pl =
                        v instanceof Parameter ? ( (Parameter)v ).getOwner() : null;
                Parameter p = new Parameter( (String)v.getName(),
                                             new SingleValueDomain( s.value() ),
                                             s.value(), pl );
                substitutions.put( p, v );
                substitutions.put( v, p );
            }
        }
    }

    protected Set<Variable> getRandomVariables() {
        return DistributionHelper.getRandomVars( call, true );
    }

    protected static Set<Variable> varsThatNeedToBeSampled( Object o ) {
        try {
            Distribution d = Expression.evaluate( o, Distribution.class,
                                                  true, false );
            if ( d instanceof FunctionOfDistributions ) {
                //varsNeedSampleArray[i] = ( (FunctionOfDistributions)d ).varsThatNeedToBeSampled();
                Set<Variable> needSample = ( (FunctionOfDistributions)d ).varsThatNeedToBeSampled((Set)null);
                return needSample;
            }
        } catch (Throwable t) {
        }
        return null;
    }

    protected Set<Variable> varsThatNeedToBeSampled( SampleChain sampleChain ) {
        Set<Variable> needSample = sampleChain.getVariables();
        //needSample = varsThatNeedToBeSampled( (needSample);
        //if ( Utils.isNullOrEmpty( needSample ) ) return needSample;
        int before = -1;
        int after = needSample == null ? 0 : needSample.size();
        while ( after > before && after > 0 && after < 1e9 ) {
            before = after;
            needSample = varsThatNeedToBeSampled( needSample );
            after = needSample.size();
        }
        return needSample;
    }

    protected Set<Variable> varsThatNeedToBeSampled( Set<Variable> varsNeedSample ) {
        // TODO -- REVIEW -- It seems like we should pass false in the call below,
        // TODO -- REVIEW -- but we probably want vars inside values of variables,
        // TODO -- REVIEW -- so we really only want to hold back on
        // TODO -- REVIEW -- DistributionFunctionCalls (or maybe Calls, in general).
        Set<Variable> varsInObj = DistributionHelper.getRandomVars( call.getObject(), true );
        Set<Variable> varsNeedSampleInObj = DistributionHelper.getRandomVars( call.getObject(), true );
        //Set<Variable> varsInArgs = null;
        //Set<Variable>[] varsArray = new Set[ call.getArguments().size() ];
        //Set<Variable>[] varsNeedSampleArray = new Set[call.getArguments().size()];

        int numBefore = varsNeedSample == null ? 0 : varsNeedSample.size();

        Set<Variable> allVars = new LinkedHashSet<>();
        //Set<Variable> varsNeedSample = null;
        if ( varsInObj != null ) {
            varsNeedSample = varsThatNeedToBeSampled(call.getObject());
            allVars.addAll( varsInObj );
        }

        for ( int i = 0; i < call.getArguments().size(); ++i ) {
            Object arg = call.getArgument( i );
            //varsArray[ i ] = DistributionHelper.getRandomVars( arg );
            Set<Variable> vars = DistributionHelper.getRandomVars( arg, true );
            if ( vars == null || vars.isEmpty() ) {
                continue;
            }
            Set<Variable> intersection = new LinkedHashSet<>( vars );
            Utils.intersect( intersection, allVars );
            varsNeedSample = Utils.addAll( varsNeedSample, intersection );
            Set<Variable> needSample = varsThatNeedToBeSampled( arg );
            varsNeedSample = Utils.addAll( varsNeedSample, needSample );
            allVars.addAll( vars );
        }

        // If any var needs resampling in the call, then any random var that
        // is an argument (or the object) also needs to be Sampled.
        Utils.intersect(allVars, varsNeedSample);
        if ( allVars != null && !allVars.isEmpty() ) {
            Variable v = DistributionHelper.getRandomVar( call.getObject() );
            if ( v != null ) {
                varsNeedSample.add( v );
            }
            for ( Object arg : call.getArguments() ) {
                v = DistributionHelper.getRandomVar( arg );
                if ( v != null ) {
                    varsNeedSample.add( v );
                }
            }
        }

        // Below is commented out because it is handled in a outside loop.
//        // If the number of vars needing sampling increased, we need to do it again.
//        int numAfter = varsNeedSample == null ? 0 : varsNeedSample.size();
//        if ( numAfter > numBefore ) {
//            varsNeedSample = varsThatNeedToBeSampled( varsNeedSample );
//        }


        if ( varsNeedSample == null ) return new LinkedHashSet<>();
        return varsNeedSample;
    }

/*
        Map<Variable, Integer> counts = new LinkedHashMap<>();
        if ( varsInObj != null ) {
            for ( Variable v : varsInObj ) {
                Integer c = counts.get( v );
                if ( c != null ) c += 1;
                else c = 1;
                counts.put( v, c );
            }
        }

            if (varsInObj != null) {
                intersection.addAll( varsInObj );
                Utils.intersect( intersection, varsArray[i] );
                varsNeedSample = Utils.addAll(varsNeedSample, intersection);
            }


            if ( varsArray[i] != null ) {
                for ( Variable v : varsArray[ i ] ) {
                    Integer c = counts.get( v );
                    if ( c != null ) c += 1;
                    else c = 1;
                    counts.put( v, c );
                }
            }

            try {
                Distribution d = Expression.evaluate( arg, Distribution.class,
                                                      true, false );
                if ( d instanceof FunctionOfDistributions ) {
                    //varsNeedSampleArray[i] = ( (FunctionOfDistributions)d ).varsThatNeedToBeSampled();
                    Set<Variable> needSample = ( (FunctionOfDistributions)d ).varsThatNeedToBeSampled();
                    Utils.addAll(varsNeedSample, needSample);
                }
            } catch (Throwable t) {
            }
        }

        // Put all the random variables for each argument into an array.
        // Also go ahead and get the variables that the arg says needs resampling.
        // Random vars that show up in more than one
        Map<count>
        for ( int i = 0; i < call.getArguments().size()-1; ++i ) {
            Set<Variable> intersection = new LinkedHashSet<>();
            if ( varsArray[i] == null || varsArray[i].isEmpty() ) {
                continue;
            }
            if (varsInObj != null) {
                intersection.addAll( varsInObj );
                Utils.intersect( intersection, varsArray[i] );
                varsNeedSample = Utils.addAll(varsNeedSample, intersection);
            }
            //Object arg1 = call.getArgument( i );
            for ( int j = i; j < call.getArguments().size(); ++j ) {
                //Object arg2 = call.getArgument( j );
                intersection = new LinkedHashSet<>();
                if (varsArray[j] != null) {
                    intersection.addAll( varsArray[i] );
                    Utils.intersect( intersection, varsArray[j] );
                    varsNeedSample = Utils.addAll(varsNeedSample, intersection);
                }
            }
        }
        if ( varsNeedSample == null ) return new LinkedHashSet<>();
        return varsNeedSample;

    }
*/

    /**
     * Return a sample of the given {@link Object}, {@code o}, and after adding the sample to the given
     * SampleChain.  If {@code o} is a distribution that is already in the {@link SampleChain}
     * (indicating that it has already been sampled), return the existing {@link Sample} instead
     * of generating a new one.
     *
     * @param o the {@link Object} to be sampled
     * @param sampleChain the chain to which the {@link Sample} is added
     * @return the Sample of o
     */
    protected static <T1,T2> Sample<T2> sample(Object o, SampleChain<T1> sampleChain) {
        if ( o == null || sampleChain == null ) {
            return null;
        }
        Variable v = DistributionHelper.getRandomVar( o );
        if ( v != null ) {
            // Check to see if we already have a sample.
            Sample<T2> chainSample = sampleChain.samplesByVariable.get( v );
            if ( chainSample != null ) {
                return chainSample;
            }
        }
        Distribution d = DistributionHelper.getDistribution( o );
        if ( d == null ) {
            if ( v != null ) {
                Debug.error( "No distribution in random variable: " + v );
            }
            return null;
        }
        Sample<T2> chainSample = sampleChain.samplesByDistribution.get(d);
        if ( chainSample != null ) {
            return chainSample;
        }

        Sample os = null;
        if ( d instanceof FunctionOfDistributions ) {
            os = ( (FunctionOfDistributions)d ).sample( sampleChain );
        } else {
            os = d == null ? null : d.sample();
        }
        SampleInContext sic = null;
        if ( v != null ) {
            if ( os instanceof SampleInContext ) {
                sic = (SampleInContext)os;
                if ( sic.getOwner() != v ) {
                    sic = new SampleInContext( sic.value, sic.weight, d, v);
                }
            }
        }
        if ( sic == null && os != null ) {
            sic = new SampleInContext( os.value(), os.weight(), d, v == null ? o : v );
        }
        if ( sic != null ) {
            sampleChain.add( sic );
            return sic;
        }
        if ( os != null ){
            sampleChain.add( os );
            return os;
        }
        return null;
    }

    public double weight( SampleChain sampleChain ) {
        double w = sampleChain.weight();
        return w;
    }

    /**
     *
     * @param sampleChain the SampleChain
     *
     * @return a possibly new sampleChain updated with the new sample(s) or some
     * other Sample if this is the first.
     */
    protected Sample<T> sampleCall(SampleChain sampleChain) {

        // Suppose this function is o.f(a, g(a, b)) with random variables, a, b, and o.
        // Also, suppose the return value is c, another random variable.
        // How do we sample?
        // Get a sample from g(a, b) :
        //    ((a=2, wa=0.2, da, va),
        //     (b=3, wb = 0.4, dv, vb),
        //     (g_rv=6, wg = wa * wb = 0.08, null, g_call))
        // Get sample from o.f(a, g(a, b):
        //    ((a=2, wa=0.2, da, a),
        //     (b=3, wb = 0.4, dv, b),
        //     (g_rv=6, wg = wa * wb = 0.08, null, g_call),
        //     (o=O(1,1), wo=0.5, do, o),
        //     (f_rv=N(0,1), wf = wa * wb * wo = 0.04, null, f_call ))
        // We only sample f()'s return value if the function call to f() is an
        // argument of some other function call that needs a real number argument
        // as opposed to a distribution.

        boolean origStale = call.isStale();
        Object origReturnValue = call.returnValue;
        long origLastUpdated = call.lastUpdated;

        try {

            // Sample the object and arguments.
            Sample s = sample( call.getObject(), sampleChain );
            Object eObj = null;
            if ( s != null ) {
                eObj = s.value();
            } else {
                try {
                    eObj = call.evaluateObject( true );
                } catch ( Throwable e ) {
                }
            }
            Object[] evalArgs = new Object[ call.getArguments().size() ];
            Class[] params = call.getParameterTypes();
            int i = 0;
            for ( Object arg : call.getArguments() ) {
                s = sample( arg, sampleChain );
                if ( s != null ) {
                    evalArgs[ i ] = s.value();
                } else {
                    try {
                        Class p = params[ i < params.length ? i :
                                          params.length - 1 ];
                        evalArgs[ i ] = call.evaluateArg( arg, p, true );
                    } catch ( Throwable e ) {
                    }
                }
                ++i;
            }

            // Invoke the call with the sampled arguments.
            Object v = null;
            try {
                call.setStale( true );
                v = call.invoke( eObj, evalArgs );
                // Set the call stale to avoid reusing the cached result since these
                // are not its normal arguments
                call.setStale( true );
            } catch ( Throwable t ) {
                t.printStackTrace();
            }
            if ( call.didEvaluationSucceed() ) {
                //v = call.returnValue;
                double w = sampleChain.weight();
                Distribution d = this;
                Object owner = this;
                SampleInContext sic = new SampleInContext( v, w, d, owner );
                sampleChain.add( sic );
                return sic;
            }
            return null;
        } finally {
            // return call to original state
            call.setStale( origStale );
            call.returnValue = origReturnValue;
            call.lastUpdated = origLastUpdated;
        }
    }

    /**
     * Sampling is done globally.  All HasParameters must implement a sample()
     * function.  A single sample chain will capture all variable samples which
     * comprise the global sample.  Sampling needs to be part of the solving
     * process (or vice-versa) since new random variables may be created during
     * solving.
     *
     * For now, sample all random variables -- we'll incorporate smarts later
     * for avoiding sampling within aggregate distributions once we figure out
     * how to determine when we can do it efficiently.  The functions that do
     * this, varsThatNeedToBeSampled(), seem expensive--we would want to save
     * the results instead of recomputing for each sample, but they would need
     * to be updated for newly created objects.
     * <ol>
     * <li>
     *     sample all random variables in object/arguments
     *     (shallow, not ones in nested functions).
     * </li><li>
     *     substitute random variables with sampled values in object/arguments.
     * </li><li>
     *     if object/arguments are DistributionFunctionCalls, call sample() to
     *     get evaluated object/arguments; otherwise evaluate as usual.
     * </li><li>
     *     sample all random variables in evaluated object/arguments (shallow).
     * </li><li>
     *     substitute random variables with sampled values in object/arguments
     * </li><li>
     *     re-evaluate function
     * </li><li>
     *     put result in SampleChain
     * </li><li>
     *
     * </li>
     * </ol>
     * <p>
     * Check the evaluatedArguments of the call.  If they are distributions, sample
     * them reusing any already sampled variables in the SampleChain.  Temporarily
     * replace the arguments with the corresponding samples and re-evaluate the call.
     * </p><p>
     * If the
     * arguments do not share any of the same random variables with the SampleChain,
     * then the arguments may be sampled directly; else, the random variables in the
     * arguments and their expressions must be replaced with their sampled values.
     * </p><p>
     * After sampling the arguments, check them again.  An argument that was sampled
     * directly may need to be resampled for a random variable that was sampled
     * afterwards.
     * </p><p>
     * Alternatively, check and see what variables show up multiple times and mark
     * those to be sampled.
     * </p>
     *
     * @param sampleChain the SampleChain
     *
     * @return a possibly new sampleChain updated with the new sample(s) or some
     * other Sample if this is the first.
     */
    protected Sample<T> sampleCallBig(SampleChain sampleChain) {

        // Suppose this function is o.f(a, g(a, b)) with random variables, a, b, and o.
        // Also, suppose the return value is c, another random variable.
        // How do we sample?
        // Get a sample from g(a, b) :
        //    ((a=2, wa=0.2, da, va),
        //     (b=3, wb = 0.4, dv, vb),
        //     (g_rv=6, wg = wa * wb = 0.08, null, g_call))
        // Get sample from o.f(a, g(a, b):
        //    ((a=2, wa=0.2, da, a),
        //     (b=3, wb = 0.4, dv, b),
        //     (g_rv=6, wg = wa * wb = 0.08, null, g_call),
        //     (o=O(1,1), wo=0.5, do, o),
        //     (f_rv=N(0,1), wf = wa * wb * wo = 0.04, null, f_call ))
        // We only sample f()'s return value if the function call to f() is an
        // argument of some other function call that needs a real number argument
        // as opposed to a distribution.

        // sample all random variables in object/arguments
        // (shallow, not ones in nested functions).

        Set<Variable> rVars = DistributionHelper.getRandomVars( call.getObject(), true ); //getRandomVariables();
        rVars = Utils.addAll( rVars, DistributionHelper.getRandomVars( call.getArguments(), true ) );
        sampleAndCreateSubstitutionVars( rVars, sampleChain );

        // substitute random variables with sampled values in object/arguments.

        substitute( this, sampleChain.getVariables() ); // TODO -- FIXME????!!!!!!

        // if object/arguments are DistributionFunctionCalls, call sample() to
        // get evaluated object/arguments; otherwise evaluate as usual.

        for ( Object arg : call.getArguments() ) {
            Distribution<?> d = DistributionHelper.getDistribution( arg );
            if ( d instanceof FunctionOfDistributions ) {
                if ( d != this ) {
                    Debug.error( true, false,
                                 "FunctionOfDistributions.tryToSample(): call returned a different distribution than this one!" );
                }
                Sample s = ( (FunctionOfDistributions<?>)d ).sample( sampleChain );
                if ( s instanceof SampleChain ) {
                    sampleChain = (SampleChain)s;
                } else {
                    sampleChain.add( s );
                }
            }
        }

        // sample all random variables in evaluated object/arguments (shallow).
        //Set<Variable> Vars
        try {
            call.evaluate( true );
        } catch ( IllegalAccessException e ) {
            e.printStackTrace();
        } catch ( InvocationTargetException e ) {
            e.printStackTrace();
        } catch ( InstantiationException e ) {
            e.printStackTrace();
        }
        rVars = DistributionHelper.getRandomVars( call.getEvaluatedObject(), true ); //getRandomVariables();
        rVars = Utils.addAll( rVars, DistributionHelper.getRandomVars( call.getEvaluatedArguments(), true ) );
        sampleAndCreateSubstitutionVars( rVars, sampleChain );

        // substitute random variables with sampled values in object/arguments

        substitute( this, sampleChain.getVariables() ); // TODO -- FIXME????!!!!!!

        // re-evaluate function

        T result = null;
        try {
            result = (T)call.invoke( call.getEvaluatedObject(), call.getEvaluatedArguments() );
        } catch ( InstantiationException e ) {
            e.printStackTrace();
        } catch ( IllegalAccessException e ) {
            e.printStackTrace();
        } catch ( InvocationTargetException e ) {
            e.printStackTrace();
        }

        // put result in SampleChain
        Distribution d = this.distribution;//getDistribution();

        SampleInContext sic =
                new SampleInContext( result, d.pdf( result ), d, null );
        sampleChain.add(sic);


        ///////////////////////////


        // Walk through the arguments and find which variables need to be sampled.
        // Those variables are the ones that are already in the sample chain or
        // show up in more than one argument.
        //Set<Variable> varsToResample = sampleChain.getVariables();
/*
        Set<Variable> varsInArgs = null;
        if ( call.getObject() != null ) {
            varsInArgs = DistributionHelper.getRandomVars( call.getObject() );
        }
        for ( int i = 0; i < call.getArguments().size(); ++i ) {
            Object arg = call.getArgument( i );
            Set<Variable> rVars = DistributionHelper.getRandomVars( arg );
            if ( rVars != null ) {
                if ( varsInArgs == null ) {
                    varsInArgs = rVars;
                } else {
                    for ( Variable rv : rVars ) {
                        if ( varsInArgs.contains( rv ) ) {

                        }
                    }
                }
            }
        }
*/

        Set<Variable> varsNeedSample = varsThatNeedToBeSampled(sampleChain);


        boolean someArgUpdated = false;
        boolean someEvalArgUpdated = false;

        boolean objUpdated = updateArgumentForSampling( call.getObject(),
                                                        varsNeedSample,
                                                        sampleChain );

        for ( int i = 0; i < call.getArguments().size(); ++i ) {
            Object arg = call.getArgument( i );
            boolean updated = updateArgumentForSampling( arg,
                                                         varsNeedSample,
                                                         sampleChain );
            if ( updated ) someArgUpdated = true;
        }
        if ( someArgUpdated ) {
            call.setEvaluatedArguments( null );
            call.setStale( true );
            try {
                call.setEvaluatedArguments( call.evaluateArgs( true ) );
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        Object[] evalArgs = call.getEvaluatedArguments();
        if (evalArgs != null ){
            for ( Object evalArg : evalArgs ) {
                boolean updated = updateArgumentForSampling( evalArg,
                                                             sampleChain.getVariables(),
                                                             sampleChain );
                if ( updated ) someEvalArgUpdated = true;
            }
        }
        if ( someEvalArgUpdated ) {
            call.setStale( true );
        }
        if (someEvalArgUpdated) {
            Object evaluatedObj = null;
//            if ( objUpdated ) {
                try {
                    evaluatedObj = call.evaluateObject( true );
                } catch ( Throwable e ) {
                    e.printStackTrace();
                }
//            }
            // ???  evaluatedArgs =

            // we can call invoke directly since we have the args ready
            // ??  call.invoke( evaluatedobj, ev )
        } else {

        }
//
//        for ( int i = 0; i < call.getArguments().size(); ++i ) {
//            Object arg = call.getArgument( i );
//            Object evaluatedArg = evalArgs != null && evalArgs.length > i ? evalArgs[ i ] : null;
//            if (succ) {
//                succ = updateArgumentForSampling( evaluatedArg );
//            }
//            Set<Variable> rVars = DistributionHelper.getRandomVars( arg );
//
//        }

        // Save away the current evaluated arguments that will be replaced.
        // Make sure to set stale.

        // Go ahead and sample the variables that haven't been sampled and replace
        // the variables with values in all of the arguments.

        // Walk through the arguments and check to see if they have random
        // variables that must be replaced.  If they do not, just sample the
        // argument directly.  If the argument is one of the random variables,
        // replace it with its sampled value.  If a FunctionOfDistributions,
        // call recursively with the updated sampleChain.
        //Object[]
                evalArgs = call.getEvaluatedArguments();
        for ( int i = 0; i < call.getArguments().size(); ++i ) {
            Object arg = call.getArgument( i );
            Object evaluatedArg = evalArgs != null && evalArgs.length > i ? evalArgs[ i ] : null;
            if ( evaluatedArg != null ) {
                Set<Variable> varsToResample = null;  // ????  This was supposed to be defined elsewhere
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

    public Call getCall() {
        return call;
    }

    public Distribution getApproximation() {
        return approximation;
    }

    public Expression<Double> getError() {
        return error;
    }

    public SampleDistribution<T> getSamples() {
        return samples;
    }

    public Double getCombinedValue() {
        return combinedValue;
    }



    @Override public Class<T> getType() {
        return type;
    }

    @Override public String toString() {
        String o = "";
        if ( getOwner() instanceof HasName ) {
            o = "" + ((HasName)getOwner()).getName();
        }
        return o + ":FunctionOfDistributions(" + call +
               (bias == null ? "" : ", " + bias) + ")";
    }

    /**
     * @param deep whether the check is made recursively if the object is composed of
     *             other Groundable objects.
     * @param seen a set of Groundable objects that have already been checked to
     *             avoid resursing deeper into an infinite loop.
     * @return whether this object is assigned a specific value and, if deep,
     * whether all contained objects are also grounded.
     */
    @Override public boolean isGrounded( boolean deep, Set<Groundable> seen ) {
        if ( call == null )
            return false;
        if ( !call.isGrounded( deep, seen ) )
            return false;
        if ( bias == null )
            return true;
        if ( bias instanceof Groundable ) {
            return ( (Groundable)bias ).isGrounded( deep, seen );
        }
        return true;
    }

    /**
     * @param deep
     * @param seen
     * @return whether the object and, if deep, its contained Grounadable objects
     * were successfully grounded. A subsequent call to isGrounded()
     * should return the same value.
     */
    @Override public boolean ground( boolean deep, Set<Groundable> seen ) {
        boolean didSomething = call != null && call.ground( deep, seen );
        if ( bias instanceof Groundable ) {
            boolean ds = ( (Groundable)bias ).ground( deep, seen );
            didSomething = didSomething || ds;
        }
        return didSomething;
    }

    /**
     * Propagate this parameter's change to other objects. This may involve
     * updating dependencies, re-elaboration of events, and maybe constraint
     * satisfaction.
     *
     * @param parameter the parameter whose value has changed
     * @param seen
     */
    @Override public void handleValueChangeEvent( Parameter<?> parameter,
                                                  Set<HasParameters> seen ) {
        if ( call != null ) {
            call.handleValueChangeEvent( parameter, seen );
        }
        // TODO -- bias
    }

    /**
     * Propagate this change to the parameter's domain to other objects. This may
     * involve updating domains of related parameters or their values.
     *
     * @param parameter the parameter whose domain has changed
     * @param seen
     */
    @Override public void handleDomainChangeEvent( Parameter<?> parameter,
                                                   Set<HasParameters> seen ) {
        if ( call != null ) {
            call.handleDomainChangeEvent( parameter, seen );
        }
        // TODO -- bias
    }

    /**
     * Set to stale anything that references the parameter whose value just
     * changed.
     * <p>
     * Lazy updating is added so that maps keyed with parameters (like
     * TimeVaryingMap) have a chance to pull out entries before they are
     * corrupted.
     *
     * @param changedParameter the parameter whose value is about to change
     * @param seen
     */
    @Override public void setStaleAnyReferencesTo(
            Parameter<?> changedParameter, Set<HasParameters> seen ) {
        if ( call != null ) {
            call.setStaleAnyReferencesTo( changedParameter, seen );
        }
        // TODO -- bias
    }

    /**
     * Remove any references to the parameter.
     *
     * @param parameter the parameter that is being detached
     */
    @Override public void detach( Parameter<?> parameter ) {
        if ( call != null ) {
            call.detach( parameter );
        }
        // TODO -- bias
    }

    /**
     * Update this parameter's value or domain so that it does not depend on stale
     * information.
     *
     * @param parameter the parameter to refresh
     * @param seen
     * @return whether or not the parameter was refreshed successfully
     */
    @Override public boolean refresh( Parameter<?> parameter,
                                      Set<ParameterListener> seen ) {
        return call != null && call.refresh( parameter, seen );
        // TODO -- bias
    }

    /**
     * Pick a new value for the {@link Variable}, possibly to help resolve constraints.
     *
     * @param variable the variable for which to pick a parameter
     * @return whether a new value was picked
     */
    @Override public <T> boolean pickParameterValue( Variable<T> variable ) {
        return call != null && call.pickParameterValue( variable );
        // TODO -- bias
    }

    /**
     * Get the name of this object.
     *
     * @return a name
     */
    @Override public String getName() {
        return call == null ? "" : call.getName();
    }

    /**
     * Adjust the value assigned to a variable to make sure it is in in the
     * domain.
     *
     * @param v    the variable whose value is to be assigned
     * @param o    the object to translate
     * @param type the variable's type
     * @return the new value to use instead of the object passed in
     */
    @Override public <T> T translate( Variable<T> v, Object o, Class<?> type ) {
        return call == null ? null : call.translate( v, o, type );
        // TODO -- bias
    }

    /**
     * Find the variables on which the input variable depends.
     *
     * @param variable the dependent variable
     * @return a list of variables
     */
    @Override public List<Variable<?>> getVariablesOnWhichDepends( Variable<?> variable ) {
        return call == null ? null : call.getVariablesOnWhichDepends(variable);
        // TODO -- bias
    }

    @Override public Set<Parameter<?>> getParameters( boolean deep,
                                                      Set<HasParameters> seen ) {
        return call == null ? null : call.getParameters(deep, seen);
        // TODO -- bias
    }

    @Override public Set<Parameter<?>> getFreeParameters( boolean deep,
                                                          Set<HasParameters> seen ) {
        return call == null ? null : call.getFreeParameters(deep, seen);
        // TODO -- bias
    }

    @Override
    public void setFreeParameters( Set<Parameter<?>> freeParams, boolean deep,
                                   Set<HasParameters> seen ) {
        if ( call != null ) {
            call.setFreeParameters( freeParams, deep, seen );
        }
        // TODO -- bias
    }

    @Override public boolean isFreeParameter( Parameter<?> p, boolean deep,
                                              Set<HasParameters> seen ) {
        return call != null && call.isFreeParameter( p, deep, seen );
        // TODO -- bias
    }

    @Override public boolean hasParameter( Parameter<?> parameter, boolean deep,
                                           Set<HasParameters> seen ) {
        return call != null && call.hasParameter( parameter, deep, seen );
        // TODO -- bias
    }

    @Override public Parameter<?> getParameter( String name ) {
        return call == null ? null : call.getParameter(name);
        // TODO -- bias
    }

    @Override
    public boolean substitute( Parameter<?> p1, Parameter<?> p2, boolean deep,
                               Set<HasParameters> seen ) {
        return call != null && call.substitute( p1, p2, deep, seen );
        // TODO -- bias
    }

    @Override
    public boolean substitute( Parameter<?> p1, Object exp, boolean deep,
                               Set<HasParameters> seen ) {
        return call != null && call.substitute( p1, exp, deep, seen );
        // TODO -- bias
    }

    @Override public void deconstruct() {
        if ( call != null ) {
            call.deconstruct();
        }
        // TODO -- bias
    }

    @Override public void addReference() {
        if ( call != null ) {
            call.addReference();
        }
        // TODO -- bias
    }

    @Override public void subtractReference() {
        if ( call != null ) {
            call.subtractReference();
        }
        // TODO -- bias
    }

    @Override public boolean isStale() {
        return call != null && call.isStale();
        // TODO -- bias?
    }

    @Override public void setStale( boolean staleness ) {
        if ( call != null ) {
            call.setStale( staleness );
        }
        // TODO -- bias?
    }

    @Override public void setStale( boolean staleness, boolean deep,
                                    Set<LazyUpdate> seen ) {
        if ( call != null ) {
            call.setStale( staleness, deep, seen );
        }
        // TODO -- bias?
    }

    /**
     * Return the possible values that the object may have.
     *
     * @param propagate
     * @param seen
     * @return
     */
    @Override public Domain<?> getDomain( boolean propagate,
                                          Set<HasDomain> seen ) {
        return call == null ? null : call.getDomain( propagate, seen );
    }

    /**
     * Make the the domain of this object the largest subset of its current domain
     * that is contained by the input domain, even if that is an empty domain. For
     * example, if this is a function f(x,y) = x+y and the sum is restricted to
     * [0,3], then the domains of x and y should be restricted such that their sum
     * is within [0,3]. In this case, we are technically restricting the range of
     * f even though we say "domain." The domain of a variable is not the same as
     * the domain of a function. Think of the function as a variable that has a
     * domain, so the range of the function is the domain of the variable
     * representing it.
     *
     * @param domain
     * @param propagate
     * @param seen
     * @return the resulting domain and whether any restriction was made to any
     * variable or expression as a result of this call.
     */
    @Override public <T> Pair<Domain<T>, Boolean> restrictDomain(
            Domain<T> domain, boolean propagate, Set<HasDomain> seen ) {
        return call == null ? null : call.restrictDomain( domain, propagate, seen );
    }

    @Override public long update() {
        return lastUpdated = LamportClock.tick();
    }
    @Override public long getLastUpdated() {
        return lastUpdated;
    }
    @Override public long getLastUpdated(Set<UsesClock> seen) {
        return lastUpdated;
    }

    /**
     * Write object recursively based on passed options.
     *
     * @param withHash whether to include "@" + hasCode() in the returned String.
     * @param deep     whether to include member/child detail and call their
     *                 MoreToString.toString() (typically) with the same options.
     * @param seen     whether the object has already been written with deep=true, in
     *                 which case it will set deep=false to end the recursion.
     * @return the string representation of the object.
     */
    @Override public String toString( boolean withHash, boolean deep,
                                      Set<Object> seen ) {
        if ( call == null ) {
            return this.getClass().getSimpleName() + (withHash ? "@" + getId() : "");
        }
        return call.toString( withHash, deep, seen );
        // TODO -- bias
    }

    /**
     * Write object recursively based on passed options.
     *
     * @param withHash     whether to include "@" + hasCode() in the returned String.
     * @param deep         whether to include member/child detail and call their
     *                     MoreToString.toString() (typically) with the same options.
     * @param seen         whether the object has already been written with deep=true, in
     *                     which case it will set deep=false to end the recursion.
     * @param otherOptions other class or context-specific options with names and values.
     * @return the string representation of the object.
     */
    @Override public String toString( boolean withHash, boolean deep,
                                      Set<Object> seen,
                                      Map<String, Object> otherOptions ) {
        if ( call == null ) {
            return this.getClass().getSimpleName() + (withHash ? "@" + getId() : "");
        }
        return call.toString( withHash, deep, seen, otherOptions );
        // TODO -- bias
    }

    /**
     * @return a single name or value
     */
    @Override public String toShortString() {
        return call == null ? this.getClass().getSimpleName() :
               call.toShortString();
        // TODO -- bias
    }

}
