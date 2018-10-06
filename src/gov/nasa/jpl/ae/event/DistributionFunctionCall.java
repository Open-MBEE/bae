package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.util.distributions.Distribution;
import gov.nasa.jpl.ae.util.distributions.FunctionOfDistributions;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.Wraps;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.Vector;

public class DistributionFunctionCall extends FunctionCall {

    public static Distribution evaluateAsDistribution( Object object, boolean recursive,
                                                       Set<Object> seen ) {
        Pair<Boolean, Set<Object>> p = Utils.seen( object, recursive, seen );
        if ( p.first ) return null;
        seen = p.second;

        Object result = null;
        if ( object instanceof Distribution ) {
            return (Distribution)object;
        }

        // See if it naturally evaluates to a Distribution
        try {
            result = Expression.evaluate( object, null, true );
            if ( result instanceof Distribution ) {
                return (Distribution)result;
            }
            if ( !( result instanceof Wraps ) ) return null;
            result = ( (Wraps<?>)result ).getValue( true );
            result = evaluateAsDistribution( result, recursive, seen );
            return (Distribution)result;
        } catch ( Throwable e1 ) {
        }

        // Got an exception -- otherwise it would have returned.
        // The exception may have been from trying to use a TVM where it's not legal.
        // So, look recursively for unexpected TVMs in inner Calls.
        FunctionCall f = null;
        try {
            f = Expression.evaluate( object, FunctionCall.class, true, false );
            if ( f != null ) {
                DistributionFunctionCall dfc = new DistributionFunctionCall( f );
                result = dfc.evaluate( true );
                if ( result instanceof Distribution ) {
                    return (Distribution)result;
                }
            }
        } catch ( Throwable e1 ) {
        }

        ConstructorCall c = null;
        try {
            c = Expression.evaluate( object, ConstructorCall.class, true, false );
            if ( c != null ) {
                DistributionConstructorCall dcc = new DistributionConstructorCall( c );
                result = dcc.evaluate( true );
                if ( result instanceof Distribution ) {
                    return (Distribution)result;
                }
            }
        } catch ( Throwable e1 ) {
        }

        // Try to evaluate it as a TimeVaryingMap? This would be an unnatural
        // evaluation since we checked the natural one earlier.
        try {
            Distribution d =
                    Expression.evaluate( object, Distribution.class, true, false );
            if ( d != null ) {
                return d;
            }
        } catch ( Throwable e1 ) {
        }

        return null;
    }

    @Override
    public Object invoke( Object evaluatedObject,
                          Object[] evaluatedArgs ) {//throws IllegalArgumentException,
        Object result = invoke( this, evaluatedObject, evaluatedArgs );
        evaluationSucceeded = true;
        return result;
    }
    public static Object invoke( Call call, Object evaluatedObject,
                                 Object[] evaluatedArgs ) {
        FunctionOfDistributions f = new FunctionOfDistributions();
        f.call = call;
        return f;
    }

    @Override
    public Object evaluateObject( boolean propagate ) throws ClassCastException,
                                                             IllegalAccessException,
                                                             InvocationTargetException,
                                                             InstantiationException {

        Throwable t = null;
        ClassCastException e1 = null;
        IllegalAccessException e2 = null;
        InvocationTargetException e3 = null;
        InstantiationException e4 = null;
        Object o = null;

        try {
            o = super.evaluateObject( propagate );
        } catch ( ClassCastException e ) {
            e1 = e;
            t = e;
        } catch ( IllegalAccessException e ) {
            e2 = e;
            t = e;
        } catch ( InvocationTargetException e ) {
            e3 = e;
            t = e;
        } catch ( InstantiationException e ) {
            e4 = e;
            t = e;
        }

        if ( o == null ) o = object;

        Distribution d = evaluateAsDistribution( object, true, null);
        if (d != null) o = d;

        if ( o == null && t != null ) {
            if ( e1 != null ) throw e1;
            if ( e2 != null ) throw e2;
            if ( e3 != null ) throw e3;
            if ( e4 != null ) throw e4;
        }
        return o;
    }

    /**
     * Construct a call to a method.
     *
     * @param method
     * @param returnType
     */
    public DistributionFunctionCall( Method method, Class<?> returnType ) {
        super( method, returnType );
    }

    /**
     * Construct a call to a method.
     *
     * @param cls
     * @param methodName
     * @param returnType
     */
    public DistributionFunctionCall( Class<?> cls, String methodName,
                                     Class<?> returnType ) {
        super( cls, methodName, returnType );
    }

    /**
     * @param object
     * @param method
     * @param returnType
     */
    public DistributionFunctionCall( Object object, Method method,
                                     Class<?> returnType ) {
        super( object, method, returnType );
    }

    public DistributionFunctionCall( Object object, Class<?> cls,
                                     String methodName, Class<?> returnType ) {
        super( object, cls, methodName, returnType );
    }

    /**
     * @param object
     * @param method
     * @param arguments
     * @param returnType
     */
    public DistributionFunctionCall( Object object, Method method,
                                     Vector<Object> arguments,
                                     Class<?> returnType ) {
        super( object, method, arguments, returnType );
    }

    /**
     * @param object
     * @param cls
     * @param methodName
     * @param arguments
     * @param returnType
     */
    public DistributionFunctionCall( Object object, Class<?> cls,
                                     String methodName,
                                     Vector<Object> arguments,
                                     Class<?> returnType ) {
        super( object, cls, methodName, arguments, returnType );
    }

    /**
     * @param object
     * @param method
     * @param arguments
     * @param nestedCall
     * @param returnType
     */
    public DistributionFunctionCall( Object object, Method method,
                                     Vector<Object> arguments, Call nestedCall,
                                     Class<?> returnType ) {
        super( object, method, arguments, nestedCall, returnType );
    }

    /**
     * @param object
     * @param method
     * @param arguments
     * @param nestedCall
     * @param returnType
     */
    public DistributionFunctionCall( Object object, Method method,
                                     Vector<Object> arguments,
                                     Parameter<Call> nestedCall,
                                     Class<?> returnType ) {
        super( object, method, arguments, nestedCall, returnType );
    }

    /**
     * @param object
     * @param cls
     * @param methodName
     * @param arguments
     * @param nestedCall
     * @param returnType TODO
     */
    public DistributionFunctionCall( Object object, Class<?> cls,
                                     String methodName,
                                     Vector<Object> arguments, Call nestedCall,
                                     Class<?> returnType ) {
        super( object, cls, methodName, arguments, nestedCall, returnType );
    }

    public DistributionFunctionCall( Object object, Class<?> cls,
                                     String methodName,
                                     Vector<Object> arguments,
                                     Parameter<Call> nestedCall,
                                     Class<?> returnType ) {
        super( object, cls, methodName, arguments, nestedCall, returnType );
    }

    /**
     * @param object
     * @param method
     * @param argumentsA
     * @param returnType
     */
    public DistributionFunctionCall( Object object, Method method,
                                     Object[] argumentsA,
                                     Class<?> returnType ) {
        super( object, method, argumentsA, returnType );
    }

    public DistributionFunctionCall( Object object, Class<?> cls,
                                     String methodName, Object[] argumentsA ) {
        super( object, cls, methodName, argumentsA );
    }

    /**
     * @param object
     * @param cls
     * @param methodName
     * @param argumentsA
     * @param returnType
     */
    public DistributionFunctionCall( Object object, Class<?> cls,
                                     String methodName, Object[] argumentsA,
                                     Class<?> returnType ) {
        super( object, cls, methodName, argumentsA, returnType );
    }

    public DistributionFunctionCall( Object object, Method method,
                                     Object[] argumentsA, Call nestedCall,
                                     Class<?> returnType ) {
        super( object, method, argumentsA, nestedCall, returnType );
    }

    public DistributionFunctionCall( Object object, Method method,
                                     Object[] argumentsA,
                                     Parameter<Call> nestedCall,
                                     Class<?> returnType ) {
        super( object, method, argumentsA, nestedCall, returnType );
    }

    public DistributionFunctionCall( Object object, Class<?> cls,
                                     String methodName, Object[] argumentsA,
                                     Call nestedCall, Class<?> returnType ) {
        super( object, cls, methodName, argumentsA, nestedCall, returnType );
    }

    public DistributionFunctionCall( Object object, Class<?> cls,
                                     String methodName, Object[] argumentsA,
                                     Parameter<Call> nestedCall,
                                     Class<?> returnType ) {
        super( object, cls, methodName, argumentsA, nestedCall, returnType );
    }

    /**
     * @param e
     */
    public DistributionFunctionCall( FunctionCall e ) {
        super( e );
    }
}
