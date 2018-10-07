package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Random;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class SampleDistribution<T> extends AbstractDistribution<T> {

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

        SampleMap() {
            super(CompareUtils.GenericComparator.instance());
        }

        public boolean add(V t, Sample<V> s) {
            combinedValue = combineSample(combinedValue, totalWeight, s);
            totalWeight += s.weight();
            SampleSet ss = get(t);
            if ( ss == null ) ss = new SampleSet();
            boolean r = ss.add(s);
            put(t, ss);
            return r;
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
        if ( sample.value() instanceof Number ) {
            double v = ((Number)sample.value()).doubleValue();
            if ( combinedValue == null ) {
                combinedValue = v;
            } else {
                combinedValue =
                        ( combinedValue * totalWeight + v * sample.weight() ) /
                        ( totalWeight + sample.weight() );
            }
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
        if ( samples == null ) samples = new SampleMap<>();
        return samples.add( s.value(), s );
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
}
