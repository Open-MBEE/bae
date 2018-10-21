package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.HasOwner;
import gov.nasa.jpl.ae.solver.HasIdImpl;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.HasId;
import gov.nasa.jpl.mbee.util.HasName;
import org.apache.commons.math3.distribution.IntegerDistribution;

import java.util.Set;

public abstract class AbstractDistribution<T>
        extends HasIdImpl
        implements Distribution<T>, Comparable {
    Object owner;

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
        if ( o instanceof HasId ) {
            return Integer.compare( getId(), ( (AbstractDistribution)o ).getId() );
        }
        return CompareUtils.compare( this, o, false, false );
    }
}
