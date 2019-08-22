package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.ae.event.ConstraintExpression;
import gov.nasa.jpl.ae.event.Functions;
import gov.nasa.jpl.ae.event.Parameter;
import gov.nasa.jpl.ae.event.StringParameter;
import gov.nasa.jpl.ae.util.Math;
import gov.nasa.jpl.mbee.util.*;
import org.omg.CORBA.Any;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static javax.swing.text.html.HTML.Tag.TT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Nests literals, concatenation, alternation, repitition.
 * Other non-necessary things that we don't include: begin, end, multiplicity, groups
 * @param <T>
 */
public class RegexDomain<T> extends HasIdImpl implements Domain<List<T>>, Simplifiable {
    public static class SimpleDomain<V> extends ObjectDomain<V> {
        public SimpleDomain() {
            super( (Class<V>)null );
        }
        public SimpleDomain(V v) {
            super( (Class<V>)null );
            add(v);
        }
        public SimpleDomain(ObjectDomain v) {
            super( v );
        }

        @Override public ObjectDomain<V> clone() {
            return new SimpleDomain<V>( this );
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
        @Override public String toString() {
            return "" + getValue( false );
        }
    }

    protected static class AnyDomain<V> extends ClassDomain<V> {
        public AnyDomain( Class<V> cls) {
            super( cls );
        }

        @Override public AnyDomain<V> clone() {
            return new AnyDomain(this.type);
        }

        @Override public String toString() {
            return ".";
        }
    }

    protected static class ManyDomain<V> extends HasIdImpl implements Domain<List<V>>, Simplifiable {
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
        public ManyDomain(Class<V> cls) {
            super();
            domainForEach = new AnyDomain(cls);
        }
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
        @Override public ManyDomain<V> clone() {
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
            long len = multiplicity.pickRandomValue();  // This isn't random.
            long delta = len - multiplicity.lowerBound;
            len = multiplicity.getLowerBound() + (delta % 4);
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
                if ( multiplicity.lowerBound > 10000 ) {
                    Debug.error(true, true,
                                "Trying to create too many objects for " +
                                getClass().getSimpleName() + ".getValue()");
                    return null;
                }
                V obj = domainForEach.getValue( propagate );
                for ( int i = 0; i < multiplicity.lowerBound; ++i ) {
                    list.add(obj);
                }
                return list;
            }
            // multiple possible values, so return null;
            return null;
        }

        /**
         * Set the value of the object that is wrapped by this object.
         *
         * @param value the new value to be wrapped
         */
        @Override public void setValue( List<V> value ) {
            Debug.error(true, true,
                        "setValue() not supported for " +
                        getClass().getSimpleName());
        }

        /**
         * Return true if there is an object wrapped.  If getValue() returns null, this call distinguishes whether null is a
         * valid value or not.  If multiple objects are wrapped, then getValue() may return null also in this case.
         *
         * @return true if there is a wrapped value
         */
        @Override public boolean hasValue() {
            return multiplicity.magnitude() > 0 &&
                   ( domainForEach == null || domainForEach.hasValue() );
        }

        /**
         * Return true if there is more than one object wrapped.  getValue() should
         * return null if this is true unless all of the objects are the same.
         *
         * @return true if there are multiple wrapped values
         */
        @Override public boolean hasMultipleValues() {
            return multiplicity.magnitude() > 1 ||
                   ( domainForEach == null || domainForEach.hasMultipleValues() );
        }

        /**
         * <ol>
         * <li><code>
         *    (()|a)* -> a*
         * </code></li>
         * <li><code>
         *     a** -> a*
         * </code></li>
         * </ol>
         *
         * @return whether the representation changed
         */
        public boolean simplifyForPatterns() {
            // a** -> a*
            if (domainForEach instanceof ManyDomain ) {
                domainForEach = ( (ManyDomain<V>)domainForEach ).domainForEach;
                return true;
            }

            // (()|a)* -> a*
            if (domainForEach instanceof OrDomain ) {
                OrDomain<?> od = (OrDomain)domainForEach;
                if (od.seq.size() == 2 && od.hasEmptyList()) {
                    Domain first = od.seq.get( 0 );
                    Domain second = od.seq.get( 1 );
                    if ( isEmptyList(first) ) {
                        domainForEach = second;
                        return true;
                    }
                    if ( isEmptyList(second) ) {
                        domainForEach = first;
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Attempt to simplify.
         *
         * @param deep whether to recursively simplify contained objects
         * @param seen a list of objects already visited to avoid infinite recursion
         * @return whether or not the object changed as a result of simplification
         */
        @Override public boolean simplify( boolean deep,
                                           Set<Simplifiable> seen ) {
            return RegexDomain.simplify(this, deep, seen );
        }

        /**
         *
         * @param deep whether to recursively simplify contained objects
         * @param seen a list of objects already visited to avoid infinite recursion
         * @return
         */
        @Override public boolean simplifyOnePass( boolean deep, Set<Simplifiable> seen ) {
            boolean changedDomainForEach = false;
            boolean changedForPatterns = false;
            if ( deep ) {
                if ( domainForEach instanceof Simplifiable ) {
                    changedDomainForEach = ( (Simplifiable)domainForEach ).simplify( deep, seen );
                }
            }
            System.out.println( "before simplifyForPatterns(): " + this );
            changedForPatterns = simplifyForPatterns();
            System.out.println( " after simplifyForPatterns(): " + this );
            return changedDomainForEach || changedForPatterns;
        }

        @Override public String toString() {
            String dfes = domainForEach.toString();
            if ( dfes.length() == 1 ) {
                return dfes + "*";
            }
            return "(" + dfes + ")*";
        }


    }

//    static final SimpleDomain begin = new SimpleDomain();
//    static final SimpleDomain end = new SimpleDomain();
//    static final SimpleDomain any = new AnyDomain();
//    static final ObjectDomain begin = new ObjectDomain((Object)null);
//    static final SimpleWrap end = new SimpleWrap();

    public static class OrDomain<V> extends RegexDomain<V> {
        public OrDomain() {
            super();
        }

        public OrDomain( List<Domain<?>> seq ) {
            super( seq );
        }

        public OrDomain( OrDomain<V> rd ) {
            super( rd );
        }

        @Override public OrDomain<V> clone() {
            return new OrDomain< V >( this );
        }

        @Override public long magnitude() {
            long num = 0;
            for ( Domain<?> d : seq ) {
                num = Math.plus( num, d.magnitude() );
                if ( Math.isInfinity( num ) ) {
                    return num;
                }
                if ( num < 0 ) {
                    Debug.error(true, true,
                                getClass().getSimpleName() +
                                ".magnitude() is negative! Returning 0.");
                    return 0L;
                }
            }
            return num;
        }

        // TODO -- redefine minusPrefix()?

        @Override public boolean hasEmptyList() {
            if ( seq == null ) {
                return true;
            }
            // Just one has to have an empty list for OrDomain.
            for ( Domain<?> d : seq ) {
                if ( hasEmptyList( d ) ) {
                    return true;
                }
            }
            return false;
        }

        /**
         * @return whether the domain contains no values (including null)
         */
        @Override public boolean isEmpty() {
            // An empty list is not an empty domain.
            if ( seq.size() == 0 ) return true;


            for ( Domain d : seq ) {
                if ( !d.isEmpty() ) {
                    return false;
                }
            }
            return true;
        }


        // TODO -- override toValueList() and hasRegex()???

        @Override public OrDomain<V> reverse() {
            RegexDomain<V> rdr = super.reverse();
            if ( rdr == null ) return null;  // TODO -- ERROR?
            if ( rdr instanceof OrDomain ) {
                return (OrDomain<V>)rdr;
            }
            Debug.error(true, true,
                        "OrDomain.reverse(): expected OrDomain as result! Got " +
                        rdr + ".  Returning original OrDomain without reversing." );
            return this;
        }

        @Override public boolean restrictToValue( List<V> v ) {
            boolean changed = false;
            for ( Domain d : seq ) {
                if ( d.restrictToValue( v ) ) {
                    changed = true;
                }
            }
            return changed;
        }

        @Override public boolean contains( List<V> vs ) {
            for ( Domain alt : this.seq ) {
                if ( alt.contains( vs ) ) {
                    return true;
                }
            }
            return false;
        }

        @Override public String toString() {
            String s = "(" + MoreToString.Helper.toString(seq,false,true,null,
                                                    null,"","|","",
                                                    true) + ")";
            return s;
        }

        /**
         * Transformation patterns:
         * <p>
         * <ol>
         * <li><code>
         *     a|a -> a
         * </code></li>
         * <li><code>
         *     a|a* -> a*
         * </code></li>
         * <li><code>
         *     a|.* -> .*
         * </code></li>
         * <li><code>
         *     x|. -> .
         * </code></li>
         * <li><code>
         *     aa*|a* -> a*
         * </code></li>
         * <li><code>
         *    ()|aa* -> a*
         * </code></li>
         * </ol>
         * </p>
         *
         * @return whether the representation changed
         */
        @Override public boolean simplifyForPatterns() {
            /**
             *  Patterns without the html tags:
             * a|a -> a
             * a|a* -> a*
             * a|.* -> .*
             * x|. -> .
             * aa*|a* -> a*
             * ()|aa* -> a*
             */

            // All rules assume at least two distinct elements.
            if ( seq.size() <= 1 ) {
                return false;
            }
            // a|.* -> .*
            Domain md = null;
            for ( Domain d : seq ) {
                if ( isManyAny( d ) ) {
                    md = d;
                    break;
                }
            }
            if ( md != null ) {
                seq.clear();
                seq.add( md );
                return true;
            }

            boolean changed = false;

            // a|a -> a
            TreeSet<Domain> set = new TreeSet<Domain>( comparator );
            set.addAll( seq );
            if ( set.size() < seq.size() ) {
                seq = new ArrayList<>( (Collection)set );
                changed = true;
            }

            // All rules assume at least two distinct elements.
            if ( seq.size() <= 1 ) {
                return changed;
            }

            boolean changedThisTime = false;

            // a|a* -> a*
            // Remove any domain which is also in a ManyDomain
            for ( Domain d : seq ) {
                if ( d instanceof ManyDomain ) {
                    Domain<?> dfe = ( (ManyDomain)d ).domainForEach;
                    // We know there are no duplicates after the last pattern
                    // transformation, so there's at most one to remove.
                    boolean changedThis = set.remove( dfe );
                    if ( changedThis ) {
                        changedThisTime = true;
                        changed = true;
                    }
                }
            }
            if ( changedThisTime ) {
                seq = new ArrayList<>( (Collection)set );
                changed = true;
                changedThisTime = false;
            }

            // x|. -> .
            boolean gotDot = false;
            for ( Domain d : seq ) {  // TODO -- searching
                if ( d instanceof AnyDomain ) {
                    gotDot = true;
                    break;
                }
            }
            if (gotDot) {
                // Remove any single elements that are a subset of .
                for ( Domain d : seq ) {
                    if ( d instanceof SimpleDomain ) {
                        set.remove( d );
                        changedThisTime = true;
                    }
                }
                if ( changedThisTime ) {
                    seq = new ArrayList<>( (Collection)set );
                    changed = true;
                    changedThisTime = false;
                }
            }

            /**
             * TODO -- implement remaining transformation patterns below.
             * aa*|a* -> a*
             * ()|aa* -> a*
             */

            return changed;
        }
    }

    public static Comparator comparator = new CompareUtils.GenericComparator() {
        @Override public int compare( Object o1, Object o2 ) {
            if ( o1 == o2 ) return 0;
            if ( o1 == null ) return -1;
            if ( o2 == null ) return 1;
            String s1 = o1.toString();
            String s2 = o2.toString();
            int comp = s1.compareTo( s2 );
            if ( comp == 0 && o1.getClass().equals( o2.getClass() ) ) {
                return 0;
            }
            if ( comp != 0 ) {
                return comp;
            }

            // comp must be 0 and classes must be different
            if ( o1 instanceof AnyDomain ) return -1;   // if o2 is a RegexDomain, then Or < And
            if ( o2 instanceof AnyDomain ) return 1;    // if o1 is a RegexDomain, then And > Or

            if ( o1 instanceof OrDomain ) return -1;   // if o2 is a RegexDomain, then Or < And
            if ( o2 instanceof OrDomain ) return 1;    // if o1 is a RegexDomain, then And > Or

            if ( o1 instanceof RegexDomain && o2 instanceof RegexDomain ) {
                comp = CompareUtils.compare( ( (RegexDomain)o1 ).seq, ( (RegexDomain)o2 ).seq );
                if ( comp != 0 ) return comp;
            }
            return CompareUtils.compare(o1, o2, true, false);
        }
    };

    public List<Domain<?>> seq = new ArrayList<Domain<?>>();

    public RegexDomain() {
    }

    public RegexDomain(List<Domain<?>> seq) {
        this.seq = seq;
    }

    public RegexDomain( RegexDomain<T> rd ) {
        for ( Domain<?> d : rd.seq ) {
            this.seq.add(d.clone());
        }
    }

    @Override public RegexDomain<T> clone() {
        return new RegexDomain<T>(this);
    }

    /**
     * @return the number of elements in the domain. If the domain is infinite,
     * Long.MAX_VALUE (which is to be interpreted as infinity).<p>
     */
    @Override public long magnitude() {
        long num = 1;  // 1 instead of 0 since an empty seq matches an empty list
        for ( Domain<?> d : seq ) {
            num = Math.times( num, d.magnitude() );
            if (num == 0 || Math.isInfinity( num ) ) {
                return num;
            }
            if ( num < 0 ) {
                Debug.error(true, true,
                            getClass().getSimpleName() +
                            ".magnitude() is negative! Returning 0.");
                return 0L;
            }
        }
        return num;
    }

    /**
     * @return whether the domain contains no values (including null)
     */
    @Override public boolean isEmpty() {
        // An empty list is not an empty domain.
        if ( seq.size() == 0 ) return false;

        for ( Domain d : seq ) {
            if ( d.isEmpty() ) {
                return true;
            }
        }
        return false;

        //return magnitude() <= 0; // This is too much computation.
    }

    /**
     * Make the domain empty, containing no values, not even null.
     *
     * @return whether any domain changed as a result of this call
     */
    @Override public boolean clearValues() {
        if ( isEmpty() ) {
            return false;
        }
        seq.clear();
        seq.add( new OrDomain() );
        return true;
    }

    protected List<Domain<?>> toDomains( List<T> ts ) {
        if ( ts == null ) return null;
        List<Domain<?>> domains = new ArrayList<Domain<?>>(ts.size());
        for ( int i=0; i < ts.size(); ++i ) {
            domains.add(new RegexDomain.SimpleDomain<>( ts.get(i) ) );
        }
        return domains;
    }

    public static boolean hasEmptyList(Domain d) {
        if ( d instanceof OrDomain ) {
            if (((OrDomain)d).hasEmptyList()) {
                return true;
            }
        }
        if ( d instanceof RegexDomain ) {
            if (((RegexDomain)d).hasEmptyList()) {
                return true;
            }
        }
        if ( d instanceof ManyDomain ) {
            return true;
        }
        return false;
    }

    public boolean hasEmptyList() {
        if ( seq == null ) {
            return true;
        }
        // All must be an empty list for the sequence to be empty.
        for ( Domain<?> d : seq ) {
            if ( !hasEmptyList( d ) ) {
                return false;
            }
        }
        return true;
    }


    @Override public boolean contains( List<T> ts ) {
        if ( ts == null ) return false;
        List<Domain<?>> domains = toDomains( ts );
        RegexDomain<T> rd = new RegexDomain<T>(domains);
        OrDomain<T> suffixes = minusPrefix( this, rd, null );
        if ( suffixes == null ) return false;
        if ( suffixes.hasEmptyList() ) return true;
        return false;
    }

    public boolean contains( RegexDomain rd ) {
        // TODO -- Should remove at least one of these at some point.
        OrDomain<T> suffixes = minusPrefix( this, rd, null );
        boolean contains1 = suffixes != null && suffixes.hasEmptyList();

        Domain newRd = intersect( this, rd, null );
        boolean contains2 = newRd != null && newRd.equals( rd );

        if ( contains1 != contains2 ) {
            Debug.error(true, false,
                        "WARNING! contains() using minusPrefix() is " +
                        contains1 + ", but using intersect() is " + contains2);
        }

        contains2 = false;  // ignore the intersect for now
        return contains1 || contains2;
    }

    @Override public List<T> pickRandomValue() {
        List<T> v = new ArrayList<>();
        for ( Domain<?> d : seq ) {
            Object o = d.pickRandomValue();
            if (o instanceof List && (getType() == null || !(getType().isInstance( o )))) {
                v.addAll(Utils.asList((Collection)o, getType()));
            } else {
                Pair<Boolean, ?> p = ClassUtils.coerce( o, getType(), false );
                T t = null;
                if ( p != null && p.first != null && p.first ) {
                    t = (T)p.second;
                    v.add( t );
                } else {
                    try {
                        t = (T)o;
                        v.add( t );
                    } catch (Throwable e) {}
                }
            }
        }
        return v;
    }

    @Override public List<T> pickRandomValueNotEqual( List<T> ts ) {
        List<T> pick1 = pickRandomValue();
        if ( CompareUtils.compare( pick1,  ts ) == 0 ) {
            pick1 = pickRandomValue();
            if ( CompareUtils.compare( pick1,  ts ) == 0 ) {
                return null;
            }
            return pick1;
        }
        return null;
    }

    @Override public boolean isInfinite() {
        return Math.isInfinity( magnitude() );
    }

    @Override public boolean isNullInDomain() {
        return false;
    }

    @Override public boolean setNullInDomain( boolean b ) {
        // TODO -- ERROR?
        return false;
    }

    @Override public Domain<List<T>> getDefaultDomain() {
        // TODO -- ERROR?
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
        if ( !contains(v) ) {
            if ( magnitude() > 0 ) {
                clearValues();
                return true;
            }
            return false;
        }
        List<Domain<?>> domains = toDomains( v );
        RegexDomain<T> rd = new RegexDomain<>( domains );
        if ( !this.equals( rd ) ) {
            this.seq = domains;
            return true;
        }
        return false;
    }

    public static <TT> List<TT> toValueList( Domain<?> d ) {
        // TODO -- what if an OrDomain has a single alternative?
        if ( d instanceof SimpleDomain ) {
            SimpleDomain sd = (SimpleDomain)d;
            if ( sd.hasValue() ) {
                return (List<TT>)Utils.newList( sd.getValue( false ));
            }
            return Utils.getEmptyList();
        }
        if ( d instanceof RegexDomain ) {
            ArrayList<TT> values = new ArrayList<>();
            RegexDomain<TT> rd = (RegexDomain)d;
            for ( Domain<?> dd : rd.seq ) {
                List<Object> list = toValueList( dd );
                if ( list != null ) {
                    List<TT> list2 = (List<TT>)Utils.asList( list, rd.getType() );
                    if ( list2 != null ) {
                        values.addAll( list2 );
                    }
                }
            }
            return values;
        }
        return null;
    }

    public static boolean hasRegex( Domain<?> d ) {
        if ( d instanceof OrDomain ) {
            return true;
        }
        if ( !(d instanceof RegexDomain ) ) {
            if ( d instanceof MultiDomain ) {
                return true;
            }
            if ( d instanceof ManyDomain ) {
                return true;
            }
            if ( d instanceof AnyDomain ) {
                return true;
            }
            return false;
        }
        RegexDomain<?> rd = (RegexDomain)d;
        for ( Domain subd : rd.seq ) {
            if ( hasRegex( subd ) ) {
                return true;
            }
        }
        return false;
    }

    protected static class SeenDomain extends RegexDomain<Domain> {
        Domain d1 = null;
        Domain d2 = null;
//        public SeenDomain( ObjectDomain<Domain> objectDomain ) {
//            super( objectDomain );
//        }
//        public SeenDomain( Collection<Domain> objects ) {
//            super();
//            addAll(objects);
//        }
        public SeenDomain( Domain d1, Domain d2, Domain result ) {
            super();
            if ( result != null ) {
                seq.add(result);
            }
            this.d1 = d1;
            this.d2 = d2;
//            add( d1 );
//            add( d2 );
        }
        public SeenDomain( SeenDomain sd ) {
            super(sd);
            this.d1 = sd.d1;
            this.d2 = sd.d2;
        }
    }

    // Pseudocode and notes by dlegg on intersecting RegexDomains
    //
    // Problem:
    //  If both arguments of Intersect have a KleeneStar element, this can lead to loops in the matching / intersecting process.
    //  This is both a problem of making sure that Intersect terminates and that it gives back an expression that appropriately captures this behavior.
    //
    //Proposed Solution:
    //  When Intersect is called, it squirrels away its arguments in a "seen set".
    //  When a loop is detected, return a special tag on output, with the arguments that are looping, and let the level that matches those arguments add the star to the patterns it gets back. For example:
    //
    //a = "(xyx)*x"
    //b = "x(yxx)*"
    //Solution: "(xyx)*x"
    //Call stack:
    //Intersect("(xyx)*x", "x(yxx)*")  - Refer to this as Call 1
    //  Intersect("x", "x(yxx)*")
    //    Intersect("", "(yxx)*")
    //      Intersect("(yxx)*", "")
    //        Intersect("", "")
    //          ""
    //        Intersect("yxx(yxx)*", "")
    //          No solution
    //        ""   (computed as alternation of "" alone)
    //      ""
    //    "x"
    //  Intersect("xyx(xyx)*x", "x(yxx)*")
    //    Intersect("yx(xyx)*x", "(yxx)*")
    //      Intersect("(yxx)*", "yx(xyx)*x")
    //        Intersect("", "yx(xyx)*x")
    //          No solution
    //        No solution
    //      Intersect("yxx(yxx)*", "yx(xyx)*x")
    //        Intersect("xx(yxx)*", "x(xyx)*x")
    //          Intersect("x(yxx)*", "(xyx)*x")
    //            Intersect("(xyx)*x", "x(yxx)*")
    //              "" - Repeat on ("(xyx)*x", "x(yxx)*")
    //            "" - Repeat on ("(xyx)*x", "x(yxx)*")
    //          "x" - Repeat on ("(xyx)*x", "x(yxx)*")
    //        "yx" - Repeat on ("(xyx)*x", "x(yxx)*")
    //      "yx" - Repeat on ("(xyx)*x", "x(yxx)*")
    //    "xyx" - Repeat on ("(xyx)*x", "x(yxx)*")
    //  Call 1 notes that "(xyx)*" is pattern to consume its initial (xyx)* KleeneStar token.
    //  From this, deduces that "(xyx)*x" is final answer, by appending initial "skip *" solution to "use *" solution
    //
    //
    //Intersect(RDS a, RDS b, LinkedHashSet<Tuple<RDS, RDS>> seen) {
    //    // recursion base cases:
    //    // there might be a way to collapse these to make them more elegant
    //    if (a is "" && b is "") {
    //        return "";
    //    } else if (head(a) exists and is KleeneStar) {
    //        return "";
    //    } else if (head(b) exists and is KleeneStar) {
    //        return "";
    //    }
    //
    //    if ( (a, b) in seen ) {
    //        return "" tagged with (a, b);
    //    }
    //
    //    output = empty Alternation;
    //
    //    ha = head(a); // first element of a
    //    hb = head(b); // first element of b
    //
    //    if (ha is Literal and hb is Literal) {
    //        if (ha == hb) {
    //            output.addAll(ha + each of Intersect(tail(a), tail(b)));
    //        }
    //    } else if (ha is Literal) {
    //        // break symmetry, by only "working" on argument a
    //        return Intersect(b, a);
    //    } else if (ha is Alternation) {
    //        // expand alternations:
    //        for (a_alt in ha) {
    //            output.addAll(Intersect(a_alt + tail(a), b);
    //        }
    //    } else if (ha is KleeneStar) {
    //        // skip repeated content:
    //        withoutRepeat = Intersect(tail(a), b);
    //        output.addAll(withoutRepeat);
    //        // use at least once:
    //        withRepeat = Intersect(ha.innerElement + a, b);
    //        for ( elem in withRepeat ) {
    //            if ( elem is tagged with (a,b) ) {
    //                output.add(KleeneStar(elem) + withoutRepeat);
    //            } else {
    //                output.add(elem);
    //            }
    //        }
    //        // NOTE: there's redundancy here when we add a tagged element, since the withoutRepeat case is represented explicitly and implicitly when the KleeneStar(elem) part matches ""
    //    }
    //
    //    // Catch redundancy, like specifying "x|(xyx)*x" instead of "(xyx)*x"
    //    output.simplify();
    //
    //    return output;
    //}
    /**
     * Restrict this domain to largest subset that is included by the
     * input domain. This will be the empty domain if there is no intersection
     * between this domain and the input domain.
     *
     * @param domainTree1
     * @param domainTree2
     * @param seen
     * @return whether the domain of any object changed as a result of this call
     */
    public static Domain intersect( RegexDomain domainTree1, Domain domainTree2,
                                    TreeMap< Domain, Map< Domain, Domain> > seen ) {
        RegexDomain domain1 = (RegexDomain)flattenAnd( domainTree1 );
        Domain<?> domain2 = flattenAnd( domainTree2 );

        if ( domain1 == null || domain2 == null ) return null;

        // check for recursion
        if ( seen == null ) {
            seen = new TreeMap< Domain, Map< Domain, Domain> >(comparator);
        } else if ( seen.containsKey( domain1 ) ) {
            Map<Domain, Domain> s = seen.get( domain1 );
            if ( s != null && s.containsKey( domain2 ) ) {
                Domain result = s.get( domain2 );
                return new SeenDomain(domain1, domain2, result.clone());
            }
        }
        TreeMap<Domain, Domain> tm = new TreeMap<Domain, Domain>(comparator);
        tm.put(domain2, null);
        seen.put(domain1, tm);


        OrDomain alternation = new OrDomain();

        tm.put(domain2, alternation);
        // FIXME? -- empty might mean empty list, which is something.
        if ( domain1.isEmpty() ) return alternation;
        if ( domain2.isEmpty() ) return alternation;

        if ( isEmptyList( domain1 ) ) {
            if ( isEmptyList( domain2 ) || hasEmptyList( domain2 ) ) {
                return domain1.clone();
            }
            return alternation;
        } else if ( isEmptyList( domain2 ) ) {
            if ( isEmptyList( domain1 ) || hasEmptyList( domain1 ) ) {
                return domain2.clone();
            }
            return alternation;
        }

        if ( isManyAny(domain1) ) {
            return domain2.clone();
        }
        if ( isManyAny(domain2) ) {
            return domain1.clone();
        }

//        // do easy stuff
//        if ( !hasRegex( domain2 ) ) {
//            List values = toValueList( domain2 );
//            RegexDomain result = domain1.clone();
//            result.restrictToValue( values );
//            tm.put(domain2, result);
//            return result;
//        }

        // head and tail of domain1
        Domain head1 = domain1 instanceof OrDomain ? domain1 :
                       (Domain)domain1.seq.get( 0 );
        RegexDomain tail1 = null;
            tail1 = domain1 instanceof OrDomain ? null :
                    new RegexDomain<>( domain1.seq.subList( 1,domain1.seq.size() ) );

        // head and tail of domain2
        Domain head2 = domain2;
        Domain tail2 = null;
        RegexDomain prd = null;
        if ( domain2 instanceof RegexDomain && !(domain2 instanceof OrDomain) ) {
            prd = (RegexDomain)domain2;
            if ( prd.seq.size() > 0 ) {
                head2 = (Domain)prd.seq.get( 0 );
//                if ( prd.seq.size() > 1 ) {
                    tail2 = new RegexDomain( prd.seq.subList( 1, prd.seq.size() ) );
//                }
            }
        }

        if ( head1 == null || head2 == null ) {
            Debug.error(true, true, "RegexDomain.intersect(): unexpected null values!");
            tm.put(domain2, null);
            return null;
        }

        if ( head1 instanceof SimpleDomain && head2 instanceof SimpleDomain ) {
            // If the heads are equal, then append head1 with the result of intersecting the tails.
            if ( Utils.valuesEqual( head1.getValue( false ), head2.getValue( false ) ) ) {
                // concatenate head with intersection of tails
                OrDomain result = doTail(head1, tail1, tail2, seen);
                alternation.seq.addAll( result.seq );
                return alternation;
            }
            // No match means no intersection.
            OrDomain od = new OrDomain();
            tm.put(domain2, od);
            return od;
        }

        if ( head1 instanceof SimpleDomain ) {
            // break symmetry, by only "working" on domain1
            // swap arguments since we know that head2 is not a SimpleDomain
            RegexDomain newDomain2 = makeAndDomain(domain2);
            Domain i = intersect( newDomain2, domain1, seen );
            tm.put(domain2, i);
            return i;
        }

        if ( head1 instanceof AnyDomain ) {
            if ( head2 instanceof AnyDomain || head2 instanceof SimpleDomain) {
                OrDomain result = doTail(head2, tail1, tail2, seen);
                alternation.seq.addAll( result.seq );
                return alternation;
            } else {
                RegexDomain newDomain2 = makeAndDomain(domain2);
                Domain i = intersect( newDomain2, domain1, seen );
                tm.put(domain2, i);
                return i;
            }
        }

        if ( head1 instanceof OrDomain ) {
            OrDomain<?> od1 = (OrDomain)head1;
            for ( Domain d : od1.seq ) {
                RegexDomain newDomain1 = concat( d, tail1 );
                Domain i = intersect( newDomain1, domain2, seen );
//                    if ( i instanceof SeenDomain ) {
//                        i = seen.get(newDomain1).get(domain2);
//                    }
                if ( i != null && !i.isEmpty() ) {
                    alternation.seq.add( i );
                }
            }
        }

        if ( head1 instanceof ManyDomain ) {
            // match head1 to nothing/empty list
            OrDomain result = doTail(new RegexDomain(), tail1, domain2, seen);
            if ( result != null && !result.isEmpty() ) {
                alternation.seq.addAll( result.seq );
            }

            // TODO -- assumes ManyDomain is .* instead of, for example, x* or (xy)*
            if ( head2 instanceof ManyDomain ) {
                // include .* in the intersection
                result = doTail(head2, tail1, tail2, seen);
                if ( result != null && !result.isEmpty() ) {
                    alternation.seq.addAll( result.seq );
                }

                // match head2 to nothing/empty list
                result = doTail(new RegexDomain(), domain1, tail2, seen);
                if ( result != null && !result.isEmpty() ) {
                    alternation.seq.addAll( result.seq );
                }
            } else {
                // use at least once
                ManyDomain md1 = (ManyDomain)head1;
                RegexDomain newDomain1 = concat( md1.domainForEach, domain1 );
                Domain withRepeat = intersect( newDomain1, domain2, seen );
                if ( withRepeat != null && !withRepeat.isEmpty() ) {
                    alternation.seq.add( withRepeat );
                }
            }
        }

        Domain fa = flattenAnd( alternation );
        if ( fa instanceof Simplifiable ) {
            ( (Simplifiable)fa ).simplify( true, null );
        }
        if ( fa != null ) {
            return fa;
        }
        return alternation;
    }

    /**
     * <p>Make the regular expression simpler without changing the language
     *    it defines.</p>
     * <p></p>
     * <h3>Simplification Rules:</h3>
     * <p>
     *     This function employs a greedy algorithm by attempting to match
     *     each pattern below, substituting where possible.  If any changes
     *     are made, it repeats applying the rules until none of the rules
     *     apply.
     * </p>
     * <p>
     *     Below, <code>a</code>, <code>b</code>, and <code>c</code> represent
     *     any regular expression. <code>x</code> and <code>y</code> represent
     *     single symbols, and <code>()</code> represents an empty sequence.
     * </p><ol>
     * <li><code>
     *     a*a* -> a*
     * </code></li>
     * <li><code>
     *     a|a -> a
     * </code></li>
     * <li><code>
     *     a|a* -> a*
     * </code></li>
     * <li><code>
     *     a|.* -> .*
     * </code></li>
     * <li><code>
     *     x|. -> .
     * </code></li>
     * <li><code>
     *     a*a -> aa*
     * </code></li>
     * <li><code>
     *     aa*|a* -> a*
     * </code></li>
     * <li><code>
     *     a() -> a
     * </code></li>
     * <li><code>
     *     ()a -> a
     * </code></li>
     * <li><code>
     *    ()|aa* -> a*
     * </code></li>
     * <li><code>
     *    (()|a)* -> a*
     * </code></li>
     * <li><code>
     *     (ab)*a -> a(ba)*
     * </code></li>
     * <li><code>
     *    (a*b)*a* -> (a|b)*
     * </code></li>
     * <li><code>
     *    a*(ba*)* -> (a|b)*
     * </code></li>
     * <li><code>
     *    abcab -> (ab)c(ab)
     * </code></li>
     * <li><code>
     *    a(bc) -> abc
     * </code></li>
     * </ol>
     * <p>
     *     The last two rules are to try to avoid looking at the n^2/2
     *     subsequences for every rule and just do it for these rules.
     *     These two rules fight each other, so it might be best to
     *     only apply the last when <code>bc</code> does not show up
     *     anywhere else.  Remember that these rules are heuristics,
     *     and we do not guarantee missing something obvious.  That's
     *     because the problem is PSPACE hard, meaning that exploring
     *     every possible case for simplification is intractable.
     * <p>
     *     Some other rules.
     * </p><ol>
     * <li><code>
     *     a** -> a*
     * </code></li>
     * <li><code>
     *     (a) -> a
     * </code></li>
     * </ol>
     * </p>
     * @param r the expression to simplify
     * @return a simplified <code>RegexDomain</code> equivalent to <code>r</code> or <code>r</code>
     */
    public static RegexDomain simplify(RegexDomain r) {

        RegexDomain s = r.clone();
        s.simplify();
        return s;
    }

    public static int maxTriesToSimplify = 100;  // REVIEW -- make this static?  good: less memory per RegexDomain; bad: can't modify for each.

    /**
     * <p>Make the regular expression simpler without changing the language
     *    it defines.</p>
     * @see <code>public static RegexDomain simplify(RegexDomain r)</code> for details
     *
     * @return whether or not the representation changed
     */
    public boolean simplify() {
        return simplify( true, null );
    }
    /**
     * <p>Make the regular expression simpler without changing the language
     *    it defines.</p>
     * @see <code>public static RegexDomain simplify(RegexDomain r)</code> for details
     *
     * @param deep whether to recursively simplify contained objects
     * @param seen a list of objects already visited to avoid infinite recursion
     * @return whether or not the representation changed
     */
    @Override public boolean simplify( boolean deep, Set<Simplifiable> seen ) {
        return simplify( this, deep, seen );
        // TODO -- may not need the static version of this method
    }

    public static boolean simplify( Simplifiable d, boolean deep, Set<Simplifiable> seen ) {
        boolean changed = false;
        for ( int i = 0; i < maxTriesToSimplify; ++i ) {
            boolean changedThisTime = d.simplifyOnePass(deep, seen);
            if ( !changedThisTime ) {
                break;
            }
            changed = true;
        }
        return changed;
    }

    @Override public boolean simplifyOnePass( boolean deep, Set<Simplifiable> seen ) {
        return simplifyOnePass( this, deep, seen );
        // TODO -- may not need the static version of this now that it's in Simplifiable
    }

    public static boolean simplifyOnePass( Simplifiable s, boolean deep, Set<Simplifiable> seen ) {
        Pair<Boolean, Set<Simplifiable>> pair = Utils.seen( s, true, seen );
        if ( pair.first ) return false;
        seen = pair.second;

        RegexDomain<?> rd = ( s instanceof RegexDomain ) ? (RegexDomain)s : null;
        if ( rd != null && rd.seq == null ) {
            return false;
        }
        boolean changed = false;

        // First, simplify the pieces
        // TODO -- should this be executed only once and be pulled up into simplify() to stay out of the try loop?
        //         If the pieces are reconstructed, then no.
        if ( deep && rd != null ) {
            for ( Domain d : rd.seq ) {
                if ( d instanceof Simplifiable ) {
                    boolean changedElement =
                            ( (Simplifiable)d ).simplify( deep, seen );
                    changed = changed || changedElement;
                }
            }
        }

        // Now, look for sequence patterns
        if ( rd != null ) {
            System.out.println( "before simplifyForPatterns(): " + s );
            boolean changedThisTime = rd.simplifyForPatterns();
            System.out.println( " after simplifyForPatterns(): " + s );
            changed = changed || changedThisTime;
        }

        return changed;
    }

    public boolean simplifyForPatterns() {
        boolean changed = false;

        List<Domain<?>> newSeq = new ArrayList<>();

        // a() -> a, ()a -> a
        boolean changedThisTime = false;
        for ( Domain d : seq ) {
            if ( isEmptyList( d ) ) {
                if ( seq.size() > 1 ) {
                    changedThisTime = true;
                }
            } else {
                newSeq.add( d );
            }
        }
        if ( changedThisTime ) {
            changed = true;
            seq = newSeq;
            changedThisTime = false;
        }
        newSeq = new ArrayList<>();

        // a*a* -> a*
        ManyDomain lastD = null;
        for ( Domain d : seq ) {
            if ( d instanceof ManyDomain ) {
                ManyDomain md = (ManyDomain)d;
                if ( lastD != null && md.domainForEach.equals( lastD.domainForEach ) ) {
                    changedThisTime = true;
                } else {
                    newSeq.add( md );
                }
                lastD = md;
            } else {
                lastD = null;
                newSeq.add( d );
            }
        }
        if ( changedThisTime ) {
            changed = true;
            seq = newSeq;
            changedThisTime = false;
        }
        lastD = null;
        newSeq = new ArrayList<>();


        // a*a -> aa*
        for ( Domain d : seq ) {
            if ( lastD != null && lastD.domainForEach.equals( d ) ) {
                changedThisTime = true;
                newSeq.add( d );
            } else if ( d instanceof ManyDomain ) {
                if ( lastD != null ) {
                    newSeq.add( lastD );
                }
                lastD = (ManyDomain)d;
            } else if ( lastD != null ) {
                newSeq.add( lastD );
                newSeq.add( d );
                lastD = null;
            } else {
                newSeq.add( d );
            }
        }
        if ( lastD != null ) {
            newSeq.add( lastD );
        }
        if ( changedThisTime ) {
            changed = true;
            seq = newSeq;
            changedThisTime = false;
        }
        lastD = null;
        newSeq = new ArrayList<>();


        // (ab)*a -> a(ba)*
        for ( Domain d : seq ) {
            if ( lastD != null ) {
                if ( lastD.domainForEach instanceof RegexDomain &&
                     !( d instanceof OrDomain ) &&
                     ((RegexDomain)lastD.domainForEach).seq.size() > 1 &&
                     d.equals( ((RegexDomain)lastD.domainForEach).seq.get(0) ) ) {
                    changedThisTime = true;
                    newSeq.add( d );
                    RegexDomain newDomainForEach = new RegexDomain();
                    RegexDomain oldDomainForEach = (RegexDomain)lastD.domainForEach;
                    int z = oldDomainForEach.seq.size();
                    newDomainForEach.seq.addAll( oldDomainForEach.seq.subList( 1, z ) );
                    newDomainForEach.seq.add( oldDomainForEach.seq.get(0) );
                    lastD = new ManyDomain(newDomainForEach);
                }
            } else if ( d instanceof ManyDomain ) {
                if ( lastD != null ) {
                    newSeq.add( lastD );
                }
                lastD = (ManyDomain)d;
            } else if ( lastD != null ) {
                newSeq.add( lastD );
                newSeq.add( d );
                lastD = null;
            } else {
                newSeq.add( d );
            }
        }
        if ( lastD != null ) {
            newSeq.add( lastD );
        }
        if ( changedThisTime ) {
            changed = true;
            seq = newSeq;
            changedThisTime = false;
        }
        lastD = null;
        newSeq = new ArrayList<>();

        // TODO -- implement remaining rules below
        // (a*b)*a* -> (a|b)*
        // a*(ba*)* -> (a|b)*
        // abcab -> (ab)c(ab)
        // a(bc) -> abc

        return changed;
    }

    public static RegexDomain makeAndDomain( Domain domain ) {
        if ( domain instanceof RegexDomain && !(domain instanceof OrDomain ) ) {
            return (RegexDomain)domain;
        }
        RegexDomain newDomain = new RegexDomain();
        newDomain.seq.add(domain);
        return newDomain;
    }

    protected static OrDomain doTail( Domain head1, RegexDomain tail1, Domain tail2, TreeMap<Domain, Map<Domain, Domain>> seen ) {
        OrDomain alternation = new OrDomain();
        Domain i = intersect( tail1, tail2, seen );
        //                if ( i instanceof SeenDomain ) {
        //                    i = seen.get(tail1).get(tail2);
        //                }
        if ( i instanceof OrDomain ) {
            OrDomain od = (OrDomain)i;
            for ( Object o : od.seq ) {
                if ( o instanceof Domain ) {
                    RegexDomain ho = concat( head1, (Domain)o );
                    if ( ho != null ) {
                        alternation.seq.add( ho );
                    }
                } else {
                    Debug.error(true, "RegexDomain.intersect(): unexpected non-domain! " + o);
                }
            }
        } else {
            RegexDomain hi = concat( head1, i );
            if ( hi != null ) {
                alternation.seq.add( hi );
            }
        }
        return alternation;
    }

    /**
     * Restrict this domain to largest subset that is included by the
     * input domain. This will be the empty domain if there is no intersection
     * between this domain and the input domain.
     *
     * @param domain
     * @return whether the domain of any object changed as a result of this call
     */
    @Override public <TT> boolean restrictTo( Domain<TT> domain ) {
        if ( domain == null ) return false;
        // First try a shortcut.
        if ( !hasRegex( domain ) ) {
            List<T> values = toValueList( domain );
            if ( values == null ) return false;
            if ( !contains( values ) ) {
                if ( !isEmpty() ) {
                    clearValues();
                    return true;
                }
                return false;
            }
            // Else, is contained, so we need to intersect the two
        }

        // See if they happen to be the same.
        if ( this.equals( domain ) ) {
            return false;
        }

        // Do the complicated intersection
        Domain result = intersect( this, domain, null );

        if ( this.equals(result) ) {
            return false;
        }

        if ( result instanceof OrDomain ) {
            this.seq = Utils.newList( result );
        } else if ( result instanceof RegexDomain ) {
            this.seq = ( (RegexDomain)result ).seq;
        } else {
            this.seq = Utils.newList( result );
        }

//        // TODO!!  HERE!!
//        Debug.error(true, true,"RegexDomain.restrictTo(): not fully implemented!");
        return true;
    }
    /**
     * Exclude elements in the input domain from this domain.
     *
     * @param domain
     * @return whether the domain of any object changed as a result of this call
     */
    @Override public <TT> Domain<TT> subtract( Domain<TT> domain ) {
        // TODO
        Debug.error(true, true,"RegexDomain.subtract(): not yet implemented!");
        return null;
    }

    /**
     * Reverse the order of the list of T elements in seq.
     * @return a new RegexDomain with the elements reversed
     */
    public RegexDomain<T> reverse() {
        RegexDomain<T> rdr = this.clone();
        Collections.reverse( rdr.seq );
        // now reverse any nested lists
        for ( Domain<?> d : rdr.seq ) {
            if ( d instanceof RegexDomain ) {
                ((RegexDomain)d).reverse();
            }
        }
        return rdr;
    }

    public static <TT> OrDomain<TT> minusSuffix( RegexDomain<TT> rd, Domain<?> suffix ) {
//    public static <TT> RegexDomain<TT> minusSuffix( RegexDomain<TT> rd, Domain<TT> suffix ) {
        // Reverse both args, pass to minusPrefix, reverse the result.
        RegexDomain<TT> rdr = rd.reverse();
        Domain<?> rSuffix = suffix;
        if ( suffix instanceof RegexDomain ) {
            rSuffix = (Domain<TT>)( (RegexDomain<TT>)suffix ).reverse();
        }
        OrDomain<TT> result = minusPrefix( rdr, rSuffix, null );
        if ( result != null ) result = result.reverse();
        return null;
    }

    @Override public boolean equals( Object obj ) {
        if ( obj instanceof RegexDomain ) {
            // REVIEW -- could be smarter; for instance (xy)z should equals x(yz)
            int comp = CompareUtils.compare( seq, ((RegexDomain)obj).seq );
            return comp == 0;
        }
        return super.equals( obj );
    }

    public static Domain flattenAnd( Domain d) {
        if ( d == null ) return null;
        if ( d instanceof OrDomain ) {
            OrDomain<?> od = (OrDomain)d;
            OrDomain<?> nod = new OrDomain();
            for ( Domain sd : od.seq ) {
                Domain nsd = flattenAnd( sd );
                if ( od.seq.size() == 1 ) {
                    return nsd;
                }
                nod.seq.add( nsd );
            }
            return nod;
        }
        if ( d instanceof RegexDomain ) {
            RegexDomain<?> rd = (RegexDomain)d;
            RegexDomain<?> nrd = new RegexDomain();
            for ( Domain sd : rd.seq ) {
                Domain nsd = flattenAnd( sd );
                if ( nsd instanceof OrDomain ) {
                    if ( ((OrDomain)nsd).seq.size() == 1 ) {  // This may not be possible based on treatment of OrDomain above.
                        nrd.seq.addAll( ( (OrDomain)nsd ).seq );
                    } else {
                        nrd.seq.add( nsd );
                    }
                } else if ( nsd instanceof RegexDomain ) {//&& ! ( nsd instanceof OrDomain ) ) {
                    nrd.seq.addAll( ( (RegexDomain)nsd ).seq );
                } else {
                    nrd.seq.add( nsd );
                }
            }
            return nrd;
        }
        if ( d instanceof ManyDomain ) {
            Domain dfe = ( (ManyDomain)d ).domainForEach;
            Domain ndfe = flattenAnd( dfe );
            if ( ndfe == dfe ) return d;
            ManyDomain nmd = new ManyDomain( ndfe );
            return nmd;
        }
        return d;
    }

    //    MinusPrefix(RDS result, RDS prefix) {
    //
    //        if (prefix is "") {
    //
    //            return {result};
    //
    //        }
    //
    //
    //
    //        // output should act a bit like a set, discarding duplicates.
    //
    //        Alternation output = empty alternation;
    //
    //
    //
    //        hr = head(result); // first element of result pattern
    //
    //        hp = head(prefix); // first element of prefix pattern
    //
    //
    //
    //        if (hp is Literal and hr is Literal) {
    //
    //            if (hp == hr) {
    //
    //                output.addAll(MinusPrefix(tail(result), tail(prefix)));
    //
    //            }
    //
    //        } else if (hr is Alternation) {
    //
    //            // expand hr and exhaustively search possibilities
    //
    //            for (alt in hr) {
    //
    //                output.addAll(MinusPrefix(alt + tail(result), tail(prefix)));
    //
    //            }
    //
    //        } else if (hp is Alternation) {
    //
    //            // expand hp and exhaustively search possibilities
    //
    //            for (alt in hp) {
    //
    //                output.addAll(MinusPrefix(tail(result), alt + tail(prefix)));
    //
    //            }
    //
    //        } else if (hr is KleeneStar) {
    //
    //            // Compute this by matching the star against 0 or more elements of prefix,
    //
    //            //   then using what's left of the prefix to match back against the result.
    //
    //            for (suffix in MinusPrefix(prefix, hr)) { // note the order of arguments; this is intentional
    //
    //                output.addAll(MinusPrefix(tail(result), suffix));
    //
    //            }
    //
    //        } else if (hp is KleeneStar) {
    //
    //            output.addAll(MinusPrefix(result, tail(prefix))); // take 0 copies
    //
    //            // otherwise, match 1 copy, then match each resulting suffix against the full prefix
    //
    //            for (suffix in MinusPrefix(result, hp.innerElement)) {
    //
    //                output.addAll(MinusPrefix(suffix, prefix));
    //
    //            }
    //
    //        } // else we're in a case we don't know how to handle, like groups. Whoops.
    //
    //
    //
    //        // may want to apply some kind of "simplify" to output at this point
    //
    //
    //
    //        return output;
    //
    //    }
    public static <TT> OrDomain<TT> minusPrefix( RegexDomain<TT> rdTree, Domain<?> prefixTree, TreeMap< Domain, Map< Domain, Domain> > seen ) {
        RegexDomain<TT> rd = (RegexDomain<TT>)flattenAnd( rdTree );
        Domain<?> prefix = flattenAnd( prefixTree );
        if ( rd == null | prefix == null ) return null;

        OrDomain<TT> alternation = new OrDomain<>();

        // check for recursion
        Map<Domain, Domain> s = null;
        if ( seen == null ) {
            seen = new TreeMap< Domain, Map< Domain, Domain> >(comparator);
        } else if ( seen.containsKey( rd ) ) {
            s = seen.get( rd );
            if ( s != null && s.containsKey( prefix ) ) {
                Domain result = s.get( prefix );
                if ( result == null || result instanceof OrDomain ) {
                    return (OrDomain<TT>)result;
                }
                alternation.seq.add(result);
//                SeenDomain sd = new SeenDomain( rd, prefix, result );
//                alternation.seq.add( sd );
                return alternation;
            } else if ( s != null ) {
                s.put(prefix, alternation);  // this just initializes an entry; alternation is likely () here.
            }
        }
        if ( s == null ) {
//            TreeMap<Domain, Domain> tm =
//                    new TreeMap<Domain, Domain>( comparator );
            s = new TreeMap<Domain, Domain>( comparator );
            s.put( prefix, alternation );
            seen.put( rd, s );
        }

        if ( prefix.isEmpty() ) {
            return alternation;
        }

        // TODO -- make like a set
        // output should act a bit like a set, discarding duplicates.
        //RegexDomain<TT> result = new RegexDomain<>();

        if ( rd.isEmpty() ) return alternation;

        if ( isEmptyList(rd) ) {
            if ( hasEmptyList( prefix ) ) {
                alternation.seq.add(rd.clone());
            }
            return alternation;
        } else if ( isEmptyList( prefix ) ) {
            alternation.seq.add(rd.clone());
            return alternation;
        }

        // head and tail of rd
        Domain<?> hr = rd instanceof OrDomain ? rd : rd.seq.get( 0 );
        RegexDomain<TT> tr = rd instanceof OrDomain ? new RegexDomain<TT>() :
             new RegexDomain<>( rd.seq.subList( 1, rd.seq.size() ) );

        // head and tail of prefix
        Domain<?> hp = prefix;
        Domain<?> tp = new RegexDomain<TT>();
        RegexDomain<TT> prd = null;
        if ( prefix instanceof RegexDomain && !(prefix instanceof OrDomain) ) {
            prd = (RegexDomain<TT>)prefix;
            hp = prd.seq.get( 0 );
            tp = new RegexDomain<TT>( prd.seq.subList( 1, prd.seq.size() ) );
        }

        if ( hr instanceof SimpleDomain && hp instanceof SimpleDomain ) {
            if ( Utils.valuesEqual( hp.getValue( false ), hr.getValue( false ) ) ) {
                alternation.seq.addAll( minusPrefix( tr, tp, seen ).seq );
            }
            // else, do nothing to alternation, but succeed
        } else if (( hr instanceof AnyDomain && ( hp instanceof AnyDomain || hp instanceof SimpleDomain ) )
            || ( hp instanceof AnyDomain && hr instanceof SimpleDomain ) ) {
            alternation.seq.addAll( minusPrefix( tr, tp, seen ).seq );
        }

        else if ( hr instanceof OrDomain ) {
            // Expand alternation hr and exhaustively search possibilities.
            for ( Object d : ((OrDomain)hr).seq ) { // Why does the compiler not let me declare d as a Domain????!
                if ( d == null ) continue;
                if ( d instanceof Domain ) {
                    OrDomain<TT> a = minusPrefix( concat( (Domain)d, tr ), tp, seen );
                    if ( a != null ) {
                        alternation.seq.addAll( a.seq );
                    }
                } else {
                    Debug.error(true,
                                "minusPrefix(): non-Domain in RegexDomain.seq!!! " +
                                d.getClass().getSimpleName() + " : " + d);
                }
            }
        } else if ( hp instanceof OrDomain ) {
            // Expand alternation hr and exhaustively search possibilities.
            for ( Object d : ((OrDomain)hp).seq ) { // Why does the compiler not let me declare d as a Domain????!
                if ( d == null ) continue;
                if ( d instanceof Domain ) {
                    OrDomain<TT> a = minusPrefix( tr, concat( (Domain)d, tp ), seen );
                    if ( a != null ) {
                        alternation.seq.addAll( a.seq );
                    }
                } else {
                    Debug.error(true, true,
                                "minusPrefix(): non-Domain in RegexDomain.seq!!! " +
                                d.getClass().getSimpleName() + " : " + d);
                }
            }

        } else if ( hr instanceof ManyDomain ) {
            // TODO -- Currently assumes ManyDomain is .* instead of x* or (xy)*
            // Compute this by matching the star against 0 or more elements of prefix,
            // then using what's left of the prefix to match back against the result.
            if ( hp instanceof ManyDomain ) {
                OrDomain<TT> alts = minusPrefix( tr, tp, seen );
                if ( alts != null ) {
                    alternation.seq.addAll( alts.seq );
                }
                alts = minusPrefix( tr, prefix, seen );
                if ( alts != null ) {
                    alternation.seq.addAll( alts.seq );
                }
                alts = minusPrefix( rd, tp, seen );
                if ( alts != null ) {
                    alternation.seq.addAll( alts.seq );
                }
                // rd looks like .*[something], so .* can absorb all of prefix
                //   and leave .*[something] at the end
                alternation.seq.add( rd );
            } else {
                // minusPrefix(.*x, y) = OR(.*x, minusPrefix(x, suffixes(y)))
                alternation.seq.add(rd); // removing y from .*x can be .*x
                RegexDomain<TT> regexPrefix = null;
                if ( prefix instanceof RegexDomain ) {
                    regexPrefix = (RegexDomain<TT>)prefix;
                } else {
                    regexPrefix = new RegexDomain<>( Utils.newList( prefix ) );
                }
                OrDomain<TT> suffixes = minusPrefix( regexPrefix, hr, seen );
                for ( Domain<?> suffix : suffixes.seq ) {
                    OrDomain<TT> alts = minusPrefix( tr, suffix, seen );
                    if ( alts != null ) {
                        alternation.seq.addAll( alts.seq );
                    }
                }
            }
        } else if ( hp instanceof ManyDomain ) {
            // take 0 copies
            OrDomain<TT> alts = minusPrefix( rd, tp, seen );
            if ( alts != null ) {
                alternation.seq.addAll( alts.seq );
            }

            // otherwise, match 1 copy, then match each resulting suffix against the full prefix
            OrDomain<TT> suffixes = minusPrefix( rd, ( (ManyDomain)hp ).domainForEach, seen );
            if ( suffixes != null ) {
                for ( Domain<?> suffix : suffixes.seq ) {
                    RegexDomain<TT> regexSuffix = null;
                    if ( suffix instanceof RegexDomain ) {
                        regexSuffix = (RegexDomain<TT>)suffix;
                    } else {
                        regexSuffix = new RegexDomain<>( Utils.newList( suffix ) );
                    }
                    OrDomain<TT> malts = minusPrefix( regexSuffix, prefix, seen );
                    if ( malts != null ) {
                        alternation.seq.addAll( malts.seq );
                    }
                }
            }
        } else if ( hr instanceof RegexDomain ) {
            RegexDomain<?> newRd = concat( (RegexDomain)hr, tr );
            OrDomain<?> result = minusPrefix( newRd, prefix, seen );
            return (OrDomain<TT>)result;
            //Debug.error(true, true,"minusPrefix(): Unexpected RegexDomain as head of sequence: " +  hr );
        } else if ( hp instanceof RegexDomain ) {
            RegexDomain<?> newPrefix = concat( (RegexDomain)hp, tp );
            OrDomain<?> result = minusPrefix( rd, newPrefix, seen );
            return (OrDomain<TT>)result;
            //Debug.error(true, true,"minusPrefix(): Unexpected RegexDomain as head of prefix: " +  hp );
        } else {
            // else we're in a case we don't know how to handle, like groups. Whoops.
            Debug.error(true, true,"Unexpected arguments: minusPrefix(" + rd + ", " + prefix + ")" );
        }

        alternation.simplify();

        return alternation;
    }

    public static boolean isEmptyList( Domain d ) {
        if ( d == null ) return false;
        if ( d instanceof SimpleDomain ) {
            return false;
        }
        if ( d instanceof AnyDomain ) {
            return false;
        }
        if ( d instanceof ManyDomain ) {
            return isEmptyList( ( (ManyDomain)d ).domainForEach );
        }
        if ( d instanceof OrDomain ) {
            OrDomain<?> od = (OrDomain<?>)d;
            if (od.seq.isEmpty() ) {
                return false;
            }
            for ( Domain dd : od.seq ) {
                if ( !isEmptyList( dd ) ) {
                    return false;
                }
            }
            return true;
        }
        if ( d instanceof RegexDomain ) {
            for ( Domain dd : ((RegexDomain<?>)d).seq ) {
                if ( !isEmptyList( dd ) ) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean isAny( Domain d ) {
        if ( d == null ) return false;
        if ( d instanceof SimpleDomain ) {
            return false;
        }
        if ( d instanceof AnyDomain ) {
            return true;
        }
        if ( d instanceof ManyDomain ) {
            return false;
        }
        if ( d instanceof OrDomain ) {
            OrDomain<?> od = (OrDomain<?>)d;
            boolean foundAny = false;
            for ( Domain dd : od.seq ) {
                if ( isAny( dd ) ) {
                    foundAny = true;
                    continue;
                }
                if ( dd.isEmpty() ) continue;
                if ( dd instanceof SimpleDomain ) continue;
                return false;
            }
            return foundAny;
        }
        if ( d instanceof RegexDomain ) {
            // A RegexDomain is a domain of lists, not items.
            return false;
        }
        return false;

    }

    public static boolean isManyAny( Domain d ) {
        if ( d == null ) return false;
        if ( d instanceof SimpleDomain ) {
            return false;
        }
        if ( d instanceof AnyDomain ) {
            return false;
        }
        if ( d instanceof ManyDomain ) {
            if ( isAny( ( (ManyDomain)d ).domainForEach ) ) {
                return true;
            }
            // Many ManyAnys is a ManyAny
            if ( isManyAny( ( (ManyDomain)d ).domainForEach ) ) {
                return true;
            }
            return false;
        }
        if ( d instanceof OrDomain ) {
            // The alternatives are a ManyAny if there's at least one ManyAny
            // since the rest are subsets of ManyAny.
            OrDomain<?> od = (OrDomain<?>)d;
            for ( Domain dd : od.seq ) {
                if ( isManyAny( dd ) ) {
                    return true;
                }
                if ( dd.isEmpty() ) continue;
                return false;
            }
            return false;
        }
        if ( d instanceof RegexDomain ) {
            // The sequence is a ManyAny if there is at least one ManyAny, and the
            // rest are Anys or empty lists.
            boolean foundManyAny = false;
            for ( Domain dd : ((RegexDomain<?>)d).seq ) {
                if ( isManyAny( dd ) ) {
                    foundManyAny = true;
                    continue;
                }
                if ( isAny( dd ) ) continue;
                if ( isEmptyList( dd ) ) continue;
                return false;
            }
            return foundManyAny;
        }
        return false;
    }


    // TODO -- You should really put this back in.
//    public boolean isEmptyList() {
//        if ( seq.isEmpty() ) return true;
//        for ( Domain d : seq ) {
//            if ( d instanceof  RegexDomain ) {
//                if ( !( (RegexDomain)d ).isEmptyList() ) {
//                    return false;
//                }
//            } else if (d instanceof ManyDomain ) {
//                if ( ( (ManyDomain)d ).domainForEach )
//            } else {
//                return false;
//            }
//        }
//        return true;
//    }

    /**
     * Combine the two domains.  If a domain is a RegexDomain, the seq list menber
     * will join with the other domain to form a new seq list for the new RegexDomain returned.
     *
     * @param d1 the first domain to be joied
     * @param d2 the second domain to be joied
     * @param <TT> the type of list item that the RegexDomain matches against
     * @return a RegexDomain joining the domains at the level of the r.seq for any RegexDomain argument, r.
     */
    public static <TT> RegexDomain<TT> concat(Domain<?> d1, Domain<?> d2) {
        return concat(d1, d2, null);
    }

    /**
     * Combine the two domains.  If a domain is a RegexDomain, the seq list menber
     * will join with the other domain to form a new seq list for the new RegexDomain returned.
     *
     * @param d1 the first domain to be joied
     * @param d2 the second domain to be joied
     * @param cls the list item Class that the RegexDomain matches against
     * @param <TT> the type of list item that the RegexDomain matches against
     * @return a RegexDomain joining the domains at the level of the r.seq for any RegexDomain argument, r.
     */
    public static <TT> RegexDomain<TT> concat(Domain<?> d1, Domain<?> d2, Class<TT> cls) {
        // TODO -- Check cls or remove the parameter.
        if ( d1 == null && d2 == null ) return null;
        List<Domain<?>> seq1 = null;
        List<Domain<?>> seq2 = null;
        if ( d1 != null ) {
            if ( d1 instanceof RegexDomain ) {
                seq1 = ( (RegexDomain<?>)d1 ).seq;
            } else {
                seq1 = Utils.newList(d1);
            }
        }
        if ( d2 != null ) {
            if ( d2 instanceof RegexDomain ) {
                seq2 = ( (RegexDomain<?>)d2 ).seq;
            } else {
                seq2 = Utils.newList(d2);
            }
        }
        if ( seq1 == null ) return new RegexDomain<TT>( seq2 );
        if ( seq2 == null ) return new RegexDomain<TT>( seq1 );
        RegexDomain<TT> r = new RegexDomain<TT>( seq1 );
        r.seq.addAll( seq2 );
        return r;
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
        return toValueList( this );
    }

    /**
     * Set the value of the object that is wrapped by this object.
     *
     * @param value the new value to be wrapped
     */
    @Override public void setValue( List<T> value ) {
        // TODO -- do something or error
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

    @Override public String toString() {
        String s = "(" + MoreToString.Helper.toString(seq,false,true,null,
                                               null,"",",","",
                                                true) + ")";
        return s;
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

        RegexDomain<Integer> rdSelf = minusPrefix(rd, rd, null);
        // rdSelf should be (emptystring) | .*2
        assertTrue( rdSelf.contains( Utils.newList() ) );
        assertTrue( rdSelf.contains( Utils.newList(2) ) );
        assertTrue( rdSelf.contains( Utils.newList(1, 2) ) );
        assertTrue( rdSelf.contains( Utils.newList(3, 2, 1, 2) ) );

        ArrayList<Integer> intList1 = Utils.newList(3, 3, 4, 2 );
        ArrayList<Integer> intList2 = Utils.newList(3, 3, 4, 2, 1 );

        assertTrue( rd.contains( intList1 ));
        assertTrue( !rd.contains( intList2 ));

        //RegexDomain<Integer> rd1 = rd.clone();
        //RegexDomain<Integer> rd2 = rd.clone();

        rd.seq.add( new ManyDomain() );
        assertTrue( rd.contains( intList2 ) );

        rdSelf = minusPrefix(rd, rd, null);
        // rdSelf should be .*
        assertTrue( rdSelf.contains( Utils.newList() ) );
        assertTrue( rdSelf.contains( Utils.newList(2) ) );
        assertTrue( rdSelf.contains( Utils.newList(1, 2) ) );
        assertTrue( rdSelf.contains( Utils.newList(3, 2, 1, 2) ) );
        assertTrue( rdSelf.contains( Utils.newList(3, 2, 1) ) );
        assertTrue( rdSelf.contains( Utils.newList(1, 2, 3, 4) ) );

        RegexDomainString rd1 = new RegexDomainString(Utils.newList());
        rd1.charListDomain.seq.add(new SimpleDomain('3'));
        rd1.charListDomain.seq.add(new ManyDomain());
        rd1.charListDomain.seq.add(new SimpleDomain('2'));
        rd1.charListDomain.seq.add(new SimpleDomain('4'));

        RegexDomainString rd2 = new RegexDomainString(Utils.newList());
        rd2.charListDomain.seq.add(new ManyDomain());
        rd2.charListDomain.seq.add(new SimpleDomain('2'));

        OrDomain<Character> rd1minus2 = minusPrefix(rd1.charListDomain, rd2.charListDomain, null);
        System.out.println("rd1.charListDomain = " + rd1.charListDomain);
        System.out.println("rd2.charListDomain = " + rd2.charListDomain);
        System.out.println("rd1minus2 = " + rd1minus2);
        assertEquals(1, rd1minus2.seq.size());
        assertEquals(new SimpleDomain<>('4'), rd1minus2.seq.get(0));

        RegexDomainString rds1 = new RegexDomainString(Utils.newList());
        rds1.charListDomain.seq.add(new SimpleDomain('3'));
        rds1.charListDomain.seq.add(new ManyDomain());
        rds1.charListDomain.seq.add(new SimpleDomain('2'));

        RegexDomainString rds2 = new RegexDomainString(Utils.newList());
        rds2.charListDomain.seq.add(new SimpleDomain('3'));
        rds2.charListDomain.seq.add(new ManyDomain());
        rds2.charListDomain.seq.add(new SimpleDomain('2'));

//        OrDomain res = minusPrefix( rds1, rds2, null );
//        System.out.println( "res = " + res );

        Parameter<String> p1 = new StringParameter("p1", rds1, null);
        Parameter<String> p2 = new StringParameter("p2", rds2, null);

        Parameter<String> p3 = new StringParameter("p3", (Domain)null, null);

        ConstraintExpression eq = new ConstraintExpression( new Functions.EQ( p1, new Functions.Sum(p3, p2) ) ); // TODO!!

        eq.restrictDomain( new BooleanDomain( true, true ), false, null );

        Domain d = eq.getDomain( true, null );
        System.out.println("" + d);
        /*
        {
            RegexDomain<Integer> rdSelf = minusPrefix(rd, rd, null);
            // rdSelf should be (emptystring) | .*2
            assertTrue( rdSelf.contains( Utils.newList() ) );
            assertTrue( rdSelf.contains( Utils.newList(2) ) );
            assertTrue( rdSelf.contains( Utils.newList(1, 2) ) );
            assertTrue( rdSelf.contains( Utils.newList(3, 2, 1, 2) ) );

            ArrayList<Integer> intList1 = Utils.newList(3, 3, 4, 2 );
            ArrayList<Integer> intList2 = Utils.newList(3, 3, 4, 2, 1 );

            assertTrue( rd.contains( intList1 ));
            assertTrue( !rd.contains( intList2 ));

            rd.seq.add( new ManyDomain() );
            assertTrue( rd.contains( intList2 ) );

            rdSelf = minusPrefix(rd, rd, null);
            // rdSelf should be .*
            assertTrue( rdSelf.contains( Utils.newList() ) );
            assertTrue( rdSelf.contains( Utils.newList(2) ) );
            assertTrue( rdSelf.contains( Utils.newList(1, 2) ) );
            assertTrue( rdSelf.contains( Utils.newList(3, 2, 1, 2) ) );
            assertTrue( rdSelf.cont
                        from David to Everyone:    3:19  PM
            ains( Utils.newList(3, 2, 1) ) );
            assertTrue( rdSelf.contains( Utils.newList(1, 2, 3, 4) ) );

        }
        */
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
    // req x in [1..3]
    // req y in [7..10]
    // req z in [7..9]
    // req x + y = z  // <-- restrict to {true}
    // req x * y > z  // <-- restrict to {true}

    // restrict x + y's domain to z's domain [7..9]
    // how does this restrict x?
    // restrict x to [7..9] - [3..4]
    // x is retricted to [3..6]~[1..5] = [3..5]
    // how does this restrict y?
    // restrict y to [7..9] - [3..5]
    // y is retricted to [2..6]~[3..4] = [3..4]

    // restrict z's domain to x+y's domain = [6..9]
    // restrict z' domain to [7..9]










}

