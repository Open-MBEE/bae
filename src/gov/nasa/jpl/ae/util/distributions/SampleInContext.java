package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.solver.Variable;

public class SampleInContext<T> extends SimpleSample<T> {
    protected Distribution distribution;
    protected Variable variable;

    /**
     * Create a Sample with the context of the distribution from which it was
     * sampled and the variable whose value is the distribution.
     *
     * @param value the sample value
     * @param weight the relative weight or probability of the sample
     * @param distribution the distribution from which the sample is drawn
     * @param variable the variable from which the sample is drawn
     */
    public SampleInContext( T value, double weight, Distribution distribution, Variable variable ) {
        super(value, weight);
        this.variable = variable;
        this.distribution = distribution;
    }

    /**
     * @return the distribution from which the sample is drawn
     */
    public Distribution getDistribution() {
        if ( distribution == null && variable != null && variable.getValue( false ) instanceof Distribution ) {
            return (Distribution)variable.getValue( false );
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
    public Variable getVariable() {
        return variable;
    }

    /**
     * Set the variable from which the sample is drawn
     */
    public void setVariable( Variable variable ) {
        this.variable = variable;
    }
}
