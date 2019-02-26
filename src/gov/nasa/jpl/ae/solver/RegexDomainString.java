package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.ae.util.Math;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.Wraps;

import java.util.*;

public class RegexDomainString implements ComparableDomain<String> {
    // static members

    public static final RegexDomainString defaultDomain =
            new RegexDomainString( makeAnyStringDomain() );
    public static final StringDomain stringDomain = StringDomain.defaultDomain;

    // instance members

    public RegexDomain<Character> charListDomain;
    // TODO -- Make a ComparableRegexDomain for charListDomain

    // Constructors

    public RegexDomainString() {
        charListDomain = makeAnyStringDomain();
    }

    public RegexDomainString(String literal) {
        charListDomain = new RegexDomain<>();
        charListDomain.seq = toCharDomains( literal );
    }

    public RegexDomainString( List<Domain<?>> charListDomain ) {
        List<Domain<?>> preprocessedList = new ArrayList<>();
        for ( Domain<?> d : charListDomain ) {
            RegexDomainString rd1 = null;
            if ( d instanceof StringDomain ) {
                rd1 = new RegexDomainString( (StringDomain)d );
            } else if ( d instanceof SingleValueDomain ) {
                rd1 = new RegexDomainString( "" + d.getValue( false ) );
            } else if ( d instanceof RegexDomainString ) {
                rd1 = (RegexDomainString)d;
            }
            if ( rd1 != null ) {
                preprocessedList.addAll( rd1.charListDomain.seq );
            }
        }
        this.charListDomain =
                (RegexDomain)RegexDomain.flattenAnd( new RegexDomain<>( preprocessedList ) );
    }

    public RegexDomainString(Domain domain) {
        if ( domain == null ) return;
        if ( domain instanceof RegexDomainString ) {
            charListDomain = ((RegexDomainString)domain).charListDomain.clone();
            return;
        }
        if ( domain instanceof RegexDomain ) {  // REVIEW -- is this ok to be OrDomain?
            charListDomain = (RegexDomain)domain;
            return;
        }
        charListDomain = new RegexDomain<>();
        if ( domain.magnitude() == 1 ) {
            charListDomain.seq = toCharDomains( "" + domain.getValue( true ) );
        } else { // TODO -- if it's not really *, we should do something difft.
            charListDomain.seq.add(new RegexDomain.ManyDomain<>() );
        }
    }

    public RegexDomainString(RegexDomainString rds) {
        charListDomain = rds.charListDomain.clone();
    }

    @Override public Domain<String> clone() {
        return new RegexDomainString( this );
    }

    protected static RegexDomain<Character> makeAnyStringDomain() {
        return new RegexDomain<Character>( Utils.newList( new RegexDomain.ManyDomain<Character>() ) );
    }

    /**
     * @return the number of elements in the domain. If the domain is infinite,
     * Long.MAX_VALUE (which is to be interpreted as infinity).<p>
     */
    @Override public long magnitude() {
        return charListDomain.magnitude();
    }

    /**
     * @return whether the domain contains no values (including null)
     */
    @Override public boolean isEmpty() {
        return charListDomain.isEmpty();
    }

    /**
     * Make the domain empty, containing no values, not even null.
     *
     * @return whether any domain changed as a result of this call
     */
    @Override public boolean clearValues() {
        return charListDomain.clearValues();
    }

    public boolean contains( String characters ) {
        List<Character> charList =toChars( characters );
        return charListDomain.contains( charList );
    }

    public boolean contains( RegexDomainString rds ) {
        return charListDomain.contains( rds.charListDomain );
    }

    @Override public String pickRandomValue() {
        List<Character> x = charListDomain.pickRandomValue();
        String s = charsToString(x);
        return s;
    }

    @Override public String pickRandomValueNotEqual( String s ) {
        List<Character> chars = toChars( s );
        List<Character> x = charListDomain.pickRandomValueNotEqual( chars );
        String ss = charsToString(x);
        return ss;
    }

    public static String charsToString( List<Character> x ) {
        if ( x == null ) return null;
        StringBuffer sb = new StringBuffer();
        for ( Character c :  x ) {
            sb.append( c );
        }
        String s = sb.toString();
        return s;
    }

    @Override public boolean isInfinite() {
        return Math.isInfinity( magnitude() );
    }

    @Override public boolean isNullInDomain() {
        return false;
    }

    @Override public boolean setNullInDomain( boolean b ) {
        return false;
    }

    @Override public Domain<String> getDefaultDomain() {
        return defaultDomain;
    }

    /**
     * Restrict the object's domain to only include the input value or an empty
     * domain if v is not contained in the current domain. If it is not possible,
     * the object's domain remains unaffected.
     *
     * @param v the value
     * @return whether the domain of any object changed as a result of this call
     */
    @Override public boolean restrictToValue( String v ) {
        List<Character> charList = toChars( v );
        boolean changed = charListDomain.restrictToValue( charList );
        return changed;
    }

    /**
     * Restrict the object's domain to largest subset that is included by the
     * input domain. This will be the empty domain if there is no intersection
     * between the object's prior domain and the input domain.
     *
     * @param domain
     * @return whether the domain of any object changed as a result of this call
     */
    @Override public <TT> boolean restrictTo( Domain<TT> domain ) {
        Domain<TT> d = domain;
        if ( d instanceof RegexDomainString ) {
            d = (Domain<TT>)( (RegexDomainString)d ).charListDomain;
        }
        return charListDomain.restrictTo( d );
    }

    /**
     * Exclude elements in the input domain from this domain.
     *
     * @param domain
     * @return whether the domain of any object changed as a result of this call
     */
    @Override public <TT> Domain<TT> subtract( Domain<TT> domain ) {
        Domain<TT> d = domain;
        if ( d instanceof RegexDomainString ) {
            d = (Domain<TT>)( (RegexDomainString)d ).charListDomain;
        }
        return charListDomain.subtract( d );
    }

    protected List<Domain<?>> toCharDomains( String characters ) {
        List<Character> charList = toChars( characters );
        List<Domain<?>> domains = new ArrayList<>(characters.length());
        for ( int i=0; i < characters.length(); ++i ) {
            domains.add(new RegexDomain.SimpleDomain<>( charList.get(i) ) );
        }
        return domains;
    }
    protected List<Character> toChars( String characters) {
        if ( characters == null ) return null;
        ArrayList<Character> charList = new ArrayList<>(characters.length());
        for ( int i=0; i < characters.length(); ++i ) {
            charList.add(characters.charAt( i ));
        }
        return charList;
    }

    @Override public <T> T evaluate( Class<T> cls, boolean propagate ) {
        return charListDomain.evaluate( cls, propagate );
    }

    @Override public Integer getId() {
        return charListDomain.getId();
    }

    /**
     * @return the type of the object that this object wraps
     */
    @Override public Class<?> getType() {
        return String.class;
    }

    /**
     * @param className the name of {@code this} class (which should be the class
     *                  redefining this method or a subclass) with generic parameters
     * @return the name of the type for the object that would be wrapped by an
     * object with a class name of {@code className}; this should be the
     * type of the return value for {@link Wraps<String>.getValue(boolean)}.
     */
    @Override public String getTypeNameForClassName( String className ) {
        return charListDomain.getTypeNameForClassName( className );
    }

    /**
     * @return the primitive class corresponding to the object wrapped by this
     * object (possibly in several layers)
     */
    @Override public Class<?> getPrimitiveType() {
        return String.class;
    }

    /**
     * @param propagate whether or not to propagate dependencies in order to determine
     *                  what object is wrapped
     * @return the object that is wrapped by this object or null if there is no
     * object or if there are multiple objects wrapped.
     */
    @Override public String getValue( boolean propagate ) {
        List<Character> v = charListDomain.getValue( false );
        String s = charsToString( v );
        return s;
    }

    /**
     * Set the value of the object that is wrapped by this object.
     *
     * @param value the new value to be wrapped
     */
    @Override public void setValue( String value ) {
        restrictToValue( value );
    }

    /**
     * Return true if there is an object wrapped.  If getValue() returns null, this call distinguishes whether null is a
     * valid value or not.  If multiple objects are wrapped, then getValue() may return null also in this case.
     *
     * @return true if there is a wrapped value
     */
    @Override public boolean hasValue() {
        return charListDomain.hasValue();
    }

    /**
     * Return true if there is more than one object wrapped.  getValue() should
     * return null if this is true unless all of the objects are the same.
     *
     * @return true if there are multiple wrapped values
     */
    @Override public boolean hasMultipleValues() {
        return charListDomain.hasMultipleValues();
    }

    public static void main(String[] args) {
        RegexDomainString rds1 = new RegexDomainString( "hello" );
        RegexDomainString rds2 = new RegexDomainString( "hello" );
        boolean test1 = rds1.contains( "hello" );
        boolean test2 = rds2.contains( rds1 );
        Domain x = RegexDomain.intersect(rds1.charListDomain, rds2.charListDomain, null);
        System.out.println("test1 = " + test1 + "; test2 = " + test2 + "; intersect = " + x);
    }


    // ComparableDomain overrides

    protected StringDomain getStringDomain() {
        return new StringDomain( getLowerBound(), getUpperBound() );
    }

    @Override public boolean greater( String t1, String t2 ) {
        return stringDomain.greater( t1, t2 );
    }

    @Override public boolean less( String t1, String t2 ) {
        return stringDomain.less(t1, t2);
    }

    @Override public boolean equals( String t1, String t2 ) {
        return stringDomain.equals(t1, t2);
    }

    @Override public boolean greaterEquals( String t1, String t2 ) {
        return stringDomain.greaterEquals(t1, t2);
    }

    @Override public boolean lessEquals( String t1, String t2 ) {
        return stringDomain.lessEquals(t1, t2);
    }

    @Override public boolean notEquals( String t1, String t2 ) {
        return stringDomain.notEquals(t1, t2);
    }

    @Override public boolean less( String t1 ) {
        return getStringDomain().less(t1);
    }

    @Override public boolean lessEquals( String t1 ) {
        return getStringDomain().lessEquals(t1);
    }

    @Override public boolean greater( String t1 ) {
        return getStringDomain().greater(t1);
    }

    @Override public boolean greaterEquals( String t1 ) {
        return getStringDomain().greaterEquals(t1);
    }

    // TODO!  HERE!!!

    @Override public boolean less( ComparableDomain<String> t1 ) {
        int c = compareTo( t1 );
        return c < 0;
    }

    @Override public boolean lessEquals( ComparableDomain<String> t1 ) {
        int c = compareTo( t1 );
        return c <= 0;
    }

    @Override public boolean greater( ComparableDomain<String> t1 ) {
        int c = compareTo( t1 );
        return c > 0;
    }

    @Override public boolean greaterEquals( ComparableDomain<String> t1 ) {
        int c = compareTo( t1 );
        return c >= 0;
    }

    @Override public int compareTo( ComparableDomain<String> domain ) {
        return compareTo( (Domain<String>)domain );
    }

    protected static TreeSet<String> getBounds( Domain<?> d ) {
        // TODO -- define these in RegexDomain?
        TreeSet<String> set = new TreeSet<>();
        if ( d instanceof RegexDomain.SimpleDomain ) {
            set.add( "" + d.getValue( false ) );
            return set;
        }
        if ( d instanceof RegexDomain.ManyDomain ) {
            Domain dfe = ( (RegexDomain.ManyDomain)d ).domainForEach;
            set.addAll(getBounds( dfe ));
            TreeSet<String> more = new TreeSet<>();
            for ( String s1 : set ) {
                for ( String s2 : set ) {
                    more.add( s1 + s2 );
                }
            }
            set.clear();
            if ( !more.isEmpty() ) {
                set.add( more.first() );
                set.add( more.last() );
            }
            set.add("");  // 0 repeated case
            return set;
//            if  ( dfe instanceof RegexDomain.AnyDomain ) {
//                return new Pair<>( StringDomain.typeMaxValue;, null );
//            } else {
//
//                tempPiece1 = domainForEach
//            }
        }
        if ( d instanceof RegexDomain.AnyDomain ) {
            set.add( "" + Character.MAX_VALUE );
            set.add( "" + Character.MIN_VALUE);
            return set;
        }
        if ( d instanceof RegexDomain.OrDomain ) {
            List<Domain> seq = ( (RegexDomain.OrDomain)d ).seq;
            for ( Domain dd : seq ) {
                Set<String> s = getBounds( dd );
                set.addAll(s);
            }
            if ( !set.isEmpty() ) {
                String first = set.first();
                String last = set.last();
                set.clear();
                set.add(first);
                set.add(last);
            }
            return set;
        }
        if ( d instanceof RegexDomain ) {
            StringBuilder sb = new StringBuilder();
            List<Domain> seq = ( (RegexDomain)d ).seq;
            for ( Domain dd : seq ) {
                Set<String> dSet = getBounds( dd );
                if ( set.isEmpty() ) {
                    set.addAll(dSet);
                } else {
                    TreeSet<String> more = new TreeSet<>();
                    for ( String s1 : set ) {
                        for ( String s2 : dSet ) {
                            more.add(s1 + s2);
                        }
                    }
                    if ( !more.isEmpty() ) {
                        String first = more.first();
                        String last = more.last();
                        set.clear();
                        set.add(first);
                        set.add(last);
                    }
                }
            }
            return set;
        }
        if ( d instanceof RegexDomainString ) {
            return getBounds( ( (RegexDomainString)d ).charListDomain );
        }
        if ( d instanceof ComparableDomain ) {
            set.add("" + ( (ComparableDomain)d ).getUpperBound());
            set.add("" + ( (ComparableDomain)d ).getLowerBound());
            return set;
        }
        return set;
    }

    @Override public String getLowerBound() {
        TreeSet<String> set = getBounds( this );
        if ( !Utils.isNullOrEmpty( set ) ) {
            return set.first();
        }
        return null;
    }

    @Override public String getUpperBound() {
        TreeSet<String> set = getBounds( this );
        if ( !Utils.isNullOrEmpty( set ) ) {
            return set.last();
        }
        return null;
    }

    /**
     * @return whether the lower bound itself is considered part of the domain or
     * just values greater than the lower bound (that are within the upper
     * bound).
     */
    @Override public boolean isLowerBoundIncluded() {
        // REVIEW -- Is this right?
        return true;
    }

    /**
     * @return whether the upper bound itself is considered part of the domain or
     * just values less than the upper bound (that are within the lower
     * bound).
     */
    @Override public boolean isUpperBoundIncluded() {
        // REVIEW -- Is this right?
        return true;
    }

    /**
     * Compares this object with the specified object for order.  Returns a
     * negative integer, zero, or a positive integer as this object is less
     * than, equal to, or greater than the specified object.
     *
     * <p>The implementor must ensure <tt>sgn(x.compareTo(y)) ==
     * -sgn(y.compareTo(x))</tt> for all <tt>x</tt> and <tt>y</tt>.  (This
     * implies that <tt>x.compareTo(y)</tt> must throw an exception iff
     * <tt>y.compareTo(x)</tt> throws an exception.)
     *
     * <p>The implementor must also ensure that the relation is transitive:
     * <tt>(x.compareTo(y)&gt;0 &amp;&amp; y.compareTo(z)&gt;0)</tt> implies
     * <tt>x.compareTo(z)&gt;0</tt>.
     *
     * <p>Finally, the implementor must ensure that <tt>x.compareTo(y)==0</tt>
     * implies that <tt>sgn(x.compareTo(z)) == sgn(y.compareTo(z))</tt>, for
     * all <tt>z</tt>.
     *
     * <p>It is strongly recommended, but <i>not</i> strictly required that
     * <tt>(x.compareTo(y)==0) == (x.equals(y))</tt>.  Generally speaking, any
     * class that implements the <tt>Comparable</tt> interface and violates
     * this condition should clearly indicate this fact.  The recommended
     * language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     *
     * <p>In the foregoing description, the notation
     * <tt>sgn(</tt><i>expression</i><tt>)</tt> designates the mathematical
     * <i>signum</i> function, which is defined to return one of <tt>-1</tt>,
     * <tt>0</tt>, or <tt>1</tt> according to whether the value of
     * <i>expression</i> is negative, zero or positive.
     *
     * @param obj the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it
     *                              from being compared to this object.
     */
    @Override public int compareTo( Domain<String> obj ) {
        if ( obj == null ) {
            throw new NullPointerException( "Error! Cannot compare against a null.");
        }
        if ( this == obj ) return 0;

        if ( obj instanceof RegexDomainString ) {
            return CompareUtils.compare(charListDomain, ( (RegexDomainString)obj ).charListDomain );
        }

        if ( obj instanceof RegexDomain.OrDomain ) {
            // TODO???
        }
        if ( obj instanceof RegexDomain ) {
            // TODO???
        }

        if ( charListDomain.seq.size() == 1 ) {
            return -1 * CompareUtils.compare( obj, charListDomain.seq.get(0));
        }

        if ( obj instanceof ComparableDomain ) {
            ComparableDomain<?> r = (ComparableDomain<?>)obj;
            StringDomain sd = new StringDomain(getLowerBound(), getUpperBound());
            int comp = sd.compare( r );
            return comp;
        }
        int comp = CompareUtils.compare( this, obj );
        return comp;
    }

    @Override public String toString() {
        return charListDomain.toString();
    }
}
