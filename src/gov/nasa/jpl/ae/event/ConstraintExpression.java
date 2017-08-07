package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.event.Functions.SuggestiveFunctionCall;
import gov.nasa.jpl.ae.solver.Constraint;
import gov.nasa.jpl.ae.solver.Satisfiable;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.mbee.util.*;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


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
      sat = Utils.isTrue( o, false );
      if (!sat.equals(Boolean.TRUE) ) {
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
                  && ( v.getDomain() == null || v.getDomain().magnitude() != 1 ) ) {
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
    boolean isPrimitive = variableHasPrimitiveValue( v );
    boolean hasChoices = v.getDomain() != null && !v.getDomain().isEmpty();
    if ( expression instanceof Suggester && isPrimitive && hasChoices && (!(expression instanceof Call) || ((Call)expression).arguments.contains(v))) {
      T newValue = ((Suggester)expression).pickValue( v );
      if ( newValue != null ) {
        //Debug.getInstance().logForce( "////////////////////   picking " + newValue + " for " + v + " in " + this );
        //Debug.turnOn();
        //if ( Debug.isOn() ) Debug.outln( "////////////////////   picking " + newValue + " for " + v + " in " + this );
        System.out.println( "////////////////////   picking " + newValue + " for " + v + " in " + this );
       // Debug.turnOff();
        setValue( v, newValue );
        return true;
      }
    }
    // TODO
    //Debug.turnOn();
    //if ( Debug.isOn() ) Debug.outln( "////////////////////   not picking value for " + v + " in " + this );
    if ( !isPrimitive ) {
      System.out.println( "////////////////////   not picking value for nonPrimitive " + v + " in " + this );
    } else if ( !hasChoices ) {
      System.out.println( "////////////////////   not picking value for empty domain for " + v + " in " + this );
    } else {
      System.out.println( "////////////////////   not picking value for " + v + " in " + this );
    }
    //Debug.turnOff();
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

  
}
