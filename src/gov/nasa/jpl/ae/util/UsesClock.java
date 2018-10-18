package gov.nasa.jpl.ae.util;

import java.util.Set;

public interface UsesClock {
    public long getLastUpdated();
    public long getLastUpdated( Set<UsesClock> seen);
}
