/**
 * 
 */
package gov.nasa.jpl.ae.solver;

import gov.nasa.jpl.ae.event.ConstructorCall;
import gov.nasa.jpl.ae.event.Functions;
import gov.nasa.jpl.ae.event.Groundable;
import gov.nasa.jpl.ae.util.DomainHelper;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Random;
import gov.nasa.jpl.mbee.util.Utils;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Vector;

/**
 * A Domain representing any object of a specified class.
 *
 */
public class ClassDomain< T > implements Domain< T > {

  protected HasIdImpl hasId = new HasIdImpl();
  protected Class<T> type;
  protected boolean nullInDomain = false;
  protected Object enclosingObject = null;

  /**
   * Create a domain for objects of the specified type.
   */
  public ClassDomain( Class<T> type ) {
    this.type = type;
  }

  /**
   * Create a domain for objects of the specified type with an enclosing object
   * for new instances of the class if an inner class.
   */
  public ClassDomain( Class<T> type, Object enclosingObject ) {
    this.type = type;
    this.enclosingObject = enclosingObject;
  }

  public ClassDomain( ClassDomain< T > classDomain ) {
    this.type = (Class< T >)classDomain.getType();
    this.enclosingObject = classDomain.enclosingObject;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.HasId#getId()
   */
  @Override
  public Integer getId() {
    return hasId.getId();
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#getType()
   */
  @Override
  public Class< ? > getType() {
    return type;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#getTypeNameForClassName(java.lang.String)
   */
  @Override
  public String getTypeNameForClassName( String className ) {
    return getType().getSimpleName();
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#getPrimitiveType()
   */
  @Override
  public Class< ? > getPrimitiveType() {
    Class< ? > c = null;
    if ( getType() != null ) {
      c = ClassUtils.primitiveForClass( getType() );
    }
    return c;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#getValue(boolean)
   */
  @Override
  public T getValue( boolean propagate ) {
    return null;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#hasValue()
   */
  @Override
  public boolean hasValue() {
    return this.type != null || isNullInDomain();
  }

  @Override public boolean hasMultipleValues() {
    return this.type != null &&
           ( isNullInDomain() || this.type.isArray() ||
             Utils.isNullOrEmpty( this.type.getFields() ) );
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#setValue(java.lang.Object)
   */
  @Override
  public void setValue( T value ) {
    if ( value == null ) return;
    if ( value instanceof Class ) {
      type = (Class<T>)value;
    } else {
      setValue( value.getClass() );
    }
  }

  public void setValue( Class<?> value ) {
    if ( value != null ) {
      type = (Class<T>)value;
    }
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Domain#clone()
   */
  @Override
  public ClassDomain< T > clone() {
    ClassDomain<T> d = new ClassDomain< T >( this );
    return d;
  }


  @Override
  public boolean contains(Object t) {
    if ( type != null && getType().isInstance( t ) ) {
      return true;
    }
    if ( t == null && isNullInDomain() ) return true;
    return false;
  }

//  /* (non-Javadoc)
//   * @see gov.nasa.jpl.ae.solver.Domain#size()
//   */
//  @Override
//  public int size() {
//    return Integer.MAX_VALUE;
//  }

  @Override
  public long magnitude() {
    // FIXME -- if no fields of the type have infinite domains, and the equals()
    // function is overridden such that equality is based on the fields,
    // then it is finite.
    if ( type == null ) {
      return nullInDomain ? 1 : 0;
    }
    return Long.MAX_VALUE;
  }

  /**
   * @return whether the domain contains no values (including null)
   */
  @Override public boolean isEmpty() {
    return getType() == null && !nullInDomain;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Domain#pickRandomValue()
   */
  @Override
  public T pickRandomValue() {
    // HACK -- TODO -- figure out why this was commented out and put back in.
    return null;
    //return constructObject();
  }



  public T constructObject() {
    if (getType() == null) {
      return null;
    }
    try {
      ConstructorCall cc = new ConstructorCall( enclosingObject, getType(),
                                                new Vector<Object>(),
                                                getType() );
      Object o = cc.evaluate(true);
      if (o instanceof Groundable) {
        ( (Groundable)o ).ground( false, null );
      }
      if (o != null) {
        return (T)o;
      } else {
        if ( getType() != null ) {
          if ( ClassUtils.getConstructorForArgs( getType(), new Object[]{} ) != null ) {
            return (T)getType().newInstance();
          } else {
            Debug.error( true, false, "Warning! No default constructor for " + getType().getCanonicalName() ) ;
          }
        } else {
          Debug.error( true, false, "Warning! No type for calling default constructor in ClassDomain: " + this );
        }
      }
    } catch ( InstantiationException e ) {
      e.printStackTrace();
    } catch ( IllegalAccessException e ) {
      e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      e.printStackTrace();
    }
    return null;
  }


  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Domain#pickRandomValueNotEqual(java.lang.Object)
   */
  @Override
  public T pickRandomValueNotEqual( T t ) {
    T tn = constructObject();
    if ( Utils.valuesEqual(t, tn) ) {
      return null;
    }
    return tn;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Domain#isInfinite()
   */
  @Override
  public boolean isInfinite() {
    // FIXME -- if no fields of the type have infinite domains, and the equals()
    // function is overridden such that equality is based on the fields,
    // then it is finite.
    return getType() != null;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Domain#isNullInDomain()
   */
  @Override
  public boolean isNullInDomain() {
    return nullInDomain;
  }

  @Override
  public boolean setNullInDomain(boolean b) {
    nullInDomain = b;
    return true;
  }


  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Domain#getDefaultDomain()
   */
  @Override
  public Domain< T > getDefaultDomain() {
    Domain<T> defaultDomain = new ClassDomain< T >( type );
    return defaultDomain;
  }


  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Domain#restrictToValue(java.lang.Object)
   */
  @Override
  public boolean restrictToValue( T v ) {
    if ( v == null ) {
      if ( nullInDomain ) {
        if ( getType() != null ) {
          type = null;
          return true;
        }
      }
      return false;
    }
    return restrictToValue( v.getClass() );
//    boolean changed = false;
//    if ( getType().isInstance( v ) && !v.getClass().equals( getType() ) ) {
//        this.type = v.getClass();
//        changed = true;
//    }
//    return changed;
  }

  public boolean restrictToValue( Class< ? > cls ) {
    boolean changed = false;
    if ( cls != null && getType().isAssignableFrom( cls ) && !cls.equals( getType() ) ) {
      this.type = (Class<T>)cls;
      changed = true;
    }
    return changed;
  }

  @Override
  public < TT > boolean restrictTo( Domain< TT > domain ) {
    if ( domain == null ) return false;
    boolean changed = false;
    if ( !domain.isNullInDomain() ) {
      if ( isNullInDomain() ) {
        changed = true;
        setNullInDomain( false );
      }
    }
    boolean c = restrictToValue( domain.getType() );
    return c || changed;
  }

  @Override
  public boolean clearValues() {
    // REVIEW -- what should we do here?
    type = null;
    nullInDomain = false;
    return false;
  }

  @Override
  public < V > V evaluate( Class< V > cls, boolean propagate ) {
    if ( cls != null ) {
      V v = Helper.evaluate( this, cls, true, propagate, false, null );
      if ( v != null ) return v;
    }
    if ( cls == null || cls.equals( Object.class ) ) {
      try {
        return (V)this;
      } catch ( ClassCastException e ) {}
    }
    return null;
  }

  @Override
  public < TT > Domain< TT > subtract( Domain< TT > domain ) {
    // This might make sense if domain was a ClassDomain for a subclass of T
    if ( domain != null ) {
      if ( isNullInDomain() && domain.isNullInDomain() ) {
        setNullInDomain( false );
      }
    }
    return (Domain<TT>)this;
  }

  @Override
  public String toString() {
    return "class " + type.getSimpleName() + (enclosingObject != null ? " in " + enclosingObject.toString() : "");
  }
}
