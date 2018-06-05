/**
 *
 */
package gov.nasa.jpl.ae.event;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.Map.Entry;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import junit.framework.Assert;
import gov.nasa.jpl.ae.solver.CollectionTree;
import gov.nasa.jpl.ae.solver.Constraint;
import gov.nasa.jpl.ae.solver.ConstraintLoopSolver;
import gov.nasa.jpl.ae.solver.Domain;
import gov.nasa.jpl.ae.solver.HasConstraints;
import gov.nasa.jpl.ae.solver.HasIdImpl;
import gov.nasa.jpl.mbee.util.Random;
import gov.nasa.jpl.mbee.util.Timer;
import gov.nasa.jpl.ae.solver.Satisfiable;
import gov.nasa.jpl.ae.solver.Solver;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.MoreToString;
import gov.nasa.jpl.mbee.util.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * A class that manages Parameters, Dependencies, and Constraints.
 *
 */
public class ParameterListenerImpl extends HasIdImpl implements Cloneable,
                                   Groundable, Satisfiable, ParameterListener,
                                   HasConstraints, HasTimeVaryingObjects,
                                   HasOwner, HasEvents,
                                   Comparable< ParameterListenerImpl > {

  public static boolean usingArcConsistency = true;
  public static boolean arcConsistencyQuiet = true;

  // Constants

  protected double timeoutSeconds = 900.0;
  protected int maxLoopsWithNoProgress = 50;
  protected long maxPassesAtConstraints = 10000;
  protected boolean usingTimeLimit = false;
  protected boolean usingLoopLimit = true;

  protected boolean snapshotSimulationDuringSolve = false;
  protected boolean snapshotToSameFile = true;
  protected int loopsPerSnapshot = 20; // set to 1 to take snapshot every time
  protected String baseSnapshotFileName = "simulationSnapshot.txt";
  public boolean amTopEventToSimulate = false;

//  protected boolean redirectStdOut = false;
//  protected PrintStream oldOut = System.out;
//  protected PrintStream oldErr = System.err;

  // Static members

  protected static int counter = 0;
  public static boolean settingTimeVaryingMapOwners = false;

  // Other Members

  protected String name = null;
  protected List< Parameter< ? > > parameters =
      new ArrayList< Parameter< ? > >();
  protected List< ConstraintExpression > constraintExpressions =
      new ArrayList< ConstraintExpression >();
  protected CollectionTree< Constraint > constraintCollection = null;
  // TODO -- REVIEW -- should dependencies these be folded in with effects?
  protected ArrayList< Dependency< ? > > dependencies =
      new ArrayList< Dependency< ? > >();
  protected ArrayList< Dependency< ? > > externalDependencies =
      new ArrayList< Dependency< ? > >();
  protected Solver solver = new ConstraintLoopSolver();

  protected Set< TimeVarying< ?, ? > > timeVaryingObjects =
      new LinkedHashSet< TimeVarying< ?, ? > >();
  protected boolean usingCollectionTree = false;
  protected Object owner = null;
  protected Object enclosingInstance = null;

  // TODO -- Need to keep a collection of ParameterListeners (just as
  // DurativeEvent has getEvents())

  public String toKString() {
    String superClass = super.getClass().getName();
    List< String > paramStrings = new ArrayList< String >();
    for ( Parameter< ? > p : parameters ) {
      String pString = p.toKString();
      paramStrings.add( pString );
      System.out.println( pString );
    }

    List< String > constraintStrings = new ArrayList< String >();
    for ( ConstraintExpression c : constraintExpressions ) {
      System.out.println( c.toString() );
    }
    return null;

  }

  public ParameterListenerImpl() {
    this( (String)null );
  }

  public ParameterListenerImpl( String name ) {
    setName( name );
  }

  public ParameterListenerImpl( ParameterListenerImpl parameterListenerImpl ) {
    this( null, parameterListenerImpl );
  }

  public ParameterListenerImpl( String name,
                                ParameterListenerImpl parameterListenerImpl ) {
    setName( name );
    setOwner( parameterListenerImpl.getOwner() );
    // copy containers after clearing
    constraintExpressions.clear();
    dependencies.clear();
    for ( ConstraintExpression c : parameterListenerImpl.constraintExpressions ) {
      ConstraintExpression nc = new ConstraintExpression( c );
      constraintExpressions.add( nc );
    }
    for ( Dependency< ? > d : parameterListenerImpl.dependencies ) {
      Dependency< ? > nd = new Dependency( d );
      dependencies.add( nd );
    }

    // We need to make sure the copied constraints are on this event's
    // parameters and not that of the copied constraints.
    List< Pair< Parameter< ? >, Parameter< ? > > > subList =
        buildSubstitutionList( parameterListenerImpl );
    for ( Pair< Parameter< ? >, Parameter< ? > > p : subList ) {
      substitute( p.first, p.second, false, null );
    }
  }

  public static void reset() {
    counter = 0;
  }

  /**
   * Sets all constraints to stale to force the engine to visit every single one at least once.
   * Used at the end of the generated constructors after all parameters have been initialized.
   */
  public void setAllConstraintsToStale() {
    for(ConstraintExpression c : constraintExpressions) {
      c.setStale(true, true, null);
    }
  }

  public static final boolean smartEquals = true;

  @Override
  public boolean equals(Object obj) {
    if ( obj == null ) return false;
    if ( super.equals(obj) ) return true;
    if ( !smartEquals ) return false;
    if ( obj instanceof ParameterListenerImpl ) {
      ParameterListenerImpl opl = (ParameterListenerImpl) obj;
      int comp = compareTo(opl, true, false, true );
      return comp == 0;
    }
    return false;
  }

  @Override
  public boolean substitute( Parameter< ? > p1, Parameter< ? > p2, boolean deep,
                             Set< HasParameters > seen ) {
    return substitute( p1, (Object)p2, deep, seen );
  }

  @Override
  public boolean substitute( Parameter< ? > p1, Object p2, boolean deep,
                             Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;
    // if ( Utils.seen( this, deep, seen ) ) return false;
    boolean subbed = false;
    boolean s = HasParameters.Helper.substitute( constraintExpressions, p1, p2,
                                                 deep, seen );
    subbed = subbed || s;
    s = HasParameters.Helper.substitute( dependencies, p1, p2, deep, seen );
    subbed = subbed || s;
    return subbed;
  }

  // Build a list of pairs of parameters, each in this event paired with the
  // corresponding ones in another event. Subclasses may need to override this
  // to add their own parameters.
  protected List< Pair< Parameter< ? >, Parameter< ? > > >
            buildSubstitutionList( ParameterListener parameterListener ) {
    ArrayList< Pair< Parameter< ? >, Parameter< ? > > > subList =
        new ArrayList< Pair< Parameter< ? >, Parameter< ? > > >();
    Iterator< Parameter< ? > > i1 = parameters.iterator();
    Iterator< Parameter< ? > > i2 =
        parameterListener.getParameters( false, null ).iterator();
    while ( i1.hasNext() ) {
      subList.add( new Pair< Parameter< ? >, Parameter< ? > >( i1.next(),
                                                               i2.next() ) );
    }
    return subList;
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#clone()
   */
  @Override
  public Object clone() {
    return new ParameterListenerImpl( this );
  }

  @Override
  public String toShortString() {
    return getName();
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return toString( Debug.isOn(), false, null );
  }

  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen,
                          Map< String, Object > otherOptions ) {
    Pair< Boolean, Set< Object > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) deep = false;
    seen = pair.second;
    StringBuffer sb = new StringBuffer();
    sb.append( getClass().getName() ); // super.toString() adds hash code
    if ( withHash ) {
      sb.append( "@" + this.hashCode() );
    }
    // TODO -- REVIEW -- Can this just call
    // MoreToString.Helper.toString(params)?
    sb.append( "(" );
    boolean first = true;

    if ( !Utils.isNullOrEmpty( getName() ) ) {
      if ( first ) first = false;
      else sb.append( ", " );
      sb.append( "name=" + getName() );
    }

    String qName = getQualifiedName();
    if ( !Utils.isNullOrEmpty( qName ) ) {
      if ( first ) first = false;
      else sb.append( ", " );
      sb.append( "qualifiedName=" + qName );
    }

    if ( first ) first = false;
    else sb.append( ", " );
    sb.append( "id=" + id );

    if ( first ) first = false;
    else sb.append( ", " );
    sb.append( "qualifiedId=" + getQualifiedId() );

    Set< Parameter< ? > > allParams = getParameters( false, null );
    for ( Parameter< ? > p : allParams ) {
      if ( first ) first = false;
      else sb.append( ", " );
      if ( deep && p.getValueNoPropagate() instanceof ParameterListenerImpl ) {
        sb.append( ( (ParameterListenerImpl)p.getValueNoPropagate() ).toString( withHash,
                                                                                true,
                                                                                seen ) );
      } else {
        sb.append( p.toString( false, withHash, deep, seen, otherOptions ) );
      }
    }
    sb.append( ")" );
    return sb.toString();
  }

  public String kSolutionString() {
    Boolean sat = isSatisfied(true, null);
    StringBuffer sb = new StringBuffer();
    sb.append((sat? "Satisfied" : "Unsatisfied") + "\n");
    sb.append( "Solution:\n" );
    sb.append(kSolutionString(0));
    sb.append( "Requirements:\n" );
    sb.append( solutionRequirements() );
        
    if (!sat) {
      sb.append( "Unsatisfied Constraints: "  + getUnsatisfiedConstraints());
    }
    
    return sb.toString();
  }

  public JSONObject kSolutionJson() {
    JSONObject json = new JSONObject();
    Boolean sat = isSatisfied(true, null);
    json.put("satisfied", "" + sat.booleanValue());
    String partialSolution = kSolutionString(0);
    json.put("solution", partialSolution);

    JSONArray jarr = solutionRequirements();
    json.put("constraints", jarr);

    if (!sat) {
      List<String> list = new ArrayList<String>();
      List<ConstraintExpression> unsatisfiedConstraints = getUnsatisfiedConstraints();
      for (ConstraintExpression c : unsatisfiedConstraints) {
        list.add("" + c);
      }
      json.put("violatedConstraints", list);
    }

    return json;
  }

  public JSONArray solutionRequirements() {
    List<String> reqs = solutionRequirementList();
    JSONArray jarr = new JSONArray(reqs);
    return jarr;
  }


  public List<String> solutionRequirementList() {
    List<String> reqs = JSONArrToReqs(kSolutionJSONArr());
    ArrayList<String> arr = new ArrayList<String>();
    for (String s : reqs) {
      arr.add( "req " + s );
    }
    return arr;
  }


  public JSONArray kSolutionJSONArr() {
    JSONArray value = new JSONArray();
    Set< Parameter< ? > > allParams = getParameters( false, null );
    for ( Parameter< ? > p : allParams ) {
      JSONObject param = new JSONObject();
      if ( p.getValueNoPropagate() instanceof ParameterListenerImpl ) {
        ParameterListenerImpl pLI =
            ( (ParameterListenerImpl)p.getValueNoPropagate() );
        param.put( "name", p.getName() );
        param.put( "type", "class" );
        JSONArray val = pLI.kSolutionJSONArr();
        param.put( "value", val );
        
      } else {
        param.put( "name", p.getName() );
        param.put( "type", "primitive" );
        if (p.getValue() == null) {
          param.put("value", JSONObject.NULL);
        } else {
          param.put("value", "" + p.getValue());
        }
      }
      value.put( param );
    }
    return value;
    
  }


  public static List<String> JSONArrToReqs(JSONArray JSONArr) {
    if ( JSONArr == null ) {
      return new ArrayList<String>();
    }
    List<String> strings = new ArrayList<String>();
    int length = JSONArr.length();
    for (int i = 0; i < length; i++ ) {
      JSONObject obj = JSONArr.getJSONObject( i );
      if (obj.opt( "type" ) != null && obj.opt( "type" ).equals("primitive")) {
        strings.add( obj.opt("name") + " = " + obj.opt( "value" )) ;
      } else {
        List<String> paramStrings = JSONArrToReqs(obj.optJSONArray("value"));
        String name = obj.optString( "name" );
        for (String s : paramStrings) {
          strings.add( name + "." + s );
        }
      }
    }
    return strings;
  }

  public String kSolutionString( int indent ) {

    String indentString = "";
    for (int i = 0 ; i < indent; i++) {
      indentString += "   ";
    }
    StringBuffer sb = new StringBuffer();
    Set< Parameter< ? > > allParams = getParameters( false, null );
    for ( Parameter< ? > p : allParams ) {
        if ( p.getValueNoPropagate() instanceof ParameterListenerImpl ) {
          ParameterListenerImpl pLI =
              ( (ParameterListenerImpl)p.getValueNoPropagate() );
          sb.append( indentString + p.getName() + " = " + pLI.getClass().getSimpleName()
                     + " {\n" );
          sb.append(  pLI.kSolutionString( indent + 1 ) );
          sb.append( indentString + "}\n" );
        } else {
          sb.append(indentString + p.getName() + " = " + MoreToString.Helper.toStringWithSquareBracesForLists((Object) p.getValue(), true, true, null) + "\n" );
        }
      
    }

    return sb.toString();
  }



  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen ) {
    return toString( withHash, deep, seen, null );
  }

  public static boolean setUsingArcConsistency(boolean b) {
    usingArcConsistency = b;
    return true;
  }

  public < T1, T2 > Dependency< ? > addDependency( Parameter< T1 > p, Call e,
                                                   boolean fire ) {
    return addDependency( p, new Expression< T2 >( e ), fire );
  }

  // TODO -- separate this method and removeDependency from Event to
  // HasDependencies?
  public < T1, T2 > Dependency< ? >
         addDependency( Parameter< T1 > p, Expression< T2 > e, boolean fire ) {
    // public < T > Dependency< ? > addDependency( Parameter< T > p, Expression<
    // T > e, boolean fire ) {
    Debug.errorOnNull( "try to add a dependency on null", p );
    if ( p == null ) return null;

    // Check if p is in enclosing class and call the enclosing class's
    // addDependency()
    if ( p.getOwner() != null && p.getOwner() != this ) {
      if ( p.getOwner() instanceof ParameterListenerImpl ) {
        ParameterListenerImpl pli = (ParameterListenerImpl)p.getOwner();
        Dependency< ? > d = pli.addDependency( p, e );
        externalDependencies.add( d );
        return d;
      }
      Debug.error( true,
                   false, getName()
                          + " adding a dependency on a parameter it doesn't own." );
    }
    removeDependenciesForParameter( p );
    Dependency< ? > d = new Dependency< T1 >( p, e );
    // Default domains are shared. The domains need to be cloned before
    // intersecting them.
    // if ( e.type == Expression.Type.Parameter ) {
    // Parameter< T > ep = (Parameter< T >)e.expression;
    // if ( ep.getDomain() instanceof AbstractRangeDomain &&
    // p.getDomain() instanceof AbstractRangeDomain ) {
    // AbstractRangeDomain< T > ard = (AbstractRangeDomain< T >)p.getDomain();
    // AbstractRangeDomain< T > eard = (AbstractRangeDomain< T >)ep.getDomain();
    // eard.intersectRestrict( ard );
    // ard.intersectRestrict( eard );
    // }
    // }
    dependencies.add( d );
    if ( fire && d.getExpression() != null
         && d.getExpression().isGrounded( false, null ) ) {
      d.apply();
    }
    return d;
  }

  public < T1, T2 > Dependency< ? > addDependency( Parameter< T1 > p,
                                                   Parameter< T2 > source ) {
    return addDependency( p, new Expression< T2 >( source ) );
  }

  public < T1, T2 > Dependency< ? > addDependency( Parameter< T1 > p,
                                                   Expression< T2 > e ) {
    // public < T > Dependency< ? > addDependency( Parameter< T > p, Expression<
    // T > e ) {
    return addDependency( p, e, true );
  }

  public < T > boolean removeDependenciesForParameter( Parameter< T > p ) {
    // assert p.getOwner() == null || p.getOwner() == this;
    boolean removed = false;
    int ct = dependencies.size() - 1;
    while ( ct >= 0 ) {
      Dependency< ? > d = dependencies.get( ct );
      if ( // d != null &&
      d.parameter == p ) {
        dependencies.remove( ct );
        removed = true;
      }
      --ct;
    }
    return removed;
  }

  // public void deleteme() {
  // dependencies.remove( 0 );
  // dependencies.remove( new Dependency(null) );
  // dependencies.iterator();
  // dependencies.listIterator();
  // dependencies.listIterator(2);
  // dependencies.removeAll( dependencies );
  // dependencies.retainAll( dependencies );
  // dependencies.set( 0, null );
  // dependencies.toArray( null );
  // }

  public < T > Dependency< ? > getDependency( Parameter< T > p ) {
    // Iterator< Dependency< ? > > i = dependencies.iterator();
    // while ( i.hasNext() ) {
    for ( Dependency< ? > d : dependencies ) {
      // Dependency< ? > d = i.next();
      if ( // d != null &&
      d.parameter == p ) {
        return d;
      }
    }
    return null;
  }

  @Override
  public boolean isGrounded( boolean deep, Set< Groundable > seen ) {
    Pair< Boolean, Set< Groundable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;
    for ( Parameter< ? > p : parameters ) {
      if ( p != null && !p.isGrounded( deep, seen ) ) return false;
    }
    return true;
  }

  @Override
  public boolean ground( boolean deep, Set< Groundable > seen ) {
    Pair< Boolean, Set< Groundable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;
    // final double timeoutMilliseconds = 50.0;
    // double startTime = System.currentTimeMillis();
    // double curTime = startTime;
    long numLoops = 0;

    boolean satisfied = false;
    boolean first = true;
    while ( first && numLoops < this.maxPassesAtConstraints ) {
      // || ( !satisfied && ( ( curTime = System.currentTimeMillis() )
      // - startTime < timeoutMilliseconds ) ) ) {
      ++numLoops;
      satisfied = true;
      first = false;
      Collection< Parameter< ? > > freeParams =
          getFreeParameters( false, null );
      for ( Parameter< ? > p : freeParams ) {
        if ( !p.isGrounded( deep, null ) ) {
          if ( !p.ground( deep, seen ) ) {
            satisfied = false;
          }
        }
      }
      for ( Dependency< ? > d : getDependencies() ) {
        if ( !d.isSatisfied( true, null ) ) {
          if ( !d.satisfy( true, null ) ) {
            satisfied = false;
          }
        }
      }
    }
    return satisfied;
  }

  public List< ConstraintExpression > getUnsatisfiedConstraints() {
    List< ConstraintExpression > list = new ArrayList< ConstraintExpression >();
    if ( constraintExpressions != null || constraintExpressions.size() == 0 ) {
      constraintExpressions = Utils.asList( getConstraints( true, null ),
                                            ConstraintExpression.class );
    }
    for ( ConstraintExpression c : constraintExpressions ) {
      if ( !c.isSatisfied( false, null ) ) {
        list.add( c );
      }
    }
    return list;
  }

  public Set< Parameter< ? > >
         getDependentParameters( boolean deep, Set< HasParameters > seen ) {
    Set< Parameter< ? > > set = new LinkedHashSet< Parameter< ? > >();

    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return set;
    seen = pair.second;

    // All parameters that have domains with single values are dependent.
    Set<Parameter<?>> params = getParameters(deep, seen);
    for ( Parameter<?> p : params ) {
      if ( p.getDomain() != null && p.getDomain().magnitude() == 1 ) {
        set.add( p );
      }
      // TODO -- check if domain is { null }
    }

    if ( false )
    for ( Constraint c : getConstraints(deep, null) ) {
      // Add the dependent variable in a dependency.
      if ( c instanceof Dependency ) {
        Dependency<?> d = (Dependency<?>) c;
        set.add(d.parameter);
      } else
      // A single parameter on one side of an equals constraint with no free
      // variables on the other side is dependent.
      if ( c instanceof ConstraintExpression ) {
        ConstraintExpression ce = (ConstraintExpression)c;
        if ( ce.expression instanceof Functions.EQ ) {
          Vector<Object> args = ((Functions.EQ) ce.expression).getArguments();
          if ( args != null && args.size() >= 2 ) {  // It should always be 2, but . . .
            for ( int i = 0; i < args.size(); ++i ) {
              try {
                Parameter<?> p = Expression.evaluate(args.get(i), Parameter.class, false);
                if ( p != null && !set.contains(p) ) {
                  for ( int j = 0; j < args.size(); ++j ) {
                    if ( j != i ) {
                      Object otherArg = args.get( j );
                      if ( otherArg instanceof HasParameters ) {
                        if ( !HasParameters.Helper.hasFreeParameter(otherArg, deep, seen) ) {
                          set.add( p );
                        }
                      } else {
                        set.add( p );
                      }
                    }
                  }
                }
              } catch (IllegalAccessException e) {
                e.printStackTrace();
              } catch (InvocationTargetException e) {
                e.printStackTrace();
              } catch (InstantiationException e) {
                e.printStackTrace();
              }
            }
          }
        }
//        if ( ((ConstraintExpression) c).form == Expression.Form.Function ) {
//        } else if (((ConstraintExpression) c).form == Expression.Form.Constructor ) {
//        }
      }
    }
    return set;
  }

  @Override
  public Parameter< ? > getParameter( String name ) {
    return HasParameters.Helper.getParameter( this, name );
  }


  // Gather any parameter instances contained by this event.
  /*
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.event.HasParameters#getParameters(boolean,
   * java.util.Set)
   */
  @Override
  public Set< Parameter< ? > > getParameters( boolean deep,
                                              Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    // if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
    Set< Parameter< ? > > set = new TreeSet< Parameter< ? > >();
    // set.addAll( getParameters() );
    // if ( deep ) {
    // for ( Parameter<?> p : getParameters() ) {
    // if ( p.getValueNoPropagate() != null &&
    // p.getValueNoPropagate() instanceof HasParameters ) {
    // set = Utils.addAll( set, ( (HasParameters)p.getValueNoPropagate()
    // ).getParameters( deep, seen ) );
    // }
    // }
    // }
    set = Utils.addAll( set,
                        HasParameters.Helper.getParameters( getParameters(),
                                                            deep, seen,
                                                            true ) );
    if ( deep ) {
      set = Utils.addAll( set,
                          HasParameters.Helper.getParameters( getDependencies(),
                                                              deep, seen,
                                                              true ) );
      set = Utils.addAll( set,
                          HasParameters.Helper.getParameters( getConstraintExpressions(),
                                                              deep, seen,
                                                              true ) );
    }
    return set;
  }

  // TODO -- define this in HasParameters
  /**
   * @param deep
   * @param seen
   * @return parameters that are Timepoints
   */
  public Set< Parameter< Long > > getTimepoints( boolean deep,
                                                 Set< HasParameters > seen ) {
    Set< Parameter< Long > > set = new LinkedHashSet< Parameter< Long > >();
    for ( Parameter< ? > p : getParameters( deep, seen ) ) {
      if ( p instanceof Timepoint || p.getValueNoPropagate() instanceof Long ) {
        set.add( (Parameter< Long >)p );
      }
    }
    return set;
  }

  // TODO -- This is not finished. Need to get deep dependents.
  @Override
  public Set< Parameter< ? > > getFreeParameters( boolean deep,
                                                  Set< HasParameters > seen ) {
    Assert.assertFalse( "This method does not yet support deep=true!", deep );
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    // if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
    Set< Parameter< ? > > set = getParameters( deep, seen );
    Set< Parameter< ? > > dependents = getDependentParameters( deep, seen );
    set.removeAll( dependents );
    return set;
  }

  @Override
  public void setFreeParameters( Set< Parameter< ? > > freeParams, boolean deep,
                                 Set< HasParameters > seen ) {
    Assert.assertTrue( "This method is not supported!", false );
  }

  @Override
  public boolean isSatisfied( boolean deep, Set< Satisfiable > seen ) {
    Collection<Constraint> constraints = getConstraints(true, null);
    for ( Constraint c : constraints ) { // REVIEW -- why is
                                                          // true passed in?
      if ( Debug.isOn() ) Debug.outln( "ParameterListenerImpl.isSatisfied(): checking "
                                       + c );
      if ( !c.isSatisfied( deep, seen ) ) {
        return false;
      }
    }
    return true;
  }

  /*
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.solver.Satisfiable#satisfy(boolean, java.util.Set)
   */
  @Override
  public boolean satisfy( boolean deep, Set< Satisfiable > seen ) {
//    if ( redirectStdOut ) {
//      ByteArrayOutputStream baosOut = new ByteArrayOutputStream();
//      ByteArrayOutputStream baosErr = new ByteArrayOutputStream();
//      System.setOut( new PrintStream( baosOut ) );
//      System.setErr( new PrintStream( baosErr ) );
//    }
    Pair< Boolean, Set< Satisfiable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;

    if ( isSatisfied( deep, null ) ) return true;
    if ( !amTopEventToSimulate ) return false;
    double clockStart = System.currentTimeMillis();
    long numLoops = 0;
    long mostConstraints = 0;
    long mostResolvedConstraints = 0;
    double highestFractionResolvedConstraint = 0;
    int numLoopsWithNoProgress = 0;
    long numberOfConstraints = getNumberOfConstraints( true, null );
    boolean satisfied = false;
    long millisPassed = (long)( System.currentTimeMillis() - clockStart );
    double curTimeLeft = ( timeoutSeconds * 1000.0 - ( millisPassed ) );

    while ( !satisfied && numLoopsWithNoProgress < maxLoopsWithNoProgress
            && ( !usingTimeLimit || curTimeLeft > 0.0 )
            && ( !usingLoopLimit || numLoops < maxPassesAtConstraints ) ) {
      if ( Debug.isOn() || this.amTopEventToSimulate ) {
        System.out.println();
        System.out.println();

        if ( usingTimeLimit ) {
          System.out.println( this.getClass().getName() + " satisfy loop with "
                              + Duration.toFormattedString( (long)curTimeLeft )
                              + " time left" );
        } else {
          System.out.println( this.getClass().getName() + " satisfy loop after "
                              + Duration.toShortFormattedStringForIdentifier( millisPassed ) );
        }
        if ( Debug.isOn() || this.amTopEventToSimulate ) {
          System.out.println( this.getClass().getName() + " satisfy loop round "
                              + ( numLoops + 1 ) + " out of "
                              + maxPassesAtConstraints );
         } else {
          System.out.println( this.getClass().getName() + " satisfy loop round "
                              + ( numLoops + 1 ) );
        }
        System.out.println( "" );
      }
      if ( amTopEventToSimulate ) {
        DurativeEvent.newMode = false; // numLoops % 2 == 0;
      }
      satisfied = tryToSatisfy( deep, null );

      // numberOfConstraints = this.getNumberOfConstraints( true, null );
      long numResolvedConstraints =
          this.getNumberOfResolvedConstraints( true, null );// solver.getNumberOfResolvedConstraints();
      double fractionResolved =
          numberOfConstraints == 0 ? 0 : ( (double)numResolvedConstraints )
                                         / numberOfConstraints;

      boolean improved = numResolvedConstraints > mostResolvedConstraints
                         || fractionResolved > highestFractionResolvedConstraint;

      // TODO -- Move call to doSnapshotSimulation() into tryToSatisfy() in
      // order to
      // move it out of this class and into DurativeEvent since Events simulate.
      if ( snapshotSimulationDuringSolve && this.amTopEventToSimulate
           && ( numLoops % loopsPerSnapshot == 0 ) ) {
        doSnapshotSimulation( improved );
      }

      if ( satisfied || improved || numberOfConstraints > mostConstraints ) {
        numLoopsWithNoProgress = 0;
        mostResolvedConstraints = 0;
        highestFractionResolvedConstraint = 0.0;
        // mostConstraints = 0;
      } else {
        ++numLoopsWithNoProgress;
        if ( numLoopsWithNoProgress >= maxLoopsWithNoProgress
             && ( Debug.isOn() || amTopEventToSimulate ) ) {
          System.out.println( "\nPlateaued at " + mostResolvedConstraints
                              + " constraints satisfied." );
          if ( Debug.isOn() ) {
            System.out.println( solver.getUnsatisfiedConstraints().size()
                                + " unresolved constraints = "
                                + solver.getUnsatisfiedConstraints() );
          } else {
            System.out.println( solver.getUnsatisfiedConstraints().size()
                                + " unresolved constraints." );
          }
        }
      }

      if ( numResolvedConstraints > mostResolvedConstraints ) {
        mostResolvedConstraints = numResolvedConstraints;
      }
      if ( fractionResolved > highestFractionResolvedConstraint ) {
        highestFractionResolvedConstraint = fractionResolved;
      }
      if ( numberOfConstraints > mostConstraints ) {
        mostConstraints = numberOfConstraints;
      }

      millisPassed = (long)( System.currentTimeMillis() - clockStart );
      curTimeLeft = ( timeoutSeconds * 1000.0 - millisPassed );
      ++numLoops;
    }
//    System.setErr( oldErr );
//    System.setOut( oldOut );
    return satisfied;
  }

  /*
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.solver.Satisfiable#satisfy(boolean, java.util.Set)
   */
  // @Override
  public boolean satisfy2( boolean deep, Set< Satisfiable > seen ) {
    Pair< Boolean, Set< Satisfiable > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return true;
    seen = pair.second;

    if ( isSatisfied( deep, null ) ) return true;
    double clockStart = System.currentTimeMillis();
    long numLoops = 0;
    long mostResolvedConstraints = 0;
    int numLoopsWithNoProgress = 0;

    boolean satisfied = false;
    long millisPassed = (long)( System.currentTimeMillis() - clockStart );
    double curTimeLeft = ( timeoutSeconds * 1000.0 - ( millisPassed ) );

    while ( !satisfied && numLoopsWithNoProgress < maxLoopsWithNoProgress
            && ( !usingTimeLimit || curTimeLeft > 0.0 )
            && ( !usingLoopLimit || numLoops < maxPassesAtConstraints ) ) {
      if ( Debug.isOn() || this.amTopEventToSimulate ) {
        if ( usingTimeLimit ) {
          System.out.println( this.getClass().getName() + " satisfy loop with "
                              + Duration.toFormattedString( (long)curTimeLeft )
                              + " time left" );
        } else {
          System.out.println( this.getClass().getName() + " satisfy loop after "
                              + Duration.toShortFormattedStringForIdentifier( millisPassed ) );
        }
        if ( Debug.isOn() || this.amTopEventToSimulate ) {
          System.out.println( this.getClass().getName() + " satisfy loop round "
                              + ( numLoops + 1 ) + " out of "
                              + maxPassesAtConstraints );
        } else {
          System.out.println( this.getClass().getName() + " satisfy loop round "
                              + ( numLoops + 1 ) );
        }
      }
      satisfied = tryToSatisfy( deep, seen );

      long numResolvedConstraints = getNumberOfResolvedConstraints();

      boolean improved = numResolvedConstraints > mostResolvedConstraints;
      // TODO -- Move call to doSnapshotSimulation() into tryToSatisfy() in
      // order to
      // move it out of this class and into DurativeEvent since Events simulate.
      if ( snapshotSimulationDuringSolve && this.amTopEventToSimulate
           && ( numLoops % loopsPerSnapshot == 0 ) ) {
        doSnapshotSimulation( improved );
      }

      if ( !satisfied && !improved ) {
        ++numLoopsWithNoProgress;
        if ( numLoopsWithNoProgress >= maxLoopsWithNoProgress
             && ( Debug.isOn() || amTopEventToSimulate ) ) {
          System.out.println( "\nPlateaued at " + mostResolvedConstraints
                              + " constraints satisfied." );
          System.out.println( solver.getUnsatisfiedConstraints().size()
                              + " unresolved constraints = "
                              + solver.getUnsatisfiedConstraints() );
        }
      } else {
        mostResolvedConstraints = numResolvedConstraints;
        numLoopsWithNoProgress = 0;
      }

      millisPassed = (long)( System.currentTimeMillis() - clockStart );
      curTimeLeft = ( timeoutSeconds * 1000.0 - millisPassed );
      ++numLoops;
    }
    return satisfied;
  }

  public long getNumberOfResolvedConstraints() {

    return 0;
  }

  // TODO -- Move call to doSnapshotSimulation() into tryToSatisfy() in order to
  // move it out of this class and into DurativeEvent.
  public void doSnapshotSimulation() {
    doSnapshotSimulation( false );
  }

  public void doSnapshotSimulation( boolean improved ) {
    // override!
  }

  protected boolean tryToSatisfy2( boolean deep, Set< Satisfiable > seen ) {
    ground( deep, null );
    if ( Debug.isOn() ) Debug.outln( this.getClass().getName()
                                     + " satisfy loop called ground() " );
    boolean satisfied = true;
    if ( !Satisfiable.Helper.satisfy( getParameters(), deep, seen ) ) {
      satisfied = false;
    }
    if ( !Satisfiable.Helper.satisfy( getConstraintExpressions(), false,
                                      seen ) ) {
      satisfied = false;
    }
    if ( !Satisfiable.Helper.satisfy( getDependencies(), false, seen ) ) {
      satisfied = false;
    }
    return satisfied;
  }

  protected boolean tryToSatisfy( boolean deep, Set< Satisfiable > seen ) {
    ground( deep, null );
    if ( Debug.isOn() ) Debug.outln( this.getClass().getName()
                                     + " satisfy loop called ground() " );

    Collection< Constraint > allConstraints = getConstraints( deep, null );
    if ( Debug.isOn() || amTopEventToSimulate ) {
      System.out.println( this.getClass().getName() + " - " + getName()
                          + ".tryToSatisfy() calling solve() with "
                          + allConstraints.size() + " constraints" );
    }
    // REVIEW -- why not call satisfy() here and solve elsewhere??

    // Restrict the domains of the variables using arc consistency on the
    // constraints.
    Consistency ac = null;
    if ( usingArcConsistency ) {
      try {
        ac = new Consistency();
        ac.constraints = allConstraints;
        ac.arcConsistency(arcConsistencyQuiet);
      } catch (Throwable t) {
        Debug.error(true, false, "Error! Arc consistency failed.");
        t.printStackTrace();
      }
    }
    // restore domains of things that are not simple variables
    if ( ac != null ) {
      for (Entry<Variable<?>, Domain<?>> e : ac.savedDomains.entrySet()) {
        if (Boolean.FALSE.equals(isSimpleVar(e.getKey()))) {
          e.getKey().setDomain((Domain) e.getValue());
        }
      }
    }

    // Now assign values to variables within their domains to satisfy
    // constraints.
    boolean satisfied = solver.solve( allConstraints );
    //System.out.println( MoreToString.Helper.toShortString( allConstraints ) );
    if ( usingArcConsistency ) {
      ac.restoreDomains();
    }
    if ( Debug.isOn() || amTopEventToSimulate ) {
      Timer t = new Timer();
      t.start();

      Collection< Constraint > unsat = solver.getUnsatisfiedConstraints();
      System.out.println( this.getClass().getName() + " - " + getName()
                          + ".tryToSatisfy() called solve(): satisfied "
                          + ( allConstraints.size() - unsat.size() )
                          + " constraints; failed to resolve " + unsat.size()
                          + " constraints" );
      if ( unsat.size() <= 5 ) {
        for ( Constraint c : unsat ) {
          System.out.println( "unsatisfied --> " + c );
        }
      }
      t.stop();
      if ( Debug.isOn() ) System.out.println( this.getClass().getName() + " - "
                                              + getName()
                                              + ".tryToSatisfy() time taken to get unsatisfied constraints:\n"
                                              + t );
    }

    Timer t = new Timer();
    t.start();
    satisfied = isSatisfied( deep, null );
    t.stop();
    if ( Debug.isOn() ) System.out.println( this.getClass().getName() + " - "
                                            + getName()
                                            + ".tryToSatisfy() time taken to check if satisfied:\n"
                                            + t );

    if ( Debug.isOn() || amTopEventToSimulate ) {
      System.out.println( this.getClass().getName() + " - " + getName()
                          + ".tryToSatisfy() called solve(): satisfied = "
                          + satisfied );
    }
    return satisfied;
  }

  protected Boolean isSimpleVar( Variable< ? > key ) {
    Object v = key.getValue( false );
    if ( v != null ) {
      if ( ClassUtils.isPrimitive( v ) ) {
        return Boolean.TRUE;
      }
    } else {
      v = key.getType();
      if ( v != null ) {
        if ( ClassUtils.isPrimitive( v ) ) {
          return Boolean.TRUE;
        }
      } else {
        return null;
      }

    }
    return Boolean.FALSE;
  }
  // @Override
  // public Collection< Constraint > getConstraints() {
  // return getConstraints( false, null );
  // }

  @Override
  public Collection< Constraint > getConstraints( boolean deep,
                                                  Set< HasConstraints > seen ) {
    boolean mayHaveBeenPropagating = Parameter.mayPropagate;
    Parameter.mayPropagate = false;
    boolean mayHaveBeenChanging = Parameter.mayChange;
    Parameter.mayChange = false;
    Pair< Boolean, Set< HasConstraints > > pair =
        Utils.seen( this, deep, seen );
    if ( pair.first ) {
      Parameter.mayPropagate = mayHaveBeenPropagating;
      Parameter.mayChange = mayHaveBeenChanging;
      return Utils.getEmptySet();
    }
    seen = pair.second;
    if ( usingCollectionTree ) {
      if ( seen != null ) seen.remove( this );
      return getConstraintCollection( deep, seen );
    }
    Set< Constraint > set = new LinkedHashSet< Constraint >();
    set = Utils.addAll( set,
                        HasConstraints.Helper.getConstraints( getParameters( false,
                                                                             null ),
                                                              deep, seen ) );
    set = Utils.addAll( set,
                        HasConstraints.Helper.getConstraints( constraintExpressions,
                                                              false, seen ) );
    set = Utils.addAll( set,
                        HasConstraints.Helper.getConstraints( dependencies,
                                                              false, seen ) );
    // for ( Parameter< ? > p : getParameters( false, null ) ) {
    // set = Utils.addAll( set, p.getConstraints( deep, seen ) );
    // }
    // set = Utils.addAll( set, constraintExpressions );
    // set = Utils.addAll( set, dependencies );
    Parameter.mayPropagate = mayHaveBeenPropagating;
    Parameter.mayChange = mayHaveBeenChanging;
    return set;
  }

  @Override
  public CollectionTree< Constraint >
         getConstraintCollection( boolean deep, Set< HasConstraints > seen ) {
    Pair< Boolean, Set< HasConstraints > > pair =
        Utils.seen( this, deep, seen );
    if ( pair.first ) {
      return null;
    }
    seen = pair.second;
    if ( constraintCollection == null ) {
      ArrayList< Object > constraintSources = new ArrayList< Object >();
      constraintSources.add( parameters );
      constraintSources.add( dependencies );
      constraintSources.add( constraintExpressions );
      constraintCollection =
          new CollectionTree< Constraint >( constraintSources );
      constraintCollection.getTypes().add( Constraint.class );
    }
    constraintCollection.setSeen( seen );

    return constraintCollection;
  }

  public Map< String, Object >
         getTimeVaryingObjectMap( boolean deep,
                                  Set< HasTimeVaryingObjects > seen ) {
    Set< TimeVarying< ?, ? > > tvs = getTimeVaryingObjects( deep, false, seen );
    Set< Parameter< ? > > params = getParameters( deep, null );
    Map< String, Object > paramsAndTvms = new LinkedHashMap< String, Object >();
    for ( Parameter< ? > p : params ) {
      Object o = p.getValueNoPropagate();
      if ( o instanceof TimeVarying ) {
        // System.out.println( p );
        // if ( p.getName() != null && p.getName().contains( "telecomPower" ) )
        // {
        // System.out.println("here1ß");
        // }
        String qName = p.getQualifiedName( null );
        if ( paramsAndTvms.containsKey( qName ) ) {
          Debug.error( false, false,
                       "Already have a parameter with name " + qName + "!" );
        } else {
          paramsAndTvms.put( qName, p );
          tvs.remove( o );
        }
      }
    }
    for ( TimeVarying< ?, ? > tv : tvs ) {
      if ( tv instanceof TimeVaryingMap ) {
        String name = ( (TimeVaryingMap< ? >)tv ).getQualifiedName( null );
        if ( !paramsAndTvms.containsKey( name ) ) {
          paramsAndTvms.put( name, tv );
        }
      }
    }
    return paramsAndTvms;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * gov.nasa.jpl.ae.event.HasTimeVaryingObjects#getTimeVaryingObjects(boolean)
   */
  @Override
  public Set< TimeVarying< ?, ? > >
         getTimeVaryingObjects( boolean deep,
                                Set< HasTimeVaryingObjects > seen ) {
    return getTimeVaryingObjects( deep, true, seen );
  }

  public Set< TimeVarying< ?, ? > >
         getTimeVaryingObjects( boolean deep, boolean includeDependencies,
                                Set< HasTimeVaryingObjects > seen ) {
    Pair< Boolean, Set< HasTimeVaryingObjects > > pair =
        Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    // if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
    Set< TimeVarying< ?, ? > > s = new LinkedHashSet< TimeVarying< ?, ? > >();
    s.addAll( timeVaryingObjects );
    s = Utils.addAll( s,
                      HasTimeVaryingObjects.Helper.getTimeVaryingObjects( getParameters( false,
                                                                                         null ),
                                                                          deep,
                                                                          seen ) );
    if ( deep ) {
      if ( includeDependencies ) {
        s = Utils.addAll( s,
                          HasTimeVaryingObjects.Helper.getTimeVaryingObjects( getDependencies(),
                                                                              false,
                                                                              seen ) );
        s = Utils.addAll( s,
                          HasTimeVaryingObjects.Helper.getTimeVaryingObjects( getConstraintExpressions(),
                                                                              false,
                                                                              seen ) );
      }
    }
    if ( settingTimeVaryingMapOwners ) {
      for ( TimeVarying< ?, ? > tv : s ) {
        if ( tv.getOwner() == null ) {
          tv.setOwner( this );
        }
      }
    }
    return s;
  }

  public static HashSet< ParameterListenerImpl >
         getNonEventObjects( Object o, boolean deep,
                             Set< HasParameters > seen ) {
    HashSet< ParameterListenerImpl > s = new LinkedHashSet< ParameterListenerImpl >();
    if ( o instanceof ParameterListenerImpl ) {
      ParameterListenerImpl pl = (ParameterListenerImpl)o;
      if ( !( o instanceof Event ) ) {
        s.add( pl );
      }
      if ( deep ) {
        s.addAll( pl.getNonEventObjects( deep, seen ) );
      }
    } else {
      for ( Parameter< ? > p : HasParameters.Helper.getParameters( o, false,
                                                                   null,
                                                                   true ) ) {
        if ( deep ) {
          s.addAll( getNonEventObjects( p.getValueNoPropagate(), deep, seen ) );
        } else if ( p.getValueNoPropagate() instanceof ParameterListenerImpl &&
                    !(p.getValueNoPropagate() instanceof Event) ) {
          s.add( (ParameterListenerImpl)p.getValueNoPropagate() );
        }
      }
    }
    return s;
  }

  /**
   * Get non-event ParameterListenerImpls, behavior classes.
   *
   * @param deep
   * @param seen
   * @return
   */
  public Collection< ParameterListenerImpl >
         getNonEventObjects( boolean deep, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;

    Set< ParameterListenerImpl > s = new LinkedHashSet< ParameterListenerImpl >();

    for ( Parameter< ? > p : getParameters( false, null ) ) {
      s.addAll( getNonEventObjects( p.getValueNoPropagate(), deep, seen ) );
    }
    for ( Dependency< ? > d : getDependencies() ) {
      s.addAll( getNonEventObjects( d, false, seen ) );
    }
    for ( ConstraintExpression c : getConstraintExpressions() ) {
      s.addAll( getNonEventObjects( c, false, seen ) );
    }
    return s;
  }

  public List< Parameter< ? > > getParameters() {
    return parameters;
  }

  public void setParameters( List< Parameter< ? > > parameters ) {
    this.parameters = parameters;
  }

  /**
   * @return the solver
   */
  public Solver getSolver() {
    return solver;
  }

  /**
   * @param solver
   *          the solver to set
   */
  public void setSolver( Solver solver ) {
    this.solver = solver;
  }

  public List< ConstraintExpression > getConstraintExpressions() {
    return constraintExpressions;
  }

  public void
         setConstraintExpressions( List< ConstraintExpression > constraints ) {
    this.constraintExpressions = constraints;
  }

  public Collection< Dependency< ? > > getDependencies() {
    return dependencies;
  }

  public void setDependencies( Collection< Dependency< ? > > dependencies ) {
    this.dependencies = new ArrayList< Dependency< ? > >( dependencies );
  }

  @Override
  public int compareTo( ParameterListenerImpl o ) {
    return compareTo( o, true );
  }

  public int compareTo( ParameterListenerImpl o, boolean checkId ) {
    return compareTo(o, checkId, false, false);
  }
  public int compareTo( ParameterListenerImpl o, boolean checkId, boolean onlyCheckId, boolean loosely ) {
    if ( this == o ) return 0;
    if ( o == null ) return -1;
    if ( checkId ) {
      int compare = CompareUtils.compare( getId(), o.getId() );
      if ( compare == 0 ) return compare;
      if ( onlyCheckId ) return compare;
    }
    int compare = getClass().getName().compareTo( o.getClass().getName() );
    if ( compare != 0 ) return compare;
    if ( !loosely ) {
      compare = getName().compareTo(o.getName());
      if (compare != 0) return compare;
    }
    Debug.errln( "ParameterListenerImpl.compareTo() potentially accessing value information" );
    compare = CompareUtils.compareCollections( parameters, o.getParameters(),
                                               checkId && onlyCheckId, checkId && onlyCheckId );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compareCollections( dependencies, o.dependencies,
            checkId, checkId && onlyCheckId );
    if ( compare != 0 ) return compare;
    compare = CompareUtils.compareCollections( constraintExpressions,
                                               o.constraintExpressions, checkId,
            checkId && onlyCheckId );
    if ( compare != 0 ) return compare;
    if ( !loosely ) {
      compare = CompareUtils.compare(this, o, false, false );
      if (compare != 0) return compare;
    }
    return compare;
  }

  // An event owns all of its parameters because it is required to contain any
  // dependency that sets one of its parameters, and has some connection through
  // its other members to any reference or constraint on it. Thus, an event must
  // know about the parent event from which it is elaborated if the parent
  // references the parameter.
  // handleValueChangeEvent( parameter, newValue ) updates dependencies,
  // effects, and elaborations for the changed parameter.
  @Override
  public void handleValueChangeEvent( Parameter< ? > parameter,
                                      Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if ( p.first ) return;
    seen = p.second;

    // REVIEW -- Should we be passing in a set of parameters? Find review/todo
    // note on staleness table.

    // Alert affected dependencies.
    List<Dependency<?>> deps = Utils.scramble(getDependencies());
    for ( Dependency< ? > d : deps ) {
      d.handleValueChangeEvent( parameter, seen );
    }
    // Alert affected timelines.
    List<TimeVarying<?, ?>> tvos =
            Utils.scramble(getTimeVaryingObjects(true, null));
    for ( TimeVarying< ?, ? > tv : tvos ) {
      if ( tv instanceof ParameterListener ) {
        ( (ParameterListener)tv ).handleValueChangeEvent( parameter, seen );
      }
    }
    List< ParameterListenerImpl > pls = Utils.scramble( getNonEventObjects( true, null ) );
    for ( ParameterListenerImpl pl : pls ) {
      pl.handleValueChangeEvent( parameter, seen );
    }
    List<ConstraintExpression> cstrs =
            Utils.scramble(getConstraintExpressions());
    for ( ConstraintExpression c : cstrs ) {
      if ( c == null ) continue;
      c.handleValueChangeEvent( parameter, seen );
    }

  }

  @Override
  public void handleDomainChangeEvent( Parameter< ? > parameter,
                                       Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if ( p.first ) return;
    seen = p.second;
    // TODO -- What are we supposed to do? call satisfy()??? Not caching
    // constraint violations as of 2012-08-03.
  }

  @Override
  public String getName() {
    if ( name != null ) return name;
    return getClass().getSimpleName();
  }

  @Override
  public String getQualifiedName( java.util.Set< Object > seen ) {
    String n = HasOwner.Helper.getQualifiedName( this, seen );
    return n;
  };

  @Override
  public String getQualifiedId( java.util.Set< Object > seen ) {
    String i = HasOwner.Helper.getQualifiedId( this, seen );
    return i;
  };

  public String getQualifiedName() {
    return getQualifiedName( null );
  }

  public String getQualifiedId() {
    return getQualifiedId( null );
  }

  public void setName( String newName ) {
    if ( newName == null || newName.isEmpty() ) {
      newName = getClass().getSimpleName()
                + Utils.numberWithLeadingZeroes( counter++, 6 );
    }
    this.name = newName;
  }

  protected boolean simpleDeconstruct = true;

  /**
   * Try to remove others' references to this, possibly because it is being
   * deleted.
   */
  @Override
  public void deconstruct() {
    if ( isDeconstructed() ) {
      if ( Debug.isOn() ) Debug.outln( "Attempted to deconstruct a deconstructed ParameterListener: "
                                       + this.toString( true, true, null ) );
      return;
    }
    if ( Debug.isOn() ) {
      Debug.outln( "Deconstructing ParameterListener: "
                   + this.toString( true, true, null ) );
    }
    if ( !simpleDeconstruct ) {
      for ( Dependency< ? > d : dependencies ) {
        d.deconstruct();
      }
      for ( Dependency< ? > d : externalDependencies ) {
        Parameter< ? > p = d.parameter;
        if ( p != null && p.getOwner() != null
             && p.getOwner() instanceof ParameterListenerImpl ) {
          ( (ParameterListenerImpl)p.getOwner() ).removeDependenciesForParameter( p );
          // TODO -- Should we add back default dependencies for startTime,
          // duration, & endTime in DurativeEvent?
        }
        d.deconstruct();
      }
      for ( ConstraintExpression ce : constraintExpressions ) {
        ce.deconstruct();
      }
      for ( TimeVarying< ?, ? > tv : timeVaryingObjects ) {
        if ( tv instanceof Deconstructable ) {
          ( (Deconstructable)tv ).deconstruct();
        }
      }
      for ( Parameter< ? > p : getParameters( false, null ) ) {
        if ( p.getOwner() != null ) {
          p.getOwner().detach( p ); // REVIEW -- this seems complicated!
        }
        if ( p.getOwner() == this || p.getOwner() == null ) {
          p.deconstruct();
        }
      }
    }
    name = "DECONSTRUCTED_" + name;

    dependencies.clear();
    externalDependencies.clear();
    constraintExpressions.clear();
    this.timeVaryingObjects.clear();
    parameters.clear();
    if ( Debug.isOn() ) {
      Debug.outln( "Done deconstructing ParameterListener: "
                   + this.toString( true, true, null ) );
    }

  }

  protected int refCount = 0;
  @Override public void addReference() {
    ++refCount;
  }

  @Override public void subtractReference() {
    --refCount;
    if ( refCount == 0 ) {
      deconstruct();
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.event.ParameterListener#detach(gov.nasa.jpl.ae.event.
   * Parameter)
   */
  @Override
  public void detach( Parameter< ? > parameter ) {
    // Remove local dependencies referencing the parameter.
    ArrayList< Dependency< ? > > dependenciesCopy =
        new ArrayList< Dependency< ? > >( getDependencies() );
    Collections.reverse( dependenciesCopy );
    int i = dependenciesCopy.size() - 1;
    for ( Dependency< ? > d : dependenciesCopy ) {
      boolean hasParam = d.hasParameter( parameter, false, null );
      if ( hasParam ) {
        d.detach( parameter );
        getDependencies().remove( i );
      }
      --i;
    }
    // Detach from constraints.
    ArrayList< ConstraintExpression > constraints =
        new ArrayList< ConstraintExpression >( getConstraintExpressions() );
    Collections.reverse( constraints );
    i = constraints.size() - 1;
    for ( ConstraintExpression c : constraints ) {
      if ( c.hasParameter( parameter, false, null ) ) {
        getConstraintExpressions().remove( i );
      }
      --i;
    }
    // Detach from timelines.
    for ( TimeVarying< ?, ? > tv : getTimeVaryingObjects( true, null ) ) {
      if ( tv instanceof ParameterListener ) {
        ( (ParameterListener)tv ).detach( parameter );
      }
    }
  }

  @Override
  public boolean isStale() {
    for ( Parameter< ? > p : getParameters() ) {
      if ( p == null ) continue;
      if ( p.isStale() ) return true;
    }
    return false;
  }

  @Override
  public void setStale( boolean staleness ) {
    Debug.error( true, false,
                 "BAD!!!!!!!!!!!!!!   THIS SHOULD NOT BE GETTING CALLED!  setStale("
                              + staleness + "): " + toShortString() );
    // TODO -- REVIEW -- Need anything here?
    assert false;
  }

  @Override
  public void setStale(boolean staleness, boolean deep, Set<LazyUpdate> seen) {
    setStale(staleness);
  }

  @Override
  public boolean refresh( Parameter< ? > parameter ) {
    boolean didRefresh = false;

    boolean triedRefreshing = false;
    // Alert affected dependencies.
    for ( Dependency< ? > d : getDependencies() ) {
      if ( d.parameter == parameter ) {
        triedRefreshing = true;
        if ( d.refresh( parameter ) ) didRefresh = true;
      }
    }

    // TODO -- Need to keep a collection of ParameterListeners (just as
    // DurativeEvent has getEvents())
    if ( triedRefreshing && didRefresh ) parameter.setStale( false );

    return didRefresh;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * gov.nasa.jpl.ae.event.ParameterListener#setStaleAnyReferencesTo(gov.nasa.
   * jpl.ae.event.Parameter)
   */
  @Override
  public void setStaleAnyReferencesTo( Parameter< ? > changedParameter,
                                       Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if ( p.first ) return;
    seen = p.second;

    // Alert affected dependencies.
    for ( Dependency< ? > d : getDependencies() ) {
      d.setStaleAnyReferencesTo( changedParameter, seen );
    }
    for ( TimeVarying< ?, ? > tv : getTimeVaryingObjects( false, null ) ) {
      if ( tv instanceof ParameterListener ) {
        ( (ParameterListener)tv ).setStaleAnyReferencesTo( changedParameter,
                                                           seen );
      }
    }
    seen.remove( this );
    Collection< ParameterListenerImpl > pls = getNonEventObjects( false, seen );
    for ( ParameterListenerImpl pl : pls ) {
      seen.remove( pl );
      pl.setStaleAnyReferencesTo( changedParameter, seen );
    }
    for ( ConstraintExpression c : getConstraintExpressions() ) {
      if ( c == null ) {
        continue;
      }
      c.setStaleAnyReferencesTo( changedParameter, seen );
    }

    // Pass message up the chain if necessary.
    Object o = getOwner();
    if ( o instanceof Parameter ) {
      o = ( (Parameter< ? >)o ).getOwner();
    }
    if ( o instanceof ParameterListener ) {
      ( (ParameterListener)o ).setStaleAnyReferencesTo( changedParameter,
                                                        seen );
    }

  }

  @Override
  public boolean hasParameter( Parameter< ? > parameter, boolean deep,
                               Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;
    // if ( Utils.seen( this, deep, seen ) ) return false;
    if ( seen != null ) seen.remove( this ); // because getParameters checks
                                             // seen set, too.

    boolean has =
        HasParameters.Helper.hasParameter( this, parameter, deep, seen );
    return has;
  }

  @Override
  public boolean isFreeParameter( Parameter< ? > p, boolean deep,
                                  Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;
    // if ( Utils.seen( this, deep, seen ) ) return false;

    return !getDependentParameters( deep, seen ).contains( p );
  }

  @Override
  public < T > boolean pickParameterValue( Variable< T > variable ) {
    for ( Dependency< ? > d : getDependencies() ) {
      if ( d.pickParameterValue( variable ) ) return true;
    }
    for ( ConstraintExpression c : getConstraintExpressions() ) {
      if ( c.pickParameterValue( variable ) ) return true;
    }
    if ( variable instanceof Parameter
         && ( (Parameter< ? >)variable ).getOwner() != this
         && Random.global.nextBoolean() ) {
      return ( (Parameter< ? >)variable ).ownerPickValue();
    }
    T value = null;
    try {
      value = (T)variable.pickRandomValue();
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    }
    if ( value != null ) {
      variable.setValue( value );
      return true;
    }
    return false;
  }

  // TODO -- add this to Deconstructable interface.
  //@Override
  public boolean isDeconstructed() {
    if ( name.startsWith( "DECONSTRUCTED_" )
         || ( Utils.isNullOrEmpty( parameters )
              && Utils.isNullOrEmpty( dependencies )
              && Utils.isNullOrEmpty( timeVaryingObjects )
              && Utils.isNullOrEmpty( constraintExpressions ) ) ) {
      return true;
    }
    return false;
  }

  /**
   * @return the timeoutSeconds
   */
  public double getTimeoutSeconds() {
    return timeoutSeconds;
  }

  /**
   * @param timeoutSeconds
   *          the timeoutSeconds to set
   */
  public void setTimeoutSeconds( double timeoutSeconds ) {
    this.timeoutSeconds = timeoutSeconds;
  }

  /**
   * @return the maxLoopsWithNoProgress
   */
  public int getMaxLoopsWithNoProgress() {
    return maxLoopsWithNoProgress;
  }

  /**
   * @param maxLoopsWithNoProgress
   *          the maxLoopsWithNoProgress to set
   */
  public void setMaxLoopsWithNoProgress( int maxLoopsWithNoProgress ) {
    this.maxLoopsWithNoProgress = maxLoopsWithNoProgress;
  }

  /**
   * @return the maxPassesAtConstraints
   */
  public long getMaxPassesAtConstraints() {
    return maxPassesAtConstraints;
  }

  /**
   * @param maxPassesAtConstraints
   *          the maxPassesAtConstraints to set
   */
  public void setMaxPassesAtConstraints( long maxPassesAtConstraints ) {
    this.maxPassesAtConstraints = maxPassesAtConstraints;
  }

  /**
   * @return the usingTimeLimit
   */
  public boolean isUsingTimeLimit() {
    return usingTimeLimit;
  }

  /**
   * @param usingTimeLimit
   *          the usingTimeLimit to set
   */
  public void setUsingTimeLimit( boolean usingTimeLimit ) {
    this.usingTimeLimit = usingTimeLimit;
  }

  /**
   * @return the usingLoopLimit
   */
  public boolean isUsingLoopLimit() {
    return usingLoopLimit;
  }

  /**
   * @param usingLoopLimit
   *          the usingLoopLimit to set
   */
  public void setUsingLoopLimit( boolean usingLoopLimit ) {
    this.usingLoopLimit = usingLoopLimit;
  }

  /**
   * @return the snapshotSimulationDuringSolve
   */
  public boolean isSnapshotSimulationDuringSolve() {
    return snapshotSimulationDuringSolve;
  }

  /**
   * @param snapshotSimulationDuringSolve
   *          the snapshotSimulationDuringSolve to set
   */
  public void
         setSnapshotSimulationDuringSolve( boolean snapshotSimulationDuringSolve ) {
    this.snapshotSimulationDuringSolve = snapshotSimulationDuringSolve;
  }

  /**
   * @return the snapshotToSameFile
   */
  public boolean isSnapshotToSameFile() {
    return snapshotToSameFile;
  }

  /**
   * @param snapshotToSameFile
   *          the snapshotToSameFile to set
   */
  public void setSnapshotToSameFile( boolean snapshotToSameFile ) {
    this.snapshotToSameFile = snapshotToSameFile;
  }

  /**
   * @return the loopsPerSnapshot
   */
  public int getLoopsPerSnapshot() {
    return loopsPerSnapshot;
  }

  /**
   * @param loopsPerSnapshot
   *          the loopsPerSnapshot to set
   */
  public void setLoopsPerSnapshot( int loopsPerSnapshot ) {
    this.loopsPerSnapshot = loopsPerSnapshot;
  }

  /**
   * @return the baseSnapshotFileName
   */
  public String getBaseSnapshotFileName() {
    return baseSnapshotFileName;
  }

  /**
   * @param baseSnapshotFileName
   *          the baseSnapshotFileName to set
   */
  public void setBaseSnapshotFileName( String baseSnapshotFileName ) {
    this.baseSnapshotFileName = baseSnapshotFileName;
  }

  /**
   * @return the amTopEventToSimulate
   */
  public boolean isAmTopEventToSimulate() {
    return amTopEventToSimulate;
  }

  /**
   * @param amTopEventToSimulate
   *          the amTopEventToSimulate to set
   */
  public void setAmTopEventToSimulate( boolean amTopEventToSimulate ) {
    this.amTopEventToSimulate = amTopEventToSimulate;
  }

  /**
   * @return the settingTimeVaryingMapOwners
   */
  public static boolean isSettingTimeVaryingMapOwners() {
    return settingTimeVaryingMapOwners;
  }

  /**
   * @param settingTimeVaryingMapOwners
   *          the settingTimeVaryingMapOwners to set
   */
  public static void
         setSettingTimeVaryingMapOwners( boolean settingTimeVaryingMapOwners ) {
    ParameterListenerImpl.settingTimeVaryingMapOwners =
        settingTimeVaryingMapOwners;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * gov.nasa.jpl.ae.solver.Satisfiable#getNumberOfResolvedConstraints(boolean,
   * java.util.Set)
   */
  @Override
  public long getNumberOfResolvedConstraints( boolean deep,
                                              Set< HasConstraints > seen ) {
    long num = 0;
    num +=
        HasConstraints.Helper.getNumberOfResolvedConstraints( getParameters(),
                                                              deep, seen );
    num +=
        HasConstraints.Helper.getNumberOfResolvedConstraints( getConstraintExpressions(),
                                                              false, seen );
    num +=
        HasConstraints.Helper.getNumberOfResolvedConstraints( getDependencies(),
                                                              false, seen );
    return num;
  }

  /*
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.solver.Satisfiable#getNumberOfUnresolvedConstraints(
   * boolean, java.util.Set)
   */
  @Override
  public long getNumberOfUnresolvedConstraints( boolean deep,
                                                Set< HasConstraints > seen ) {
    long num = 0;
    num +=
        HasConstraints.Helper.getNumberOfUnresolvedConstraints( getParameters(),
                                                                deep, seen );
    num +=
        HasConstraints.Helper.getNumberOfUnresolvedConstraints( getConstraintExpressions(),
                                                                false, seen );
    num +=
        HasConstraints.Helper.getNumberOfUnresolvedConstraints( getDependencies(),
                                                                false, seen );
    return num;
  }

  /*
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.solver.Satisfiable#getNumberOfConstraints(boolean,
   * java.util.Set)
   */
  @Override
  public long getNumberOfConstraints( boolean deep,
                                      Set< HasConstraints > seen ) {
    long num = 0;
    num += HasConstraints.Helper.getNumberOfConstraints( getParameters(), deep,
                                                         seen );
    num +=
        HasConstraints.Helper.getNumberOfConstraints( getConstraintExpressions(),
                                                      false, seen );
    num += HasConstraints.Helper.getNumberOfConstraints( getDependencies(),
                                                         false, seen );
    return num;
  }

  /*
   * (non-Javadoc)
   *
   * @see
   * gov.nasa.jpl.ae.event.ParameterListener#translate(gov.nasa.jpl.ae.solver.
   * Variable, java.lang.Object, java.lang.Class)
   */
  @Override
  public < T > T translate( Variable< T > p, Object o, Class< ? > type ) {
    return null;
  }

  @Override
  public Object getOwner() {
    return owner;
  }

  public ParameterListenerImpl getOwningObject() {
    Object o = getOwner();
    if ( o instanceof ParameterListenerImpl ) {
      return (ParameterListenerImpl)o;
    }
    if ( o instanceof Parameter && ((Parameter)o).getValueNoPropagate() == this ) {
      Object oo = ((Parameter)o).getOwner();
      if ( oo instanceof  ParameterListenerImpl ) {
        return (ParameterListenerImpl)oo;
      }
    }
    return null;
  }

  @Override
  public void setOwner( Object owner ) {
    this.owner = owner;
  }

  @Override
  public List< Variable< ? > >
         getVariablesOnWhichDepends( Variable< ? > variable ) {
    ArrayList< Variable< ? > > varList = new ArrayList< Variable< ? > >();
    Set< Variable< ? > > varSet = new LinkedHashSet< Variable< ? > >();
    for ( ConstraintExpression c : getConstraintExpressions() ) {
      if ( c == null ) continue;
      List< Variable< ? > > vars = c.getVariablesOnWhichDepends( variable );
      if ( !Utils.isNullOrEmpty( vars ) ) {
        varSet.addAll( vars );
      }
    }
    for ( Dependency d : getDependencies() ) {
      List< Variable< ? > > vars = d.getVariablesOnWhichDepends( variable );
      if ( !Utils.isNullOrEmpty( vars ) ) {
        varSet.addAll( vars );
      }
    }
    // TODO -- need to recurse into other objects
    varList.addAll( varSet );
    return varList;
  }

  @Override
  public Set<Event> getEvents(boolean deep, Set<HasEvents> seen) {
    Set<ParameterListenerImpl> objects = getObjects(deep, seen);
    Set< Event > set = Utils.asSet( objects, Event.class);

    return set;
//
//    Pair< Boolean, Set< HasEvents > > pair = Utils.seen( this, deep, seen );
//    if ( pair.first ) return Utils.getEmptySet();
//    seen = pair.second;
//    Set< Event > set = new LinkedHashSet< Event >();
//
//    // Get Events in parameters, too.
//    for ( Parameter p: getParameters() ) {
//      if ( p == null ) continue;
//      Object o = p.getValueNoPropagate();
//      Object deo = null;
//      try {
//        deo = Expression.evaluate(o, Event.class, false);
//      } catch (IllegalAccessException e) {
//      } catch (InvocationTargetException e) {
//      } catch (InstantiationException e) {
//      }
//      if ( deo instanceof Event ) {
//        set.add((Event) deo );
//      }
//      if ( deep ) {
//        deo = null;
//        try {
//          deo = Expression.evaluate(o, HasEvents.class, false);
//        } catch (IllegalAccessException e) {
//        } catch (InvocationTargetException e) {
//        } catch (InstantiationException e) {
//        }
//        if ( deo instanceof HasEvents ) {
//          set = Utils.addAll( set, ( (HasEvents)deo ).getEvents( deep, seen ) );
//        }
//      }
//    }
//    return set;
  }

  public Set<ParameterListenerImpl> getObjects(boolean deep, Set<HasEvents> seen) {
    Pair< Boolean, Set< HasEvents > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    Set< ParameterListenerImpl > set = new LinkedHashSet< ParameterListenerImpl >();

    // Get Events in parameters, too.
    for ( Parameter p: getParameters() ) {
      if ( p == null ) continue;
      Object o = p.getValueNoPropagate();
      Object deo = null;
      try {
        deo = Expression.evaluate(o, ParameterListenerImpl.class, false);
      } catch (IllegalAccessException e) {
      } catch (InvocationTargetException e) {
      } catch (InstantiationException e) {
      }
      if ( deo instanceof ParameterListenerImpl ) {
        set.add((ParameterListenerImpl) deo );
      }
      if ( deep ) {
        // FIXME -- TODO -- Is this not the same as starting from line 2031.
        deo = null;
        try {
          deo = Expression.evaluate(o, ParameterListenerImpl.class, false);
        } catch (IllegalAccessException e) {
        } catch (InvocationTargetException e) {
        } catch (InstantiationException e) {
        }
        if ( deo instanceof ParameterListenerImpl ) {
          set = Utils.addAll( set, ( (ParameterListenerImpl)deo ).getObjects( deep, seen ) );
        }
      }
    }
    return set;
  }

}
