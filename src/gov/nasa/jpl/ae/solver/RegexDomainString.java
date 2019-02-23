package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.mbee.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class RegexDomainString implements Domain<String> {
    RegexDomain<Character> charListDomain;

    public RegexDomainString() {
        charListDomain = new RegexDomain<>();
    }

    public RegexDomainString(String literal) {
        charListDomain = new RegexDomain<>();
        charListDomain.seq = toCharDomains( literal );
    }

    @Override public Domain<String> clone() {
        return null;
    }

    /**
     * @return the number of elements in the domain. If the domain is infinite,
     * Long.MAX_VALUE (which is to be interpreted as infinity).<p>
     */
    @Override public long magnitude() {
        return 0;
    }

    /**
     * @return whether the domain contains no values (including null)
     */
    @Override public boolean isEmpty() {
        return false;
    }

    /**
     * Make the domain empty, containing no values, not even null.
     *
     * @return whether any domain changed as a result of this call
     */
    @Override public boolean clearValues() {
        return false;
    }

    public boolean contains( String characters ) {
        List<Character> charList =toChars( characters );
        return super.contains( charList );
    }

    @Override public String pickRandomValue() {
        return null;
    }

    @Override public String pickRandomValueNotEqual( String s ) {
        return null;
    }

    @Override public boolean isInfinite() {
        return false;
    }

    @Override public boolean isNullInDomain() {
        return false;
    }

    @Override public boolean setNullInDomain( boolean b ) {
        return false;
    }

    @Override public Domain<String> getDefaultDomain() {
        return null;
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
        return false;
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
        return false;
    }

    /**
     * Exclude elements in the input domain from this domain.
     *
     * @param domain
     * @return whether the domain of any object changed as a result of this call
     */
    @Override public <TT> Domain<TT> subtract( Domain<TT> domain ) {
        return null;
    }

    protected List<Domain<Character>> toCharDomains( String characters ) {
        List<Character> charList = toChars( characters );
        List<Domain<Character>> domains = new ArrayList<>(characters.length());
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
        return null;
    }

    @Override public Integer getId() {
        return null;
    }

    /**
     * @return the type of the object that this object wraps
     */
    @Override public Class<?> getType() {
        return null;
    }

    /**
     * @param className the name of {@code this} class (which should be the class
     *                  redefining this method or a subclass) with generic parameters
     * @return the name of the type for the object that would be wrapped by an
     * object with a class name of {@code className}; this should be the
     * type of the return value for {@link Wraps<V>.getValue(boolean)}.
     */
    @Override public String getTypeNameForClassName( String className ) {
        return null;
    }

    /**
     * @return the primitive class corresponding to the object wrapped by this
     * object (possibly in several layers)
     */
    @Override public Class<?> getPrimitiveType() {
        return null;
    }

    /**
     * @param propagate whether or not to propagate dependencies in order to determine
     *                  what object is wrapped
     * @return the object that is wrapped by this object or null if there is no
     * object or if there are multiple objects wrapped.
     */
    @Override public String getValue( boolean propagate ) {
        return null;
    }

    /**
     * Set the value of the object that is wrapped by this object.
     *
     * @param value the new value to be wrapped
     */
    @Override public void setValue( String value ) {

    }

    /**
     * Return true if there is an object wrapped.  If getValue() returns null, this call distinguishes whether null is a
     * valid value or not.  If multiple objects are wrapped, then getValue() may return null also in this case.
     *
     * @return true if there is a wrapped value
     */
    @Override public boolean hasValue() {
        return false;
    }

    /**
     * Return true if there is more than one object wrapped.  getValue() should
     * return null if this is true unless all of the objects are the same.
     *
     * @return true if there are multiple wrapped values
     */
    @Override public boolean hasMultipleValues() {
        return false;
    }
}
