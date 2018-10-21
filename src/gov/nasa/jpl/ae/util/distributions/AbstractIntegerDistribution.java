package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.IntegerDistribution;

import java.util.Set;

public class AbstractIntegerDistribution<D extends IntegerDistribution> extends AbstractDistribution<Integer> {
    D d;

    //    public abstract Distribution<Double> plus( Distribution<?> otherDist );
    //    public abstract Distribution<Double> minus( Distribution<?> otherDist );
    //    public abstract Distribution<Double> times( Distribution<?> otherDist );
    //    public abstract Distribution<Double> dividedBy( Distribution<?> otherDist );

    @Override public double probability( Integer t ) {
        return d.probability( t );
    }

    @Override public double pdf( Integer integer ) {
        double p = d.probability( integer );
        if ( p > 0.0 ) return p;
        // TODO ??
        return 0;
    }

    @Override public Double mean() {
        return d.getNumericalMean();
    }

    @Override public Double variance() {
        return d.getNumericalVariance();
    }


    @Override public Sample<Integer> sample() {
        Integer x = d.sample();
        Double w = 1.0; //pdf(x);
        return new SimpleSample<>( x, w );
    }

    @Override public Sample<Integer> sample( Distribution<Integer> bias ) {
        Sample<Integer> s = bias.sample();
        double w = pdf( s.value() ) / bias.pdf( s.value() );
        return new SimpleSample<>( s.value(), w );
    }

    @Override public double cumulativeProbability( Integer t ) {
        return d.cumulativeProbability( (Integer)t );
    }

    @Override public Class<Integer> getType() {
        return Integer.class;
    }

//    @Override public Object getOwner() {
//        return null;
//    }
//
//    @Override public void setOwner( Object owner ) {
//
//    }

    /**
     * Get the name preceded by parent names, separated by '.'
     *
     * @param seen a list of objects that have already been visited and that are to
     *             be used to avoid infinite recursion
     * @return
     */
    @Override public String getQualifiedName( Set<Object> seen ) {
        return null;
    }

    /**
     * Get the ID preceded by parent IDs, separated by '.'
     *
     * @param seen a list of objects that have already been visited and that are to
     *             be used to avoid infinite recursion
     * @return
     */
    @Override public String getQualifiedId( Set<Object> seen ) {
        return null;
    }
}
