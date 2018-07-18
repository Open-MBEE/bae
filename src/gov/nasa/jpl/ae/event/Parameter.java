package gov.nasa.jpl.ae.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import gov.nasa.jpl.ae.solver.AbstractRangeDomain;
import gov.nasa.jpl.ae.solver.CollectionTree;
import gov.nasa.jpl.ae.solver.Constraint;
import gov.nasa.jpl.ae.solver.Domain;
import gov.nasa.jpl.ae.solver.HasConstraints;
import gov.nasa.jpl.ae.solver.HasDomain;
import gov.nasa.jpl.ae.solver.HasIdImpl;
import gov.nasa.jpl.ae.solver.ObjectDomain;
import gov.nasa.jpl.mbee.util.*;
import gov.nasa.jpl.ae.solver.RangeDomain;
import gov.nasa.jpl.ae.solver.Satisfiable;
import gov.nasa.jpl.ae.solver.Variable;

/**
 *
 */
public class Parameter< T > extends HasIdImpl implements Cloneable, Groundable,
                            Comparable< Parameter< ? > >, Satisfiable, Node,
                            Variable< T >, LazyUpdate, HasConstraints, HasOwner,
                            MoreToString, Deconstructable {
  public static final Set< Parameter< ? > > emptySet =
      new TreeSet< Parameter< ? > >();

  /**
   * Can values be selected or changed for Parameters when not grounded or in
   * order to satisfy a constraint.
   */
  public static boolean allowPickValue = true;

  // These are for debug validation.
  public static boolean mayPropagate = true;
  public static boolean mayChange = true;

  protected String name = null;
  public Domain< T > domain = null;
  protected T value = null;
  protected ParameterListener owner = null; // REVIEW -- Only one listener!
  protected boolean stale;
  protected List< Constraint > constraintList = new ArrayList< Constraint >();
  protected boolean deconstructed = false;

  public Parameter() {}

  public Parameter( String n, Domain< T > d, ParameterListener o ) {
    name = n;
    domain = d;
    owner = o;
    stale = true; // not grounded
  }

  // public Parameter( String n, T v ) {
  // name = n;
  // value = v;
  // }
  public Parameter( String n, Domain< T > d, T v, ParameterListener o ) {
    name = n;
    domain = d;
    value = v;
    setValueOwner(value);
    owner = o;
    stale = !isGrounded( true, null );
  }

  @SuppressWarnings( "unchecked" )
  public Parameter( String n, Domain< T > d, FunctionCall fc,
                    ParameterListener o, boolean propagate ) {
    name = n;
    domain = d;
    if ( fc != null ) {
      try {
          value = (T)fc.evaluate( propagate );
          setValueOwner(value);
      } catch ( IllegalAccessException e ) {
        // TODO Auto-generated catch block
        //e.printStackTrace();
      } catch ( InvocationTargetException e ) {
        // TODO Auto-generated catch block
        //e.printStackTrace();
      } catch ( InstantiationException e ) {
        // TODO Auto-generated catch block
        //e.printStackTrace();
      }
    }
    owner = o;
    stale = !isGrounded( true, null );
  }

//  public Parameter( String n, Domain< T > d, Parameter<T> v, ParameterListener o ) {
//    this(n, d, (v == null) ? null : v.getValue(), o);
//  }

  public Parameter( Parameter< T > parameter ) {
    name = parameter.name;
    value = parameter.value;
    setValueOwner(value);
    domain = parameter.domain;
    owner = parameter.owner;
    stale = !isGrounded( true, null );
  }

  /*
  public Parameter( String n, Domain d, Expression<T> expression,
                    ParameterListener o ) {
    name = n;
    domain = d;
    owner = o;
    if ( expression != null ) {
      if ( o instanceof ParameterListenerImpl ) {
        ParameterListenerImpl pli = (ParameterListenerImpl)o;
        pli.addDependency( this, expression );
      }
    }
    stale = true; // not grounded
  }
*/

  //@Override
  public boolean isDeconstructed() {
    if ( deconstructed ) return true;
    if ( value instanceof Deconstructable ) {
//      if ( ( (Deconstructable)value ).isDeconstructed() ) {
//        return true;
//      }
    }
    return false;
  }

  @Override
  public void deconstruct() {
    if ( isDeconstructed() ) return;
    name = "DECONSTRUCTED_"
            + ( getOwner() == null ? "" : getOwner().getName() + "_"
                                          + getOwner().getId() + "_" ) + name;
    value = null; // Can't deconstruct what we don't own, so set to null.
    domain = null; // This may be shared by others.
    owner = null;
    stale = true;
    // The parameter does own its constraints.
    for ( Constraint c : constraintList ) {
      if ( c instanceof Deconstructable ) {
        ( (Deconstructable)c ).deconstruct();
      }
    }
    deconstructed = true;
  }


  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#clone()
   */
  @Override
  public Object clone() {
    return new Parameter< T >( this );
  }

  @Override
  public boolean equals( Object val ) {
    if ( val instanceof Parameter) {
      return compareTo((Parameter)val) == 0;
    }
    return false;
  }

  // THis used to be equals until problems occurred because it was not
  // consistent with compareTo().
  public boolean valEquals( Object val ) {
    if ( this == val ) return true;
    if ( value == val ) return true;
    if ( value == null ) return false;
    if ( val == null ) return false;
    if ( val instanceof Parameter && ( (Parameter<?>)val ).valueEquals( value ) ) {
      return true;
    }
    //if ( val instanceof Parameter ) return ( compareTo( (Parameter<?>)val ) == 0 );
    if ( value.equals( val ) ) return true;
    return false;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.HasName#getName()
   */
  @Override
  public String getName() {
    return name;
  }

  public void setName( String name ) {
    this.name = name;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.HasOwner#getQualifiedName(java.util.Set)
   */
  @Override
  public String getQualifiedName(java.util.Set<Object> seen) {
    String n = HasOwner.Helper.getQualifiedName( this, seen );
    return n;
  };

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.HasOwner#getQualifiedId(java.util.Set)
   */
  @Override
  public String getQualifiedId(java.util.Set<Object> seen) {
    String i = HasOwner.Helper.getQualifiedId( this, seen );
    return i;
  };

  /**
   * @return the domain
   */
  @Override
  public Domain< T > getDomain( boolean propagate, Set< HasDomain > seen ) {
    return domain;
  }

  @Override
  public Domain< T > getDomain() {
    return getDomain( false, null );
  }

  /**
   * @param domain
   *          the domain to set
   */
  @Override
  public void setDomain( Domain< T > domain ) {
    setDomain( domain, true );
  }
  public void setDomain( Domain< T > domain, boolean propagate ) {
    this.domain = domain;
    this.constraintList.clear();
    if ( propagate && owner != null ) {
      //owner.setStaleAnyReferencesTo( this );
      owner.handleDomainChangeEvent( this, null );
    }
  }

  public T getValueNoPropagate() {
    return value;
  }

  @Override
  public T getValue( boolean propagate ) {
    if ( propagate ) return getValue();
    return getValueNoPropagate();
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#hasValue()
   */
  @Override
  public boolean hasValue() {
    return value != null || (domain != null && domain.isNullInDomain());
  }

  public T getValue() {
    if ( Debug.isOn() ) Debug.outln( "Parameter.getValue() start: " + this );
    if ( Debug.isOn() ) assert mayPropagate;
    if ( isStale() ) {
      if ( owner != null ) {
        owner.refresh( this );
        if ( Debug.isOn() ) Debug.outln( "Parameter.getValue() refreshed: " + this );
      } else {
        setStale( false );
        if ( Debug.isOn() ) Debug.outln( "Parameter.getValue() no owner for " + this );
      }
    }
    if ( Debug.isOn() ) Debug.outln( "Parameter.getValue() finish: " + this );
    return value;
  }

  /**
   * @return the Parameter's value, the domain's lower bound, or null.
   */
  public T getValueOrMin() {
    if ( value != null ) return value;
    if ( domain instanceof RangeDomain ) {
      return (T)((RangeDomain<T>)domain).getLowerBound();
    }
    return null;
  }

  /**
   * @return the Parameter's value, the domain's upper bound, or null.
   */
  public T getValueOrMax() {
    if ( value != null ) return value;
    if ( domain instanceof RangeDomain ) {
      return (T)((RangeDomain<T>)domain).getUpperBound();
    }
    return null;
  }

  public Object getMember( String fieldName ) {
    return getMember( fieldName, false );
  }
  public Object getMember( String fieldName, boolean suppressExceptions ) {
    T v = getValueNoPropagate();
    if ( v == null ) return null;
    Object f = ClassUtils.getFieldValue( v, fieldName, suppressExceptions );
    return f;
  }

  public <T1> boolean valueEquals( T1 otherValue ) {
    if ( this == otherValue ) return true;
    if ( !hasValue() ) {
      if ( otherValue instanceof Wraps ) {
        return !((Wraps) otherValue).hasValue();
      }
      return otherValue == null;  // REVIEW -- not sure about this
    } else if ( otherValue instanceof Wraps && !((Wraps) otherValue).hasValue()) {
      return false;
    }
    if ( value == otherValue ) return true;
    //if ( value == null || otherValue == null ) return false;

    if ( value != null && value != this && value.equals( otherValue ) ) return true;
    if ( otherValue instanceof Wraps ) {
      Object w = ((Wraps)otherValue).getValue(false);
      if ( otherValue != w ) {
        return valueEquals( w );
      }
    }
    return false;
  }

  public Class< ? > getType() {
    if ( domain != null && domain.getType() != null ) {
      return domain.getType();
    }
    if ( value != null ) {
      Class< ? extends Object > cls = value.getClass();
//      if ( cls != null ) {
//        if ( cls.equals( Integer.class ) ) {
//          return Long.class;
//        }
//        if ( cls.equals( Float.class ) ) {
//          return Double.class;
//        }
//      }
      return cls;
    }
    return null;
  }

  public Parameter< T > assignValue( T value ) {
    setValue( value ); // TODO -- REVIEW -- use a global usingLazyUpdate?
    return this;
  }

  @Override
  public void setValue( T value ) {
    setValue( value, true ); // TODO -- REVIEW -- use a global usingLazyUpdate?
  }
  // setValue( value, false ) is lazy/passive updating
  // setValue( value, true ) is proactive updating
  protected void setValue( T val, boolean propagateChange ) {
    setValue( val, propagateChange, null );
  }
  protected void setValue( T val, boolean propagateChange, Set<HasParameters> seen ) {
    String valString = null;
    //Debug.turnOn();
    if ( Debug.isOn() ) {
      valString = MoreToString.Helper.toShortString( val );
      Debug.outln( "Parameter.setValue(" + valString + ") start: " + this.toString( true, false, null ) );
    }
    if ( Debug.isOn() ) assert !propagateChange || mayPropagate;
    if ( Debug.isOn() )assert mayChange;
    T castVal = null;
    try {
      try {
        castVal = (T)Expression.evaluate( val, getType(), propagateChange, false);
      } catch ( Throwable t ) {
      }
      val = castVal;
      if ( Debug.isOn() ) valString = MoreToString.Helper.toLongString( val );
    } catch ( ClassCastException cce ) {
      cce.printStackTrace();
    }
    if ( val != null && owner != null ) {
      Object newVal = owner.translate(this, val, getType());
      if ( Debug.isOn() ) {
        Debug.outln(" $$$$$$$$$$$$$$ $$$$$$$$$$$$$$$ translate(" + val + ", type=" + getType() + ") = " + newVal + " $$$$$$$$$$$$$ $$$$$$$$$$$");
      }
      if ( newVal != null ) val = (T)newVal;
    } else {
      if ( Debug.isOn() ) {
        Debug.outln(" $$$$$$$$$$$$$$ $$$$$$$$$$$$$$$ DID NOT CALL TRANSLATE FOR " + this + "  $$$$$$$$$$$$$ $$$$$$$$$$$");
      }
    }
    boolean changing = !valueEquals( val );
    if ( Debug.isOn() ) Debug.outln( "Parameter.setValue(" + valString
                                     + "): changing = " + changing );
    if ( changing ) {
      if ( owner != null ) {
        if ( Debug.isOn() ) Debug.outln( "Parameter.setValue(" + valString
                                         + "): setStaleAnyReferencesTo("
                                         + this.toString( true, false, null ) + ")" );
        setValueOwner(val);
        // lazy/passive updating
        owner.setStaleAnyReferencesTo( this, null );

        // set isGrounded constraint stale
        Collection<Constraint> constraints = getConstraints(true, null);
        if ( constraints != null ) {
          for ( Constraint c : constraints ) {
            if ( c instanceof ConstraintExpression
                 && ( (ConstraintExpression)c ).expression instanceof Call ) {
              ((Call)((ConstraintExpression)c).expression).setStaleAnyReferencesTo(this, null);
            //.setStale( true );
            } else if ( c instanceof ParameterListener ) {
              ((ParameterListener)c).setStaleAnyReferencesTo( this, null );
            }
          }
        }
      } else {
        if ( Debug.isOn() ) Debug.outln( "Parameter.setValue(" + valString
                                         + "): owner is null" );
      }
      valString = MoreToString.Helper.toString( val, true, false, null );
      //if ( val instanceof TimeVarying || (val instanceof Wraps && ((Wraps)val).getValue( false ) instanceof TimeVarying)) {
//            System.out.println(
//                    " $$$$$$$$$$$$$$   " + this.name + "@" + this.id + ".setValue(" + valString + "): " + " -- previous value: " + MoreToString.Helper.toLongString(  this ) + "   $$$$$$$$$$$$$" );
      //}
      if ( Debug.isOn() ) {
        Debug.outln(" $$$$$$$$$$$$$$   setValue(" + val + "): " + this.toString( true, false, null ) + "   $$$$$$$$$$$$$");
      }

      T oldValue = this.value;
      this.value = val;

      // add reference
      if ( this.value instanceof Deconstructable ) {
        ((Deconstructable)this.value).addReference();
      }
      // dereference
      if ( oldValue instanceof Deconstructable ) {
        ((Deconstructable)oldValue).subtractReference();
      }


      if ( Debug.isOn() ) Debug.outln( "Parameter.setValue(" + valString
                                       + "): value set!" );
      //constraintList.clear();
      if ( owner != null ) {// && propagateChange ) {  // TODO -- add propagateChange back in?
        if ( Debug.isOn() ) Debug.outln( "Parameter.setValue(" + valString
                                         + "): handleValueChangeEvent("
                                         + this.toString( true, false, null ) + ")" );
        owner.handleValueChangeEvent( this, null );
      }
    }
    setStale( false );
    if ( Debug.isOn() ) Debug.outln( "Parameter.setValue(" + valString + ") finish: " + this.toString( true, false, null ) );
    Debug.turnOff();
  }

  protected boolean setValueOwner( T val ) {
    if ( val instanceof HasOwner ) {
      HasOwner ho = (HasOwner)val;
      if ( ho.getOwner() == null ) {
        ho.setOwner(this);
        return true;
      } else if ( ho.getOwner() instanceof Variable && ho.getOwner() != this) {
        if ( ho != ( (Variable)ho.getOwner() ).getValue( false ) ) {
          ho.setOwner(this);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * @return the owner
   */
  public ParameterListener getOwner() {
    return owner;
  }

  /**
   * @param owner the owner to set
   */
  public void setOwner( ParameterListener owner ) {
    this.owner = owner;
  }

  @Override
  public boolean isGrounded(boolean deep, Set< Groundable > seen) {
    Pair< Boolean, Set< Groundable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;
    //if ( owner == null ) return false;
    if ( deep && value instanceof Groundable ) {
      return ( (Groundable)value ).isGrounded(deep, seen);
    }
    return (domain == null || value != null || domain.isNullInDomain());
  }

//  // Override this!
//  // REVIEW -- consider making this an interface &/or abstract class.
//  public T getDefaultValue() {
//    return null;
//  }

  public boolean refresh() {
    if ( owner != null ) {
      if ( owner.refresh( this ) ) {
        return true;
      }
    }
    return false;
  }

  public static boolean setAllowPickValue( boolean allow ) {
    allowPickValue = allow;
    return true;
  }

  @Override
  public boolean pickValue() {
    if ( Random.global.nextBoolean() ) {
      return ownerPickValue();
    }
    T value = pickRandomValue();
    if ( value != null ) {
      if ( !valueEquals( value ) ) {
        if ( Debug.isOn() ) Debug.outln( "////////////////////   picking " + value + " for " + this );
        setValue( value );
        return true;
      }
    }
    return false;
  }

  @Override
  public T pickRandomValue() {
    if ( domain == null ) {
      return null;
    }
    T newValue = null;
    try {
      newValue = (T)domain.pickRandomValue();
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    String ownerStr = (owner == null) ? "?" : owner.getName();
    if ( Debug.isOn() ) Debug.outln( "Picking random value for " + ownerStr + "."
                        + this.name + " from " + this.domain + " --> "
                        + newValue );
    return newValue;
  }

  @Override
  public boolean ground( boolean deep, Set< Groundable > seen ) {
    Pair< Boolean, Set< Groundable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;
    //if ( owner == null ) return false;
    if ( isGrounded(deep, null) ) return true;
    if ( refresh() ) return true;
    if ( isDependent()) return false;

    if ( deep && value instanceof Groundable ) {
      ((Groundable)value).ground(deep, seen);
      if ( isGrounded(deep, null) ) return true;
    }

    if (Parameter.allowPickValue ){
      T newValue = pickRandomValue();
      if ( newValue != null ) {
        setValue( newValue );
        return true;
      }
    }

    // If the domain is an ObjectDomain, ground by constructing a new object.
    // This may contribute to thrashing in construction/deconstruction.
    if (value == null && domain instanceof ObjectDomain && !domain.isNullInDomain()) {
     Object o = ((ObjectDomain)domain).constructObject();
     if (o != null) {
       setValue((T)o);
     }
    }
    
    return isGrounded(deep, null);
  }

  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo( Parameter< ? > o ) {
    return compareTo( o, false );
  }
  public int compareTo( Parameter< ? > o, boolean checkId ) {
    if ( this == o ) return 0;
    if ( o == null ) return 1; // REVIEW -- okay for o to be null? complain?
//    if ( Timepoint.isComparableToTimepoint( o ) ) {
//      return super.compareTo( o );
//    }

    if ( checkId ) {
      return CompareUtils.compare( getId(), o.getId() );
    }

    int compare = 0;
    compare = CompareUtils.compare( getValueNoPropagate(), o.getValueNoPropagate(), true );
    if ( compare != 0 ) return compare;

    compare = CompareUtils.compare( name, o.name, true );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compare( getClass().getName(), o.getClass().getName(), true );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compare( getDomain(), o.getDomain(), true );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compare( getType(), o.getType(), true );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compare( getOwner(), o.getOwner(), true );
    if ( compare != 0 ) return compare;
    return compare;
  }

  public boolean inDomain() {
    boolean inDom = false;
    try {
      inDom = domain == null || domain.magnitude() == 0
              || domain.contains( value );
    } catch ( ClassCastException e ) {
      if ( Debug.isOn() ) Debug.errln( "Warning! Parameter value and domain types do not match! " + this );
      if ( value instanceof Parameter ) {
        if ( Debug.isOn() ) Debug.errln( "Warning! Parameter inside Parameter! " + this );
        inDom = ( (Parameter<?>)value ).inDomain();
      }
    }
    return inDom;
  }

  @Override
  public boolean isSatisfied(boolean deep, Set< Satisfiable > seen) {
    Pair< Boolean, Set< Satisfiable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;
    //if ( owner == null ) return false;
    boolean nullDomain = domain == null;
    if ( nullDomain ) return true;
    boolean emptyDomain = domain.magnitude() == 0;
    if ( emptyDomain ) return true;
    boolean grounded = isGrounded(deep, null);
    boolean stale = isStale();
    boolean inDom = inDomain();
    if (!(grounded && !stale && inDom)) return false;
    T v = getValueNoPropagate();
    if ( deep && v != null && v instanceof Satisfiable ) {
      if ( !((Satisfiable)v).isSatisfied(deep, seen) ) return false;
    }
    return true;
  }

  @Override
  public boolean satisfy(boolean deep, Set< Satisfiable > seen) {
    Pair< Boolean, Set< Satisfiable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;
    if ( owner == null ) return false;
    if ( isSatisfied(deep, null) ) return true;
    ground(deep, null);
    getValue();
    if ( isSatisfied(deep, null) ) return true;
    if ( deep && value instanceof Satisfiable ) {
      ((Satisfiable)value).satisfy(deep, seen);
    }
    if ( isSatisfied(deep, null) ) return true;
    if ( !isDependent() && Parameter.allowPickValue){
      T newValue = pickRandomValue();
      if ( newValue != null ) {
        setValue( newValue, true );
      }
      if ( isSatisfied(deep, null) ) return true;
      ownerPickValue();
      if ( isSatisfied(deep, null) ) return true;
    }
    return false;
  }

  public boolean isDependent(){
    if (owner != null && !owner.isFreeParameter( this, true, null )) {
      return true;
    }
    return false;
  }

  protected boolean ownerPickValue() {
    if ( owner != null ) {//&& owner instanceof ParameterListenerImpl ) {
      if ( ((ParameterListener)owner).pickParameterValue( this ) ) return true;
    }
    return false;
  }

  @Override
  public String toShortString() {
    return toString(false, false, false, null, null);
    //return MoreToString.Helper.toShortString( value );
  }

  @Override
  public String toString() {
    return toString( true, false, true, null, null );
  }

  public String toString( boolean withOwner, boolean withHash,
                          boolean deep, Set< Object > seen,
                          Map< String, Object > otherOptions ) {
    Pair< Boolean, Set< Object > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) deep = false;
    seen = pair.second;
    StringBuffer sb  = new StringBuffer();
    if ( withOwner && getOwner() != null ) {
      if ( Utils.isNullOrEmpty( getOwner().getName() ) ) {
        if ( getOwner() instanceof Variable ) {
          sb.append( "<VAR>@" + getOwner().hashCode() + ":");
        } else if ( getOwner() instanceof Event ) {
          sb.append( "<EVT>@" + getOwner().hashCode() + ":");
        } else {
          sb.append( "<OBJ>@" + getOwner().hashCode() + ":");
        }
      } else {
        sb.append( getOwner().getName() + ":");
      }
    }
    if ( !Utils.isNullOrEmpty( getName() ) || withOwner || deep || withHash ) {
      if ( Utils.isNullOrEmpty( getName() ) )
        sb.append( "_" );
      else
        sb.append( getName() );
    }
    if ( withHash ) {
      sb.append("@" + hashCode() );
    }
    if ( !Utils.isNullOrEmpty( getName() ) || withOwner || deep || withHash ) {
      sb.append( "=" );
    }
    if ( !deep ) {
      sb.append( value == null ? "null" : MoreToString.Helper.toShortString( value ) );
      // TODO -- It seems like toShortString() should take a withHash argument.
      // Maybe add an additional call to the interface for this.
      if ( value != null && withHash && !ClassUtils.isPrimitive( value.getClass() ) ) {
        sb.append("@" + value.hashCode());
      }
    } else if ( isGrounded( false, Utils.asSet(seen, Groundable.class) ) ) {
      T value = getValueNoPropagate();
      String valueString = null;
      if ( value instanceof MoreToString ) {
        valueString =
            toString( (MoreToString)value, withOwner, withHash, deep, seen );
      } else {
        valueString = "" + value;
      }
      sb.append( valueString );
    } else if ( getDomain() != null ) {
      sb.append( getDomain() );
    } else {
      sb.append( "(ungrounded, null domain)" );
    }
    return sb.toString();
  }

  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen ) {
    return toString( true, withHash, deep, seen, null );
  }

  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen,
                          Map< String, Object > otherOptions ) {
    boolean withOwner = true;
    if ( otherOptions != null ) {
      Object o = otherOptions.get( "withOwner" );
      if ( o instanceof Boolean ) {
        withOwner = ((Boolean)o).booleanValue();
      } else if ( Utils.isFalse( o, false ) ) {
        withOwner = false;
      }
    }
    return toString( withOwner, withHash, deep, seen, otherOptions );
  }

  /**
   * Helper function for passing a withOwner option used by Parameter in
   * MoreToString.toString(...). Be careful to avoid infinite recursive calls,
   * such as calling Parameter.toString(this,...) from within this.toString().
   *
   * @return the object's MoreToString.toString(...) after adding withOwner to
   *         the options.
   */
  public static String toString( MoreToString object,
                                 boolean withOwner, boolean withHash,
                                 boolean deep, Set< Object > seen,
                                 Map< String, Object > otherOptions ) {
    otherOptions.put("withOwner", withOwner);
    return object.toString( withHash, deep, seen, otherOptions );
  }

  /**
   * Helper function for passing a withOwner option used by Parameter in
   * MoreToString.toString(...)
   *
   * @return the object's MoreToString.toString(...) after adding withOwner to
   *         the options.
   */
  public static String toString( MoreToString object,
                                 boolean withOwner, boolean withHash,
                                 boolean deep, Set< Object > seen ) {
    Map<String, Object > otherOptions = new TreeMap< String, Object >();
    return toString( object, withOwner, withHash, deep, seen, otherOptions );
  }

  /**
   * Helper function for MoreToString.toString() when it is not known whether
   * the input object implements MoreToString.
   *
   * @return ((MoreToString)object).toString(...) with the same options passed
   *         if the object does implement MoreToString; otherwise return
   *         object.toString().
   */
  public static String toString( Object object,
                                 boolean withOwner, boolean withHash,
                                 boolean deep, Set< Object > seen ) {
    // REVIEW -- Can we assume that this gets called only when object does not
    // implement MoreToString?
    if ( object == null ) return "null";
    if ( object instanceof MoreToString ) {
      Map<String, Object > otherOptions = new TreeMap< String, Object >();
      return toString( (MoreToString)object, withOwner, withHash, deep, seen,
                       otherOptions );
    }
    return object.toString();
  }
  /**
   * Helper function for MoreToString.toString() when it is not known whether
   * the input object implements MoreToString.
   *
   * @return ((MoreToString)object).toString(...) with the same options passed
   *         if the object does implement MoreToString; otherwise return
   *         object.toString().
   */
  public static String toString( Object object,
                                 boolean withOwner, boolean withHash,
                                 boolean deep, Set< Object > seen,
                                 Map< String, Object > otherOptions ) {
    if ( object == null ) return "null";
    if ( object instanceof MoreToString ) {
      return toString( (MoreToString)object, withOwner, withHash, deep, seen,
                       otherOptions );
    }
    return object.toString();
  }


  public String toKString() {
    String name = this.getName();
    Class<?> cls = this.getType();
    String classString;

    if (cls.equals( Integer.class )) {
      classString = "Int";
    }
    else if (cls.equals(Boolean.class)) {
      classString = "Bool";
    }
    else if (cls.equals(Double.class)) {
      classString = "Real";
    }
    else if (cls.equals(String.class)) {
      classString = "String";
    }
    else {
      classString = cls.getSimpleName();
    }

    return name + " : " + classString;
  }


  @Override
  public boolean isStale() {
    return stale;
  }

  @Override
  public void setStale( boolean staleness ) {
    if ( Debug.isOn() ) {
      if ( stale != staleness ) Debug.outln( "setStale(" + staleness + "): "
                                                    + toShortString() );
      Debug.outln( "setStale(" + staleness + ") to " + this );
    }
    stale = staleness;
  }

  @Override
  public void setStale(boolean staleness, boolean deep, Set<LazyUpdate> seen) {
    Pair< Boolean, Set< LazyUpdate > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return;
    seen = pair.second;

    setStale(staleness);
  }

  public Collection< Constraint > getConstraints(boolean deep,
                                                 Set<HasConstraints> seen ) {
    Pair< Boolean, Set< HasConstraints > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    //if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();

    // check for cached constraints
//    if ( !Utils.isNullOrEmpty( constraintList ) ) return constraintList;
//    if ( constraintList == null ) {
      constraintList = new ArrayList< Constraint >();
//    }

    // get domain constraints
    Method method;
    if ( domain != null && domain instanceof AbstractRangeDomain
         && ( value == null || value instanceof Comparable ) ) {
      Collection<Constraint> s = ((AbstractRangeDomain<T>) domain).getConstraints(this);
      constraintList.addAll( s );
    } else {
      try {
        method = getClass().getMethod( "inDomain", (Class< ? >[])null );
        constraintList.add( new ConstraintExpression( new FunctionCall( this, method,
                                                               (Object[])null, (Class<?>)null ) ) );
      } catch ( NoSuchMethodException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( SecurityException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
//    NotEquals< T > f =
//        new Functions.NotEquals< T >( new Expression< T >( value ),
//                                      new Expression< T >( (T)null ) );
//    cList.add( new ConstraintExpression( f ) );

    // get constraints in the value
    if ( deep ) {
      T v = getValueNoPropagate();
      if ( v != null &&
//           v instanceof ParameterListenerImpl ) {
//        cList.addAll( ((ParameterListenerImpl)v).getConstraints( deep, seen ) );
//      } else if ( v != null &&
                  v instanceof HasConstraints ) {
        Collection<Constraint> s = ((HasConstraints) v).getConstraints(deep, seen);
        constraintList.addAll( s );
      }
    }
    return constraintList;
  }

  @Override
  public long getNumberOfResolvedConstraints( boolean deep,
                                              Set< HasConstraints > seen ) {
    long num = 0;
    for ( Constraint c : getConstraints(deep, seen) ) {
      if ( c.isSatisfied( false, null ) ) {
        ++num;
      }
    }
    return num;
  }

  @Override
  public long getNumberOfUnresolvedConstraints( boolean deep,
                                                Set< HasConstraints > seen ) {
    return getNumberOfConstraints( deep, seen )
           - getNumberOfResolvedConstraints( deep, seen );
  }

  @Override
  public long getNumberOfConstraints( boolean deep, Set< HasConstraints > seen ) {
    return getConstraints(deep, seen).size();
  }

  @Override
  public CollectionTree getConstraintCollection(boolean deep, Set< HasConstraints > seen) {
    // TODO Auto-generated method stub
    return null;
  }

  /*
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.solver.Wraps#getPrimitiveType()
   */
  @Override
  public Class< ? > getPrimitiveType() {
    Class< ? > c = null;
    if ( getType() != null ) {
      c = ClassUtils.primitiveForClass( getType() );
      if ( c == null && getValueNoPropagate() != null
           && Wraps.class.isInstance( getValueNoPropagate() ) ) {// isAssignableFrom( getType() ) ) {
        c = ( (Wraps< ? >)getValueNoPropagate() ).getPrimitiveType();
      }
      if ( c == null && getDomain() != null ) {
        c = getDomain().getPrimitiveType();
      }
    }
    return c;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Wraps#getTypeNameForClassName(java.lang.String)
   */
  @Override
  public String getTypeNameForClassName( String className ) {
    return ClassUtils.parameterPartOfName( className, false );
  }

  @Override
  public < TT > Pair<Domain<TT>, Boolean> restrictDomain( Domain< TT > domain,
                                           boolean propagate,
                                           Set< HasDomain > seen ) {
    Domain<?> d = this.domain == null ? null : this.domain.clone();
    boolean changed = this.domain == null ? false : this.domain.restrictTo( domain );
    if ( changed ) {
      this.constraintList.clear();
      if ( owner != null ) {
        owner.handleDomainChangeEvent( this, null );
      }
      if ( Debug.isOn() ) {
        if ( Debug.isOn() ) Debug.outln( "Changed domain of "
                                         + MoreToString.Helper.toLongString( this )
                                         + " from " + d + " to " + this.domain );
      }
    }
    return new Pair(this.domain, changed);
  }

  @Override
  public void setOwner( Object owner ) {
    if ( owner == null || owner instanceof ParameterListener ) {
      setOwner((ParameterListener)owner);
    } else {
      Debug.error( "A Parameter's owner must be a ParameterListener!  Trying to set to " + owner );
    }
  }

  @Override
  public < TT > TT evaluate( Class< TT > cls, boolean propagate ) {
    TT tt = Evaluatable.Helper.evaluate( this, cls, true, propagate, false, null );
    if ( tt != null ) return tt;
    T t = getValue(propagate);
    tt = Evaluatable.Helper.evaluate( t, cls, true, propagate, true, null );
    return tt;
  }

  public List< Variable< ? > > getIndependentVariables() {
    ArrayList<Variable<?>> independentVars = new ArrayList< Variable<?> >();
    if ( getOwner() == null ) return null;
    List<Variable<?>> vars = getOwner().getVariablesOnWhichDepends(this);
    if ( vars != null ) {
      for (Variable<?> v : vars) {
        if (v instanceof Parameter && !((Parameter<?>) v).isDependent()) {
          independentVars.add((Parameter<?>) v);
        }
      }
    }
    return independentVars;
  }

  //protected int refCount = 0;
  @Override public void addReference() {
    //++refCount;
  }

  @Override public void subtractReference() {
    //--refCount;
    //if ( refCount == 0 ) {
    //  deconstruct();
    //}
  }


}
