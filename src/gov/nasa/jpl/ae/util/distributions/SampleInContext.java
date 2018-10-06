package gov.nasa.jpl.ae.util.distributions;

public class SampleInContext<T> extends SimpleSample<T> {
    protected Distribution distribution;
    /**
     * The object from which the sample is drawn.  This could be a distribution
     * or a variable with a distribution as its value.
     */
    protected Object owner;

    /**
     * Create a Sample with the context of the distribution from which it was
     * sampled and the variable whose value is the distribution.
     *
     * @param value the sample value
     * @param weight the relative weight or probability of the sample
     * @param distribution the distribution from which the sample is drawn
     * @param owner the variable from which the sample is drawn
     */
    public SampleInContext( T value, double weight, Distribution distribution, Object owner ) {
        super(value, weight);
        this.owner = owner;
        this.distribution = distribution;
    }

    /**
     * @return the distribution from which the sample is drawn
     */
    public Distribution getDistribution() {
        if ( distribution == null && owner != null  ) {
            Distribution d = DistributionHelper.getDistribution( owner );
            if ( d != null )
            return (Distribution)d;
        }
        return distribution;
    }

    /**
     * Set the distribution from which the sample is drawn
     */
    public void setDistribution( Distribution distribution ) {
        this.distribution = distribution;
    }

    /**
     * @return get the variable from which the sample is drawn
     */
    public Object getOwner() {
        return owner;
    }

    /**
     * The object from which the sample is drawn.  This could
     */
    public void setOwner( Object owner ) {
        this.owner = owner;
    }
}
