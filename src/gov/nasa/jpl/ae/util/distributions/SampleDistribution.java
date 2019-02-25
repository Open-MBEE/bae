package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class SampleDistribution<T> extends AbstractDistribution<T> {

    public boolean recordCombinedValues = false;
    public boolean recordingSamples = false;
    public ArrayList<Pair<Double, Sample>> combinedValues = new ArrayList<>();

    static class SampleSet extends ArrayList<Sample> {
        Double totalWeight = 0.0;
        Double combinedValue = null;

        @Override public boolean add( Sample sample ) {
            combinedValue = combineSample(combinedValue, totalWeight, sample);
            totalWeight += sample.weight();
            return super.add( sample );
        }
    }

    static class SampleMap<V> extends TreeMap< V, SampleSet > {
        Double totalWeight = 0.0;
        Double combinedValue = null;
        public boolean recordingSamples = false;
        Sample lastSample = null;

        SampleMap() {
            super(CompareUtils.GenericComparator.instance());
        }

        public boolean add(V t, Sample<V> s) {
            if ( s == null ) return false;
            Double combinedVal = combineSample(combinedValue, totalWeight, s);
            if ( combinedVal != null ) {
                combinedValue = combinedVal;
                totalWeight += s.weight();
            }
            lastSample = s;
            if ( recordingSamples ) {
                SampleSet ss = get( t );
                if ( ss == null ) ss = new SampleSet();
                boolean r = ss.add( s );
                put( t, ss );
                return r;
            }
            return true;
        }
        double weight(V v) {
            SampleSet ss = get(v);
            if ( ss == null ) return 0.0;
            return ss.totalWeight;
        }

        public long count() {
            long total = 0;
            for ( SampleSet ss : values() ) {
                total += ss.size();
            }
            return total;
        }
    }

    public static Double combineSample(Double combinedValue,
                                       double totalWeight, Sample sample) {
        double v;

        if ( sample.value() instanceof Number ) {
            v = ((Number)sample.value()).doubleValue();
        } else if ( sample.value() instanceof Boolean ) {
            v = ((Boolean)sample.value()) ? 1.0 : 0.0;
        } else {
            return null;
        }
        if ( combinedValue == null ) {
            combinedValue = v;
        } else {
            combinedValue =
                    ( combinedValue * totalWeight + v * sample.weight() ) /
                    ( totalWeight + sample.weight() );
        }
        return combinedValue;
    }

    /**
     * The set of samples that approximate the distribution.
     */
    protected SampleMap< T > samples = null;

    /**
     * An upperbound on the error of the distribution of samples
     */
    Double error = null;

    protected Class<T> type = null;

    public boolean add( Sample<T> s ) {
        if ( samples == null ) {
            samples = new SampleMap<>();
            samples.recordingSamples = recordingSamples;
        }
        boolean result = samples.add( s.value(), s );
        if ( recordCombinedValues ) {
            combinedValues.add( new Pair( samples.combinedValue, s ) );
        }
        return result;
    }

    public boolean isEmpty() {
        return samples == null || samples.equals( null );
    }

    public long size() {
        if (isEmpty() ) return 0;
        return samples.count();
    }

    @Override public double probability( T t ) {
        if ( samples == null ) return 0.0;
        if ( samples.totalWeight > 0.0 )
            return samples.weight( t ) / samples.totalWeight;
        return 0.0;
        // TODO -- Do we want to fit an approximate distribution to the samples?
    }

    @Override public double pdf( T t ) {
        return probability( t );
        // TODO -- Is this right?
    }

    @Override public Number mean() {
        if ( samples == null ) {
            return null;
        }
        return samples.combinedValue;
    }

    @Override public Double variance() {
        double totalVar = 0.0;
        double totalWeight = 0.0;
        for ( Map.Entry<T, SampleSet> e : samples.entrySet() ) {
            T valObj = e.getKey();
            SampleSet sampleSet = e.getValue();
            if ( valObj == null || sampleSet == null ) continue;
            double diff = 0.0;
            if ( valObj instanceof Number ) {
                diff = ((Number)valObj).doubleValue() - samples.combinedValue;
            } else if ( valObj instanceof Boolean ) {
                diff = (((Boolean)valObj) ? 1.0 : 0.0) - samples.combinedValue;
            } else {
                // TODO -- WARNING?!?!
                continue;
            }
            totalVar += diff * diff * sampleSet.totalWeight;
            totalWeight += sampleSet.totalWeight;
        }
        if ( totalWeight == 0.0 ) {
            // TODO -- ERROR?!?!
            return null;
        }
        double variance = totalVar / totalWeight;
        return variance;
    }

    protected Double supportBound(Sample v, boolean lower) {
        if ( v instanceof SampleChain ) {
            Sample last = ( (SampleChain)v ).lastSample;
            if ( last != v ) {
                return supportBound( last, lower );
            }
        }
        if ( v instanceof SampleInContext ) {
            SampleInContext sic = (SampleInContext)v;
            if ( sic.getDistribution() != null ) {
                if ( lower ) {
                    return sic.getDistribution().supportLowerBound();
                } else {
                    return sic.getDistribution().supportUpperBound();
                }
            }
        }
        return null;
    }

    public Double supportBound(boolean lower) {
        T t = null;
        if ( samples != null ) {
            for ( Map.Entry<T, SampleSet> e : samples.entrySet() ) {
                SampleSet s = e.getValue();
                if ( !Utils.isNullOrEmpty( s ) ) {
                    for ( Sample v : s ) {
                        Double b = supportBound( v, lower );
                        if ( b != null ) {
                            return b;
                        }
                    }
                }
                if ( t == null ) {
                    t = e.getKey();
                } else {
                    int c = CompareUtils.compare( t, e.getKey() );
                    if ( (lower && c > 0 ) || (!lower && c < 0 ) ) {
                        t = e.getKey();
                    }
                }
            }
        }
        Pair<Boolean, Double> p = ClassUtils.coerce( t, Double.class, false );
        if ( p != null && p.first != null && p.first ) {
            return p.second;
        } else {
            System.err.println( "WARNING!  Wasted time unsuccessfully trying to get bound from SampleDistribution! " + this );
        }
        return null;
    }

    @Override public Double supportLowerBound() {
        return supportBound( true );
    }
    @Override public Double supportUpperBound() {
        return supportBound( false );
    }

    @Override public Double biasSupportFactor() {
        if ( bias != null ) {
            return super.biasSupportFactor();
        }
        // Find a SampleChain and get its bias factor
        SampleChain sc = null;
        if ( samples != null && samples.lastSample instanceof SampleChain ) {
            sc = (SampleChain)samples.lastSample;
            Double f = sc.biasFactor();
            if ( f != null ) {
                return f;
            }
        }
        if ( samples != null && samples.values() != null ) {
            for ( SampleSet set : samples.values() ) {
                for ( Sample s : set ) {
                    if ( s instanceof SampleChain ) {
                        Double f = ( (SampleChain)s ).biasFactor();
                        if ( f != null ) {
                            return f;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Sample from the samples
     * @return a sample from the samples
     */
    @Override public Sample<T> sample() {
        // TODO -- Do we want to fit an approximate distribution to the samples?
        if ( samples == null || samples.totalWeight == 0.0 ) return null;
        double r = Random.global.nextDouble();
        double accumulatedWeight = 0.0;
        double w = samples.totalWeight;
        for ( Map.Entry< T, SampleSet > ssE : samples.entrySet() ) {
            accumulatedWeight += ssE.getValue().totalWeight;
            if ( accumulatedWeight / w >= r ) {
                return new SimpleSample<>( ssE.getKey(), ssE.getValue().totalWeight );
            }
        }
        return null;
    }

    @Override public double cumulativeProbability( T t ) {
        // TODO !!
        if ( samples == null || samples.totalWeight == 0.0 ) return 0.0;
        double accumulatedWeight = 0.0;
        for ( Map.Entry< T, SampleSet > ssE : samples.entrySet() ) {
            if ( compare( t, ssE.getKey() ) < 0 ) break;
            accumulatedWeight += ssE.getValue().totalWeight;
        }
        return accumulatedWeight / samples.totalWeight;
    }

    protected int compare( T t1, T t2 ) {
        if ( samples != null )
            return samples.comparator().compare( t1, t2 );
        return CompareUtils.compare( t1, t2 );
    }

    @Override public Class<T> getType() {
        return type;
    }

    /**
     * Set the value of the object that is wrapped by this object.
     *
     * @param value the new value to be wrapped
     */
    @Override public void setValue( T value ) {
        this.samples.clear();
        this.combinedValues.clear();
        this.samples.add( value, new SimpleSample<T>( value, 1.0 ) );
    }

    public void toFile( String fileName ) {
        StringBuilder sb =new StringBuilder();
        if ( recordCombinedValues ) {
            for ( Pair<Double, Sample> p : combinedValues ) {
                sb.append( "" + p.first + "\n" );
            }
            FileUtils.stringToFile(sb.toString(), fileName);
            System.out.println("wrote " + combinedValues.size() + " values to " + fileName);
        }
    }

}
