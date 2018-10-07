package gov.nasa.jpl.ae.util.distributions;

import gov.nasa.jpl.ae.event.HasOwner;
import gov.nasa.jpl.mbee.util.HasId;
import gov.nasa.jpl.mbee.util.HasName;
import org.apache.commons.math3.distribution.IntegerDistribution;

import java.util.Set;

public abstract class AbstractDistribution<T> implements Distribution<T> {
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
}
