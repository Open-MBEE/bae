/**
 * 
 */
package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.mbee.util.Debug;

import java.lang.reflect.Method;
import java.util.Map.Entry;

/**
 * LinearTimeline is a TimeVaryingMap of Doubles that interpolates values
 * linearly with time between entries. TimeVaryingMap uses the last value seen
 * and is, thus, a step function.
 * 
 */
public class LinearTimeline extends TimeVaryingMap< Double > {

  /**
   * 
   */
  private static final long serialVersionUID = -4707739577094130809L;


  /**
   * @param name
   */
  public LinearTimeline( String name ) {
    super( name );
  }

  /**
   * @param name
   * @param defaultValue
   */
  public LinearTimeline( String name, Interpolation interpolation ) {
    super( name, Double.class, null, null,null, interpolation );
  }

  /**
   * @param name
   * @param initialValueFunction
   * @param o
   * @param samplePeriod
   * @param horizonDuration
   */
  public LinearTimeline( String name, Method initialValueFunction, Object o,
                         int samplePeriod, int horizonDuration ) {
    super( name, initialValueFunction, o, samplePeriod, horizonDuration );
  }
  
  public LinearTimeline( LinearTimeline timeline ) {
    super(timeline);
  }
  
//  @Override
//  public LinearTimeline clone() {
//    return new LinearTimeline( this );
//  }
//
//  @Override
//  public LinearTimeline emptyClone() {
//    LinearTimeline timeline = new LinearTimeline( getName(), null, true, this.interpolation );
//    return timeline;
//  }



}
