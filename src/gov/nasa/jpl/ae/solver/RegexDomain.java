package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.ae.event.ConstraintExpression;
import gov.nasa.jpl.ae.event.Functions;
import gov.nasa.jpl.ae.event.Parameter;
import gov.nasa.jpl.ae.event.StringParameter;
import gov.nasa.jpl.mbee.util.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 * Nests literals, concatenation, alternation, repitition.
 * Other non-necessary things that we don't include: begin, end, multiplicity, groups
 * @param <T>
 */
public class RegexDomain<T> implements Domain<List<T>> {
    public static class SimpleDomain<V> extends ObjectDomain<V> {
        public SimpleDomain() {
            super( (Class<V>)null );
        }
        public SimpleDomain(V v) {
            super( (Class<V>)null );
            add(v);
        }
    }

    protected static class SimpleWrap<V> implements Wraps<V> {
        V v = null;
        boolean isNull = false;
        public SimpleWrap() {
        }
        public SimpleWrap( V v ) {
            setValue( v );
        }
        @Override public Class<?> getType() {
            return null;
        }
        @Override public String getTypeNameForClassName( String className ) {
            return null;
        }
        @Override public Class<?> getPrimitiveType() {
            return null;
        }
        @Override public V getValue( boolean propagate ) {
            return v;
        }
        @Override public void setValue( V value ) {
            v = value;
            isNull = value == null;
        }
        @Override public boolean hasValue() {
            return v != null || isNull;
        }
        @Override public boolean hasMultipleValues() {
            return false;
        }
    }

    protected static class AnyDomain<V> extends ClassDomain<V> {
        public AnyDomain( Class<V> cls) {
            super( cls );
        }
    }

    protected static class AnyDomainLong extends HasIdImpl implements Domain<Object> {
        static final AnyDomain instance = new AnyDomain();

        @Override public Domain<Object> clone() {
            return new AnyDomain();
        }
        @Override public long magnitude() {
            return Long.MAX_VALUE;
        }
        @Override public boolean isEmpty() {
            return false;
        }
        @Override public boolean clearValues() {
            // TODO -- Warning?  Error?
            return false;
        }
        @Override public boolean contains( Object o ) {
            return true;
        }
        @Override public Object pickRandomValue() {
            return new Object();
        }
        @Override public Object pickRandomValueNotEqual( Object o ) {
            return pickRandomValue();
        }
        @Override public boolean isInfinite() {
            return true;
        }
        @Override public boolean isNullInDomain() {
            return true;
        }
        @Override public boolean setNullInDomain( boolean b ) {
            return false;
        }
        @Override public Domain<Object> getDefaultDomain() {
            return instance;
        }
        @Override public boolean restrictToValue( Object v ) {
            // TODO -- Warning?  Error?
            return false;
        }
        @Override public <TT> boolean restrictTo( Domain<TT> domain ) {
            // TODO -- Warning?  Error?
            return false;
        }
        @Override public <TT> Domain<TT> subtract( Domain<TT> domain ) {
            // TODO -- Warning?  Error?
            return null;
        }
        @Override public <T> T evaluate( Class<T> cls, boolean propagate ) {
            // TODO -- Warning?  Error?
            return null;
        }
        @Override public Class<?> getType() {
            return Object.class;
        }
        @Override public String getTypeNameForClassName( String className ) {
            return "Object";
        }
        @Override public Class<?> getPrimitiveType() {
            return null;
        }
        @Override public Object getValue( boolean propagate ) {
            // TODO -- Warning?  Error?
            return null;
        }
        @Override public void setValue( Object value ) {
            // TODO -- Warning?  Error?
        }
        @Override public boolean hasValue() {
            return false;
        }
        @Override public boolean hasMultipleValues() {
            return false;
        }
    }

    protected static class ManyDomain<V> extends HasIdImpl implements Domain<List<V>> {
        public static final ManyDomain<Object> defaultDomain = new ManyDomain<Object>(Object.class);
        IntegerDomain multiplicity = new IntegerDomain( 0,Integer.MAX_VALUE );
        Domain<V> domainForEach = null;
        public ManyDomain() {
            super();
            domainForEach = new AnyDomain(Object.class);
        }
        public ManyDomain(Domain domainForEach) {
            super();
            this.domainForEach = domainForEach;
        }
//        public ManyDomain(Class<V> cls) {
//            super();
//            domainForEach = new AnyDomain(cls);
//        }
//        public ManyDomain(Class<V> cls, int minNumber, int maxNumber) {
//            super();
//            multiplicity.setBounds( minNumber, maxNumber );
//            domainForEach = any;
//        }
        public ManyDomain(ManyDomain<V> d) {
            super();
            multiplicity = (IntegerDomain)d.multiplicity.clone();
            domainForEach = d.domainForEach.clone();
        }
        @Override public Domain<List<V>> clone() {
            return new ManyDomain<V>( this );
        }
        @Override public long magnitude() {
            if ( multiplicity.magnitude() >= Long.MAX_VALUE ) return Long.MAX_VALUE;
            if ( domainForEach == null ) {
                return multiplicity.magnitude();
            }
            if ( domainForEach.magnitude() >= Long.MAX_VALUE ) return Long.MAX_VALUE;
            Long result = null;
            try {
                result = Functions.pow( domainForEach.magnitude(),
                                        multiplicity.magnitude() );
            } catch ( Throwable e ) {
            }
            if ( result == null ) return 1;
            return result;
        }
        @Override public boolean isEmpty() {
            return multiplicity.magnitude() == 0 ||
                   (domainForEach != null && domainForEach.magnitude() == 0 );
        }
        @Override public boolean clearValues() {
            if ( multiplicity.magnitude() > 0 ) {
                multiplicity.clearValues();
                return true;
            }
            return false;
        }
        @Override public boolean contains( List<V> vs ) {
            if ( !multiplicity.contains( vs.size() ) ) return false;
            if ( domainForEach != null ) {
                for ( V v : vs ) {
                    if ( !domainForEach.contains( v ) ) {
                        return false;
                    }
                }
            }
            return true;
        }
        @Override public List<V> pickRandomValue() {
            long len = multiplicity.pickRandomValue();  // This isn't uniformly random.
            List<V> list = new ArrayList<V>();
            if ( domainForEach == null ) {
                // TODO -- error or warning?
            } else {
                for ( int i = 0; i < len; ++i ) {
                    list.add( domainForEach.pickRandomValue() );
                }
            }
            return list;
        }
        @Override public List<V> pickRandomValueNotEqual( List<V> vs ) {
            List<V> vList = pickRandomValue();
            if ( CompareUtils.compare( vs, vList ) == 0 ) {
                vList = pickRandomValue();
                if ( CompareUtils.compare( vs, vList ) == 0 ) {
                    return null;
                }
            }
            return vList;
        }
        @Override public boolean isInfinite() {
            long m = magnitude();
            if ( m >= Long.MAX_VALUE ) {
                return true;
            }
            return false;
        }
        @Override public boolean isNullInDomain() {
            return false;
        }
        @Override public boolean setNullInDomain( boolean b ) {
            Debug.error(true, false,
                        "setNullInDomain() not supported in " +
                        this.getClass().getSimpleName());
            return false;
        }
        @Override public ManyDomain<V> getDefaultDomain() {
            return (ManyDomain<V>)defaultDomain;
        }
        @Override public boolean restrictToValue( List<V> v ) {
            Debug.error(true, false,
                        "restrictToValue() not supported in " +
                        this.getClass().getSimpleName());
            return false;
        }
        @Override public <TT> boolean restrictTo( Domain<TT> domain ) {
            Debug.error(true, false,
                        "restrictTo() not supported in " +
                        this.getClass().getSimpleName());
            return false;
        }
        @Override public <TT> Domain<TT> subtract( Domain<TT> domain ) {
            Debug.error(true, false,
                        "subtract() not supported in " +
                        this.getClass().getSimpleName());
            return null;
        }
        @Override public <T> T evaluate( Class<T> cls, boolean propagate ) {
            T t = Evaluatable.Helper.evaluate( this, cls, false, false );
            return t;
        }

        protected static boolean isListType = false;

        /**
         * @return the type of the object that this object wraps
         */
        @Override public Class<?> getType() {
            if ( isListType ) {
                return List.class;
            }
            if ( domainForEach != null ) {
                return domainForEach.getType();
            }
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
            if ( isListType ) {
                // ???
            }
            if ( domainForEach != null ) {
                return domainForEach.getTypeNameForClassName(className);
            }
            return null;
        }

        /**
         * @return the primitive class corresponding to the object wrapped by this
         * object (possibly in several layers)
         */
        @Override public Class<?> getPrimitiveType() {
            if ( isListType ) {
                return null;
            }
            if ( domainForEach != null ) {
                return domainForEach.getPrimitiveType();
            }
            return null;
        }

        /**
         * @param propagate whether or not to propagate dependencies in order to determine
         *                  what object is wrapped
         * @return the object that is wrapped by this object or null if there is no
         * object or if there are multiple objects wrapped.
         */
        @Override public List<V> getValue( boolean propagate ) {
            if ( multiplicity.magnitude() == 1 && domainForEach != null &&
                 domainForEach.magnitude() == 1 ) {
                List<V> list = new ArrayList<>();
                return domainForEach.getValue();
            }
            return null;
        }

        /**
         * Set the value of the object that is wrapped by this object.
         *
         * @param value the new value to be wrapped
         */
        @Override public void setValue( List<V> value ) {

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

    //
    static final SimpleDomain begin = new SimpleDomain();
    static final SimpleDomain end = new SimpleDomain();
    static final SimpleDomain any = new AnyDomain();
    static final ObjectDomain begin = new ObjectDomain((Object)null);
    static final SimpleWrap end = new SimpleWrap();

    protected static class OrDomain<V> extends LinkedHashSet<Domain<V>>
            implements Domain<V>{
        /**
         * @return the number of elements in the domain. If the domain is infinite,
         * Long.MAX_VALUE (which is to be interpreted as infinity).<p>
         */
        @Override public long magnitude() {
            return 0;
        }

        /**
         * Make the domain empty, containing no values, not even null.
         *
         * @return whether any domain changed as a result of this call
         */
        @Override public boolean clearValues() {
            return false;
        }

        @Override public V pickRandomValue() {
            return null;
        }

        @Override public V pickRandomValueNotEqual( V v ) {
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

        @Override public Domain<V> getDefaultDomain() {
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
        @Override public boolean restrictToValue( V v ) {
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
        @Override public V getValue( boolean propagate ) {
            return null;
        }

        /**
         * Set the value of the object that is wrapped by this object.
         *
         * @param value the new value to be wrapped
         */
        @Override public void setValue( V value ) {

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
        //        public OrDomain( ObjectDomain<V> objectDomain ) {
//            super( objectDomain );
//        }
//        public OrDomain( Collection<V> objects ) {
//            super( objects );
//        }
    }

    List<Domain<T>> seq = new ArrayList<>();


    @Override public Domain<List<T>> clone() {
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

    @Override public boolean contains( List<T> ts ) {
        return false;
    }

    @Override public List<T> pickRandomValue() {
        return null;
    }

    @Override public List<T> pickRandomValueNotEqual( List<T> ts ) {
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

    @Override public Domain<List<T>> getDefaultDomain() {
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
    @Override public boolean restrictToValue( List<T> v ) {
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

    //

    // Image functions (given domains, compute output domain):
    //    Concatenate(RDS1, RDS2) = RDS(RDS1.seq, RDS2.seq)
    //        Concatenate the elements in each domain's seq
    //
    //    MinusPrefix(RDS_result, RDS_prefix) /* check parameter ordering */   // MinusPrefix("abc", "a") = "bc"
    //        Basically, match RDS_prefix against RDS_result, and return an alternation (OrDomain) of the suffix resulting by subtracting each prefix. Pseudocode:
    //
    //        // I'm calling SimpleDomain, OrDomain, or ManyDomain a "RegexDomainElement"
    //
    //        if (RDS_prefix is empty) {
    //            return RDS_result;
    //        }
    //
    //        RegexDomainElement result_elem = first elem of RDS_result;
    //        RegexDomainElement prefix_elem = first elem of RDS_prefix;
    //
    //        switch (type_of(prefix_elem)) {
    //            case Literal:
    //                switch (type_of(result_elem)) {
    //                    case Literal:
    //                        if (prefix_elem == result_elem) {
    //                            collect MinusPrefix(tail of RDS_result, tail of RDS_prefix);
    //                        }
    //                    case Alternation:    (x | xy | xyz) ~ "xyfoo"
    //                        if (result_elem.contains(prefix_elem)) {
    //                            collect MinusPrefix(tail of RDS_result, tail of RDS_prefix);
    //                        }
    //                    case KleeneStar:   "x*x" ~ "xxx"
    //                        // can either match or not match prefix
    //                        collect MinusPrefix(tail of RDS_result, RDS_prefix);
    //                        if (result_elem matches prefix_elem) {
    //                            also collect MinusPrefix(RDS_result, tail of RDS_prefix);
    //                            // note that this captures the case when RDS_prefix is the last character matched, when the next level has a branch that doesn't match the KleeneStar against prefix
    //                        }
    //                }
    //
    //            case Alternation:
    //                switch (type_of(result_elem)) {
    //                    case Literal:
    //                        // symmetric with above
    //                    case Alternation:
    //                        if (prefix_elem intersects result_elem) {
    //                            collect MinusPrefix(tail of RDS_result, tail of RDS_prefix);
    //                        }
    //                    case KleeneStar:
    //                        // can either match or not match prefix
    //                        collect MinusPrefix(tail of RDS_result, RDS_prefix);
    //                        if (result_elem matches any in prefix_elem)) {
    //                            also collect MinusPrefix(RDS_result, tail of RDS_prefix);
    //                        }
    //                }
    //
    //            case KleeneStar:
    //                // all cases are symmetric with one above
    //                switch (type_of(result_elem)) {
    //                    case Literal:
    //                    case Alternation:
    //                    case KleeneStar:
    //                }
    //        }
    public <TT> Domain<TT> minusPrefix( Domain<TT> domain ) {
        return null;
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
     * type of the return value for {@link Wraps<T>.getValue(boolean)}.
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
    @Override public List<T> getValue( boolean propagate ) {
        return null;
    }

    /**
     * Set the value of the object that is wrapped by this object.
     *
     * @param value the new value to be wrapped
     */
    @Override public void setValue( List<T> value ) {

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

    public static void main( String args[] ) {
        RegexDomain<Integer> rd = new RegexDomain<>();
        //rd.seq.add(begin);
        rd.seq.add(new SimpleDomain(3));
//        rd.seq.add(new AnyDomain(Integer.class));
//        rd.seq.add(new AnyDomain(Integer.class));
        rd.seq.add(new ManyDomain());
        rd.seq.add(new SimpleDomain(2));
        //rd.seq.add(end);

        ArrayList<Integer> intList1 = Utils.newList(3, 3, 4, 2 );
        ArrayList<Integer> intList2 = Utils.newList(3, 3, 4, 2, 1 );

        assertTrue( rd.contains( intList1 ));
        assertTrue( !rd.contains( intList2 ));

        rd.seq.add( new ManyDomain() );
        assertTrue( rd.contains( intList2 ) );

        RegexDomainString rds1 = new RegexDomainString();
        rds1.charListDomain.seq.add(new SimpleDomain('3'));
        rds1.charListDomain.seq.add(new ManyDomain());
        rds1.charListDomain.seq.add(new SimpleDomain('2'));

        RegexDomainString rds2 = new RegexDomainString();
        rds2.charListDomain.seq.add(new SimpleDomain('3'));
        rds2.charListDomain.seq.add(new ManyDomain());
        rds2.charListDomain.seq.add(new SimpleDomain('2'));

        Parameter<String> p1 = new StringParameter("p1", rds1, null);
        Parameter<String> p2 = new StringParameter("p2", rds2, null);

        Parameter<String> p3 = new StringParameter("p3", (Domain)null, null);)

        ConstraintExpression eq;

        eq.restrictDomain( new BooleanDomain( true, true ), false, null );

        Domain d = eq.getDomain( true, null );
        System.out.println("" + d);

//        RegexDomainString rds3 = new RegexDomainString();
//        rds3.seq.add(rds1);
//        rds3.seq.add(rds2);
//
//
//        String s1 = "3342";
//        String s2 = "33421";
//
//        assertTrue( rds.contains( s1 ));
//        assertTrue( !rds.contains( s2 ));
//
//        String s2 = "33.*2";

        // var u: String  = "foo bar monkey got hungry"
        // req u = "foo " + x + " monkey " + t + " hungry"
        // c = eq(u,add("foo ", add( x, add( " monkey ", add( t, add( " hungry" ) ) ) )
        //   eq.restrictDomain(true, u, add())
        //    add("foo ",...).restrictDomain( RDS("foo bar monkey got hungry"), "foo ", add(x,...))
        //      Domain d2 =  add("foo ",...).inverseDomain( RDS("foo bar monkey got hungry"), add(x,...) );
        //        d2 = MinusPrefix(RDS("foo bar monkey got hungry"), RDS("foo"));
        //          d2 = RDS("foo bar monkey got hungry").minusPrefix( RDS("foo") ) = RDS("bar monkey got hungry")
        //             = RDS("foo bar monkey got hungry").?????( RDS("foo") ) = RDS("bar monkey got hungry")
        //      p = add(x,..).restrictDomain( d2, propagate, seen );
        //


        //  add().restrictDomain("foo .* monkey got hungry", "foo bar monkey .* hungry"
    }

    // var x : Int
    // var y : Int
    // var z : Int
    // req x in [1..5]
    // req y in [1..5]
    // req z in [7..9]
    // req x + y = z  // <-- restrict to {true}

    // restrict x + y's domain to z's domain [7..9]
    // how does this restrict x?
    // restrict x to [7..9] - [1..5]
    // x is retricted to [2..8]~[1..5] = [2..5]
    // how does this restrict y?
    // restrict y to [7..9] - [1..5]
    // y is retricted to [2..8]~[1..5] = [2..5]

    // restrict z's domain to x+y's domain = [4..10]
    // restrict z' domain to [7..9]










}

