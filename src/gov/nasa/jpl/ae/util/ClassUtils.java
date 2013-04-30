/**
 * 
 */
package gov.nasa.jpl.ae.util;

import gov.nasa.jpl.ae.event.Expression;
import japa.parser.ast.body.Parameter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import junit.framework.Assert;

/**
 * @author bclement
 *
 */
public class ClassUtils {

  /**
     * Compare argument types to determine how well a function call matches.
     */
    public static class ArgTypeCompare< T > {
  
      //public Class<?>[] candidateArgTypes = null;
      public Class<?>[] referenceArgTypes = null;
  //    public boolean isVarArgs = false;
  
      public int numMatching = 0;
      public int numNull = 0;
      public int numDeps = 0;
      public boolean okNumArgs = false;
  
      public T best = null;
      public boolean gotOkNumArgs = false;
      public int mostMatchingArgs = 0;
      public int mostDeps = 0;
      public boolean allArgsMatched = false;
      public boolean allNonNullArgsMatched = false;
      public double bestScore = Double.MAX_VALUE;
  
      /**
       * @param argTypes1
       * @param argTypes2
       * @param isVarArgs
       */
      public ArgTypeCompare( Class< ? >[] referenceArgTypes ) {
        super();
        this.referenceArgTypes = referenceArgTypes;
      }
  
      public void compare( T o, Class<?>[] candidateArgTypes,
                           boolean isVarArgs ) {
        numMatching = 0;
        numNull = 0;
        numDeps = 0;
        boolean debugWasOn = Debug.isOn();
        if ( debugWasOn ) Debug.turnOff();
  //      double score = numArgsCost + argMismatchCost * argTypes.length;
        int candidateArgsLength =
            candidateArgTypes == null ? 0 : candidateArgTypes.length;
        int referenceArgsLength =
            referenceArgTypes == null ? 0 : referenceArgTypes.length;
        okNumArgs =
            ( candidateArgsLength == referenceArgsLength )
              || ( isVarArgs
                   && ( candidateArgsLength < referenceArgsLength 
                        || candidateArgsLength == 1 ) );
        if ( Debug.isOn() ) Debug.outln( "okNumArgs = " + okNumArgs );
  //      if ( okNumArgs ) score -= numArgsCost;
        for ( int i = 0; i < Math.min( candidateArgsLength,
                                       referenceArgsLength ); ++i ) {
          if ( referenceArgTypes[ i ] == null ) {
            if ( Debug.isOn() ) Debug.outln( "null arg[ " + i + " ]" );
            continue;
          }
          if ( candidateArgTypes[ i ] == null ) {
            if ( Debug.isOn() ) Debug.outln( "null arg for args[ " + i
                + " ].getClass()=" + referenceArgTypes[ i ] );
            ++numNull;
            ++numDeps;
          } else if ( candidateArgTypes[ i ].isAssignableFrom( referenceArgTypes[ i ] ) ) {
              if ( Debug.isOn() ) Debug.outln( "argTypes1[ " + i + " ]="
                           + candidateArgTypes[ i ] + " matches args[ " + i
                           + " ].getClass()=" + referenceArgTypes[ i ] );
            ++numMatching;
          } else if ( candidateArgTypes[ i ].isPrimitive() &&
                      classForPrimitive(candidateArgTypes[ i ]).isAssignableFrom( referenceArgTypes[ i ] ) ) {
            if ( Debug.isOn() ) Debug.outln( "argTypes1[ " + i + " ]="
                         + candidateArgTypes[ i ] + " matches args[ " + i
                         + " ].getClass()=" + referenceArgTypes[ i ] );
            ++numMatching;
          } else if ( Parameter.class.isAssignableFrom( candidateArgTypes[ i ] ) &&
                      Expression.class.isAssignableFrom( referenceArgTypes[ i ] ) ) {
              if ( Debug.isOn() ) Debug.outln( "argTypes1[ " + i + " ]="
                           + candidateArgTypes[ i ]
                           + " could be made dependent on args[ " + i
                           + " ].getClass()=" + referenceArgTypes[ i ] );
            ++numDeps;
          } else {
              if ( Debug.isOn() ) Debug.outln( "argTypes1[ " + i + " ]="
                           + candidateArgTypes[ i ]
                           + " does not match args[ " + i + " ].getClass()="
                           + referenceArgTypes[ i ] );
          }
        }
        if ( ( best == null )
            || ( !gotOkNumArgs && okNumArgs )
            || ( ( gotOkNumArgs == okNumArgs )
                 && ( ( numMatching > mostMatchingArgs )
                      || ( ( numMatching == mostMatchingArgs ) 
                           && ( numDeps > mostDeps ) ) ) ) ) {
         best = o;
         gotOkNumArgs = okNumArgs;
         mostMatchingArgs = numMatching;
         mostDeps = numDeps;
         allArgsMatched = ( numMatching >= candidateArgsLength );
         allNonNullArgsMatched = ( numMatching + numNull >= candidateArgsLength );
           if ( Debug.isOn() ) Debug.outln( "new match " + o + ", mostMatchingArgs="
                        + mostMatchingArgs + ",  allArgsMatched = "
                        + allArgsMatched + " = numMatching(" + numMatching
                        + ") >= candidateArgTypes.length("
                        + candidateArgsLength + "), numDeps=" + numDeps );
        }
        if ( debugWasOn ) Debug.turnOn();
      }
    }

    // TODO -- this would be useful for TimeVaryingMap.valueFromString();
//  public static <V> V valueFromString() {
//    V value = null;
//    
//    return value;
//  }
    
//    // boolean, byte, char, short, int, long, float, and double
//    public static Class< ? > primitiveForClass( Class< ? > nonPrimClass ) {
//      if ( nonPrimClass == Integer.class ) {
//        return int.class;
//      }
//      if ( nonPrimClass == Double.class ) {
//        return double.class;
//      }
//      if ( nonPrimClass == Boolean.class ) {
//        return boolean.class;
//      }
//      if ( nonPrimClass == Long.class ) {
//        return long.class;
//      }
//      if ( nonPrimClass == Float.class ) {
//        return float.class;
//      }
//      if ( nonPrimClass == Character.class ) {
//        return char.class;
//      }
//      if ( nonPrimClass == Short.class ) {
//        return short.class;
//      }
//      if ( nonPrimClass == Byte.class ) {
//        return byte.class;
//      }
//      return null;
//    }
    
  // boolean, byte, char, short, int, long, float, and double
  public static Class< ? > classForPrimitive( Class< ? > primClass ) {
    return getPrimToNonPrim().get( primClass );
  }
  // boolean, byte, char, short, int, long, float, and double
  public static Class< ? > primitiveForClass( Class< ? > nonPrimClass ) {
    return getNonPrimToPrim().get( nonPrimClass );
  }
  
  
  private static Map<String,Class<?>> nonPrimitives = initializeNonPrimitives();
  private static Map<String,Class<?>> primitives = initializePrimitives();
  private static Map<Class<?>,Class<?>> primToNonPrim = initializePrimToNonPrim();
  private static Map<Class<?>,Class<?>> nonPrimToPrim = initializeNonPrimToPrim();
  
  public static Map<String,Class<?>> getPrimitives() {
    if ( primitives == null ) initializePrimitives();
    return primitives;
  }
  public static Map<String,Class<?>> getNonPrimitives() {
    if ( nonPrimitives == null ) initializeNonPrimitives();
    return nonPrimitives;
  }
  public static Map< Class< ? >,Class< ? > > getPrimToNonPrim() {
    if ( primToNonPrim == null ) initializePrimToNonPrim();
    return primToNonPrim;
  }
  public static Map< Class< ? >,Class< ? > > getNonPrimToPrim() {
    if ( nonPrimToPrim == null ) initializeNonPrimToPrim();
    return nonPrimToPrim;
  }
  
  // boolean, byte, char, short, int, long, float, and double
  public static Class< ? > classForPrimitive( String primClass ) {
    return getPrimitives().get( primClass );
  }

  // boolean, byte, char, short, int, long, float, and double
  private static Map< String, Class< ? > > initializeNonPrimitives() {
    nonPrimitives = new TreeMap< String, Class<?> >();
    for ( Class< ? > c : getNonPrimToPrim().keySet() ) {
      nonPrimitives.put( c.getSimpleName(), c );
    }
    return nonPrimitives;
  }
  
  // boolean, byte, char, short, int, long, float, and double
  private static Map< String, Class< ? > > initializePrimitives() {
    primitives = new TreeMap< String, Class<?> >();
    for ( Class< ? > c : getPrimToNonPrim().keySet() ) {
      primitives.put( c.getSimpleName(), c );
    }
//    primitives.put( "boolean", boolean.class );
//    primitives.put( "byte", byte.class );
//    primitives.put( "char", char.class );
//    primitives.put( "short", short.class );
//    primitives.put( "int", int.class );
//    primitives.put( "long", long.class );
//    primitives.put( "float", float.class );
//    primitives.put( "double", double.class );
//    primitives.put( "void", void.class );
    return primitives;
  }
  
  // boolean, byte, char, short, int, long, float, and double
  private static Map< Class< ? >, Class< ? > > initializePrimToNonPrim() {
    primToNonPrim = new HashMap< Class< ? >, Class<?> >();
    primToNonPrim.put( boolean.class, Boolean.class );
    primToNonPrim.put( byte.class, Byte.class );
    primToNonPrim.put( char.class, Character.class );
    primToNonPrim.put( short.class, Short.class );
    primToNonPrim.put( int.class, Integer.class );
    primToNonPrim.put( long.class, Long.class );
    primToNonPrim.put( float.class, Float.class );
    primToNonPrim.put( double.class, Double.class );
    primToNonPrim.put( void.class, Void.class );
    return primToNonPrim;
  }

  // boolean, byte, char, short, int, long, float, and double
  private static Map< Class< ? >, Class< ? > > initializeNonPrimToPrim() {
    nonPrimToPrim = new HashMap< Class< ? >, Class<?> >();
    for ( Entry< Class< ? >, Class< ? > > e : getPrimToNonPrim().entrySet() ) {
      nonPrimToPrim.put( e.getValue(), e.getKey() );
    }
    return nonPrimToPrim;
  }


  public static Class< ? > getNonPrimitiveClass( String type, String preferredPackage ) {
    if ( type == null ) return null;
    Class< ? > cls = null;
    cls = getClassForName( type, preferredPackage, false );
    return getNonPrimitiveClass( cls );
  }

  public static Class< ? > getNonPrimitiveClass( Class< ? > cls ) {
    if ( cls == null ) return null;
    Class< ? > result = ClassUtils.classForPrimitive( cls );
    if ( result == null ) return cls;
    return result;
  }

  public static String addBackParametersToQualifiedName( String className,
                                                  String qualifiedName ) {
    String parameters = parameterPartOfName( className, true );
    String strippedName = ClassUtils.noParameterName( className );
    if ( !Utils.isNullOrEmpty( parameters ) ) {
      String parameters2 = ClassUtils.parameterPartOfName( qualifiedName, true );
      if ( Utils.isNullOrEmpty( parameters2 )
           && qualifiedName.endsWith( strippedName ) ) {
        return qualifiedName + parameters;
      }
    }
    return qualifiedName;
  }
  
  public static String getNonPrimitiveClassName( String type ) {
    String simpleName = simpleName( type );
    Class<?> prim = getPrimitives().get( simpleName );
    String newName = type;
    if ( prim != null ) {
      Class<?> nonPrim = getPrimToNonPrim().get( prim );
      Assert.assertNotNull( nonPrim );
      newName = nonPrim.getSimpleName();
    }    
//    Class< ? > cls = getNonPrimitiveClass( type );
//    String newName = type;
//    if ( cls != null ) {
//      if ( cls.isArray() ) {
//        newName = cls.getSimpleName();
//      } else {
//        newName = cls.getName();
//      }
//      newName = addBackParametersToQualifiedName( type, newName );
//    }
    return newName;
  }

  public static boolean isSubclassOf( Class<?> c1, Class<?> c2 ) {
    try {
      c1.asSubclass( c2 );
    } catch( ClassCastException e ) {
      return false;
    }
    return true;
  }

  public static Class< ? > tryClassForName( String className, 
                                            boolean initialize ) {
    return tryClassForName( className, initialize, null );
  }

  public static Class< ? > tryClassForName( String className, 
                                            boolean initialize,
                                            ClassLoader myLoader ) {
    if ( Debug.isOn() ) Debug.outln( "trying tryClassForName( " + className + " )");
    Class< ? > classForName = null;
    if ( myLoader == null ) myLoader = Utils.loader; 
    if ( myLoader == null ) myLoader = gov.nasa.jpl.ae.event.Expression.class.getClassLoader(); 
    try {
      if ( myLoader == null ) {
        classForName = Class.forName( className );
      } else {
        classForName = Class.forName( className, initialize, myLoader );//, initialize, Utils.class.getClassLoader() );
      }
    } catch ( Exception e ) {
      // ignore
    } catch ( NoClassDefFoundError e) {
      // ignore
    }
    if ( classForName == null ) {
      classForName = getClassOfClass( className, "", initialize );
    }
    if ( Debug.isOn() ) Debug.outln( "tryClassForName( " + className + " ) = " + classForName );
    return classForName;
  }

  public static String replaceAllGenericParameters( String className, char replacement ) {
    if ( Utils.isNullOrEmpty( className ) ) return className;
    StringBuffer newName = new StringBuffer( className );
    int parameterDepth = 0;
    for ( int i=0; i<className.length(); ++i ) {
      char c = className.charAt( i );
      boolean replace = false;
      switch( c ) {
        case '>':
          --parameterDepth;
          replace = true;
          break;
        case '<':
          ++parameterDepth;
          replace = true;
          break;
        default:
          if ( parameterDepth > 0 ) replace = true;
      }
      if ( replace ) {
        newName.setCharAt( i, replacement );
      }
      if ( parameterDepth < 0 ) {
        Debug.error( false,
                     "Error! Generic parameter nesting is invalid for class name:\n  \""
                         + className + "\" at char #" + Integer.toString( i )
                         + "\n" + Utils.spaces( i + 3 ) + "^" );
      }
    }
    return newName.toString();
  }
  
  public static Class<?> getClassOfClass( String longClassName,
                                          String preferredPackageName,
                                          boolean initialize ) {
    Class< ? > classOfClass = null;
    String clsOfClsName = replaceAllGenericParameters( longClassName, '~' );
    int pos = clsOfClsName.lastIndexOf( '.' );
    if ( pos >= 0 ) {
      String clsOfClsName1 = longClassName.substring( 0, pos );
      String clsOfClsName2 = longClassName.substring( pos+1 );
      classOfClass =
          getClassOfClass( clsOfClsName1, clsOfClsName2, preferredPackageName,
                           initialize );
    }
    return classOfClass;
  }

  public static Class<?> getClassOfClass( String className,
                                          String clsOfClsName,
                                          String preferredPackageName,
                                          boolean initialize ) {
    Class< ? > classOfClass = null;
    Class< ? > classForName =
        getClassForName( className, preferredPackageName, initialize );
    if ( classForName == null ) {
      classForName =
          getClassOfClass( className, preferredPackageName, initialize );
    }
    if ( classForName != null ) {
      classOfClass = getClassOfClass( classForName, clsOfClsName );
    }
    return classOfClass;
  }

  public static Class<?> getClassOfClass( Class<?> cls,
                                            String clsOfClsName ) {
      if ( cls != null ) {
        Class< ? >[] classes = cls.getClasses();
        if ( classes != null ) {
          String clsName = cls.getName(); 
          String clsSimpleName = cls.getSimpleName();
          String longName = clsName + "." + clsOfClsName;
          String shorterName = clsSimpleName + "." + clsOfClsName;
          for ( int i = 0; i < classes.length; ++i ) {
            String n = classes[ i ].getName();
            String sn = classes[ i ].getSimpleName();
            if ( n.equals( longName )
                 || n.equals( shorterName )
                 || sn.equals( clsOfClsName )
  //               || n.endsWith( "." + longName )
  //               || n.endsWith( "." + shorterName )
                 || n.endsWith( "." + clsOfClsName ) ) {
              return classes[ i ];
            }
          }
        }
      }
      return null;
    }

  public static HashMap< String, Class<?> > classCache = new HashMap< String, Class<?> >();

  public static Class<?> getClassForName( String className,
                                            String preferredPackage,
                                            boolean initialize ) {
    if ( Utils.isNullOrEmpty( className ) ) return null;
    Class<?> cls = null;
    char firstChar = className.charAt( 0 );
    if ( firstChar >= 'a' && firstChar <= 'z' ) {
      cls = classForPrimitive( className );
      if ( cls != null ) return cls;
    }
    cls = classCache.get( className );
    if ( cls != null ) return cls;
      List< Class<?>> classList = getClassesForName( className, initialize );
      if ( !Utils.isNullOrEmpty( classList ) ) {
        for ( Class< ? > c : classList ) {
          if ( c.getPackage().getName().equals( preferredPackage ) ) {
            if ( Debug.isOn() ) Debug.errln("Found preferred package! " + preferredPackage );
            classCache.put( className, c );
            return c;
          }
        }
      }
      cls = getClassFromClasses( classList );
      if ( cls != null )
        classCache.put( className, cls );
      return cls;
    }
  //  public static Class<?> getClassForName( String className,
  //                                          boolean initialize ) {    
  //    return getClassFromClasses( getClassesForName( className, initialize ) );
  //  }

  public static HashMap< String, List< Class<?> > > classesCache =
      new HashMap< String, List<Class<?>> >();

  public static List< Class< ? > > getClassesForName( String className,
                                                        boolean initialize ) {
  //                                                    ClassLoader loader,
  //                                                    Package[] packages) {
    List< Class< ? > > classList = classesCache.get( className );
    if ( !Utils.isNullOrEmpty( classList ) ) return classList;
    classList = new ArrayList< Class< ? > >();
    if ( Utils.isNullOrEmpty( className ) ) return null;
  //    ClassLoader loader = Utils.class.getClassLoader();
  //    if ( loader != null ) {
  //      for ( String pkgName : packagesToForceLoad ) {
  //        try {
  //          loader.getResources( pkgName);
  //          loader.getResources( pkgName + File.separator + "*" );
  //          loader.loadClass("");
  //        } catch ( IOException e ) {
  //          // ignore
  //        } catch ( ClassNotFoundException e ) {
  //          // ignore
  //        }
  //      }
  //    }
      if ( Debug.isOn() ) Debug.outln( "getClassesForName( " + className + " )" );
      Class< ? > classForName = tryClassForName( className, initialize );//, loader );
      if ( classForName != null ) classList.add( classForName );
      String strippedClassName = ClassUtils.noParameterName( className );
      if ( Debug.isOn() ) Debug.outln( "getClassesForName( " + className + " ): strippedClassName = "
                   + strippedClassName );
      boolean strippedWorthTrying = false;
      if ( !Utils.isNullOrEmpty( strippedClassName ) ) {
        strippedWorthTrying = !strippedClassName.equals( className ); 
        if ( strippedWorthTrying ) {
          classForName = tryClassForName( strippedClassName, initialize );//, loader );
          if ( classForName != null ) classList.add( classForName );
        }
      }
      List<String> FQNs = getFullyQualifiedNames( className );//, packages );
      if ( Debug.isOn() ) Debug.outln( "getClassesForName( " + className + " ): fully qualified names = "
          + FQNs );
      if ( FQNs.isEmpty() && strippedWorthTrying ) {
        FQNs = getFullyQualifiedNames( strippedClassName );//, packages );
      }
      if ( !FQNs.isEmpty() ) {
        for ( String fqn : FQNs ) {
          classForName = tryClassForName( fqn, initialize );//, loader );
          if ( classForName != null ) classList.add( classForName );
        }
      }
      if ( !Utils.isNullOrEmpty( classList ) ) {
        classesCache.put( className, classList );
      }
      return classList;
    }

  public static Collection<String> getPackageStrings(Package[] packages) {
    Set<String> packageStrings = new TreeSet<String>();
    if ( Utils.isNullOrEmpty( packages ) ) {
      packages = Package.getPackages();
    }
    for (Package aPackage : packages ) {
      packageStrings.add(aPackage.getName());
    }
    return packageStrings;
  }
  
  public static boolean isPackageName( String packageName ) {
    Package[] packages = Package.getPackages();
    for (Package aPackage : packages ) {
      String packageString = aPackage.getName();
      if ( packageString.startsWith( packageName ) ) {
        if ( packageString.charAt( packageName.length() ) == '.' ) return true;
      }
    }
//    Collection<String> packageStrings = getPackageStrings( null );
//    if ( packageStrings.contains( packageName ) ) return true;
//    for ( String packageString : packageStrings ) {
//      if ( packageString.startsWith( packageName ) ) {
//        if ( packageString.charAt( packageName.length() ) == '.' ) return true;
//      }
//    }
    return false;
  }

  public static String simpleName( String longName ) {
    int pos = longName.lastIndexOf( "." );
    return longName.substring( pos+1 ); // pos is -1 if no '.'
  }

  public static String getFullyQualifiedName( String classOrInterfaceName,
                                              boolean doTypeParameters ) {
    String typeParameters = "";
    if ( classOrInterfaceName.contains( "<" )
         && classOrInterfaceName.contains( ">" ) ) {
      typeParameters = parameterPartOfName( classOrInterfaceName, false );
      // TODO -- how does this work for multiple parameters? e.g., Map<String,Float>
      String check = replaceAllGenericParameters( typeParameters, '~' );
      Assert.assertFalse( check.contains( "," ) );
      if ( typeParameters != null && typeParameters.contains( "Customer" ) ) {
        Debug.breakpoint();
      }
      typeParameters = "<" + ( doTypeParameters
                               ? getFullyQualifiedName( typeParameters, true )
                               : ClassUtils.getNonPrimitiveClassName( typeParameters ) ) + ">";
      classOrInterfaceName =
          classOrInterfaceName.substring( 0, classOrInterfaceName.indexOf( '<' ) );
    }
    List< String > names = ClassUtils.getFullyQualifiedNames( classOrInterfaceName );
    if ( Utils.isNullOrEmpty( names ) ) {
      names = ClassUtils.getFullyQualifiedNames( ClassUtils.simpleName( classOrInterfaceName ) );
    }
    if ( !Utils.isNullOrEmpty( names ) ) {
      for ( String n : names ) {
        if ( n.endsWith( classOrInterfaceName ) ) {
          classOrInterfaceName = n;
          break;
        }
      }
    }
    return classOrInterfaceName + typeParameters;
  }

  public static List<String> getFullyQualifiedNames(String simpleClassOrInterfaceName ) {
    return getFullyQualifiedNames( simpleClassOrInterfaceName, null );
  }

  public static List<String> getFullyQualifiedNames(String simpleClassOrInterfaceName, Package[] packages) {
    Collection<String> packageStrings = getPackageStrings( packages );
  
    List<String> fqns = new ArrayList<String>();
    if ( Debug.isOn() ) Debug.outln( "getFullyQualifiedNames( " + simpleClassOrInterfaceName
                 + " ): packages = " + packageStrings );
    for (String aPackage : packageStrings) {
        try {
            String fqn = aPackage + "." + simpleClassOrInterfaceName;
            Class.forName(fqn);
            fqns.add(fqn);
        } catch (NoClassDefFoundError e) {
          // Ignore
        } catch (Exception e) {
            // Ignore
        }
    }
    if ( Debug.isOn() ) Debug.outln( "getFullyQualifiedNames( " + simpleClassOrInterfaceName
                 + " ): returning " + fqns );
    return fqns;
  }

  public static Constructor< ? > getConstructorForArgs( String className,
                                                        Object[] args,
                                                        String preferredPackage ) {
    Class< ? > classForName = getClassForName( className, preferredPackage, false );
    if ( classForName == null ) {
      System.err.println( "Couldn't find the class " + className
                          + " to get constructor with args=" + Utils.toString( args ) );
      return null;
    }
    return getConstructorForArgs( classForName, args );
  }

  public static Constructor< ? > getConstructorForArgTypes( String className,
                                                            Class<?>[] argTypes,
                                                            String preferredPackage ) {
    Class< ? > classForName = getClassForName( className, preferredPackage, false );
    if ( classForName == null ) {
      System.err.println( "Couldn't find the class " + className
                          + " to get constructor with args=" + Utils.toString( argTypes ) );
      return null;
    }
    return getConstructorForArgTypes( classForName, argTypes );
  }

  public static Class<?>[] getClasses( Object[] objects ) {
    //return ClassUtils.toClass( objects );
    Class< ? > argTypes[] = null;
    if ( objects != null ) {
      argTypes = new Class< ? >[ objects.length ];
      for ( int i = 0; i < objects.length; ++i ) {
        if ( objects[ i ] == null ) {
          argTypes[ i ] = null;
        } else {
          argTypes[ i ] = objects[ i ].getClass();
        }
      }
    }
    return argTypes;
  }

  /**
   * Determines whether the array contain only Class<?> objects possibly with
   * some (but not all nulls).
   * 
   * @param objects
   * @return
   */
  public static boolean areClasses( Object[] objects ) {
    boolean someClass = false;
    boolean someNotClass = false;
    for ( Object o : objects ) {
      if ( o == null ) continue;
      if ( o instanceof Class ) {
        someClass = true;
      } else {
        someNotClass = true;
      }
    }
    return !someNotClass && someClass;
  }

  public static Constructor< ? > getConstructorForArgs( Class< ? > cls,
                                                          Object[] args ) {
      if ( Debug.isOn() ) Debug.outln( "getConstructorForArgs( " + cls.getName() + ", "
                   + Utils.toString( args ) );
      Class< ? > argTypes[] = getClasses( args );
      return getConstructorForArgTypes( cls, argTypes );
  /*    //Method matchingMethod = null;
      boolean gotOkNumArgs = false;
      int mostMatchingArgs = 0;
      boolean allArgsMatched = false;
      Constructor< ? > ctor = null;
      
      for ( Constructor< ? > aCtor : cls.getConstructors() ) {
          int numMatching = 0;
          boolean okNumArgs =
              ( aCtor.getParameterTypes().length == args.length )
                  || ( aCtor.isVarArgs() 
                       && ( aCtor.getParameterTypes().length < args.length
                            || aCtor.getParameterTypes().length == 1 ) );
          //if ( !okNumArgs ) continue;
          for ( int i = 0; i < Math.min( aCtor.getParameterTypes().length,
                                         args.length ); ++i ) {
            if ( args[ i ] == null ) continue;
            if ( ((Class<?>)aCtor.getParameterTypes()[ i ]).isAssignableFrom( args[ i ].getClass() ) ) {
              ++numMatching;
            }
          }
          if ( ( ctor == null ) || ( okNumArgs && !gotOkNumArgs )
               || ( ( okNumArgs && !gotOkNumArgs ) 
                    && ( numMatching > mostMatchingArgs ) ) ) {
            ctor = aCtor;
            gotOkNumArgs = okNumArgs;
            mostMatchingArgs = numMatching;
            allArgsMatched = ( numMatching >= aCtor.getParameterTypes().length );
          }
        }
      if ( ctor != null && !allArgsMatched ) {
        System.err.println( "constructor returned (" + ctor
                            + ") does not match all args: " + toString( args ) );
      } else if ( ctor == null ) {
        System.err.println( "constructor " + cls.getSimpleName() + toString( args )
                            + " not found" );
      }
      return ctor;
  */  }

  public static Pair< Constructor< ? >, Object[] >
      getConstructorForArgs( Class< ? > eventClass, Object[] arguments,
                             Object enclosingInstance ) {
    boolean nonStaticInnerClass = ClassUtils.isInnerClass( eventClass );
    if ( Debug.isOn() ) Debug.outln( eventClass.getName() + ": nonStaticInnerClass = " + nonStaticInnerClass );
    Object newArgs[] = arguments;
    if ( nonStaticInnerClass ) {
      newArgs = new Object[ arguments.length + 1 ];
      assert enclosingInstance != null;
      newArgs[ 0 ] = enclosingInstance;
      for ( int i = 0; i < arguments.length; ++i ) {
        newArgs[ i + 1 ] = arguments[ i ];
      }
    }
    Constructor< ? > constructor =
        ClassUtils.getConstructorForArgs( eventClass, newArgs );
    return new Pair< Constructor< ? >, Object[] >(constructor, newArgs );
  }

  public static < T > T getBestArgTypes( Map< T, Pair< Class< ? >[], Boolean > > candidates,
                                         // ConstructorDeclaration[] ctors,
                                         Class< ? >... argTypes ) {
    ArgTypeCompare< T > atc =
        new ArgTypeCompare< T >( argTypes );
    for ( Entry< T, Pair< Class< ? >[], Boolean > > e : candidates.entrySet() ) {
      atc.compare( e.getKey(), e.getValue().first, e.getValue().second );
    }
    if ( atc.best != null && !atc.allArgsMatched ) {
      System.err.println( "constructor returned (" + atc.best
                          + ") only matches " + atc.mostMatchingArgs
                          + " args: " + Utils.toString( argTypes, false ) );
    } else if ( atc.best == null ) {
      System.err.println( "best args not found in " + candidates );
    }
    return atc.best;
  }

  public static Constructor< ? > getConstructorForArgTypes( Constructor<?>[] ctors,
                                                              Class< ? >... argTypes ) {
      //Constructor< ? >[] ctors = null;
      ArgTypeCompare< Constructor< ? > > atc =
          new ArgTypeCompare< Constructor< ? > >( argTypes );
      for ( Constructor< ? > aCtor : ctors) {
        atc.compare( aCtor, aCtor.getParameterTypes(), aCtor.isVarArgs() );
      }
      if ( atc.best != null && !atc.allArgsMatched ) {
        System.err.println( "constructor returned (" + atc.best
                            + ") only matches " + atc.mostMatchingArgs
                            + " args: " + Utils.toString( argTypes, false ) );
      } else if ( atc.best == null ) {
        System.err.println( "constructor not found in " + ctors );
  //                          cls.getSimpleName()
  //                          + toString( argTypes, false ) + " not found for "
  //                          + cls.getSimpleName() );
      }
      return atc.best;
    }

  public static Constructor< ? > getConstructorForArgTypes( Class< ? > cls,
                                                              Class< ? >... argTypes ) {
      if ( argTypes == null ) argTypes = new Class< ? >[] {};
      if ( Debug.isOn() ) Debug.outln( "getConstructorForArgTypes( cls=" + cls.getName()
                   + ", argTypes=" + Utils.toString( argTypes ) + " )" );
      return getConstructorForArgTypes( cls.getConstructors(), argTypes );
  /*    ArgTypeCompare atc = new ArgTypeCompare( argTypes );
      for ( Constructor< ? > aCtor : cls.getConstructors() ) {
        atc.compare( aCtor, aCtor.getParameterTypes(), aCtor.isVarArgs() );
      }
      if ( atc.best != null && !atc.allArgsMatched ) {
        System.err.println( "constructor returned (" + atc.best
                            + ") only matches " + atc.mostMatchingArgs
                            + " args: " + toString( argTypes, false ) );
      } else if ( atc.best == null ) {
        System.err.println( "constructor " + cls.getSimpleName()
                            + toString( argTypes, false ) + " not found for "
                            + cls.getSimpleName() );
      }
      return (Constructor< ? >)atc.best;
  */  }

  public static Constructor< ? >
    getConstructorForArgTypes( Class< ? > cls, String packageName ) {
    Pair< Constructor< ? >, Object[] > p = 
        getConstructorForArgs(cls, new Object[]{}, packageName );
    if ( p == null ) return null;
    return p.first;
  }

  public static boolean isInnerClass( Class< ? > eventClass ) {
    return ( !Modifier.isStatic( eventClass.getModifiers() )
             && eventClass.getEnclosingClass() != null );
  }

  public static Class<?> getClassFromClasses( List< Class< ? > > classList ) {
    if ( Utils.isNullOrEmpty( classList ) ) {
      return null;
    }
    if ( classList.size() > 1 ) {
      System.err.println( "Error! Got multiple class candidates for constructor! " + classList );
    }
    return classList.get( 0 );
  }

  public static Method getJavaMethodForCommonFunction( String functionName,
                                                       List<Object> args ) {
    return getJavaMethodForCommonFunction( functionName, args.toArray() );
  }

  // TODO -- feed this through ArgTypeCompare to avoid mismatches
    public static Method getJavaMethodForCommonFunction( String functionName,
                                                         Object[] args ) {
  //    boolean alreadyClass = areClasses( args );
      Class<?>[] argTypes = null;
  //    if ( alreadyClass ) {
  //      argTypes = new Class<?>[args.length];
  //      boolean ok = toArrayOfType( args, argTypes, Class.class );
  //      assert ok;
  //    } else {
        argTypes = getClasses(args); 
  //    }
      return getJavaMethodForCommonFunction( functionName, argTypes );
    }

  public static Method getJavaMethodForCommonFunction( String functionName,
                                                       Class[] argTypes ) {
    // REVIEW -- Could use external Reflections library to get all classes in a
    // package:
    //   Reflections reflections = new Reflections("my.project.prefix");
    //   Set<Class<? extends Object>> allClasses = 
    //      reflections.getSubTypesOf(Object.class);
    //   use on:
    //     java.lang
    //     org.apache.commons.lang.
    //     java.util?
    Class< ? >[] classes =
        new Class< ? >[] { Math.class, //StringUtils.class,
                           Integer.class,
                           Double.class, Character.class, Boolean.class,
                           String.class,
                           //org.apache.commons.lang.ArrayUtils.class,
                           Arrays.class };
    for ( Class<?> c : classes ) {
      Method m = getMethodForArgTypes( c, functionName, argTypes );
      if ( m != null ) return m;
    }
    return null;
  }

  public static Method getMethodForArgs( String className,
                                         String preferredPackage,
                                         String callName,
                                         Object... args ) {
    Class< ? > classForName = getClassForName( className, preferredPackage, false );
    if ( Debug.errorOnNull( "Couldn't find the class " + className + " for method "
                      + callName + ( args == null ? "" : Utils.toString( args ) ),
                      classForName ) ) {
      return null;
    }
    return getMethodForArgs( classForName, callName, args );
  }

  public static Method getMethodForArgs( Class< ? > cls, String callName,
                                           Object... args ) {
      if ( Debug.isOn() ) Debug.outln( "getMethodForArgs( cls=" + cls.getName() + ", callName="
          + callName + ", args=" + Utils.toString( args ) + " )" );
      Class< ? > argTypes[] = null;
  //    boolean allClasses = areClasses( args ); 
  //    if ( allClasses ) {
  //      //argTypes = (Class< ? >[])args;
  //      boolean ok = toArrayOfType( args, argTypes, Class.class );
  //      assert ok;
  //    } else {
        argTypes = getClasses( args );
  //    }
      return getMethodForArgTypes( cls, callName, argTypes );
    }

  public static Method getMethodForArgTypes( String className,
                                             String preferredPackage,
                                             String callName,
                                             Class<?>... argTypes ) {
    return getMethodForArgTypes( className, preferredPackage, callName,
                                 argTypes, true );
  }

  private static int lengthOfCommonPrefix( String s1, String s2 ) {
    int i=0;
    for ( ; i < Math.min( s1.length(), s2.length() ); ++i ) {
      if ( s1.charAt(i) != s2.charAt(i) ) {
        break;
      }
    }
    return i;
  }
  
  public static Method getMethodForArgTypes( String className,
                                             String preferredPackage,
                                             String callName,
                                             Class<?>[] argTypes,
                                             boolean complainIfNotFound ) {
    //Debug.turnOff();  // DELETE ME -- FIXME
    Debug.outln("=========================start===============================");
    //Debug.errln("=========================start===============================");
    //Class< ? > classForName = getClassForName( className, preferredPackage, false );
    List< Class< ? > > classesForName = getClassesForName( className, false );
    //Debug.err("classForName = " + classForName );
    Debug.err("classesForName = " + classesForName );
    if ( Utils.isNullOrEmpty( classesForName ) ) {
      if ( complainIfNotFound ) {
        System.err.println( "Couldn't find the class " + className + " for method "
                     + callName
                     + ( argTypes == null ? "" : Utils.toString( argTypes ) ) );
      }
      Debug.outln("===========================end==============================");
      //Debug.errln("===========================end==============================");
      //Debug.turnOff();  // DELETE ME -- FIXME
      return null;
    }
    Method best = null;
    Method nextBest = null;
    int charsInCommonWithPreferredPackage = 0;
    boolean tie = false;
    for ( Class<?> cls : classesForName ) {
      //Method m = getMethodForArgTypes( classForName, callName, argTypes );
      Method m = getMethodForArgTypes( cls, callName, argTypes, false );
      if ( m != null ) {
        int numCharsInCommon = lengthOfCommonPrefix(preferredPackage,cls.getCanonicalName());
        if ( best == null ) {
          best = m;
          charsInCommonWithPreferredPackage = numCharsInCommon;
        } else {
          if ( numCharsInCommon > charsInCommonWithPreferredPackage ) {
            best = m;
            charsInCommonWithPreferredPackage = numCharsInCommon;
          } else if ( charsInCommonWithPreferredPackage == numCharsInCommon ) {
            tie = true;
            nextBest = m;
          }
        }
      }
    }
    if ( tie ) {
      System.err.println( "Warning! Multiple candidate methods found by getMethodForArgTypes("
          + className + "." + callName
          + Utils.toString( argTypes, false )
          + "): (1) " + best + " (2) " + nextBest );
    }
    if ( Debug.isOn() ) Debug.errorOnNull( "getMethodForArgTypes(" + className + "." + callName
                 + Utils.toString( argTypes, false ) + "): Could not find method!", best );
    Debug.outln("===========================end==============================");
    //Debug.errln("===========================end==============================");
    //Debug.turnOff();  // DELETE ME -- FIXME
    return best;
  }

  public static Method oldGetMethodForArgTypes( String className,
                                             String preferredPackage,
                                             String callName,
                                             Class<?>[] argTypes,
                                             boolean complainIfNotFound ) {
    Class< ? > classForName = getClassForName( className, preferredPackage, false );
    if ( classForName == null ) {
      if ( complainIfNotFound ) {
        System.err.println( "Couldn't find the class " + className + " for method "
                     + callName
                     + ( argTypes == null ? "" : Utils.toString( argTypes ) ) );
      }
      return null;
    }
    Method m = getMethodForArgTypes( classForName, callName, argTypes );
    if ( Debug.isOn() ) Debug.errorOnNull( "getMethodForArgTypes(" + className + "." + callName
                 + Utils.toString( argTypes, false ) + "): Could not find method!", m );
    return m;
  }

  public static Method getMethodForArgTypes( Class< ? > cls, String callName,
                                             Class<?>... argTypes ) {
    return getMethodForArgTypes( cls, callName, argTypes, true );
  }
  public static Method getMethodForArgTypes( Class< ? > cls, String callName,
                                             Class<?>[] argTypes, boolean complain ) {
  //    return getMethodForArgTypes( cls, callName, argTypes, 10.0, 2.0, null );
  //  }
  //  public static Method getMethodForArgTypes( Class< ? > cls, String callName,
  //                                             Class<?>[] argTypes,
  //                                             double numArgsCost,
  //                                             double argMismatchCost,
  //                                             Map< Class< ? >, Map< Class< ? >, Double > > transformCost ) {
      if ( argTypes == null ) argTypes = new Class<?>[] {};
      if ( Debug.isOn() ) Debug.outln( "getMethodForArgTypes( cls=" + cls.getName() + ", callName="
                   + callName + ", argTypes=" + Utils.toString( argTypes ) + " )" );
  //    Method matchingMethod = null;
  //    boolean gotOkNumArgs = false;
  //    int mostMatchingArgs = 0;
  //    int mostDeps = 0;
  //    boolean allArgsMatched = false;
  //    double bestScore = Double.MAX_VALUE;
      boolean debugWasOn = Debug.isOn();
      //Debug.turnOff();
      Method[] methods = null;
      if ( Debug.isOn() ) Debug.outln( "calling getMethods() on class " + cls.getName() );
      try {
        methods = cls.getMethods();
      } catch ( Exception e ) {
        System.err.println( "Got exception calling " + cls.getName()
                            + ".getMethod(): " + e.getMessage() );
      }
      if ( Debug.isOn() ) Debug.outln( "--> got methods: " + Utils.toString( methods ) );
      ArgTypeCompare atc = new ArgTypeCompare( argTypes );
      if ( methods != null ) {
        for ( Method m : methods ) {
          if ( m.getName().equals( callName ) ) {
            atc.compare( m, m.getParameterTypes(), m.isVarArgs() );
          }
        }
      }
      if ( debugWasOn ) {
        Debug.turnOn();
      }
      if ( atc.best != null && !atc.allArgsMatched ) {
      if ( Debug.isOn() ) Debug.errln( "getMethodForArgTypes( cls="
                                       + cls.getName() + ", callName="
                                       + callName + ", argTypes="
                                       + Utils.toString( argTypes )
                                       + " ): method returned (" + atc.best
                                       + ") only matches "
                                       + atc.mostMatchingArgs + " args: "
                                       + Utils.toString( argTypes ) );
      } else if ( atc.best == null && complain ) {
        System.err.println( "method " + callName + "(" + Utils.toString( argTypes ) + ")"
                            + " not found for " + cls.getSimpleName() );
      }
      return (Method)atc.best;
    }

  public static Object getFieldValue( Object o, String fieldName ) {
    if ( o == null || Utils.isNullOrEmpty( fieldName ) ) {
      return null;
    }
    Exception ex = null;
    Field f = null;
    try {
      f = o.getClass().getField( fieldName );
      return f.get( o );
    } catch ( NoSuchFieldException e ) {
      ex = e;
    } catch ( SecurityException e ) {
      ex = e;
    } catch ( IllegalArgumentException e ) {
      ex = e;
    } catch ( IllegalAccessException e ) {
      ex = e;
    }
    if ( f == null && o instanceof gov.nasa.jpl.ae.event.Parameter ) {
        return getFieldValue( ( (gov.nasa.jpl.ae.event.Parameter)o ).getValueNoPropagate(),
                              fieldName );
    }
    if ( f == null && ex != null ) {
      // TODO Auto-generated catch block
      ex.printStackTrace();
    }
    return null;
  }

  public static String parameterPartOfName( String longName ) {
    return parameterPartOfName( longName, true );
  }
  public static String parameterPartOfName( String longName,
                                            boolean includeBrackets ) {
    int pos1 = longName.indexOf( '<' );
    if ( pos1 == -1 ) {
      return null;
    }
    int pos2 = longName.lastIndexOf( '>' );
    assert( pos2 >= 0 );
    if ( pos2 == -1 ) return null;
    if ( !includeBrackets ) {
      pos1 += 1;
      pos2 -=1;
    }
    String paramPart = longName.substring( pos1, pos2+1 ).trim();
    return paramPart;
  }

  public static String noParameterName( String longName ) {
    if ( longName == null ) return null;
    int pos = longName.indexOf( '<' );
    if ( pos == -1 ) {
      return longName;
    }
    String noParamName = longName.substring( 0, pos );
    return noParamName;
  }

  /**
   * @param type
   * @return whether the input type is a number class or a number primitive.
   */
  public static boolean isNumber( Class< ? > type ) {
    if ( type == null ) return false;
    Class<?> forPrim = classForPrimitive( type );
    if ( forPrim != null ) type = forPrim;
    return ( Number.class.isAssignableFrom( type ) );
  }

  /**
   * @param type
   * @return whether the input type has an integer domain (int, short, long, etc).
   */
  public static boolean isInteger( Class< ? > type ) {
    if ( type == null ) return false;
    return ( Integer.class.isAssignableFrom( type ) );
//    Class<?> forPrim = classForPrimitive( type );
//    if ( forPrim != null ) type = forPrim;
  }
}
