package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.mbee.util.Debug;

import java.util.*;

/**
 * This is a composite sample made up of single samples of variables/distributions.
 * @param <T> The type of the last sample taken, which may be for the highest-level variable.
 */
public class SampleChain<T> implements Sample<T> {
//    Distribution<T> distributionSource = null;
//    Call callSource = null;
    Sample<T> lastSample = null;
    //List<SampleChain<?>> nextSamples = null;

    List<Sample> samples = new ArrayList<>();
    Map<Variable, Sample> samplesByVariable = new LinkedHashMap<Variable, Sample>();
    // We assume that the Java Object hash code differentiates distributions.
    Map<Distribution, Sample> samplesByDistribution = new LinkedHashMap<>();

    public SampleChain() {
    }

    public SampleChain( Sample<T> sample ) {
        //super( lastSample.value(), lastSample.weight() );
        //this.lastSample = lastSample;
        add(sample);
    }

//    public SampleChain( Distribution<T> distributionSource, Call callSource,
//                        Sample<T> lastSample ) {
//        this( lastSample );
//        this.distributionSource = distributionSource;
//        this.callSource = callSource;
//    }

    public boolean add( Sample s ) {
        if ( s instanceof SampleInContext ) {
            SampleInContext sic = (SampleInContext)s;
            if ( sic.getOwner() != null && sic.getOwner() instanceof Variable &&
                 samplesByVariable.containsKey( sic.getOwner() ) ) {
                // The variable has already been sampled!
                Debug.error( true, true, "Already sampled variable: " + sic.getOwner() );
                return false;
            }
            if ( sic.getDistribution() != null &&
                 samplesByDistribution.containsKey( sic.getDistribution() ) ) {
                // The distribution has already been sampled!
                Debug.error( true, true, "Already sampled distribution: " + sic.getDistribution() );
                return false;
            }
            samplesByDistribution.put( sic.getDistribution(), sic );
            if ( sic.getOwner() instanceof Variable ) {
                samplesByVariable.put( (Variable)sic.getOwner(), sic );
            }
        }
        lastSample = s;
        samples.add( s );

        return true;
    }

    @Override public T value() {
        return lastSample.value();
    }

    @Override
    public double weight() {
        double w = 1.0;
        for ( Sample s : samples ) {
            w *= s.weight();
        }
        //return weight * (lastSample != null ? lastSample.weight() : 1.0);
        return w;
    }

    public Set<Variable> getVariables() {
        if ( samplesByVariable == null ) return null;
        return samplesByVariable.keySet();
    }
}
