package gov.nasa.jpl.ae.solver;

import java.util.Set;

public interface Simplifiable {
  /**
   * Attempt to simplify.
   * @param deep whether to recursively simplify contained objects
   * @param seen a list of objects already visited to avoid infinite recursion
   * @return whether or not the object changed as a result of simplification
   */
  public boolean simplify( boolean deep, Set<Simplifiable> seen );

  /**
   * Attempt to simplify making just one pass when multiple passes may be needed.
   * @param deep whether to recursively simplify contained objects
   * @param seen a list of objects already visited to avoid infinite recursion
   * @return whether or not the object changed as a result of simplification
   */
  public boolean simplifyOnePass( boolean deep, Set<Simplifiable> seen );
}
