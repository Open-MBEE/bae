package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.solver.ClassDomain;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.ae.solver.Domain;
import gov.nasa.jpl.ae.solver.HasDomain;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.MoreToString;
import gov.nasa.jpl.mbee.util.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.util.*;


/**
 * 
 */
public class ConstructorCall extends Call {

  protected static boolean isAlwaysNotStaleByDefault = true;

  protected Class<?> thisClass = null;
  protected Constructor<?> constructor = null;
  //protected Object newObject = null;

  protected Object getEnclosingInstance() {
    return object;
  }
  
  public boolean hasEnclosingClass() {
    if ( thisClass == null ) return false;
    return thisClass.getEnclosingClass() != null;
  }
  public boolean isNestedClass() {
    return hasEnclosingClass();
  }
  public boolean isInnerClass() {
    return hasEnclosingClass() && !isStatic();
  }
  public static boolean isStatic( Class<?> cls ) {
    if ( cls == null ) return false;
    boolean s = ClassUtils.isStatic(cls);
    return s;
  }
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.Call#isStatic()
   */
  @Override
  public boolean isStatic() {
    return isStatic( thisClass );
  }

  /**
   * Construct a call to a static constructor.
   * @param constructor
   */
  public ConstructorCall( Constructor<?> constructor,
                          Class<?> returnType ) {
    this.constructor = constructor; // the constructor must be static
    this.returnType = returnType;
    this.alwaysNotStale = isAlwaysNotStaleByDefault;
  }

  /**
   * Construct a call to a static constructor.
   * @param cls
   * @param returnType
   */
  public ConstructorCall( Class<?> cls,
                          Class<?> returnType ) {
    thisClass = cls;
    this.returnType = returnType;
    setConstructor( ClassUtils.getConstructorForArgTypes( cls, (Class<?>[])null ) );
    this.alwaysNotStale = isAlwaysNotStaleByDefault;
  }

  /**
   * @param object
   * @param constructor
   */
  public ConstructorCall( Object object, Constructor<?> constructor,
                          Class<?> returnType ) {
    this.object = object;
    this.returnType = returnType;
    setConstructor( constructor );
    this.alwaysNotStale = isAlwaysNotStaleByDefault;
  }

  public ConstructorCall( Object object, Class<?> cls,
                          Class<?> returnType ) {
    this( cls, returnType );
    this.object = object;
  }

  /**
   * @param object
   * @param constructor
   * @param arguments
   */
  public ConstructorCall( Object object, Constructor<?> constructor,
                          Vector< Object > arguments,
                          Class<?> returnType ) {
    this.object = object;
    setConstructor( constructor );
    this.arguments = arguments;
    this.returnType = returnType;
    this.alwaysNotStale = isAlwaysNotStaleByDefault;
    hasTypeErrors();
  }

  /**
   * @param object
   * @param cls
   * @param arguments
   * @param returnType
   */
  public ConstructorCall( Object object, Class<?> cls,
                          Vector< Object > arguments,
                          Class<?> returnType ) {
    this.object = object;
    this.thisClass = cls;
    this.arguments = arguments;
    this.constructor = getConstructor();
    this.returnType = returnType;
    this.alwaysNotStale = isAlwaysNotStaleByDefault;
    hasTypeErrors();
  }

  /**
   * @param object
   * @param constructor
   * @param arguments
   * @param nestedCall
   * @param returnType
   */
  public ConstructorCall( Object object, Constructor<?> constructor, Vector< Object > arguments,
                          Call nestedCall,
                          Class<?> returnType ) {
    this(object, constructor, arguments, returnType);
    this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
  }

  /**
   * @param object
   * @param constructor
   * @param arguments
   * @param nestedCall
   * @param returnType
   */
  public ConstructorCall( Object object, Constructor<?> constructor, Vector< Object > arguments,
                       Parameter<Call> nestedCall,
                       Class<?> returnType ) {
    this(object, constructor, arguments, returnType);
    this.nestedCall = nestedCall;
  }

  /**
   * @param object
   * @param cls
   * @param arguments
   * @param nestedCall
   * @param returnType
   */
  public ConstructorCall( Object object, Class<?> cls,
                       Vector< Object > arguments,
                       Call nestedCall,
                       Class<?> returnType ) {
    this(object, cls, arguments, returnType);
    this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
  }

  public ConstructorCall( Object object, Class<?> cls,
                       Vector< Object > arguments,
                       Parameter<Call> nestedCall,
                       Class<?> returnType ) {
    this(object, cls, arguments, returnType);
    this.nestedCall = nestedCall;
  }

  /**
   * @param object
   * @param constructor
   * @param argumentsA
   * @param returnType
   */
  public ConstructorCall( Object object, Constructor<?> constructor,
                          Object argumentsA[],
                          Class<?> returnType ) {
    this.object = object;
    setConstructor( constructor );
    this.arguments = new Vector<Object>();
    if ( argumentsA != null ) {
      for ( Object o : argumentsA ) {
        this.arguments.add( o );
      }
    }
    this.returnType = returnType;
    this.alwaysNotStale = isAlwaysNotStaleByDefault;
    hasTypeErrors();
  }

  /**
   * @param object
   * @param cls
   * @param argumentsA
   * @param returnType
   */
  public ConstructorCall( Object object, Class<?> cls,
                          Object argumentsA[],
                          Class<?> returnType ) {
    this.object = object;
    this.thisClass = cls;
    this.arguments = new Vector<Object>();
    if ( argumentsA != null ) {
      for ( Object o : argumentsA ) {
        this.arguments.add( o );
      }
    }
    this.constructor = getConstructor();
    this.returnType = returnType;
    this.alwaysNotStale = isAlwaysNotStaleByDefault;
    hasTypeErrors();
  }

  public ConstructorCall( Object object, Constructor<?> constructor,
                          Object argumentsA[],
                          ConstructorCall nestedCall,
                          Class<?> returnType ) {
    this( object, constructor, argumentsA, returnType );
    this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
    hasTypeErrors();
  }

  public ConstructorCall( Object object, Constructor<?> constructor,
                          Object argumentsA[],
                          Parameter<Call> nestedCall,
                          Class<?> returnType ) {
    this( object, constructor, argumentsA, returnType );
    this.nestedCall = nestedCall;
    hasTypeErrors();
  }

  public ConstructorCall( Object object, Class<?> cls,
                          Object argumentsA[], Call nestedCall,
                          Class<?> returnType ) {
    this( object, cls, argumentsA, returnType );
    this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
  }
  public ConstructorCall( Object object, Class<?> cls,
                          Object argumentsA[],
                          Parameter<Call> nestedCall,
                          Class<?> returnType ) {
    this( object, cls, argumentsA, returnType );
    this.nestedCall = nestedCall;
  }

  /**
   * @param constructorCall
   */
  public ConstructorCall( ConstructorCall constructorCall ) {
    this.object = constructorCall.object;
    this.thisClass = constructorCall.thisClass;
    this.constructor = constructorCall.constructor;
    this.arguments = constructorCall.arguments;
    this.nestedCall = constructorCall.nestedCall;
    this.returnType = constructorCall.returnType;
    this.argHelper = constructorCall.argHelper;
    this.alwaysStale = constructorCall.alwaysStale;
    this.alwaysNotStale = constructorCall.alwaysNotStale;

    hasTypeErrors();
  }

  // TODO -- REVIEW -- should this call super.clone() all the way up to Object?
  @Override
  public ConstructorCall clone() {
    ConstructorCall c = new ConstructorCall(this);
    return c;
  }
  
  @Override
  public Class<?>[] getParameterTypes() {
    if ( constructor == null ) return new Class<?>[]{};
    Class< ? >[] ctorTypes = constructor.getParameterTypes();
    if ( !isInnerClass() ) return ctorTypes;
    int newSize = Utils.isNullOrEmpty( ctorTypes ) ? 0 : ctorTypes.length - 1;
    Class< ? >[] newTypes =  new Class< ? >[ newSize ];
    for ( int i = 0; i < newSize; ++i ) {
      newTypes[ i ] = ctorTypes[i+1];
    }
    return newTypes;
  }

  @Override
  public Set< Parameter< ? > > getParameters( boolean deep,
                                              Set<HasParameters> seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    Set< Parameter< ? > > set = new LinkedHashSet< Parameter< ? >>();
//    if ( !isStatic() ) {
//      if (object instanceof Parameter) set.add((Parameter<?>) object);
////      if ( deep ) {
////        set = Utils.addAll(set, getMemberParameters(object, deep, seen));
////      }
//    }

    for (Object o : arguments) {
      set = Utils.addAll( set, getMemberParameters( o, deep, seen));
    }

//    //if ( nestedCall != null ) {//&& nestedCall.getValue() != null ) {
//    // REVIEW -- bother with adding nestedCall as a parameter?
//    set = Utils.addAll( set, HasParameters.Helper.getParameters( nestedCall, deep, seen, true ) );
////      set = Utils.addAll( set, nestedCall.getValue().getParameters( deep, seen ) );
//    //}
//    set = Utils.addAll( set,
//            HasParameters.Helper.getParameters( returnValue, deep,
//                    seen, true ) );
//    set = Utils.addAll( set,
//            HasParameters.Helper.getParameters( evaluatedArguments,
//                    deep, seen, true ) );

    return set;
  }



  @Override
  public Member getMember() {
    return constructor;
  }

  @Override
  public boolean isVarArgs() {
    return constructor.isVarArgs();
  }
  
  @Override
  public Object invoke( Object evaluatedObject, Object[] evaluatedArgs ) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
    evaluationSucceeded = false;
    if ( !isGrounded(false, null) ) {
      return null;
    }

    Object[] args = false ? new Object[]{evaluatedArgs} : evaluatedArgs; // handling this in calling method, evaluate()
    if ( isInnerClass() ) {
      if ( evaluatedObject == null ) {
        return null;
      }
      Object[] newArgs = new Object[args.length+1];
      newArgs[0] = evaluatedObject;
      if ( args != null ) {
        for ( int i=0; i<args.length; ++i ) {
          newArgs[i+1] = args[i];
        }
        args = newArgs;
      }
    }
    try {
      Object newValue = constructor.newInstance( args );
      setReturnValue(newValue);
      if ( Debug.isOn() ) {
          System.out.println("ConstructorCall constructor = " + constructor.toGenericString());
          System.out.println("ConstructorCall args = " + args);
          System.out.println("ConstructorCall.invoke " + constructor.getName() + "("
                  + Utils.toString( evaluatedArgs, false )
                  + "): ConstructorCall{" + this + "} = " + returnValue );
      }
      evaluationSucceeded = true;
    } catch (Exception e ) {
      evaluationSucceeded = false;
      if ( Debug.isOn() ) {
        Debug.error(true, false, "ConstructorCall constructor = " + constructor.toGenericString());
        Debug.error(true, false, "ConstructorCall.invoke " + constructor.getName() + "("
                            + Utils.toString( evaluatedArgs, false )
                            + "): ConstructorCall{" + this + "} " + e.getMessage() );
        e.printStackTrace();
      }
      throw e;
    }
    return returnValue; //newObject;
  }

  protected Boolean _isParameterListenerImpl = null;

  public boolean isParameterListenerImpl() {
    if (_isParameterListenerImpl != null ) {
      return _isParameterListenerImpl;
    }
    if ( this.getMember() == null ) return false;
    Class<?> c = this.getMember().getDeclaringClass();
    while ( c != null ) {
      if ( "ParameterListenerImpl".equals( c.getSimpleName() ) ) {
        _isParameterListenerImpl = true;
        return true;
      }
      c = c.getSuperclass();
    }
    _isParameterListenerImpl = false;
    return false;
  }

  protected Boolean _isTimeVaryingMap = null;

  public boolean isTimeVaryingMap() {
    if (_isTimeVaryingMap != null ) {
      return _isTimeVaryingMap;
    }
    _isTimeVaryingMap = false;
    if ( this.getMember() == null ) return false;
    Class<?> c = this.getMember().getDeclaringClass();
    while ( c != null ) {
      if ( "TimeVaryingMap".equals( c.getSimpleName() ) ) {
        _isTimeVaryingMap = true;
        return true;
      }
      c = c.getSuperclass();
    }
    return false;
  }

  /**
   * In some cases, changing the parameters may not require re-evaluation.
   * @param b
   */
  @Override
  protected void setStaleOnChangedParameter( boolean b ) {
    if ( b && isParameterListenerImpl() ) return;
    setStale( b );
  }


  @Override
  public Object evaluate( boolean propagate ) throws IllegalAccessException, InvocationTargetException, InstantiationException { // throws IllegalArgumentException,
    if ( returnValue != null ) {
      // If it's a generated class, we just need to update dependencies, and we
      // can rely on the LazyUpdate interface for that.
      if ( !hasSomethingDeconstructed() &&
           (!isStale(false) || isParameterListenerImpl() ) ) {
        evaluationSucceeded = true;
        return returnValue;
      }
    }
    //System.out.println("Calling " + toShortString());
    setReturnValue(null);
    Object v = evaluate( propagate, true );
    return v;
  }

  protected Object lastReturnValue = null;

  protected void setReturnValue( Object value ) {
    if ( value != lastReturnValue &&
         lastReturnValue instanceof Deconstructable ) {
      //((Deconstructable) lastReturnValue).deconstruct();
    }
    returnValue = value;
    lastReturnValue = value;
  }

  protected static boolean isDeconstructed( Object o ) {
    if ( o == null ) return false;
    if ( o instanceof Collection ) {
      for ( Object oo : (Collection)o ) {
        if ( isDeconstructed( oo ) ) {
          return true;
        }
      }
    }
    if ( ( o instanceof ParameterListenerImpl
           && ( (ParameterListenerImpl)o ).isDeconstructed() ) ||
         ( o instanceof Parameter
           && ( (Parameter)o ).isDeconstructed() ) ) {
      return true;
    }
    return false;
  }

  protected boolean hasSomethingDeconstructed() {
    if ( isDeconstructed( returnValue ) ) {
      return true;
    }
    if ( isDeconstructed( evaluatedArguments ) ) {
      return true;
    }
    if ( isDeconstructed( evaluatedObject ) ) {
      return true;
    }
    if ( isDeconstructed( getParameters( false, null ) ) ) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isStale() {
    return isStale( true );
  }
  public boolean isStale(boolean checkDeconstructed) {

    if ( alwaysNotStale ) {
      if ( checkDeconstructed && hasSomethingDeconstructed() ) {
        return true;
      }
      return false;
    }
    if ( stale ) {
      return true;
    }

    // Stale arguments don't make stale the constructors of ParameterListenerImpls
    // since the corresponding dependencies become stale instead.  Constructors of
    // ParameterListenerImpls are to be interpreted as simply a constraint on type.
    if ( isParameterListenerImpl() ) {
      return false;
    }
    return super.isStale();

//    boolean argsStale = areArgsStale(); // calls this.setStale(true) if true, so we don't need to here.
//    if ( argsStale ) {
//      return true;
//    }
//
//    return false;
  }


  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.Call#calculateDomain(boolean, java.util.Set)
   */
  @Override
  public Domain< ? > calculateDomain( boolean propagate, Set< HasDomain > seen ) {
    return new ClassDomain(getType(), getObject());
  }

  @Override
  public Boolean hasTypeErrors() {
    if ( super.hasTypeErrors() ) return true;
    if ( thisClass != getReturnType() ) return true;
    return false;
  }

  @Override
  public String toString(boolean withHash, boolean deep, Set< Object > seen,
                         Map< String, Object > otherOptions) {

    StringBuffer sb = new StringBuffer();
    if ( object != null ) {
      if ( object instanceof DurativeEvent ) {
        sb.append( ((DurativeEvent)object).getName() + " new." );
      } else {
        sb.append( MoreToString.Helper.toString( object, withHash, deep, seen,
                                                 otherOptions ) );
        sb.append( " new." );
      }
    }
    if ( constructor == null ) {
      sb.append( "null" );
    } else {
      sb.append( constructor.getName() );// + "(" );
      sb.append( MoreToString.Helper.toString( arguments, withHash, deep, seen,
                                               otherOptions,
                                               MoreToString.PARENTHESES, true ) );
    }
    if ( nestedCall != null ) {
      sb.append( "." + nestedCall.toString( withHash, deep, seen, otherOptions) );
    }
    return sb.toString();
  }
  
  // Getters and setters   
  
  /**
   * @return the constructor
   */
  public Constructor<?> getConstructor() {
    if ( this.constructor != null ) return this.constructor; 
    Object argArr[] = null;
    if ( !Utils.isNullOrEmpty( arguments ) ) {
      argArr = arguments.toArray();
    }
    Pair< Constructor< ? >, Object[] > p =
        ClassUtils.getConstructorForArgs( thisClass, argArr, object );
    this.constructor = p.first;
    return this.constructor;
  }
  
  /**
   * @param constructor the constructor to set
   */
  public void setConstructor( Constructor<?> constructor ) {
    this.constructor = constructor;
    setStale( true );
    if ( constructor != null ) {
      this.thisClass = constructor.getDeclaringClass();
    }
    setReturnValue(null);
 }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.Call#setNestedCall(gov.nasa.jpl.ae.event.Call)
   */
  @Override
  public void setNestedCall( Call nestedCall ) {
    super.setNestedCall( nestedCall );
//    this.newObject = null;
    setStale( true );
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.Call#getReturnType()
   */
  @Override
  public Class< ? > getReturnType() {
    if ( returnType != null ) return returnType;
    return thisClass;
  }
  
}
