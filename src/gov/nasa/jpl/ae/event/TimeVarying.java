package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.mbee.util.HasId;
import gov.nasa.jpl.mbee.util.Wraps;

/**
 *
 */

/**
 * @author bclement
 *
 * TODO -- REVIEW -- Should this implement Map<Timepoint, T>?
 * TODO -- REVIEW -- Look at seqr tms to see if can leverage Timeline infrastructure.
 */
public interface TimeVarying< T, V > extends Comparable< TimeVarying< T, V > >, HasId<Integer>, Wraps< V > { //extends Map< Timepoint, T > {
  public V getValue( Parameter< T > t );
  public V getValue( T t );
  public V setValue( Parameter< T > t, V value );
  public V unsetValue( Parameter< T > t, V value );
//  public Iterator< ? > iterator();
////  public T getValueAtTime( Timepoint t );
////  public T setValueAtTime( Timepoint t, T value );
////  // allows iteration through values
////  public Timepoint timeOfNextValue( Timepoint t );
  boolean isApplied( Effect effect );
  public boolean canBeApplied( Effect effect );
  public Object getOwner();
  public void setOwner( Object owner );
}
