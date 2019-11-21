/**
 * 
 */
package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.event.Expression.Form;
import gov.nasa.jpl.ae.event.TimeVaryingMap.BoolOp;
import gov.nasa.jpl.ae.event.TimeVaryingMap.Inequality;
import gov.nasa.jpl.ae.solver.*;
import gov.nasa.jpl.ae.util.distributions.*;
import gov.nasa.jpl.mbee.util.*;
import gov.nasa.jpl.ae.util.DomainHelper;
import gov.nasa.jpl.mbee.util.Random;
import org.apache.commons.math3.distribution.*;

import java.lang.Math;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;

/**
 * 
 * TODO -- Try to define methods with <T> arguments instead of Expression<T>.
 * FunctionCall may now support both with evaluateArgs().
 * 
 */
public class Functions {

  // private static boolean complainAboutBadExpressions = true;
  private static Method newListMethod =
          ClassUtils.getMethodsForName( Utils.class, "newList" )[ 0 ];

  private static Expression forceExpression( Object o ) {
    if ( o instanceof Expression ) return (Expression< ? >)o;
    if ( o instanceof Parameter ) return new Expression( (Parameter< ? >)o );
    if ( o instanceof Call ) return new Expression( (Call)o );
    return new Expression( o );
  }

  public static Predicate predicate(Expression e) {
    System.out.println( "predicate("+e+")" );
    Call c = null;
    try {
      c = (Call)e.evaluate( Call.class, true );
    } catch (Throwable t) {
      Debug.error(true, false, "WARNING: Trying to create a Predicate with (" + e + ") instead of a function call!");
    }
    if ( c != null ) {
      return predicate( c );
    }
    // Evaluate ignoring o
    Debug.error(true, false, "WARNING: Predicate ignores argument!");
    return new Predicate() {
      @Override public boolean test( Object o ) {
        Boolean b = null;
        try {
          b = (Boolean)e.evaluate( Boolean.class, true );
        } catch (Throwable t) {}
        if ( b != null ) return b;
        Object x = e.evaluate( Boolean.class, true );
        return Utils.isTrue( x );
      }
    };
  }

  public static Predicate predicate(Call c) {
    return predicate( c, -1 );
  }

  /**
   * Create a java.util.function.Predicate for functional programming operations
   * from a Call (presumably a FunctionCall that returns a boolean).  The Object
   * passed to the predicate should be substituted for the Call's object if the
   * argument index is 0.  If the argument index is i > 0, then the passed object
   * should be substuted for the ith argument to the Call.  An argument index
   * less than zero signifies that the argument index is unknown, in which case
   * the class of the passed argument is used to guess the appropriate argument.
   * If the
   *
   * @param c the Call to evaluate as the predicate
   * @param argumentIndex the index of the Call where the test element should be
   *                      passed; 0 for the object of the Call or i for the ith
   *                      argument to the call.
   * @return
   */
  public static Predicate predicate(Call c, final int argumentIndex) {
    System.out.println( "predicate(" + c + ", " + argumentIndex + ")" );
    return new Predicate() {
      int argIndex = argumentIndex;
      private void subarg( Object o ) {
        if ( argIndex == 0 ) {
          c.setObject( o );
        } else if ( argIndex > 0 ) {
          Vector<Object> args = c.getArguments();
          if ( c.getArguments().size() >= argIndex ) {
            args.set( argIndex - 1, o );
          } else {
            // Append o to the end of the arguments at the proper index, adding
            // nulls for missing arguments. REVIEW -- what if it's var args
            int i = args.size();
            while ( i < argIndex ) {
              args.add( null );
            }
            args.add(o);
          }
          c.setArguments( args );
        } else {
          // argIndex < 0 --> figure it out
          if ( !c.isStatic() ) {
            if ( c.getObjectType() == null || c.getObjectType().isInstance( o ) ) {
              argIndex = 0;  // remember for the next time the predicate is evaluated
            }
          }
          if ( argIndex < 0 ) {
            int i = 0;
            if ( i < c.getParameterTypes().length ) {
              if ( c.getParameterTypes()[i] == null ||
                   c.getParameterTypes()[i].isInstance( o ) ) {
                if ( c.isVarArgs() && i == c.getParameterTypes().length - 1 &&
                     c.getArguments().size() >= c.getParameterTypes().length ) {
                  argIndex = c.getArguments().size() + 1;
                } else {
                  argIndex = i + 1;  // remember for the next time the predicate is evaluated
                }
              }
            }
          }
          if ( argIndex >= 0 ) {
            // Now that we've guessed the index, call again to do the substitution.
            subarg( o );
          } else {
            Debug.error( true, true,
                         "predicate(" + c + ") called with no place to substitute arguments for: " + o +
                         "; evaluating without argument substitution." );
          }
        }
      }
      @Override public boolean test( Object o ) {
        subarg( o );
        Boolean b = null;
        try {
          b = (Boolean)new Expression( c ).evaluate( Boolean.class, true );
        } catch (Throwable t) {}
        if ( b != null ) return b;
        Object res = null;
        try {
          res = c.evaluate( true );
        } catch ( Throwable t ) {}
        b = Utils.isTrue( res );
        if ( b == null ) return false;
        return b;
      }
    };
  }

  // Abstract n-ary functions
  public static class SuggestiveFunctionCall extends FunctionCall
                                             implements Suggester {
    public FunctionCall pickFunctionCall = null;
    public FunctionCall reversePickFunctionCall = null;
    public FunctionCall isGrounded = null;

    public SuggestiveFunctionCall( // Method isGroundedMethod,
                                   Object object, Method method,
                                   Object[] arguments ) {
      super( object, method, arguments, (Class< ? >)null );
      // if
    }

    public SuggestiveFunctionCall( SuggestiveFunctionCall suggestiveFunctionCall ) {
      super( suggestiveFunctionCall );
      pickFunctionCall = suggestiveFunctionCall.pickFunctionCall;
      reversePickFunctionCall = suggestiveFunctionCall.reversePickFunctionCall;
    }

    public SuggestiveFunctionCall( FunctionCall functionCall ) {
      super( functionCall );
    }

    public SuggestiveFunctionCall( FunctionCall functionCall,
                                   FunctionCall pickFunctionCall,
                                   FunctionCall reversePickFunctionCall ) {
      super( functionCall );
      this.pickFunctionCall = pickFunctionCall;
      this.reversePickFunctionCall = reversePickFunctionCall;
    }

    // TODO -- Need a value argument -- the target return value! Then, this can
    // be renamed "inverse()."
    @Override
    public < T > T pickValue( Variable< T > variable ) {
      return Functions.pickValueBF2( this, variable );
    }

    @Override
    public SuggestiveFunctionCall clone() {
      SuggestiveFunctionCall c = new SuggestiveFunctionCall( this );
      return c;
    }

    @Override
    public Domain< ? > calculateDomain( boolean propagate,
                                        Set< HasDomain > seen ) {
      if ( !isMonotonic() ) {
        // Must be overridden
        Debug.error( true, false,
                     "FunctionCall.calculateDomain() must be overridden by "
                                 + this.getClass().getName() );
        return null;
      }
      SuggestiveFunctionCall fc = this.clone();
      Domain< ? > d = DomainHelper.combineDomains( arguments, fc, true );
      return d;
    }

    /**
     * Invert the function with respect to a range/return value and a given
     * argument.
     * This is called an inverse image as opposed to an inverse function,
     * which only returns a single value in the domain of the argument and does
     * not exist for many functions, e.g. f(x) = x^2.
     * <p>
     * If g is the inverse of f, then <br>
     * g(f(x)) = x.<br>
     * g(f(x,y)) = {(u,v) | f(u,v) = f(x,y)}<br>
     * g(f(x,y),x) = {v | f(x,v) = f(x,y)}<br>
     * g(f(x,y),x,y) = true
     * <p>
     * So, if f(x,y) = x + y, then<br>
     * g(z) = {(u,v) | u + v = z}<br>
     * g(z,x) = {v | v = z - x}<br>
     * g(z,x,y} = true if x + y = z, else false
     * <p>
     * Given a FunctionCall f with arguments (a1, a2, .. an) where n is the
     * number of arguments, g = f.inverse(r, ai) is a FunctionCall where ai must
     * be an argument to f. g.evaluate() returns a Domain representing the set
     * of values that ai may be assigned such that f.evaluate() == r.
     *
     * @param returnValue
     * @param arg
     *          the single argument with respect to which the inverse is
     *          constructed (ex. the x in f(x))
     * @return a new FunctionCall that returns a set of possible values
     *         <p>
     *         <b>Subclasses should override this method if the function is not
     *         a bijection</b> (one-to-one and the domain and range are the
     *         same), in which case the inverse may not be a single value. For
     *         example, if f(x)=x^2, then the inverse is {sqrt(x), -sqrt(x)}.
     *
     * @todo We should constrain the FunctionCall returned to, itself, return a domain.
     * @todo We could add a generic parameter to Call and return `FunctionCall<Domain<T>>`.
     * @todo In addition, the `arg` parameter could be of type T.
     */
    public FunctionCall inverse( Object returnValue, Object arg ) { // Variable<?>
                                                                    // variable
                                                                    // ) {
      FunctionCall singleValueFcn = inverseSingleValue( returnValue, arg );
      if ( singleValueFcn == null ) return null;
//      return singleValueFcn;
      return new FunctionCall( null,
                               newListMethod,
                               new Object[] { singleValueFcn },
                               (Class< ? >)null );
    }

    /**
     * Invert the function with respect to a range/return value and a given
     * argument.
     * <p>
     * This could be implemented by calling {@link #inverse(Object, Object)} and
     * selecting a value from the set of possible values returned by the
     * inverse.
     * 
     * @param returnValue
     * @param arg
     *          the single argument with respect to which the inverse is
     *          constructed (i.e. the x in f(x))
     * @return a new FunctionCall that returns a possible value
     *         <p>
     *         <b>Subclasses of {@link SuggestiveFunctionCall} should override
     *         this method.</b>
     */
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      return null;
    }

  }

  public static class Binary< T, R > extends SuggestiveFunctionCall
                            implements Suggester {
    // public SuggestiveFunctionCall functionCall = null;
    // public SuggestiveFunctionCall pickFunctionCall = null;
    // public SuggestiveFunctionCall reversePickFunctionCall = null;

    public Binary( Variable< T > o1, Expression< T > o2,
                   String functionMethod ) {
      super( (Object)null, getFunctionMethod( functionMethod ),
             new Object[] { o1, o2 } );
      // functionCall = this;//(SuggestiveFunctionCall)this.expression;
    }

    public Binary( Expression< T > o1, Expression< T > o2,
                   String functionMethod ) {
      super( // new SuggestiveFunctionCall(
             (Object)null, getFunctionMethod( functionMethod ),
             new Object[] { o1, o2 }
      // )
      );
      // functionCall = this;//(SuggestiveFunctionCall)this.expression;
    }

    public Binary( Expression< T > o1, Expression< T > o2,
                   String functionMethod, String pickFunctionMethod1,
                   String pickFunctionMethod2 ) {
      this( o1, o2, functionMethod );
      // functionCall.
      pickFunctionCall =
          new FunctionCall( (Object)null,
                            getFunctionMethod( pickFunctionMethod1 ),
                            // functionCall.
                            getArgumentArray(), (Class< ? >)null );
      Vector< Object > args = new Vector< Object >( // functionCall.
                                                    getArgumentVector() );
      Collections.reverse( args );
      // functionCall.
      reversePickFunctionCall =
          new FunctionCall( (Object)null,
                            getFunctionMethod( pickFunctionMethod2 ),
                            args.toArray(), (Class< ? >)null );
    }

    public Binary( Object o1, Object o2, String functionMethod,
                   String pickFunctionMethod1, String pickFunctionMethod2 ) {
      this( forceExpression( o1 ), forceExpression( o2 ), functionMethod,
            pickFunctionMethod1, pickFunctionMethod2 );
      // this( ( o1 instanceof Expression ) ? )
    }

    public Binary( Object o1, Object o2, String functionMethod ) {
      this( forceExpression( o1 ), forceExpression( o2 ), functionMethod );
    }

    public Binary( Binary< T, R > b ) {
      super( b );
    }

    /*
     * (non-Javadoc)
     * 
     * @see gov.nasa.jpl.ae.event.Expression#isGrounded(boolean, java.util.Set)
     */
    @Override
    public boolean isGrounded( boolean deep, Set< Groundable > seen ) {
      if ( arguments == null || arguments.size() < 2 ) return false;
      if ( !areArgumentsGrounded( deep, seen ) ) return false;
      return true;
    }

    private static Method getFunctionMethod( String functionMethod ) {
      Method m = null;
      try {
        m = Functions.class.getMethod( functionMethod, Expression.class,
                                       Expression.class );
      } catch ( SecurityException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( NoSuchMethodException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return m;
    }

    @Override
    public < T1 > T1 pickValue( Variable< T1 > variable ) {
      return pickValueBF2( this, // functionCall,
                           variable );
    }

    public Vector< Expression > getArgumentExpressions() {
      Vector< Expression > argExprs =
          new Vector< Expression >( (Collection< Expression >)Utils.asList( Utils.newList( super.getArgumentArray() ),
                                                                            Expression.class ) );
      return argExprs;
    }

    public Domain< ? > inverseDomain( Domain< ? > returnValue,
                                      Object argument ) {
      if ( returnValue == null ) return null;
      // FunctionCall inverse = inverse( returnValue, argument );
      // if ( inverse == null ) return null;
      Domain< ? > theCombineDomain = null;
      LinkedHashSet< Object > possibleValues = new LinkedHashSet< Object >();
      if ( returnValue instanceof AbstractRangeDomain ) {
        if ( isMonotonic() ) {
          AbstractRangeDomain ard = (AbstractRangeDomain)returnValue;
          addInverseToList( ard.getLowerBound(), argument, possibleValues );
          if ( ard.getLowerBound() != ard.getUpperBound() ) {
            addInverseToList( ard.getUpperBound(), argument, possibleValues );
          }
        } else {
          // not monotonic, such as Equals
          if ( !returnValue.isInfinite() || returnValue.magnitude() == 1 ) {
            for ( long i = 0; i < returnValue.magnitude(); ++i ) {
              Object rv = ( (AbstractRangeDomain)returnValue ).getNthValue( i );
              if (rv != null) {
                addInverseToList( rv, argument, possibleValues );
              }
            }
          }
        }
      }
      // TODO -- Handle the million other cases
      theCombineDomain =
          DomainHelper.combineDomains( Utils.asList( possibleValues ), //need to pass in a list of domains, call getcomporabledomain, if null make an object domain out of the objects
                                       new Identity< T >( (Expression< T >)null ),
                                       true );
      return theCombineDomain;
    }

    protected void
              addInverseToList( Object returnValue, Object argument,
                                Collection< Object > listOfInverseResults ) {
      FunctionCall inverse = inverse( returnValue, argument );
      if ( inverse == null ) {
        return;
      }
      Object cObj = null;
      if ( inverse instanceof HasDomain ) {
        Domain<?> d = inverse.getDomain(false, null);
        if ( d == null && newListMethod.equals( inverse.getMethod() ) ) {
          d = DomainHelper.combineDomains(inverse.getArguments(), null, false);
        }
        boolean flatten = (newListMethod.equals( inverse.getMethod() ) && d != null && !d.isEmpty());
        List<Object> values = DomainHelper.getRepresentativeValues(d, null, flatten);
        if ( values != null && values.size() > 0 ) {
          listOfInverseResults.addAll(values);
          return;
        }
      }
      try {
        cObj = inverse.evaluate( false, true );
      } catch ( IllegalAccessException e ) {
        e.printStackTrace();
      } catch ( InvocationTargetException e ) {
        e.printStackTrace();
      } catch ( InstantiationException e ) {
        e.printStackTrace();
      }
      if ( cObj instanceof Collection ) {
        Collection< ? > c = (Collection< ? >)cObj;
        listOfInverseResults.addAll( c );
        // } else if ( cObj instanceof Domain ) {
        //
      } else {
        listOfInverseResults.add( cObj );
      }
    }

    @Override
    public < TT > Pair< Domain< TT >, Boolean >
           restrictDomain( Domain< TT > domain, boolean propagate,
                           Set< HasDomain > seen ) {
      Object o1 = this.arguments.get( 0 );
      Object o2 = this.arguments.get( 1 );
      boolean changed = false;
      if ( o1 instanceof HasDomain || o2 instanceof HasDomain ) {
        HasDomain hd1 = o1 instanceof HasDomain ? (HasDomain)o1 : null;
        HasDomain hd2 = o2 instanceof HasDomain ? (HasDomain)o2 : null;
        Pair<Domain<?>, Boolean> p = null;
        if ( hd1 != null ) {
          Domain d1 = inverseDomain( domain, o1 );
          p = hd1.restrictDomain( d1, propagate, seen );
          if ( p != null && Boolean.TRUE.equals( p.second ) ) changed = true;
        }
        if ( p != null && p.first != null && p.first.isEmpty() ) {
          if ( this.domain != null ) {
            this.domain.clearValues();
          }
          // return new Pair( this.domain, changed );
        } else if ( hd2 != null ) {
          Domain d2 = inverseDomain( domain, o2 );
          p = hd2.restrictDomain( d2, propagate, seen );
          if ( p != null && Boolean.TRUE.equals(p.second) ) {
            changed = true;
          }
        }
      }
      this.domain = (Domain< T >)getDomain( propagate, null );
      return new Pair( this.domain, changed );
    }
  }

  public static abstract class BooleanBinary< T > extends Binary< T, Boolean >
                                            implements Suggester {

    public BooleanBinary( Expression< T > o1, Expression< T > o2,
                          String functionMethod ) {
      super( o1, o2, functionMethod );
    }

    public BooleanBinary( Expression< T > o1, Expression< T > o2,
                          String functionMethod, String pickFunctionMethod1,
                          String pickFunctionMethod2 ) {
      super( o1, o2, functionMethod, pickFunctionMethod1, pickFunctionMethod2 );
    }

    public BooleanBinary( Object o1, Object o2, String functionMethod ) {
      super( o1, o2, functionMethod );
    }

    public BooleanBinary( Object o1, Object o2, String functionMethod,
                          String pickFunctionMethod1,
                          String pickFunctionMethod2 ) {
      super( o1, o2, functionMethod, pickFunctionMethod1, pickFunctionMethod2 );
    }

    public BooleanBinary( BooleanBinary< T > bb ) {
      super( bb );
    }

    // /* (non-Javadoc)
    // * @see
    // gov.nasa.jpl.ae.event.Call#restrictDomain(gov.nasa.jpl.ae.solver.Domain,
    // boolean, java.util.Set)
    // */
    // @Override
    // public < TT > Pair<Domain< TT >,Boolean> restrictDomain( Domain< TT >
    // domain,
    // boolean propagate,
    // Set< HasDomain > seen ) {
    // boolean changed = false;
    // if ( domain.contains((TT)Boolean.TRUE) &
    // domain.contains((TT)Boolean.FALSE) ) {
    // } else if ( domain.magnitude() == 1 ) {
    // Object v = domain.getValue( propagate );
    // if ( v instanceof Boolean ) {
    // changed = restrictDomains(Boolean.TRUE.equals((Boolean)v);
    // }
    // }
    //// Domain oldDomain = this.domain.clone();
    //// Domain newDomain = (Domain< TT >)getDomain(propagate, null);
    //// boolean thisChanged = Utils.valuesEqual( oldDomain, newDomain );
    //// this.domain = newDomain;
    // return new Pair(this.domain, changed);// || thisChanged);
    // }
    //
    // // REVIEW -- This seems out of place. Does something else do this?
    // public abstract boolean restrictDomains( boolean targetResult );

  }

  public static class Unary< T, R > extends SuggestiveFunctionCall
                           implements Suggester {
    public Unary( Variable< T > o, String functionMethod ) {
      super( (Object)null, getFunctionMethod( functionMethod ),
             new Object[] { o } );
    }

    public Unary( Expression< T > o1, String functionMethod ) {
      super( // new SuggestiveFunctionCall(
             (Object)null, getFunctionMethod( functionMethod ),
             new Object[] { o1 }
      // )
      );
      // functionCall = this;//(SuggestiveFunctionCall)this.expression;
    }

    public Unary( Expression< T > o1, String functionMethod,
                  String pickFunctionMethod1
    // String pickFunctionMethod2
    ) {
      this( o1, functionMethod );
      // functionCall.
      pickFunctionCall =
          new FunctionCall( (Object)null,
                            getFunctionMethod( pickFunctionMethod1 ),
                            // functionCall.
                            getArgumentArray(), (Class< ? >)null );
      Vector< Object > args = new Vector< Object >( // functionCall.
                                                    getArgumentVector() );
      Collections.reverse( args );
      // functionCall.
      reversePickFunctionCall = pickFunctionCall;
      // new FunctionCall( (Object)null,
      // getFunctionMethod( pickFunctionMethod2 ),
      // args.toArray() );
    }

    public Unary( Object o1, String functionMethod,
                  String pickFunctionMethod1 ) { // , String pickFunctionMethod2
                                                 // ) {
      this( forceExpression( o1 ), functionMethod, pickFunctionMethod1 );// ,
                                                                         // pickFunctionMethod2
                                                                         // );
      // this( ( o1 instanceof Expression ) ? )
    }

    public Unary( Object o1, String functionMethod ) {
      this( forceExpression( o1 ), functionMethod );
    }

    public Unary( Unary m ) {
      super( m );
    }

    public Unary clone() {
      return new Unary( this );
    }

    private static Method getFunctionMethod( String functionMethod ) {
      Method m = null;
      try {
        m = Functions.class.getMethod( functionMethod, Expression.class );
      } catch ( SecurityException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( NoSuchMethodException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return m;
    }

    @Override
    public < T1 > T1 pickValue( Variable< T1 > variable ) {
      return pickValueBF2( this, // functionCall,
                           variable );
    }

    public Vector< Expression > getArgumentExpressions() {
      Vector< Expression > argExprs =
          new Vector< Expression >( (Collection< Expression >)Utils.asList( super.getArgumentArray(),
                                                                            Expression.class ) );
      return argExprs;
    }

  }

  public static class Conditional< T > extends SuggestiveFunctionCall
                                 implements Suggester {
    public Conditional( Expression< Boolean > condition,
                        Expression< T > thenExpr, Expression< T > elseExpr ) {
      super( null, getIfThenElseMethod(),
             new Object[] { condition, thenExpr, elseExpr } );
    }

    public Conditional( Conditional< T > c ) {
      super( c );
    }

    public Conditional< T > clone() {
      return new Conditional( this );
    }

    @Override
    public boolean isMonotonic() {
      // TODO Auto-generated method stub
      return super.isMonotonic();
    }

    public static Method getIfThenElseMethod() {
      Method m = null;
      try {
        m = Functions.class.getMethod( "ifThenElse", Expression.class,
                                       Expression.class, Expression.class );
      } catch ( SecurityException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch ( NoSuchMethodException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return m;
    }

    @Override
    public Domain< ? > calculateDomain( boolean propagate,
                                        Set< HasDomain > seen ) {
      if ( arguments == null || arguments.size() != 3 ) {
        // TODO -- ERROR
        return null;
      }
      Object o1 = this.arguments.get( 0 );
      Object o2 = this.arguments.get( 1 );
      Object o3 = this.arguments.get( 2 );

      Domain< ? > d1 = o1 == null ? null : DomainHelper.getDomain( o1 );
      Domain< ? > d2 = null;
      Domain< ? > d3 = null;
      if (d1 == null || d1.magnitude() == 2) {
          d2 = o2 == null ? new SingleValueDomain<>( null ) :
               DomainHelper.getDomain( o2 );
          d3 = o3 == null ? new SingleValueDomain<>( null ) :
               DomainHelper.getDomain( o3 );
      } else if (d1 != null && d1.magnitude() == 1) {
        if (Utils.isTrue( d1.getValue( true ) )) {
          d2 = o2 == null ? new SingleValueDomain<>( null ) :
               DomainHelper.getDomain( o2 );
        } else {
          d3 = o3 == null ? new SingleValueDomain<>( null ) :
               DomainHelper.getDomain( o3 );
        }
      }

//      AbstractRangeDomain< T > ard2 =
//          d2 instanceof AbstractRangeDomain ? (AbstractRangeDomain< T >)d2
//                                            : null;
//      AbstractRangeDomain< T > ard3 =
//          d3 instanceof AbstractRangeDomain ? (AbstractRangeDomain< T >)d3
//                                            : null;
      Domain< Boolean > condo =
          d1 instanceof Domain ? (Domain< Boolean >)d1 : null;

      // Return combination of domains if the condition is not restricted to
      // exactly one of {true, false}.
      if ( condo == null || condo.isEmpty()
           || ( condo.contains( true ) && condo.contains( false ) ) ) {
        if ( d2 == null ) {
          if ( o2 == null && d3 != null ) {
            // REVIEW -- Is this really how we want to interpret this?
            // REVIEW -- If the argument is uninitialized, shouldn't it be the
            // REVIEW -- ClassDomain of the parameter, T?
            d3.setNullInDomain( true );
          }
          return d3;
        }
        if ( d3 == null ) {
          if ( o3 == null && d2 != null ) {
            d2.setNullInDomain( true );
          }
          return d2;
        }

        // Combine regex and string domains
        if ( d2 instanceof RegexDomainString || d3 instanceof RegexDomainString ||
             RegexDomain.isDomainRegex( d2 ) || RegexDomain.isDomainRegex( d3 ) ||
             ( d2 instanceof StringDomain && d3 instanceof StringDomain ) ) {
          Domain dd2 = d2;
          Domain dd3 = d3;
          if ( d2 instanceof StringDomain ) {
            StringDomain sd2 = (StringDomain)d2;
            dd2 = new RegexDomainString( sd2 );
          }
          if ( dd2 instanceof RegexDomainString ) {
            dd2 = ( (RegexDomainString)dd2 ).charListDomain;
          }
          if ( d3 instanceof StringDomain ) {
            StringDomain sd3 = (StringDomain)d3;
            dd3 = new RegexDomain.SimpleDomain( sd3.getValue( false ) );
          }
          if ( dd3 instanceof RegexDomainString ) {
            dd3 = ( (RegexDomainString)dd3 ).charListDomain;
          }
          Domain od = new RegexDomain.OrDomain(Utils.newList(dd2, dd3));
          return od;
        }

        MultiDomain< T > md = new MultiDomain< T >( (Class< T >)getType(),
                                                    (Set< Domain< T > >)Utils.newSet( (Domain< T >)d2,
                                                                                      (Domain< T >)d3 ),
                                                    null );
        Set< Domain< T > > s = md.computeFlattenedSet();
        if ( s != null && s.size() == 1 ) {
          return s.iterator().next();
        }
        return md;
      }

      if ( condo.contains( true ) ) {
        return d2;
      }
      return d3;
    }

    @Override
    public < TT > Pair< Domain< TT >, Boolean >
           restrictDomain( Domain< TT > domain, boolean propagate,
                           Set< HasDomain > seen ) {
      if ( domain == null ) return new Pair( getDomain( propagate, null ),
                                             false );

      if ( arguments == null || arguments.size() != 3 ) {
        // TODO -- ERROR
        return null;
      }
      Object o1 = this.arguments.get( 0 );
      Object o2 = this.arguments.get( 1 );
      Object o3 = this.arguments.get( 2 );

      Domain d1 = o1 == null ? null : DomainHelper.getDomain( o1 );
      Domain d2 = o2 == null ? null : DomainHelper.getDomain( o2 );
      Domain d3 = o3 == null ? null : DomainHelper.getDomain( o3 );
      AbstractRangeDomain ard2 =
          d2 instanceof AbstractRangeDomain ? (AbstractRangeDomain)d2 : null;
      AbstractRangeDomain ard3 =
          d3 instanceof AbstractRangeDomain ? (AbstractRangeDomain)d3 : null;
      BooleanDomain condo =
          d1 instanceof BooleanDomain ? (BooleanDomain)d1 : null;

      // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      // TODO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      // TODO -- DON'T CREATE A NEW DOMAIN IF this.domain IS CORRECT!!!!!!!
      // TODO!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
      // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

      // if ( condo == null || condo.isEmpty() ) {
      // // TODO -- ERROR
      // if ( d2 != null ) {
      // Domain d2c = d2.clone();
      // if ( d2c instanceof AbstractRangeDomain )
      // ((AbstractRangeDomain)d2c).makeEmpty();
      // else d2c.setValue( null );
      // boolean changed = Utils.valuesEqual( this.domain, d2c );
      // return new Pair(d2c, changed);
      // }
      // return null;
      // }
      Domain< ? > oldDomain = getDomain( propagate, null );
      this.domain = oldDomain;

      boolean changed = false;
      if ( condo == null || condo.isEmpty()
           || ( condo.contains( true ) && condo.contains( false ) ) ) {
        // Can't tell whether to restrict the domain for true case (ard2) or the
        // false (ard3).
        // If only one of the choices overlaps with the input domain, then the
        // condition
        // is restricted to that one!
        if ( ard2 != null && ard3 != null
             && domain instanceof AbstractRangeDomain ) {
          AbstractRangeDomain< T > ard = (AbstractRangeDomain< T >)domain;
          boolean overlaps2 = ard2.overlaps( ard );
          boolean overlaps3 = ard3.overlaps( ard );
          if ( overlaps2 && !overlaps3 ) {
            condo = BooleanDomain.trueDomain;
            if ( o1 instanceof HasDomain ) {
              Pair< Domain< Boolean >, Boolean > p =
                  ( (HasDomain)o1 ).restrictDomain( condo, propagate, seen );
              changed = changed || ( p != null && p.second );
            }
          } else if ( !overlaps2 && overlaps3 ) {
            condo = BooleanDomain.falseDomain;
            if ( o1 instanceof HasDomain ) {
              Pair< Domain< Boolean >, Boolean > p =
                  ( (HasDomain)o1 ).restrictDomain( condo, propagate, seen );
              changed = changed || ( p != null && p.second );
            }
          } else if ( !overlaps2 && !overlaps3 ) {
            changed = oldDomain.clearValues();
            this.domain = oldDomain;
            return new Pair( this.domain, changed );
          }

        }
      }
      if ( condo == null || condo.isEmpty()
           || ( condo.contains( true ) && condo.contains( false ) ) ) {
        return new Pair( this.domain, changed );
      } else if ( condo.size() == 1 ) {
        if ( condo.contains( Boolean.TRUE ) ) {
          if ( ard2 != null && !ard2.isEmpty() ) {
            if ( o2 instanceof HasDomain ) {
              Pair< Domain< TT >, Boolean > p =
                  ( (HasDomain)o2 ).restrictDomain( domain, propagate, seen );
              if ( p != null ) {
                changed = changed || ( p.second != null && p.second );
                this.domain = ( (HasDomain)o2 ).getDomain( propagate, null );
              }
            } else {
              boolean cc = ard2.restrictTo(domain);
              changed = changed || cc;
            }
          }
        } else if ( condo.contains( Boolean.FALSE ) ) {
          if ( ard3 != null && !ard3.isEmpty() ) {
            if ( o3 instanceof HasDomain ) {
              Pair< Domain< TT >, Boolean > p =
                  ( (HasDomain)o3 ).restrictDomain( domain, propagate, seen );
              if ( p != null ) {
                changed = changed || ( p.second != null && p.second );
                this.domain = ( (HasDomain)o3 ).getDomain( propagate, null );
              }
            } else {
              boolean cc = ard3.restrictTo( domain );
              changed = changed || cc;
            }
          }
        } else {
          // This case is not possible since we check for condo.isEmpty() above.
          return null;
        }
        return new Pair( this.domain, changed );
      }

      // condo must contain both true and false at this point.

      // check to see if neither or only one of d2 and d3 intersect with domain.
      Domain dca = domain.clone();
      Domain dcb = domain.clone();
      boolean changedA = dca.restrictTo( d2 );
      boolean changedB = dcb.restrictTo( d3 );
      if ( dca.isEmpty() && dcb.isEmpty() ) {
        // No values in domain can be produced--no solution! return an empty
        // domain.
        this.domain = dca;
        return new Pair( this.domain, changedA );
      }
      if ( dca.isEmpty() ) {
        // We can restrict the condition to be true.
        condo.restrictTo( new BooleanDomain( true, true ) );
        this.domain = dca;
        changed = changedA;
      } else if ( dcb.isEmpty() ) {
        // We can restrict the condition to be false.
        condo.restrictTo( new BooleanDomain( false, false ) );
        this.domain = dcb;
        changed = changedB;
      } else {
        Domain newDomain = DomainHelper.combineDomains( Utils.newList( o2, o3 ),
                                                        new Identity< T >( (Expression< T >)null ),
                                                        true );
        changed = Utils.valuesEqual( this.domain, newDomain );
      }

      return new Pair( this.domain, changed );
    }
  }

  public static class IF< T > extends Conditional< T > {
    public IF( Expression< Boolean > condition, Expression< T > thenExpr,
               Expression< T > elseExpr ) {
      super( condition, thenExpr, elseExpr );
    }
  }

  public static < T > T ifThenElse( boolean b, T thenT, T elseT ) {
    if ( b ) return thenT;
    return elseT;
  }

  public static < T > Object ifThenElse( Object condition, T thenT, T elseT ) {
    if ( condition == null ) {
      //System.out.println("ifThenElse(" + condition + ", " + thenT + ", " + elseT + ") returning elseT = " + elseT);
      //return elseT;
      return null;
    }

    Pair< Object, TimeVaryingMap< ? > > p = booleanOrTimelineOrDistribution( condition );
    if ( p == null ) {
      //System.out.println("ifThenElse(" + condition + ", " + thenT + ", " + elseT + ") returning null");
      return null;
    }

    // Handle simple case
    if ( p != null && p.first != null && !(p.first instanceof Distribution) &&
         p.second == null ) {
      Boolean b = getBoolean( p.first );
      if ( b != null ) {
        if ( b ) {
          //System.out.println("ifThenElse(" + condition + ", " + thenT + ", " + elseT + ") returning thenT = " + thenT);
          return thenT;
        }
        //System.out.println("ifThenElse(" + condition + ", " + thenT + ", " + elseT + ") returning elseT = " + elseT);
        return elseT;
      }
    }

    Pair< Object, TimeVaryingMap< ? > > pThen = booleanOrTimelineOrDistribution( thenT );
    Pair< Object, TimeVaryingMap< ? > > pElse = booleanOrTimelineOrDistribution( elseT );

    Object argCond = p != null && p.first != null ? p.first : condition;
    Object argThen = pThen != null && pThen.first != null ? pThen.first : thenT;
    Object argElse = pElse != null && pElse.first != null ? pElse.first : elseT;

    // Handle Distribution
    if ( argCond instanceof Distribution ||
         argThen instanceof Distribution ||
         argElse instanceof Distribution ) {
      Distribution d = DistributionHelper.ifThenElse( argCond, argThen, argElse );
      //System.out.println("ifThenElse(" + condition + ", " + thenT + ", " + elseT + ") returning DistributionHelper.ifThenElse( argCond=" + argCond + ", argThen=" + argThen + ", argElse=" + argElse + ") = " + d);
      return d;
    }

    // Handle timeline
    TimeVaryingMap< ? > tvm = p.second;
    if ( tvm == null ) return elseT;
    Object t = tvm.ifThenElse( thenT, elseT );
    //System.out.println("ifThenElse(" + condition + ", " + thenT + ", " + elseT + ") returning t = " + t);
    return t;
  }

  public static  Boolean getBoolean( Object o ) {
    if ( o == null ) return null;
    if ( o instanceof Boolean ) return (Boolean) o;
    if ( o instanceof Collection ) {
      Collection c = (Collection)o;
      if ( c.isEmpty() ) return null;
      boolean allTrue = true;
      boolean allFalse = true;
      for ( Object oo : (Collection)o ) {
        Boolean b = getBoolean( oo );
        if ( b == null ) return null;
        if ( b.booleanValue() ) {
          allFalse = false;
          if ( !allTrue ) return null;
        } else {
          allTrue = false;
          if ( !allFalse ) return null;
        }
      }
      if ( allTrue ) return true;
      if ( allFalse ) return false;
      return null;
    }
    if ( o instanceof Map ) {
      return getBoolean(((Map)o).values());
    }
    if ( o.getClass().isArray() ) {
      Object[] oa = (Object[])o;
      List<Object> list = Utils.arrayAsList( oa );
      return getBoolean( list );
    }
    Object b = null;
    try {
      b = Expression.evaluate( o, Boolean.class, true );
    } catch ( Throwable t ) {
      // ignore
    }
    if ( b instanceof Boolean ) {
      return (Boolean)b;
    }
    Distribution d = DistributionHelper.getDistribution( o );
    if ( d != null ) {
      double pTrue = d.probability( true );
      boolean mustBeTrue = Utils.valuesEqual( pTrue, 1.0 );
      boolean mustBeFalse = Utils.valuesEqual( pTrue, 0.0 );
      if ( mustBeTrue ) {
        return true;
      }
      if ( mustBeFalse ) {
        return false;
      }
      return null;
    }
    return null;
  }

  /**
   * <p>
   *     Evaluate if-then-else conditional function. If the condition evaluates
   *     to null, it is interpreted as "false."
   * </p>
   * <p>
   *     We want to avoid evaluating both the thenExpr and elseExpr if we can for
   *     performance reasons.
   *     If a recursive function call is made in the thenExpr, the conditionExpr
   *     may determine the termination of the recursion by the elseExpr.  Thus.
   *     limited evaluation is important for avoiding infinite recursion.
   * </p>
   * 
   * @param conditionExpr
   * @param thenExpr
   * @param elseExpr
   * @return the evaluation of thenExpr if conditionExpr evaluates to true, else
   *         the evaluation of elseExpr
   * @throws InstantiationException
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   */
  public static < T > Object
         ifThenElse( Expression< ? > conditionExpr, Expression< T > thenExpr,
                     Expression< T > elseExpr ) throws IllegalAccessException,
                                                InvocationTargetException,
                                                InstantiationException {

    if ( conditionExpr == null ) return null;

    // Try not to evaluate both then and else expressions.
    Boolean b = getBoolean( conditionExpr.expression );

    Pair< Object, TimeVaryingMap< ? > > p =
        booleanOrTimelineOrDistribution( conditionExpr.expression );

//    boolean mustBeTrue = false;
//    boolean mustBeFalse = false;
//
//    Distribution d1 = p == null || p.second != null
//                      ? null
//                      : DistributionHelper.getDistribution( p.first );
//    if ( d1 != null ) {
//      double pTrue = d1.probability( true );
//      mustBeTrue = Utils.valuesEqual( pTrue, 1.0 );
//      mustBeFalse = Utils.valuesEqual( pTrue, 0.0 );
//    } else if ( p != null && p.second != null ) {
//      // condition is a timeline
//      Collection<?> vals = p.second.values();
//      if ( !Utils.isNullOrEmpty( vals ) ) {
//        boolean allTrue = true;
//        boolean allFalse = true;
//        for ( Object v : vals ) {
//          Object o = Expression.evaluate( v, Boolean.class, true );
//          if ( !( o instanceof Boolean ) ) {
//            allTrue = false;
//            allFalse = false;
//            break;
//          }
//          if ( )
//        }
//      }
//    }
//
//    Object o = Expression.evaluate( conditionExpr, Boolean.class, true );
//    if ( o == null
//         || ( !( o instanceof Boolean ) && o.getClass() != boolean.class ) ) {
//      Debug.error( true, false,
//                   "Could not evaluate condition of if-then-else as true/false; got "
//                   + o );
//      return null;
//    }
//
//
    boolean evalThen = thenExpr != null && (b == null || b);
    boolean evalElse = elseExpr != null && (b == null || !b);

    // Handle timeline
    if ( p != null && p.second != null ) {
      Object thenObj = evalThen ? thenExpr.evaluate( true ) : null;
      Object elseObj = evalElse ? elseExpr.evaluate( true ) : null;
      T result =
          (T)( new TimeVaryingPlottableMap() ).ifThenElse( p.second, thenObj,
                                                           elseObj );
      return result;
    }

    // Handle distributions
    boolean isCondDist = p == null
                         ? false
                         : DistributionHelper.isDistributionType( p.first );
    boolean isThenDist = thenExpr == null
                         ? false
                         : DistributionHelper.isDistributionType( thenExpr.expression );
    boolean isElseDist = elseExpr == null
                         ? false
                         : DistributionHelper.isDistributionType( elseExpr.expression );
    if ( isCondDist || isThenDist || isElseDist ) {
      Object thenObj = evalThen ? thenExpr.evaluate( true ) : null;
      Object elseObj = evalElse ? elseExpr.evaluate( true ) : null;
      return ifThenElse( p == null ? null : p.first, thenObj, elseObj );
    }

    //Distribution d2 = DistributionHelper.getDistribution( thenExpr );
    //Distribution d3 = DistributionHelper.getDistribution( elseExpr );

//    if ( //p != null && DistributionHelper.isDistribution( p.first ) ||
//         d1 != null || d2 != null || d3 != null ) {
//      Object thenObj = thenExpr.evaluate( true );
//      Object elseObject = elseExpr == null ? null : elseExpr.evaluate( true );
//      return ifThenElse( d1 == null && p != null ? p.first : d1, thenObj, elseObject );
//    }

    if ( b == null ) {
//      Debug.error( true, false,
//                   "Could not evaluate condition of if-then-else as true/false" );
      return null;
    }

    if ( !b ) {
      T elseT = null;
      try {
        elseT = (T)( elseExpr == null ? null : elseExpr.evaluate( false ) );
      } catch ( ClassCastException e ) {
        e.printStackTrace();
      }
      return elseT;
    }
    T thenT = null;
    try {
      thenT = (T)( thenExpr == null ? null : thenExpr.evaluate( false ) );
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    return thenT;
  }

  protected static Class getClassFromArgument( Object o )
          throws IllegalAccessException, InstantiationException,
                 InvocationTargetException {
    Object r = ( o == null ? null : Expression.evaluateDeep( o, null, false, false ) );
    Class<?> cls = Expression.evaluate( o, Class.class, true, false );
    if ( cls == null ) {
      if ( r instanceof String ) {
        cls = ClassUtils.getClassForName( ( (String)r ).replaceAll( ".class$", "" ),
                                          null, new String[]{}, true );
      }
    }
    return cls;
  }

  public static Object isInstanceOf( Expression< ? > o1,
                                     Expression< ? > o2 ) throws IllegalAccessException,
                                                                 InvocationTargetException,
                                                                 InstantiationException {
    if ( o1 == null && o2 == null ) return null;
    Object r1 = ( o1 == null ? null : Expression.evaluateDeep( o1, null, false, false ) );
    Class cls = getClassFromArgument( o2 );
    if ( cls == null ) {
      return null;
    }
    Object result = isInstanceOf( r1, cls );
    if ( Utils.isTrue( result ) ) {
      return result;
    }
    Object result2 = isInstanceOf( r1, cls );
    if ( Utils.isTrue( result2 ) ) {
      return result2;
    }
    return result;
  }

  public static Object
      isInstanceOf( Object o, Class<?> cls) throws IllegalAccessException,
                                                   InvocationTargetException,
                                                   InstantiationException {
    // TODO -- handle timelines and distributions
    if ( cls == null ) return null;
    if ( o == null ) return false;
    return cls.isInstance( o );
  }

  public static Object cast( Expression< ? > o1,
                             Expression< ? > o2 ) throws IllegalAccessException,
                                                         InvocationTargetException,
                                                         InstantiationException {
    if ( o1 == null && o2 == null ) return null;
    Object r1 = ( o1 == null ? null : Expression.evaluateDeep( o1, null, false, false ) );
    Object r2 = ( o2 == null ? null : Expression.evaluateDeep( o2, null, false, false ) );
    Class<?> cls = null;
    Object oo = ClassUtils.getClassForName( "" + o2, null, new String[]{}, true);
    if ( oo instanceof Class ) {
      cls = (Class)oo;
    }
    if ( cls == null ) {
      if ( r2 instanceof String ) {
        cls = ClassUtils.getClassForName( ( (String)r2 ).replaceAll( ".class$", "" ),
                                          null, new String[]{}, true );
      } else if ( r2 instanceof Class ) {
        cls = (Class)r2;
      }
      if ( cls == null ) {
        return null;
      }
    }
    Object result = cast( r1, cls );
    if ( result != null ) {
      return result;
    }
    if ( o1 != r1 ) {
      result = isInstanceOf( o1, cls );
    }
    return result;
  }

  public static Object
      cast( Object o, Class<?> cls) throws IllegalAccessException,
                                           InvocationTargetException,
                                           InstantiationException {
    // TODO -- handle timelines and distributions
    if ( cls == null || o == null ) return null;

    if ( o == null ) return false;
    Object result = Expression.evaluate( o, cls, false, false );
    return result;
  }

  public static class ArgMin< R, T > extends SuggestiveFunctionCall
                            implements Suggester {
    public ArgMin( Expression< R > argLabel1, Expression< T > arg1,
                   Expression< R > argLabel2, Expression< T > arg2 ) {
      super( null, getArgMinMethod(),
             new Object[] { argLabel1, arg1, argLabel2, arg2 } );
    }

    public ArgMin( Expression< ? >... keysAndValues ) {
      super( null, getVarArgMinMethod(), keysAndValues );
    }

    public ArgMin( ArgMin< R, T > a ) {
      super( a );
    }

    public ArgMin< R, T > clone() {
      return new ArgMin< R, T >( this );
    }

    @Override
    public boolean isMonotonic() {
      // TODO Auto-generated method stub
      return false;// super.isMonotonic();
    }

    protected static Method _argMinMethod = null;

    public static Method getArgMinMethod() {
      if ( _argMinMethod != null ) return _argMinMethod;
      try {
        _argMinMethod =
            Functions.class.getMethod( "argmin", Object.class, Object.class,
                                       Object.class, Object.class );
      } catch ( SecurityException e ) {
        e.printStackTrace();
      } catch ( NoSuchMethodException e ) {
        e.printStackTrace();
      }
      return _argMinMethod;
    }

    protected static Method _varArgMinMethod = null;

    public static Method getVarArgMinMethod() {
      if ( _varArgMinMethod != null ) return _varArgMinMethod;
      try {
        _varArgMinMethod =
            Functions.class.getMethod( "argmin", Object[].class );
      } catch ( SecurityException e ) {
        e.printStackTrace();
      } catch ( NoSuchMethodException e ) {
        e.printStackTrace();
      }
      return _varArgMinMethod;
    }
  }

  public static class ArgMax< R, T > extends SuggestiveFunctionCall
                            implements Suggester {
    public ArgMax( Expression< R > argLabel1, Expression< T > arg1,
                   Expression< R > argLabel2, Expression< T > arg2 ) {
      super( null, getArgMaxMethod(),
             new Object[] { argLabel1, arg1, argLabel2, arg2 } );
    }

    public ArgMax( Expression< ? >... keysAndValues ) {
      super( null, getVarArgMaxMethod(), keysAndValues );
    }

    public ArgMax( ArgMax< R, T > a ) {
      super( a );
    }

    public ArgMax< R, T > clone() {
      return new ArgMax< R, T >( this );
    }

    @Override
    public boolean isMonotonic() {
      // TODO Auto-generated method stub
      return false;// super.isMonotonic();
    }

    protected static Method _argMaxMethod = null;

    public static Method getArgMaxMethod() {
      if ( _argMaxMethod != null ) return _argMaxMethod;
      try {
        _argMaxMethod =
            Functions.class.getMethod( "argmax", Object.class, Object.class,
                                       Object.class, Object.class );
      } catch ( SecurityException e ) {
        e.printStackTrace();
      } catch ( NoSuchMethodException e ) {
        e.printStackTrace();
      }
      return _argMaxMethod;
    }

    protected static Method _varArgMaxMethod = null;

    public static Method getVarArgMaxMethod() {
      if ( _varArgMaxMethod != null ) return _varArgMaxMethod;
      try {
        _varArgMaxMethod =
            Functions.class.getMethod( "argmax", Object[].class );
      } catch ( SecurityException e ) {
        e.printStackTrace();
      } catch ( NoSuchMethodException e ) {
        e.printStackTrace();
      }
      return _varArgMaxMethod;
    }
  }

  // Simple math functions

  public static class Min< T, R > extends Binary< T, R > {
    public Min( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "min", "pickValueForward", "pickValueReverse" );
      setMonotonic( true );
    }

    public Min( Object o1, Object c ) {
      super( o1, c, "min", "pickValueForward", "pickValueReverse" );
      setMonotonic( true );
    }

    public Min( Functions.Min< T, R > m ) {
      super( m );
    }

    public Min< T, R > clone() {
      return new Min< T, R >( this );
    }

    @Override
    public // < T1 extends Comparable< ? super T1 > >
    FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      return new Max< T, T >( returnValue, otherArg );
    }

    @Override
    public Domain< ? > getDomain( boolean propagate, Set< HasDomain > seen ) {
      // avoid infinite recursion
      Pair< Boolean, Set< HasDomain > > pair =
          Utils.seen( this, propagate, seen );
      if ( pair.first ) return null;
      seen = pair.second;

      Domain< ? > rd =
          DomainHelper.combineDomains( new ArrayList< Object >( getArgumentExpressions() ),
                                       new Min< T, R >( null, null ), true );
      return rd;
    }

    /**
     * Return a domain for the matching input argument restricted by the domain
     * of the other arguments and of an expected return value.
     * 
     * @param returnValue
     * @param argument
     * @return
     */
    public Domain< ? > inverseDomain( Domain< ? > returnValue,
                                      Object argument ) {
      FunctionCall inverse = inverse( returnValue, argument );
      if ( inverse == null ) return null;
      return inverse.getDomain( false, null );
    }
  }

  public static class Max< T, R > extends Binary< T, R > {
    public Max( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "max", "pickValueForward", "pickValueReverse" );
      setMonotonic( true );
    }

    public Max( Object o1, Object c ) {
      super( o1, c, "max", "pickValueForward", "pickValueReverse" );
      setMonotonic( true );
    }

    public Max( Functions.Max< T, R > m ) {
      super( m );
    }

    public Max< T, R > clone() {
      return new Max< T, R >( this );
    }

    @Override
    public // < T1 extends Comparable< ? super T1 > >
    FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      return new Min< T, T >( returnValue, otherArg );
    }

    @Override
    public Domain< ? > getDomain( boolean propagate, Set< HasDomain > seen ) {
      // avoid infinite recursion
      Pair< Boolean, Set< HasDomain > > pair =
          Utils.seen( this, propagate, seen );
      if ( pair.first ) return null;
      seen = pair.second;

      Domain< ? > rd =
          DomainHelper.combineDomains( new ArrayList< Object >( getArgumentExpressions() ),
                                       new Max< T, R >( null, null ), true );
      return rd;
    }

    /**
     * Return a domain for the matching input argument restricted by the domain
     * of the other arguments and of an expected return value.
     * 
     * @param returnValue
     * @param argument
     * @return
     */
    public Domain< ? > inverseDomain( Domain< ? > returnValue,
                                      Object argument ) {
      FunctionCall inverse = inverse( returnValue, argument );
      if ( inverse == null ) return null;
      return inverse.getDomain( false, null );
    }
  }

  public static class Sum< T, R > extends Binary< T, R > {
    public Sum( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "add", "pickValueForward", "pickValueReverse" );
      setMonotonic( true );
    }

    public Sum( Object o1, Object c ) {
      super( o1, c, "add", "pickValueForward", "pickValueReverse" );
      setMonotonic( true );
    }

    public Sum( Functions.Sum< T, R > m ) {
      super( m );
    }

    public Sum< T, R > clone() {
      return new Sum< T, R >( this );
    }

    protected boolean argumentsAreString(Object returnValue) {
      if ( arguments == null || arguments.size() != 2 ) return false;

      Object deepReturnValue = returnValue;
      Object arg = arguments.get( 0 );
      Object otherArg = arguments.get( 1 );
      Object deepArg = arg;
      Object deepOtherArg = otherArg;

      try {
        deepReturnValue =
                Expression.evaluateDeep( returnValue, null, false, false );
      } catch ( Throwable e ) {
        // fail quietly, revert to using the unevaluated form
      }
      try {
        deepArg = Expression.evaluateDeep( arg, null, false, false );
      } catch ( Throwable e ) {
        // fail quietly, revert to using the unevaluated form
      }
      try {
        deepOtherArg = Expression.evaluateDeep( otherArg, null, false, false );
      } catch ( Throwable e ) {
        // fail quietly, revert to using the unevaluated form
      }

      if ( deepReturnValue instanceof String || deepArg instanceof String
           || deepOtherArg instanceof String ) {
        return true;
      }
      return false;
    }

    @Override
    public Domain<?> calculateDomain( boolean propagate, Set<HasDomain> seen ) {
      if ( !argumentsAreString( this.returnValue ) ) {
        return super.calculateDomain( propagate, seen );
      }
      return calculateDomainForStrings( propagate, seen );
    }

    public Domain<?> calculateDomainForStrings( boolean propagate, Set<HasDomain> seen ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object arg = arguments.get( 0 );
      Object otherArg = arguments.get( 1 );
      Domain<?> d1 = DomainHelper.getDomain( arg );
      Domain<?> d2 = DomainHelper.getDomain( otherArg );
      RegexDomainString rds = new RegexDomainString(Utils.newList(d1, d2));
      return rds;
    }

    @Override
    public // < T1 extends Comparable< ? super T1 > >
    FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      boolean isFirstArg = arg == arguments.get( 0 );
      Object otherArg = ( isFirstArg ? arguments.get( 1 ) : arguments.get( 0 ) );
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      boolean areString = argumentsAreString( returnValue );
      if ( areString ) {
        if (isFirstArg) {
          return new MinusSuffix( returnValue, otherArg );
        } else {
          return new MinusPrefix( returnValue, otherArg );
        }
      }
      
      return new Minus< T, T >( returnValue, otherArg );
    }

    @Override
    public Domain<?> inverseDomain( Domain<?> returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      boolean isFirstArg = arg == arguments.get( 0 );
      Object otherArg = ( isFirstArg ? arguments.get( 1 ) : arguments.get( 0 ) );
      if ( returnValue == null || otherArg == null ) return null; // arg can be null!

      boolean areString = argumentsAreString( returnValue );
      if ( !areString ) {
        return super.inverseDomain( returnValue, arg );
      }

      Call call = null;
      if (isFirstArg) {
        call = new MinusSuffix( returnValue, otherArg );
      } else {
        call = new MinusPrefix( returnValue, otherArg );
      }

      return call.getDomain( true, null );
    }

  }

  public static class Add< T, R > extends Sum< T, R > {
    public Add( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
    }

    public Add( Object o1, Object c ) {
      super( o1, c );
    }
  }

  public static class Plus< T, R > extends Sum< T, R > {
    public Plus( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
      // functionCall.
    }

    public Plus( Object o1, Object c ) {
      super( o1, c );
    }
  }

  public static class Sub< T, R > // < T extends Comparable< ? super T >,
                         // R >
                         extends Binary< T, R > {
    public Sub( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "subtract", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public Sub( Object o1, Object c ) {
      super( o1, c, "subtract", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public Sub( Functions.Sub< T, R > m ) {
      super( m );
    }

    public Sub< T, R > clone() {
      return new Sub< T, R >( this );
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
                                                         // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      if ( firstArg ) {
        return new Sum< T, T >( returnValue, otherArg );
      }
      return new Sub< T, T >( otherArg, returnValue );
    }
  }

  public static class Minus< T, R > extends Sub< T, R > {
    public Minus( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
      // functionCall.
      setMonotonic( true );
    }

    public Minus( Object o1, Object c ) {
      super( o1, c );
      // functionCall.
      setMonotonic( true );
    }
  }
  
  public static class MinusSuffix extends Binary< String, String > {
    public MinusSuffix( Expression<String> o1, Expression<String> o2 ) {
      super( o1, o2, "subtractSuffix", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public MinusSuffix( Object o1, Object c ) {
      super( o1, c, "subtractSuffix", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public MinusSuffix( Functions.MinusSuffix m ) {
      super( m );
    }

    public MinusSuffix clone() {
      return new MinusSuffix( this );
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 ) :
                          arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
      // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
      // null!
      if ( firstArg ) {
        return new Sum<String, String>( returnValue, otherArg );
      }
      return new MinusPrefix( otherArg, returnValue );
    }

    /**
     * Return the possible results of minusSuffix(x,y) for any x in the domain of
     * the first argument and any y in the domain of the second.  If there is only
     * one value in each domain, return minusSuffix(x,y).  For Suffix of x, px,
     * <p>
     * If the second argument's domain is multivalued, then we look for the domain min
     * value ("" for StringDomain), the domain max value ("ÿÿÿÿÿÿÿÿ"), and px,
     * a Suffix of the first argument.  If it is a range domain ["" px], then the
     * outputs of minusSuffix(x,"") is x and  is ""; thus return
     * ["" minusSuffix(x,px)]. If the second argument is the range domain [px "ÿÿÿÿÿÿÿÿ"] then
     * return [minusSuffix(x,px) x].  if [px1, px2] return [minusSuffix(x,px2) minuSuffix(x,px1)].
     * Otherwise, return ["" x], representing all possible removed Suffixes.
     * <p>
     * domain(minusSuffix([x x], ["" px])) = [minusSuffix(x,px), x]
     * domain(minusSuffix([x x], [px1 px2])) = [minusSuffix(x,px2) minusSuffix(x,px1)]
     * domain(minusSuffix([x x], [px y])) = [minusSuffix(x,px) x]
     * <p>
     * If the first argument's domain is [x1 x2], then we can repeat the logic
     * above for each of x1 and x2. and have two domains.  One option is to create
     * a multidomain.  Otherwise, how do we combine them?  Let's see . . .
     * <p>
     * The first domain may range within ["", minusSuffix(x1, px1_1),
     * minusSuffix(x1,px1_2), x1 a].  If x1 is a substring of x2,
     * <p>
     * domain(minusSuffix([x1 x2], y)) = [domain(minusSuffix(x1, y)).
     * <p>
     * <p>
     * <p>
     * If the first argument's domain is multivaleud and not the second, then
     *
     * @param propagate
     * @param seen
     * @return
     */
    @Override public Domain calculateDomain( boolean propagate, Set<HasDomain> seen ) {
      return Functions.calculateStringDomain(this, propagate, seen);
    }

  }

  public static class MinusPrefix extends Binary< String, String > {
    public MinusPrefix( Expression< String > o1, Expression< String > o2 ) {
      super( o1, o2, "subtractPrefix", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public MinusPrefix( Object o1, Object c ) {
      super( o1, c, "subtractPrefix", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public MinusPrefix( Functions.MinusPrefix m ) {
      super( m );
    }

    public MinusPrefix clone() {
      return new MinusPrefix( this );
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
      // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
      // null!
      if ( firstArg ) {
        return new Sum< String, String >( returnValue, otherArg );
      }
      return new MinusSuffix( otherArg, returnValue );
    }

    /**
     * Return the possible results of minusPrefix(x,y) for any x in the domain of
     * the first argument and any y in the domain of the second.  If there is only
     * one value in each domain, return minusPrefix(x,y).  For prefix of x, px,
     * <p>
     * If the second argument's domain is multivalued, then we look for the domain min
     * value ("" for StringDomain), the domain max value ("ÿÿÿÿÿÿÿÿ"), and px,
     * a prefix of the first argument.  If it is a range domain ["" px], then the
     * outputs of minusPrefix(x,"") is x and  is ""; thus return
     * ["" minusPrefix(x,px)]. If the second argument is the range domain [px "ÿÿÿÿÿÿÿÿ"] then
     * return [minusPrefix(x,px) x].  if [px1, px2] return [minusPrefix(x,px2) minuPrefix(x,px1)].
     * Otherwise, return ["" x], representing all possible removed prefixes.
     * <p>
     * domain(minusPrefix([x x], ["" px])) = [minusPrefix(x,px), x]
     * domain(minusPrefix([x x], [px1 px2])) = [minusPrefix(x,px2) minusPrefix(x,px1)]
     * domain(minusPrefix([x x], [px y])) = [minusPrefix(x,px) x]
     * <p>
     * If the first argument's domain is [x1 x2], then we can repeat the logic
     * above for each of x1 and x2. and have two domains.  One option is to create
     * a multidomain.  Otherwise, how do we combine them?  Let's see . . .
     * <p>
     * The first domain may range within ["", minusPrefix(x1, px1_1),
     * minusPrefix(x1,px1_2), x1 a].  If x1 is a substring of x2,
     * <p>
     * domain(minusPrefix([x1 x2], y)) = [domain(minusPrefix(x1, y)).
     * <p>
     * <p>
     * <p>
     * If the first argument's domain is multivaleud and not the second, then
     *
     * @param propagate
     * @param seen
     * @return
     */
    @Override public Domain calculateDomain( boolean propagate, Set<HasDomain> seen ) {
      return calculateStringDomain(this, propagate, seen);
    }

  }


  public static Domain calculateStringDomain( Binary<String, String> minusPrefixOrSuffix,
                                              boolean propagate, Set<HasDomain> seen ) {
    if ( minusPrefixOrSuffix.getArguments().size() != 2 ) {
      return RegexDomainString.defaultDomain;
    }
    Object a1 = minusPrefixOrSuffix.getArgument( 0 );
    Object a2 = minusPrefixOrSuffix.getArgument( 1 );
    Domain<?> d1 = DomainHelper.getDomain( a1 );
    Domain<?> d2 = DomainHelper.getDomain( a2 );
    if ( d1 == null || d2 == null
//         || d1.magnitude() <= 0
//         || d2.magnitude() <= 0
//         || gov.nasa.jpl.ae.util.Math
//                 .isInfinity( d1.magnitude() ) || gov.nasa.jpl.ae.util.Math
//                 .isInfinity( d2.magnitude() )
            ) {
      return RegexDomainString.defaultDomain;
    }


    // Now do it the right way.
    RegexDomainString rd1 = null;
    RegexDomainString rd2 = null;
    RegexDomain rdc1 = null;
    RegexDomain rdc2 = null;

    if ( d1 instanceof StringDomain ) {
      rd1 = new RegexDomainString( (StringDomain)d1 );
    } else if ( d1 instanceof SingleValueDomain ) {
      rd1 = new RegexDomainString( "" + d1.getValue( false ) );
    } else if ( d1 instanceof RegexDomainString ) {
      rd1 = (RegexDomainString)d1;
    } else if ( d1 instanceof RegexDomain ) {
      rdc1 = (RegexDomain)d1;
    }
    if ( rdc1 == null && rd1 != null ) {
      rdc1 = rd1.charListDomain;
    }

    if ( d2 instanceof StringDomain ) {
      rd2 = new RegexDomainString( (StringDomain)d2 );
    } else if ( d2 instanceof SingleValueDomain ) {
      rd2 = new RegexDomainString( "" + d2.getValue( false ) );
    } else if ( d2 instanceof RegexDomainString ) {
      rd2 = (RegexDomainString)d2;
    } else if ( d2 instanceof RegexDomain ) {
      rdc2 = (RegexDomain)d2;
    }
    if ( rdc2 == null && rd2 != null ) {
      rdc2 = rd2.charListDomain;
    }

    if ( rdc1 == null ) {
      Debug.error( true, true,
                   "Could not get RegexDomain for " + a1 );
    }
    if ( rdc2 == null ) {
      Debug.error( true, true,
                   "Could not get RegexDomain for " + a2 );
    }
    if ( rdc1 == null || rdc2 == null ) {
      return null;
    }



    boolean isPrefix = minusPrefixOrSuffix instanceof MinusPrefix;
    RegexDomain.OrDomain<Character> d3;
    if ( isPrefix ) {
      d3 = RegexDomain.minusPrefix( rdc1, rdc2, null );
    } else{
      d3 = RegexDomain.minusSuffix( rdc1, rdc2 );
    }

    System.out.println("calculateStringDomain(" + minusPrefixOrSuffix + ") = " + d3);
    return d3;
  }


  // Warning! this is matched by regex with no treatment for characters with special meaning, like *.
  protected static String wild = StringDomain.typeMaxValue;

  /**
   *
   * Subtract the prefix/suffix based on the rules below for upper or lower bound, y.
   *
   * domain(minusPrefix([x x], ["" px])) = [minusPrefix(x,px), x]
   * domain(minusPrefix([x x], [px1 px2])) = [minusPrefix(x,px2) minusPrefix(x,px1)]
   * domain(minusPrefix([x x], [px y])) = [minusPrefix(x,px) x]
   *
   * Allowing wildcards makes this painful.  We should just not allow them; else,
   * go full regex.
   *
   * REVIEW -- should this be replaced with regex operations?  The weird part is
   * that there are wildcards in both the pattern and the string to match.
   * It's like unification beyond predicates.  One problem is that there are
   * multiple solutions.  For example,
   * x = "* a b * c"
   * y = "* b c"
   * this is wrong: "* a "->"* ", "b * c"-> "b c" because there are two spaces between b and c.
   * one correct one: "* a b * "-> "*", "* "->" b ", "c"->"c"
   *
   */
  protected static StringDomain minusPrefixSuffixWild( String x, String y,
                                                       boolean isPrefix,
                                                       boolean isLowerBound) {
    if ( y == null || y.isEmpty() ) {
      return new StringDomain(x, x);
    }

    String lb = x;  // The string with the most subtracted.
    String ub = x;  // The string with the least subtracted.

    // NOTE: The Utils.longestCommon* and longestPrefix* functions may be useful here.

    // check for wildcard symbol at the front of the x string.
    boolean xHasWildToSubtract = false;
    if (isPrefix) {
      while ( lb.startsWith( wild ) && wild.length() > 0 ) {
        // This means anything will match as a prefix.
        lb = lb.substring( wild.length() );
        xHasWildToSubtract = true;
      }
    } else {
      while ( lb.endsWith( wild ) && wild.length() > 0) {
        // This means anything will match as a suffix.
        lb = lb.substring( 0, lb.length() - wild.length() );
        xHasWildToSubtract = true;
      }
    }

    // If xLbWildPos >= 0 then there is a wildcard after a prefix/suffix to subtract.
    int xFirstWild = x.indexOf( wild );
    int xLastWild = x.lastIndexOf( wild );
    //boolean xHasWildAfter = false;

    String ylb = y;
    String yub = y;

    // check for wildcard symbol at the front of the y string.
    boolean yHasWildToSubtract = false;
    if (isPrefix) {
      while ( ylb.startsWith( wild ) && wild.length() > 0 ) {
        // This means anything will match as a prefix.
        ylb = ylb.substring( wild.length() );
        yHasWildToSubtract = true;
      }
    } else {
      while ( ylb.endsWith( wild ) && wild.length() > 0) {
        // This means anything will match as a suffix.
        ylb = ylb.substring( 0, ylb.length() - wild.length() );
        yHasWildToSubtract = true;
      }
    }
    // If yLbWildPos >= 0 then there is a wildcard after a prefix/suffix to subtract.
    int yFirstWild = y.indexOf( wild );
    int yLastWild = y.lastIndexOf( wild );
    //int yLbWildPos = isPrefix ? y.indexOf( wild ) : y.lastIndexOf( wild );
    //boolean yHasWildAfter = false;


    if (xHasWildToSubtract && yHasWildToSubtract) {
      StringDomain d =  new StringDomain( "", x );
      d.kind = isPrefix ? StringDomain.Kind.SUFFIX_RANGE : StringDomain.Kind.PREFIX_RANGE;
      return d;
    }

    // remove wild on the end since it won't actually match anything
    String ylb2 = ylb.endsWith( wild ) ? ylb.substring( 0, ylb.length()-wild.length() ) : ylb;
    // well, remove all wilds then -- maybe we won't use them anyway
    String ylb3 = ylb2.replace( wild, "" );
    if ( yHasWildToSubtract ) {
      // y' wildcard can match anything, but any remaining characters must match something.
      if ( ylb3.isEmpty() ) {
        // nothing needed to match beyond the wild card, so all can be subtracted
        lb = "";
      } else {
        int pos = isPrefix ? x.lastIndexOf( ylb3 ) : x.indexOf( ylb3 );
        if ( pos >= 0 ) {
          if ( isPrefix ) {
            int farthest = pos + ylb3.length();
            if ( xLastWild >= 0 ) farthest = Math.max(xLastWild + wild.length(), farthest );
            lb = x.substring( farthest );
          } else {
            int farthest = pos;
            if ( xFirstWild >= 0 ) farthest = Math.min(xFirstWild, farthest );
            lb = x.substring( 0, farthest );
          }
        }
      }
    } else {
      int len = Utils.longestCommonPrefixLength( isPrefix ? lb : Functions.reverse( lb ),
                                                 isPrefix ? ylb3 : Functions.reverse( ylb3 ) );
      lb = lb.substring( len );
    }
    if (isPrefix) {
      while ( lb.startsWith( wild ) && wild.length() > 0 ) {
        // This means anything will match as a prefix.
        lb = lb.substring( wild.length() );
        xHasWildToSubtract = true;
      }
    } else {
      while ( lb.endsWith( wild ) && wild.length() > 0) {
        // This means anything will match as a suffix.
        lb = lb.substring( 0, lb.length() - wild.length() );
        xHasWildToSubtract = true;
      }
    }

    if (!xHasWildToSubtract && !yHasWildToSubtract ) {
      if ( isPrefix ) {
        if ( ub.startsWith( yub ) ){
          ub = ub.substring( yub.length() );
        } else if ( ub.startsWith( ylb ) ) {
          ub = ub.substring( ylb.length() );
          if ( yub.startsWith( ylb ) ) {
            String newyub = yub.substring( ylb.length() );

            int lenn = Utils.longestCommonPrefixLength( ub, newyub );
            ub = ub.substring( lenn );
          }
        }
      } else {
        if ( ub.endsWith( yub ) ){
          ub = ub.substring( 0, ub.length() - yub.length() );
        } else if ( ub.endsWith( ylb ) ) {
          ub = ub.substring( ylb.length() );
          if ( yub.endsWith( ylb ) ) {
            String newyub = yub.substring( 0, yub.length() - ylb.length() );
            int lenn = Utils.longestCommonPrefixLength( reverse(ub), reverse(newyub) );
            ub = ub.substring( 0, ub.length() - lenn );
          }
        }
      }
    }
    StringDomain d = new StringDomain( lb, ub );
    d.kind = isPrefix ? StringDomain.Kind.SUFFIX_RANGE : StringDomain.Kind.PREFIX_RANGE;
    return d;
  }

  public static String reverse( String s ) {
    return new StringBuilder( s ).reverse().toString();
  }

  protected static Domain prefixesAndSuffixes( String dom1Val, StringDomain sd2,
                                               boolean isPrefix ) {
    String r1 = null;
    String r2 = null;
    // Try not to loop over the whole thing.  Try from both sides.
    // Left side first.
    long n1 = 0;

    if ( sd2.isInfinite() ) {
      String v2 = null;
      String v1 = null;

      StringDomain d1 = minusPrefixSuffixWild( dom1Val, sd2.getLowerBound(), isPrefix, false );
      StringDomain d2 = minusPrefixSuffixWild( dom1Val, sd2.getUpperBound(), isPrefix, false );
      d1.union( d2 );
      return d1;
    }
    for ( ; n1 < sd2.size(); ++n1 ) {
      String nv = sd2.getNthValue( n1 );
      String v = null;
      if ( isPrefix ) {
        v = minusPrefix( dom1Val, nv );
      } else {
        v = minusSuffix( dom1Val, nv );
      }

      if ( v != null ) {
        if ( r1 == null ) {
          r1 = v;
        } else if ( !r1.equals( v ) ) {
          r2 = v;
          break;
        }
      }
    }
    // Now right side.
    if ( r2 != null ) {
      boolean startedReplacing = false;
      for ( long n = sd2.size() - 1; n > n1; --n ) {
        String nv = sd2.getNthValue( n );
        String v = null;
        if ( isPrefix ) {
          v = minusPrefix( dom1Val, nv );
        } else {
          v = minusSuffix( dom1Val, nv );
        }
        if ( v != null ) {
          if ( r2.equals( v ) ) {
            break;
          } else if ( !r1.equals( v ) ) {
            // a third value to maybe set as max or min
            if ( ( sd2.less( v, r2 ) && sd2.less( r2, r1 ) ) || (
                    sd2.greater( v, r2 ) && sd2.greater( r2, r1 ) ) ) {
              r2 = v;
              startedReplacing = true;
            } else if ( ( sd2.less( v, r1 ) && sd2.less( r1, r2 ) ) || (
                    sd2.greater( v, r1 ) && sd2.greater( r1, r2 ) ) ) {
              r1 = v;
              startedReplacing = true;
            } else if ( startedReplacing ) {
              // hit a max or min -- time to quit
              break;
            }
          } else if ( startedReplacing ) {
            // hit a max or min -- time to quit
            break;
          }
        }
      }
    }
    StringDomain sd = null;
    if ( r1 == null ) {
       sd = new StringDomain( r2, r2 );
    } else
    if ( r2 == null ) {
      sd = new StringDomain( r1, r1 );
    } else {
        boolean less = sd2.less( r1, r2 );
        if ( less ) {
            sd = new StringDomain( r1, r2 );
        } else {
            sd = new StringDomain( r2, r1 );
        }
    }
    sd.kind = isPrefix ?
              StringDomain.Kind.SUFFIX_RANGE :
              StringDomain.Kind.PREFIX_RANGE;
    return sd;
  }

  public static class Floor<T> extends Unary<T, T> {
    protected static final String floor = "floor";

    public Floor( Variable<T> o ) {
      super( o, floor );
    }

    public Floor( Expression<T> o1 ) {
      super( o1, floor );
    }

    public Floor( Object o1 ) {
      super( o1, floor );
    }

    public Floor( Unary m ) {
      super( m );
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( returnValue == null ) return null;
      AbstractRangeDomain<T> d = DomainHelper.createSubDomainAbove( returnValue, true, false );
      T ub = null;
      try {
        ub = plus( d.getLowerBound(), 1 );
      } catch ( Throwable e ) {
      }
      if ( ub != null ) {
        d.setUpperBound( ub );
        d.excludeUpperBound();
        return new Identity( new Expression(d) );
      }
      Debug.error(true, false, "ERROR!  Floor.inverseSingleValue(" + returnValue + ") failed!" );
      return null;
    }

  }

  public static <T> Object floor( Object e ) {
    Pair<Object, TimeVaryingMap<?>> p = numberOrTimelineOrDistribution( e );
    if ( p == null ) {
      return null;
    }
    if ( p.second != null ) {
        try {
            return (T)p.second.floor();
        } catch ( Throwable e1 ) {
            e1.printStackTrace();
        }
        return null;
    }
    if ( p.first instanceof Number ) {
        double floored = Math.floor( ((Number)p.first).doubleValue() );
        Pair<Boolean, ? extends Object> flooredPair =
              ClassUtils.coerce( floored, p.first.getClass(), false );
        if ( flooredPair != null && flooredPair.first != null && flooredPair.first ) {
          return flooredPair.second;
        }
    } else if ( p.first instanceof Distribution ) {
//        Debug.error(true, false, "ERROR! floor() for Distribution is not yet implemented!");
        return DistributionHelper.floor( (Distribution)p.first );
    }
    Debug.error(true, false, "ERROR! floor(" + p.first + ") has argument of unexpected type, " + p.first.getClass().getSimpleName());
    return e;
  }

  // A potentially simpler implementation of mod.
  public static class Mod2< T, R > extends Sub< T, R > {
    public Mod2( Expression< T > o1, Expression< T > o2 ) {
      super( o1, new Expression<T>( new Times(o2, new Divide(o1, o2))));
      // functionCall.
      setMonotonic( false );
    }

    public Mod2( Object o1, Object c ) {
      super( o1, (Object)new Expression(new Times(c, new Divide(o1, c))));
      // functionCall.
      setMonotonic( false );
    }

    public Mod2( Functions.Mod2< T, R > m ) {
      super( m );
    }

    public Mod2< T, R > clone() {
      return new Mod2< T, R >( this );
    }
}

  /**
   * Mod(x,y) = x - y * floor( x / y )
   * @param <T>
   * @param <R>
   */
  public static class Mod< T, R > extends Binary< T, R > {
    public Mod( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "mod", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( false );
    }

    public Mod( Object o1, Object c ) {
      super( o1, c, "mod", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( false );
    }

    public Mod( Functions.Mod< T, R > m ) {
      super( m );
    }

    public Mod< T, R > clone() {
      return new Mod< T, R >( this );
    }

    /**
     * mod(x,y) = x - y * floor( x / y )
     * <p>
     *     For positive valued x, the result of mod(x,y) ranges in
     *     [0, min(x.domain.ub, abs(y).domain.ub)] - {abs(y).domain.ub}.
     * </p>
     * <p>
     *     If x is always negative, then
     *     [max(x.domain.lb, -abs(y).domain.ub), 0] - {-(abs(y).domain.ub)}.
     * </p>
     * <p>
     *     If x can be positive or negative,
     *     [max(x.domain.lb, -abs(y).domain.ub), min(x.domian.ub, abs(y).domain.ub)] - {-(abs(y).domain.ub), abs(y).domain.ub}.
     * </p>
     *
     * @param propagate
     * @param seen
     * @return
     */
    @Override
    public Domain<?> calculateDomain( boolean propagate, Set<HasDomain> seen ) {
      Domain< ? > d = DomainHelper.combineDomains( arguments, this, true );
      return d;
        /*
      if ( arguments != null && arguments.size() == 2 ) {
        Object arg1 = arguments.get( 0 );
        Object arg2 = arguments.get( 1 );

        //Domain d1 = DomainHelper.getDomain( arg1 );
        //Domain d2 = DomainHelper.getDomain( arg2 );
        ComparableDomain<Object> cd1 = DomainHelper.getComparableDomain( arg1 );
        ComparableDomain<Object> cd2 = DomainHelper.getComparableDomain( arg2 );
        if ( cd1 != null && cd2 != null ) {

        }
      }
      return super.calculateDomain( propagate, seen );
        */
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
      // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
      // null!
      if ( firstArg ) {
        return new Sum< T, T >( returnValue, new Times(otherArg, new Divide(arg, otherArg)) );
      }
      // The set of numbers whose mod with otherArg equals returnValue.  Represent as
      // otherArg * IntegerDomain.positiveDomain + returnValue.
      // TODO  -- We should intersect the result with the domain of arg.
      // FIXME -- Instead, we're just using arg's domain instead of the positive integers.
      Domain d = DomainHelper.getDomain( arg );
      if ( d == null ) d = IntegerDomain.positiveDomain;
      return new Sum< T, T >( returnValue, new Plus( new Times( d, otherArg ), returnValue ) );
    }
  }

  /**
   * Remainder(x,y) = Mod(x,y) = x - y * floor( x / y )
   * @param <T>
   * @param <R>
   */
  public static class Remainder< T, R > extends Mod< T, R > {
    public Remainder( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
      setMonotonic( false );
    }

    public Remainder( Object o1, Object c ) {
      super( o1, c );
      setMonotonic( false );
    }

    public Remainder( Functions.Remainder< T, R > m ) {
      super( m );
    }

    public Remainder< T, R > clone() {
      return new Remainder< T, R >( this );
    }

}

  /**
   * mod(o1,o2) = o1 % o2 = o1 - o2 * floor( o1 / o2 )
   *
   * @param o1
   * @param o2
   * @param <T>
   * @param <TT>
   * @return
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws InstantiationException
   */
  public static < T, TT > T
  mod( Expression< T > o1,
            Expression< TT > o2 ) throws IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    T r1 = Expression.evaluateDeep(o1, null, false, false);
    TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    if ( r1 instanceof Double && r2 instanceof Number ) {
      return (T)(Double)0.0;
    }
    return mod( r1, r2 );
  }

  /**
   * mod(o1,o2) = o1 % o2 = o1 - o2 * floor( o1 / o2 )
   *
   * @param o1
   * @param o2
   * @param <V1>
   * @param <V2>
   * @return
   * @throws ClassCastException
   * @throws IllegalAccessException
   * @throws InvocationTargetException
   * @throws InstantiationException
   */
  public static < V1, V2 > V1 mod( V1 o1, V2 o2 ) throws ClassCastException,
                                                           IllegalAccessException,
                                                           InvocationTargetException,
                                                           InstantiationException {
    return minus( o1, times(o2, floor( divide( o1, o2 ) ) ) );
  }



  public static class Times< T, R > extends Binary< T, R > {
    public Times( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "times", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public Times( Object o1, Object c ) {
      super( o1, c, "times", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public Times( Functions.Times< T, R > m ) {
      super( m );
    }

    public Times< T, R > clone() {
      return new Times< T, R >( this );
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      return new Divide< T, T >( returnValue, otherArg );
    }

    @Override
    public Domain< ? > inverseDomain( Domain< ? > returnValue,
                                      Object argument ) {
      if ( returnValue == null || argument == null ) return null;
      // If the return value and the other arg can be zero,
      // then the inverse can be anything.
      if ( arguments == null || arguments.size() != 2 ) {
        return super.inverseDomain( returnValue, argument );
      }
      Object otherArg = ( argument == arguments.get( 1 ) ? arguments.get( 0 )
                                                         : arguments.get( 1 ) );
      if ( otherArg == null ) {
        return super.inverseDomain( returnValue, argument );
      }
      boolean returnHasZero = DomainHelper.contains( returnValue, 0.0 ) ||
                              DomainHelper.contains( returnValue, 0 );
      if ( returnHasZero ) {
        Domain<Object> otherDomain = DomainHelper.getDomain( otherArg );
        boolean otherHasZero =
                otherDomain != null &&
                ( DomainHelper.contains( otherDomain, 0.0 ) ||
                  DomainHelper.contains( otherDomain, 0 ) );
        if ( otherHasZero ) {
          return otherDomain.getDefaultDomain();
        }
      }
      return super.inverseDomain( returnValue, argument );
    }

  }

  public static class Mul< T, R > extends Times< T, R > {
    public Mul( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
      setMonotonic( true );
    }

    public Mul( Object o1, Object c ) {
      super( o1, c );
      setMonotonic( true );
    }
  }

  public static class Divide< T, R > extends Binary< T, R > {
    public Divide( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "divide", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public Divide( Object o1, Object c ) {
      super( o1, c, "divide", "pickValueForward", "pickValueReverse" );
      // functionCall.
      setMonotonic( true );
    }

    public Divide( Functions.Divide< T, R > m ) {
      super( m );
    }

    public Divide< T, R > clone() {
      return new Divide< T, R >( this );
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
                                                         // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      if ( firstArg ) {
        return inverseDivideForNumerator( returnValue, otherArg );
      } else {
        return inverseDivideForDenominator( returnValue, otherArg );
      }
    }

    public static boolean areIntegers( Object r1, Object r2 ) {
      if ( r1 == null || r2 == null ) return false;
      boolean isint = r1 instanceof Integer &&
                      r2 instanceof Integer;
      return isint;
    }
    public static boolean areIntegersOrLong( Object r1, Object r2 ) {
        if ( r1 == null || r2 == null ) return false;
        boolean isint = ( r1 instanceof Integer || r1 instanceof Long ) &&
                        ( r2 instanceof Integer || r2 instanceof Long );
        return isint;
    }

    public static <T> FunctionCall inverseDivideForNumerator(Object returnValue,
                                                             Object denominator) {
      // If the arguments are both integer/long, then we need to return
      // the range of integers that when divided by otherArg, equals returnValue.
      // For example, for 3 = x / 5, x can be [15,19].
      // So, we want
      // [returnValue * otherArg, (returnValue+1) * otherArg - 1]
      // special cases:
      //   1 = x / y  -> x = y
      //   0 = x / inf  -> x is anything
      //   z = x / inf  -> x is inf
      //   0 = x / y  -> x = 0
      //   inf = x / 0  -> x is anything
      //   z = x / 0  -> x = 0
      //
      Object r1=null, r2=null;
      try {
        r1 = Expression.evaluateDeep( returnValue, null, false, false );
        r2 = Expression.evaluateDeep( denominator, null, false, false );
      } catch (Throwable t) {}

      if ( r1 == null || r2 == null ) return null;
      if ( areIntegersOrLong( r1, r2 ) ) {
        long l1 = ((Number)r1).longValue();
        long l2 = ((Number)r2).longValue();
        boolean isPositive = l1 < 0 == l2 < 0;
        Domain d = null;
        boolean resultLong = !areIntegers( r1, r2 );
        Integer i1 = resultLong ? null : ((Number)r1).intValue();
        Integer i2 = resultLong ? null : ((Number)r2).intValue();

        // If the return value is one, return the denominator
        if ( l1 == 1 ) {
          return new Identity<Number>(new Expression<Number>( denominator ) );
        }
        // If the denominator value is one, return the return value
        if ( l2 == 1 ) {
          return new Identity<Number>(new Expression<Number>( returnValue ) );
        }
        // If the denominator is zero
        if ( l2 == 0 ) {
          // If the return value is also. zero the numerator is zero.
          if ( l1 == 0 ) {
            return new Identity<Number>(new Expression<Number>( 0 ) );
          }
          // If the return value is infinity, the numerator is either
          // non-zero positive or negative and not zero, I guess.
          if ( gov.nasa.jpl.ae.util.Math.isInfinity( r1 ) ||
               gov.nasa.jpl.ae.util.Math.isNegInfinity( r1 ) ) {
            if ( isPositive ) {
              if ( resultLong ) {
                d = new LongDomain( 1, LongDomain.typeMaxValue );
              } else {
                d = new IntegerDomain( 1, IntegerDomain.typeMaxValue );
              }
            } else {
              if ( resultLong ) {
                d = new LongDomain( LongDomain.typeMinValue, -1 );
              } else {
                d = new IntegerDomain( IntegerDomain.typeMinValue, -1 );
              }
            }
          }
          //d = resultLong ? new LongDomain() : new IntegerDomain();
        } else
        // If the return value is zero
        if ( l1 == 0 ) {
          // and the denominator is infinity, then the numerator can be anything.
          if ( gov.nasa.jpl.ae.util.Math.isInfinity( r2 ) ||
               gov.nasa.jpl.ae.util.Math.isNegInfinity( r2 ) ) {
            d = resultLong ? new LongDomain() : new IntegerDomain();
          }
        }

//        if ( d == null ) {
//          return new Minus( new Times( new Plus( returnValue, 1 ), denominator ), 1 );
//        }
//        // TODO -- delete the if-else below -- will not get called because d == null returns above

        // Integer or Long domain =
        // [returnValue * otherArg, (returnValue+1) * otherArg - 1]
        if ( d == null && resultLong ) {
          Long lb = gov.nasa.jpl.ae.util.Math.times( l1, l2 );
          Long ub =
                  gov.nasa.jpl.ae.util.Math.minus(
                          gov.nasa.jpl.ae.util.Math.times(
                                  gov.nasa.jpl.ae.util.Math.plus(l1, l1 < 0 ? -1 : 1),
                                  l2 ),
                          isPositive ? 1 : -1);
          // swap in case of negative signs
          if ( ub < lb ) {
            Long t = ub;
            ub = lb;
            lb = t;
          }
          d = new LongDomain( lb,ub );
        } else if ( d == null ) {
          Integer lb = gov.nasa.jpl.ae.util.Math.times( i1, i2 );
          Integer ub =
                  gov.nasa.jpl.ae.util.Math.minus(
                          gov.nasa.jpl.ae.util.Math.times(
                                  gov.nasa.jpl.ae.util.Math.plus(i1, i1 < 0 ? -1 : 1),
                                  i2 ),
                          isPositive ? 1 : -1);
          // swap in case of negative signs
          if ( ub < lb ) {
            Integer t = ub;
            ub = lb;
            lb = t;
          }
          d = new IntegerDomain(lb, ub);
        }
        if ( d != null ) {
          return new Identity<Number>( new Expression<Number>( d ) );
//          return new FunctionCall( null, newListMethod,
//                                   new Object[] { d }, (Class<?>)null );
        }
      }
      return new Times< T, T >( returnValue, denominator );
    }

    public static <T> FunctionCall inverseDivideForDenominator(Object returnValue,
                                                           Object numerator) {
      // If the arguments are both integer/long, then we need to return
      // the range of integers that can divided into the numerator to get the returnValue.
      // For example, for 3 = 100 / x, x can be [26,33].
      // So, we want
      // [1 + numerator / (returnValue + 1), numerator / returnValue]
      // but only if abs(numerator) >= abs(returnValue); otherwise, return 0;
      // special cases:
      //   1 = x / y  -> y = x
      //   z = z / y  -> y = 1
      //   z = -z / y  -> y = -1
      //   z = inf / y  -> y is inf or -inf
      //   0 = 0 / y  -> y = anything
      //   0 = x / y  -> y = inf or -inf (or *maybe* empty set if x = inf)
      //   z = 0 / y  -> y = 0
      //   inf = x / y  -> y = 0

      Object r1=null, r2=null;
      try {
        r1 = Expression.evaluateDeep( returnValue, null, false, false );
        r2 = Expression.evaluateDeep( numerator, null, false, false );
      } catch (Throwable t) {}

      if ( r1 == null || r2 == null ) return null;
      if ( !areIntegersOrLong( r1, r2 ) ) {
        return new Divide< T, T >( numerator, returnValue );
      }
      long l1 = ((Number)r1).longValue();
      long l2 = ((Number)r2).longValue();
      Domain d = null;
      boolean resultLong = !areIntegers( r1, r2 );
      Integer i1 = resultLong ? null : ((Number)r1).intValue();
      Integer i2 = resultLong ? null : ((Number)r2).intValue();
      boolean isPositive = l1 < 0 == l2 < 0;

      // If you divide a smaller int by a bigger one, you get 0 because there are no fractions.
      // This takes care of the special case: z = 0 / y  -> y = 0
      if ( Math.abs( l2 ) < Math.abs( l1 ) ) {
        return new Identity<Number>(new Expression<Number>( 0 ) );
      }
      // If the return value is one, return the numerator
      if ( l1 == 1 ) {
        return new Identity<Number>(new Expression<Number>( numerator ) );
      }
      // If the values are equal return 1.
      if ( Utils.valuesEqual( Math.abs( l2 ), Math.abs( l1 ) ) ) {
        return new Identity<Number>(new Expression<Number>( isPositive ? 1 : -1 ) );
      }
      // If the return value and the denominator are zero, return anything
      if ( l2 == 0 && l1 == 0 ) {
          d = resultLong ? new LongDomain() : new IntegerDomain();
          return new Identity<Number>( new Expression<Number>( d ) );
      }
      // If the numerator is inf, return inf
      // If the return value is zero, return inf
      if ( gov.nasa.jpl.ae.util.Math.isInfinity( r2 ) ||
           gov.nasa.jpl.ae.util.Math.isNegInfinity( r2 ) ) {
        Number n = null;
        if ( resultLong ) {
          n = isPositive ? LongDomain.typeMaxValue : LongDomain.typeMinValue;
        } else {
          n = isPositive ? IntegerDomain.typeMaxValue : IntegerDomain.typeMinValue;
        }
        return new Identity<Number>(new Expression<Number>( n ) );
      }
      // If the return value is zero, return {-inf,inf}
      if ( l1 == 0 ) {
        Set<Domain> includeSet = new LinkedHashSet<>();
        Set<Domain> excludeSet = null;
        if ( resultLong ) {
          d = new LongDomain( LongDomain.typeMinValue, LongDomain.typeMinValue );
          includeSet.add( d );
          d = new LongDomain( LongDomain.typeMaxValue, LongDomain.typeMaxValue );
          includeSet.add( d );
        } else {
          d = new IntegerDomain( IntegerDomain.typeMinValue, IntegerDomain.typeMinValue );
          includeSet.add( d );
          d = new IntegerDomain( IntegerDomain.typeMaxValue, IntegerDomain.typeMaxValue );
          includeSet.add( d );
        }
        d = new MultiDomain( resultLong ? Long.class : Integer.class, includeSet, excludeSet);
        return new Identity<Number>( new Expression<Number>( d ) );
      }

      // Integer or Long domain =
      // [1 + numerator / (returnValue + 1), numerator / returnValue]
      if ( d == null && resultLong ) {
        Long lb =
                gov.nasa.jpl.ae.util.Math.plus(
                        gov.nasa.jpl.ae.util.Math.dividedBy(
                                l2,
                                gov.nasa.jpl.ae.util.Math.plus(l1, l1 > 0 ? 1 : -1) ),
                        isPositive ? 1 : -1);
        Long ub = gov.nasa.jpl.ae.util.Math.dividedBy( l2, l1 );
        // swap in case of negative signs
        if ( ub < lb ) {
          Long t = ub;
          ub = lb;
          lb = t;
        }
        d = new LongDomain( lb, ub );
      } else if ( d == null ) {
        Integer lb =
                gov.nasa.jpl.ae.util.Math.plus(
                        gov.nasa.jpl.ae.util.Math.dividedBy(
                                i2,
                                gov.nasa.jpl.ae.util.Math.plus(i1, i1 > 0 ? 1 : -1) ),
                        isPositive ? 1 : -1);
        Integer ub = gov.nasa.jpl.ae.util.Math.dividedBy( i2, i1 );
        // swap in case of negative signs
        if ( ub < lb ) {
          Integer t = ub;
          ub = lb;
          lb = t;
        }
        d = new IntegerDomain( lb, ub );
      }


      if ( d == null ) {
        return new Divide< T, T >( numerator, returnValue );
//        // [1 + numerator / (returnValue + 1), numerator / returnValue]
//        return new Minus( new Times( new Plus( returnValue, 1 ), denominator ), 1 );
      } else {
        return new Identity<Number>( new Expression<Number>( d ) );
      }
    }


    @Override
    public Domain< ? > inverseDomain( Domain< ? > returnValue,
                                      Object argument ) {
      if ( returnValue == null || argument == null ) return null;
      // If the return value and the numerator can be zero,
      // then the denominator can be anything.
      if ( arguments == null || arguments.size() != 2 ) {
        return super.inverseDomain( returnValue, argument );
      }
      boolean isDenominator = argument == arguments.get( 1 ); // this arg is the first
      // If the denominator
      if ( isDenominator ) {
        boolean returnHasZero = DomainHelper.contains( returnValue, 0.0 ) ||
                                DomainHelper.contains( returnValue, 0 );
        if ( returnHasZero ) {
          Domain<Object> numeratorDomain = DomainHelper.getDomain( arguments.get( 0 ) );
          boolean numeratorHasZero =
                  numeratorDomain != null &&
                  ( DomainHelper.contains( numeratorDomain, 0.0 ) ||
                    DomainHelper.contains( numeratorDomain, 0 ) );
          if ( numeratorHasZero ) {
            return numeratorDomain.getDefaultDomain();
          }
        }
      }
      return super.inverseDomain( returnValue, argument );
    }
  }


  public static class Div< T, R > extends Divide< T, R > {
    public Div( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
      setMonotonic( true );
    }

    public Div( Object o1, Object c ) {
      super( o1, c );
      setMonotonic( true );
    }
  }

  public static class Pow< T, R > extends Binary< T, R > {
    public Pow( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "pow", "pickValueForward", "pickValueReverse" );
      setMonotonic( true );
    }

    public Pow( Object o1, Object c ) {
      super( o1, c, "pow", "log", "log" );
      setMonotonic( true );
    }

    public Pow( Functions.Pow< T, R > m ) {
      super( m );
    }

    public Pow< T, R > clone() {
      return new Pow< T, R >( this );
    }
  }

  public static class Get< T, R > extends Binary< T, R > {
    public Get( Expression<T> o1, Expression<T> o2 ) {
      super( o1, o2, "get", "pickValueForward", "pickValueReverse" );
      setMonotonic( false );
    }

    public Get( Object o1, Object c ) {
      super( o1, c, "get", "pickValueForward", "pickValueReverse" );
      setMonotonic( false );
    }

    public Get( Functions.Get<T, R> m ) {
      super( m );
    }

    public Get<T, R> clone() {
      return new Get<T, R>( this );
    }

    @Override public <T1> T1 pickValue( Variable<T1> variable ) {
      return super.pickValue( variable );
    }

    @Override public <T> boolean pickParameterValue( Variable<T> variable ) {
      return super.pickParameterValue( variable );
    }

    @Override
    public Domain<?> calculateDomain(boolean propagate, Set<HasDomain> seen) {
      SuggestiveFunctionCall fc = this.clone();
      Domain< ? > d = DomainHelper.combineDomains( arguments, fc, false );
      return d;
//      Object result = null;
//      Object key = this.arguments == null? null : this.arguments.firstElement();
//      Domain keyDomain = null;
//      if ( key != null ) {
//        keyDomain = DomainHelper.getDomain( key );
//      }
//      if ( keyDomain == null ) {//|| keyDomain.isInfinite() ) {
//        return null;
//      }
//      List<Object> keys =
//              DomainHelper.getRepresentativeValues( keyDomain, null );
//
//      if ( keys != null && keys.size() > 1 && this.arguments.size() > 1 ) {
//        for ( Object k : keys ) {
//          Get g = new Get( k, )
//          try {
//            result = evaluate( false );
//          } catch ( IllegalAccessException e ) {
//          } catch ( InvocationTargetException e ) {
//          } catch ( InstantiationException e ) {
//          }
//        }
//      } else {
//        try {
//          result = evaluate( false );
//        } catch ( IllegalAccessException e ) {
//        } catch ( InvocationTargetException e ) {
//        } catch ( InstantiationException e ) {
//        }
//      }
//      return DomainHelper.getDomain(result);
    }

    @Override
    public < TT > Pair< Domain< TT >, Boolean >
    restrictDomain( Domain< TT > domain, boolean propagate,
                    Set< HasDomain > seen ) {
      //boolean changed = false;
      Object o1 = this.arguments.get( 0 );
      Object o2 = this.arguments.get( 1 );
      try {
        Object obj = o1;
        Object key = Expression.evaluate(o2, String.class, propagate);
        if ( o1 instanceof Expression ) {
          obj = ((Expression) o1).evaluate(propagate);
        }
        Object k = Functions.get(obj, (String) key);
        if ( k instanceof HasDomain ) {
          Pair<Domain<TT>, Boolean> p =
                  ((HasDomain) k).restrictDomain(domain, propagate, seen);
          if ( p != null ) {
            Domain<TT> newDomain = p.first;
            this.domain = newDomain;
          }
          return p;
        }
      } catch (IllegalAccessException e) {
      } catch (InvocationTargetException e) {
      } catch (InstantiationException e) {
      }
      return null;
    }

    // TODO
    public Domain< ? > inverseDomain( Domain< ? > returnValue,
                                      Object argument ) {
      Domain<?> d = null;
      if (arguments == null || arguments.size() != 2) return null;
      boolean first = argument == arguments.get(1);
      Object otherArg = (first ? arguments.get(0) : arguments.get(1));
      if (otherArg == null) return null; // arg can be null!
      if (first) {
        // TODO -- search all objects (from top-level Event and maybe all static members of all classes in all packages everywhere forever, 1000 years.
      } else {
        // TODO -- return a member of the other arg whose value equals the returnValue
        // create a function call to get(otherArg, returnValue) defined below.
      }
      return d;
    }

    @Override
    // TODO
    public // < T1 extends Comparable< ? super T1 > >
    FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if (arguments == null || arguments.size() != 2) return null;
      boolean first = arg == arguments.get(1);
      Object otherArg = (first ? arguments.get(0) : arguments.get(1));
      if (returnValue == null || otherArg == null) return null; // arg can be
      // null!
      if (first) {
        // TODO -- search all objects (from top-level Event and maybe all static members of all classes in all packages everywhere forever, 1000 years.
      } else {
        // TODO -- return a member of the other arg whose value equals the returnValue
        // create a function call to get(otherArg, returnValue) defined below.
      }
      return null;// new Minus<T, T>(returnValue, otherArg);
    }

    // TODO -- need to test
    protected Set<Object> getMatchingMembers(Object object, Object value) {
      if ( object == null ) {
        return null;
      }
      LinkedHashSet<Object> set = new LinkedHashSet<Object>();
      Field[] fs = ClassUtils.getAllFields(object.getClass());
      for (Field f : fs) {
        try {
          Object o = f.get(object);
          if (Expression.valuesEqual(o, value)) {
            set.add(o);
          }
        } catch (IllegalAccessException e) {
        }
      }
      return set;
    }

  }

  public static void extendSize(Collection<?> c, int newSize) {
    if ( c == null ) return;
    if ( c.size() <= newSize ) {
      if ( c instanceof List ) {
        for ( int i = c.size(); i < newSize; ++i ) {
          ((List)c).add( null );
        }
      }
    }
  }

  public static <V1, V2> V2 get( V1 object, String key ) {
    if ( Utils.isNullOrEmpty(key) || object == null ) return null;
    V2 v2 = null;
    // TODO -- handle Entries, Pairs, Tuples?
    // TODO -- check if object has a get(x) method

    // Array
    if ( object.getClass().isArray() ) {
      Integer i = Utils.toInteger( key );
      Object[] arr = (Object[])object;
      if ( i != null && i < arr.length ) {
        v2 = (V2)arr[ i ];
      }
    } else if ( object instanceof Map ) {

      // Map
      v2 = (V2)((Map)object).get(key);

    } else if ( object instanceof Collection ) {

      // Collection
      Collection<V2> c = (Collection<V2>)object;
      Integer i = Utils.toInteger( key );
      if ( i != null && i < c.size() ) {

        // List
        if ( object instanceof List ) {
          v2 = (V2)((List)c).get(i);
        } else {

          // Other Collection
          int ctr = 0;
          Iterator<V2> iter = c.iterator();
          while (iter.hasNext()) {
            V2 vv = iter.next();
            if ( i == ctr++ ) {
              v2 = vv;
              break;
            }
          }
        }
      }
    }
    return v2;
  }

  public static <V1, V2> V2 get( Expression<?> object, Expression<?> key ) {
    Object o = null;
    String name = null;
    try {
      o = object.evaluate(false);
      name = key.evaluate(String.class, false);
    } catch (IllegalAccessException e) {
    } catch (InvocationTargetException e) {
    } catch (InstantiationException e) {
    } catch (ClassCastException e) {
    }
    V2 v2 = (V2)get(o, name);
    return v2;
  }


  public static class GetMember< T, R > extends Binary< T, R > {
    public GetMember( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "getMember", "pickValueForward", "pickValueReverse" );
      setMonotonic( false );
    }

    public GetMember( Object o1, Object c ) {
      super( o1, c, "getMember", "pickValueForward", "pickValueReverse" );
      setMonotonic( false );
    }

    public GetMember( Functions.GetMember< T, R > m ) {
      super( m );
    }

    public GetMember< T, R > clone() {
      return new GetMember< T, R >( this );
    }

    @Override
    public Set<Parameter<?>> getParameters(boolean deep, Set<HasParameters> seen) {
      // don't need seen since we won't be recursing anyways?
//      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
//      if ( pair.first ) return Utils.getEmptySet();
//      seen = pair.second;

      Set< Parameter< ? > > set = new LinkedHashSet< Parameter< ? >>();
      Object retVal = this.returnValue;

      if(retVal instanceof Parameter) {
        set.add((Parameter) retVal);
      } else if(retVal instanceof Expression) {
        Parameter p = null;
        try {
          p = Expression.evaluate(retVal, Parameter.class, true);
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        } catch (InvocationTargetException e) {
          e.printStackTrace();
        } catch (InstantiationException e) {
          e.printStackTrace();
        }
        if(p != null) {
          set.add(p);
        }
      }

      return set;
    }


    @Override
    public <T1> T1 pickValue(Variable<T1> variable) {
      return super.pickValue(variable);
    }

    @Override
    public <T> boolean pickParameterValue(Variable<T> variable) {
      return super.pickParameterValue(variable);
    }

    @Override
    public Domain<?> calculateDomain(boolean propagate, Set<HasDomain> seen) {
      Object result = null;
      try {
        result = evaluate(false);
      } catch (IllegalAccessException e) {
      } catch (InvocationTargetException e) {
      } catch (InstantiationException e) {
      }
      if ( result == null ) return null;
      return DomainHelper.getDomain(result);
    }

    @Override
    public < TT > Pair< Domain< TT >, Boolean >
    restrictDomain( Domain< TT > domain, boolean propagate,
                    Set< HasDomain > seen ) {
      //boolean changed = false;
      Object o1 = this.arguments.get( 0 );
      Object o2 = this.arguments.get( 1 );
      try {
        Object obj = o1;
        Object memberName = Expression.evaluate(o2, String.class, propagate);
        if ( memberName instanceof String ) {
          if ( o1 instanceof Expression ) {
            obj = ((Expression) o1).evaluate(propagate);
          }
          Object member = Functions.getMember(obj, (String) memberName);
          if ( member instanceof HasDomain ) {
            Pair<Domain<TT>, Boolean> p =
                    ((HasDomain) member).restrictDomain(domain, propagate,
                                                        seen);
            if ( p != null ) {
              Domain<TT> newDomain = p.first;
              this.domain = newDomain;
            }
            return p;
          }
        }
      } catch (IllegalAccessException e) {
      } catch (InvocationTargetException e) {
      } catch (InstantiationException e) {
      }
      return null;
    }

    // TODO
    public Domain< ? > inverseDomain( Domain< ? > returnValue,
                                      Object argument ) {
      Domain<?> d = null;
      if (arguments == null || arguments.size() != 2) return null;
      boolean first = argument == arguments.get(1);
      Object otherArg = (first ? arguments.get(0) : arguments.get(1));
      if (otherArg == null) return null; // arg can be null!
      if (first) {

        // TODO -- search all objects (from top-level Event and maybe all static members of all classes in all packages everywhere forever, 1000 years.
      } else {
        // TODO -- return a member of the other arg whose value equals the returnValue
        // create a function call to getMatchingMembers(otherArg, returnValue) defined below.
      }
      return d;
    }

      @Override
      // TODO
    public // < T1 extends Comparable< ? super T1 > >
    FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if (arguments == null || arguments.size() != 2) return null;
      boolean first = arg == arguments.get(1);
      Object otherArg = (first ? arguments.get(0) : arguments.get(1));
      if (returnValue == null || otherArg == null) return null; // arg can be
      // null!
      if (first) {
        // TODO -- search all objects (from top-level Event and maybe all static members of all classes in all packages everywhere forever, 1000 years.
      } else {
        // TODO -- return a member of the other arg whose value equals the returnValue
        // create a function call to getMatchingMembers(otherArg, returnValue) defined below.
      }
      return new Minus<T, T>(returnValue, otherArg);  // TODO -- REVIEW -- why Minus?  Is this an unintended copy/paste?
    }

    // TODO -- need to test
    protected Set<Object> getMatchingMembers(Object object, Object value) {
      if ( object == null ) {
        return null;
      }
      LinkedHashSet<Object> set = new LinkedHashSet<Object>();
      Field[] fs = ClassUtils.getAllFields(object.getClass());
      for (Field f : fs) {
        try {
          Object o = f.get(object);
          if (Expression.valuesEqual(o, value)) {
            set.add(o);
          }
        } catch (IllegalAccessException e) {
        }
      }
      return set;
    }

    @Override
    public void setStaleAnyReferencesTo( Parameter<?> changedParameter,
                                         Set<HasParameters> seen ) {
      super.setStaleAnyReferencesTo( changedParameter, seen );
      if ( !isStale() ) {
        Object owner = getArgument( 0 );
        Object memberName = getArgument( 1 );
        if ( memberName != null &&
             Expression.valuesEqual( memberName, changedParameter.getName() ) &&
             ( changedParameter.getOwner() == null ||
               Expression.valuesEqual( changedParameter.getOwner(), owner ) ) ) {
          System.out.println("Setting GetMember stale: " + this.toShortString() );
          setStale( true );
        }
      }
    }

    @Override public String toString() {
      if ( getArguments().size() == 2 ) {
        String objName = null;
        if ( getArgument( 0 ) instanceof HasOwner ) {
          objName = ((HasOwner)getArgument( 0 )).getQualifiedName( null );
        } else if ( getArgument( 0 ) instanceof HasName ) {
          objName = ((HasName)getArgument( 0 )).getName().toString();
        } else {
          objName = MoreToString.Helper.toShortString( getArgument( 0 ) );
        }
        return "GetMember(" + objName + ", " + getArgument( 1 ) + ")";
      }
      return toString( true, false, null, true, null );
    }

    @Override
    public synchronized String toString( boolean withHash, boolean deep,
                                         Set<Object> seen, boolean argsShort,
                                         Map<String, Object> otherOptions ) {
      //return super.toString( withHash, deep, seen, true, otherOptions );
      return toString();
    }

    @Override
    public synchronized String toString( boolean withHash, boolean deep,
                                         Set<Object> seen,
                                         Map<String, Object> otherOptions ) {
      return toString();
    }
  }


  public static <V1, V2> V2 getMember( V1 object, String memberName ) {
    if ( Utils.isNullOrEmpty(memberName) || object == null ) return null;
    V2 v2 = null;
    if ( object instanceof  Parameter ) {
      v2 = (V2)((Parameter)object).getMember(memberName, true);
    } else {
      v2 = (V2)ClassUtils.getFieldValue( object, memberName, true );
    }
//    System.out.println("getMember(" + ClassUtils.getName( object ) +
//                       ", \"" + memberName + "\") = " +
//                       MoreToString.Helper.toString(v2, true, false, null) );
    // TODO handle timeline and distribution cases
    return v2;
  }

  public static <V1, V2> V2 getMember( Expression<?> object, Expression<?> memberName ) {
    Object o = null;
    String name = null;
    try {
      o = object.evaluate(false);
      name = memberName.evaluate(String.class, false);
    } catch (IllegalAccessException e) {
    } catch (InvocationTargetException e) {
    } catch (InstantiationException e) {
    } catch (ClassCastException e) {
    }
    V2 v2 = (V2)getMember(o, name);
    return v2;
  }


  // TODO -- If MAX_VALUE is passed in, should treat as infinity; should also
  // print "inf"
  // add(Expr, Expr) should call this fcn.
  public static < V1, V2 > V1 plus( V1 o1, V2 o2 ) throws ClassCastException,
                                                   IllegalAccessException,
                                                   InvocationTargetException,
                                                   InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    Object result = null;
    if ( o1 instanceof String || o2 instanceof String ) { // TODO -- this won't
                                                          // work for timelines
      result = "" + o1 + o2;
      // String s = MoreToString.Helper.toString( o1 ) +
      // MoreToString.Helper.toString( o2 );
    } else {
      Number n1 = null;
      Number n2 = null;
      TimeVaryingMap< ? > map1 = null;
      TimeVaryingMap< ? > map2 = null;
      Distribution<?> d1 = null;
      Distribution<?> d2 = null;

      Object arg1 = null;
      Object arg2 = null;

      Pair< Object, TimeVaryingMap< ? > > p1 = numberOrTimelineOrDistribution( o1 );
      map1 = p1.second;
      if ( p1.first instanceof Distribution ) {
        d1 = (Distribution)p1.first;
        arg1 = d1;
      } else if ( p1.first instanceof Number ) {
        n1 = (Number)p1.first;
        arg1 = map1 == null ? n1 : map1;
      }
      if ( arg1 == null ) arg1 = o1;
      Pair< Object, TimeVaryingMap< ? > > p2 = numberOrTimelineOrDistribution( o2 );
      map2 = p2.second;
      if ( p2.first instanceof Distribution ) {
        d2 = (Distribution)p2.first;
        arg2 = d2;
      } else if ( p2.first instanceof Number ) {
        n2 = (Number)p2.first;
        arg2 = map2 == null ? n2 : map2;
      }
      if ( arg2 == null) arg2 = o2;

      /*
      Pair< Number, TimeVaryingMap< ? > > p1 = numberOrTimeline( o1 );
      n1 = p1.first;
      map1 = p1.second;
*/
      if ( map1 != null ) {
        result = (V1)plus( map1, o2 );
      } else {
        /*
        Pair< Number, TimeVaryingMap< ? > > p2 = numberOrTimeline( o2 );
        n2 = p2.first;
        map2 = p2.second;
        */

        if ( map2 != null ) {
          result = (V1)plus( o1, map2 );
        }
      }

      // Number n1 = null;
      // Number n2 = null;
      // try {
      // n1 = Expression.evaluate( o1, Number.class, false );
      // n2 = Expression.evaluate( o2, Number.class, false );
      // } catch ( Throwable e ) {
      // // ignore
      // }
      if ( n1 != null && n2 != null ) {
        if ( Infinity.isEqual( n1 ) ) {
          try {
            if ( NegativeInfinity.isEqual( n2 ) ) {
              result = Zero.forClass( o1.getClass(), n1.getClass() );
            } else {
              result = Infinity.forClass( o1.getClass(), n1.getClass() );
            }
          } catch ( ClassCastException e ) {
            e.printStackTrace();
          }
        } else if ( NegativeInfinity.isEqual( n1 ) ) {
          try {
            if ( Infinity.isEqual( n2 ) ) {
              result = Zero.forClass( o1.getClass(), n1.getClass() );
            } else {
              result = NegativeInfinity.forClass( o1.getClass(), n1.getClass() );
            }
          } catch ( ClassCastException e ) {
            e.printStackTrace();
          }
        } else if ( n1 instanceof Double || n2 instanceof Double ) {
          result = (Double)gov.nasa.jpl.ae.util.Math.plus( n1.doubleValue(),
                                                           n2.doubleValue() );
        } else if ( n1 instanceof Float || n2 instanceof Float ) {
          result = (Float)gov.nasa.jpl.ae.util.Math.plus( n1.floatValue(),
                                                          n2.floatValue() );
        } else if ( n1 instanceof Long || n2 instanceof Long ) {
          result = (Long)gov.nasa.jpl.ae.util.Math.plus( n1.longValue(),
                                                         n2.longValue() );
        } else {
          result = (Integer)gov.nasa.jpl.ae.util.Math.plus( n1.intValue(),
                                                            n2.intValue() );
        }
      }
      if ( d1 != null || d2 != null ) {
        result = DistributionHelper.plus( //arg1, arg2,
                                          o1, o2 );
      }
        // else {
      // TimeVaryingMap<?> map = null;
      // try {
      // map = Expression.evaluate( o1, TimeVaryingMap.class, false );
      // } catch ( Throwable e ) {
      // //ignore
      // }
      // if ( map != null ) result = plus( map, o2 );
      // else {
      // try {
      // Object obj = Expression.evaluate( o2, TimeVaryingMap.class, false );
      // if ( obj instanceof TimeVaryingMap ) {
      // map = (TimeVaryingMap< ? >)obj;
      // }
      // } catch ( Throwable e ) {
      // //ignore
      // }
      // if ( map != null ) result = plus( o1, map );
      // }
      // }
    }
    try {
      if ( o1 != null && o2 != null && result != null ) {
        Class< ? > cls1 = o1.getClass();
        Class< ? > cls2 = o2.getClass();
        Object x =
            Expression.evaluate( result,
                                 ClassUtils.dominantTypeClass( cls1, cls2 ),
                                 false );
        if ( x == null ) x = result;
        // TODO: type casting this w/ V1 assume that it is the dominant class
        return (V1)x;
      }
      return (V1)result;
    } catch ( Throwable e ) {
      e.printStackTrace();
    }
    return null;
  }

  public static < V1, V2 > V1
         min( V1 o1, V2 o2 ) throws ClassCastException, IllegalAccessException,
                             InvocationTargetException, InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    Object result = null;
    if ( o1 instanceof String || o2 instanceof String ) {
      String s1 = "" + o1;
      String s2 = "" + o2;
      int comp = s1.compareTo( s2 );
      result = ( comp == 1 ) ? s2 : s1;
      // String s = MoreToString.Helper.toString( o1 ) +
      // MoreToString.Helper.toString( o2 );
    } else {
      Number n1 = null;
      Number n2 = null;
      TimeVaryingMap< ? > map1 = null;
      TimeVaryingMap< ? > map2 = null;

      Pair< Number, TimeVaryingMap< ? > > p1 = numberOrTimeline( o1 );
      n1 = p1.first;
      map1 = p1.second;

      if ( map1 != null ) {
        result = (V1)min( map1, o2 );
      } else {
        Pair< Number, TimeVaryingMap< ? > > p2 = numberOrTimeline( o2 );
        n2 = p2.first;
        map2 = p2.second;

        if ( map2 != null ) {
          result = (V1)min( o1, map2 );
        }
      }

      // TimeVaryingMap<?> map = null;
      // try {
      // map = Expression.evaluate( o1, TimeVaryingMap.class, false );
      // } catch ( Throwable e ) {
      // //ignore
      // }
      // if ( map != null ) result = min( map, o2 );
      // else {
      // try {
      // map = Expression.evaluate( o2, TimeVaryingMap.class, false );
      // } catch ( Throwable e ) {
      // //ignore
      // }
      // if ( map != null ) result = min( o1, map );
      // else {
      // Number n1 = null;
      // Number n2 = null;
      // try {
      // n1 = Expression.evaluate( o1, Number.class, false );
      // n2 = Expression.evaluate( o2, Number.class, false );
      // } catch ( Throwable e ) {
      // // ignore
      // }
      if ( n1 != null && n2 != null ) {
        if ( NegativeInfinity.isEqual( n1 )
             || NegativeInfinity.isEqual( n2 ) ) {
          try {
            result = NegativeInfinity.forClass( o1.getClass(), n1.getClass() );
          } catch ( ClassCastException e ) {
            e.printStackTrace();
          }
        } else if ( n1 instanceof Double || n2 instanceof Double ) {
          result = Math.min( n1.doubleValue(), n2.doubleValue() );
        } else if ( n1 instanceof Float || n2 instanceof Float ) {
          result = Math.min( n1.floatValue(), n2.floatValue() );
        } else if ( n1 instanceof Long || n2 instanceof Long ) {
          result = Math.min( n1.longValue(), n2.longValue() );
          // TODO
//        } else if ( n1 instanceof BigDecimal || n2 instanceof BigDecimal ) {
//        } else if ( n1 instanceof BigInteger || n2 instanceof BigInteger ) {
        } else {
          result = Math.min( n1.intValue(), n2.intValue() );
        }
      }
    }
    // }
    // }
    try {
      if ( o1 != null && o2 != null && result != null ) {
        Class< ? > cls1 = o1.getClass();
        Class< ? > cls2 = o2.getClass();
        Object x =
            Expression.evaluate( result,
                                 ClassUtils.dominantTypeClass( cls1, cls2 ),
                                 false );
        if ( x == null ) x = result;
        // TODO: type casting this w/ V1 assume that it is the dominant class
        return (V1)x;
      }
      return (V1)result;
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    return null;
  }

  public static < T, TT > T min( Expression< T > o1,
                                 Expression< TT > o2 ) throws ClassCastException,
                                                       IllegalAccessException,
                                                       InvocationTargetException,
                                                       InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    T r1 = Expression.evaluateDeep(o1, null, false, false);
    TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    return min( r1, r2 );
  }

  public static < T, TT > T max( Expression< T > o1,
                                 Expression< TT > o2 ) throws IllegalAccessException,
                                                       InvocationTargetException,
                                                       InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    T r1 = Expression.evaluateDeep(o1, null, false, false);
    TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    return max( r1, r2 );
  }

  public static < V1, V2 > V1
         max( V1 o1, V2 o2 ) throws ClassCastException, IllegalAccessException,
                             InvocationTargetException, InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    Object result = null;
    if ( o1 instanceof String || o2 instanceof String ) {
      String s1 = "" + o1;
      String s2 = "" + o2;
      int comp = s1.compareTo( s2 );
      result = ( comp == -1 ) ? s2 : s1;
      // String s = MoreToString.Helper.toString( o1 ) +
      // MoreToString.Helper.toString( o2 );
    } else {
      Number n1 = null;
      Number n2 = null;
      TimeVaryingMap< ? > map1 = null;
      TimeVaryingMap< ? > map2 = null;

      Pair< Number, TimeVaryingMap< ? > > p1 = numberOrTimeline( o1 );
      n1 = p1.first;
      map1 = p1.second;

      if ( map1 != null ) {
        result = (V1)max( map1, o2 );
      } else {
        Pair< Number, TimeVaryingMap< ? > > p2 = numberOrTimeline( o2 );
        n2 = p2.first;
        map2 = p2.second;

        if ( map2 != null ) {
          result = (V1)max( o1, map2 );
        }
      }
      if ( n1 != null && n2 != null ) {
        if ( Infinity.isEqual( n1 ) || Infinity.isEqual( n2 ) ) {
          try {
            result = Infinity.forClass( o1.getClass(), n1.getClass() );
          } catch ( ClassCastException e ) {
            e.printStackTrace();
          }
        } else if ( n1 instanceof Double || n2 instanceof Double ) {
          result = Math.max( n1.doubleValue(), n2.doubleValue() );
        } else if ( n1 instanceof Float || n2 instanceof Float ) {
          result = Math.max( n1.floatValue(), n2.floatValue() );
        } else if ( n1 instanceof Long || n2 instanceof Long ) {
          result = Math.max( n1.longValue(), n2.longValue() );
        } else {
          result = Math.max( n1.intValue(), n2.intValue() );
        }
      }
    }
    try {
      if ( o1 != null && o2 != null && result != null ) {
        Class< ? > cls1 = o1.getClass();
        Class< ? > cls2 = o2.getClass();
        Object x =
            Expression.evaluate( result,
                                 ClassUtils.dominantTypeClass( cls1, cls2 ),
                                 false );
        if ( x == null ) x = result;
        // TODO: type casting this w/ V1 assume that it is the dominant class
        return (V1)x;
      }
      return (V1)result;
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    return null;
  }

  public static < L, V1, V2 > Object
         argmin( L l1, V1 o1, L l2,
                 V2 o2 ) throws ClassCastException, IllegalAccessException,
                         InvocationTargetException, InstantiationException {
    return argminormax( l1, o1, l2, o2, true );
  }

  public static < L, V1, V2 > Object
         argmax( L l1, V1 o1, L l2,
                 V2 o2 ) throws ClassCastException, IllegalAccessException,
                         InvocationTargetException, InstantiationException {
    return argminormax( l1, o1, l2, o2, false );
  }

  public static < L, V1, V2 > Object
         argmin( Object... keysAndValues ) throws ClassCastException,
                                           IllegalAccessException,
                                           InvocationTargetException,
                                           InstantiationException {
    return argminormax( true, keysAndValues );
  }

  public static < L, V1, V2 > Object
         argmax( Object... keysAndValues ) throws ClassCastException,
                                           IllegalAccessException,
                                           InvocationTargetException,
                                           InstantiationException {
    return argminormax( false, keysAndValues );
  }

  public static Object argminormax( boolean isMin,
                                    Object... args ) throws ClassCastException,
                                                     IllegalAccessException,
                                                     InvocationTargetException,
                                                     InstantiationException {
    if ( args.length % 2 == 1 ) {
      Debug.error( "argmin/max expects key-value pairs but received an odd number of arguments!" );
    }
    Object bestLabel = null;
    Object bestValue = null;
    for ( int i = 0; i < args.length - 1; i += 2 ) {
      Object label = args[ i ];
      Object value = args[ i + 1 ];
      if ( bestLabel == null ) {
        bestLabel = label;
        bestValue = value;
      } else {
        Object winningLabel =
            argminormax( label, value, bestLabel, bestValue, isMin );
        if ( winningLabel instanceof TimeVaryingMap ) {
          bestLabel = winningLabel;
          if ( isMin ) {
            if ( i < args.length - 2 ) { // Don't compute if nothing left to
                                         // compare against
              bestValue =
                  ( new Functions.Min( new Expression( value ),
                                       new Expression( bestValue ) ) ).evaluate( true );
            }
          } else {
            if ( i < args.length - 2 ) { // Don't compute if nothing left to
                                         // compare against
              bestValue =
                  ( new Functions.Max( new Expression( value ),
                                       new Expression( bestValue ) ) ).evaluate( true );
            }
          }
        } else if ( winningLabel != null && winningLabel != bestLabel
                    && winningLabel == label ) {
          bestLabel = label;
          bestValue = value;
        }
      }
    }
    return bestLabel;
  }

  public static < L, V1, V2 > Object
         argminormax( L l1, V1 o1, L l2, V2 o2,
                      boolean isMin ) throws ClassCastException,
                                      IllegalAccessException,
                                      InvocationTargetException,
                                      InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    Object result = null;
    if ( o1 instanceof String || o2 instanceof String ) {
      String s1 = "" + o1;
      String s2 = "" + o2;
      int comp = s1.compareTo( s2 );
      result = ( comp == ( isMin ? 1 : -1 ) ) ? l2 : l1;
      // String s = MoreToString.Helper.toString( o1 ) +
      // MoreToString.Helper.toString( o2 );
    } else {
      Number n1 = null;
      Number n2 = null;
      TimeVaryingMap< ? > map1 = null;
      TimeVaryingMap< ? > map2 = null;

      Pair< Number, TimeVaryingMap< ? > > p1 = numberOrTimeline( o1 );
      n1 = p1.first;
      map1 = p1.second;

      if ( map1 != null ) {
        result = argminormax( l1, map1, l2, o2, isMin );
      } else {
        Pair< Number, TimeVaryingMap< ? > > p2 = numberOrTimeline( o2 );
        n2 = p2.first;
        map2 = p2.second;

        if ( map2 != null ) {
          result = argminormax( l1, o1, l2, map2, isMin );
        }
      }
      if ( result == null && map1 == null && map2 == null ) {
        if ( n2 == null && n1 != null ) {
          result = l1;
        } else if ( n1 == null && n2 != null ) {
          result = l2;
        } else if ( n1 != null && n2 != null ) {
          result =
              ( Double.compare( n1.doubleValue(),
                                n2.doubleValue() ) == ( isMin ? 1 : -1 ) ) ? l2
                                                                           : l1;
        }
      }
    }
    try {
      if ( result instanceof TimeVaryingMap ) {
        return result;
      }
      if ( l1 != null && l2 != null && result != null ) {
        Class< ? > cls1 = l1.getClass();
        Class< ? > cls2 = l2.getClass();
        Object x =
            Expression.evaluate( result,
                                 ClassUtils.dominantTypeClass( cls1, cls2 ),
                                 false );
        if ( x == null ) x = result;
        // TODO: type casting this w/ V1 assume that it is the dominant class
        return (L)x;
      }
      return (L)result;
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    return null;
  }

  public static TimeVaryingMap< ? > getTimeline( Object o ) {
    TimeVaryingMap< ? > tvm = null;
    tvm = tryToGetTimelineQuick( o );
    if ( tvm != null ) {
      return tvm;
    }
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    return tvm;
  }

  public static Pair< Object, TimeVaryingMap< ? > >
         objectOrTimeline( Object o ) {
    TimeVaryingMap< ? > tvm = null;
    tvm = tryToGetTimelineQuick( o );
    Object n = tryToGetObjectQuick( o );
    if ( tvm != null || n != null ) {
      return new Pair< Object, TimeVaryingMap< ? > >( n, tvm );
    }
    // if ( n != null ) {
    // new Pair< Object, TimeVaryingMap<?> >( n, tvm );
    // }
    try {
      n = Expression.evaluate( o, null, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( n == null ) {
      try {
        tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
      } catch ( Throwable e ) {
        // ignore
      }
    }
    Pair< Object, TimeVaryingMap< ? > > p =
        new Pair< Object, TimeVaryingMap< ? > >( n, tvm );
    return p;
  }

  public static Pair< Boolean, TimeVaryingMap< ? > >
         booleanOrTimeline( Object o ) {
    TimeVaryingMap< ? > tvm = null;
    tvm = tryToGetTimelineQuick( o );
    Boolean n = tryToGetBooleanQuick( o );
    if ( tvm != null || n != null ) {
      return new Pair< Boolean, TimeVaryingMap< ? > >( n, tvm );
    }
    // if ( n != null ) {
    // new Pair< Boolean, TimeVaryingMap<?> >( n, tvm );
    // }
    try {
      n = Expression.evaluate( o, Boolean.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    // if ( n == null ) {
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    // }
    Pair< Boolean, TimeVaryingMap< ? > > p =
        new Pair< Boolean, TimeVaryingMap< ? > >( n, tvm );
    return p;
  }

  public static Pair< Number, TimeVaryingMap< ? > >
         numberOrTimeline( Object o ) {
    Number n = tryToGetNumberQuick( o );
    TimeVaryingMap< ? > tvm = null;
    tvm = tryToGetTimelineQuick( o );
    if ( tvm != null || n != null ) {
      return new Pair< Number, TimeVaryingMap< ? > >( n, tvm );
    }
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( n == null && o instanceof String ) {
      n = toNumber( o, true );
    }
    if ( n == null ) {
      try {
        n = Expression.evaluate( o, Number.class, false );
      } catch ( Throwable e ) {
        // ignore
      }
    }
    // if ( n != null ) {
    // new Pair< Number, TimeVaryingMap<?> >( n, tvm );
    // }
    //// if ( n == null ) {
    //// }
    Pair< Number, TimeVaryingMap< ? > > p =
        new Pair< Number, TimeVaryingMap< ? > >( n, tvm );
    return p;
  }

  public static Pair< Object, TimeVaryingMap< ? > >
        numberOrTimelineOrDistribution( Object o ) {
    Number n = tryToGetNumberQuick( o );
    TimeVaryingMap< ? > tvm = tryToGetTimelineQuick( o );
    Distribution<?> dist = tryToGetDistributionQuick( o );
    if ( tvm != null || n != null || dist != null ) {
      if ( dist != null ) {
        return new Pair<Object, TimeVaryingMap<?>>( dist, tvm );
      }
      return new Pair<Object, TimeVaryingMap<?>>( n, tvm );
    }
    try {
      dist = Expression.evaluate( o, Distribution.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( dist == null && n == null && o instanceof String ) {
      n = toNumber( o, true );
    }
    if ( dist == null && n == null ) {
      try {
        n = Expression.evaluate( o, Number.class, false );
      } catch ( Throwable e ) {
        // ignore
      }
    }
    // if ( n != null ) {
    // new Pair< Number, TimeVaryingMap<?> >( n, tvm );
    // }
    //// if ( n == null ) {
    //// }

    if ( dist != null ) {
      return new Pair<Object, TimeVaryingMap<?>>( dist, tvm );
    }
    return new Pair<Object, TimeVaryingMap<?>>( n, tvm );
  }

  public static Pair< Object, TimeVaryingMap< ? > >
        booleanOrTimelineOrDistribution( Object o ) {
    Boolean n = tryToGetBooleanQuick( o );
    TimeVaryingMap< ? > tvm = tryToGetTimelineQuick( o );
    Distribution<?> dist = tryToGetDistributionQuick( o );
    if ( tvm != null || n != null || dist != null ) {
      if ( dist != null ) {
        return new Pair<Object, TimeVaryingMap<?>>( dist, tvm );
      }
      return new Pair<Object, TimeVaryingMap<?>>( n, tvm );
    }
    try {
      dist = Expression.evaluate( o, Distribution.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( dist == null && n == null && o instanceof String ) {
      n = Utils.isTrue( o, true );
    }
    if ( dist == null && n == null ) {
      try {
        n = Expression.evaluate( o, Boolean.class, false );
      } catch ( Throwable e ) {
        // ignore
      }
    }
    // if ( n != null ) {
    // new Pair< Number, TimeVaryingMap<?> >( n, tvm );
    // }
    //// if ( n == null ) {
    //// }

    if ( dist != null ) {
      return new Pair<Object, TimeVaryingMap<?>>( dist, tvm );
    }
    return new Pair<Object, TimeVaryingMap<?>>( n, tvm );
  }


  protected static TimeVaryingMap< ? > tryToGetTimelineQuick( Object o ) {
    if ( o == null ) return null;
    if ( o instanceof TimeVaryingMap ) return (TimeVaryingMap)o;
    if ( o instanceof Expression ) o = ( (Expression< ? >)o ).expression;
    if ( o instanceof Parameter ) o =
        ( (Parameter< ? >)o ).getValueNoPropagate();
    if ( o instanceof TimeVaryingMap ) return (TimeVaryingMap)o;
    return null;
  }

  protected static Number tryToGetNumberQuick( Object o ) {
    if ( o == null ) return null;
    if ( o instanceof Number ) return (Number)o;
    if ( o instanceof Expression ) o = ( (Expression< ? >)o ).expression;
    if ( o instanceof Parameter ) o =
        ( (Parameter< ? >)o ).getValueNoPropagate();
    if ( o instanceof Number ) return (Number)o;
    return null;
  }

  protected static Boolean tryToGetBooleanQuick( Object o ) {
    if ( o == null ) return null;
    if ( o instanceof Boolean ) return (Boolean)o;
    if ( o instanceof Expression ) o = ( (Expression< ? >)o ).expression;
    if ( o instanceof Parameter ) o =
        ( (Parameter< ? >)o ).getValueNoPropagate();
    if ( o instanceof Boolean ) return (Boolean)o;
    return null;
  }

  protected static Object tryToGetObjectQuick( Object o ) {
    if ( o == null ) return null;
    if ( o instanceof Expression ) o = ( (Expression< ? >)o ).expression;
    if ( o instanceof Parameter ) o =
        ( (Parameter< ? >)o ).getValueNoPropagate();
    return o;
  }

  public static Distribution<?> tryToGetDistributionQuick( Object o ) {
    if ( o == null ) return null;
    if ( o instanceof Distribution ) return (Distribution)o;
    if ( o instanceof Expression ) o = ( (Expression< ? >)o ).expression;
    if ( o instanceof Parameter ) o =
            ( (Parameter< ? >)o ).getValueNoPropagate();
    if ( o instanceof Distribution ) return (Distribution)o;
    return null;
  }



  // public static <V1, V2> V1 times( V1 o1, V2 o2 ) {
  public static < V1, V2 > V1 times( V1 o1, V2 o2 )
                                                    throws IllegalAccessException,
                                                    InvocationTargetException,
                                                    InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    Number n1 = null;
    Number n2 = null;
    TimeVaryingMap< ? > map1 = null;
    TimeVaryingMap< ? > map2 = null;
    Distribution<?> d1 = null;
    Distribution<?> d2 = null;

    Object arg1 = null;
    Object arg2 = null;

    Pair< Object, TimeVaryingMap< ? > > p1 = numberOrTimelineOrDistribution( o1 );
    map1 = p1.second;
    if ( p1.first instanceof Distribution ) {
      d1 = (Distribution)p1.first;
      arg1 = d1;
    } else if ( p1.first instanceof Number ) {
      n1 = (Number)p1.first;
      arg1 = map1 == null ? n1 : map1;
    }
    if ( arg1 == null ) arg1 = o1;
    Pair< Object, TimeVaryingMap< ? > > p2 = numberOrTimelineOrDistribution( o2 );
    map2 = p2.second;
    if ( p2.first instanceof Distribution ) {
      d2 = (Distribution)p2.first;
      arg2 = d2;
    } else if ( p2.first instanceof Number ) {
      n2 = (Number)p2.first;
      arg2 = map2 == null ? n2 : map2;
    }
    if ( arg2 == null) arg2 = o2;


    Object result = null;
//    Pair< Number, TimeVaryingMap< ? > > p1 = numberOrTimeline( o1 );
//    n1 = p1.first instanceof Number ? (Number)p1.first : null;
//    map1 = p1.second;

    if ( map1 != null ) {
      result = (V1)times( map1, o2 );
    } else {
//      Pair< Number, TimeVaryingMap< ? > > p2 = numberOrTimeline( o2 );
//      n2 = p2.first;
//      map2 = p2.second;

      if ( map2 != null ) {
        result = (V1)times( o1, map2 );
      }
    }

    if ( n1 != null && n2 != null ) {
      if ( Infinity.isEqual( n1 ) ) {
        try {
          if ( Zero.isEqual( n2 ) ) {
            result = One.forClass( o1.getClass(), n1.getClass() );
          } else if ( Utils.isNegative( n2 ) ) {
            result = NegativeInfinity.forClass( o1.getClass(), n1.getClass() );
          } else {
            result = Infinity.forClass( o1.getClass(), n1.getClass() );
          }
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      } else if ( NegativeInfinity.isEqual( n1 ) ) {
        try {
          if ( Zero.isEqual( n2 ) ) {
            result = NegativeOne.forClass( o1.getClass(), n1.getClass() );
          } else if ( Utils.isNegative( n2 ) ) {
            result = Infinity.forClass( o1.getClass(), n1.getClass() );
          } else {
            result = NegativeInfinity.forClass( o1.getClass(), n1.getClass() );
          }
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      } else if ( Zero.isEqual( n1 ) ) {
        try {
          if ( Infinity.isEqual( n2 ) ) {
            result = One.forClass( o1.getClass(), n1.getClass() );
          } else if ( NegativeInfinity.isEqual( n2 ) ) {
            result = NegativeOne.forClass( o1.getClass(), n1.getClass() );
          } else {
            result = Zero.forClass( o1.getClass(), n1.getClass() );
          }
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
        // TODO -- other types, like BigDecimal
      } else if ( n1 instanceof Double || n2 instanceof Double ) {
        // result = ((Double)n1.doubleValue()) * ((Double)n2.doubleValue());
        result = (Double)gov.nasa.jpl.ae.util.Math.times( n1.doubleValue(),
                                                          n2.doubleValue() );
      } else if ( n1 instanceof Float || n2 instanceof Float ) {
        // result = ((Float)n1.floatValue()) * ((Float)n2.floatValue());
        result = (Float)gov.nasa.jpl.ae.util.Math.times( n1.floatValue(),
                                                         n2.floatValue() );
      } else if ( n1 instanceof Long || n2 instanceof Long ) {
        // result = ((Long)n1.longValue()) * ((Long)n2.longValue());
        result = (Long)gov.nasa.jpl.ae.util.Math.times( n1.longValue(),
                                                        n2.longValue() );
        // } else if ( n1 instanceof Integer || n2 instanceof Integer ) {
        // // result = ((Integer)n1.intValue()) * ((Integer)n2.intValue());
        // result = (Integer)times(n1.intValue(), n2.intValue());
      } else {
        // result = ((Integer)n1.intValue()) * ((Integer)n2.intValue());
        result = (Integer)gov.nasa.jpl.ae.util.Math.times( n1.intValue(),
                                                           n2.intValue() );
      }
    }

    if ( d1 != null || d2 != null ) {
      result = DistributionHelper.times( arg1, arg2 );
    }

    try {
      if ( o1 != null && o2 != null && result != null ) {
        Class< ? > cls1 = o1.getClass();
        Class< ? > cls2 = o2.getClass();
        // TOOD -- what if cls1 is not a Number?
        Object x =
            Expression.evaluate( result,
                                 ClassUtils.dominantTypeClass( cls1, cls2 ),
                                 false );
        if ( x == null ) x = result;
        // TODO: type casting this w/ V1 assume that it is the dominant class
        return (V1)x;
      }
      return (V1)result;
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    return null;
  }

  public static < V1, V2 > V1 divide( V1 o1, V2 o2 )
                                                     throws IllegalAccessException,
                                                     InvocationTargetException,
                                                     InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    Number n1 = null;
    Number n2 = null;
    TimeVaryingMap< ? > map1 = null;
    TimeVaryingMap< ? > map2 = null;
    Distribution<?> d1 = null;
    Distribution<?> d2 = null;

    Object arg1 = null;
    Object arg2 = null;

    Pair< Object, TimeVaryingMap< ? > > p1 = numberOrTimelineOrDistribution( o1 );
    map1 = p1.second;
    if ( p1.first instanceof Distribution ) {
      d1 = (Distribution)p1.first;
      arg1 = d1;
    } else if ( p1.first instanceof Number ) {
      n1 = (Number)p1.first;
      arg1 = map1 == null ? n1 : map1;
    }
    if ( arg1 == null ) arg1 = o1;
    Pair< Object, TimeVaryingMap< ? > > p2 = numberOrTimelineOrDistribution( o2 );
    map2 = p2.second;
    if ( p2.first instanceof Distribution ) {
      d2 = (Distribution)p2.first;
      arg2 = d2;
    } else if ( p2.first instanceof Number ) {
      n2 = (Number)p2.first;
      arg2 = map2 == null ? n2 : map2;
    }
    if ( arg2 == null) arg2 = o2;

    Object result = null;
    if ( map1 != null ) {
      result = (V1)divide( map1, o2 );
    } else {
      if ( map2 != null ) {
        result = (V1)divide( o1, map2 );
      }
    }

    if ( n1 != null && n2 != null ) {
      if ( Infinity.isEqual( n1 ) ) {
        try {
          if ( Infinity.isEqual( n2 ) ) {
            result = One.forClass( o1.getClass(), n1.getClass() );
          } else if ( NegativeInfinity.isEqual( n2 ) ) {
            result = NegativeOne.forClass( o1.getClass(), n1.getClass() );
          } else if ( Utils.isNegative( n2 ) ) {
            result = NegativeInfinity.forClass( o1.getClass(), n1.getClass() );
          } else {
            result = Infinity.forClass( o1.getClass(), n1.getClass() );
          }
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      } else if ( NegativeInfinity.isEqual( n1 ) ) {
        try {
          if ( Infinity.isEqual( n2 ) ) {
            result = NegativeOne.forClass( o1.getClass(), n1.getClass() );
          } else if ( NegativeInfinity.isEqual( n2 ) ) {
            result = One.forClass( o1.getClass(), n1.getClass() );
          } else if ( Utils.isNegative( n2 ) ) {
            result = Infinity.forClass( o1.getClass(), n1.getClass() );
          } else {
            result = NegativeInfinity.forClass( o1.getClass(), n1.getClass() );
          }
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      } else if ( Zero.isEqual( n1 ) ) {
        try {
          result = Zero.forClass( o1.getClass(), n1.getClass() );
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      } else if ( Zero.isEqual( n2 ) ) {
        try {
          if ( Utils.isNegative( n1 ) ) {
            result = NegativeInfinity.forClass( o1.getClass(), n1.getClass() );
          } else {
            result = Infinity.forClass( o1.getClass(), n1.getClass() );
          }
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
        // TODO -- other types, like BigDecimal
      } else if ( n1 instanceof Double || n2 instanceof Double ) {
        // result = ((Double)n1.doubleValue()) / ((Double)n2.doubleValue());
        result =
            (Double)gov.nasa.jpl.ae.util.Math.dividedBy( n1.doubleValue(),
                                                         n2.doubleValue() );
      } else if ( n1 instanceof Float || n2 instanceof Float ) {
        // result = ((Float)n1.floatValue()) / ((Float)n2.floatValue());
        result = (Float)gov.nasa.jpl.ae.util.Math.dividedBy( n1.floatValue(),
                                                             n2.floatValue() );
      } else if ( n1 instanceof Long || n2 instanceof Long ) {
        // result = ((Long)n1.longValue()) / ((Long)n2.longValue());
        result = (Long)gov.nasa.jpl.ae.util.Math.dividedBy( n1.longValue(),
                                                            n2.longValue() );
        // } else if ( n1 instanceof Integer || n2 instanceof Integer ) {
        //// result = ((Integer)n1.intValue()) / ((Integer)n2.intValue());
        // result = (Integer)dividedBy( n1.intValue(), n2.intValue() );
      } else {
        // result = ((Integer)n1.intValue()) / ((Integer)n2.intValue());
        result = (Integer)gov.nasa.jpl.ae.util.Math.dividedBy( n1.intValue(),
                                                               n2.intValue() );
      }
    }

    if ( d1 != null || d2 != null ) {
      result = DistributionHelper.divide( arg1, arg2 );
    }

    // }
    // }
    try {
      if ( o1 != null && o2 != null && result != null ) {
        Class< ? > cls1 = o1.getClass();
        Class< ? > cls2 = o2.getClass();
        Object x =
            Expression.evaluate( result,
                                 ClassUtils.dominantTypeClass( cls1, cls2 ),
                                 false );
        if ( x == null ) x = result;
        // TODO: type casting this w/ V1 assume that it is the dominant class
        //System.out.println("divide(r1,r2) = " + x + "= divide(" + MoreToString.Helper.toLongString(o1) + ", " + MoreToString.Helper.toLongString(o1) + ")");
        return (V1)x;
      }
      //System.out.println("divide(r1,r2) = " + result + "= divide(" + MoreToString.Helper.toLongString(o1) + ", " + MoreToString.Helper.toLongString(o1) + ")");
      return (V1)result;
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    //System.out.println("divide(r1,r2) = null = divide(" + MoreToString.Helper.toLongString(o1) + ", " + MoreToString.Helper.toLongString(o1) + ")");
    return null;
  }

  public static < V1, V2 > V1 pow( V1 o1, V2 o2 ) throws IllegalAccessException,
                                                  InvocationTargetException,
                                                  InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    Object result = null;
    Number n1 = null;
    Number n2 = null;
    TimeVaryingMap< ? > map1 = null;
    TimeVaryingMap< ? > map2 = null;

    Pair< Number, TimeVaryingMap< ? > > p1 = numberOrTimeline( o1 );
    n1 = p1.first;
    map1 = p1.second;

    if ( map1 != null ) {
      result = (V1)pow( map1, o2 );
    } else {
      Pair< Number, TimeVaryingMap< ? > > p2 = numberOrTimeline( o2 );
      n2 = p2.first;
      map2 = p2.second;

      if ( map2 != null ) {
        result = (V1)pow( o1, (TimeVaryingMap< V1 >)map2 );
      }
    }
    // TimeVaryingMap<?> map = null;
    // try {
    // map = Expression.evaluate( o1, TimeVaryingMap.class, false );
    // } catch ( ClassCastException e ) {
    // //ignore
    // }
    // if ( map != null ) result = pow( map, o2 );
    // else {
    // try {
    // map = Expression.evaluate( o2, TimeVaryingMap.class, false );
    // } catch ( ClassCastException e ) {
    // //ignore
    // }
    // if ( map != null ) result = pow( o1, map );
    // else {
    // Number n1 = null;
    // Number n2 = null;
    // try {
    // n1 = Expression.evaluate( o1, Number.class, false );
    // n2 = Expression.evaluate( o2, Number.class, false );
    // } catch ( Throwable e ) {
    // // ignore
    // }
    if ( n1 != null && n2 != null ) {
      if ( Infinity.isEqual( n1 ) ) {
        try {
          if ( Zero.isEqual( n2 ) ) {
            result = One.forClass( o1.getClass(), n1.getClass() );
          } else if ( Utils.isNegative( n2 ) ) {
            result = Zero.forClass( o1.getClass(), n1.getClass() );
          } else {
            result = Infinity.forClass( o1.getClass(), n1.getClass() );
          }
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      } else if ( NegativeInfinity.isEqual( n1 ) ) {
        try {
          if ( Utils.isNegative( n2 ) ) {
            result = Zero.forClass( o1.getClass(), n1.getClass() );
          } else {
            result = null;
          }
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      } else if ( Zero.isEqual( n1 ) ) {
        try {
          result = Zero.forClass( o1.getClass(), n1.getClass() );
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      }
      if ( n1 != null && n2 != null && result == null ) {
        // TODO -- other types, like BigDecimal
        // TODO -- Need to add a gov.nasa.jpl.ae.util.Math.pow() to handle
        // overflow.
        try {
          result = (Double)Math.pow( n1.doubleValue(), n2.doubleValue() );
          if ( n1 instanceof Double
               || n2 instanceof Double ) {} else if ( n1 instanceof Float
                                                      || n2 instanceof Float ) {
            result = (Float)( (Double)result ).floatValue();
          } else if ( n1 instanceof Long || n2 instanceof Long ) {
            result = (Long)( (Double)result ).longValue();
          } else {
            result = (Integer)( (Double)result ).intValue();
          }
          return (V1)result;
        } catch ( ClassCastException e ) {
          e.printStackTrace();
        }
      }
      // return (V1)result;
    }
    // }
    // }
    try {
      if ( o1 != null && o2 != null && result != null ) {
        Class< ? > cls1 = o1.getClass();
        Class< ? > cls2 = o2.getClass();
        // TOOD -- what if cls1 is not a Number?
        Object x =
            Expression.evaluate( result,
                                 ClassUtils.dominantTypeClass( cls1, cls2 ),
                                 false );
        if ( x == null ) x = result;
        // TODO: type casting this w/ V1 assume that it is the dominant class
        return (V1)x;
      }
      return (V1)result;
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    return null;
  }

  public static < V1, V2 > V1 minus( V1 o1, V2 o2 ) throws ClassCastException,
                                                    IllegalAccessException,
                                                    InvocationTargetException,
                                                    InstantiationException {
    // basic definition works for numbers, but not for strings
    if (o1 instanceof String || o2 instanceof String) {
      try {
        String result = minusSuffix(o1.toString(), o2.toString());
      
        Class< ? > cls1 = o1.getClass();
        Class< ? > cls2 = o2.getClass();
        Object x =
            Expression.evaluate( result,
                                 ClassUtils.dominantTypeClass( cls1, cls2 ),
                                 false );
        if ( x == null ) x = result;
        return (V1)x; // even if x is null; evaluate will try to cast, so there's nothing more to do, and the operation fails.
      } catch ( ClassCastException e ) {
        e.printStackTrace();
      }
    }
    return plus( o1, times( o2, -1 ) );
  }
  
  public static String minusSuffix( String s1, String s2 ) {
    if (s1.endsWith( s2 )) {
      String s =  s1.substring( 0, s1.length() - s2.length() );
//      System.out.println("minusSuffix(" + s1 + ", " + s2 + ") = " + s);
      return s;
    } else {
      return s1;
    }
  }
  
  public static String minusPrefix( String s1, String s2 ) {
    if (s1.startsWith( s2 )) {
      String s = s1.substring( s2.length() );
//      System.out.println("minusPrefix(" + s1 + ", " + s2 + ") = " + s);
      return s;
    } else {
      return s1;
    }
  }

  public static < T, TT > T add( Expression< T > o1,
                                 Expression< TT > o2 ) throws IllegalAccessException,
                                                       InvocationTargetException,
                                                       InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    T r1 = Expression.evaluateDeep(o1, null, false, false);
    TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    return plus( r1, r2 );
  }

  public static < T, TT > T
         subtract( Expression< T > o1,
                   Expression< TT > o2 ) throws IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    T r1 = Expression.evaluateDeep(o1, null, false, false);
    TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    return minus( r1, r2 );
  }

  public static < T, TT > String
         subtractSuffix( Expression< T > o1,
                   Expression< TT > o2 ) throws IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    T r1 = Expression.evaluateDeep(o1, null, false, false);
    TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    if ( r1 instanceof List && ( (List)r1 ).size() == 1 ) {
      r1 = (T)((List)r1).get( 0 );
    }
    if ( r2 instanceof List && ( (List)r2 ).size() == 1 ) {
      r2 = (TT)((List)r2).get( 0 );
    }
    return minusSuffix( r1.toString(), r2.toString() );
  }

  public static < T, TT > String
         subtractPrefix( Expression< T > o1,
                   Expression< TT > o2 ) throws IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    T r1 = Expression.evaluateDeep(o1, null, false, false);
    TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    if ( r1 instanceof List && ( (List)r1 ).size() == 1 ) {
      r1 = (T)((List)r1).get( 0 );
    }
    if ( r2 instanceof List && ( (List)r2 ).size() == 1 ) {
      r2 = (TT)((List)r2).get( 0 );
    }
    return minusPrefix( r1.toString(), r2.toString() );
  }

  public static < T, TT > T
         times( Expression< T > o1,
                Expression< TT > o2 ) throws IllegalAccessException,
                                      InvocationTargetException,
                                      InstantiationException {
    if ( o1 == null || o2 == null ) return null;
//    T r1 = (T)o1.evaluate( false );
//    TT r2 = (TT)o2.evaluate( false );
      T r1 = Expression.evaluateDeep(o1, null, false, false);
        TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    return times( r1, r2 );
  }

  public static < T, TT > T
         divide( Expression< T > o1,
                 Expression< TT > o2 ) throws IllegalAccessException,
                                       InvocationTargetException,
                                       InstantiationException {
    T result = null;
    if ( o1 != null && o2 != null ) {
//      T r1 = (T)o1.evaluate( false );
//      TT r2 = (TT)o2.evaluate( false );
        T r1 = Expression.evaluateDeep(o1, null, false, false);
        TT r2 = Expression.evaluateDeep(o2, null, false, false);
      if ( r1 != null && r2 != null ) {
        result = divide( r1, r2 );
      }
    }
    //System.out.println("divide(o1,o2) = " + result + "= divide(" + MoreToString.Helper.toLongString(o1) + ", " + MoreToString.Helper.toLongString(o1) + ")");
    return result;
  }

  public static < T, TT > T pow( Expression< T > o1,
                                 Expression< TT > o2 ) throws IllegalAccessException,
                                                       InvocationTargetException,
                                                       InstantiationException {
    if ( o1 == null || o2 == null ) return null;
    T r1 = Expression.evaluateDeep(o1, null, false, false);
    TT r2 = Expression.evaluateDeep(o2, null, false, false);
    if ( r1 == null || r2 == null ) return null;
    return pow( r1, r2 );
  }

  public static class Identity< T > extends Unary< T, T > {
    public Identity( Expression< T > o ) {
      super( o, "identity" );
      setMonotonic( true );
    }

    public Identity( Identity< T > m ) {
      super( m );
    }

    public Identity< T > clone() {
      return new Identity< T >( this );
    }

    @Override
    public < T > T pickValue( Variable< T > variable ) {
      return variable.getValue( false );
    }
  }

  public static < T > T identity( T t ) {
    return t;
  }

  public static Expression< ? > identity( Expression< ? > e ) {
    return e;
  }

  public static class Negative< T > extends Unary< T, T > {
    public Negative( Expression< T > o ) {
      super( o, "negative" );
      // functionCall.
      setMonotonic( true );
    }

    public Negative( Negative< T > m ) {
      super( m );
    }

    public Negative< T > clone() {
      return new Negative< T >( this );
    }

    // TODO -- handle cast errors
    @Override
    public < T > T pickValue( Variable< T > variable ) {
      Object arg = this.getArgument( 0 );// ((FunctionCall)this.expression).getArgument(
                                         // 0 );
      if ( arg == variable ) {
        try {
          return (T)negative( variable );
        } catch ( IllegalAccessException e ) {
          e.printStackTrace();
        } catch ( InvocationTargetException e ) {
          e.printStackTrace();
        } catch ( InstantiationException e ) {
          e.printStackTrace();
        }
      }
      return pickValueE( variable, arg );
      // else if ( arg instanceof Suggester ) {
      // return (T)(negative(((Number)((Suggester)arg).pickValue( variable ))));
      // } else if ( arg instanceof Expression && ((Expression)arg).expression
      // instanceof Suggester ) {
      // return
      // (T)(negative((Number)((Suggester)((Expression)arg).expression).pickValue(
      // variable )));
      // }
      // return null;
    }
  }

  public static < T > Object negative( T v ) throws IllegalAccessException,
                                             InvocationTargetException,
                                             InstantiationException {
    if ( v instanceof Number ) {
      return negative( (Number)v );
    }
    if ( v instanceof Variable ) {
      return negative( (Variable< T >)v );
    }
    
    // If the string is a number just toggle a minus in front of the string.
    if ( v instanceof String ) {
      String s = (String)v;
      return negative( s );
    }
    
    if ( v instanceof TimeVaryingMap ) {
      return ( (TimeVaryingMap)v ).negative();
    }

    if ( v instanceof Distribution ) {
        return negative( (Distribution)v );
    }

    Debug.error( true, true, "Unknown type for negative(" + v + ")" );
    return null;
  }

  public static < T > Object
         negative( Variable< T > v ) throws IllegalAccessException,
                                     InvocationTargetException,
                                     InstantiationException {
    T r = v.getValue( false );
    return negative( r );
    // if ( r instanceof Number ) {
    // return negative( (Number)r );
    // }
    // return null;
  }

  public static java.lang.Number negative( String v ) {
    Long i = Utils.toLong( v );
    Double n = Utils.toDouble( v );
    if ( i == null && n == null ) {
      Debug.error( true, false, "Tried to apply negative() to a string that is not a number: " + v );
      return null;
    }
    if ( i != null && n != null && i.doubleValue() == n.doubleValue() ) {
      i = -i;
      return i;
    }
    if ( n != null ) {
      n = -n;
      return n;
    }
    i = -i;
    return i;
  }

  public static Number negative( Number n ) {
    if ( n == null ) return null;
    Number result = null;
    if ( n.getClass().isAssignableFrom( java.lang.Double.class ) ) {
      result = ( (Double)n ) * -1.0;
    } else if ( n.getClass().isAssignableFrom( java.lang.Integer.class ) ) {
      result = ( (Integer)n ) * -1;
    } else if ( n.getClass().isAssignableFrom( java.lang.Long.class ) ) {
      result = ( (Long)n ) * -1;
    } else if ( n.getClass().isAssignableFrom( java.lang.Float.class ) ) {
      result = ( (Float)n ) * -1.0;
    } else if ( n.getClass().isAssignableFrom( java.lang.Short.class ) ) {
      result = ( (Short)n ) * -1;
    } else if ( n.getClass().isAssignableFrom( BigDecimal.class ) ) {
      result = ( (BigDecimal)n ).negate();
    } else if ( n.getClass().isAssignableFrom( BigInteger.class ) ) {
      result = ( (BigInteger)n ).negate();
    }
    return result;
  }

  public static < T > Object
         negative( Expression< T > o ) throws IllegalAccessException,
                                       InvocationTargetException,
                                       InstantiationException {
    if ( o == null ) return null;
    T r = null;
    try {
      r = Expression.evaluateDeep(o, null, false, false);
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }

    if ( r instanceof Parameter ) {
      r = ( (Parameter< T >)r ).getValue( true );
    }
    if ( r instanceof Number ) {
      return negative( (Number)r );
    }
    if ( r instanceof TimeVaryingMap ) {
      return ( (TimeVaryingMap< T >)r ).negative();
      // return negative( (TimeVaryingMap<T>)r );
    }
//    if ( r instanceof Distribution ) {
//      return negative((Distribution)r);
//    }
    return null;
  }

  public static Distribution negative ( Distribution d ) {
    return DistributionHelper.negative(d);
  }

  public static class Neg< T > extends Unary< T, T > {
    public Neg( Expression< T > o ) {
      super( o, "neg" );
      // functionCall.
      setMonotonic( true );
    }

    public Neg( Neg< T > m ) {
      super( m );
    }

    public Neg< T > clone() {
      return new Neg< T >( this );
    }

    // TODO -- handle cast errors
    @Override
    public < T > T pickValue( Variable< T > variable ) {
      Object arg = this.getArgument( 0 );// ((FunctionCall)this.expression).getArgument(
                                         // 0 );
      if ( arg == variable ) {
        try {
          return (T)negative( variable );
        } catch ( IllegalAccessException e ) {
          e.printStackTrace();
        } catch ( InvocationTargetException e ) {
          e.printStackTrace();
        } catch ( InstantiationException e ) {
          e.printStackTrace();
        }

      }

      return pickValueE( variable, arg );
      // else if ( arg instanceof Suggester ) {
      // return (T)(negative(((Number)((Suggester)arg).pickValue( variable ))));
      // } else if ( arg instanceof Expression && ((Expression)arg).expression
      // instanceof Suggester ) {
      // return
      // (T)(negative((Number)((Suggester)((Expression)arg).expression).pickValue(
      // variable )));
      // }
      // return null;
    }
  }

  public static < T > java.lang.Number neg( T v ) {
    if ( v instanceof Number ) {
      return neg( (Number)v );
    }
    if ( v instanceof Variable ) {
      return neg( (Variable< T >)v );
    }
    if ( v instanceof String ) {
      return neg( (Number)v );
    }
    return null;
  }

  public static < T > java.lang.Number neg( Variable< T > v ) {
    T r = v.getValue( false );
    return neg( r );
    // if ( r instanceof Number ) {
    // return negative( (Number)r );
    // }
    // return null;
  }

  public static java.lang.Number neg( String v ) {
    Long i = Utils.toLong( v );
    Double n = Utils.toDouble( v );
    if ( i == null && n == null ) {
      // TODO -- ERROR!
      return null;
    }
    if ( i != null && n != null && i.doubleValue() == n.doubleValue() ) {
      i = -i;
      return i;
    }
    if ( n != null ) {
      n = -n;
      return n;
    }
    i = -i;
    return i;
  }

  public static Number neg( Number n ) {
    if ( n == null ) return null;
    Number result = null;
    if ( n.getClass().isAssignableFrom( java.lang.Double.class ) ) {
      result = ( (Double)n ) * -1.0;
    } else if ( n.getClass().isAssignableFrom( java.lang.Integer.class ) ) {
      result = ( (Integer)n ) * -1;
    }
    return result;
  }

  public static < T > java.lang.Number
         neg( Expression< T > o ) throws IllegalAccessException,
                                  InvocationTargetException,
                                  InstantiationException {
    if ( o == null ) return null;
    T r = null;
    try {
      r = Expression.evaluateDeep(o, null, false, false);
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    if ( r instanceof Number ) {
      return neg( (Number)r );
    }
    return null;
  }


  public static class P extends Binary<Boolean, Boolean> {
    //Integer maxSamples = null;//FunctionOfDistributions.maxSamplesDefault;

    public P( Variable<Boolean> o ) {
      super( o, null, "p" );
    }

    public P( Expression<Boolean> o1 ) {
      super( o1, null, "p" );
    }

    public P( Object o1 ) {
      super( o1, null, "p" );
    }

    public P( P m ) {
      super( m );
    }


    public P( Variable<Boolean> o, Object unsampledDefault ) {
      super( o, unsampledDefault, "p" );
    }

    public P( Expression<Boolean> o1, Object unsampledDefault ) {
      super( o1, forceExpression( unsampledDefault ), "p" );
    }

    public P( Object o1, Object unsampledDefault ) {
      super( o1, unsampledDefault, "p" );
    }


      //    public P( Variable<Boolean> o, Object maxSamples ) {
//      super( o, "p" );
//      //this.maxSamples = maxSamples;
//      init( maxSamples );
//    }
//
//    public P( Expression<Boolean> o1, Object maxSamples )  {
//      super( o1, "p" );
//      //this.maxSamples = maxSamples;
//      init( maxSamples );
//    }
//
//    public P( Object o1, Object maxSamples ) {
//      super( o1, "p" );
//      //this.maxSamples = maxSamples;
//      init( maxSamples );
//    }
//
//    public P( P m, Object maxSamples ) {
//      super( m );
//      //this.maxSamples = maxSamples;
//      init( maxSamples );
//    }

//    protected void init( Object maxSamples ) {
//      if ( maxSamples == null ) return;
//      if ( getArguments() != null && getArguments().size() > 0 && getArgument( 0 ) != null ) {
//        FunctionOfDistributions fod = null;
//        try {
//          fod = Expression.evaluate(getArgument(0), FunctionOfDistributions.class, true);
//          Long maxS = Expression.evaluate(maxSamples, Long.class, true);
//          if ( fod != null && maxS != null ) {
//            fod.setMaxSamples( maxS.intValue() );
//          }
//        } catch ( Throwable e ) {
//        }
//      }
//    }

    @Override
    public Domain<?> calculateDomain( boolean propagate, Set<HasDomain> seen ) {
      return new DoubleDomain( 0.0, 1.0 );
      // TODO
      /*
      if ( arguments == null || arguments.isEmpty() ) {
        return null;
      }
      Object arg = this.getArgument( 0 );
      if ( arg == null ) {
        return null;
      }
      if ( arg instanceof )
      Domain<Object> d = DomainHelper.getDomain( arg );
      Object[] args = null;
      try {
        args = evaluateArgs( true );
      } catch ( Throwable t ) {
        return null;
      }
      if ( args != null && args.length > 0 ) {
        arg = args[0];
        if ( arg != null ) {
          return DomainHelper.getDomain( arg );
        }
      }
      */
    }

  }

  public static Double p(Expression<Boolean> exp) {
      return p(exp, null);
  }
  public static Double p(Expression<Boolean> exp, Expression<Boolean> unsampledDefault) {
    return p(exp, (Object)unsampledDefault);
  }
  public static Double p(Expression<Boolean> exp, Object unsampledDefault) {
    System.out.println("calling p(" + exp + ")");
    if ( exp == null ) return null;
    return p((Object)exp, unsampledDefault);
//    switch (exp.form) {
//        case Value:
//            return p((Object)exp.expression);
//        case Function:
//            return p( (Call)exp.expression );
//        case Constructor:
//            return p( (Call)exp.expression );
//        case Parameter:
//            return p( ((Parameter)exp.expression).getValueNoPropagate() );
//    }
  }

//  public static Double p(Call fc) {
//    try {
//      Object r = fc.evaluate( true );
//      if ( r instanceof Distribution ) {
//        Distribution d = (Distribution)r;
//        if ( Boolean.class.isAssignableFrom( d.getType() ) ) {
//          Double p = d.probability( true );
//        }
//      } else return p(r);
//    } catch ( Throwable t ) {
//    }
//    return null;
//  }

  public static Double p( Object o ) {
    return p(o, null);
  }
  public static Double p( Object o, Object unsampledDefault ) {
    //try {
    System.out.println("calling p(" + o + ")");
      if ( o instanceof Distribution ) {
          Distribution d = (Distribution)o;
          if ( d.getType() != null &&  Boolean.class.isAssignableFrom( d.getType() ) ) {
              System.out.println("Getting the probability of " + d);
              Double p = null;
              if ( d instanceof FunctionOfDistributions ) {
                  p = ((FunctionOfDistributions)d).probability( true, unsampledDefault );
              } else {
                  p = d.probability( true );
              }
              if ( d instanceof FunctionOfDistributions ) {
                ((FunctionOfDistributions)d).getSamples().toFile("samples_" + System.currentTimeMillis() + ".csv");
              } else if ( d instanceof SampleDistribution ) {
                ((SampleDistribution)d).toFile("samples_" + System.currentTimeMillis() + ".csv");
              }
              System.out.println("Got probability " + p + " for " + d);
              if ( p != null && p >= 0.0 ) return p;
          }
      };
      try {
          Object r = Expression.evaluate( o, Distribution.class, false, false );
          if ( r != null && r != o && r instanceof Distribution ) {
              return p(r, unsampledDefault);
          }
      } catch ( Throwable t ) {
        t.printStackTrace();
      }
      try {
          Object r = Expression.evaluate( o, Boolean.class, false, false );
          if ( r != null && r instanceof Boolean ) {
              return (Boolean)r ? 1.0 : 0.0;
          }
      } catch ( Throwable t ) {
      }
//      if ( o instanceof Boolean ) {
//          return (Boolean)o ? 1.0 : 0.0;
//      }
//      if ( o instanceof Call ) {
//          return p((Call)o);
//      }
//      if ( o instanceof Variable ) {
//          return p(((Variable)o).getValue( false ));  // TODO -- check inf recursion!
//      }
//      if ( o instanceof Expression ) {
//          return p(((Expression)o).getExpression());  // TODO -- check inf recursion!
//      }
//      if ( o instanceof TimeV ) {
//          return p(((Expression)o).getExpression());  // TODO -- check inf recursion!
//      }
      return null;
      //} finally {
      //}
  }


  protected static boolean isParameter(Object o) {
    Parameter p = null;
    try {
      p = Expression.evaluate( o, Parameter.class, false, false );
    } catch ( Throwable t ) {}
    return p != null;
  }

  // Inequality functions

  public static class EQ< T > extends BooleanBinary< T > {

    public EQ( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "equals", "pickEqualToForward", "pickEqualToForward" );
    }

    public EQ( Object o1, Object o2 ) {
      super( o1, o2, "equals", "pickEqualToForward", "pickEqualToForward" );
    }

    public EQ( EQ< T > m ) {
      super( m );
    }

    public EQ< T > clone() {
      return new EQ< T >( this );
    }

    /**
     * Determine whether the arguments to the call are grounded.  Parameter
     * arguments do not need to be grounded foe EQ.
     * @param deep
     * @param seen
     * @return
     */
    @Override
    public synchronized boolean areArgumentsGrounded( boolean deep,
                                                      Set< Groundable > seen ) {
      // Check if arguments are grounded if groundable.  Ok for arguments to be null.
      if ( arguments != null ) {
        for ( Object o : arguments ) {
          if ( o != null && o instanceof Groundable && !isParameter( o )
               && !( (Groundable)o ).isGrounded( deep, seen ) ) {
            return false;
          }
        }
      }
      return true;
    }


    @Override
    public Domain<?> inverseDomain(Domain<?> returnValue, Object argument) {
      if ( returnValue == null ) return null;
      if ( returnValue.magnitude() > 1 ) return DomainHelper.getDomain( argument );
      Boolean retVal1 = Utils.isTrue(returnValue.pickRandomValue());
      boolean retVal = retVal1 != null && retVal1;
      FunctionCall inverseCall = invert(retVal, argument);
      Domain<?> d =  inverseCall.getDomain(false, null);
      return d;
    }

    @Override
    public FunctionCall inverse( Object returnValue, Object arg ) {
      FunctionCall f =
          invert( Boolean.TRUE.equals(Utils.isTrue( returnValue )), arg );
      f.arguments = new Vector< Object >( Utils.newList( Boolean.FALSE ) ); // this
                                                                            // argument
                                                                            // is
                                                                            // ignored
      return f;
      // new FunctionCall( this, getClass(), "invert",
      // new Object[] { Utils.isTrue( returnValue ), arg }, (Class<?>)null );
      // return f.evaluate();
    }

    public FunctionCall invert( final boolean eqReturnValue,
                                final Object arg ) {
      LinkedHashSet< Object > otherArgs = getOtherArgs( arg );
      // should only be one
      final Object otherArg = otherArgs.iterator().next();
      final boolean isArgFirst = arguments.firstElement() == arg;

      try {
        return new FunctionCall( (Method)Functions.class.getMethod( "not",
                                                                    new Class< ? >[] { Expression.class } ),
                                 (Class< ? >)null ) {
          /**
           * Invocation of this inverse function returns the other arg if the return value is true, else
           * a value not equal to the other arg.
           * TODO -- REVIEW -- Should this be returning a set of values or a domain?
           * TODO -- REVIEW -- Does anything calls this, or is it just for getDomain()?
           * @param evaluatedObject
           * @param evaluatedArgs
           * @return
           * @throws IllegalArgumentException
           * @throws InstantiationException
           * @throws IllegalAccessException
           * @throws InvocationTargetException
           */
          @Override
          public Object
                 invoke( Object evaluatedObject,
                         Object[] evaluatedArgs ) throws IllegalArgumentException,
                                                  InstantiationException,
                                                  IllegalAccessException,
                                                  InvocationTargetException {
            //Object argEval = null;
            Object otherArgEval = null;
            // Try using already evaluated args first.
            if ( evaluatedArgs != null && evaluatedArgs.length == 2 ) {
              //argEval = evaluatedArgs[isArgFirst ? 0 : 1];
              if (eqReturnValue) {
                otherArgEval = evaluatedArgs[isArgFirst ? 1 : 0];
                evaluationSucceeded = true;
                return otherArgEval;
//              } else {
//                if (Expression.valuesEqual(argEval, otherArgEval)) {
//                  return null;
//                }
//                return argEval;
              }
            }

            // use arg and otherArg and evaluate result
            Object x = null;
            if ( eqReturnValue ) {
              x = otherArg;
            } else {
              Domain<?> d = getDomain(false, null);
              x = DomainHelper.getRepresentativeValues( d, null );
//              x = d.pickRandomValue();
//              if (x == null) {
//                x = arg;
//              }
//            }
//            if ( x instanceof Evaluatable ) {
//              return ((Evaluatable) x).evaluate(null, false);
            }
            evaluationSucceeded = true;
            return x;
          }

          /**
           * If the return value is true, then the domain is the domain of the other arg.
           * If false, we need to return the domain of the arg hat includes values that
           * are possibly not equal to the other arg.  So, if the other arg has a domain
           * with multiple values, then arg can be anything in its domain beause it will
           * always be not equal to one or more of the values in the other arg's domain.
           * If the other arg has a single value in its domain, the return the argDomain
           * with the value of the other arg's domain removed.
           * @param propagate
           * @param seen
           * @return
           */
          @Override
          public Domain< ? > getDomain( boolean propagate,
                                        Set< HasDomain > seen ) {
            //if ( otherArg instanceof HasDomain ) {
            Domain< ? > argDomain = null;
            try {
              // A plain ConstructorCall doesn't redefine calculateDomain(), throwing an exception,
              // so we don't waant to ask for its domain.
              if ( arg== null ||
                   arg.getClass().equals(ConstructorCall.class)  ||
                   ( arg instanceof Expression &&
                     ((Expression) arg).getExpression().getClass().equals(ConstructorCall.class) ) ) {
                argDomain = null;
              } else {
                argDomain = DomainHelper.getDomain(arg);
              }
            } catch ( Throwable t ) {}
            if ( argDomain == null ) {
              return DomainHelper.emptyDomain();
            }
            if ( argDomain.isEmpty() ) {
              return argDomain;
            }
            Domain< ? > otherDomain = DomainHelper.getDomain( otherArg );
            if ( otherDomain == null || otherDomain.isEmpty() ) {
              return argDomain;
            }
            if ( eqReturnValue || otherDomain.magnitude() > 1 ) {
              return otherDomain;
            }
            // Since the tests above failed, it must be the case that other arg
            // has a single value in its domain that needs to be removed from
            // arg's domain.
            Domain<?> newDomain =  argDomain.subtract( otherDomain );
            return newDomain;
          }
        };  // end of anonymous FunctionCall

      } catch ( Throwable e ) {
        e.printStackTrace();
      }

      return null;
    }  // end of invert(retVal, arg)

    @Override
    public Domain< ? > calculateDomain( boolean propagate,
                                        Set< HasDomain > seen ) {
      Object o1 = this.arguments.get( 0 );
      Object o2 = this.arguments.get( 1 );
      if ( o1 instanceof HasDomain && o2 instanceof HasDomain ) {
        HasDomain hd1 = (HasDomain)o1;
        HasDomain hd2 = (HasDomain)o2;
        Domain d1 = hd1.getDomain( propagate, seen );
        Domain d2 = hd2.getDomain( propagate, seen );
        if ( d1 instanceof RangeDomain && d2 instanceof RangeDomain ) {
          AbstractRangeDomain rd1 = (AbstractRangeDomain)d1;
          AbstractRangeDomain rd2 = (AbstractRangeDomain)d2;
          if ( rd1.size() == 1 && rd2.size() == 1 && rd1.equals( rd2 ) ) {
            //System.out.println( "true" );
            return new BooleanDomain( true, true );
          } else if ( rd1.intersects( rd2 ) ) {// greaterEquals(rd1.getUpperBound(),
                                               // rd2.getLowerBound()) &&
            // rd1.lessEquals(rd1.getLowerBound(), rd2.getUpperBound()) ){
            //System.out.println( "true or false" );
            return new BooleanDomain( false, true );
          } else if ( Debug.isOn()) System.out.println( "false" );
          return new BooleanDomain( false, false );
        } else if ( d1 != null && d2 != null ) {
            if ( d1 != null && !d1.isEmpty() && d2 != null && !d2.isEmpty() ) {
            Domain d3 = d1.clone();
            d3.restrictTo( d2 );
            if (!d3.isEmpty()) {
              if ( d1.magnitude() == 1 && d2.magnitude() == 1 ) {
                return new BooleanDomain( true, true );
              }
              return new BooleanDomain( false, true );
            }
            return new BooleanDomain( false, false );
          }
        }
      }
      return BooleanDomain.defaultDomain;
    }

    /*
     * @Override public < T1 > T1 pickValue( Variable< T1 > variable ) { T1
     * newValue = null; FunctionCall f = (FunctionCall)this.expression;
     * Variable< T1 > otherArg = null; boolean found = false; for ( Object arg :
     * f.arguments ) { if ( variable == arg ) { found = true; } else if ( arg
     * instanceof Expression && ( ( (Expression)arg ).expression == variable ) )
     * { found = true; } else if ( arg instanceof Expression && !( (
     * (Expression)arg ).expression instanceof Parameter ) && variable
     * instanceof Parameter ) { if ( ( (Expression< T1 >)arg ).hasParameter(
     * (Parameter< T1 >)variable, false, null ) ) { newValue =
     * variable.pickRandomValue(); } } else if ( arg instanceof Variable ) { if
     * ( otherArg == null ) { otherArg = (Variable< T1 >)arg; } } } if (
     * otherArg != null && found ) { if ( Debug.isOn() ) Debug.outln(
     * "suggesting value " + otherArg.getValue() + " to make " +
     * getClass().getSimpleName() + " true" ); return otherArg.getValue(); } if
     * ( newValue == null ) { if ( Debug.isOn() ) Debug.outln(
     * "suggesting same value " + variable.getValue() + " for " +
     * getClass().getSimpleName() + " true" ); return variable.getValue(); } if
     * ( Debug.isOn() ) Debug.outln( "suggesting value " + newValue + " for " +
     * getClass().getSimpleName() + " true" ); return newValue; }
     * 
     */
  }

  public static class Equals< T > extends EQ< T > {
    public Equals( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
    }

    public Equals( Object o1, Object o2 ) {
      super( o1, o2 );
    }
  }

  public static class NEQ< T > extends BooleanBinary< T > {
    public NEQ( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "notEquals", "pickNotEqualToForward",
             "pickNotEqualToForward" );
    }

    public NEQ( Object o1, Object o2 ) {
      super( o1, o2, "notEquals", "pickNotEqualToForward",
             "pickNotEqualToForward" );
    }

    public NEQ( NEQ< T > m ) {
      super( m );
    }

    public NEQ< T > clone() {
      return new NEQ< T >( this );
    }

    @Override
    public Domain< ? > calculateDomain( boolean propagate, Set< HasDomain > seen ) {
      if ( getArguments().size() != 2 ) {
        return BooleanDomain.defaultDomain;
      }
      Object a1 = getArgument( 0 );
      Object a2 = getArgument( 1 );
      Domain< ? > d1 = DomainHelper.getDomain( a1 );
      Domain< ? > d2 = DomainHelper.getDomain( a2 );
      if ( d1 == null || d2 == null || d1.magnitude() <= 0
           || d2.magnitude() <= 0 || gov.nasa.jpl.ae.util.Math.isInfinity(d1.magnitude()) || gov.nasa.jpl.ae.util.Math.isInfinity(d2.magnitude()) ) {
        return BooleanDomain.defaultDomain;
      }
      
      // hasFalse if the symmetric difference is non-empty
      boolean hasFalse = !(d1.clone().subtract( d2 ).isEmpty() &&
                           d2.clone().subtract( d1 ).isEmpty() );
      // hasTrue if the intersection is non-empty
      Domain< ? > x = d1.clone();
      x.restrictTo( d2 );
      boolean hasTrue = !x.isEmpty();
      if (hasTrue && hasFalse) return BooleanDomain.defaultDomain;
      if (hasTrue) return BooleanDomain.trueDomain;
      if (hasFalse) return BooleanDomain.falseDomain;
      // impossible situation: the domains are badly implemented, can't decide
      // TODO: perhaps this should be some kind of "empty" domain?
      System.err.println( "Empty BooleanDomain needed!" );
      return BooleanDomain.defaultDomain;
    }
    
    // @Override
    // public < T1 > T1 pickValue( Variable< T1 > variable ) {
    // return pickValueBB( this, variable );
    // }
    /*
     * T1 newValue = null; FunctionCall f = (FunctionCall)this.expression;
     * Variable< T1 > otherArg = null; boolean found = false; for ( Object arg :
     * f.arguments ) { if ( variable == arg ) { found = true; } else if ( arg
     * instanceof Expression && ( ( (Expression)arg ).expression == variable ) )
     * { found = true; } else if ( arg instanceof Expression && !( (
     * (Expression)arg ).expression instanceof Parameter ) && variable
     * instanceof Parameter ) { if ( ( (Expression< T1 >)arg ).hasParameter(
     * (Parameter< T1 >)variable, false ) ) { newValue =
     * variable.pickRandomValue(); } } else if ( arg instanceof Variable ) { if
     * ( otherArg == null ) { otherArg = (Variable< T1 >)arg; } } } if (
     * otherArg != null && found ) { newValue = null; for ( int i=0; i < 5; ++i
     * ) { T1 v = variable.pickRandomValue(); if ( v != otherArg.getValue() ) {
     * if ( newValue == null ) { newValue = v; } else if ( otherArg.getDomain()
     * == null || otherArg.getDomain().contains( v ) ) { return v; } } } } if (
     * newValue == null ) { return variable.getValue(); } return newValue; }
     */
  }

  public static class NotEquals< T > extends NEQ< T > {
    public NotEquals( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
    }

    public NotEquals( Object o1, Object o2 ) {
      super( o1, o2 );
    }
  }

  public static void excludeUpperBound( RangeDomain r ) {
    r.excludeUpperBound();
    if ( r instanceof AbstractFiniteRangeDomain ) {
      ( (AbstractFiniteRangeDomain)r ).fixToIncludeBounds();
    }
  }

  public static void excludeLowerBound( RangeDomain r ) {
    r.excludeLowerBound();
    if ( r instanceof AbstractFiniteRangeDomain ) {
      ( (AbstractFiniteRangeDomain)r ).fixToIncludeBounds();
    }
  }
  
  /**
   * Restricts left and right to be consistent with left <= right
   * @param left The domain of the left operand
   * @param right The domain of the right operand
   * @return true iff either domain was changed
   */
  public static < T > Pair<Boolean, Boolean> restrictDomainsByLTE( Domain<T> left, Domain<T> right ) {
    return restrictDomainsByLT(left, right, false);
  }
  /**
   * Restricts left and right to be consistent with left < right
   * @param left The domain of the left operand
   * @param right The domain of the right operand
   * @return true iff either domain was changed
   */
  public static < T > Pair<Boolean, Boolean> restrictDomainsByLT( Domain<T> left, Domain<T> right ) {
    return restrictDomainsByLT(left, right, true);
  }
  /**
   * Restricts left and right to be consistent with left < right or left <= right
   * @param left The domain of the left operand
   * @param right The domain of the right operand
   * @param strict Set to true to use strict inequality (left < right), or to false to use non-strict (left <= right)
   * @return A pair of booleans, (changedLeft, changedRight), true iff corresponding domain was changed
   */
  public static < T > Pair<Boolean, Boolean> restrictDomainsByLT( Domain<T> left, Domain<T> right, boolean strict ) {
    boolean changedLeft  = false;
    boolean changedRight = false;
    
    if (left instanceof AbstractRangeDomain< ? > &&
        right instanceof AbstractRangeDomain< ? >) {
      AbstractRangeDomain< T > l  = (AbstractRangeDomain< T >)left;
      AbstractRangeDomain< T > r = (AbstractRangeDomain< T >)right;
      
      T llo = l.getLowerBound();
      T lhi = l.getUpperBound();
      T rlo = r.getLowerBound();
      T rhi = r.getUpperBound();
      
      if (l.less( rhi, llo ) ||
          (l.lessEquals( rhi, llo ) && 
           (!r.isUpperBoundIncluded() ||
            !l.isLowerBoundIncluded()))) {
        // there is no overlap at all, don't try to adjust bounds
        changedLeft = !l.isEmpty();
        changedRight = !r.isEmpty();
        l.makeEmpty();
        r.makeEmpty();
      } else {
        if (l.less( rhi, lhi )) {
          l.setUpperBound( rhi );
          if (!strict && r.includeUpperBound()) {
            l.includeUpperBound();
          }
          changedLeft = true;
        }
        if (l.includeUpperBound() &&
            l.lessEquals( rhi, lhi ) &&
            (strict || !r.includeUpperBound())) {
          excludeUpperBound( l );
          changedLeft = true;
        }
        
        if (l.less( rlo, llo )) {
          r.setLowerBound( llo );
          if (!strict && l.includeLowerBound()) {
            r.includeLowerBound();
          }
          changedRight = true;
        }
        if (r.includeLowerBound() &&
            l.lessEquals( rlo, llo ) &&
            (strict || !l.includeLowerBound())) {
          excludeLowerBound( r );
          changedRight = true;
        }
      }
    }
    
    return new Pair<>(changedLeft, changedRight);
  }
  
  

  public static class LT< T > extends BooleanBinary< T > {
    public LT( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "lessThan", "pickLessThan", "pickGreaterThanOrEqual" );
      // functionCall.
      setMonotonic( true );
    }

    public LT( Object o1, Object o2 ) {
      super( o1, o2, "lessThan", "pickLessThan", "pickGreaterThanOrEqual" );
      // functionCall.
      setMonotonic( true );
    }

    public LT( LT< T > m ) {
      super( m );
    }

    public LT< T > clone() {
      return new LT< T >( this );
    }

    @Override
    public FunctionCall inverse( Object returnValue, Object arg ) {
      return super.inverse( returnValue, arg );
    }

    @Override
    public Domain< Boolean > calculateDomain( boolean propagate,
                                              Set< HasDomain > seen ) {
      if ( getArguments().size() != 2 ) {
        return BooleanDomain.defaultDomain;
      }
      Object a1 = getArgument( 0 );
      Object a2 = getArgument( 1 );
      Domain< ? > d1 = DomainHelper.getDomain( a1 );
      Domain< ? > d2 = DomainHelper.getDomain( a2 );
      if ( d1 == null || d2 == null || d1.magnitude() <= 0
           || d2.magnitude() <= 0 || gov.nasa.jpl.ae.util.Math.isInfinity(d1.magnitude()) || gov.nasa.jpl.ae.util.Math.isInfinity(d2.magnitude()) ) {
        return BooleanDomain.defaultDomain;
      }
      if ( d1 instanceof AbstractRangeDomain
           && d2 instanceof AbstractRangeDomain ) {
        AbstractRangeDomain< Object > rd1 = (AbstractRangeDomain< Object >)d1;
        AbstractRangeDomain< Object > rd2 = (AbstractRangeDomain< Object >)d2;
        if ( Boolean.TRUE.equals(DomainHelper.less( rd1, rd2 )) ) return BooleanDomain.trueDomain;
        if ( Boolean.TRUE.equals(DomainHelper.greaterEquals( rd1, rd2 )) ) return BooleanDomain.falseDomain;
        // Object lb1 = rd1.getLowerBound();
        // Object ub1 = rd1.getUpperBound();
        // Object lb2 = rd2.getLowerBound();
        // Object ub2 = rd2.getUpperBound();
        //
        // if ( rd1.greaterEquals( lb1, ub2 ) ) {
        // return BooleanDomain.falseDomain;
        // }
        // if ( rd1.less( ub1, lb2 )
        // || ( rd1.equals( ub1, lb2 )
        // && ( !rd1.isUpperBoundIncluded()
        // || !rd2.isLowerBoundIncluded() ) ) ) {
        // return BooleanDomain.trueDomain;
        // }
      }
      // TODO -- There are many possibilities here. Instead, define less(),
      // alwaysLess(), etc. methods for domains that take a variety of
      // arguments.
      return BooleanDomain.defaultDomain;
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
                                                         // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      AbstractRangeDomain< ? > subDomainBelow =
          DomainHelper.createSubDomainBelow( otherArg, false, false );
      AbstractRangeDomain< ? > subDomainAbove =
          DomainHelper.createSubDomainAbove( otherArg, false, false ); //I think this should have include = true ?
      Boolean b = null;
      try {
        b = Expression.evaluate( returnValue, Boolean.class, true );
      } catch ( ClassCastException e ) {

      } catch ( IllegalAccessException e ) {

      } catch ( InvocationTargetException e ) {

      } catch ( InstantiationException e ) {

      }
      if ( firstArg ) {
        subDomainAbove.includeLowerBound();
        return new Conditional( new Expression< Boolean >( b, Boolean.class ),
                                new Expression< Object >( subDomainBelow ),
                                new Expression< Object >( subDomainAbove ) );
      }
      subDomainBelow.includeUpperBound();
      return new Conditional( new Expression< Boolean >( b, Boolean.class ),
                              new Expression< Object >( subDomainAbove ),
                              new Expression< Object >( subDomainBelow ) );
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * gov.nasa.jpl.ae.event.Call#restrictDomain(gov.nasa.jpl.ae.solver.Domain,
     * boolean, java.util.Set)
     */
    @Override
    public < TT > Pair< Domain< TT >, Boolean >
           restrictDomain( Domain< TT > domain, boolean propagate,
                           Set< HasDomain > seen ) {
      boolean changed = false;
      if ( domain.contains( (TT)Boolean.TRUE )
           && domain.contains( (TT)Boolean.FALSE ) ) {
        // nothing to do
      } else if ( domain.magnitude() == 1 ) {
        Object v = domain.getValue( propagate );
        if ( v instanceof Boolean ) {
          String oldDom = this.getDomain( propagate, null ).toString();
          changed = restrictDomains( Boolean.TRUE.equals( (Boolean)v ) );
          if ( Debug.isOn() && changed ) {
            System.out.println( "Restricted " + getName() + " from " + oldDom
                                + " to " + getDomain( propagate, null ) );
          }
        }
      }
      // Domain oldDomain = this.domain.clone();
      // Domain newDomain = (Domain< TT >)getDomain(propagate, null);
      // boolean thisChanged = Utils.valuesEqual( oldDomain, newDomain );
      // this.domain = newDomain;
      return new Pair( this.domain, changed );// || thisChanged);
    }

    // REVIEW -- This seems out of place. Does something else do this?
    public boolean restrictDomains( boolean targetResult ) {
      if ( arguments.size() < 2 ) return false;
      Expression< T > e1 = (Expression< T >)arguments.get( 0 );
      Expression< T > e2 = (Expression< T >)arguments.get( 1 );
      Domain< T > d1 = e1.getDomain( false, null );
      Domain< T > d2 = e2.getDomain( false, null );
      Pair<Boolean, Boolean> changes = null;
      boolean c1 = false;
      boolean c2 = false;
      if (targetResult) {
        changes = restrictDomainsByLT( d1, d2 );
        c1 = Boolean.TRUE.equals( changes.first );
        c2 = Boolean.TRUE.equals( changes.second );
      } else {
        changes = restrictDomainsByLTE( d2, d1 );
        c1 = Boolean.TRUE.equals( changes.second );
        c2 = Boolean.TRUE.equals( changes.first );
      }
      boolean changed = false;
      if (c1) {
        Pair<Domain<T>, Boolean> p1 = e1.restrictDomain( d1, true, null );
        changed |= p1 != null && Boolean.TRUE.equals( p1.second );
      }
      if (c2) { 
        Pair<Domain<T>, Boolean> p2 = e2.restrictDomain( d2, true, null );
        changed |= p2 != null && Boolean.TRUE.equals( p2.second );
      }
      return changed;
    }

  }

  public static class Less< T > extends LT< T > {
    public Less( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
      // functionCall.
      setMonotonic( true );
    }

    public Less( Object o1, Object o2 ) {
      super( o1, o2 );
      // functionCall.
      setMonotonic( true );
    }
  }

  public static class LTE< T > extends BooleanBinary< T > {
    public LTE( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "lessThanOrEqual", "pickLessThanOrEqual",
             "pickGreaterThan" );
      // functionCall.
      setMonotonic( true );
    }

    public LTE( Object o1, Object o2 ) {
      super( o1, o2, "lessThanOrEqual", "pickLessThanOrEqual",
             "pickGreaterThan" );
      // functionCall.
      setMonotonic( true );
    }

    public LTE( LTE< T > m ) {
      super( m );
    }

    public LTE< T > clone() {
      return new LTE< T >( this );
    }

    @Override
    public FunctionCall inverse( Object returnValue, Object arg ) {
      return super.inverse( returnValue, arg );
    }

    @Override
    public Domain< Boolean > calculateDomain( boolean propagate,
                                              Set< HasDomain > seen ) {
      if ( getArguments().size() != 2 ) {
        return BooleanDomain.defaultDomain;
      }
      Object a1 = getArgument( 0 );
      Object a2 = getArgument( 1 );
      Domain< ? > d1 = DomainHelper.getDomain( a1 );
      Domain< ? > d2 = DomainHelper.getDomain( a2 );
      if (d1 == null || d2 == null || d1.magnitude() <= 0
              || d2.magnitude() <= 0 || gov.nasa.jpl.ae.util.Math.isInfinity(d1.magnitude()) || gov.nasa.jpl.ae.util.Math.isInfinity(d2.magnitude()) ) {
        return BooleanDomain.defaultDomain;
      }
      if ( d1 instanceof AbstractRangeDomain
           && d2 instanceof AbstractRangeDomain ) {
        AbstractRangeDomain< Object > rd1 = (AbstractRangeDomain< Object >)d1;
        AbstractRangeDomain< Object > rd2 = (AbstractRangeDomain< Object >)d2;
        if ( Boolean.TRUE.equals(DomainHelper.lessEquals( rd1, rd2 )) ) return BooleanDomain.trueDomain;
        if ( Boolean.TRUE.equals(DomainHelper.greater( rd1, rd2 )) ) return BooleanDomain.falseDomain;
      }
      // TODO -- There are many possibilities here. Instead, define less(),
      // alwaysLess(), etc. methods for domains that take a variety of
      // arguments.
      return BooleanDomain.defaultDomain;
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
                                                         // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      AbstractRangeDomain< ? > subDomainBelow =
          DomainHelper.createSubDomainBelow( otherArg, true, false );
      AbstractRangeDomain< ? > subDomainAbove =
          DomainHelper.createSubDomainAbove( otherArg, true, false );
      Boolean b = null;
      try {
        b = Expression.evaluate( returnValue, Boolean.class, true );
      } catch ( ClassCastException e ) {

      } catch ( IllegalAccessException e ) {

      } catch ( InvocationTargetException e ) {

      } catch ( InstantiationException e ) {

      }
      if ( firstArg ) {
        excludeLowerBound( subDomainAbove );
        return new Conditional( new Expression< Boolean >( b, Boolean.class ),
                                new Expression< Object >( subDomainBelow ),
                                new Expression< Object >( subDomainAbove ) );
      }
      excludeUpperBound( subDomainBelow );
      return new Conditional( new Expression< Boolean >( b, Boolean.class ),
                              new Expression< Object >( subDomainAbove ),
                              new Expression< Object >( subDomainBelow ) );
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * gov.nasa.jpl.ae.event.Call#restrictDomain(gov.nasa.jpl.ae.solver.Domain,
     * boolean, java.util.Set)
     */
    @Override
    public < TT > Pair< Domain< TT >, Boolean >
           restrictDomain( Domain< TT > domain, boolean propagate,
                           Set< HasDomain > seen ) {
      boolean changed = false;
      if ( domain.contains( (TT)Boolean.TRUE )
           && domain.contains( (TT)Boolean.FALSE ) ) {
        // nothing to do
      } else if ( domain.magnitude() == 1 ) {
        Object v = domain.getValue( propagate );
        if ( v instanceof Boolean ) {
          String oldDom = this.getDomain( propagate, null ).toString();
          changed = restrictDomains( Boolean.TRUE.equals( (Boolean)v ) );
          if ( Debug.isOn() && changed ) {
            System.out.println( "Restricted " + getName() + " from " + oldDom
                                + " to " + getDomain( propagate, null ) );
          }
        }
      }
      // Domain oldDomain = this.domain.clone();
      // Domain newDomain = (Domain< TT >)getDomain(propagate, null);
      // boolean thisChanged = Utils.valuesEqual( oldDomain, newDomain );
      // this.domain = newDomain;
      return new Pair( this.domain, changed );// || thisChanged);
    }

    // REVIEW -- This seems out of place. Does something else do this?
    public boolean restrictDomains( boolean targetResult ) {
      if ( arguments.size() < 2 ) return false;
      Expression< T > e1 = (Expression< T >)arguments.get( 0 );
      Expression< T > e2 = (Expression< T >)arguments.get( 1 );
      Domain< T > d1 = e1.getDomain( false, null );
      Domain< T > d2 = e2.getDomain( false, null );
      Pair<Boolean, Boolean> changes = null;
      boolean c1 = false;
      boolean c2 = false;
      if (targetResult) {
        changes = restrictDomainsByLTE( d1, d2 );
        c1 = Boolean.TRUE.equals( changes.first );
        c2 = Boolean.TRUE.equals( changes.second );
      } else {
        changes = restrictDomainsByLT( d2, d1 );
        c1 = Boolean.TRUE.equals( changes.second );
        c2 = Boolean.TRUE.equals( changes.first );
      }
      boolean changed = false;
      if (c1) {
        Pair<Domain<T>, Boolean> p1 = e1.restrictDomain( d1, true, null );
        changed |= p1 != null && Boolean.TRUE.equals( p1.second );
      }
      if (c2) { 
        Pair<Domain<T>, Boolean> p2 = e2.restrictDomain( d2, true, null );
        changed |= p2 != null && Boolean.TRUE.equals( p2.second );
      }
      return changed;
    }

  }

  public static class LessEquals< T > extends LTE< T > {
    public LessEquals( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
      // functionCall.
      setMonotonic( true );
    }

    public LessEquals( Object o1, Object o2 ) {
      super( o1, o2 );
      // functionCall.
      setMonotonic( true );
    }
  }

  public static class GT< T extends Comparable< ? super T > >
                        extends BooleanBinary< T > {
    public GT( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "greaterThan", "pickGreaterThan", "pickLessThanOrEqual" );
      // functionCall.
      setMonotonic( true );
    }

    public GT( Object o1, Object o2 ) {
      super( o1, o2, "greaterThan", "pickGreaterThan", "pickLessThanOrEqual" );
      // functionCall.
      setMonotonic( true );
    }

    public GT( GT< T > m ) {
      super( m );
    }

    public GT< T > clone() {
      return new GT< T >( this );
    }

    @Override
    public FunctionCall inverse( Object returnValue, Object arg ) {
      return super.inverse( returnValue, arg );
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
                                                         // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      AbstractRangeDomain< ? > subDomainBelow =
          DomainHelper.createSubDomainBelow( otherArg, false, false );
      AbstractRangeDomain< ? > subDomainAbove =
          DomainHelper.createSubDomainAbove( otherArg, false, false );
      Boolean b = null;
      try {
        b = Expression.evaluate( returnValue, Boolean.class, true );
      } catch ( ClassCastException e ) {

      } catch ( IllegalAccessException e ) {

      } catch ( InvocationTargetException e ) {

      } catch ( InstantiationException e ) {

      }

      if ( firstArg ) {
        subDomainBelow.includeUpperBound();

        return new Conditional( new Expression< Boolean >( b, Boolean.class ),
                                new Expression< Object >( subDomainAbove ),
                                new Expression< Object >( subDomainBelow ) );
      }
      subDomainAbove.includeLowerBound();
      return new Conditional( new Expression< Boolean >( b, Boolean.class ),
                              new Expression< Object >( subDomainBelow ),
                              new Expression< Object >( subDomainAbove ) );
    }

    /*
     * (non-Javadoc)
     * 
     * @see gov.nasa.jpl.ae.event.FunctionCall#calculateDomain(boolean,
     * java.util.Set)
     */
    @Override
    public Domain< Boolean > calculateDomain( boolean propagate,
                                              Set< HasDomain > seen ) {
      if ( getArguments().size() != 2 ) {
        return BooleanDomain.defaultDomain;
      }
      Object a1 = getArgument( 0 );
      Object a2 = getArgument( 1 );
      Domain< ? > d1 = DomainHelper.getDomain( a1 );
      Domain< ? > d2 = DomainHelper.getDomain( a2 );
      if ( d1 == null || d2 == null || d1.magnitude() <= 0
           || d2.magnitude() <= 0 ) {
        return BooleanDomain.defaultDomain;
      }
      if ( d1 instanceof AbstractRangeDomain
           && d2 instanceof AbstractRangeDomain ) {
        AbstractRangeDomain< Object > rd1 = (AbstractRangeDomain< Object >)d1;
        AbstractRangeDomain< Object > rd2 = (AbstractRangeDomain< Object >)d2;
        if ( Boolean.TRUE.equals(DomainHelper.greater( rd1, rd2 )) ) return BooleanDomain.trueDomain;
        if ( Boolean.TRUE.equals(DomainHelper.lessEquals( rd1, rd2 )) ) return BooleanDomain.falseDomain;
      }
      // TODO -- There are many possibilities here. Instead, define less(),
      // alwaysLess(), etc. methods for domains that take a variety of
      // arguments.
      return BooleanDomain.defaultDomain;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * gov.nasa.jpl.ae.event.Call#restrictDomain(gov.nasa.jpl.ae.solver.Domain,
     * boolean, java.util.Set)
     */
    @Override
    public < TT > Pair< Domain< TT >, Boolean >
           restrictDomain( Domain< TT > domain, boolean propagate,
                           Set< HasDomain > seen ) {
      boolean changed = false;
      if ( domain.contains( (TT)Boolean.TRUE )
           && domain.contains( (TT)Boolean.FALSE ) ) {
        // nothing to do
      } else if ( domain.magnitude() == 1 ) {
        Object v = domain.getValue( propagate );
        if ( v instanceof Boolean ) {
          String oldDom = this.getDomain( propagate, null ).toString();
          changed = restrictDomains( Boolean.TRUE.equals( (Boolean)v ) );
          if ( Debug.isOn() && changed ) {
            System.out.println( "Restricted " + getName() + " from " + oldDom
                                + " to " + getDomain( propagate, null ) );
          }
        }
      }
      return new Pair( this.domain, changed );// || thisChanged);
    }

    // REVIEW -- This seems out of place. Does something else do this?
    public boolean restrictDomains( boolean targetResult ) {
      if ( arguments.size() < 2 ) return false;
      Expression< T > e1 = (Expression< T >)arguments.get( 0 );
      Expression< T > e2 = (Expression< T >)arguments.get( 1 );
      Domain< T > d1 = e1.getDomain( false, null );
      Domain< T > d2 = e2.getDomain( false, null );
      Pair<Boolean, Boolean> changes = null;
      boolean c1 = false;
      boolean c2 = false;
      if (targetResult) {
        changes = restrictDomainsByLT( d2, d1 );
        c1 = Boolean.TRUE.equals( changes.second );
        c2 = Boolean.TRUE.equals( changes.first );
      } else {
        changes = restrictDomainsByLTE( d1, d2 );
        c1 = Boolean.TRUE.equals( changes.first );
        c2 = Boolean.TRUE.equals( changes.second );
      }
      boolean changed = false;
      if (c1) {
        Pair<Domain<T>, Boolean> p1 = e1.restrictDomain( d1, true, null );
        changed |= p1 != null && Boolean.TRUE.equals( p1.second );
      }
      if (c2) { 
        Pair<Domain<T>, Boolean> p2 = e2.restrictDomain( d2, true, null );
        changed |= p2 != null && Boolean.TRUE.equals( p2.second );
      }
      return changed;
    }

  }

  public static class Greater< T extends Comparable< ? super T > >
                             extends GT< T > {
    public Greater( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
      setMonotonic( true );
    }

    public Greater( Object o1, Object o2 ) {
      super( o1, o2 );
      setMonotonic( true );
    }

  }

  public static class GTE< T extends Comparable< ? super T > >
                         extends BooleanBinary< T > {
    public GTE( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2, "greaterThanOrEqual", "pickGreaterThanOrEqual",
             "pickLessThan" );
      setMonotonic( true );
    }

    public GTE( Object o1, Object o2 ) {
      super( o1, o2, "greaterThanOrEqual", "pickGreaterThanOrEqual",
             "pickLessThan" );
      setMonotonic( true );
    }

    public GTE( GTE< T > m ) {
      super( m );
    }

    public GTE< T > clone() {
      return new GTE< T >( this );
    }

    @Override
    public FunctionCall inverse( Object returnValue, Object arg ) {
      return super.inverse( returnValue, arg );
    }

    @Override
    public FunctionCall inverseSingleValue( Object returnValue, Object arg ) {
      if ( arguments == null || arguments.size() != 2 ) return null;
      Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 )
                                                    : arguments.get( 1 ) );
      boolean firstArg = otherArg != arguments.get( 0 ); // thus arg is the
                                                         // first
      if ( returnValue == null || otherArg == null ) return null; // arg can be
                                                                  // null!
      AbstractRangeDomain< ? > subDomainBelow =
          DomainHelper.createSubDomainBelow( otherArg, true, false );
      AbstractRangeDomain< ? > subDomainAbove =
          DomainHelper.createSubDomainAbove( otherArg, true, false );
      Boolean b = null;
      try {
        b = Expression.evaluate( returnValue, Boolean.class, true );
      } catch ( ClassCastException e ) {

      } catch ( IllegalAccessException e ) {

      } catch ( InvocationTargetException e ) {

      } catch ( InstantiationException e ) {

      }
      if ( firstArg ) {
        excludeUpperBound( subDomainBelow );
        return new Conditional( new Expression< Boolean >( b, Boolean.class ),
                                new Expression< Object >( subDomainAbove ),
                                new Expression< Object >( subDomainBelow ) );
      }
      excludeLowerBound( subDomainAbove );
      return new Conditional( new Expression< Boolean >( b, Boolean.class ),
                              new Expression< Object >( subDomainBelow ),
                              new Expression< Object >( subDomainAbove ) );
    }

    /*
     * (non-Javadoc)
     * 
     * @see gov.nasa.jpl.ae.event.FunctionCall#calculateDomain(boolean,
     * java.util.Set)
     */
    @Override
    public Domain< Boolean > calculateDomain( boolean propagate,
                                              Set< HasDomain > seen ) {
      if ( getArguments().size() != 2 ) {
        return BooleanDomain.defaultDomain;
      }
      Object a1 = getArgument( 0 );
      Object a2 = getArgument( 1 );
      Domain< ? > d1 = DomainHelper.getDomain( a1 );
      Domain< ? > d2 = DomainHelper.getDomain( a2 );
      if ( d1 == null || d2 == null || d1.magnitude() <= 0
           || d2.magnitude() <= 0 || gov.nasa.jpl.ae.util.Math.isInfinity(d1.magnitude()) || gov.nasa.jpl.ae.util.Math.isInfinity(d2.magnitude()) ) {
        return BooleanDomain.defaultDomain;
      }
      if ( d1 instanceof AbstractRangeDomain
           && d2 instanceof AbstractRangeDomain ) {
        AbstractRangeDomain< Object > rd1 = (AbstractRangeDomain< Object >)d1;
        AbstractRangeDomain< Object > rd2 = (AbstractRangeDomain< Object >)d2;
        if ( Boolean.TRUE.equals(DomainHelper.greaterEquals( rd1, rd2 )) ) return BooleanDomain.trueDomain;
        if ( Boolean.TRUE.equals(DomainHelper.less( rd1, rd2 )) ) return BooleanDomain.falseDomain;
      }
      // TODO -- There are many possibilities here. Instead, define less(),
      // alwaysLess(), etc. methods for domains that take a variety of
      // arguments.
      return BooleanDomain.defaultDomain;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * gov.nasa.jpl.ae.event.Call#restrictDomain(gov.nasa.jpl.ae.solver.Domain,
     * boolean, java.util.Set)
     */
    @Override
    public < TT > Pair< Domain< TT >, Boolean >
           restrictDomain( Domain< TT > domain, boolean propagate,
                           Set< HasDomain > seen ) {
      boolean changed = false;
      if ( domain.contains( (TT)Boolean.TRUE )
           && domain.contains( (TT)Boolean.FALSE ) ) {
        // nothing to do
      } else if ( domain.magnitude() == 1 ) {
        Object v = domain.getValue( propagate );
        if ( v instanceof Boolean ) {
          String oldDom = this.getDomain( propagate, null ).toString();
          changed = restrictDomains( Boolean.TRUE.equals( (Boolean)v ) );
          if ( Debug.isOn() && changed ) {
            System.out.println( "Restricted " + getName() + " from " + oldDom
                                + " to " + getDomain( propagate, null ) );
          }
        }
      }
      return new Pair( this.domain, changed );// || thisChanged);
    }

    // REVIEW -- This seems out of place. Does something else do this?
    public boolean restrictDomains( boolean targetResult ) {
      if ( arguments.size() < 2 ) return false;
      Expression< T > e1 = (Expression< T >)arguments.get( 0 );
      Expression< T > e2 = (Expression< T >)arguments.get( 1 );
      Domain< T > d1 = e1.getDomain( false, null );
      Domain< T > d2 = e2.getDomain( false, null );
      Pair<Boolean, Boolean> changes = null;
      boolean c1 = false;
      boolean c2 = false;
      if (targetResult) {
        changes = restrictDomainsByLTE( d2, d1 );
        c1 = Boolean.TRUE.equals( changes.second );
        c2 = Boolean.TRUE.equals( changes.first );
      } else {
        changes = restrictDomainsByLT( d1, d2 );
        c1 = Boolean.TRUE.equals( changes.first );
        c2 = Boolean.TRUE.equals( changes.second );
      }
      boolean changed = false;
      if (c1) {
        Pair<Domain<T>, Boolean> p1 = e1.restrictDomain( d1, true, null );
        changed |= p1 != null && Boolean.TRUE.equals( p1.second );
      }
      if (c2) { 
        Pair<Domain<T>, Boolean> p2 = e2.restrictDomain( d2, true, null );
        changed |= p2 != null && Boolean.TRUE.equals( p2.second );
      }
      return changed;
    }

  }

  public static class GreaterEquals< T extends Comparable< ? super T > >
                                   extends GTE< T > {

    public GreaterEquals( Expression< T > o1, Expression< T > o2 ) {
      super( o1, o2 );
    }

    public GreaterEquals( Object o1, Object o2 ) {
      super( o1, o2 );
    }
  }

  public static class DoesThereExist< T > extends BooleanBinary< T > {
    // REVIEW -- This could extend ForAll or vice versa.

    public DoesThereExist( Variable< T > variable,
                           // Domain<T> d,
                           Expression< Boolean > o ) {
      super( variable, o, "thereExists" ); // TODO -- pickFunctions?
      setMonotonic( Functions.isMonotonic( o ) );
    }

    public DoesThereExist( Variable< T > variable, Collection< T > valueSet,
                           Expression< Boolean > o ) {
      this( variable, o ); // TODO -- pickFunctions?
      variable.setDomain( new ObjectDomain< T >( valueSet ) );
    }

    public DoesThereExist( Collection< T > valueSet, Expression< Boolean > o ) {
      super( new Parameter< T >( "", new ObjectDomain< T >( valueSet ), null ),
             o, "forAll" ); // TODO -- pickFunctions?
      setMonotonic( Functions.isMonotonic( o ) );
    }

    public DoesThereExist( DoesThereExist< T > m ) {
      super( m );
    }

    public DoesThereExist< T > clone() {
      return new DoesThereExist< T >( this );
    }
  }

  public static class ThereExists< T > extends DoesThereExist< T > {

    public ThereExists( Variable< T > variable,
                        // Domain<T> d,
                        Expression< Boolean > o ) {
      super( variable, o );
    }

    public ThereExists( Variable< T > variable, Collection< T > valueSet,
                        Expression< Boolean > o ) {
      super( variable, valueSet, o );
    }

    public ThereExists( Collection< T > valueSet, Expression< Boolean > o ) {
      super( valueSet, o );
    }
  }

  public static class Exists< T > extends DoesThereExist< T > {

    public Exists( Variable< T > variable, Expression< Boolean > o ) {
      super( variable, o );
    }

    public Exists( Variable< T > variable, Collection< T > valueSet,
                   Expression< Boolean > o ) {
      super( variable, valueSet, o ); // TODO -- pickFunctions?
    }

    public Exists( Collection< T > valueSet, Expression< Boolean > o ) {
      super( valueSet, o ); // TODO -- pickFunctions?
    }

  }

  public static class ForAll< T > extends BooleanBinary< T > {
    // Collection<?> quantifiedVariables = null;
    // public ForAll( Collection< Variable<?> > variables,
    // Expression< Boolean > o ) {
    // super( variables, o, "forAll" );
    // }
    public ForAll( Variable< T > variable, Expression< Boolean > o ) {
      super( variable, o, "forAll" ); // TODO -- pickFunctions?
      setMonotonic( Functions.isMonotonic( o ) );
    }

    public ForAll( Variable< T > variable, Collection< T > valueSet,
                   Expression< Boolean > o ) {
      this( variable, o ); // TODO -- pickFunctions?
      variable.setDomain( new ObjectDomain< T >( valueSet ) );
    }

    public ForAll( Collection< T > valueSet, Expression< Boolean > o ) {
      super( new Parameter< T >( "", new ObjectDomain< T >( valueSet ), null ),
             o, "forAll" ); // TODO -- pickFunctions?
      setMonotonic( Functions.isMonotonic( o ) );
    }

    public ForAll( ForAll< T > m ) {
      super( m );
    }

    public ForAll< T > clone() {
      return new ForAll< T >( this );
    }

  }

  public static < T extends Comparable< T > > Boolean
         thereExists( Variable< T > variable,
                      Expression< Boolean > o ) throws IllegalAccessException,
                                                InvocationTargetException,
                                                InstantiationException {
    return !forAll( variable, new Expression< Boolean >( new Not( o ) ) );
  }

  public static < T extends Comparable< T > > Boolean
         forAll( Variable< T > variable,
                 Expression< Boolean > o ) throws IllegalAccessException,
                                           InvocationTargetException,
                                           InstantiationException {
    if ( variable == null ) return null; // TODO -- fix this. If o is True,
                                         // doesnt matter if variable is null
    if ( o == null ) return true; // TODO REVIEW

    // If the variable is not in expression, then it doesnt matter what
    // the variable is. Just evaluate the expression:
    if ( variable instanceof Parameter
         && !o.hasParameter( (Parameter< T >)variable, true, null ) ) {
      return (Boolean)o.evaluate( Boolean.class, false );
    }
    Domain< T > d = variable.getDomain();
    Boolean b = null;

    // The variable doesnt have a domain, then just need to evaluate the
    // expression:
    // REVIEW
    if ( d == null || d.magnitude() == 0 ) {
      b = (Boolean)o.evaluate( Boolean.class, false );
    }

    RangeDomain< T > rd = null;
    if ( b == null && d instanceof RangeDomain ) {
      rd = (RangeDomain< T >)d;
    }

    // If the function is monotonic then evaluate expression with values
    // at the range endpoints:
    if ( b == null && isMonotonic( o ) && rd != null ) {
      variable.setValue( rd.getLowerBound() );
      if ( !(Boolean)o.evaluate( Boolean.class, true ) ) {
        b = false;
      } else {
        variable.setValue( rd.getUpperBound() );
        Boolean tb = (Boolean)o.evaluate( Boolean.class, true );
        if ( tb != null ) {
          if ( !tb ) {
            b = false;
          } else {
            b = true;
          }
        }
      }
    }

    // If the range is finite then try every value in the domain:
    if ( b == null && d.magnitude() > 0
         && d instanceof AbstractFiniteRangeDomain ) {// !d.isInfinite() ) {
      AbstractFiniteRangeDomain< T > afrd = (AbstractFiniteRangeDomain< T >)d;
      b = true;
      for ( long i = 0; i < d.magnitude(); ++i ) {
        variable.setValue( afrd.getNthValue( i ) );
        Boolean tb = (Boolean)o.evaluate( Boolean.class, true );
        if ( tb != null ) {
          if ( !tb ) {
            b = false;
            break;
          }
        }
      }
    }
    if ( Debug.isOn() ) Debug.outln( "forAll(" + variable + " in " + d + ", "
                                     + o + " = " + b );
    return b;
  }

  public static boolean isMonotonic( Expression< ? > o ) {
    if ( o.form == Form.Function ) {
      if ( ( (FunctionCall)o.expression ).isMonotonic() ) {
        return true;
      }
    }
    return false;
  }

  private static boolean expressionsAreOkay( boolean complain,
                                             Expression< ? >... exprs ) {
    if ( exprs == null ) return true;
    for ( Expression< ? > expr : exprs ) {
      if ( expr == null || !expr.isGrounded( false, null ) ) {
        if ( complain ) {
          System.err.println( "Expression is not grounded! " + expr );

        }
        return false;
      }
    }
    return true;
  }

  /*
   * // TODO -- make this work for TimeVarying public static < T extends
   * Comparable< ? super T > > Boolean lessThan( Expression< T > o1, Expression<
   * T > o2 ) throws IllegalAccessException, InvocationTargetException,
   * InstantiationException { // if ( !expressionsAreOkay(
   * complainAboutBadExpressions, o1, o2 ) ) { //// if ( !o1.isGrounded() ||
   * !o2.isGrounded() ) { // return false; //// } //// return greaterThan( o2,
   * o1 ); // } if ( o1 == o2 ) return false; if ( o1 == null || o2 == null )
   * return (o2 != null); T r1 = o1.evaluate( false ); T r2 = o2.evaluate( false
   * ); if ( r1 == r2 ) return false; if ( r1 == null || r2 == null ) return (r2
   * != null); boolean b; if ( r1.getClass().isAssignableFrom(
   * java.lang.Double.class ) || r2.getClass().isAssignableFrom(
   * java.lang.Double.class ) ) { Number n1 = Expression.evaluate( o1,
   * Number.class, false ); Number n2 = Expression.evaluate( o2, Number.class,
   * false ); b = n1.doubleValue() < n2.doubleValue(); } else { b =
   * r1.compareTo( r2 ) < 0; } if ( Debug.isOn() ) Debug.outln( o1 + " < " + o2
   * + " = " + b ); return b; }
   */

  public static < T extends Comparable< ? super T > > Object
         lessThan( Expression< T > o1,
                   Expression< T > o2 ) throws IllegalAccessException,
                                        InvocationTargetException,
                                        InstantiationException {
    return compare( o1, o2, Inequality.LT );
  }

  public static < V1, V2 > Object lessThan( V1 o1,
                                            V2 o2 ) throws IllegalAccessException,
                                                    InvocationTargetException,
                                                    InstantiationException {
    return compare( o1, o2, Inequality.LT );
  }

  public static < T extends Comparable< ? super T > > Object
         lessThanOrEqual( Expression< T > o1,
                          Expression< T > o2 ) throws IllegalAccessException,
                                               InvocationTargetException,
                                               InstantiationException {
    return compare( o1, o2, Inequality.LTE );
  }

  public static < V1, V2 > Object
         greaterThanOrEqual( V1 o1, V2 o2 ) throws IllegalAccessException,
                                            InvocationTargetException,
                                            InstantiationException {
    return compare( o1, o2, Inequality.GTE );
  }

  public static < T extends Comparable< ? super T > > Object
         greaterThanOrEqual( Expression< T > o1,
                             Expression< T > o2 ) throws IllegalAccessException,
                                                  InvocationTargetException,
                                                  InstantiationException {
    return compare( o1, o2, Inequality.GTE );
  }

  public static < V1, V2 > Object greaterThan( V1 o1,
                                               V2 o2 ) throws IllegalAccessException,
                                                       InvocationTargetException,
                                                       InstantiationException {
    return compare( o1, o2, Inequality.GT );
  }

  public static < T extends Comparable< ? super T > > Object
         greaterThan( Expression< T > o1,
                      Expression< T > o2 ) throws IllegalAccessException,
                                           InvocationTargetException,
                                           InstantiationException {
    return compare( o1, o2, Inequality.GT );
  }

  public static < V1, V2 > Object
         lessThanOrEqual( V1 o1, V2 o2 ) throws IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    return compare( o1, o2, Inequality.LTE );
  }

  public static Number toNumber( Object o, boolean simple ) {
    if ( o == null ) return null;
    if ( o instanceof Number ) return (Number)o;
    if ( o instanceof String ) return toNumber( (String)o );
    if ( simple ) return null;
    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, true );
    } catch ( ClassCastException e ) {} catch ( IllegalAccessException e ) {} catch ( InvocationTargetException e ) {} catch ( InstantiationException e ) {}
    return n;
  }

  // TODO -- need to be able to return BigDecimal if others fail.
  public static Double toNumber( String s ) {
    if ( Utils.isNullOrEmpty( s ) ) return null;
    try {
      return Double.parseDouble( s );
    } catch ( NumberFormatException e ) {} catch ( NullPointerException e ) {}
    return null;
  }

  public static < T extends Comparable< ? super T > > Object
         compare( Expression< T > o1, Expression< T > o2,
                  Inequality i ) throws IllegalAccessException,
                                 InvocationTargetException,
                                 InstantiationException {
    if ( o1 == null && o2 == null ) return null;
    Object r1 = ( o1 == null ? null : Expression.evaluateDeep( o1, null, false, false ) );
    Object r2 = ( o2 == null ? null : Expression.evaluateDeep( o2, null, false, false ) );
    return compare( r1, r2, i );
  }

    /**
     * Compare two objects, which could be numbers, strings to treat as numbers,
     * plain strings, TimeVaryingMaps, Distributions, or a mix.
     *
     *
     * @param o1 the first object
     * @param o2 the second object
     * @param i the kind of comparison (<, <=, =, ...)
     * @param <V1> the type of the first object
     * @param <V2> the type of the second object
     * @return either a Boolean value indicating whether the inequality holds,
     *         a Distribution indicating the probability that it holds,
     *         a TimeVaryingMap<Boolean> indicating at what times it holds,
     *         a TimeVaryingMap of distributions, indicating the probability that it holds over time, or
     *         a Distribution of TimeVaryingMaps for whether it holds for alternative courses of time.
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     */
  public static < V1, V2 > Object
         compare( V1 o1, V2 o2, Inequality i ) throws IllegalAccessException,
                                               InvocationTargetException,
                                               InstantiationException {
    Object result = null;
    if ( o1 == null || o2 == null ) {
      result = TimeVaryingMap.doesInequalityHold( o1, o2, i );
      //result = null;
    }
    else if ( o1 instanceof String || o2 instanceof String ) {
      Number n1 = toNumber( o1, true );
      Number n2 = toNumber( o2, true );
      if ( (!( o1 instanceof String ) || n1 != null) && (!( o2 instanceof String )
           || n2 != null) ) {
        if ( o1 instanceof String && n1 != null ) {
          o1 = (V1)n1;
        }
        if ( o2 instanceof String && n2 != null ) {
          o2 = (V2)n2;
        }
        result = TimeVaryingMap.doesInequalityHold( o1, o2, i );
      } else {
        result = TimeVaryingMap.doesInequalityHold( o1.toString(),
                                                    o2.toString(), i );
        // o1.toString().compareTo( o2.toString() ) <= 0;
      }
    } else {
      Number r1 = null;
      Number r2 = null;
      TimeVaryingMap< ? > map1 = null;
      TimeVaryingMap< ? > map2 = null;
      Distribution<?> d1 = null;
      Distribution<?> d2 = null;

      Boolean b1 = null;
      Boolean b2 = null;

      Object arg1 = null;
      Object arg2 = null;

      Pair< Object, TimeVaryingMap< ? > > p1 = numberOrTimelineOrDistribution( o1 );
      if ( p1.first == null && p1.second == null ) {
        Pair<Boolean, TimeVaryingMap<?>> pb1 = booleanOrTimeline( o1 );
        if ( pb1 != null ) b1 = pb1.first;
      }
      map1 = p1.second;
      if ( p1.first instanceof Distribution ) {
        d1 = (Distribution)p1.first;
        arg1 = d1;
      } else if ( p1.first instanceof Number ) {
        r1 = (Number)p1.first;
        arg1 = map1 == null ? r1 : map1;
      } else if ( b1 != null ) {
        arg1 = b1;
      }
      if ( arg1 == null ) arg1 = o1;
      Pair< Object, TimeVaryingMap< ? > > p2 = numberOrTimelineOrDistribution( o2 );
      if ( p2.first == null && p2.second == null ) {
        Pair<Boolean, TimeVaryingMap<?>> pb2 = booleanOrTimeline( o2 );
        if ( pb2 != null ) b2 = pb2.first;
      }
      map2 = p2.second;
      if ( p2.first instanceof Distribution ) {
        d2 = (Distribution)p2.first;
        arg2 = d2;
      } else if ( p2.first instanceof Number ) {
        r2 = (Number)p2.first;
        arg2 = map2 == null ? r2 : map2;
      } else if ( b2 != null ) {
        arg2 = b2;
      }
      if ( arg2 == null) arg2 = o2;


      if ( d1 != null || d2 != null ) {
        //result = DistributionHelper.compare(o1, o2, arg1, arg2, i);
        result = DistributionHelper.compare(arg1, arg2, i);
      } else if ( map1 != null ) {
        result = (V1)compare( map1, arg2, i );
      } else {
        if ( map2 != null ) {
          result = (V1)compare( arg1, map2, i );
        }
      }

      // make boolean into number
      if ( b1 != null || b2 != null ) {
        // We assign 1 to true and 2 to false because Boolean domain has true < false.
        if ( b1 != null && b2 != null ) {
          arg1 = b1 ? 1 : 2;
          arg2 = b2 ? 1 : 2;
        } else {
          // If only one of them is boolean, treat it like a bit: 0 for false, 1 for true.
          if ( b1 != null ) {
            arg1 = b1 ? 1 : 0;
          }
          if ( b2 != null ) {
            arg2 = b2 ? 1 : 0;
          }
        }
      }

      if ( result == null ) {
        result = TimeVaryingMap.doesInequalityHold( arg1, arg2, i );
      }
    }
    if ( Debug.isOn() ) Debug.outln( o1 + " i " + o2 + " = " + result );
    return result;
  }

  public static Object and( Expression< ? > o1,
                            Expression< ? > o2 ) throws IllegalAccessException,
                                                 InvocationTargetException,
                                                 InstantiationException {
    return applyBool( o1, o2, BoolOp.AND );
  }

  public static < V1, V2 > Object
         and( V1 o1, V2 o2 ) throws IllegalAccessException,
                             InvocationTargetException, InstantiationException {
    return applyBool( o1, o2, BoolOp.AND );
  }

  public static Object or( Expression< ? > o1,
                           Expression< ? > o2 ) throws IllegalAccessException,
                                                InvocationTargetException,
                                                InstantiationException {
    return applyBool( o1, o2, BoolOp.OR );
  }

  public static < V1, V2 > Object
         or( V1 o1, V2 o2 ) throws IllegalAccessException,
                            InvocationTargetException, InstantiationException {
    return applyBool( o1, o2, BoolOp.OR );
  }

  public static Object xor( Expression< ? > o1,
                            Expression< ? > o2 ) throws IllegalAccessException,
                                                 InvocationTargetException,
                                                 InstantiationException {
    return applyBool( o1, o2, BoolOp.XOR );
  }

  public static < V1, V2 > Object
         xor( V1 o1, V2 o2 ) throws IllegalAccessException,
                             InvocationTargetException, InstantiationException {
    return applyBool( o1, o2, BoolOp.XOR );
  }

  public static Object not( Expression< ? > o ) throws IllegalAccessException,
                                                InvocationTargetException,
                                                InstantiationException {
    return applyBool( o, (Expression< ? >)null, BoolOp.NOT );
  }

  public static < V > Object not( V o ) throws IllegalAccessException,
                                        InvocationTargetException,
                                        InstantiationException {
    return applyBool( o, (Object)null, BoolOp.NOT );
  }

  public static Object applyBool( Expression< ? > o1, Expression< ? > o2,
                                  BoolOp i ) throws IllegalAccessException,
                                             InvocationTargetException,
                                             InstantiationException {
    if ( o1 == null && o2 == null ) return null;
    Object r1 = ( o1 == null ? null : Expression.evaluateDeep( o1, null, false, false ) );
    Object r2 = ( o2 == null ? null : Expression.evaluateDeep( o2, null, false, false ) );
    return applyBool( r1, r2, i );
  }

  public static < V1, V2 > Object applyBool( V1 o1, V2 o2,
                                             BoolOp i ) throws IllegalAccessException,
                                                        InvocationTargetException,
                                                        InstantiationException {
    Object result = null;
    if ( o1 == null || o2 == null ) result = null;
    Boolean r1 = null;
    Boolean r2 = null;
    TimeVaryingMap< ? > map1 = null;
    TimeVaryingMap< ? > map2 = null;

    Pair< Boolean, TimeVaryingMap< ? > > p1 = booleanOrTimeline( o1 );
    r1 = p1.first;
    map1 = p1.second;

    if ( map1 != null ) {
      result = (V1)applyBool( map1, o2, i );
    } else {
      Pair< Boolean, TimeVaryingMap< ? > > p2 = booleanOrTimeline( o2 );
      r2 = p2.first;
      map2 = p2.second;

      if ( map2 != null ) {
        result = (V1)applyBool( o1, map2, i );
      }
    }
    if ( result == null ) {
      if ( r1 == null && r2 == null ) return null;
      else result = TimeVaryingMap.applyOp( r1, r2, i );
    }
    if ( Debug.isOn() ) Debug.outln( o1 + " i " + o2 + " = " + result );
    return result;
  }

  public static TimeVaryingMap< Boolean >
         applyBool( Object o, TimeVaryingMap< ? > tv,
                    BoolOp i ) throws ClassCastException,
                               IllegalAccessException,
                               InvocationTargetException,
                               InstantiationException {
    return applyBool( tv, o, i );
  }

  public static TimeVaryingMap< Boolean >
         applyBool( TimeVaryingMap< ? > tv, Object o,
                    BoolOp i ) throws ClassCastException,
                               IllegalAccessException,
                               InvocationTargetException,
                               InstantiationException {
    if ( tv == null ) return null;

    TimeVaryingMap< ? > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable t ) {}
    if ( tvm != null ) return applyBool( tv, tvm, i );

    Boolean n = null;
    try {
      if ( !( o instanceof TimeVaryingMap ) ) {
        n = Utils.isTrue( o, true );// Expression.evaluate( o, Number.class,
                                    // false
        // );
      }
    } catch ( Throwable t ) {}
    // if ( n != null )
    return TimeVaryingMap.applyBool( tv, n, false, i );

    // return null;
  }

  public static TimeVaryingMap< Boolean >
         applyBool( TimeVaryingMap< ? > tv1, TimeVaryingMap< ? > tv2,
                    BoolOp i ) throws ClassCastException,
                               IllegalAccessException,
                               InvocationTargetException,
                               InstantiationException {
    return TimeVaryingMap.applyBool( tv1, tv2, i );
  }
  /*
   * // TODO -- make this work for TimeVarying public static < T extends
   * Comparable< ? super T > > Boolean lessThanOrEqual( Expression< T > o1,
   * Expression< T > o2 ) throws IllegalAccessException,
   * InvocationTargetException, InstantiationException { // if (
   * !expressionsAreOkay( complainAboutBadExpressions, o1, o2 ) ) { //// if (
   * !o1.isGrounded() || !o2.isGrounded() ) { // return false; //// } ////
   * return greaterThanOrEqual( o2, o1 ); // } if ( o1 == o2 ) return true; if (
   * o1 == null || o2 == null ) return (o1 == null); T r1 = o1.evaluate( false
   * ); T r2 = o2.evaluate( false ); if ( r1 == r2 ) return true; if ( r1 ==
   * null || r2 == null ) return (r1 == null); boolean b; if (
   * r1.getClass().isAssignableFrom( java.lang.Double.class ) ||
   * r2.getClass().isAssignableFrom( java.lang.Double.class ) ) { Number n1 =
   * Expression.evaluate( o1, Number.class, false ); Number n2 =
   * Expression.evaluate( o2, Number.class, false ); b = n1.doubleValue() <=
   * n2.doubleValue(); } else { b = r1.compareTo( r2 ) <= 0; } if ( Debug.isOn()
   * ) Debug.outln( o1 + " <= " + o2 + " = " + b ); return b; }
   */
  // // TODO -- make this work for TimeVarying
  // public static < T extends Comparable< ? super T > > Boolean
  // greaterThan( Expression< T > o1, Expression< T > o2 ) throws
  // IllegalAccessException, InvocationTargetException, InstantiationException {
  //// if ( !expressionsAreOkay( complainAboutBadExpressions, o1, o2 ) ) {
  ////// if ( !o1.isGrounded() || !o2.isGrounded() ) {
  //// return false; // TODO -- REVIEW -- throw exception?
  ////// }
  ////// return lessThan( o2, o1 );
  //// }
  // if ( o1 == o2 ) return false;
  // if ( o1 == null || o2 == null ) return (o1 != null);
  // T r1 = o1.evaluate( false );
  // T r2 = o2.evaluate( false );
  // if ( r1 == r2 ) return false;
  // if ( r1 == null || r2 == null ) return (r1 != null);
  // boolean b;
  // if ( r1.getClass().isAssignableFrom( java.lang.Double.class ) ||
  // r2.getClass().isAssignableFrom( java.lang.Double.class ) ) {
  // Number n1 = Expression.evaluate( o1, Number.class, false );
  // Number n2 = Expression.evaluate( o2, Number.class, false );
  // b = n1.doubleValue() > n2.doubleValue();
  // } else {
  // b = r1.compareTo( r2 ) > 0;
  // }
  // if ( Debug.isOn() ) Debug.outln( o1 + " > " + o2 + " = " + b );
  // return b;
  // }

  // HERE!!! TODO
  public static < T > T pickGreaterThan( Expression< T > o1,
                                         Expression< T > o2 ) {
    return pickGreater( o1, o2, false );
  }

  public static < T > T pickGreaterThanOrEqual( Expression< T > o1,
                                                Expression< T > o2 ) {
    return pickGreater( o1, o2, true );
  }

  public static < T > T pickGreaterThan( Expression< T > o ) {
    return pickGreater( o, false );
  }

  public static < T > T pickGreaterThanOrEqual( Expression< T > o ) {
    return pickGreater( o, true );
  }

  public static < T > T pickGreaterThan( Expression< T > o,
                                         AbstractRangeDomain< T > domain ) {
    return pickGreater( o, domain, false );
  }

  public static < T > T
         pickGreaterThanOrEqual( Expression< T > o,
                                 AbstractRangeDomain< T > domain ) {
    return pickGreater( o, domain, true );
  }

  public static < T > T pickGreater( Expression< T > o,
                                     AbstractRangeDomain< T > domain,
                                     boolean orEquals ) {
    T t = null;
    if ( o == null ) return null;
    T r = null;
    try {
      r = (T)Expression.evaluateDeep( o, null, false, false );
    } catch ( IllegalAccessException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    } catch ( InstantiationException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    }
    if ( r == null ) return null;
    t = domain.pickRandomValueGreater( r, orEquals );
    return t;
  }

  public static < T > T pickGreater( Expression< T > o, boolean orEquals ) {
    if ( o == null ) return null;
    Domain< T > d = o.getDomain( false, null );
    if ( d != null && d instanceof AbstractRangeDomain ) {
      Domain< T > dfd = d.getDefaultDomain();
      if ( dfd instanceof AbstractRangeDomain ) {
        return pickGreater( o, (AbstractRangeDomain< T >)dfd, orEquals );
      }
    }
    return null;
  }

  public static < T > T pickGreater( Expression< T > o1, Expression< T > o2,
                                     boolean orEquals ) {
    Domain< T > d = o1.getDomain( false, null );
    if ( d instanceof AbstractRangeDomain ) {
      AbstractRangeDomain< T > ard = (AbstractRangeDomain< T >)d;
      return pickGreater( o2, ard, orEquals );
    }
    return pickGreater( o2, orEquals );
  }

  public static < T > T pickLessThan( Expression< T > o1, Expression< T > o2 ) {
    return pickLess( o1, o2, false );
  }

  public static < T > T pickLessThanOrEqual( Expression< T > o1,
                                             Expression< T > o2 ) {
    return pickLess( o1, o2, true );
  }

  public static < T > T pickLessThan( Expression< T > o ) {
    return pickLess( o, false );
  }

  public static < T > T pickLessThanOrEqual( Expression< T > o ) {
    return pickLess( o, true );
  }

  public static < T > T pickLessThan( Expression< T > o,
                                      AbstractRangeDomain< T > domain ) {
    return pickLess( o, domain, false );
  }

  public static < T > T pickLessThanOrEqual( Expression< T > o,
                                             AbstractRangeDomain< T > domain ) {
    return pickLess( o, domain, true );
  }

  public static < T > T pickLess( Expression< T > o,
                                  AbstractRangeDomain< T > domain,
                                  boolean orEquals ) {
    T t = null;
    if ( o == null ) return null;
    T r = null;
    try {
      r = (T)Expression.evaluateDeep( o, null, false, false );
    } catch ( IllegalAccessException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    } catch ( InstantiationException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    }
    if ( r == null ) return null;
    t = domain.pickRandomValueLess( r, orEquals );
    return t;
  }

  public static < T > T pickLess( Expression< T > o, boolean orEquals ) {
    if ( o == null ) return null;
    Domain< T > d = o.getDomain( false, null );
    if ( d != null && d instanceof AbstractRangeDomain ) {
      Domain< T > dfd = d.getDefaultDomain();
      if ( dfd instanceof AbstractRangeDomain ) {
        return pickLess( o, (AbstractRangeDomain< T >)dfd, orEquals );
      }
    }
    return null;
  }

  public static < T > T pickLess( Expression< T > o1, Expression< T > o2,
                                  boolean orEquals ) {
    Domain< T > d = o1.getDomain( false, null );
    if ( d instanceof AbstractRangeDomain ) {
      AbstractRangeDomain< T > ard = (AbstractRangeDomain< T >)d;
      return pickLess( o2, ard, orEquals );
    }
    return pickLess( o2, orEquals );
  }

  // Picking Equals
  // ///////////////////////////////////////////////////////////////////

  public static < T > T pickEqualToForward( Expression< T > o1,
                                            Expression< T > o2 ) {

    return pickEquals( o1, o2, true );
  }

  public static < T > T pickEqualToReverse( Expression< T > o1,
                                            Expression< T > o2 ) {

    return pickEquals( o1, o2, false );
  }

  public static < T > T pickEquals( Expression< T > o1, Expression< T > o2,
                                    boolean forward ) {

    T t = null;

    if ( ( o1 != null ) && ( o2 != null ) ) {

      // If we are selection a value for the first arg then evaluate the second
      // arg expression,
      // otherwise due the reverse:
      try {
        Expression<T> target = forward ? o1 : o2;
        Expression<T> val = forward ? o2 : o1;
        t = (T)( val.evaluate( false ) );  // REVIEW -- Need to evaluate deep?
        // Don't pick values for timeline variables that are part of an expression.
        if ( t instanceof TimeVaryingMap ) {
          Parameter v = null;
          // See if the target is just a Parameter; otherwise, the variable is
          // in a larger expression, and we don't want to pick a value for it.
          try {
            v = Expression.evaluate( target, Parameter.class, false, false );
          } catch (Throwable e) {
            // ignore what is likely a ClassCastException when the target is not
            // just a Parameter.
          }
          if ( v == null ) t = null;
        }
      } catch ( IllegalAccessException e ) {
        // TODO Auto-generated catch block
        // e.printStackTrace();
      } catch ( InvocationTargetException e ) {
        // TODO Auto-generated catch block
        // e.printStackTrace();
      } catch ( InstantiationException e ) {
        // TODO Auto-generated catch block
        // e.printStackTrace();
      }

    }

    return t;
  }

  // Picking Not Equals
  // ///////////////////////////////////////////////////////////////////

  public static < T > T pickNotEqualToForward( Expression< T > o1,
                                               Expression< T > o2 ) {

    Domain< T > domain = o1.getDomain( false, null );
    return pickNotEquals( o2, domain );
  }

  public static < T > T pickNotEqualToReverse( Expression< T > o1,
                                               Expression< T > o2 ) {

    Domain< T > domain = o2.getDomain( false, null );
    return pickNotEquals( o1, domain );
  }

  public static < T > T pickNotEquals( Expression< T > o, Domain< T > domain ) {

    if ( o == null ) return null;
    T r = null;
    try {
      r = (T)o.evaluate( false );  // REVIEW -- Need to evaluate deep?
    } catch ( IllegalAccessException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    } catch ( InstantiationException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    }
    if ( r == null ) return null;
    T t = null;
    Object obj = domain.pickRandomValueNotEqual( r );
    try {
      t = (T)obj;
    } catch ( ClassCastException e ) {
      // ignore
    }
    return t;
  }

  // Picking for logical operators

  public static < T > T pickTrue( Object o, Variable< T > variableForPick ) {
    Expression< ? > e = null;
    if ( o instanceof Expression ) {
      e = (Expression< ? >)o;
    } else {
      e = new Expression( o );
    }
    if ( e.getType() != null
         && Boolean.class.isAssignableFrom( e.getType() ) ) {
      return pickTrue( (Expression< Boolean >)o, variableForPick );
    }
    return null;
  }

  public static < T > T pickTrue( Expression< Boolean > expr,
                                  Variable< T > variableForPick ) {
    if ( expr == null || expr.expression == null ) return null;
    switch ( expr.getForm() ) {
      case Constructor:
      case Function:
        Call c = (Call)expr.expression;
        // If the expression embeds a SuggestiveFunctionCall, call it.
        if ( c instanceof SuggestiveFunctionCall ) {
          ( (SuggestiveFunctionCall)c ).pickValue( variableForPick );
        } else {
          // Evaluate the function and see if we can pickFrom the result.
          try {
            Object o = c.evaluate( false );  // REVIEW -- Need to evaluate deep?
            return pickTrue( o, variableForPick );
          } catch ( IllegalAccessException e ) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
          } catch ( InvocationTargetException e ) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
          } catch ( InstantiationException e ) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
          }
          return null;
        }
      case Parameter:
        // This assumes that the Parameter and the Variable are the same.
        Parameter< Boolean > p = (Parameter< Boolean >)expr.expression;
        if ( variableForPick != null && variableForPick.equals( p ) ) {
          return (T)(Boolean)true;
        }
        return pickTrue( p.getValueNoPropagate(), variableForPick );
      case Value:
        return pickTrue( expr.expression, variableForPick );
      case None:
        return pickTrue( expr.expression, variableForPick );
      default:
        // TODO -- ERROR -- Bad Form
    };
    return null;
  }

  public static boolean pickTrue( Expression< Boolean > o1,
                                  Expression< Boolean > o2 ) {
    return true;
  }

  public static < T > T pickTrue( Expression< Boolean > o1,
                                  Expression< Boolean > o2,
                                  Variable< T > variableForPick ) {
    T t1 = pickTrue( o1, variableForPick );
    T t2 = pickTrue( o1, variableForPick );
    if ( t1 == null ) return t2;
    if ( t2 == null ) return t1;
    return Random.global.nextBoolean() ? t1 : t2;
  }

  // Picking Sum (Add/Plus are subtypes of Sum)
  // /////////////////////////////////////////
  // Picking Sub (Minus is subtype of Sub)
  // ////////////////////////////////////////////
  // Picking Times
  // ///////////////////////////////////////////////////////////////////
  // Picking Divide
  // ///////////////////////////////////////////////////////////////////
  // TODO is it okay to pick zero for Divide?

  public static < T > T pickValueForward( Expression< T > o1,
                                          Expression< T > o2 ) {

    Domain< T > domain = o1.getDomain( false, null );
    return pickRandomValueInDomain( domain );
  }

  public static < T > T pickValueReverse( Expression< T > o1,
                                          Expression< T > o2 ) {

    Domain< T > domain = o2.getDomain( false, null );
    return pickRandomValueInDomain( domain );
  }

  public static < T > T pickRandomValueInDomain( Domain< T > domain ) {

    T t = null;
    try {
      t = (T)domain.pickRandomValue();
    } catch ( ClassCastException e ) {
      // TODO???
    }
    return t;
  }

  public static < T, V1, V2 > Object
         equals( Expression< T > o1,
                 Expression< T > o2 ) throws IllegalAccessException,
                                      InvocationTargetException,
                                      InstantiationException {
    if ( o1 == o2 ) return true;
    // if ( o1 == null || o2 == null ) return false;
    T r1 = (T)( o1 == null ? null : o1.evaluate( false ) );  // REVIEW -- Need to evaluate deep?
    T r2 = (T)( o2 == null ? null : o2.evaluate( false ) );
    if ( Expression.valuesEqual( r1, r2 ) ) return true;
//    if ( r1 == null || r2 == null ) return false;
    Pair< Object, TimeVaryingMap< ? > > p1 = objectOrTimeline( r1 );
    Pair< Object, TimeVaryingMap< ? > > p2 = objectOrTimeline( r2 );
    TimeVaryingMap< ? > tvm1 = p1 == null ? null : p1.second;
    TimeVaryingMap< ? > tvm2 = p2 == null ? null : p2.second;
    TimeVaryingMap<Boolean> tvmResult = null;
    BooleanDomain domain = null;
    Boolean tvm = false;
    if ( tvm1 != null ) {
      tvm = true;
      if ( tvm2 != null ) {
        tvmResult = TimeVaryingMap.compare( tvm1, tvm2, Inequality.EQ );
      } else {
        tvmResult = compare( tvm1, r2, Inequality.EQ );

      }
    } else if ( tvm2 != null ) {
      tvm = true;
      tvmResult = compare( r1, tvm2, Inequality.EQ );
    } else {
      Object d1 = getDistribution( r1 );
      Object d2 = getDistribution( r2 );
      if (DistributionHelper.isDistribution(d1) || DistributionHelper.isDistribution(d2)) {
        //FIXME -- eqDistribution() doesn't work in most cases.
        //return eqDistribution(d1, d2);
        Object e = eqDistribution(d1 == null ? r1 : d1, d2 == null ? r2 : d2);
        if ( e != null ) {
          return e;
        }
      }
    }
    if (tvm && tvmResult != null) {
      // REVIEW - pulling a value out of a constant TVM is now handled in Expression,
      // which has the context to better decide whether to do so. Should it be left here too?
      boolean allSame = tvmResult.allValuesSame();
      if (allSame) {
        if (!tvmResult.isEmpty()) {
          if (tvmResult.getValue( (long )0) != null) {
            if (tvmResult.getValue( (long)0).equals( true )) {
              return true;
            }
          }
          
          return false;
        } else {
          return true;
        }
      } else {
        return tvmResult;
      }
    }
    return eq( r1, r2 );
  }

  protected static Object getDistribution( Object o ) {
    return getDistribution( o, null );
  }
  protected static Object getDistribution( Object o, Set<Object> seen ) {
    if ( o == null ) return null;

    Pair< Boolean, Set< Object > > pair = Utils.seen( o, true, seen );
    if ( pair.first ) return null;
    seen = pair.second;

    if ( o instanceof Distribution ) {
      return o;
    }
    if ( o instanceof Wraps ) { // This covers expressions, parameters, calls, and TimeVaryingMaps.
      Object oo = ((Wraps)o).getValue( false );
      Object d = getDistribution( oo, seen ); // warning! infinite loop
      if ( d != null ) {
        return d;
      }
    }
    return null;
  }

  protected static BooleanDistribution eqDistribution(Object o1, Object o2){
    boolean isDist1 = DistributionHelper.isDistribution(o1);
    boolean isDist2 = DistributionHelper.isDistribution(o2);
    BooleanDistribution b = null;
    if (isDist1 || isDist2) {
      b = DistributionHelper.equals(o1,o2);
    }
    return b;
  }

  public static <T> Boolean eq( T r1, T r2 ) {
    if ( Expression.valuesEqual( r1, r2, null, true, true )) return true;
    if ( Utils.valuesLooselyEqual( r1, r2, true ) ) return true;
    if ( r1 == null || r2 == null ) return false;
    boolean b = false;
    b = CompareUtils.compare( r1, r2, false ) == 0;
    if ( !b ) {
      boolean isNum1 = r1 instanceof Number;
      boolean isNum2 = r2 instanceof Number;
      if ( isNum1 && isNum2 ) {
        b = DoubleDomain.defaultDomain.equals( ( (Number)r1 ).doubleValue(),
                                               ( (Number)r2 ).doubleValue() );
      }
    }
    if ( !b && r1 instanceof Comparable ) {
      if ( r1 instanceof Parameter && !( r2 instanceof Parameter ) ) {
        b = ( (Parameter< ? >)r1 ).valueEquals( r2 );
      } else if ( r2 instanceof Parameter && !( r1 instanceof Parameter ) ) {
        b = ( (Parameter< ? >)r2 ).valueEquals( r1 );
      } else {
        if ( r2 == null ) b = false;
        else {
          try {
            b = ( (Comparable< T >)r1 ).compareTo( r2 ) == 0;
          } catch ( Throwable t ) {
            b = false;
          }
        }
      }
    } else {
      b = b || r1.equals( r2 );
    }
    if ( !b ) {
      Object r11 = null;
      Object r22 = null;
      boolean changed1 = false;
      boolean changed2 = false;
      if ( r1 instanceof Wraps ) {
        r11 = ( (Wraps< ? >)r1 ).getValue( false );
        changed1 = !r1.equals( r11 );
        if ( changed1 ) b = eq( r11, r2 );
      }
      if ( !b && r2 instanceof Wraps ) {
        r22 = ( (Wraps< ? >)r2 ).getValue( false );
        changed2 = !r2.equals( r22 );
        if ( changed2 ) b = eq( r22, r1 );
        if ( !b && r11 != null ) {
          if ( changed1 && changed2 ) {
            b = eq( r11, r22 );
          }
        }
      }
    }
    if ( Debug.isOn() ) Debug.outln( "eq(): " + r1 + " == " + r2 + " = " + b );
    return b;
  }

  public static < T > Object
         notEquals( Expression< T > o1,
                    Expression< T > o2 ) throws IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    T r1 = (T)( o1 == null ? null : o1.evaluate( false ) );  // REVIEW -- Need to evaluate deep?
    T r2 = (T)( o2 == null ? null : o2.evaluate( false ) );
    Pair< Object, TimeVaryingMap< ? > > p1 = objectOrTimeline( r1 );
    Pair< Object, TimeVaryingMap< ? > > p2 = objectOrTimeline( r2 );
    TimeVaryingMap< ? > tvm1 = p1 == null ? null : p1.second;
    TimeVaryingMap< ? > tvm2 = p2 == null ? null : p2.second;
    if ( tvm1 != null ) {
      if ( tvm2 != null ) {
        return TimeVaryingMap.compare( tvm1, tvm2, Inequality.NEQ );
      }
      return compare( tvm1, r2, Inequality.NEQ );
    } else if ( tvm2 != null ) {
      return compare( r1, tvm2, Inequality.NEQ );
    }
    Boolean b = !eq( o1, o2 );
    if ( Debug.isOn() ) Debug.outln( o1 + " != " + o2 + " = " + b );
    return b;
  }

  // Logic functions

  public static class And extends BooleanBinary< Boolean > {
    public And( Expression< Boolean > o1, Expression< Boolean > o2 ) {
      super( o1, o2, "and", "pickTrue", "pickTrue" );
      setMonotonic( true );
    }

    public And( Expression< Boolean > o1, FunctionCall o2 ) {
      super( o1, o2, "and", "pickTrue", "pickTrue" );
      setMonotonic( true );
    }

    public And( FunctionCall o2, Expression< Boolean > o1 ) {
      super( o1, o2, "and", "pickTrue", "pickTrue" );
      setMonotonic( true );
    }

    public And( And a ) {
      super( a );
      setMonotonic( true );
    }

    public And clone() {
      return new And( this );
    }

    @Override
    public < TT > Pair< Domain< TT >, Boolean >
           restrictDomain( Domain< TT > domain, boolean propagate,
                           Set< HasDomain > seen ) {
      return super.restrictDomain( domain, propagate, seen );
    }
    
    @Override
    public Domain< ? > calculateDomain( boolean propagate,
                                        Set< HasDomain > seen ) {
      return super.calculateDomain( propagate, seen );
    }

    /**
     * The inverse for And must evaluate the argument to the specified
     * returnValue, so we want the argument to be the same as the returnValue.
     * Thus, Equals is the inverse.
     * 
     * @see gov.nasa.jpl.ae.event.Functions.SuggestiveFunctionCall#inverseSingleValue(java.lang.Object,
     *      java.lang.Object)
     */
    @Override
    public // < T1 extends Comparable< ? super T1 > >
    FunctionCall inverseSingleValue( Object returnValue, Object arg ) {

      if ( arguments == null || arguments.size() != 2 ) return null;
      // Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 ) :
      // arguments.get( 1 ) );
      if ( returnValue == null // || otherArg == null
      ) return null; // arg can be null!
      return new Conditional(forceExpression(returnValue), forceExpression( new BooleanDomain(true, true) ), forceExpression( new BooleanDomain() ) );
    }

  }
  // REVIEW -- Should a propagate flag be added? Currently false.
  /*
   * public static Object and(Expression<Boolean> o1, Expression<Boolean> o2)
   * throws IllegalAccessException, InvocationTargetException,
   * InstantiationException { if ( o1 == null && o2 == null ) return null;
   * Object r1 = (o1 == null ? null : o1.evaluate( false )); Object r2 = (o2 ==
   * null ? null : o2.evaluate( false )); return and( r1, r2 ); }
   * 
   * public static < V1, V2 > Object and( V1 o1, V2 o2 ) throws
   * IllegalAccessException, InvocationTargetException, InstantiationException {
   * Object result = null; if ( o1 == null || o2 == null ) result = null; else
   * if ( o1 instanceof String || o2 instanceof String ) { Boolean b1 =
   * Utils.isTrue( o1, true ); Boolean b2 = Utils.isTrue( o2, true ); result =
   * and( b1, b2 ); } else { Boolean r1 = null; Boolean r2 = null;
   * TimeVaryingMap< ? > map1 = null; TimeVaryingMap< ? > map2 = null;
   * 
   * Pair< Boolean, TimeVaryingMap< ? > > p1 = booleanOrTimeline( o1 ); r1 =
   * p1.first; map1 = p1.second;
   * 
   * if ( map1 != null ) { result = (V1)and( map1, o2 ); } else { Pair< Boolean,
   * TimeVaryingMap< ? > > p2 = booleanOrTimeline( o2 ); r2 = p2.first; map2 =
   * p2.second;
   * 
   * if ( map2 != null ) { result = (V1)and( o1, map2 ); } } if ( result == null
   * ) { if ( ( r1 != null && !r1 ) || ( r2 != null && !r2 ) ) result = false;
   * else if ( r1 == null || r2 == null ) result = null; else result = r1 && r2;
   * } } if ( Debug.isOn() ) Debug.outln( o1 + " && " + o2 + " = " + result );
   * return result; }
   */

  public static class Or extends BooleanBinary< Boolean > {
    public Or( Expression< Boolean > o1, Expression< Boolean > o2 ) {
      super( o1, o2, "or", "pickTrue", "pickTrue" );
      setMonotonic( true );
    }

    public Or( Expression< Boolean > o1, FunctionCall o2 ) {
      super( o1, o2, "or", "pickTrue", "pickTrue" );
      setMonotonic( true );
    }

    public Or( Or a ) {
      super( a );
      setMonotonic( true );
    }

    public Or clone() {
      return new Or( this );
    }

    /*
     * (non-Javadoc)
     * 
     * @see gov.nasa.jpl.ae.event.Expression#isGrounded(boolean, java.util.Set)
     */
    @Override
    public boolean isGrounded( boolean deep, Set< Groundable > seen ) {
      return super.isGrounded(deep, seen);
//      try {
//        return evaluate( false ) != null;
//      } catch ( IllegalAccessException e ) {
//      } catch ( InvocationTargetException e ) {
//      } catch ( InstantiationException e ) {
//      }
//      return false;
    }

    /**
     * The inverse for Or must evaluate the argument to the specified
     * returnValue, so we want the argument to be the same as the returnValue.
     * Thus, Equals is the inverse.
     * 
     * @see gov.nasa.jpl.ae.event.Functions.SuggestiveFunctionCall#inverseSingleValue(java.lang.Object,
     *      java.lang.Object)
     */
    @Override
    public // < T1 extends Comparable< ? super T1 > >
    FunctionCall inverseSingleValue( Object returnValue, Object arg ) {

      if ( arguments == null || arguments.size() != 2 ) return null;
      // Object otherArg = ( arg == arguments.get( 1 ) ? arguments.get( 0 ) :
      // arguments.get( 1 ) );
      if ( returnValue == null // || otherArg == null
      ) return null; // arg can be null!
      return new Conditional(forceExpression(returnValue), forceExpression( new BooleanDomain() ), forceExpression( new BooleanDomain(false, false) ) );
    }

  }

  /*
   * public static Object or(Expression<Boolean> o1, Expression<Boolean> o2)
   * throws IllegalAccessException, InvocationTargetException,
   * InstantiationException { if ( o1 == null && o2 == null ) return null;
   * Object r1 = (o1 == null ? null : o1.evaluate( false )); Object r2 = (o2 ==
   * null ? null : o2.evaluate( false )); return or( r1, r2 ); }
   * 
   * 
   * public static < V1, V2 > Object or( V1 o1, V2 o2 ) throws
   * IllegalAccessException, InvocationTargetException, InstantiationException {
   * Object result = null; if ( o1 == null || o2 == null ) result = null; else
   * if ( o1 instanceof String || o2 instanceof String ) { Boolean b1 =
   * Utils.isTrue( o1, true ); Boolean b2 = Utils.isTrue( o2, true ); result =
   * or( b1, b2 ); } else { Boolean r1 = null; Boolean r2 = null;
   * TimeVaryingMap< ? > map1 = null; TimeVaryingMap< ? > map2 = null;
   * 
   * Pair< Boolean, TimeVaryingMap< ? > > p1 = booleanOrTimeline( o1 ); r1 =
   * p1.first; map1 = p1.second;
   * 
   * if ( map1 != null ) { result = (V1)or( map1, o2 ); } else { Pair< Boolean,
   * TimeVaryingMap< ? > > p2 = booleanOrTimeline( o2 ); r2 = p2.first; map2 =
   * p2.second;
   * 
   * if ( map2 != null ) { result = (V1)or( o1, map2 ); } } if ( result == null
   * ) { if ( ( r1 != null && r1 ) || ( r2 != null && r2 ) ) result = true; else
   * if ( r1 == null || r2 == null ) result = null; else result = r1 || r2; } }
   * if ( Debug.isOn() ) Debug.outln( o1 + " || " + o2 + " = " + result );
   * return result; }
   */

  public static class Xor extends BooleanBinary< Boolean > {
    public Xor( Expression< Boolean > o1, Expression< Boolean > o2 ) {
      super( o1, o2, "xor" );
    }

    public Xor( Expression< Boolean > o1, FunctionCall o2 ) {
      super( o1, o2, "xor" );
    }

    public Xor( Xor a ) {
      super( a );
    }

    public Xor clone() {
      return new Xor( this );
    }

  }
  // public static Boolean
  // xor( Expression< Boolean > o1, Expression< Boolean > o2 ) throws
  // IllegalAccessException, InvocationTargetException, InstantiationException {
  // if ( o1 == null || o2 == null ) return null;
  // Boolean r1 = (Boolean)o1.evaluate( false );
  // Boolean r2 = (Boolean)o2.evaluate( false );
  // if ( r1 == null || r2 == null ) return null;
  // boolean b = ( r1 ^ r2 );
  // if ( Debug.isOn() ) Debug.outln( o1 + " ^ " + o2 + " = " + b );
  // return b;
  // }
  /*
   * public static Object xor(Expression<Boolean> o1, Expression<Boolean> o2)
   * throws IllegalAccessException, InvocationTargetException,
   * InstantiationException { if ( o1 == null && o2 == null ) return null;
   * Object r1 = (o1 == null ? null : o1.evaluate( false )); Object r2 = (o2 ==
   * null ? null : o2.evaluate( false )); return xor( r1, r2 ); }
   * 
   * 
   * public static < V1, V2 > Object xor( V1 o1, V2 o2 ) throws
   * IllegalAccessException, InvocationTargetException, InstantiationException {
   * Object result = null; if ( o1 == null || o2 == null ) result = null; else
   * if ( o1 instanceof String || o2 instanceof String ) { Boolean b1 =
   * Utils.isTrue( o1, true ); Boolean b2 = Utils.isTrue( o2, true ); result =
   * xor( b1, b2 ); } else { Boolean r1 = null; Boolean r2 = null;
   * TimeVaryingMap< ? > map1 = null; TimeVaryingMap< ? > map2 = null;
   * 
   * Pair< Boolean, TimeVaryingMap< ? > > p1 = booleanOrTimeline( o1 ); r1 =
   * p1.first; map1 = p1.second;
   * 
   * if ( map1 != null ) { result = (V1)xor( map1, o2 ); } else { Pair< Boolean,
   * TimeVaryingMap< ? > > p2 = booleanOrTimeline( o2 ); r2 = p2.first; map2 =
   * p2.second;
   * 
   * if ( map2 != null ) { result = (V1)xor( o1, map2 ); } } if ( result == null
   * ) { if ( r1 != null && r2 != null ) { result = r1 ^ r2; } else result =
   * null; } } if ( Debug.isOn() ) Debug.outln( o1 + " ^ " + o2 + " = " + result
   * ); return result; }
   */

  public static class Not extends Unary< Boolean, Boolean >
                          implements Suggester {
    public Not( Expression< Boolean > o ) {
      super( o, "not" );
      setMonotonic( true );
    }

    public Not( Not a ) {
      super( a );
      setMonotonic( true );
    }

    public Not clone() {
      return new Not( this );
    }

    @Override
    public < T > T pickValue( Variable< T > variable ) {
      Object arg = // ((FunctionCall)this.expression).
          getArgument( 0 );
      if ( arg == variable ) {
        return (T)(Boolean)false;
      }
      return pickValueE( variable, arg );
      // else if ( arg instanceof Suggester ) {
      // return (T)(Boolean)(!((Boolean)((Suggester)arg).pickValue( variable
      // )));
      // }
      // return null;
    }
  }

  // public static Boolean not( Expression< Boolean > o ) throws
  // IllegalAccessException, InvocationTargetException, InstantiationException {
  // if ( o == null ) return null;
  // Boolean r = (Boolean)o.evaluate( false );
  // if ( r == null ) return null;
  // boolean b = !r;
  // if ( Debug.isOn() ) Debug.outln( "!" + o + " = " + b );
  // return b;
  // }

  /*
   * public static Object not(Expression<Boolean> o) throws
   * IllegalAccessException, InvocationTargetException, InstantiationException {
   * if ( o == null ) return null; Object r = (o == null ? null : o.evaluate(
   * false )); return not( r ); }
   * 
   * 
   * public static < V > Object not( V o ) throws IllegalAccessException,
   * InvocationTargetException, InstantiationException { Object result = null;
   * if ( o == null ) result = null; else if ( o instanceof String ) { Boolean b
   * = Utils.isTrue( o, true ); result = not( b ); } else { Boolean r = null;
   * TimeVaryingMap< ? > map = null;
   * 
   * Pair< Boolean, TimeVaryingMap< ? > > p = booleanOrTimeline( o ); r =
   * p.first; map = p.second;
   * 
   * if ( map != null ) { result = (V)not( map ); } if ( result == null ) { if (
   * r != null ) result = !r; } } if ( Debug.isOn() ) Debug.outln( "!" + o +
   * " = " + result ); return result; }
   * 
   */


  public static class IsInstanceOf extends BooleanBinary< Object > {

    public IsInstanceOf( Expression<Object> o1, Expression<Object> o2 ) {
      super( o1, o2, "isInstanceOf", "pickValueForward", "pickValueReverse" );
      setMonotonic( false );
    }

    public IsInstanceOf( Object o1, Object o2 ) {
      super( o1, o2, "isInstanceOf", "pickValueForward", "pickValueReverse" );
      setMonotonic( false );
    }

    public IsInstanceOf( IsInstanceOf a ) {
      super( a );
      setMonotonic( false );
    }

    public IsInstanceOf clone() {
      return new IsInstanceOf( this );
    }

    // TODO -- add the inverse and other functions!

    protected Class getClassToCheck() {
      if ( arguments.size() < 2 ) return null;
      try {
        return getClassFromArgument( arguments.get(1) );
      } catch ( Throwable e ) {
      }
      return null;
    }

    @Override
    public Domain<?> calculateDomain( boolean propagate, Set<HasDomain> seen ) {
      // TODO -- If the class argument is a variable instead of a
      if (arguments.size() < 2) return null;
      Object obj = arguments.get( 0 );
      Object classArg = arguments.get( 1 );
      Domain<?> cd = DomainHelper.getDomain( classArg );
      Domain<?> d = DomainHelper.getDomain( obj );
      Object o = null;
      Class cls = null;
      if ( cd != null && cd.magnitude() == 1 ) {
        Object clsObj = cd.getValue( false );
        if ( clsObj instanceof Class ) {
          cls = (Class)clsObj;
        }
      } else if ( cd.magnitude() > 1 ) {
        // TODO
        Debug.error(true, false, "IsInstanceOf.calculateDomain() does not handle class arguments whose domains are size greater than 1: " + cd);
        return BooleanDomain.defaultDomain;
      }
      if ( cls == null ) {
        cls = getClassToCheck();
      }
      if ( d != null && d.magnitude() == 1 ) {
        o = d.getValue( false );
      } else if ( d instanceof ClassDomain ) {
        Object objClassObj = d.getType();
        if ( objClassObj instanceof Class ) {
          if ( cls.isAssignableFrom( (Class)objClassObj ) ) {
            return BooleanDomain.trueDomain;
          } else if ( ( (Class)objClassObj ).isAssignableFrom( cls ) ) {
            return BooleanDomain.defaultDomain;
          } else {
            return BooleanDomain.falseDomain;
          }
        } else {
          Debug.error(true, false, "IsInstanceOf.calculateDomain(): failed to get class of ClassDomain: " + d);
          return BooleanDomain.defaultDomain;
        }
      }
      if ( cls != null && o != null ) {
        Object r = null;
        try {
          r = isInstanceOf( o, cls );
        } catch ( Throwable e ) {
        }
        if ( Utils.isTrue( r ) ) return BooleanDomain.trueDomain;
        if ( Utils.isFalse( r ) ) return BooleanDomain.falseDomain;
      }
      return BooleanDomain.defaultDomain;
    }
  }

  public static class Cast extends Binary< Object, Object > {

    public Cast( Expression<Object> o1, Expression<Object> o2 ) {
      super( o1, o2, "cast", "pickValueForward", "pickValueReverse" );
      setMonotonic( false );
    }

    public Cast( Object o1, Object o2 ) {
      super( o1, o2, "cast", "pickValueForward", "pickValueReverse" );
      setMonotonic( false );
    }

    public Cast( Cast a ) {
      super( a );
      setMonotonic( false );
    }

    public Cast clone() {
      return new Cast( this );
    }

    // TODO -- add the inverse and other functions!

  }


  // TimeVaryingMap functions

  public static < T > TimeVaryingMap< Boolean >
         compare( Object o, TimeVaryingMap< T > tv,
                  Inequality i ) throws ClassCastException,
                                 IllegalAccessException,
                                 InvocationTargetException,
                                 InstantiationException {
    return compare( tv, o, i, true );
  }

  public static < T > TimeVaryingMap< Boolean >
  compare( TimeVaryingMap< T > tv, Object o,
           Inequality i ) throws ClassCastException,
                                 IllegalAccessException,
                                 InvocationTargetException,
                                 InstantiationException {
    return compare( tv, o, i, false );
  }
  public static < T > TimeVaryingMap< Boolean >
         compare( TimeVaryingMap< T > tv, Object o,
                  Inequality i, boolean reverse ) throws ClassCastException,
                                 IllegalAccessException,
                                 InvocationTargetException,
                                 InstantiationException {
    if ( tv == null || o == null ) return null;

    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable t ) {}
    if ( tvm != null ) {
      if ( reverse ) return compare( tvm, (TimeVaryingMap<? extends Number>)tv, i );
      return compare( tv, tvm, i );
    }

    Number n = null;
    try {
      n = toNumber( o, false );// Expression.evaluate( o, Number.class, false );
    } catch ( Throwable t ) {}
    if ( n != null ) return TimeVaryingMap.compare( tv, n, reverse, i );
    
    Object typeMatch = null;
    try {
      typeMatch = Expression.evaluate( o, tv.getType(), false );
      // TODO - should we try to just evaluate o regardless of type, to strip away any Wrapping classes?
      // Or, does this already handle enough cases?
    } catch ( Throwable t ) {}
    if (typeMatch != null) return TimeVaryingMap.compare( tv, typeMatch, reverse, i );

    return TimeVaryingMap.compare( tv, o, reverse, i );
  }

  public static < T, TT extends Number > TimeVaryingMap< Boolean >
         compare( TimeVaryingMap< T > tv1, TimeVaryingMap< TT > tv2,
                  Inequality i ) throws ClassCastException,
                                 IllegalAccessException,
                                 InvocationTargetException,
                                 InstantiationException {
    return TimeVaryingMap.compare( tv1, tv2, i );
  }

  public static < T > TimeVaryingMap< Boolean >
         lessThan( Object o, TimeVaryingMap< T > tv ) throws ClassCastException,
                                                      IllegalAccessException,
                                                      InvocationTargetException,
                                                      InstantiationException {
    return compare( o, tv, Inequality.LT );
  }

  public static < T > TimeVaryingMap< Boolean >
         lessThan( TimeVaryingMap< T > tv, Object o ) throws ClassCastException,
                                                      IllegalAccessException,
                                                      InvocationTargetException,
                                                      InstantiationException {
    return compare( tv, o, Inequality.LT );
    // if ( tv == null || o == null ) return null;
    // Pair< Number, TimeVaryingMap< ? > > p = numberOrTimeline( o );
    // if ( tvm != null ) return lessThan( tv, tvm );
    //
    // Number n = p.first;
    // TimeVaryingMap<?> tvm = p.second;
    // try {
    // n = toNumber( o, false );// Expression.evaluate( o, Number.class, false
    // );
    // } catch ( Throwable t ) {}
    // if ( n != null ) return tv.lessThanClone( n );
    // TimeVaryingMap< ? extends Number > tvm = null;
    // try {
    // tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    // } catch ( Throwable t ) {}
    // return null;
  }

  public static < T, TT extends Number > TimeVaryingMap< Boolean >
         lessThan( TimeVaryingMap< T > tv1,
                   TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                              IllegalAccessException,
                                              InvocationTargetException,
                                              InstantiationException {
    return compare( tv1, tv2, Inequality.LT );
  }

  public static < T > TimeVaryingMap< Boolean >
         lessThanOrEqual( Object o,
                          TimeVaryingMap< T > tv ) throws ClassCastException,
                                                   IllegalAccessException,
                                                   InvocationTargetException,
                                                   InstantiationException {
    return compare( o, tv, Inequality.LTE );
  }

  public static < T > TimeVaryingMap< Boolean >
         lessThanOrEqual( TimeVaryingMap< T > tv,
                          Object o ) throws ClassCastException,
                                     IllegalAccessException,
                                     InvocationTargetException,
                                     InstantiationException {
    return compare( tv, o, Inequality.LT );
    // if ( tv == null || o == null ) return null;
    // Number n = null;
    // try {
    // n = toNumber( o, false );// Expression.evaluate( o, Number.class, false
    // );
    // } catch ( Throwable t ) {}
    // if ( n != null ) return tv.lessThanOrEqualClone( n );
    // TimeVaryingMap< ? extends Number > tvm = null;
    // try {
    // tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    // } catch ( Throwable t ) {}
    // if ( tvm != null ) return lessThanOrEqual( tv, tvm );
    // return null;
  }

  public static < T, TT extends Number > TimeVaryingMap< Boolean >
         lessThanOrEqual( TimeVaryingMap< T > tv1,
                          TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                                     IllegalAccessException,
                                                     InvocationTargetException,
                                                     InstantiationException {
    return compare( tv1, tv2, Inequality.LTE );
  }

  public static < T > TimeVaryingMap< T >
         min( Object o, TimeVaryingMap< T > tv ) throws ClassCastException,
                                                 IllegalAccessException,
                                                 InvocationTargetException,
                                                 InstantiationException {
    return min( tv, o );
  }

  public static < T > TimeVaryingMap< T >
         min( TimeVaryingMap< T > tv,
              Object o ) throws ClassCastException, IllegalAccessException,
                         InvocationTargetException, InstantiationException {
    if ( tv == null || o == null ) return null;

    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable t ) {}
    if ( tvm != null ) return min( tv, tvm );

    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable t ) {}
    if ( n != null ) return tv.minClone( n );
    return null;
  }

  public static < T, TT extends Number > TimeVaryingMap< T >
         min( TimeVaryingMap< T > tv1,
              TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                         IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    return TimeVaryingMap.min( tv1, tv2 );
  }

  public static < T > TimeVaryingMap< T >
         max( Object o, TimeVaryingMap< T > tv ) throws ClassCastException,
                                                 IllegalAccessException,
                                                 InvocationTargetException,
                                                 InstantiationException {
    return max( tv, o );
  }

  public static < T > TimeVaryingMap< T >
         max( TimeVaryingMap< T > tv,
              Object o ) throws ClassCastException, IllegalAccessException,
                         InvocationTargetException, InstantiationException {
    if ( tv == null || o == null ) return null;

    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable t ) {}
    if ( tvm != null ) return max( tv, tvm );

    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable t ) {}
    if ( n != null ) return tv.maxClone( n );
    return null;
  }

  public static < T, TT extends Number > TimeVaryingMap< T >
         max( TimeVaryingMap< T > tv1,
              TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                         IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    return TimeVaryingMap.max( tv1, tv2 );
  }

  public static < L, T > TimeVaryingMap< L >
         argminormax( L l1, Object o, L l2, TimeVaryingMap< T > tv,
                      boolean isMin ) throws ClassCastException,
                                      IllegalAccessException,
                                      InvocationTargetException,
                                      InstantiationException {
    return argminormax( l2, tv, l1, o, isMin );
  }

  public static < L, T > TimeVaryingMap< L >
         argminormax( L l1, TimeVaryingMap< T > tv, L l2, Object o,
                      boolean isMin ) throws ClassCastException,
                                      IllegalAccessException,
                                      InvocationTargetException,
                                      InstantiationException {
    if ( tv == null || o == null ) return null;

    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable t ) {}
    if ( tvm != null ) return TimeVaryingMap.argminormax( l1, tv, l2, tvm,
                                                          isMin );

    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable t ) {}
    if ( n != null ) return TimeVaryingMap.argminormax( l1, tv, l2, n, isMin );

    return null;
  }

  public static < L, T, TT extends Number > TimeVaryingMap< L >
         argmax( L l1, TimeVaryingMap< T > tv1, L l2,
                 TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                            IllegalAccessException,
                                            InvocationTargetException,
                                            InstantiationException {
    return TimeVaryingMap.argmax( l1, tv1, l2, tv2 );
  }

  // public static < T > TimeVaryingMap< T > times( Object o1,
  // Object o2 ) {
  // if ( o1 == null || o2 == null ) return null;
  // Number n = Expression.evaluate( o1, Number.class, false );
  // if ( n != null ) return times( n, o2 );
  // TimeVaryingMap< ? extends Number > tvm =
  // Expression.evaluate( o, TimeVaryingMap.class, false );
  // if ( tvm != null ) return times( tv, tvm );
  // return null;
  // }
  public static < T > TimeVaryingMap< T >
         times( Object o, TimeVaryingMap< T > tv ) throws ClassCastException,
                                                   IllegalAccessException,
                                                   InvocationTargetException,
                                                   InstantiationException {
    return times( tv, o );
  }

  public static < T > TimeVaryingMap< T >
         times( TimeVaryingMap< T > tv,
                Object o ) throws ClassCastException, IllegalAccessException,
                           InvocationTargetException, InstantiationException {
    Pair< Number, TimeVaryingMap< ? > > p = numberOrTimeline( o );
    Number n = p.first;
    TimeVaryingMap< ? extends Number > tvm =
        (TimeVaryingMap< ? extends Number >)p.second;
    if ( n != null ) return tv.times( n );
    if ( tvm != null ) return times( tv, tvm );
    return null;
  }

  public static < T, TT extends Number > TimeVaryingMap< T >
         times( TimeVaryingMap< T > tv1,
                TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                           IllegalAccessException,
                                           InvocationTargetException,
                                           InstantiationException {
    return TimeVaryingMap.times( tv1, tv2 );
  }

  public static < T > TimeVaryingMap< T >
         pow( Object o, TimeVaryingMap< T > tv ) throws ClassCastException,
                                                 IllegalAccessException,
                                                 InvocationTargetException,
                                                 InstantiationException {
    if ( tv == null || o == null ) return null;

    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( tvm != null ) return (TimeVaryingMap< T >)pow( tvm, tv );

    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( n != null ) return tv.npow( n );
    return null;
  }

  public static < T > TimeVaryingMap< T >
         pow( TimeVaryingMap< T > tv,
              Object o ) throws ClassCastException, IllegalAccessException,
                         InvocationTargetException, InstantiationException {
    if ( tv == null || o == null ) return null;

    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( tvm != null ) return pow( tv, tvm );

    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( n != null ) return tv.pow( n );

    return null;
  }

  public static < T, TT > TimeVaryingMap< T >
         pow( TimeVaryingMap< T > tv1,
              TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                         IllegalAccessException,
                                         InvocationTargetException,
                                         InstantiationException {
    return TimeVaryingMap.pow( tv1, tv2 );
  }

  public static < T > TimeVaryingMap< T >
         divide( Object o, TimeVaryingMap< T > tv ) throws ClassCastException,
                                                    IllegalAccessException,
                                                    InvocationTargetException,
                                                    InstantiationException {
    if ( tv == null || o == null ) return null;

    TimeVaryingMap< T > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( tvm != null ) return divideMap( tvm, tv );

    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( n != null ) return TimeVaryingMap.dividedBy( n, tv );

    return null;
  }

  public static < T > TimeVaryingMap< T >
         divide( TimeVaryingMap< T > tv,
                 Object o ) throws ClassCastException, IllegalAccessException,
                            InvocationTargetException, InstantiationException {
    if ( tv == null || o == null ) return null;

    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( tvm != null ) return divideMap( tv, tvm );

    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable e ) {
      // ignore
    }
    if ( n != null ) return tv.dividedBy( n );

    return null;
  }

  public static < T, TT > TimeVaryingMap< T >
         divideMap( TimeVaryingMap< T > tv1,
                    TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                               IllegalAccessException,
                                               InvocationTargetException,
                                               InstantiationException {
    return TimeVaryingMap.dividedBy( tv1, tv2 );
  }

  public static < T > TimeVaryingMap< T >
         plus( Object o, TimeVaryingMap< T > tv ) throws ClassCastException,
                                                  IllegalAccessException,
                                                  InvocationTargetException,
                                                  InstantiationException {
    return plus( tv, o );
  }

  public static < T > TimeVaryingMap< T >
         plus( TimeVaryingMap< T > tv,
               Object o ) throws ClassCastException, IllegalAccessException,
                          InvocationTargetException, InstantiationException {
    if ( tv == null || o == null ) return null;
    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable t ) {}
    if ( n != null ) return tv.plus( n );
    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable t ) {}
    if ( tvm != null ) return plus( tv, tvm );
    return null;
  }

  public static < T, TT extends Number > TimeVaryingMap< T >
         plus( TimeVaryingMap< T > tv1,
               TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                          IllegalAccessException,
                                          InvocationTargetException,
                                          InstantiationException {
    return TimeVaryingMap.plus( tv1, tv2 );
  }

//  public static < T > TimeVaryingMap< T >
//         minus( Object o, TimeVaryingMap< T > tv ) throws ClassCastException,
//                                                   IllegalAccessException,
//                                                   InvocationTargetException,
//                                                   InstantiationException {
//    return minus( tv, o );
//  }

  public static < T > TimeVaryingMap< T >
         minus( TimeVaryingMap< T > tv,
                Object o ) throws ClassCastException, IllegalAccessException,
                           InvocationTargetException, InstantiationException {
    if ( tv == null || o == null ) return null;
    Number n = null;
    try {
      n = Expression.evaluate( o, Number.class, false );
    } catch ( Throwable t ) {}
    if ( n != null ) return tv.minus( n );
    TimeVaryingMap< ? extends Number > tvm = null;
    try {
      tvm = Expression.evaluate( o, TimeVaryingMap.class, false );
    } catch ( Throwable t ) {}
    if ( tvm != null ) return minus( tv, tvm );
    return null;
  }

  public static < T, TT extends Number > TimeVaryingMap< T >
         minus( TimeVaryingMap< T > tv1,
                TimeVaryingMap< TT > tv2 ) throws ClassCastException,
                                           IllegalAccessException,
                                           InvocationTargetException,
                                           InstantiationException {
    return TimeVaryingMap.minus( tv1, tv2 );
  }

  public static < T1 > T1 pickValueE( Variable< T1 > variable, Object object ) {
    if ( object instanceof Suggester ) {
      return (T1)( (Suggester)object ).pickValue( variable );
    } else if ( object instanceof Expression
                && ( (Expression< ? >)object ).expression instanceof Suggester ) {
      return (T1)( (Suggester)( (Expression< ? >)object ).expression ).pickValue( variable );
    }
    return null;
  }

  protected static < T, T1 > T1 pickValueBB2( BooleanBinary< T > booleanBinary,
                                              Variable< T1 > variable ) {
    T1 newValue = pickValueBF2( variable,
                                booleanBinary.// functionCall.
                                    pickFunctionCall,
                                booleanBinary.// functionCall.
                                    reversePickFunctionCall );
    return newValue;
  }

  protected static < T1 > T1 pickValueBF2( Variable< T1 > variable,
                                           FunctionCall pickFunctionCall ) {
    try {
      return (T1)pickFunctionCall.evaluate( false );
    } catch ( IllegalAccessException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    } catch ( InstantiationException e ) {
      // TODO Auto-generated catch block
      // e.printStackTrace();
    }
    return null;
  }

  protected static < T1 > T1 pickValueBF2( SuggestiveFunctionCall functionCall,
                                           Variable< T1 > variable ) {
    T1 newValue = pickValueBF2( variable, functionCall.pickFunctionCall,
                                functionCall.reversePickFunctionCall );
    return newValue;
  }

  public static Object getArgumentWithVariable( FunctionCall fCall,
                                                Variable< ? > variable,
                                                boolean mustBeOnlyOne ) {
    ArrayList< Object > list =
        getArgumentsWithVariable( fCall, variable, true, mustBeOnlyOne );
    if ( Utils.isNullOrEmpty( list ) ) return null;
    return list.get( 0 );
  }

  public static ArrayList< Object >
         getArgumentsWithVariable( FunctionCall fCall,
                                   Variable< ? > variable ) {
    ArrayList< Object > list =
        getArgumentsWithVariable( fCall, variable, false, false );
    return list;
  }

  public static ArrayList< Object >
         getArgumentsWithVariable( FunctionCall fCall, Variable< ? > variable,
                                   boolean returnOnlyOne,
                                   boolean mustBeOnlyOne ) {
    if ( fCall == null || variable == null ) return null;
    Vector< Object > arguments = fCall.getArgumentVector();
    ArrayList< Object > argsWithVariable = new ArrayList< Object >();
    if ( variable instanceof Parameter ) {
      for ( Object arg : arguments ) {
        if ( arg == null ) continue;
        Object o = null;
        try {
          o = Expression.evaluate(arg, Variable.class, false, false);
        } catch ( Throwable e ) {
        }
        boolean eq = o != null && variable.equals( o );
        if ( Expression.valuesEqual( variable, arg, Parameter.class )
             || ( arg instanceof HasParameters
                  && ( (HasParameters)arg ).hasParameter( (Parameter< ? >)variable,
                                                          true, null ) ) ) {
          argsWithVariable.add( arg );
          if ( returnOnlyOne && !mustBeOnlyOne ) {
            return argsWithVariable;
          }
          if ( mustBeOnlyOne && !argsWithVariable.isEmpty() ) {
            return null;
          }
        }
      }
    }
    return argsWithVariable;
  }

  protected static SuggestiveFunctionCall
            getSuggestiveFunctionCall( Object o ) {
    SuggestiveFunctionCall call = null;

    try {
      call = Expression.evaluate( o, SuggestiveFunctionCall.class, true );
    } catch ( ClassCastException e ) {} catch ( IllegalAccessException e ) {} catch ( InvocationTargetException e ) {} catch ( InstantiationException e ) {}

    return call;
  }

  /**
   * Pick a value for the variable in the context of a binary function using
   * pickFunctionCall if variable is in the expression of the first argument to
   * the binary function or reversePickFunctionCall if in the expression of the
   * second argument.
   * 
   * @param variable
   * @param pickFunctionCall
   * @param reversePickFunctionCall
   * @return
   */
  protected static < T1 > T1
            pickValueBF2( Variable< T1 > variable,
                          FunctionCall pickFunctionCall,
                          FunctionCall reversePickFunctionCall ) {
    // check for valid input
    if ( variable == null ) return null;
    if ( pickFunctionCall == null
         && reversePickFunctionCall == null ) return null;
    if ( !( variable instanceof Parameter ) ) {
      Debug.error( false,
                   "Unfortunately, pickValueBF2() depends on variable being a Parameter! "
                          + variable );
      return null;
    }
    Parameter< T1 > variableParam = (Parameter< T1 >)variable;

    // get the arguments of the binary function, assumed to be the same as the
    // arguments of the pick functions
    if ( pickFunctionCall == null
         && reversePickFunctionCall == null ) return null;
    Vector< Object > args =
        ( pickFunctionCall == null ) ? reversePickFunctionCall.getArgumentVector()
                                     : pickFunctionCall.getArgumentVector();
    assert ( args.size() == 2 );
    if ( args.size() != 2 ) return null;
    Object arg1 = args.get( 0 );
    Object arg2 = args.get( 1 );
    Expression< T1 > o1 = null;
    Expression< T1 > o2 = null;
    if ( arg1 instanceof Expression ) {
      o1 = (Expression< T1 >)arg1;
    }
    if ( arg2 instanceof Expression ) {
      o2 = (Expression< T1 >)arg2;
    }

    // choose the argument as the context for which a value is picked for the
    // variable
    // This was changed to use Utils.valuesEqual(), but we dont know why...
    // boolean isFirst = o1 != null && Utils.valuesEqual( variable, o1 );
    // boolean isSecond = o2 != null && Utils.valuesEqual( variable, o2 );
    boolean isFirst = o1 != null && Expression.valuesEqual( variableParam, o1,
                                                            Parameter.class );
    boolean isSecond = o2 != null && Expression.valuesEqual( variableParam, o2,
                                                             Parameter.class );
    boolean inFirst =
        isFirst || ( o1 != null
                     && o1.hasParameter( variableParam, true, null ) );
    boolean inSecond =
        isSecond || ( o2 != null
                      && o2.hasParameter( variableParam, true, null ) );

    if ( !inFirst && !inSecond ) {
      Debug.error( true, false,
                   "Error! pickValueBF2(variable=" + variable
                                + ", pickFunction=" + pickFunctionCall
                                + ", reversePickFunctionCall="
                                + reversePickFunctionCall
                                + "): variable not in function arguments! args="
                                + args );
      return null;
    }
    FunctionCall chosenPickCall = null;
    Expression< T1 > arg = null;
    Expression< T1 > otherArg = null;
    boolean equal;
    boolean first;
    if ( inFirst && ( reversePickFunctionCall == null || !inSecond ) ) {
      first = true;
    } else if ( inSecond && ( pickFunctionCall == null || !inFirst ) ) {
      first = false;
    } else {
      // in both arguments; pick randomly
      first = Random.global.nextBoolean();
    }
    chosenPickCall = first ? pickFunctionCall : reversePickFunctionCall;
    arg = first ? o1 : o2;
    otherArg = first ? o2 : o1;
    equal = first ? isFirst : isSecond;

    // pick a value for the chosen argument
    T1 t1 = null;
    try {
      chosenPickCall.setStale( true );
      t1 = (T1)chosenPickCall.evaluate( false );
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

    // If the argument is the variable, then picking a value for the function is
    // the same as picking a value for the variable.
    if ( equal ) return t1;

    // else, the variable is a part of the argument.
    // Example: pick value for x for z <= x + y
    // arg1 = z, arg2 = x+y, chosen arg = arg2
    // If z = 1, then t1 will be chosen >= 1, let's say t1 = 10 and y = 3
    // Need to choose value for x where x + y = 10; x = 10 - y = 10 - 3 = 7

    // If the argument is a FunctionCall, try to invert the call with the target
    // value, t1, to solve for the variable.
    SuggestiveFunctionCall fCall = getSuggestiveFunctionCall( arg.expression );
    if ( fCall == null ) { // arg.expression instanceof SuggestiveFunctionCall )
                           // {
      return null;
    }
    // fCall=Plus(x,y)
    // SuggestiveFunctionCall fCall = (SuggestiveFunctionCall)arg.expression;
    ArrayList< Object > argsWithVar =
        getArgumentsWithVariable( fCall, variable );
    if ( Utils.isNullOrEmpty( argsWithVar ) || argsWithVar.size() > 1 ) {
      // TODO -- solve for variable! or simplify expression!
      return null;
    }
    Object subExprArg = argsWithVar.get( 0 );
    if ( !( subExprArg instanceof Expression ) ) {
      return null;
    }
    // inverseCall = inverse of Plus(x,y)
    // inverseCall(x) = t1 - y
    // inverseCall(y) = t1 - x
    FunctionCall inverseCall = fCall.inverse( new Expression< T1 >( t1 ),
                                              (Expression< ? >)subExprArg );
    if ( inverseCall instanceof SuggestiveFunctionCall ) {
      T1 t11 = ( (SuggestiveFunctionCall)inverseCall ).pickValue( variable );
      return t11;
    } else if ( inverseCall != null ) {
      Object result = null;
      try {
        result = inverseCall.evaluate( true );
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

      // HACK!!!
      // The default inverse function wraps a single value in a List because
      // the caller may want the set of possible values since the interface
      // implements inverse images.  The interface should be updated to pass
      // a domain to avoid possible misinterpretation of the List.
      // In the meantime, we try to avoid misinterpretation here by pulling a
      // single object out of the List.  This could be a dangerous assumption
      // since the result could be the list itself as the only value.  But,
      // as of this writing, the cases where an image (set of values) is
      // returned mostly shows up in a Domain instead of a Collection.
      if ( result instanceof List && ( (List)result ).size() == 1 ) {
        result = ( (List)result ).get( 0 );
      }

      if(!Expression.valuesEqual(variableParam, subExprArg, Parameter.class)) {
        Equals<T1> subExprFunction = new Equals<>(subExprArg, new Expression(result));
        return subExprFunction.pickValue(variable);
      }

      if ( result instanceof Collection ) {
        Collection< T1 > coll = (Collection< T1 >)result;
        T1 t11 = coll.isEmpty() ? null : get( coll, Random.global.nextInt( coll.size() ) );
//        if ( t11 instanceof Domain ) {xs
//          if ( !( Domain.class.isAssignableFrom( arg.getType() ) ) ) {
//            return (T1)( (Domain)t11 ).getValue( true );
//          }
//
//        }
        return t11;
      } else {
        Class< T1 > cls = (Class< T1 >)variable.getClass();
        try {
          T1 t11 = Expression.evaluate( result, cls, true );
          return t11;
        } catch ( ClassCastException e ) {
          // TODO Auto-generated catch block
          e.printStackTrace();
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

    return null;
  }

  public static < T > T get( Collection< T > coll, int index ) {
    if ( coll instanceof List ) {
      return ( (List< T >)coll ).get( index );
    } else {
      T t = null;
      Iterator< T > iter = coll.iterator();
      for ( int i = 0; i != index + 1 && iter.hasNext(); ++i ) {
        t = iter.next();
      }
      return t;
    }
  }

  // delete this function
  private static <T, T1> T1 pickValueBB( BooleanBinary< T > booleanBinary,
                                         Variable< T1 > variable ) {
    T1 newValue = pickValueBF( booleanBinary,//.functionCall,
                               variable );
    return newValue;
  }

  // delete this function
  private static < T1 > T1 pickValueBF( FunctionCall f,
                                        Variable< T1 > variable ) {
    boolean propagate = true;
    T1 newValue = null;
    // FunctionCall f = (FunctionCall)booleanBinary.expression;
    // Variable< T1 > otherArg = null;
    Object otherArg = null;
    Variable< T1 > otherVariable = null;
    boolean found = false;
    for ( Object arg : f.arguments ) {
      if ( variable == arg ) {
        found = true;
      } else if ( arg instanceof Expression
                  && ( ( (Expression< ? >)arg ).expression == variable ) ) {
        found = true;
      } else if ( arg instanceof Expression
                  && !( ( (Expression< ? >)arg ).expression instanceof Parameter )
                  && variable instanceof Parameter ) {
        if ( ( (Expression< ? >)arg ).hasParameter( (Parameter< T1 >)variable,
                                                    false, null ) ) {
          try {
            newValue = (T1)variable.pickRandomValue();
          } catch ( ClassCastException e ) {
            // TODO??
          }
        }
      } else {
        // if ( arg instanceof Variable ) {
        if ( otherArg == null ) {
          otherArg = arg;// (Variable< T1 >)arg;
          if ( otherArg instanceof Variable ) {
            otherVariable = (Variable< T1 >)otherArg;
          } else if ( otherArg instanceof Expression ) {
            if ( ( (Expression< T1 >)otherArg ).form == Expression.Form.Parameter ) {
              otherVariable =
                  (Variable< T1 >)( (Expression< T1 >)otherArg ).expression;
            }
          }
        }
      }
    }
    Object value = null;
    Object otherValue = null;
    // if ( found ) {
    // if ( otherArg == null ) {
    // otherValue =
    // }
    // }
    if ( otherArg != null && found ) {
      if ( f.method.getName().equalsIgnoreCase( "equals" ) ) {
        value = variable.getValue( propagate );
        if ( otherArg instanceof Variable ) {
          otherValue = ( (Variable< ? >)otherArg ).getValue( propagate );
        } else if ( otherArg instanceof Expression ) {
          try {
            otherValue = ( (Expression< ? >)otherArg ).evaluate( propagate );
          } catch ( IllegalAccessException e ) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
          } catch ( InvocationTargetException e ) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
          } catch ( InstantiationException e ) {
            // TODO Auto-generated catch block
            // e.printStackTrace();
          }
        }
        if ( otherValue instanceof Variable ) {
          if ( value != null && !value.getClass().isInstance( otherValue )
               && value.getClass()
                       .isInstance( ( (Variable< ? >)otherValue ).getValue( propagate ) ) ) {
            otherValue = ( (Variable< ? >)otherValue ).getValue( propagate );
          }
        }
        if ( Debug.isOn() ) Debug.outln( "suggesting other arg value "
                                         + otherValue + " to make "
                                         + f.getClass().getSimpleName()
                                         + " true" );
        return (T1)otherValue;
      }
      newValue = null;
      Boolean r = false;
      for ( int i = 0; i < 5; ++i ) {
        T1 v = null;
        try {
          v = (T1)variable.pickRandomValue();
        } catch ( ClassCastException e ) {
          // TODO??
        }
        Pair< Boolean, Object > p = null;
        try {
          p = ClassUtils.runMethod( false, (Object)null, f.method,
                                    new Expression< T1 >( v ), otherArg );
          r = (Boolean)p.second;
        } catch ( IllegalArgumentException e ) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        if ( p != null && p.first && r ) {
          if ( newValue == null ) {
            newValue = v;
          } else if ( otherVariable != null && otherVariable.getDomain() == null
                      || otherVariable.getDomain().contains( v ) ) {
            if ( Debug.isOn() ) Debug.outln( "suggesting value " + v
                                             + " to try make "
                                             + f.getClass().getSimpleName()
                                             + " true" );
            return v;
          }
        }
      }
    }
    if ( value == null ) {
      value = variable.getValue( propagate );
    }
    if ( newValue == null ) {
      if ( Debug.isOn() ) Debug.outln( "suggesting same value " + value
                                       + " for " + f.getClass().getSimpleName()
                                       + " true" );
      return (T1)value;
    }
    if ( Debug.isOn() ) Debug.outln( "suggesting value " + newValue + " for "
                                     + f.getClass().getSimpleName() + " true" );
    return newValue;
  }

  public static void main( String[] args ) {
    Parameter< Double > z = new Parameter< Double >( "z",
                                                     (Domain< Double >)( new DoubleDomain( 0.0,
                                                                                           10.0 ) ),
                                                     10.0, null );
    Parameter< Integer > y =
        new Parameter< Integer >( "y", new IntegerDomain( 1, 5 ), 1, null );
    Parameter< Integer > x =
        new Parameter< Integer >( "x", new IntegerDomain( 4, 10 ), 1, null );
    Parameter< Double > w =
        new Parameter< Double >( "w", new DoubleDomain( 0.0, 10.0 ), 2.0,
                                 null );
    Sum< Integer, Integer > xPlusY = new Sum< Integer, Integer >( x, y );
    Sum< Integer, Integer > xPlusYPlusW =
        new Sum< Integer, Integer >( xPlusY, w );
    Less< Integer > expr = new Less< Integer >( z, xPlusYPlusW );
    System.out.println( "expr = " + expr );
    //// Integer xVal = expr.pickValue( x );
    //// System.out.println("Picked " + xVal + " for x = " + x + " in expr = " +
    //// expr );
    // Integer zVal = expr.pickValue( z );
    // System.out.println("Picked " + zVal + " for z = " + z + " in expr = " +
    //// expr );
    Double wVal = expr.pickValue( w );
    System.out.println( "Picked " + wVal + " for w = " + w + " in expr = "
                        + expr );

    Less< Double > exprLess = new Less< Double >( z, w );
    Double zVal = exprLess.pickValue( z );
    System.out.println( "Picked " + zVal + " for z = " + z + " in exprLess = "
                        + exprLess );

    // test for inverse functions
    System.out.println( "x = " + x + "; domain = " + x.getDomain() );
    System.out.println( "y = " + y + "; domain = " + y.getDomain() );
    EQ< Integer > eq = new EQ< Integer >( x, y );
    FunctionCall i = eq.inverse( Boolean.TRUE, x );
    System.out.println( "eq: " + eq );
    System.out.println( "inverse of eq: " + i );
    try {
      Object r = i.evaluate( true );
      System.out.println( "evaluation of i: " + r );
    } catch ( IllegalAccessException e ) {
      e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      e.printStackTrace();
    } catch ( InstantiationException e ) {
      e.printStackTrace();
    }


    System.out.println("=== MinusPrefix.calculateDomain() ===\n");

    StringDomain d1 = new StringDomain( "ab", "abcde" );
    d1.kind = StringDomain.Kind.PREFIX_RANGE;
    System.out.println( "StringDomain d1 = " + d1 + ", size = " + d1.size() );
//    for ( int i = 0; i < d1.size(); ++i ) {
//      System.out.println( "value " + i + " = " + d1.getNthValue( i ) );
//    }

    StringDomain d2 = new StringDomain( "aba", "aba_what_aba" );
    d2.kind = StringDomain.Kind.SUFFIX_RANGE;
    d2.excludeLowerBound();
    d2.excludeUpperBound();
    System.out.println( "StringDomain d2 = " + d2 + ", size = " + d2.size() );
//    for ( int i = 0; i < d2.size(); ++i ) {
//      System.out.println( "value " + i + " = " + d2.getNthValue( i ) );
//    }

    StringDomain d3 = new StringDomain( "abc", "abcdefg" );
    System.out.println( "StringDomain d3 = " + d3 + ", size = " + d3.size() );


    MinusPrefix mp = new MinusPrefix( new Expression<String>( d3 ),
                                      new Expression<String>( d1 ) );

    Domain cdd = mp.calculateDomain( true, null );

    System.out.println("" + mp + ".calculateDomain() = " + cdd);


    System.out.println("\ndividing negative integers");
    System.out.println(" 7 / 3 = " + (7 / 3));
    System.out.println(" -7 / 3 = " + (-7 / 3));
    System.out.println(" 7 / -3 = " + (7 / -3));
    System.out.println(" -7 / -3 = " + (-7 / -3));

    // inverseDivideForNumerator(2, 3) = [6, 8]
    System.out.println("inverseDivideForNumerator(2, 3) = " +
                       Functions.Divide.inverseDivideForNumerator(2, 3));
    System.out.println("inverseDivideForNumerator(2L, 3L) = " +
                       Functions.Divide.inverseDivideForNumerator(2L, 3L));
    // inverseDivideForNumerator(0, 0) = 0?
    System.out.println("inverseDivideForNumerator(0, 0) = " +
                       Functions.Divide.inverseDivideForNumerator(0, 0));
    // inverseDivideForNumerator(0, inf) = anything
    System.out.println("inverseDivideForNumerator(0, Integer.MAX_VALUE) = " +
                       Functions.Divide.inverseDivideForNumerator(0, Integer.MAX_VALUE));
    // inverseDivideForNumerator(0, -inf) = anything
    System.out.println("inverseDivideForNumerator(0, Integer.MIN_VALUE) = " +
                       Functions.Divide.inverseDivideForNumerator(0, Integer.MIN_VALUE));
    // inverseDivideForNumerator(inf, 0) = anything
    System.out.println("inverseDivideForNumerator(Integer.MAX_VALUE, 0) = " +
                       Functions.Divide.inverseDivideForNumerator(Integer.MAX_VALUE, 0));
    // inverseDivideForNumerator(-inf, 0) = anything
    System.out.println("inverseDivideForNumerator(Integer.MIN_VALUE, 0) = " +
                       Functions.Divide.inverseDivideForNumerator(Integer.MIN_VALUE, 0));

    System.out.println("");

    // inverseDivideForDenominator(3, 100) = [26, 33]
    System.out.println("inverseDivideForDenominator(3, 100) = [26, 33] = " +
                       Functions.Divide.inverseDivideForDenominator(3, 100));
    System.out.println("inverseDivideForDenominator(3L, 100L) = [26, 33] = " +
                       Functions.Divide.inverseDivideForDenominator(3L, 100L));
    // inverseDivideForDenominator(0, 0) = 0?
    System.out.println("inverseDivideForDenominator(0, 0) = 0? = " +
                       Functions.Divide.inverseDivideForDenominator(0, 0));
    // inverseDivideForDenominator(0, inf) = anything
    System.out.println("inverseDivideForDenominator(0, Integer.MAX_VALUE) = inf = " +
                       Functions.Divide.inverseDivideForDenominator(0, Integer.MAX_VALUE));
    // inverseDivideForDenominator(0, -inf) = anything
    System.out.println("inverseDivideForDenominator(0, Integer.MIN_VALUE) = -inf = " +
                       Functions.Divide.inverseDivideForDenominator(0, Integer.MIN_VALUE));
    // inverseDivideForDenominator(inf, 0) = anything
    System.out.println("inverseDivideForDenominator(Integer.MAX_VALUE, 0) = 0 = " +
                       Functions.Divide.inverseDivideForDenominator(Integer.MAX_VALUE, 0));
    // inverseDivideForDenominator(-inf, 0) = anything
    System.out.println("inverseDivideForDenominator(Integer.MIN_VALUE, 0) = 0 = " +
                       Functions.Divide.inverseDivideForDenominator(Integer.MIN_VALUE, 0));


    // TODO -- Add tests for overflow!!
  }

}
