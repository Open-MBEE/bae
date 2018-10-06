package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.util.distributions.Distribution;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Vector;

public class DistributionConstructorCall extends ConstructorCall {

    @Override
    public Object invoke( Object evaluatedObject,
                          Object[] evaluatedArgs ) {
        return DistributionFunctionCall.invoke( this, evaluatedObject, evaluatedArgs );
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
        Distribution d =
                DistributionFunctionCall.evaluateAsDistribution( o, true, null );
        if ( d != null ) o = d;

        if ( o == null && t != null ) {
            if ( e1 != null ) throw e1;
            if ( e2 != null ) throw e2;
            if ( e3 != null ) throw e3;
            if ( e4 != null ) throw e4;
        }
        return o;
    }

    @Override
    public Boolean hasTypeErrors( Object[] evaluatedArgs ) {
        return false;
        // TODO Auto-generated method stub
        //return super.hasTypeErrors( evaluatedArgs );
    }

    @Override
    public synchronized Boolean hasTypeErrors() {
        return false;
        // TODO Auto-generated method stub
        //return super.hasTypeErrors();
    }

    @Override
    public Object evaluateArg( Object unevaluatedArg, Class< ? > c,
                               boolean propagate ) throws ClassCastException,
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
            o = super.evaluateArg( unevaluatedArg, c, propagate );
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
        Distribution d =
                DistributionFunctionCall.evaluateAsDistribution( o, true, null );
        if ( d != null ) o = d;

        if ( o == null && t != null ) {
            if ( e1 != null ) throw e1;
            if ( e2 != null ) throw e2;
            if ( e3 != null ) throw e3;
            if ( e4 != null ) throw e4;
        }
        return o;
    }



    /**
     * Construct a call to a static constructor.
     *
     * @param constructor
     * @param returnType
     */
    public DistributionConstructorCall( Constructor<?> constructor,
                                        Class<?> returnType ) {
        super( constructor, returnType );
    }

    /**
     * Construct a call to a static constructor.
     *
     * @param cls
     * @param returnType
     */
    public DistributionConstructorCall( Class<?> cls, Class<?> returnType ) {
        super( cls, returnType );
    }

    /**
     * @param object
     * @param constructor
     * @param returnType
     */
    public DistributionConstructorCall( Object object,
                                        Constructor<?> constructor,
                                        Class<?> returnType ) {
        super( object, constructor, returnType );
    }

    public DistributionConstructorCall( Object object, Class<?> cls,
                                        Class<?> returnType ) {
        super( object, cls, returnType );
    }

    /**
     * @param object
     * @param constructor
     * @param arguments
     * @param returnType
     */
    public DistributionConstructorCall( Object object,
                                        Constructor<?> constructor,
                                        Vector<Object> arguments,
                                        Class<?> returnType ) {
        super( object, constructor, arguments, returnType );
    }

    /**
     * @param object
     * @param cls
     * @param arguments
     * @param returnType
     */
    public DistributionConstructorCall( Object object, Class<?> cls,
                                        Vector<Object> arguments,
                                        Class<?> returnType ) {
        super( object, cls, arguments, returnType );
    }

    /**
     * @param object
     * @param constructor
     * @param arguments
     * @param nestedCall
     * @param returnType
     */
    public DistributionConstructorCall( Object object,
                                        Constructor<?> constructor,
                                        Vector<Object> arguments,
                                        Call nestedCall, Class<?> returnType ) {
        super( object, constructor, arguments, nestedCall, returnType );
    }

    /**
     * @param object
     * @param constructor
     * @param arguments
     * @param nestedCall
     * @param returnType
     */
    public DistributionConstructorCall( Object object,
                                        Constructor<?> constructor,
                                        Vector<Object> arguments,
                                        Parameter<Call> nestedCall,
                                        Class<?> returnType ) {
        super( object, constructor, arguments, nestedCall, returnType );
    }

    /**
     * @param object
     * @param cls
     * @param arguments
     * @param nestedCall
     * @param returnType
     */
    public DistributionConstructorCall( Object object, Class<?> cls,
                                        Vector<Object> arguments,
                                        Call nestedCall, Class<?> returnType ) {
        super( object, cls, arguments, nestedCall, returnType );
    }

    public DistributionConstructorCall( Object object, Class<?> cls,
                                        Vector<Object> arguments,
                                        Parameter<Call> nestedCall,
                                        Class<?> returnType ) {
        super( object, cls, arguments, nestedCall, returnType );
    }

    /**
     * @param object
     * @param constructor
     * @param argumentsA
     * @param returnType
     */
    public DistributionConstructorCall( Object object,
                                        Constructor<?> constructor,
                                        Object[] argumentsA,
                                        Class<?> returnType ) {
        super( object, constructor, argumentsA, returnType );
    }

    /**
     * @param object
     * @param cls
     * @param argumentsA
     * @param returnType
     */
    public DistributionConstructorCall( Object object, Class<?> cls,
                                        Object[] argumentsA,
                                        Class<?> returnType ) {
        super( object, cls, argumentsA, returnType );
    }

    public DistributionConstructorCall( Object object,
                                        Constructor<?> constructor,
                                        Object[] argumentsA,
                                        ConstructorCall nestedCall,
                                        Class<?> returnType ) {
        super( object, constructor, argumentsA, nestedCall, returnType );
    }

    public DistributionConstructorCall( Object object,
                                        Constructor<?> constructor,
                                        Object[] argumentsA,
                                        Parameter<Call> nestedCall,
                                        Class<?> returnType ) {
        super( object, constructor, argumentsA, nestedCall, returnType );
    }

    public DistributionConstructorCall( Object object, Class<?> cls,
                                        Object[] argumentsA, Call nestedCall,
                                        Class<?> returnType ) {
        super( object, cls, argumentsA, nestedCall, returnType );
    }

    public DistributionConstructorCall( Object object, Class<?> cls,
                                        Object[] argumentsA,
                                        Parameter<Call> nestedCall,
                                        Class<?> returnType ) {
        super( object, cls, argumentsA, nestedCall, returnType );
    }

    /**
     * @param constructorCall
     */
    public DistributionConstructorCall( ConstructorCall constructorCall ) {
        super( constructorCall );
    }
}
