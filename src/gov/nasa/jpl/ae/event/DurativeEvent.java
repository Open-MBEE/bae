
package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.solver.*;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.FileUtils;
import gov.nasa.jpl.mbee.util.MoreToString;
import gov.nasa.jpl.mbee.util.NameTranslator;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.TimeUtils;
import gov.nasa.jpl.mbee.util.Timer;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.TimeUtils.Units;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;

import junit.framework.Assert;

/**
 *
 */

public class DurativeEvent extends ParameterListenerImpl implements Event,
                           Cloneable, HasEvents, Groundable, Satisfiable,
                           Executor,
                           // Comparable< Event >,
                           ParameterListener, HasTimeVaryingObjects {

  // Static members

  public static boolean doPlot = false;

  protected static int counter = 0;

  // Other Members

  public static boolean writeConstraintsOut = false;

  protected boolean tryToSatisfyOnElaboration = false;
  protected boolean deepSatisfyOnElaboration = false;

  public Timepoint startTime = new Timepoint( "startTime", this );
  public Duration duration = new Duration( this );
  public Timepoint endTime = new Timepoint( "endTime", this );
  // TODO -- REVIEW -- create TimeVariableParameter and EffectMap classes for
  // effects?
  // protected Set< Effect > effects = new LinkedHashSet< Effect >();
  protected List< Pair< Parameter< ? >, Set< Effect > > > effects =
      new ArrayList< Pair< Parameter< ? >, Set< Effect > > >();
  protected Map< ElaborationRule, Vector< Event > > elaborations =
      new TreeMap<>();

  protected Dependency startTimeDependency = null;

  protected Dependency endTimeDependency = null;

  protected Dependency durationDependency = null;

  // TODO -- consider breaking elaborations up into separate constraints
  protected AbstractParameterConstraint elaborationsConstraint =
      new AbstractParameterConstraint() {

        protected final int id = HasIdImpl.getNext();

        @Override
        public boolean satisfy( boolean deep, Set< Satisfiable > seen ) {
          Pair< Boolean, Set< Satisfiable > > pair =
              Utils.seen( this, deep, seen );
          if ( pair.first ) return true;
          seen = pair.second;
          System.out.println(getName() + ".elaborationsConstraint.satisfy()");
          System.out.println(getName() + ".elaborations.size() = " + elaborations.size());
          //System.out.println(getName() + ".elaborations = " + elaborations);
          boolean satisfied = true;
          if ( !DurativeEvent.this.startTime.isGrounded( deep,
                                                         null ) ) return false;

          // Don't elaborate outside the horizon. Need startTime grounded to
          // know.
          Long value = startTime.getValue( true );
          // if ( !startTime.isGrounded(deep, null) ) return false;
          if ( value >= Timepoint.getHorizonDuration() ) {
            if ( Debug.isOn() ) Debug.outln( "satisfyElaborations(): No need to elaborate event outside the horizon: "
                                             + getName() );
            return true;
          }
          List< Pair< ElaborationRule, Vector< Event > > > list =
              new ArrayList< Pair< ElaborationRule, Vector< Event > > >();
          for ( Entry< ElaborationRule, Vector< Event > > er : elaborations.entrySet() ) {
            list.add( new Pair< ElaborationRule, Vector< Event > >( er.getKey(),
                                                                    er.getValue() ) );
          }
          elaborations.clear();
          for ( Pair< ElaborationRule, Vector< Event > > p : list ) {
            ElaborationRule r = p.first;
            Vector< Event > events = p.second;
            boolean sat = r.isConditionSatisfied();
            if ( r.isStale() || ( isElaborated( events ) != sat ) ) {
              if ( r.attemptElaboration( DurativeEvent.this, events, true,
                                         tryToSatisfyOnElaboration,
                                         deepSatisfyOnElaboration ) ) {
                if ( !r.isConditionSatisfied() ) satisfied = false;
              } else {
                if ( r.isConditionSatisfied() ) satisfied = false;
              }
            }
            elaborations.put( r, events );
          }
//          // Mark fromTimeVarying expressions as not stale.
//          if ( satisfied ) {
//              if ( fromT)
//          }
          return satisfied;
        }

        @Override
        public boolean isStale() {
          for ( Entry< ElaborationRule, Vector< Event > > er : elaborations.entrySet() ) {
            ElaborationRule r = er.getKey();
            if ( r.isStale() ) {
              return true;
            }
          }
          if ( super.isStale() ) return true;
          return false;
        };

        @Override
        public boolean isSatisfied( boolean deep, Set< Satisfiable > seen ) {
          Pair< Boolean, Set< Satisfiable > > pair =
              Utils.seen( this, deep, seen );
          if ( pair.first ) return true;
          seen = pair.second;

          // Don't elaborate outside the horizon. Need startTime grounded to know.
          Long value = startTime.getValue( false );
          if ( value != null && value >= Timepoint.getHorizonDuration() ) {
              return true;
          }

          if ( isStale() ) return false;
          for ( Entry< ElaborationRule, Vector< Event > > er : elaborations.entrySet() ) {
            if ( !startTime.isGrounded( deep, null ) ) return false;
            if ( startTime.getValueNoPropagate() < Timepoint.getHorizonDuration() ) {
              if ( isElaborated( er ) != er.getKey().isConditionSatisfied() ) {
                return false;
              }
            }
          }
          return true;
        }

        @Override
        public Parameter< ? > getParameter( String name ) {
            return HasParameters.Helper.getParameter( this, name );
        }

        @Override
        public Set< Parameter< ? > >
               getParameters( boolean deep, Set< HasParameters > seen ) {
          Pair< Boolean, Set< HasParameters > > pair =
              Utils.seen( this, deep, seen );
          if ( pair.first ) return Utils.getEmptySet();
          seen = pair.second;
          // if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
          Set< Parameter< ? > > s = new LinkedHashSet< Parameter< ? > >();
          for ( Entry< ElaborationRule, Vector< Event > > er : elaborations.entrySet() ) {
            s = Utils.addAll( s, er.getKey().getCondition()
                                   .getParameters( deep, seen ) );
          }
          return s;
        }

        @Override
        public String toShortString() {
          return getName() + ".elaborationsConstraint";
        }

        @Override
        public String toString() {
          // TODO -- should make this look evaluable, ex: condition ->
          // eventExists
          return getName() + ".elaborationsConstraint";
        }

        @Override
        public void setFreeParameters( Set< Parameter< ? > > freeParams,
                                       boolean deep,
                                       Set< HasParameters > seen ) {
          Assert.assertFalse( "setFreeParameters() not supported!", true );
          // if ( Utils.seen( this, deep, seen ) ) return;
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
        public String toString( boolean withHash, boolean deep,
                                Set< Object > seen ) {
          return toString();
        }

        @Override
        public String toString( boolean withHash, boolean deep,
                                Set< Object > seen,
                                Map< String, Object > otherOptions ) {
          return toString();
        }

        @Override
        public void deconstruct() {
          // nothing to deconstruct
        }

          @Override public void addReference() {
          }

          @Override public void subtractReference() {
          }

          @Override
        public CollectionTree
               getConstraintCollection( boolean deep,
                                        Set< HasConstraints > seen ) {
          // TODO Auto-generated method stub
          return null;
        }

      }; // end of elaborationsConstraint

  // TODO -- consider breaking effects into separate constraints
  protected AbstractParameterConstraint effectsConstraint =
      new AbstractParameterConstraint() {

        protected final int id = HasIdImpl.getNext();

        protected boolean
                  areEffectsOnTimeVaryingSatisfied( Parameter< ? > variable,
                                                    Set< Effect > effects,
                                                    boolean deep,
                                                    Set< Satisfiable > seen ) {
          boolean deepGroundable = deep;
          Set< Groundable > seenGroundable = null;
          if ( !variable.isGrounded( deepGroundable,
                                     seenGroundable ) ) return false;
          if ( !variable.isSatisfied( deep, seen ) ) return false;
          for ( Effect e : effects ) {
            if ( e == null ) {
              Debug.error( true, "Error! null effect in event " );
              continue;
            }
            if ( !checkIfEffectVariableMatches( variable, e ) ) return false;
            if ( !e.isApplied( variable )
                 || !variable.isGrounded( deepGroundable, seenGroundable ) ) {
              return false;
            }
          }
          return true;
        }

        protected boolean
                  satisfyEffectsOnTimeVarying( Parameter< ? > variable,
                                               Set< Effect > effects,
                                               boolean deep,
                                               Set< Satisfiable > seen ) {
          boolean deepGroundable = deep;
          Set< Groundable > seenGroundable = null;
          boolean satisfied = true;
          if ( !variable.isGrounded( deepGroundable, seenGroundable ) ) {
            variable.ground( deepGroundable, seenGroundable );
          }
          if ( !variable.isSatisfied( deep, null ) ) {
            variable.satisfy( deep, seen );
          }
          for ( Effect e : effects ) {
            if ( !checkIfEffectVariableMatches( variable, e ) ) return false;
            if ( !e.isApplied( variable ) ) {
              if ( !variable.isGrounded( deepGroundable, seenGroundable ) ) {
                satisfied = false;
              } else {
                Object value = variable.getValue( true );
                if ( ( !( value instanceof TimeVarying ) )
                     && value instanceof Parameter ) {
                  return satisfyEffectsOnTimeVarying( (Parameter< ? >)value,
                                                      effects, deep, seen );
                } else if ( value instanceof TimeVarying ) {
                  TimeVarying< ?, ? > tv = (TimeVarying< ?, ? >)value;
                  if ( tv.canBeApplied( e ) ) {
                    e.applyTo( tv, true );
                  } else {
                    satisfied = false;
                  }
                }
              }
            }
          }
          return satisfied;
        }

        @Override
        public boolean satisfy( boolean deep, Set< Satisfiable > seen ) {
          Pair< Boolean, Set< Satisfiable > > pair =
              Utils.seen( this, deep, seen );
          if ( pair.first ) return true;
          seen = pair.second;
          boolean satisfied = true;
          for ( Pair< Parameter< ? >, Set< Effect > > p : new ArrayList<>(effects) ) {
            Parameter< ? > variable = p.first;
            Set< Effect > set = p.second;
            if ( !satisfyEffectsOnTimeVarying( variable, set, deep, seen ) ) {
              satisfied = false;
            }
          }
          return satisfied;
        }

        @Override
        public boolean isSatisfied( boolean deep, Set< Satisfiable > seen ) {
          Pair< Boolean, Set< Satisfiable > > pair =
              Utils.seen( this, deep, seen );
          if ( pair.first ) return true;
          seen = pair.second;
          for ( Pair< Parameter< ? >, Set< Effect > > p : effects ) {
            Parameter< ? > variable = p.first;
            Set< Effect > set = p.second;
            if ( !areEffectsOnTimeVaryingSatisfied( variable, set, deep,
                                                    seen ) ) {
              return false;
            }
          }
          return true;
        }

        @Override
        public Parameter< ? > getParameter( String name ) {
            return HasParameters.Helper.getParameter( this, name );
        }

        @Override
        public Set< Parameter< ? > >
               getParameters( boolean deep, Set< HasParameters > seen ) {
          Pair< Boolean, Set< HasParameters > > pair =
              Utils.seen( this, deep, seen );
          if ( pair.first ) return Utils.getEmptySet();
          seen = pair.second;
          // if ( Utils.seen( this, deep, seen ) ) return Utils.getEmptySet();
          Set< Parameter< ? > > s = new LinkedHashSet< Parameter< ? > >();
          for ( Set< Effect > set : Pair.getSeconds( getEffects() ) ) {// .values()
                                                                       // ) {
            for ( Effect e : set ) {
              if ( e instanceof HasParameters ) {
                s = Utils.addAll( s,
                                  ( (HasParameters)e ).getParameters( deep,
                                                                      seen ) );
              }
            }
          }
          return s;
        }

        @Override
        public String toShortString() {
          // TODO -- maybe make this look evaluable, ex: v.getValue(t) ==
          // fCall.arg0
          return getName() + ".effectsConstraint";
        }

        @Override
        public String toString() {
          // TODO -- maybe make this look evaluable, ex: v.getValue(t) ==
          // fCall.arg0
          return getName() + ".effectsConstraint";
        }

        @Override
        public void setFreeParameters( Set< Parameter< ? > > freeParams,
                                       boolean deep,
                                       Set< HasParameters > seen ) {
          Assert.assertFalse( "setFreeParameters() is not supported!", true );
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
        public String toString( boolean withHash, boolean deep,
                                Set< Object > seen ) {
          return toString();
        }

        @Override
        public String toString( boolean withHash, boolean deep,
                                Set< Object > seen,
                                Map< String, Object > otherOptions ) {
          return toString();
        }

        @Override
        public void deconstruct() {
          // nothing to deconstruct
        }
        @Override public void addReference() {
        }
        @Override public void subtractReference() {
        }

        @Override
        public CollectionTree
               getConstraintCollection( boolean deep,
                                        Set< HasConstraints > seen ) {
          // TODO Auto-generated method stub
          return null;
        }

      }; // end of effectsConstraint

  // Constructors

  public DurativeEvent() {
    this( (String)null );
  }

  public DurativeEvent( String name ) {
    setName( name );
    loadParameters();

    // create the dependency, endTime = startTime + duration
    // TODO -- REVIEW -- should this dependency just be a constraint instead?
    Functions.Sum< Long, Long > sum =
        new Functions.Sum< Long, Long >( new Expression< Long >( startTime ),
                                         new Expression< Long >( duration ) );
    endTimeDependency = addDependency( endTime, sum, false );
    Functions.Sub< Long, Long > sub1 =
        new Functions.Sub< Long, Long >( new Expression< Long >( endTime ),
                                         new Expression< Long >( duration ) );
    startTimeDependency = addDependency( startTime, sub1, false );
    Functions.Sub< Long, Long > sub2 =
        new Functions.Sub< Long, Long >( new Expression< Long >( endTime ),
                                         new Expression< Long >( startTime ) );
    durationDependency = addDependency( duration, sub2, false );
  }

  public DurativeEvent( ParameterListenerImpl listener ) {
    super( listener );
  }

  public DurativeEvent( String name, ParameterListenerImpl listener ) {
    super( name, listener );
  }

  public DurativeEvent( String name, Date start, Long duration ) {
    this( name );
    if ( start != null ) {
      this.startTime.setValue( start );
    }
    if ( duration == null ) duration = new Long( 1 );
    this.duration.setValue( duration.longValue(), true );
  }

  public DurativeEvent( String name, Long start, Long duration ) {
    this( name );
    if ( start != null ) {
      this.startTime.setValue( start );
    }
    if ( duration == null ) duration = new Long( 1 );
    this.duration.setValue( duration.longValue(), true );
  }

  public DurativeEvent( String name, String activitiesFileName ) {
    this( name );
    try {
      fromCsvFile( activitiesFileName );
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }

  public DurativeEvent( String name, TimeVaryingMap< ? > tvm,
                        Object enclosingInstance, String type,
                        Expression[] arguments ) {
    this( name );
    addElaborationFromTimeVarying( tvm, enclosingInstance, type, arguments );
  }

  public DurativeEvent( String name, TimeVaryingMap< ? > tvm,
                        Object enclosingInstance, Class< ? extends Event > type,
                        Expression[] arguments ) {
    this( name );
    addElaborationFromTimeVarying( tvm, enclosingInstance, type, arguments,
                                   new Expression< Boolean >( true ) );
  }

  public void addElaborationFromTimeVarying( TimeVaryingMap< ? > tvm,
                                             Object enclosingInstance,
                                             String type,
                                             Expression[] arguments ) {
    addElaborationFromTimeVarying( tvm, enclosingInstance, type, arguments,
                                   new Expression< Boolean >( true ) );
  }

  public void addElaborationFromTimeVarying( TimeVaryingMap< ? > tvm,
                                             Object enclosingInstance,
                                             String type,
                                             Expression[] arguments,
                                             Expression< Boolean > condition ) {
    Class< ? extends Event > eventClass = eventClassFromName( type );
    // Class
    addElaborationFromTimeVarying( tvm, enclosingInstance, eventClass,
                                   arguments, condition );
  }

  // publi

  protected Class< ? extends Event > eventClassFromName( String type ) {
    Class< ? extends Event > eventClass = DurativeEvent.class;
    if ( type != null && !type.equals( "DurativeEvent" ) ) {
      Class< ? > cls = null;
      try {
        cls = ClassUtils.classForName( type );
      } catch ( ClassNotFoundException e1 ) {}
      if ( cls != null && Event.class.isAssignableFrom( cls ) ) {
        eventClass = (Class< ? extends Event >)cls;
      }
    }
    return eventClass;
  }

  public boolean
         addElaborationFromTimeVarying( TimeVaryingMap< ? > tvm,
                                        Object enclosingInstance,
                                        Class< ? extends Event > eventClass,
                                        Expression< ? >[] arguments,
                                        Expression< Boolean > condition ) {
    Parameter< Long > lastStart = null;
    Object lastValue = null;
    boolean changed = false;
    // Add an elaboration for every non-null value
    for ( Entry< Parameter< Long >, ? > e : tvm.entrySet() ) {
      boolean c =
          tryToAddElaboration( enclosingInstance, eventClass, arguments,
                               condition, lastStart, e.getKey(), lastValue );
      if ( c ) changed = true;
      if ( !e.getKey().valueEquals( lastStart ) ) {
        lastStart = e.getKey();
        lastValue = e.getValue();
      }
    }
    // Add for the last value of the timeline.
    boolean c =
        tryToAddElaboration( enclosingInstance, eventClass, arguments,
                             condition, lastStart,
                             Timepoint.getHorizonTimepoint(), lastValue );
    if ( c ) changed = true;

    return changed;
  }

  public boolean tryToAddElaboration( Object enclosingInstance,
                                      Class< ? extends Event > eventClass,
                                      Expression< ? >[] arguments,
                                      Expression< Boolean > condition,
                                      Parameter< Long > start,
                                      Parameter< Long > end, Object value ) {
    if ( start != null && start.getValue() != null && value != null
         && !Utils.valuesEqual( value, 0 ) && !Utils.valuesEqual( value, "" )
         && !Utils.isFalse( value, false ) && !end.valueEquals( start ) ) {
      ElaborationRule r =
          addElaborationRule( condition, enclosingInstance, eventClass,
                              arguments, start, end );
      if ( r != null ) return true;
    }
    return false;
  }

  private void debugToCsvFile( TimeVaryingMap< ? > tv, String fileName ) {
    // write to file
    String pathAndFile = EventSimulation.csvDir + File.separator + "debug"
                         + File.separator + fileName;
    String dateFormat = "yyyy-DDD'T'HH:mm:ss.SSSZ";// TimeUtils.aspenTeeFormat;
    Calendar cal = Calendar.getInstance( TimeZone.getTimeZone( "GMT" ) );
    tv.toCsvFile( pathAndFile, "Data Timestamp,Data Value", dateFormat, cal );
  }

  public static int debugCt = 0;

  private void debugElaborationsToCsvFile() {
    Set< Event > events = new LinkedHashSet< Event >();
    for ( Entry< ElaborationRule, Vector< Event > > e : elaborations.entrySet() ) {
      events.addAll( e.getValue() );
    }
    EventSimulation.writeEvents( events, EventSimulation.csvDir + File.separator
                                         + "debug",
                                 "" + debugCt );
  }

  public boolean
         repairElaborationFromTimeVarying( TimeVaryingMap< ? > tvm,
                                           Object enclosingInstance,
                                           // DurativeEvent parent, // shouldn't
                                           // this be this?!
                                           Class< ? extends Event > eventClass,
                                           Expression< ? >[] arguments,
                                           Expression< Boolean > condition ) {
    // Class<? extends Event> eventClass = eventClassFromName(type);
    boolean changed = false;

    if ( elaborations == null ) {
      Debug.error( "Expected elaborations to be non-null!" );
      return false;
    }

    // FIXME -- put inside if (Debug.isOn())
    System.out.println( "~~~~  REPAIR START " + elaborations.size()
                        + "  ~~~~" );
    int c = 0;
    for ( Entry< Parameter< Long >, ? > e : tvm.entrySet() ) {
      if ( Expression.valuesEqual( e.getValue(), 1.0 ) ) c++;
    }
    System.out.println( "~~~~~  " + c + " entries with value 1  ~~~~~" );

    TimeVaryingMap< ? > tvmCopy = tvm.clone();
    Set< ElaborationRule > elaborationsToProcess =
        new LinkedHashSet< ElaborationRule >( elaborations.keySet() );
    Set< ElaborationRule > elaborationsToDelete =
        new LinkedHashSet< ElaborationRule >();
    // Map< ElaborationRule, Vector< Event > > newElaborations =
    // new LinkedHashMap< ElaborationRule, Vector<Event> >();

//    ++debugCt;
//    debugToCsvFile( tvm, "dataRateAboveThreshold" + debugCt + ".csv" );
//    debugElaborationsToCsvFile();

    ArrayList< ElaborationRule > rules;
    boolean didChange =
        repairElaborations( tvm, enclosingInstance, eventClass, arguments,
                            condition, tvmCopy, elaborationsToProcess,
                            elaborationsToDelete, // newElaborations,
                            true );
    if ( didChange ) changed = true;

    didChange = repairElaborations( tvm, enclosingInstance, eventClass,
                                    arguments, condition, tvmCopy,
                                    elaborationsToProcess, elaborationsToDelete, // newElaborations,
                                    false );
    if ( didChange ) changed = true;

    elaborationsToDelete.addAll( elaborationsToProcess );
    // FIXME -- put inside if (Debug.isOn())
    System.out.println( "~~~~~  " + elaborationsToDelete.size()
                        + " elaborations to delete  ~~~~~" );
    for ( ElaborationRule rule : elaborationsToDelete ) {
      Vector< Event > removedEvents = elaborations.remove( rule );
      if ( !changed && removedEvents != null ) changed = true;
      for ( Event event : removedEvents ) {
        event.deconstruct();
      }
      rule.deconstruct();
    }

    // Now create elaboration rules for the remaining unprocessed timepoints in
    // tvmCopy.
    boolean addedStuff =
        addElaborationFromTimeVarying( tvmCopy, enclosingInstance, eventClass,
                                       arguments, condition );
    if ( addedStuff ) changed = true;

    // FIXME -- put inside if (Debug.isOn())
    System.out.println( "~~~~~  REPAIR END " + elaborations.size()
                        + "  ~~~~~" );

    return changed;
  }

  protected boolean
            repairElaborations( TimeVaryingMap< ? > tvm,
                                Object enclosingInstance,
                                Class< ? extends Event > eventClass,
                                Expression< ? >[] arguments,
                                Expression< Boolean > condition,
                                TimeVaryingMap< ? > tvmCopy,
                                Set< ElaborationRule > elaborationsToProcess,
                                Set< ElaborationRule > elaborationsToDelete,
                                // Map< ElaborationRule, Vector< Event > >
                                // newElaborations,
                                // DurativeEvent parent,
                                boolean onlyRepairExactMatches ) {
    // Should events be replaced (re-elaborated ) or just corrected when they
    // don't match intervals in tvm, the TimeVaryingMap.
    boolean replace = false;

    boolean changed = false;
    ArrayList< ElaborationRule > rules =
        new ArrayList< ElaborationRule >( elaborationsToProcess );
    for ( ElaborationRule rule : rules ) {
      Vector< Event > events = elaborations.get( rule );
      if ( events.size() != 1 ) {
        Debug.error( false,
                     "ElaborationRule generated from TimeVaryingMap should have exactly one event" );
        elaborationsToProcess.remove( rule );
        continue;
      }
      Event event = events.get( 0 );
      Timepoint start = event.getStartTime();
      if ( start == null ) continue; // error??!!
      // Find the closest match on second pass--it doesn't need to match
      // exactly.
      Entry< Parameter< Long >, ? > entry = tvmCopy.lowerEntry( start );
      if ( entry == null ) {
        if ( onlyRepairExactMatches ) continue;
        entry = tvmCopy.higherEntry( start );
      }
      if ( entry == null ) continue;

      Parameter< Long > t = entry.getKey();
      if ( !t.valueEquals( start.getValue() )
           && onlyRepairExactMatches ) continue;

      // In case there are multiple entries at the same time, find one with a
      // non-zero value.
      NavigableMap< Parameter< Long >, ? > mapToT =
          tvm.subMap( null, true, t.getValue(), true );
      if ( mapToT.isEmpty() ) {
          Debug.error(true, true, "subMap does not include entry with same value!");
      }
      Parameter< Long > last = mapToT.isEmpty() ? t : mapToT.lastKey();
      if ( last != null && last.getValue() == t.getValue() ) {
        t = last;
      }
      Object v = tvmCopy.get( t );
      if ( Utils.valuesEqual( v, 0 ) || Utils.valuesEqual( v, "" ) ) continue; // not
                                                                               // a
                                                                               // start
                                                                               // time
                                                                               // if
                                                                               // 0

      // Set< Parameter< Long > > matchingTimepoints = tvmCopy.getKeys( t );
      // // Get the
      // if (!Utils.isNullOrEmpty( matchingTimepoints )) {
      // t = matchingTimepoints.
      // }
      // Object v = null;
      // for ( Parameter<Long> tp : matchingTimepoints ) {
      // v = tvmCopy.get(tp);
      // if ( !Utils.valuesEqual( v, 0 ) && !Utils.valuesEqual( v, "" ) ) {
      // t = tp;
      // break; // not a start time if 0
      // }
      // }
      // if ( Utils.valuesEqual( v, 0 ) || Utils.valuesEqual( v, "" ) )
      // continue; // not a start time if 0

      // Matched start time!
      // Remove matched interval and rule from sets to process.
      // First, remove the keys matching t with non-zero value in tvmCopy. No
      // need to leave zero-valued entries since end times are matched against
      // the original tvm map.
      for ( Entry< Parameter< Long >, ? > subMapEntry : mapToT.descendingMap()
                                                              .entrySet() ) {// .navigableKeySet().descendingSet()
                                                                             // )
                                                                             // {
        Parameter< Long > tt = subMapEntry.getKey();
        if ( !tt.equals( t ) ) break;
        // v = subMapEntry.getValue();//tvmCopy.get(tt);
        // if ( !Utils.valuesEqual( v, 0 ) && !Utils.valuesEqual( v, "" ) ) {
        tvmCopy.remove( tt );
        // }
      }

      // //Iterator< Parameter<Long> > i = matchingTimepoints.iterator();
      // for ( Parameter<Long> tp : new ArrayList< Parameter<Long>
      // >(matchingTimepoints) ) {
      // //while ( i.hasNext() ) {
      // //Parameter<Long> tp = i.next();
      // v = tvmCopy.get(tp);
      // if ( !Utils.valuesEqual( v, 0 ) && !Utils.valuesEqual( v, "" ) ) {
      // tvmCopy.remove( tp ); // only leave
      // }
      // }
      //// tvmCopy.remove( t ); // only leave
      elaborationsToProcess.remove( rule );

      // Now correct start end time if necessary.
      Timepoint eventEnd = event.getEndTime();
      if ( eventEnd == null ) continue; // error??!!
      Parameter< Long > intervalEnd = tvm.getTimepointLater( t );
      if ( intervalEnd == null || intervalEnd.getValue() == null ) continue;
      if ( !eventEnd.valueEquals( intervalEnd ) ) {
        if ( replace || !( event instanceof DurativeEvent ) ) {
          elaborationsToDelete.add( rule );
          ElaborationRule r =
              addElaborationRule( condition, enclosingInstance, eventClass,
                                  arguments, t, intervalEnd );
          if ( r != null ) {
            changed = true;
            // newElaborations.put(r, new Vector<Event>() );
          }
        } else {
          // To fix, just substitute the new start and end times for the old
          // ones in the dependency created by the EventInvocation.
          // Also fix the EventInvocation's arguments and memberAssignments.

          // Fix the EventInvocation.
          DurativeEvent dEvent = (DurativeEvent)event;
          long dur = intervalEnd.getValue() - t.getValue();
          Expression< Long > durationExpr = new Expression< Long >( dur );
          Expression< Long > startExpr = new Expression< Long >( t );

          for ( EventInvocation ei : rule.eventInvocations ) {
            Object[] args = ei.getArguments();
            if ( args != null && args.length >= 2 ) {
              Object oldStartExpr = args[ args.length - 2 ];
              if ( !Expression.valuesEqual( oldStartExpr, startExpr ) ) {
                // FIXME -- put inside if (Debug.isOn())
                printFromToTime( "startTime", oldStartExpr, startExpr );
                // Swap in new argument.
                args[ args.length - 2 ] = startExpr;
                // Fix the dependency by replacing it.
                dEvent.addDependency( dEvent.startTime, startExpr );
                changed = true;
              }
              Object oldDurExpr = args[ args.length - 1 ];
              if ( !Expression.valuesEqual( oldDurExpr, durationExpr ) ) {
                if ( Debug.isOn() ) {
                try {
                  if ( Math.abs( ( (Long)Expression.evaluate( oldDurExpr,
                                                              Long.class,
                                                              true ) )
                                 - ( (Long)Expression.evaluate( durationExpr,
                                                                Long.class,
                                                                true ) ) ) > 2
                                                                             * 3600
                                                                             * 1000 ) {
                    Debug.breakpoint();
                  }
                } catch ( ClassCastException | IllegalAccessException
                          | InvocationTargetException
                          | InstantiationException e ) {
                  e.printStackTrace();
                }
                }
                printFromToTime( "duration", oldDurExpr, durationExpr );
                // Swap in new argument.
                args[ args.length - 1 ] = durationExpr;
                // Fix the dependency by replacing it.
                dEvent.addDependency( dEvent.duration, durationExpr );
                changed = true;
              }
            }
            // TODO?
            if ( !Utils.isNullOrEmpty( ei.getMemberAssignments() ) ) {
              Debug.error( false,
                           "Member assignments not fixed for repaired elaboration from TimeVaryingMap!!!" );
            }
          }

        }
      }
    }
    return changed;
  }

  private void printFromToTime( String name, Object from,
                                Expression< Long > to ) {
    Long ot = null;
    Long nt = null;
    try {
      ot = Expression.evaluate( from, Long.class, true );
    } catch ( ClassCastException | IllegalAccessException
              | InvocationTargetException | InstantiationException e ) {}
    try {
      nt = (Long)to.evaluate( true );
    } catch ( ClassCastException | IllegalAccessException
              | InvocationTargetException | InstantiationException e ) {}
    try {
      System.out.println( "Fixing " + name + ": "
                          + ( ot == null ? "null"
                                         : Timepoint.toTimestamp( ot.longValue() ) )
                          + " -> "
                          + ( ot == null ? "null"
                                         : Timepoint.toTimestamp( nt.longValue() ) ) );
    } catch ( Throwable t ) {
      t.printStackTrace();
    }
  }

  protected ElaborationRule addElaborationRule( Expression< Boolean > condition,
                                                Object enclosingInstance,
                                                Class< ? extends Event > eventClass,
                                                Expression< ? >[] arguments,
                                                Parameter< Long > start,
                                                Parameter< Long > end ) {
    // TODO - find a better way of deciding whether to use duration
    // or startTime / endTime - right now, depends on which constructor
    // was defined, which depends on the model specifics
    Long duration = new Long( end.getValue( true ) - start.getValue( true ) );
    String childName = String.format( "%s%06d", name, counter++ );
    Expression< ? >[] augmentedArgs =
        new Expression< ? >[ arguments.length + 2 ];
    // Repackage arguments, passing in the start time and duration.
    for ( int i = 0; i < arguments.length; ++i ) {
      augmentedArgs[ i ] = arguments[ i ];
    }
    augmentedArgs[ arguments.length ] = new Expression< Long >( start );
    augmentedArgs[ arguments.length + 1 ] =
        new Expression< Long >( duration.longValue() );
//        new Expression< Long >( end );
    ElaborationRule r =
        addElaborationRule( condition, enclosingInstance, eventClass, childName,
                            augmentedArgs );
    return r;
  }

  public DurativeEvent( DurativeEvent durativeEvent ) {
    this( null, durativeEvent );
  }

  public DurativeEvent( String name, DurativeEvent durativeEvent ) {
    setName( name );
    copyParameters( durativeEvent );
    loadParameters();

    // copy containers after clearing
    constraintExpressions.clear();
    effects.clear();
    dependencies.clear();
    for ( ConstraintExpression c : durativeEvent.constraintExpressions ) {
      ConstraintExpression nc = new ConstraintExpression( c );
      constraintExpressions.add( nc );
    }
    // for ( Map.Entry< Parameter< ? >, Set< Effect > > e :
    // durativeEvent.effects.entrySet() ) {
    for ( Pair< Parameter< ? >, Set< Effect > > p : durativeEvent.effects ) {
      Set< Effect > newSet = new LinkedHashSet< Effect >();
      try {
        for ( Effect eff : p.second ) {
          checkIfEffectVariableMatches( p.first, eff );
          Effect ne = eff.clone();
          newSet.add( ne );
        }
      } catch ( CloneNotSupportedException e1 ) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
      }
      effects.add( new Pair< Parameter< ? >, Set< Effect > >( (Parameter< ? >)p.first.clone(),
                                                              newSet ) );
    }
    for ( Dependency< ? > d : durativeEvent.dependencies ) {
      Dependency< ? > nd = new Dependency( d );
      dependencies.add( nd );
    }

    // We need to make sure the copied constraints are on this event's
    // parameters and not that of the copied constraints.
    List< Pair< Parameter< ? >, Parameter< ? > > > subList =
        buildSubstitutionList( durativeEvent );
    for ( Pair< Parameter< ? >, Parameter< ? > > p : subList ) {
      substitute( p.first, p.second, false, null );
    }
    // }
  }

  /**
   * Read a CSV file and create an event for each row that elaborates from this
   * event. Assume that a row has the following fields:
   * <ul>
   * <li>start time as a date (in supported formats for
   * {@link TimeUtils#dateFromTimestamp(String, TimeZone)}) or integer offset
   * <li>duration as an integer offset (optional)
   * <li>end time as a date or integer offset (optional and only if no duration)
   * <li>name as a string (optional)
   * </ul>
   * Any lines that do not have at least a start time are ignored.
   * <p>
   * The first line is checked to see if it is a column header to specify which
   * field is which. The following regular expression patterns (ignoring letter
   * case) are matched to field names to disambiguate the fields:
   * <ol>
   * <li>name --&gt; name
   * <li>duration --&gt; duration
   * <li>end --&gt; end time
   * <li>start|time --&gt; start time
   * <li>activity --&gt; name
   * <li>'[^a-zA-Z]id[^a-zA-Z]' --&gt; name
   * </ol>
   * 
   * @param fileName
   * @throws IOException
   */
  public void fromCsvFile( String fileName ) throws IOException {
    if ( fileName == null ) return;
    ArrayList< ArrayList< String > > lines = FileUtils.fromCsvFile( fileName );

    if ( Utils.isNullOrEmpty( lines ) ) {
      // TODO -- error?
      return;
    }

    // A map to remember which field is which
    HashMap< String, Integer > fieldMap = new LinkedHashMap< String, Integer >();

    // TODO -- Get the header if there is one to help determine fields by name.

    // process lines
    boolean fieldMapInitialized = false;
    // HashMap<Object, Integer> fieldMapI = new LinkedHashMap<Object, Integer>();
    for ( ArrayList< String > fields : lines ) {
      Date start = null;
      Date end = null;
      String name = null;
      Double duration = null;
      String type = null;

      fieldMapInitialized = fieldMapInitialized || !fieldMap.isEmpty();

      TimeZone gmtZone = TimeZone.getTimeZone( "GMT" );

      // for ( String field : fields ) {
      for ( int i = 0; i < fields.size(); ++i ) {
        String field = fields.get( i );

        if ( field == null ) continue;

        // Check for titles in a header
        if ( !fieldMapInitialized ) {
            if ( field.toLowerCase().contains( "start" ) ) {
                fieldMap.put( "start", i );
                continue;
            }
            if ( field.toLowerCase().contains( "end" ) ) {
                fieldMap.put( "end", i );
                continue;
            }
            if ( field.toLowerCase().contains( "duration" ) ) {
                fieldMap.put( "duration", i );
                continue;
            }
            if ( field.toLowerCase().contains( "type" ) ) {
                fieldMap.put( "type", i );
                continue;
            }
            if ( field.toLowerCase().contains( "name" ) ) {
                fieldMap.put( "name", i );
                continue;
            }
            // Look for "id" in the name, but since a lot of words have "id" in them,
            // look for "id" as a separate word.
            if ( field.toLowerCase().matches( "(^id$)|(^id[^a-z].*)|(.*[^a-z]id$)|(.*[^a-z]id[^a-z].*)" ) ) {
                fieldMap.put( "id", i );
                continue;
            }
        }

        // Try to match start or end time
        Date d = TimeUtils.dateFromTimestamp( field, gmtZone );
        if ( d != null ) {
          if ( ( fieldMapInitialized && fieldMap.containsKey( "start" ) &&
                 fieldMap.get( "start" ) == i )
               || ( !fieldMapInitialized && start == null ) ) {
            start = d;
            if ( !fieldMapInitialized || !fieldMap.containsKey( "start" ) ) {
              fieldMap.put( "start", i );
            }
          } else if ( ( fieldMapInitialized && fieldMap.containsKey( "end" ) &&
                        fieldMap.get( "end" ) == i )
                      || ( !fieldMapInitialized && end == null ) ) {
            end = d;
            if ( !fieldMapInitialized || !fieldMap.containsKey( "end" ) ) {
              fieldMap.put( "end", i );
            }
          }
          continue;
        }

        // Try to match duration
        if ( ( fieldMapInitialized && fieldMap.containsKey( "duration" ) &&
               fieldMap.get( "duration" ) == i )
             || ( !fieldMapInitialized && duration == null ) ) {
          duration = TimeUtils.toDurationInSeconds( field );
          if ( !fieldMapInitialized || !fieldMap.containsKey( "duration" ) ) {
            fieldMap.put( "duration", i );
          }
          if ( duration != null ) continue;
        }

        // Try to match name
        if ( ( fieldMapInitialized && fieldMap.containsKey( "name" ) && fieldMap.get( "name" ) == i )
             || ( !fieldMapInitialized
                  && ( name == null
                       || ( !name.matches( ".*[A-Za-z].*" )
                            && field.matches( ".*[A-Za-z].*" ) ) ) ) ) {
          if ( !fieldMapInitialized || !fieldMap.containsKey( "name" ) ) {
            fieldMap.put( "name", i );
          }
          name = field;
          if ( name != null ) continue;
        }

        // Try to match type
        if ( ( fieldMapInitialized && fieldMap.containsKey( "type" ) &&
               fieldMap.get( "type" ) == i )
             || ( !fieldMapInitialized
                  && ( type == null || ( !type.matches( ".*[A-Za-z].*" )
                                         && field.matches( ".*[A-Za-z].*" ) ) )
                  && ClassUtils.getClassForName( field, (String)null,
                                                 (String)null,
                                                 false ) != null ) ) {
          if ( !fieldMapInitialized || !fieldMap.containsKey( "type" ) ) {
            fieldMap.put( "type", i );
          }
          type = field;
        }

        // Make sure start is before end.
        if ( !fieldMapInitialized && end != null && start != null &&
             end.before( start ) ) {
          // swap start and end values
          Date tmp = start;
          start = end;
          end = tmp;
          // swap indices in field map
          int tmpi = fieldMap.get( "start" );
          fieldMap.put( "start", fieldMap.get( "end" ) );
          fieldMap.put( "end", tmpi );
        }

      } // end for field in fields

        // Add elaboration if not outside the horizon
        if ( start != null && ( end == null || end.after( Timepoint.epoch ) )
             && start.before( Timepoint.getHorizon() ) ) {
            addElaborationRule( start, end, duration, name, type );
        }

        if ( !fieldMapInitialized ) {
          // If the type wasn't found, use the name as the type.
          if ( !fieldMap.containsKey( "type" ) ) {
              if ( fieldMap.containsKey( "name" ) ) {
                  fieldMap.put( "type", fieldMap.get( "name" ) );
              } else if ( fieldMap.containsKey( "id" ) ) {
                  fieldMap.put( "type", fieldMap.get( "id" ) );
              }
          }
          // If the name wasn't found, use the id as the name.
          if ( !fieldMap.containsKey( "name" ) ) {
              if ( fieldMap.containsKey( "id" ) ) {
                  fieldMap.put( "name", fieldMap.get( "id" ) );
              }
          }
      }

    } // for line in lines

    if ( Debug.isOn() ) Debug.outln( "read map from file, " + fileName + ":\n"
                                     + this.toString() );
  }

  public boolean addElaborationRule( Date start, Date end, Double duration,
                                     String name, String typeName ) {
    if ( start == null ) return false;
    // Compute duration if not given.
    if ( duration == null && end != null ) {
      duration = ( end.getTime() - start.getTime() ) / 1000.0;
    }
    Expression< String > nameExpr =
            new Expression< String >( name == null ? this.name : name );
    Expression< Long > startExpr =
        new Expression< Long >( new Timepoint( start ), Long.class );
    // Duration dd = new Duration( name, durVal, durUnits, o );
    Expression< Long > durationExpr = new Expression< Long >(
                                                              ( new Double( duration == null ? 1
                                                                                             : duration
                                                                                               / Timepoint.conversionFactor( Units.seconds ) ) ).longValue(),
                                                              Long.class );
    Class< ? > cls =
        typeName == null ? DurativeEvent.class
                         : ClassUtils.getClassForName( typeName, (String)null,
                                                       (String)null, false );
    Class< ? extends Event > eventClass = null;

    if ( cls != null && Event.class.isAssignableFrom( eventClass ) ) {
      eventClass = (Class< ? extends Event >)cls;
    } else {
      eventClass = DurativeEvent.class;
    }
    String eventName = eventClass == DurativeEvent.class ? typeName : "";
    this.addElaborationRule( new Expression< Boolean >( true ), null,
                             eventClass, eventName,
                             new Expression< ? >[] { nameExpr, startExpr,
                                                     durationExpr } );
    return true;
  }

  public static boolean checkIfEffectVariableMatches( Parameter< ? > variable,
                                                      Effect e ) {
    if ( e instanceof EffectFunction ) {
      EffectFunction ef = (EffectFunction)e;
      if ( ef.getObject() != null
           && !Expression.valuesEqual( ef.getObject(), variable ) ) {
        Debug.error( true, false,
                     "Error! Variable (" + variable + ") and effect variable ("
                           + ef.getObject() + ") do not match! " + ef );
        return false;
      }
    }
    return true;
  }

  public void fixTimeDependencies() {}

  public void fixTimeDependencies1() {
    boolean gotStart = false, gotEnd = false, gotDur = false;
    boolean stillHaveStart = false, stillHaveEnd = false, stillHaveDur = false;
    int numGot = 0;
    int numHave = 0;
    // see what time dependencies we have
    for ( Dependency< ? > d : getDependencies() ) {
      if ( d.parameter == startTime ) {
        if ( d == startTimeDependency ) {
          if ( !stillHaveStart ) {
            ++numHave;
            stillHaveStart = true;
          }
        } else {
          if ( !gotStart ) {
            gotStart = true;
            ++numGot;
          }
        }
      } else if ( d.parameter == endTime ) {
        if ( d == endTimeDependency ) {
          if ( !stillHaveEnd ) {
            stillHaveEnd = true;
            ++numHave;
          }
        } else {
          if ( !gotEnd ) {
            gotEnd = true;
            ++numGot;
          }
        }
      } else if ( d.parameter == duration ) {
        if ( d == durationDependency ) {
          if ( !stillHaveDur ) {
            stillHaveDur = true;
            ++numHave;
          }
        } else {
          if ( !gotDur ) {
            gotDur = true;
            ++numGot;
          }
        }
      }
    }
    // get rid of already replaced dependencies
    if ( gotStart && stillHaveStart ) {
      getDependencies().remove( startTimeDependency );
      stillHaveStart = false;
      --numHave;
    }
    if ( gotEnd && stillHaveEnd ) {
      getDependencies().remove( endTimeDependency );
      stillHaveEnd = false;
      --numHave;
    }
    if ( gotDur && stillHaveDur ) {
      getDependencies().remove( durationDependency );
      stillHaveDur = false;
      --numHave;
    }
    // only want one dependency among the three
    if ( numHave > 1 ) {
      if ( stillHaveEnd ) {
        if ( stillHaveStart ) {
          getDependencies().remove( startTimeDependency );
        }
        if ( stillHaveDur ) {
          getDependencies().remove( durationDependency );
        }
      } else if ( stillHaveDur ) {
        if ( stillHaveStart ) {
          getDependencies().remove( startTimeDependency );
        }
      } else {
        assert false;
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * gov.nasa.jpl.ae.event.ParameterListenerImpl#substitute(gov.nasa.jpl.ae.
   * event.Parameter, gov.nasa.jpl.ae.event.Parameter, boolean)
   */
  @Override
  public boolean substitute( Parameter< ? > p1, Parameter< ? > p2, boolean deep,
                             Set< HasParameters > seen ) {
    // Parent class adds 'this' to seen, so just checking here.
    if ( !Utils.isNullOrEmpty( seen ) && seen.contains( this ) ) return false;
    // call parent class's method
    boolean subbed = super.substitute( p1, p2, deep, seen );
    boolean s = HasParameters.Helper.substitute( effects, p1, p2, deep, null );
    subbed = subbed || s;
    // for ( Entry< Parameter< ?>, Set< Effect >> e : effects.entrySet() ) {
    // if ( e.getValue() instanceof HasParameters ) {
    // boolean s = ( (HasParameters)e.getValue() ).substitute( p1, p2, deep,
    // seen );
    // subbed = subbed || s;
    // }
    // }
    return subbed;
  }

  // Methods

  // Subclasses should override this to add their own parameters.
  protected void loadParameters() {
    parameters.clear();
    parameters.add( startTime );
    parameters.add( duration );
    parameters.add( endTime );
  }

  // Subclasses should override this to add their own parameters.
  // TODO -- Can this just iterate through parameters container to avoid
  // overriding in subclasses?
  protected void copyParameters( DurativeEvent event ) {
    // Inefficiently reallocating these parameters--maybe create
    // Parameter.copy(Parameter)
    startTime = new Timepoint( event.startTime );
    duration = new Duration( event.duration );
    endTime = new Timepoint( event.endTime );
  }

  /*
   * (non-Javadoc)
   *
   * @see java.lang.Object#clone()
   */
  @Override
  public Object clone() {
    return new DurativeEvent( this );
  }
  


  public String kSolutionString( int indent, Set<ParameterListenerImpl> seen ) {
    Pair< Boolean, Set< ParameterListenerImpl > > pair = Utils.seen( this, true, seen );
    if ( pair.first ) return "";
    seen = pair.second;

    String indentString = "";
    for (int i = 0 ; i < indent; i++) {
      indentString += "   ";
    }
    StringBuffer sb = new StringBuffer();
    
    
    Parameter<?> dontNeedParams[] = { startTime, duration, endTime };  

    Set< Parameter< ? > > allParams = getParameters( false, null );
    allParams.removeAll( Arrays.asList( dontNeedParams ) );
    for ( Parameter< ? > p : allParams ) {
        if ( p.getValueNoPropagate() instanceof ParameterListenerImpl ) {
          ParameterListenerImpl pLI =
              ( (ParameterListenerImpl)p.getValueNoPropagate() );
          sb.append( indentString + p.getName() + " = " + pLI.getClass().getSimpleName()
                     + " {\n" );
          sb.append(  pLI.kSolutionString( indent + 1, seen ) );
          sb.append( indentString + "}\n" );
        } else {
            sb.append(indentString + p.getName() + " = " + MoreToString.Helper.toStringWithSquareBracesForLists((Object) p.getValue(), true, true, null) + "\n");
        }
      
    }

    return sb.toString();
  }
  

  public JSONArray kSolutionJSONArr( Set<ParameterListenerImpl> seen ) {
    JSONArray value = new JSONArray();

    Pair< Boolean, Set< ParameterListenerImpl > > pair = Utils.seen( this, true, seen );
    if ( pair.first ) return value;
    seen = pair.second;

    Parameter<?> dontNeedParams[] = { startTime, duration, endTime };

    Set< Parameter< ? > > allParams = getParameters( false, null );
    allParams.removeAll( Arrays.asList( dontNeedParams ) );
    for ( Parameter< ? > p : allParams ) {
      JSONObject param = new JSONObject();
      if ( p.getValueNoPropagate() instanceof ParameterListenerImpl ) {
        ParameterListenerImpl pLI =
            ( (ParameterListenerImpl)p.getValueNoPropagate() );
        param.put( "name", p.getName() );
        param.put( "type", "class" );
        JSONArray val = pLI.kSolutionJSONArr(seen);
        param.put( "value", val );
        
      } else {
        param.put( "name", p.getName() );
        param.put( "type", "primitive" );
        param.put( "value", MoreToString.Helper.toStringWithSquareBracesForLists((Object) p.getValue(), true, true, null)) ;
      }
      value.put( param );
    }
    return value;
    
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

  /*
   * (non-Javadoc)
   * 
   * @see gov.nasa.jpl.ae.event.ParameterListenerImpl#toString(boolean, boolean,
   * java.util.Set, java.util.Map)
   */
  @Override
  public String toString( boolean withHash, boolean deep, Set< Object > seen,
                          Map< String, Object > otherOptions ) {
    Pair< Boolean, Set< Object > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) deep = false;
    seen = pair.second;
    StringBuffer sb = new StringBuffer();
    sb.append( getClass().getName() + "::" );
    if ( deep ) {
        sb.append( getQualifiedName() );
    } else {
        sb.append( getName() );
    }
    if ( withHash ) sb.append( "@" + hashCode() );
    //if ( deep ) {
    sb.append( "(" );
    boolean first = true;

    if ( !Utils.isNullOrEmpty( getName() ) ) {
        if ( first ) first = false;
        else sb.append( ", " );
        sb.append( "name=" + getName() );
    }

    if ( first ) first = false;
    else sb.append( ", " );
    sb.append( "id=" + id );

    if ( first ) first = false;
    else sb.append( ", " );
    sb.append( "qualifiedId=" + getQualifiedId() );

    Parameter<?> firstParams[] = { startTime, duration, endTime }; // Could
    // use
    // Arrays.sort()
    // .search()
    List< Parameter< ? > > allParams =
        new ArrayList< Parameter< ? > >( Arrays.asList( firstParams ) );
    Set<Parameter<?>> restParams = getParameters( false, null );
    restParams.removeAll( allParams );
    allParams.addAll( restParams );
    for ( Object p : allParams ) {
        if ( first ) first = false;
        else sb.append( ", " );
        if ( p instanceof Parameter ) {
        sb.append( ( (Parameter< ? >)p ).toString( false, withHash, deep, seen,
                                          otherOptions ) );
        } else {
            sb.append( p.toString() );
        }
    }

    // Print out the events following the parameters.
    // TODO -- print out some context to be able to recreate the elaborations that contain the events.
    Set<Event> events = getEvents( false, null );
    if ( first ) first = false;
    else sb.append( ", " );
    sb.append( "events=Set{" );
    boolean eventFirst = true;
    for ( Event e : events ) {
        if ( eventFirst ) eventFirst = false;
        else sb.append( ", " );
        sb.append(e.getName() + "@" + e.getId() );
    }
    sb.append( "}" );

    // Add the close paren oon the list of attributes and return the string.
    sb.append( ")" );
    return sb.toString();
  }


  //
  // public String simpleString( boolean withHash, boolean deep, Set< Object >
  // seen,
  // Map< String, Object > otherOptions ) {
  // Pair< Boolean, Set< Object > > pair = Utils.seen( this, deep, seen );
  // if ( pair.first ) deep = false;
  // seen = pair.second;
  // StringBuffer sb = new StringBuffer();
  // sb.append( getName() + ":");
  //
  // if ( withHash ) sb.append( "@" + hashCode() );
  // sb.append( "(" );
  // boolean first = true;
  //
  //
  //
  // Parameter<?> firstParams[] = { startTime, duration, endTime }; // Could use
  // Arrays.sort() .search()
  // List< Parameter< ? > > allParams =
  // new ArrayList< Parameter< ? > >(Arrays.asList( firstParams ));
  // Set< Parameter< ? > > restParams = getParameters( false, null );
  // restParams.removeAll( allParams );
  // for ( Object p : restParams ) {
  // if ( first ) first = false;
  // else sb.append( ", " );
  // if ( p instanceof Parameter ) {
  // if (( (Parameter)p ).getValue() instanceof ParameterListenerImpl) {
  // sb.append( ((ParameterListenerImpl)(( (Parameter)p
  // ).getValue())).simpleString(withHash, deep, seen, otherOptions));
  // } else {
  // sb.append( ((Parameter<?>)p).toString( false, withHash, deep, seen,
  // otherOptions ) );
  //
  // }
  // } else {
  // sb.append( p.toString() );
  // }
  // sb.append( "\n" );
  // }
  // sb.append( ")" );
  // return sb.toString();
  // }


  public void executeAndSimulate() {
    executeAndSimulate( 1.0e12 );
  }

  public void executeAndSimulate( double timeScale ) {
    Timer timer = new Timer();
    amTopEventToSimulate = true;
    execute();
    System.out.println( "execution:\n" + executionToString() );
    simulate( timeScale );
    amTopEventToSimulate = false;
    System.out.println( "Finished executing and simulating:" );
    System.out.println( timer.toString() );
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#execute()
   */
  @Override
  public void execute() {
    if(mode != SolvingMode.SATISFY) {
        optimize();
        return;
    }

    // REVIEW -- differentiate between execute for simulation and execute in
    // external environment?
    if ( Debug.isOn() ) Debug.outln( getName() + ".execute()" );
    Timer timer = new Timer();
    System.out.println( getName() + ".execute(): starting stop watch" );
    boolean satisfied = satisfy( true, null );
    // Check if it's really satisfied.  TODO -- This shouldn't be necessary!!
    if (satisfied) satisfied = isSatisfied( true, null );
    if ( Debug.isOn() ) Debug.outln( getName()
                                     + ".execute() called satisfy() --> "
                                     + satisfied );
    // if ( !satisfied ) {
    // satisfied = solver.solve( getConstraints( true, null ) );
    // if ( Debug.isOn() ) Debug.outln( getName() + ".execute() called solve()
    // --> " + satisfied );
    // }
    timer.stop();
    System.out.println( "\n" + getName()
                        + ".execute(): Time taken to elaborate and resolve constraints:" );
    System.out.println( timer );
    timer.start();
    Collection< Constraint > constraints = getConstraints( true, null );
    if ( writeConstraintsOut ) {
      System.out.println( "All " + constraints.size() + " constraints: " );
      for ( Constraint c : constraints ) {
        System.out.println( "Constraint: " + c );
      }
    }
    if ( satisfied ) {
      System.out.println( "\nAll constraints were satisfied!" );
    } else {
      Collection< Constraint > unsatisfiedConstraints =
          ConstraintLoopSolver.getUnsatisfiedConstraints( constraints );
      if ( unsatisfiedConstraints.isEmpty() ) {
        System.out.println( ( constraints.size()
                              - unsatisfiedConstraints.size() )
                            + " out of " + constraints.size()
                            + " constraints were satisfied!" );
        System.err.println( getName() + "'s constraints were not satisfied!" );
      } else {
        System.err.println( "Could not resolve the following "
                            + unsatisfiedConstraints.size()
                            + " constraints for " + getName() + ":" );
        for ( Constraint c : unsatisfiedConstraints ) {
          System.err.println( c.toString() );
          c.isSatisfied( true, null ); // REVIEW -- can look shallow since
                                       // constraints
                                       // were gathered deep?!
        }
      }
    }

    System.out.println( "\n" + getName()
                        + ".execute(): Time taken to gather and write out constraints:" );
    System.out.println( timer );

    // HACK -- Sleeping to separate system.err from system.out.
    try {
      Thread.sleep( 100 );
    } catch ( InterruptedException e1 ) {
      e1.printStackTrace();
    }

  }

  public void optimize() {
      if(mode == SolvingMode.SATISFY) {
          System.err.println("in optimize(): mode is set to SATISFY!");
          return;
      }

      if(objectiveParamName == null || targetParamName == null) {
          System.err.println("objective and target parameters unknown; optimize mode: " + mode);
          return;
      }

      Timer timer = new Timer();
      System.out.println( getName() + ".optimize(): starting stop watch" );

      Parameter objective = this.getParameter(objectiveParamName);
      Parameter target = this.getParameter(targetParamName);

      if(objective == null) {
          System.err.println("could not find objective parameter " + objectiveParamName);
          return;
      }

      if(target == null) {
          System.err.println("could not find target parameter " + targetParamName);
          return;
      }

      if(objective.getType() != Double.class || target.getType() != Double.class) {
          System.err.println("objective has type " + objective.getType() + " and target has type " + target.getType());
          System.err.println("both types should be Double/Real");
          return;
      }

      System.out.println("Initial solve:");
      // solve first to get an initial constraint
      boolean satisfied = satisfy( true, null );

      // bail if failed to solve initially
      if(!satisfied) {
          Collection< Constraint > constraints = getConstraints( true, null );
          Collection< Constraint > unsatisfiedConstraints =
                  ConstraintLoopSolver.getUnsatisfiedConstraints( constraints );
          if ( unsatisfiedConstraints.isEmpty() ) {
              System.out.println( ( constraints.size()
                      - unsatisfiedConstraints.size() )
                      + " out of " + constraints.size()
                      + " constraints were satisfied!" );
              System.err.println( getName() + "'s constraints were not satisfied!" );
          } else {
              System.err.println( "Could not resolve the following "
                      + unsatisfiedConstraints.size()
                      + " constraints for " + getName() + ":" );
              for ( Constraint c : unsatisfiedConstraints ) {
                  System.err.println( c.toString() );
                  c.isSatisfied( true, null ); // REVIEW -- can look shallow since
                  // constraints
                  // were gathered deep?!
              }
          }

          System.out.println("Initial solve failed!!");

          return;
      }

      Double bestSoFar = (Double) objective.getValue();
      Map<Parameter, Object> bestSolution = null;
      Double bound = null;

      // set the initial next value to try
      Double nextToTry;
      // if we are maximizing and the initial value given is negative, try the positive version since doubling doesn't work
      // vice versa for minimizing
      if(mode == SolvingMode.MAXIMIZE && bestSoFar < 0 || mode == SolvingMode.MINIMIZE && bestSoFar > 0) {
          nextToTry = -bestSoFar;
      } else if(bestSoFar == 0) { // if the initial solution is 0, (arbitrarily) try 64 (for max) or -64 (for min)
          nextToTry = mode == SolvingMode.MAXIMIZE ? 64.0 : -64.0;
      } else { // otherwise just double the best so far
          nextToTry = bestSoFar*2.0;
      }

//      // construct the ConstraintExpression to be added to the list
//      Expression<Boolean> constraintFunction;
//      if(mode == SolvingMode.MAXIMIZE) {
//          constraintFunction = new Expression<>(new Functions.GreaterEquals<>(new Expression<Double>(objective), new Expression<>(nextToTry)));
//      } else {
//          constraintFunction = new Expression<>(new Functions.LessEquals<>(new Expression<Double>(objective), new Expression<>(nextToTry)));
//      }
//
//      ConstraintExpression optimizingConstraint = new ConstraintExpression(constraintFunction);
//      constraintExpressions.add(optimizingConstraint);

      // keep track of the old version of each constraint so we can remove
      ConstraintExpression oldOptimizingConstraint = null;
      ConstraintExpression oldBestSoFarConstraint = null;
      ConstraintExpression oldBoundConstraint = null;

      //DoubleParameter nextToTryP = new DoubleParameter("", nextToTry, (ParameterListener)null);

      //bound = mode == SolvingMode.MAXIMIZE ? Double.MAX_VALUE : -Double.MAX_VALUE;
      // keep going until we are confident in our value to a certain threshold
      while(bound == null || !Functions.eq(bestSoFar, nextToTry)) {
          System.out.println("trying to solve with " + objectiveParamName + " = " + nextToTry);

          if (mode == SolvingMode.MAXIMIZE) {
              System.out.println( objectiveParamName + " in [" + bestSoFar +
                                  ", " + bound + "]" );
          } else {
              System.out.println( objectiveParamName + " in [" + bound +
                                  ", " + bestSoFar + "]" );
          }
          System.out.println("trying to solve with " + objectiveParamName +
                             (mode == SolvingMode.MAXIMIZE ? ">=" : "<=")  +
                             nextToTry);

          // replace the constraint for bestSoFar -- helps arc consistency
          if ( oldBestSoFarConstraint != null ) {
              constraintExpressions.remove(oldBestSoFarConstraint);
          }
          Expression<Boolean> bestSoFarConstraintFunction;
          if(mode == SolvingMode.MAXIMIZE) {
              bestSoFarConstraintFunction =
                      new Expression<>(new Functions.GreaterEquals<>(new Expression<Double>(objective),
                                                                     new Expression<>(bestSoFar)));
          } else {
              bestSoFarConstraintFunction =
                      new Expression<>(new Functions.LessEquals<>(new Expression<Double>(objective),
                                                                  new Expression<>(bestSoFar)));
          }
          ConstraintExpression bestSoFarConstraint = new ConstraintExpression(bestSoFarConstraintFunction);
          constraintExpressions.add(bestSoFarConstraint);
          oldBestSoFarConstraint = bestSoFarConstraint;


          // replace the constraint for bound -- helps arc consistency
          if ( bound != null ) {
              if ( oldBoundConstraint != null ) {
                  constraintExpressions.remove( oldBoundConstraint );
              }
              Expression<Boolean> boundConstraintFunction;
              if ( mode == SolvingMode.MAXIMIZE ) {
                  boundConstraintFunction = new Expression<>( new Functions.LessEquals<>(
                          new Expression<Double>( objective ),
                          new Expression<>( bound ) ) );
              } else {
                  boundConstraintFunction = new Expression<>( new Functions.GreaterEquals<>(
                          new Expression<Double>( objective ),
                          new Expression<>( bound ) ) );
              }
              ConstraintExpression boundConstraint =
                      new ConstraintExpression( boundConstraintFunction );
              constraintExpressions.add( boundConstraint );
              oldBoundConstraint = boundConstraint;
          }

          // remove the old version of the optimizing constraint
          if ( oldOptimizingConstraint != null ) {
              constraintExpressions.remove(oldOptimizingConstraint);
          }

          // run arc consistency without the optimizing constraint
          Collection< Constraint > allConstraints = getConstraints( true, null );
          //allConstraints.add( optimizingConstraint );

          // See if arc consistency gives the solution.
          Consistency ac = null;
          Map<Variable<?>, Domain<?>> original = null;
          RangeDomain<Double> objectiveDomain = null;
          if ( usingArcConsistency ) {
              try {
                  ac = new Consistency();
                  ac.constraints = allConstraints;
                  original = ac.getDomainState();
                  ac.arcConsistency(arcConsistencyQuiet);
                  Domain d = objective.getDomain();
                  System.out.println( "domain of optimization param: " + d );
                  Double dblBound = null;
                  //
                  if ( d instanceof RangeDomain ) {
                      // get the upper or lower bound of the objective found by arc consistency
                      objectiveDomain = (RangeDomain<Double>)d;
                      Object b = null;
                      if ( mode == SolvingMode.MAXIMIZE ) {
                          b = objectiveDomain.getUpperBound();
                      } else {
                          b = objectiveDomain.getLowerBound();
                      }

                      // if bestSoFar is equal to the extreme found by arc consistency or if arc consistency has already
                      // fixated on a single value, we are done
                      try {
                          dblBound = Expression.evaluate( b, Double.class, false, false );
                          if ( Functions.eq( bestSoFar, dblBound ) ) {
                              System.out.println("optimize(): reached bound!");
                              break;
                          }
                          if ( Functions.eq(objectiveDomain.getLowerBound(), objectiveDomain.getUpperBound())) {
                              Object domainVal = objectiveDomain.getValue( false );
                              if ( domainVal instanceof Double ) {
                                  bestSoFar = (Double)domainVal;
                                  objective.value = bestSoFar;
                                  System.out.println(
                                          "optimize(): only one value possible: "
                                          + objective );
                                  break;
                              }
                          }
                      } catch ( Throwable e ) {
                      }
                  }

              } catch (Throwable t) {
                  Debug.error(true, false, "Error! Arc consistency failed.");
                  t.printStackTrace();
              } finally {
                  if ( original != null ) {
                      ac.restoreDomains( original );
                  }
              }
          }

          // construct the optimizing ConstraintExpression to be added to the list
          Expression<Boolean> optimizingConstraintFunction;
          if(mode == SolvingMode.MAXIMIZE) {
              optimizingConstraintFunction =
                      new Expression<>(new Functions.Equals<>(
                              new Expression<Double>(objective), new Expression<>(nextToTry)));
          } else {
              optimizingConstraintFunction
                      = new Expression<>(new Functions.Equals<>(
                              new Expression<Double>(objective), new Expression<>(nextToTry)));
          }
          ConstraintExpression optimizingConstraint = new ConstraintExpression(optimizingConstraintFunction);
          constraintExpressions.add(optimizingConstraint);
          oldOptimizingConstraint = optimizingConstraint;

          satisfied = satisfy(true, null);
          if ( satisfied ) {
              satisfied = isSatisfied( true, null );
          }

          if(satisfied) {
              System.out.println("successfully satisfied with " + objectiveParamName + " = " + objective.getValueNoPropagate());
              bestSoFar = nextToTry;
              bestSolution = saveAssignments();
          } else {
              System.out.println("unsuccessful with " + objectiveParamName + " = " + nextToTry);
              if ( bound == null ) {
                  bound = nextToTry;
              } else {
                  if ( mode == SolvingMode.MAXIMIZE ) {
                      bound = Math.min( bound, nextToTry );
                  } else {
                      bound = Math.max( bound, nextToTry );
                  }
              }
          }

          // pick next value
          if(bound == null) { // keep looking for the bound
              //bound is capped by Double
              if(Math.abs(bestSoFar) > Double.MAX_VALUE*0.5) {
                  nextToTry = bestSoFar*0.5 + bound*0.5;
              } else {
                  nextToTry = gov.nasa.jpl.ae.util.Math.times( bestSoFar, 2.0 );
              }
          } else { // binary search
              nextToTry = bestSoFar*0.5 + bound*0.5;
          }
          if ( objectiveDomain != null && !objectiveDomain.contains( nextToTry ) ) {
              if ( nextToTry < objectiveDomain.getLowerBound() ) {
                  nextToTry = objectiveDomain.getLowerBound();
              } else {
                  nextToTry = objectiveDomain.getUpperBound();
              }
              if ( Functions.eq( nextToTry, bestSoFar ) ) {
                  System.out.println("optimize(): no room to wiggle in domain, " + objectiveDomain + ". objective = " + objective);
                  break;
              }
          }

          Object underlyingExpression = optimizingConstraint.expression;
          if(underlyingExpression instanceof Call) {
              Object rhsValue = ((Call) underlyingExpression).arguments.get(1);
              if(rhsValue instanceof Expression) {
                  ((Expression) rhsValue).expression = new Expression<Double>(nextToTry);
              } else {
                  System.err.println("rhsValue is not an Expression - aborting");
                  return;
              }
          } else {
              System.err.println("underlyingExpression is not a Call - aborting");
              return;
          }

          this.setAllParametersToStale();
      }

      System.out.println("finished optimizing with " + objectiveParamName + " = " + bestSoFar);
      target.value = bestSoFar; // no setValue to avoid messiness with staleness

      timer.stop();
      System.out.println( "\n" + getName()
              + ".optimize(): Time taken to optimize:" );
      System.out.println( timer );

      if ( bestSolution != null ) {
          loadAssignments( bestSolution );
          satisfied = satisfy( true, null );
          if ( satisfied ) {
              satisfied = isSatisfied( true, null );
          }
          satisfied = true; // It was satisfied before, so . . . .
      }

      if ( writeConstraintsOut ) {
          Collection< Constraint > constraints = getConstraints( true, null );
          System.out.println( "All " + constraints.size() + " constraints: " );
          for ( Constraint c : constraints ) {
              System.out.println( "Constraint: " + c );
          }
      }
  }

  public EventSimulation createEventSimulation() {
    Set< Event > events = getEvents( true, null );
    System.out.println( "Simulating " + events.size() + " events." );
    EventSimulation sim = new EventSimulation( events, 1.0e12 );
    sim.topEvent = this;
    sim.add( (Event)this );
    settingTimeVaryingMapOwners = true;

    Map< String, Object > paramsAndTvms = getTimeVaryingObjectMap( true, null );

    settingTimeVaryingMapOwners = false;
    System.out.println( "Simulating " + paramsAndTvms.size()
                        + " state variables." );
    for ( Map.Entry< String, Object > entry : paramsAndTvms.entrySet() ) {
      Object tvo = entry.getValue();// e.getKey();
      Parameter< ? > p = null;
      TimeVarying< ?, ? > tv = null;
      String name = entry.getKey();
      if ( tvo instanceof Parameter ) {
        p = (Parameter< ? >)tvo;
        // name = p.getName();
        if ( p.getValueNoPropagate() instanceof TimeVarying ) {
          tv = (TimeVarying< ?, ? >)p.getValueNoPropagate();
        }
      } else if ( tvo instanceof TimeVarying ) {
        tv = (TimeVarying< ?, ? >)tvo;
      }
      if ( tv instanceof TimeVaryingMap ) {
        String category = "";
        if ( !Utils.isNullOrEmpty( name ) ) {
          category = name;
        } else {
          if ( tv instanceof TimeVaryingPlottableMap ) {
            category = ( (TimeVaryingPlottableMap< ? >)tv ).getName();
            // category = ((TimeVaryingPlottableMap<?>)tv).category.getValue();
          }
        }
        if ( Utils.isNullOrEmpty( category )
             && tv.getOwner() instanceof ParameterListener ) {
          category = ( (ParameterListener)tv.getOwner() ).getName();
        }
        // Debug.turnOn();
        sim.add( (TimeVaryingMap< ? >)tv, category );
      }
    }
    return sim;
  }

  public void simulate( double timeScale ) {
    simulate( timeScale, System.out );
  }

  public void simulate( double timeScale, java.io.OutputStream os ) {
    simulate( timeScale, os, doPlot );
  }

  public void simulate( double timeScale, java.io.OutputStream os,
                        boolean runPlotter ) {
    if ( Debug.isOn() ) {
      Debug.outln( "\nsimulate( timeScale=" + timeScale + ", runPlotter="
                   + runPlotter + " ): starting stop watch\n" );
    }
    Timer timer = new Timer();
    try {
      EventSimulation sim = createEventSimulation();
      sim.tryToPlot = runPlotter;
      System.out.println( sim.numEvents() + " event/state transitions." );
      sim.simulate( timeScale, os );
    } catch ( Exception e ) {
      e.printStackTrace();
    }
    if ( Debug.isOn() ) {
      Debug.outln( "\nsimulate( timeScale=" + timeScale + ", runPlotter="
                   + runPlotter + " ): completed\n" + timer + "\n" );
    }
  }

  @Override
  public void doSnapshotSimulation( boolean improved ) {
    boolean didntWriteFile = true;
    // augment file name
    String fileName = this.baseSnapshotFileName;
    if ( fileName != null ) {
      int pos = fileName.lastIndexOf( '.' );
      String pkg = getClass().getPackage().getName();
      if ( pkg != null ) {
        if ( pos == -1 ) pos = fileName.length();
        fileName = fileName.substring( 0, pos ) + "." + pkg
                   + fileName.substring( pos );
      }
      String bestFileName = "best_" + fileName;
      if ( !snapshotToSameFile ) {
        fileName = Utils.addTimestampToFilename( fileName );
      }
      File file = new File( fileName );

      // write out simulation to file
      System.out.println( "writing simulation snapshot to "
                          + file.getAbsolutePath() );
      try {
        if ( Debug.isOn() ) Debug.outln( "  which is the same as "
                                         + file.getCanonicalPath() );
      } catch ( IOException e1 ) {
        // ignore
      }
      System.out.println( TimeUtils.gmtCal.getTime().toString() );
      if ( writeSimulation( file ) ) {
        String fn = FileUtils.removeFileExtension( fileName );
        writeAspen( fn + ".mdl", fn + ".ini" );
        didntWriteFile = false;
      }
      if ( improved ) {
        writeSimulation( bestFileName );
      }
    }
    if ( didntWriteFile ) {
      writeSimulation( System.out );
    }
  }

  public boolean writeSimulation( String fileName ) {
    File file = new File( fileName );
    return writeSimulation( file );
  }

  public boolean writeSimulation( File file ) {
    boolean succ = false;
    FileOutputStream os = null;
    try {
      os = new FileOutputStream( file );
      succ = writeSimulation( os );
    } catch ( FileNotFoundException e ) {
      System.err.println( "Writing simulation output to file "
                          + file.getAbsolutePath() + " failed" );
      e.printStackTrace();
    }
    try {
      os.close();
    } catch ( IOException e ) {
      System.err.println( "Trouble closing output file "
                          + file.getAbsolutePath() );
      e.printStackTrace();
    }
    return succ;
  }

  public boolean writeSimulation( OutputStream os ) {
    PrintWriter w = new PrintWriter( os, true );
    w.println( "remaining " + solver.getUnsatisfiedConstraints().size()
               + " unsatisfied constraints: "
               + Utils.join( solver.getUnsatisfiedConstraints(),
                             "\nConstraint: " ) );
    w.println( "execution:\n" + executionToString() + "\n" );
    simulate( 1e15, os, false );
    return true;
  }

    /**
     * Elaborate for the specified class an event (or events from a TimeVaryingMap) requiring no arguments.
     * @param condition
     * @param eventClass
     * @param fromTimeVarying
     * @param <T>
     * @return
     */
    public < T extends Event > boolean elaborates( Expression< Boolean > condition, Class< T > eventClass,
                                                      Expression< TimeVaryingMap< ? > > fromTimeVarying
                                                 ) {
        LinkedHashMap<String, Expression<?>> map = new LinkedHashMap<String, Expression<?>>();
        boolean retVal = elaborates(condition, eventClass, fromTimeVarying, map);
        return retVal;
    }

    /**
     * Elaborate for the specified class an event (or events from a TimeVaryingMap) passing a simgle argument.
     * @param condition
     * @param eventClass
     * @param fromTimeVarying
     * @param paramName
     * @param argument
     * @param <T>
     * @return
     */
    public < T extends Event > boolean elaborates( Expression< Boolean > condition, Class< T > eventClass,
                                                   Expression< TimeVaryingMap< ? > > fromTimeVarying,
                                                   String paramName, Expression<?> argument
    ) {
        LinkedHashMap<String, Expression<?>> map = new LinkedHashMap<String, Expression<?>>();
        map.put(paramName, argument);
        boolean retVal = elaborates(condition, eventClass, fromTimeVarying, map);
        return retVal;
    }

    /**
     * Elaborate for the specified class an event (or events from a TimeVaryingMap) passing two arguments.
     * @param condition
     * @param eventClass
     * @param fromTimeVarying
     * @param paramName1
     * @param argument1
     * @param paramName2
     * @param argument2
     * @param <T>
     * @return
     */
    public < T extends Event > boolean elaborates( Expression< Boolean > condition, Class< T > eventClass,
                                                   Expression< TimeVaryingMap< ? > > fromTimeVarying,
                                                   String paramName1, Expression<?> argument1,
                                                   String paramName2, Expression<?> argument2
    ) {
        LinkedHashMap<String, Expression<?>> map = new LinkedHashMap<String, Expression<?>>();
        map.put(paramName1, argument1);
        map.put(paramName2, argument2);
        boolean retVal = elaborates(condition, eventClass, fromTimeVarying, map);
        return retVal;
    }

    /**
     * Elaborate for the specified class an event (or events from a TimeVaryingMap) passing three or more arguments.
     * @param condition
     * @param eventClass
     * @param fromTimeVarying
     * @param paramName1
     * @param argument1
     * @param paramName2
     * @param argument2
     * @param paramName3
     * @param argument3
     * @param moreArgs
     * @param <T>
     * @return
     */
    public < T extends Event > boolean elaborates( Expression< Boolean > condition, Class< T > eventClass,
                                                   Expression< TimeVaryingMap< ? > > fromTimeVarying,
                                                   String paramName1, Expression<?> argument1,
                                                   String paramName2, Expression<?> argument2,
                                                   String paramName3, Expression<?> argument3,
                                                   Object... moreArgs
    ) {
        LinkedHashMap<String, Expression<?>> map = new LinkedHashMap<String, Expression<?>>();
        map.put(paramName1, argument1);
        map.put(paramName2, argument2);
        map.put(paramName3, argument3);
        for ( int i=0; i < moreArgs.length-1; i += 2 ) {
            Object paramNameArg = moreArgs[i];
            Object argumentArg = moreArgs[i+1];
            if ( paramNameArg instanceof String ) {
                if (argumentArg instanceof Expression ) {
                    map.put((String)paramNameArg, (Expression<?>)argumentArg);
                } else {
                    Debug.error(true, false, "Bad arguments (" + paramNameArg+ ", " + argumentArg + ") passed to elaborate " + eventClass.getCanonicalName() + "!  Expected (String, Expression).");
                }
            } else {
                Debug.error(true, false, "Bad arguments (" + paramNameArg+ ", " + argumentArg + ") passed to elaborate " + eventClass.getCanonicalName() + "!  Expected (String, Expression)");
            }
        }
        boolean retVal = elaborates(condition, eventClass, fromTimeVarying, map);
        return retVal;
    }

    protected < T extends Event > boolean elaborates( Expression< Boolean > condition, Class< T > eventClass,
                             Expression< TimeVaryingMap< ? > > fromTimeVarying,
                             Map<String, Expression<?>> argumentMap
                             //Expression< ? >... arguments
                             ) {
        if ( Debug.isOn() ) {
            Debug.outln( "VVVVVVVVVVVVVVVVVVVVVVVV   " + name + ".elaborates(" + eventClass.getSimpleName() + ", " +
                            (fromTimeVarying == null ? "null" :
                                    ClassUtils.getName(fromTimeVarying)) + ", " +
                            argumentMap.toString() + ")" );
        }
        if (condition == null) {
            condition = new Expression<Boolean>(true);
        }

        Expression<?>[] arguments = Utils.toArrayOfType(argumentMap.values(), Expression.class);

        Class<?> eventEnclosingClass = eventClass.getEnclosingClass();
        Object enclosingThis = eventEnclosingClass == null ? null : this;
        
        while (enclosingThis != null && !eventEnclosingClass.isInstance( enclosingThis )) {
          if (enclosingThis instanceof ParameterListenerImpl) {
            enclosingThis = ((ParameterListenerImpl)enclosingThis).enclosingInstance;
          } else {
            enclosingThis = null;
          }
        }
        
        addElaborationRule(condition, enclosingThis, eventClass,
                           eventClass == null ? "event" :
                           eventClass.getSimpleName(), arguments,
                           fromTimeVarying);
        
        return true;
    }

  // Create an ElaborationRule for constructing an eventClass with
  // constructorParamTypes.
  // This method assumes that there is a constructor whose parameters match the
  // types of the arguments.
  public < T extends Event > ElaborationRule
         addElaborationRule( Expression< Boolean > condition,
                             Parameter< ? > enclosingInstance,
                             Class< T > eventClass, String eventName,
                             Expression< ? >[] arguments,
                             Expression< TimeVaryingMap< ? > > fromTimeVarying ) {

    // Now create the EventInvocation from the constructor and arguments.
    Vector< EventInvocation > invocation = new Vector< EventInvocation >();
    EventInvocation newInvocation = null;
    newInvocation =
        new EventInvocation( eventClass, eventName, enclosingInstance,
                             arguments, fromTimeVarying,
                             (Map< String, Object >)null );
    invocation.add( newInvocation );
    Vector< Event > eventVector = new Vector< Event >();
    ElaborationRule elaborationRule =
        new ElaborationRule( condition, invocation );

    if ( elaborations.keySet().contains(elaborationRule) ) {
        System.out.println("Tried to add same elaboration (" + elaborationRule.toString() + ") rule to elaborations for " + getName());
        return null;
    }

    elaborations.put( elaborationRule, eventVector );
    return elaborationRule;
  }

  public < T extends Event > ElaborationRule
         addElaborationRule( Expression< Boolean > condition,
                             Object enclosingInstance, Class< T > eventClass,
                             String eventName, Expression< ? >[] arguments ) {
    Parameter< ? > p = null;
    if ( enclosingInstance instanceof Parameter ) {
      p = (Parameter< ? >)enclosingInstance;
    } else {// if ( enclosingInstance != null ) {
      p = new Parameter< Object >( "", null, enclosingInstance, this );
    }
    return addElaborationRule( condition, p, eventClass, eventName, arguments,
                               null );
  }

  public < T extends Event > ElaborationRule
         addElaborationRule( Expression< Boolean > condition,
                             Object enclosingInstance, Class< T > eventClass,
                             String eventName, Expression< ? >[] arguments,
                             Expression< TimeVaryingMap< ? > > fromTimeVarying ) {
    return addElaborationRule( condition,
                               new Parameter< Object >( "", null,
                                                        enclosingInstance,
                                                        this ),
                               eventClass, eventName, arguments,
                               fromTimeVarying );
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#addEffect(event.TimeVarying, java.lang.reflect.Method,
   * java.util.Vector)
   */
  @Override
  public void addEffect( Parameter< ? > sv, Object obj, Method effectFunction,
                         Vector< Object > arguments ) {
    assert sv != null;
    Effect e =
        new EffectFunction( obj, effectFunction, arguments, (Class< ? >)null ); // TODO?
                                                                                // last
                                                                                // arg?
    addEffect( sv, e );
  }

  public void addEffect( Parameter< ? > sv, Effect e ) {
    checkIfEffectVariableMatches( sv, e );
    Set< Effect > effectSet = null;
    // Pair< Parameter< ? >, Set< Effect >> p = null;
    for ( Pair< Parameter< ? >, Set< Effect > > pp : effects ) {
      if ( Utils.valuesEqual( pp.first.getValue( true ),
                              sv.getValue( true ) ) ) {
        // p = pp;
        effectSet = pp.second;
        break;
      }
    }
    // if ( p != null ) {
    //// if ( effects.containsKey( sv ) ) {
    // effectSet = p.second; //effects.get( sv );
    // }
    if ( effectSet == null ) {
      effectSet = new LinkedHashSet< Effect >();
      effects.add( new Pair< Parameter< ? >, Set< Effect > >( sv, effectSet ) );
    }
    if ( Debug.isOn() ) Debug.outln( getName() + "'s effect (" + e
                                     + ") in being added to set (" + effectSet
                                     + ") for variable (" + sv + ")." );
    effectSet.add( e );
  }

  public void addEffects( Parameter< ? > sv, Set< Effect > set ) {
    if ( set == null || sv == null ) {// || sv.getValue( false ) == null ) {
      Debug.error( false, "Error! null arguments to " + name + ".addEffects("
                          + sv + ", " + set + ")" );
      return;
    }
    Set< Effect > effectSet = null;
    for ( Pair< Parameter< ? >, Set< Effect > > pp : effects ) {
      if ( pp.first.getValue( true ) != null
           && Utils.valuesEqual( pp.first.getValue( true ),
                                 sv.getValue( true ) ) ) {
        if ( Debug.isOn() ) Debug.outln( getName() + "'s addEffect() says "
                                         + pp.first.getValue( true ) + " == "
                                         + sv.getValue( true ) );
        effectSet = pp.second;
        break;
      }
    }
    if ( effectSet == null ) {
      effectSet = new LinkedHashSet< Effect >();// set;
      effects.add( new Pair< Parameter< ? >, Set< Effect > >( sv, effectSet ) );
    }
    if ( set != null ) {
      effectSet.addAll( set );
    }
    if ( Debug.isOn() ) {
      for ( Pair< Parameter< ? >, Set< Effect > > pp : effects ) {
        if ( Utils.valuesEqual( pp.first.getValue( false ),
                                sv.getValue( false ) ) ) {
          effectSet = pp.second;
          for ( Effect effect : effectSet ) {
            if ( effect instanceof EffectFunction ) {
              EffectFunction ef = (EffectFunction)effect;
              if ( ef.object != null && !pp.first.equals( ef.object ) ) {
                Debug.error( true,
                             "Error! effect variable (" + pp.first
                                   + ") does not match that of EffectFunction! ("
                                   + ef + ")" );
              }
            }
          }
        }
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#addEffect(event.TimeVarying, java.lang.Object,
   * java.lang.reflect.Method, java.lang.Object)
   */
  @Override
  public void addEffect( Parameter< ? > sv, Object obj, Method method,
                         Object arg ) {
    Vector< Object > v = new Vector< Object >();
    v.add( arg );
    addEffect( sv, obj, method, v );
  }

  // These effects are dynamic, so we don't want to add them to the static set.
  // public void addEffects( Dependency<?> dependency ) {
  // for ( Effect e : getEffectsFromDependency( dependency ) ) {
  // addEffect()
  // }
  // }

  // Gather effect functions from a Dependency.
  public static Collection< Effect >
         getEffectsFromDependency( Dependency< ? > d ) {
    if ( d == null || d.getExpression() == null ) return Utils.getEmptyList();
    List< Effect > effects = new ArrayList< Effect >();
    List< FunctionCall > calls = d.getExpression().getFunctionCalls();
    Affectable affectable = null;
    for ( FunctionCall call : calls ) {
      try {
        affectable = Expression.evaluate( call.getObject(), Affectable.class,
                                          false, false );
      } catch ( ClassCastException e ) {
        // ignore
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
      if ( affectable != null ) {
        if ( affectable.doesAffect( call.getMethod() ) ) {
          Effect effect = new EffectFunction( call );
          effects.add( effect );
        }
      }
    }
    return effects;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * gov.nasa.jpl.ae.event.ParameterListenerImpl#getTimeVaryingObjects(boolean,
   * java.util.Set)
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
    if ( seen != null ) seen.remove( this );
    Set< TimeVarying< ?, ? > > set =
        super.getTimeVaryingObjects( deep, includeDependencies, seen );
    // Set< TimeVarying< ? > > set = new TreeSet< TimeVarying< ? > >();
    set =
        Utils.addAll( set,
                      HasTimeVaryingObjects.Helper.getTimeVaryingObjects( effects,
                                                                          deep, seen ) );
    if ( deep ) {
      set =
          Utils.addAll( set,
                        HasTimeVaryingObjects.Helper.getTimeVaryingObjects( elaborationsConstraint,
                                                                            deep, seen ) );
      set =
          Utils.addAll( set,
                        HasTimeVaryingObjects.Helper.getTimeVaryingObjects( effectsConstraint,
                                                                            deep, seen ) );
      set =
          Utils.addAll( set,
                        HasTimeVaryingObjects.Helper.getTimeVaryingObjects( elaborations,
                                                                            deep, seen ) );
      for ( Event e : getEvents( false, null ) ) {
        if ( e instanceof HasTimeVaryingObjects ) {
          set =
              Utils.addAll( set,
                            ( (HasTimeVaryingObjects)e ).getTimeVaryingObjects( deep,
                                                                                seen ) );
        }
      }
    }
    return set;
  }

  @Override
  public Collection< ParameterListenerImpl >
         getNonEventObjects( boolean deep, Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    if ( seen != null ) seen.remove( this );
    Collection< ParameterListenerImpl > set =
        super.getNonEventObjects( deep, seen );
    for ( Pair< Parameter< ? >, Set< Effect > > e : getEffects() ) {
      set.addAll( getNonEventObjects( e, deep, seen ) );
    }
    if ( deep ) {
      set.addAll( getNonEventObjects( elaborationsConstraint, deep, seen ) );
      set.addAll( getNonEventObjects( effectsConstraint, deep, seen ) );
      for ( Entry< ElaborationRule, Vector< Event > > e : getElaborations().entrySet() ) {
        set.addAll( getNonEventObjects( e, deep, seen ) );
      }
      for ( Event e : getEvents( false, null ) ) {
        set.addAll( getNonEventObjects( e, deep, seen ) );
      }
    }
    return set;
  }

  /*
   * Gather any parameter instances contained by this event. (non-Javadoc)
   * 
   * @see gov.nasa.jpl.ae.event.ParameterListenerImpl#getParameters(boolean,
   * java.util.Set)
   */
  @Override
  public Set< Parameter< ? > > getParameters( boolean deep,
                                              Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > pair = Utils.seen( this, deep, seen );
    if ( pair.first ) return Utils.getEmptySet();
    seen = pair.second;
    if ( seen != null ) seen.remove( this );
    Set< Parameter< ? > > set = super.getParameters( deep, seen );
    if ( deep ) {
      set = Utils.addAll( set,
                          HasParameters.Helper.getParameters( elaborationsConstraint,
                                                              deep, seen,
                                                              true ) );
      set = Utils.addAll( set,
                          HasParameters.Helper.getParameters( effectsConstraint,
                                                              deep, seen,
                                                              true ) );
      set = Utils.addAll( set,
                          HasParameters.Helper.getParameters( elaborations,
                                                              deep, seen,
                                                              true ) );
      set =
          Utils.addAll( set, HasParameters.Helper.getParameters( effects, deep,
                                                                 seen, true ) );
      for ( Event e : getEvents( deep, null ) ) {
        if ( e instanceof HasParameters ) {
          set =
              Utils.addAll( set,
                            ( (HasParameters)e ).getParameters( deep, seen ) );
        }
      }
    }
    return set;
  }

  public static boolean newMode = false;

  /*
   * (non-Javadoc)
   * 
   * @see gov.nasa.jpl.ae.event.ParameterListenerImpl#tryToSatisfy(boolean,
   * java.util.Set)
   */
  @Override
  protected boolean tryToSatisfy( boolean deep, Set< Satisfiable > seen ) {
    // if ( //amTopEventToSimulate &&
    // mode % 2 == 1 ) {
    // return tryToSatisfy( deep, seen, false );
    // }
    return tryToSatisfy( deep, seen, newMode );
  }

  protected boolean tryToSatisfy( boolean deep, Set< Satisfiable > seen,
                                  boolean newTrySat ) {
    boolean satisfied = true;
    if ( newTrySat ) {
      satisfied = super.tryToSatisfy2( deep, seen );
    } else {
      satisfied = super.tryToSatisfy( deep, seen );
    }
    if ( satisfied ) {
      // already handled through getConstraints()
      // if ( !effectsConstraint.satisfy( deep, seen ) ) {
      // satisfied = false;
      // }
      if ( !Satisfiable.Helper.satisfy( effects, deep, seen ) ) {
        satisfied = false;
      }
      // already handled through getConstraints()
      // if ( !elaborationsConstraint.satisfy( false, seen ) ) {
      // satisfied = false;
      // }
      // REVIEW -- Is this necessary if elaborationsConstraint is called? Well,
      // they are different in that satisfyElaborations tries to satisfy the
      // events after they are elaborated. Maybe the constraint should do this
      // so that we can get rid of satisfyElaborations(), or maybe
      // elaborationsConstraint.satisfy() should simply call
      // satisfyElaborations.
      if ( !satisfyElaborations( deep, seen ) ) {
        satisfied = false;
      }
    }
    if ( Debug.isOn() ) Debug.outln( this.getClass().getName()
                                     + " satisfy loop called satisfyElaborations() " );
    return satisfied;
  }

  @Override
  public long getNumberOfResolvedConstraints( boolean deep,
                                              Set< HasConstraints > seen ) {
    Pair< Boolean, Set< HasConstraints > > pair =
        Utils.seen( this, deep, seen );
    if ( pair.first ) {
      return 0;
    }
    seen = pair.second;
    if ( seen != null ) seen.remove( this );
    long num = 0;
    num += super.getNumberOfResolvedConstraints( deep, seen );
    seen.add(this);
    num += elaborationsConstraint.getNumberOfResolvedConstraints( false, seen );
    num += effectsConstraint.getNumberOfResolvedConstraints( deep, seen );
    if ( deep ) {
      num +=
          HasConstraints.Helper.getNumberOfResolvedConstraints( elaborations.keySet(),
                                                                deep, seen );
      num += HasConstraints.Helper.getNumberOfResolvedConstraints( effects,
                                                                   deep, seen );
      Set< Event > events = getEvents( false, null );
      num += HasConstraints.Helper.getNumberOfResolvedConstraints( events, deep,
                                                                   seen );
    }
    return num;
  }

  @Override
  public long getNumberOfUnresolvedConstraints( boolean deep,
                                                Set< HasConstraints > seen ) {
    Pair< Boolean, Set< HasConstraints > > pair =
        Utils.seen( this, deep, seen );
    if ( pair.first ) {
      return 0;
    }
    seen = pair.second;
    if ( seen != null ) seen.remove( this );
    long num = 0;
    num += super.getNumberOfUnresolvedConstraints( deep, seen );
    num +=
        elaborationsConstraint.getNumberOfUnresolvedConstraints( false, seen );
    num += effectsConstraint.getNumberOfUnresolvedConstraints( deep, seen );
    if ( deep ) {
      num +=
          HasConstraints.Helper.getNumberOfUnresolvedConstraints( elaborations.keySet(),
                                                                  deep, seen );
      num +=
          HasConstraints.Helper.getNumberOfUnresolvedConstraints( effects, deep,
                                                                  seen );
      Set< Event > events = getEvents( false, null );
      num +=
          HasConstraints.Helper.getNumberOfUnresolvedConstraints( events, deep,
                                                                  seen );
    }
    return num;
  }

  @Override
  public long getNumberOfConstraints( boolean deep,
                                      Set< HasConstraints > seen ) {
    Pair< Boolean, Set< HasConstraints > > pair =
        Utils.seen( this, deep, seen );
    if ( pair.first ) {
      return 0;
    }
    seen = pair.second;
    if ( seen != null ) seen.remove( this );
    long num = 0;
    num += super.getNumberOfConstraints( deep, seen );
    num += elaborationsConstraint.getNumberOfConstraints( false, seen );
    num += effectsConstraint.getNumberOfConstraints( deep, seen );
    if ( deep ) {
      num +=
          HasConstraints.Helper.getNumberOfConstraints( elaborations.keySet(),
                                                        deep, seen );
      num +=
          HasConstraints.Helper.getNumberOfConstraints( effects, deep, seen );
      Set< Event > events = getEvents( false, null );
      num += HasConstraints.Helper.getNumberOfConstraints( events, deep, seen );
    }
    return num;
  }

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
    if ( seen != null ) seen.remove( this );
    Collection< Constraint > set = new LinkedHashSet< Constraint >();
    set = Utils.addAll( set, super.getConstraints( deep, seen ) );
    // if ( set.equals( Utils.getEmptySet() ) ) return set;
    set.add( elaborationsConstraint );
    set.add( effectsConstraint );
    if ( deep ) {
      set = Utils.addAll( set,
                          HasConstraints.Helper.getConstraints( elaborationsConstraint,
                                                                false, seen ) );
      set = Utils.addAll( set,
                          HasConstraints.Helper.getConstraints( effectsConstraint,
                                                                deep, seen ) );
      set = Utils.addAll( set,
                          HasConstraints.Helper.getConstraints( elaborations.keySet(),
                                                                false, seen ) );
      set = Utils.addAll( set,
                          HasConstraints.Helper.getConstraints( effects, deep,
                                                                seen ) );
      Set< Event > events = getEvents( false, null );
      set =
          Utils.addAll( set, HasConstraints.Helper.getConstraints( events, deep,
                                                                   seen ) );
    }
    Parameter.mayPropagate = mayHaveBeenPropagating;
    Parameter.mayChange = mayHaveBeenChanging;
    return set;
  }

  @Override
  public CollectionTree getConstraintCollection( boolean deep,
                                                 Set< HasConstraints > seen ) {
    Pair< Boolean, Set< HasConstraints > > pair =
        Utils.seen( this, deep, seen );
    if ( pair.first ) {
      return null;
    }
    seen = pair.second;

    if ( constraintCollection == null ) {
      if ( seen != null ) seen.remove( this );
      constraintCollection = super.getConstraintCollection( deep, seen );
      constraintCollection.add( elaborationsConstraint );
      constraintCollection.add( effectsConstraint );
      if ( deep ) {
        constraintCollection.add( elaborationsConstraint );
        constraintCollection.add( effectsConstraint );
        constraintCollection.add( elaborations );
        constraintCollection.add( effects );
      }
    }
    return constraintCollection;
  }

  public static void testConstraintCollection() {
    // TODO -- HERE!
  }

  /*
   * Gather any event instances contained by this event.
   *
   * NOTE: Uses reflection to dig events out of members, but does not look in
   * arrays or collections other than elaborations, so this may need
   * redefinition in subclasses. This enforces the idea that events do not occur
   * unless elaborated.
   *
   * (non-Javadoc)
   *
   * @see gov.nasa.jpl.ae.event.HasEvents#getEvents(boolean)
   */
  @Override
  public Set< Event > getEvents( boolean deep, Set< HasEvents > seen ) {
      return super.getEvents( deep, seen );
    //if ( elaborations == null ) return Utils.getEmptySet();
//    Pair< Boolean, Set< HasEvents > > pair = Utils.seen( this, deep, seen );
//    if ( pair.first ) return Utils.getEmptySet();
//    seen = pair.second;
//
//      Set< Event > set = new LinkedHashSet< Event >();
//    if ( elaborations != null ) {
//        for (Entry<ElaborationRule, Vector<Event>> e : elaborations.entrySet()) {
//            if (e.getValue() == null) continue;
//            for (Event event : e.getValue()) {
//                set.add(event);
//                if (deep) {
//                    if (event instanceof HasEvents) set =
//                            Utils.addAll(set, ((HasEvents) event).getEvents(deep, seen));
//                }
//            }
//        }
//    }
//
//    if ( seen != null ) {
//        seen.remove(this);
//    }
//    Set<Event> moreEvents = super.getEvents(deep, seen);
//    set.addAll( moreEvents );
//
//    return set;
  }

    public Set<ParameterListenerImpl> getObjects(boolean deep, Set<HasEvents> seen) {
        Pair<Boolean, Set<HasEvents>> pair = Utils.seen(this, deep, seen);
        if (pair.first) return Utils.getEmptySet();
        seen = pair.second;
        Set<ParameterListenerImpl> set = new LinkedHashSet<ParameterListenerImpl>();
        if ( elaborations != null ) {
            for (Entry<ElaborationRule, Vector<Event>> e : elaborations.entrySet()) {
                if (e.getValue() == null) continue;
                for (Event event : e.getValue()) {
                    if ( event instanceof ParameterListenerImpl ) {
                        ParameterListenerImpl obj = (ParameterListenerImpl) event;
                        set.add( obj );
                        if (deep) {
                            set = Utils.addAll(set, obj.getObjects(deep, seen));
                        }
                    }
                }
            }
        }

        if ( seen != null ) {
            seen.remove(this);
        }
        Set<ParameterListenerImpl> moreEvents = super.getObjects(deep, seen);
        set.addAll( moreEvents );

        return set;
    }

        // Conditionally create child event instances from this event instance.
  /*
   * (non-Javadoc)
   * 
   * @see event.Event#elaborate(boolean)
   */
  @Override
  public void elaborate( boolean force ) {
    if ( elaborations == null ) {
      elaborations = new LinkedHashMap< ElaborationRule, Vector< Event > >();
    }
    for ( Entry< ElaborationRule, Vector< Event > > er : elaborations.entrySet() ) {
      if ( Debug.isOn() ) Debug.outln( getName() + " trying to elaborate "
                                       + er );
      elaborate( er, force );
    }
  }

  /**
   * @param entry
   * @return
   */
  protected boolean
            isElaborated( Entry< ElaborationRule, Vector< Event > > entry ) {
    return isElaborated( entry.getValue() );
  }

  /**
   * @param events
   * @return
   */
  protected boolean isElaborated( Vector< Event > events ) {
    boolean r = !Utils.isNullOrEmpty( events );
    if ( Debug.isOn() ) Debug.outln( "isElaborated(" + events + ") = " + r
                                     + " for " + this.getName() );
    return r;
  }

  /**
   * @param entry
   * @param force
   * @return
   */
  public boolean elaborate( Entry< ElaborationRule, Vector< Event > > entry,
                            boolean force ) {
    boolean elaborated = true;
    if ( !isElaborated( entry ) || force ) {

      // Don't elaborate outside the horizon. Need startTime grounded to know.
      if ( !startTime.isGrounded( false, null ) ) return false;
      // REVIEW -- is force a good argument to getValue()?
      if ( startTime.getValue( force ) >= Timepoint.getHorizonDuration() ) {
        if ( Debug.isOn() ) Debug.outln( "satisfyElaborations(): No need to elaborate event outside the horizon: "
                                         + getName() );
        return true;
      }

      Vector< Event > oldEvents = entry.getValue();
      elaborated = entry.getKey().attemptElaboration( this, oldEvents, true );
    }
    return elaborated;
  }

  @Override
  public boolean isDeconstructed() {
    if ( Utils.isNullOrEmpty( effects ) && Utils.isNullOrEmpty( elaborations )
         && super.isDeconstructed() ) {
      return true;
    }
    return false;
  }

  /*
   * Try to remove others' references to this, possibly because it is being
   * deleted. (non-Javadoc)
   * 
   * @see gov.nasa.jpl.ae.event.ParameterListenerImpl#detach()
   */
  @Override
  public void deconstruct() {
    if ( isDeconstructed() ) {
      if ( Debug.isOn() ) {
        Debug.outln( "Attempted to deconstruct a deconstructed event: "
                     + this );
        // try {
        // Thread.sleep(100);
        // } catch ( InterruptedException e ) {
        // // TODO Auto-generated catch block
        // e.printStackTrace();
        // }
      }
      return;
    }
    String msg = "Deconstructing event: " + this.toString( true, false, null );
    if ( Debug.isOn() ) Debug.outln( msg );
    System.err.println( msg );

    // Get time varying objects to use later before potentially disconnecting
    // them.
    Set< TimeVarying< ?, ? > > timeVaryingObjs =
        getTimeVaryingObjects( true, null );

    // Detach elaborations.
    if ( elaborations != null ) {
      for ( Entry< ElaborationRule, Vector< Event > > e : elaborations.entrySet() ) {
        for ( Event evt : e.getValue() ) {
          if ( evt != null ) evt.deconstruct();
        }
        e.getKey().deconstruct();
      }
      elaborations.clear();
      // elaborations = null;
    }

    // Detach effects.
    assert effects != null;
    for ( Pair< Parameter< ? >, Set< Effect > > p : effects ) {
      Parameter< ? > tvp = p.first;
      TimeVarying< ?, ? > tv = null;
      if ( tvp != null ) {
        try {
          tv = Expression.evaluate( tvp, TimeVarying.class, false );
        } catch ( ClassCastException e ) {
          // TODO Auto-generated catch block
          // e.printStackTrace();
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
        // tvp.deconstruct();
      }
      Set< Effect > set = p.second;
      if ( set != null ) {
        for ( Effect e : set ) {
          if ( tv != null ) {
            e.unApplyTo( tv ); // should this happen in EffectFunction?
          }
          // if ( e instanceof EffectFunction ) {
          // ((EffectFunction)e).deconstruct();
          // }
        }
      }
    }
    effects.clear();

    // Detach effects embedded in dependency expressions.
    for ( Dependency< ? > dependency : getDependencies() ) {
      for ( Effect e : getEffectsFromDependency( dependency ) ) {
        EffectFunction effectFunction;
        if ( e instanceof EffectFunction ) {
          effectFunction = (EffectFunction)e;
        } else {
          continue;
        }
        if ( effectFunction.getObject() != null ) {
          TimeVarying< ?, ? > tv = null;
          try {
            tv = Expression.evaluate( effectFunction.getObject(),
                                      TimeVarying.class, false, false );
          } catch ( ClassCastException e1 ) {
            // TODO Auto-generated catch block
            // e1.printStackTrace();
          } catch ( IllegalAccessException e1 ) {
            // TODO Auto-generated catch block
            // e1.printStackTrace();
          } catch ( InvocationTargetException e1 ) {
            // TODO Auto-generated catch block
            // e1.printStackTrace();
          } catch ( InstantiationException e1 ) {
            // TODO Auto-generated catch block
            // e1.printStackTrace();
          }
          if ( tv != null ) {
            effectFunction.unApplyTo( tv );
          }
        }
      }
    }

    // Remove references to time parameters from TimeVaryingMaps.
    // TODO -- REVIEW -- Is this already being done by
    // ParameterListenerImpl.detach()
    // and TimeVaryingMap.detach( parameter )?
    Set< Parameter< Long > > timepoints = getTimepoints( false, null );
    for ( TimeVarying< ?, ? > tv : timeVaryingObjs ) {
      if ( tv instanceof TimeVaryingMap ) {
        TimeVaryingMap< ? > tvm = (TimeVaryingMap< ? >)tv;
        for ( Parameter< Long > p : timepoints ) {
          if ( Debug.isOn() ) Debug.out( "i" );
          tvm.detach( p );
        }
        // ( (TimeVaryingMap<?>)tv ).keySet().removeAll( timepoints );
        // ( (TimeVaryingMap<?>)tv ).isConsistent();
      }
    }

    super.deconstruct();

    if ( Debug.isOn() ) Debug.outln( "Done deconstructing event: "
                                     + this.toString( true, true, null ) );
  }

  @Override
  public void detach( Parameter< ? > parameter ) {
    super.detach( parameter );
    // TODO - REVIEW - remove effects referencing parameter (does this make
    // sense?)
    for ( Pair< Parameter< ? >, Set< Effect > > p : effects ) {
      Parameter< ? > tvp = p.first;
      ArrayList< Effect > set = new ArrayList< Effect >( p.second );
      for ( Effect e : set ) {
        if ( e instanceof EffectFunction ) {
          EffectFunction ef = (EffectFunction)e;
          if ( ef.hasParameter( parameter, false, null ) ) {
            effects.remove( e );
          }
        }
      }
    }

    // TODO - REVIEW - remove elaborations referencing parameter (does this make
    // sense?)

    // See if other events need to detach the parameter
    for ( Event e : getEvents( false, null ) ) {
      if ( e instanceof ParameterListener ) {
        ( (ParameterListener)e ).detach( parameter );
      }
    }
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#executionToString()
   */
  @Override
  public String executionToString() {
    StringBuffer sb = new StringBuffer();
    Set< Event > events = new TreeSet< Event >();
    events.add( this );
    events = Utils.addAll( events, getEvents( true, null ) );
    for ( Event e : events ) {
      sb.append( MoreToString.Helper.toString( e, true, false, null ) + "\n" );
    }
    for ( ParameterListenerImpl pl : getNonEventObjects( true, null ) ) {
      sb.append( MoreToString.Helper.toString( pl, true, false, null ) + "\n" );
    }
    // for ( TimeVarying<?> tv : getTimeVaryingObjects( true, null ) ) {
    // sb.append( MoreToString.Helper.toString( tv, true, true, null ) + "\n" );
    // }
    Set< Object > seen = new LinkedHashSet< Object >();
    Map<String, Object> tvoMap = getTimeVaryingObjectMap(true, null);
    for ( Object o : tvoMap.values() ) {
      if ( o == null ) continue;
      if ( seen.contains( o ) ) continue;
      TimeVaryingMap< ? > tvm = Functions.tryToGetTimelineQuick( o );
      if ( tvm != null ) {
          if ( seen.contains( tvm ) ) continue;
//        Object owner = tvm.getOwner();
//        if ( owner instanceof Parameter ) {
//          // if ( seen.contains( owner ) ) continue;
//          if ( !seen.contains( owner ) ) {
//            seen.add( o );
//            seen.add( owner );
//            o = owner;
//          }
//        }
        seen.add( tvm );
      }
      // Object value = o;
      // if ( o instanceof Parameter ) {
      // Object v = ( (Parameter)o ).getValueNoPropagate();
      // if ( v instanceof HasOwner ) {
      // Object owner = ( (HasOwner)v ).getOwner();
      // if ( owner instanceof Parameter )
      // }
      // }
      //// TimeVarying<?> tv = null;
      //// if ( o instanceof TimeVarying ) {
      //// tv = (TimeVarying<?>)o;
      //// } else if (o instanceof Parameter && ) {
      //// tv =
      //// }
      sb.append( MoreToString.Helper.toString( o, true, true, null ) + "\n" );
    }
    return sb.toString();
  }

  public void writeAspen( String mdlFileName, String iniFileName ) {
    FileOutputStream mdlOs = null;
    FileOutputStream iniOs = null;
    try {
      mdlOs = new FileOutputStream( mdlFileName );
      iniOs = new FileOutputStream( iniFileName );
      writeAspen( mdlOs, iniOs );
    } catch ( FileNotFoundException e ) {
      e.printStackTrace();
    }
    try {
      if ( mdlOs != null ) {
        mdlOs.flush();
        mdlOs.close();
      }
    } catch ( IOException e ) {
      e.printStackTrace();
    }
    try {
      if ( iniOs != null ) {
        iniOs.flush();
        iniOs.close();
      }
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }

  public void writeAspen( OutputStream mdlOs, OutputStream iniOs ) {
    // Load keywords into name translator so we can translate names to avoid
    // them.
    final String aspenKeyWords[] =
        { "activity", "parameter", "start_time", "duration", "end_time",
          "permissions", "priority", "model", "fulfills", "resource",
          "state_variable", "depletable", "nondepletable", "default",
          "satisfies", "decomposition", "decompositions", "reservations",
          "dependencies", "alternatives", "expansion", "expansions",
          "uncertainty", "uncertain", "of", "start", "end", "starts_before",
          "starts_after", "ends_before", "ends_after", "starts_at", "ends_at",
          "where", "with", "or", "string", "real", "int", "bool", "constraint",
          "constraints", "ordered", "ordered_backtoback", "usage" };
    PrintWriter mdl = new PrintWriter( mdlOs );
    PrintWriter ini = new PrintWriter( iniOs );
    NameTranslator nt = new NameTranslator();
    for ( String n : aspenKeyWords ) {
      nt.translate( n, "AE", "ASPEN" );
    }

    // write model.mdl
    String modelName = nt.translate( getName(), "AE", "ASPEN" );
    mdl.println( "model " + modelName + " {" );
    String unitStr = Timepoint.getUnits().toString();
    unitStr = unitStr.substring( 0, unitStr.length() - 1 ); // removing last
                                                            // char, 's'
    mdl.println( "  time_scale = " + unitStr + ";" );
    mdl.println( "  time_zone = gmt;" );
    mdl.println( "  time_format = tee;" );
    mdl.println( "  horizon_start = "
                 + TimeUtils.toAspenTimeString( Timepoint.getEpoch() ) + ";" );
    mdl.println( "  horizon_duration = " + Timepoint.getHorizonDuration()
                 + ";" );
    mdl.println( "};\n" );

    // write events
    Set< Event > events = new LinkedHashSet< Event >();
    events.add( this );
    events = Utils.addAll( events, getEvents( true, null ) );
    // final int typeDepth = 2; // the nested class depth, for which types will
    // be created.
    for ( Event e : events ) {
      // Create activity type based on enclosing classes.
      List< Class< ? > > enclosingClasses = new ArrayList< Class< ? > >();
      Class< ? > enclosingClass = e.getClass().getEnclosingClass();
      // System.out.println( e.getClass().getName() );
      while ( enclosingClass != null ) {
        // System.out.println("is enclosed by " + enclosingClass.getSimpleName()
        // );
        enclosingClasses.add( enclosingClass );
        enclosingClass = enclosingClass.getEnclosingClass();
      }
      String typeName = null;
      if ( enclosingClasses.size() > 0 ) {
        typeName =
            enclosingClasses.get( enclosingClasses.size() - 1 ).getSimpleName();
      } else {
        typeName = e.getClass().getSimpleName();
      }
      // Get the corresponding ASPEN activity schema name.
      Long id = nt.getIdForNameAndDomain( typeName, "AE" );
      boolean newSchema = ( id == null );
      typeName = nt.translate( typeName, "AE", "ASPEN" );
      // Create a unique ASPEN instance name.
      String instanceName = e.getName();
      if ( Utils.isNullOrEmpty( instanceName ) ) {
        instanceName = typeName;
      }
      instanceName = nt.makeNameUnique( instanceName, "AE" );
      instanceName = nt.translate( instanceName, "AE", "ASPEN" );
      if ( newSchema ) {
        mdl.println( "activity " + typeName + " {}" );
      }
      ini.println( typeName + " " + instanceName + " {" );
      ini.println( "  start_time = "
                   + Math.max( 0,
                               Math.min( 1073741823,
                                         e.getStartTime().getValueOrMin() ) )
                   + ";" );
      ini.println( "  duration = "
                   + Math.max( 1,
                               Math.min( 1073741823,
                                         e.getDuration().getValueOrMin() ) )
                   + ";\n}" );
    }

    // write timelines
    Set< TimeVarying< ?, ? > > states = getTimeVaryingObjects( true, null );
    for ( TimeVarying< ?, ? > state : states ) {
      String tlName = "";
      if ( state instanceof TimeVaryingMap ) {
        tlName = ( (TimeVaryingMap< ? >)state ).getName();
      }
      if ( state instanceof AspenTimelineWritable ) {
        tlName = nt.makeNameUnique( tlName, "AE" );
        tlName = nt.translate( tlName, "AE", "ASPEN" );
        AspenTimelineWritable tl = (AspenTimelineWritable)state;
        mdl.print( tl.toAspenMdl( tlName ) );
        mdl.flush();
        ini.print( tl.toAspenIni( tlName ) );
        ini.flush();
      }
    }
  }

  // getters and setters

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#getStartTime()
   */
  @Override
  public Timepoint getStartTime() {
    return startTime;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#setStartTime(event.Timepoint)
   */
  @Override
  public void setStartTime( Timepoint startTime ) {
    this.startTime = startTime;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#getDuration()
   */
  @Override
  public Duration getDuration() {
    return duration;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#setDuration(event.Duration)
   */
  @Override
  public void setDuration( Duration duration ) {
    this.duration = duration;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#getEndTime()
   */
  @Override
  public Timepoint getEndTime() {
    return endTime;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#setEndTime(event.Timepoint)
   */
  @Override
  public void setEndTime( Timepoint endTime ) {
    this.endTime = endTime;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#getEffects()
   */
  @Override
  public List< Pair< Parameter< ? >, Set< Effect > > > getEffects() {
    return effects;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#setEffects(java.util.SortedMap)
   */
  @Override
  public void
         setEffects( List< Pair< Parameter< ? >, Set< Effect > > > effects ) {
    this.effects = effects;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#getElaborations()
   */
  @Override
  public Map< ElaborationRule, Vector< Event > > getElaborations() {
    return elaborations;
  }

  /*
   * (non-Javadoc)
   * 
   * @see event.Event#setElaborations(java.util.Map)
   */
  @Override
  public void
         setElaborations( Map< ElaborationRule, Vector< Event > > elaborations ) {
    this.elaborations = elaborations;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * gov.nasa.jpl.ae.event.ParameterListenerImpl#compareTo(gov.nasa.jpl.ae.event
   * .ParameterListenerImpl)
   */
  @Override
  public int compareTo( ParameterListenerImpl o ) {
    return compareTo( o, true );
  }

  @Override
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
    int compare = super.compareTo( o, checkId, onlyCheckId, loosely );
    if ( compare != 0 ) return compare;
//    compare = Utils.compareTo(getClass().getName(), o.getClass().getName());
//    if (compare != 0) return compare;
//    compare = Utils.compareTo(getName(), o.getName());
//    if (compare != 0) return compare;
    if ( o instanceof DurativeEvent ) {
      DurativeEvent oe = (DurativeEvent)o;
      compare = startTime.compareTo( oe.getStartTime() );
      if ( compare != 0 ) return compare;
      compare = endTime.compareTo( oe.getEndTime() );
      if ( compare != 0 ) return compare;
      compare =
          CompareUtils.compareCollections( effects, oe.effects, true, checkId );
      if ( compare != 0 ) return compare;
      compare = CompareUtils.compareCollections( elaborations, oe.elaborations,
                                                 true, checkId );
      if ( compare != 0 ) return compare;
    }
    compare = CompareUtils.compareCollections( parameters, o.getParameters(),
                                               true, checkId );
    if ( compare != 0 ) return compare;
    if ( !loosely ) {
      compare = CompareUtils.compare(this, o, false, checkId);
      if (compare != 0) return compare;
    }
    return compare;
  }

  protected void effectsHandleValueChangeEvent(Parameter<?> parameter, Set<HasParameters> seen) {
      for ( Pair< Parameter< ? >, Set< Effect > > effectPair : effects ) {
          if ( effectPair == null ) continue;
          Parameter< ? > par = effectPair.first;
          TimeVaryingMap< ? > tvm = null;
          Object pv = par.getValue();
          if ( pv instanceof TimeVaryingMap ) {
              tvm = (TimeVaryingMap< ? >)pv;
          }
          if ( par != null && parameter.equals( par ) ) {
              // nothing to do -- it's already updated
          }

          Set< Effect > effectSet = effectPair.second;
          for ( Effect effect : effectSet ) {
              if ( effect instanceof EffectFunction ) {
                  EffectFunction efff = (EffectFunction)effect;
                  TimeVaryingMap< ? > tv =
                          Functions.tryToGetTimelineQuick( efff.getObject() );
                  if ( tv != null ) tvm = tv;
                  if ( tvm == null ) continue;
                  Pair< Parameter< Long >, Object > timeVal =
                          tvm.getTimeAndValueOfEffect( efff );

                  // Object v = tvm.getValueOfEffect( efff );
                  int pos = tvm.getIndexOfValueArgument( efff );
                  Object arg = pos >= 0 && pos < efff.getArguments().size()
                          ? efff.getArgument( pos )
                          : null;

                  if ( HasParameters.Helper.hasParameter( arg, parameter, true, null,
                          true ) ) {
                      // unapply and reapply effect
                      Object owner = tvm.getOwner();
                      if ( owner instanceof Parameter ) {
                          Parameter< ? > tvParam = (Parameter< ? >)owner;
                          if ( efff.isApplied( tvParam ) ) {
                              tvm.unapply( efff );
                              if ( tvm.canBeApplied( efff ) ) {
                                  efff.applyTo( tvm, true );
                              }
                          }
                      }
                  }
              }
          }
      }

  }

  private static boolean newChanges = false;

  /*
   * (non-Javadoc)
   * 
   * @see
   * gov.nasa.jpl.ae.event.ParameterListenerImpl#handleValueChangeEvent(gov.nasa
   * .jpl.ae.event.Parameter)
   *
   * An event owns all of its parameters because it is required to contain any
   * dependency that sets one of its parameters and has some connection through
   * its other members to any reference or constraint on it. Thus, an event must
   * know about the parent event from which it is elaborated if the parent
   * references the parameter. handleValueChangeEvent( parameter, newValue )
   * updates dependencies, effects, and elaborations for the changed parameter.
   */
  @Override
  public void handleValueChangeEvent( Parameter< ? > parameter,
                                      Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if ( p.first ) return;
    seen = p.second;

    // REVIEW -- Should we be passing in a set of parameters? Find review/todo
    // note on staleness table.

    // Update effects before other things since they're probably going to be
    // handled more than once, and we want to handle it carefully at first.
    if ( newChanges ) {
        effectsHandleValueChangeEvent(parameter, seen);
    }

    // The super class updates the dependencies.
    seen.remove( this );
    super.handleValueChangeEvent( parameter, seen );

    // Update other events
    for ( Event e : getEvents( false, null ) ) {
      if ( e instanceof ParameterListener ) {
        ( (ParameterListener)e ).handleValueChangeEvent( parameter, seen );
      }
    }
  }

  /**
   * @return whether or not the constraints of the elaborations have been
   *         satisfied.
   */
  public boolean satisfyElaborations() {
    boolean deep = true;
    return satisfyElaborations( deep, null );
  }

  /**
   * @param deep
   * @param seenSatisfiable
   * @return whether or not the constraints of the elaborations have been
   *         satisfied.
   */
  public boolean satisfyElaborations( boolean deep,
                                      Set< Satisfiable > seenSatisfiable ) {
    if ( elaborations == null ) return false;
    Pair< Boolean, Set< Satisfiable > > pair =
        Utils.seen( this, deep, seenSatisfiable );
    if ( pair.first ) return true;
    seenSatisfiable = pair.second;

    // REVIEW -- code below is replicated in elaborate() and
    // elaborationsConstraint.
    // Don't elaborate outside the horizon. Need startTime grounded to know.
    Set< Groundable > seenGroundable = null;
    // Set< Satisfiable > seenSatisfiable = null;
    if ( !startTime.isGrounded( deep,
                                seenGroundable ) ) startTime.ground( deep,
                                                                     seenGroundable );
    if ( !startTime.isGrounded( deep, seenGroundable ) ) return false;
    if ( startTime.getValue( true ) >= Timepoint.getHorizonDuration() ) {
      if ( Debug.isOn() ) Debug.outln( "satisfyElaborations(): No need to elaborate event outside the horizon: "
                                       + getName() );
      return true;
    }

    boolean satisfied = true;
    elaborate( true );
    // REVIEW -- This vector doesn't necessarily avoid concurrent modification
    // errors. Consider changing elaborations from a map into a list of pairs,
    // like parameters and effects.
    Vector< Vector< Event > > elaboratedEvents =
        new Vector< Vector< Event > >();
    for ( Vector< Event > v : elaborations.values() ) {
      elaboratedEvents.add( new Vector< Event >( v ) );
    }
    for ( Vector< Event > v : elaboratedEvents ) {
      for ( Event e : v ) {
        if ( e instanceof Satisfiable ) {
          if ( !( (Satisfiable)e ).isSatisfied( deep, null ) ) {
            if ( e instanceof ParameterListenerImpl ) {
              ParameterListenerImpl pl = (ParameterListenerImpl)e;
              pl.amTopEventToSimulate = false;
              pl.maxLoopsWithNoProgress = 2;
              pl.maxPassesAtConstraints = 1;
              pl.timeoutSeconds = Math.max( 0.5, timeoutSeconds / 2.0 );
              pl.usingLoopLimit = true;
              pl.usingTimeLimit = true;
            }
            if ( !( (Satisfiable)e ).satisfy( deep, seenSatisfiable ) ) {
              satisfied = false;
            }
          }
        }
      }
    }
    return satisfied;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * gov.nasa.jpl.ae.event.ParameterListenerImpl#refresh(gov.nasa.jpl.ae.event.
   * Parameter)
   */
  @Override
  public boolean refresh( Parameter<?> parameter, Set<ParameterListener> seen ) {
      Pair<Boolean, Set<ParameterListener>> pr = Utils.seen( this, true, seen );
      if ( pr != null && pr.first ) return false;
      seen = pr.second;

      seen.remove(this);
      boolean didRefresh = super.refresh( parameter, seen );
      seen.add(this);

    if ( !didRefresh ) {
      for ( Event e : getEvents( false, null ) ) {
        if ( e instanceof ParameterListener ) {
          if ( ( (ParameterListener)e ).refresh( parameter, seen ) ) return true;
        }
      }
    }
    return didRefresh;
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * gov.nasa.jpl.ae.event.ParameterListenerImpl#setStaleAnyReferencesTo(gov.
   * nasa.jpl.ae.event.Parameter)
   */
  @Override
  public void setStaleAnyReferencesTo( Parameter< ? > changedParameter,
                                       Set< HasParameters > seen ) {
    Pair< Boolean, Set< HasParameters > > p = Utils.seen( this, true, seen );
    if ( p.first ) return;
    seen = p.second;
    seen.remove( this ); // removing this so that call to super succeeds

    // Alert affected dependencies.
    super.setStaleAnyReferencesTo( changedParameter, seen );
    seen.add(this);

    Set<Event> events = getEvents(false, null);
    for ( Event e : events ) {
      if ( e instanceof ParameterListener ) {
        ( (ParameterListener)e ).setStaleAnyReferencesTo( changedParameter,
                                                          seen );
      }
    }

    for ( ElaborationRule e : getElaborations().keySet() ) {
      e.setStaleAnyReferenceTo( changedParameter, seen );
    }
  }

  public static void doEditCommandFile( DurativeEvent d ) throws IOException {
    Runtime.getRuntime().exec( "xterm -e 'vi commandFile'" );
    d.addObservation( Timepoint.now(), d.endTime, Timepoint.now().getValue() );
  }
  // {
  //
  // DurativeEvent commandEditing = this;
  // addObservation(Timepoint.now(), commandEditing .endTime,
  // Timepoint.now().getValue());
  // Command.
  // commandSequence.add(Timepoint.fromTimestamp("2012-08-05T11:00:00-07:00"),
  // new Command(Runtime.getRuntime(), Runtime.class, "exec",
  // new Object[] { "xterm -e 'vi commandFile'" }));
  //
  // }
  //// Timepoint startTime =
  // Timepoint.fromTimestamp("2012-08-05T11:00:00-07:00");
  //// commandSequence.add( startTime,
  //// new Command( Runtime.getRuntime(),
  //// Runtime.class,
  //// "exec",
  //// new Object[] { "xterm -e 'vi commandFile'" },
  //// (Call)null ) );
  ////
  //// }

  public < V > Constraint addObservation( Parameter< Long > timeSampled,
                                          Parameter< V > systemVariable,
                                          V value ) {
    // String exprString =
    // systemVariable.getOwner().getName() + "." + systemVariable.getName()
    // + ".getValue( timeSampled ) == value";
    // String xmlFileName = "";
    // String pkgName = "";
    // EventXmlToJava xmlToJava = new EventXmlToJava( xmlFileName, pkgName );
    // Expression<V> eqExpr = xmlToJava.javaToAeExpr( exprString, null, true );
    FunctionCall evalFunc =
        new FunctionCall( null, Expression.class, "evaluate",
                          new Object[] { systemVariable, TimeVarying.class,
                                         true, true },
                          (Class< ? >)null );
    Expression< V > getValExpr =
        new Expression< V >( new FunctionCall( null, TimeVarying.class,
                                               "getValue",
                                               new Object[] { timeSampled },
                                               evalFunc, (Class< ? >)null ) );
    Functions.Equals< V > eqExpr =
        new Functions.Equals< V >( value, getValExpr );
    ConstraintExpression c = new ConstraintExpression( eqExpr );
    constraintExpressions.add( c );
    return c;
  }
  // public <V> Constraint addObservation( Parameter< Long> timeSampled,
  // Parameter<V> systemVariable,
  // V value ) {
  // return null;
  // }

  // TODO -- This is not finished. Need to get deep dependents.
  @Override
  public Set< Parameter< ? > >
         getDependentParameters( boolean deep, Set< HasParameters > seen ) {
    Set< Parameter< ? > > set = new LinkedHashSet< Parameter< ? > >();
    ArrayList< Dependency< ? > > s =
        new ArrayList< Dependency< ? > >( dependencies );
    if ( startTimeDependency != null ) s.remove( startTimeDependency );
    if ( endTimeDependency != null ) s.remove( endTimeDependency );
    if ( durationDependency != null ) s.remove( durationDependency );
    for ( Dependency< ? > d : s ) {
      set.add( d.parameter );
    }
    return set;
  }

  @Override
  public List< Variable< ? > >
         getVariablesOnWhichDepends( Variable< ? > variable ) {
    // TODO! Need to check effects and elaborations
    return super.getVariablesOnWhichDepends( variable );
  }

  protected TimeVaryingMap< Boolean > validStartDomainIntervals() {
      TimeVaryingMap<Boolean> overallValidTimes =
              new TimeVaryingMap<>( "", Boolean.TRUE, Boolean.class );
      // Trim the domain by the startTime's domain.
      if ( startTime != null && startTime.getDomain() != null && startTime.getDomain() instanceof RangeDomain ) {
          RangeDomain rd = (RangeDomain)startTime.getDomain();
          Object lb = rd.getLowerBound();
          Object ub = rd.getUpperBound();
          Parameter<Long> k = null;
          if ( lb instanceof Number ) {
              long llb = ( (Number)lb ).longValue();
              if ( !rd.isLowerBoundIncluded() ) {
                  llb += 1;
              }
              if ( llb > 0 ) {
                  // set 0 time to false
                  k = overallValidTimes.firstKey();
                  if ( k != null && k.getValueNoPropagate() == 0 ) {
                      overallValidTimes.setValue( k, Boolean.FALSE );
                  } else {
                      k = new SimpleTimepoint( 0L );
                      overallValidTimes.setValue( k, Boolean.FALSE );
                  }
                  // Set value at lower bound to true
                  k = new SimpleTimepoint( llb );
                  overallValidTimes.setValue( k, Boolean.TRUE );
              }
          }
          if ( ub instanceof Number ) {
              long lub = ( (Number)ub ).longValue();
              if ( rd.isUpperBoundIncluded() ) {
                  lub += 1;
              }
              // Set value at upper bound to FALSE
              k = new SimpleTimepoint( lub );
              overallValidTimes.setValue( k, Boolean.FALSE );
          }
      }
      return overallValidTimes;
  }

  public TimeVaryingMap< Boolean > validStartIntervals() {
    TimeVaryingMap<Boolean> overallValidTimes = validStartDomainIntervals();
    // Now get valid times based on effects
    boolean hasRelevantEffects = false;
    List< Pair< Parameter< ? >, Set< Effect > > > fects = getEffects();
    for ( Pair< Parameter< ? >, Set< Effect > > p : fects ) {
      if ( p == null || p.first == null || p.first.getValue() == null
           || p.second == null ) continue;
      Object tvmo = p.first.getValue();
      if ( tvmo instanceof TimeVaryingMap ) {
        TimeVaryingMap< ? > tvm = (TimeVaryingMap< ? >)tvmo;
        for ( Effect effect : p.second ) {
          Pair< Parameter< Long >, Object > tv =
              tvm.getTimeAndValueOfEffect( effect );
          if ( tv == null || tv.first == null ) continue; // ||
                                                          // !tv.first.equals(
                                                          // startTime ) )
                                                          // continue;
          TimeVaryingMap< Boolean > validTimes = tvm.validTime( effect, false );
          if ( validTimes != null ) {
            if ( overallValidTimes == null ) {
              if ( validTimes.totalDurationWithValue( true ) > 0L ) {
                overallValidTimes = validTimes;
              } else {
                Debug.error( true, false,
                             "No valid start times for " + getName() );
              }
            } else {
              TimeVaryingMap< Boolean > anded =
                  TimeVaryingMap.and( overallValidTimes, validTimes );
              if ( anded.totalDurationWithValue( true ) > 0L ) {
                overallValidTimes = anded;
              } else {
                Debug.error( true, false,
                             "No valid start times for " + getName() );
                break;
              }
            }
          }
        }
      }
    }
    return overallValidTimes;
  }

  public Long pickStartInValidInterval() {
    TimeVaryingMap< Boolean > overallValidTimes = validStartIntervals();
    if ( overallValidTimes == null || overallValidTimes.isEmpty()
         || overallValidTimes.totalDurationWithValue( true ) <= 0 ) {
      Debug.error( true, false,
                   "Returning no valid start times for " + getName() );
      return null;
    }
    Long pickedTime = overallValidTimes.pickRandomTimeWithValue( true );
    System.out.println( "Picked startTime = " + pickedTime
                        + " in valid intervals for " + getName() );
    return pickedTime;
  }

  @Override
  public < T > boolean pickParameterValue( Variable< T > variable ) {
    if ( variable == null ) return false;
    if ( variable.equals( startTime ) ) {
      Long newStartTime = pickStartInValidInterval();
      if ( newStartTime != null && newStartTime != startTime.getValue() ) {
        variable.setValue( (T)newStartTime );
        return true;
      }
    }
    return false;
    // return super.timeWhenFractionOfTotalDurationWithValue( variable, );
  }

    @Override
    public Boolean execute( Long time, String name, String shortClassName,
                            String longClassName, String value ) {
        return false;
    }

    @Override public Thread getThread() {
        return null;
    }
}
