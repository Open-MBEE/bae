package gov.nasa.jpl.ae.util;

public class LamportClock {
    public static boolean usingLamportClock = true;

    private static long clock = 0L;
    public static synchronized long tick() {
        return clock++;
    }
}

