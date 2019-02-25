package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.BinomialDistribution;

import java.util.TreeMap;

/**
 * Created by dank on 6/29/17.
 */
public class BooleanDistribution extends AbstractDistribution<Boolean> {

    protected BinomialDistribution d;

    public BooleanDistribution(double p) {
        init(p);
    }

    protected void init(double p) {
        d = new BinomialDistribution( Distribution.random, 1, p);
        parameters = new TreeMap<>();
        parameters.put("p", p);
    }

    public double probability() {
        return probability( true );
    }

    @Override public double probability( Boolean aBoolean ) {
        return d.probability( aBoolean ? 1 : 0 );
    }

    @Override public double pdf( Boolean aBoolean ) {
        return probability( aBoolean );
    }

    @Override public double cumulativeProbability( Boolean aBoolean ) {
        return d.cumulativeProbability( aBoolean ? 1 : 0 );
    }

    @Override public Sample<Boolean> sample() {
        // we should try to never ... ???
        int s = d.sample();
        double w = 1.0;//d.probability( s );
        return new SimpleSample<>( s == 1, w );
    }

    @Override public Sample<Boolean> sample( Distribution<Boolean> bias ) {
        Sample<Boolean> ds = bias.sample();
        double w = pdf(ds.value()) / bias.pdf(ds.value());//d.probability( s );
        return new SimpleSample<>( ds.value(), w );
    }

    @Override public Class<Boolean> getType() {
        return Boolean.class;
    }

    /**
     * Set the value of the object that is wrapped by this object.
     *
     * @param value the new value to be wrapped
     */
    @Override public void setValue( Boolean value ) {
        if ( value == null ) return;
        init( value ? 1.0 : 0.0 );
    }

    /**
     * Return true if there is an object wrapped.  If getValue() returns null, this call distinguishes whether null is a
     * valid value or not.  If multiple objects are wrapped, then getValue() may return null also in this case.
     *
     * @return true if there is a wrapped value
     */
    @Override public boolean hasValue() {
        //return super.hasValue();
        return d != null;
    }

    @Override public boolean hasMultipleValues() {
        return size() > 1 && !allValuesEqual();
    }

    @Override public boolean isEmpty() {
        return d == null;
    }

    @Override public boolean isDiscrete() {
        return true;
    }

    @Override public long size() {
        return probability() == 0.0 || probability() == 1.0 ? 1 : 2;
    }

    @Override public Boolean firstValue() {
        return probability() == 0.0 ? false : true;
    }

    @Override public boolean allValuesEqual() {
        return size() == 1;
    }

    /**
     * @param propagate whether or not to propagate dependencies in order to determine
     *                  what object is wrapped
     * @return the object that is wrapped by this object
     */
    @Override public Boolean getValue( boolean propagate ) {
        double p = probability();
        return p == 0.0 ? false : p == 1.0 ? true : null;
    }

    @Override public Double mean() {
        return d.getNumericalMean();
    }

    @Override public Double variance() {
        return d.getNumericalVariance();
    }

    @Override public Double supportLowerBound() {
        return 0.0;
    }

    @Override public Double supportUpperBound() {
        return 1.0;
    }

    @Override public String toString() {
        return this.getClass().getSimpleName() + "(" + d.getProbabilityOfSuccess() +
               (bias == null ? "" : ", " + bias) + ")";
    }
}
