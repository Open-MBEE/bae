package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.event.Functions.SuggestiveFunctionCall;
import gov.nasa.jpl.ae.solver.*;
import gov.nasa.jpl.ae.util.distributions.BooleanDistribution;
import gov.nasa.jpl.mbee.util.*;
import gov.nasa.jpl.mbee.util.Random;

import java.lang.reflect.InvocationTargetException;
import java.util.*;


/**
 * TODO -- Look at javaparser at http://code.google.com/p/javaparser/ and JavaCC
 * TODO -- Consider using Ptolemy's expression parser
 */

/**
 * @author bclement
 * 
 */
public class ConstraintExpression extends Expression< Boolean >
                                  implements Constraint { // ,
                                                          // ParameterListener
                                                          // {

  protected boolean applicableAsDependency = false;
  protected Parameter<?> dependentVar = null;
  public static double probabilityOfApplyingDependency = 0.7;

  /**
   * @param value
   */
  public ConstraintExpression( Boolean value ) {
    super( value );
  }

  /**
   * @param parameter
   */
  public ConstraintExpression( Parameter< Boolean > parameter ) {
    super( parameter );
  }

  // /**
  // * @param method
  // */
  // public Constraint(Method method) {
  // super(method);
  // }

  /**
   * @param expression
   */
  public ConstraintExpression( Expression< Boolean > expression ) {
    super( expression, true );
  }

  /**
   * @param functionCall
   */
  public ConstraintExpression( FunctionCall functionCall ) {
    super( functionCall );
  }

  /**
   * (non-Javadoc)
   * 
   * @see gov.nasa.jpl.ae.solver.Constraint#isSatisfied(boolean, Set)
   */
  @Override
  public boolean isSatisfied(boolean deep, Set< Satisfiable > seen) {
    Boolean sat = null;
    try {
      Object o = evaluate( this, Boolean.class, false );
      if ( o instanceof BooleanDistribution ) {
        sat = ((BooleanDistribution)o).probability() > 0.0;
      } else {
        sat = Utils.isTrue( o, false );
      }
      if (!Boolean.TRUE.equals(sat)) {
        if ( o instanceof Wraps ) {
          Object oo = ((Wraps)o).getValue(false);
          sat = Utils.isTrue(oo, false);
        }
      }
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
    if ( sat == null ) sat = Boolean.FALSE;
    if ( false && deep & sat ) {
      sat = HasParameters.Helper.isSatisfied( this, false, null );
    }
    if ( Debug.isOn() ) Debug.outln( "ConstraintExpression.isSatisfied() = " + sat + ": " + this );
    return sat;
  }

  public <T> boolean applyAsIfDependency() {
    return applyAsIfDependency( null, null );
  }

  public <T> boolean applyAsIfDependency( Parameter<?> noChangeParam, Set<HasParameters> seen ) {

    Pair< Boolean, Set< HasParameters > > pair =
            Utils.seen( this, true, seen );
    if ( pair.first ) {
      return false;
    }
    seen = pair.second;

    applicableAsDependency = false;
    Pair<Parameter<?>, Object> p = valueToSetLikeDependency();
    if ( p == null ) return false;
    Parameter<T> dependentVar = (Parameter<T>) p.first;
    this.dependentVar = dependentVar;
    if ( dependentVar == null ) return false;
    if ( dependentVar == noChangeParam ) return false;

    T oldVal = dependentVar.getValueNoPropagate();

    // fix domain for null
    if ( p.second == null && dependentVar.getDomain() != null && !dependentVar.getDomain().isNullInDomain() && isValueExpGrounded ) {
      if ( dependentVar.getDomain() instanceof ObjectDomain ) {
        ( (ObjectDomain)dependentVar.getDomain() ).add( null );
      }
      if ( dependentVar.getDomain() instanceof ClassDomain ) {
        ( (ClassDomain)dependentVar.getDomain() ).setNullInDomain( true );
      }
    }

    applicableAsDependency = true;
    dependentVar.setValue((T)p.second, true, seen);
    T newVal = dependentVar.getValueNoPropagate();
    if ( oldVal == newVal ) return false;
    return true;
  }

  protected static boolean isValueExpGrounded = false;

  public Pair<Parameter<?>, Object> valueToSetLikeDependency() {
    Pair<Parameter<?>, Object> p = dependencyLikeVar();
    if ( p == null ) return p;
    if ( p.first == null ) return null;
    Parameter<?> dependentVar = p.first;
    Object value = null;
    boolean succ = false;
    try {
      value = Expression.evaluateDeep(p.second, dependentVar.getType(), true, false);
      if (!(p.second instanceof Call) || ((Call) p.second).didEvaluationSucceed())
        succ = true;
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    } catch (InvocationTargetException e) {
      e.printStackTrace();
    } catch (InstantiationException e) {
      e.printStackTrace();
    }
    if ( !succ ) return null;

    // HACK -- using static variable instead of stuffing in return value!
    isValueExpGrounded = true;
    if ( p.second instanceof Groundable ) {
      isValueExpGrounded = ((Groundable)p.second).isGrounded( false, null );
    }

    return new Pair<Parameter<?>, Object>(dependentVar, value);
  }

  protected static Set<String> equalFunctionNames =
          new LinkedHashSet<String>(Utils.arrayAsList(new String[]{
                  "eq", "equal", "equals", "is", "=", "==", "equivalent"
          }));

  public boolean canBeDependency() {
    if ( !isEqualsFunction() ) {
      return false;
    }
    Pair<Parameter<?>, Object> pair = dependencyLikeVar();
    if ( pair == null || pair.first == null ) return false;
//    try {
//      Parameter<?> p = Expression.evaluate(pair.first, Parameter.class, false);
//      if ( p == null ) return false;
//      if ( pair.second == null ||
//           !HasParameters.Helper.hasParameter(pair.second, p, false, null, true) ) {
        return true;
//      }
//    } catch (IllegalAccessException e) {
//    } catch (InvocationTargetException e) {
//    } catch (InstantiationException e) {
//    }
//    return false;
  }

  public boolean isEqualsFunction() {
    if ( this.form != Form.Function || !(this.expression instanceof FunctionCall) ) return false;
    FunctionCall fc = (FunctionCall)this.expression;
    if ( equalFunctionNames.contains(fc.method.getName().toLowerCase()) ) {
      return true;
    }
    return false;
  }

  public Pair<Parameter<?>, Object> dependencyLikeVar() {
    ConstraintExpression ce = this;
    if ( !( ce.expression instanceof Functions.EQ ) ) {
      return null;
    }
    Vector<Object> args = ((Functions.EQ) ce.expression).getArguments();
    if ( args == null || args.size() < 2 ) {  // It should always be 2, but . . .
      return null;
    }
    for ( int i = 0; i < args.size(); ++i ) {
      try {
        Parameter<?> p = null;
        try {
          p = Expression.evaluate(args.get(i), Parameter.class, false);
        } catch (Throwable t) {
          // ignore
        }
        if ( p == null ) continue;//&& !set.contains(p) ) {
        for ( int j = 0; j < args.size(); ++j ) {
          if ( j == i ) continue;
          Object otherArg = args.get( j );
//          if ( otherArg instanceof HasParameters ) {
//            if ( !HasParameters.Helper.hasFreeParameter(otherArg, false, null) ) {
//              return new Pair<Parameter<?>, Object>(p, otherArg);
//            }
//          } else {
            return new Pair<Parameter<?>, Object>(p, otherArg);
//          }
        }
      } catch (Throwable e) {
//        e.printStackTrace();
//      } catch (InvocationTargetException e) {
//        e.printStackTrace();
//      } catch (InstantiationException e) {
//        e.printStackTrace();
      }
    }
    return null;
  }

  private static boolean pickDeep = true;
  /**
   * (non-Javadoc)
   * 
   * @see gov.nasa.jpl.ae.solver.Constraint#satisfy(boolean, Set)
   */
  @Override
  public boolean satisfy(boolean deep, Set< Satisfiable > seen) {
    //boolean wasDebugOn = Debug.isOn();
    boolean satisfied = true;
    //Debug.turnOn();
    if ( Debug.isOn() ) Debug.outln( "ConstraintExpression.satisfy() for " + this );
    if ( isSatisfied(deep, seen) ) return true;
    
    // Try satisfying the contained Parameters individually to make sure they
    // have valid values.
    HasParameters.Helper.satisfy( this, true, null );

    if ( Random.global.nextDouble() < probabilityOfApplyingDependency && !isSatisfied(deep, seen) )  {
      applyAsIfDependency();
    }

    // Now try choosing new values for the variables to meet this constraint.
    if ( Parameter.allowPickValue && !isSatisfied(deep, seen) ) {
      Set< Variable< ? > > vars = new LinkedHashSet<Variable<?>>(getVariables());
      if ( Debug.isOn() ) Debug.outln("ConstraintExpression.isSatisfied()   Picking values for " + vars + " in " + this);

      ArrayList<Variable<?>> copy = new ArrayList<Variable<?>>(vars);
      boolean pickingDeep = false;
      if ( pickDeep && Random.global.nextDouble() < 0.1) {
        // add indepedent vars for dependent vars
        for ( Variable< ? > v : copy ) {
          if ( v instanceof Parameter ) {
            List<Variable<?>> iVars = ((Parameter<?>)v).getIndependentVariables();
            if ( !Utils.isNullOrEmpty( iVars ) ) {
              if (((Parameter<?>)v).isDependent()) {
                vars.remove( v );
              }
              vars.addAll( iVars );
              pickingDeep = true;
            }
          }
        }

      }

      Variable<?>[] a = new Variable<?>[vars.size()];
      vars.toArray( a );
      for ( Variable< ? > v : Utils.scramble(a) ) {
        // Make sure the variable is not dependent and not locked.
        if ( ( !( v instanceof Parameter ) || (!( (Parameter)v ).isDependent() || Random.global.nextDouble() < 0.1) )
             && ( !applicableAsDependency || dependentVar != v )
                  && ( v.getDomain() == null || v.getDomain().magnitude() != 1 ) ) {//&& (!(v.getValue( false ) instanceof TimeVaryingMap) ||  ) {
          boolean picked = false;
          boolean p = pickingDeep && !copy.contains( v );
          if ( !p ) {
            picked = pickParameterValue( v );
          }
          if ( p || !picked ) {
            picked = v.pickValue();
          }
          if (picked && isSatisfied(deep, seen)) break;
        }
      }
    }
    satisfied = isSatisfied(deep, seen);
    //if ( !wasDebugOn ) Debug.turnOff();
    return satisfied;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Constraint#getVariables()
   */
  @Override
  public Set< Variable< ? > > getVariables() {
    return ParameterConstraint.Helper.getVariables( this, false, null );
  }

  protected static boolean variableHasPrimitiveValue( Variable<?> v ) {
    if ( v == null ) return false;
    if ( v.getType() != null && !ClassUtils.isPrimitive( v.getType() ) ) {
      return false;
    }
    Object o = v.getValue( false );
    if ( o == null ) return true;
    if ( o != null && ClassUtils.isPrimitive( o ) ) {
      return true;
    }
    // Not sure about support for the Expression case below.
    if ( o instanceof Expression && ClassUtils.isPrimitive( ((Expression<?>)o).expression) ) {
      return true;
    }
    return false;
  }
  
  @Override
  public < T > boolean pickParameterValue( Variable< T > v ) {
    // Currently do not support picking values for non-primitives (like TimeVaryingMap).
//    boolean isPrimitive = variableHasPrimitiveValue( v );
//    boolean hasChoices = v.getDomain() != null && !v.getDomain().isEmpty();
    if ( expression instanceof Suggester &&
         //isPrimitive && hasChoices &&
         (!(v instanceof Parameter) || !(expression instanceof Call) || ((Call)expression).hasParameter((Parameter<?>)v, true, null))) {
      T newValue = ((Suggester)expression).pickValue( v );
      if ( newValue != null ) {
        //Debug.getInstance().logForce( "////////////////////   picking " + newValue + " for " + v + " in " + this );
        //Debug.turnOn();
        //if ( Debug.isOn() ) Debug.outln( "////////////////////   picking " + newValue + " for " + v + " in " + this );
       // Debug.turnOff();
        if ( v.getType() != null ) {
          try {
            Object ooo =
                    Expression.evaluate(newValue, v.getType(), true, false);
            newValue = (T)ooo;
          } catch (IllegalAccessException e) {
            e.printStackTrace();
          } catch (InvocationTargetException e) {
            e.printStackTrace();
          } catch (InstantiationException e) {
            e.printStackTrace();
          }
        }
        if ( v.getType() == null || v.getType().isInstance(newValue) ) {
          if ( Debug.isOn() ) {
            Debug.outln( "////////////////////   picking " + newValue + " for " + v +
                            " in " + this );
          }
          setValue(v, newValue);
          return true;
        }
      }
    }
    // TODO
    if ( Debug.isOn() ) {
      Debug.outln( "////////////////////   not picking value for " + v + " in " + this );
    }
    return false;
  }

  protected < T > void setValue( Variable<T> v, T newValue ) {
    v.setValue( newValue );
  }
  
  @Override
  public < T > boolean restrictDomain( Variable< T > v ) {
    switch ( form ) {
      case Function:
        FunctionCall f = (FunctionCall)expression;
        if ( f instanceof SuggestiveFunctionCall ) {
          //((SuggestiveFunctionCall)f).res
        }
      case Constructor:
      case Parameter:
      case Value:
      case None:
    }
    // TODO
    assert(false);
    return ParameterConstraint.Helper.restrictDomain( this, v );
  }

  @Override
  public < T > boolean isFree( Variable< T > v ) {
    return ParameterConstraint.Helper.isFree( this, v, false, null );
  }

  @Override
  public < T > boolean isDependent( Variable< T > v ) {
    return ParameterConstraint.Helper.isDependent( this, v, false, null );
  }

  @Override
  public Set< Variable< ? > > getFreeVariables() {
    Set< Variable< ? > > vars = getVariables();
    Set< Variable< ? > > freeVars = new LinkedHashSet< Variable<?> >();
    for ( Variable<?> v : vars) {
      if ( v instanceof Parameter && !((Parameter<?>)v).isDependent()) {
        freeVars.add( v );
      }
    }
    return freeVars;
  }

  @Override
  public void setFreeVariables( Set< Variable< ? > > freeVariables ) {
    ParameterConstraint.Helper.setFreeVariables( this, freeVariables, false, null );
    // TODO
    assert(false);
//    if ( freeParameters == null ) {
//      freeParameters = new TreeSet< Parameter< ? > >();
//    }
//    for ( Variable< ? > v : freeVariables ) {
//      if ( v instanceof Parameter< ? > ) {
//        freeParameters.add( (Parameter< ? >)v );
//      }
//    }
  }

  @Override
  public int compareTo( Constraint o ) {
    return compareTo( o, true );
  }
  public int compareTo( Constraint o, boolean checkId ) {
    if ( this == o ) return 0;
    if ( o == null ) return -1;
    if ( checkId ) return CompareUtils.compare( getId(), o.getId() );
    if ( o instanceof Expression ) {
      int compare = super.compareTo( (Expression< ? >)o );
      if ( compare != 0 ) return compare;
    }
    return ParameterConstraint.Helper.compareTo( this, o );
//    return this.toString().compareTo( o.toString() );
  }

//  @Override
//  public boolean isStale() {
//    if ( expression instanceof La)
//    return ParameterConstraint.Helper.isStale( this, false, null );
//    //return HasParameters.Helper.isStale( this, false );
////    for ( Parameter< ? > p : getParameters( false ) ) {
////      if ( p.isStale() ) return true;
////    }
////    return false;
//  }

//  @Override
//  public void setStale( boolean staleness ) {
//    if ( Debug.isOn() ) Debug.outln( "setStale(" + staleness + ") to " + this );
//    ParameterConstraint.Helper.setStale( this, staleness );
//  }

  @Override public void handleValueChangeEvent( Parameter<?> parameter,
                                                Set<HasParameters> seen ) {
    super.handleValueChangeEvent( parameter, seen );
    //applyAsIfDependency(parameter, seen);
  }

  @Override
  public < T > T evaluate( Class< T > cls, boolean propagate ) {
    T t = Evaluatable.Helper.evaluate( this, cls, true, propagate, false, null );
    if ( t != null ) return t;
    if ( cls != null && cls.isAssignableFrom( Boolean.class ) ) {
      Boolean b = isSatisfied( false, null );
      return (T)b;
    }
    return null;
  }

  @Override
  public List< Variable< ? > >
         getVariablesOnWhichDepends( Variable< ? > variable ) {
    Set< Variable< ? > > vars = getVariables();
    if ( vars.contains( variable ) ) {
      ArrayList<Variable<?>> varList = new ArrayList< Variable<?> >( vars );
      varList.remove( variable );
      return varList;
    }
    return null;
  }

  public static double getProbabilityOfApplyingDependency() {
    return probabilityOfApplyingDependency;
  }

  public static void setProbabilityOfApplyingDependency(
          double probabilityOfApplyingDependency ) {
    ConstraintExpression.probabilityOfApplyingDependency =
            probabilityOfApplyingDependency;
  }


}
