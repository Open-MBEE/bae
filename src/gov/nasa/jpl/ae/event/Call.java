
package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.solver.Domain;
import gov.nasa.jpl.ae.solver.HasDomain;
import gov.nasa.jpl.ae.solver.HasIdImpl;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.MoreToString;
import gov.nasa.jpl.mbee.util.Random;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.Wraps;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import junit.framework.Assert;

public abstract class Call extends HasIdImpl implements HasParameters,
                                                        ParameterListener,
                                                        HasDomain,
                                                        Groundable,
                                                        Comparable< Call >,
                                                        MoreToString,
                                                        Cloneable,
                                                        Wraps<Object> {

  /**
   * A function call on the result of this function call.
   */
  protected Domain<?> domain = null;
  protected Parameter<Call> nestedCall = null;
  protected Object object = null; // object from which constructor is invoked
  protected Class<?> returnType = null;
  protected Vector< Object > arguments = null; // arguments to constructor
  public Vector< Vector< Object > > alternativeArguments = new Vector< Vector< Object > >(); // arguments to member if the default arguments don't work.
  protected Object[] evaluatedArguments = null; // arguments to constructor
  protected boolean evaluationSucceeded = false;

  protected boolean stale = true;
  protected boolean alwaysStale = false;
  public boolean alwaysNotStale = false;

  public Object returnValue = null;  // a cached value
  
  protected boolean proactiveEvaluation = false;
  
  abstract public Class<?>[] getParameterTypes();
  abstract public Member getMember();
  abstract public Object invoke( Object obj, Object[] evaluatedArgs ) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException;
  abstract public boolean isVarArgs();
  abstract public boolean isStatic();
  abstract public Call clone();
  
  // Hook to externally preprocess arguments for proper parameter type matching.
  public static interface ArgHelper {
    public void helpArgs( Call call );
  }
  public ArgHelper argHelper = null;
  
  public Call() {}
 
  /**
   * The type of the object returned by this call.<p>
   * This will likely need to be redefined in subclasses. 
   * 
   * @return the return type
   */
  public Class< ? > getReturnType() {
    return returnType;
  }

  /**
   * Set the type of the object returned by this call.
   * 
   * @param returnType the new return type
   */
  public void setReturnType( Class< ? > returnType ) {
    this.returnType = returnType;
  }

  
  public Class< ? > getObjectType() {
    if ( getMember() == null ) return null;
    if ( isStatic() ) return null;
    return getMember().getDeclaringClass();
  }
  
  static boolean simpleDeconstruct = true;
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.Deconstructable#deconstruct()
   */
  @Override
  public synchronized void deconstruct() {
    if ( nestedCall != null ) {
      if ( nestedCall.getValue( false ) != null ) {
        nestedCall.getValue( false ).deconstruct();
        nestedCall.setValue( null );
      }
      nestedCall.deconstruct();
      //nestedCall = null;
    }
    if ( this.arguments != null ) {
      if ( !simpleDeconstruct ) {
      for ( Object a : arguments ) {
        if ( a instanceof Expression ) {
          ((Expression<?>)a).deconstruct();
        } else if ( a instanceof Parameter ) { // TODO - check this first and then check if Deconstructable.
          if ( ( (Parameter<?>)a ).getOwner() == null ) {
            ( (Parameter<?>)a ).deconstruct();
          }
        }
      }
      }
      this.arguments.clear();
    }
    setReturnValue(null);
    this.evaluatedArguments = null;
    this.argHelper = null;
    this.alternativeArguments.clear();
    this.object = null; // Can't deconstruct since Call does not own it.
    setStale( true );
//    if ( evaluatedArguments != null ) {
//      this.evaluatedArguments.clear(); // Can't deconstruct since Call does not own them.
//    }
  }

  
  public static boolean divisibleBy( int x, int y ) {
      if ( y == 0 ) return false;
      return ( x / y ) * y == x;
  }
  
  public static boolean isA( Object o, Class<?> c ) {
      return isA( o, c, true, true );
  }  
  public static boolean isA( Object o, Class<?> c, boolean nullOkay,
                             boolean tryEvaluating ) {
      if ( o == null ) return nullOkay;
      if ( c == null ) return false;
      Class<?> oc = o.getClass();
      if ( c.isAssignableFrom( oc ) ) return true;
      if ( o instanceof Wraps ) {
          Wraps<?> w = ((Wraps<?>)o);
          oc = w.getType();
          if ( oc != null && c.isAssignableFrom( oc ) ) return true;
          oc = w.getPrimitiveType();
          if ( oc != null && c.isAssignableFrom( oc ) ) return true;
      }
      if ( tryEvaluating ) {
          Object v = null;
          try {
            v = Expression.evaluate( o, c, false );
          } catch ( ClassCastException e ) {
            // TODO Auto-generated catch block
            //e.printStackTrace();
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
          return isA( v, c, false, false );
      }
      return false;
  }
  
  public static boolean isA( Collection< ? > c, Class< ? >[] classes ) {
      return isA( c, Utils.arrayAsList( classes, Class.class ) );
  }

  public static boolean isA( Collection< ? > c, Class< ? >[] classes,
                             boolean isVarArgs ) {
      return isA( c, Utils.arrayAsList( classes, Class.class ), isVarArgs );
  }
  
  public static boolean isA( Collection< ? > c, Collection< Class > classes ) {
      return isA( c, classes, false );
  }
  
  public static boolean isA( Collection< ? > c, Collection< Class > classes,
                             boolean isVarArgs ) {
      if ( c == null || classes == null ) return false;

      // check if the list sizes match the number
      boolean argsSame = c.size() == classes.size();
      boolean numberOfArgsOkay = argsSame || ( isVarArgs && classes.size() < c.size() );
      if ( !numberOfArgsOkay ) return false;
      
      Iterator< Class > i = classes.iterator();
      Class< ? > cls = null;
      for ( Object o : c ) {
          if ( i.hasNext() ) {
              cls = i.next();
          }
          if ( !isA( o, cls ) ) return false;
      }
      return true;
  }
  
  public synchronized boolean canMapToListOfArguments() {
      int as = arguments.size();
      if ( as == 0 ) return false;

      Class<?> cls = getMember().getDeclaringClass();
      if ( cls == null ) return false;

      Class< ? >[] types = getParameterTypes();
      int ts = types.length;
      
      if ( ts == 0 ) {
          // check if arguments can substitute for the object
          boolean allMatched = true;
          for ( Object a : arguments ) {
              allMatched = isA( a, cls );
          }
          if ( allMatched ) return true;
      }
      // check each argument to see if it matches the types
      boolean allMatched = true;
      for ( Object a : arguments ) {
            allMatched = ( ( ( a instanceof Collection ) &&
                             isA( (Collection< ? >)a, types, isVarArgs() ) ) || 
                           ( ts == 1 && isA( a, types[ 0 ] ) ) );
          if ( !allMatched ) break;
      }
      if ( allMatched ) return true;
      return false;
  }

  public synchronized boolean canMapToListOfArguments( Object[] evaluatedArgs ) {
      // replace this.arguments with evaluatedArgs and call canMapToListOfArguments()
      Vector< Object > oldArguments = arguments;
      Vector< Object > newArguments = new Vector< Object >();
      newArguments.addAll( Utils.arrayAsList( evaluatedArgs ) );
      arguments = newArguments;
      boolean ans = canMapToListOfArguments();
      arguments = oldArguments;
      return ans;
  }

  public Boolean objectHasTypeErrors( Object evaluatedObject ) {
    Boolean gotErrors = false;
    // test object
    if ( !isStatic() ) {
      if ( evaluatedObject == null ) {
        gotErrors = true;
      } else {
        Class<?> c = getMember() == null ? null : getMember().getDeclaringClass();
        if ( c == null ) {
          if ( getMember() == null ) {
            gotErrors = true;
          }
        } else {
          if ( !c.isAssignableFrom( evaluatedObject.getClass() ) &&
               !Collection.class.isAssignableFrom( c ) &&
               ( !evaluatedObject.getClass().isArray() ||
                 ( ((Object[])evaluatedObject).length > 0 &&
                   ((Object[])evaluatedObject)[0] != null &&
                   !c.isAssignableFrom(((Object[])evaluatedObject)[0].getClass())))) {
            gotErrors = true;
          }
        }
      }
    }
    if ( gotErrors ) {
      System.out.println("WARNING! objectHasTypeErrors(" + evaluatedObject + ") returning true for " + this + "!");
    }
    return gotErrors;
  }

  public Boolean hasTypeErrors( Object[] evaluatedArgs ) {
    boolean gotErrors = hasTypeErrors();
    int numEvalArgs = 0;
    if ( evaluatedArgs != null ) {
      numEvalArgs = evaluatedArgs.length;
    }
    if ( gotErrors || evaluatedArgs == null || numEvalArgs == 0 ) return gotErrors;
    for ( int i = 0; !gotErrors && i < evaluatedArgs.length; i++ ) {
      //Class< ? > c = getParameterTypes()[ i ];
      Class< ? > oc = getParameterTypes()[ Math.min(i,getParameterTypes().length-1) ];
      Class< ? > c = oc;
      if ( c != null ) {
          Class< ? > np = ClassUtils.classForPrimitive( c );
          if ( np != null ) c = np;
      }
      if ( c == null || c.equals( Object.class ) ) continue;
      if ( i >= getParameterTypes().length-1 && isVarArgs() ) {
        if ( !c.isArray() ) {
          Debug.error( true, true, "class " + c.getSimpleName() + " should be a var arg array!" );
        } else {
          c = c.getComponentType();
        }
      }
      if ( evaluatedArgs[ i ] == null ) {
        if ( c.isPrimitive() ) {
          gotErrors = true; 
        }
      } else if (!c.isAssignableFrom( evaluatedArgs[ i ].getClass() ) &&
                  ( !isVarArgs() ||
                    ( !Collection.class.isAssignableFrom( c ) &&
                      ( !evaluatedArgs[ i ].getClass().isArray() ||
                        ( ((Object[])evaluatedArgs[ i ]).length > 0 &&
                          ((Object[])evaluatedArgs[ i ])[0] != null &&
                          !c.isAssignableFrom(((Object[])evaluatedArgs[ i ])[0].getClass())))))) {
        gotErrors = true;
      }
    }
    return (Boolean) gotErrors;
  }
  
  public synchronized Boolean hasTypeErrors() {
    if ( getMember() == null ) return true;
    Class< ? >[] paramTypes = getParameterTypes();
    int az = arguments == null ? 0 : arguments.size();
    int pz = paramTypes == null ? 0 : paramTypes.length;
    boolean hasErrors = false;
    if ( !isVarArgs() ) {
      //Assert.assertEquals( arguments.size(), paramTypes.length );
      if ( az != pz ) {
        hasErrors = true;
      }
    } else if ( az < pz - 1 ) {
      //this.compareTo( this );  // why was this here? to see if any exceptions would be raised?
      hasErrors = true;
    }
    // Code below is not right! The arguments may be expressions, the results of
    // whose evaluations may match, but they cannot be checked without evaluating.
//    for ( int i = 0; i < arguments.size(); i++ ) {
//      Class< ? > c = paramTypes[ i ];
//      Assert.assertTrue( c.isAssignableFrom( arguments.get( i ).getClass() ) );
//    }
    if ( hasErrors && !isVarArgs() && ( arguments == null || paramTypes == null ) ) {
      Debug.error("Error!  arguments or paramTypes is null in " + this);
    }
    return hasErrors;
  }

  @Override
  public int compareTo( Call o ) {
    return compareTo( o, true );
  }
  public int compareTo( Call o, boolean checkId ) {
    if ( this == o ) return 0;
    if ( o == null ) return -1;
    if ( checkId ) return CompareUtils.compare( getId(), o.getId() );
    int compare = 0;//super.compareTo( o );
    compare = CompareUtils.compare( getMember(), o.getMember(), true );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compare( getParameterTypes(), o.getParameterTypes(), true );
    if ( compare != 0 ) return compare;
//    compare = Utils.compareTo( getReturnType(), o.getReturnType(), true );
//    if ( compare != 0 ) return compare;
    compare = CompareUtils.compare( getClass().getName(), o.getClass().getName() );
    if ( compare != 0 ) return compare;
    // TODO -- would like to skip this since it changes.
    if ( Debug.isOn() ) Debug.errln( "Call.compareTo comparing value information." );
    compare = CompareUtils.compare( arguments, o.arguments, true );
    if ( compare != 0 ) return compare;
    if (!isStatic()) {
      compare = CompareUtils.compare(object, o.object, true);
      if (compare != 0) return compare;
    }
    compare = CompareUtils.compare( this, o );
    if ( compare != 0 ) return compare;
    return compare;
  }
  
  public Object evaluate( boolean propagate ) throws IllegalAccessException, InvocationTargetException, InstantiationException { // throws IllegalArgumentException,
    if ( returnValue != null && !isStale() ) {
        evaluationSucceeded = true;
        return returnValue;
    }
    //System.out.println("Calling " + toShortString());
    setReturnValue(null);

    return evaluate(propagate, true);
  }
  
  // TODO -- consider an abstract Call class
  public synchronized Object evaluate( boolean propagate, boolean doEvalArgs ) throws IllegalAccessException, InvocationTargetException, InstantiationException { // throws IllegalArgumentException,
    Object result = null;
    
    // Hook to externally preprocess args for proper parameter type matching.
    if ( argHelper != null ) {
      argHelper.helpArgs( this );
    }
    
    //result = evaluate( propagate, doEvalArgs, true );
//    Debug.getInstance().logForce("\n####  ####  evaluating Call: " + this);
    if ( Debug.isOn() ) {
      Debug.outln("\n####  ####  evaluating Call: " + this);
    }
    try {
        result = evaluateWithSetArguments( propagate, doEvalArgs);
    } catch (  IllegalAccessException e ) {
        throw e;
    } catch (  InvocationTargetException e ) {
        throw e;
    } catch (  InstantiationException e ) {
        throw e;
    } finally {
//      Debug.getInstance().logForce( "####  ####  Call "
//          + ( didEvaluationSucceed() ? "succeeded" : "failed" )
//          + ": " + this + "\n" + "####  ####  #### result ---> "
//          + result + "\n" );
      if ( Debug.isOn() ) {
        Debug.outln( "####  ####  Call "
                     + ( didEvaluationSucceed() ? "succeeded" : "failed" )
                     + ": " + this + "\n" + "####  ####  #### result ---> "
                     + result + "\n" );
      }
    }
    return result;
  }
  
  // TODO -- This method tries each set of arguments and returns the result for
  // the first set that works. However, it's possible to mix arguments from the
  // alternatives--should we evaluate the combinations of different args from
  // different sets.  Unfortunately that grows k^n for k alternatives and n 
  // arguments.
  public synchronized Object evaluate( boolean propagate, boolean doEvalArgs,
                                       boolean useAlternatives ) throws IllegalAccessException, InvocationTargetException, InstantiationException { // throws IllegalArgumentException,
    Object result = null;
    Throwable t = null;
    try {
      result = evaluateWithSetArguments( propagate, doEvalArgs );
    } catch ( Throwable t1 ) {
      t = t1;
    }
    
    if ( !didEvaluationSucceed() && useAlternatives ) {
      Vector< Object > originalArguments = arguments;
      for ( Vector< Object > altArgs : alternativeArguments ) {
        try {
          setArguments( altArgs );
          result = evaluateWithSetArguments( propagate, doEvalArgs );
          if ( didEvaluationSucceed() ) {
            break;
          }
        } catch ( Throwable t2 ) {
          if ( t == null ) t = t2;
        }
      }
      setArguments( originalArguments );
    }
    
    if ( !didEvaluationSucceed() ) {
      if ( t instanceof IllegalAccessException ) throw (IllegalAccessException)t;
      if ( t instanceof InvocationTargetException ) throw (InvocationTargetException)t;
      if ( t instanceof InstantiationException ) throw (InstantiationException)t;
    }
    return result;
  }
  
  public synchronized Object evaluateWithSetArguments( boolean propagate,
                                                       boolean doEvalArgs )
                                                                       throws IllegalAccessException,
                                                                       InvocationTargetException,
                                                                       InstantiationException,
                                                                       IllegalArgumentException {
    evaluationSucceeded = false;
    // IllegalAccessException, InvocationTargetException {
    if ( getMember() == null ) {
      Debug.error( true, false, "evaluate() failed!  No member for " + this );
      return null;
    }

//    if ( propagate ) {
//      if ( !ground( propagate, null ) ) {
//      }
//    } 
    //else {
    //  if ( !isGrounded( false, null ) ) {
      //  return null;
      //}
    //}
    //Object result = null;
    
    // evaluate the arguments before invoking the method on them
    Object evaluatedArgs[] = null;
    Object[] unevaluatedArgs = arguments.toArray();
    if ( ( doEvalArgs ) || hasTypeErrors( unevaluatedArgs ) ) {
      if ( evaluatedArguments == null || evaluatedArguments.length == 0 ) {
        evaluatedArguments = evaluateArgs( propagate );
      }
    }
    else {
      evaluatedArguments = unevaluatedArgs;
    }
    
    evaluatedArgs = Arrays.copyOf( evaluatedArguments, evaluatedArguments.length );
    
    try {
      
      Object evaluatedObj = evaluateObject(propagate);

      evaluatedArgs = fixArgsForVarArgs( evaluatedArgs, false );
      
      Object newValue = invoke( evaluatedObj, evaluatedArgs );// arguments.toArray() );
      setReturnValue(newValue);

      // No longer stale after invoked with updated arguments and result is cached.
      if ( evaluationSucceeded ) setStale( false );
      
    } catch ( ClassCastException e ) {
      evaluationSucceeded = false;
      //e.printStackTrace();
      throw e;
    } catch ( IllegalAccessException e ) {
      evaluationSucceeded = false;
      //e.printStackTrace();
      throw e;
    } catch ( IllegalArgumentException e ) {
      evaluationSucceeded = false;
      //e.printStackTrace();
      throw e;
    } catch ( InvocationTargetException e ) {
      evaluationSucceeded = false;
      e.printStackTrace();
      throw e;
    } catch ( InstantiationException e ) {
      evaluationSucceeded = false;
      //e.printStackTrace();
      throw e;
    } finally {
      evaluatedArguments = null;
    }

    if ( Debug.isOn() ) Debug.outln( "evaluate() returning " + returnValue );
    
    return returnValue;
  }

  public Object evaluateObject( boolean propagate ) throws ClassCastException,
                                                    IllegalAccessException,
                                                    InvocationTargetException,
                                                    InstantiationException {
    if ( isStatic() ) {
      return null;
    }
    // evaluate the object, whose method will be invoked from a nested call
    if ( nestedCall != null && nestedCall.getValue( propagate ) != null ) {
      // REVIEW -- if this is buggy, consider wrapping object in a Parameter and
      // making this a dependency.  Cached newObject of constructor is similar.
//    if ( propagate || object == null ) {
      object = Expression.evaluate( nestedCall.getValue( propagate ), null,
                                    propagate, false );
      //      }
    }
    if ( Debug.isOn() ) Debug.outln( "About to invoke a "
        + getClass().getSimpleName() + ": "
        + this );
    // Make sure we have the right object from which to invoke the member.
    Member m = getMember();
    Class<?> cls = ( m == null ? null : m.getDeclaringClass() );
    Object evaluatedObj = Expression.evaluate( object, cls, propagate, true );

    return evaluatedObj;
  }

  
  /**
   * Wrap the variable args in an array as a single arg to the vararg.
   * 
   * @param evaluatedArgs
   * @return
   */
  protected Object[] fixArgsForVarArgs( Object[] evaluatedArgs, boolean complain ) {
    if ( !isVarArgs() || evaluatedArgs == null ) return evaluatedArgs;
    int paramSize = getParameterTypes().length;
    if ( evaluatedArgs.length < paramSize - 1 ) {
      return evaluatedArgs;
    }
    try {
      Object[] newArgs = new Object[ paramSize ];
      for ( int i = 0; i < paramSize - 1; ++i ) {
        newArgs[ i ] = evaluatedArgs[ i ];
      }
      Class< ? > lastParamType = getParameterTypes()[getParameterTypes().length - 1];
      Object varArgArray = Array.newInstance(lastParamType.getComponentType(), evaluatedArgs.length - paramSize + 1);
      for ( int i = paramSize - 1, j = 0; i < evaluatedArgs.length; ++i, ++j ) {
        Array.set(varArgArray, j, evaluatedArgs[ i ]);
      }
      newArgs[ paramSize - 1 ] = varArgArray;
      return newArgs;
    } catch ( Throwable t ) {
      if (complain) t.printStackTrace();
      return evaluatedArgs;
    }
  }

  /**
   * Try to match arguments to parameters by evaluating or creating expressions.
   * 
   * @param propagate
   * @return the evaluated arguments
   * @throws ClassCastException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws InstantiationException
   */
  public Object[] evaluateArgs( boolean propagate ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( getMember() == null ) return null;
    Class< ? >[] paramTypes = getParameterTypes();
    return evaluateArgs( propagate, paramTypes, arguments, isVarArgs(), true );
  }

  /**
   * @param propagate
   * @param c
   * @param unevaluatedArg
   * @param isVarArg is true if the type, c, is for a variable length parameter type
   * @return
   * @throws ClassCastException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws InstantiationException
   */
  public Object evaluateArg( boolean propagate,
                             Class< ? > c,
                             Object unevaluatedArg,
                             boolean isVarArg,
                             boolean complainIfError ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    //Object unevaluatedArg = arg;
    if ( Debug.isOn() ) Debug.outln("Call.evaluateArgs(): unevaluated arg = " + unevaluatedArg );
//    if ( paramType == null ) {
//      System.err.println("evaluateArg() " + arg + " don't match parameters " + Utils.toString(paramTypes, false) );
//      return unevaluatedArg;
//    }
    if ( c != null ) {
        Class< ? > np = ClassUtils.classForPrimitive( c );
        if ( np != null ) c = np;
    }
    if ( c != null && c.equals( Object.class ) ) c = null;
    if ( c != null && isVarArg ) {
      if ( !c.isArray() ) {
        if ( complainIfError ) {
          Debug.error( true, true, "class " + c.getSimpleName() + " should be a var arg array!" );
        }
      } else {
        c = c.getComponentType(); // TODO -- don't we need to pass info along
                                  // about whether the result should be an
                                  // array?
      }
    }
    if ( ( c == null || c.equals( Object.class ) ) // || Expression.class.isAssignableFrom( c ) )
         && unevaluatedArg instanceof Wraps ) {
      c = ((Wraps)unevaluatedArg).getType();
    }
    //Object result = Expression.evaluate( unevaluatedArg, c, propagate, true );
    Object result = evaluateArg( unevaluatedArg, c, propagate);
    if ( complainIfError && !( result == null || c == null || c.isInstance( result ) )) {
      Debug.error( true, "\nArgument " + result +
                         ( result == null ?
                           "" : " of type " + result.getClass().getCanonicalName() )
                         + " is not an instance of " + c.getSimpleName() + " for call to " + this.getMember() );
    }
    return result;
  }

  public Object evaluateArg( Object unevaluatedArg, Class< ? > c,
                             boolean propagate ) throws ClassCastException,
                                                 IllegalAccessException,
                                                 InvocationTargetException,
                                                 InstantiationException {
    if ( c == Object.class ) {
      c = null;
    }
    return Expression.evaluate( unevaluatedArg, c, propagate, true );
  }

  // Try to match arguments to parameters by evaluating or creating expressions.
  public Object[] evaluateArgs( boolean propagate,
                                Class< ? >[] paramTypes,
                                Vector< Object > args,
                                boolean isVarArgs,
                                boolean complainIfError ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if( args == null ) {
      Debug.error("Error! args is null!");
      return null;
    }
    //boolean wasDebugOn = Debug.isOn();
    //Debug.turnOff();
    assert ( args.size() == paramTypes.length
             || ( isVarArgs && ( args.size() > paramTypes.length
                                 || paramTypes.length == 1 ) ) );
    Object argObjects[] = new Object[args.size()];
    for ( int i = 0; i < args.size(); ++i ) {
      Object unevaluatedArg = args.get( i );
      if ( Debug.isOn() ) Debug.outln("Call.evaluateArgs(): unevaluated arg = " + unevaluatedArg );
      Class< ? > c = null;
      if ( paramTypes.length == 0 ) {
        System.err.println("evaluateArgs() " + args + " don't match parameters " +
                           Utils.toString(paramTypes, false) + " in call: " + this );
        break;
      } else {
        c = paramTypes[ Math.min(i,paramTypes.length-1) ];
      }
      boolean isVarArg = i >= paramTypes.length-1 && isVarArgs;
      argObjects[i] = evaluateArg(propagate, c, unevaluatedArg, isVarArg, complainIfError);
      //Expression.evaluate( unevaluatedArg, c, propagate, true );
      if ( complainIfError && Debug.isOn()
           && !( argObjects[ i ] == null
                 || c == null
                 || c.isInstance( argObjects[ i ] )
                 || ( isVarArg && c.isArray()
                      && c.getComponentType()
                          .isInstance( argObjects[ i ] ) ) ) ) {
        Debug.error( true, false, "\nArgument " +argObjects[ i ] +
                           ( argObjects[ i ] == null ?
                             "" : " of type " + argObjects[ i ].getClass().getCanonicalName() )
                           + " is not an instance of " + c.getSimpleName() +
                           " for argument " + i + " of call: " + this );
      }
    }
    //if ( wasDebugOn ) Debug.turnOn();
    if ( Debug.isOn() ) Debug.outln( "Call.evaluateArgs(" + args + ") = "
                 + Utils.toString( argObjects ) );
    return argObjects;
  }
  
  @Override
  public Class< ? > getType() {
    return getReturnType();
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Wraps#getTypeNameForClassName(java.lang.String)
   */
  @Override
  public String getTypeNameForClassName( String className ) {
    return getType().getSimpleName();
  }

  @Override
  public Class< ? > getPrimitiveType() {
    Class< ? > c = null;
    if ( getType() != null ) {
      c = ClassUtils.primitiveForClass( getType() );
      Object r = null;
      try {
        r = evaluate( false );
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
      if ( c == null && r != null
           && Wraps.class.isInstance( r ) ) {// isAssignableFrom( getType() ) ) {
        c = ( (Wraps< ? >)r ).getPrimitiveType();
      }
    }
    return c;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#getValue(boolean)
   */
  @Override
  public Object getValue( boolean propagate ) {
    try {
      return evaluate( propagate );
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
    return null;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#hasValue()
   */
  @Override
  public boolean hasValue() {
    boolean r =  returnValue != null ||
                 didEvaluationSucceed();
    return r;
  }



  @Override
  public void setValue( Object value ) {
    // TODO -- this could be used to set free values when inverting the function
    Debug.error( false, "Error! Call.setValue() is not yet supported!" );
  }
  
  /**
   * @return the evaluationSucceeded
   */
  public boolean didEvaluationSucceed() {
    return evaluationSucceeded;
  }

  @Override
  public boolean substitute( Parameter< ? > p1, Parameter< ? > p2, boolean deep,
                             Set<HasParameters> seen ) {
    return substitute( p1, (Object)p2, deep, seen );
  }
  @Override
  public synchronized boolean substitute( Parameter< ? > p1, Object p2, boolean deep,
                             Set<HasParameters> seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;

    // TODO -- use HasParameters.Helper!!
    boolean subbed = false;
    if ( !isStatic() ) {
      if (p1 == object) {
        object = p2;
        subbed = true;
      }
      if (!subbed && HasParameters.Helper.substitute(object, p1, p2, deep, seen, true)) {
        subbed = true;
      }
    }
    if ( HasParameters.Helper.substitute( arguments, p1, p2, deep, seen, true )) {
      subbed = true;
    }
    if ( p1 == nestedCall && p2 instanceof Parameter ) {
      nestedCall = (Parameter< Call >)p2; // REVIEW -- If p2 is set to something other than a Call, then this is trouble!
      subbed = true;
    } else if ( HasParameters.Helper.substitute( nestedCall, p1, p2, deep, seen, true ) ) {
      subbed = true;
    }
    Object retVal = null;
    Object[] evalArgs = null;
    if ( subbed ) {
      clearCache();
    } else {
      if ( p1 == returnValue ) {
        retVal = p2;
        subbed = true;
      } else if ( HasParameters.Helper.substitute( evaluatedArguments, p1, p2,
                                                   deep, seen, true ) ) {
        evalArgs = evaluatedArguments;
        subbed = true;
      }
      
    }
    if ( subbed ) {
      setStale(true);
      if ( retVal != null ) {
        setReturnValue(retVal);
      }
      if ( evalArgs != null ) {
        evaluatedArguments = evalArgs;
      }
    }
    return subbed;
  }
  
  
  public Set< Parameter< ? > >
         getMemberParameters( Object o, boolean deep,
                              Set< HasParameters > seen ) {
    // seen set is checked later (if o instanceof Expression)
    Set< Parameter< ? > > set = null;
    if ( o instanceof Expression ) {
       Object exp = ((Expression) o).getExpression();
       if ( exp instanceof HasParameters ) {
         // return empty set if we've already visited this expression.
         Pair<Boolean, Set<HasParameters>> pair = Utils.seen((HasParameters)exp, deep, seen);
         if (pair.first) return Utils.getEmptySet();
         seen = pair.second;
       }
       // need to remove from seen set because HasParameters.Helper.getParameters()
       // is passing true for checkIfHasParameters.
       if ( seen != null ) seen.remove(exp);
       set = getMemberParameters( exp, deep, seen );
    } else if ( deep || o instanceof Call ) {
      set = HasParameters.Helper.getParameters( o, deep, seen, true );
    } else if ( set == null ) {
      set = new LinkedHashSet< Parameter< ? > >();
    }
    if ( o instanceof Parameter ) set.add( (Parameter<?> )o);

    return set;
  }

  @Override
  public Parameter< ? > getParameter( String name ) {
    return HasParameters.Helper.getParameter( this, name );
  }


  @Override
  public Set< Parameter< ? > > getParameters( boolean deep,
                                              Set<HasParameters> seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    Set< Parameter< ? > > set = new LinkedHashSet< Parameter< ? >>();
    if ( !isStatic() ) {
      // check object for Parameters
      if (object instanceof Parameter) set.add((Parameter<?>) object);
      // check for Parameter in an Expression as a shortcut to getMemberParameters()
      else if ( object instanceof Expression && ((Expression)object).expression instanceof Parameter ) {
        set.add( (Parameter<?>)((Expression)object).expression );
      } else if ( !deep ) {  // deep==true case is processed below for all object types
        set = Utils.addAll(set, getMemberParameters(object, deep, seen));
      }
      // check for nested Parameters if deep == true
      if ( deep ) {
        set = Utils.addAll(set, getMemberParameters(object, deep, seen));
      }
    }

    for (Object o : arguments) {
      set = Utils.addAll( set, getMemberParameters( o, deep, seen));
    }

    //if ( nestedCall != null ) {//&& nestedCall.getValue() != null ) {
      // REVIEW -- bother with adding nestedCall as a parameter?
    set = Utils.addAll( set, HasParameters.Helper.getParameters( nestedCall, deep, seen, true ) );
//      set = Utils.addAll( set, nestedCall.getValue().getParameters( deep, seen ) );
    //}
    set = Utils.addAll( set,
                        HasParameters.Helper.getParameters( returnValue, deep,
                                                            seen, true ) );
    set = Utils.addAll( set, 
                        HasParameters.Helper.getParameters( evaluatedArguments,
                                                            deep, seen, true ) );
    
    return set;
  }

  @Override
  public Set< Parameter< ? > > getFreeParameters( boolean deep,
                                                  Set<HasParameters> seen ) {
    Assert.assertFalse( "This method should not be called since a Function"
                        + " does not differentiate between free and dependent"
                        + " parameters.", true );
    return null;
  }
  @Override
  public void setFreeParameters( Set< Parameter< ? >> freeParams,
                                 boolean deep,
                                 Set<HasParameters> seen ) {
    Assert.assertTrue( "This method is not supported!", false );
  }
  
  public synchronized boolean areArgumentsGrounded( boolean deep,
                                                    Set< Groundable > seen ) {
    // Check if arguments are grounded if groundable.  Ok for arguments to be null.
    if ( arguments != null ) {
      for ( Object o : arguments ) {
        if ( o != null && o instanceof Groundable
             && !( (Groundable)o ).isGrounded( deep, seen ) ) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public synchronized boolean isGrounded( boolean deep, Set< Groundable > seen ) {
    Pair< Boolean, Set< Groundable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;
    if ( getMember() == null ) return false;
    // Check types without throwing exception (like checkForTypeErrors().
    Class< ? >[] paramTypes = getParameterTypes();
    int ecLength = 0;

    // Check the object
    if ( !this.isStatic() ) {
      if ( (nestedCall == null && getObject() == null) || ( getObject() instanceof Parameter && !((Parameter) getObject()).hasValue()) ) {
        return false;
      }
    }
    // Code below commented out because it assumes that the enclosing object must be an argument, but the object is
    // not stuffed into arguments and only stuffed into the evaluatedArgs just before invoking.
//    if ( this instanceof ConstructorCall) {
//      ConstructorCall cc = (ConstructorCall) this;
//      if ( cc.thisClass.getEnclosingClass() != null && !Modifier.isStatic( cc.thisClass.getModifiers() )) {
//        ecLength = 1;
//      }
//    }
//    if ( paramTypes.length > ecLength
//         && ( arguments == null || (!isVarArgs() && arguments.size() - ecLength != paramTypes.length ) ) ) {
//
//      return false;
//    }
    if ( !areArgumentsGrounded( deep, seen ) ) {
      return false;
    }
    if ( nestedCall == null ) {
//      if ( !Modifier.isStatic( getMember().getModifiers() ) &&
//           object == null ) return false;
    } else {
      if ( !nestedCall.isGrounded( deep, seen ) ) return false;
    }
    return true;
  }

  @Override
  public boolean ground( boolean deep, Set< Groundable > seen ) {
    if ( seen != null && seen.contains( this ) ) return true;
    if ( isGrounded( deep, null ) ) return true;
    //this.returnValue = null;
    setStale( true );
    return groundImpl( deep, seen );
  }

  public synchronized boolean groundImpl( boolean deep, Set< Groundable > seen ) {
    Pair< Boolean, Set< Groundable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;
    boolean grounded = true;
    if ( getMember() == null ) return false;

    // Check the object
    if ( !this.isStatic() ) {
      if ( nestedCall == null && getObject() instanceof Groundable &&
           ( (getObject() instanceof Parameter && !((Parameter) getObject()).hasValue()) ||
             (!(getObject() instanceof Parameter) && !( (Groundable)getObject() ).isGrounded(deep, seen)) ) ) {
        if ( !( (Groundable)getObject() ).ground( deep, seen ) ) {
          grounded = false;
        }
      }
      if ( nestedCall == null && getObject() == null ) {
        grounded = false;
      }
    }

    // Check types without throwing exception (like checkForTypeErrors().
    Class< ? >[] paramTypes = getParameterTypes();
    if ( paramTypes.length > 0
         && ( arguments == null || arguments.size() != paramTypes.length ) )   {
      return false;
    }
    // Ground groundable arguments.  OK for arguments to be null.
    if ( arguments != null ) {
      for ( Object o : arguments ) {
        if ( o != null && o instanceof Groundable
             && !( (Groundable)o ).ground( deep, seen ) ) {
          grounded = false;
        }
      }
    }
    if ( nestedCall != null ) {
      if ( !nestedCall.ground( deep, seen ) ) {
        grounded = false;
      } else if ( object == null ) {
        if ( nestedCall.getValue(true) == null && !isStatic() ) {
          grounded = false;
        } else {
          try {
            object = nestedCall.getValue(true).evaluate( deep );
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
          if ( object == null ) grounded = false;
        }
      }
    }
    if ( grounded ) setStale( true );
    return grounded;
  }
  
//  public synchronized String toLongString() {
//    StringBuffer sb = new StringBuffer();
//    if ( getMember() == null ) {
//      sb.append( "null" );
//    } else {
//      sb.append( getMember().getName() );
//      sb.append( MoreToString.Helper.toShortString( arguments, 
//                                                    MoreToString.PARENTHESES,
//                                                    null,
//                                                    true ) );
//    }
//    return sb.toString();
//  }
  
  @Override
  public synchronized String toShortString() {
    StringBuffer sb = new StringBuffer();
    if ( getMember() == null ) {
      sb.append( "null" );
    } else {
      sb.append( getMember().getName() );
      sb.append( MoreToString.Helper.toShortString( arguments, 
                                                    MoreToString.PARENTHESES,
                                                    null,
                                                    true ) );
    }
    return sb.toString();
  }
  
  @Override
  public String toString() {
    return MoreToString.Helper.toString( this );
  }
  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen ) {
    return toString( withHash, deep, seen, null );
  }
  @Override
  public synchronized String toString(boolean withHash, boolean deep, Set< Object > seen,
                                      Map< String, Object > otherOptions) {
    Pair< Boolean, Set< Object > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) deep = false;
    seen = pair.second;
    StringBuffer sb = new StringBuffer();
    if ( nestedCall != null ) {
      sb.append( //"nested::" + 
                 nestedCall.toString(withHash, deep, seen,
                                     otherOptions) );
      if ( object != null ) {
        sb.append( "-->");
      } else {
        sb.append( "." );
      }
    }
    if ( object != null && !isStatic() ) {
      TimeVaryingMap tvm = Functions.tryToGetTimelineQuick( object );
      if ( tvm != null ) {
        sb.append(MoreToString.Helper.toString(object, withHash, false, seen, otherOptions) + ".");
      } else {
        if ( object instanceof DurativeEvent ) {
          sb.append( ((DurativeEvent)object).getName() + "." );
        } else if ( object instanceof Class ) {
            sb.append( ClassUtils.toString( (Class<?>)object ) + "." );
        } else {
          sb.append( object.toString() + "." );
        }
      }
    }
    if ( getMember() == null ) {
      sb.append( "null" );
    } else {
      sb.append( getMember().getName() );
      if ( deep ) {
        sb.append("(");
        boolean first = true;
        for ( Object arg : arguments ) {
          if ( first ) {
            first = false;
          } else {
            sb.append(", ");
          }
          //TimeVaryingMap tvm = Functions.tryToGetTimelineQuick( arg );
          //if ( tvm != null ) {
            sb.append(MoreToString.Helper.toString(arg, withHash, false, seen, otherOptions));
          //} else {
          //  sb.append(MoreToString.Helper.toString(arg, withHash, false, seen, otherOptions));
          //}
        }
        sb.append(")");
      } else {
        sb.append(MoreToString.Helper.toString(arguments, withHash, deep, seen,
                otherOptions,
                MoreToString.PARENTHESES, true));
      }
      if ( !Utils.isNullOrEmpty( evaluatedArguments ) ) {
        sb.append( " = " );
        sb.append( getMember().getName() );
        sb.append( MoreToString.Helper.toString( evaluatedArguments, withHash,
                                                 deep, seen, otherOptions ) );
      }
      if ( returnValue != null ) {
        sb.append( " = " + MoreToString.Helper.toString(returnValue, withHash, false, seen, otherOptions));
      }
    }
    return sb.toString();
  }

  protected static boolean possiblyStale( Object obj ) {
    //if ( obj == null || obj instanceof TimeVarying ) return true;
    if ( obj == null ) return true;
    if ( obj instanceof LazyUpdate && ((LazyUpdate)obj).isStale() ) return true;
    if ( obj instanceof Variable ) {
      Object v = ((Variable<?>)obj).getValue( false );
      if ( possiblyStale( v ) ) return true;
    }
    return false;
  }
  
  public boolean isStaleNoPropagate() {
    return stale;
  }
  @Override
  public boolean isStale() {
    if ( alwaysNotStale ) {
      return false;
    }
    if ( stale ) {
//      try {
//        if ( Random.global.nextDouble() < 0.3 ) {
//          evaluate(false);
//        }
//        return stale;
//      } catch (IllegalAccessException e) {
//      } catch (InvocationTargetException e) {
//      } catch (InstantiationException e) {
//      }
      return true;
    }
    if ( evaluatedArguments != null ) {
      for ( Object arg : evaluatedArguments ) {
        if ( arg instanceof LazyUpdate )  {
          if ( ( (LazyUpdate)arg ).isStale() ) {
            setStale( true );
            return true;
          }
        }
      }
    }

    boolean argsStale = areArgsStale();
    if ( argsStale ) {
      return true;
    }

    for ( Parameter< ? > p : getParameters( false, null ) ) {
      if ( p.isStale() && ( !( p.getValueNoPropagate() instanceof ParameterListenerImpl ) || !isGetMember() ) ) {
        setStale( true );
        return true;
      }
    }
    if ( nestedCall != null ) {
      if ( nestedCall.isStale() ) {
        setStale( true );
        return true;
      }
    }
    if ( object instanceof LazyUpdate && !isStatic() )  {
      if ( ( (LazyUpdate)object ).isStale() ) {
        setStale( true );
        return true;
      }
    }
    if ( possiblyStale( returnValue ) )
      return true;
    return false;
  }

  public boolean areArgsStale() {
    if ( Utils.isNullOrEmpty(arguments ) ) {
      return false;
    }
    Boolean isGetMember = null;
    for ( Object arg : arguments ) {
      if ( arg instanceof LazyUpdate )  {
        if ( ( (LazyUpdate)arg ).isStale() ) {
          // HACK -- isn't there a better way?
          // Check if we're just getting a member of a ParameterListenerImpl,
          // in which case, the evaluated arguments will determine whether
          // it's really stale.
          if (isGetMember == null) {
            isGetMember = isGetMember();
          }
          if ( Boolean.TRUE.equals( isGetMember ) ) {
            ParameterListenerImpl pli = null;
            try {
              pli = Expression.evaluate(arg, ParameterListenerImpl.class, false);
            } catch (Throwable e) {
            }
            if (pli != null) {
              continue;
            }
          }
          setStale( true );
          return true;
        }
      }
    }
    return false;
  }

  public boolean isGetMember() {
    return getMember() != null && getMember().getName() != null && getMember().getName().contains("getMember");
  }

  protected void setReturnValue( Object value ) {
    returnValue = value;
  }

  protected void clearCache() {
    setReturnValue(null);
    evaluatedArguments = null;
  }
  
  @Override
  public void setStale( boolean staleness ) {
    if ( stale != staleness && Debug.isOn() ) Debug.outln( "setStale(" + staleness + "): "
                                                    + toShortString() );
    if ( staleness ) {
      clearCache();
    }
    stale = alwaysStale || staleness;
  }


  /**
   * Recursively make this and associated arguments, parameters stale.
   * @param staleness desired staleness
   * @param deep whether or not to recurse - will pass staleness onto setStale(boolean) if false
   * @param seen Set of LazyUpdate objects that have already been seen - will be populated during the recursion
   */
  @Override
  public void setStale(boolean staleness, boolean deep, Set<LazyUpdate> seen) {
    if (deep) {
      Pair< Boolean, Set< LazyUpdate > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return;
      seen = pair.second;

      if ( alwaysNotStale ) {
        return;
      }

      setStale(staleness);

      if ( evaluatedArguments != null ) {
        for ( Object arg : evaluatedArguments ) {
          if ( arg instanceof LazyUpdate )  {
            ((LazyUpdate) arg).setStale(staleness, deep, seen);
          }
        }
      }

      if ( arguments != null ) {
        for ( Object arg : arguments ) {
            if ( arg instanceof LazyUpdate )  {
                ((LazyUpdate) arg).setStale(staleness, deep, seen);
            }
        }
      }

      for ( Parameter< ? > p : getParameters( false, null ) ) {
        setStale(staleness, deep, seen);
      }
      if ( nestedCall != null ) {
        nestedCall.setStale(staleness, deep, seen);
      }
      if ( object instanceof LazyUpdate && !isStatic() )  {
        ((LazyUpdate) object).setStale(staleness, deep, seen);
      }
    }

    else {
      setStale(staleness);
    }
  }

  @Override
  public boolean hasParameter( Parameter< ? > parameter, boolean deep,
                               Set<HasParameters> seen ) {
    if ( parameter == null ) return false;
    Object val = parameter.getValueNoPropagate();
    Set< Parameter< ? > > parameters = getParameters( deep, seen );
    if ( val instanceof HasOwner ) {
      for ( Parameter<?> p : parameters ) {
        if ( val.equals( p.getValueNoPropagate() ) ) {
          return true;
        }
      }
    }
    boolean b = parameters.contains( parameter );
    return b;
  }

  @Override
  public boolean isFreeParameter( Parameter< ? > p, boolean deep,
                                  Set<HasParameters> seen ) {
    // REVIEW -- Is this just done by Events? Maybe throw
    // assertion that this method id not supported for ElaborationRule.
    return false;
  }

  // Getters and setters 
  
  /**
   * @return the object
   */
  public Object getObject() {
    return object;
  }

  /**
   * @param object the object to set
   */
  public void setObject( Object object ) {
    if ( this.object != object ) {
      this.object = object;
      setStale( true );
    }
  }

//  /**
//   * @return the arguments
//   */
//  public Vector< Object > getEvaluatedArguments() {
//    return evaluatedArguments;
//  }

//  /**
//   * @return the arguments
//   * The caller is responsible for setting the stale flag if the arguments are modified.
//   */
//  public Vector< Object > getArgument() {
//    return arguments;
//  }
  /**
   * @param n
   * @return the nth argument
   */
  public Object getArgument(int n) {
    if ( arguments.size() <= n ) {
      return null;
    }
    return arguments.get( n );
  }

  /**
   * @return a the arguments in an array
   */
  public Object[] getArgumentArray() {
    return arguments.toArray();
  }
  
  /**
   * @return a copy of the arguments as a {@link Vector}
   */
  public Vector<Object> getArgumentVector() {
    return new Vector<Object>( arguments );
  }

  /**
   * @param arguments the arguments to set
   */
  public synchronized void setArguments( Vector< Object > arguments ) {
    if ( arguments != this.arguments ) {
      this.arguments = arguments;
      setStale( true );
    }
  }

  /**
   * @param i index of argument to set
   * @param argument the argument to set
   */
  public synchronized void setArgument( int i, Object argument ) {
    while ( arguments.size() < i ) {
      arguments.add( null );
      setStale( true );
    }
    if ( i == arguments.size() ) {
      arguments.add( argument );
      setStale( true );
    } else if ( arguments.get( i ) != argument ) {
      this.arguments.set(i, argument);
      setStale( true );
    }
  }

  /**
   * @return the evaluatedArguments
   */
  public Object[] getEvaluatedArguments() {
    return evaluatedArguments;
  }
  /**
   * @param evaluatedArguments the evaluatedArguments to set
   */
  public void setEvaluatedArguments( Object[] evaluatedArguments ) {
    this.evaluatedArguments = evaluatedArguments;
  }
  /**
   * @return the arguments
   */
  public Vector< Object > getArguments() {
    return arguments;
  }
  /**
   * @return the nestedCall
   * The caller is responsible for setting stale = true if modifying the nestedCall. 
   */
  public Call getNestedCall() {
    return (nestedCall == null ? null : nestedCall.getValue(false) );
  }

  /**
   * @param nestedCall the nestedCall to set
   */
  public void setNestedCall( Call nestedCall ) {
    if ( this.nestedCall == null ) {
      this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
    } else {
      this.nestedCall.setValue( nestedCall );
    }
    setStale( true );
  }
  
  public Class< ? > getTypeForSubstitutionIndex( int index ) {
    // If the argument substitutes for the object of the call,
    // check and see if the object should be a Collection.
    if ( index == 0 ) {
        Class< ? > objTypeReqd = getObjectType();
        return objTypeReqd;
    } else {
        Class< ? >[] types = getParameterTypes();
        if ( Utils.isNullOrEmpty( types ) ) return null;
        if ( types.length < index ) {
          if ( isVarArgs() ) {
            return types[types.length-1];
          }
          return null;
        }
        return types[index-1];
    }
  }


  abstract public Domain< ? > calculateDomain( boolean propagate, Set< HasDomain > seen );

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.HasDomain#getDomain(boolean, java.util.Set)
   */
  @Override
  public Domain< ? > getDomain( boolean propagate, Set< HasDomain > seen ) {
//    if ( domain == null || domainIsStale() ) {
      domain = calculateDomain(propagate, seen);
//    }
    return domain;
  }
  
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.HasDomain#restrictDomain(gov.nasa.jpl.ae.solver.Domain, boolean, java.util.Set)
   */
  @Override
  public <T> Pair< Domain< T >, Boolean > restrictDomain( Domain< T > domain, boolean propagate,
                                         Set< HasDomain > seen ) {
    // This should be overridden
    boolean changed = false;
    Domain< ? > thisDomain = this.getDomain(propagate, seen);
    // Commenting out below since the domain is not really changed, and the
    // restriction is not passed on to the arguments.
//    if ( thisDomain != null ) {
//      changed = thisDomain.restrictTo(domain);
//    }
//    thisDomain = this.getDomain(propagate, seen);
    return new Pair<Domain<T>, Boolean>((Domain<T>)thisDomain, changed);
  }

  // The following code was re-factored from MethodCall:
  /**
   * @param objects
   * @param call the Call to invoke on each object in the Collection
   * @param indexOfObjectArgument
   *            where in the list of arguments an Object from the Collection
   *            is substituted (1 to total number of args or 0 to indicate
   *            that the objects are each substituted for
   *            methodCall.objectOfCall).
   * @return the results of the Call on each of the objects
   */
  public static Collection< Object > map( Collection< ? > objects,
                                             Call call,
                                             int indexOfObjectArgument ) {
      return call.map( objects, indexOfObjectArgument );
  }
  /**
   * @param objects
   * @param indexOfObjectArgument
   *            where in the list of arguments an object from the Collection
   *            is substituted (1 to total number of args or 0 to indicate
   *            that the objects are each substituted for
   *            methodCall.objectOfCall).
   * @return the results of the Call on each of the objects
   */
  public Collection< Object > map( Collection< ? > objects,
                                       int indexOfObjectArgument ) {
      Collection< Object > coll = new ArrayList<Object>();
      try {
        if ( objects != null ) for ( Object o : objects ) {
            sub( indexOfObjectArgument, o );
            Object result = null;
            result = evaluate(true);
            coll.add( result );
        }
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

      return coll;
  }
  
  /**
   * Substitute an object for a specified argument in this Call.
   * 
   * @param indexOfArg
   *            the index of the argument to be replaced
   * @param obj
   *            the replacement for the argument
   */
  protected synchronized void sub( int indexOfArg, Object obj ) {
    sub( this, indexOfArg, obj );
  }
  protected static void sub( Call call, int indexOfArg, Object obj ) {
    sub(call, (Vector< Object >)null, indexOfArg, obj );
  }
  protected static void sub( Call call, Vector< Object > arguments, int indexOfArg, Object obj ) {
      if ( arguments == null ) arguments = call.arguments;
      if ( indexOfArg < 0 ) Debug.error("bad indexOfArg " + indexOfArg );
      else if ( indexOfArg == 0 ) {
        if ( call.object != obj ) {
          call.object = obj;
          if ( !call.isStatic() ) {
            call.setStale(true);
          }
        }
      }
      else {
        if ( indexOfArg > arguments.size() ) {
          if ( Debug.isOn() ) Debug.err( "bad index " + indexOfArg + "; only " + arguments.size() + " arguments!  Adding null argument placeholders!" );
          if ( indexOfArg > 100 ) {
            Debug.error( "bad index " + indexOfArg + "; greater than 100" );
          } else {
            while ( indexOfArg > arguments.size() ) {
              arguments.add( null );
            }
          }
        }
        if ( arguments.get( indexOfArg - 1 ) != obj ) {
          arguments.set(indexOfArg-1,obj);
          call.setStale( true );
        }
      }
  }
  
  /**
   * Replace the entry of the array at the specified index with the specified
   * object.
   * 
   * @param arguments
   * @param indexOfArg
   * @param obj
   */
  protected static void sub( Object[] arguments, int indexOfArg, Object obj ) {
    if ( indexOfArg < 0 ) Debug.error("bad indexOfArg " + indexOfArg );
    else if ( arguments == null ) return;
    else if ( indexOfArg >= arguments.length ) {
      Debug.error( "bad index " + indexOfArg + "; only " + arguments.length
                   + " arguments!" );
    }
    else arguments[indexOfArg] = obj;
  }
  /**
   * Similar to {@link #sub(Call, Vector, int, Object)} except the arguments are
   * not the arguments of the call.
   * 
   * @param call
   * @param arguments
   * @param indexOfArg
   * @param obj
   */
  protected static void sub( Call call, Object[] arguments, int indexOfArg, Object obj ) {
    if ( indexOfArg < 0 ) Debug.error("bad indexOfArg " + indexOfArg );
    else if ( indexOfArg == 0 ) {
      call.object = obj;
      if ( !call.isStatic() ) {
        call.setStale(true);
      }
    }
    else if ( arguments == null ) return;
    else if ( indexOfArg > arguments.length ) Debug.error( "bad index "
                                                           + indexOfArg
                                                           + "; only "
                                                           + arguments.length
                                                           + " arguments!" );
    else arguments[indexOfArg-1] = obj;
  }
  ////////
  
  
  /**
   * Compute a transitive closure of a set using this MethodCall as a relation from an argument to the return value.
   * @param initialSet the Set of initial items to be substituted for an argument or the object of this MethodCall
   * @param indexOfObjectArgument
   *            where in the list of arguments an object from the set
   *            is substituted (1 to total number of args or 0 to indicate
   *            that the objects are each substituted for
   *            methodCall.objectOfCall).
   * @param maximumSetSize the size of the resulting set will be limited to the maximum of this argument and the size of initialSet 
   * @return a new Set that includes the initialSet and the results of applying the methodCall on each item (substituting the argument for the given index) in the new Set  
   */
  public < XX > Set< XX > closure( Set< XX > initialSet,
                                   int indexOfObjectArgument, int maximumSetSize ) {
      Set< XX > closedSet = new TreeSet< XX >( CompareUtils.GenericComparator.instance() );
      closedSet.addAll( initialSet );
      ArrayList< XX > queue =
              new ArrayList< XX >( initialSet );
      Set< XX > seen = new LinkedHashSet< XX >();
      try {
      while ( !queue.isEmpty() ) {
          XX item = queue.get( 0 );
          queue.remove( 0 );
          sub( indexOfObjectArgument, item );
          if ( seen.contains( item ) ) continue;
          seen.add( item );
          Object result = null;
            result = evaluate( true, true );
          if ( !evaluationSucceeded ) continue;
          Collection< XX > newItems = null;
          try {
              if ( result instanceof Collection ) {
                  newItems = (Collection< XX >)result;
              } else {
                  newItems = (Collection< XX >)Utils.newSet( result );
              }
          } catch ( ClassCastException e ) {
              continue;
          }
          if ( !Utils.isNullOrEmpty( newItems ) ) {
              Utils.addN( closedSet, maximumSetSize - closedSet.size(), newItems );
          }
      }
      } catch ( IllegalAccessException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      } catch ( InvocationTargetException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      } catch ( InstantiationException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      }  // TODO -- args right?
      return closedSet;
  }
  
  /**
   * Compute a transitive closure of a map using this MethodCall to specify for each key in the map a set of items that should have a superset of related items in the map.
   * @param relationMapToClose the Set of initial items to be substituted for an argument or the object of this MethodCall
   * @param indexOfObjectArgument
   *            where in the list of arguments an object from the set
   *            is substituted (1 to total number of args or 0 to indicate
   *            that the objects are each substituted for
   *            methodCall.objectOfCall).
   * @param maximumSetSize the size of the resulting set will be limited to the maximum of this argument and the size of initialSet 
   * @return a new Set that includes the initialSet and the results of applying the methodCall on each item (substituting the argument for the given index) in the new Set  
   */
  public < XX, C extends Map< XX, Set< XX > > > C mapClosure( C relationMapToClose,
                                                              int indexOfObjectArgument,
                                                              int maximumSetSize ) {
      ArrayList< XX > queue =
              new ArrayList< XX >( relationMapToClose.keySet() );
//      Set< XX > seen = new LinkedHashSet< XX >();
      try {
      while ( !queue.isEmpty() ) {
          XX item = queue.get( 0 );
          queue.remove( 0 );
          sub( indexOfObjectArgument, item );
//          if ( seen.contains( item ) ) continue;
//          seen.add( item );
//          Method method =
//                  ClassUtils.getMethodForArgs( AbstractSystemModel.class, "isA",
//                                               item, item );
//          MethodCall methodCall =
//                  new MethodCall( null, method,
//                                  new Object[] { null, item } );
          Object result = null;
            result = evaluate( true, true );
          if ( !evaluationSucceeded ) continue;
          Collection< XX > isItemSet = null;
          try {
              if ( result instanceof Collection ) {
                  isItemSet = (Collection< XX >)result;
              } else {
                  isItemSet = (Collection< XX >)Utils.newSet( result );
              }
          } catch ( ClassCastException e ) {
              continue;
          }
          Set< XX > relatedToItem = relationMapToClose.get( item );
          for ( XX isA : isItemSet ) {
              Set< XX > related = relationMapToClose.get( isA );
              int ct = 0;
              if ( related == null ) {
                  related = new TreeSet< XX >(CompareUtils.GenericComparator.instance());
                  relationMapToClose.put( isA, related );
              } else {
                  ct = related.size();
              }
              related.addAll( relatedToItem );
              if ( related.size() > ct ) {
                  queue.add( isA );
              }
              if ( relationMapToClose.size() >= maximumSetSize ) break;
          }
      }
      } catch ( IllegalAccessException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      } catch ( InvocationTargetException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      } catch ( InstantiationException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      }  // TODO -- args right?
      return relationMapToClose;
  }
  public Object getOtherArg( Object theArg ) {
    LinkedHashSet< Object > otherArgs = getOtherArgs( theArg );
    int n = Random.global.nextInt( otherArgs.size() );
    Iterator<Object> iter = otherArgs.iterator();
    Object otherArg = null;
    for (int i = 0; i != n; ++i) {
      otherArg = iter.next();
    }
    return otherArg;
  }
  public LinkedHashSet<Object> getOtherArgs( Object theArg ) {
    //FunctionCall f = (FunctionCall)this.expression;
    Object otherArg = null;
    LinkedHashSet<Object> otherArgs = new LinkedHashSet< Object >();
    boolean found = false;
    for ( Object arg : arguments ) {
      if ( theArg == arg ) {
        found = true;
      } else if ( arg instanceof Expression
                  && ( ( (Expression)arg ).expression == theArg ) ) {
        found = true;
      } else {
        otherArgs.add( arg );
      }
    }
    if ( !found ) {
      // TODO -- ERROR
    }
    return otherArgs;
  }
  
  
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#handleValueChangeEvent(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public void handleValueChangeEvent( Parameter< ? > parameter, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;
    
    for ( Object o : getArguments() ) {
      if ( o instanceof ParameterListener ) {
        ((ParameterListener)o).handleValueChangeEvent( parameter, seen );
      }
    }
    if ( object instanceof ParameterListener && !isStatic() ) {
      ((ParameterListener)object).handleValueChangeEvent( parameter, seen );
    }
    if ( nestedCall instanceof ParameterListener ) {
      ((ParameterListener)nestedCall).handleValueChangeEvent( parameter, seen );
    }
    if ( evaluatedArguments != null ) {
      for ( Object o : evaluatedArguments ) {
        if ( o instanceof ParameterListener ) {
          ((ParameterListener)o).handleValueChangeEvent( parameter, seen );
        }
      }
    }
    if ( returnValue instanceof ParameterListener ) {
      ((ParameterListener)returnValue).handleValueChangeEvent( parameter, seen );
    }

    if ( !proactiveEvaluation ) return;

    if ( stale ) {
      try {
        evaluate( true );
      } catch ( IllegalAccessException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( InvocationTargetException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( InstantiationException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#handleDomainChangeEvent(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public void handleDomainChangeEvent( Parameter< ? > parameter, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;
    
    for ( Object o : getArguments() ) {
      if ( o instanceof ParameterListener ) {
        ((ParameterListener)o).handleDomainChangeEvent( parameter, seen );
      }
    }
    // TODO -- do for object, nestedCall, returnValue, and evaluatedArguments.
    if ( !proactiveEvaluation ) return;
    // TODO -- How do we proactively handle this?  evaluate() wouldn't change anything, right? 
  }
  
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#setStaleAnyReferencesTo(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public void setStaleAnyReferencesTo( Parameter< ? > changedParameter, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;
    if ( changedParameter == null ) return;

    // System.out.println("setStaleAnyReferencesTo(" + changedParameter.name + ") in " + toShortString() );

    // TODO -- REVIEW -- seems like we ought to return
    if ( stale == false ) {
      if ( hasParameter( changedParameter, false, null ) ) {
        setStale( true );
      }
      if ( changedParameter.getValueNoPropagate() instanceof TimeVarying ) {
        if ( containsValue( changedParameter.getValueNoPropagate(), false, null ) ) {
          this.setStale( true );
        }
      }
    }

    for ( Object o : getArguments() ) {
      if ( o instanceof ParameterListener ) {
        ParameterListener pl = (ParameterListener)o;
        pl.setStaleAnyReferencesTo( changedParameter, seen );
//        if ( !stale && pl.isStale() ) {
//          setStale(true);
//        }
      }
    }
    if ( object instanceof ParameterListener && !isStatic() ) {
      ParameterListener pl = (ParameterListener)object;
      pl.setStaleAnyReferencesTo( changedParameter, seen );
//      if ( !stale && pl.isStale() ) {
//        setStale(true);
//      }
    }
    if ( nestedCall instanceof ParameterListener ) {
      ParameterListener pl = (ParameterListener)nestedCall;
      pl.setStaleAnyReferencesTo( changedParameter, seen );
//      if ( !stale && pl.isStale() ) {
//        setStale(true);
//      }
    }
    if ( returnValue instanceof ParameterListener ) {
      ParameterListener pl = (ParameterListener)returnValue;
      pl.setStaleAnyReferencesTo( changedParameter, seen );
//      if ( !stale && pl.isStale() ) {
//        setStale(true);
//      }
    }
    if ( getEvaluatedArguments() != null ) {
      for ( Object o : getEvaluatedArguments() ) {
        ParameterListener pl = (ParameterListener)o;
        pl.setStaleAnyReferencesTo( changedParameter, seen );
//        if ( !stale && pl.isStale() ) {
//          setStale(true);
//        }
      }
    }
    if ( !proactiveEvaluation ) return;
    // TODO -- How do we proactively handle this?  evaluate() wouldn't change anything, right? 
  }

  public boolean containsValue( Object value, boolean deep, Set<Object> seen ) {
    if ( value == null ) return false;
    if ( this == value ) {
      return true;
    }

    Pair< Boolean, Set< Object > > p = Utils.seen( this, true, seen );
    if (p.first) return false;
    seen = p.second;

    Set<Parameter<?>> params = getParameters( deep, null );
    for ( Parameter<?> parm : params ) {
      if ( parm == value ) {
        return true;
      }
      Object v = parm.getValueNoPropagate();
      if ( Expression.contains( v, value, deep, seen ) ) {
        return true;
      }
    }
    for ( Object o : getArguments() ) {
      if ( Expression.contains( o, value, deep, seen ) ) {
        return true;
      }
    }
    if ( Expression.contains( object, value, deep, seen ) ) {
      return true;
    }
    if ( Expression.contains( nestedCall, value, deep, seen ) ) {
      return true;
    }
    if ( Expression.contains( returnValue, value, deep, seen ) ) {
      return true;
    }
    if ( getEvaluatedArguments() != null ) {
      for ( Object o : getEvaluatedArguments() ) {
        if ( Expression.contains( o, value, deep, seen ) ) {
          return true;
        }
      }
    }
    return false;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#detach(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public void detach( Parameter< ? > parameter ) {
    for ( Object o : getArguments() ) {
      if ( o == null ) continue;
      if ( o.equals( parameter ) )  {
        // TODO -- How to detach?  Replace with null in arguments?
        //((Parameter)o).
      }
      if ( o instanceof ParameterListener ) {
        ((ParameterListener)o).detach( parameter );
      }
    }
    if ( object instanceof ParameterListener && !isStatic() ) {
      ( (ParameterListener)object ).detach( parameter );
    }
    if ( nestedCall instanceof ParameterListener ) {
      ( (ParameterListener)nestedCall ).detach( parameter );
    }
    if ( returnValue instanceof ParameterListener ) {
      ( (ParameterListener)returnValue ).detach( parameter );
    }
    if ( getEvaluatedArguments() != null ) {
      for ( Object o : getEvaluatedArguments() ) {
        if ( o instanceof ParameterListener ) {
          ((ParameterListener)o).detach( parameter );
        }
      }
    }

    if ( !proactiveEvaluation ) return;
    // TODO -- How do we proactively handle this?  evaluate() wouldn't change anything, right? 
  }
  
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#refresh(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public boolean refresh( Parameter< ? > parameter ) {
    // TODO -- REVIEW -- Is thjs necessary? Doesn't the
    // setStaleAnyReferencesTo() function do this? Or, does it only do it on
    // value changes, and are there other cases where things become stale and
    // need to tell the world?
    if ( hasParameter( parameter, true, null ) ) {
      setStale(true);
      try {
        evaluate( true );
      } catch ( IllegalAccessException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( InvocationTargetException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( InstantiationException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return false;
  }
  
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#pickValue(gov.nasa.jpl.ae.solver.Variable)
   */
  @Override
  public < T > boolean pickParameterValue( Variable< T > variable ) {
    if ( variable == null ) return false;
    if ( this instanceof Suggester ) {
      T newValue = ((Suggester)this).pickValue( variable );
      if ( newValue != null ) {
        variable.setValue( newValue );
        return true;
      }
    } else {
      // TODO -- be smart anyway!!!!!!!!!!!! coerce getMember to be suggestive
    }
    return false;
  }
  
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#getName()
   */
  @Override
  public String getName() {
    if ( getMember() == null ) return null;
    return getMember().getName();
  }
  
  @Override
  public <T> T translate( Variable<T> v , Object o , Class< ? > type  ) {
    return null;
  }
  
  @Override
  public List< Variable< ? > >
         getVariablesOnWhichDepends( Variable< ? > variable ) {
    Debug.error( "This function is not implemented and should not be called." );
    return null;
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