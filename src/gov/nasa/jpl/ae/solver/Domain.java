package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.mbee.util.Evaluatable;
import gov.nasa.jpl.mbee.util.HasId;
import gov.nasa.jpl.mbee.util.Wraps;

public interface Domain< T > extends Cloneable, HasId<Integer>, Wraps< T >, Evaluatable {

  public Domain< T > clone();

  /**
   * @return the number of elements in the domain. If the domain is infinite,
   *         Long.MAX_VALUE (which is to be interpreted as infinity).<p>
   */
  public long magnitude();

  /**
   * @return whether the domain contains no values (including null)
   */
  public boolean isEmpty();
  
  /**
   * Make the domain empty, containing no values, not even null.
   * @return whether any domain changed as a result of this call 
   */
  public boolean clearValues();

  public boolean contains( T t );

  public T pickRandomValue();
  public T pickRandomValueNotEqual( T t );

  public boolean isInfinite();

  public boolean isNullInDomain();
  // TODO -- Change this to return void when not worried about models
  // putting a constraint on this function.
  public boolean setNullInDomain( boolean b );

  public Domain< T > getDefaultDomain();
  //public void setDefaultDomain( Domain< T > domain );

  /**
   * Restrict the object's domain to only include the input value or an empty
   * domain if v is not contained in the current domain. If it is not possible,
   * the object's domain remains unaffected.
   * 
   * @param v
   *          the value
   * @return whether the domain of any object changed as a result of this call
   */
  public boolean restrictToValue( T v );

  /**
   * Restrict this domain to largest subset that is included by the
   * input domain. This will be the empty domain if there is no intersection
   * between this domain and the input domain.
   * 
   * @param domain
   * @return whether the domain of any object changed as a result of this call
   */
  public <TT> boolean restrictTo( Domain< TT > domain );
  
  /**
   * Exclude elements in the input domain from this domain.
   * 
   * @param domain
   * @return whether the domain of any object changed as a result of this call
   */
  public <TT> Domain< TT > subtract( Domain< TT > domain );
  
}
