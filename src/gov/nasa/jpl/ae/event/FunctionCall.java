package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.util.Pair;
import gov.nasa.jpl.ae.util.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

/**
 * 
 */

/**
 * 
 */
public class FunctionCall extends Call {

  protected Method method = null;
  //protected Object object = null; // object from which method is invoked
  //protected Vector< Object > arguments = null; // arguments to method

  /**
   * A function call on the result of this function call.
   */
//  protected Parameter<FunctionCall> nestedCall = null;
  
  /**
   * Construct a call to a static method.
   * @param method
   */
  public FunctionCall( Method method ) {
    this.method = method; // the method must be static
  }

  /**
   * Construct a call to a static method.
   * @param cls
   * @param methodName
   */
  public FunctionCall( Class<?> cls, String methodName ) {
    this.method = Utils.getMethodForArgTypes( cls, methodName, (Class<?>[])null ); 
  }

  /**
   * @param object
   * @param method
   */
  public FunctionCall( Object object, Method method ) {
    this.object = object;
    this.method = method;
  }

  public FunctionCall( Object object, Class<?> cls, String methodName ) {
    this( cls, methodName );
    this.object = object;
  }

  /**
   * @param object
   * @param method
   * @param arguments
   */
  public FunctionCall( Object object, Method method, Vector< Object > arguments ) {
    this.object = object;
    this.method = method;
    this.arguments = arguments;
    hasTypeErrors();
  }

  /**
   * @param object
   * @param cls
   * @param methodName
   * @param arguments
   */
  public FunctionCall( Object object, Class<?> cls, String methodName,
                       Vector< Object > arguments ) {
    this.object = object;
    Object argArr[] = null;
    if ( !Utils.isNullOrEmpty( arguments ) ) {
      argArr = arguments.toArray();
    }
    this.method = Utils.getMethodForArgs( cls, methodName, argArr );
    this.arguments = arguments;
    hasTypeErrors();
  }

  /**
   * @param object
   * @param method
   * @param arguments
   * @param nestedCall
   */
  public FunctionCall( Object object, Method method, Vector< Object > arguments,
                       Call nestedCall ) {
    this(object, method, arguments);
    this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
  }

  /**
   * @param object
   * @param method
   * @param arguments
   * @param nestedCall
   */
  public FunctionCall( Object object, Method method, Vector< Object > arguments,
                       Parameter<Call> nestedCall ) {
    this(object, method, arguments);
    
    this.nestedCall = nestedCall;
  }

  /**
   * @param object
   * @param cls
   * @param methodName
   * @param arguments
   * @param nestedCall
   */
  public FunctionCall( Object object, Class<?> cls, String methodName,
                       Vector< Object > arguments,
                       Call nestedCall ) {
    this(object, cls, methodName, arguments);
    this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
  }

  public FunctionCall( Object object, Class<?> cls, String methodName,
                       Vector< Object > arguments,
                       Parameter<Call> nestedCall ) {
    this(object, cls, methodName, arguments);
    this.nestedCall = nestedCall;
  }

  /**
   * @param object
   * @param method
   * @param arguments
   */
  public FunctionCall( Object object, Method method, Object argumentsA[] ) {
    this.object = object;
    this.method = method;
    this.arguments = new Vector<Object>();
    if ( argumentsA != null ) {
      for ( Object o : argumentsA ) {
        this.arguments.add( o );
      }
    }
    hasTypeErrors();
  }

  /**
   * @param object
   * @param cls
   * @param methodName
   * @param argumentsA
   */
  public FunctionCall( Object object, Class<?> cls, String methodName,
                       Object argumentsA[] ) {
    this.object = object;
    this.method = Utils.getMethodForArgs( cls, methodName, argumentsA );
    this.arguments = new Vector<Object>();
    if ( argumentsA != null ) {
      for ( Object o : argumentsA ) {
        this.arguments.add( o );
      }
    }
    hasTypeErrors();
  }

  public FunctionCall( Object object, Method method, Object argumentsA[],
                       Call nestedCall ) {
    this.object = object;
    this.method = method;
    this.arguments = new Vector<Object>();
    if ( argumentsA != null ) {
      for ( Object o : argumentsA ) {
        this.arguments.add( o );
      }
    }
    this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
    hasTypeErrors();
  }

  public FunctionCall( Object object, Method method, Object argumentsA[],
                       Parameter<Call> nestedCall ) {
    this.object = object;
    this.method = method;
    this.arguments = new Vector<Object>();
    if ( argumentsA != null ) {
      for ( Object o : argumentsA ) {
        this.arguments.add( o );
      }
    }
    this.nestedCall = nestedCall;
    hasTypeErrors();
  }

  public FunctionCall( Object object, Class<?> cls, String methodName,
                       Object argumentsA[], Call nestedCall ) {
    this( object, cls, methodName, argumentsA );
    this.nestedCall = new Parameter<Call>("", null, nestedCall, null );
  }
  public FunctionCall( Object object, Class<?> cls, String methodName,
                       Object argumentsA[], Parameter<Call> nestedCall ) {
    this( object, cls, methodName, argumentsA );
    this.nestedCall = nestedCall;
  }

  /**
   * @param e
   */
  public FunctionCall( FunctionCall e ) {
    this.object = e.object;
    this.method = e.method;
    this.arguments = e.arguments;
    this.nestedCall = e.nestedCall;
    hasTypeErrors();
  }

  @Override
  public Class<?>[] getParameterTypes() {
    return method.getParameterTypes();
  }
  
  @Override
  public Member getMember() {
    return method;
  }

  @Override
  public boolean isVarArgs() {
    return method.isVarArgs();
  }
  
  @Override
  public Object invoke( Object[] evaluatedArgs ) throws IllegalArgumentException, InstantiationException, IllegalAccessException, InvocationTargetException {
    Object result = method.invoke( object, evaluatedArgs ); 
    return result;
  }
  
  // TODO -- delete this when version is stable -- the same implementation is in Call
  // Try to match arguments to parameters by evaluating or creating expressions.
  protected Object[] evaluateArgs( boolean propagate ) {
    Class< ? >[] paramTypes = method.getParameterTypes();
    return evaluateArgs( propagate, paramTypes, arguments, method.isVarArgs() );
  }

  // Getters and setters 
  
  /**
   * @return the method
   */
  public Method getMethod() {
    return method;
  }

  /**
   * @param method the method to set
   */
  public void setMethod( Method method ) {
    this.method = method;
  }

}
