package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.MoreToString;

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
            Distribution d = sic.getDistribution();
            Object owner = sic.getOwner();
            if ( owner == null ||
                 (!( owner instanceof Variable ) && d != null &&
                  d.getOwner() instanceof Variable) ) {
                owner = d.getOwner();
            }
            if ( owner != null && owner instanceof Variable &&
                 samplesByVariable.containsKey( owner ) ) {
                // The variable has already been sampled!
                // Debug.error( true, false, "Already sampled variable: " + owner );
                return false;
            }
            if ( d != null && samplesByDistribution.containsKey( d ) ) {
                // The distribution has already been sampled!
                //Debug.error( true, false, "Already sampled distribution: " + d );
                return false;
            }
            samplesByDistribution.put( d, sic );
            if ( owner instanceof Variable ) {
                samplesByVariable.put( (Variable)owner, sic );
            }
        }
        lastSample = s;
        samples.add( s );

        return true;
    }

    @Override public T value() {
        if ( lastSample == null ) return null;
        return lastSample.value();
    }

    @Override
    public double weight() {
        double w = 1.0;
        if ( samplesByVariable != null ) {
            for ( Sample s : samplesByVariable.values() ) {
                if ( s == null ) continue;
                if ( s instanceof SampleInContext ) {
                    SampleInContext sic = (SampleInContext)s;
                    if ( sic.getDistribution() instanceof FunctionOfDistributions ) {
                        continue;
                    }
                }
                w *= s.weight();
            }
        }
        return w;
    }

    public Set<Variable> getVariables() {
        Debug.error(true, false,"Warning!  This should probably ignore FunctionOfDistributions!");
        if ( samplesByVariable == null ) return null;
        return samplesByVariable.keySet();
    }

    @Override public String toString() {
        return toStringSalient();
    }

    public String toStringSalient() {
        StringBuilder sb = new StringBuilder();
        sb.append( "SampleChain" );
        ArrayList<String> samples = new ArrayList<>();
        if ( samplesByVariable != null ) {
            for ( Sample s : samplesByVariable.values() ) {
                if ( s == null ) continue;
                if ( s instanceof SampleInContext ) {
                    SampleInContext sic = (SampleInContext)s;
                    if ( sic.getDistribution() instanceof FunctionOfDistributions ) {
                        continue;
                    }
                    samples.add( "(" + sic.getOwner() + ", " + sic.value() + ", " + sic.weight() + ")" );
                } else {
                    samples.add( "(" + s.value() + ", " + s.weight() + ")" );
                }
            }
        }
        samples.add( "value=" + value() + ", weight=" + weight() );
        sb.append( MoreToString.Helper.toString( samples ) );
        return sb.toString();
    }
}
