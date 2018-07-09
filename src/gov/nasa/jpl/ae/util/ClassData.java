package gov.nasa.jpl.ae.util;

import gov.nasa.jpl.ae.event.ConstructorCall;
import gov.nasa.jpl.ae.event.Parameter;
import gov.nasa.jpl.ae.event.ParameterListenerImpl;
import gov.nasa.jpl.ae.event.TimeDomain;
import gov.nasa.jpl.ae.solver.BooleanDomain;
import gov.nasa.jpl.ae.solver.DoubleDomain;
import gov.nasa.jpl.ae.solver.IntegerDomain;
import gov.nasa.jpl.ae.solver.LongDomain;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Utils;
import japa.parser.ast.CompilationUnit;
import japa.parser.ast.body.BodyDeclaration;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.ConstructorDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.TypeDeclaration;
import japa.parser.ast.type.ClassOrInterfaceType;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class
ClassData {

  /**
   * A struct for packaging a name, type, and value as Strings.
   */
  public static class Param implements Comparator< Param >,
                            Comparable< Param > {
    public String name;
    public String type;
    public String value;
    public String scope;
  
//    public Param( String name, String type, String value ) {
//      this.name = name;
//      this.type = type;
//      this.value = value;
//    }
    public Param( String name, String type, String value, String scope ) {
      this.name = name;
      this.type = type;
      this.value = value;
      this.scope = scope;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
      return "(" + name + ", " + type + ", " + value + ")";
    }

    /** 
     * Only the name and type are compared.
     * (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo( Param o ) {
      return compare( this, o );
    }

    /** 
     * Only the name and type are compared.
     * (non-Javadoc)
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    @Override
    public int compare( Param o1, Param o2 ) {
      int compare = CompareUtils.compare( o1.name, o2.name );
      if ( compare != 0 ) return compare;
      compare = CompareUtils.compare( o1.type, o2.type );
      if ( compare != 0 ) return compare;
      return 0;
    }

  }

  public static final Set< MethodDeclaration > emptyMethodDeclarationSet =
      new TreeSet<MethodDeclaration>(new CompareUtils.GenericComparator< MethodDeclaration >());

  // protected String currentEnclosingClassName;
  // protected String currentScopedClassName;
  protected Map< String, Boolean > isStaticMap =
      new TreeMap< String, Boolean >();
  // Map: longName -> method name -> set of javaparser.MethodDeclarations
  protected Map< String, Map< String, Set< MethodDeclaration > > > methodTable =
      new TreeMap< String, Map< String, Set< MethodDeclaration > > >();

  // // Map class name (long?) -> set of javaparser.ConstructorDeclarations
  // protected Map< String, Set< ConstructorDeclaration> >
  // constructorDeclarations =
  // new TreeMap< String, Set< ConstructorDeclaration > >();

  // Map: inner class name -> enclosing class name
  // protected Map< String, Set< String > > innerToEnclosingClassNames =
  // new TreeMap< String, Set< String > >();
  // WARNING! TODO -- Two classes could nest classes with the same name, so
  // the Map<S,Set<S>> above is correct, but maybe not useful. Instead, will
  // keep the current enclosing class, and the current scoped name.
  protected Map< String, String > nestedToEnclosingClassNames =
      new TreeMap< String, String >();

  /**
   * The package for all of the classes.
   */
  protected String packageName = null;
  
  /**
   * Map: class long name -> parameter name -> Param
   */
  protected Map< String, Map< String, Param > > paramTable =
      new TreeMap< String, Map< String, Param > >();

  // TODO -- Need to handle local variables of function declarations
  // TODO -- Consider creating a subclass of ParameterListenerImpl for each of these
  // TODO -- instead of an actual function.

  /**
   * This map contains the parameters in the signatures of function declarations.
   * The map is used to determine the scope of variables referenced in function
   * declarations.
   * Map: function long name -> function declaration object -> parameter name -> Param
   */
  protected Map< String, Map< Object, Map< String, Param > > > functionParamTable =
          new TreeMap<>();

  /**
   * Parameters may be created for evaluation at parse time.
   */
  protected Map< ClassData.Param, Parameter< ? > > parameterMap =
      new TreeMap< ClassData.Param, Parameter<?> >();
  
  protected Map< String, ParameterListenerImpl > aeClasses = // REVIEW -- rename to aeObjects??!
      new TreeMap< String, ParameterListenerImpl >();

  /**
   * The long name of the class currently being processed.
   */
  protected String currentClass = null;

  protected ParameterListenerImpl currentAeClass = null;

  private int counter = 0;

  /**
   * Map: simpleName -> javaparser.CompilationUnit
   */
  private Map< String, CompilationUnit > classes =
      new TreeMap< String, CompilationUnit >();

  /**
   * The javaparser.CompilationUnit for the class currently being processed.
   */
  private CompilationUnit currentCompilationUnit = null;

  /**
   * These are the imports explicit in the model.
   */
  public Map< String, Set< String > > imports = new LinkedHashMap<>();


  public String getClassNameWithoutScope( String className ) {
    if ( Utils.isNullOrEmpty(className) ) return className;
    int pos = className.lastIndexOf('.');
    if ( pos < 0 ) return className;
    int pos1 = className.lastIndexOf('<');
    if ( pos1 < 0 || pos1 > pos ) {
      return className.substring(pos+1);
    }
    int braketCt = 0;
    pos = className.length()-1;
    while (pos >= 0) {
      char c = className.charAt(pos);
      if ( braketCt == 0 && c == '.') {
        return className.substring(pos+1);
      }
      if ( c == '<') {
        braketCt -= 1;
      }
      if ( c == '>') {
        braketCt += 1;
      }
      pos += 1;
    }
    return className;
  }

  /**
   * Try to figure out the scope of the class name if an inner class, and return
   * the scoped class name.
   * 
   * @param className
   * @return
   */
  public String getClassNameWithScope( String className ) {
    if ( Utils.isNullOrEmpty( className ) ) return null;
    // Return input class name if in table.
    if ( getClassNames().contains( className ) ) {
      return className;
    }
    // See if the class is an inner class of the current class
    String classNameWithScope = this.currentClass + "." + className;
    if ( getClassNames().contains( classNameWithScope ) ) {
      return classNameWithScope;
    }

    // Loop through existing class names and find those that end with the input
    // name. Pick the one that seems to be "best" and print a warning if not
    // sure.
    String otherClassName = null;
    for ( String n : getClassNames() ) {
      if ( n.endsWith( className )
           && ( n.length() == className.length() || n.charAt( n.lastIndexOf( className ) - 1 ) == '.' ) ) {
        if ( otherClassName != null && otherClassName.endsWith( className ) ) {
          if ( n.endsWith( classNameWithScope ) ) {
            if ( otherClassName.endsWith( classNameWithScope ) ) {
              if ( n.contains( classNameWithScope ) ) {
                if ( otherClassName.contains( classNameWithScope ) ) {
                  System.err.println( "Warning! Got more than one candidate class for "
                                      + className
                                      + ": "
                                      + otherClassName
                                      + ", " + n );
                  if ( n.length() < otherClassName.length() ) {
                    otherClassName = n;
                  }
                } else {
                  otherClassName = n;
                }
              }
            } else {
              otherClassName = n;
            }
          }
        } else {
          otherClassName = n;
        }
      }
    }

    if ( Utils.isNullOrEmpty( otherClassName ) ) {
      otherClassName = getImportedClassNameWithScope( className );
      // otherClassName = ClassUtils.getFullyQualifiedName( className, false );
    }
    if ( Utils.isNullOrEmpty( otherClassName ) ) {
      otherClassName = getClassNameWithScopeInPackages( className );
    }
    return otherClassName;
  }

  public String getClassNameWithScopeInPackages( String className ) {
//    System.out.println( "TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT    " + className );
    Package[] pkgs = Package.getPackages();
    Collection<String> pkgNames = ClassUtils.getPackageStrings( pkgs );
    for ( String p : pkgNames ) {
    //for ( int i = 0; i < pkgNames.size(); ++i ) {
      //String p = pkgNames.get(i);
      String classNameWithScope = p + "." + className;
      Class<?> cls = null;
      try {
        cls = Class.forName( classNameWithScope );
      } catch ( ClassNotFoundException e ) {
      }
      if ( cls != null ) {
        return classNameWithScope; //cls.getCanonicalName();
      }
    }
    return null;
  }

  public Set<String> getImportedClassNames() {
    Collection<String> importNames = imports.keySet();
    // Make sure current class is first
    if ( !Utils.isNullOrEmpty( getCurrentClass() ) ) {
      boolean removed = importNames.remove( getCurrentClass() );
      if ( removed ) {
        List<String> newNames = Utils.asList( importNames );
        newNames.add( 0, getCurrentClass() );
        importNames = newNames;
      }
    }
    return new LinkedHashSet<>( importNames );
  }

    public String getImportedClassNameWithScope( String className ) {
    String otherClassName = null;
//            ClassUtils.getFullyQualifiedName( className, false );
//    if ( !Utils.isNullOrEmpty( otherClassName ) ) {
//      return otherClassName;
//    }
    Collection<String> importNames = getImportedClassNames();
    for ( String impName : importNames ) {
      Set<String> imps = imports.get( impName );
      if ( imps == null ) continue;
      for ( String imp : imps ) {
        if ( imp != null && imp.endsWith( "." + className ) ) {
          otherClassName = imp;
          break;
        }
      }
    }
    return otherClassName;
  }

  public String getClassNameWithScope( String classOrInterfaceName,
                                       boolean doTypeParameters ) {
    if ( Utils.isNullOrEmpty( classOrInterfaceName ) ) return null;
    String typeParameters = "";
    if ( classOrInterfaceName.contains( "<" )
         && classOrInterfaceName.contains( ">" ) ) {
      typeParameters =
          classOrInterfaceName.substring( classOrInterfaceName.indexOf( '<' ) + 1,
                                          classOrInterfaceName.lastIndexOf( '>' ) )
                              .trim();
      if ( doTypeParameters ) {
        String tpNameWithScope = getClassNameWithScope( typeParameters, true );
        if ( !Utils.isNullOrEmpty( tpNameWithScope ) ) {
          typeParameters = tpNameWithScope;
        }
      }
      typeParameters =
          "<" + ClassUtils.getNonPrimitiveClassName( typeParameters ) + ">";
      classOrInterfaceName =
          classOrInterfaceName.substring( 0, classOrInterfaceName.indexOf( '<' ) );
    }
    String classNameWithScope = getClassNameWithScope( classOrInterfaceName );
    if ( !Utils.isNullOrEmpty( classNameWithScope ) ) {
      classOrInterfaceName = classNameWithScope;
    }
    return classOrInterfaceName + typeParameters;
  }

  public Set< MethodDeclaration > getClassMethodsWithName( String methodName,
                                                           String className ) {
    if ( Debug.errorOnNull( false,
                            "Passed null to getClassMethodsWithName( methodName="
                                + methodName + ", className=" + className + ")",
                            methodName, className ) ) {
      return emptyMethodDeclarationSet;
    }
    
    Map< String, Set< MethodDeclaration > > classMethods =
        methodTable.get( className );
    if ( Utils.isNullOrEmpty(classMethods) ) {
      classMethods = methodTable.get( ClassUtils.simpleName( className ) );
    }
    if ( Utils.isNullOrEmpty(classMethods)  ) {
      String scopedName = getClassNameWithScope( className );
      if ( !Utils.isNullOrEmpty( scopedName ) ) {
        classMethods = methodTable.get( scopedName );
      }
    }
    Set< MethodDeclaration > methodSet = emptyMethodDeclarationSet;
    if ( Utils.isNullOrEmpty(classMethods)  && isInnerClass( className ) ) {
      methodSet = getClassMethodsWithName( methodName, getEnclosingClassName( className ) );
    }
    if ( Utils.isNullOrEmpty( methodSet ) && Utils.isNullOrEmpty(classMethods)  ) {
      if ( Debug.isOn() ) Debug.outln( "getClassMethodsWithName(" + methodName + ", " + className
                   + ") couldn't find class and method in ClassData methodTable cache!\nmethodTable="
                   + methodTable.toString() );
      return methodSet;
    }
    if ( Utils.isNullOrEmpty( methodSet ) ) {
      methodSet = classMethods.get( methodName );
    }
    if ( Utils.isNullOrEmpty( methodSet ) ) {
      if ( Debug.isOn() ) Debug.outln( "getClassMethodsWithName(" + methodName + ", " + className
                   + ") = null\nmethodTable=" + methodTable.toString() );
    }
    return methodSet;
  }

  public String getFullyQualifiedName( String classOrInterfaceName,
                                       boolean doTypeParameters ) {
    String typeParameters = "";
    if ( classOrInterfaceName.contains( "<" )
         && classOrInterfaceName.contains( ">" ) ) {
      typeParameters =
          classOrInterfaceName.substring( classOrInterfaceName.indexOf( '<' ) + 1,
                                          classOrInterfaceName.lastIndexOf( '>' ) )
                              .trim();
      typeParameters =
          "<"
              + ( doTypeParameters
                                  ? getFullyQualifiedName( typeParameters, true )
                                  : ClassUtils.getNonPrimitiveClassName( typeParameters ) )
              + ">";
      classOrInterfaceName =
          classOrInterfaceName.substring( 0, classOrInterfaceName.indexOf( '<' ) );
    }
    String n = ClassUtils.getFullyQualifiedName( classOrInterfaceName, false );
    if ( Utils.isNullOrEmpty( n ) || n.equals( classOrInterfaceName ) ) {
      n = getClassNameWithScope( classOrInterfaceName );
    }
    if ( Utils.isNullOrEmpty( n ) || n.equals( classOrInterfaceName ) ) {
      n = getClassNameWithScope( ClassUtils.simpleName( classOrInterfaceName ) );
    }
    if ( Utils.isNullOrEmpty( n ) || n.equals( classOrInterfaceName ) ) {
      n = classOrInterfaceName;
    }
    n = n + typeParameters;
    if ( Debug.isOn() ) Debug.outln( "getFullyQualifiedName("
                                     + classOrInterfaceName + ", "
                                     + doTypeParameters + ") = " + n );
    return n;
  }
  
  /**
   * @param className
   * @param paramName
   * @param lookOutsideClassData
   * @return
   */
  public Parameter<?> getParameter( String className, String paramName, String paramType,
                                       boolean lookOutsideClassData,
                                       boolean setCurrentClass,
                                       boolean addIfNotFound,
                                       boolean complainIfNotFound ) {
    Param param = getParam( className, paramName,paramType, lookOutsideClassData,
                            setCurrentClass, addIfNotFound, complainIfNotFound );
    Parameter<?> parameter = parameterMap.get( param );
    return parameter;
  }
  
  /**
   * Find the Param with the given name, paramName, in the given class,
   * className. Create the param if it does not exist with null type and value.
   * This sets the currentClass!
   * 
   * @param className
   * @param paramName
   * @param lookOutsideClassData
   * @return the found or created Param or null if the paramName is null or "".
   */
  public Param getParam( String className, String paramName, String paramType,
                         boolean lookOutsideClassData, boolean setCurrentClass,
                         boolean addIfNotFound, boolean complainIfNotFound ) {
    if ( className == null ) className = getCurrentClass();
    ParameterListenerImpl aeClass = getAeClass( className, true );
    className = aeClass.getName();
    if ( setCurrentClass ) {
      setCurrentAeClass( aeClass );
    }
    Param p = lookupMemberByName( className, paramName, lookOutsideClassData, false );
    if ( p == null && paramName != null && addIfNotFound ) {
      p = makeParam( className, paramName, paramType, null );
    }
    if ( p == null ) {
      Debug.errorOnNull( complainIfNotFound, complainIfNotFound, "Could not " +
                         ( addIfNotFound ? "create" : "find" ) +
                         " parameter " + className + "." + paramName, p );
    }
    return p;
  }
  
  /**
   * @param className
   * @param paramName
   * @param lookOutsideClassData
   * @return
   */
  public Parameter<?> getParameter( String className, String paramName,
                                       boolean lookOutsideClassData,
                                       boolean setCurrentClass,
                                       boolean addIfNotFound,
                                       boolean complainIfNotFound ) {
    
    return getParameter(className, paramName, null, lookOutsideClassData,
                        setCurrentClass, addIfNotFound, complainIfNotFound );
  }
  
  /**
   * Find the Param with the given name, paramName, in the given class,
   * className. Create the param if it does not exist with null type and value.
   * This sets the currentClass!
   * 
   * @param className
   * @param paramName
   * @param lookOutsideClassData
   * @return the found or created Param or null if the paramName is null or "".
   */
  public Param getParam( String className, String paramName,
                         boolean lookOutsideClassData, boolean setCurrentClass,
                         boolean addIfNotFound, boolean complainIfNotFound ) {
    
    return getParam(  className,  paramName, null, lookOutsideClassData,  setCurrentClass,
                      addIfNotFound,  complainIfNotFound );
  }
  
  /**
   * @param className
   *          the name of the AE class
   * @param createIfNotFound
   *          whether to create the AE class if it does not exist
   * @return the AE class (ParameterListenerImpl) with the given name or null if
   *         there isn't one and one is not created (based on createIfNotFound)
   */
  public ParameterListenerImpl getAeClass( String className,
                                           boolean createIfNotFound ) {
    ParameterListenerImpl aeClass = null;
    if ( className == null ) {
      className = "GeneratedClass" + this.counter++;
    }
    if ( aeClasses.containsKey( className ) ) {
      aeClass = aeClasses.get( className );
    } else if ( createIfNotFound ) {
      aeClass = constructClass( className );
      aeClasses.put( className, aeClass );
    }
    return aeClass;
  }
  
  public ParameterListenerImpl constructClass( String className ) {
    return new ParameterListenerImpl( className );
  }

//  public < T > Parameter< T > makeParameter( String className, String paramName, Class< T > type, T value ) {
//    ParameterListenerImpl aeClass = getAeClass( className, true );
//    
//    Parameter < T > parameter = new Parameter< T >( p.name, null, (T)p.value, aeClass );
//    HERE!!
//    return parameter;
//  }
  public < T > Parameter< T > makeParameter( String className, Param p ) {//, Class< T > type ) {
    ParameterListenerImpl aeClass = getAeClass( className, true );
    Parameter < T > parameter = constructParameter( className, p, null );//new Parameter< T >( p.name, null, p.value, aeClass );
    aeClass.getParameters().add( parameter );
    parameterMap.put( p, parameter );
    return parameter;
  }
  
  /**
   * Create a Param with the given name, type, and value Strings, and add the
   * Param to the paramTable for the given class name. If the Param already
   * exists in the table, it will be overwritten.
   * 
   * @param className
   * @param paramName
   * @param type
   * @param value
   * @return the created Param
   */
  public Param makeParam( String className, String paramName, String type,
                           String value ) {
    Param p = new Param( paramName, type, value, className );
    Param existingParam = Utils.put( paramTable, className, paramName, p );
    Parameter< ? > parameter = parameterMap.get( p );
    if ( parameter == null ) {
      parameter = makeParameter(className, p );// constructParameter( className, p );
    }// else 
    if ( existingParam != null ) {
      if ( parameterMap.containsKey( existingParam ) ) {
        parameterMap.remove( existingParam );
        parameterMap.put( p, parameter );
      }
    }
    return p;
  }

  /**
   * a struct of two types and an array
   */
  public static class PTA {
    public PTA( Class< ? extends Parameter< ? >> paramType,
                Class< ? > genericType, Object[] argArr ) {
      this.paramType = paramType;
      this.genericType = genericType;
      this.argArr = argArr;
    }
    public Class< ? extends Parameter< ? > > paramType = null;
    public Class< ? > genericType = null;
    public Object[] argArr = null;
  }
  
//  public Pair< Pair< String, String >, String[] >
//  convertToParameterTypeAndConstructorArgs( String paramName, String paramTypeName,
//                                            String classOfParameterName ) {
//    
//  }
  /**
   * Determines the AE translated parameter type, generic parameter types, and arguments.  
   * @param paramName
   * @param paramTypeName
   * @param classNameOfParameter
   * @param enclosingObject
   * @return
   */
  public PTA
      convertToParameterTypeAndConstructorArguments( String paramName,
                                                     String paramTypeName,
                                                     String classNameOfParameter,
                                                     String enclosingObject) {
    PTA typesAndArgs = null;
    
    Class< ? extends Parameter< ? > > paramType = null;
    Class< ? > genericParamType = null;
    Object[] argArr = null;
    
    if ( Utils.isNullOrEmpty( paramTypeName ) ) {
      Param pDef =
          lookupMemberByName( classNameOfParameter, paramName, true, true );
      if ( pDef != null ) {
        paramTypeName = pDef.type;
      }
    }
    String type = "Parameter";
    String parameterTypes = paramTypeName;

    // parameterTypes = getFullyQualifiedName( parameterTypes, true );
    parameterTypes = getClassNameWithScope( parameterTypes, true );
    String castType = parameterTypes;
    // TODO -- REVIEW -- Why is p.value in args by default, but recognized types
    // do not include p.value?
//    String valueArg = "null";
//    String typePlaceholder = "!TYPE!";
    // if ( valueArg.equals( "null" )
    // || ( valueArg.startsWith( "new Expression" ) &&
    // valueArg.endsWith( "(null)" ) ) ) {
//    valueArg = "(" + typePlaceholder + ")" + valueArg; // replacing !TYPE! later
    // }
    argArr = new Object[]{ paramName, null, null, getCurrentAeClass() };
    //String args = "\"" + paramName + "\", null, " + valueArg + ", this";
    String parameterClass =
        typeToParameterType( paramTypeName );
    if ( Utils.isNullOrEmpty( paramTypeName ) ) {
      System.err.println( "Error! creating a field " + paramName + ":" + paramTypeName + " of unknown type!!" );
    } else if ( !parameterClass.equals( paramTypeName ) ) {
      type = parameterClass;
      if ( !type.equals( "Parameter" ) ) {
        // This is the case for IntegerParameter, StringParameter, . . .
        parameterTypes = null;
        if ( !Utils.isNullOrEmpty( castType ) ) {
//          args = "\"" + paramName + "\", " + valueArg + ", this";
          argArr = new Object[]{ paramName, // null  // use default domain instead of null
                                 getCurrentAeClass() };
        }
      }
    } else if ( paramTypeName.toLowerCase().equals( "time" ) ) {
      type = "Timepoint";
      parameterTypes = null;
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
//        args = "\"" + paramName + "\", " + valueArg + ", this";
        argArr = new Object[]{ paramName, TimeDomain.positiveDomain, getCurrentAeClass() };
      }
    } else if ( paramTypeName.toLowerCase().startsWith( "long" )
                || paramTypeName.trim().replaceAll( " ", "" )
                                .equals( "Parameter<Long>" ) ) {
      type = "LongParameter";
      parameterTypes = null; // "Integer";
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
        // args = "\"" + paramName + "\", " + valueArg + ", this";
        argArr = new Object[] { paramName, LongDomain.defaultDomain,
                                getCurrentAeClass() };
        //castType = castType.toLowerCase();
      }
    } else if ( paramTypeName.toLowerCase().startsWith( "int" )
                || paramTypeName.trim().replaceAll( " ", "" )
                         .equals( "Parameter<Integer>" ) ) {
      type = "IntegerParameter";
      parameterTypes = null; // "Integer";
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
//        args = "\"" + paramName + "\", " + valueArg + ", this";
        argArr = new Object[]{ paramName, IntegerDomain.defaultDomain, getCurrentAeClass() };
      }
    } else if ( paramTypeName.toLowerCase().equals( "double" )
                || paramTypeName.trim().replaceAll( " ", "" )
                         .equals( "Parameter<Double>" ) ) {
      type = "DoubleParameter";
      parameterTypes = null;
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
//        args = "\"" + paramName + "\", " + valueArg + ", this";
        argArr = new Object[]{ paramName, DoubleDomain.defaultDomain, getCurrentAeClass() };
      }
    } else if ( paramTypeName.toLowerCase().equals( "boolean" )
                || paramTypeName.trim().replaceAll( " ", "" )
                         .equals( "Parameter<Boolean>" ) ) {
      type = "BooleanParameter";
      parameterTypes = null;
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
//        args = "\"" + paramName + "\", " + valueArg + ", this";
        argArr = new Object[]{ paramName, BooleanDomain.defaultDomain, getCurrentAeClass() };
      }
    } else if ( paramTypeName.equals( "String" )
                || paramTypeName.trim().replaceAll( " ", "" )
                         .equals( "Parameter<String>" ) ) {
      type = "StringParameter";
      parameterTypes = null;
      // args = "\"" + p.name + "\", this";
      // } else if ( p.type.startsWith( "TimeVaryingMap" ) ) {
      // args = "\"" + p.name + "\", this";
    }
//    if ( Utils.isNullOrEmpty( castType ) ) {
//      typePlaceholder = "(" + typePlaceholder + ")";
//      args = args.replace( typePlaceholder, "" );
//    } else {
//      args = args.replace( typePlaceholder, castType );
//    }

//    // HACK -- TODO
//    if ( args.contains( ", new FunctionCall" ) ) {
//      args += ", true";
//    }

    type = type + ( Utils.isNullOrEmpty( parameterTypes ) ? "" : "<" + parameterTypes + ">" );
    paramType =
        (Class< ? extends Parameter< ? > >)ClassUtils.getClassForName( type,
                                                                       null,
                                                                       getPackageName(),
                                                                       false );
    genericParamType = ClassUtils.getClassForName( parameterTypes, null, getPackageName(), false );

    //types = (T)new Pair< Class<? extends Parameter<?>>, Class<?> >( paramType, genericParamType );
    //typesAndArgs = (R)new Pair< T, Object[] >( types, argArr );
    typesAndArgs = new PTA( paramType, genericParamType, argArr );
    return typesAndArgs;
  }

  public ClassData.PTA
  convertToEventParameterTypeAndConstructorArguments( ClassData.Param p,
                                                      String classNameOfParameter,
                                                      String enclosingObject) {
    return convertToParameterTypeAndConstructorArguments( p.name, p.type,
                                                          classNameOfParameter, enclosingObject );
  }
  
  /**
   * Create a Parameter based on the type
   * @param className
   * @param param
   * @return a parameter
   */
  public < P extends Parameter< ? > > P constructParameter( String className,
                                                            Param param,
                                                            String enclosingObject) {
    PTA pta =
        convertToParameterTypeAndConstructorArguments( param.name, param.type, className, enclosingObject );
    Class< P > cls = (Class< P >)pta.paramType;
    ConstructorCall call = new ConstructorCall( null, cls, pta.argArr, (Class<?>)null );
    P parameter = null;
    try {
      parameter = (P)call.evaluate( true );
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
    return parameter;
  }

  public String scopeForParameter( String className, String paramName,
                                   boolean lookOutsideClassData,
                                   boolean complainIfNotFound ) {
    Param ppp = lookupMemberByName(className, paramName, lookOutsideClassData, complainIfNotFound);
    //if ( ppp == null ) return null;
    if ( ppp != null && !Utils.isNullOrEmpty(ppp.scope) ) {
      return ppp.scope;
    }
    if ( ppp != null ) {
      String classNameWithScope = getClassNameWithScope(className);
      Map<String, Param> params = paramTable.get(classNameWithScope);
      if (params.values().contains(ppp)) {
        return className;
      }
    }
    if ( isInnerClass( className ) ) {
      String enclosingClassName = getEnclosingClassName(className);
      if (!Utils.isNullOrEmpty(enclosingClassName)) {
        String s = scopeForParameter(enclosingClassName, paramName, lookOutsideClassData, complainIfNotFound);
        if ( !Utils.isNullOrEmpty( s ) ) return s;
      }
    }
    return null;
  }

  protected Param lookupMemberByName( String className, String paramName,
                                      boolean lookOutsideClassData ) {
    return lookupMemberByName( className, paramName, lookOutsideClassData, true );
  }

  public Param lookupMemberByName( String scope, String paramName,
                                      boolean lookOutsideClassData,
                                      boolean complainIfNotFound ) {
    if ( Debug.errorOnNull( complainIfNotFound, complainIfNotFound,
                            "Passing null in lookupMemberByName(" + scope
                            + ", " + paramName + ")", scope, paramName ) ) {
      return null;
    }
    if ( scope.equals( "this" ) ) {
      scope = currentClass;
    }

    ArrayList< Map< String, Param > > functionParamMaps = new ArrayList<>();
    Map< String, Param > params = null;
    // Check if the scope is a function parameter
    Map<Object, Map<String, Param>> functionDeclMap =
            functionParamTable.get( scope );
    if ( !Utils.isNullOrEmpty( functionDeclMap ) ) {
      functionParamMaps.addAll( functionDeclMap.values() );
    }
    Param p = null;
    for ( Map<String, Param> fpMap : functionParamMaps ) {
      p = fpMap.get( paramName );
      // FIXME -- How do we know we got the right one -- there may be others
      // in other functions.
      if ( p != null ) {
        break;
      }
    }

    String classNameWithScope = getClassNameWithScope( scope );
    if ( p == null ) {
      // Check if the className is known.
      params = paramTable.get( scope );

      // If the name is not in the table, make sure it's the scoped name.
      if ( functionParamMaps.isEmpty() && params == null && !Utils
              .valuesEqual( classNameWithScope, scope ) ) {
        if ( classNameWithScope != null || ( !lookOutsideClassData
                                             && complainIfNotFound && !Debug
                .errorOnNull( false,
                              "Error! Could not find a class definition for "
                              + scope + " when looking for member " + paramName
                              + ".", classNameWithScope ) ) ) {
          params = paramTable.get( classNameWithScope );
        }
      }
    }
    if ( p == null && params != null ) {
      p = params.get( paramName );
    }
    // If not in the table and an inner class, check enclosing class's scope.
    if ( p == null && isInnerClass( scope ) ) {
      String enclosingClassName = getEnclosingClassName( scope );
      if ( !Utils.isNullOrEmpty( enclosingClassName ) ) {
        p =
            lookupMemberByName( enclosingClassName, paramName, lookOutsideClassData,
                                complainIfNotFound && lookOutsideClassData );
      }
    }
    Class< ? > classForName = null;
    String classMaybeWithScope = classNameWithScope == null ? scope : classNameWithScope;
    if ( p == null && lookOutsideClassData ) {
      //if ( Utils.isNullOrEmpty( classNameWithScope ) ) {
      //  classNameWithScope = getClassNameWithScope( className );
      //}
      classForName =
          ClassUtils.getClassForName( classMaybeWithScope, paramName, getPackageName(),
                                      false );
      if ( classForName != null ) {
        Field field = ClassUtils.getField( classForName, paramName, true );
        if ( field != null ) {
          p = new Param( paramName, ClassUtils.toString( field.getType() ),
                         null, classForName.getCanonicalName().replaceAll("<.*>", "") );
        }
      }
    }

    // FIXME -- TODO -- Superclasses should be explored before enclosing classes!!!
    if ( p == null ) {
      ClassOrInterfaceDeclaration clsDecl = this.getClassDeclaration(scope);
      if ( clsDecl != null ) {
        List<ClassOrInterfaceType> superclasses =
                clsDecl.getExtends();
        if ( superclasses != null ) {
          for (ClassOrInterfaceType ciType : superclasses) {
            String superclassName = ciType.toString();
            p = lookupMemberByName( superclassName, paramName, lookOutsideClassData,
                                        complainIfNotFound && lookOutsideClassData );
            if ( p != null ) break;
          }
        }
      }
    }

    if ( Debug.isOn() ) Debug.outln( "lookupMemberByName( className="
                                     + scope + ", paramName=" + paramName
                                     + ") returning " + p );
    if ( p == null && complainIfNotFound ) {
      Debug.errorOnNull( false, "lookupMemberByName(" + scope + ", "
                                + paramName
                                + "): no parameter found\n  paramTable =\n"
                                + paramTable + "\n  enclosingClasses =\n"
                                + nestedToEnclosingClassNames, p );
    }
    return p;
  }

  public Param lookupCurrentClassMember( String name, boolean complainIfNotFound ) {
    return lookupMemberByName( currentClass, name, false, complainIfNotFound );
  }

  /**
   * @param memberName
   * @param lookOutsideClassDataForTypes
   * @param complainIfNotFound
   * @return a Param with the input member name for the current class
   */
  public Param lookupCurrentClassMember( String memberName,
                                          boolean lookOutsideClassDataForTypes,
                                          boolean complainIfNotFound ) {
    return lookupMemberByName( currentClass, memberName, lookOutsideClassDataForTypes,
                               complainIfNotFound );
  }

  public Map<String, List<Object>> lookupMethodByName( String className, String methodName,
                                                       boolean lookOutsideClassData,
                                                       boolean complainIfNotFound ) {
    Map<String, List<Object> > decls = new LinkedHashMap<>();
    if ( Debug.errorOnNull( complainIfNotFound, complainIfNotFound,
            "Passing null in lookupMethodByName(" + className
                    + ", " + methodName + ")", className, methodName ) ) {
      return decls;
    }
    if ( className.equals( "this" ) ) {
      className = currentClass;
    }
    // Check if the className is known.
    Map< String, Set< MethodDeclaration > > methods = methodTable.get(className);
    // If the name is not in the table, make sure it's the scoped name.
    String classNameWithScope = null;
    if ( methods == null ) {
      classNameWithScope = getClassNameWithScope( className );
      if ( classNameWithScope != null
              || ( !lookOutsideClassData && complainIfNotFound && !Debug.errorOnNull( false,
              "Error! Could not find a class definition for "
                      + className
                      + " when looking for member "
                      + methodName
                      + ".",
              classNameWithScope ) ) ) {
        // if ( Utils.isNullOrEmpty( classNameWithScope ) ) {
        methods = methodTable.get( classNameWithScope );
      }
    }
    //Param p = null;
    Set<MethodDeclaration> p = null;
    //Object decl = null;
    if ( methods != null ) {
      p = methods.get( methodName );
      if ( !Utils.isNullOrEmpty(p) ) {
        decls.put( classNameWithScope != null ? classNameWithScope : className,
                   Utils.asList(p, Object.class) );
      }
    }

    // check superclasses
    if ( decls.isEmpty() ) {
      ClassOrInterfaceDeclaration clsDecl = this.getClassDeclaration(className);
      if ( clsDecl != null ) {
        List<ClassOrInterfaceType> superclasses =
                clsDecl.getExtends();
        if ( superclasses != null ) {
          for (ClassOrInterfaceType ciType : superclasses) {
            String superclassName = ciType.toString();
            decls = lookupMethodByName(superclassName, methodName, lookOutsideClassData,
                    complainIfNotFound && lookOutsideClassData);
            if ( decls != null && !decls.isEmpty() ) break;
          }
        }
      }
    }

    // If not in the table and an inner class, check enclosing class's scope.
    if ( Utils.isNullOrEmpty( decls ) && isInnerClass( className ) ) {
      String enclosingClassName = getEnclosingClassName( className );
      if ( !Utils.isNullOrEmpty( enclosingClassName ) ) {
        decls = lookupMethodByName( enclosingClassName, methodName, lookOutsideClassData,
                        complainIfNotFound && lookOutsideClassData );
      }
    }
    Class< ? > classForName = null;
    if ( Utils.isNullOrEmpty( decls ) && lookOutsideClassData ) {
      classForName =
              ClassUtils.getClassForName( className, methodName, getPackageName(),
                      false );
      if ( classForName != null ) {
        Method[] m = ClassUtils.getMethodsForName( classForName, methodName );
        if ( m != null && m.length > 0 ) {
          decls.put(classForName.getCanonicalName(), Utils.arrayAsList( m, Object.class ) );
        }
      }
    }

    if ( Debug.isOn() ) Debug.outln( "lookupMethodByName( className="
            + className + ", paramName=" + methodName
            + ") returning " + decls );
    if ( Utils.isNullOrEmpty( decls ) && complainIfNotFound ) {
      Debug.errorOnNull( false, "lookupMethodByName(" + className + ", "
              + methodName
              + "): no parameter found\n  methodTable =\n"
              + paramTable + "\n  enclosingClasses =\n"
              + nestedToEnclosingClassNames, p );
    }
    return decls;
  }

  public String getClassNameForMethodDeclaration(
          MethodDeclaration mDecl ) {
    if ( mDecl == null ) return null;
    for ( Map.Entry<String, Map<String, Set<MethodDeclaration>>> e : getMethodTable().entrySet() ) {
      Map<String, Set<MethodDeclaration>> m1 = e.getValue();
      Set<MethodDeclaration> s = m1.get( mDecl.getName() );
      if ( s != null && s.contains( mDecl ) ) {
        return e.getKey();
      }
    }
    return null;
  }
  public ClassOrInterfaceDeclaration getClassForMethodDeclaration(
          MethodDeclaration mDecl ) {
    String clsName = getClassNameForMethodDeclaration( mDecl );
    if ( !Utils.isNullOrEmpty( clsName ) ) {
      ClassOrInterfaceDeclaration cdecl = getClassDeclaration( clsName );
      return cdecl;
    }
    return null;
  }



  public Set< String > getAllEnclosingClassNames(String className ) {
    Set<String> all = new LinkedHashSet<>();
    String encloser = className;
    int ct = 0;
    while (true) {
      encloser = getEnclosingClassName( encloser );
      if ( encloser == null || all.contains( encloser) ) {
        break;
      }
      all.add( encloser );
      ++ct;
      if ( ct > 10000 ) {
        Debug.error(true, true, "How can there be 10000 enclosing classes?!");
        break;
      }
    }
    return all;
  }

  /**
   * Gets the name of the class enclosing the class indicated by className
   * @param className (fully qualified) name of the class of which to find the encloser
   * @return the class within which the class with the input name is declared or
   *         null if there is no such class
   */
  public String getEnclosingClassName( String className ) {
      // TODO: might be able to change this to just a simple string op since we're using fully qualified names now
      if ( className == null ) return null;
      String[] parsedNames;
      String enclosingClassName;
//      if (className.contains(".")) {
//        parsedNames = className.split("[.]");
//        className = parsedNames.length > 0 ? parsedNames[parsedNames.length - 1] : parsedNames[0];
//      }
      enclosingClassName = nestedToEnclosingClassNames.get( className );
      if ( !Utils.isNullOrEmpty( enclosingClassName ) ) {
        if ( Debug.isOn() ) Debug.outln( "getEnclosingClassName(" + className + ") = "
                     + enclosingClassName );
        return enclosingClassName;
      }
      String scopedName = getClassNameWithScope( className );
      if ( !Utils.isNullOrEmpty( scopedName ) ) {
        if (!scopedName.equals(className)) {
          enclosingClassName = nestedToEnclosingClassNames.get(scopedName);
        } else {
          String descopedName = getClassNameWithoutScope( className );
          if ( !Utils.isNullOrEmpty( descopedName ) ) {
            enclosingClassName = nestedToEnclosingClassNames.get(descopedName);
          }
        }
  //      if ( !Utils.isNullOrEmpty( enclosingClassName ) ) {
  //        return true;
  //      }
      }
      if ( Debug.isOn() ) Debug.outln( "getEnclosingClassName(" + className + ") = "
                   + enclosingClassName );
      return enclosingClassName;
  //    if ( className == null ) return null;
  //    if ( nestedToEnclosingClassNames.containsKey( className ) ) {
  //      return nestedToEnclosingClassNames.get( className );
  //    }
  //    String scopedName = getClassNameWithScope( className );
  //    if ( scopedName == null ) return null;
  //    if ( !nestedToEnclosingClassNames.containsKey( scopedName ) ) {
  //      return nestedToEnclosingClassNames.get( scopedName );
  //    }
  ////    if ( scopedName != null ) {
  ////      int pos = scopedName.lastIndexOf( '.' );
  ////      if ( pos == -1 ) return null;
  ////    }
  //    return null;
    }

  /**
   * @param className
   * @return whether the class with the input name is declared as a static class
   */
  public boolean isClassStatic( String className ) {
    if ( isStatic( className ) ) return true;
    String scopedName = getClassNameWithScope( className );
    return isStatic( scopedName );
  }

  /**
   * @param className
   * @return whether the class with the input name is non-static class declared
   *         within another class
   */
  public boolean isInnerClass( String className ) {
    // TODO -- should have a ClassDeclaration stub class to collect this info.
    boolean is = (!knowIfClassIsStatic(className) || !isClassStatic( className )) && isNested( className );
    if ( Debug.isOn() ) Debug.outln( "ClassData.isInnerClass( " + className + ") = " + is );
    return is;
  }

  /**
   * @param memberName
   * @return whether the member with the input name is declared as static
   */
  public boolean isMemberStatic( String memberName ) {
    String className = currentClass;
    return isMemberStatic( className, memberName );
  }

  // TODO -- is generated Java for member getting the static tag?  
  /**
   * @param className
   * @param memberName
   * @return whether the member named memberName is declared in the class named
   *         className as static
   */
  public boolean isMemberStatic( String className, String memberName ) {
    String memberShortName = memberName;
    int pos = memberName.lastIndexOf( '.' ); 
    if ( pos >= 1 ) {
      className = memberName.substring( 0, pos );
      memberShortName = memberName.substring( pos+1 );
    }
    String tryName = className + "." + memberShortName;
    if ( isStatic( tryName ) ) return true;
    String scopedName = getClassNameWithScope( className );
    if ( !Utils.isNullOrEmpty( scopedName ) ) {
      tryName = scopedName + "." + memberShortName;
      if ( isStatic( tryName ) ) return true;
    }
    return false;
  }

  /**
   * @param className
   * @return whether the class with the input name is declared within another
   *         class
   */
  public boolean isNested( String className ) {
    String ecn = getEnclosingClassName( className );
    boolean is = !Utils.isNullOrEmpty( ecn );
    if ( Debug.isOn() ) Debug.outln( "isNested( " + className + ") = " + is + ": "
                 + ( is ? ecn : "" ) );
    return is;
  }

  /**
   * @param name
   * @return whether there is some element with the input name that is declared
   *         as static
   */
  public boolean isStatic( String name ) {
    if ( Utils.isNullOrEmpty( name ) ) return false;
    Boolean s = isStaticMap.get( name );
    return s != null && s;
  }

  /**
   * @param className
   * @return whether there is a record of whether the class with the input name
   *         is declared as static or not
   */
  public boolean knowIfClassIsStatic( String className ) {
    if ( knowIfStatic( className ) ) return true;
    String scopedName = getClassNameWithScope( className );
    return knowIfStatic( scopedName );
  }

  /**
   * @param name
   * @return whether there is a record of whether an element with the input name
   *         is declared as static or not
   */
  public boolean knowIfStatic( String name ) {
    if ( Utils.isNullOrEmpty( name ) ) return true;
    Boolean s = isStaticMap.get( name );
    return s != null;
  }

  /**
   * @return the isStaticMap
   */
  public Map< String, Boolean > getIsStaticMap() {
    return isStaticMap;
  }

  /**
   * @param isStaticMap the isStaticMap to set
   */
  public void setIsStaticMap( Map< String, Boolean > isStaticMap ) {
    this.isStaticMap = isStaticMap;
  }

  /**
   * @param className
   * @return constructors for the class with the given className that is defined
   *         in the XML.
   */
  public Set< ConstructorDeclaration > getConstructors( String className ) {
    ClassOrInterfaceDeclaration classDecl = getClassDeclaration( className );
    if ( classDecl == null ) return null;
    if ( classDecl.getMembers() == null ) return null;
    Set< ConstructorDeclaration > s = new TreeSet<ConstructorDeclaration>(new CompareUtils.GenericComparator< ConstructorDeclaration >());
    for ( BodyDeclaration m : classDecl.getMembers() ) {
      if ( m instanceof ConstructorDeclaration ) {
        if ( !s.contains( m ) ) {
          s.add( (ConstructorDeclaration)m );
        }
      }
    }
    return s;
  }

  /**
   * Look for a class declaration of a particular name nested inside another class declaration.
   * @param className (fully qualified) name of class to be found
   * @param classDecl ClassOrInterfaceDeclaration object within which to look
   * @return a matching ClassOrInterfaceDeclaration object
   */
  public static ClassOrInterfaceDeclaration getClassDeclaration( String className,
                                                                 ClassOrInterfaceDeclaration classDecl ) {
    // First check and see if this is "the one."
    // only need to check simple name; this gets called by getClassDeclaration(String) which will find the enclosing class
    // using a fully qualified name. This will only search within an enclosing class
    if ( classDecl.getName().equals( ClassUtils.simpleName(className) ) ) {
      return classDecl;
    } else {
      // Now check nested classes.
      if ( classDecl != null && classDecl.getMembers() != null ) {
        // iterate through nested declarations
        for ( BodyDeclaration bd : classDecl.getMembers() ) {
          if ( bd instanceof ClassOrInterfaceDeclaration ) {
            ClassOrInterfaceDeclaration nestedClassDecl = (ClassOrInterfaceDeclaration)bd;

            // will likely only recurse one level because getEnclosingClass from getDeclaration(String) only goes up one level
            nestedClassDecl = getClassDeclaration( className, nestedClassDecl );
            if ( nestedClassDecl != null ) return nestedClassDecl;
          }
        }
      }
    }
    return null;
  }

  /**
   * Look for a class declaration of a particular name within a certain compilation unit
   * @param className (fully qualified) name of class to be found
   * @param cu compilation unit within which to look
   * @return ClassOrInterfaceDeclaration object matching with the given class name
   */
  public static ClassOrInterfaceDeclaration getClassDeclaration( String className,
                                                          CompilationUnit cu ) {
    if ( cu == null || cu.getTypes() == null ) return null;
    // iterate through the types in the compilation unit
    for ( TypeDeclaration t : cu.getTypes() ) {
      if ( t instanceof ClassOrInterfaceDeclaration ) {
        // look inside each ClassOrInterface object for the given class name
        ClassOrInterfaceDeclaration classDecl = 
            getClassDeclaration( className, (ClassOrInterfaceDeclaration)t );
        if ( classDecl != null ) return classDecl;
      }
    }
    return null;
  }

  /**
   * Look for a class declaration matching a particular name
   * @param className (fully qualified) name of class to be found
   * @return matching class declaration
   */
  public ClassOrInterfaceDeclaration getClassDeclaration( String className ) {
    ClassOrInterfaceDeclaration classDecl = null;

    // try to find a matching CompilationUnit - should probably only find one when className is "Global"
    CompilationUnit cu = getClasses().get( className );

    if ( cu == null ) { // didn't find one
      // go one level up to look for the enclosing class declaration
      String enclosingClassName = getEnclosingClassName( className );

      if ( !Utils.isNullOrEmpty( enclosingClassName ) ) {
        // recurse to find the enclosing class's declaration object. Will eventually get to Global, which is base case
        ClassOrInterfaceDeclaration enclosingDecl = getClassDeclaration( enclosingClassName );

        //once enclosing class declaration is found, look within it for className
        if ( enclosingDecl != null && enclosingDecl.getMembers() != null ) {
          classDecl = getClassDeclaration( className, enclosingDecl );
          return classDecl;
        }
      }

      return null; // should not reach here - means it couldn't find the enclosing class name
    }

    // base case for Global - look in compilation unit
    classDecl = getClassDeclaration( className, cu );

    // look in other compilation units
    if ( classDecl == null ) {
      for ( CompilationUnit cu2 : getClasses().values() ) {
        if ( cu == cu2 ) continue;
        classDecl = getClassDeclaration( className, cu );
        if ( classDecl != null ) {
          return classDecl;
        }
      }
    }
    return classDecl;
  }

  
  /**
   * @return the methodTable
   */
  public Map< String, Map< String, Set< MethodDeclaration >>> getMethodTable() {
    return methodTable;
  }

  /**
   * @param methodTable the methodTable to set
   */
  public
      void
      setMethodTable( Map< String, Map< String, Set< MethodDeclaration >>> methodTable ) {
    this.methodTable = methodTable;
  }

  /**
   * @return the nestedToEnclosingClassNames
   */
  public Map< String, String > getNestedToEnclosingClassNames() {
    return nestedToEnclosingClassNames;
  }

  /**
   * @param nestedToEnclosingClassNames the nestedToEnclosingClassNames to set
   */
  public
      void
      setNestedToEnclosingClassNames( Map< String, String > nestedToEnclosingClassNames ) {
    this.nestedToEnclosingClassNames = nestedToEnclosingClassNames;
  }

  /**
   * @return the packageName
   */
  public String getPackageName() {
    return packageName;
  }

  /**
   * @param packageName the packageName to set
   */
  public void setPackageName( String packageName ) {
    this.packageName = packageName;
  }

  /**
   * @return the paramTable
   */
  public Map< String, Map<Object, Map< String, Param > > > getFunctionParamTable() {
    return functionParamTable;
  }

  /**
   * @return the paramTable
   */
  public Map< String, Map< String, Param >> getParamTable() {
    return paramTable;
  }

  /**
   * @param paramTable the paramTable to set
   */
  public void setParamTable( Map< String, Map< String, Param >> paramTable ) {
    this.paramTable = paramTable;
  }

  /**
   * @return the parameterMap
   */
  public Map< ClassData.Param, Parameter< ? >> getParameterMap() {
    return parameterMap;
  }

  /**
   * @param parameterMap the parameterMap to set
   */
  public void
      setParameterMap( Map< ClassData.Param, Parameter< ? >> parameterMap ) {
    this.parameterMap = parameterMap;
  }

  /**
   * @return the currentClass
   */
  public String getCurrentClass() {
    return currentClass;
  }

  /**
   * @param currentClass the currentClass to set
   */
  public void setCurrentClass( String currentClass ) {
    this.currentClass = currentClass;
  }

  /**
   * @return the aeClasses
   */
  public Map< String, ParameterListenerImpl > getAeClasses() {
    return aeClasses;
  }

  /**
   * @param aeClasses the aeClasses to set
   */
  public void setAeClasses( Map< String, ParameterListenerImpl > aeClasses ) {
    this.aeClasses = aeClasses;
  }

  /**
   * @return the currentAeClass
   */
  public ParameterListenerImpl getCurrentAeClass() {
    return currentAeClass;
  }

  /**
   * @param currentAeClass the currentAeClass to set
   */
  public void setCurrentAeClass( ParameterListenerImpl currentAeClass ) {
    this.currentAeClass = currentAeClass;
    setCurrentClass( ( currentAeClass == null ? null : currentAeClass.getName() ) );
  }

  public static String typeToParameterType( String type ) {
    if ( Utils.isNullOrEmpty( type ) ) {
      //type = "null";
    } else if ( type.toLowerCase().equals( "time" ) ) {
      type = "Long";
    } else if ( type.toLowerCase().equals( "duration" ) ) {
      type = "Long";
    } else {
      String classType = JavaToConstraintExpression.typeToClass( type );
      final String[] primClassesSame =
          new String[] { "Boolean", //"Character",
                         //"Byte", "Short", 
                         "Integer",
                         //"Long", "Float",
                         "Double",// "Void"
                         "String" };
      if ( Arrays.asList( primClassesSame ).contains( classType ) ) {
        type = classType;
      } else {
      //if ( classType.equals( type ) ) {
        type = "";
      }
      type = type + "Parameter";
    }
    return type;
  }

/**
 * @return the classes
 */
public Map< String, CompilationUnit > getClasses() {
    return classes;
}

/**
 * @return the names of the classes
 */
public Set<String> getClassNames() {
  // Note: this.paramTable is used because it is populated at the beginning.
  // this.methodTable could also be used. this.classes cannot be used since
  // it only contains classes processed so far.
  return paramTable.keySet();
}

public boolean isClassName( String name ) {
  if ( Utils.isNullOrEmpty( name ) ) return false;
  if ( getClassNames().contains( name ) ) return true;
  String classNameWithScope = getClassNameWithScope( name );
  if ( Utils.isNullOrEmpty( classNameWithScope ) ) return false;
  if ( getClassNames().contains( classNameWithScope ) ) return true;
  return false;
}

/**
 * @param classes the classes to set
 */
public void setClasses( Map< String, CompilationUnit > classes ) {
    this.classes = classes;
}

/**
 * @return the currentCompilationUnit
 */
public CompilationUnit getCurrentCompilationUnit() {
    return currentCompilationUnit;
}

/**
 * @param currentCompilationUnit the currentCompilationUnit to set
 */
public void setCurrentCompilationUnit( CompilationUnit currentCompilationUnit ) {
    this.currentCompilationUnit = currentCompilationUnit;
}

}
