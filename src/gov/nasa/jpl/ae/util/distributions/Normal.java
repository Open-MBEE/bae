package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.NormalDistribution;

public class Normal extends AbstractRealDistribution<NormalDistribution>  {

  public Normal(Double mu, Double sigma) {
    d = new NormalDistribution( Distribution.random,  mu == null ? 0.0 : mu, sigma == null ? 1.0 : sigma);
  }

}
