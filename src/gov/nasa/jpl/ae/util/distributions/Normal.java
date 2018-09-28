package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.mbee.util.Random;
import org.apache.commons.math3.distribution.NormalDistribution;

public class Normal extends NormalDistribution implements Distribution  {

  public Normal(Double mu, Double sigma){
    super( Distribution.random,  mu == null ? 0.0 : mu, sigma == null ? 1.0 : sigma);
  }
}
