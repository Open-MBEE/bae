package gov.nasa.jpl.ae.util.distributions;

import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.TreeMap;

public class Normal extends AbstractRealDistribution<NormalDistribution>  {

  public Normal(Double mu, Double sigma) {
    d = new NormalDistribution( Distribution.random,
                                mu == null ? 0.0 : mu,
                                sigma == null ? 1.0 : sigma);
    parameters = new TreeMap<>();
    parameters.put("mean", mu);
    parameters.put("standard deviation", sigma);
  }

  @Override public String toString() {
    return this.getClass().getSimpleName() + "(" + d.getMean() + ", " + d.getNumericalVariance() +
           (bias == null ? "" : ", " + bias) + ")";
  }

  public static void main(String[] args) {
    Normal n1 = new Normal( 0.0,1.0 );
    System.out.println( n1.d.density( 0.1452 ) );
  }
}
