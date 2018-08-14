package gov.nasa.jpl.ae.event;
import gov.nasa.jpl.ae.event.TimeVaryingMap.Interpolation;
import gov.nasa.jpl.ae.solver.AbstractRangeDomain;
import gov.nasa.jpl.ae.solver.Domain;
import gov.nasa.jpl.ae.solver.HasDomain;
import gov.nasa.jpl.ae.solver.HasIdImpl;
import gov.nasa.jpl.ae.solver.Satisfiable;
import gov.nasa.jpl.ae.solver.SingleValueDomain;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.ae.util.DomainHelper;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Evaluatable;
import gov.nasa.jpl.mbee.util.MoreToString;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.Wraps;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import junit.framework.Assert;

/**
 * 
 *         TODO -- REVIEW -- What about an Expression
 *         interface with implementations of Value, Parameter, MethodExpression,
 *         and Function? Would it make sense to put constructors in an
 *         expression factory?
 * 
 *         TODO -- Look at javaparser at http://code.google.com/p/javaparser/
 *         and JavaCC.
 * 
 *         TODO -- Consider using Ptolemy's expression parser.
 * 
 */
public class Expression< ResultType > extends HasIdImpl
                                    implements Cloneable, HasParameters, ParameterListener, Groundable,
                                               LazyUpdate, Satisfiable,
                                               HasDomain, HasTimeVaryingObjects,
                                               MoreToString, Wraps< ResultType >, Evaluatable {//, Comparable< Expression< ? > > {
  public Object expression = null;
  public Form form = Form.None;
  public Class<? extends ResultType> resultType = null;//Type.None;
  // freeParameters if not null specifies which parameters can be reassigned
  // values for satisfy().
  protected Set< Parameter< ? > > freeParameters = null;
  protected boolean evaluationSucceeded = false;


  public enum Form {
    None(null), Value(Object.class),
    Parameter(Parameter.class),
    //Method(Method.class),
    Function(FunctionCall.class),
    Constructor(ConstructorCall.class);
    
    private Class<?> myClass;
    Form( Class<?> c ) {
      myClass = c;
    }
    public Class<?> getFormClass() {
      return myClass;
    }
  }


  /**
   * Create an Expression by parsing a String in a specified language.
   * 
   * @param expressionString the String to parse
   * @param expressionLanguage the language to parse
   */
  public Expression( String expressionString, String expressionLanguage,
                     Class<ResultType> cls ) {
    resultType = cls;
    // TODO -- javaparser?
    System.err.println("Error! Expression( String ) constructor not yet supported! See EventXmlToJava.javaToEventExpression()");
    // REVIEW -- Should Expression be sub-classed for different languages?
  }

  /**
   * @param object
   */
  public Expression( Object object ) {
    this( object, null );
    if ( object != null && resultType == null ) {
    resultType = (Class< ? extends ResultType >)object.getClass();
    }
  }

  /**
   * @param value
   * @param cls
   */
  public <T> Expression( T value, Class< ResultType > cls ) {
    resultType = cls;
    if ( cls == null ) {
        Expression<ResultType> e = null;
        if ( value instanceof Expression ) {
            e = new Expression<ResultType>( (Expression<ResultType>)value );
        } else if ( value instanceof Parameter ) {
            e = new Expression<ResultType>( (Parameter<ResultType>)value );
        } else if ( value instanceof Call ) {
            e = new Expression<ResultType>( (Call)value );
        }
        if ( e != null ) {
            copyMembers( e );
            return;            
        }
    }
    this.expression = value;
    if ( cls != null && value != null && !cls.isInstance( value ) ) {
      Debug.error( true,
                   "Expression initialized with incompatible value arg in Expression("
                       + value.getClass().getCanonicalName() + " "
                       + MoreToString.Helper.toShortString( value ) + ", "
                       + cls.getCanonicalName() );
    }
    form = Form.Value;
  }

  /**
   * @param parameter
   */
  public Expression( Parameter< ResultType > parameter, Class< ResultType > cls ) {
    // REVIEW -- why not use parameter.getType()????
    this.expression = parameter;
    ResultType value = ( parameter == null ? null : parameter.getValue() );
    if ( value != null && cls == null ) {
      try {
        resultType = (Class< ? extends ResultType >)value.getClass();
      } catch ( ClassCastException e ) {}
    }
    if ( cls != null ) { 
      resultType = cls;
      if ( value != null && !cls.isInstance( value ) ) {
        Debug.error( true,
                     "Expression initialized with incompatible parameter arg in Expression(Parameter<"
                         + value.getClass().getCanonicalName() + "> "
                         + parameter + ", " + cls.getCanonicalName() );
      }
    }
    form = Form.Parameter;
  }

  /**
   * @param parameter
   */
  public Expression( Parameter< ResultType > parameter ) {
    this( parameter, null );
    if ( parameter != null && parameter.getValueNoPropagate() != null ) {
      resultType = (Class< ? extends ResultType >)parameter.getValueNoPropagate().getClass();
    }
  }

  /**
   * @param function
   */
  public Expression( FunctionCall function ) {
    init(function);
  }
  public Expression( FunctionCall function, Class<ResultType> resultType ) {
    init(function);
    if ( getResultType() == null || resultType != null ) {
      if ( getResultType() != null && resultType != null ) {
        if ( resultType.isAssignableFrom( this.resultType ) ) {
          
        }
      }
      setResultType( resultType );
    }
  }
  public void init( FunctionCall function ) {
    this.expression = function;
    if ( function != null && function.method != null ) {
      resultType = (Class< ? extends ResultType >)function.getReturnType();
    }
    form = Form.Function;
  }
  
  /**
   * @param constructor
   */
  public Expression( ConstructorCall constructor ) {
    init(constructor);
  }
  public void init( ConstructorCall constructor ) {
    this.expression = constructor;
    if ( constructor != null ) {
      resultType = (Class< ? extends ResultType >)constructor.thisClass;
    }
    form = Form.Constructor;
  }
  
  /**
   * @param call
   */
  public Expression( Call call ) {
    if ( call instanceof ConstructorCall ) {
      init((ConstructorCall)call);
    } else if (call == null || call instanceof FunctionCall ) {
      init((FunctionCall)call);
    } else {
      Debug.error( true, "Error! Unknown Call type passed to Expression(Call call) constructor!" );
      this.expression = call;
    }
  }

  public Expression( Expression<ResultType> e, boolean deep ) {
    this( e.expression, e.form, e.resultType, e.freeParameters, e.evaluationSucceeded, deep );
  }
  

  public Expression( Object expression, Form form,
                     Class< ? extends ResultType > resultType,
                     Set< Parameter< ? > > freeParameters,
                     boolean evaluationSucceeded ) {
    this( expression, form, resultType, freeParameters, evaluationSucceeded, false );
  }
  
  public Expression( Object expression, Form form,
                     Class< ? extends ResultType > resultType,
                     Set< Parameter< ? > > freeParameters,
                     boolean evaluationSucceeded,
                     boolean deep ) {
    super();
    this.form = form;
    this.resultType = resultType;
    this.evaluationSucceeded = evaluationSucceeded;
    this.expression = expression;
    if ( !deep ) {
      //this.freeParameters = freeParameters;
    } else {
      switch (form) {
      case None:
      case Value:  // TODO -- is this right for Value?
      case Parameter:
        this.expression = expression; // REVIEW -- why is this not a deep copy?
        break;
      case Function:
        this.expression = (Object) ((FunctionCall)expression).clone();
        break;
      case Constructor:
        this.expression = (Object) ((ConstructorCall)expression).clone();
        break;
//      case Parameter:
//        expression = (Object) new Parameter<ResultType>((Parameter<ResultType>)e.expression);
//        break;
      default:
        Assert.assertTrue( "Error! Bad expression type!", false );
      }
    }
  }

  // default shallow
  public Expression( Expression<ResultType> e ) {
    this(e, false);
  }

  
  @Override
  public Expression<ResultType> clone() throws CloneNotSupportedException {
    Expression<ResultType> e = new Expression< ResultType >( this );
    return e;
  }

  @Override
  public void deconstruct() {
    if ( expression instanceof Deconstructable ) {
      if ( form != Form.None && form != Form.Value &&
          ( form != Form.Parameter || ((Parameter<?>)expression).getOwner() == null ) ) {
        ( (Deconstructable)expression ).deconstruct();
      }
    }
    //expression = null;
  }

  public void copyMembers( Expression< ? > expr ) {
    this.expression = expr.expression;
    this.form = expr.form;
    this.resultType = (Class< ? extends ResultType >)expr.resultType;
    this.freeParameters = expr.freeParameters;
    this.evaluationSucceeded = expr.evaluationSucceeded;
  }
  
  public Boolean isResultType( Object o ) {
    try {
      ResultType t = (ResultType)o;
    } catch ( ClassCastException e ) {
      return false;
    }
    return true;
  }
  
  
  @Override
  public < TT > TT evaluate( Class< TT > cls, boolean propagate ) {
    
    if ( cls == null || resultType == null || cls.isAssignableFrom( resultType ) ) {
      Object o = null;
      try {
        o = evaluate( propagate );
      } catch ( IllegalAccessException e ) {
        e.printStackTrace();
      } catch ( InvocationTargetException e ) {
        e.printStackTrace();
      } catch ( InstantiationException e ) {
        e.printStackTrace();
      }
      if ( o != null && cls != null && cls.isInstance( o ) ) {
        return (TT)o;
      }
    }
    TT tt = Evaluatable.Helper.evaluate( expression, cls, true, propagate, true, null );
    return tt;
  }

  // REVIEW -- What if resultType == Expression.class?
  /**
   * Evaluate the expression and return the resulting value. For example, if the
   * expression is Math.min(4,6), evaluate() returns 4. If the expression is of
   * Type Value and is just the String "foo", then "foo" is returned.
   * 
   * @param propagate
   *          whether to try and update potentially stale values before
   *          evaluating.
   * @return the resulting value
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public Object evaluate( boolean propagate ) throws IllegalAccessException, InvocationTargetException, InstantiationException {//throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    evaluationSucceeded = false;
    if ( form == null || ( form != Form.None && expression == null ) ) {
      //System.out.print("\nevaluate(" + this + ") = ");
      //System.out.println("null (1)");
      return null;
    }
    try {
    switch (form) {
    case None:
      try {
        throw new IllegalAccessException();
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
      //System.out.print("\nevaluate(" + this + ") = ");
      //System.out.println("null (2)");
      return null; // TODO -- REVIEW -- exit?
    case Value:
      //System.out.print("\nevaluate(" + this + ") = ");
      //System.out.println("expression = " + expression);
      return expression;
    case Parameter:
          Parameter< ? > p = (Parameter< ? >)expression;
          while ( p != null ) {
            Object o = null;
            Object r = null;
            o = p.getValue( propagate );
            if ( o == null ) {
              //System.out.print("\nevaluate(" + this + ") = ");
              //System.out.println("null (3)");
              return null;
            }
            try {
              if ( resultType != null ) {
                if ( resultType.isInstance( o ) ) {
                  evaluationSucceeded = true;
                  //System.out.print("\nevaluate(" + this + ") = ");
                  //System.out.println("o1 = "+o);
                  return o;
                } else if ( resultType.isInstance( p ) ) {
                  evaluationSucceeded = true;
                  return p;
                } else {
                  if ( resultType == Integer.class && Double.class.isAssignableFrom(o.getClass()) ) {
                    Double d = (Double)o;
                    evaluationSucceeded = true;
                    //System.out.print("\nevaluate(" + this + ") = ");
                    //System.out.println("d1.intValue() = " + d.intValue());
                    return (Integer)d.intValue();
                  } else if ( resultType == Long.class && Double.class.isAssignableFrom(o.getClass()) ) {
                    Double d = (Double)o;
                    evaluationSucceeded = true;
                    //System.out.print("\nevaluate(" + this + ") = ");
                    //System.out.println("d1.intValue() = " + d.intValue());
                    return (Long)d.longValue();
                  }
                  if ( o != null ) return o;
                  o = p.evaluate( resultType, propagate );
                  if ( o != null ) return o;
                  evaluationSucceeded = false;
                  throw new ClassCastException();
                }
              }
              if ( o instanceof Parameter ) {
                p = (Parameter< ? >)o;
              } else if ( o instanceof Expression ) {
                Object rt = ( (Expression<ResultType>)o ).evaluate( propagate );
                evaluationSucceeded = ( (Expression<ResultType>)o ).didEvaluationSucceed();
                //System.out.print("\nevaluate(" + this + ") = ");
                //System.out.println("rt1 = "+rt);
                return rt;
              } else {
                r = o;
                evaluationSucceeded = true;
                //System.out.print("\nevaluate(" + this + ") = ");
                //System.out.println("r1 = "+r);
                return r;
              }
            } catch ( ClassCastException cce ) {
              evaluationSucceeded = false;
              try {
                if ( Double.class.isAssignableFrom(o.getClass()) ) {
                  Double d = (Double)o;
                  evaluationSucceeded = true;
                  //System.out.print("\nevaluate(" + this + ") = ");
                  //System.out.println("d2.intValue() = "+d.intValue());
                  return (Long)d.longValue();
                }
              } catch ( Exception e ) {
                evaluationSucceeded = false;
                // ignore
              }
              if ( o instanceof Parameter ) {
                p = (Parameter< ? >)o;
              } else if ( o instanceof Expression ) {
                Object rt = ( (Expression<ResultType>)o ).evaluate( propagate );
                evaluationSucceeded = ( (Expression<ResultType>)o ).didEvaluationSucceed();
                //System.out.print("\nevaluate(" + this + ") = ");
                //System.out.println("rt2 = "+rt);
                return rt;
              } else {
                Debug.error( false,
                             "Could not cast result of expression evaluation to ResultType: "
                                 + ( resultType != null ? "" : "(" + resultType
                                                               + ") " ) + this );
                //cce.printStackTrace();
                evaluationSucceeded = false;
                //System.out.print("\nevaluate(" + this + ") = ");
                //System.out.println("(o2 = "+o);
                return o;
              }
            }
          }
    case Constructor:
    case Function:
      Object r = ((Call)expression).evaluate( propagate );
      evaluationSucceeded = ((Call)expression).didEvaluationSucceed();
      //System.out.print("\nevaluate(" + this + ") = ");
      //System.out.println("r3 = "+r);
      return r;
    default:
      evaluationSucceeded = false;
      //System.out.print("\nevaluate(" + this + ") = ");
      //System.out.println("null (4)");
      return null;
    }
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    }
    evaluationSucceeded = false;
    //System.out.print("\nevaluate(" + this + ") = ");
    //System.out.println("null (5)");
    return null;  // TODO -- REVIEW -- shouldn't get here -- die?
  }
  
  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen,
                          Map< String, Object > otherOptions ) {
    Pair< Boolean, Set< Object > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) deep = false;
    seen = pair.second;
    switch (form) {
    case None:
      try {
        throw new IllegalAccessException();
      } catch (IllegalAccessException e) {
        e.printStackTrace();
      }
      return null; // TODO -- REVIEW -- exit?
    case Value:
//      return ((ResultType)expression).toString();
    case Parameter:
//    case Method:
    case Function:
    case Constructor:
      if ( expression == null ) return "null";
      return MoreToString.Helper.toString( expression, withHash, deep, seen,
                                           otherOptions );
    default:
      return null;
    }
  }

  @Override
  public String toShortString() {
    return MoreToString.Helper.toShortString( expression );
  }

  @Override
  public String toString() {
    return MoreToString.Helper.toString( expression );
  }

  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen ) {
    return MoreToString.Helper.toString( expression, withHash, deep, seen );
  }

  @Override
  public boolean substitute( Parameter<?> p1, Parameter<?> p2, boolean deep,
                             Set<HasParameters> seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;
    //if ( Utils.seen( this, deep, seen ) ) return false;
    boolean subbed = false;
    if ( form == Form.Parameter ) {
      if ( p1 == expression ) {
        expression = p2;
        subbed = true;
      }
    }
    if ( !subbed && ( expression instanceof HasParameters ) ) {
      HasParameters gotParameters = (HasParameters) expression;
      assert( gotParameters != null );
      subbed = gotParameters.substitute( p1, p2, deep, seen );
    }
    return subbed;
  }

  @Override
  public boolean substitute( Parameter<?> p1, Object p2, boolean deep,
                             Set<HasParameters> seen ) {
    //System.out.println("\nsubstitute(Parameter p1=" + p1 + ", Object p2=" + p2 + ") in " + this );
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;
    //if ( Utils.seen( this, deep, seen ) ) return false;
    
    boolean subbed = false;
    if ( p2 instanceof Parameter ) {
      seen.remove( this );
      return substitute( p1, (Parameter<?>)p2, deep, seen );
    }
    if ( p2 instanceof Expression ) {
      seen.remove( this );
      return substitute( p1, (Expression<?>)p2, deep, seen );
    }
    if ( HasParameters.Helper.subParamsEqual( expression, p1 ) ) {
      expression = p2;
      form = Form.Value;
      resultType = null;//(Class< ? extends ResultType >)( p2 == null ? Object.class : p2.getClass() );
      subbed = true;
    } else
    if ( expression instanceof HasParameters ) {
      HasParameters gotParameters = (HasParameters) expression;
      assert( gotParameters != null );
      subbed = gotParameters.substitute( p1, p2, deep, seen );
    }
    return subbed;
  }

  public boolean substitute( Parameter< ? > p1, Expression< ? > p2,
                             boolean deep, Set<HasParameters> seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;
    
    if ( p2.getForm() == Form.Parameter && p2.expression instanceof Parameter ) {
      seen.remove( this );
      return substitute( p1, (Parameter<?>)p2.expression, deep, seen );
    }
    //if ( Utils.seen( this, deep, seen ) ) return false;
    boolean subbed = false;
    if ( form == Form.Parameter ) {
      if ( p1 == expression ) {
        // REVIEW -- This hopefully will never get called since a substitution
        // like this should happen at a higher level, such as in a call for
        // which this object is an argument.
        this.copyMembers( p2 );
        expression = p2;
        subbed = true;
      }
    }
    if ( expression instanceof HasParameters ) {
      HasParameters gotParameters = (HasParameters) expression;
      assert( gotParameters != null );
      subbed = gotParameters.substitute( p1, p2, deep, seen );
    }
    return subbed;
  }

  @Override
  public Parameter< ? > getParameter( String name ) {
    return HasParameters.Helper.getParameter( this, name );
  }


  @Override
  public Set< Parameter<?> > getParameters( boolean deep,
                                            Set<HasParameters> seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    //if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
    Set< Parameter<?> > set = new LinkedHashSet< Parameter<?> >();
    if ( form == Form.Parameter ) {
      if ( expression != null ) {
        Parameter<?> p = (Parameter<?>) this.expression; 
        set.add( p );
        if ( deep ) {
          Object v = p.getValueNoPropagate(); 
          if ( v != null && v instanceof HasParameters ) {
            set = Utils.addAll( set, ((HasParameters)v).getParameters( deep, seen ) );
          }
        }
      }
    } else if ( expression instanceof HasParameters ) {
      HasParameters gotParameters = (HasParameters) expression;
      set = Utils.addAll( set, gotParameters.getParameters( deep, seen ) );
    }
    return set;
  }
  
  /**
   * @return the freeParameters
   */
  public Set< Parameter< ? > > getFreeParameters() {
    // REVIEW -- this assumes that the parameters of the constraint and their
    // freedom never change.
    if ( freeParameters == null ) {
      freeParameters = new LinkedHashSet< Parameter< ? > >();
      for ( Parameter< ? > p : getParameters( false, null ) ) {
        if ( p.getOwner() != null && 
             p.getOwner().isFreeParameter( p, false, null ) ) {
          freeParameters.add( p );
        }
      }
    }
    return freeParameters;
  }

  /**
   * @param freeParameters
   *          the freeParameters to set
   */
  public void setFreeParameters( Set< Parameter< ? > > freeParameters ) {
    this.freeParameters = freeParameters;
  }

  public Set<Parameter<?>> getFreeParameters( boolean deep,
                                              Set<HasParameters> seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    return getFreeParameters();
  }

  @Override
  public void setFreeParameters( Set< Parameter< ? > > freeParams, boolean deep,
                                 Set<HasParameters> seen ) {
    setFreeParameters( freeParams );
  }
  
  /**
   * @return the expression
   */
  public Object getExpression() {
    return expression;
  }

  /**
   * @param expression the expression to set
   */
  public void setExpression( Object expression ) {
    this.expression = expression;
  }

  /**
   * @return the form
   */
  public Form getForm() {
    return form;
  }

  /**
   * @param form the form to set
   */
  public void setForm( Form form ) {
    this.form = form;
  }

  /**
   * @param resultType the resultType to set
   */
  public void setResultType( Class< ? extends ResultType > resultType ) {
    this.resultType = resultType;
  }

  @Override
  public boolean isGrounded(boolean deep, Set< Groundable > seen) {
    if ( expression == null && form != Form.Value) return false;
    if (deep && expression instanceof Groundable) {
      return ((Groundable)expression).isGrounded(deep, seen);
    }
//    if ( expression == null ) {
//      return false;
//    }
    switch (form) {
    case Value:
//    case Method:
      return true; // null should be ok, right?
    case Parameter: // Groundable -- should not get here
    case Function: // Groundable -- should not get here
    case Constructor: // Groundable -- should not get here
      return true;
    case None:
      return false;
    default:
      Debug.error(true, false, "Error! isGrounded(): Expression has invalid type: " + form );
      if ( Debug.isOn() ) { 
        try {
          throw new IllegalAccessException();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      return false; // TODO -- REVIEW -- exit?
    }
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.Groundable#ground(boolean, java.util.Set)
   */
  @Override
  public boolean ground(boolean deep, Set< Groundable > seen) {
    if (expression instanceof Groundable && deep) {
      return ((Groundable)expression).ground(deep, seen);
    }
    if ( expression == null ) {
      return true;  // added 2016-09-13, this doesn't seem right -- TODO -- FIXME
    }
    switch (form) {
    case Value:
      return true; // null should be ok, right?
    case Parameter: // Groundable -- should not get here
    case Function: // Groundable -- should not get here
    case Constructor: // Groundable -- should not get here
    case None:
    default:
      Debug.error(true, false, "Error! ground(): Can't ground an Expression with null contents unless it's a value. Expression has invalid type: " + form );
      return false; // TODO -- REVIEW -- exit?
    }
    //return grounded;
  }

  // NOTE: Don't use hashCode() unless overridden -- default may vary between runs!
  //@Override
  public int compareTo( Expression< ? > o ) {
    if ( this == o ) return 0;
    if ( o == null ) return 1;
    int compare = CompareUtils.compare( form.ordinal(), o.form.ordinal() );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compare( expression, o.expression, true );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compare( this, o, false );
    if ( compare != 0 ) return compare;
    return compare;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.LazyUpdate#isStale()
   */
  @Override
  public boolean isStale() {
    if ( expression instanceof LazyUpdate ) {
      return ((LazyUpdate)expression).isStale();
    }
    for ( Parameter< ? > p : getParameters( false, null ) ) {
      if ( p.isStale() ) return true;
    }
    return false;
  }

  @Override
  public void setStale( boolean staleness ) {
//    if ( expression instanceof Call ) {
//      ((Call) expression).setStale( staleness );
//      Debug.error(true, false, "Warning!  May not be supported: Expression.setStale(" + staleness + "): " + toShortString() );
//
//    } else {
      Debug.error(true, false, "BAD!!!!!!!!!!!!!!   THIS SHOULD NOT BE GETTING CALLED!  setStale(" + staleness + "): "
              + toShortString());
//    }
    if ( Debug.isOn() ) Debug.outln( "setStale(" + staleness + ") to " + this );
    // TODO -- REVIEW -- Do nothing?
  }

  @Override
  public void setStale(boolean staleness, boolean deep, Set<LazyUpdate> seen) {
    if(expression instanceof LazyUpdate) {
      ((LazyUpdate) expression).setStale(staleness, deep, seen);
    }
  }

  @Override
  public boolean hasParameter( Parameter< ? > parameter, boolean deep,
                               Set<HasParameters> seen ) {
    boolean has = HasParameters.Helper.hasParameter( this, parameter, deep, seen );
    return has;
  }

  // TODO -- REVIEW -- make or add to an interfacce?
  public static boolean contains( Object o, Object value, boolean deep, Set<Object> seen ) {
    if (value == null ) return false;
    if ( o == value ) {
      return true;
    }
    if ( deep && o instanceof Call ) {
      if (((Call)o).containsValue( value, deep, seen ) ) {
        return true;
      }
    }
    if ( o instanceof Expression ) {
      if (((Expression)o).containsValue( value, deep, seen ) ) {
        return true;
      }
    }
    return false;
  }

  public boolean containsValue( Object value, boolean deep, Set<Object> seen ) {
    if (value == null ) return false;
    if ( expression == value ) return true;

    Pair< Boolean, Set< Object > > p = Utils.seen( this, true, seen );
    if (p.first) return false;
    seen = p.second;

    if ( contains(expression, value, deep, seen) ) {
      return true;
    }
    return false;
  }

  @Override
  public boolean isFreeParameter( Parameter< ? > p, boolean deep,
                                  Set<HasParameters> seen ) {
    //return HasParameters.Helper.isFreeParameter(this, p, deep, seen );
    // REVIEW -- Should Expressions know which are free? Should it just be the
    // owner's job, in which case the Parameter can determine it itself?
    Debug.error("Expression.isFreeParameter() is not supported!");
    return false;
  }

  @Override
  public boolean isSatisfied(boolean deep, Set< Satisfiable > seen) {
    return HasParameters.Helper.isSatisfied( this, deep, seen );
  }

  @Override
  public boolean satisfy(boolean deep, Set< Satisfiable > seen) {
    return HasParameters.Helper.satisfy( this, deep, seen );
  }

  @Override
  public Domain< ResultType > getDomain( boolean propagate, Set< HasDomain > seen ) {
    // avoid infinite recursion
    Pair< Boolean, Set< HasDomain > > pair = Utils.seen( this, propagate, seen );
    if ( pair.first ) return null;
    seen = pair.second;
    
    if ( expression == null ) {
      if ( form == Form.Value ) {
        return SingleValueDomain.getNullDomain();
      }
      return null;
    }
    switch (form) {
    case Value:
//    case Method:
      AbstractRangeDomain<ResultType> d = (AbstractRangeDomain< ResultType >)DomainHelper.makeRangeDomainFromValue(expression);
      if ( d != null ) {
        return d;
      }
      return new SingleValueDomain<ResultType>( (ResultType)expression ); // since expression is not null
    case Parameter:
      Domain<ResultType> paramDomain = ((Parameter<ResultType>)expression).getDomain( propagate, seen );
      if (paramDomain != null) paramDomain = paramDomain.clone();
      return paramDomain;
    case Function:
    case Constructor:
      Domain<ResultType> functionDomain = (Domain< ResultType >)((Call)expression).getDomain( propagate, seen );
      if (functionDomain != null) functionDomain = functionDomain.clone();
      return functionDomain;
    case None:
    default:
      Debug.error(true, false, "Error! getDomain(): Expression has invalid type: " + form );
      if ( Debug.isOn() ) { 
        try {
          throw new IllegalAccessException();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      return null;
    }
  }

  // REVIEW -- Should Expression remember its domain?  No, if anything the FunctionCall (wrapped by the Expression) should remember.
  @Override
  public <T> Pair< Domain< T >, Boolean > restrictDomain( Domain< T > domain, boolean propagate,
                                         Set< HasDomain > seen ) {
    // check for bad input
    if ( domain == null ) return null;
    
    // avoid infinite recursion
    Pair< Boolean, Set< HasDomain > > pair = Utils.seen( this, propagate, seen );
    if ( pair.first ) return null;
    seen = pair.second;

    // If the expression is null, check to see if null is in the domain.
    if ( expression == null ) {
      if ( domain.contains( null ) ) {
        return new Pair(domain, false);  // REVIEW -- Does this make sense?
      } else {
        return null;
      }
    }

    Pair< Domain< T >, Boolean > result = null;
    switch (form) {
    case Value:
      // If the expression is a value, then the value must be in the input domain.
      Throwable throwable = null;
      try {
        if ( !domain.contains( (T)evaluate(expression, domain.getType(), true ) ) ) {
          //expression = null; // REVIEW -- Do we really want to set it to null?
          return null;
        }
      } catch (ClassCastException e) {
        throwable = e;
      } catch ( IllegalAccessException e ) {
        throwable = e;
      } catch ( InvocationTargetException e ) {
        throwable = e;
      } catch ( InstantiationException e ) {
        throwable = e;
      } finally {
        if ( throwable != null ) {
          T t = domain.getValue( true );
          if ( !valuesEqual( t, expression ) ) {
            //expression = null; // REVIEW -- Do we really want to set it to null?
            return null;
          }
        }
      }
      result = new Pair(domain, false);
      break;
    case Parameter:
      result = (Pair<Domain< T >,Boolean>)((Parameter<ResultType>)expression).restrictDomain( domain, propagate, seen );
      break;
    case Function:
      result = (Pair<Domain< T >,Boolean>)((FunctionCall)expression).restrictDomain( domain, propagate, seen );
      break;
    case Constructor:
      result = (Pair<Domain< T >,Boolean>)((ConstructorCall)expression).restrictDomain( domain, propagate, seen );
      break;
    case None:
    default:
      Debug.error(true, false, "Error! getDomain(): Expression has invalid type: " + form );
      if ( Debug.isOn() ) { 
        try {
          throw new IllegalAccessException();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      return null;
    }
    return result;
  }


  
  @Override
  public Set< TimeVarying< ?, ? >>
      getTimeVaryingObjects( boolean deep, Set< HasTimeVaryingObjects > seen ) {
    // TODO -- use HasParameters.Helper!!
    Pair< Boolean, Set< HasTimeVaryingObjects > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    //if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
    Set< TimeVarying<?,?> > set = new LinkedHashSet< TimeVarying<?,?> >();
    set = Utils.addAll( set, HasTimeVaryingObjects.Helper.getTimeVaryingObjects( expression, deep, seen ) );
    // REVIEW -- We could make Call extend HasTimeVaryingObject, but it seems
    // like everybody has to know about everybody!
    // What about a general Has that has a get( Class<?> c, deep, seen )?
    // Consider again using reflection to go through members?
    if ( deep && ( form == Form.Function || form == Form.Constructor ) ) {
      Call call = (Call)expression;
      set = Utils.addAll( set, HasTimeVaryingObjects.Helper.getTimeVaryingObjects( call.getObject(), deep, seen ) );
      set = Utils.addAll( set, HasTimeVaryingObjects.Helper.getTimeVaryingObjects( call.getArgumentArray(), deep, seen ) );
    }
    return set;
  }

  /**
   * Evaluate/dig or wrap the object of the given type cls from the object o,
   * which may be a Parameter or an Expression.
   * 
   * @param object
   *          the object to evaluate
   * @param cls
   *          the type of the object to find
   * @return o if o is of type cls, an object of type cls that is an evaluation
   *         of o, or null otherwise.
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public static <TT> TT evaluate( Object object, Class< TT > cls,
                                  boolean propagate ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return evaluate( object, cls, propagate, false );
  }
  
  public static <TT> TT evaluate( Object object, Class< TT > cls,
                                  boolean propagate,
                                  boolean allowWrapping ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( object == null ) return null;
    // Check if object is already what we want.
    boolean isTypeCompatible = cls != null && cls.isInstance( object );
    if ( isTypeCompatible || cls == object.getClass() ) {
      TT result = null;
      if ( isTypeCompatible ) {
        try {
          result = (TT)object;
        } catch (ClassCastException e) {
        }
        if ( result != null ) {
          return result;
        }
      }
      try {
        result = evaluateDeep( object, cls, propagate, allowWrapping );
        if ( result != null && (cls == null || cls.isInstance( result ) ) ) return result;
        result = Evaluatable.Helper.evaluate( object, cls, propagate, true );
        if ( result != null ) return result;
        return (TT)object;
      } catch (ClassCastException e) {
      }
      return null;
    }
    Object result = evaluateDeep( object, cls, propagate, allowWrapping );
    if ( result != null && (cls == null || cls.isInstance( result ) ) ) return (TT)result;
    if ( result != null && result != object &&
         (!(result instanceof TimeVarying) ||
          (((TimeVarying) result).getType() != null &&
           !cls.isAssignableFrom(((TimeVarying) result).getType()))) ) {
      Object result2 = Evaluatable.Helper.evaluate( result, cls, propagate, true );
      if ( result2 != null ) return (TT)result2;
    }
    // TODO -- uncomment the code below to ensure objects of the specified type.
    //if ( result != null && cls != null && !cls.isAssignableFrom( result.getClass() ) ) {
    //  result = null;
    //}
    return (TT)result;
  }
  
  public static <TT> TT evaluateDeep( Object object, Class< TT > cls,
                                      boolean propagate,
                                      boolean allowWrapping ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    // Try to evaluate object or dig inside to get the object of the right type. 
    Object value = null;
    if ( object instanceof Parameter ) {
      value = ( (Parameter)object ).getValue( propagate );
      return evaluate( value, cls, propagate, allowWrapping );  
    } 
    else if ( object instanceof Expression ) {
      Expression< ? > expr = (Expression<?>)object;
      if ( cls != null && cls.isInstance( expr ) && expr.form != Form.Function) {
        return (TT)expr;
      }
      if ( cls != null && cls.isInstance( expr.expression ) &&
           expr.form != Form.Function) {
        return (TT)expr.expression;
      }
      // This just evaluates one level down, but would evaluate a call.
      value = expr.evaluate( propagate );
      // This evaluates to find the result of the right type.
      value = evaluate( value, cls, propagate, allowWrapping );
      if ( cls != null && ( value == null || !cls.isInstance( value ) ) &&
           cls.isInstance( expr.expression ) ) {
        return (TT)expr.expression;
      }
      return (TT)value;
    }
    else if ( object instanceof Call) {
      value = ( (Call)object ).evaluate( propagate );
      return evaluate( value, cls, propagate, allowWrapping );  
    }
    else if ( cls != null && ClassUtils.isNumber( cls ) &&
              object != null &&
              ClassUtils.isNumber( object.getClass() ) ) {
      try {
        Number n = (Number)object;
        TT r = (TT)ClassUtils.castNumber( n, (Class< ? extends Number >)cls );
        return r;
      } catch ( Exception e ) {
        // ignore
      }
    }
    else if ( allowWrapping && cls != null && !cls.equals( Object.class ) ){
      // If evaluating doesn't work, maybe we need to wrap the value in a parameter.
      if ( cls.isAssignableFrom( Parameter.class ) ) {
        if ( Debug.isOn() ) Debug.error( false, "Warning: wrapping value with a parameter with null owner!" );
        return (TT)( new Parameter( null, null, object, null ) );
      } else if ( cls.isAssignableFrom( Expression.class ) ) {
        return (TT)( new Expression( object ) );
      } else if ( cls.isAssignableFrom( TimeVaryingMap.class ) ) {
        TimeVaryingMap<Object> tvm = new TimeVaryingMap<Object>();
        tvm.interpolation = TimeVaryingMap.STEP;
        tvm.put( SimpleTimepoint.zero, object );
        return (TT)(tvm);
      }
    }
    // Try pulling the only item out of an array or collection.
    if ( object != null && cls != null && !Collection.class.isAssignableFrom( cls ) && !cls.isArray() ) {
      if ( object.getClass().isArray() && ((Object[])object).length == 1 ) {
        object = ((Object[])object)[0];
        return evaluate( object, cls, propagate, allowWrapping );
      } else if ( object instanceof Collection && ((Collection<?>)object).size() == 1 ) {
        object = ((Collection<?>)object).iterator().next();
        return evaluate( object, cls, propagate, allowWrapping );
      }
    }
    
    if (object != null && cls != null &&
        !TimeVaryingMap.class.isAssignableFrom( cls ) &&
        object instanceof TimeVaryingMap) {
      TimeVaryingMap<?> tvm = (TimeVaryingMap<?>)object;
      if ( tvm.allValuesSame() ) {
        return evaluate( tvm.getValue( propagate ), cls, propagate, allowWrapping );
      }
    }
    
    
    TT r = null;
    try {
      r = (TT)object;
    } catch ( ClassCastException cce ) {
      Debug.errln( "Warning! No evaluation of " + object + " with type " + cls.getName() + "!" );
      throw cce;
    }
    return r;
  }
  
  /**
   * Determine whether the values of two objects are equal by evaluating them. 
   * @param o1
   * @param o2
   * @return whether the evaluations of o1 and o2 are equal.
   * @throws ClassCastException
   */
  public static boolean valuesEqual( Object o1, Object o2 ) throws ClassCastException {
    return valuesEqual( o1, o2, null, false, false );
  }
  public static boolean valuesEqual( Object o1, Object o2, Class<?> cls ) throws ClassCastException {
    return valuesEqual( o1, o2, cls, false, false );
  }
  public static boolean valuesEqual( Object o1, Object o2, Class<?> cls,
                                     boolean propagate,
                                     boolean allowWrapping ) throws ClassCastException {
    if ( o1 == o2 ) return true;
    //if ( o1 == null || o2 == null ) return false;
//    if ( (o1 instanceof Float && o2 instanceof Double ) || (o2 instanceof Float && o1 instanceof Double ) ) {
//      Debug.out( "" );
//    }
    Object v1 = null;
    Object v2 = null;
    try {
      v1 = evaluate( o1, cls, propagate, false );
      v2 = evaluate( o2, cls, propagate, false );
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
    if ( Utils.valuesEqual( v1, v2 ) ) return true;
    Class< ? > cls1 = null;
    if ( v1 != null ) {
      cls1 = v1.getClass();
    }
    if( !Utils.valuesEqual( o1, v1 ) ||  !Utils.valuesEqual( o2, v2 )) {
      if ( cls1 != null && cls1 != cls && valuesEqual( v2, v1, cls1 ) ) return true;
      if ( v2 != null ) {
        Class< ? > cls2 = v2.getClass();
        if ( cls2 != cls && cls2 != cls1 && valuesEqual( v1, v2, cls2 ) ) return true;
      }
    }
    return false;
    /*
    Class< ? > cls1 =
        ( cls != null ) ? cls : ( ( v1 == null ) ? null : v1.getClass() );
    Class< ? > cls2 =
        ( cls != null ) ? cls : ( ( v2 == null ) ? null : v2.getClass() );
    v1 = evaluate( v1, cls1, propagate, allowWrapping );
    v2 = evaluate( v2, cls1, propagate, allowWrapping );
    if ( Utils.valuesEqual( v1, v2 ) ) return true;
    Class< ? > cls1 =
        ( cls != null ) ? cls : ( ( v1 == null ) ? null : v1.getClass() );
    Class< ? > cls2 =
        ( cls != null ) ? cls : ( ( v2 == null ) ? null : v2.getClass() );
    Object v1 = evaluate( o1, cls1, propagate, allowWrapping );
    Object v2 = evaluate( o2, cls1, propagate, allowWrapping );
    if ( Utils.valuesEqual( v1, v2 ) ) return true;
    if ( cls1 != cls2 ) {
      v1 = evaluate( o1, cls2, propagate, allowWrapping );
      v2 = evaluate( o2, cls2, propagate, allowWrapping );      
    }
    return Utils.valuesEqual( v1, v2 );
    */
  }

  /**
   * A deep search looking for FunctionCalls
   */
  public List<FunctionCall> getFunctionCalls() {
    if ( form == Form.Function ) {
      return ((FunctionCall)expression).getFunctionCallsRecursively();
    }
    return Utils.getEmptyList();
  }

  public Class< ? extends ResultType > getResultType() {
    if ( this.resultType != null ) return this.resultType;
    ResultType r = null;
    try {
      r = (ResultType)evaluate( false );
    } catch ( ClassCastException e ) {
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
    if ( r != null ) {
      resultType = (Class< ? extends ResultType >)r.getClass();
    }
    return resultType;
  }
  
  @Override
  public Class< ? > getType() {
    return getResultType();
  }

  @Override
  public Class< ? > getPrimitiveType() {
    Class< ? > c = null;
    if ( getType() != null ) {
      c = ClassUtils.primitiveForClass( getType() );
      ResultType r = null;
      try {
        r = (ResultType)evaluate( false );
      } catch ( ClassCastException e ) {
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
      if ( c == null && expression != null && Wraps.class.isInstance( expression ) ) {
        c = ( (Wraps< ? >)expression).getPrimitiveType();
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
  public ResultType getValue( boolean propagate ) {
    try {
      return (ResultType)evaluate( propagate );
    } catch ( ClassCastException e ) {
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
    return null;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#hasValue()
   */
  @Override
  public boolean hasValue() {
    if ( expression instanceof Wraps ) {
      return ((Wraps) expression).hasValue();
    }
    return expression != null || form == Form.Value;
  }


  @Override
  public void setValue( ResultType value ) {
    if ( expression instanceof Wraps ) {
      ((Wraps<ResultType>)expression).setValue( value );
    }
  }

  /**
   * @return the evaluationSucceeded
   */
  public boolean didEvaluationSucceed() {
    return evaluationSucceeded;
  }

  @Override
  public void handleValueChangeEvent( Parameter< ? > parameter, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;


    if ( expression instanceof ParameterListener ) {
      ( (ParameterListener)expression ).handleValueChangeEvent( parameter, seen );
    }
  }

  @Override
  public void handleDomainChangeEvent( Parameter< ? > parameter, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;


    if ( expression instanceof ParameterListener ) {
      ( (ParameterListener)expression ).handleDomainChangeEvent( parameter, seen );
    }
  }

  @Override
  public void setStaleAnyReferencesTo( Parameter< ? > changedParameter, Set< HasParameters > seen ) {
    if ( Debug.isOn() ) Debug.outln( "@@ setStaleAnyReferencesTo() called from " + this.toShortString() );
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;
    
    TimeVaryingMap.setStaleAnyReferencesToForTimeVarying( changedParameter, seen );
    
    if ( expression instanceof ParameterListener ) {
      ( (ParameterListener)expression ).setStaleAnyReferencesTo( changedParameter, seen );
    }
  }

  @Override
  public void detach( Parameter< ? > parameter ) {
    if ( expression instanceof ParameterListener ) {
      ( (ParameterListener)expression ).detach( parameter );
    }
  }

  @Override
  public boolean refresh( Parameter< ? > parameter ) {
    if ( expression instanceof ParameterListener ) {
      return ( (ParameterListener)expression ).refresh( parameter );
    }
    return false;
  }

  @Override
  public < T > boolean pickParameterValue( Variable< T > variable ) {
    if ( expression instanceof ParameterListener ) {
      return ( (ParameterListener)expression ).pickParameterValue( variable );
    }
    return false;
  }

  @Override
  public String getName() {
    if ( expression instanceof ParameterListener ) {
      return ( (ParameterListener)expression ).getName();
    }
    return null;
  }

  @Override
  public < T > T translate( Variable< T > p , Object o , Class< ? > type  ) {
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
