/**
 *
 */
package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Random;

/**
 * @author bclement
 *
 */
public class StringDomain extends AbstractRangeDomain<String> {

	public static final int maxStringSize = 8;
	public static final String typeMaxValue = String.format("%0" + maxStringSize + "d", 0).replace( "0", "" + (char)( '\0' + 255 ) );
	public static final String typeMinValue = "";
	public static final StringDomain defaultDomain = new StringDomain();

    /**
     * The Kind tells us how to interpret the inherited range as a set of strings.
     */
    public enum Kind {PREFIX_RANGE, SUFFIX_RANGE, ALPHABETIC, TWO_VALUED, UNKNOWN}

	public Kind kind = Kind.UNKNOWN;

  public StringDomain() {
    super(typeMinValue, typeMaxValue);
  }

  public StringDomain(String minValue, String maxValue) {
    super(minValue, maxValue);
  }

  public StringDomain( RangeDomain<String> stringDomain ) {
    super( stringDomain );
    if ( stringDomain instanceof StringDomain ) {
        kind = ( (StringDomain)stringDomain ).kind;
    }
  }

	/* (non-Javadoc)
	 * @see event.Domain#isInfinite()
	 */
	@Override
	public boolean isInfinite() {
        if ( oldBrokenWay ) return true;
		return size() < 0;
	}

    /* (non-Javadoc)
     * @see event.Domain#size()
     */
    @Override public long size() {
        // TODO?
        if ( lowerBound == null || upperBound == null ) return 0;
        if ( lowerBound.equals( upperBound ) ) {
            if ( oldBrokenWay ) return 1;
            if ( lowerIncluded || upperIncluded ) {
                return 1;
            } else {
                return 0;
            }
        }
        if ( oldBrokenWay ) return -1;
        if ( lowerBound.contains( getTypeMaxValue() ) ||upperBound.contains( getTypeMaxValue() ) ) {
            return -1;
        }
        if ( treatAsPrefixOrSuffix() ) {
            return upperBound.length() - lowerBound.length() + 1 -
                   (lowerIncluded ? 0 : 1 ) - ( upperIncluded ? 0 : 1 );
        }
        return -1;
    }

	protected boolean oldBrokenWay = false;

	/* (non-Javadoc)
	 * @see event.Domain#pickRandomValue()
	 */
	@Override
	public String pickRandomValue() {
	    if ( !oldBrokenWay && size() != -1 ) {
	        if ( size() == 0 ) return null;
            Long n = Random.global.nextLong() % size();
            String s = getNthValue( n );
            return s;
        }

	  // REVIEW -- Not a uniform distribution.
        if ( !oldBrokenWay && size() == 1 ) {
            return getValue( false );
        }
		int length = Random.global.nextInt( maxStringSize );
        String initStr = oldBrokenWay ? "" : getLowerBound();
		StringBuffer s = new StringBuffer(initStr);
		// HACK -- Below favors picking the lower bound.
		while (length > 0) {
		    char c = (char)( '\0' + Random.global.nextInt( 256 ) );
		    if ( !oldBrokenWay &&
                 !getUpperBound().equals( getTypeMaxValue() ) &&
                 !less( s.toString() + c, getUpperBound() ) ) {
		        break;
            }
            s.append( c );

			--length;
		}
		return s.toString();
	}

	@Override
	public String toString() {
		if ( lowerBound == typeMinValue && upperBound == typeMaxValue ) {
			return "*";
		}
		// TODO -- escapes? substitute \" and \n  for '"' and newline, etc.?
		return "[\"" + getLowerBound() + "\" \"" + getUpperBound() + "\"]";
	}

	public boolean isSuffix() { return kind == Kind.SUFFIX_RANGE; }
    public boolean isPrefix() { return kind == Kind.PREFIX_RANGE; }
    public boolean isMaybeSuffix() { return isUnknown() || isSuffix(); }
    public boolean isMaybePrefix() { return isUnknown() || isPrefix(); }
    public boolean isUnknown() { return kind == Kind.UNKNOWN; }
    public boolean isAlphabetic() { return kind == Kind.ALPHABETIC; }
    public boolean isTwoValued() { return kind == Kind.TWO_VALUED; }

    public boolean treatAsPrefix() {
        if ( lowerBound == null || upperBound == null ) {
            return false;
        }
        boolean uswl = isMaybePrefix() && upperBound.startsWith( lowerBound );
        if ( uswl ) return true;
        boolean lswu = isMaybePrefix() && lowerBound.startsWith( upperBound );
        return lswu;
    }
    public boolean treatAsSuffix() {
        if ( lowerBound == null || upperBound == null ) {
            return false;
        }
        boolean uewl = isMaybeSuffix() && upperBound.endsWith( lowerBound );
        if ( uewl ) return true;
        boolean lewu = isMaybeSuffix() && lowerBound.endsWith( upperBound );
        return lewu;
    }
    public boolean treatAsPrefixOrSuffix() {
	    return treatAsPrefix() || treatAsSuffix();
    }

    @Override
    public String getNthValue( long n ) {
	    if ( n < 0 ) return null;
	    if ( oldBrokenWay ) return null;
	    long sz = size();
	    if ( sz <= 0 ) return null;
        boolean uswl = isMaybePrefix() && upperBound.startsWith( lowerBound );
        boolean uewl = isMaybeSuffix() && upperBound.endsWith( lowerBound );
	    boolean lswu = isMaybePrefix() && lowerBound.startsWith( upperBound );
        boolean lewu = isMaybeSuffix() && lowerBound.endsWith( upperBound );
        boolean doingSubstrings = uswl || uewl || lswu || lewu;
        //if ( doingSubstrings ) {
            String big = uswl || uewl ? upperBound : lowerBound;
            String small = uswl || uewl ? lowerBound :  upperBound;
            boolean sw = uswl || lswu;
            // b  i  g  s  t  r  i  n  g
            // b  i  g
            // p1s      p2s - - - - - - > p2e
            // p1e
            // b  i  g  s  t  r  i  n  g
            //                   i  n  g
            // p1e < - - - - - - p1s      p2s
            //                            p2e
            int p1start = sw ? 0 : big.length() - small.length();
            int p1end =  0;
            int p2start = sw ? small.length() : big.length();
            int p2end = big.length();
            //int iterations = Math.max( Math.abs(p1start - p1end), Math.abs(p2start - p2end) )
        //}
        String first = !doingSubstrings ? lowerBound : big.substring( p1start, p2start );
        String last = !doingSubstrings ? upperBound : big;
        boolean firstIncluded = uswl || uewl ? lowerIncluded : upperIncluded;
        boolean lastIncluded = uswl || uewl ? upperIncluded : lowerIncluded;
        int p1 = p1start;
        int inc1 = sw ? 0 : -1;
        int p2 = p2start;
        int inc2 = sw ? 1 : 0;
        int i = 0;
        if ( firstIncluded && (first != null || nullInDomain) ) {
            if ( n == 0 ) {
                return first;
            }
            ++i;
        }
        p1 += inc1;
        p2 += inc2;
        if ( doingSubstrings ) {
            //for ( ; i < sz - (lastIncluded ? 1 : 0); ++i, p1 += inc1, p2 += inc2 ) {
            // if ( n == i ) {
            p1 += inc1 * (n-i);
            p2 += inc2 * (n-i);
            if ( n < sz - (lastIncluded ? 1 : 0) ) {
                return big.substring( p1, p2 );
            }
            //}
            //}
        }
        if ( lastIncluded && (n == sz - 1) ) {
            //if ( n == i ) {
                return last;
            //}
        }
        // TODO?
        return null;
    }

  // TODO
  // TODO
  // TODO
  // TODO Since T in Domain<T> is Comparable<T>, can't we move these
  // TODO inequalities into AbstractDomainRange??
  // TODO
  // TODO
  // TODO
  @Override
  public boolean greater( String t1, String t2 ) {
    return compare( t1, t2 ) > 0;
  }

  @Override
  public boolean less( String t1, String t2 ) {
    return compare( t1, t2 ) < 0;
  }

  @Override
  public boolean greaterEquals( String t1, String t2 ) {
    return compare( t1, t2 ) >= 0;
  }

  @Override
  public boolean lessEquals( String t1, String t2 ) {
    return compare( t1, t2 ) <= 0;
  }

  public int compare( String t1, String t2 ) {
      if ( t1 == t2 ) return 0;
      if ( t1 == null ) return -1;
      if ( t2 == null ) return 1;
      if ( t1.equals(t2) ) return 0;
      if ( isMaybePrefix() || isMaybeSuffix() ) {
          if ( t2.contains( t1 ) ) return -1;
          if ( t1.contains( t2 ) ) return 1;
      }
      return t1.compareTo( t2 );
  }

  @Override
  public AbstractRangeDomain< String > clone() {
    return new StringDomain( this );
  }

  @Override
  public String getTypeMaxValue() {
    return typeMaxValue;
  }

  @Override
  public String getTypeMinValue() {
    return typeMinValue;
  }

  @Override
  public RangeDomain< String > getDefaultDomain() {
    return defaultDomain;
  }

//  @Override
//  public void setDefaultDomain( Domain< String > domain ) {
//    if ( domain instanceof StringDomain ) {
//      defaultDomain = (StringDomain)domain;
//    } else if ( domain instanceof RangeDomain ) {
//      defaultDomain = new StringDomain((RangeDomain< String >)domain);
//    }
//  }

  @Override
  public Class< String > getType() {
    return String.class;
  }

  @Override
  public Class< ? > getPrimitiveType() {
    return String.class;  // TODO -- REVIEW -- return null??
  }

  @Override
  public StringDomain make( String lowerBound, String upperBound ) {
      return new StringDomain(lowerBound, upperBound);
  }

  @Override
  public int compareTo( Domain< String > o ) {
    return super.compare( o );
  }

    @Override public boolean contains( String s, boolean strictly ) {
	    if ( treatAsPrefixOrSuffix() ) {
            if ( s.length() > Math.max( lowerBound.length(), upperBound.length() ) ) {
                return false;
            }
            // TODO -- REVIEW! Is this a valid interpretation of strictly?
            if ( !strictly && ( lowerBound.contains( s ) || upperBound.contains( s ) ) ) {
                return true;
            }
            if ( !lowerBound.contains( s ) && !upperBound.contains( s ) ) {
                return false;
            }
	        for ( int n = 0; n < size(); ++n ) {
	            String r = getNthValue( n );
	            if ( s.equals( r ) ) {
	                return true;
                }
            }
        }
        return super.contains( s, strictly );
    }

    @Override public boolean setBounds( String lowerBound, String upperBound ) {
//	    if ( isMaybePrefix() || isMaybeSuffix() ) {
//	        if ( less( upperBound, lowerBound ) ) {
//	            this.lowerBound = upperBound;
//	            this.upperBound = lowerBound;
//            } else {
        boolean changed = false;
        if ( !equals( this.lowerBound, lowerBound ) ) {
            this.lowerBound = lowerBound;
            changed = true;
        }
        if ( !equals( this.upperBound, upperBound ) ) {
            this.upperBound = upperBound;
            changed = true;
        }

//            }
        if ( !changed && (!lowerIncluded || !upperIncluded) && (!equals(lowerBound, upperBound) || (!lowerIncluded && !upperIncluded)) ) {
            changed = true;
        }
            lowerIncluded = true;
            upperIncluded = true;
            return changed;
//        }
//        return super.setBounds( lowerBound, upperBound );
    }

    //    @Override
//    public boolean intersectRestrict( AbstractRangeDomain<String> o ) {
//	      if ( treatAsPrefixOrSuffix() ) {
//
//        }
//        return super.intersectRestrict( o );
//    }

    @Override public <TT> boolean restrictTo( AbstractRangeDomain<TT> domain ) {
        //if ( oldBrokenWay) return
        if ( this.equals( domain ) ) {
            return false;
        }
        boolean isString = domain instanceof StringDomain;
        boolean thisOne = size() == 1 || magnitude() == 1;
        long ds = domain.size();
        long m = domain.magnitude();
        boolean thatOne = ds == 1 || m == 1;
        if ( thisOne && thatOne ) {
            makeEmpty();
            return true;
        }
        if ( thisOne && !thatOne ) {
            // don't want to really check if string is in a range
            return false;
        }
        if ( thatOne && !thisOne ) {
            // don't want to really check if string is in a range
            this.copy( (StringDomain)domain );
            return true;
        }
        StringDomain originalDomain = (StringDomain)clone();
        intersectRestrict( (AbstractRangeDomain<String>)domain );
        boolean changed = !this.equals( originalDomain );
        if ( changed ) {
            if ( isEmpty() ) {
                copy( originalDomain );
                return false;
            }
            Debug.error( true, false,
                         "WARNING! restricted " + originalDomain + " with "
                         + domain + " as ranges!  Result = " + this );
            return true;
        }
        return false;
    }

    public static void main( String[] args ) {
        // testing
        StringDomain d0 = new StringDomain( "", "abcde" );
        d0.kind = Kind.PREFIX_RANGE;
        System.out.println( "StringDomain d1 = " + d0 + ", size = " + d0.size() );
        for ( int i = 0; i < d0.size(); ++i ) {
            System.out.println( "value " + i + " = " + d0.getNthValue( i ) );
        }

        StringDomain d1 = new StringDomain( "ab", "abcde" );
        d1.kind = Kind.PREFIX_RANGE;
        System.out.println( "StringDomain d1 = " + d1 + ", size = " + d1.size() );
        for ( int i = 0; i < d1.size(); ++i ) {
            System.out.println( "value " + i + " = " + d1.getNthValue( i ) );
        }

        StringDomain d2 = new StringDomain( "aba", "aba_what_aba" );
        d2.kind = Kind.SUFFIX_RANGE;
        d2.lowerIncluded = false;
        d2.upperIncluded = false;
        System.out.println( "StringDomain d2 = " + d2 + ", size = " + d2.size() );
        for ( int i = 0; i < d2.size(); ++i ) {
            System.out.println( "value " + i + " = " + d2.getNthValue( i ) );
        }

        StringDomain d3 = new StringDomain( "abc", "abcdefg" );
        System.out.println( "StringDomain d3 = " + d3 + ", size = " + d3.size() );
        StringDomain d3_by_1 = (StringDomain)d3.clone();
        boolean r = d3_by_1.restrictTo( d1 );
        System.out.println( "StringDomain d3_by_1 = " + d3_by_1 + ", size = " +
                            d3_by_1.size() + " " + (r ? "" : "not ") + "restricted");
        StringDomain d1_by_3 = (StringDomain)d1.clone();
        r = d1_by_3.restrictTo( d3 );
        System.out.println( "StringDomain d1_by_3 = " + d1_by_3 + ", size = " +
                            d1_by_3.size() + " " + (r ? "" : "not ") + "restricted" );
    }

}
