package gov.nasa.jpl.ae.event;

public interface Executor {
  public Boolean execute( Long time, String name,
                          String shortClassName, String longClassName,
                          String value );
  public Thread getThread();
}
