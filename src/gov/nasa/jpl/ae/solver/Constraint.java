package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.ae.event.LazyUpdate;

import java.util.Set;

public interface Constraint extends Satisfiable, Comparable< Constraint >,
                                    LazyUpdate {
  public Set< Variable< ? > > getVariables();

  public < T > void pickValue( Variable< T > v ); // not implemented

  public < T > void restrictDomain( Variable< T > v ); // not implemented

  public < T > boolean isFree( Variable< T > v );

  public < T > boolean isDependent( Variable< T > v );
  
  /**
   * @return the freeVariables
   */
  public Set< Variable< ? > > getFreeVariables();

  /**
   * @param freeParameters
   *          the freeVariables to set
   */
  public void setFreeVariables( Set< Variable< ? > > freeVariables );

}