package gov.nasa.jpl.ae.event;

public interface Deconstructable {
  void deconstruct();
  //boolean isDeconstructed();
  void addReference();
  void subtractReference();
}
