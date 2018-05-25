/**
 *
 */
package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.solver.AbstractRangeDomain;
import gov.nasa.jpl.ae.solver.ComparableDomain;
import gov.nasa.jpl.ae.solver.Domain;
import gov.nasa.jpl.ae.solver.HasDomain;
import gov.nasa.jpl.ae.solver.HasIdImpl;
import gov.nasa.jpl.ae.solver.IntegerDomain;
import gov.nasa.jpl.ae.solver.LongDomain;
import gov.nasa.jpl.ae.solver.MultiDomain;
import gov.nasa.jpl.ae.solver.RangeDomain;
import gov.nasa.jpl.ae.solver.SingleValueDomain;
import gov.nasa.jpl.ae.solver.StringDomain;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.ae.util.DomainHelper;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.FileUtils;
import gov.nasa.jpl.mbee.util.MoreToString;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Random;
import gov.nasa.jpl.mbee.util.TimeUtils;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.Wraps;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//import com.panayotis.gnuplot.JavaPlot;

/**
 *
 * TimeVaryingMap is a {@link TreeMap} for implementing {@link TimeVarying},
 * mapping a {@link Parameter< Long>} to a generic class, V. It is also
 * implements {@link ParameterListener} in order to maintain {@link TreeMap}
 * consistency when the key changes. It "floats" entries before its
 * {@link Parameter< Long>} changes to protect the data structure and
 * reinserts the entry after the {@link Parameter< Long>} has changed.
 *
 */
public class TimeVaryingMap< V > extends TreeMap< Parameter< Long >, V >
                                 implements Cloneable,
                                            TimeVarying< Long, V >,
                                            Affectable,
                                            ParameterListener,
                                            HasOwner,
                                            AspenTimelineWritable,
                                            HasDomain {

  protected static boolean checkConsistency = false;

  private static final long serialVersionUID = -2428504938515591538L;

  public static Set<String> resourcePaths = Collections.synchronizedSet( new LinkedHashSet<String>());
  
  public static Interpolation STEP = new TimeVaryingMap.Interpolation(TimeVaryingMap.Interpolation.STEP);
  public static Interpolation LINEAR = new TimeVaryingMap.Interpolation(TimeVaryingMap.Interpolation.LINEAR);
  public static Interpolation RAMP = new TimeVaryingMap.Interpolation(TimeVaryingMap.Interpolation.RAMP);
  public static Interpolation NONE = new TimeVaryingMap.Interpolation(TimeVaryingMap.Interpolation.NONE);
  
  public static final TimeVaryingMap<Double> zero = new TimeVaryingMap< Double >( "zero", 0.0, Double.class );
  public static final TimeVaryingMap<Double> one = new TimeVaryingMap< Double >( "one", null, 1.0, Double.class ) {
    private static final long serialVersionUID = 1L;
    {
      // adding endpoint so that integrate() will work on it
      setValue( Timepoint.getHorizonTimepoint(), 1.0 );
    }
  };
  public static final TimeVaryingMap<Long> longOne = new TimeVaryingMap< Long >( "one", null, 1L, Long.class ) {
    private static final long serialVersionUID = 1L;
    {
      // adding endpoint so that integrate() will work on it
      setValue( Timepoint.getHorizonTimepoint(), 1L );
    }
  };
  public static final TimeVaryingMap<Double> time = one.integrate();
  public static final TimeVaryingMap<Long> longTime = longOne.integrate();
  
  public static class Interpolation  {
    public static final byte STEP = 0; // value for key = get(floorKey( key ))
    public static final byte LINEAR = 1; // floorVal+(ceilVal-floorVal)*(key-floorKey)/(ceilKey-floorKey)
    public static final byte RAMP = 2; // linear
    public static final byte NONE = Byte.MAX_VALUE; // value for key = get(key)
    public byte type = STEP;
    public Interpolation() {}
    public Interpolation( byte type ) {
      this.type = type;
    }
    @Override
    public String toString() {
      switch ( type ) {
        case NONE:
          return "NONE";
        case STEP:
          return "STEP";
        case LINEAR:
          return "LINEAR";
        case RAMP:
          return "RAMP";
        default:
          return null;
      }
    }
    public void fromString( String s ) {
      try{
        type = Byte.parseByte( s );
      } catch ( NumberFormatException e ) {
        // ignore
      }
      if ( s.toLowerCase().equals( "none" ) ) {
        type = NONE;
      } else if ( s.toLowerCase().equals( "step" ) ) {
        type = STEP;
      } else if ( s.toLowerCase().equals( "linear" ) ) {
        type = LINEAR;
      } else if ( s.toLowerCase().equals( "ramp" ) ) {
        type = RAMP;
      } else {
        Debug.error(true, "Can't parse interpolation string! " + s );
      }
    }
    
    public boolean isNone() {
      return type == NONE;
    }
    public boolean isStep() {
      return type == STEP;
    }
    public boolean isLinear() {
      return type == LINEAR || type == RAMP;
    }
    public boolean isRamp() {
      return isLinear();
    }
  }

  public Interpolation interpolation = new Interpolation();

  protected final int id = HasIdImpl.getNext();

  protected Object owner = null;
 
  protected Domain<V> domain = null;
  

  /**
   * For the convenience of referring to the effect method.
   */
  protected static Method setValueMethod1 = getSetValueMethod();
  //protected static Method setValueMethod2 = getSetValueMethod2();
  protected static Method addNumberMethod = null;
  protected static Method addNumberAtTimeMethod = null;
  protected static Method addNumberForTimeRangeMethod = null;
  protected static Method addMapMethod = null;
  protected static Method subtractNumberMethod = null;
  protected static Method subtractNumberAtTimeMethod = null;
  protected static Method subtractNumberForTimeRangeMethod = null;
  protected static Method subtractMapMethod = null;
  protected static Method multiplyNumberMethod = null;
  protected static Method multiplyNumberAtTimeMethod = null;
  protected static Method multiplyNumberForTimeRangeMethod = null;
  protected static Method multiplyMapMethod = null;

  protected static Method divideNumberMethod = null;
  protected static Method divideNumberAtTimeMethod = null;
  protected static Method divideNumberForTimeRangeMethod = null;
  protected static Method divideMapMethod = null;

  /**
   * Floating effects are those whose time or duration is changing. They must be
   * removed from TimeVaryingMap's map before they change; else, they will
   * corrupt the map. Before changing, they are placed in this floatingEffects
   * list, and after changing they are removed from this list and added back to
   * the map.
   */
  protected List< TimeValue > floatingEffects = new ArrayList< TimeValue >();

  protected String name;

  protected Class<V> type = null;
  protected boolean sureAboutType = false;

  protected Set<Effect> appliedSet = new LinkedHashSet<Effect>();

  protected static Map< Method, Integer > effectMethods = initEffectMethods();

  protected static Map< Method, Method > inverseMethods = null;

  protected static Map< Method, MathOperation > operationForMethod = null;

  protected static Comparator< Method > methodComparator;

  protected static Collection< Method > arithmeticMethods;



  public class TimeValue extends Pair< Parameter< Long>, V >
                               implements HasParameters {

    protected final int id = HasIdImpl.getNext();

    public TimeValue( Parameter< Long> t, V v ) {
      super( t, v );
    }

    @Override
    public void deconstruct() {
      maybeDeconstructParameter( TimeVaryingMap.this, first );
      maybeDeconstructParameter( TimeVaryingMap.this, second );
    }

    @Override
    public boolean isStale() {
      return HasParameters.Helper.isStale( this, false, null, false );
    }

    @Override
    public void setStale( boolean staleness ) {
      Debug.error( true, false, "BAD!!!!!!!!!!!!!!   THIS SHOULD NOT BE GETTING CALLED!  setStale(" + staleness + "): "
                     + toShortString() );
      Debug.error( "This method is not supported!");
    }

    @Override
    public Set< Parameter< ? > > getParameters( boolean deep,
                                                Set< HasParameters > seen ) {
      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return Utils.getEmptySet();
      seen = pair.second;
      //if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
      return HasParameters.Helper.getParameters( this, deep, null, false );
    }

    @Override
    public Set< Parameter< ? > > getFreeParameters( boolean deep,
                                                    Set< HasParameters > seen ) {
      Debug.error( "This method is not supported!");
      return null;
    }

    @Override
    public void setFreeParameters( Set< Parameter< ? > > freeParams,
                                   boolean deep,
                                   Set< HasParameters > seen ) {
      Debug.error( "This method is not supported!");
    }

    @Override
    public boolean isFreeParameter( Parameter< ? > parameter, boolean deep,
                                    Set< HasParameters > seen ) {
      // TODO -- REVIEW -- not sure about this
      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return false;
      seen = pair.second;
      //if ( Utils.seen( this, deep, seen ) ) return false;
      return HasParameters.Helper.isFreeParameter( this, parameter, deep, seen, false );
    }

    @Override
    public boolean hasParameter( Parameter< ? > parameter, boolean deep,
                                 Set< HasParameters > seen ) {
      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return false;
      seen = pair.second;
      //if ( Utils.seen( this, deep, seen ) ) return false;
      return HasParameters.Helper.hasParameter( this, parameter, deep, seen, false );
    }

    @Override
    public Parameter<?> getParameter(String name) {
      return HasParameters.Helper.getParameter(this, name);
    }

    @Override
    public boolean substitute( Parameter< ? > p1, Parameter< ? > p2,
                               boolean deep,
                               Set< HasParameters > seen ) {
      return substitute( p1, (Object)p2, deep, seen );
    }
    @Override
    public boolean substitute( Parameter< ? > p1, Object p2,
                               boolean deep,
                               Set< HasParameters > seen ) {
      breakpoint();
      Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
      if ( pair.first ) return false;
      seen = pair.second;
      //if ( Utils.seen( this, deep, seen ) ) return false;
      return HasParameters.Helper.substitute( this, p1, p2, deep, seen, false );
    }

    @Override
    public Integer getId() {
      return id;
    }
    @Override
    public int hashCode() {
      return id;
    }

    @Override
    public String toShortString() {
      return MoreToString.Helper.toShortString( this, null, true );
    }

    @Override
    public String toString( boolean withHash, boolean deep, Set< Object > seen ) {
      return toString( withHash, deep, seen, null );
    }

    @Override
    public String toString( boolean withHash, boolean deep, Set< Object > seen,
                            Map< String, Object > otherOptions ) {
      return MoreToString.Helper.toString(this, withHash, deep, seen,
                                          otherOptions, false );
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

  }

  public static boolean before( Parameter< Long > o1, Parameter< Long > o2 ) {
    int c = compareTo( o1, o2 );
    return c < 0;
  }
  public static boolean after( Parameter< Long > o1, Parameter< Long > o2 ) {
    int c = compareTo( o1, o2 );
    return c > 0;
  }
  public static int compareTo( Parameter< Long > o1, Parameter< Long > o2 ) {
    return TimeComparator.instance.compare( o1, o2 );
  }
  
  public static class TimeComparator implements Comparator< Parameter< Long > > {

    public boolean propagate = false;
    public boolean checkId = true;
    public static TimeComparator instance = new TimeComparator();

    @Override
    public int compare( Parameter< Long > o1, Parameter< Long > o2 ) {
      if ( o1 == o2 ) return 0;
      if ( o1 == null ) return -1;
      if ( o2 == null ) return 1;
      Long v1 = null;
      Long v2 = null;
      try {
        v1 = o1.getValueNoPropagate();
        v2 = o2.getValueNoPropagate();
        if ( v1 != v2 ) {//return 0;
          if ( v1 == null ) return -1;
          if ( v2 == null ) return 1;
          if ( v1 < v2 ) return -1;
          if ( v2 < v1 ) return 1;
        }
      } catch (ClassCastException e) {
          Debug.error("Unexpected type; should be Parameter<Long>; o1=" + o1 + ", o2=" + o2);
      }
      return o1.compareTo( o2, checkId );
    }
  }

  public TimeVaryingMap() {
    super(TimeComparator.instance);
  }

  /**
   *
   */
  public TimeVaryingMap( String name ) {
    this();
    this.name = name;

  }
  
  public TimeVaryingMap( String name, String fileName ) {
    this(name);
    fromCsvFile( fileName, type );
    if ( domain == null && type != null ) {
      domain = DomainHelper.getDomainForClass( type );
    }
  }


  public TimeVaryingMap( String name, Class<V> type ) {
    this(name);
    this.type = type;
    if ( type != null && !Object.class.equals(type) ) this.sureAboutType = true;
    if ( domain == null && type != null ) {
      domain = DomainHelper.getDomainForClass( type );
    }
  }
  protected TimeVaryingMap( String name, String fileName, Class<V> type ) {
    this(name, type);
    fromCsvFile( fileName, type );
    if ( domain == null && type != null ) {
      domain = DomainHelper.getDomainForClass( type );
    }
  }
  protected TimeVaryingMap( String name, String fileName, Date offset, TimeUtils.Units units, Class<V> type ) {
    this(name, type);
    fromCsvFile( fileName, offset, units, type );
    if ( domain == null && type != null ) {
      domain = DomainHelper.getDomainForClass( type );
    }
  }


  protected TimeVaryingMap( String name, V defaultValue, Class<V> type ) {
    this(name,type);
    // REVIEW -- consider forcing all constructors to provide non-null type
    V valueToInsert = null;
    if ( this.type == null ) {
      if ( defaultValue != null ) {
        setType( defaultValue.getClass() );
      }
      valueToInsert = defaultValue;
    } else {
      try {
        valueToInsert = Expression.evaluate( defaultValue, this.type, false, true );
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
    }
    Parameter< Long> t = new Parameter< Long>(null, null, 0L, this);
    Debug.errorOnNull(true,"this should neeeever be null", t.getValue(false) );
    if ( valueToInsert != null ) {
      put(t, valueToInsert);
    }
    if ( domain == null && type != null ) {
      domain = DomainHelper.getDomainForClass( type );
    }
    if ( Debug.isOn() || checkConsistency ) isConsistent();
  }

  public TimeVaryingMap( String name, String fileName, String backupFileName,
                         V defaultValue, Class<V> type ) {
    this( name, defaultValue, type );
    fromCsvFile( fileName, backupFileName, type );
    if ( domain == null && type != null ) {
      domain = DomainHelper.getDomainForClass( type );
    }
  }
  public TimeVaryingMap( String name, String fileName,
                         V defaultValue, Class<V> type ) {
    this( name, defaultValue, type );
    fromCsvFile( fileName, type );
    if ( domain == null && type != null ) {
      domain = DomainHelper.getDomainForClass( type );
    }
  }

//  public TimeVaryingMap( String name, V defaultValue ) {
//    this(name, defaultValue, null);
//  }
//
  public TimeVaryingMap( String name, String fileName, V defaultValue ) {
    this(name, defaultValue, null);
    fromCsvFile( fileName, type );
    if ( domain == null && type != null ) {
      domain = DomainHelper.getDomainForClass( type );
    }
  }

  /**
   * @param obj object to cast from
   * @param cls Class to which to cast o
   * @return obj cast to T or {@code null} if the cast fails
   */
  public static < T > T tryCast( Object obj, Class<T> cls ) {
    if ( cls != null && obj != null ) {
      try {
        if ( Number.class.isAssignableFrom( cls ) && ClassUtils.isNumber( obj.getClass() ) ) {
          T x = ClassUtils.castNumber( (Number)obj, cls );
          if ( x != null ) return x;
        }
        return cls.cast( obj );
      } catch ( ClassCastException e ) {
        try {
          return (T)obj; // may work for primitive cls where cast() does not.
        } catch( ClassCastException e1 ) {
          // ignore
        }
      }
    }
    return null;
  }

  /**
   * @param obj object to cast from
   * @return obj cast to V or {@code null} if the cast fails
   */
  @SuppressWarnings( "unchecked" )
  public V tryCastValue( Object obj ) {
    if ( getType() != null ) {
      return tryCast( obj, getType() );
    }
    try {
      return (V)obj;
    } catch ( ClassCastException e ) {
      // ignore
    }
    return null;
  }

  /**
   * @param obj object to cast from
   * @return obj cast to V or {@code null} if the cast fails
   */
  @SuppressWarnings( "unchecked" )
  public static Parameter< Long> tryCastTimepoint( Object obj ) {
    if ( obj instanceof Parameter ) {
      Parameter<?> p = (Parameter< ? >)obj;
      Object val = p.getValueNoPropagate();
      if ( val instanceof Long ) {
        return (Parameter< Long >)obj;
      } else if (val instanceof Integer) {
        try {
          Long l = Expression.evaluate( val, Long.class, false );
          return new SimpleTimepoint( l );
        } catch ( ClassCastException e ) {
        } catch ( IllegalAccessException e ) {

        } catch ( InvocationTargetException e ) {

        } catch ( InstantiationException e ) {

        }
      }
    }
    return null;
  }


  /**
   * @param obj object to cast from
   * @param propagate whether to propagate dependencies as part of the evaluation
   * @return obj cast to V or {@code null} if the cast fails
   */
  public V tryEvaluateValue( Object obj, boolean propagate ) {
    try {
      return Expression.evaluate( obj, getType(), propagate, true );
    } catch ( Throwable e ) {
      // ignore
    }
    return null;
  }

  /**
   * @param obj
   *          object to cast or convert to a timepoint
   * @param propagate
   *          whether to propagate dependencies as part of the evaluation
   * @return obj evaluated as Timepoint or {@code null} if the conversion or
   *         cast fail
   */
  public Parameter< Long> tryEvaluateTimepoint( Object obj, boolean propagate ) {
    return tryEvaluateTimepoint( obj, propagate, true );
  }

  private static Parameter< Long> param = new Parameter< Long >( "", null, 0L, null );
  private static Class< ? extends Parameter > pcls = param.getClass();
      //(Class< ? extends Parameter< Long >>)param.getClass();

  //(Class< ? extends Parameter< Long >>)( isEmpty() ? Parameter.class : firstKey().getClass() );

  /**
   * @param obj
   *          object to cast or convert to a timepoint
   * @param propagate
   *          whether to propagate dependencies as part of the evaluation
   * @param okToWrap
   *          whether a Parameter may be created to wrap an Long value into a
   *          timepoint
   * @return obj evaluated as Timepoint or {@code null} if the conversion or
   *         cast fail
   */
  @SuppressWarnings( "unchecked" )
  public static Parameter< Long> tryEvaluateTimepoint( Object obj, boolean propagate, boolean okToWrap ) {
    try {
      return Expression.evaluate( obj, (Class<Parameter< Long>>)pcls, propagate, okToWrap );
    } catch ( Throwable e ) {
      // ignore
    }
    return null;
  }

  public TimeVaryingMap( String name, Method initialValueFunction,
                         Object o, long samplePeriod, long horizonDuration ) {
    super(TimeComparator.instance);
    this.name = name;
    samplePeriod = correctSamplePeriod( samplePeriod, horizonDuration );
      for ( long t = 0; t < horizonDuration; t += samplePeriod ) {
        // WARNING: creating Parameter< Long> with no owner in order to avoid
        // unnecessary overhead with constraint processing. If modified while in
        // the map, it can corrupt the map.
        Pair< Boolean, Object > p = ClassUtils.runMethod( false, o, initialValueFunction, t );
        if ( p.first ) {
          Parameter< Long > tp = makeTempTimepoint( t, false );
          if ( tp == null || tp.getValue( false ) == null ) {
            System.err.println( "Error - inserting null timepoint!" );
          } else {
            put( tp,// new Parameter< Long>( "", t, this ),
                 tryCastValue( p.second ) );
          }
        }
      }
    if ( Debug.isOn() || checkConsistency ) isConsistent();
  }

  
  /**
   * Create a {@link TimeVaryingMap} and initialize it with the given {@link Method} and
   * arguments, assuming that one or more of the arguments and the object from
   * which the method is called are {@link TimeVaryingMap}s whose values should be used
   * in the invocation instead of the map itself.
   * <p>
   * For example, if {@code s1} and {@code s2} are
   * {@link TimeVaryingMap}&lt;String&gt;s, then for
   * {@code b = s1.contains(s2)}, {@code b} would be a
   * {@link TimeVaryingMap}&lt;Boolean&gt; where
   * {@code b.getValue(t) == s1.getValue(t).contains(s2.getValue(t))}.  This map
   * would be created with the following code invoking this constructor:<p>
   * {@code Method method = ClassUtils.getMethodForArgTypes(String.class, "contains", new Class[]{ CharacterSequence.class });}<br>
   * {@link TimeVaryingMap}&lt;Boolean&gt;("myMap", Boolean.class, s1, method, new Object[]{ s2 }, Interpolation.STEP)} .
   * 
   * @param name
   *          the name of the map to create
   * @param valueType
   *          the value type of the map
   * @param obj
   *          the instance from which the method is called
   * @param method
   *          the function to be called
   * @param args
   *          the arguments to the function
   * @param interpolation
   *          how to interpolate between map entries in the created map
   */
  public TimeVaryingMap( String name, Class<V> valueType, Object obj, Method method, Object[] args, Interpolation interpolation ) {
    //this(name, (String)null, (V)null, valueType, interpolation);
    this( name, valueType, new FunctionCall( obj, method, args, (Call)null, valueType ), interpolation );

    // TODO -- unless the above isn't good, delete the code below.
    if ( "a".contains( "b" ) ) { // This is always false.  Ha ha, stupid compiler! 

    // gather timepoints and interpret which objects/arguments will be treated as TVMs
    TreeSet< Parameter< Long > > timePoints =
        new TreeSet< Parameter< Long > >( TimeComparator.instance );
    // Process the object of the method.
    TimeVaryingMap<?> objectMap = null;
    if ( obj != null && !ClassUtils.isStatic( method ) && obj instanceof TimeVaryingMap ) {
      Class<?> methodClass = method.getDeclaringClass();
      objectMap = (TimeVaryingMap<?>)obj;
      // If the TVM values are compatible with the method's class or, in the case that the TVM value type is not known, and the TVM itself is not compatible with the method's class, then assume that the TVM values are meant to be the instance of the call.
      if ( ( objectMap.getType() != null && methodClass.isAssignableFrom( objectMap.getType() ) ) ||
          (objectMap.getType() == null && !methodClass.isInstance( obj ) ) ) {
        timePoints.addAll( objectMap.keySet() );
      } else {
        // Hide the fact that the object is a TVM since we're not using it's values.  Interpret a non-null objectMap as an indication that it's values should be used.
        objectMap = null; 
      }
    }
    // Process the arguments.
    ArrayList< TimeVaryingMap< ? > > argMaps =
        new ArrayList< TimeVaryingMap< ? > >();
    Class< ? >[] paramTypes = method.getParameterTypes();
    int i = 0;
    Class<?> pType = null;
    for ( Object arg : args ) {
      if ( arg instanceof TimeVaryingMap ) {
        TimeVaryingMap<?> tv = (TimeVaryingMap<?>)arg;
        if ( i < paramTypes.length ) { // helps with variable arguments
          pType = paramTypes[i];
        }
        // If the TVM values are compatible with the method's class or, in the case that the TVM value type is not known, and the TVM itself is not compatible with the method's class, then assume that the TVM values are meant to be the instance of the call.
        if ( arg != null && pType != null && ( ( tv.getType() != null && pType.isAssignableFrom( tv.getType() ) ) ||
            (tv.getType() == null && !pType.isInstance( obj ) ) ) ) {
          argMaps.add( tv );
          timePoints.addAll( tv.keySet() );
        } else {
          argMaps.add( null );
        }
      } else {
        argMaps.add( null );
      }
      ++i;
    }

    // Now run for each timepoint.
    for ( Parameter<Long> t : timePoints ) {
      // Get the object.
      Object object = obj;
      if ( objectMap != null ) {
        object = objectMap.getValue( t );
      }

      // Get the arguments.
      Object[] argsForTime = new Object[args.length];
      i = 0;
      for ( Object arg : args ) {
        Object argForTime = arg;
        if ( argMaps.get( i ) != null ) {
          argForTime = argMaps.get( i ).getValue( t );
        }
        argsForTime[i] = argForTime; 
        ++i;
      }
      
      // Run for timepoint!
      Object result = null;
      try {
        result = ClassUtils.runMethod( obj, method, argsForTime );
        V v = tryCastValue( result );
        if ( v != null ) {
          setValue(t, v);
        }
      } catch ( IllegalArgumentException e ) {
        e.printStackTrace();
      } catch ( IllegalAccessException e ) {
        e.printStackTrace();
      } catch ( InvocationTargetException e ) {
        e.printStackTrace();
      }      
      
    }
    if ( Debug.isOn() || checkConsistency ) isConsistent();
    }
  }

  /**
   * Create a {@link TimeVaryingMap} and initialize it with the given
   * {@link Call} and arguments, assuming that one or more of the arguments and
   * the object from which the Call is invoked are {@link TimeVaryingMap}s whose
   * values should be used in the invocation instead of the map itself.
   * <p>
   * For example, if {@code s1} and {@code s2} are
   * {@link TimeVaryingMap}&lt;String&gt;s, then for
   * {@code b = s1.contains(s2)}, {@code b} would be a
   * {@link TimeVaryingMap}&lt;Boolean&gt; where
   * {@code b.getValue(t) == s1.getValue(t).contains(s2.getValue(t))}. This map
   * would be created with the following code:<br>
   * {@code FunctionCall f = new FunctionCall(s1, String.class, "contains", new Object[]{ s2 }, Boolean.class)}<br> 
   * {@code {@link TimeVaryingMap}&lt;Boolean&gt; b = new
   * {@link TimeVaryingMap}&lt;Boolean&gt;("myMap", Boolean.class, f, Interpolation.STEP)} .
   * 
   * @param name
   *          the name of the map to create
   * @param cls
   *          the value type of the map
   * @param call
   *          the function to be called
   * @param interpolation
   *          how to interpolate between map entries in the created map
   */
  public TimeVaryingMap( String name, Class<V> cls, Call call, Interpolation interpolation,
                         boolean newStufffffffffffffffffffffffffffffffffffff ) {
    this(name, (String)null, (V)null, cls, interpolation);
    
    // Initialize the transformation of the input call into a call that returns a TimeVaryingMap.
    //object and arguments to potentially be transformed.
    Call newCall = null;
    boolean callIsConstructor = false;
    if ( call instanceof FunctionCall ) {
      callIsConstructor = false;
      newCall = new TimeVaryingFunctionCall( (FunctionCall)call );
    } else if ( call instanceof ConstructorCall ) {
      newCall = new TimeVaryingConstructorCall( (ConstructorCall)call );
      callIsConstructor = true;
    } else {
      // TODO -- ERROR
      callIsConstructor = false;
    }
    Object newObject = call.getObject();
    Object[] newArgs = new Object[call.getArguments().size()];
    int i = 0;
    for ( Object arg : call.getArguments() ) {
      newArgs[i] = arg;
      ++i;
    }
    
    // gather timepoints and interpret which objects/arguments will be treated as TVMs
    TreeSet< Parameter< Long > > timePoints = 
        new TreeSet< Parameter< Long > >( TimeComparator.instance );

    
    // Process the object of the method.
    TimeVaryingMap<?> objectMap = TimeVaryingFunctionCall.evaluateAsTimeVaryingMap( call.getObject(), true, null );
    if ( objectMap != null ) {
      newObject = objectMap;
    }

  }
  
  
  public TimeVaryingMap( String name, Class<V> cls, Call call, Interpolation interpolation ) {
      this(name, (String)null, (V)null, cls, interpolation);
      if ( size() == 1 && firstKey().getValue() == 0 && firstValue() == null ) {
        remove(firstKey());
      }
    // gather timepoints and interpret which objects/arguments will be treated as TVMs
    TreeSet< Parameter< Long > > timePoints = 
        new TreeSet< Parameter< Long > >( TimeComparator.instance );
    // Process the object of the method.
    TimeVaryingMap<?> objectMap = null;
    Object obj = call.getObject();
    Member member = call.getMember();
    if ( call instanceof TimeVaryingFunctionCall ) {
      Object oo = null;
      try {
        oo = call.evaluateObject( true );
      } catch (Throwable e ) {
      }
      if ( oo != null ) obj = oo;
      if ( obj instanceof TimeVaryingMap ) {
        objectMap = (TimeVaryingMap< ? >)obj;
      } else { 
        objectMap = Functions.tryToGetTimelineQuick( obj );
      }
    }
    if ( obj != null && !call.isStatic() && obj instanceof TimeVaryingMap ) {
      Class<?> methodClass = member.getDeclaringClass();
      objectMap = (TimeVaryingMap<?>)obj;
      // If the TVM values are compatible with the method's class or, in the case that the TVM value type is not known, and the TVM itself is not compatible with the method's class, then assume that the TVM values are meant to be the instance of the call.
      if ( ( objectMap.getType() != null && methodClass.isAssignableFrom( objectMap.getType() ) ) ||
          (objectMap.getType() == null && !methodClass.isInstance( obj ) ) ) {
        timePoints.addAll( objectMap.keySet() );
      } else {
        // Hide the fact that the object is a TVM since we're not using it's values.  Interpret a non-null objectMap as an indication that it's values should be used.
        objectMap = null; 
      }
    }
    // Process the arguments.
    ArrayList< TimeVaryingMap< ? > > argMaps =
        new ArrayList< TimeVaryingMap< ? > >();
    Class< ? >[] paramTypes = call.getParameterTypes();
    Object[] args = call.getArgumentArray();
    int i = 0;
    Class<?> pType = null;
    for ( Object arg : args ) {
      Object a = arg;
      if ( i < paramTypes.length ) {  // helps with variable arguments
        pType = paramTypes[i];
        pType = ClassUtils.getNonPrimitiveClass( pType );
      }
      if ( call instanceof TimeVaryingFunctionCall ) {
        Object oo = null;
        try {
          oo = call.evaluateArg( a, pType, true );
        } catch (Throwable e ) {
        }
        if ( oo != null ) a = oo;
//        if ( a instanceof TimeVaryingMap ) {
//          objectMap = (TimeVaryingMap< ? >)obj;
//        }
      }
      if ( a instanceof TimeVaryingMap ) {
        TimeVaryingMap<?> tv = (TimeVaryingMap<?>)a;
        // If the TVM values are compatible with the method's class or, in the case that the TVM value type is not known, and the TVM itself is not compatible with the method's class, then assume that the TVM values are meant to be the instance of the call.
        if ( pType != null && ( ( tv.getType() != null &&
                                  ( pType.isAssignableFrom( tv.getType() ) ||
                                    ( Number.class.isAssignableFrom( pType ) &&
                                      Number.class.isAssignableFrom( tv.getType() ) ) ) ) ||
            ( (tv.getType() == null || tv.getType() ==  Object.class) && !pType.isInstance( a ) ) ) ) {
          argMaps.add( tv );
          timePoints.addAll( tv.keySet() );
        } else {
          argMaps.add( null );
        }
      } else {
        argMaps.add( null );
      }
      ++i;
    }

    // Now run for each timepoint.
    for ( Parameter<Long> t : timePoints ) {
      // Get the object.
      Object object = obj;
      if ( objectMap != null ) {
        object = objectMap.getValue( t );
      }
  
      // Get the arguments.
      Object[] argsForTime = new Object[args.length];
      i = 0;
      for ( Object arg : args ) {
        Object argForTime = arg;
        if ( argMaps.get( i ) != null ) {
          argForTime = argMaps.get( i ).getValue( t );
        }
        argsForTime[i] = argForTime; 
        ++i;
      }
      
      // Run for timepoint!
      Object result = null;
      Call newCall = null;
      try {
        if ( member instanceof Method ) {
          newCall = new FunctionCall( object, (Method)member, argsForTime, (Call)null, call.getReturnType() );
        } else {
          newCall = new ConstructorCall( object, (Constructor<?>)member, argsForTime, (ConstructorCall)null, call.getReturnType() );
        }
        result = newCall.evaluate( true );
        V v = tryCastValue( result );
        if ( v != null ) {
          setValue(t, v);
        }
      } catch ( IllegalArgumentException e ) {
        e.printStackTrace();
      } catch ( IllegalAccessException e ) {
        e.printStackTrace();
      } catch ( InvocationTargetException e ) {
        e.printStackTrace();
      } catch ( InstantiationException e ) {
        e.printStackTrace();
      }      
      
    }
    
    removeDuplicates();
  
    if ( Debug.isOn() || checkConsistency ) isConsistent();
  }


  public TimeVaryingMap( String name, TimeVaryingMap<V> tvm ) {
    this( name, null, null, tvm.type );
    owner = null; //owner = tvm.owner;
    interpolation = tvm.interpolation;
    floatingEffects.clear();
    floatingEffects.addAll( tvm.floatingEffects );
    appliedSet.clear();
    appliedSet.addAll( tvm.appliedSet );
    clear(); // clears the default value.
    putAll( tvm );
  }

  public <VV>TimeVaryingMap( String name, TimeVaryingMap<VV> tvm, Class<V> cls ) {
    this( name, null, null, cls );
    owner = tvm.owner;
    interpolation = tvm.interpolation;
    floatingEffects.clear();
    List< TimeVaryingMap< VV >.TimeValue > effects = tvm.floatingEffects;
    List< TimeVaryingMap< V >.TimeValue > newEffects = new ArrayList< TimeVaryingMap<V>.TimeValue >();
    for ( TimeVaryingMap< VV >.TimeValue f : effects ) {
      TimeVaryingMap< V >.TimeValue tv = new TimeValue( f.first, tryCastValue( f.second ) );
      newEffects.add(tv);
    }
    floatingEffects.addAll( newEffects );
    appliedSet.clear();
    appliedSet.addAll( tvm.appliedSet );
    clear(); // clears the default value.
    for ( Map.Entry< Parameter< Long >, VV > e : tvm.entrySet() ) {
      V v = tryCast( e.getValue(), cls );
      if ( v != null || e.getValue() == null ) {
        setValue( e.getKey(), v );
      }
    }
  }

  public TimeVaryingMap( TimeVaryingMap<V> tvm ) {
    this( tvm.getName(), tvm );
  }
  public <VV> TimeVaryingMap( TimeVaryingMap<VV> tvm, Class<V> cls ) {
    this( tvm.getName(), tvm, cls );
  }

  public TimeVaryingMap( String name, String fileName, V defaultValue,
                         Class< V > cls, Interpolation interpolation ) {
    this( name, defaultValue, cls );
    if (interpolation != null ) this.interpolation = interpolation;
    fromCsvFile( fileName, type );
  }
  
  @Override
  public TimeVaryingMap<V> clone() {
    TimeVaryingMap<V> tvm = new TimeVaryingMap<V>(this);
    return tvm;
  }
  public <VV> TimeVaryingMap<VV> clone(Class<VV> cls) {
    TimeVaryingMap<VV> tvm = new TimeVaryingMap<VV>(this, cls);
    return tvm;
  }
  public TimeVaryingMap< V > emptyClone() {
    TimeVaryingMap<V> tvm = new TimeVaryingMap<V>(this.name, this.type);
    return tvm;
  }
  public <T> TimeVaryingMap< T > emptyClone(Class<T> cls) {
    TimeVaryingMap<T> tvm = new TimeVaryingMap<T>(this.name, cls);
    return tvm;
  }

  public TimeVaryingMap< V > makeNew() {
    return makeNew(this.getName());
  }
  public TimeVaryingMap< V > makeNew(String name) {
    TimeVaryingMap< V > copy = emptyClone();
    copy.setName( name );
    return copy;
  }

  public static <T> void maybeDeconstructParameter( ParameterListener pl,
                                                    Object maybeParam ) {
    if ( maybeParam instanceof Parameter ) {
      Parameter<T> param = (Parameter< T >)maybeParam;
      if ( param.getOwner() == null || param.getOwner() == pl ) {
        T val = param.getValueNoPropagate();
        param.deconstruct();
        // setting value back for timelines that are dependent on this one and
        // use the same timepoints.
        param.value = val;
      }
    }
  }

  public static <KEY,VAL> void deconstructMap( ParameterListener pl, TreeMap<KEY,VAL> map ) {
    List<VAL> values = new LinkedList< VAL >();
    // need to remove keys before values in case the values are keys!
    while (!map.isEmpty()) {
      Entry< KEY, VAL > front = map.firstEntry();
      KEY key = front.getKey();
      VAL value = front.getValue();
      map.remove( key );
      values.add( value );
      maybeDeconstructParameter( pl, key );
    }
    for ( VAL value : values ) {
      maybeDeconstructParameter( pl, value );
    }
  }

    public Pair< Parameter<?>, ParameterListener > getTimeVaryingMapOwners() {
        TimeVaryingMap<?> tvm = this;
        ParameterListener pl = null;
        Parameter<?> p = null;

        Object tvmOwner = tvm.getOwner();
        if ( tvmOwner != null ) {
            if ( tvmOwner instanceof Parameter ) {
                p = (Parameter<?>)tvmOwner;
                pl = p.getOwner();
            } else if (tvmOwner instanceof ParameterListener ) {
                pl = (ParameterListener)tvmOwner;
                for ( Parameter<?> pp : pl.getParameters( false, null ) ) {
                    if ( Utils.valuesEqual( pp.getValue( false ), tvm ) ) {
                        p = pp;
                        break;
                    }
                }
            }
        }
        return new Pair< Parameter< ? >, ParameterListener >( p, pl );
    }

    /**
     * Make sure anything dependent on this is stale.  EffectFunction handles
     * this, so it is not needed when an EffectFunction is changing this map.
     */
    public void setStaleAnyReferencesToTimeVarying() {
        //System.out.println("setStaleAnyReferencesToTimeVarying(): " + toShortString() );
        Pair<Parameter<?>, ParameterListener> pair = getTimeVaryingMapOwners();
        if ( pair != null && pair.first != null && pair.second != null ) {
            pair.second.setStaleAnyReferencesTo( pair.first, null );
        }
    }

    /**
     * Make sure anything dependent on this has the chance to update.
     * EffectFunction handles this, so it is not needed when an EffectFunction
     * is changing this map.
     */
    public void handleChangeToTimeVaryingMap() {
        Pair<Parameter<?>, ParameterListener> pair = getTimeVaryingMapOwners();
        if ( pair != null && pair.second != null ) {
            pair.second.handleValueChangeEvent( pair.first, null );
        }
    }

  protected boolean deconstructingMap = true;
  //protected boolean deconstructingKeys = true;
  //protected boolean deconstructingValues = false;

  @Override
  public void deconstruct() {
    System.out.println("@@@  deconstructing timeline: " + name );
    setStaleAnyReferencesToTimeVarying();
    name = "DECONSTRUCTED_" + name;
    if ( deconstructingMap ) {
      deconstructMap( this, this );
    } else {
      clear();
    }
    for ( TimeValue tv : floatingEffects ) {
      tv.deconstruct();
    }
    floatingEffects.clear();
    handleChangeToTimeVaryingMap();
  }

  protected void breakpoint() {}

  public Parameter< Long> getTimepointBefore( Parameter< Long> t ) {
    if ( t == null ) return null;
    Parameter< Long > k = this.lowerKey( t );
    if ( k != null && k.getValueNoPropagate() == null ) {
      Debug.error( true, "Error! Null timepoint " + k + " in TimeVaryingMap: "
                         + this );
    }
    return k;
  }

  public Parameter< Long> getTimepointBefore( Long t ) {
    if ( t == null ) return null;
    Parameter< Long> tp = makeTempTimepoint( t, false );
    return getTimepointBefore( tp );
  }

  public Parameter< Long> getTimepointEarlier( Parameter< Long> t ) {
    if ( t == null ) return null;
    return getTimepointEarlier( t.getValue( false ) );
  }

  public Parameter< Long> getTimepointEarlier( Long t ) {
    if ( t == null ) return null;
    Long justBeforeTimeVal = t;
    Parameter< Long> justBeforeTime = getTimepointBefore( t );
    while ( justBeforeTime != null && justBeforeTime.getValue( false ) != null ) {
      justBeforeTimeVal = justBeforeTime.getValue( false );
      if ( justBeforeTimeVal < t ) {
        break;
      }
      justBeforeTime = getTimepointBefore( justBeforeTime );
    }
    return justBeforeTime;
  }

  public Parameter< Long> getTimepointAfter( Parameter< Long> t ) {
    if ( t == null ) return null;
    Parameter< Long > k = this.higherKey( t );
    if ( k != null && k.getValueNoPropagate() == null ) {
      Debug.error( true, "Error! Null timepoint " + k + " in TimeVaryingMap: "
                         + this );
    }
    return k;
  }

  public Parameter< Long> getTimepointAfter( Long t ) {
    if ( t == null ) return null;
    Parameter< Long> tp = makeTempTimepoint( t, true );
    return getTimepointAfter( tp );
  }

  public Parameter< Long> getTimepointLater( Parameter< Long> t ) {
    if ( t == null ) return null;
    return getTimepointLater( t.getValue( false ) );
  }

  public Parameter< Long> getTimepointLater( Long t ) {
    if ( t == null ) return null;
    Long justAfterTimeVal = t;
    Parameter< Long> justAfterTime = getTimepointAfter( t );
    while ( justAfterTime != null ) {
      justAfterTimeVal = justAfterTime.getValue( false );
      if ( justAfterTimeVal > t ) {
        break;
      }
      justAfterTime = getTimepointAfter( justAfterTime );
    }
    return justAfterTime;
  }

  public V getValueBefore( Parameter< Long> t ) {
    if ( t == null ) return null;
    Parameter< Long> justBeforeTime = getTimepointBefore( t );
    V valBefore = null;
    if ( justBeforeTime != null ) {
      valBefore = get( justBeforeTime );
    }
    return valBefore;
  }

  public V getValueBefore( Long t ) {
    if ( t == null ) return null;
    Parameter< Long> justBeforeTime = getTimepointBefore( t );
    V valBefore = null;
    if ( justBeforeTime != null ) {
      valBefore = get( justBeforeTime );
    }
    return valBefore;
  }

  public V getValueEarlier( Parameter< Long> t ) {
    if ( t == null ) return null;
    return getValueEarlier( t.getValue( false ) );
  }

  public V getValueEarlier( Long t ) {
    if ( t == null ) return null;
    Parameter< Long> justEarlierTime = getTimepointEarlier( t );
    V valBefore = null;
    if ( justEarlierTime != null ) {
      valBefore = get( justEarlierTime );
    }
    return valBefore;
  }

  public V getValueAfter( Parameter< Long> t ) {
    if ( t == null ) return null;
    Parameter< Long> justAfterTime = getTimepointAfter( t );
    V valAfter = null;
    if ( justAfterTime != null ) {
      valAfter = get( justAfterTime );
    }
    return valAfter;
  }

  public V getValueAfter( Long t ) {
    if ( t == null ) return null;
    Parameter< Long> justAfterTime = getTimepointAfter( t );
    V valAfter = null;
    if ( justAfterTime != null ) {
      valAfter = get( justAfterTime );
    }
    return valAfter;
  }

    public V getValueLater( Parameter< Long> t ) {
        if ( t == null ) return null;
        return getValueLater( t.getValue( false ) );
    }

    public V getValueLater( Long t ) {
        if ( t == null ) return null;
        Parameter< Long> justLaterTime = getTimepointLater( t );
        V valAfter = null;
        if ( justLaterTime != null ) {
            valAfter = get( justLaterTime );
        }
        return valAfter;
    }

    public Parameter< Long> makeTempTimepoint( Long t, boolean maxName ) {
    //if ( t == null ) return null;
    String n = ( maxName ? StringDomain.typeMaxValue : null );
    Parameter< Long> tp = new Parameter< Long>( n, null, t, this );
    return tp;
  }

  protected static long correctSamplePeriod( long samplePeriod,
                                            long horizonDuration ) {
    if ( samplePeriod == 0 ) {
      samplePeriod = Math.max( 1, horizonDuration / 10 );
    }
    return samplePeriod;
  }

  @Override
  public void handleValueChangeEvent( Parameter< ? > parameter, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;

    breakpoint();
    if ( parameter == null ) return;
    if ( parameter.getValueNoPropagate() instanceof Long ) {
      unfloatEffects( parameter );
    }
  }

  protected void floatEffects( Parameter< Long> t ) {
    breakpoint();
    if ( t == null ) return;
    if (t.getValueNoPropagate() == null) {
      Debug.error( "No value for timepoint! " + t);
    }
    ;
    if ( !containsKey( t ) ) return;
    V value = get( t );
    if ( Debug.isOn() ) Debug.outln( getName() + ": floating effect, (" + t + ", " + value + ")" );
    floatingEffects.add( new TimeValue( t, value ) );
    remove( t );
    if ( Debug.isOn() || checkConsistency ) isConsistent();
  }

  protected void unfloatEffects( Parameter<?> t ) {
    breakpoint();
    if ( t == null ) return;
    if ( t.getValueNoPropagate() == null ) return;
    ArrayList<TimeValue> copy = new ArrayList<TimeValue>( floatingEffects );
    for ( TimeValue e : copy ) {
      if ( e.first.compareTo( t ) == 0 ) {
        put( e.first, e.second );
        floatingEffects.remove( e );
        if ( Debug.isOn() ) Debug.outln( getName() + ": unfloated effect, " + e );
      }
    }
    if ( Debug.isOn() || checkConsistency ) isConsistent();
  }

  @Override
  public void handleDomainChangeEvent( Parameter< ? > param, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;

    unfloatEffects( tryCastTimepoint( param ) );
  }

  @Override
  public String getName() {
    if ( name != null && !name.isEmpty() ) return name;
    return getClass().getSimpleName();
  }

  public void setName( String newName ) {
    if ( Utils.isNullOrEmpty( newName ) ) {
      newName = getClass().getSimpleName();
    }
    this.name = newName;
  }

  @Override
  public Object getOwner() {
    return owner;
  }
  @Override
  public void setOwner( Object owner ) {
    this.owner = owner;
  }
  public List< TimeValue > getFloatingEffects() {
    return floatingEffects;
  }
  public void setFloatingEffects( List< TimeValue > floatingEffects ) {
    this.floatingEffects = floatingEffects;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.HasParameters#getParameters(boolean, java.util.Set)
   * Add startTimes, durations, values that are Parameters, and (if deep)
   * parameters of effects.
   */
  @Override
  public Set< Parameter< ? > > getParameters( boolean deep,
                                              Set< HasParameters > seen ) {
    // Not allowing public access since some are not owned by this object.
    return Utils.getEmptySet();
  }

  public Set< Parameter< ? > > getParameters() {
    return getParameters( false, null );
    // Not allowing public access since some are not owned by this object.
//    HashSet< HasParameters > seen = new LinkedHashSet< HasParameters >();
//    Set< Parameter< ? > > params = new LinkedHashSet< Parameter< ? > >();
//    params = Utils.addAll( params, HasParameters.Helper.getParameters( keySet(), false, seen, true ) );
//    params = Utils.addAll( params, HasParameters.Helper.getParameters( values(), false, seen, true ) );
//    params = Utils.addAll( params, HasParameters.Helper.getParameters( floatingEffects, false, seen, true ) );
//    return params;
  }

  @Override
  public Set< Parameter< ? > > getFreeParameters( boolean deep,
                                                  Set< HasParameters > seen ) {
    Debug.error( true, "This method is not supported!" );
    return null;
  }
  @Override
  public void setFreeParameters( Set< Parameter< ? > > freeParams,
                                 boolean deep,
                                 Set< HasParameters > seen) {
    Debug.error( true, "This method is not supported!" );
  }


  @Override
  public boolean hasParameter( Parameter< ? > parameter, boolean deep,
                               Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;

    if ( HasParameters.Helper.hasParameter( this, parameter, deep, seen, false ) ) {
      return true;
    }
    if ( HasParameters.Helper.hasParameter( floatingEffects, parameter, deep, seen, true ) ) {
      return true;
    }
    return false;
  }

  @Override
  public Parameter<?> getParameter(String name) {
      return HasParameters.Helper.getParameter(this, name);
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

    breakpoint();
    if ( HasParameters.Helper.substitute( this, p1, p2, deep, seen, false ) ) {
      if ( Debug.isOn() || checkConsistency ) isConsistent();
      return true;
    }
    if ( HasParameters.Helper.substitute( floatingEffects, p1, p2, deep, seen, true ) ) {
      if ( Debug.isOn() || checkConsistency ) isConsistent();
      return true;
    }
    return false;

  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.LazyUpdate#isStale()
   */
  @Override
  public boolean isStale() {
    if ( !floatingEffects.isEmpty() ) return true;
    return ( HasParameters.Helper.isStale( this, false, null, false ) );
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.LazyUpdate#setStale(boolean)
   */
  @Override
  public void setStale( boolean staleness ) {
      Debug.getInstance()
      .logForce( "BAD!!!!!!!!!!!!!!   THIS SHOULD NOT BE GETTING CALLED!  setStale(" + staleness + "): "
                     + toShortString() );
    if ( Debug.isOn() ) Debug.outln( "setStale(" + staleness + ") to " + this );
    Debug.error( "This method is not supported!");
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#setStaleAnyReferencesTo(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public void setStaleAnyReferencesTo( Parameter< ? > changedParameter,
                                       Set< HasParameters > seen ) { // ignoring since there's no recursive call here
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if (p.first) return;
    seen = p.second;

    if ( Debug.isOn() ) Debug.outln( getName() + ".setStaleAnyReferencesTo(" + changedParameter + ")" );
    if ( changedParameter == null ) return;
    if ( containsKey( changedParameter ) ) {
      if ( Debug.isOn() ) Debug.outln( getName() + ".setStaleAnyReferencesTo(" + changedParameter + "): does contain" );
      floatEffects( tryCastTimepoint( changedParameter ) );
    } else {
      if ( Debug.isOn() ) Debug.outln( getName() + ".setStaleAnyReferencesTo(" + changedParameter + "): does not contain" );
    }
    if ( Debug.isOn() || checkConsistency ) isConsistent();
    
//    // Pass message up the chain if necessary.
//    Object o = getOwner();
//    if ( o instanceof Parameter ) {
//      o = ((Parameter<?>)o).getOwner();
//    }
//    if ( o instanceof ParameterListener ) {
//      ((ParameterListener)o).setStaleAnyReferencesTo( changedParameter, seen );
//    }
  }

//  @Override
//  public V get(Object key) {
//    return super.get(key);
//  }

  @Override
  public V put(Parameter<Long> key, V value) {
    return super.put(key, value);
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#refresh(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public boolean refresh( Parameter< ? > parameter ) {
    // TODO -- REVIEW -- do nothing? owner's responsibility?
    // TODO -- TimeVaryingMap should own the values if they are Parameters and
    // maybe any Parameters the value has itself, unless the value is a
    // ParameterListener and owns its own Parameters.
    return false;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.TimeVarying#getValue(gov.nasa.jpl.ae.event.Parameter< Long>)
   */
  @Override
  public V getValue( Parameter< Long > t ) {
    return getValue( t, true );
  }
  public V getValue( Parameter< Long > t, boolean valuesEqualForKeysOk ) {
    if ( t == null ) return null;
    NavigableMap< Parameter< Long >, V > m = headMap( t, true );
    if ( !m.isEmpty() ) {
      Entry< Parameter< Long >, V > e = m.lastEntry();
      if ( e.getKey().equals( t )
           || ( valuesEqualForKeysOk && Expression.valuesEqual( e.getKey(), t,
                                                                Long.class ) ) ) {
        return e.getValue();
      }
    }
    if ( Debug.isOn() || checkConsistency ) isConsistent();
    // Saving this check until later in case a null time value is acceptable,
    // and get(t) above works.
    if ( t.getValue( false ) == null ) return null;
    if ( valuesEqualForKeysOk ) {
      return getValue( t.getValue( false ) );
    }
    V v1 = null, v2 = null;
    if ( interpolation.type == Interpolation.STEP ) {
      if ( !m.isEmpty() ) {
        v1 = m.lastEntry().getValue();
      }
      if ( Debug.isOn() ) {
        v2 = getValueBefore( t );
        //Assert.assertEquals( v1, v2 );
        Debug.outln("getValue() change looks good.");
      }
      return v1; //
    } else if ( interpolation.type == Interpolation.NONE ) {
      return null;
    } else if ( interpolation.type == Interpolation.LINEAR ) {
      if ( m.isEmpty() ) return null;
      V v = interpolatedValue( t, m.lastEntry() );
      return v;
    }
    Debug.error( true,
                 "TimeVaryingMap.getValue(): invalid interpolation type! "
                     + interpolation.type );
    return null;
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.TimeVarying#getValue(java.lang.Long)
   */
  @Override
  public V getValue( Long t ) {
    if ( t == null ) return null;
    if ( isEmpty() ) return null;
    Parameter< Long> tp = makeTempTimepoint( t, true );
    // Find the entry for this timepoint or the preceding timepoint. 
    Entry< Parameter< Long>, V > e = this.floorEntry( tp );
    if ( Debug.isOn() || checkConsistency ) isConsistent();
    if ( e != null ) {
      Parameter< Long > k = e.getKey();
      if ( k != null && k.getValue( false ) != null
           && LongDomain.defaultDomain.equals( t, k.getValue( false ) ) ) {
        return e.getValue();
      }
      if ( interpolation.type == Interpolation.NONE ) {
        return null;
      }
      if ( interpolation.type == Interpolation.STEP ) {
        return e.getValue();
      }
      if ( interpolation.isLinear() ) {
        V v = interpolatedValue( tp, e );
        return v;
      } else {
        Debug.error( true, "Interpolation " + interpolation + " not expected!");
      }
    }
    // If we couldn't find a value, then there are no values defined for the point.
    // See if the first entry could work in case of multiple entries at the same time.
    if ( !isEmpty() ) {
      Entry< Parameter< Long>, V > f = firstEntry();
      Parameter< Long> k = f.getKey();
      if ( LongDomain.defaultDomain.lessEquals( k.getValue( false ), t ) ) {
        return f.getValue();
      }
    }
    return null;
  }
  
  public V interpolatedValue(Parameter< Long> t, Entry< Parameter< Long>, V > entryBefore) {
    if ( t == null ) return null;
    if ( entryBefore == null ) return null;
    Parameter< Long> t1 = null;
    V v1 = null, v2 = null;
    t1 = entryBefore.getKey();
    v1 = entryBefore.getValue();
    if ( Debug.isOn() ) {
      //Assert.assertEquals( t1, getTimepointBefore( t ) );
      //Assert.assertEquals( v1, get( t1 ) );
      Debug.outln("getValue() change looks good.");
    }
    Parameter< Long> t2 = getTimepointAfter( t );
    //v1 = get( t1 );
    
    return interpolatedValue( t1, t1, t2, v1, v2 );
  }

  public V interpolatedValue(Parameter< Long> t1, Parameter< Long> t, Parameter< Long> t2, V v1, V v2 ) {
    if ( t1.valueEquals( t2 ) ) return v1;
    v2 = get( t2 );
    if ( v1 == null ) return null;
    if ( v2 == null ) return v1;
    // floorVal+(ceilVal-floorVal)*(key-floorKey)/(ceilKey-floorKey)
    return interpolatedValue( t1.getValue(), t.getValue(), t2.getValue(), v1, v2 );
  }
  
  public V interpolatedValue(Long t1, Long t, Long t2, V v1, V v2 ) {
    try {
      v1 = Functions.plus( v1,
                           Functions.divide( Functions.times( Functions.minus( v2, v1 ),
                                                              Functions.minus( t, t1 ) ),
                                             Functions.minus( t2, t1 ) ) );
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    } catch ( IllegalAccessException e ) {
      e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      e.printStackTrace();
    } catch ( InstantiationException e ) {
      e.printStackTrace();
    }
    return v1;
  }

  public Long interpolatedTime(Parameter< Long> t1, Parameter< Long> t2, V v1, V v, V v2 ) {
    Long l1 = t1.getValue( false );
    Long l2 = t2.getValue( false );
    if ( Utils.valuesEqual( v1, v2 ) ) return l1;
    if ( t1 == null ) return null;
    if ( t2 == null ) return l1;
    // floorVal+(ceilVal-floorVal)*(key-floorKey)/(ceilKey-floorKey)
    return interpolatedTime( l1, l2, v1, v, v2 );
  }

  // t = t1 + ((v - v1) / (v2 - v1)) + (t2 - t1)
  public Long interpolatedTime(Long t1, Long t2, V v1, V v, V v2 ) {
    Object x = null;
    try {
      x = Functions.plus( t1, Functions.divide(
                                      Functions.times(
                                              Functions.minus( t2, t1 ),
                                              Functions.minus( v, v1 ) ),
                                      Functions.minus( v2, v1 ) ) );
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    } catch ( IllegalAccessException e ) {
      e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      e.printStackTrace();
    } catch ( InstantiationException e ) {
      e.printStackTrace();
    }
    if ( x instanceof Number ) {
      return ( (Number)x ).longValue();
    }
    return null;
  }

  /**
   * Return whether there exists a Parameter< Long>1 for an entry
   * (Parameter< Long>1, value1) such that
   * {@code Parameter< Long>1 == tp} or, if
   * {@code mustBeAtSameTimepoint} is false, {@code Parameter< Long>1.value}
   * equals {@code tp.value}, and value1 equals value.
   *
   * @param value
   *          the value to match with a value in the map
   * @param tp
   *          the time value required for the returned key
   * @param mustBeSameTimepoint
   *          if true, the matching entry must have this timepoint key (tested
   *          with ==)
   * @return true iff the {@code Parameter< Long>} key in the map, whose time
   *         value equals {@code tp}'s value and whose map entry equals value or
   *         {@code null} if there is no such Parameter< Long>.
   */
  public boolean hasValueAt( V value, Parameter< Long> tp,
                             boolean mustBeSameTimepoint ) {
    if ( tp == null ) return false;
    V v = get( tp );
    if ( value == v && ( v != null || containsKey( tp ) ) ) return true;
    if ( v != null ) return Expression.valuesEqual( value, v );// value.equals( v );
    // Saving this null check until later in case a null time value is
    // acceptable, and get(t) above works.
    if ( mustBeSameTimepoint || tp.getValue( false ) == null ) return false;
    return hasValueAt( value, tp.getValueNoPropagate() );
  }

  /**
   * Return the {@code Parameter< Long>1} for an entry
   * {@code (Parameter< Long>1, value1)} such that
   * {@code Parameter< Long>1 == tp} or if no such key exists
   * {@code Parameter< Long>1.value} equals {@code tp.value}, and
   * {@code value1} equals {@code value}.
   *
   * @param value
   *          the value to match with a value in the map
   * @param tp
   *          the time required for the returned key
   * @return a {@code Parameter< Long>} key in the map, which equals
   *         {@code tp} or whose time value equals that of {@code tp} and whose
   *         map entry equals {@code value} or {@code null} if there is no such
   *         key.
   */
  public Parameter< Long> keyForValueAt( V value, Parameter< Long> tp ) {
    if ( tp == null ) return null;
    if ( containsKey(tp) && Utils.valuesEqual( get( tp ), value ) ) return tp;
    Object tpVal = tp.getValueNoPropagate();
    
    Long l = null;
    try {
      l = Expression.evaluate( tpVal, Long.class, false );
    } catch ( ClassCastException e ) {

    } catch ( IllegalAccessException e ) {

    } catch ( InvocationTargetException e ) {

    } catch ( InstantiationException e ) {

    }
    if (l == null) {
      return null;
    }
    return keyForValueAt( value, l);
  }

  /**
   * Return the {@code Parameter< Long>1} for an entry
   * {@code (Parameter< Long>1, value1)} such that
   * {@code Parameter< Long>1.value} equals {@code t}, and {@code value1}
   * equals {@code value}.
   *
   * @param value
   *          the value to match with a value in the map
   * @param t
   *          the time value required for the returned key
   * @return a {@code Parameter< Long>} key in the map, whose time value
   *         equals {@code t} and whose map entry equals {@code value} or
   *         {@code null} if there is no such key.
   */
  public Parameter< Long> keyForValueAt( V value, Long t ) {
    // REVIEW -- use getKeys()?
    if ( t == null ) return null;
    Parameter< Long> tp = makeTempTimepoint( t, false );
    Entry< Parameter< Long>, V > e = this.floorEntry( tp );
    Parameter< Long> startKey = null;
    if ( e != null ) {
      startKey = e.getKey();
    } else if ( !isEmpty() ) {
      startKey = firstEntry().getKey();
    } else {
      return null;
    }
    NavigableMap< Parameter< Long >, V > tailMap =
        this.tailMap( startKey, true );
    for ( java.util.Map.Entry< Parameter< Long >, V > te : tailMap.entrySet() ) {
      Object mVal = te.getValue();
      if ( Utils.valuesEqual( value, mVal )
           && TimeDomain.defaultDomain.equals( t, te.getKey()
                                                    .getValueNoPropagate() ) ) {
        return te.getKey();
      }
      if ( TimeDomain.defaultDomain.greater( te.getKey().getValueNoPropagate(),
                                             t ) ) break;
    }
    return null;
  }

  /**
   * @param value
   *          the value to match
   * @param t
   *          the time value to match
   * @return whether there is a key with a time value equal to {@code t} and and
   *         a value equal to {@code value}.
   */
  public boolean hasValueAt( V value, Long t ) {
    return keyForValueAt( value, t ) != null;
  }

//  public V setValue( Long t, V value ) {
//  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.TimeVarying#setValue(gov.nasa.jpl.ae.event.Parameter< Long>, java.lang.Object)
   */
  @Override
  public V setValue( Parameter< Long > t, V value ) {
    breakpoint();
    // System.out.println( "$ $ $ # # # $ $ $    " + getName() + " setValue(" + t + ", " + value + ")" );
    if ( Debug.isOn() ) Debug.outln( getName() + " setValue(" + t + ", " + value + ")" );
    if ( t == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Error! trying to insert a null Parameter< Long> into the map!" );
      return null;
    }
    if ( t.getValueNoPropagate() == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Error! trying to insert a null Parameter< Long> value into the map!" );
      return null;
    }
    if ( Debug.isOn() ) {
      if ( t.getOwner() == null ) {
        Debug.error( false, "Warning: inserting a Parameter< Long> with null owner into the map--may be detached!" );
      }
      if ( value != null && value instanceof Parameter
           && ( (Parameter<?>)value ).getOwner() == null ) {
        Debug.error( true, "Warning: trying to insert a value with a null owner into the map--may be detached!" );
      }
    }

    // Sometimes the time value is a
    t = fixTimepoint(t);

    V oldValue = null;
    Parameter< Long> tp = keyForValueAt( value, t );
    if ( Debug.isOn() || checkConsistency ) isConsistent();
    if ( tp != null && tp != t ) {
      //remove( tp );
    }
    if ( getType() != null && value != null && !(getType().isAssignableFrom( value.getClass() ) ) ) {
      V valueBefore = value;
      try {
        value = Expression.evaluate( value, getType(), true, true );
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
      // TODO better message for null case ..  if ( valueBefore==nu)
      if ( valueBefore != value ) {
        String warningMsg = null;
        if ( value != null && !getType().isAssignableFrom( value.getClass() ) ) {
          Debug.error( false, warningMsg1(valueBefore, value) );
        } else {
          if ( Debug.isOn() ) {
            Debug.errln( warningMsg1(valueBefore, value) );
          }
        }
      }
    }
    if ( tp != t ) {
      Parameter<Long> tt = tryCastTimepoint( t );
      if (tt == null) {
        Debug.error( false, "Could not cast input time to Long" );
        return null;
      }
      setStaleAnyReferencesToTimeVarying();
      oldValue = put( t, value );
      if ( value != null &&
           ( type == null || value.getClass().isAssignableFrom( type ) ) ) {
        setType( value.getClass() );
      }
      handleChangeToTimeVaryingMap();
    }
    if ( Debug.isOn() || checkConsistency ) isConsistent();
    if ( Debug.isOn() ) Debug.outln( getName() + "setValue(" + t + ", " + value
                                     + ") returning oldValue=" + oldValue );
    return oldValue;

  }

  protected String warningMsg1(Object valueBefore, Object value) {
    String warningMsg = null;
    if ( value == null ) {
      warningMsg =
              "Warning: tried to insert value of wrong type. Expected type is "
                      + (getType() == null ? "null" : getType().getSimpleName()) + ".  Inserting null.";
    } else {
      warningMsg =
              "Warning: tried to insert value of wrong type, "
                      + valueBefore.getClass().getSimpleName()
                      + ". Expected type is " + (getType() == null ? "null" : getType().getSimpleName())
                      + ".  Inserting value of type "
                      + value.getClass().getSimpleName() + " instead. value = "
                      + MoreToString.Helper.toLongString(value) + "; this = "
                      + this.toString(true, true, null);
    }
    return warningMsg;
  }
  // Change non-Long timepoint value to Long
  public static Parameter<Long> fixTimepoint( Object tp ) {
    if ( tp instanceof Parameter ) {
      Object val = ((Parameter<?>) tp).getValueNoPropagate();
      if ( val instanceof Long ) {
        return (Parameter< Long >)tp;
      } else if (val instanceof Integer) {
        try {
          Long l = Expression.evaluate( val, Long.class, false );
          ((Parameter) tp).setValue(l);
        } catch ( ClassCastException e ) {
        } catch ( IllegalAccessException e ) {
        } catch ( InvocationTargetException e ) {
        } catch ( InstantiationException e ) {
        }
      }
    }
    return (Parameter< Long >)tp;
  }

  /**
   * @param n the number by which this map is multiplied
   * @return this map after multiplying each value by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> multiply( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this;
    if ( TimeVaryingMap.class.isAssignableFrom( getType() ) ) {
      for ( java.util.Map.Entry< Parameter< Long >, V > e : entrySet() ) {
        V v = e.getValue();
        if ( v instanceof TimeVaryingMap ) {
          TimeVaryingMap<?> tvm = (TimeVaryingMap< ? >)v;
          tvm.multiply( n );
        }
      }
      return this;
    }
    return multiply( n, firstKey(), null );
  }

  /**
   * @param n the number by which the map is multiplied
   * @return a copy of the map whose values are each multiplied by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> times( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this.clone();
    return times( n, firstKey(), null );
  }

  /**
   * @param n the number by which the map is multiplied
   * @param fromKey
   *          the key from which all values are multiplied by {@code n}.
   * @return this map after multiplying each value in the range [fromKey,
   *         toKey) by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > multiply( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return multiply( n, fromKey, null );
  }

  /**
   * For nested TimeVaryingMaps, an operation on elements is called recursively
   * on sub-maps. If the call is non-static, then it is assumed that the sub-map
   * is the object whose method or enclosed member's constructor is called. If
   * the call is static, then the sub-map replaces an argument to the call. The
   * argument chosen is the first whose type is compatible with TimeVaryingMap
   * and is null (if any such arguments are null). If there are no such
   * arguments, then the object is replaced if the type is compatible; else, an
   * IllegalArgumentException is raised.
   *
   * @param call
   *          the Call to apply to sub-maps
   * @return this or a copy of this depending on the call
   */
  public TimeVaryingMap< V > applyToSubMaps( Call call ) {
    // if ( TimeVaryingMap.class.isAssignableFrom( getType() ) ) {
    Integer indexOfBestArgumentToReplace = null;
    boolean isStatic = call.isStatic();
    for ( java.util.Map.Entry< Parameter< Long >, V > e : entrySet() ) {
      V v = e.getValue();
      Object retVal = null;
      if ( v instanceof TimeVaryingMap ) {
        TimeVaryingMap< ? > tvm = (TimeVaryingMap< ? >)v;
        // TODO -- REVIEW -- it would be good to be sure that this
        // substitution makes sense.
        if ( !isStatic ) {
          call.setObject( tvm );
          try {
            retVal = call.evaluate( true );
          } catch ( IllegalAccessException e1 ) {
            // TODO Auto-generated catch block
            //e1.printStackTrace();
          } catch ( InvocationTargetException e1 ) {
            // TODO Auto-generated catch block
            //e1.printStackTrace();
          } catch ( InstantiationException e1 ) {
            // TODO Auto-generated catch block
            //e1.printStackTrace();
          }
        } else {
          boolean gotTVM = false, gotNullTVM = false;
          for ( int i = 0; i < call.getArgumentArray().length; ++i ) {
            Object arg = call.getArgument( i );
            Class< ? > argType = call.getParameterTypes()[ i ];
            if ( arg == null && argType.isAssignableFrom( TimeVaryingMap.class ) ) {
              if ( indexOfBestArgumentToReplace == null && !gotNullTVM ) {
                indexOfBestArgumentToReplace = i;
                gotNullTVM = true;
                call.setArgument( i, tvm );
                break;
              }
            } else if ( arg instanceof TimeVaryingMap && !gotTVM ) {
              if ( indexOfBestArgumentToReplace == null && !gotTVM && !gotNullTVM ) {
                indexOfBestArgumentToReplace = i;
                gotTVM = true;
              }
            }
          }
          if ( indexOfBestArgumentToReplace != null ) {
            call.setArgument( indexOfBestArgumentToReplace, tvm );
          } else {
            // TODO -- REVIEW -- it would be good to be sure that this
            // substitution makes sense.
            if ( ( TimeVaryingMap.class.isAssignableFrom( call.getMember()
                                                           .getDeclaringClass() ) )
                 || call.getObject() instanceof TimeVaryingMap ) {
              call.setObject( tvm );
            } else {
              throw new IllegalArgumentException( "Warning! cannot apply call, "
                                                  + call + ", to map value, " + v );
            }
          }
        }
        try {
          retVal = call.evaluate( true );
        } catch ( IllegalAccessException e1 ) {
          // TODO Auto-generated catch block
          //e1.printStackTrace();
        } catch ( InvocationTargetException e1 ) {
          // TODO Auto-generated catch block
          //e1.printStackTrace();
        } catch ( InstantiationException e1 ) {
          // TODO Auto-generated catch block
          //e1.printStackTrace();
        }
      } else {
        Debug.error( false, "Warning! cannot apply call, "
                            + MoreToString.Helper.toLongString( call )
                            + ", to non-map value, "
                            + MoreToString.Helper.toLongString( v ) );
      }
    }
    // }
    return this;
  }

  
  /**
   * @param n the number by which the map is multiplied
   * @param fromKey the first key whose value is multiplied by {@code n}
   * @param toKey is not multiplied.  To include the last key, pass {@code null} for {@code toKey}.
   * @return this map after multiplying each value in the range [{@code fromKey}, {@code toKey})
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > multiply( Number n, Parameter< Long > fromKey,
                                        Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {

    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
      map = subMap( fromKey, true, toKey, true );
    } else {
      boolean same = toKey.equals(fromKey);  // include the key if same
      map = subMap( fromKey, true, toKey, same );
    }
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      e.setValue( Functions.times(e.getValue(), n ) );
    }
    return this;
  }

  /**
   * Multiply this map with another. This achieves for all {@code t} in
   * {@code thisBefore.keySet()} and {@code tvm.keySet()},
   * {@code thisAfter.get(t) == thisBefore.get(t) * tvm.get(t)}.
   *
   * @param tvm
   *          the {@code TimeVaryingMap} with which this map is multiplied
   * @return this {@code TimeVaryingMap} after multiplying by {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public <VV> TimeVaryingMap< V > multiply( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( tvm == null ) return null;
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    keys.addAll( tvm.keySet() );
    for ( Parameter< Long > k : keys ) {
      VV v = tvm.getValue( k, false );
      Number n = Expression.evaluate( v, Number.class, false );
      //if ( n != null ) {// n.doubleValue() != 0 ) {
      multiply( n, k, k );
      //}
    }
    return removeDuplicates();
  }
  /**
   * @param n the number by which the map is multiplied
   * @param fromKey
   *          the key from which all values are multiplied by {@code n}.
   * @return a copy of this map for which each value in the range [{@code fromKey},
   *         {@code toKey}) is multiplied by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > times( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return times( n, fromKey, null );
  }

  /**
   * Return the product of this map with another. This achieves for all
   * {@code t} in {@code this.keySet()} and {@code tvm.keySet()},
   * {@code newTvm.get(t) == this.get(t) * tvm.get(t)}.
   *
   * @param tvm the {@code TimeVaryingMap} with which this map is multiplied
   * @return a copy of this map multiplied by {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public < VV > TimeVaryingMap< V > timesOld( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    TimeVaryingMap< V > newTvm = this.clone();
    newTvm.multiply( tvm );
    return newTvm;
  }
  
  
  public <VV extends Number> TimeVaryingMap< V > times( TimeVaryingMap< VV > map ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return applyOperation( map, MathOperation.TIMES );
//    TimeVaryingMap< V > newTvm = emptyClone();
//    newTvm.clear();
//    Set< Parameter< Long > > keys =
//        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
//    keys.addAll( this.keySet() );
//    keys.addAll( map.keySet() );
//    for ( Parameter< Long> k : keys ) {
//      V v1 = this.getValue( k );
//      VV v2 = map.getValue( k );
//      Object v3 = Functions.times( v1, v2 );
//      // Multiplying a number and null will return the number.
//      // But this doesn't make sense when the interpolation is NONE.
//      if ( v3 == null && interpolation != NONE ) {
//        v3 = v1 != null ? v1 : v2;
//      }
//      V v4 = tryCastValue( v3 );
//      if ( v4 != null ) {
//        newTvm.setValue( k, v4 );
//      }
//    }
//    return newTvm;
  }


  public static < VV1, VV2 extends Number > TimeVaryingMap< VV1 > times( TimeVaryingMap< VV1 > tvm1,
                                                                         TimeVaryingMap< VV2 > tvm2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return tvm1.times( tvm2 );
  }

  /**
   * @param n the number by which the map is multiplied
   * @param fromKey
   *          the key from which all values are multiplied by {@code n}.
   * @return a copy of this map for which each value in the range [fromKey,
   *         toKey) is multiplied by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public <VV> TimeVaryingMap< VV > times( Number n, Parameter< Long > fromKey,
                                     Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    Class<VV> cls = (Class<VV>)ClassUtils.dominantTypeClass( getType(), n.getClass() );
    //TimeVaryingMap< VV > newTvm = new TimeVaryingMap< VV >(getName() + "_times_" + n, this, cls); 
    TimeVaryingMap< VV > newTvm = clone(cls);
    newTvm.setName( getName() + "_times_" + n );
    newTvm.multiply( n, fromKey, toKey );
    return newTvm;
  }


  /**
   * @param n the power by which this map is raised
   * @return this map after multiplying each value by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> power( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this;
    if ( TimeVaryingMap.class.isAssignableFrom( getType() ) ) {
      for ( java.util.Map.Entry< Parameter< Long >, V > e : entrySet() ) {
        V v = e.getValue();
        if ( v instanceof TimeVaryingMap ) {
          TimeVaryingMap<?> tvm = (TimeVaryingMap< ? >)v;
          tvm.power( n );
        }
      }
      return this;
    }
    return power( n, firstKey(), null );
  }

  /**
   * @param n the power to which the map is raised
   * @return a copy of the map whose values are each raised to the {@code n} power
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> pow( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this.clone();
    return pow( n, firstKey(), null );
  }

  /**
   * @param n the number by which the map is multiplied
   * @param fromKey
   *          the key from which all values are multiplied by {@code n}.
   * @return this map after multiplying each value in the range [fromKey,
   *         toKey) by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > power( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return power( n, fromKey, null );
  }

  // FIXME -- need to repeat changing of type like in the power() method below
  // for other math operations!

  /**
   * @param n the number by which the map is multiplied
   * @param fromKey the first key whose value is multiplied by {@code n}
   * @param toKey is not multiplied.  To include the last key, pass {@code null} for {@code toKey}.
   * @return this map after multiplying each value in the range [{@code fromKey}, {@code toKey})
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > power( Number n, Parameter< Long > fromKey,
                                    Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {

    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
      map = subMap( fromKey, true, toKey, true );
    } else {
      boolean same = toKey.equals(fromKey);  // include the key if same
      map = subMap( fromKey, true, toKey, same );
    }
    if ( !map.isEmpty() && ( n.doubleValue() < 0.0 || n.doubleValue() != n.longValue() ) ) {
      Class<?> cls = ClassUtils.mostSpecificCommonSuperclass( new Class<?>[]{Double.class, getType()} );
      setType( cls );
    }
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      e.setValue( tryCast(Functions.pow(e.getValue(), n ), getType()) );
    }
    return this;
  }
  

  /**
   * Multiply this map with another. This achieves for all {@code t} in
   * {@code thisBefore.keySet()} and {@code tvm.keySet()},
   * {@code thisAfter.get(t) == thisBefore.get(t) * tvm.get(t)}.
   *
   * @param tvm
   *          the {@code TimeVaryingMap} with which this map is multiplied
   * @return this {@code TimeVaryingMap} after multiplying by {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public <VV> TimeVaryingMap< V > power( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( tvm == null ) return null;
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    keys.addAll( tvm.keySet() );
    for ( Parameter< Long > k : keys ) {
      VV v = tvm.getValue( k, false );
      Number n = Expression.evaluate( v, Number.class, false );
      power( n, k, k );
    }
    return removeDuplicates();
  }

  /**
   * @param n the number by which the map is multiplied
   * @param fromKey
   *          the key from which all values are multiplied by {@code n}.
   * @return a copy of this map for which each value in the range [{@code fromKey},
   *         {@code toKey}) is multiplied by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > pow( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return pow( n, fromKey, null );
  }

  /**
   * Return the product of this map with another. This achieves for all
   * {@code t} in {@code this.keySet()} and {@code tvm.keySet()},
   * {@code newTvm.get(t) == this.get(t) * tvm.get(t)}.
   *
   * @param tvm
   *          the map/timeline by which the map is multiplied
   * @return a copy of this map multiplied by {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public < VV > TimeVaryingMap< V > powOld( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    TimeVaryingMap< V > newTvm = this.clone();
    newTvm.power( tvm );
    return newTvm;
  }

  public < VV > TimeVaryingMap< V > pow( TimeVaryingMap< VV > map ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return applyOperation( map, MathOperation.POW );
//    TimeVaryingMap< V > newTvm = emptyClone();
//    newTvm.clear();
//    Set< Parameter< Long > > keys =
//        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
//    keys.addAll( this.keySet() );
//    keys.addAll( map.keySet() );
//    for ( Parameter< Long> k : keys ) {
//      V v1 = this.getValue( k );
//      VV v2 = map.getValue( k );
//      Object v3 = Functions.pow( v1, v2 );
//      // Multiplying a number and null will return the number.
//      // But this doesn't make sense when the interpolation is NONE.
//      if ( v3 == null && interpolation != NONE ) {
//        v3 = v1 != null ? v1 : v2;
//      }
//      V v4 = tryCastValue( v3 );
//      if ( v4 != null ) {
//        newTvm.setValue( k, v4 );
//      }
//    }
//    return newTvm;
  }


  public static < VV1, VV2 > TimeVaryingMap< VV1 > pow( TimeVaryingMap< VV1 > tvm1,
                                                        TimeVaryingMap< VV2 > tvm2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return tvm1.pow( tvm2 );
  }

  /**
   * @param n the base {@code Number} that this map raises
   * @return a copy of this map for which each value in the range [fromKey,
   *         toKey) is multiplied by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public <VV> TimeVaryingMap< VV > pow( Number n, Parameter< Long > fromKey,
                                  Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    Class<VV> cls = (Class<VV>)ClassUtils.dominantTypeClass( getType(), n.getClass() );    
    TimeVaryingMap< VV > newTvm = this.clone(cls);
    newTvm.setName( "pow_" + getName() + "_" + n );
    //TimeVaryingMap< VV > newTvm = new TimeVaryingMap< VV >("pow_" + getName() + "_" + n, this, cls); 
    newTvm.power( n, fromKey, toKey );
    return newTvm;
  }


  
  /**
   * @param n the number by which this map is multiplied
   * @return this map after multiplying each value by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> npower( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this;
    if ( TimeVaryingMap.class.isAssignableFrom( getType() ) ) {
      for ( java.util.Map.Entry< Parameter< Long >, V > e : entrySet() ) {
        V v = e.getValue();
        if ( v instanceof TimeVaryingMap ) {
          TimeVaryingMap<?> tvm = (TimeVaryingMap< ? >)v;
          tvm.npower( n );
        }
      }
      return this;
    }
    return npower( n, firstKey(), null );
  }

  /**
   * @param n the number to raise to the power of the map
   * @return a copy of the map with the values, {@code v}, replaced by {@code n^v}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> npow( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this.clone();
    return npow( n, firstKey(), null );
  }

  /**
   * @param n the number by which the map is multiplied
   * @param fromKey
   *          the key from which all values are multiplied by {@code n}.
   * @return this map after multiplying each value in the range [fromKey,
   *         toKey) by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< Double > npower( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return npower( n, fromKey, null );
  }

  /**
   * @param n the number by which the map is multiplied
   * @param fromKey the first key whose value is multiplied by {@code n}
   * @param toKey is not multiplied.  To include the last key, pass {@code null} for {@code toKey}.
   * @return this map after multiplying each value in the range [{@code fromKey}, {@code toKey})
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public <VV> TimeVaryingMap< VV > npower( Number n, Parameter< Long > fromKey,
                                    Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
      map = subMap( fromKey, true, toKey, true );
    } else {
      boolean same = toKey.equals(fromKey);  // include the key if same
      map = subMap( fromKey, true, toKey, same );
    }
    if ( !map.isEmpty() ) {
      setType( Double.class );
    }
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      Number r = Functions.pow(n, e.getValue() );
      e.setValue( tryCastValue( (Double)r.doubleValue() ) );
    }
    return (TimeVaryingMap< VV >)this;
  }

  /**
   * Multiply this map with another. This achieves for all {@code t} in
   * {@code thisBefore.keySet()} and {@code tvm.keySet()},
   * {@code thisAfter.get(t) == thisBefore.get(t) * tvm.get(t)}.
   *
   * @param tvm
   *          the {@code TimeVaryingMap} with which this map is multiplied
   * @return this {@code TimeVaryingMap} after multiplying by {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public <VV> TimeVaryingMap< V > npower( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( tvm == null ) return null;
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    keys.addAll( tvm.keySet() );
    for ( Parameter< Long > k : keys ) {
      VV v = tvm.getValue( k, false );
      Number n = Expression.evaluate( v, Number.class, false );
      npower( n, k, k );
    }
    return removeDuplicates();
  }

  /**
   * @param n the number by which the map is multiplied
   * @param fromKey
   *          the key from which all values are multiplied by {@code n}.
   * @return a copy of this map for which each value in the range [{@code fromKey},
   *         {@code toKey}) is multiplied by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > npow( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return npow( n, fromKey, null );
  }

  /**
   * Return the product of this map with another. This achieves for all
   * {@code t} in {@code this.keySet()} and {@code tvm.keySet()},
   * {@code newTvm.get(t) == this.get(t) * tvm.get(t)}.
   *
   * @param tvm
   *          the map to which this map is raised
   * @return a copy of this map raised to the power of {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public < VV > TimeVaryingMap< V > npow( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    TimeVaryingMap< V > newTvm = this.clone();
    newTvm.npower( tvm );
    return newTvm;
  }

  public static < VV1, VV2 extends Number > TimeVaryingMap< VV1 > npow( TimeVaryingMap< VV1 > tvm1,
                                                                       TimeVaryingMap< VV2 > tvm2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return tvm1.npow( tvm2 );
  }

  /**
   * @param n the {@code Number} with which this map is raised
   * @return a copy of this map for which each value in the range [fromKey,
   *         toKey) is raised to the power of {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public <VV> TimeVaryingMap< VV > npow( Number n, Parameter< Long > fromKey,
                                  Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    Class<VV> cls = (Class<VV>)ClassUtils.dominantTypeClass( getType(), n.getClass() );    
    //TimeVaryingMap< VV > newTvm = new TimeVaryingMap< VV >("now_" + getName() + "_" + n, this, cls); 
    TimeVaryingMap< VV > newTvm = this.clone(cls);
    newTvm.setName( "now_" + getName() + "_" + n );
    newTvm.npower( n, fromKey, toKey );
    return newTvm;
  }

  public enum MathOperation { PLUS, MINUS, NEG, TIMES, DIVIDE, POW, LOG, MIN, MAX,
                              MOD, AND, OR, NOT, LT, LTE, GT, GTE, EQ, NEQ };

  public static boolean symmetric( MathOperation op ) {
    switch ( op ) {
      case DIVIDE:
      case LOG:
      case MINUS:
      case MOD:
      case POW:
      case NOT:
      case LT:
      case LTE:
      case GT:
      case GTE:
      case NEG:
        return false;
      case MIN:
      case MAX:
      case PLUS:
      case TIMES: 
      case AND:
      case OR:
      case EQ:
      case NEQ:
        return true;        
      default:
        Debug.error("Unknown MathOperation " + op + "!");
    };
    return false;
  }
  
  
  public boolean allValuesSame() {
    if (this.isEmpty()) {
      return true;
    }
    Object firstVal = this.getValue(0L);
    for ( Map.Entry< Parameter< Long >, ? > e : this.entrySet() ) {
      Object mapVal = this.getValue( e.getKey() );
      if (firstVal != mapVal && (firstVal == null || !firstVal.equals(mapVal))) {
        return false;
      }
    }
    return true;

  }
   
  /**
   * Return the result of applying a binary operation to this map and another.
   * This achieves for all {@code t} in {@code this.keySet()} and
   * {@code tvm.keySet()}, {@code newTvm.get(t) == this.get(t) OP tvm.get(t)}
   * for operation OP.
   *
   * @param map
   *          the map/timeline with which the operation is applied
   * @param op
   *          the binary operation
   * @return a copy of this map with the operation applied
   * @throws InstantiationException
   * @throws InvocationTargetException
   * @throws IllegalAccessException
   * @throws ClassCastException
   */
  public < VV, VVV > TimeVaryingMap< VVV > applyOperation( TimeVaryingMap< VV > map, MathOperation op ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Class<VVV> resultClass = (Class< VVV >)ClassUtils.dominantTypeClass( getType(), map.getType() );
    return applyOperation( map, op, resultClass );
  }
  public < VV, VVV > TimeVaryingMap< VVV > applyOperation( TimeVaryingMap< VV > map, MathOperation op, Class<VVV> resultType ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    //Class<VVV> cls = (Class< VVV >)ClassUtils.dominantTypeClass( getType(), map.getType() );
    TimeVaryingMap< VVV > newTvm = emptyClone(resultType);
    newTvm.setType( resultType );
    newTvm.clear();
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    if ( map != null ) {
      keys.addAll( map.keySet() );
    }
    for ( Parameter< Long> k : keys ) {
      V v1 = this.getValue( k );
      VV v2 = map == null ? null : map.getValue( k );
      Object v3 = applyOperation( v1, v2, op );
      VVV v4 = newTvm.tryCastValue( v3 );
      if ( v4 != null ) {
        newTvm.setValue( k, v4 );
      }
    }
    return newTvm;
  }
  
  
  public < VV, VVV > Object applyOperation( V v1, VV v2, MathOperation op ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Object v3 = null;
    switch ( op ) {
      case DIVIDE:
        v3 = Functions.divide( v1, v2 );
        break;
      case LOG:
        //v3 = Functions.log( v1, v2 );
        throw new IllegalArgumentException("log(TimeVaryingMap) not yet supported!");
        //break;
      case MAX:
        v3 = Functions.max( v1, v2 );
        break;
      case MIN:
        v3 = Functions.min( v1, v2 );
        break;
      case MINUS:
        v3 = Functions.minus( v1, v2 );
        break;
      case MOD:
        //v3 = Functions.mod( v1, v2 );
        throw new IllegalArgumentException("mod(TimeVaryingMap) not yet supported!");
        //break;
      case PLUS:
        v3 = Functions.plus( v1, v2 );
        break;
      case POW:
        v3 = Functions.pow( v1, v2 );
        break;
      case TIMES: 
        v3 = Functions.times( v1, v2 );
        break;
      case NEG: 
        v3 = Functions.negative( v1 );
        break;
      case AND:
        v3 = Functions.and( v1, v2 );
        break;
      case OR:
        throw new IllegalArgumentException( "or(V1,V1) not yet implemented!" );
        //v3 = Functions.or( v1, v2 );
        //break;
      case NOT:
        throw new IllegalArgumentException( "not(V1,V1) not yet implemented!" );
        //v3 = Functions.not( v1 );
        //break;
      case LT:
        v3 = Functions.lessThan( v1, v2 );
        break;
      case LTE:
        v3 = Functions.lessThanOrEqual( v1, v2 );
        break;
      case GT:
        throw new IllegalArgumentException( "greaterThan(V1,V1) not yet implemented!" );
        //v3 = Functions.greaterThan( v1, v2 );
        //break;
      case GTE:
        throw new IllegalArgumentException( "greaterThanOrEqual(V1,V1) not yet implemented!" );
        //v3 = Functions.greaterThanOrEqual( v1, v2 );
        //break;
      case EQ:
        throw new IllegalArgumentException( "equals(V1,V1) not yet implemented!" );
        //v3 = Functions.equals( v1, v2 );
        //break;
      case NEQ:
        throw new IllegalArgumentException( "notEquals(V1,V1) not yet implemented!" );
        //v3 = Functions.notEquals( v1, v2 );
        //break;
      default:
        throw new IllegalArgumentException("Unknown MathOperation " + op + "!");
    };
    // Applying the operation to a number and null (in that order) will return
    // the number. If the order of the operands is (null, number), then the
    // number is used only if the operation is symmetric. This behavior may be
    // interpreted as not modifying the existing timeline if the other operand
    // does not exist.
    // But this doesn't make sense when the interpolation is NONE, so nothing
    // is set in that case.
    if ( v3 == null && (interpolation != NONE || interpolation != NONE || op != MathOperation.TIMES ) ) {
      v3 = v1 != null ? v1 : (symmetric(op) ? v2 : null);
    }
    return v3;
//    VVV v4 = (VVV)(resultType == null ? v3 : resultType.cast( v3 ) );
//    return v4;
}
  
  /**
   * @param n the number by which this map is divided
   * @return this map after dividing each value by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> divide( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this;
    return divide( n, firstKey(), null );
  }



  public Call getCallForThisMethod(Object...args) {
    String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
    FunctionCall c = new FunctionCall( null, getClass(), methodName, args, (Class<?>)null );
    return c;
  }

  public static Call getCallForThisMethod( Class<?> cls, Object...args ) {
    String methodName = Thread.currentThread().getStackTrace()[2].getMethodName();
    if ( cls == null ) {
      String className = Thread.currentThread().getStackTrace()[2].getClassName();
      try {
        cls = Class.forName( className );
      } catch ( ClassNotFoundException e ) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    FunctionCall c = new FunctionCall( null, cls, methodName, args, (Class<?>)null );
    return c;
  }

  /**
   * @param n the number by which the map is divided
   * @return a copy of the map whose values are each divided by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public <TT> TimeVaryingMap<TT> dividedBy( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return (TimeVaryingMap< TT >)this.clone();
    Call c = getCallForThisMethod( n );
    if ( TimeVaryingMap.class.isAssignableFrom( getType() ) ) {
      TimeVaryingMap<TT> newTvm = (TimeVaryingMap<TT>)this.clone();
      newTvm.applyToSubMaps( c );
      return newTvm;
    }
    return (TimeVaryingMap<TT>)dividedBy( n, firstKey(), null );
  }

  /**
   * @param n the number by which the map is divided
   * @param fromKey
   *          the key from which all values are divided by {@code n}.
   * @return this map after divideing each value in the range [fromKey,
   *         toKey) by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > divide( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return divide( n, fromKey, null );
  }

  /**
   * @param n the number by which the map is divided
   * @param fromKey the first key whose value is divided by {@code n}
   * @param toKey is not divided.  To include the last key, pass {@code null} for {@code toKey}.
   * @return this map after dividing each value in the range [{@code fromKey}, {@code toKey})
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > divide( Number n, Parameter< Long > fromKey,
                                     Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
      map = subMap( fromKey, true, toKey, true );
    } else {
      boolean same = toKey.equals(fromKey);  // include the key if same
      map = subMap( fromKey, true, toKey, same );
    }
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      V v = Functions.divide(e.getValue(), n );
      e.setValue( v );
    }
    return this;
  }

  /**
   * Multiply this map with another. This achieves for all {@code t} in
   * {@code thisBefore.keySet()} and {@code tvm.keySet()},
   * {@code thisAfter.get(t) == thisBefore.get(t) * tvm.get(t)}.
   *
   * @param tvm
   *          the {@code TimeVaryingMap} with which this map is multiplied
   * @return this {@code TimeVaryingMap} after multiplying by {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public <VV> TimeVaryingMap< V > divide( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( tvm == null ) return null;
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    keys.addAll( tvm.keySet() );
    for ( Parameter< Long > k : keys ) {
      VV v = tvm.getValue( k, false );
      Number n = Expression.evaluate( v, Number.class, false );
      //if ( n != null ) {// n.doubleValue() != 0 ) {
      divide( n, k, k );
      //}
    }
    return removeDuplicates();
  }
  
  /**
   * @param n the number by which the map is divided
   * @param fromKey
   *          the key from which all values are divided by {@code n}.
   * @return a copy of this map for which each value in the range [{@code fromKey},
   *         {@code toKey}) is divided by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public <VV> TimeVaryingMap< VV > dividedBy( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return dividedBy( n, fromKey, null );
  }

  /**
   * @param n the number by which the map is divided
   * @param fromKey the first key whose value is divided by {@code n}
   * @param toKey
   *          is not divided. To include the last key, pass {@code null} for {@code toKey}.
   * @return a copy of this map for which each value in the range [fromKey,
   *         toKey) is divided by {@code n}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public <VV> TimeVaryingMap< VV > dividedBy( Number n, Parameter< Long > fromKey,
                                        Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    Class<VV> cls = (Class<VV>)ClassUtils.dominantTypeClass( getType(), n.getClass() );    
    //TimeVaryingMap< VV > newTvm = new TimeVaryingMap< VV >(getName() + "_div_" + n, this, cls); 
    TimeVaryingMap< VV > newTvm = clone(cls);
    newTvm.setName( getName() + "_div_" + n );
    newTvm.divide( n, fromKey, toKey );
    return newTvm;
  }

  /**
   * Return the quotient of this map with another. This achieves for all
   * {@code t} in {@code this.keySet()} and {@code tvm.keySet()},
   * {@code newTvm.get(t) == this.get(t) / tvm.get(t)}.
   *
   * @param tvm
   *          the map by which this map is divided
   * @return a copy of this map multiplied by {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public < VV extends Number > TimeVaryingMap< V > dividedByOld( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    TimeVaryingMap< V > newTvm = this.clone();
    newTvm.divide( tvm );
    return newTvm;
  }
  
  public <VV> TimeVaryingMap< V > dividedBy( TimeVaryingMap< VV > map ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return applyOperation( map, MathOperation.DIVIDE );
//    TimeVaryingMap< V > newTvm = emptyClone();
//    newTvm.clear();
//    Set< Parameter< Long > > keys =
//        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
//    keys.addAll( this.keySet() );
//    keys.addAll( map.keySet() );
//    for ( Parameter< Long> k : keys ) {
//      V v1 = this.getValue( k );
//      VV v2 = map.getValue( k );
//      Object v3 = Functions.divide( v1, v2 );
//      // Multiplying a number and null will return the number.
//      // But this doesn't make sense when the interpolation is NONE.
//      if ( v3 == null && interpolation != NONE ) {
//        v3 = v1 != null ? v1 : v2;
//      }
//      V v4 = tryCastValue( v3 );
//      if ( v4 != null ) {
//        newTvm.setValue( k, v4 );
//      }
//    }
//    return newTvm;
  }



  /**
   * Return the quotient of two maps. This achieves for all
   * {@code t} in {@code tvm1.keySet()} and {@code tvm2.keySet()},
   * {@code newTvm.get(t) == tvm1.get(t) / tvm2.get(t)}.
   *
   * @param tvm1
   * @param tvm1
   * @return a copy of {@code tvm1} divided by {@code tvm2}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public static < VV1, VV2 > TimeVaryingMap< VV1 > dividedBy( TimeVaryingMap< VV1 > tvm1,
                                                                             TimeVaryingMap< VV2 > tvm2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return tvm1.dividedBy( tvm2 );
  }


  public static < VV1 extends Number, VV2, VV3 > TimeVaryingMap< VV3 > dividedBy( VV1 o1,
                                                                             TimeVaryingMap< VV2 > tvm2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( o1 == null ) return null;
    TimeVaryingMap<VV3> tvm1 = (TimeVaryingMap< VV3 >)tvm2.emptyClone();
    tvm1.setValue( new SimpleTimepoint( "", 0L, tvm1 ), (VV3)o1 );
    return dividedBy(tvm1, tvm2);
  }


  /**
   * @param n
   * @return a this map after adding {@code n} to each value
   */
  public TimeVaryingMap<V> add( Number n ) {
    if ( isEmpty() ) return this;
    return add( n, firstKey(), null );
  }

  /**
   * @param n
   * @return a copy of this map for which {@code n} is added to each value
   */
  public TimeVaryingMap<V> plus( Number n ) {
    if ( isEmpty() ) return this.clone();
    return plus( n, firstKey(), null );
  }

  /**
   * @param n
   * @return a copy of this map for which {@code n} is min'd with each value
   */
  public TimeVaryingMap<V> minClone( Number n ) {
    if ( isEmpty() ) return this.clone();
    return minClone( n, firstKey(), null );
  }

  /**
   * @param n
   * @return a copy of this map for which {@code n} is max'd with each value
   */
  public TimeVaryingMap<V> maxClone( Number n ) {
    if ( isEmpty() ) return this.clone();
    return maxClone( n, firstKey(), null );
  }

//  /**
//   * @param n
//   * @return a copy of this map for which {@code n} is lessThan'd with each value
//   */
//  public TimeVaryingMap<V> lessThanClone( Number n ) {
//    if ( isEmpty() ) return this.clone();
//    return lessThanClone( n, firstKey(), null );
//  }
//
//  /**
//   * @param n
//   * @return a copy of this map for which {@code n} is lessThanOrEqual'd with each value
//   */
//  public TimeVaryingMap<V> lessThanOrEqualClone( Number n ) {
//    if ( isEmpty() ) return this.clone();
//    return lessThanOrEqualClone( n, firstKey(), null );
//  }

  public boolean contains( Long t ) {
    return getKey( t ) != null;
  }

  @Override
  public boolean containsKey(Object key) {
    if ( key instanceof Parameter) {
      Object v = ( (Parameter<?>)key ).getValueNoPropagate();
      if ( v == null || v instanceof Long ) {
        return super.containsKey( key );
      }
    }
    return false;
  }

  
  /**
   * Gets the first key in the map equal the input.  
   * @param t
   * @return
   */
  public Parameter< Long> getKey( Long t ) {
    if ( isEmpty() ) return null;
    Parameter< Long > tt = makeTempTimepoint( t, false );
    return getKey( tt, true );
//    Parameter< Long > bk = ceilingKey(tt);
//    if ( bk != null  && bk.getValueNoPropagate().equals( t ) ) {
//      return bk;
//    }
//    bk = lowerKey(tt);
//    if ( bk != null  && bk.getValueNoPropagate().equals( t ) ) {
//      return bk;
//    }
//    return null;
  }


  /**
   * Returns a view of the portion of this map whose keys range from
   * {@code timeFrom} to {@code timeTo}.  If {@code timeFrom} and
   * {@code timeTo} are equal, the returned map is empty unless
   * {@code fromInclusive} and {@code toInclusive} are both false.  The
   * returned map is backed by this map, so changes in the returned map are
   * reflected in this map, and vice-versa.  The returned map supports all
   * optional map operations that this map supports.
   *
   * <p>The returned map will throw an {@code IllegalArgumentException}
   * on an attempt to insert a key outside of its range, or to construct a
   * submap either of whose endpoints lie outside its range.
   *
   * @param timeFrom lower bound timepoint in the returned map; null means the first key
   * @param fromInclusive {@code true} if the low endpoint
   *        is to be included in the returned view
   * @param timeTo upper bound timepoint in the returned map; null means the last key
   * @param toInclusive {@code true} if the high endpoint
   *        is to be included in the returned view
   * @return a view of the portion of this map whose keys range from
   *         {@code timeFrom} to {@code timeTo}
   * @throws ClassCastException if {@code timeFrom} and {@code timeTo}
   *         cannot be compared to one another using this map's comparator
   *         (or, if the map has no comparator, using natural ordering).
   *         Implementations may, but are not required to, throw this
   *         exception if {@code timeFrom} or {@code timeTo}
   *         cannot be compared to keys currently in the map.
   */
  public NavigableMap< Parameter< Long >, V > subMap( Long timeFrom,
                                                         boolean fromInclusive,
                                                         Long timeTo,
                                                         boolean toInclusive ) {
    if ( isEmpty() ) return this;

    // Interpret null as the first and last key.
    if ( timeFrom == null ) timeFrom = firstKey().getValue();
    if ( timeTo == null ) timeTo = lastKey().getValue();

    // Find keys (tpe and tpl) that can be passed into an equivalent call, subMap(tpe, true, tpl, true)
    Parameter< Long> tpe = getKey( timeFrom );  // warning! This assumes getKey(timeFrom) gets the first key.
    if ( tpe != null ) {
      if ( !fromInclusive ) {
        tpe = getTimepointLater( tpe );   // warning! This assumes getTimepointLater( tpe ) gets the first key.
      }
    } else {
      tpe = getTimepointAfter(timeFrom);
    }

    //Parameter< Long> tpl = toInclusive ? getTimepointLater( timeTo ) : getTimepointEarlier( timeTo );
    Parameter< Long> tpl = null;
    if ( toInclusive ) {
      tpl = getTimepointLater( timeTo );  // warning! This assumes getTimepointLater( timeTo ) gets the first key.
      if ( tpl != null ) {
        tpl = getTimepointBefore( tpl );
      } else {
        tpl = lastKey();
      }
    } else {
      tpl = getTimepointBefore( timeTo );
    }

    // Empty set and error cases.
    if ( tpe == null || tpl == null || tpe.getValue() == null
         || tpl.getValue() == null || tpe.getValue() > tpl.getValue() ) {  // assumes that tpe <= tpl if tpe.getValue() == tpl.getValue()
      Parameter< Long > f = firstKey();
      return this.subMap( f, false, f, false );
    }
    
    return this.subMap( tpe, true, tpl, true );
  }

  public NavigableMap< Parameter< Long >, V > subMapBad( Long timeFrom,
                                                      boolean fromInclusive,
                                                      Long timeTo,
                                                      boolean toInclusive ) {
 if ( isEmpty() ) return this;
 Parameter< Long> tpe = getTimepointEarlier( timeFrom );
 Parameter< Long > f = firstKey();
 if ( tpe == null ) {
   tpe = f;
   fromInclusive = true;
 }
 Parameter< Long> tpl = getTimepointLater( timeTo );
 if ( tpl == null ) {
   tpl = lastKey();
   toInclusive = true;
 }
 int c = compareTo( tpe, tpl );

 if ( c > 0 ) return this.subMap( f, false, f, false );
 if ( c == 0 ) {
   if ( tpe.valueEquals( timeFrom ) ) {
     return subMap( f, true, f, true );
   }
   return this.subMap( f, false, f, false );
 }
 return subMap(tpe, fromInclusive, tpl, toInclusive);
}
  
  /**
   * @param tt
   *          the timepoint to which a key is sought to match
   * @return all keys in the map that match the input timepoint {@code tt}.
   */
  public Set< Parameter< Long > > getKeys( Parameter< Long> tt ) {
    return getKeys( tt.getValueNoPropagate() );
  }
  /**
   * @param t
   *          the timepoint to which a key is sought to match
   * @return all keys in the map that match the input time value {@code t}.
   */
  public Set< Parameter< Long > > getKeys( Long t ) {
    return subMap( t, true, t, true ).keySet();
/*
  if ( isEmpty() ) return Collections.emptySet();
    Parameter< Long> tpe = getTimepointEarlier( t );
    boolean includeFrom = ( tpe == null );
    if ( includeFrom ) {
      tpe = firstKey();
    }
    Parameter< Long> tpl = getTimepointLater( t );
    boolean includeTo = ( tpl == null );
    if ( includeTo ) {
      tpl = lastKey();
    }
    int c = compareTo( tpe, tpl );
    if ( c > 0 ) return Collections.emptySet();
    if ( c == 0 ) {
      if ( tpe.valueEquals( t ) ) {
        Set< Parameter< Long >> s = new LinkedHashSet< Parameter< Long >>();
        s.add( tpe );
        return s;
      }
      return Collections.emptySet();
    }
    return subMap(tpe, includeFrom, tpl, includeTo).keySet();
*/
  }
  /**
   * @param tt
   *          the timepoint to which a key is sought to match
   * @param equalValuesOk
   *          specifies whether {@code key.equals(tt)} must be true for the
   *          returned {@code key} or if matching {@code Long} values are
   *          good enough.
   * @param firstOrLast if true, the first of the set of matching keys is returned; else, the last of the set is returned.
   * @return the key in the map that matches the input timepoint {@code tt}.
   */
  public Parameter< Long> getKey( Parameter< Long > tt, boolean equalValuesOk,
                                    boolean firstOrLast ) {
    Long v = tt.getValue(false);
    NavigableMap< Parameter< Long >, V > m = subMap( v, true, v, true );
    if ( m.isEmpty() ) return null;
    Parameter< Long > timepoint = firstOrLast ? m.firstKey() : m.lastKey();
    return timepoint;
  }
  /**
   * @param tt
   *          the timepoint to which a key is sought to match
   * @param equalValuesOk
   *          specifies whether {@code key.equals(tt)} must be true for the
   *          returned {@code key} or if matching {@code Long} values are
   *          good enough.
   * @return the key in the map that matches the input timepoint {@code tt}.
   */
  public Parameter< Long> getKey( Parameter< Long > tt, boolean equalValuesOk ) {
    if ( tt == null ) return null;
    NavigableMap< Parameter< Long >, V > m = headMap( tt, true );

    Parameter< Long > k;
    if ( !m.isEmpty() ) {
      k = m.lastKey();
      if ( k != null && k.getValue( false ) == null ) {
        Debug.error( true, "Error! Found null key "
                           + MoreToString.Helper.toLongString( k )
                           + " in TimeVaryingMap: "
                           + MoreToString.Helper.toLongString( this ) );
      }
      if ( k == tt || ( equalValuesOk && k.valueEquals( tt ) ) ) return k;
    }

    if ( !equalValuesOk ) return null;
    Parameter< Long > bk = getTimepointAfter(tt);
    if ( bk != null && bk.equals( tt ) ) {
      return bk;
    }
//    bk = getTimepointBefore( tt );
//    if ( bk != null && bk.equals( tt ) ) {
//      return bk;
//    }
    return null;

//    Parameter< Long > bk = lowerKey(tt);
//    if ( k == null )
//    bk = ceilingKey(tt);
//    if ( bk != null && bk.equals( tt ) ) {
//      return bk;
//    }
//    return null;
  }

  /**
   * If not already in the map, insert the timepoint, {@code key} (or a clone of
   * {@code key} if owned by another object), along with the value of this map
   * at that timepoint. If {@code onlyIfDifferentValue} is true, then
   * {@code key} is considered in the map if a key with the same {@code Long}
   * value exists.
   *
   * @param key
   * @param onlyIfDifferentValue
   * @return the inserted key or the existing key if already in the map
   */
  public Parameter< Long > putKey( Parameter< Long > key,
                                      boolean onlyIfDifferentValue ) {

    if ( key == null ) return null;
    Parameter< Long > k = getKey( key, onlyIfDifferentValue );
    if ( k != null ) return k;
//    if ( key.getOwner() != this ) {
//      if ( key.getOwner() != null ) {
//        key = (Parameter< Long >)key.clone();
//      }
//      key.setOwner( this );
//    }
    put( key, getValue( key, onlyIfDifferentValue ) );
    return key;
  }

  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not added {@code n}. To
   *          include the last key, pass {@code null} for {@code toKey}.
   * @return this map after adding {@code n} to each value in the range [{@code fromKey},
   *         {@code toKey})
   */
  public TimeVaryingMap< V > add1( Number n, Parameter< Long > fromKey,
                                   Parameter< Long > toKey ) {
//    if ( fromKey.getValueNoPropagate() == 10 ) {
//      Debug.outln("");
//    }
    fromKey = putKey( fromKey, true );
    toKey = putKey( toKey, true );
    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
      if ( before( toKey, fromKey ) ) toKey = fromKey;
      map = subMap( fromKey, true, toKey, true );
    } else {
      boolean same = toKey.equals(fromKey);  // include the key if same
      map = subMap( fromKey, true, toKey, same );
    }
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      if ( e.getValue() instanceof Double ) {
        Double v = (Double) e.getValue();
        v = v + n.doubleValue();
        try {
          e.setValue(tryCastValue(v));
        } catch (Exception exc) {
          // ignore
        }
      } else if ( e.getValue() instanceof Float ) {
          Float v = (Float)e.getValue();
          v = v + n.floatValue();
          try {
            e.setValue( tryCastValue( v ) );
          } catch ( Exception exc ) {
            // ignore
          }
      } else if ( e.getValue() instanceof Long ) {
        Long v = (Long)e.getValue();
        // TODO -- handle Long?
        v = (long)( v + n.doubleValue() );
        try {
          e.setValue( tryCastValue( v ) );
        } catch ( Exception exc ) {
          // ignore
        }
      } else if ( e.getValue() instanceof Integer ) {
        Integer v = (Integer)e.getValue();
        // TODO -- handle Long?
        v = (int)( v + n.doubleValue() );
        try {
          e.setValue( tryCastValue( v ) );
        } catch ( Exception exc ) {
          // ignore
        }
      }
    }
    return this;
  }

  /**
   * @param n
   * @param fromKey
   *          the key from which {@code n} is added to all values.
   * @return this map after adding {@code n} to each value in the range [{@code fromKey},
   *         {@code toKey})
   */
  public TimeVaryingMap< V > add( Number n, Parameter< Long > fromKey ) {
    return add( n, fromKey, null );
  }

  public V integral(Parameter< Long > fromKey, Parameter< Long > toKey) {
    TimeVaryingMap< V > tvm = integrate(fromKey, toKey, null);
    if ( tvm == null || tvm.isEmpty() ) return tryCastValue( 0 );
    return tvm.lastValue();
  }
  
  public V lastValue() {
    return getValue( lastKey() );
  }
 
  public TimeVaryingMap< V > integrate(Parameter< Long > fromKey,
                                       Parameter< Long > toKey) {
    return integrate( fromKey, toKey, null );
  }
  public TimeVaryingMap< V > integrate(Parameter< Long > fromKey,
                                       Parameter< Long > toKey, TimeVaryingMap< V > tvm ) {
    if ( tvm == null ) tvm = new TimeVaryingMap< V >( this.name + "Integral", this.type );
    if ( this.interpolation.type == Interpolation.STEP ) {
      tvm.interpolation.type = Interpolation.LINEAR;
    } else {
      Debug.error( true, "No support yet for quadratic interpolation as integral of linear function!" );
    }
    
    boolean same = toKey == fromKey;  // include the key if same
    Parameter< Long > insertedFromKey = null;
    Parameter< Long > insertedToKey = null;
    if ( !containsKey( fromKey ) ) {
      fromKey = putKey( fromKey, false );
      insertedFromKey = fromKey;
    }
    if ( same ) {
      toKey = fromKey;
    } else {
      if ( !containsKey( toKey ) ) {
        toKey = putKey( toKey, false );
        insertedToKey = toKey;
      }
    }
    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
    }
    if ( before( toKey, fromKey ) ) toKey = fromKey;
    map = subMap( fromKey, true, toKey, true );
    boolean succeededSomewhere = false;
    Map.Entry< Parameter< Long >, V > ePrev = null;
    V lastIntegralValue = tryCastValue( 0 );
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      V integralValue = null;
      Long timeDiff = 0L;
      if ( ePrev != null && ePrev.getKey() != null ) {
        Long v2 = e.getKey().getValueNoPropagate();
        Long v1 = ePrev.getKey().getValueNoPropagate();
        if ( v2 != null && v1 != null ) {
          timeDiff = v2 - v1;
        }
      }
      try {
          if ( ePrev == null ) {
            integralValue = tryCastValue( (Double)0.0 );
          } else {
            V endValue = ePrev.getValue();
            if ( this.interpolation.isLinear() ) {
              endValue = e.getValue();
            }
            if ( lastIntegralValue == null || endValue == null || ePrev.getValue() == null ) {
              integralValue = null;
            } else {
              try {
                integralValue =
                    tryCastValue( Functions.plus( lastIntegralValue, 
                                  Functions.times( Functions.divide( Functions.plus(endValue, ePrev.getValue()), 2 ),
                                  timeDiff ) ) );
              } catch ( IllegalAccessException e1 ) {
                e1.printStackTrace();
                return null;
              } catch ( InvocationTargetException e1 ) {
                e1.printStackTrace();
                return null;
              } catch ( InstantiationException e1 ) {
                e1.printStackTrace();
                return null;
              }
            }
            succeededSomewhere = integralValue != null;
          }
      } catch ( ClassCastException exc ) {
        exc.printStackTrace();
      }
      if ( integralValue != null ) {
        tvm.put( e.getKey(), integralValue );
        lastIntegralValue = integralValue;
      }
      ePrev = e;
    }
    if ( insertedFromKey != null ) {
      remove(insertedFromKey);
    }
    if ( insertedToKey != null ) {
      remove(insertedToKey);
    }
    //if (succeededSomewhere) appliedSet.add(  )
    return tvm;
  }
  
  public TimeVaryingMap< V > integrate() {
    return integrate(new SimpleTimepoint("",0L, this),
                     new SimpleTimepoint("", Timepoint.horizonDuration, this));
  }
    
  
  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not added {@code n}. To
   *          include the last key, pass {@code null} for {@code toKey}.  null values are
   *          treated as zero when adding with a non-null.
   * @return this map after adding {@code n} to each value in the range [{@code fromKey},
   *         {@code toKey})
   */
  public TimeVaryingMap< V > add( Number n, Parameter< Long > fromKey,
                                  Parameter< Long > toKey ) {

    //if ( n == null ) return; //REVIEW
    if ( Debug.isOn() ) System.out.println( getQualifiedName(null) + ".add(" + n + ", " + fromKey + ", " + toKey + ")" );
    if ( fromKey != null && fromKey.getValueNoPropagate() == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Error! trying to insert a null Parameter< Long> value into the map!" );
      return null;
    }
    if ( toKey != null && toKey.getValueNoPropagate() == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Error! trying to insert a null Parameter< Long> value into the map!" );
      return null;
    }
    if ( fromKey == null ) {
      if ( Debug.isOn() ) Debug.error( false, "Error! trying to insert a null Parameter< Long> into the map!" );
      return null;
    }

    boolean same = toKey == fromKey;  // include the key if same
    fromKey = putKey( fromKey, false );
    if ( same ) {
      toKey = fromKey;
    } else {
      toKey = putKey( toKey, false );
    }
    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
      if ( before( toKey, fromKey ) ) toKey = fromKey;
      map = subMap( fromKey, true, toKey, true );
    } else {
      map = subMap( fromKey, true, toKey, same );
    }
    boolean succeededSomewhere = false;
    if ( !map.isEmpty() ) {
      setStaleAnyReferencesToTimeVarying();
    }
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      if ( e.getValue() == null ) {
        try {
          e.setValue( tryCastValue( n ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Double ) {
        Double v = (Double)e.getValue();
        if ( n == null ) n = 0.0;
        v = v + n.doubleValue();
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Float ) {
        Float v = (Float)e.getValue();
        if ( n == null ) n = 0.0;
        v = v + n.floatValue();
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Long ) {
        Long v = (Long)e.getValue();
        if ( n == null ) n = 0.0;
        v = (long)( v + n.doubleValue() );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Integer ) {
        Integer v = (Integer)e.getValue();
        if ( n == null ) n = 0.0;
        v = (int)( v + n.doubleValue() );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      }
    }
    if ( succeededSomewhere ) {
      // TODO -- REVIEW -- Is it okay to do this after updating the map instead
      // of before?
      handleChangeToTimeVaryingMap();
    }
    //if (succeededSomewhere) appliedSet.add(  )
    return this;
  }

  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not min'd {@code n}. To
   *          include the last key, pass {@code null} for {@code toKey}.  null values are
   *          treated as zero when min'ing with a non-null.
   * @return this map after min'ing {@code n} with each value in the range [{@code fromKey},
   *         {@code toKey})
   */
  public TimeVaryingMap< V > min( Number n, Parameter< Long > fromKey,
                                  Parameter< Long > toKey ) {

    //if ( n == null ) return; //REVIEW
    boolean same = toKey == fromKey;  // include the key if same
    fromKey = putKey( fromKey, false );
    if ( same ) {
      toKey = fromKey;
    } else {
      toKey = putKey( toKey, false );
    }
    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
      if ( before( toKey, fromKey ) ) toKey = fromKey;
      map = subMap( fromKey, true, toKey, true );
    } else {
      map = subMap( fromKey, true, toKey, same );
    }
    boolean succeededSomewhere = false;
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      if ( e.getValue() == null ) {
        try {
          e.setValue( tryCastValue( n ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Double ) {
        Double v = (Double)e.getValue();
        if ( n == null ) n = 0.0;
        v = Math.min(v, n.doubleValue() );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Float ) {
        Float v = (Float)e.getValue();
        if ( n == null ) n = 0.0;
        v = Math.min( v, n.floatValue() );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Long ) {
        Long v = (Long)e.getValue();
        if ( n == null ) n = 0.0;
        v = (long)( Math.min( v, n.doubleValue() ) );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Integer ) {
        Integer v = (Integer)e.getValue();
        if ( n == null ) n = 0.0;
        v = (int)( Math.min(v, n.doubleValue() ) );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      }
    }
    //if (succeededSomewhere) appliedSet.add(  )
    return this;
  }

  /**
   * @param tvm the {@code TimeVaryingMap} to be min'd with this {@code TimeVaryingMap}
   * @return this {@code TimeVaryingMap} after min'ing {@code tvm}
   */
  public <VV> TimeVaryingMap< V > min( TimeVaryingMap< VV > tvm ) {
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    keys.addAll( tvm.keySet() );
    for ( Parameter< Long > k : keys ) {
      VV v = tvm.getValue( k, false );
      Number n;
      try {
        n = Expression.evaluate( v, Number.class, false );
        min( n, k, k );
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
    return removeDuplicates();
  }

  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not max'd {@code n}. To
   *          include the last key, pass {@code null} for {@code toKey}.  null values are
   *          treated as zero when maxing with a non-null.
   * @return this map after maxing {@code n} with each value in the range [{@code fromKey},
   *         {@code toKey})
   */
  public TimeVaryingMap< V > max( Number n, Parameter< Long > fromKey,
                                  Parameter< Long > toKey ) {

    //if ( n == null ) return; //REVIEW
    boolean same = toKey == fromKey;  // include the key if same
    fromKey = putKey( fromKey, false );
    if ( same ) {
      toKey = fromKey;
    } else {
      toKey = putKey( toKey, false );
    }
    Map< Parameter< Long >, V > map = null;
    if ( toKey == null ) {
      toKey = lastKey();
      if ( before( toKey, fromKey ) ) toKey = fromKey;
      map = subMap( fromKey, true, toKey, true );
    } else {
      map = subMap( fromKey, true, toKey, same );
    }
    boolean succeededSomewhere = false;
    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
      if ( e.getValue() == null ) {
        try {
          e.setValue( tryCastValue( n ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Double ) {
        Double v = (Double)e.getValue();
        if ( n == null ) n = 0.0;
        v = Math.max(v, n.doubleValue() );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Float ) {
        Float v = (Float)e.getValue();
        if ( n == null ) n = 0.0;
        v = Math.max( v, n.floatValue() );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Long ) {
        Long v = (Long)e.getValue();
        if ( n == null ) n = 0.0;
        v = (long)( Math.max( v, n.doubleValue() ) );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      } else if ( e.getValue() instanceof Integer ) {
        Integer v = (Integer)e.getValue();
        if ( n == null ) n = 0.0;
        v = (int)( Math.max(v, n.doubleValue() ) );
        try {
          e.setValue( tryCastValue( v ) );
          succeededSomewhere = true;
        } catch ( ClassCastException exc ) {
          exc.printStackTrace();
        }
      }
    }
    //if (succeededSomewhere) appliedSet.add(  )
    return this;
  }

  /**
   * @param tvm the {@code TimeVaryingMap} to be max'd to this {@code TimeVaryingMap}
   * @return this {@code TimeVaryingMap} after maxing {@code tvm}
   */
  public <VV> TimeVaryingMap< V > max( TimeVaryingMap< VV > tvm ) {
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    keys.addAll( tvm.keySet() );
    for ( Parameter< Long > k : keys ) {
      VV v = tvm.getValue( k, false );
      Number n;
      try {
        n = Expression.evaluate( v, Number.class, false );
        max( n, k, k );
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
    return removeDuplicates();
  }

//  /**
//   * @param n
//   * @param fromKey
//   * @param toKey
//   *          the first key after {@code fromKey} to whose value is not max'd {@code n}. To
//   *          include the last key, pass {@code null} for {@code toKey}.  null values are
//   *          treated as zero when maxing with a non-null.
//   * @return this map after maxing {@code n} with each value in the range [{@code fromKey},
//   *         {@code toKey})
//   */
//  public TimeVaryingMap< Boolean > lessThan( Number n, Parameter< Long > fromKey,
//                                             Parameter< Long > toKey ) {
//
//    //if ( n == null ) return; //REVIEW
//    boolean same = toKey == fromKey;  // include the key if same
//    fromKey = putKey( fromKey, false );
//    if ( same ) {
//      toKey = fromKey;
//    } else {
//      toKey = putKey( toKey, false );
//    }
//    Map< Parameter< Long >, V > map = null;
//    if ( toKey == null ) {
//      toKey = lastKey();
//      if ( before( toKey, fromKey ) ) toKey = fromKey;
//      map = subMap( fromKey, true, toKey, true );
//    } else {
//      map = subMap( fromKey, true, toKey, same );
//    }
//    boolean succeededSomewhere = false;
//    for ( Map.Entry< Parameter< Long >, V > e : map.entrySet() ) {
//      if ( e.getValue() == null ) {
//        try {
//          e.setValue( tryCastValue( n ) );
//          succeededSomewhere = true;
//        } catch ( ClassCastException exc ) {
//          exc.printStackTrace();
//        }
//      } else if ( e.getValue() instanceof Double ) {
//        Double v = (Double)e.getValue();
//        if ( n == null ) n = 0.0;
//        v = Math.max(v, n.doubleValue() );
//        try {
//          e.setValue( tryCastValue( v ) );
//          succeededSomewhere = true;
//        } catch ( ClassCastException exc ) {
//          exc.printStackTrace();
//        }
//      } else if ( e.getValue() instanceof Float ) {
//        Float v = (Float)e.getValue();
//        if ( n == null ) n = 0.0;
//        v = Math.max( v, n.floatValue() );
//        try {
//          e.setValue( tryCastValue( v ) );
//          succeededSomewhere = true;
//        } catch ( ClassCastException exc ) {
//          exc.printStackTrace();
//        }
//      } else if ( e.getValue() instanceof Integer ) {
//        Integer v = (Integer)e.getValue();
//        if ( n == null ) n = 0.0;
//        v = (int)( Math.max(v, n.doubleValue() ) );
//        try {
//          e.setValue( tryCastValue( v ) );
//          succeededSomewhere = true;
//        } catch ( ClassCastException exc ) {
//          exc.printStackTrace();
//        }
//      } else if ( e.getValue() instanceof Long ) {
//        Long v = (Long)e.getValue();
//        if ( n == null ) n = 0.0;
//        v = (long)( Math.max( v, n.doubleValue() ) );
//        try {
//          e.setValue( tryCastValue( v ) );
//          succeededSomewhere = true;
//        } catch ( ClassCastException exc ) {
//          exc.printStackTrace();
//        }
//      }
//    }
//    //if (succeededSomewhere) appliedSet.add(  )
//    return this;
//  }
//
//  /**
//   * @param tvm the {@code TimeVaryingMap} to be max'd to this {@code TimeVaryingMap}
//   * @return this {@code TimeVaryingMap} after maxing {@code tvm}
//   */
//  public <VV> TimeVaryingMap< V > lessThan( TimeVaryingMap< VV > tvm ) {
//    Set< Parameter< Long > > keys =
//        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
//    keys.addAll( this.keySet() );
//    keys.addAll( tvm.keySet() );
//    for ( Parameter< Long > k : keys ) {
//      VV v = tvm.getValue( k, false );
//      Number n;
//      try {
//        n = Expression.evaluate( v, Number.class, false );
//        max( n, k, k );
//      } catch ( ClassCastException e ) {
//        // TODO Auto-generated catch block
//        e.printStackTrace();
//      } catch ( IllegalAccessException e ) {
//        // TODO Auto-generated catch block
//        e.printStackTrace();
//      } catch ( InvocationTargetException e ) {
//        // TODO Auto-generated catch block
//        e.printStackTrace();
//      } catch ( InstantiationException e ) {
//        // TODO Auto-generated catch block
//        e.printStackTrace();
//      }
//    }
//    return removeDuplicates();
//  }
//

  /**
   * @param tvm the {@code TimeVaryingMap} to be added to this {@code TimeVaryingMap}
   * @return this {@code TimeVaryingMap} after adding {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public <VV> TimeVaryingMap< V > add( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    for ( Parameter< Long >  k : tvm.keySet() ) {
      putKey( k, true );
    }
    for ( Parameter< Long > k : keySet() ) {
      VV v = tvm.getValue( k, false );
      Number n = Expression.evaluate( v, Number.class, false );
      add( n, k, k );
    }
    return removeDuplicates();
  }

  /**
   * @param n
   * @return this map after subtracting {@code n} from each value
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> subtract( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this;
    return subtract( n, firstKey(), null );
  }

  /**
   * @param n
   * @param fromKey
   *          the key from which {@code n} is subtracted from all values.
   * @return this map after subtracting {@code n} from each value in the range [{@code fromKey},
   *         {@code toKey})
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > subtract( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return subtract( n, fromKey, null );
  }

  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not subtracted by {@code n}.
   *          To include the last key, pass {@code null} for {@code toKey}.
   * @return this map after subtracting {@code n} from each value in the range [{@code fromKey},
   *         {@code toKey})
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > subtract( Number n, Parameter< Long > fromKey,
                                       Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return add( Functions.times(n, -1), fromKey, toKey );
  }

  /**
   * @param tvm
   *          the {@code TimeVaryingMap} to be subtracted from this
   *          {@code TimeVaryingMap}
   * @return this {@code TimeVaryingMap} after subtracting {@code tvm}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public <VV> TimeVaryingMap< V > subtract( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( tvm == null ) return null;
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    keys.addAll( tvm.keySet() );
    //System.out.println( "this - tvm -> this\n" + this.toString( true, false, null ) + "\n + " + tvm.toString( true, false, null ) );
    for ( Parameter< Long > k : keys ) {
      VV v = tvm.getValue( k, false );
      Number n = Expression.evaluate( v, Number.class, false );
      //if ( n != null ) {// n.doubleValue() != 0 ) {
      subtract( n, k, k );
      //}
    }
    //System.out.println( " = " + this.toString( true, false, null ) );
    removeDuplicates();
    //System.out.println( " = " + this.toString( true, false, null ) );
    return this;
  }

  /**
   * @return this map after removing all but the first entry from each set of adjacent
   *         entries with the same values.
   */
  public <VV> TimeVaryingMap< V > removeDuplicates() {
    if ( Debug.isOn() ) Debug.outln( "before removing duplicates " + this );
    List<Parameter< Long > > dups = new ArrayList< Parameter< Long > >();
    Parameter< Long> lastKey = null;
    Parameter< Long> firstKeyOfSameValue = null;
    V lastValue = null;
    // Need to keep first and last--remove only ones in between, so use a
    // counter to skip adding the first.
    int ct = 0;  
    for ( java.util.Map.Entry< Parameter< Long >, V > e : entrySet() ) {
      Parameter< Long > key = e.getKey();
      V value = e.getValue();
      if ( Utils.valuesLooselyEqual( lastKey, key, true, false ) ) {
        if ( Utils.valuesEqual( lastValue, value ) ) {
          dups.add( lastKey );
          if ( firstKeyOfSameValue == lastKey ) {
            firstKeyOfSameValue = key;
          }
        }
      } else if ( Utils.valuesEqual( lastValue, value ) ) {
////        if ( ct > 0 ) {
////          dups.add( key );
////          key = lastKey;
////        }
////        ++ct;
//        dups.add( key );
//        key = lastKey;
      } else {
        firstKeyOfSameValue = key;
      }
      if ( firstKeyOfSameValue == null ) {
        firstKeyOfSameValue = key;
      }

        //      } else {
//        ct = 0;
//      }
      lastKey = key;
      lastValue = value;
    }
    for ( Parameter< Long> k : dups ) {
      remove( k );
    }
    if ( Debug.isOn() ) Debug.outln( " after removing duplicates " + this );
     return this;
  }

  
  /**
   * @param n
   * @param fromKey
   *          the key from which {@code n} is added to all values.
   * @return a copy of this map for which {@code n} is added to each value in the range
   *         [{@code fromKey}, {@code toKey})
   */
  public TimeVaryingMap< V > plus( Number n, Parameter< Long > fromKey ) {
    return plus( n, fromKey, null );
  }
  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not added {@code n}. To
   *          include the last key, pass {@code null} for {@code toKey}.
   * @return a copy of this map for which {@code n} is added to each value in the range
   *         [{@code fromKey}, {@code toKey})
   */
  public <VV> TimeVaryingMap< VV > plus( Number n, Parameter< Long > fromKey,
                                    Parameter< Long > toKey ) {
    Class<VV> cls = (Class< VV >)ClassUtils.dominantTypeClass( getType(), n.getClass() );
    //TimeVaryingMap< VV > newTvm = new TimeVaryingMap<VV>(getName() + "_plus_" + n, this, cls);//this.clone();
    TimeVaryingMap< VV > newTvm = clone(cls);
    newTvm.setName( getName() + "_plus_" + n );
    newTvm.add( n, fromKey, toKey );
    return newTvm;
  }

  /**
   * @param map
   * @return a copy of this TimeVaryingMap with {@code map} added to it
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public <VV extends Number> TimeVaryingMap< V > plusOld( TimeVaryingMap< VV > map ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    TimeVaryingMap< V > newTvm = this.clone();
    newTvm.add( map );
    return newTvm;
  }

  public <VV extends Number> TimeVaryingMap< V > plus( TimeVaryingMap< VV > map ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return applyOperation( map, MathOperation.PLUS );
//    TimeVaryingMap< V > newTvm = emptyClone();
//    newTvm.clear();
//    Set< Parameter< Long > > keys =
//        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
//    keys.addAll( this.keySet() );
//    keys.addAll( map.keySet() );
//    for ( Parameter< Long> k : keys ) {
//      V v1 = this.getValue( k );
//      VV v2 = map.getValue( k );
//      Object v3 = Functions.plus( v1, v2 );
//      // Adding null to a number should return the number. This is important
//      // when interpolation is NONE.
//      if ( v3 == null ) {
//        v3 = v1 != null ? v1 : v2; 
//      }
//      V v4 = tryCastValue( v3 );
//      if ( v4 != null ) {
//        newTvm.setValue( k, v4 );
//      }
//    }
//    return newTvm;
  }

  
  /**
   * @param map1
   * @param map2
   * @return a new map that sums {@code map1} and {@code map2}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public static < VV1, VV2 extends Number > TimeVaryingMap< VV1 > plus( TimeVaryingMap< VV1 > map1,
                                                                        TimeVaryingMap< VV2 > map2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return map1.plus( map2 );
  }

  /**
   * @param map
   * @return a copy of this TimeVaryingMap with {@code map} subtracted from it
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public < VV extends Number > TimeVaryingMap< V > minus( TimeVaryingMap< VV > map ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    TimeVaryingMap< V > newTvm = this.clone();
    newTvm.subtract( map );
    return newTvm;
  }

  /**
   * @param map1
   * @param map2
   * @return a new map that is {@code map1} minus {@code map2}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public static < VV1, VV2 extends Number > TimeVaryingMap< VV1 > minus( TimeVaryingMap< VV1 > map1,
                                                                         TimeVaryingMap< VV2 > map2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return map1.minus( map2 );
  }

  /**
   * @param n
   * @return a copy of this map for which {@code n} is subtracted from each value
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap<V> minus( Number n ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    if ( isEmpty() ) return this.clone();
    return minus( n, firstKey(), null );
  }

  /**
   * @param n
   * @param fromKey
   *          the key from which {@code n} is subtracted from all values.
   * @return a copy of this map for which {@code n} is subtracted from each value in the range
   *         [{@code fromKey}, {@code toKey})
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public TimeVaryingMap< V > minus( Number n, Parameter< Long > fromKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return minus( n, fromKey, null );
  }
  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not subtracted by {@code n}. To
   *          include the last key, pass {@code null} for {@code toKey}.
   * @return a copy of this map for which {@code n} is subtracted from each value in the range
   *         [{@code fromKey}, {@code toKey})
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   */
  public <VV> TimeVaryingMap< VV > minus( Number n, Parameter< Long > fromKey,
                                          Parameter< Long > toKey ) throws IllegalAccessException, InvocationTargetException, InstantiationException {
    //TimeVaryingMap< V > newTvm = this.clone();
    Class<VV> cls = (Class< VV >)ClassUtils.dominantTypeClass( getType(), n.getClass() );
    TimeVaryingMap< VV > newTvm = new TimeVaryingMap< VV >(getName() + "_minus_" + n, this, cls);
    newTvm.subtract( n, fromKey, toKey );
    return newTvm;
  }

  public TimeVaryingMap<V> negative() throws IllegalAccessException, InvocationTargetException, InstantiationException {
    return times( (Number)new Integer(-1) );
  }  

  /**
   * @param n
   * @param fromKey
   *          the key from which {@code n} is min'd with all values.
   * @return a copy of this map for which {@code n} is min'd with each value in the range
   *         [{@code fromKey}, {@code toKey})
   */
  public TimeVaryingMap< V > min( Number n, Parameter< Long > fromKey ) {
    return min( n, fromKey, null );
  }
  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not min'd {@code n}. To
   *          include the last key, pass {@code null} for {@code toKey}.
   * @return a copy of this map for which {@code n} is min'd with each value in the range
   *         [{@code fromKey}, {@code toKey})
   */
  public <VV> TimeVaryingMap< VV > minClone( Number n, Parameter< Long > fromKey,
                                             Parameter< Long > toKey ) {
    Class<VV> cls = (Class< VV >)ClassUtils.dominantTypeClass( getType(), n.getClass() );
    TimeVaryingMap< VV > newTvm = this.clone(cls);
    newTvm.min( n, fromKey, toKey );
    return newTvm;
  }

  /**
   * @param map
   * @return a copy of this TimeVaryingMap with {@code map} min'd with it
   */
  public <VV extends Number> TimeVaryingMap< V > minCloneOld( TimeVaryingMap< VV > map ) {
    TimeVaryingMap< V > newTvm = this.clone();
    newTvm.min( map );
    return newTvm;
  }

  public <VV,VVV> TimeVaryingMap< VVV > minClone( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return applyOperation( tvm, MathOperation.MIN );
  }


  /**
   * @param map1
   * @param map2
   * @return a new map that mins {@code map1} and {@code map2}
   * @throws InstantiationException 
   * @throws InvocationTargetException 
   * @throws IllegalAccessException 
   * @throws ClassCastException 
   */
  public static < VV1, VV2 extends Number > TimeVaryingMap< VV1 > min( TimeVaryingMap< VV1 > map1,
                                                                       TimeVaryingMap< VV2 > map2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return map1.minClone( map2 );
  }

  /**
   * @param n
   * @param fromKey
   *          the key from which {@code n} is max'd with all values.
   * @return a copy of this map for which {@code n} is max'd with each value in the range
   *         [{@code fromKey}, {@code toKey})
   */
  public TimeVaryingMap< V > max( Number n, Parameter< Long > fromKey ) {
    return max( n, fromKey, null );
  }
  /**
   * @param n
   * @param fromKey
   * @param toKey
   *          the first key after {@code fromKey} to whose value is not max'd {@code n}. To
   *          include the last key, pass {@code null} for {@code toKey}.
   * @return a copy of this map for which {@code n} is max'd with each value in the range
   *         [{@code fromKey}, {@code toKey})
   */
  public <VV> TimeVaryingMap< VV > maxClone( Number n, Parameter< Long > fromKey,
                                       Parameter< Long > toKey ) {
    Class<VV> cls = (Class< VV >)ClassUtils.dominantTypeClass( getType(), n.getClass() );
    //TimeVaryingMap< VV > newTvm = this.clone(cls);
    TimeVaryingMap< VV > newTvm = this.clone(cls);
    newTvm.setName( "max_" + getName() + "_" + n );
    //TimeVaryingMap< VV > newTvm = new TimeVaryingMap< VV >("max_" + getName() + "_" + n, this, cls); 
    newTvm.max( n, fromKey, toKey );
    return newTvm;
  }

  /**
   * @param tvm
   * @return a copy of this TimeVaryingMap with {@code tvm} max'd with it
   */
  public <VV> TimeVaryingMap< V > maxClone( TimeVaryingMap< VV > tvm ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return applyOperation( tvm, MathOperation.MAX );
  }

  public <VV extends Number> TimeVaryingMap< V > maxCloneOld( TimeVaryingMap< VV > map ) {
    TimeVaryingMap< V > newTvm = this.clone();
    newTvm.max( map );
    return newTvm;
  }

  /**
   * @param map1
   * @param map2
   * @return a new map that maxes {@code map1} and {@code map2}
   * @throws InstantiationException 
   * @throws IllegalAccessException 
   * @throws InvocationTargetException 
   * @throws ClassCastException 
   */
  public static < VV1, VV2 extends Number > TimeVaryingMap< VV1 > max( TimeVaryingMap< VV1 > map1,
                                                                       TimeVaryingMap< VV2 > map2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return map1.maxClone( map2 );
  }

  public static < L, VV1, VV2 > TimeVaryingMap< L > argmax( L l1, TimeVaryingMap< VV1 > map1,
                                                            L l2, TimeVaryingMap< VV2 > map2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return argminormax( l1, map1, l2, map2, false );
  }
  public static < L, VV1, VV2 > TimeVaryingMap< L > argmin( L l1, TimeVaryingMap< VV1 > map1,
                                                            L l2, TimeVaryingMap< VV2 > map2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return argminormax( l1, map1, l2, map2, true );
  }
  public static < L, VV1, VV2 > TimeVaryingMap< L > argminormax( L l1, TimeVaryingMap< VV1 > map1,
                                                                 L l2, TimeVaryingMap< VV2 > map2,
                                                                 boolean isMin ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    // Handle null and empty maps; if both null, return null; if both are empty,
    // return an empty map; if one is empty, return a map with the other label
    // starting at the start of the non-empty map.
    if ( map1 == null && map2 == null ) return null;
    TimeVaryingMap< L > newTvm = new TimeVaryingMap< L >();
    boolean m1empty = Utils.isNullOrEmpty( map1 );
    boolean m2empty = Utils.isNullOrEmpty( map2 );
    if ( m1empty && m2empty ) { return newTvm; }
    if ( m1empty ) {
      L label = l2 instanceof TimeVaryingMap ? (L)((TimeVaryingMap<?>)l2).getValue( map2.firstKey() ) : l2;
      newTvm.setValue( map2.firstKey(), label );
      return newTvm;
    }
    if ( m2empty ) {
      L label = l1 instanceof TimeVaryingMap ? (L)((TimeVaryingMap<?>)l1).getValue( map1.firstKey() ) : l1;
      newTvm.setValue( map1.firstKey(), l1 );
      return newTvm;
    }

    // Collect keys and compute entries for each.
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( map1.keySet() );
    keys.addAll( map2.keySet() );
    for ( Parameter< Long> k : keys ) {
      VV1 v1 = map1.getValue( k );
      VV2 v2 = map2.getValue( k );
      L label = null;
      if ( v2 == null ) label = l1;
      else if ( v1 == null ) label = l2;
      else {
        int comp = CompareUtils.compare( v1, v2 );
        label = (comp == (isMin ? 1 : -1)) ? l2 : l1;
      }
      if ( label instanceof TimeVaryingMap ) {
        label = (L)((TimeVaryingMap<?>)label).getValue( k );
      }
      newTvm.setValue( k, label );
    }
    newTvm.removeDuplicates();
    return newTvm;
  }

  public static < L, VV1, VV2 > TimeVaryingMap< L > argminormax( L l1, TimeVaryingMap< VV1 > map1,
                                                                 L l2, TimeVaryingMap< VV2 > map2,
                                                                 L l3, TimeVaryingMap< VV2 > map3,
                                                                 boolean isMin ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    TimeVaryingMap< L > newTvm = new TimeVaryingMap< L >();
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( map1.keySet() );
    keys.addAll( map2.keySet() );
    keys.addAll( map3.keySet() );
    for ( Parameter< Long> k : keys ) {
      VV1 v1 = map1.getValue( k );
      VV2 v2 = map2.getValue( k );
      VV2 v3 = map3.getValue( k );
      L label = null;
      Object vminormax = null;
      int comp = 0;
      if ( v2 == null ) {
        label = l1;
        vminormax = v1;
      }
      else if ( v1 == null ) {
        label = l2;
        vminormax = v2;
      }
      else {
        comp = CompareUtils.compare( v1, v2 );
        label = (comp == (isMin ? 1 : -1)) ? l2 : l1;
        vminormax = (comp == (isMin ? 1 : -1)) ? v2 : v1;
      }
      if ( v3 != null ) {
        comp = CompareUtils.compare( vminormax, v3 );
        label = (comp == (isMin ? 1 : -1)) ? l3 : label;
      }
      if ( label instanceof TimeVaryingMap ) {
        label = (L)((TimeVaryingMap<?>)label).getValue( k );
      }
      newTvm.setValue( k, label );
    }
    return newTvm;
  }

  public static < L, VV1, VV2 > TimeVaryingMap< L > argmax( L l1, TimeVaryingMap< VV1 > map1,
                                                            L l2, VV2 v2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return argminormax( l1, map1, l2, v2, false );
  }
  public static < L, VV1, VV2 > TimeVaryingMap< L > argmin( L l1, TimeVaryingMap< VV1 > map1,
                                                            L l2, VV2 v2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    return argminormax( l1, map1, l2, v2, true );
  }
  public static < L, VV1, VV2 > TimeVaryingMap< L > argminormax( L l1, TimeVaryingMap< VV1 > map1,
                                                                 L l2, VV2 v2,
                                                                 boolean isMin ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
    TimeVaryingMap< L > newTvm = new TimeVaryingMap< L >();
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( map1.keySet() );
    for ( Parameter< Long> k : keys ) {
      VV1 v1 = map1.getValue( k );
      int comp = CompareUtils.compare( v1, v2 );
      L label = (comp == (isMin ? 1 : -1)) ? l2 : l1;
      if ( label instanceof TimeVaryingMap ) {
        label = (L)((TimeVaryingMap<?>)label).getValue( k );
      }
      newTvm.setValue( k, label );
    }
    return newTvm;
  }

  public static enum Inequality { EQ, NEQ, LT, LTE, GT, GTE }; 
  public static enum BoolOp { AND, OR, XOR, NOT }; 

  public static TimeVaryingMap<Boolean> equals(TimeVaryingMap<?> map, Number n) {
    return compare(map, n, false, Inequality.EQ);
  }
  public static TimeVaryingMap<Boolean> equals(Number n, TimeVaryingMap<?> map) {
    return compare(map, n, true, Inequality.EQ);
  }
  public static TimeVaryingMap<Boolean> equals(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return compare(map1, map2, Inequality.EQ);
  }

  public static TimeVaryingMap<Boolean> notEquals(TimeVaryingMap<?> map, Number n) {
    return compare(map, n, false, Inequality.NEQ);
  }
  public static TimeVaryingMap<Boolean> notEquals(Number n, TimeVaryingMap<?> map) {
    return compare(map, n, true, Inequality.NEQ);
  }
  public static TimeVaryingMap<Boolean> notEquals(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return compare(map1, map2, Inequality.NEQ);
  }

  public static TimeVaryingMap<Boolean> lessThan(TimeVaryingMap<?> map, Number n) {
    return compare(map, n, false, Inequality.LT);
  }
  public static TimeVaryingMap<Boolean> lessThan(Number n, TimeVaryingMap<?> map) {
    return compare(map, n, true, Inequality.LT);
  }
  public static TimeVaryingMap<Boolean> lessThan(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return compare(map1, map2, Inequality.LT);
  }
  public static TimeVaryingMap<Boolean> lessThanOrEqual(TimeVaryingMap<?> map, Number n) {
    return compare(map, n, false, Inequality.LTE);
  }
  public static TimeVaryingMap<Boolean> lessThanOrEqual(Number n, TimeVaryingMap<?> map) {
    return compare(map, n, true, Inequality.LTE);
  }
  public static TimeVaryingMap<Boolean> lessThanOrEqual(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return compare(map1, map2, Inequality.LTE);
  }

  public static TimeVaryingMap<Boolean> greaterThan(TimeVaryingMap<?> map, Number n) {
    return compare(map, n, false, Inequality.GT);
  }
  public static TimeVaryingMap<Boolean> greaterThan(Number n, TimeVaryingMap<?> map) {
    return compare(map, n, true, Inequality.GT);
  }
  public static TimeVaryingMap<Boolean> greaterThan(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return compare(map1, map2, Inequality.GT);
  }
  public static TimeVaryingMap<Boolean> greaterThanOrEqual(TimeVaryingMap<?> map, Number n) {
    return compare(map, n, false, Inequality.GTE);
  }
  public static TimeVaryingMap<Boolean> greaterThanOrEqual(Number n, TimeVaryingMap<?> map) {
    return compare(map, n, true, Inequality.GTE);
  }
  public static TimeVaryingMap<Boolean> greaterThanOrEqual(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return compare(map1, map2, Inequality.GTE);
  }

  
  public static TimeVaryingMap<Boolean> compare(TimeVaryingMap<?> map, Number n, boolean reverse, Inequality i) {
    if ( map == null ) return null;
    TimeVaryingMap<Boolean> result = new TimeVaryingMap< Boolean >();  // REVIEW -- give it a name?
    // handle time zero
    if ( map.isEmpty() || map.firstKey().getValueNoPropagate() != 0L) {
      Object mapVal = map.getValue( 0L );
      Boolean value = null;
      if ( (mapVal != null && n != null) || i == Inequality.EQ || i == Inequality.NEQ ) {
        value = reverse ? doesInequalityHold( n, mapVal, i )
                : doesInequalityHold( mapVal, n, i );
      }
      result.setValue( SimpleTimepoint.zero, value );
    }
    for ( Map.Entry< Parameter< Long >, ? > e : map.entrySet() ) {
      Object mapVal = map.getValue( e.getKey() );
      Boolean value = null;
      if ( (mapVal != null && n != null) || i == Inequality.EQ || i == Inequality.NEQ ) {
        value = reverse ? doesInequalityHold( n, mapVal, i )
                        : doesInequalityHold( mapVal, n, i );
      }
      result.setValue( e.getKey(), value );
    }
    result.removeDuplicates();
    return result;
  }
  
  public static TimeVaryingMap<Boolean> compare(TimeVaryingMap<?> map, Object o, boolean reverse, Inequality i) {
    if ( map == null ) return null;
    TimeVaryingMap<Boolean> result = new TimeVaryingMap< Boolean >();  // REVIEW -- give it a name?
    // handle time zero
    if ( map.isEmpty() || map.firstKey().getValueNoPropagate() != 0L) {
      Object mapVal = map.getValue( 0L );
      Boolean value = null;
      if ( (mapVal != null && o != null) || i == Inequality.EQ || i == Inequality.NEQ ) {
        value = reverse ? doesInequalityHold( o, mapVal, i )
                : doesInequalityHold( mapVal, o, i );
      }
      result.setValue( SimpleTimepoint.zero, value );
    }
    for ( Map.Entry< Parameter< Long >, ? > e : map.entrySet() ) {
      Object mapVal = map.getValue( e.getKey() );
      Boolean value = null;
      if ( (mapVal != null && o != null) || i == Inequality.EQ || i == Inequality.NEQ ) {
        value = reverse ? doesInequalityHold( o, mapVal, i )
                        : doesInequalityHold( mapVal, o, i );
      }
      result.setValue( e.getKey(), value );
    }
    result.removeDuplicates();
    return result;
  }



  public static TimeVaryingMap<Boolean> compare(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2, Inequality i) {
    if ( map1 == null || map2 == null ) return null;
    TimeVaryingMap<Boolean> result = new TimeVaryingMap< Boolean >();  // REVIEW -- give it a name?
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >(map1.keySet());
    keys.addAll( map2.keySet() );
    // handle time zero
    if ( keys.isEmpty() || keys.iterator().next().getValueNoPropagate() != 0L) {
      keys.add(SimpleTimepoint.zero);
    }
    Long tVal = -1L;
    for ( Parameter< Long> t : keys ) {
      // Don't repeat comparison at same time point.
      if ( tVal == t.getValue( false ) ) {
          continue;
      }
      // Get the first and last values for all timepoints with the same Long value
      // and make sure they are the same.
      tVal = t.getValue( false );
      Object v1first = map1.getFirstValue(tVal);
      Object v1last = map1.getLastValue(tVal);
      Object v2first = map2.getFirstValue(tVal);
      Object v2last = map2.getLastValue(tVal);
      Boolean value1 = false;
      Boolean value2 = false;
      if ( (v1first != null && v2first != null) || i == Inequality.EQ || i == Inequality.NEQ ) {
        value1 = doesInequalityHold( v1first, v2first, i );
      }
      Boolean value = value1;
      if ( (v1last != null && v2last != null) || i == Inequality.EQ || i == Inequality.NEQ ) {
        value2 = doesInequalityHold( v1last, v2last, i );
        Object v3 = null;
        try {
          v3 = Functions.and( value1, value2 );
        } catch ( Throwable z ) {
        }
        if ( v3 instanceof Boolean ) {
          value = (Boolean)v3;
        }
      }
      result.setValue( t, value );
    }
    result.removeDuplicates();
    return result;
  }

  public V getFirstValue( Long tVal ) {
      return getValueLater( tVal-1 );
  }

    public V getLastValue( Long tVal ) {
        return getValueEarlier( tVal+1 );
    }

  public static boolean doesInequalityHold( Object v1, Object v2, Inequality i) {
    boolean value = false;
    int comp = CompareUtils.compare( v1, v2 );
    switch (i) {
      case EQ:
        value = comp == 0;
        break;
      case NEQ:
        value = comp != 0;
        break;
      case LT:
        value = comp < 0;
        break;
      case LTE:
        value = comp <= 0;
        break;
      case GT:
        value = comp > 0;
        break;
      case GTE:
        value = comp >= 0;
        break;
      default:
        // TODO -- error
    }
    return value;
  }
  
  public static TimeVaryingMap<Boolean> and(TimeVaryingMap<?> map, Boolean n) {
    return applyBool(map, n, false, BoolOp.AND);
  }
  public static TimeVaryingMap<Boolean> and(Boolean n, TimeVaryingMap<?> map) {
    return applyBool(map, n, true, BoolOp.AND);
  }
  public static TimeVaryingMap<Boolean> and(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return applyBool(map1, map2, BoolOp.AND);
  }

  public static TimeVaryingMap<Boolean> or(TimeVaryingMap<?> map, Boolean n) {
    return applyBool(map, n, false, BoolOp.OR);
  }
  public static TimeVaryingMap<Boolean> or(Boolean n, TimeVaryingMap<?> map) {
    return applyBool(map, n, true, BoolOp.OR);
  }
  public static TimeVaryingMap<Boolean> or(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return applyBool(map1, map2, BoolOp.OR);
  }

  public static TimeVaryingMap<Boolean> xor(TimeVaryingMap<?> map, Boolean n) {
    return applyBool(map, n, false, BoolOp.XOR);
  }
  public static TimeVaryingMap<Boolean> xor(Boolean n, TimeVaryingMap<?> map) {
    return applyBool(map, n, true, BoolOp.XOR);
  }
  public static TimeVaryingMap<Boolean> xor(TimeVaryingMap<?> map1, TimeVaryingMap<?> map2) {
    return applyBool(map1, map2, BoolOp.XOR);
  }

  public static TimeVaryingMap<Boolean> not(TimeVaryingMap<?> map) {
    return applyBool(map, null, false, BoolOp.NOT);
  }

  public static TimeVaryingMap< Boolean > applyBool( TimeVaryingMap< ? > map,
                                                      Boolean n, boolean reverse,
                                                      BoolOp boolOp ) {
    if ( map == null ) return null;
    TimeVaryingMap<Boolean> result = new TimeVaryingMap< Boolean >();  // REVIEW -- give it a name?

    // handle zero
    if ( map.isEmpty() || map.firstKey().getValueNoPropagate() != 0L ) {
      Object mapVal = map.getValue( 0L );
      Boolean value = null;
      value = reverse ? applyOp( n, mapVal, boolOp )
                      : applyOp( mapVal, n, boolOp );
      result.setValue( SimpleTimepoint.zero, value );
    }

    for ( Map.Entry< Parameter< Long >, ? > e : map.entrySet() ) {
      Object mapVal = map.getValue( e.getKey() );
      Boolean value = null;
      value = reverse ? applyOp( n, mapVal, boolOp )
                      : applyOp( mapVal, n, boolOp );
      result.setValue( e.getKey(), value );
    }
    result.removeDuplicates();
    return result;
  }

  public static TimeVaryingMap< Boolean > applyBool( TimeVaryingMap< ? > map1,
                                                      TimeVaryingMap< ? > map2,
                                                      BoolOp boolOp ) {
    if ( map1 == null || map2 == null ) return null;
    TimeVaryingMap<Boolean> result = new TimeVaryingMap< Boolean >();  // REVIEW -- give it a name?
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >(map1.keySet());
    keys.addAll( map2.keySet() );
    // handle zero
    if ( keys.isEmpty() || keys.iterator().next().getValueNoPropagate() != 0L ) {
      keys.add(SimpleTimepoint.zero);
    }
    for ( Parameter< Long> t : keys ) {
      Object v1 = map1.getValue(t);
      Object v2 = map2.getValue(t);
      Boolean value = null;
      value = applyOp( v1, v2, boolOp );
      result.setValue( t, value );
    }
    result.removeDuplicates();
    return result;
  }
  
  public static Boolean applyOp( Object v1, Object v2, BoolOp boolOp ) {
    Boolean b1 = Utils.isTrue( v1, true ); 
    Boolean b2 = Utils.isTrue( v2, true );
    if ( b1 == null && b2 == null ) return null;
    boolean isOneNull = b1 == null || b2 == null;
    boolean nonNullValue = b1 == null ? b2.booleanValue() : b1.booleanValue();
    switch( boolOp ) {
      case AND:
        if ( isOneNull ) {
          if ( !nonNullValue ) return false;
          return null;
        }
        return b1 && b2;
      case OR:
        if ( isOneNull ) {
          if ( nonNullValue ) return true;
          return null;
        }
        return b1 || b2;
      case XOR:
        if ( isOneNull ) return null;
        return b1 ^ b2;
      case NOT:
        return !b1;
      default:
        return null;
    }
  }
  
  public static Object getValueAtTime(Object object, Parameter< Long> t) {
    if ( object instanceof TimeVarying ) {
      return ((TimeVarying< Long, ?>)object).getValue( t );
    }
    return object;
  }
  
  public Object ifThenElse( Object condition, Object thenObj, Object elseObj ) {
    Pair< Boolean, TimeVaryingMap< ? > > p = Functions.booleanOrTimeline( condition );
    if ( p.second != null ) {
      TimeVaryingMap<Object> newTvm = getEmptyMap( thenObj, elseObj );
      newTvm = p.second.ifThenElseToTarget( thenObj, elseObj, newTvm );
      return newTvm;
//      return p.second.ifThenElse( thenObj, elseObj );
    }
    Boolean b = Utils.isTrue( condition, false );
    if ( b == null ) return null;
    if ( b ) return thenObj;
    return elseObj;
  }
  
  protected static TimeVaryingMap<?> mapToClone( Object thenObj, Object elseObj ) {
    TimeVaryingMap<?> mapToClone = null;
    if ( thenObj instanceof TimeVaryingMap ) {
      if ( elseObj instanceof TimeVaryingMap ) {
        Class<?> cls = ClassUtils.dominantObjectType( thenObj, elseObj );
        if ( cls != null && cls.isInstance( thenObj ) ) {
          mapToClone = (TimeVaryingMap<?>)thenObj;
        } else if ( cls != null && cls.isInstance( elseObj ) ) {
          mapToClone = (TimeVaryingMap<?>)elseObj;
        }
      } else {
        mapToClone = (TimeVaryingMap<?>)thenObj;
      }
    } else if ( elseObj instanceof TimeVaryingMap ) {
      mapToClone = (TimeVaryingMap<?>)elseObj;
    }
    return mapToClone;
  }

  protected TimeVaryingMap<Object> getEmptyMap( Object thenObj, Object elseObj ) {
    TimeVaryingMap<?> mapToClone = mapToClone(thenObj, elseObj);
    TimeVaryingMap<Object> newTvm = (TimeVaryingMap< Object >)( mapToClone == null ? new TimeVaryingMap<Object>() : mapToClone.emptyClone() );
    return newTvm;
  }
  
  public TimeVaryingMap<Object> ifThenElse( Object thenObj, Object elseObj ) {
    TimeVaryingMap<Object> newTvm = getEmptyMap( thenObj, elseObj );
    newTvm = ifThenElseToTarget( thenObj, elseObj, newTvm );
    return newTvm;
  }
  
  public TimeVaryingMap<Object> ifThenElseToTarget( Object thenObj, Object elseObj, TimeVaryingMap<Object> newTvm ) {
    Set< Parameter< Long > > keys =
        new TreeSet< Parameter< Long > >( Collections.reverseOrder() );
    keys.addAll( this.keySet() );
    if ( thenObj instanceof TimeVaryingMap ) {
      keys.addAll( ((TimeVaryingMap<?>)thenObj).keySet() );
    }
    if ( elseObj instanceof TimeVaryingMap ) {
      keys.addAll( ((TimeVaryingMap<?>)elseObj).keySet() );
    }
    for ( Parameter< Long> k : keys ) {
      Object v3 = null;
      V v1 = this.getValue( k );
      if ( Boolean.TRUE.equals(Utils.isTrue( v1 )) ) {
        v3 = getValueAtTime(thenObj, k);
      } else {
        v3 = getValueAtTime(elseObj, k);
      }
      newTvm.setValue( k, v3 );
    }
    if ( newTvm != null ) newTvm.removeDuplicates();
    return newTvm;
  }

  
  /**
   * Validate the consistency of the map for individual and adjacent entries.
   * @return whether or not the entries in the map make sense.
   */
  public boolean isConsistent() {
    Parameter< Long> lastTp = null;
    long lastTime = -1;
    boolean ok = true;
    ArrayList<V> valuesAtSameTime = new ArrayList< V >();
    ArrayList< Parameter< Long > > timesAtSameTime =
        new ArrayList< Parameter< Long > >();
    boolean firstEntry = true;
    for ( Map.Entry< Parameter< Long>, V >  entry : entrySet() ) {
      Parameter< Long> tp = entry.getKey();
      V value = null;
      try {
        value = entry.getValue();
      } catch ( ClassCastException cce ) {
        ok = false;
        System.err.println( "Error! Value "
                            + MoreToString.Helper.toLongString( entry.getValue() )
                            + " has the wrong type in TimeVaryingMap! "
                            + MoreToString.Helper.toLongString( this ) );
        cce.printStackTrace();
      }
      boolean timepointValueChanged = true;
      boolean duplicateValueAtTime = false;
      // Check for problems with the key.
      // No null keys.
      if ( tp == null ) {
        Debug.error( true, "Error! null key in TimeVaryingMap " + getName() );
        ok = false;
      } else if ( tp.getValueNoPropagate() == null ) {
        // No null values for Parameter< Long> key.
        Debug.errorOnNull( true,
                           "Error! null param value for Parameter< Long> "
                               + MoreToString.Helper.toLongString( tp )
                               + " in TimeVaryingMap " + getName(),
                           tp.getValueNoPropagate() );
        ok = false;
      } else {
       timepointValueChanged = !tp.getValueNoPropagate().equals( lastTime );
        if ( !firstEntry && tp.getValueNoPropagate() < lastTime ) {
          // Time cannot decrease.
          Debug.error( true, "Error! time value for entry "
                             + MoreToString.Helper.toLongString( entry )
                             + " should be >= "
                             + MoreToString.Helper.toLongString( lastTime )
                             + " in TimeVaryingMap " + getName() );
          ok = false;
        } else {
          lastTime = tp.getValueNoPropagate();
        }
      }
      if ( timepointValueChanged ) {
        valuesAtSameTime.clear();
        timesAtSameTime.clear();
      }
      timesAtSameTime.add( tp );
      duplicateValueAtTime = valuesAtSameTime.contains( value );
      if ( tp != null && tp == lastTp ) {
        // A key should have only one entry.
        Debug.error( true, "Error! Parameter< Long> has duplicate entry "
                           + MoreToString.Helper.toLongString( entry )
                           + " in TimeVaryingMap " + getName() );
        ok = false;
      }

      // Check for problems with the value.
      if ( value != null && getType() != null
           && !getType().isAssignableFrom( value.getClass() ) ) {
        Debug.error( true,
                     "Error! value " + MoreToString.Helper.toLongString( value )
                         + " at time " + MoreToString.Helper.toLongString( tp )
                         + " has an incompatible type "
                         + value.getClass().getCanonicalName()
                         + "; getType() = " + getType().getCanonicalName() );
        ok = false;
      }
      if ( !firstEntry && duplicateValueAtTime ) {
        if ( Debug.isOn() ) {
          Debug.err( "Warning! duplicate entry of value "
                     + MoreToString.Helper.toLongString( value ) + " at time "
                     + MoreToString.Helper.toLongString( tp ) + " with time set "
                     + MoreToString.Helper.toLongString( timesAtSameTime )
                     + " and value set "
                     + MoreToString.Helper.toLongString( valuesAtSameTime )
                     + " for TimeVaryingMap " + getName() );
        }
        ok = false;
      }
      lastTp = tp;
      if ( !duplicateValueAtTime ) {
        valuesAtSameTime.add( value );
      }
      firstEntry = false;
    }
    if ( ok ) {
      if ( Debug.isOn() ) Debug.outln( getName() + " is consistent" );
    } else {
      if ( Debug.isOn() ) Debug.outln( getName()
                                       + " is not consistent: "
                                       + MoreToString.Helper.toLongString( this ) );
    }
    return ok;
  }

  @Override
  public V unsetValue( Parameter< Long > t, V value ) {
    breakpoint();
    if ( t == null ) {
      if ( Debug.isOn() ) {
        Debug.error( false,
                     "Warning! unsetValue("
                         + MoreToString.Helper.toLongString( t ) + ", "
                         + MoreToString.Helper.toLongString( value )
                         + "): Null Parameter< Long> key for TimeVaryingMap "
                         + MoreToString.Helper.toLongString( this ) );
      }
      return null;
    }
    if ( t.getValueNoPropagate() == null ) {
      if ( Debug.isOn() ) {
        Debug.error( false,
                     "Warning! unsetValue("
                         + MoreToString.Helper.toLongString( t ) + ", "
                         + MoreToString.Helper.toLongString( value )
                         + "): Null value for Parameter< Long> key for TimeVaryingMap "
                         + MoreToString.Helper.toLongString( this ) );
      }
      return null;
    }
    V oldValue = null;
    Parameter< Long> tpInMap = keyForValueAt( value, t );
// possible fix to a bug, but fixing it elsewhere
//    Parameter< Long> tI = tryCastTimepoint( t );
//    Parameter< Long> tpInMap = keyForValueAt( value, tI.getValueNoPropagate() );
    if ( tpInMap != t ) {
      if ( Debug.isOn() ) {
        Debug.error( false,
                     "Warning! unsetValue("
                         + MoreToString.Helper.toLongString( t ) + ", "
                         + MoreToString.Helper.toLongString( value )
                         + "): Parameter< Long> key is not in the map for TimeVaryingMap "
                         + MoreToString.Helper.toLongString( this ) );
      }
      return null;
    }
    if ( tpInMap == null ) {
      if ( Debug.isOn() ) {
        Debug.error( false,
                     "Warning! unsetValue("
                         + MoreToString.Helper.toLongString( t ) + ", "
                         + MoreToString.Helper.toLongString( value )
                         + "): no matching entry in TimeVaryingMap "
                         + MoreToString.Helper.toLongString( this ) );
      }
    } else {
      oldValue = get( tpInMap );
      if ( Debug.isOn() ) {
        Debug.outln( "unsetValue(" + MoreToString.Helper.toLongString( t )
                     + ", " + MoreToString.Helper.toLongString( value )
                     + "): removing entry ("
                     + MoreToString.Helper.toLongString( tpInMap ) + ", "
                     + MoreToString.Helper.toLongString( oldValue ) + ") in "
                     + this.toString( true, true, null ) );
      }
      remove( tpInMap );
    }
    if ( Debug.isOn() || checkConsistency ) isConsistent();
    return oldValue;
  }

  public void unapply( Effect effect ) {
    unapply( effect, true );
  }
  
  public static boolean newChanges = true;
  public Effect getUndoEffect( Effect effect, boolean timeArgFirst ) {
    Pair< Parameter< Long >, V > p = null;
    if ( isArithmeticEffect( effect ) && effect instanceof EffectFunction ) {
      Effect inverseEffect = getInverseEffect( effect );
      if ( !newChanges ) {
        return inverseEffect;
      }

      // correct the value argument since it might have changed since originally
      // applied
      p = getTimeAndValueOfEffect( effect, timeArgFirst );
      if ( p != null ) {
        Parameter< Long > t = p.first;
        MathOperation op =
            getOperationForMethod( ( (EffectFunction)effect ).getMethod() );
        V val = getValueChangeAt( t, op );
        if ( inverseEffect instanceof EffectFunction ) {
          int pos = getIndexOfValueArgument( (EffectFunction)inverseEffect );
          Vector< Object > args = ( (EffectFunction)inverseEffect ).arguments;
          if ( args != null ) {
            if ( pos >= 0 && pos < args.size() ) {
              Object oldVal = args.get( pos );
              if ( Debug.isOn() ) System.out.println( "MMMMMM   Replacing " + oldVal + " with "
                                  + val + " in undoEffect: " + inverseEffect );
              args.set( pos, val );
            }
          }
        }
      }

      return inverseEffect;
      
    } else {
      // if not arithmetic effect
      // TODO
    }
    return null;
  }
  
  public void unapply( Effect effect, boolean timeArgFirst ) {
    if ( effect == null ) return;
    if ( !appliedSet.contains(effect) ) {
      return;
    }
    Pair< Parameter< Long>, V > p = null;
    if ( isArithmeticEffect( effect ) ) {
      Effect undoEffect = getUndoEffect( effect, timeArgFirst );
      if ( Debug.isOn() ) {
        Debug.outln( "unapply("
                     + MoreToString.Helper.toString( effect, true, false, null )
                     + ") : inverseEffect = "
                     + MoreToString.Helper.toString( undoEffect, true, false,
                                                     null ) );
      }
      if ( canBeApplied( undoEffect ) ) {
        undoEffect.applyTo( this, true );
        appliedSet.remove( effect );
        appliedSet.remove( undoEffect );
      } else {
        Debug.error( true, "Error! Cannot unapply effect: "
                           + MoreToString.Helper.toLongString( effect ) );
      }
//      return;
    }
    if ( p == null ) {
      p = getTimeAndValueOfEffect( effect, timeArgFirst );
    }
    if ( p != null ) {
      unsetValue( p.first, p.second );
      appliedSet.remove(effect);
    }
  }

  public Effect getInverseEffect( Effect effect ) {
    if ( isArithmeticEffect( effect ) && effect instanceof EffectFunction) {
      EffectFunction eff = (EffectFunction)effect;
      Method inverseMethod = getInverseMethod( eff.getMethod() );
      if ( inverseMethod == null ) return null;
      EffectFunction invEff = new EffectFunction( eff );
      invEff.setMethod( inverseMethod );
      return invEff;
    }
    // REVIEW -- Should we include setValue() & unsetValue()?
    return null;
  }

  public Method getInverseMethod( Method method ) {
    if ( method == null ) return null;  // REVIEW -- complain?
    Method inv = getInverseMethods().get( method );
    return inv;
  }
  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.event.ParameterListener#detach(gov.nasa.jpl.ae.event.Parameter)
   */
  @Override
  public void detach( Parameter< ? > parameter ) {
    if ( parameter instanceof Parameter ) {
      remove( parameter );
    }
    Set<Parameter<?>> detachSet = new LinkedHashSet< Parameter<?> >();
    for ( Entry< Parameter< Long>, V > e : this.clone().entrySet() ) {
      if ( e.getValue() instanceof HasParameters ) {
        if ( ( (HasParameters)e.getValue() ).hasParameter( parameter, false,
                                                           null ) ) {
          detachSet.add( e.getKey() );
        }
      }
    }
    for ( Parameter<?> p : detachSet ) detach( p );
  }


  @Override
  public boolean isFreeParameter( Parameter< ? > parameter, boolean deep,
                                  Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return false;
    seen = pair.second;
    //if ( Utils.seen( this, deep, seen ) ) return false;
    return HasParameters.Helper.isFreeParameter( this, parameter, deep, seen, false );
  }

  /* (non-Javadoc)
   * @see java.util.AbstractMap#equals(java.lang.Object)
   * Note: this class has a natural ordering that is inconsistent with equals.
   */
  @Override
  public boolean equals( Object o ) {
    if ( this == o ) return true;
    if ( o instanceof TimeVarying ) {
      return ( compareTo( (TimeVarying< Long, V>)o, false ) == 0 );
    }
    return false;
  }

  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo( TimeVarying< Long, V > o ) {
    return compareTo( o, true );
  }
  public int compareTo( TimeVarying< Long, V > o, boolean checkId ) {
    if ( o == null ) return 1;
    if ( checkId ) return CompareUtils.compare( getId(), o.getId() );
    int compare = 0;
    if ( o instanceof TimeVaryingMap ) {
      TimeVaryingMap<?> otvm = (TimeVaryingMap<?>)o;
      compare = CompareUtils.compare( getName(), otvm.getName(), true );
      if ( compare != 0 ) return compare;
    }
    if ( Debug.isOn() ) Debug.err( "TimeVaryingMap.compareTo() may compare values, which, if changed while this is in a map, can corrupt the map." );
    compare = CompareUtils.compare( this, o ); // WARNING: values change!!!
    if ( compare != 0 ) return compare;
    return compare;
  }

  // TODO -- use getValue() to compare TimeVaryingMaps!!!
  //public int compareValues

  public static Method getSetValueMethod() {
    return getSetValueWithParameterMethod();
  }
  public static Method getSetValueWithParameterMethod() {
    if ( setValueMethod1 == null ) {
      for ( Method m : TimeVaryingMap.class.getMethods() ) {
        if ( m.getName().equals("setValue") && m.getParameterTypes() != null
             && m.getParameterTypes().length == 2
             && m.getParameterTypes() [0] == Parameter.class ) {
          setValueMethod1 = m;
        }
      }
    }
    return setValueMethod1;
  }

  protected static void setArithmeticMethods() {
    //Parameter< Long> t = new Parameter< Long >( null );
    Class< TimeVaryingMap > cls = TimeVaryingMap.class;
    if ( arithmeticMethods == null ) {
      arithmeticMethods = new TreeSet< Method >( methodComparator );
    } else {
      arithmeticMethods.clear();
    }
    try {
      addNumberMethod =
          cls.getMethod( "add", new Class<?>[] { Number.class } );
      arithmeticMethods.add( addNumberMethod );
      addNumberAtTimeMethod =
          cls.getMethod( "add", new Class<?>[] { Number.class,
                                                 Parameter.class } );
      arithmeticMethods.add( addNumberAtTimeMethod );
      addNumberForTimeRangeMethod =
          cls.getMethod( "add", new Class<?>[] { Number.class,
                                                 Parameter.class,
                                                 Parameter.class } );
      arithmeticMethods.add( addNumberForTimeRangeMethod );
      addMapMethod =
          cls.getMethod( "add", new Class<?>[] { TimeVaryingMap.class } );
      arithmeticMethods.add( addMapMethod );

      subtractNumberMethod =
          cls.getMethod( "subtract", new Class<?>[] { Number.class } );
      arithmeticMethods.add( subtractNumberMethod );
      subtractNumberAtTimeMethod =
          cls.getMethod( "subtract", new Class<?>[] { Number.class,
                                                 Parameter.class } );
      arithmeticMethods.add( subtractNumberAtTimeMethod );
      subtractNumberForTimeRangeMethod =
          cls.getMethod( "subtract", new Class<?>[] { Number.class,
                                                 Parameter.class,
                                                 Parameter.class } );
      arithmeticMethods.add( subtractNumberForTimeRangeMethod );
      subtractMapMethod =
          cls.getMethod( "subtract", new Class<?>[] { TimeVaryingMap.class } );
      arithmeticMethods.add( subtractMapMethod );

      multiplyNumberMethod =
          cls.getMethod( "multiply", new Class<?>[] { Number.class } );
      arithmeticMethods.add( multiplyNumberMethod );
      multiplyNumberAtTimeMethod =
          cls.getMethod( "multiply", new Class<?>[] { Number.class,
                                                 Parameter.class } );
      arithmeticMethods.add( multiplyNumberAtTimeMethod );
      multiplyNumberForTimeRangeMethod =
          cls.getMethod( "multiply", new Class<?>[] { Number.class,
                                                 Parameter.class,
                                                 Parameter.class } );
      arithmeticMethods.add( multiplyNumberForTimeRangeMethod );
      multiplyMapMethod =
          cls.getMethod( "multiply", new Class<?>[] { TimeVaryingMap.class } );
      arithmeticMethods.add( multiplyMapMethod );

      divideNumberMethod =
          cls.getMethod( "divide", new Class<?>[] { Number.class } );
      arithmeticMethods.add( divideNumberMethod );
      divideNumberAtTimeMethod =
          cls.getMethod( "divide", new Class<?>[] { Number.class,
                                                 Parameter.class } );
      arithmeticMethods.add( divideNumberAtTimeMethod );
      divideNumberForTimeRangeMethod =
          cls.getMethod( "divide", new Class<?>[] { Number.class,
                                                 Parameter.class,
                                                 Parameter.class } );
      arithmeticMethods.add( divideNumberForTimeRangeMethod );
      divideMapMethod =
          cls.getMethod( "divide", new Class<?>[] { TimeVaryingMap.class } );
      arithmeticMethods.add( divideMapMethod );

    } catch ( SecurityException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch ( NoSuchMethodException e ) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  protected static Method getAddNumberAtTimeMethod() {
    if ( addNumberAtTimeMethod == null ) {
      setArithmeticMethods();
    }
    return addNumberAtTimeMethod;
  }
  protected static Method getAddNumberForTimeRangeMethod() {
    if ( addNumberForTimeRangeMethod == null ) {
      setArithmeticMethods();
    }
    return addNumberForTimeRangeMethod;
  }
  public static Method getAddNumberMethod() {
    if ( addNumberMethod == null ) {
      setArithmeticMethods();
    }
    return addNumberMethod;
  }
  public static Method getAddMapMethod() {
    if ( addMapMethod == null ) {
      setArithmeticMethods();
    }
    return addMapMethod;
  }

  public Interpolation getInterpolation() {
    return interpolation;
  }
  public static Method getSubtractNumberMethod() {
    if ( subtractNumberMethod == null ) {
      setArithmeticMethods();
    }
    return subtractNumberMethod;
  }
  public static Method getSubtractNumberAtTimeMethod() {
    if ( subtractNumberAtTimeMethod == null ) {
      setArithmeticMethods();
    }
    return subtractNumberAtTimeMethod;
  }
  public static Method getSubtractNumberForTimeRangeMethod() {
    if ( subtractNumberForTimeRangeMethod == null ) {
      setArithmeticMethods();
    }
    return subtractNumberForTimeRangeMethod;
  }
  public static Method getSubtractMapMethod() {
    if ( subtractMapMethod == null ) {
      setArithmeticMethods();
    }
    return subtractMapMethod;
  }
  public static Method getMultiplyNumberMethod() {
    if ( multiplyNumberMethod == null ) {
      setArithmeticMethods();
    }
    return multiplyNumberMethod;
  }
  public static Method getMultiplyNumberAtTimeMethod() {
    if ( multiplyNumberAtTimeMethod == null ) {
      setArithmeticMethods();
    }
    return multiplyNumberAtTimeMethod;
  }
  public static Method getMultiplyNumberForTimeRangeMethod() {
    if ( multiplyNumberForTimeRangeMethod == null ) {
      setArithmeticMethods();
    }
    return multiplyNumberForTimeRangeMethod;
  }
  public static Method getMultiplyMapMethod() {
    if ( multiplyMapMethod == null ) {
      setArithmeticMethods();
    }
    return multiplyMapMethod;
  }

  public static Method getDivideNumberMethod() {
    if ( divideNumberMethod == null ) {
      setArithmeticMethods();
    }
    return divideNumberMethod;
  }
  public static Method getDivideNumberAtTimeMethod() {
    if ( divideNumberAtTimeMethod == null ) {
      setArithmeticMethods();
    }
    return divideNumberAtTimeMethod;
  }
  public static Method getDivideNumberForTimeRangeMethod() {
    if ( divideNumberForTimeRangeMethod == null ) {
      setArithmeticMethods();
    }
    return divideNumberForTimeRangeMethod;
  }
  public static Method getDivideMapMethod() {
    if ( divideMapMethod == null ) {
      setArithmeticMethods();
    }
    return divideMapMethod;
  }


  public static Map< Method, Method > getInverseMethods() {
    if ( inverseMethods == null ) {
      initEffectMethods();
    }
    return inverseMethods;
  }
  public static MathOperation getOperationForMethod(Method m) {
    return getOperationForMethod().get( m );
  }
  public static Map< Method, MathOperation > getOperationForMethod() {
    if ( operationForMethod == null ) {
      initEffectMethods();
    }
    return operationForMethod;
  }
  public static Comparator< Method > getMethodComparator() {
    return methodComparator;
  }

  Parameter< Long> getTimeOfEffect( EffectFunction effectFunction ) {
    Integer i = getIndexOfTimepointArgument( effectFunction );
    if ( i == null || i < 0 ) {
      i = getIndexOfFirstTimepointParameter( effectFunction );
    }
    if ( i != null && i >= 0 ) {
      return tryEvaluateTimepoint( effectFunction.arguments.get( i ), false );
    }
    return null;
  }

  public Integer getIndexOfTimepointArgument( EffectFunction effectFunction ) {
    Integer i = getEffectMethodsMap().get( effectFunction.getMethod() );
    if ( Debug.isOn() ) Debug.outln( "getIndexOfTimepointArgument("
                                     + effectFunction.getMethod().getName()
                                     + ") = " + i );
    return i;
  }

  Object getValueOfEffect( EffectFunction effectFunction ) {
    Pair< Parameter< Long >, Object > p = getTimeAndValueOfEffect( effectFunction );
    if ( p == null ) return null;
    return p.second;
  }

  
  public Integer getIndexOfValueArgument( EffectFunction effectFunction ) {
    if ( effectFunction == null || effectFunction.getArguments() == null ) return null;
    Integer tpi = getIndexOfTimepointArgument(effectFunction);
    Pair< Parameter< Long >, Object > p = getTimeAndValueOfEffect( effectFunction );
    if ( p == null ) return -1;
    Object val = p.second;
    int pos = -1;
    int posTypeOk = -1;
    int i = 0;
    while ( i < effectFunction.getArguments().size() ) {
      if ( tpi == null || tpi != i ) {
        pos = i;
        Object arg = effectFunction.getArgument(i);
        if ( Utils.valuesEqual(val, arg) ) {
          return pos;
        }
        if ( posTypeOk == -1 && getType() != null && getType().isInstance( arg ) ) {
          posTypeOk = i;
        }
      }
      ++i;
    }
    if ( posTypeOk != -1 ) {
      return posTypeOk;
    }
    return pos;
  }

  public <TT> Pair< Parameter< Long>, TT > getTimeAndValueOfEffect( Effect effect ) {
    return getTimeAndValueOfEffect( effect, null );
  }

  /**
   * The time of the effect and the value applied are picked from the effect
   * functions arguments according to their types and whether the first argument
   * is suggested (by {@code timeFirst}) to be the timepoint. the arguments.
   *
   * @param effect
   *          the effect, from whose arguments the time and value are identified
   * @param timeFirst
   *          is a suggestion by the caller as to whether it believes that the
   *          first argument is the time argument as it is for setValue() and
   *          other effect methods. If it is null, then the timepoint is
   *          identified more carefully and the first argument is chosen in the
   *          case that there appear to be multiple viable candidates, from
   *          which no more evidence is found for one over others.
   * @return the timepoint at which the effect takes place and the value applied
   *         in a Pair
   */
  public < TT > Pair< Parameter< Long >, TT >
      getTimeAndValueOfEffect( Effect effect, Boolean timeFirst ) {
    // REVIEW -- Why not use <T>? Can't enforce it?
    if ( !( effect instanceof EffectFunction ) ) {
      return null;
    }
    EffectFunction effectFunction = (EffectFunction)effect;
    if ( effectFunction == null || effectFunction.getMethod() == null ) {
      if ( Debug.isOn() ) {
        Debug.errln( getName() + ".getTimeAndValueOfEffect(Effect=" + effect
                     + ") called with no effect method! "
                     + MoreToString.Helper.toLongString( this ) );
      }
      return null;
    }
    if ( effectFunction.getMethod().getParameterTypes().length < 2 ) {
//      Debug.error( getName() + ".getTimeAndValueOfEffect(Effect="
//                   + MoreToString.Helper.toLongString( effect )
//                   + ") Error! Method takes "
//                   + effectFunction.getMethod().getParameterTypes().length
//                   + " parameters, but 2 are required." );
      return null;
    }

    if ( effectFunction.arguments == null || effectFunction.arguments.size() < 2 ) {
//      Debug.error( getName() + ".getTimeAndValueOfEffect(Effect="
//                   + MoreToString.Helper.toLongString( effect )
//                   + ") Error! Method has "
//                   + effectFunction.getMethod().getParameterTypes().length
//                   + " arguments, but 2 are required." );
      return null;
    }
    boolean complainIfNotTimepoint = timeFirst != null;

    timeFirst = ( timeFirst == null ) || timeFirst;
    Parameter< Long > tp = null;
    TT value = null;

    Integer idx = getIndexOfTimepointArgument( effectFunction );
    if ( idx != null ) {
      // Object arg = effectFunction.arguments.get( idx );
      // if ( isTimepoint( arg ) ) {
      // tp = tryEvaluateTimepoint(arg, true);
      timeFirst = ( idx == 0 );
      // }
    }

    Object arg1 = effectFunction.arguments.get( timeFirst ? 0 : 1 );
    Object arg2 = effectFunction.arguments.get( timeFirst ? 1 : 0 );
    tp = tryEvaluateTimepoint( arg1, true );

    value = (TT)tryEvaluateValue( arg2, true );

    if ( isTimepoint(tp) ) {
      return new Pair< Parameter< Long >, TT >( tp, value );
    }

    Pair< Object, Parameter< Long >> p =
        whichIsTheTimepoint( arg1, arg2, timeFirst && complainIfNotTimepoint,
                             !timeFirst && complainIfNotTimepoint, timeFirst );
    if ( p == null ) {
      return null;
    }
    tp = p.second;
    Object tpArg = ( p.first == arg1 ) ? arg1 : arg2;
    Object valueArg = ( p.first == arg1 ) ? arg2 : arg1;
    value = (TT)tryEvaluateValue( valueArg, true );
    Pair< Parameter< Long >, TT > pair =
        new Pair< Parameter< Long >, TT >( tp, value );
    if ( value == null ) {
      if ( valueArg != null ) {
        Parameter< Long > otp = tryEvaluateTimepoint( valueArg, true, true );
        if ( otp != null ) {
          TT ov = (TT)tryEvaluateValue( tpArg, true );
          if ( Debug.isOn() ) {
            Debug.err( "Looks like timepoint and value are reversed in call to getTimeAndValueOfEffect( Effect "
                       + MoreToString.Helper.toLongString( effect ) + ", "
                       + "Boolean " + MoreToString.Helper.toLongString( timeFirst )
                       + ") = " + MoreToString.Helper.toLongString( pair ) );
          }
        }
      }
    }
    return pair;
  }


  public static Integer getFirstParameterOfType( Method method, Object clsOrObj ) {
    if ( clsOrObj instanceof Class ) {
      Class<?> cls = (Class<?>)clsOrObj;
      Integer pos = getIndexOfFirstParameterOfType( method, cls );
      if ( pos != null && pos >= 0 ) {
        return pos;
      }
    }
    return null;  // TODO -- HERE!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  }

  public static Integer getIndexOfFirstParameterOfType( Method method, Class<?> cls ) {
    if ( method == null ) return null;
    Class< ? >[] pTypes = method.getParameterTypes();
    if ( pTypes == null || pTypes.length <= 0 ) {
      return null;
    }
    Integer best = null;
    //boolean assigned = false;
    for ( Integer i = 0; i < pTypes.length; ++i ) {
      Class<?> pType = pTypes[i];
      if ( pType == null ) continue;
      if ( !cls.isAssignableFrom( pType ) ) continue;
      if ( best == null ) {
        best = i;
      }
//      TypeVariable< ? >[] gTypes = pType.getTypeParameters();
//      if ( gTypes == null ) continue;
//      for ( TypeVariable< ? > typeVar : gTypes ) {
//        if ( typeVar == null ) continue;
//      }
    }
    return best;
  }

  public static Integer getIndexOfFirstTimepointParameter( Method method ) {
    Parameter< Long> p = new Parameter< Long >("objForCls", null, null );
    Class< Parameter< Long> > cls = (Class< Parameter< Long >>)p.getClass();
    return getIndexOfFirstParameterOfType( method, cls );
//    if ( method == null ) return null;
//    Class< ? >[] pTypes = method.getParameterTypes();
//    if ( pTypes == null || pTypes.length <= 0 ) {
//      return null;
//    }
//    Integer best = null;
//    for ( Integer i = 0; i < pTypes.length; ++i ) {
//      Class<?> pType = pTypes[i];
//      if ( pType == null ) continue;
//      if ( !Parameter.class.isAssignableFrom( pType ) ) continue;
//      if ( best == null ) best = i;
//      TypeVariable< ? >[] gTypes = pType.getTypeParameters();
//      if ( gTypes == null ) continue;
//      for ( TypeVariable< ? > typeVar : gTypes ) {
//        if ( typeVar == null ) continue;
//      }
//    }
//    return null;
  }

  public static Integer getIndexOfFirstTimepointParameter( EffectFunction effectFunction ) {
    return getIndexOfFirstTimepointParameter( effectFunction.getMethod() );
  }

  public static boolean testGetFirstTimepointParameter() {
//    boolean debugWasOn = Debug.isOn();
//    if ( !debugWasOn ) Debug.turnOn();

    boolean succ = true;
    TimeVaryingMap tvm = new TimeVaryingMap< Long >("imap", null, 0L, Long.class);
    EffectFunction f =
        new EffectFunction( tvm,
                            TimeVaryingMap.getSetValueMethod(),
                            new Object[] { new Parameter< Long >( "four",
                                                                     null, tvm ),
                                           new Long( 14 ) } );
    //assert ( 0 == tvm.getIndexOfFirstTimepointParameter( f ) );
    Method.setAccessible( TimeVaryingMap.class.getDeclaredMethods(), true );
    for ( Method method : TimeVaryingMap.class.getDeclaredMethods() ) {
      f = new EffectFunction( tvm, method );
      if ( Debug.isOn() ) Debug.outln( "method " + method
                   + " has first timepoint parameter at position "
                   + tvm.getIndexOfFirstTimepointParameter( f ) );
      Class<?> clss = ParameterListenerImpl.class;
      Class< ? >[] pTypes = method.getParameterTypes();
      if ( pTypes == null || pTypes.length <= 0 ) {
        continue;
      }
      for ( Integer i = 0; i < pTypes.length; ++i ) {
        Class<?> pType = pTypes[i];
        if ( pType == null ) continue;
        //if ( !clss.isAssignableFrom( pType ) ) continue;
        TypeVariable< ? >[] gTypes = pType.getTypeParameters();
        if ( gTypes == null ) continue;
        for ( TypeVariable< ? > typeVar : gTypes ) {
          if ( typeVar == null ) continue;
          if ( Debug.isOn() ) Debug.outln( "method=" + method.getName() + ", parameter type="
                       + pType + ", type variable=" + typeVar
                       + ", typeVar.getName()=" + typeVar.getName()
          + ", typeVar.getBounds()=" + typeVar.getBounds()
          + ", typeVar.getGenericDeclaration()=" + typeVar.getGenericDeclaration()
          );
        }
      }
    }


//    if ( !debugWasOn ) Debug.turnOff();
    return succ;
  }


  /**
   * Struct used to help compare objects to tell which is a timepoint key and
   * which is a V value for the enclosing map.  The object may be a timepoint
   * and will be checked.  Boolean attributes may be null to indicate that they
   * have not been evaluated.
   */
  class CandidateTimepoint {
    /**
     * object to evaluate
     */
    protected Object o = null;
    /**
     * conversion of object to timepoint
     */
    protected Parameter< Long> tp = null;
    /**
     * whether tp is actually a timepoint
     */
    protected Boolean isATimepoint = null;
    /**
     *  whether the value of the timepoint is valid
     */
    protected Boolean inDomain = null;
    /**
     * whether or not the object needed to be converted to get the timepoint
     */
    protected Boolean neededConversion = null;
    /**
     * whether or not the object needed to be wrapped with a Parameter to be
     * converted to a timepoint
     */
    protected Boolean neededWrapping = null;

    /**
     * The default constructor
     */
    public CandidateTimepoint() {}

    /**
     * Constructor with all fields.
     *
     * @param o
     *          object to evaluate
     * @param tp
     *          conversion of object to timepoint
     */
    public CandidateTimepoint( Object o, Parameter< Long > tp ) {
      super();
      if ( tp == null ) {
        setObject(o);
      } else {
        this.o = o;
        setTp(tp);
      }
    }

    /**
     * @return the candidate object
     */
    public Object getObject() {
      if ( o == null && tp != null ) {
        return tp;
      }
      return o;
    }

    /**
     * @return the converted timepoint
     */
    public Parameter< Long > getTimepoint() {
      if ( tp == null && o != null ) {
        setTimepointToObject();
      }
      return tp;
    }

    public void setObject( Object o ) {
      this.o = o;
//      Assert.assertNull(tp);
//      setTimepointToObject();
      isATimepoint = null;//isTimepoint( o );
      inDomain = null;
    }
    public void setTp( Parameter< Long > tp) {
      this.tp = tp;
      isATimepoint = null;//isTimepoint( tp );
      inDomain = null;
//      if ( o == null ) o = tp;
    }
    public boolean knowIfIsATimepoint() {
      return ( isATimepoint != null );
    }
    public boolean knowIfInDomain() {
      return ( inDomain != null );
    }
    protected boolean setTimepointToObject() {
      if ( tp != null ) {
        if ( Debug.isOn() ) Debug.err( "Warning! Trying to replace existing tp with o." );
      }
      tp = tryCastTimepoint( o );
      if ( tp == o ) {
        setNeededConversion( false );
        setNeededWrapping( false );
      }
      return tp == o;
    }
    public boolean isInDomain() {
      if ( inDomain == null ) {
        if ( tp == null && o != null ) {
          setTimepointToObject();
        }
        inDomain = Timepoint.defaultDomain.contains( tp.getValue(false) );
      }
      return inDomain;
    }
    public boolean isATimepoint() {
      if ( isATimepoint == null ) {
        if ( tp == null ) {
          isATimepoint = setTimepointToObject();
        } else {
          isATimepoint = isTimepoint( tp );
        }
      }
      return isATimepoint;
    }

    public boolean knowIfNeededConversion() {
      return ( neededConversion != null );
    }
    public boolean knowIfNeededWrapping() {
      return ( neededWrapping != null );
    }
    /**
     * @return neededConversion
     */
    public Boolean neededConversion() {
      return neededConversion;
    }

    /**
     * @param neededConversion the neededConversion to set
     */
    public void setNeededConversion( Boolean neededConversion ) {
      this.neededConversion = neededConversion;
    }

    /**
     * @return neededWrapping
     */
    public Boolean neededWrapping() {
      return neededWrapping;
    }

    /**
     * @param neededWrapping the neededWrapping to set
     */
    public void setNeededWrapping( Boolean neededWrapping ) {
      this.neededWrapping = neededWrapping;
    }

//    public boolean isWrapped() {
//      if ( tp == null || o == null ) return false;
//      return false;
//    }

    /**
     * Just try to convert using wrapping.
     * @param propagate
     * @return
     */
    public boolean tryWrapping( boolean propagate ) {
      setTp( tryEvaluateTimepoint( o, propagate, true ) );
      return isATimepoint();
    }

    public boolean doConversion( boolean propagate, boolean okToWrap ) {
      setTimepointToObject();
      if ( isATimepoint() ) {
        setNeededConversion( false );
        setNeededWrapping( false );
        return true;
      }
      setTp( tryEvaluateTimepoint( o, propagate, false ) );
      if ( isATimepoint() ) {
        setNeededConversion( true );
        setNeededWrapping( false );
        return true;
      }
      if ( okToWrap ) {
        if ( tryWrapping( propagate ) ) {
          setNeededWrapping( true );
        }
      }
      return isATimepoint();
    }

  }

  /**
   * @param o1
   *          candidate timepoint
   * @param o2
   *          candidate timepoint
   * @param o1ShouldBe
   * @param o2ShouldBe
   * @param o1WinsTie
   * @return the object, call it A, paired with its successful conversion to a
   *         timepoint, iff A meets higher priority criteria than the other object, B, for the following
   *         (with 1=highest priority, 4=lowest): (1)
   *         the other, B, cannot be converted successfully, (2) A is expected
   *         to be a timepoint ([A]ShouldBe=True and [B]ShouldBe=False), (3) The timepoint value of one is not in the default timepoint domain. (4) A
   *         is more easily transformed into a Timepoint than B (casting is
   *         easier than evaluating, which is easier than wrapping with a
   *         Parameter ), or (5) A wins in the tie (o1WinsTie == (A==o1));
   *         otherwise, neither can be converted, and null is returned.
   */
  public Pair< Object, Parameter< Long > > whichIsTheTimepoint( Object o1,
                                                                   Object o2,
                                                                   boolean o1ShouldBe,
                                                                   boolean o2ShouldBe,
                                                                   boolean o1WinsTie ) {
    // check for null values
    if ( o1 == null && o2 == null ) return null;
    if ( o1 != null && o2 == null ) {
      return new Pair< Object, Parameter< Long >>(o1, tryEvaluateTimepoint( o1, true ));
    }
    if ( o1 == null && o2 != null ) {
      return new Pair< Object, Parameter< Long >>(o2, tryEvaluateTimepoint( o2, true ));
    }
    // make some convenient inferences
    boolean strongPreference = o1WinsTie ? o1ShouldBe : o2ShouldBe;
    boolean strongPreferenceFor1 = strongPreference && o1ShouldBe;
    boolean strongPreferenceFor2 = strongPreference && o2ShouldBe;
    boolean o1IsPreferred = strongPreferenceFor1 || (!strongPreferenceFor2 && o1WinsTie );
    boolean o2IsPreferred = !o1IsPreferred;
    boolean inconsistent = o1WinsTie ? o2IsPreferred : o1IsPreferred;
    if ( inconsistent ) {
      Debug.error( true, "Error! Inconsistent arguments. The expected object is not also the tie breaker." );
    }

    // make oi the preferred choice and oii the other
    Object preferredObj = o1IsPreferred ? o1 : o2;
    Object nonPreferredObj = o1IsPreferred ? o2 : o1;

    CandidateTimepoint preferred = new CandidateTimepoint( preferredObj, null );
    CandidateTimepoint nonPreferred = new CandidateTimepoint( nonPreferredObj, null );

    // Check if one is already a timepoint, and
    // see if one is not in the default time domain and the other is.
    Pair< Object, Parameter< Long > > p =
        selectTimepoint(preferred, nonPreferred, strongPreference, true, true );
    if ( p != null ) return p;

    // Try to get a timepoint from evaluation of the object and check domains.
    preferred.doConversion( true, false );
    nonPreferred.doConversion( true, false );
    p = selectTimepoint( preferred, nonPreferred, strongPreference, true, true );
    if ( p != null ) return p;

    // Try to get a timepoint from evaluation of the object while allowing
    // wrapping and check domains.
    preferred.tryWrapping( true );
    nonPreferred.tryWrapping( true );
    p = selectTimepoint( preferred, nonPreferred, strongPreference, true, true );
    if ( p != null ) return p;

    // If couldn't make either on a timepoint, fail--no more magic tricks.
    if ( !preferred.isATimepoint() && !nonPreferred.isATimepoint() ) return null;

    // See if one works as a value and not the other
    if ( strongPreference ) {
      V valPref = tryEvaluateValue( nonPreferredObj, true );
      if ( valPref != null ) {
        return new Pair< Object, Parameter< Long > >( preferred.getObject(),
                                                         preferred.getTimepoint() );
      }
    } else {
      V valNonPref = tryEvaluateValue( preferredObj, true );
      if ( valNonPref != null ) {
        return new Pair< Object, Parameter< Long > >( nonPreferred.getObject(),
                                                         nonPreferred.getTimepoint() );
      }
    }

    if ( preferred.isATimepoint() ) {
      return new Pair< Object, Parameter< Long > >( preferred.getObject(),
                                                       preferred.getTimepoint() );
    }
    return null;
  }

  /**
   * Determine which of the two candidates better resembles a timepoint
   * according to preferences.
   *
   * @param pref
   *          the preferred candidate
   * @param nonPref
   *          the non-preferred candidate
   * @param strongPreference
   *          whether or not the preferred candidate wins in a tie
   * @return the preferred object paired with its conversion to a timepoint, the
   *         non-preferred object and timepoint if the preferred isn't
   *         available, or null
   */
  protected Pair< Object, Parameter< Long > >
      selectTimepoint( CandidateTimepoint pref, CandidateTimepoint nonPref,
                       boolean strongPreference ) {
    return selectTimepoint( pref, nonPref, strongPreference, true, false );
  }

  /**
   * Determine which of the two candidates better resembles a timepoint
   * according to preferences.
   *
   * @param pref
   *          the preferred candidate
   * @param nonPref
   *          the non-preferred candidate
   * @param strongPreference
   *          whether or not the preferred candidate wins in a tie
   * @param checkIfTimepoint
   * @param checkIfInDomain
   * @return the preferred object paired with its conversion to a timepoint, the
   *         non-preferred object and timepoint if the preferred isn't
   *         available, or null
   */
  protected Pair< Object, Parameter< Long > >
      selectTimepoint( CandidateTimepoint pref, CandidateTimepoint nonPref,
                       boolean strongPreference, boolean checkIfTimepoint,
                       boolean checkIfInDomain ) {
    if ( pref == null || nonPref == null ) return null;
    // check whether the candidates are found to be timepoints (Parameter< Long>)
    if ( checkIfTimepoint ) {
      if ( pref.isATimepoint()
           && ( strongPreference || !nonPref.isATimepoint() ) ) {
        return new Pair< Object, Parameter< Long >>( pref.getObject(),
                                                        pref.getTimepoint() );
      }
      if ( !strongPreference && nonPref.isATimepoint() ) { // !isPrefTp
        return new Pair< Object, Parameter< Long >>( nonPref.getObject(),
                                                        nonPref.getTimepoint() );
      }
    }
    boolean bothAreTimepoints = pref.isATimepoint() && nonPref.isATimepoint();
    if ( bothAreTimepoints  ) {
      // check if an actual Timepoint (as opposed to just Parameter< Long>)
      if ( strongPreference && pref.getTimepoint() instanceof Timepoint
           && !( nonPref.getTimepoint() instanceof Timepoint ) ) {
        return new Pair< Object, Parameter< Long >>( pref.getObject(),
                                                        pref.getTimepoint() );
      }
      // check if value is a legal time value
      if ( checkIfInDomain ) {
        if ( pref.isInDomain() && ( strongPreference || !nonPref.isInDomain() ) ) {
          return new Pair< Object, Parameter< Long >>( pref.getObject(),
                                                          pref.getTimepoint() );
        }
        if ( !strongPreference && nonPref.isInDomain() ) { // !isPrefTp is known
          return new Pair< Object, Parameter< Long >>(
                                                          nonPref.getObject(),
                                                          nonPref.getTimepoint() );
        }
      }
    }
    return null;

  }


  public boolean isTimepoint( Object tp ) {
    boolean isParam = tp instanceof Parameter;
    if ( !isParam ) return false;
    Parameter<?> p = (Parameter<?>)tp;
    boolean isIntParam = p.getValueNoPropagate() instanceof Long;
    return isIntParam;
  }

  @Override
  public boolean isApplied( Effect effect ) {
    return isApplied(effect, getSetValueMethod(), getSetValueMethod() );//, true );
  }

  public static boolean isArithmeticEffect( Effect effect ) {
    if ( effect instanceof EffectFunction ) {
      EffectFunction f = (EffectFunction)effect;
      if (getArithmeticMethods().contains(f.getMethod())) return true;
    }
    return false;
  }

  public static Collection<Method> getArithmeticMethods() {
    if ( arithmeticMethods == null ) {
      setArithmeticMethods();
    }
    return arithmeticMethods;
  }

  public boolean isApplied( Effect effect, Method method1, Method method2 ) {
    breakpoint();
    if ( Debug.isOn() || checkConsistency ) isConsistent();
    if ( isArithmeticEffect( effect ) ) {
      return appliedSet.contains(effect); // HACK! We have a problem here! We can't know if it's
                   // applied unless we keep track of all effects here!
    }

    Pair< Parameter< Long>, V > p = getTimeAndValueOfEffect( effect );//, method1, method2 ); //, timeArgFirst );
    if ( p == null ) return false;
    Object t = p.first;
    V value = p.second;
//    if ( value != null ) {
    // FIXME!!! t is converted to a Parameter even if the time of the effect is
    // an integer.
      if ( t instanceof Parameter
           && ( (Parameter<?>)t ).getValueNoPropagate() instanceof Long ) {
        return hasValueAt( value, tryCastTimepoint( t ), true );  
      } if ( t instanceof Long ) {
        return hasValueAt( value, (Long)t );
      }
//    }
    return false;
  }

  /**
   * Parses a value from a string of the same type as the map value, V.
   * @param s
   * @return
   */
  public V valueFromString( String s ) {
    V value = null;
    if ( type == Double.class || type == double.class ) {
      value = type.cast( Double.parseDouble( s ) );
    } else if ( type == Float.class || type == float.class ) {
      value = type.cast( Float.parseFloat( s ) );
    } else if ( type == Long.class || type == long.class ) {
      value = type.cast( Long.parseLong( s ) );
    } else if ( type == Integer.class || type == int.class ) {
      value = type.cast( Integer.parseInt( s ) );
    } else if ( type == Boolean.class || type == boolean.class ) {
      value = type.cast( Boolean.parseBoolean( s ) );
    } else if ( type != null ) {
      value = type.cast( s );
    } else {
      List<Object> objs = new ArrayList< Object >();
      objs.add( s );
      try {
        objs.add( Double.parseDouble( s ) );
      } catch (NumberFormatException e) {
      }
      try {
        objs.add( Long.parseLong( s ) );
      } catch (NumberFormatException e) {
      }
      objs.add( Boolean.parseBoolean( s ) );
      for ( Object o : objs ) {
        try {
          value = tryCastValue( o );
          if ( value != null ) break;
        } catch ( ClassCastException e ) {
        }
      }
    }
    if ( value == null && s != null ) {
      try {
        value = tryCastValue( s );
      } catch ( ClassCastException e ) {
        e.printStackTrace();
      }
    }
    return value;
  }

  public void fromStringMapWithJulianDates( Map<String,String> map, Class<V> cls ) {
    clear();
    for ( Entry<String, String> ss : map.entrySet() ) {
      Long key = null;
      try {
          Double dKey = Double.parseDouble( ss.getKey() );
          key = Timepoint.julianToInteger( dKey );
      } catch (NumberFormatException e) {
      }
      if ( key != null && key >= 0 ) {
        Timepoint tp = new Timepoint( null, key, this );
        V value = valueFromString( ss.getValue() );
        setValue( tp, value );
      }
    }    
  }

  public static Long resolveOffset( Long value, TimeUtils.Units units, Date offset) {
    if ( value == null ) return null;
    return resolveOffset( value.doubleValue(), units, offset );
  }
  public static Long resolveOffset( Double value, TimeUtils.Units units, Date offset) {
    if ( value == null ) return null;
    Long timeValue = value.longValue();
    if ( units != null ) {
      timeValue = ((Double)(value / Timepoint.conversionFactor( units ))).longValue();
    }
    if ( offset != null ) {
      timeValue += Timepoint.fromDateToInteger( offset );
    }
    return timeValue;
  }

  public static SimpleTimepoint getTimepointFromString( String value,
                                                        TimeUtils.Units units,
                                                        Date offset) {
    Long longValue = null;
    TimeZone gmtZone = TimeZone.getTimeZone( "GMT" );
    Date d = TimeUtils.dateFromTimestamp( value, gmtZone );
    if ( d != null ) longValue = Timepoint.fromDateToInteger( d );
    if ( longValue == null ) {
      // If the key is not a timestamp but a number, then we need to consider
      // that it may be a Julian date or an offset from some date (maybe just
      // from the epoch).
      try {
        longValue = Long.parseLong( value );
        longValue = resolveOffset( longValue, units, offset );
      } catch ( NumberFormatException e ) {
        try {
          Double dKey = Double.parseDouble( value );
          // If it's a real number, and no offset or units are specified, then
          // assume that it is a Julian date.
          if ( offset == null && units == null && dKey != null ) {
            longValue = Timepoint.julianToInteger( dKey );
          } else {
            longValue = resolveOffset( dKey, units, offset );
          }
          if ( longValue != null && offset != null ) {
            if ( units == null ) {
              longValue += Timepoint.fromDateToInteger( offset );
            } else {
              longValue = ( (Double)( Timepoint.fromDateToInteger( offset )
                                + longValue / Timepoint.conversionFactor( units ) ) ).longValue();
            }
            TimeUtils.julianToMillis( TimeUtils.Julian_Jan_1_2000 );
          } if ( longValue == null ) {
            longValue = dKey.longValue();
          }
        } catch ( NumberFormatException ee ) {
          Debug.error(true, "ERROR! Can't parse time value from \"" + value + "\"");
        }
      }
    }
    if ( longValue != null ) {
      SimpleTimepoint tp = new SimpleTimepoint( null, longValue, null );
      return tp;
    }
    return null;
  }


  public void fromStringMap( Map<String,String> map, Class<V> cls ) {
    fromStringMap( map, null, null, cls );
  }
  public void fromStringMap( Map<String,String> map,
                             Date offset, TimeUtils.Units units, Class<V> cls ) {
    clear();
    SimpleTimepoint pre_tp = null;
    V pre_value = null;
    TimeZone gmtZone = TimeZone.getTimeZone( "GMT" );

    int ct = 0;
    for ( Entry<String, String> ss : map.entrySet() ) {
      SimpleTimepoint tp = getTimepointFromString( ss.getKey(), units, offset );
      if ( tp != null ) {
        tp.setOwner( this );
        // add time-value pair if time is within the horizon.
        Long t = tp.getValueNoPropagate();
        if ( t != null ) 
        {
           if(t < 0 ) {
             if ( pre_tp == null || tp.compareTo( pre_tp ) > 0 )
             {
               pre_tp = tp;
               pre_value = valueFromString( ss.getValue() );
             }
           }
           else if ( t < Timepoint.getHorizonDuration() ) {
               V value = valueFromString( ss.getValue() );
               setValue( tp, value );
           } else {
             //Debug.breakpoint();
           }
        }
      }
      ++ct;
    }
    if( pre_tp != null )
    {
      if( getValue(0L) == null && this.interpolation.type != Interpolation.NONE )
      {
        SimpleTimepoint zero_tp = new SimpleTimepoint( null, 0L, this );
        if ( interpolation.isStep() ) {
          setValue( zero_tp, pre_value );
        } else if (interpolation.isLinear() ) {
          Parameter<Long> t2 = firstKey();
          V v2 = firstValue();
          V v = interpolatedValue( pre_tp, zero_tp, t2, pre_value, v2 );
          if ( v != null ) {
            setValue( zero_tp, v );
          }
        }
      }
    }
    if ( type == null ) {
      Class<?> c = ClassUtils.dominantObjectType( values() );
      if ( cls != null && cls.isAssignableFrom( c ) ) {
        c = cls;
      }
      setType( c );
    }
  }

  public void fromString( String s, Class<V> cls ) {
    Map<String,String> map = new TreeMap<String,String>();
    Pattern p = Pattern.compile( "([^{@]*)?([^{@]*)(@[^{]*)?" );
    Matcher matcher = p.matcher( s );
    int end = 0;
    if ( matcher.find() ) {
      end = matcher.end();
      if ( matcher.groupCount() >= 1 ) {
        if ( Utils.isNullOrEmpty( matcher.group( 1 ) ) ) {
          interpolation.fromString( matcher.group( 1 ) );
        }
        name = matcher.group( 2 );
      }
      if ( matcher.groupCount() == 2 ) {
        name = matcher.group( 1 );
      }
    }
    MoreToString.Helper.fromString( map, s.substring( end ) );
    fromStringMap( map, cls );
  }

  public void fromCsvString( String s, Class<V> cls ) {
    Map<String,String> map = new TreeMap<String,String>();
    MoreToString.Helper.fromString( map, s, "", "\\s*", "", "", "\\s*,\\s*", "" );
    fromStringMap( map, cls );
  }
  
  protected static File findFileInResourcePaths(String fileName, String backupFileName) {
    File f = null;
    for ( String path : resourcePaths ) {
      File pFile = new File(path);
      if ( !pFile.exists() ) continue;
      f = FileUtils.findFile( pFile, fileName );
      if ( f == null && !Utils.isNullOrEmpty( backupFileName ) ) {
        f = FileUtils.findFile( pFile, backupFileName );
      }
      if (f != null ) break;
    }
    return f;
  }
  protected static File findFileSimple(String fileName, String backupFileName) {
      File f = FileUtils.findFile( fileName );
      if ( f == null && !Utils.isNullOrEmpty( backupFileName ) ) {
        f = FileUtils.findFile( backupFileName );
      }
      return f;
  }
  protected static File findFile(String fileName, String backupFileName) {
    File f = findFileSimple(fileName, backupFileName);
    if ( f != null ) return f;
    f = findFileInResourcePaths( fileName, backupFileName);
    return f;
  }

  public void fromCsvFile( String fileName ) {
    fromCsvFile( fileName, null );
  }
  public void fromCsvFile( String fileName, Class<V> cls ) {
    fromCsvFile( fileName, null, null, cls );
  }
  public void fromCsvFile( String fileName, Date offset, TimeUtils.Units units, Class<V> cls ) {
    fromCsvFile( fileName, null, offset, units, cls );
  }
  public void fromCsvFile( String fileName, String backupFileName, Class<V> cls ) {
    fromCsvFile( fileName, null, null, null, cls );
  }
  public void fromCsvFile( String fileName, String backupFileName, Date offset,
                           TimeUtils.Units units, Class<V> cls ) {
    String fName = fileName;
    if ( fName == null && backupFileName == null ) return;
    if ( fName == null ) fName = backupFileName;
    try {
      File f = findFile( fName, backupFileName );
      if ( f == null ) {
        Debug.error(true, false, "Error!  Could not find csv file! " + fileName);
        return;
      }
      if ( !f.exists() ) {
        Debug.error(true, false, "Error!  csv file does not exist! " + f);
      }
      if (f != null ) System.out.println("Loading timeline from csv file, " + f.toString() + ".");
      ArrayList< ArrayList< String > > lines = FileUtils.fromCsvFile( f );
      //String s = FileUtils.fileToString( f );
      Map<String,String> map = new TreeMap<String,String>();
      //MoreToString.Helper.fromString( map, s, "", "\\s+", "", "", "[ ]*,[ ]*", "" );
      for ( ArrayList<String> line : lines ) {
        if ( line.size() == 1 && Utils.count( "\\s", line.get(0) ) == 1 ) {
          line = Utils.newList(line.get( 0 ).split( "\\s" ));
        }
        if ( line.size() >= 2 ) {
          map.put( line.get(0), line.get(1) );
        }
      }
      fromStringMap( map, offset, units, cls );
      if ( Debug.isOn() ) Debug.outln( "read map from file, " + fName + ":\n" + this.toString() );
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }
  public void toCsvFile( String fileName, String header, String format, Calendar cal ) {
    String s = toCsvString(header, format, cal);
    if ( Debug.isOn() ) Debug.outln( "wrote map to file, " + fileName + ":\n" + s );
    FileUtils.stringToFile( s, fileName );
  }

  public void toCsvFile( String fileName ) {
    toCsvFile(fileName, null, null, null);
  }

  public String toCsvString() {
    return toCsvString( null, null, null );
  }

  protected String getTimeString( Parameter<Long> p, String dateFormat, Calendar cal ) {
    String timeString = null;
    if ( dateFormat != null  ) {
      if ( cal == null ) cal = TimeUtils.gmtCal;
      if ( p == null || p.getValueNoPropagate() == null ) {
        return null;
      }
      long t = p.getValueNoPropagate();
      timeString = Timepoint.toTimestamp( t, dateFormat, cal );
    } else {
      timeString = p.toShortString();
    }
    return timeString;
  }
  
  protected String getCsvLine( Parameter< Long > p, V v, String dateFormat, Calendar cal ) {
    String timeString = getTimeString( p, dateFormat, cal );
    if ( Utils.isNullOrEmpty( timeString ) ) return null;
    String vString = null;
//    if ( v instanceof Double && ((Double)v) >= -Float.MAX_VALUE && ((Double)v) <= Float.MAX_VALUE && Math.abs( ((Double)v).doubleValue() ) > Float.MIN_VALUE ) {
//      vString = String.format( "%f", ((Double)v).floatValue() );
//    } else {
      vString = MoreToString.Helper.toShortString( v );
//    }
    String line = timeString + "," + vString + "\n";
    return line;
  }
  
  public String toCsvString(String header, String dateFormat, Calendar cal ) {
    StringBuffer sb = new StringBuffer();
    if ( !Utils.isNullOrEmpty( header ) ) {
      sb.append(header + "\n");
    }
    Parameter< Long > lastKey = null;
    for ( java.util.Map.Entry< Parameter< Long >, V > e : entrySet() ) {
      String line = getCsvLine( e.getKey(), e.getValue(), dateFormat, cal );
      if ( line == null ) continue;
      sb.append( line );
      lastKey = e.getKey();
    }
    // Add a final point in case the plotter does not render beyond last point
    Timepoint h = Timepoint.getHorizonTimepoint();
    if ( (interpolation == null || interpolation.type != Interpolation.NONE) && lastKey != null
         && before( lastKey, h ) ) {
      V v = getValue(h);
      if ( v != null ) {
        String line = getCsvLine( h, v, dateFormat, cal );
        if ( line != null ) sb.append( line );
      }
    }
    return sb.toString();
  }

  @Override
  public String toShortString() {
    return getName();
  }

  public String toShortString( boolean withHash) {
    return getName() + ( withHash ? "@" + hashCode() : "" );
  }

  @Override
  public String toString() {
    return toString( Debug.isOn(), false, null );
  }

  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen ) {
    return Parameter.toString( this, true, withHash, deep, seen );
  }

  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen,
                          Map< String, Object > otherOptions ) {
    StringBuffer sb = new StringBuffer();
    sb.append( interpolation + " " );
    sb.append( this.getName() );
    if ( withHash ) sb.append( "@" + hashCode() );
    if ( deep || size() <= 10 ) {
      sb.append( MoreToString.Helper.toString( this, withHash, deep, seen,
              otherOptions, CURLY_BRACES, false ) );
    } else {
      sb.append("( ... " + size() + " entries  ... )");
    }
    return sb.toString();
  }

  @Override
  public < T > boolean pickParameterValue( Variable< T > variable ) {
    // TODO Auto-generated method stub
    return false;
  }

  @Override
  public Integer getId() {
    return id;
  }
  @Override
  public int hashCode() {
    return id;
  }

  /**
   * @return the type
   */
  @Override
  public Class<V> getType() {
    if ( type == null || Object.class.equals(type) ) {
      for ( V t : values() ) {
        if ( t != null ) {
          setType( t.getClass() );
          if ( type != null ) break;
        }
      }
    }
    return type;
  }

  /**
   * @param type the type to set
   */
  @SuppressWarnings( "unchecked" )
  public void setType( Class< ? > type ) {
    if ( this.type == type || (type != null && sureAboutType ) ) {
      return;
    }
    Class<V> oldType = this.type;
    while ( type != null && this.type == oldType ) {
      try {
        this.type = (Class< V >)type;
        break;
      } catch ( ClassCastException e ) {
        // ignore
      }
      type = type.getSuperclass();
    }
    if ( this.type != oldType && this.type != null
         && ( domain == null
              || ( domain.getType() != null
                   && !this.type.isAssignableFrom( domain.getType() ) ) ) ) {
      Domain<V> d = DomainHelper.getDomainForClass( this.type );
      if ( d != null ) {
        domain = d;
      }
    }
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.ae.solver.Wraps#getTypeNameForClassName(java.lang.String)
   */
  @Override
  public String getTypeNameForClassName( String className ) {
    // There should only be one generic parameter of type V
    return ClassUtils.parameterPartOfName( className, false );
  }

  /*
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.solver.Wraps#getPrimitiveType()
   */
  @Override
  public Class< ? > getPrimitiveType() {
    Class< ? > c = null;
    Class< ? > t = getType();
    if ( t != null ) {
      c = ClassUtils.primitiveForClass( t );
      //V v = getFirstValue();
      if ( c == null ) {
        for ( V v : values() ) {
          if ( c == null && v != null
              && Wraps.class.isInstance( v ) ) {// isAssignableFrom( getType() ) ) {
            c = ( (Wraps< ? >)v ).getPrimitiveType();
          }
          if ( c != null ) break;
        }
      }
    }
    return c;
  }

  public V firstValue() {
    V value = this.firstEntry().getValue();
    return value;
  }

  @Override
  public V getValue( boolean propagate ) {
    if ( isEmpty() ) return null;
    V value = firstValue();
    if ( size() > 1 ) {
      if (!this.allValuesEqual()) {
        Debug.error(false, false, "Warning! Calling getValue() on TimeVaryingMap with multiple values.  Returning null for " + this );
        return null;
      }
    }
    return value;
  }

  public boolean allValuesEqual() {
    V value = firstValue();
    for ( V v : values() ) {
      if ( !Expression.valuesEqual(value, v) ) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void setValue( V value ) {
    if ( size() == 0 ) {
      Parameter< Long> t = new Parameter< Long>(null, null, 0L, this);
      put( t, value );
    } else if ( size() > 0 ) {
      if ( size() > 1 ) {
        Debug.error(false, "Warning! Setting all values  to " + value + " in " + this );
      }
      for ( java.util.Map.Entry< Parameter< Long >, V > e : entrySet() ) {
        e.setValue( value );
      }
    }
  }

  /* (non-Javadoc)
   * @see gov.nasa.jpl.mbee.util.Wraps#hasValue()
   */
  @Override
  public boolean hasValue() {
    // We have to always have a value in order to not equal a null parameter.  An effect variable will not initialize if the empty map equals null.
    return true;
    //return !isEmpty();
  }

  /*  Getting the average is painful.  Should probably integrate.  TODO
   * 
  public Number zero() {
    if ( getType() == null ) return null;
    if (Number.class.isAssignableFrom( getType() )) {
      return (Number)tryCast(0, getType());
    }
    return null;
  }
  public Number sum() {
    Number sum = zero();
    if ( sum == null ) return null;
    Functions.plus( o1, o2 );
     gov.nasa.jpl.ae.util.Math.plus()
  }

  public Number avg() {
    Number sum = zero();
    if ( sum == null ) return null;
    Functions.plus( o1, o2 );
     gov.nasa.jpl.ae.util.Math.plus()
  }
  */
  public Number min() {
    return getMinValue(null, null);
  }
  public Number max() {
    return getMaxValue(null, null);
  }
  public Number minimum( Parameter<Long> start, Parameter<Long> end ) {
    return getMinValue(start, end);
  }
  public Number maximum( Parameter<Long> start, Parameter<Long> end ) {
    return getMaxValue( start, end );
  }
  public Number getMinValue( Parameter<Long> start, Parameter<Long> end ) {
    return getMinOrMaxValue( true, start, end );
  }
  public Number getMaxValue( Parameter<Long> start, Parameter<Long> end ) {
    return getMinOrMaxValue( false, start, end );
  }
  public Number getMinOrMaxValue( boolean isMin ) {
    return getMinOrMaxValue( isMin, null, null );
  }
  public Number getMinOrMaxValue( boolean isMin, Parameter<Long> start, Parameter<Long> end ) {
    if ( isEmpty() ||
         !ClassUtils.isNumber( getType() ) ) {
      return null;
    }
    boolean isInt = ClassUtils.isLong( getType() );
    Long minInt = Long.MAX_VALUE;
    Double minDouble = Double.MAX_VALUE;
    long mul = isMin ? 1 : -1;
    if ( start == null || start.getValueNoPropagate() == null) {
      start = firstKey();
    }
    if ( end == null || end.getValueNoPropagate() == null ) {
      end = lastKey();
    }
    if ( start == null || end == null || start.getValueNoPropagate() > end.getValueNoPropagate() ) {
      return null;
    }
    NavigableMap< Parameter< Long >, V > map = subMap( start, true, end, true );
    Collection< V > vals = map.values();
    for ( V v : vals ) {
      if ( v != null &&  v instanceof Number ) {
        if ( isInt ) {
          Long i = ((Number)v).longValue();
          if ( minInt == Long.MAX_VALUE || mul * i.longValue() < mul * minInt.longValue() ) {
            minInt = i;
          }
        } else {
          Double d = ((Number)v).doubleValue();
          if ( minDouble == Double.MAX_VALUE || mul * d.doubleValue() < mul * minDouble.doubleValue() ) {
            minDouble = d;
          }
        }
      }
    }
    if ( Debug.isOn() ) {
      System.out.println( "getMinOrMaxValue(" + start + ", " + end + ") = " + minInt + " or " + minDouble  + " for " + this.getName() );
      System.out.println( "submap = {" + map.firstKey() + "(" + Timepoint.toTimestamp( map.firstKey().getValue() ) + ":?,...," + map.lastKey() + "(" + Timepoint.toTimestamp( map.lastKey().getValue() ) + ":?}");
    }
    return isInt ? minInt : minDouble;
  }

  @Override
  public String toAspenMdl( String tlName ) {
    if ( Utils.isNullOrEmpty( tlName ) ) {
      tlName = getName();
    }
    StringBuffer sb = new StringBuffer();
    if ( ClassUtils.isNumber( getType() ) ) {
      Number minVal = min();
      Number maxVal = max();
      // If all values are positive, set minVal to 0 unless it would make it
      // hard to see the difference.
      if ( minVal.doubleValue() > 0.0 && maxVal.doubleValue() / minVal.doubleValue() > 1.2 ) {
        minVal = 0;
      }
      sb.append( "resource " + tlName + " {\n" );
      sb.append( "  type = depletable;\n" );
      sb.append( "  min_value = " + minVal.doubleValue() + ";\n" );
      sb.append( "  capacity = " + maxVal.doubleValue() + ";\n" );
      sb.append( "  default = " + minVal.doubleValue() + ";\n" );
      sb.append( "}\n" );

      sb.append( "activity " + tlName + "Changer {\n" );
      sb.append( "  duration = 1;\n" );
      //sb.append( "  " + (isInt?"int":"real") + " value;\n");
      sb.append( "  real value;\n");
      sb.append( "  reservations = " + tlName + " use value;\n");
      sb.append( "}\n" );
    } else {
      sb.append( "state_variable " + tlName + " {\n  default = \"\";\n}\n" );
      sb.append( "activity " + tlName + "Changer {\n" );
      sb.append( "  duration = 1;\n" );
      sb.append( "  string value;\n" );
      sb.append( "  reservations = " + tlName + " change_to value at_start;\n");
      sb.append("}\n" );
    }
    return sb.toString();
  }

  @Override
  public String toAspenIni( String tlName ) {
    StringBuffer sb = new StringBuffer();
    boolean isNum = ClassUtils.isNumber( getType() );
    int ctr = 0;
    for ( Map.Entry< Parameter< Long >, V > e : entrySet() ) {
      if ( e.getKey() == null || e.getKey().getValue(false) == null ) continue;
      long startTime = e.getKey().getValue(false).longValue();
      String q = isNum ? "" : "\"";
      Object value = null;
      try {
        value = Expression.evaluate( e.getValue(), null, false );
      } catch ( ClassCastException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      } catch ( IllegalAccessException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      } catch ( InvocationTargetException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      } catch ( InstantiationException e1 ) {
        // TODO Auto-generated catch block
        //e1.printStackTrace();
      }
      if ( isNum && value == null ) {
        value = new Double(min().doubleValue());
      }
      if ( isNum && value != null ) {
        Number d = (Number)value;
        value = d.doubleValue();
      }
      value = q + value + q;
      sb.append( tlName + "Changer " + tlName + "Changer_"  + (ctr++) + " {\n" );
      sb.append( "  start_time = " + Math.max( 0, Math.min( 1073741823, startTime)) + ";\n" );
      sb.append( "  value = " + value + ";\n" );
      sb.append("}\n" );
    }
    sb.append("\n");
    return sb.toString();
  }

  public static Map< Method, Integer > effectMethodsMap() {
    if ( effectMethods == null ) effectMethods = initEffectMethods();
    return effectMethods;
  }
  public Map< Method, Integer > getEffectMethodsMap() {
    if ( effectMethods == null ) effectMethods = initEffectMethods();
    return effectMethods;
  }

  @Override
  public Collection< Method > getEffectMethods() {
    return effectMethods();
  }

  public static Collection< Method > effectMethods() {
    Map< Method, Integer > m = effectMethodsMap();
    if ( m == null ) return null;
    return m.keySet();
  }

  protected static LinkedHashSet< String > effectNames = null; 
  public static Set< String > effectMethodNames() {
    if ( effectNames == null ) {
      effectNames = new LinkedHashSet< String >();
      for ( Method m : effectMethods() ) {
        effectNames.add( m.getName() );
      }
    }
    return effectNames;
  }

  @Override
  public boolean doesAffect( Method method ) {
    return affects(method);
  }
  public static boolean affects( Method method ) {
    return effectMethods().contains( method );
  }
  
  protected static Map< Method, Integer > initEffectMethods() {
    methodComparator = new Comparator< Method >() {

      @Override
      public int compare( Method o1, Method o2 ) {
        int cmp;
        if (o1 == o2) return 0;
        if (o1 == null) return -1;
        if (o2 == null) return 1;
        cmp = o1.getName().compareTo( o2.getName() );
        if ( cmp != 0 ) return cmp;
        cmp = o1.getDeclaringClass().getName()
                .compareTo( o2.getDeclaringClass().getName() );
        if ( cmp != 0 ) return cmp;
        cmp = CompareUtils.compareCollections( o1.getParameterTypes(),
                                               o2.getParameterTypes(),
                                               true, true );
        return cmp;
      }
    };
    effectMethods = new TreeMap< Method, Integer >( methodComparator  );
    if ( inverseMethods == null ) {
      inverseMethods = new TreeMap< Method, Method >( methodComparator );
    }
    if ( operationForMethod == null ) {
      operationForMethod = new TreeMap< Method, MathOperation >( methodComparator );
    }
    operationForMethod.clear();
    inverseMethods.clear();

    Method m = getSetValueMethod();
    if ( m != null ) effectMethods.put( m, 0 );
    m = getAddNumberAtTimeMethod();
    if ( m != null ) inverseMethods.put( m, getSubtractNumberAtTimeMethod() );
    if ( m != null ) effectMethods.put( m, 1 );
    if ( m != null ) operationForMethod.put( m, MathOperation.PLUS );
    m = getAddNumberForTimeRangeMethod();
    if ( m != null ) inverseMethods.put( m, getSubtractNumberForTimeRangeMethod() );
    if ( m != null ) effectMethods.put( m, 1 );
    if ( m != null ) operationForMethod.put( m, MathOperation.PLUS );
    m = getSubtractNumberAtTimeMethod();
    if ( m != null ) inverseMethods.put( m, getAddNumberAtTimeMethod() );
    if ( m != null ) effectMethods.put( m, 1 );
    if ( m != null ) operationForMethod.put( m, MathOperation.MINUS );
    m = getSubtractNumberForTimeRangeMethod();
    if ( m != null ) inverseMethods.put( m, getAddNumberForTimeRangeMethod() );
    if ( m != null ) effectMethods.put( m, 1 );
    if ( m != null ) operationForMethod.put( m, MathOperation.MINUS );
    m = getMultiplyNumberAtTimeMethod();
    if ( m != null ) inverseMethods.put( m, getDivideNumberAtTimeMethod() );
    if ( m != null ) effectMethods.put( m, 1 );
    if ( m != null ) operationForMethod.put( m, MathOperation.TIMES );
    m = getMultiplyNumberForTimeRangeMethod();
    if ( m != null ) inverseMethods.put( m, getDivideNumberForTimeRangeMethod() );
    if ( m != null ) effectMethods.put( m, 1 );
    if ( m != null ) operationForMethod.put( m, MathOperation.TIMES );
    m = getDivideNumberAtTimeMethod();
    if ( m != null ) inverseMethods.put( m, getMultiplyNumberAtTimeMethod() );
    if ( m != null ) effectMethods.put( m, 1 );
    if ( m != null ) operationForMethod.put( m, MathOperation.DIVIDE );
    m = getDivideNumberForTimeRangeMethod();
    if ( m != null ) inverseMethods.put( m, getMultiplyNumberForTimeRangeMethod() );
    if ( m != null ) effectMethods.put( m, 1 );
    if ( m != null ) operationForMethod.put( m, MathOperation.DIVIDE );

    //m = getSetValueMethod2();
    //m = TimeVaryingMap.class.getMethod("unsetValue");
    //m = TimeVaryingMap.class.getMethod("unapply");
    return effectMethods;
  }
  
  protected V getValueChangeAt(Parameter<Long> t, MathOperation op) {
    if ( t == null ) return null;
    // First determine original value applied
    Parameter<Long> k = getKey( t, true );
    if ( k == null ) {
      Debug.error(true, false, "WARNING!!! gettAddedValueAt(" + t + "): no entry found at time");
      return null;
    }
    if ( k != t ) {
      Debug.error(true, false, "WARNING!!! Unappyling effect with different time object: effect time = " + t.toString( true, false, null ) + "; found time = " + k.toString( true, false, null ));
    }
    V v = getValue(t);
    V vb = getValueBefore( t );
    V dv = v;
    try {
      //if ( vb == null ) dv = v;
      switch( op ) {
        case AND:  // vb and dv = v, so dv = v is fine
          break;
        case OR:  // vb or dv = v, so dv = v is fine
          break;
        case EQ: // vb == dv = v
          if ( v instanceof Boolean ) {
            if ((Boolean)v) {
              dv = vb;
            } else {
              dv = Functions.pickNotEqualToForward( new Expression<V>(dv), new Expression<V>(vb) );
            }
          }
          break;
        case GT:
          if ( v instanceof Boolean ) {
            if ((Boolean)v) {
              dv = Functions.pickGreater( new Expression<V>(vb), false );
            } else {
              dv = Functions.pickLess( new Expression<V>(vb), true );
            }
          }
          break;
        case GTE:
          if ( v instanceof Boolean ) {
            if ((Boolean)v) {
              dv = Functions.pickGreater( new Expression<V>(vb), true );
            } else {
              dv = Functions.pickLess( new Expression<V>(vb), false );
            }
          }
          break;
        case LT:
          if ( v instanceof Boolean ) {
            if ((Boolean)v) {
              dv = Functions.pickLess( new Expression<V>(vb), false );
            } else {
              dv = Functions.pickGreater( new Expression<V>(vb), true );
            }
          }
          break;
        case LTE:
          if ( v instanceof Boolean ) {
            if ((Boolean)v) {
              dv = Functions.pickLess( new Expression<V>(vb), true );
            } else {
              dv = Functions.pickGreater( new Expression<V>(vb), false );
            }
          }
          break;
        case NEQ:
          // TODO
          break;
        case NOT:
          Object o = Functions.not( v );
          dv = tryCastValue( o );
        case NEG:
          dv = Functions.times(v, -1);
          // N/A
          break;
        case DIVIDE:  // vb / dv = v, so dv = vb / v
          dv = Functions.divide( vb, v );
          break;
        case LOG:  // log base dv of vb is v, so dv^v = vb and dv = vb^(1/v)
          if ( vb instanceof Number && v instanceof Number ) {
            Double d = Math.pow( ( (Number)vb ).doubleValue(), 1.0 / ( (Number)values() ).doubleValue() );
            dv = tryCastValue( d );
          }
          break;
        case MAX: // dv = v
          break;
        case MIN: // dv = v
          break;
        case MINUS: // v = vb - dv, so dv = vb - v
          dv = Functions.minus( vb, v );
          break;
        case MOD:  // v = vb mod dv, so one answer is dv = vb - v;  the smallest number is the smallest factor of vb - v that is greater than v; so, it has to be in (v, vb-v].
          dv = Functions.minus( vb, v );
        case PLUS:
          dv = Functions.minus( v, vb );
          break;
        case POW:  // vb^dv = v, so log base vb of v = dv
          if ( vb instanceof Number && v instanceof Number ) {
            Double d = Math.log( ( (Number)v ).doubleValue() ) / Math.log( ( (Number)vb ).doubleValue() );
            dv = tryCastValue( d );
          }
          break;
        case TIMES:  // vb * dv = v, so dv = v / vb
          dv = Functions.divide( v, vb );
          break;
        default:
      }
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    } catch ( IllegalAccessException e ) {
      e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      e.printStackTrace();
    } catch ( InstantiationException e ) {
      e.printStackTrace();
    }
    return dv;
  }

  public static void testMapCallConstructor() {
    TimeVaryingMap<String> s1 = new TimeVaryingMap<>( "s1", String.class );
    s1.setValue( new SimpleTimepoint( 0 ), "hello" );
    s1.setValue( new SimpleTimepoint( 4 ), "world" );
    TimeVaryingMap<String> s2 = new TimeVaryingMap<>( "s1", String.class );
    s2.setValue( new SimpleTimepoint( 2 ), "hell" );
    s2.setValue( new SimpleTimepoint( 6 ), "o" );
    FunctionCall f = new FunctionCall( s1, String.class, "contains", new Object[]{ s2 }, Boolean.class );
    TimeVaryingMap<Boolean> b = new TimeVaryingMap<>( "s1_contains_s2", Boolean.class, f, STEP );
    System.out.println( "b = " + b );
  }
  
  // TODO -- make this a JUnit
  public static void main( String[] args ) {

    testMapCallConstructor();
    
    boolean succ = testGetFirstTimepointParameter();
    //Assert.assertTrue( succ );

    String fileName1 = "integerTimeline.csv";
    String fileName2 = "aggregateLoad.csv";
    
    TimeVaryingMap< Integer > intMap1 = null;
    TimeVaryingMap< Double > doubleMap2 =null;
    TimeVaryingMap< Double > doubleMap3 = null;
    TimeVaryingMap< Integer > intMap4 = null;
    TimeVaryingMap< Integer > intMap5 = null;
    TimeVaryingMap< Double > doubleMap6 = null;

    intMap1 = new TimeVaryingMap< Integer >( "integer_map", fileName1, Integer.class );
    System.out.println( "map1 loaded from " + fileName1 + ":\n" + intMap1 );
    doubleMap2 = new TimeVaryingMap< Double >( "double_map", fileName2, Double.class );

    //Assert.assertTrue( intMap1.isConsistent() );
   // Assert.assertTrue( doubleMap2.isConsistent() );

    System.out.println( "\nmap2 loaded from " + fileName2 + ":\n" + doubleMap2 );
    try {
      intMap1.multiply( 2, intMap1.firstKey(), null );
      System.out.println( "\nmap1 multiplied by 2:\n" + intMap1 );
      doubleMap3 = doubleMap2.plus( 12.12 );
      System.out.println( "\nnew map3 = map2 plus 12.12:\n" + doubleMap3 );
      doubleMap3 = doubleMap2.times( 1111, doubleMap2.firstKey(), doubleMap2.lastKey() );
      System.out.println( "\nmap3 = map2 times 1111 (except for the last entry):\n" + doubleMap3 );
      doubleMap3 = doubleMap2.times( 1111, doubleMap2.lastKey(), doubleMap2.lastKey() );
      System.out.println( "\nmap3 = map2 times 1111 (for just the last entry):\n" + doubleMap3 );

    //Assert.assertTrue( intMap1.isConsistent() );
    //Assert.assertTrue( doubleMap2.isConsistent() );
    //Assert.assertTrue( doubleMap3.isConsistent() );

    doubleMap3.add( intMap1 );
    System.out.println( "\nmap3 = map3 + map1:\n" + doubleMap3);
    doubleMap3.divide( 0.5 );
    System.out.println( "\nmap3 /= 0.5:\n" + doubleMap3);
    System.out.println( "map2:\n" + doubleMap2);
    doubleMap3 = doubleMap2.dividedBy( 2.0 );
    System.out.println( "\nmap3 = map2 / 2.0:\n" + doubleMap3);
    System.out.println( "map2:\n" + doubleMap2);
    doubleMap3 = doubleMap2.dividedBy( 2 );
    System.out.println( "\nmap3 = map2 / 2:\n" + doubleMap3);
    System.out.println( "map2:\n" + doubleMap2);


//    Assert.assertTrue( intMap1.isConsistent() );
//    Assert.assertTrue( doubleMap2.isConsistent() );
//    Assert.assertTrue( doubleMap3.isConsistent() );

    System.out.println( "map1:\n" + intMap1);
    doubleMap3 = intMap1.dividedBy( 2.0 );
    System.out.println( "\nmap3 = map1 / 2.0:\n" + doubleMap3 );

//    Assert.assertTrue( intMap1.isConsistent() );
//    try {
//      Assert.assertTrue( doubleMap3.isConsistent() ); // TODO -- THIS CURRENTLY FAILS!
//    } catch ( AssertionFailedError e ) {
//      System.err.println("Caught assertion failure and continuing.");
//    }

    intMap1.clear();
    doubleMap2.clear();
    doubleMap3.clear();
    intMap4 = new TimeVaryingMap< Integer >();

    // some timepoints to use
    Timepoint zero = new Timepoint( "zero", 0L, null );
    Timepoint one = new Timepoint( "one", 1L, null );
    Timepoint two = new Timepoint( "two", 2L, null );
    Timepoint four = new Timepoint( "four", 4L, null );
    Timepoint eight = new Timepoint( "eight", 8L, null );
    Timepoint zero2 = new Timepoint( "zero", 0L, null );
    Timepoint one2 = new Timepoint( "one", 1L, null );
    Timepoint two2 = new Timepoint( "two", 2L, null );
    Timepoint four2 = new Timepoint( "four", 4L, null );
    Timepoint eight2 = new Timepoint( "eight", 8L, null );

    intMap1.setValue( zero, 0 );
    intMap1.setValue( one, 2 );
    intMap1.setValue( two, 4 );

    intMap4.setValue( eight, 0 );

    intMap5 = intMap1.plus( intMap4 );
    System.out.println( "\nmap5 = map1 + map4 = " + intMap1 + " + " + intMap4
                        + " = " + intMap5 + "\n");
    intMap5 = intMap1.minus( intMap4 );
    System.out.println( "\nmap5 = map1 - map4 = " + intMap1 + " - " + intMap4
                        + " = " + intMap5 + "\n" );
    intMap5 = intMap4.plus( intMap1 );
    System.out.println( "\nmap5 = map4 + map1 = " + intMap4 + " + " + intMap1
                        + " = " + intMap5 + "\n" );
    intMap5 = intMap4.minus( intMap1 );
    System.out.println( "\nmap5 = map4 - map1 = " + intMap4 + " - " + intMap1
                        + " = " + intMap5 + "\n" );

    intMap4.setValue( four, 21 );
    intMap4.setValue( eight, 3 );

    intMap5 = intMap1.plus( intMap4 );
    System.out.println( "\nmap5 = map1 + map4 = " + intMap1 + " + " + intMap4
                        + " = " + intMap5 + "\n" );
    intMap5 = intMap1.minus( intMap4 );
    System.out.println( "\nmap5 = map1 - map4 = " + intMap1 + " - " + intMap4
                        + " = " + intMap5 + "\n" );
    intMap5 = intMap4.plus( intMap1 );
    System.out.println( "\nmap5 = map4 + map1 = " + intMap4 + " + " + intMap1
                        + " = " + intMap5 + "\n" );
    intMap5 = intMap4.minus( intMap1 );
    System.out.println( "\nmap5 = map4 - map1 = " + intMap4 + " - " + intMap1
                        + " = " + intMap5 + "\n" );

//    Assert.assertTrue( intMap1.isConsistent() );
//    Assert.assertTrue( intMap4.isConsistent() );
//    Assert.assertTrue( intMap5.isConsistent() );

    doubleMap2.setValue( zero, 0.0 );
    doubleMap2.setValue( one, 2.0 );
    doubleMap2.setValue( two, 4.0 );

    doubleMap3.setValue( eight, 0.0 );

    doubleMap6 = doubleMap2.plus( doubleMap3 );
    System.out.println( "\nmap6 = map2 + map3 = " + doubleMap2 + " + " + doubleMap3
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap2.minus( doubleMap3 );
    System.out.println( "\nmap6 = map2 - map3 = " + doubleMap2 + " - " + doubleMap3
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap3.plus( doubleMap2 );
    System.out.println( "\nmap6 = map3 + map2 = " + doubleMap3 + " + " + doubleMap2
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap3.minus( doubleMap2 );
    System.out.println( "\nmap6 = map3 - map2 = " + doubleMap3 + " - " + doubleMap2
                        + " = " + doubleMap6 );

    doubleMap3.setValue( four, 21.0 );
    doubleMap3.setValue( eight, 3.0 );

    doubleMap6 = doubleMap2.plus( doubleMap3 );
    System.out.println( "\nmap6 = map2 + map3 = " + doubleMap2 + " + " + doubleMap3
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap2.minus( doubleMap3 );
    System.out.println( "\nmap6 = map2 - map3 = " + doubleMap2 + " - " + doubleMap3
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap3.plus( doubleMap2 );
    System.out.println( "\nmap6 = map3 + map2 = " + doubleMap3 + " + " + doubleMap2
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap3.minus( doubleMap2 );
    System.out.println( "\nmap6 = map3 - map2 = " + doubleMap3 + " - " + doubleMap2
                        + " = " + doubleMap6 );

    // multiple timepoints with the same time value
    System.out.println("\nmultiple timepoints with the same time value");
    doubleMap2.putAll( doubleMap3 );
    doubleMap2.removeDuplicates();
    doubleMap2.substitute( zero, zero2, false, null );
    doubleMap2.substitute( two, two2, false, null );
    doubleMap3.substitute( eight, eight2, false, null );

    doubleMap6 = doubleMap2.plus( doubleMap3 );
    System.out.println( "\nmap6 = map2 + map3 = " + doubleMap2 + " + " + doubleMap3
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap2.minus( doubleMap3 );
    System.out.println( "\nmap6 = map2 - map3 = " + doubleMap2 + " - " + doubleMap3
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap3.plus( doubleMap2 );
    System.out.println( "\nmap6 = map3 + map2 = " + doubleMap3 + " + " + doubleMap2
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap3.minus( doubleMap2 );
    System.out.println( "\nmap6 = map3 - map2 = " + doubleMap3 + " - " + doubleMap2
                        + " = " + doubleMap6 );

//    Assert.assertTrue( doubleMap2.isConsistent() );
//    Assert.assertTrue( doubleMap3.isConsistent() );
//    Assert.assertTrue( doubleMap6.isConsistent() );


    System.out.println("\nmaps with nulls");

    doubleMap3.setValue( zero, null);
    doubleMap3.setValue( one, null );
    doubleMap3.setValue( two, 4.0 );
    doubleMap3.setValue( four, null );
    doubleMap3.setValue( eight, null );

    doubleMap6 = doubleMap2.plus( doubleMap3 );
    System.out.println( "\nmap6 = map2 + map3 = " + doubleMap2 + " + " + doubleMap3
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap2.minus( doubleMap3 );
    System.out.println( "\nmap6 = map2 - map3 = " + doubleMap2 + " - " + doubleMap3
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap3.plus( doubleMap2 );
    System.out.println( "\nmap6 = map3 + map2 = " + doubleMap3 + " + " + doubleMap2
                        + " = " + doubleMap6 );
    doubleMap6 = doubleMap3.minus( doubleMap2 );
    System.out.println( "\nmap6 = map3 - map2 = " + doubleMap3 + " - " + doubleMap2
                        + " = " + doubleMap6 );

    HashMap< String, TimeVaryingMap<?> > tvmapMap = new LinkedHashMap< String, TimeVaryingMap<?> >();
    tvmapMap.put( "map1", intMap1 );
    tvmapMap.put( "map2", doubleMap2 );
    tvmapMap.put( "map3", doubleMap3 );
    tvmapMap.put( "map4", intMap4 );
    tvmapMap.put( "map5", intMap5 );
    tvmapMap.put( "map6", doubleMap6 );

    System.out.println("\nargmin and argmax");

    for ( Map.Entry< String, TimeVaryingMap< ? > > e1 : tvmapMap.entrySet() ) {
      for ( Map.Entry< String, TimeVaryingMap< ? > > e2 : tvmapMap.entrySet() ) {
        TimeVaryingMap<String> labelMap = argmax( e1.getKey(), e1.getValue(), e2.getKey(), e2.getValue() );
        System.out.println( "\nlabelMap = argmax(" + e1.getKey() + ", " + e2.getKey() + ") = argmax(" + e1.getValue() + "," + e2.getValue() + ") = " + labelMap );
        //Assert.assertTrue( labelMap.isConsistent() );
        labelMap = argmin( e1.getKey(), e1.getValue(), e2.getKey(), e2.getValue() );
        System.out.println( "labelMap = argmin(" + e1.getKey() + ", " + e2.getKey() + ") = argmin(" + e1.getValue() + "," + e2.getValue() + ") = " + labelMap );
        //Assert.assertTrue( labelMap.isConsistent() );
      }
    }
//    TimeVaryingMap<String> labelMapMax26 = argmax( "map2", doubleMap2, "map6", doubleMap6 );
//    System.out.println( "\nlabelMap = argmax(map2, map6) = argmin(" + doubleMap2 + "," + doubleMap6 + ") = " + labelMapMax26 );
//    
//    TimeVaryingMap<String> labelMapMin36 = argmin( "map3", doubleMap3, "map6", doubleMap6 );
//    System.out.println( "\nlabelMap = argmin(map3, map6) = argmin(" + doubleMap3 + "," + doubleMap6 + ") = " + labelMapMin36 );

//    Assert.assertTrue( doubleMap2.isConsistent() );
//    Assert.assertTrue( doubleMap3.isConsistent() );
//    Assert.assertTrue( doubleMap6.isConsistent() );

    testJavaPlot( null );
    } catch ( IllegalAccessException e1 ) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    } catch ( InvocationTargetException e1 ) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    } catch ( InstantiationException e1 ) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
  }
  
    public static void testJavaPlot(String[] args) {
//        JavaPlot p = new JavaPlot();
//        p.addPlot("sin(x)");
//        p.plot();
    }

  @Override
  public boolean canBeApplied( Effect effect ) {
    if ( effect == null ) return false;
    // checks to see if the timepoint is valid
    if ( effect instanceof EffectFunction ) {
      EffectFunction effectFunction = (EffectFunction)effect;
      Parameter< Long > t = getTimeOfEffect( effectFunction );
      return isTimepoint( t ) && t.getValueNoPropagate() >= 0;

    }
    return true;
  }

  /**
   * Both checks and asserts that an effect was applied.
   * @param effect
   * @return whether the effect was applied before this call
   */
  public boolean wasApplied( Effect effect ) {
    return !appliedSet.add(effect);
  }

  @Override
  public < T > T translate( Variable< T > p , Object o , Class< ? > type  ) {
    return null;
  }

  /**
   * Create a new map that is a translation in time of this map as a function of
   * time. For example, <code>g = f.translate(5)</code> is interpreted as
   * <code>g(t) = f(t - 5)</code> and shifts values in <code>f</code> forward 5
   * time units to get <code>g</code>.
   * 
   * @param timeDelta
   *          the time by which the map is translated
   * @return a new map translated by o in time.
   */
  public TimeVaryingMap<V> translate( Object timeDelta ) {
    if ( timeDelta == null ) return null;
    try {
      Long t = Expression.evaluate( timeDelta, Long.class, true, false );
      if ( t == null ) return null;
      return translate( t );
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    } catch ( IllegalAccessException e ) {
      e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      e.printStackTrace();
    } catch ( InstantiationException e ) {
      e.printStackTrace();
    }
    return null;
  }
  
  /**
   * Create a new map that is a translation in time of this map as a function of
   * time. For example, <code>g = f.translate(5)</code> is interpreted as
   * <code>g(t) = f(t - 5)</code> and shifts values in <code>f</code> forward 5
   * time units to get <code>g</code>.
   * 
   * @param timeDelta
   *          the time by which the map is translated
   * @return a new map translated by o in time.
   */
  public TimeVaryingMap<V> translate( Long timeDelta ) {
    if ( timeDelta == null ) return null;
    TimeVaryingMap<V> tvm = emptyClone();
    Iterator< Entry<Parameter<Long>, V > > i = timeDelta < 0 ? entrySet().iterator() : descendingMap().entrySet().iterator();
    
    // Determine value at time zero
    if ( timeDelta > 0 && interpolation != TimeVaryingMap.NONE && !isEmpty() ) {
      Parameter< Long > zero = keySet().iterator().next();
      if ( zero == null || zero.getValueNoPropagate() != 0L ) {
        zero = new SimpleTimepoint( "", 0L, this );
      }
      V v = getValue( zero );
      if ( v != null ) {
        put( zero, v );
      }
    }
    
    while ( i.hasNext() ) {
      Entry<Parameter<Long>, V > e = i.next();
      Parameter<Long> oldTime = e.getKey();
      Long newT = oldTime.getValue() + timeDelta;
      if ( newT >= 0 ) {
        SimpleTimepoint newTime = new SimpleTimepoint( "", newT, tvm );
        tvm.put( newTime, e.getValue() );
      }
    }
    return tvm;
  }
  
  public TimeVaryingMap<V> shift( Long t ) {
    return translate( t );
  }
  public  TimeVaryingMap<V> shift( Object o ) {
    return translate( o );
  }
  
  public TimeVaryingMap<V> sample( Long period, Interpolation interpolation ) {
    if ( period == null || period == 0L ) return null;
    TimeVaryingMap<V> tvm = emptyClone();
    tvm.interpolation = interpolation;
    Long lastTime = null;
    Long nextTime = null;
    V lastValue = null;
    V nextValue = null;
    for ( Map.Entry< Parameter<Long>, V > e : entrySet() ) {
      nextTime = e.getKey().getValue();
      nextValue = e.getValue();
      if ( lastTime != null ) {
        Long t = lastTime + period;
        while ( t < e.getKey().getValue() ) {
          V v = lastValue;
          if ( interpolation == LINEAR ) {
            v = interpolatedValue( t, lastTime, nextTime, lastValue, nextValue );
          }
          tvm.put( new SimpleTimepoint( "", t, tvm ), v );
          t += period;
        }
      }
      tvm.put( e.getKey(), e.getValue() );
      lastTime = e.getKey().getValue();
      lastValue = e.getValue();
    }
    return tvm;
  }
  
  public TimeVaryingMap<V> snapToMinuteIncrement( Long minutes, boolean earlier ) {
    return snapToTimeIncrement( Timepoint.minutes( minutes ), earlier );
  }

  public TimeVaryingMap<V> snapToTimeIncrement( Long timeIncrement, boolean earlier ) {
    if ( timeIncrement == null || timeIncrement == 0L ) return null;
    TimeVaryingMap<V> tvm = emptyClone();
    for ( Map.Entry< Parameter<Long>, V > e : entrySet() ) {
      Long t = e.getKey().getValueNoPropagate();
      Long offset = t % timeIncrement;
      if ( offset > 0 ) {
        if ( earlier ) {
          t = t - offset;
        } else {
          t = t + timeIncrement - offset;
        }
      }
      SimpleTimepoint k = new SimpleTimepoint( "", t, tvm );
      tvm.put( k, e.getValue() );
    }
    return tvm;
  }

  /**
   * @return a generated map of the changes in values from the previous point.
   *         <p>
   *         For example, the deltaMap of { 0=0.0, 3=4.4, 7=10.0 } is { 0=0.0,
   *         3=4.4, 7=5.6 }. The map is fully computed on each call. For any
   *         Consumable c, c.initializeFromDeltaMap(c.getDeltaMap()) should not
   *         change the entries in c.
   *         
   *         TODO -- REVIEW -- This is similar to differentiation.
   */
 public TimeVaryingMap< Double > getDeltaMap() {
    TimeVaryingPlottableMap< Double > deltaMap =
      new TimeVaryingPlottableMap< Double >( "delta_" + name );
    Double lastValue = 0.0;
    for ( Entry< Parameter< Long>, V > e : entrySet() ) {
       Double thisValue = 0.0; // null value is interpreted as 0.0
      if ( e.getValue() instanceof Number ) {
        thisValue = ( (Number)e.getValue() ).doubleValue();
      }
      deltaMap.put( e.getKey(), thisValue - lastValue );
      lastValue = thisValue;
    }
    deltaMap.interpolation = NONE;
    return deltaMap;
  }
 
 
 public TimeVaryingMap< Boolean > validTime(Call call, int argIndexOfValue, Object[] otherArgs) {
   // TODO? -- HERE!!!  -- Who's supposed to call this?
   Collection< Object > x = call.map( this.values(), argIndexOfValue );
   return null;
 }

 public TimeVaryingMap< Boolean >
 validTimeForSetValue( EffectFunction ef, boolean basedOnDomainsAndNotValues ) {
   try {
     if ( !basedOnDomainsAndNotValues ) {
       V v = (V)getValueOfEffect( ef );
       Boolean b = domain.contains( v );
       return new TimeVaryingMap< Boolean >( "", b, Boolean.class );
     } else {
       Domain< V > d = domainOfEffectValueArgument( ef );
       Boolean b = !mutuallyExclusive( domain, d );
       return new TimeVaryingMap< Boolean >( "", b, Boolean.class );
     }
   } catch ( Throwable t ) {
     t.printStackTrace();
     return null;
   }
 }

 /**
  * Get the time intervals where the effect could be moved.
  *  If looking at just the currently assigned values and not the domains,
  *  then we just need to walk through the timepoints where the effect
  *  could occur and see if it's outside the domain.
  * @param ef the effect to move
  * @return a Boolean TimeVaryingMap specifying the valid time intervals.
  */
 public TimeVaryingMap< Boolean > validTime( EffectFunction ef ) {
   TimeVaryingMap< Boolean > tvm =
       new TimeVaryingMap< Boolean >( "", false, Boolean.class );

   // The time of the effect is already restricted by its own domain.
   Parameter< Long > t = this.getTimeOfEffect( ef );
   if ( t == null ) return null;
   Domain< Long > timepointDomain = t.getDomain();
   if ( timepointDomain.isEmpty() ) {
     return new TimeVaryingMap< Boolean >( "", false, Boolean.class );
   }

   MathOperation op = getOperationForMethod( ef.getMethod() );
   if ( op == null ) {
     return null;
   }
   V v = (V)getValueOfEffect( ef );

   Class< V > resultType =
       (Class< V >)ClassUtils.dominantTypeClass( getType(),
                                                 domain.getType() );
   RangeDomain< Long > trd = (RangeDomain)timepointDomain;
   Long timeLowerBound = trd.getLowerBound();
   Long timeUpperBound = trd.getUpperBound();
   Parameter< Long > tb = getTimepointBefore( timeLowerBound );
   if ( tb == null ) tb = firstKey();
   NavigableMap< Parameter< Long >, V > m =
       subMap( tb, true, lastKey(), true );
   for ( Entry< Parameter< Long >, V > entry : m.entrySet() ) {
     if ( entry.getKey().getValueNoPropagate() > timeUpperBound ) {
       tvm.setValue( entry.getKey(), false );
       break;
     }
     V val = entry.getValue();
     V newVal = null;
     try {
       Object r = applyOperation( val, v, op );
       newVal = tryCastValue( r );
       Boolean b = domain.contains( newVal );
       tvm.setValue( entry.getKey(), b );
     } catch ( ClassCastException e1 ) {
       e1.printStackTrace();
     } catch ( IllegalAccessException e1 ) {
       e1.printStackTrace();
     } catch ( InvocationTargetException e1 ) {
       e1.printStackTrace();
     } catch ( InstantiationException e1 ) {
       e1.printStackTrace();
     }
   }
   return tvm;

 }

 
 /**
  * Get the time intervals where the effect could be moved.
  * 
  * @param e the effect to move
  * @param basedOnDomainsAndNotValues
  * @return a Boolean TimeVaryingMap specifying the valid time intervals.
  */
  public TimeVaryingMap< Boolean >
         validTime( Effect e, boolean basedOnDomainsAndNotValues ) {
    if ( domain == null ) {
      return new TimeVaryingMap< Boolean >( "", true, Boolean.class );
    }
    if ( !( e instanceof EffectFunction ) ) {
      Debug.error( "validTime() expects an EffectFunction." );
      return null;
    }
    EffectFunction ef = (EffectFunction)e;
    if ( ef.getMethod() != null
         && ef.getMethod().getName().contains( "setValue" ) ) {
      return validTimeForSetValue( ef, basedOnDomainsAndNotValues );
    }

    // We might quickly determine that the effect is always bad.
    Domain< V > d = domainForEffect( ef, basedOnDomainsAndNotValues );
    Boolean alwaysBad = mutuallyExclusive( domain, d );
    if ( alwaysBad ) {
      return new TimeVaryingMap< Boolean >( "", false, Boolean.class );
    }

    // See if there are times when the effect must be bad.
    // This would be when other effects' times are constrained to certain
    // ranges such that, in combination with this effect, yield values that are
    // all outside the domain.

    // The time of the effect is already restricted by its own domain.
    Parameter< Long > t = this.getTimeOfEffect( ef );
    if ( t == null ) return null;
    Domain< Long > timepointDomain = t.getDomain();
    if ( timepointDomain.isEmpty() ) {
      return new TimeVaryingMap< Boolean >( "", false, Boolean.class );
    }

    if ( !basedOnDomainsAndNotValues ) {
      return validTime(ef);
    }
    return validTimeForDomains( ef );  
  }

  public void dontWantToDelete(ComparableDomain<Long> timepointDomain) {
    Set< Parameter< Long > > mustBeBefore = new LinkedHashSet< Parameter< Long > >();
    Set< Parameter< Long > > mayBeBefore = new LinkedHashSet< Parameter< Long > >();
    
    for ( Parameter<Long> tp : keySet() ) {
      if ( tp == null ) continue;
      Domain<Long> tDomain = tp.getDomain();
      if ( tDomain == null ) continue;
      if ( tDomain instanceof ComparableDomain && timepointDomain instanceof ComparableDomain ) {
        if ( ( (ComparableDomain< Long >)tDomain ).less( (ComparableDomain< Long >)timepointDomain ) ) {
          mustBeBefore.add( tp );
        } else if ( !( (ComparableDomain< Long >)tDomain ).greater( (ComparableDomain< Long >)timepointDomain ) ) {
          mayBeBefore.add( tp );
        }
      } else if ( timepointDomain.magnitude() == 1 && tDomain instanceof ComparableDomain ) {
        Long tpt = timepointDomain.getValue( true );
        if ( ( (ComparableDomain< Long >)tDomain ).less( tpt ) ) {
          mustBeBefore.add( tp );
        } else if ( !( (ComparableDomain< Long >)tDomain ).greater( tpt ) ) {
          mayBeBefore.add( tp );
        }
      } else if ( tDomain.magnitude() == 1 && timepointDomain.magnitude() == 1 ) {
        Long tpt = timepointDomain.getValue( true );
        Long td = tDomain.getValue( true );
        try {
          if ( Boolean.TRUE.equals(Functions.lessThan( td, tpt )) ) {
            mustBeBefore.add( tp );
          } else if (Boolean.FALSE.equals(Functions.greaterThan( td, tpt )) ) {
            mayBeBefore.add( tp );
          }
        } catch ( IllegalAccessException e1 ) {
          e1.printStackTrace();
        } catch ( InvocationTargetException e1 ) {
          e1.printStackTrace();
        } catch ( InstantiationException e1 ) {
          e1.printStackTrace();
        }
      }
    }
    
  }
  
  /**
   * Get the time intervals where the effect could be moved based on the domains
   * of the timepoints and values of the effects on this timeline,
   * 
   * Determine if there are other effects that must overlap in time with domain
   * of the time of the effect. For add(amount, time), any timepoint on the
   * timeline that is always before this one will contribute to this effect.
   * 
  * @param ef the effect to move
  * @return a Boolean TimeVaryingMap specifying the valid time intervals.
   */
  public TimeVaryingMap< Boolean > validTimeForDomains( EffectFunction ef ) {

    Parameter< Long > t = this.getTimeOfEffect( ef );
    if ( t == null ) return null;
    Domain< Long > timepointDomain = t.getDomain();
    if ( timepointDomain.isEmpty() ) {
      return new TimeVaryingMap< Boolean >( "", false, Boolean.class );
    }

    TimeVaryingMap< Boolean > tvm =
        new TimeVaryingMap< Boolean >( "", false, Boolean.class );

    // If zero is in the domain of the time of this effect, then nothing is
    // definitely before this one, and we can assume there is no restriction.
    if ( timepointDomain == null || timepointDomain.contains( 0L ) ) {
      return new TimeVaryingMap< Boolean >( "", true, Boolean.class );
    }

    TimeVaryingMap< Effect > effectMap = new TimeVaryingMap< Effect >();
    for ( Effect eff : appliedSet ) {
      Pair< Parameter< Long >, Object > p = getTimeAndValueOfEffect( eff );
      if ( p != null && p.first != null ) {
        effectMap.setValue( p.first, eff );
      }
    }

    Domain< V > mustEffectDomain = null;
    MultiDomain< V > mayEffectDomain = new MultiDomain< V >();

    TreeMap< Long, Set< Parameter< Long > > > domainTimepoints =
        new TreeMap< Long, Set< Parameter< Long > > >();

    // Gather endpoints of domain.
    for ( Parameter< Long > tp : keySet() ) {
      if ( tp == null ) continue;
      Domain< Long > tDomain = tp.getDomain();
      if ( tDomain == null ) continue;

      if ( tDomain instanceof ComparableDomain && !tDomain.isEmpty() ) {
        Long lb = ( (ComparableDomain< Long >)tDomain ).getLowerBound();
        if ( lb != null ) {
          Utils.add( domainTimepoints, lb, tp );
          // domainTimepoints.put( lb, tDomain );
        }
        Long ub = ( (ComparableDomain< Long >)tDomain ).getUpperBound();
        if ( ub != null ) {
          Utils.add( domainTimepoints, ub, tp );
        }
      }
    }

    // For each domain timepoint determine whether the effect will drive the
    // timeline value outside its domain and set the valid timeline, tvm, to
    // true or false for that timepoint.
    MathOperation op = getOperationForMethod( ef.getMethod() );
    if ( op == null ) {
      return null;
    }

    for ( Entry< Long, Set< Parameter< Long > > > ntry : domainTimepoints.entrySet() ) {
      Long dtp = ntry.getKey();

      // Anything outside the domain of the effect is invalid.
      if ( timepointDomain != null && !timepointDomain.contains( dtp ) ) {
        tvm.setValue( new SimpleTimepoint( "", dtp, tvm ), false );
        continue;
      }

      // TODO -- this might be faster if not iterating through every for every
      // for the time domain endpoints of every entry. If we build the set of
      // entries associated with each domainTimepoint, and we kept a set of
      // pastEffects and potentialEffects updated for the entries at each
      // domainTimepoint,
      // then we could many times reuse the previous domain calculations and
      // avoid the O(n^2) complexity below. The computation after the inner loop
      // would need to allocate a different mayEffectDomain so that it can be
      // reused.
      mustEffectDomain = null;
      mayEffectDomain.includeSet = null;
      for ( Entry< Parameter< Long >, V > entry : entrySet() ) {
        Parameter< Long > tp = entry.getKey();

        Domain< Long > tDom = tp.getDomain();
        if ( tDom == null || tDom instanceof SingleValueDomain ) {
          tDom = new LongDomain( tp.getValue(), tp.getValue() );
        }

        // Find out if this entry must have happened before or by dtp.
        Long latestTimeOfEntry = tp.getValue();
        if ( tDom instanceof ComparableDomain ) {
          latestTimeOfEntry = ((ComparableDomain< Long >)tDom).getUpperBound();
        }
        boolean pastEffect = latestTimeOfEntry != null && latestTimeOfEntry <= dtp;

        Effect eff = effectMap.get( tp );
        if ( eff == null || !( eff instanceof EffectFunction ) ) {
          // Treat this like a setValue effect--no math operation.
          V val = get( tp );
          if ( val == null ) {
            Debug.error( "Error! No value for timepoint!" );
            continue;
          }
          Domain< V > valDomain = DomainHelper.getDomain( val );
          if ( valDomain != null ) {
            if ( pastEffect ) {
              mustEffectDomain = valDomain;
            } else {
              mayEffectDomain.include( valDomain );
            }
          }
        } else {
          EffectFunction efff = (EffectFunction)eff;
          if ( pastEffect ) {
            if ( mustEffectDomain == null ) {
              mustEffectDomain = domainForEffect( efff, true );
            } else {
              mustEffectDomain =
                  domainForEffect( efff, mustEffectDomain, true );
            }
          } else {
            if ( Utils.isNullOrEmpty( mayEffectDomain.includeSet ) ) {
              Domain< V > aloneDomain = domainForEffect( efff, true );
              mayEffectDomain.include( aloneDomain );
            } else {
              Domain< V > combinedDomain =
                  domainForEffect( efff, mayEffectDomain, true );
              mayEffectDomain.include( combinedDomain );
            }
          }
        }
      }

      Domain< V > combinedDomain =
          domainForOperation( op, mustEffectDomain, mayEffectDomain );
      mayEffectDomain.include( mustEffectDomain );
      mayEffectDomain.include( combinedDomain );
      Domain< V > effectDomain = domainForEffect( ef, mayEffectDomain, true );
      boolean mayBeValid = !mutuallyExclusive( domain, effectDomain );
      tvm.setValue( new SimpleTimepoint( "", dtp, tvm ), mayBeValid );
    }
    return tvm;
  }

  public boolean mutuallyExclusive( Domain< V > d1, Domain< V > d2 ) {

    Domain< V > copy = d1.clone();
    if ( !d1.equals( copy ) ) {
      Debug.error( "Error! Expected domain to be equal to its clone!" );
    }
    Domain< V > x = copy.subtract( d2 );
    // If the copy domain did not change after subtracting, then the
    // domains do not intersect.
    Boolean b = d1.equals( copy );
    return b;
  }
 
  public Domain< V > domainOfEffectValueArgument( EffectFunction ef ) {
    int i = getIndexOfValueArgument( ef );
    Object valueArg = ef.getArgument( i );
    Domain< V > d = (Domain< V >)DomainHelper.getDomain( valueArg );
    if ( d == null ) {
      d = new SingleValueDomain( getValueOfEffect( ef ) );
    }
    return d;
  }
 
  public Domain< V > domainForEffect( EffectFunction ef,
                                      boolean basedOnDomainsAndNotValues ) {
    Domain< V > inputDomain = getDomain( false, null );
    return domainForEffect( ef, inputDomain, basedOnDomainsAndNotValues );
  }

  private class OpRunner<VV, VVV> {
    MathOperation op;
    Class<VVV> resultClass;
    public OpRunner( MathOperation op, Class<VVV> resultClass ) {
      this.op = op;
      this.resultClass = resultClass;
    }
    public VVV apply( V v1, VV v2 ) throws ClassCastException, IllegalAccessException, InvocationTargetException, InstantiationException {
      Object o = applyOperation( v1, v2, op );
      if ( resultClass == null ) return (VVV)o;
      VVV vvv = Expression.evaluate( o, resultClass, true );
      return (VVV)o;
    }
  }
  
  public Domain<V> domainForOperation( MathOperation op, Domain<V> d1, Domain<V> d2 ) {
    OpRunner< V, V > opr = new OpRunner< V, V >( op, getType() );
    V v = d1.getValue( false );
    FunctionCall fc =
        new FunctionCall( opr, OpRunner.class, "apply",
                          new Object[] { v, v }, getType() );
//    if ( copy instanceof RangeDomain
//         || copy instanceof SingleValueDomain ) {
//      if ( vd instanceof RangeDomain || vd instanceof SingleValueDomain ) {
    Domain< V > ddd = (Domain< V >)DomainHelper.combineDomains( Utils.newList( (Object)d1,
                                                                               (Object)d2 ),
                                                                fc, true );
    return ddd;
    //      }
//    }

  }
  
  public Domain< V > domainForEffect( EffectFunction ef,
                                      Domain< V > inputDomain,
                                      boolean basedOnDomainsAndNotValues ) {
      Domain< V > d = inputDomain;
      if ( d == null ) return null;
      Domain< V > copy = d.clone();
      if ( d.isEmpty() ) return copy;
      V v = (V)getValueOfEffect( ef );
      MathOperation op = getOperationForMethod( ef.getMethod() );
      if ( op == null ) {
        return null;
      }
      Class< V > resultClass =
          (Class< V >)ClassUtils.dominantTypeClass( getType(),
                                                    domain.getType() );

    if ( basedOnDomainsAndNotValues ) {
      Domain< V > vd = domainOfEffectValueArgument( ef );
      if ( vd instanceof SingleValueDomain ) {
        // Do nothing -- we can evaluate based on the value instead of the
        // domain.
      } else if ( vd instanceof RangeDomain ) {
        RangeDomain< V > rv = (RangeDomain< V >)vd;

        Domain< V > ddd = domainForOperation(op, copy, rv);
//        // Domain< V > d1 = domainForEffect
//        OpRunner< V, V > opr = new OpRunner< V, V >( op, resultClass );
//        FunctionCall fc =
//            new FunctionCall( opr, OpRunner.class, "apply",
//                              new Object[] { v, v }, resultClass );
//        if ( copy instanceof RangeDomain
//             || copy instanceof SingleValueDomain ) {
//          if ( vd instanceof RangeDomain || vd instanceof SingleValueDomain ) {
//            Domain< V > ddd =
//                (Domain< V >)DomainHelper.combineDomains( Utils.newList( (Object)copy,
//                                                                         (Object)vd ),
//                                                          fc );
            return ddd;
//          }
//        }
//        return null;
      } else {
        return null;
      }
    }
      
    try {
      if ( copy instanceof SingleValueDomain ) {
        Object o = applyOperation( copy.getValue( false ), v, op );
        V v1 = tryCastValue( o );
        copy.setValue( v1 );
        return copy;
      } else if ( copy instanceof MultiDomain ) {
        MultiDomain< V > md = (MultiDomain< V >)copy;
        Set< Domain< V > > fd = md.getFlattenedSet();
        if ( fd == null ) {
          fd = md.includeSet;
        }
        Set< Domain< V > > newSet = new LinkedHashSet< Domain< V > >();
        for ( Domain< V > dd : fd ) {
          Domain< V > nd =
              domainForEffect( ef, dd, basedOnDomainsAndNotValues );
          if ( nd != null && !nd.isEmpty() ) {
            newSet.add( nd );
          }
        }
        if ( newSet.isEmpty() ) {
          copy.clearValues();
          return copy;
        }
        if ( newSet.size() == 1 ) {
          return newSet.iterator().next();
        }
        return new MultiDomain< V >( resultClass, newSet, null );
      } else if ( copy instanceof AbstractRangeDomain ) {
        AbstractRangeDomain< V > ard = (AbstractRangeDomain< V >)copy;
        V v1 = applyOperation( ard.getLowerBound(), v, op, resultClass );
        V v2 = applyOperation( ard.getUpperBound(), v, op, resultClass );
        if ( ard.less( v2, v1 ) ) {
          ard.setBounds( v2, v1 );
        } else {
          ard.setBounds( v1, v2 );
        }
        return ard;
      }
    } catch ( ClassCastException e ) {
      e.printStackTrace();
    } catch ( IllegalAccessException e ) {
      e.printStackTrace();
    } catch ( InvocationTargetException e ) {
      e.printStackTrace();
    } catch ( InstantiationException e ) {
      e.printStackTrace();
    }
    return null;
  }
 
  private < VV, VVV > VVV
          applyOperation( V v1, VV v2, MathOperation op,
                          Class< VVV > resultClass ) throws ClassCastException,
                                                     IllegalAccessException,
                                                     InvocationTargetException,
                                                     InstantiationException {
    Object o = applyOperation( v1, v2, op );
    if ( resultClass == getType() ) {
      V v = tryCastValue( o );
      return (VVV)v;
    }
    VVV v = Expression.evaluate( o, resultClass, true );
    return v;
  }
 
  
// public V effectResult( EffectFunction ef, V inputValue) {
//   V ev = (V)getValueOfEffect( ef );
//   Object r = this.applyOp( inputValue, ev, boolOp );
//   return null;
// }
   
  @Override
  public String getQualifiedName( Set< Object > seen ) {
    String n = HasOwner.Helper.getQualifiedName( this, null );
    return n;
  }
  @Override
  public String getQualifiedId( Set< Object > seen ) {
    String n = HasOwner.Helper.getQualifiedId( this, null );
    return n;
  }

  @Override
  public List< Variable< ? > >
         getVariablesOnWhichDepends( Variable< ? > variable ) {
    // TODO?!
    Debug.error(true, false,  "Warning!  The TimeVaryingMap.getVariablesOnWhichDepends() function is not yet implemented and should not be called." );
    return null;
  }

  @Override
  public Domain< V > getDomain( boolean propagate, Set< HasDomain > seen ) {
    return domain;
  }
  
  @Override
  public < T > Pair< Domain< T >, Boolean >
         restrictDomain( Domain< T > domain, boolean propagate,
                         Set< HasDomain > seen ) {
    boolean changed = this.domain.restrictTo( domain );
    for ( Effect e : appliedSet ) {
      if (e instanceof EffectFunction) {
        EffectFunction ef = (EffectFunction)e;
        Pair< Domain< T >, Boolean > p = ef.restrictDomain( domain, propagate, seen );
        if ( p != null && p.second != null ) {
          changed = changed || p.second;
        }
      }
    }
    for ( V v : values() ) {
      if ( v instanceof HasDomain ) {
        Pair< Domain< T >, Boolean > p =
            ( (HasDomain)v ).restrictDomain( domain, propagate, seen );
        if ( p != null && p.second != null ) {
          changed = changed || p.second;
        }
      }
    }
    Pair< Domain< T >, Boolean > p =
        new Pair< Domain< T >, Boolean >( (Domain< T >)getDomain( propagate, seen ),
                                          changed );
    return p;
  }

  public int compareValues( V v1,  V v2 ) {
    int comp = CompareUtils.compare( (Number)v1, (Number)v2 );
    return comp;
  }

  public boolean less( V v1,  V v2 ) {
    int comp = compareValues( v1, v2 );
    return comp < 0;
  }
  public boolean lessEquals( V v1,  V v2 ) {
    int comp = compareValues( v1, v2 );
    return comp <= 0;
  }
  public boolean greater( V v1,  V v2 ) {
    int comp = compareValues( v1, v2 );
    return comp > 0;
  }
  public boolean greaterEquals( V v1,  V v2 ) {
    int comp = compareValues( v1, v2 );
    return comp >= 0;
  }
  
  public boolean equals( V v1,  V v2 ) {
    int comp = compareValues( v1, v2 );
    return comp == 0;
  }
  
  public Long pickRandomTimeWithValue(V value) {
    double fraction = Random.global.nextDouble();
    Long time = timeWhenFractionOfTotalDurationWithValue( value, fraction );
    return time;
  }
  
  public Long timeWhenFractionOfTotalDurationWithValue(V value, double fraction ) {
    if ( fraction < 0.0 || fraction > 1.0 ) {
      Debug.error( "timeWhenFractionOfTotalDurationWithValue() expects fraction between 0 and 1 but got " + fraction );
      return null;
    }
    
    Long totalDur = totalDurationWithValue(value);
    Long durationTarget = (new Double(((double)totalDur) * fraction)).longValue(); 
    Long totalSoFar = 0L;
    Long prevTime = 0L;
    V prevVal = getValue( 0L );
    for ( Entry<Parameter<Long>, V> e : entrySet() ) {
      Long prevTotalSoFar = totalSoFar;
      if ( e == null ) continue;
      Parameter<Long> tp = e.getKey();
      if ( tp == null ) continue;
      V val = e.getValue();
      if ( interpolation == LINEAR ) {
        if ( ( lessEquals(prevVal, value ) && greaterEquals(val, value) ) || ( greaterEquals(prevVal, value ) && lessEquals(val, value) ) ) {
          if ( equals(prevVal, value ) && equals(val, value) ) {
            totalSoFar += tp.getValue() - prevTime;
          } else if (getType() == Integer.class || getType() == Short.class || getType() == Byte.class ) {
            long prevValLong = ((Number)prevVal).longValue();
            long valLong = ((Number)val).longValue();
            if ( prevValLong == valLong ) {
              Debug.error( "HUH??????!!!!!!" );
              totalSoFar += tp.getValue() - prevTime;
            } else {
              Long timeEqual = (tp.getValue() - prevTime) / Math.abs(prevValLong - valLong);
              totalSoFar += timeEqual;
            }
          } else if ( getType() == Double.class || getType() == Float.class ) {
            totalSoFar += 1;
          } else {
            Debug.error( "Linear interpolation with unexpected type: " + getType() );
            totalSoFar += 1;
          }
        }
      } else {
        if ( Utils.valuesEqual( prevVal, value ) ) {
          if ( interpolation == NONE ) {
            totalSoFar += 1;
          }
          totalSoFar += tp.getValue() - prevTime;
        } else {
        }
      }
      prevVal = val;
      prevTime = tp.getValue();
      if ( totalSoFar >= durationTarget ) {
        prevTime -= ( totalSoFar - durationTarget );
        break;
      }
    }
    if ( Utils.valuesEqual( prevVal, value ) && prevTime < Timepoint.getHorizonDuration() ) {
      if ( totalSoFar < durationTarget ) {
        if ( interpolation == NONE ) {
          totalSoFar += 1;
        } else {
          totalSoFar += Timepoint.getHorizonDuration() - prevTime;
        }
        prevTime = Timepoint.getHorizonDuration();
        if ( totalSoFar >= durationTarget ) {
          prevTime -= ( totalSoFar - durationTarget );
        }
      }
    }
    return prevTime;
  }
  
  
  public Long totalDurationWithValue(V value) {
    Long total = 0L;
    Long prevTime = 0L;
    V prevVal = getValue( 0L );
    for ( Entry<Parameter<Long>, V> e : entrySet() ) {
      if ( e == null ) continue;
      Parameter<Long> tp = e.getKey();
      if ( tp == null ) continue;
      V val = e.getValue();
      if ( interpolation == LINEAR ) {
        if ( ( lessEquals(prevVal, value ) && greaterEquals(val, value) ) || ( greaterEquals(prevVal, value ) && lessEquals(val, value) ) ) {
          if ( equals(prevVal, value ) && equals(val, value) ) {
            total += tp.getValue() - prevTime;
          } else if (getType() == Integer.class || getType() == Short.class || getType() == Byte.class ) {
            long prevValLong = ((Number)prevVal).longValue();
            long valLong = ((Number)val).longValue();
            if ( prevValLong == valLong ) {
              Debug.error( "HUH????!!!" );
              total += tp.getValue() - prevTime;
            } else {
              Long timeEqual = (tp.getValue() - prevTime) / Math.abs(prevValLong - valLong);
              total += timeEqual;
            }
          } else if ( getType() == Double.class || getType() == Float.class ) {
            total += 1;
          } else {
            Debug.error( "Linear interpolation with unexpected type: " + getType() );
            total += 1;
          }
        }
      } else {
        if ( Utils.valuesEqual( prevVal, value ) ) {
          if ( interpolation == NONE ) {
            total += 1;
          }
          total += tp.getValue() - prevTime;
        } else {
        }
      }
      prevVal = val;
      prevTime = tp.getValue();
    }
    if ( Utils.valuesEqual( prevVal, value ) && prevTime < Timepoint.getHorizonDuration() ) {
      if ( interpolation == NONE ) {
        total += 1;
      } else {
        total += Timepoint.getHorizonDuration() - prevTime;
      }
    }
    return total;
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


}  // end of TimeVaryingMap class
