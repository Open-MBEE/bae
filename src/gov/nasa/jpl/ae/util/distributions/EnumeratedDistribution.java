package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Utils;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.NotANumberException;
import org.apache.commons.math3.exception.NotFiniteNumberException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EnumeratedDistribution<T> extends AbstractDistribution<T> {
    org.apache.commons.math3.distribution.EnumeratedDistribution<T> d = null;
    Class<T> type = null;

//    public EnumeratedDistribution(final List<gov.nasa.jpl.mbee.util.Pair<T, Double> > pmf)
//            throws NotPositiveException, MathArithmeticException,
//                   NotFiniteNumberException, NotANumberException {
//        d = new org.apache.commons.math3.distribution.EnumeratedDistribution(Distribution.random, pmf);
//    }
    public EnumeratedDistribution(final List<Pair<T, Double>> pmf)
            throws NotPositiveException, MathArithmeticException,
                   NotFiniteNumberException, NotANumberException {
        d = new org.apache.commons.math3.distribution.EnumeratedDistribution(
                Distribution.random, pmf);
    }
    public EnumeratedDistribution(final Map<T, Double> pmf)
            throws NotPositiveException, MathArithmeticException,
                   NotFiniteNumberException, NotANumberException {
        d = new org.apache.commons.math3.distribution.EnumeratedDistribution(
                Distribution.random, mapToPairList(pmf));
    }


    protected <K, V> List< Pair<K, V> > mapToPairList(Map<K,V> map) {
        ArrayList<Pair<K, V>> r = new ArrayList<Pair<K, V>>();
        for ( Map.Entry e : map.entrySet() ) {
            Object k = e.getKey();
            // Go ahead and initialize the type by inferring it from the elements.
            if ( type == null ) {
                type = (Class<T>)k.getClass();
            } else {
                type = (Class<T>)ClassUtils.dominantTypeClass( type, k.getClass() );
            }
            Pair p = new Pair( k, e.getValue() );
            r.add(p);
        }
        return r;
    }

    @Override public double probability( T t ) {
        try {
            for ( Pair<T,Double> p : d.getPmf() ) {
                if ( Utils.valuesEqual( p.getFirst(), t ) ) {
                    return p.getSecond();
                }
            }
        } catch (Throwable e) {
            return 0.0;
        }
        return 0.0;
    }

    @Override public double pdf( T t ) {
        return probability( t );
    }

    @Override public Sample<T> sample() {
        Object s = d.sample();
        double w = 1.0;
        return new SimpleSample<T>( (T)s, w );
    }

    @Override public Sample<T> sample( Distribution<T> bias ) {
        Sample<T> s = bias.sample();
        double w = pdf( s.value() ) / bias.pdf( s.value() );
        return new SimpleSample<T>( s.value(), w );
    }

    @Override public double cumulativeProbability( T t ) {
        double total = 0.0;
        for ( Pair<T,Double> p : d.getPmf() ) {
            if ( CompareUtils.compare( p.getFirst(), t ) < 0 ) {
                Double pr = p.getSecond();
                if ( pr != null ) {
                    total += pr;
                }
            }
        }
        return total;
    }

    @Override public Class<T> getType() {
        return type;
    }

    @Override public Double mean() {
        double total = 0.0;
        double totalWeight = 0.0;
        Double cv = null;
        for ( Pair<T,Double> p : d.getPmf() ) {
            cv = DistributionHelper.combineValues( getType(),
                                                   new SimpleSample( p.getFirst(),
                                                                     p.getSecond() ),
                                                   cv, totalWeight );
            totalWeight += p.getSecond();
        }
        return cv;
    }

    @Override public Double variance() {
        Double m = mean();
        if ( m == null ) return null;
        Double v = null;
        double totalWeight = 0.0;
        for ( Pair<T,Double> p : d.getPmf() ) {
            gov.nasa.jpl.mbee.util.Pair<Boolean, Double> pair =
                    ClassUtils.coerce( p.getFirst(), Double.class, false );
            if ( pair != null && Utils.isTrue( pair.first ) && pair.second != null ) {
                double diff = m - pair.second;
                v += diff * diff;
            } else {
                return null;
            }
            totalWeight += 1.0;
        }
        if ( totalWeight <= 0.0 ) return null;
        return v / totalWeight;
    }
}
