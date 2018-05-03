package gov.nasa.jpl.ae.event;

public interface Deconstructable {
  void deconstruct();
  void addReference();
  void subtractReference();
}
