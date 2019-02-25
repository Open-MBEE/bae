package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.ae.util.Math;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.Wraps;

import java.util.ArrayList;
import java.util.List;

public class RegexDomainString implements Domain<String> {
    public RegexDomain<Character> charListDomain;

    public RegexDomainString() {
        charListDomain = new RegexDomain<>();
    }

    public RegexDomainString(String literal) {
        charListDomain = new RegexDomain<>();
        charListDomain.seq = toCharDomains( literal );
    }
    public RegexDomainString(Domain domain) {
        if ( domain == null ) return;
        if ( domain instanceof RegexDomainString ) {
            charListDomain = ((RegexDomainString)domain).charListDomain.clone();
        }
        if ( domain instanceof RegexDomain ) {
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
        return StringDomain.defaultDomain;
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
        return charListDomain.restrictTo( domain );
    }

    /**
     * Exclude elements in the input domain from this domain.
     *
     * @param domain
     * @return whether the domain of any object changed as a result of this call
     */
    @Override public <TT> Domain<TT> subtract( Domain<TT> domain ) {
        return charListDomain.subtract( domain );
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
        System.out.println("test1 = " + test1 + "; test2 = " + test2);
    }
}
