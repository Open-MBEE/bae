/**
 * 
 */
package gov.nasa.jpl.ae.event;

import java.util.Set;

/**
 * @author bclement
 *
 */
public interface LazyUpdate {
  public boolean isStale();
  public void setStale( boolean staleness );
  public void setStale(boolean staleness, boolean deep, Set<LazyUpdate> seen);
}
