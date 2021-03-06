package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.solver.Domain;
import gov.nasa.jpl.ae.solver.IntegerDomain;
import gov.nasa.jpl.ae.solver.LongDomain;
import gov.nasa.jpl.ae.solver.TimeVariable;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.TimeUtils;
import gov.nasa.jpl.mbee.util.TimeUtils.Units;
import gov.nasa.jpl.mbee.util.Utils;

import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;


/**
 * 
 */

/**
 *
 */
public class Timepoint extends LongParameter implements TimeVariable {

  // epoch is a timestamp corresponding to TimePoint = 0 as the date/time that
  // the simulation starts. It is an offset of the time since Jan 1, 1970.
  // For example, if epoch == 1341614935000 milliseconds, then a TimePoint or
  // int value of 0 corresponds to Fri, Jul 06, 2012 3:48:55 PM.
  // This number comes from using the 'date' unix command:
  //    $ date; date '+%s'
  //    Fri, Jul 06, 2012  3:48:55 PM
  //    1341614935
  // The units of time and the epoch are specified by Units units below.
  protected static Date epoch = new Date(0);
  protected static Date horizon = null;
  // The unit of time for the epoch and all other integer values of time.  
  protected static TimeUtils.Units units = TimeUtils.Units.seconds;

  protected static int counter = 0;
  protected static long horizonDuration = 24L * 3600;// * 200 * (4 * 365 + 1) / 4;  // 200 years

  private final static Timepoint epochTimepoint =
          new Timepoint( "", 0L, ParameterListener.instance );
  private static Timepoint horizonTimepoint = null;
  
  protected static final LongDomain defaultDomain = LongDomain.positiveDomain;
                                               //= TimeDomain.horizonDomain;
  
  public long timeSinceMidnight() {
    if ( !isGrounded( false, null ) ) return 0;
    double f = conversionFactor( TimeUtils.Units.milliseconds );
    long v =  (long)(getValue(false) * f + getEpoch().getTime());
    Date d = new Date( v );
    return timeSinceMidnight( d );
//    return (long)( ( ( (long)( v * f ) ) % ( 24 * 3600 * 1000 ) ) / f );
  }
  

  /**
	 * @param name
	 * @param o 
	 */
	public Timepoint(String name, ParameterListener o) {
		super(name, defaultDomain, o);
	}
	
  /**
   * @param name
   * @param domain
   * @param o
   */
  public Timepoint(String name, Domain< Long> domain, ParameterListener o) {
    super(name, domain, o);
  }

	/**
	 * @param name
	 * @param value
	 * @param o
	 */
	public Timepoint(String name, Long value, ParameterListener o) {
		super(name, defaultDomain, value, o);
	}

  /**
   * @param name
   * @param domain
   * @param value
   * @param o
   */
  public Timepoint(String name, Domain< Long> domain, Long value, ParameterListener o) {
    super(name, domain, value, o);
  }

	/**
   * @param value
   */
  public Timepoint( long value ) {
    this( "", value, null );
  }

	/**
	 * @param timepoint
	 */
	public Timepoint(Timepoint timepoint) {
		super(timepoint);
	}
	
  public Timepoint( Date date ) {
    this( Timepoint.fromDateToInteger( date ) );
  }


  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo( Parameter< ? > o ) {
    return compareTo( o, false, false );
  }
  public int compareTo( Parameter< ? > o, boolean propagate, boolean checkId ) {
    if ( this == o ) return 0;
    if ( o == null ) return 1; // REVIEW -- okay for o to be null? complain?
    int compare = 0;
    if ( o instanceof Timepoint ) {
      Long v1 = null;
      Long v2 = null;
      try {
        v1 = Expression.evaluate( this, Long.class, propagate );
      } catch ( ClassCastException e ) {
      } catch ( IllegalAccessException e ) {
      } catch ( InvocationTargetException e ) {
      } catch ( InstantiationException e ) {
      }
      try {
        v2 = Expression.evaluate( o, Long.class, propagate );
      } catch ( ClassCastException e ) {
      } catch ( IllegalAccessException e ) {
      } catch ( InvocationTargetException e ) {
      } catch ( InstantiationException e ) {
      }
      compare = CompareUtils.compare( v1, v2, true );
//      return compare;
      if ( compare != 0 ) return compare;
    }
    return super.compareTo( o, checkId );
  }
  
  public void setValue( Date date ) {
    if ( date != null ) {
      this.value = fromMillisToInteger( date.getTime() ); 
    }
  }
	
  public static synchronized Timepoint fromMillis( long millis ) {
    return new Timepoint( "timepoint" + ( counter++ ),
                          fromMillisToInteger( millis ), null );
  }

  public static synchronized Timepoint fromDate( Date date) {
    return new Timepoint( "timepoint" + ( counter++ ),
                          fromDateToInteger( date ), null );
  }

	public static synchronized Timepoint fromTimestamp( String timestamp ) {
    return new Timepoint( "timepoint" + ( counter++ ),
                          fromTimestampToInteger( timestamp ), null );
    // REVIEW -- other formats?
	}

  public static double conversionFactor( Units toUnits) {
    return Units.conversionFactor( Timepoint.units, toUnits );
  }

  public long convertTo( TimeUtils.Units toUnit ) {
	  Double f = new Double( conversionFactor( toUnit ) );
	  f *= getValue(false);
	  return (long)( f.longValue() );
	}
	
  public static long microseconds( double microseconds ) {
    return ( (Double)( microseconds
                       / conversionFactor( Units.microseconds ) ) ).longValue();
  }
  public static double asMicroseconds( long duration ) {
    return duration * conversionFactor( Units.microseconds );
  }
  public static long nanoseconds( double nanoseconds ) {
    return ( (Double)( nanoseconds
                       / conversionFactor( Units.nanoseconds ) ) ).longValue();
  }
  public static double asNanoseconds( long duration ) {
    return duration * conversionFactor( Units.nanoseconds );
  }
  public static long seconds( double seconds ) {
    return ( (Double)( seconds
                       / conversionFactor( Units.seconds ) ) ).longValue();
  }
  public static double asSeconds( long duration ) {
    return duration * conversionFactor( Units.seconds );
  }
  public static long milliseconds( double milliseconds ) {
    return ( (Double)( milliseconds
                       / conversionFactor( Units.milliseconds ) ) ).longValue();
  }
  public static double asMilliseconds( long duration ) {
    return duration * conversionFactor( Units.milliseconds );
  }
  public static long minutes( double minutes ) {
    return ( (Double)( minutes
                       / conversionFactor( Units.minutes ) ) ).longValue();
  }
  public static double asMinutes( long duration ) {
    return duration * conversionFactor( Units.minutes);
  }
  public static long hours( double hours ) {
    return ( (Double)( hours
                       / conversionFactor( Units.hours ) ) ).longValue();
  }
  public static double asHours( long duration ) {
    return duration * conversionFactor( Units.hours );
  }
  public static long days( double days ) {
    return ( (Double)( days
                       / conversionFactor( Units.days ) ) ).longValue();
  }
  public static double asDays( long duration ) {
    return duration * conversionFactor( Units.days );
  }
  public static long weeks( double weeks ) {
    return days( weeks * 7.0 );
  }
  public static double asWeeks( long duration ) {
    return asDays( duration ) / 7.0;
  }

  public static long years( double years ) {
    return days( years * 365.25 );
  }
  public static double asYears( long duration ) {
    return asDays( duration ) / 365.25;
  }

  public static long months( double months ) {
    return years( months / 12.0 );
  }
  public static double asMonths( long duration ) {
    return asYears( duration ) * 12.0;
  }


  public static Long fromMillisToInteger( long millis ) {
    long t = (long)( Units.conversionFactor( Units.milliseconds, Timepoint.units )
                   * ( millis - Timepoint.getEpoch().getTime() ) );
    return t;
  }

  public static Long fromDateToInteger( Date date ) {
    return fromMillisToInteger( date.getTime() );
  }

	public Date getDate() {
	  if ( !isGrounded( false, null ) ) return null;
      long epMillis = getEpoch().getTime();
	  Date d = new Date( epMillis + (long)(conversionFactor( TimeUtils.Units.milliseconds )
	                            * getValue(false)) );
	  return d;
	}
	
	public static Calendar gmtCalendar = TimeUtils.gmtCal;
	
  public static Long fromTimestampToInteger( String timestamp ) {
    Long t = null;
    DateFormat df = new SimpleDateFormat( TimeUtils.timestampFormat );
    df.setCalendar( gmtCalendar );
    df.setTimeZone( TimeZone.getTimeZone( "GMT" ) );;
    try {
      Date d = df.parse( timestamp );
      assert ( d != null );
      t = fromDateToInteger(d);
    } catch ( java.text.ParseException e1 ) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    return t;
  }

  // Converts time offset to a date-time String in Timepoint.timestamp format.
  // Assumes t is an offset from Timepoint.epoch in Timepoint.units. 
  public static String toTimestamp( long t ) {
    Calendar cal = gmtCalendar;
    return toTimestamp(t, TimeUtils.timestampFormat, cal);
  }
  public static String toDoyTimestamp( long t ) {
    Calendar cal = gmtCalendar;
    return toTimestamp(t, TimeUtils.dayOfYearTimestampFormat, cal);
  }
  // REVIEW -- TODO -- FIXME -- Does this assume milliseconds since the epoch?!
  public static String toTimestamp( long t, String dateFormat, Calendar cal) {
    double cf = conversionFactor( Units.milliseconds );
    //DateFormat df = new Da
    SimpleDateFormat sdf = new SimpleDateFormat( dateFormat ); 
    sdf.setCalendar( cal );
    sdf.setTimeZone( TimeZone.getTimeZone( "GMT" ) );
    cal.setTimeInMillis( (long)( Timepoint.getEpoch().getTime() + (t * cf)  ) );
    String timeString =
        sdf.format( cal.getTime() );
    return timeString;
  }

  public String toTimestamp( String dateFormat, Calendar cal) {
    Long val = getValue(false);
    if ( val == null ) return null;
    return toTimestamp( val, dateFormat, cal );
  }
  
  public String toTimestamp() {
    Long val = getValue(false);
    if ( val == null ) return null;
    return toTimestamp( val );
//    Double cf = Units.conversionFactor( Units.milliseconds );
//    return millisToTimestamp( (long)( getValue() * cf ) );
////    Calendar cal = Calendar.getInstance();
////    cal.setTimeInMillis( (long)( ( getEpoch().getTime() + getValue() ) * cf ) );
////    String timeString =
////        new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" ).format( cal.getTime() );
////    return timeString;
  }

  public String toDoyTimestamp() {
    Long val = getValue(false);
    if (val == null) return null;
    return toDoyTimestamp(val);
  }

  public static long timeSinceMidnight( Date start ) {
    Calendar c1 = Calendar.getInstance(TimeZone.getTimeZone( "GMT" ));
    Calendar c2 = Calendar.getInstance(TimeZone.getTimeZone( "GMT" ));
    c1.setTime( start );
    c2.setTime( start );
    c2.set( Calendar.HOUR_OF_DAY, 0 );
    c2.set(Calendar.MINUTE, 0);
    c2.set(Calendar.SECOND, 0);
    c2.set(Calendar.MILLISECOND, 0);
    long diffMillis = (long)( c1.getTimeInMillis() - c2.getTimeInMillis() ); 
    double f = conversionFactor( Units.milliseconds );
    return (long)( diffMillis / f );
  }

  @Override
  public String toString() {
    //return toTimestamp();
    return super.toString();
  }

  /**
   * @return the epoch
   */
  public synchronized static Date getEpoch() {
    return epoch;
  }
  public synchronized static Timepoint getEpochTimepoint() {
    return epochTimepoint;
  }

  public synchronized static Date getHorizon() {
    horizon = getHorizonTimepoint().getDate(); 
    return horizon;
  }
  
  public synchronized static Timepoint getHorizonTimepoint() {
    // TODO REVIEW -- consider adding a dependency so that these parameters can
    // change during problem solving.
    if (horizonTimepoint == null ) {
      horizonTimepoint =
              new Timepoint( "horizon",
                             new LongDomain(getHorizonDuration(), getHorizonDuration()),
                             ParameterListener.instance );
    }
    if ( horizonTimepoint.getValueNoPropagate() == null ||
         !Utils.valuesEqual(horizonTimepoint.getValueNoPropagate(),
                            getHorizonDuration() ) ) {
      horizonTimepoint.setValue( getHorizonDuration() );
    }
    return horizonTimepoint;
  }

  /**
   * @param epoch the epoch to set
   */
  public synchronized static boolean setEpoch( Date epoch ) {
    Timepoint.epoch = epoch;
    System.out.println("Epoch set to " + epoch );
    return true;
  }

  public static boolean setEpoch( String epochString ) {
    String ds = epochString.replaceAll("\"", "");
    Date d =  TimeUtils.dateFromTimestamp( ds, TimeZone.getTimeZone( "GMT" ) );
    return setEpoch( d );
  }

  /**
   * @return the units
   */
  public static TimeUtils.Units getUnits() {
    return units;
  }

  /**
   * @param units the units to set
   */
  public static boolean setUnits( TimeUtils.Units units ) {
    System.out.println("Units set to " + units );
    Timepoint.units = units;
    return true;
  }

  /**
   * @param timeUnits
   */
  public static boolean setUnits( String timeUnits ) {
    setUnits( TimeUtils.Units.fromString( timeUnits ) );
    return true;
  }

  /**
   * @param horizon the date marking the horizon end
   */
  public synchronized static boolean setHorizon( Date horizon ) {
    if ( horizon == null ) return false;
    Timepoint.horizon = horizon;
    System.out.println("Horizon set to " + horizon );
    //Timepoint.horizonTimepoint = horizon;
    if ( getEpoch() != null ) {
      long millis = horizon.getTime() - getEpoch().getTime();
      horizonDuration = milliseconds( millis );
      System.out.println("Horizon duration = " + horizonDuration );
      getHorizonTimepoint().getDomain().setValue(horizonDuration);
      getHorizonTimepoint().setValue(horizonDuration);
    }
    return true;
  }

  /**
   * @param timestamp the date and time marking the horizon end
   */
  public static boolean setHorizon( String timestamp ) {
    String ds = timestamp.replaceAll("\"", "");
    Date d = TimeUtils.dateFromTimestamp(ds, TimeZone.getTimeZone("GMT"));
    return setHorizon( d );
  }

  /**
   * @param duration the horizon duration to set
   */
  public static boolean setHorizonDuration( long duration ) {
    horizonDuration = duration;
    System.out.println("Horizon duration set to " + horizonDuration + " " + units );
    TimeDomain.horizonDomain.setUpperBound( horizonDuration );
    if ( horizonTimepoint != null ) {
      horizonTimepoint.getDomain().setValue(duration);
      horizonTimepoint.setValue(duration);
    }
    if ( getHorizonTimepoint() != null ) {
      horizon = getHorizonTimepoint().getDate();
    } else {
      horizon = null;
    }

    return true;
  }

  /**
   * @return the horizon duration
   */
  public static Long getHorizonDuration() {
    return horizonDuration;
  }


  public static Timepoint now() {
    return fromDate( TimeUtils.gmtCal.getTime() );
  }

  public static Long julianToInteger( Double julianDate ) {
    long millis = TimeUtils.julianToMillis( julianDate );
    Long i = fromMillisToInteger( millis );
    return i;
  }

  public static void main( String[] args ) {
    //Double jdDouble = 
  }
  
}
