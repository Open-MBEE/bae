/**
 * 
 */
package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.ae.util.Math;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Random;

/**
 * @author bclement
 *
 */
public class IntegerDomain extends AbstractFiniteRangeDomain< Integer > {

  public static final int typeMaxValue = Integer.MAX_VALUE;
  public static final int typeMinValue = Integer.MIN_VALUE;

  //public static IntegerDomain domain = new IntegerDomain();  // REVIEW -- why is this not defaultDomain?
  public static IntegerDomain positiveDomain =
      new IntegerDomain(0, typeMaxValue);
  public static final IntegerDomain defaultDomain = new IntegerDomain();  // REVIEW -- make this final?

  public IntegerDomain() {
    super(typeMinValue, typeMaxValue);
  }
  
  public IntegerDomain(int minValue, int maxValue) {
    super(minValue, maxValue);
  }

  public IntegerDomain( RangeDomain< Integer > domain ) {
    super( domain );
  }

  /* (non-Javadoc)
   * @see event.Domain#isInfinite()
   */
  @Override
  public boolean isInfinite() {
    return false;
  }

  /* (non-Javadoc)
   * @see event.Domain#size()
   */
  @Override
  public long size() {
    if ( lowerBound == null || upperBound == null ) return 0;
    if ( lowerBound.equals( upperBound ) ) return 1;
    return Math.plus( (int)upperBound, (int)-lowerBound );
  }

  /* (non-Javadoc)
   * @see event.Domain#getLowerBound()
   */
  @Override
  public Integer getLowerBound() {
    return lowerBound;
  }

  /* (non-Javadoc)
   * @see event.Domain#getUpperBound()
   */
  @Override
  public Integer getUpperBound() {
    return upperBound;
  }

  /* (non-Javadoc)
   * @see event.Domain#pickRandomValue()
   */
  @Override
  public Integer pickRandomValue() {
    if ( this.isEmpty() ) {
      return null;
    }
    if ( size() == 1 ) {
        return getValue( false );
    }

    Integer ub = getUpperBound();
    Integer lb = getLowerBound();
    if ( ub == null || lb == null ) {
      Debug.error( true, false, "Trying to pick random value with null bound for LongDomain: " + this);
      return null;
    }

    //return (int) Math.abs( getLowerBound() + Math.random() * size() );
    // a bunch of tricks to avoid overflow
    double r1 = Random.global.nextDouble();
    double r2 = Random.global.nextDouble();
    double middle = getMiddleValue();
    double half = ub - middle;
    int r = 0;
    // Have to use max and min since middle is not exactly in the middle
    // REVIEW -- does this skew the distribution?
    if ( r1 < 0.5 ) {
      r = max(lb, (int)(middle - r2 * half));
    } else {
      r = min(ub, (int)(middle + r2 * half));
    }
    return r;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.AbstractRangeDomain#pickRandomValueLessThan(java.lang.Object)
   */
  @Override
  public Integer pickRandomValueLessThan( Integer maxVal ) {
     int u = getUpperBound();
     setUpperBound( maxVal );
     int p = pickRandomValue();
     setUpperBound( u );
     return p;
   }

  
  private Integer getMiddleValue() {
    // TODO -- should interpret null as zero 
    if ( lowerBound == null || upperBound == null ) return 0;
    
    return (int)(lowerBound + upperBound/2 - lowerBound/2); // this is a floor
  }

  @Override
  public boolean contains( Integer t ) {
    if ( t == null && 
        ( lowerBound != null  || upperBound != null ) ) return false;
   if ( t == null ) return nullInDomain;
   if ( lowerBound == null && upperBound == null ) return false;
   return (lowerBound == null || lowerBound <= t) && (upperBound == null || upperBound >= t);
  }

  // counts from zero!!!
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.AbstractFiniteRangeDomain#getNthValue(long)
   */
  @Override
  public Integer getNthValue( long n ) {
    return (int)(((long)getLowerBound()) + n);
  }

  @Override
  public boolean greater( Integer t1, Integer t2 ) {
    if ( t1 == null ) return false;
    if ( t2 == null ) return t1 != null;
    return t1 > t2;
  }

  @Override
  public boolean less( Integer t1, Integer t2 ) {
    if ( t1 == null ) return t2 != null;
    if ( t2 == null ) return false;
    return t1 < t2;
  }

//  @Override
//  public boolean equals( Integer t1, Integer t2 ) {
//    return t1 >= t2;
//  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.AbstractFiniteRangeDomain#greaterEquals(java.lang.Comparable, java.lang.Comparable)
   */
  @Override
  public boolean greaterEquals( Integer t1, Integer t2 ) {
    if ( t1 == null ) return t2 == null;
    if ( t2 == null ) return true;
    return t1 >= t2;
  }

  @Override
  public boolean lessEquals( Integer t1, Integer t2 ) {
    if ( t1 == null ) return true;
    if ( t2 == null ) return t1 == null;
    return t1 <= t2;
  }

  @Override
  public AbstractFiniteRangeDomain< Integer > clone() {
    return new IntegerDomain( this );
  }

  @Override
  public Class< Integer > getType() {
    return Integer.class;
  }

  @Override
  public Class< ? > getPrimitiveType() {
    return int.class;
  }

  @Override
  public Integer getTypeMaxValue() {
    return typeMaxValue;
  }

  @Override
  public Integer getTypeMinValue() {
    return typeMinValue;
  }

  @Override
  public RangeDomain< Integer > getDefaultDomain() {
    return defaultDomain ;
  }

//  @Override
//  public void setDefaultDomain( Domain< Integer > domain ) {
//    if ( domain instanceof IntegerDomain ) {
//      defaultDomain = (IntegerDomain)domain;
//    } else if ( domain instanceof RangeDomain ) {
//      defaultDomain = new IntegerDomain((RangeDomain< Integer >)domain);
//    }
//  }
  
  @Override
  public IntegerDomain make( Integer lowerBound, Integer upperBound ) {
    return new IntegerDomain(lowerBound, upperBound);
  }

  @Override
  public Integer getNextGreaterValue( Integer t ) {
    if (t == null || equals(t, getTypeMaxValue()) ) return null;
    return t + 1;
  }

  @Override
  public Integer getPreviousLesserValue( Integer t ) {
    if (t == null || equals(t, getTypeMinValue()) ) return null;
    return t - 1;
  }

  @Override
  public int compareTo( Domain< Integer > o ) {
    return super.compare( o );
  }

}
