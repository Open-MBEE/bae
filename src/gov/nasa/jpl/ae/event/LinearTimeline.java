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
   */
  public LinearTimeline( ) {
    super( "name", null, null, Double.class, TimeVaryingMap.LINEAR );
  }

  /**
   * @param name
   */
  public LinearTimeline( String name ) {
    super( name, null, null, Double.class, TimeVaryingMap.LINEAR );
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
