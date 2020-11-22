package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.HasOwner;
import gov.nasa.jpl.ae.solver.HasIdImpl;
import gov.nasa.jpl.mbee.util.*;
import org.apache.commons.math3.distribution.IntegerDistribution;

import java.util.*;

public abstract class AbstractDistribution<T>
        extends HasIdImpl
        implements Distribution<T>, Comparable {
    Object owner;
    Distribution<T> bias = null;
    Map<String, Object> parameters = null;

    //    public abstract Distribution<Double> plus( Distribution<?> otherDist );
    //    public abstract Distribution<Double> minus( Distribution<?> otherDist );
    //    public abstract Distribution<Double> times( Distribution<?> otherDist );
    //    public abstract Distribution<Double> dividedBy( Distribution<?> otherDist );

    @Override public Object getOwner() {
        return owner;
    }

    @Override public void setOwner( Object owner ) {
        this.owner = owner;
    }

    /**
     * Get the name preceded by parent names, separated by '.'
     *
     * @param seen a list of objects that have already been visited and that are to
     *             be used to avoid infinite recursion
     * @return
     */
    @Override public String getQualifiedName( Set<Object> seen ) {
        String qn = null;
        if ( owner instanceof HasOwner ) {
            qn = ( (HasOwner)owner ).getQualifiedName( seen );
        } else if ( owner instanceof HasName ) {
            qn = "" + ( (HasName)owner ).getName();
        }
        if ( this instanceof HasName ) {
            if ( qn == null ) qn = "" + ( (HasName)this ).getName();
            else qn = qn + "." + ( (HasName)this ).getName();
        }
        return qn;
    }

    /**
     * Get the ID preceded by parent IDs, separated by '.'
     *
     * @param seen a list of objects that have already been visited and that are to
     *             be used to avoid infinite recursion
     * @return
     */
    @Override public String getQualifiedId( Set<Object> seen ) {
        String qid = null;
        if ( owner instanceof HasOwner ) {
            qid = ( (HasOwner)owner ).getQualifiedId( seen );
        } else if ( owner instanceof HasId ) {
            qid = "" + ( (HasId)owner ).getId();
        }
        if ( this instanceof HasId ) {
            if ( qid == null ) qid = "" + ( (HasId)this ).getId();
            else qid = qid + "." + ( (HasId)this ).getId();
        }
        return qid;
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
     * @param o the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object
     * is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it
     *                              from being compared to this object.
     */
    @Override public int compareTo( Object o ) {
        if ( this == o ) return 0;
        if ( o == null ) return 1;
        if ( this.getClass().equals( o.getClass() ) ) {
            int c = CompareUtils.compare(parameters, ((AbstractDistribution)o).parameters);
            if ( c != 0 ) return c;
            c = CompareUtils.compare(bias, ((AbstractDistribution)o).bias);
            return c;
        }
        if ( o instanceof HasId ) {
            return Integer.compare( getId(), ( (HasId<Integer>)o ).getId() );
        }
        return CompareUtils.compare( this, o, false, false );
    }

    @Override public boolean equals( Object obj ) {
        int c = compareTo(obj);
        return c == 0;
    }

    public static boolean cachingSupport = false;
    public static Map< Integer, Map< Integer, Double > >
            supportFactorCache = new HashMap<>();
    public static Map< Integer, Map< Integer, List< Pair< Double,Double > > > >
            supportSubtractCache = new HashMap<>();

    /**
     * Compute a factor to correct for biased sampling over a subset of the possible
     * values.  If using d2 as a bias for sampling d1, and the possible values for
     * d2 are different from those of d1, return the probability that a sample from
     * d1 would be in the support range of d2.
     * @param d1
     * @param d2
     * @return
     */
    public static double supportFactor(Distribution d1, Distribution d2) {
        if ( d1 == null || d2 == null ) return 1.0;
        Double fc = null;
        if ( cachingSupport ) {
            fc = Utils.get( supportFactorCache, d1.hashCode(), d2.hashCode() );
            if ( fc != null ) return fc;
        }
        // Subtract the value range of d2 from that of d1 to find the ranges of
        // values in d1 that are not values of d2.
        List<Pair<Double, Double>> ranges = supportSubtract( d1, d2 );
        // Find the probabilty that a sample of d1 is not in the range of values
        // for d2 by using the CDF of d1 to sum the probabilities of being in ranges
        // that were just computed.
        if ( ranges == null ) return 1.0;
        double sum = 0.0;
        for ( Pair<Double, Double> p : ranges ) {
            sum += d1.cumulativeProbability( p.second ) - d1.cumulativeProbability( p.first );
        }
        // Now subtract that probability from 1 to get the probability of a d1
        // sample being in the range of d2's possible values.
        double f = 1.0 - sum;
        String ownerName = d1.getOwner() == null ? "" : d1.getQualifiedName( null );
        System.out.println("supportFactor(" + ownerName + "=" + d1 + ", " + d2 +
                           ") = cumulative probability of being in " + ranges +
                           ", which is " + f);
        if ( cachingSupport ) {
            Utils.put( supportFactorCache, d1.hashCode(), d2.hashCode(), f );
        }
        return f;
    }
    public static List< Pair< Double,Double > > supportSubtract(Distribution d1, Distribution d2) {
        if ( d1 == null || d2 == null ) return null;
        if ( cachingSupport ) {
            List<Pair<Double, Double>> cachedRanges =
                    Utils.get( supportSubtractCache, d1.hashCode(), d2.hashCode() );
            if ( cachedRanges != null ) {
                return cachedRanges;
            }
        }
        ArrayList< Pair< Double,Double > > ranges = new ArrayList<>();
        Double lb1 = d1.supportLowerBound();
        Double lb2 = d2.supportLowerBound();
        Double ub1 = d1.supportUpperBound();
        Double ub2 = d2.supportUpperBound();
        // make sure they overlap first
        if ( ub2 < lb1 || lb2 > ub2 ) {
            if ( cachingSupport ) {
                Utils.put( supportSubtractCache, d1.hashCode(), d2.hashCode(),
                           ranges );
            }
            return ranges;
        }
        if ( lb1 < lb2 ) {
            ranges.add( new Pair<>( lb1, lb2 ) );
        }
        if ( ub1 > ub2 ) {
            ranges.add( new Pair<>( ub2, ub1 ) );
        }
        if ( cachingSupport ) {
            Utils.put( supportSubtractCache, d1.hashCode(), d2.hashCode(),
                       ranges );
        }
        return ranges;
    }

    @Override public Sample<T> sample( Distribution<T> bias ) {
        if ( bias == null ) return null;
        Sample<T> s = bias.sample();
        double w = pdf( s.value() ) / bias.pdf( s.value() );
        // This is not the right place to use the support factor.
//        double supportFactor = supportFactor( this, bias );
//        w *= supportFactor;
        return new SimpleSample<T>( s.value(), w );
    }

    public Double biasSupportFactor() {
        Double f = biasSupportFactor( bias );
        return f;
    }

    @Override public Double biasSupportFactor( Distribution<T> bias ) {
        Double f = supportFactor( this, bias );
        return f;
    }


    // Override methods in Wraps interface.

    /**
     * Return true if there is an object wrapped.  If getValue() returns null, this call distinguishes whether null is a
     * valid value or not.  If multiple objects are wrapped, then getValue() may return null also in this case.
     *
     * @return true if there is a wrapped value
     */
    @Override public boolean hasValue() {
        return !isEmpty();// && allValuesSame();
    }

    @Override public boolean hasMultipleValues() {
        return size() > 1 && !allValuesSame();
    }

    public boolean allValuesSame() {
        return probability( getValue( false ) ) == 1.0;
    }

    /**
     * @param className the name of {@code this} class (which should be the class
     *                  redefining this method or a subclass) with generic parameters
     * @return the name of the type for the object that would be wrapped by an
     * object with a class name of {@code className}; this should be the
     * type of the return value for {@link Wraps<>.getValue(boolean)}.
     */
    @Override public String getTypeNameForClassName( String className ) {
        // There should only be one generic parameter of type V
        return ClassUtils.parameterPartOfName( className, false );
    }

    /**
     * @return the primitive class corresponding to the object wrapped by this
     * object (possibly in several layers)
     */
    @Override public Class<?> getPrimitiveType() {
        Class< ? > c = null;
        Class< ? > t = getType();
        if ( t != null ) {
            c = ClassUtils.primitiveForClass( t );
            //V v = getFirstValue();
            if ( c == null ) {
                T v = getValue( false );
                if ( c == null && v != null
                     && Wraps.class.isInstance( v ) ) {// isAssignableFrom( getType() ) ) {
                    c = ( (Wraps< ? >)v ).getPrimitiveType();
                }
            }
        }
        return c;
    }

    public boolean isEmpty() {
        if ( supportUpperBound() != null || supportLowerBound() != null ||
             mean() != null ) {
            return false;
        }
        return true;
    }

    protected static Set<Class> discreteClassSet =
            Utils.newSet( Long.class, long.class, Integer.class, int.class,
                          Boolean.class, boolean.class, String.class );

    public boolean isDiscrete() {
        return discreteClassSet.contains( getType() );
    }

    public long size() {
        if ( supportUpperBound() != null && supportLowerBound() != null ) {
            if ( supportUpperBound().equals(supportLowerBound()) ) {
                return 1;
            }
            if ( isDiscrete() ) {
                long s = ((Double)(supportUpperBound() - supportLowerBound())).longValue();
                return s;
            } else {
                return Long.MAX_VALUE;
            }
        }
        Number m = mean();
        Double v = m == null ? null : variance();
        if ( m != null && v != null ) {
            if ( v == 0.0 ) {
                return 1;
            }
            if ( isDiscrete() ) {
                long s = ((Double)(supportUpperBound() - supportLowerBound())).longValue();
                return s;
            } else {
                return Long.MAX_VALUE;
            }
        }
        return -1; // unknown
    }

    public T firstValue() {
        Number d = supportLowerBound();
        if ( d == null ) {
            d = mean();
        }
        if ( d == null ) {
            d = supportUpperBound();
        }
        if ( d != null ) {
            Pair<Boolean, T>
                    p = ClassUtils.coerce(d, getType(), false);
            if ( p != null && p.first != null && p.first ) {
                return p.second;
            }
        }
        return null;
    }

    public boolean allValuesEqual() {
        if ( size() == 1 ) return true;
        return false;
    }

    /**
     * @param propagate whether or not to propagate dependencies in order to determine
     *                  what object is wrapped
     * @return the object that is wrapped by this object
     */
    @Override public T getValue( boolean propagate ) {
        Number m = mean();
        Double v = variance();
        if ( m != null && v != null && v == 0.0 ) {
            return (T)m;
        }
        if ( isEmpty() ) return null;
        T value = firstValue();
        if ( size() > 1 ) {
            if (!this.allValuesEqual()) {
                Debug.error(false, false, "Warning! Calling getValue() on TimeVaryingMap with multiple values.  Returning null for " + this );
                return null;
            }
        }
        return value;
    }

//    /**
//     * Set the value of the object that is wrapped by this object.
//     *
//     * @param value the new value to be wrapped
//     */
//    @Override public void setValue( T value ) {
//        //
//    }
}
