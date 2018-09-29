package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.Call;

public class SampleChain<T> extends SimpleSample<T> {
    Distribution<T> distributionSource = null;
    Call callSource = null;
    Sample<?> lastSample;
    //List<SampleChain<?>> nextSamples = null;

    public SampleChain( T value, double individiualWeight, Sample<?> lastSample ) {
        super( value, individiualWeight );
        this.lastSample = lastSample;
    }

    public SampleChain( T value, double weight,
                        Distribution<T> distributionSource, Call callSource,
                        Sample<?> lastSample ) {
        this( value, weight, lastSample );
        this.distributionSource = distributionSource;
        this.callSource = callSource;
    }

    @Override
    public double weight() {
        return weight * (lastSample != null ? lastSample.weight() : 1.0);
    }

}
