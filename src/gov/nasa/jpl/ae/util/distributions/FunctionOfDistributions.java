package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.Call;
import gov.nasa.jpl.ae.event.Expression;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Utils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class FunctionOfDistributions<T> implements Distribution<T> {
    Call call;
    Distribution distribution = null;

    Map< T, Set< Sample< T > > > samples = null;
    boolean recordingSamples = true;
    boolean combiningSamples = true;
    double totalWeight = 0.0;
    T combinedValue = null;

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

    @Override public Pair<T, Double> sample() {
        Pair<T, Double> p = tryToSample();
        if ( recordingSamples ) {
            SimpleSample s = new SimpleSample( p.first, p.second );
            if ( samples == null ) samples = new LinkedHashMap<>();
            Utils.add( samples, p.first, s);
        }
        if ( combiningSamples ) {
            if ( getType() == Double.class ) {
                Double cd = (totalWeight*((Double)combinedValue) +
                             p.second * ((Double)p.first)) /
                            (totalWeight + p.second);
                combinedValue = (T)cd;
            } else {
                // TODO -- FIXME!!!
            }
            totalWeight += p.second;
        }
        return p;
    }

    protected Pair<T, Double> tryToSample() {
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
        } else {
            Distribution<?> d = null;
            try {
                d = Expression.evaluate( r, Distribution.class, true );
                this.distribution = d;
                if ( d != null ) {
                    Object o = d.sample();
                    if ( getType() == null || getType().isInstance( o ) ) {
                        t = (T)o;
                    }
                    w = pdf(t);
                }
            } catch ( Throwable e ) {
                // ignore
            }
        }
        return new Pair(t, w);
    }

    @Override public double cumulativeProbability( T t ) {
        return 0;
    }

    @Override public Class<T> getType() {
        return null;
    }
}
