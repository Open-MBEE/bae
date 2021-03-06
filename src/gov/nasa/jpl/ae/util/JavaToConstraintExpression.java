/**
 * 
 */
package gov.nasa.jpl.ae.util;

import gov.nasa.jpl.ae.event.Call;
import gov.nasa.jpl.ae.event.ConstructorCall;
import gov.nasa.jpl.ae.event.FunctionCall;
import gov.nasa.jpl.ae.event.Functions;
import gov.nasa.jpl.ae.event.Functions.Binary;
import gov.nasa.jpl.ae.event.Functions.SuggestiveFunctionCall;
import gov.nasa.jpl.ae.event.Functions.Unary;
import gov.nasa.jpl.ae.event.Parameter;
import gov.nasa.jpl.ae.event.ParameterListenerImpl;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.NameTranslator;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.Wraps;
import japa.parser.ASTParser;
import japa.parser.ParseException;
import japa.parser.ast.expr.*;
import japa.parser.ast.stmt.AssertStmt;
import japa.parser.ast.stmt.BlockStmt;
import japa.parser.ast.stmt.CatchClause;
import japa.parser.ast.stmt.DoStmt;
import japa.parser.ast.stmt.ExplicitConstructorInvocationStmt;
import japa.parser.ast.stmt.ExpressionStmt;
import japa.parser.ast.stmt.ForStmt;
import japa.parser.ast.stmt.ForeachStmt;
import japa.parser.ast.stmt.IfStmt;
import japa.parser.ast.stmt.LabeledStmt;
import japa.parser.ast.stmt.ReturnStmt;
import japa.parser.ast.stmt.Statement;
import japa.parser.ast.stmt.SwitchEntryStmt;
import japa.parser.ast.stmt.SwitchStmt;
import japa.parser.ast.stmt.SynchronizedStmt;
import japa.parser.ast.stmt.ThrowStmt;
import japa.parser.ast.stmt.TryStmt;
import japa.parser.ast.stmt.TypeDeclarationStmt;
import japa.parser.ast.stmt.WhileStmt;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import junit.framework.Assert;

/**
 * This class is a collection of utilities for taking Java expressions and
 * creating Parameters and Constraints for problem solving.
 */
public class JavaToConstraintExpression { // REVIEW -- Maybe inherit from ClassData?

//  // Map: longName -> parameter name -> Param
//  protected Map< String, Map< String, Param > > paramTable =
//      new TreeMap< String, Map< String, Param > >();
//
//  // Map: longName -> method name -> set of javaparser.MethodDeclarations
//  protected Map< String, Map< String, Set< MethodDeclaration > > > methodTable =
//      new TreeMap< String, Map< String, Set< MethodDeclaration > > >();
//
//  protected Map< String, String > nestedToEnclosingClassNames =
//      new TreeMap< String, String >();
////  protected String currentEnclosingClassName;
////  protected String currentScopedClassName;
//  protected Map< String, Boolean > isStaticMap = new TreeMap< String, Boolean >();
//  
//  /**
//   * The package for all of the classes.
//   */
//  protected String packageName = null;

  public static boolean initAllToNull = true;

  // This is for handling class names outside Java syntax.
  protected NameTranslator nameTranslator = new NameTranslator();

  //private String currentClass = null;

  private ClassData classData = new ClassData();

  //protected EventXmlToJava xmlToJava = null;
  
  public JavaToConstraintExpression( String packageName ) {
    // TODO -- shouldn't need this, but there is a remaining complication with
    // EventXmlToJava.getConstructors().
    //xmlToJava = eventXmlToJava;
    getClassData().setPackageName( packageName );
  }

  public static String operatorResultType( BinaryExpr.Operator o, String argType1,
                                     String argType2 ) {
    switch ( o ) {
      case or: // ||
      case and: // &&
      case equals: // ==
      case notEquals: // !=
      case less: // <
      case greater: // >
      case lessEquals: // <=
      case greaterEquals: // >=
        return "Boolean";
      case binOr: // |
      case binAnd: // &
      case xor: // ^
      case lShift: // <<
      case rSignedShift: // >>
      case rUnsignedShift: // >>>
      case plus: // +
      case minus: // -
      case times: // *
      case divide: // /
      case remainder: // %
      default:
        return ClassUtils.dominantType( argType1, argType2 );
    }
  }

  public static String
      operatorResultType( UnaryExpr.Operator operator, String argType ) {
    return argType;
  }

  public static BinaryExpr.Operator assignOpToBinaryOp( AssignExpr.Operator ao ) {
    // REVIEW -- pull this out and make it a static array;
    BinaryExpr.Operator bo = null;
    switch ( ao ) {
      case assign:
        System.err.println( "Error! Trying to translate an assignment with the '=' operator to a binary operator." );
        break; // leave null
      case plus:
        bo = BinaryExpr.Operator.plus;
        break;
      case minus:
        bo = BinaryExpr.Operator.minus;
        break;
      case star:
        bo = BinaryExpr.Operator.times;
        break;
      case slash:
        bo = BinaryExpr.Operator.divide;
        break;
      case and:
        bo = BinaryExpr.Operator.and;
        break;
      case or:
        bo = BinaryExpr.Operator.or;
        break;
      case xor:
        bo = BinaryExpr.Operator.xor;
        break;
      case rem:
        bo = BinaryExpr.Operator.remainder;
        break;
      case lShift:
        bo = BinaryExpr.Operator.lShift;
        break;
      case rSignedShift:
        bo = BinaryExpr.Operator.rSignedShift;
        break;
      case rUnsignedShift:
        bo = BinaryExpr.Operator.rUnsignedShift;
        break;
      default:
        System.err.println( "Error! Trying to translate an unknow assignment operator "
                            + ao + " to a binary operator." );
    }
    return bo;
  }
  
  public Call javaCallToEventFunction( String fName,
                                              Class<?> returnType,
                                              Vector<Object> arguments,
                                              Class<?>... argTypes) {
    return javaCallToEventFunction(getClassData(), fName, returnType, arguments, argTypes );
  }
  public static Call javaCallToEventFunction( ClassData classData,
                                              String fName,
                                              Class<?> returnType,
                                              Vector<Object> arguments,
                                              Class<?>... argTypes) {

    String[] packages = new String[]{"gov.nasa.jpl.view_repo.sysml"};
    return javaCallToCall(packages, fName, returnType, arguments, argTypes, classData );
  }

  public Call javaCallToCall( String[] packages,
                                     String fName,
                                     Class<?> returnType,
                                     Vector<Object> arguments,
                                     Class<?>[] argTypes) {
    return javaCallToCall( packages, fName, returnType, arguments, argTypes,
                           getClassData() );
  }
  public static Call javaCallToCall( String[] packages,
                                     String fName,
                                     Class<?> returnType,
                                     Vector<Object> arguments,
                                     Class<?>[] argTypes,
                                     ClassData classData ) {
    Class<?> cls = null;
    Method method = null;
    Call call = null;
    Constructor<?> constructor = null;
    
    // Search by memberName:
    cls = ClassUtils.getClassForName(null, fName,
                                     packages,  false);
    
    if (cls != null) {
    
      method = ClassUtils.getMethodForArgTypes( cls, fName, argTypes);
      
      if (method != null) {
        call = new FunctionCall(null, method, arguments, returnType);
      }
      else {
        if ( Debug.isOn() ) Debug.errln( "javaCallToEventFunction( " + fName +
                    "): no method found!" );
        return null;      
      }
    }
    // It must be a ConstructorCall:
    else {
      
      // Search by class:
      cls = ClassUtils.getClassForName(fName, null,
                                       packages,  false);
      if ( cls == null && classData != null && fName != null ) {
        String fName2 = classData.getImportedClassNameWithScope( fName );
        if ( !fName.equals( fName2 ) ) {
          cls = ClassUtils.getClassForName( fName2, null, packages, false );
        }
      }

      if ( cls == null ) {
        if ( Debug.isOn() ) Debug.errln( "javaCallToEventFunction( " + fName +
                 "): no class found!" );
          return null;
      }

      constructor = ClassUtils.getConstructorForArgTypes( cls, argTypes );
      
      if (constructor != null) {
        
        // Add a empty argument expressions if arguments size is 0, but
        // the constructor has more than one argument:
        // Could also check if they are not equal and deal with it more
        // intelligently
        int numConstructorArgs = constructor.getParameterTypes().length;
        if ( (arguments == null || arguments.size() == 0) && numConstructorArgs > 0) {
          
          Vector<Object> emptyArgs = new Vector<Object>();
          for(int i = 0; i < numConstructorArgs; ++i) {
            emptyArgs.add( emptyExpression );
          }
          
          call = new ConstructorCall( null, constructor, emptyArgs, returnType);
        }
        else {
          call = new ConstructorCall( null, constructor, arguments, returnType );
        }
      }
      else {
        if ( Debug.isOn() ) Debug.errln( "javaCallToEventFunction( " + fName +
            "): no constructor found!" );
        return null; 
      }
      
    }
    
    if ( returnType != null && (call.getReturnType() == null || !call.getReturnType().equals( returnType ) ) ) {
      call.setReturnType( returnType );
    }
    
    return call;
  }    
  
  public static String
      astUnaryOpToEventFunctionName( UnaryExpr.Operator operator ) {
    return "" + Character.toUpperCase( operator.toString().charAt( 0 ) )
           + operator.toString().substring( 1 );
  }

  public static < T, R > ConstructorCall
      javaUnaryOpToEventFunction( UnaryExpr.Operator operator, Class< ? > returnType  ) {
    if ( operator == null ) return null;
    return unaryOpNameToEventFunction( operator.toString(), returnType, true );
  }
  public static < T, R > ConstructorCall
      unaryOpNameToEventFunction( String fName, Class< ? > returnType, boolean complain ) {
    
    String op2func = unaryOperatorSymbolToFunctionName( fName );
    if ( op2func != null ) {
      fName = op2func;
    } else {
      fName = Utils.toCamelCase( fName );
    }
    
    Class< ? extends Unary< T, R > > cls = (Class< ? extends Unary< T, R >>)
        getFunctionClassOfType(fName, Unary.class);

    if ( cls == null ) {
        Debug.error( complain, complain, "javaUnaryOpToEventFunction( " + fName +
                     "): no function found!" );
        return null;
    }
    ConstructorCall ctorCall =
        new ConstructorCall( null, cls, new Object[] { emptyExpression }, returnType );
    return ctorCall;
    
  }

    protected static List< Class< ? extends FunctionCall > > functionClasses =
            null;
    /**
     * @return a list of built-in, common FunctionCall classes. 
     */
    public static List< Class< ? extends FunctionCall > > getFunctionClasses() {
        if ( functionClasses != null ) return functionClasses;
        functionClasses = new ArrayList< Class< ? extends FunctionCall > >();
        Class< ? >[] classes = Functions.class.getClasses();
        for ( Class< ? > cls : classes ) {
//            System.out.println("cls = " + cls );
            if ( cls != null && FunctionCall.class.isAssignableFrom( cls ) ) {
                @SuppressWarnings( "unchecked" )
                Class< ? extends FunctionCall > fcls =
                        (Class< ? extends FunctionCall >)cls;
                functionClasses.add( fcls );
            }
        }
        return functionClasses;
    }

    public static String binaryOperatorSymbolToFunctionName( String op ) {
      int length = op.trim().length();
      char first = length >= 1 ? op.trim().charAt( 0 ) : 0;
      char second = length >= 2 ? op.trim().charAt( 1 ) : 0;
      if ( length == 2 ) {
        switch ( first ) {
          case '&':
            if ( second == '&' ) return "And";
            return null;
          case '|':
            if ( second == '|' ) return "Or";
            return null;
          case '=':
            if ( second == '=' ) return "Equals";
            if ( second == '~' ) return "RegexMatch"; //TODO 
            return null;            
          case '<':
            if ( second == '=' ) return "LTE";
            if ( second == '>' ) return "NEQ";
            return null;
          case '>':
            if ( second == '=' ) return "GTE";
            return null;
          case '!':
            if ( second == '=' ) return "NEQ";
            return null;  
          default:
            // unknown
            return null;
        }
      }
      if ( length == 1 ) {
        switch ( first ) {
          case '+':
            return "Plus";
          case '-':
            return "Minus";
          case '*':
            return "Times";
          case '/':
            return "DividedBy";
          case '&':
            return "And"; // bitwise?
          case '|':
            return "Or"; // bitwise?
          case '^':
            return "Xor"; // bitwise?
          case '%':
            return "Mod";
          case '<':
            return "LT";
          case '>':
            return "GT";
          case '=':
            return "EQ";
          default:
            // unknown
            return null;
        }
      }
      return null;
    }
    
    public static String unaryOperatorSymbolToFunctionName( String op ) {
      int length = op.trim().length();
      char first = length >= 1 ? op.trim().charAt( 0 ) : 0;
      char second = length >= 2 ? op.trim().charAt( 1 ) : 0;
      if ( length == 2 ) {
        switch ( first ) {
          case '+':
            if ( second == '+' ) return "Increment"; // TODO -- add to Functions.java
            return null;
          case '-':
            if ( second == '-' ) return "Decrement"; // TODO -- add to Functions.java
            return null;
          default:
            // unknown
            return null;
        }
      }
      if ( length == 1 ) {
        switch ( first ) {
          case '+':
            return "Noop"; // TODO -- add to Functions.java
          case '-':
            return "Negative";
          case '!':
            return "Not";
          default:
            // unknown
            return null;
        }
      }
      return null;
    }
    
  // \([A-Za-z]*\), //\(.*\)
  // case \1: // \2
    public static < T, R > Class< ? extends Binary< T, R > > 
      binaryOpNameToFunctionClass( String opName ) {
        Class< ? extends Binary > foo = 
                getFunctionClassOfType( opName, Binary.class );;
        return (Class< ? extends Binary< T, R > >)foo ;
    }
     
    public static < T, R > Class< ? > functionNameToFunctionClass( String opName ) {
        Class< ? > foo = getFunctionClassOfType( opName, SuggestiveFunctionCall.class );;
        return foo;
    }
   
    public static < T > Class< ? extends T >
            getFunctionClassOfType( String opName, Class< T > type ) {
      
        if ( Utils.isNullOrEmpty( opName ) ) return null;
        
        Class< ? extends T > cls = null;
        
        String newOpName = binaryOperatorSymbolToFunctionName( opName );
        if ( newOpName != null ) opName = newOpName;
        
        String fName = opName;
        
        // Capitalize the first character of the fName:
        fName = "" + Character.toUpperCase( fName.toString().charAt( 0 ) )
                        + fName.toString().substring( 1 ).replaceAll( "[$]", "" );
                
        try {
          
            // See if is a class in Functions.java:
            for ( Class< ? extends FunctionCall > fcls : getFunctionClasses() ) {
                if ( ( type == null || type.isAssignableFrom( fcls ) ) && 
                     fcls.getSimpleName().equalsIgnoreCase( fName ) ) {
                    @SuppressWarnings( "unchecked" )
                    Class< ? extends T > tcls = (Class< ? extends T >)fcls;
                    return tcls;
                }
            }
          
            String[] packages = new String[]{"gov.nasa.jpl.ae.event"};
            @SuppressWarnings( "unchecked" )
            Class< ? extends T > foo = (Class< ? extends T >)
                                          ClassUtils.getClassForName( fName, null, 
                                                                      packages, false );

          if ( foo != null && ( type == null || type.isAssignableFrom( foo ) ) &&
               foo.getSimpleName().equalsIgnoreCase( fName ) ) {
            cls = foo;
          }

            // If class was not found yet, then search some more:
            if ( cls == null) {
              
                @SuppressWarnings( "unchecked" )
                Class< ? extends T > foo2 = (Class< ? extends T >)
                                              ClassUtils.getClassForName( "Functions." + fName,
                                                                          null,
                                                                          "gov.nasa.jpl.ae.event",
                                                                           false );
              if ( foo2 != null && ( type == null || type.isAssignableFrom( foo2 ) ) &&
                   foo2.getSimpleName().equalsIgnoreCase( fName ) ) {
                cls = foo2;
              }
            }
          } catch ( ClassCastException e ) {
            e.printStackTrace();
          }
        
        return cls;
    }
  
    public static String
            javaBinaryOpToEventFunctionName( BinaryExpr.Operator operator ) {
        if ( operator == null ) return null;
        Class< ? extends Binary< ?, ? > > cls = 
                binaryOpNameToFunctionClass( operator.toString() );
        if ( cls == null ) return null;
        return cls.getSimpleName();
    }  

  static final Class< gov.nasa.jpl.ae.event.Expression > aeExprCls =
      gov.nasa.jpl.ae.event.Expression.class;

  public static < T, R > ConstructorCall javaBinaryOpToEventFunction( BinaryExpr.Operator operator, Class<?> returnType ) {
      //String fName = javaBinaryOpToEventFunctionName( operator );
      if ( operator == null ) return null;
      return binaryOpNameToEventFunction( operator.toString(), returnType );
  }
  public static < T, R > ConstructorCall
      binaryOpNameToEventFunction( String fName, Class< ? > returnType  ) {
    Class< ? extends Functions.Binary< T, R > > cls = null;
    cls = binaryOpNameToFunctionClass( fName );
    if ( cls == null ) {
        if ( Debug.isOn() ) {
          Debug.error( "javaBinaryOpToEventFunction( " + fName +
                       "): no function found!" );
        }
        return null;
    }
    ConstructorCall ctorCall =
        new ConstructorCall( null, cls, new Object[] { emptyExpression,
                                                       emptyExpression },
                                                       returnType );
    return ctorCall;
  }

  public static gov.nasa.jpl.ae.event.Expression< Object > emptyExpression =
          new gov.nasa.jpl.ae.event.Expression< Object >( (Object)null );  
  public static < T, R > ConstructorCall getIfThenElseConstructorCall(Class<?> returnType) {
    Class< Functions.Conditional > cls = Functions.Conditional.class;
    ConstructorCall ctorCall =
        new ConstructorCall( null, cls, new Object[] { emptyExpression, 
                                                       emptyExpression,
                                                       emptyExpression },
                                                       returnType );
    return ctorCall;
  }
  public static ConstructorCall getIsInstanceOfConstructorCall() {
    Class< Functions.IsInstanceOf > cls = Functions.IsInstanceOf.class;
    ConstructorCall ctorCall =
            new ConstructorCall( null, cls, new Object[] { emptyExpression,
                                                                  emptyExpression },
                                 Boolean.class );
    return ctorCall;
  }
  public static ConstructorCall getCastConstructorCall() {
    Class< Functions.Cast > cls = Functions.Cast.class;
    ConstructorCall ctorCall =
            new ConstructorCall( null, cls, new Object[] { emptyExpression,
                                                                  emptyExpression },
                                 Object.class );
    return ctorCall;
  }

  public static ConstructorCall getArgMinMaxConstructorCall(String fName, int numArgs, Class<?> returnType) {
    Class< ? > cls = null;
    if ( fName.toLowerCase().equals( "argmin" ) ) {
      cls = Functions.ArgMin.class;
    } else if ( fName.toLowerCase().equals( "argmax" ) ) {
      cls = Functions.ArgMax.class;
    } else {
      return null;
    }
    Object[] args = new Object[numArgs];
    for ( int i = 0; i < numArgs; ++i ) {
      args[i] = emptyExpression;
    }
    ConstructorCall ctorCall =
        new ConstructorCall( null, cls, args, returnType );
    return ctorCall;
  }

  /**
   * Translate the input type/class name to the corresponding non-primitive
   * Class name. The case of the letters is largely ignored, and some
   * non-standard type names are considered: for example, typeToClass("real")
   * returns "Float" even though "real" is not a Java type.
   * 
   * @param type
   * @return the name of the non-primitive Class that corresponds to the input
   *         type name.
   * 
   */
  public static String typeToClass( String type ) {
    
    if ( Utils.isNullOrEmpty( type ) ) {
      return type;
    }
    // check for generic parameters
    Pattern p = Pattern.compile("([A-Za-z_][A-Za-z0-9._]*\\s*)<(.*)>(\\s*)");
    Matcher m = p.matcher(type);
    if ( m.matches() && m.groupCount() > 1 ) {
      String t1 = typeToClass(m.group(1));
      String t2 = typeToClass(m.group(2));
      String t3 = m.groupCount() > 2 && m.group(3) != null ? m.group(3) : "";
      return t1 + "<" + t2 + ">" + t3;
    }

    // convert types to Class equivalents
    // TODO -- REVIEW -- other than "time", shouldn't this be in ClassUtils?
    if ( type.toLowerCase().equals( "time" )
        || type.toLowerCase().startsWith( "duration" ) ) {
      type = "Time";
    } else if ( type.toLowerCase().startsWith( "long" ) ) {
      type = "Long";
    } else if ( type.toLowerCase().startsWith( "int" )
                || type.toLowerCase().startsWith( "integer" ) ) {
      type = "Integer";
    } else if ( type.toLowerCase().equals( "double" )
                || type.toLowerCase().startsWith( "real" ) ) {
      type = "Double";
    } else if ( type.toLowerCase().startsWith( "float" ) ) {
      type = "Float";
    } else if ( type.toLowerCase().equals( "boolean" )
                || type.toLowerCase().equals( "bool" ) ) {
      type = "Boolean";
    } else if ( type.equals( "string" ) ) {
      type = "String";
    }
    
    return type;
  }

  public static String classToPrimitive( String type ) {
    
    if ( Utils.isNullOrEmpty( type ) ) {
      return type;
    }
    int pos = type.lastIndexOf( "Parameter" );
    if ( pos >= 0 ) {
      type = type.substring( 0, pos );
    }
    Class<?> c = null;
    try {
      c = Class.forName( type );
    } catch ( ClassNotFoundException e ) {
    }
    if ( c != null ) {
      if ( c.isPrimitive() ) {
        return c.getSimpleName().replace( '$', '.' );
      }
      type = c.getSimpleName().replace( '$', '.' );
    }
    final String[] primClassesSame =
        new String[] { "Boolean", //"Character",
                       "Byte", "Short", //"Integer",
                       "Long", "Float", "Double", "Void" };
    if ( Arrays.asList( primClassesSame ).contains( type ) ) {
      return type.toLowerCase();
    }
    if ( type.toLowerCase().equals( "time" )
         || type.toLowerCase().equals( "timepoint" )
         || type.toLowerCase().equals( "duration" ) ) {
      return "long";
    }
    if ( type.toLowerCase().equals( "integer" ) ) {
      return "int";
    }
    if ( type.toLowerCase().equals( "real" ) ) {
      return "double";
    }
    if ( type.toLowerCase().equals( "character" ) ) {
      return "char";
    }
    return type;
  }

  public static String constructorStringOfGenericType(String name, String typeName,
                                                      String parameterTypeName,
                                                      String constructorArgs ) {
    StringBuffer ctorString = new StringBuffer();
    if (constructorArgs == null) {
      ctorString.append("null;");
    } else {
      ctorString.append("new " + typeName);
      if (!Utils.isNullOrEmpty(parameterTypeName)) {
        ctorString.append(
                "< " + ClassUtils.getNonPrimitiveClassName(parameterTypeName) +
                " >");
      }
      ctorString.append("( " + constructorArgs + " );");
    }
    return ctorString.toString();
  }

  public String getConstructorString( ClassData.Param p ) {
    String args[] = convertToEventParameterTypeAndConstructorArgs( p, null );
    String s = constructorStringOfGenericType(p.name, args[ 0 ],
                                              args[ 1 ], args[ 2 ] );
    return s;
  }

  public String astToAeExpr( Expression expr,
                             boolean convertFcnCallArgsToExprs,
                             boolean lookOutsideClassDataForTypes,
                             boolean complainIfDeclNotFound ) {
    return astToAeExpr( expr, null, convertFcnCallArgsToExprs,
                        lookOutsideClassDataForTypes, complainIfDeclNotFound );
  }

  public String astToAeExpr( Expression expr, String type,
                             boolean convertFcnCallArgsToExprs,
                             boolean lookOutsideClassDataForTypes,
                             boolean complainIfDeclNotFound ) {
    return astToAeExpr( expr, type, convertFcnCallArgsToExprs,
                        lookOutsideClassDataForTypes, false, complainIfDeclNotFound, false );
  }

  public String astToAeExpr( Expression expr, String type,
                             boolean convertFcnCallArgsToExprs,
                             boolean lookOutsideClassDataForTypes,
                             boolean getParameterValue,
                             boolean complainIfDeclNotFound,
                             boolean evaluateCall ) {
    if ( expr == null ) return null;

    Class<?> returnType = null;  // TODO?
    String returnTypeString = "(Class<?>)null";
    
    type = JavaToConstraintExpression.typeToClass( type );
    if ( Utils.isNullOrEmpty( type ) ) {
      type = astToAeExprType( expr, null,
                              lookOutsideClassDataForTypes, complainIfDeclNotFound );
    }
    // Get non-primitive class name and remove .class suffix for type parameter
    String classlessType = type;
    if ( !Utils.isNullOrEmpty( type ) ) {
      classlessType = Utils.replaceSuffix( type, ".class", "" );
      boolean removedSuffix = classlessType.length() < type.length();
      classlessType = ClassUtils.getNonPrimitiveClassName( classlessType );
      if ( removedSuffix ) {
        type = classlessType + ".class";
      } else {
        type = classlessType;
      }
    }

    // TimeVaryingFunctionCalls may have TimeVaryingMap args where the method
    // expects another type, so we don't specify the type of expression to avoid
    // compile errors.
    boolean isTimeVarying = classlessType != null && classlessType.contains("TimeVarying");

    String classlessTypeWithScope = getClassData().getClassNameWithScope(classlessType);

    final String prefix =
        "new Expression" + ( isTimeVarying || Utils.isNullOrEmpty( classlessTypeWithScope ) ? "" : "<" + classlessTypeWithScope + ">" ) + "((Object)";
    final String suffix = " )";
    String middle = null;
    /*** BinaryExpr ***/
    if ( expr.getClass() == BinaryExpr.class ) {
        BinaryExpr be = ( (BinaryExpr)expr );
        middle =
          "new Functions."
               + JavaToConstraintExpression.javaBinaryOpToEventFunctionName( be.getOperator() ) + "( "
               + astToAeExpr( be.getLeft(), true,
                              lookOutsideClassDataForTypes,
                              complainIfDeclNotFound ) + ", "
               + astToAeExpr( be.getRight(), 
                              true,
                              lookOutsideClassDataForTypes,
                              complainIfDeclNotFound)  + " )";
//        if ( !convertFcnCallArgsToExprs ) {
//          middle = "(" + middle + ").functionCall";
//        }
        if ( evaluateCall ) {
          middle = "Expression.evaluate(" + middle + ", null, true)";
        }
    } else
    /*** UnaryExpr ***/
    if ( expr.getClass() == UnaryExpr.class ) {
        UnaryExpr ue = ( (UnaryExpr)expr );
        middle = "new Functions."
                 + JavaToConstraintExpression.astUnaryOpToEventFunctionName( ue.getOperator() ) + "( "
                 + astToAeExpr( ue.getExpr(), type,
                                true, lookOutsideClassDataForTypes,
                                complainIfDeclNotFound ) + " )";
//        if ( !convertFcnCallArgsToExprs ) {
//          middle = "(" + middle + ").functionCall";
//        }
        if ( evaluateCall ) {
          middle = "Expression.evaluate(" + middle + ", null, true)";
          //middle = "(" + middle + ").evaluate(true)";
        }
    } else
    /*** ConditionalExpr ***/
    if ( expr.getClass() == ConditionalExpr.class ) {
      ConditionalExpr be = ( (ConditionalExpr)expr );
      middle = "new Functions.Conditional(" + 
               astToAeExpr( be.getCondition(), true, lookOutsideClassDataForTypes, complainIfDeclNotFound ) + ", " + 
               astToAeExpr( be.getThenExpr(), true, lookOutsideClassDataForTypes, complainIfDeclNotFound ) + ", " +
               astToAeExpr( be.getElseExpr(), true, lookOutsideClassDataForTypes, complainIfDeclNotFound ) + " ) ";
      if ( evaluateCall ) {
        middle = "Expression.evaluate(" + middle + ", null, true)";
        //middle = "(" + middle + ").evaluate(true)";
      }
    } else
    /*** EnclosedExpr ***/
    if ( expr.getClass() == EnclosedExpr.class ) {
        middle =
            astToAeExpr( ( (EnclosedExpr)expr ).getInner(), type,
                         convertFcnCallArgsToExprs, lookOutsideClassDataForTypes,
                         complainIfDeclNotFound);
      /*** CastExpr ***/
    } else if ( expr.getClass() == CastExpr.class ) {
      CastExpr ce = (CastExpr)expr;
      middle = "new Functions.Cast(" +
               astToAeExpr( ce.getExpr(), true, lookOutsideClassDataForTypes, complainIfDeclNotFound ) + ", " +
               ce.getType().toString() + ".class ) ";
      if ( evaluateCall ) {
        middle = "Expression.evaluate(" + middle + ", null, true)";
      }
//      middle =
//              astToAeExpr( ce.getExpr(), type,
//                           convertFcnCallArgsToExprs, lookOutsideClassDataForTypes,
//                           complainIfDeclNotFound);
    /*** InstanceOfExpr ***/
    } else if ( expr.getClass() == InstanceOfExpr.class ) {
      InstanceOfExpr ioe = (InstanceOfExpr)expr;
      middle = "new Functions.IsInstanceOf(" +
               astToAeExpr( ioe.getExpr(), true, lookOutsideClassDataForTypes, complainIfDeclNotFound ) + ", " +
               ioe.getType().toString() + ".class ) ";
      if ( evaluateCall ) {
        middle = "Expression.evaluate(" + middle + ", null, true)";
      }
      /*** NameExpr ***/
    } else if ( expr.getClass() == NameExpr.class ) {
      middle = nameExprToAe( (NameExpr)expr, true, evaluateCall, getParameterValue, true );
      if ( middle != null && middle.equals("time") ) {
        middle = "TimeVaryingMap.time";
      }
    /*** ThisExpr ***/
    } else if ( expr.getClass() == ThisExpr.class ) {
      middle = expr.toString(); // just "this", right?
    /*** FieldAccessExpr ***/
    } else if ( expr.getClass() == FieldAccessExpr.class ) {
      FieldAccessExpr fieldAccessExpr = (FieldAccessExpr)expr;
      middle = fieldExprToAe( fieldAccessExpr, lookOutsideClassDataForTypes,
                              complainIfDeclNotFound, true, evaluateCall,
                              getParameterValue, true );
    /*** AssignExpr ***/
    } else if ( expr.getClass() == AssignExpr.class ) {
        AssignExpr ae = (AssignExpr)expr;
        String result = null;
        ClassData.Param p = getClassData().lookupCurrentClassMember( ae.getTarget().toString(),
                                            lookOutsideClassDataForTypes, false );
        if ( ae.getOperator() == AssignExpr.Operator.assign ) {
          if ( p == null ) {
            result = ae.getTarget().toString() + " = "
                     + astToAeExpr( ae.getValue(), false, lookOutsideClassDataForTypes,
                                    complainIfDeclNotFound );
          } else {
            result =
                ae.getTarget().toString() + ".setValue( "
                    + astToAeExpr( ae.getValue(), convertFcnCallArgsToExprs,
                                   lookOutsideClassDataForTypes,
                                   complainIfDeclNotFound ) + " )";
          }
          return result;
        }
        if ( p != null ) {
          BinaryExpr abe = new BinaryExpr();
          abe.setLeft( ae.getTarget() );
          abe.setRight( ae.getValue() );
          abe.setOperator( JavaToConstraintExpression.assignOpToBinaryOp( ae.getOperator() ) );
          Assert.assertNotNull( abe.getOperator() );
          result =
              ae.getTarget().toString() + ".setValue( "
                  + astToAeExpr( abe, convertFcnCallArgsToExprs,
                                 lookOutsideClassDataForTypes,
                                 complainIfDeclNotFound ) + " )";
          return result;
        }
        middle = ae.toString();
    /*** MethodCallExpr ***/
    /*** ObjectCreationExpr ***/
    } else if ( expr.getClass() == MethodCallExpr.class ||
                expr.getClass() == ObjectCreationExpr.class ) {

      // try treating as a binary operation or other function in Functions.java
      middle = null;
      MethodCallExpr mx = expr.getClass() == MethodCallExpr.class ? (MethodCallExpr)expr : null;
      if ( mx != null && mx.getScope() == null &&
           ((MethodCallExpr)expr).getArgs() != null ) {//&& 
           //((MethodCallExpr)expr).getArgs().size() == 2 ) {        
        Class<?> fcnClass = null;
        if ( ((MethodCallExpr)expr).getArgs().size() == 2 ) {
          fcnClass = binaryOpNameToFunctionClass(mx.getName());
        }
        // See if this is a function like argmax.
        if (fcnClass == null) {// && ((MethodCallExpr)expr).getArgs().size() > 2 ) {
          fcnClass = functionNameToFunctionClass(mx.getName());
        }
        if ( fcnClass != null ) {
          StringBuilder sb = new StringBuilder();
          String fName = fcnClass.getSimpleName();
          sb.append( "new Functions." + fName + "( " );
          boolean first = true;
          for ( Expression a : mx.getArgs() ) {
            if ( first ) first = false;
            else sb.append( ", " );
            sb.append( astToAeExpr( a, true, lookOutsideClassDataForTypes,
                                    complainIfDeclNotFound ) );
          }
          sb.append( " )");
          middle = sb.toString();
//          if ( !convertFcnCallArgsToExprs ) {
//            middle = "(" + middle + ").functionCall";
//          }
          if ( evaluateCall ) {
            middle = "Expression.evaluate(" + middle + ", null, true)";
            // middle = "(" + middle + ").evaluate(true)";
          }
        }
      }

      // If not a built-in class in Functions, then create the call the old-fashioned way.
      if ( middle == null ) {
        JavaForFunctionCall javaForFunctionCall =
            new JavaForFunctionCall( this, expr, convertFcnCallArgsToExprs,
                                     getClassData().getPackageName() , evaluateCall, returnType );
        //if ( convertFcnCallArgsToExprs ) {
          middle = javaForFunctionCall.toNewFunctionCallString();
      }
//        } else {
//          if ( Utils.isNullOrEmpty( javaForFunctionCall.getScope() ) ) {
//          middle = javaForFunctionCall.getScope() + ".";
//          if (javaForFunctionCall.methodOrConstructor) {
//            middle += javaForFunctionCall.callName + "(" + javaForFunctionCall.argumentArrayJava + ")";
//          } else {
//            middle += "new " javaForFunctionCall.callName + "(" + javaForFunctionCall.argumentArrayJava + ")";            
//          }
//        }
    } else if ( expr.getClass().getSimpleName().endsWith( "LiteralExpr" ) ) {
      if ( expr.getClass() == NullLiteralExpr.class ) {
        return "null";
      } else {
        middle = expr.toString();
      }
    } else  { //if ( expr.getClass() == ConditionalCallExpr.class ) {
      //case "ConditionalExpr": // TODO
        middle = expr.toString();
    }
    if ( !convertFcnCallArgsToExprs ) {
      return middle;
    }
    if ( middle == null || middle.equals("null") ) {
      return "null";
    }
    return prefix + middle + suffix;
  }
  
  private static HashSet<String> someNumberTypes = new LinkedHashSet<String>( Utils.newList( "Long", "Double", "Float" ) );
  
  public Object //gov.nasa.jpl.ae.event.Expression< ? >
      astToAeExpression( Expression expr, boolean convertFcnCallArgsToExprs,
                         boolean lookOutsideClassDataForTypes,
                         boolean complainIfDeclNotFound ) {
    return astToAeExpression( expr, null, convertFcnCallArgsToExprs,
                              lookOutsideClassDataForTypes, complainIfDeclNotFound );
  }

  public Object //gov.nasa.jpl.ae.event.Expression< ? >
      astToAeExpression( Expression expr, String type,
                         boolean convertFcnCallArgsToExprs,
                         boolean lookOutsideClassDataForTypes,
                         boolean complainIfDeclNotFound ) {
    return astToAeExpression( expr, type, null,
                              convertFcnCallArgsToExprs, lookOutsideClassDataForTypes,
                              false,
                              complainIfDeclNotFound, false );
  }

  /**
   * @param expr
   * @param type
   * @param specifier
   *          a String representation of a member of the type to help
   *          disambiguate
   * @param convertFcnCallArgsToExprs
   * @param lookOutsideClassDataForTypes
   * @param complainIfDeclNotFound
   * @param evaluateCall
   * @return
   */
  public Object astToAeExpression( Expression expr,
                                   String type,
                                   String specifier, // TODO -- this is never used!!!!
                                   boolean convertFcnCallArgsToExprs,
                                   boolean lookOutsideClassDataForTypes,
                                   boolean getParameterValue,
                                   boolean complainIfDeclNotFound,
                                   boolean evaluateCall ) {
      if ( expr == null ) return null;
      
      Class< ? > returnType = null;
      
      type = JavaToConstraintExpression.typeToClass( type );
      if ( Utils.isNullOrEmpty( type ) ) {
        type = astToAeExprType( expr, specifier,
                                lookOutsideClassDataForTypes, complainIfDeclNotFound );
      }
      // Get non-primitive class name and remove .class suffix for type parameter
      String classlessType = type;
      if ( !Utils.isNullOrEmpty( type ) ) {
        classlessType = Utils.replaceSuffix( type, ".class", "" );
        boolean removedSuffix = classlessType.length() < type.length();
        classlessType = ClassUtils.getNonPrimitiveClassName( classlessType );
        if ( removedSuffix ) {
          type = classlessType + ".class";
        } else {
          type = classlessType;
        }
      }
      gov.nasa.jpl.ae.event.Expression<?> aeExpr = null;
      final String prefix =
          "new Expression" + ( Utils.isNullOrEmpty( classlessType ) ? "" : "<" + classlessType + ">" ) + "((Object)";
      final String suffix = " )";
      //String middle = null;
      /*** BinaryExpr ***/
      if ( expr.getClass() == BinaryExpr.class ) {
          BinaryExpr be = ( (BinaryExpr)expr );
          ConstructorCall call = javaBinaryOpToEventFunction( be.getOperator(), returnType  );
          Debug.errorOnNull( true, "A Functions class must exist for every Java binary operator", call );
          Vector< Object > args = new Vector< Object >();
          args.add(astToAeExpression( be.getLeft(), true,
                                      lookOutsideClassDataForTypes,
                                      complainIfDeclNotFound ) );
          args.add(astToAeExpression( be.getRight(), 
                                      true,
                                      lookOutsideClassDataForTypes,
                                      complainIfDeclNotFound ) );
          call.setArguments( args );
          if ( evaluateCall ) {
            try {
              aeExpr = new gov.nasa.jpl.ae.event.Expression( call.evaluate( true ) );
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
          } else {
            aeExpr = new gov.nasa.jpl.ae.event.Expression( call );
          }
          //return aeExpr;
      } else
      /*** UnaryExpr ***/
      if ( expr.getClass() == UnaryExpr.class ) {
          UnaryExpr ue = ( (UnaryExpr)expr );
          //          middle = "new Functions."
//                   + JavaToConstraintExpression.astUnaryOpToEventFunctionName( ue.getOperator() ) + "( "
//                   + astToAeExpr( ue.getExpr(), type,
//                                  true, lookOutsideClassDataForTypes,
//                                  complainIfDeclNotFound ) + " )";
//  //        if ( !convertFcnCallArgsToExprs ) {
//  //          middle = "(" + middle + ").functionCall";
//  //        }
//          if ( evaluateCall ) {
//            middle = "(" + middle + ").evaluate(true)"; 
//          }
          ConstructorCall call = javaUnaryOpToEventFunction( ue.getOperator(), returnType  );
          if ( call != null ) {
              Vector< Object > args = new Vector< Object >(1);
              args.add( astToAeExpression( ue.getExpr(), true,
                                     lookOutsideClassDataForTypes,
                                     complainIfDeclNotFound ) );
              call.setArguments( args );
              if ( evaluateCall ) {
                try {
                  aeExpr = new gov.nasa.jpl.ae.event.Expression( call.evaluate( true ) );
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
              } else {
                aeExpr = new gov.nasa.jpl.ae.event.Expression( call );
              }
          }
          //return aeExpr;
     } else
     /*** ConditionalExpr ***/
     if ( expr.getClass() == ConditionalExpr.class ) {
       ConditionalExpr be = ( (ConditionalExpr)expr );
       ConstructorCall call = getIfThenElseConstructorCall(returnType);
       Debug.errorOnNull( true, "A Functions class must exist for every Java binary operator", call );
       Vector< Object > args = new Vector< Object >();
       args.add(astToAeExpression( be.getCondition(), true,
                                   lookOutsideClassDataForTypes,
                                   complainIfDeclNotFound ) );
       args.add(astToAeExpression( be.getThenExpr(), 
                                   true,
                                   lookOutsideClassDataForTypes,
                                   complainIfDeclNotFound ) );
       args.add(astToAeExpression( be.getElseExpr(), 
                                   true,
                                   lookOutsideClassDataForTypes,
                                   complainIfDeclNotFound ) );
       call.setArguments( args );
       if ( evaluateCall ) {
         try {
          aeExpr = new gov.nasa.jpl.ae.event.Expression( call.evaluate( true ) );
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
       } else {
         aeExpr = new gov.nasa.jpl.ae.event.Expression( call );
       }
     } else
      /*** EnclosedExpr ***/
      if ( expr.getClass() == EnclosedExpr.class ) {
          Object o =
              astToAeExpression( ( (EnclosedExpr)expr ).getInner(), type,
                           convertFcnCallArgsToExprs, lookOutsideClassDataForTypes,
                           complainIfDeclNotFound);
      aeExpr =
          (gov.nasa.jpl.ae.event.Expression< ? >)( ( o instanceof gov.nasa.jpl.ae.event.Expression )
                                                   ? o
                                                   : new gov.nasa.jpl.ae.event.Expression( o ) );
          //return aeExpr;
      /*** CastExpr ***/
      } else if ( expr.getClass() == CastExpr.class ) {
        CastExpr ce = (CastExpr)expr;
//        Object o =
//            astToAeExpression( ce.getExpr(), type,
//                               convertFcnCallArgsToExprs, lookOutsideClassDataForTypes,
//                               complainIfDeclNotFound);
//        aeExpr =
//            (gov.nasa.jpl.ae.event.Expression< ? >)( ( o instanceof gov.nasa.jpl.ae.event.Expression )
//                                                 ? o
//                                                 : new gov.nasa.jpl.ae.event.Expression( o ) );
        Object o =
                astToAeExpression( ce.getExpr(),
                                   true, lookOutsideClassDataForTypes,
                                   complainIfDeclNotFound);
        String cStr = JavaToConstraintExpression.typeToClass( ce.getType().toString() );
        String c = getClassData().getClassNameWithScope( cStr );
        if ( c != null ) cStr = c;
        ConstructorCall call = getCastConstructorCall();
        Debug.errorOnNull( true, "A Functions class must exist for every Java binary operator", call );
        Vector< Object > args = new Vector< Object >();
        args.add( o );
        args.add( new gov.nasa.jpl.ae.event.Expression<String>( cStr ) );
        call.setArguments( args );
        if ( evaluateCall ) {
          try {
            aeExpr = new gov.nasa.jpl.ae.event.Expression( call.evaluate( true ) );
          } catch ( IllegalAccessException e ) {
          } catch ( InvocationTargetException e ) {
          } catch ( InstantiationException e ) {
          }
        } else {
          aeExpr = new gov.nasa.jpl.ae.event.Expression( call );
        }
      } else if ( expr.getClass() == InstanceOfExpr.class ) {
        InstanceOfExpr ioe = (InstanceOfExpr)expr;
        Object o =
                astToAeExpression( ioe.getExpr(),
                                   true, lookOutsideClassDataForTypes,
                                   complainIfDeclNotFound);
        String cStr = JavaToConstraintExpression.typeToClass( ioe.getType().toString() );
        String c = getClassData().getClassNameWithScope( cStr );
        if ( c != null ) cStr = c;
        ConstructorCall call = getIsInstanceOfConstructorCall();
        Debug.errorOnNull( true, "A Functions class must exist for every Java binary operator", call );
        Vector< Object > args = new Vector< Object >();
        args.add( o );
        args.add( new gov.nasa.jpl.ae.event.Expression<String>( cStr ) );
        call.setArguments( args );
        if ( evaluateCall ) {
          try {
            aeExpr = new gov.nasa.jpl.ae.event.Expression( call.evaluate( true ) );
          } catch ( IllegalAccessException e ) {
          } catch ( InvocationTargetException e ) {
          } catch ( InstantiationException e ) {
          }
        } else {
          aeExpr = new gov.nasa.jpl.ae.event.Expression( call );
        }
      /*** NameExpr ***/
      } else if ( expr.getClass() == NameExpr.class ) {
        aeExpr = nameExprToAeExpression( (NameExpr)expr, true, evaluateCall, false, getParameterValue, true, false );
        //return aeExpr;
      /*** ThisExpr ***/
      } else if ( expr.getClass() == ThisExpr.class ) {
        // REVIEW -- is this right?
        aeExpr =
          new gov.nasa.jpl.ae.event.Expression< ParameterListenerImpl >( getClassData().getCurrentAeClass(),
                                                                         ParameterListenerImpl.class );
        /*** FieldAccessExpr ***/
      } else if ( expr.getClass() == FieldAccessExpr.class ) {
        FieldAccessExpr fieldAccessExpr = (FieldAccessExpr)expr;
        aeExpr = fieldExprToAeExpression( fieldAccessExpr,
                                          convertFcnCallArgsToExprs,
                                          lookOutsideClassDataForTypes,
                                          complainIfDeclNotFound, true,
                                          evaluateCall, false, true );
      /*** AssignExpr ***/
      } else if ( expr.getClass() == AssignExpr.class ) {
          AssignExpr ae = (AssignExpr)expr;
          String result = null;
          ClassData.Param p = getClassData().lookupCurrentClassMember( ae.getTarget().toString(),
                                              lookOutsideClassDataForTypes, false );
//          if ( p == null ) {
//            p = getClassData().getParam( null, ae.getTarget().toString(), lookOutsideClassDataForTypes, true, true );
//          }
          Parameter< Object > parameter =
            (Parameter< Object >)getClassData().getParameter( null,ae.getTarget().toString(),
                                                         lookOutsideClassDataForTypes,
                                                         true, true, complainIfDeclNotFound );
          if ( ae.getOperator() == AssignExpr.Operator.assign ) {
            Object value = astToAeExpression(ae.getValue(), false,
                                             lookOutsideClassDataForTypes,
                                             complainIfDeclNotFound);
            FunctionCall fc = new FunctionCall( parameter, Parameter.class, "assignValue", new Object[]{ value }, (Class<?>)null );
            aeExpr = new gov.nasa.jpl.ae.event.Expression( fc );
            //return aeExpr;
          } else
          if ( p != null ) {
            BinaryExpr abe = new BinaryExpr();
            abe.setLeft( ae.getTarget() );
            abe.setRight( ae.getValue() );
            abe.setOperator( JavaToConstraintExpression.assignOpToBinaryOp( ae.getOperator() ) );
            Assert.assertNotNull( abe.getOperator() );
            Object value =
                astToAeExpression( abe, convertFcnCallArgsToExprs,
                                   lookOutsideClassDataForTypes,
                                   complainIfDeclNotFound );
            FunctionCall fc =
                new FunctionCall( parameter, Parameter.class, "assignValue",
                                  new Object[] { value }, (Class<?>)null );
            aeExpr = new gov.nasa.jpl.ae.event.Expression( fc );
            //return aeExpr;
          }
          // TODO -- what happens here??!!
          //middle = ae.toString();
          if ( aeExpr == null && complainIfDeclNotFound ) {
            Debug.error( "Error! could not parse AssignExpr: " + ae );
          }
          //return null;
      /*** MethodCallExpr ***/
      /*** ObjectCreationExpr ***/
      } else if ( expr.getClass() == MethodCallExpr.class ||
                  expr.getClass() == ObjectCreationExpr.class ) {
        
        // try treating as a binary operation
        aeExpr = null;
        if ( expr.getClass() == MethodCallExpr.class &&
             ((MethodCallExpr)expr).getArgs() != null && 
             ((MethodCallExpr)expr).getArgs().size() == 2 ) {        
          MethodCallExpr mx = (MethodCallExpr)expr;
          ConstructorCall call = binaryOpNameToEventFunction(mx.getName(), null);
          if ( call != null ) {
            Vector< Object > args = new Vector< Object >();
            for ( Expression a : mx.getArgs() ) {
              args.add(astToAeExpression( a, true,
                                          lookOutsideClassDataForTypes,
                                          complainIfDeclNotFound ) );
            }
            call.setArguments( args );
            if ( evaluateCall ) {
              try {
                aeExpr = new gov.nasa.jpl.ae.event.Expression( call.evaluate( true ) );
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
            } else {
              aeExpr = new gov.nasa.jpl.ae.event.Expression( call );
            }
          }
        }
        if ( aeExpr == null ) {
          JavaForFunctionCall javaForFunctionCall =
              new JavaForFunctionCall( this, expr, convertFcnCallArgsToExprs,
                                       getClassData().getPackageName(),
                                       evaluateCall,
                                       returnType);
          aeExpr = javaForFunctionCall.toNewExpression(complainIfDeclNotFound);
        }
          //return aeExpr;
//          //if ( convertFcnCallArgsToExprs ) {
//            middle = javaForFunctionCall.toNewFunctionCallString();
//  //        } else {
//  //          if ( Utils.isNullOrEmpty( javaForFunctionCall.getScope() ) ) {
//  //          middle = javaForFunctionCall.getScope() + ".";
//  //          if (javaForFunctionCall.methodOrConstructor) {
//  //            middle += javaForFunctionCall.callName + "(" + javaForFunctionCall.argumentArrayJava + ")";
//  //          } else {
//  //            middle += "new " javaForFunctionCall.callName + "(" + javaForFunctionCall.argumentArrayJava + ")";            
//  //          }
//  //        }
      } else if ( expr instanceof LiteralExpr ) { //.getClass().getSimpleName().endsWith( "LiteralExpr" ) ) {
          aeExpr = parseNonNullLiteralExpression( (LiteralExpr)expr );
          //return aeExpr;
      } else  { // TODO!!!!!!!!  More expressions to parse!! //if ( expr.getClass() == ConditionalCallExpr.class ) {
        //case "ConditionalExpr": // TODO
          //middle = expr.toString();
        Class<?> cls = ClassUtils.getClassForName( type, null, getClassData().getPackageName(), false );
        aeExpr = new gov.nasa.jpl.ae.event.Expression< Class >( cls, Class.class );
      }
      if ( !convertFcnCallArgsToExprs && aeExpr != null ) {
        return aeExpr.expression;
      }
      return aeExpr;//prefix + middle + suffix;
    }

  public gov.nasa.jpl.ae.event.Expression< ? >
      parseNonNullLiteralExpression( LiteralExpr expr ) {
    if ( expr.getClass() == NullLiteralExpr.class ) {
      return new gov.nasa.jpl.ae.event.Expression< Object >( (Object)null );
    } else {
      if ( expr instanceof BooleanLiteralExpr ) {
        Boolean b = ( (BooleanLiteralExpr)expr ).getValue();
        return new gov.nasa.jpl.ae.event.Expression< Boolean >( b );
      }
      String s = null;
      if ( expr instanceof StringLiteralExpr ) {
        if ( expr instanceof IntegerLiteralExpr ) {
          Integer i = Utils.toInteger( ( (IntegerLiteralExpr)expr ).getValue() );
          return new gov.nasa.jpl.ae.event.Expression< Integer >( i );
        }
        if ( expr instanceof LongLiteralExpr ) {
          Long d = Utils.toLong( ( (LongLiteralExpr)expr ).getValue() );
          return new gov.nasa.jpl.ae.event.Expression< Long >( d );
        }
        if ( expr instanceof DoubleLiteralExpr ) {
          Double d = Utils.toDouble( ( (DoubleLiteralExpr)expr ).getValue() );
          return new gov.nasa.jpl.ae.event.Expression< Double >( d );
        }
        if ( expr instanceof CharLiteralExpr ) {
          s = ( (StringLiteralExpr)expr ).getValue();
          return new gov.nasa.jpl.ae.event.Expression< String >( s );
        }
        // Just a plain StringLiteralExpr
        s = ( (StringLiteralExpr)expr ).getValue();
        return new gov.nasa.jpl.ae.event.Expression< String >( s );
      }
    }
    Debug.error( true, "Error! Unrecognized expression: " + expr );
    return null;
  }

    // TODO -- should probably import and do a switch on all classes in
    // japa.parser.ast.expr.*
    public String astToAeExprType( Expression expr, String specifier,
                                   boolean lookOutsideClassData, boolean complainIfNotFound ) {
      ClassData.Param p = null;
      String name = null;
      String result = null;
      if ( expr == null ) return null;
      String className = expr.getClass().getSimpleName();
      // Inefficient string compare.
      if ( Debug.isOn() ) Debug.outln( "starting astToAeExprType(" + className + ":" + expr + ")" );
      if ( expr.getClass() == ConditionalExpr.class ) {
          ConditionalExpr ce = ( (ConditionalExpr)expr );
          String type1 = astToAeExprType( ce.getThenExpr(), specifier,
                                          lookOutsideClassData, complainIfNotFound );
          String type2 = astToAeExprType( ce.getElseExpr(), specifier,
                                          lookOutsideClassData, complainIfNotFound );
          result = ClassUtils.dominantType( type1, type2 );
      } else if ( expr.getClass() == ArrayCreationExpr.class ) {
        ArrayCreationExpr be = ( (ArrayCreationExpr)expr );
        result = be.getType().toString();
      } else if ( expr.getClass() == BinaryExpr.class ) {
          BinaryExpr be = ( (BinaryExpr)expr );
          result =
              JavaToConstraintExpression.operatorResultType( be.getOperator(),
                                  astToAeExprType( be.getLeft(), specifier, lookOutsideClassData, complainIfNotFound ),
                                  astToAeExprType( be.getRight(), specifier, lookOutsideClassData, complainIfNotFound ) );
      } else if ( expr.getClass() == UnaryExpr.class ) {
          UnaryExpr ue = ( (UnaryExpr)expr );
          result =
              JavaToConstraintExpression.operatorResultType( ue.getOperator(),
                                  astToAeExprType( ue.getExpr(), specifier, lookOutsideClassData, complainIfNotFound ) );
      } else if ( expr.getClass() == EnclosedExpr.class ) {
          result = astToAeExprType( ( (EnclosedExpr)expr ).getInner(), specifier, lookOutsideClassData, complainIfNotFound );
      } else if ( expr.getClass() == MethodCallExpr.class ) {
        Class< ? > returnType = null;
        JavaForFunctionCall javaForFunctionCall =
            new JavaForFunctionCall( this, expr, false,
                                     getClassData().getPackageName(), false,
                                     returnType  );
        Method mm = javaForFunctionCall.getMatchingMethod();
        if ( mm != null ) {
          Class<?> type = mm.getReturnType(); 
          Class<?> cls = mm.getDeclaringClass();
          boolean typeWasObject = type == Object.class;
          if ( type != null && !typeWasObject ) {
            result = ( type.isArray() ? type.getSimpleName() : type.getName() );
          }
          // handle return value with generic type for Wraps.getValue() and Parameter.getValue*()
          if ( ( type == null || typeWasObject ) && cls != null
              && mm.getName().startsWith( "getValue" )
              //&& mm.getName().equals( "getValue" )
  //            && result.equals( "java.lang.Object" )
              && Wraps.class.isAssignableFrom( cls ) ) {
            Object n = null;
            try {
              n = cls.newInstance();
            } catch ( InstantiationException e ) {
              // ignore
            } catch ( IllegalAccessException e ) {
              // ignore
            }
            if ( n != null && n instanceof Wraps ) {
              result = ( (Wraps< ? >)n ).getTypeNameForClassName( javaForFunctionCall.getClassName() );
              if ( result != null && result.endsWith( "Object" ) ) {
                typeWasObject = false;
              }
              type = ( (Wraps< ? >)n ).getType();
            }
          } else if ( ( type == null || typeWasObject ) && cls != null
                  && mm.getName().equals( "get" ) && Collection.class.isAssignableFrom(cls) ) {
            // TODO -- Remove this case if the case below handles this properly. (It should!)
            // handle return value with generic type for Collections.get(int)
            String oTypeName = javaForFunctionCall.getObjectTypeName();
            result = getGenericParameters(oTypeName);  // There should only be one.
          } else {
            // handle the generic return type with Method.getGenericReturnType() and getObjectTypeName() similar to Collection.get() above.
            ArrayList<String> paramNames = new ArrayList<String>();
            for ( TypeVariable<? extends Class<?>> ttt : cls.getTypeParameters() ) {
              paramNames.add( ttt.getName() );
            }
            int pos = paramNames.indexOf( mm.getGenericReturnType().getTypeName() );
            if ( pos >= 0 ) {
              String oTypeName = javaForFunctionCall.getObjectTypeName();
              String callingClassParameters = getGenericParameters(oTypeName);
              result = parseNthGenericArg(pos, callingClassParameters);
            }
            // TODO -- This case does not handle returning Set<String> from a function, public Set<T> getSet(), called from a Foo<String> where String is substituted for T in Foo<T>.
          }

          if ( type == null ) {
            if ( result == null ) {
              result = ClassUtils.parameterPartOfName( javaForFunctionCall.getClassName(), false );
            }
  //          if ( result == null && typeWasObject ) {
  //            result = "Object";
  //          }
  //          //type = ClassUtils.getClassForName( javaForFunctionCall.className, null, false );
          } else {
            if ( result == null ) {
              result = (type.isArray() ? type.getSimpleName() : type.getName());
            }
          }
          if ( result != null && result.endsWith( "Object" ) && typeWasObject ) {
            result = null;
          }
        }
        if ( result == null ) {
          // don't worry about it--special purpose code is called later for this
          result = "null";
          if ( Debug.isOn() ) Debug.outln( "astToAeExprType(" + expr + ") = " + result
                       + "; ok for MethodCallExpr!" );
          complainIfNotFound = false;
        }
      } else if ( expr.getClass() == CastExpr.class ) {
        CastExpr ce = (CastExpr)expr;
        if ( ce.getType() != null ) {
          result = "" + ce.getType();
        }
      } else if ( expr.getClass() == InstanceOfExpr.class ) {
        result = "Boolean";
      } else if ( expr.getClass() == NameExpr.class ) {
        name = ( (NameExpr)expr ).getName();
        if ( name != null && name.equals("time") ) {
          result = "TimeVaryingMap<Double>";
        }
  /*      // HACK? to avoid errors for package name prefix.  What about org?
        if ( name.equals( "java" ) ) {
          result = "java";
        }
        if ( result == null ) {
          // Maybe it's just a class or package name.
          Class< ? > cls = ClassUtils.getClassForName( expr.toString(), packageName, false );
          if ( cls != null ) {
            // REVIEW -- Is it's type itself because it is a type (class name)?
            result = expr.toString();
          }
          if ( result == null ) {
            // REVIEW -- Is it's type itself because it is a package name?
            Package pkg = Package.getPackage( expr.toString() );
            if ( pkg != null ) {
              result = expr.toString();
            }
            // try this? ClassUtils.getPackageStrings( packages );
          }
        }
  *///      below doesn't work.
  //      if ( name == "True" ) name = "true";
  //      if ( name == "False" ) name = "false";
      } else if ( expr.getClass() == ThisExpr.class ) {
        result = getCurrentClass();
      } else if ( expr.getClass() == FieldAccessExpr.class ) {
        FieldAccessExpr fieldAccessExpr = (FieldAccessExpr)expr;
        result = fieldExprToAeType( fieldAccessExpr, lookOutsideClassData );
        if ( result == null ) {
          // REVIEW -- This probably won't work! What case is this?
          if ( Debug.isOn() ) Debug.err( "Can't determine type from FieldAccessExpr: " + expr );
          name = expr.toString();
        }
      } else if ( expr.getClass() == ObjectCreationExpr.class ) {
        ObjectCreationExpr oce = (ObjectCreationExpr)expr;
        result = oce.getType().toString();
      } else if ( expr.getClass() == ClassExpr.class ) {
        ClassExpr ce = (ClassExpr)expr;
        //String pType = astToAeExprType( ce.getType(), lookOutsideXml, complainIfNotFound );
        String c = getClassData().getClassNameWithScope( ce.getType().toString(), true );
        if ( Utils.isNullOrEmpty( c ) ) {
          Class<?> cc = 
              ClassUtils.getClassForName( ce.getType().toString(), specifier,
                                          getClassData().getPackageName(),
                                          false );
          if ( cc != null ) {
            c = ClassUtils.getNonPrimitiveClassName( cc.getName() );
          } else {
            c = "?";
          }
        }
        result = "Class<" + c + ">";
      } else {
          if ( className.endsWith( "LiteralExpr" ) ) {
            // get the part before "LiteralExpr"
            String typeOfLiteral =
                className.substring( 0, className.length() - 11 );
            if ( typeOfLiteral.equals( "Null" ) ) {
              result = "null"; // BAD!  REVIEW -- Do we want void or String?
            } else {
              result = typeOfLiteral;
            }
          } else if ( className.contains( "Literal" ) ) {
            result = className.substring( 0, className.indexOf( "Literal" ) );
          }
          name = expr.toString();
      }
  
      if ( result == null &&  name != null ) {
        if ( name.startsWith( "\"" ) ) {
          result = "String";
        } else {
          p = getClassData().lookupCurrentClassMember( name, lookOutsideClassData, false );
          result = ( p == null ) ? null : p.type;
        }
  
        // Maybe it's just a class or package name.
        // REVIEW -- Is it's type itself because it is a type?
        if ( result == null ) {
          Class< ? > cls =
              ClassUtils.getClassForName( expr.toString(), specifier,
                                          getClassData().getPackageName(), false );
          if ( cls != null ) {
            result = expr.toString();
  //        } else {
  //          if ( ClassUtils.isPackageName( expr.toString() ) ) {
  //            result = expr.toString(); // ??
  //          }
          }
        }
      }
      
      if ( result != null && result.equals("Time") ) {
        result = "Long";
      }
      
      if ( complainIfNotFound && (result == null || result.length() <= 0) )
        Debug.errorOnNull( "Error! null type for expression " + expr + "!", result );
      if ( Debug.isOn() ) Debug.outln( "astToAeExprType(" + expr + ") = " + result );
      // Nested type cannot be referenced by its binary name.
      if ( result != null ) result = result.replace( '$', '.' );
      return result;
    }


    public static String getGenericParameters( String classWithParams ) {
      int pos1 = classWithParams.indexOf('<');
      int pos2 = classWithParams.lastIndexOf('>');
      String callingClassParameters = null;
      if ( pos1 > 0 && pos2 > pos1 ) {
        String result = classWithParams.substring(pos1+1, pos2);
        return result;
      }
      return null;
    }

  /**
   * Get the nth argument in a comma-separated list of generic parameters, counting from 0.<br>
   * For example, the 3rd argument (n = 2) in "X, Y<A<B>>, Z" is "Z".<br>
   * If n is greater than or equal to the number of arguments, then null is returned.<br>
   * The return value is unspecified for malformed input.
   *
   * @param n index of argument in argList counting from 0
   * @param argList the comma-separated list of arguments to parse
   * @return a substring representing the nth argument or null if there are not that many arguments
   */
    public static String parseNthGenericArg(int n, String argList) {
      if ( argList == null ) return null;
      int ct = 0;
      int depth = 0; // depth of nested < . . . >
      int p = 0;
      int pos1 = 0;
      int pos2 = 0;
      while ( ct <= n && p < argList.length() ) {
        char c = argList.charAt(p);
        if ( c == '<' ) {
          ++depth;
        } else if ( depth == 0 && c == ',' ) {
          ++ct;
          if ( ct == n ) {
            pos1 = p + 1;
          } else if ( ct > n ) {
            pos2 = p;
          }
        } else if ( depth > 0 && c == '>' ) {
          --depth;
        }
        ++p;
      }
      // check if never reached argument;
      if ( ct < n ) return null;
      // set pos2 in case the nth arg is the last arg
      if ( pos2 == 0 ) pos2 = p;
      String result = argList.substring(pos1, pos2).trim();
      return result;
    }

  public String
      fieldExprToAeType( FieldAccessExpr fieldAccessExpr, boolean lookOutsideClassData ) {
    String result = null;

    // Maybe it's just a class or package name.
    Class< ? > cls =
        ClassUtils.getClassForName( fieldAccessExpr.toString(), null,
                                    getClassData().getPackageName(), false );
    if ( cls != null ) {
      // REVIEW -- Is it's type itself because it is a type (class name)?
      result = fieldAccessExpr.toString();
    } else {

      Package pkg = Package.getPackage( fieldAccessExpr.toString() );
      if ( pkg != null ) {
        result = fieldAccessExpr.toString();
      } else {
        //FieldAccessExpr fieldAccessExpr = (FieldAccessExpr)expr;
        // The member/field type is defined in its parent's class, and the parent
        // class can be found by getting the type of the FiedAccessExpr's scope.
        // if ( fieldAccessExpr.getScope() instanceof FieldAccessExpr ) {
        String parentType =
                astToAeExprType( fieldAccessExpr.getScope(), fieldAccessExpr.getField(),
                                 lookOutsideClassData, false );
        ClassData.Param p = null;
        if ( !Utils.isNullOrEmpty( parentType ) ) {
          p = getClassData().lookupMemberByName( parentType, fieldAccessExpr.getField(),
                                                 lookOutsideClassData, false );
        }
        // }
        if ( p == null ) {
          // If the member is static, then the scope is a class name, and we can
          // try looking it up. // TODO -- Check to see if it's static.
          p = getClassData().lookupMemberByName( fieldAccessExpr.getScope().toString(),
                                                 fieldAccessExpr.getField(),
                                                 lookOutsideClassData, false );
        }
        if ( p != null ) {
          result = p.type;
        } else {
          // Maybe it's not a field access, but an enclosed class.
          if ( Utils.isNullOrEmpty( parentType ) ) {
            parentType = fieldAccessExpr.getScope().toString();
          }
          Class<?> classForName = ClassUtils.getClassOfClass( parentType,
                                                              fieldAccessExpr.getField()
                                                                             .toString(),
                                                              getClassData().getPackageName(),
                                                              false );
          if ( classForName != null ) {
            result = classForName.getName();
          }
        }
      }
    }
    return result;
  }

  public String fieldExprToAe( FieldAccessExpr fieldAccessExpr,
                               boolean lookOutsideClassDataForTypes,
                               boolean complainIfDeclNotFound,
                               boolean wrapInFunction, boolean evaluateCall,
                               boolean getParameterValue, boolean propagate ) {
    // TODO -- make this more like fieldExprToAeExpression(), which handles more
    // cases (not just when scope is FieldAccessExpr and NameExpr) and passes
    // convertFcnCallArgsToExprs arg through.
    String aeString = null;
    if ( fieldAccessExpr.getScope() == null
         || ( !(fieldAccessExpr.getScope() instanceof FieldAccessExpr) && !(fieldAccessExpr.getScope() instanceof NameExpr) ) ) {
      if ( fieldAccessExpr.getScope() instanceof ThisExpr ) {
        aeString = fieldAccessExpr.toString();
      } else {
        String parentString = astToAeExpr(fieldAccessExpr.getScope(), true, lookOutsideClassDataForTypes, complainIfDeclNotFound);
        aeString =
                packageExpressionString( parentString, fieldAccessExpr,
                                         wrapInFunction, evaluateCall,
                                         getParameterValue, propagate );
      }
      return aeString;
    }
      /* */
      // Maybe it's just a class or package name.
      Class< ? > cls =
          ClassUtils.getClassForName( fieldAccessExpr.toString(),
                                      null, getClassData().getPackageName(), false );
      if ( cls != null ) {
        return fieldAccessExpr.toString();
      }
      Package pkg = Package.getPackage( fieldAccessExpr.toString() );
      if ( pkg != null ) {
        return fieldAccessExpr.toString();
      }
      /* */
      String parentType =
          astToAeExprType( fieldAccessExpr.getScope(),
                           null, lookOutsideClassDataForTypes, complainIfDeclNotFound );
      if ( !Utils.isNullOrEmpty( parentType ) ) {
        ClassData.Param p =
            getClassData().lookupMemberByName( parentType,
                                          fieldAccessExpr.getField(), false,
                                          false );
        String parentString = null;
        if ( fieldAccessExpr.getScope() instanceof FieldAccessExpr ) {
          parentString =
              fieldExprToAe( (FieldAccessExpr)fieldAccessExpr.getScope(),
                             lookOutsideClassDataForTypes,
                             complainIfDeclNotFound, wrapInFunction, false,
                             true, propagate );
        } else {
          parentString =
              nameExprToAe( (NameExpr)fieldAccessExpr.getScope(),
                            wrapInFunction, evaluateCall, !wrapInFunction,
                            propagate );
        }
        Class< ? > parentAsClass =
            ClassUtils.getClassForName( parentString,
                                        null, getClassData().getPackageName(), false );
        boolean madeEnum = false;
        if ( parentAsClass != null ) {
//          Field f = ClassUtils.getField( parentAsClass, fieldAccessExpr.getField(), true );
//          if ( parentAsClass.isEnum() || ( f != null && ClassUtils.isStatic( f ) && ClassUtils.isFinal( f ) ) ) {
            madeEnum = true;
            parentString = Utils.replaceSuffix( parentString, ".class", "" );
            aeString = parentString + "." + fieldAccessExpr.getField();
//          } else {
//            if ( !parentString.endsWith( ".class" ) ) {
//              parentString = parentString + ".class";
//            }
//          }
        }
        if ( !madeEnum ) {
          aeString =
              packageExpressionString( parentString, fieldAccessExpr,
                                       wrapInFunction, evaluateCall,
                                       p != null && getParameterValue, propagate );
        }
//        if ( wrapInFunction ) {
//          aeString =
//              "new FunctionCall(" + parentString
//                  + ", Parameter.class, \"getMember\", " + "new Object[]{\""
//                  + fieldAccessExpr.getField() + "\"})";
//        } else {
//          aeString = parentString + "." + fieldAccessExpr.getField();
//        }
//        if ( p != null && getParameterValue ) {
//          if ( wrapInFunction ) {
//            // nesting function calls
//            aeString =
//                "new FunctionCall(null, Parameter.class, \"getValue\", "
//                    + "new Object[]{ true }, " + aeString + ")";
//          } else {
//            aeString += ".getValue(" + propagate + ")";
//          }
//        }
//        if ( wrapInFunction && evaluateCall ) {
//          aeString = "(" + aeString + ").evaluate(" + propagate + ")";
//        }
      }
    return aeString;
  }
  
  public gov.nasa.jpl.ae.event.Expression< ? >
    fieldExprScopeToAeExpression( FieldAccessExpr fieldAccessExpr,
                                  boolean convertFcnCallArgsToExprs,
                                  boolean lookOutsideClassDataForTypes,
                                  boolean complainIfDeclNotFound,
                                  boolean wrapInFunction, boolean evaluateCall,
                                  boolean propagate ) {
    if ( fieldAccessExpr.getScope() == null ) {
      Debug.error( false, "Warning! Got FieldAccessExpr with null scope! "
                          + fieldAccessExpr );
      return null;
    }
    gov.nasa.jpl.ae.event.Expression< ? > parentExpr = null;
    if ( fieldAccessExpr.getScope() instanceof FieldAccessExpr ) {
      parentExpr =
          fieldExprToAeExpression( (FieldAccessExpr)fieldAccessExpr.getScope(),
                                   convertFcnCallArgsToExprs,
                                   lookOutsideClassDataForTypes,
                                   complainIfDeclNotFound, wrapInFunction,
                                   false, true, propagate );
    } else if ( fieldAccessExpr.getScope() instanceof NameExpr ) {
      String type = astToAeExprType( fieldAccessExpr.getScope(),
                                     null,
                                     lookOutsideClassDataForTypes, complainIfDeclNotFound );
      boolean addIfNotFound = type != null && !type.equals( fieldAccessExpr.getScope().toString() ) && !wrapInFunction;
      parentExpr =
          nameExprToAeExpression( (NameExpr)fieldAccessExpr.getScope(),
                                  wrapInFunction, evaluateCall,
                                  addIfNotFound, false, propagate, complainIfDeclNotFound );
    } else {
      Object o =
          astToAeExpression( fieldAccessExpr.getScope(), null,
                             null,
                             convertFcnCallArgsToExprs, lookOutsideClassDataForTypes,
                             false,
                             complainIfDeclNotFound, evaluateCall );
      if ( o instanceof gov.nasa.jpl.ae.event.Expression ) {
        parentExpr = (gov.nasa.jpl.ae.event.Expression< ? >)o;
      } else if ( o instanceof Parameter ) {
        parentExpr = new gov.nasa.jpl.ae.event.Expression( (Parameter<?>)o );
      } else if ( o instanceof FunctionCall ) {
        parentExpr = new gov.nasa.jpl.ae.event.Expression( (FunctionCall)o );
      }
    }
    return parentExpr;
  }
  
  public gov.nasa.jpl.ae.event.Expression< ? >
      fieldExprToAeExpression( FieldAccessExpr fieldAccessExpr,
                               boolean convertFcnCallArgsToExprs,
                               boolean lookOutsideClassDataForTypes,
                               boolean complainIfDeclNotFound,
                               boolean wrapInFunction, boolean evaluateCall,
                               boolean getParameterValue, boolean propagate ) {
    gov.nasa.jpl.ae.event.Expression< ? > aeExpr = null;
    gov.nasa.jpl.ae.event.Expression< ? > parentExpr = null;

    // Maybe it's just a class or package name.
    Class< ? > cls =
        ClassUtils.getClassForName( fieldAccessExpr.toString(),
                                    null, getClassData().getPackageName(), false );
    if ( cls != null ) {
      return new gov.nasa.jpl.ae.event.Expression< Class >( cls, Class.class );
    }
    Package pkg = Package.getPackage( fieldAccessExpr.toString() );
    if ( pkg != null ) {
      return new gov.nasa.jpl.ae.event.Expression< Package >( pkg,
                                                              Package.class );
    }

    if ( fieldAccessExpr.getScope() == null ) {
      Debug.error( false, "Warning! Got FieldAccessExpr with null scope! "
                          + fieldAccessExpr );
      NameExpr nameExpr = new NameExpr( fieldAccessExpr.getField() );
      aeExpr =
          nameExprToAeExpression( nameExpr, wrapInFunction, evaluateCall,
                                  wrapInFunction, false, propagate, complainIfDeclNotFound );
    } else {
      parentExpr =
          fieldExprScopeToAeExpression( fieldAccessExpr,
                                        convertFcnCallArgsToExprs,
                                        lookOutsideClassDataForTypes,
                                        complainIfDeclNotFound, wrapInFunction,
                                        evaluateCall, propagate );
      if ( parentExpr != null ) {
        FunctionCall functionCall = null;
        
        // Check if enum
        if ( Debug.isOn() ) System.out.println( "fieldExprToAeExpression(" + fieldAccessExpr + "): parent type name = " + parentExpr.getType().getCanonicalName() );
        Class<?> enumClass = parentExpr.getType();
        if ( enumClass != null && !enumClass.isEnum() && Class.class.isAssignableFrom( enumClass ) ) {
          enumClass = (Class< ? >)parentExpr.getValue( false );
        }
        if ( enumClass != null && Debug.isOn()) System.out.println( "fieldExprToAeExpression(" + fieldAccessExpr + "): parent as Enum class = " + enumClass.getCanonicalName() );
        if ( enumClass != null && enumClass.isEnum() ) {  // Won't this be Class<Class> where the type parameter is the enum class?
          Object constant = ClassUtils.getEnumConstant( enumClass, fieldAccessExpr.getField() );
          if ( constant != null ) {
//            functionCall = new FunctionCall(null, Utils.class, "getEnumConstant",
//                                            new Object[] { parentExpr.expression,
//                                                           "" + fieldAccessExpr.getField() } );
            aeExpr = new gov.nasa.jpl.ae.event.Expression( constant );
          } else {
            functionCall = new FunctionCall(null, ClassUtils.class, "getEnumConstant",
                                            new Object[] { enumClass,
                                                           "" + fieldAccessExpr.getField() } );
          }
        } else {
          // treat as a member
          if ( parentExpr.expression instanceof Parameter ) {
            functionCall =
                new Functions.GetMember<>(parentExpr.expression, "" + fieldAccessExpr.getField() );
//                new FunctionCall( parentExpr.expression, Parameter.class,
//                                  "getMember",
//                                  new Object[] { "" + fieldAccessExpr.getField(), true }, (Class<?>)null );
          } else if ( parentExpr.expression instanceof Class ) {
            functionCall =
                new FunctionCall( null, ClassUtils.class,
                                  "getField",
                                  new Object[] { parentExpr.expression, "" + fieldAccessExpr.getField(), true } );
          }
          aeExpr = new gov.nasa.jpl.ae.event.Expression( functionCall );
          String parentType =
              astToAeExprType( fieldAccessExpr.getScope(),
                               null, lookOutsideClassDataForTypes, complainIfDeclNotFound );
          ClassData.Param p =
              getClassData().lookupMemberByName( parentType,
                                            fieldAccessExpr.getField(), false,
                                            false );
          aeExpr =
              packageExpression( aeExpr, wrapInFunction, evaluateCall,
                                 p != null && getParameterValue, propagate );
        }
//        if ( p != null && getParameterValue ) {
//          // if ( wrapInFunction ) {
//          // nesting function calls
//          functionCall =
//              new FunctionCall( aeExpr, Parameter.class, "getValue",
//                                new Object[] { true } );
//          aeExpr = new gov.nasa.jpl.ae.event.Expression( functionCall );
//        }
//        if ( evaluateCall ) { // && wrapInFunction ) {
//          Object result = null;
//          result = aeExpr.evaluate( propagate );
//          if ( !aeExpr.didEvaluationSucceed() ) {
//            aeExpr = null;
//          } else if ( !( result instanceof gov.nasa.jpl.ae.event.Expression ) ) {
//            aeExpr = new gov.nasa.jpl.ae.event.Expression( result );
//          }
//        }
      }
    }
    return aeExpr;
  }
  
  public gov.nasa.jpl.ae.event.Expression< ? >
      packageExpression( Object object,
                         boolean wrapInFunction, boolean evaluateCall,
                         boolean getParameterValue, boolean propagate ) {
    gov.nasa.jpl.ae.event.Expression< ? > aeExpr =
        ( object instanceof Expression ) ? (gov.nasa.jpl.ae.event.Expression< ? >)object
                                         : null;
    if ( getParameterValue || wrapInFunction ) {
      // nesting function calls
      FunctionCall functionCall =
          new FunctionCall( object, Parameter.class, "getValue",
                            new Object[] { true }, (Class<?>)null );
      aeExpr = new gov.nasa.jpl.ae.event.Expression( functionCall );
    }
    if ( evaluateCall && aeExpr != null ) { // && wrapInFunction ) {
      Object result = null;
      try {
        result = aeExpr.evaluate( propagate );
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
      if ( !aeExpr.didEvaluationSucceed() ) {
        aeExpr = null;
      } else if ( !( result instanceof gov.nasa.jpl.ae.event.Expression ) ) {
        aeExpr = new gov.nasa.jpl.ae.event.Expression( result );
      }
    }
    if ( aeExpr == null && !wrapInFunction && object instanceof Parameter ) {
      aeExpr = new gov.nasa.jpl.ae.event.Expression( ((Parameter< ? >)object).getValue( propagate ) );
    }
    return aeExpr;
  }

  public String packageExpressionString( String parentString,
                                         FieldAccessExpr fieldAccessExpr,
                                         boolean wrapInFunction,
                                         boolean evaluateCall,
                                         boolean getParameterValue,
                                         boolean propagate ) {
    String aeString;
    if ( wrapInFunction ) {
      aeString =
              "new Functions.GetMember(" + parentString + ", \""
              + fieldAccessExpr.getField() + "\")";
//          "new FunctionCall(" + parentString
//              + ", Parameter.class, \"getMember\", " + "new Object[]{\""
//              + fieldAccessExpr.getField() + "\"})";
    } else {
      aeString = parentString + "." + fieldAccessExpr.getField();
    }
    //MAYBE UNCOMMENT THIS IDK WHAT IT'S DOING
//    if ( getParameterValue ) {
//      if ( wrapInFunction ) {
//        // nesting function calls
//        aeString =
//            "new FunctionCall(null, Parameter.class, \"getValue\", "
//                + "new Object[]{ true }, " + aeString + ")";
//      } else {
//        aeString += ".getValue(" + propagate + ")";
//      }
//    }
    if ( wrapInFunction && evaluateCall ) {
      aeString = "(" + aeString + ").evaluate(" + propagate + ")";
    }
    return aeString;
  }


  protected Expression fixExpression( Expression expr ) {
    return fixExpression(expr, this);

  }
    protected static Expression fixExpression( Expression expr, JavaToConstraintExpression j2ce ) {
      if ( expr == null ) return null;
      String newExprString = j2ce.astToAeExpr( expr, false, true, false );
      Expression newExpr = parseExpression( newExprString );
      return newExpr;
    }

    protected List<Expression> fixExpressions( List<Expression> exprs ) {
      if ( exprs == null ) return null;
      List<Expression> newExprs = new ArrayList<Expression>();
      for ( Expression expr : exprs) {
        Expression newExpr = fixExpression( expr );
        if ( newExpr != null ) newExprs.add( newExpr );
        else newExprs.add( expr );
      }
      return newExprs;
    }

    /**
       * Convert the Java statement into an AE Java statement by converting 
       * expressions with astToAeExpr().
       * @param stmt the statement to fix
       */
    public void fixStatement( Statement stmt ) {
      fixStatement(stmt, this);
    }
    public static void fixStatement( Statement stmt, JavaToConstraintExpression j2ce ) {
        if ( stmt == null ) return;
        if ( stmt instanceof ExpressionStmt ) {
          ExpressionStmt exprStmt = (ExpressionStmt)stmt;
          Expression expr = j2ce.fixExpression( exprStmt.getExpression() );
          exprStmt.setExpression( expr );
    //      if ( exprStmt.getExpression() instanceof AssignExpr ) {
    //        AssignExpr assignExpr = (AssignExpr)exprStmt.getExpression();
    //        String newExprString = astToAeExpr( assignExpr, false, true, false );
    //        
    //        assignExpr.getValue();
    //        Param p = lookupMemberByName(className, paramName, false, false);
    //      }
        } else if ( stmt instanceof BlockStmt ) {
          BlockStmt bs = (BlockStmt)stmt;
          j2ce.fixStatements( bs.getStmts() );
        } else if ( stmt instanceof AssertStmt ) {
          AssertStmt assertStmt = (AssertStmt)stmt;
          Expression expr = j2ce.fixExpression( assertStmt.getCheck() );
          if ( expr != null ) assertStmt.setCheck( expr );
        } else if ( stmt instanceof DoStmt ) {
          DoStmt doStmt = (DoStmt)stmt;
          //List<Statement> stmtList = new ArrayList<Statement>();
          j2ce.fixStatement( doStmt.getBody() );
          Expression expr = j2ce.fixExpression( doStmt.getCondition() );
          if ( expr != null ) doStmt.setCondition( expr );
          //doStmt.setBody( stmtList.get( 0 ) );
        } else if ( stmt instanceof ExplicitConstructorInvocationStmt ) {
          ExplicitConstructorInvocationStmt ecis = (ExplicitConstructorInvocationStmt)stmt;
          List< Expression > exprs = j2ce.fixExpressions( ecis.getArgs() );
          if ( exprs != null ) ecis.setArgs( exprs );
          Expression expr = j2ce.fixExpression( ecis.getExpr() );
          if ( expr != null ) ecis.setExpr( expr );
        } else if ( stmt instanceof ForeachStmt ) {
          ForeachStmt foreachStmt = (ForeachStmt)stmt;
          j2ce.fixStatement( foreachStmt.getBody() );
          Expression expr = j2ce.fixExpression( foreachStmt.getIterable() );
          if ( expr != null ) foreachStmt.setIterable(expr);
          expr = j2ce.fixExpression( foreachStmt.getVariable() );
          if ( expr != null && expr instanceof VariableDeclarationExpr ) {
            foreachStmt.setVariable( (VariableDeclarationExpr)expr );
          }
        } else if ( stmt instanceof ForStmt ) {
          ForStmt forStmt = (ForStmt)stmt;
          j2ce.fixStatement( forStmt.getBody() );
          List< Expression > exprs = j2ce.fixExpressions( forStmt.getInit() );
          if ( exprs != null ) forStmt.setInit( exprs );
          Expression expr = j2ce.fixExpression( forStmt.getCompare() );
          if ( expr != null ) forStmt.setCompare( expr );
          exprs = j2ce.fixExpressions( forStmt.getUpdate() );
          if ( exprs != null ) forStmt.setUpdate( exprs );        
        } else if ( stmt instanceof IfStmt ) {
          IfStmt ifStmt = (IfStmt)stmt;
          List<Statement> stmtList = new ArrayList<Statement>();
          stmtList.add( ifStmt.getThenStmt() );
          stmtList.add( ifStmt.getElseStmt() );
          j2ce.fixStatements( stmtList );
          Expression expr = j2ce.fixExpression( ifStmt.getCondition() );
          if ( expr != null ) ifStmt.setCondition( expr );
        } else if ( stmt instanceof LabeledStmt ) {
          LabeledStmt labeledStmt = (LabeledStmt)stmt;
          j2ce.fixStatement( labeledStmt.getStmt() );
        } else if ( stmt instanceof ReturnStmt ) {
          ReturnStmt returnStmt = (ReturnStmt)stmt;
          Expression expr = j2ce.fixExpression( returnStmt.getExpr() );
          if ( expr != null ) returnStmt.setExpr( expr );
        } else if ( stmt instanceof SwitchEntryStmt ) {
          SwitchEntryStmt switchStmt = (SwitchEntryStmt)stmt;
          j2ce.fixStatements( switchStmt.getStmts() );
          Expression expr = j2ce.fixExpression( switchStmt.getLabel() );
          if ( expr != null ) switchStmt.setLabel( expr );
        } else if ( stmt instanceof SwitchStmt ) {
          SwitchStmt switchStmt = (SwitchStmt)stmt;
          j2ce.fixStatements( switchStmt.getEntries() );
          Expression expr = j2ce.fixExpression( switchStmt.getSelector() );
          if ( expr != null ) switchStmt.setSelector( expr );
        } else if ( stmt instanceof SynchronizedStmt ) {
          SynchronizedStmt synchStmt = (SynchronizedStmt)stmt;
          j2ce.fixStatement( synchStmt.getBlock() );
          Expression expr = j2ce.fixExpression( synchStmt.getExpr() );
          if ( expr != null ) synchStmt.setExpr( expr );
        } else if ( stmt instanceof ThrowStmt ) {
          ThrowStmt throwStmt = (ThrowStmt)stmt;
          Expression expr = j2ce.fixExpression( throwStmt.getExpr() );
          if ( expr != null ) throwStmt.setExpr( expr );
        } else if ( stmt instanceof TryStmt ) {
          TryStmt tryStmt = (TryStmt)stmt;
          List<Statement> stmtList = new ArrayList<Statement>();
          stmtList.add( tryStmt.getTryBlock() );
          stmtList.add( tryStmt.getFinallyBlock() );
          for ( CatchClause cc : tryStmt.getCatchs() ) {
            stmtList.add( cc.getCatchBlock() );
          }
          j2ce.fixStatements( stmtList );
        } else if ( stmt instanceof TypeDeclarationStmt ) {
          // TODO
//          TypeDeclarationStmt typeDeclStmt = (TypeDeclarationStmt)stmt;
//          for ( BodyDeclaration member : typeDeclStmt.getTypeDeclaration().getMembers() ) {
//            //member
//          }
        } else if ( stmt instanceof WhileStmt ) {
          WhileStmt whileStmt = (WhileStmt)stmt;
          j2ce.fixStatement( whileStmt.getBody() );
          Expression expr = j2ce.fixExpression( whileStmt.getCondition() );
          if ( expr != null ) whileStmt.setCondition( expr );
        } else {
          System.err.println( "fixStatement(): got unhandled Statement type: "
                              + stmt.getClass().getName() );
        }
      }

    /**
     * Converts Java statements into AE Java statements by converting expressions
     * with astToAeExpr().
     * @param stmts
     */
    protected void fixStatements( List<? extends Statement> stmts ) {
      if ( stmts == null ) return;
      for ( Statement stmt : stmts ) {
        fixStatement( stmt );
      }
    }

    /**
       * Fix all names in the string so that they can be used as Java identifiers
       * or type names.  This currently just calls fixName( value ) since fixName()
       * does not change number tokens in the string.
       * 
       * @param value
       *          The string to be fixed.
       * @return A translation of name into a valid Java identifier or type name.
       */
      public String fixValue( String value ) {
        return value;
    /*    if ( value == null ) return null;
        //String javaValue = nameTranslator.substitute( value, "xml", "java" );
        String javaValue = fixName( value );
        //if ( Debug.isOn() ) Debug.outln("fixName(\"" + value + "\") = \"" + javaValue + "\"" );
        return javaValue;
    */  }

    public String getObjectFromScope( Expression scopeExpr ) {
      String object = null;
      if ( scopeExpr != null) {
        object = astToAeExpr( scopeExpr, true, true, false );
      }
      if ( object == null ) object = "null";
      return object;
    }
    public Object getObjectExpressionFromScope( Expression scopeExpr ) {
      Object object = null;
      if ( scopeExpr != null) {
        object = astToAeExpression( scopeExpr, true, true, false );
      }
      if ( object == null ) object = "null";
      return object;
    }
    

    /**
     * Don't call this!  It can cause an infinite loop
     *   JavaToConstraintExpression.javaToAeExpression(String, String, boolean) line: 1339    
     *   JavaForFunctionCall.toNewFunctionCall() line: 452   
     *   JavaForFunctionCall.toNewExpression() line: 507 
     *   JavaToConstraintExpression.astToAeExpression(Expression, String, String, boolean, boolean, boolean, boolean) line: 661  
     *   JavaToConstraintExpression.astToAeExpression(Expression, String, boolean, boolean, boolean) line: 499   
     *   JavaToConstraintExpression.javaToAeExpression(String, String, boolean) line: 1339   
     * <p>TODO -- Find out why toNewFunctionCall() calls this instead of javaToAeExpr()!!!
     * <p>Fixing by commenting out first line and changing fourth line.
     * 
     * @param exprString
     * @param type
     * @param convertFcnCallArgsToExprs
     * @return
     */
    public gov.nasa.jpl.ae.event.Expression< ? > javaToAeExpression( String exprString, String type, 
                                boolean convertFcnCallArgsToExprs ) {
      //String exprStr = javaToAeExpr( exprString, type, convertFcnCallArgsToExprs );
      
      gov.nasa.jpl.ae.event.Expression< ? > expr = null;
      //Expression astExpr = parseExpression( exprStr );
      Expression astExpr = parseExpression( exprString );
      Object o = astToAeExpression( astExpr, type, null, convertFcnCallArgsToExprs, true, false, true, true );
      expr = (gov.nasa.jpl.ae.event.Expression< ? >)( o instanceof gov.nasa.jpl.ae.event.Expression
               ? o
               : new gov.nasa.jpl.ae.event.Expression( o ) );
      return expr;
    }
    
    public String javaToAeExpr( String exprString, String type, 
                                boolean convertFcnCallArgsToExprs ) {
      return javaToAeExpr( exprString, type, convertFcnCallArgsToExprs, false );
    }

    public String javaToAeExpr( String exprString, String type, 
                                boolean convertFcnCallArgsToExprs,
                                boolean evaluateCall ) {
      return javaToAeExpr( exprString, type, convertFcnCallArgsToExprs, false , evaluateCall );
    }
    public String javaToAeExpr( String exprString, String type, 
                                boolean convertFcnCallArgsToExprs,
                                boolean getParameterValue,
                                boolean evaluateCall ) {
      Expression expr = parseExpression( exprString );
      return astToAeExpr( expr, type, convertFcnCallArgsToExprs, true, getParameterValue, true,
                          evaluateCall );
    }

  public < T > gov.nasa.jpl.ae.event.Expression< T >
      nameExprToAeExpression( NameExpr nameExpr, boolean wrapInFunction,
                              boolean evaluateCall, boolean addIfNotFound,
                              boolean getParameterValue,
                              boolean propagate, boolean complainIfNotFound ) {
      
      String aeString = nameExpr.getName();
      if ( aeString != null && aeString.equals("time") ) {
        aeString = "TimeVaryingMap.time";
      }
      //ClassData.Param p = classData.lookupCurrentClassMember( aeString, false, false );
      //if ( p == null ) 
      ClassData.Param p = getClassData().getParam( null, aeString, true, true,
                                                   addIfNotFound, false );
      Parameter< T > parameter =
          (Parameter< T >)( p == null ? null : getClassData().getParameterMap().get( p ) );

      // REVIEW -- Why not check for things other than a member?
      if ( parameter == null ) {
        
        Class<?> cls = ClassUtils.getClassForName( aeString, null, (String)null, true );
        if ( cls != null ) {
          return new gov.nasa.jpl.ae.event.Expression< T >(cls);
        }
        if ( complainIfNotFound ) {
          Debug.error( true, "Could not find a parameter or class for name expression \"" + aeString + "\"." );
        }

        return null;
      }

    gov.nasa.jpl.ae.event.Expression< T > aeExpression =
        (gov.nasa.jpl.ae.event.Expression< T >)packageExpression( parameter,
                                                                  wrapInFunction,
                                                                  evaluateCall,
                                                                  getParameterValue,
                                                                  propagate );
//      gov.nasa.jpl.ae.event.Expression< T > aeExpression = null;
//
//      if ( wrapInFunction ) {
//        aeExpression =
//            new gov.nasa.jpl.ae.event.Expression<T>( new FunctionCall( parameter,
//                                                                    Parameter.class,
//                                                                    "getValue",
//                                                                    new Object[] { true } ) );
//        if ( evaluateCall ) {
//          aeExpression =
//              new gov.nasa.jpl.ae.event.Expression<T>( aeExpression.evaluate( propagate ) );
//        }
//      } else {
//        aeExpression =
//            new gov.nasa.jpl.ae.event.Expression<T>( parameter.getValue( propagate ) );
//      }
      return aeExpression;
    }

  public String nameExprToAe( NameExpr nameExpr, boolean wrapInFunction,
                              boolean evaluateCall, boolean getParameterValue,
                              boolean propagate ) {
    if ( nameExpr == null ) return null;
    String aeString = nameExpr.getName();
    ClassData.Param p =
        getClassData().lookupCurrentClassMember( aeString, false, false );
    if ( p == null ) {
      return aeString;
    }

    // wrap member access in GetMember call
    if ( !getParameterValue ) {
      String objectEnclosingParam; // reference to object containing the desired parameter

      // split currentClass from ClassData by "." and check to see if p.scope appears anywhere in that list
      // if p.scope appears in the list, then p.scope is an enclosing class
      if(Arrays.asList(getClassData().getCurrentClass().split(Pattern.quote("."))).contains(p.scope)) {
        objectEnclosingParam = p.scope + ".this";
      } else { // otherwise, param is declared in this (possibly a super class)
        objectEnclosingParam = "this";
      }

      return "new Functions.GetMember(" + objectEnclosingParam + ", " + "\"" + p.name + "\"" + ")";
    }

    if ( wrapInFunction ) {
      aeString =
          "new FunctionCall(" + aeString + ", Parameter.class, \"getValue\", "
              + "new Object[]{ true })";
      if ( evaluateCall ) {
        aeString = "(" + aeString + ").evaluate(" + propagate + ")";
      }
    } else {
      aeString += ".getValue(" + propagate + ")";
    }
    return aeString;
  }

  public static Expression parseExpression( String exprString ) {
    if ( Debug.isOn() ) Debug.outln( "trying to parse Java expression \""
                                     + exprString + "\"" );
    ASTParser parser = new ASTParser( new StringReader( exprString ) );
    Expression expr = null;
    try {
      expr = parser.Expression();
    } catch ( ParseException e ) {
      Debug.error( true, false,
                   "Failed to parse Java expression \"" + exprString + "\"" );
      e.printStackTrace();
    }
    return expr;
  }

  // Had to dodge the domain code in order to get 'Foo.new Bar()' to work.
  protected static boolean useClassDomain = true;

  public String getDomainString(String type, String enclosingObject) {
    if (!useClassDomain ) return "null";
    if (!type.equals( "Integer" ) && !type.equals("Boolean") &&!type.equals( "Double" ) && !type.equals( "String" )) {
      String qType = getClassData().getClassNameWithScope(type);
      if (qType == null) qType = type;
      if ( Utils.isNullOrEmpty(enclosingObject) ) {
        String enclosing = getClassData().getEnclosingClassName(type);
        if ( enclosing == null && type != qType ) {
          enclosing = getClassData().getEnclosingClassName(qType);
        }
        String qEnc = getClassData().getClassNameWithScope(enclosing);
        if ( qEnc != null ) enclosing = qEnc;
        if (enclosing != null) {
          String cc = getClassData().getClassNameWithScope( getCurrentClass() );
          // test if the class we want (enclosing) is one of the enclosing classes of this
          if (cc != null &&
              (cc.equals( enclosing ) ||
               getClassData().getAllEnclosingClassNames( cc ).contains( enclosing ))) {
            enclosingObject = enclosing + ".this";
          }
        }
      }
// This commented out code was replaced by the code below.  Not sure about this.  -- BJC
//      if ( !Utils.isNullOrEmpty(enclosingObject) ) {
//        return "new ClassDomain<" + qType + ">(" + qType + ".class, " + enclosingObject + ")";
//      }
      String qTypeNoGenerics = qType.replaceAll( "<.*>", "" );
      return "new ClassDomain<" + qType + ">( ((Class<" + qType + ">)((Object)" + qTypeNoGenerics + ".class))" +
        (!Utils.isNullOrEmpty( enclosingObject ) ? ", " + enclosingObject : "") + " )";
    }
    return "null";
  }
  

  public String[] convertToEventParameterTypeAndConstructorArgs( ClassData.Param p,
                                                                 String enclosingObject ) {
    return convertToEventParameterTypeAndConstructorArgs( p, getCurrentClass(), enclosingObject );
  }
  

  /**
   * Determines the AE translated parameter type, generic parameter types, and arguments.  
   * @param p
   * @param classOfParameterName
   * @return
   */
  public String[]
      convertToEventParameterTypeAndConstructorArgs( ClassData.Param p,
                                                     String classOfParameterName,
                                                     String enclosingObject ) {
    if (p.type == null || p.type.isEmpty() || p.type.equalsIgnoreCase("null")) {
      ClassData.Param pDef =
              getClassData().lookupMemberByName(classOfParameterName, p.name,
                                                true,
                                                true);
      if (pDef != null) {
        p.type = pDef.type;
      }
    }
    return convertToTypeAndConstructorArgs( p, enclosingObject );
  }

  /**
   * Determines the AE translated parameter type, generic parameter types, and arguments.
   * @param p
   * @return
   */
  public String[] convertToTypeAndConstructorArgs( ClassData.Param p, String enclosingObject ) {
    boolean evaluateForType = true;

    String ret[] = new String[ 4 ];
    String type = "Parameter";
    String parameterTypes = p.type;

    // parameterTypes = getFullyQualifiedName( parameterTypes, true );
    parameterTypes = getClassData().getClassNameWithScope( parameterTypes, true );
    String castType = parameterTypes;
    String typePlaceholder = "!TYPE!";
    String valuePlaceholder = "!VALUE!";
    if ( Utils.isNullOrEmpty( p.value ) ) {
      if ( initAllToNull || Utils.isNullOrEmpty( p.type ) ||
           p.type.toLowerCase().equals( "time" ) ||
           ClassUtils.getPrimitives().containsKey( p.type.toLowerCase() ) ||
           Utils.isTrue( p.valueIsConstructor )
           ) {
        p.value = "null";
      } else {
        p.value = "new " + typePlaceholder + "()";
      }
    }
    String domain = getDomainString(p.type, enclosingObject);
    String args = "\"" + p.name + "\"," + domain + ", " + valuePlaceholder + ", this";
    String parameterClass =
        ClassData.typeToParameterType( p.type );
    if ( Utils.isNullOrEmpty( p.type ) ) {
      System.err.println( "Error! creating a field " + p + " of unknown type!" );
    } else if ( p.type.toLowerCase().startsWith( "long" )
                || p.type.trim().replaceAll( " ", "" )
                         .equals( "Parameter<Long>" ) ) {
      type = "LongParameter";
      parameterTypes = null; // "Integer";
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
        args = "\"" + p.name + "\", " + valuePlaceholder + ", this";
        
        if ( p.value != null && !Utils.isNullOrEmpty( p.value.trim() )
             && Character.isDigit( p.value.trim().charAt( 0 ) ) && !castType.contains( "." )) {
          castType = castType.toLowerCase();
        }
      }
    } else if ( !parameterClass.equals( p.type ) ) {
      type = parameterClass;
      if ( !type.equals( "Parameter" ) ) {
        parameterTypes = null;
        if ( !Utils.isNullOrEmpty( castType ) ) {
          args = "\"" + p.name + "\", " + valuePlaceholder + ", this";
        }
      }
    } else if ( p.type.toLowerCase().equals( "time" ) ) {
      type = "Timepoint";
      parameterTypes = null;
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
        args = "\"" + p.name + "\", " + valuePlaceholder + ", this";
        castType = "Long";
      }
    } else if ( p.type.toLowerCase().startsWith( "int" )
                || p.type.trim().replaceAll( " ", "" )
                         .equals( "Parameter<Integer>" ) ) {
      type = "IntegerParameter";
      parameterTypes = null; // "Integer";
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
        args = "\"" + p.name + "\", " + valuePlaceholder + ", this";
      }
    } else if ( p.type.toLowerCase().equals( "double" )
                || p.type.trim().replaceAll( " ", "" )
                         .equals( "Parameter<Double>" ) ) {
      type = "DoubleParameter";
      parameterTypes = null;
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
        args = "\"" + p.name + "\", " + valuePlaceholder + ", this";
      }
    } else if ( p.type.toLowerCase().equals( "boolean" )
                || p.type.trim().replaceAll( " ", "" )
                         .equals( "Parameter<Boolean>" ) ) {
      type = "BooleanParameter";
      parameterTypes = null;
      // args = "\"" + p.name + "\", this";
      if ( !Utils.isNullOrEmpty( castType ) ) {
        args = "\"" + p.name + "\", " + valuePlaceholder + ", this";
      }
    } else if ( p.type.equals( "String" )
                || p.type.trim().replaceAll( " ", "" )
                         .equals( "Parameter<String>" ) ) {
      type = "StringParameter";
      parameterTypes = null;
      // args = "\"" + p.name + "\", this";
      // } else if ( p.type.startsWith( "TimeVaryingMap" ) ) {
      // args = "\"" + p.name + "\", this";
    } else if ( p.type.startsWith( "TimeVarying" )
        || p.type.trim().replaceAll( " ", "" )
                 .startsWith( "Parameter<TimeVarying" ) ) {
      String ttype = p.type.trim().replaceAll( " ", "" );
      type = "StateVariable";
      parameterTypes = null;
      if (ttype.matches( "Parameter<TimeVarying[^<>]*<[^<>]*>>" ) ) {
        int bpos = ttype.lastIndexOf( '<' + 1 );
        int epos = ttype.lastIndexOf( ">" ) - 1;
        parameterTypes = ttype.substring( bpos, epos );
      }
    }
    String valueArg;
    String castTypeNoParams = "";
    
    if ( Utils.isNullOrEmpty( castType ) ) {
      if (p.value.contains( typePlaceholder )) {
        p.value = "null"; // tried to be smart, but couldn't figure out the type, so give up
      }
    } else {
      if ( evaluateForType ) {
        castTypeNoParams = castType.replaceFirst("<.*>", "");
      } else {
        castTypeNoParams = castType;
      }
      if ( getClassData().isInnerClass( castTypeNoParams ) ) {
        String ctnpEnclosing = getClassData().getEnclosingClassName( castTypeNoParams );
        if ( !getClassData().getCurrentClass().equals( ctnpEnclosing ) &&
             !getClassData().getAllEnclosingClassNames( getClassData().getCurrentClass() ).contains( ctnpEnclosing ) ) {
          // since this is inner to a sibling class, can't decide on the instance right now
          // so set to null and hope someone later sets it to an object
          p.value = "null"; // REVIEW
        }
      }
    }
    p.value = p.value.replace( typePlaceholder, castTypeNoParams );
    // TODO -- REVIEW -- Why is p.value in args by default, but recognized types
    // do not include p.value?
    valueArg = javaToAeExpr( p.value, p.type, true, true, true );
    if ( evaluateForType ) {
      valueArg = "Expression.evaluate(" + valueArg + ", " + typePlaceholder +
                 ".class, true)"; // replacing !TYPE! later
    } else {
      valueArg = "(" + typePlaceholder + ")" + valueArg; // replacing !TYPE! later
    }
    
    if ( Utils.isNullOrEmpty( castType ) ) {
      if ( evaluateForType ) {
        String typePlaceholder2 = typePlaceholder + ".class";
        args = args.replace(typePlaceholder2, "null");
        valueArg = valueArg.replace( typePlaceholder2, "null" );
      } else {
        String typePlaceholder1 = "(" + typePlaceholder + ")";
        args = args.replace(typePlaceholder1, "");
        valueArg = valueArg.replace( typePlaceholder1, "" );
      }
    } else {
      args = args.replace( typePlaceholder, castTypeNoParams );
      valueArg = valueArg.replaceAll( typePlaceholder, castTypeNoParams );
    }
    
    args = args.replace( valuePlaceholder, valueArg );

    // HACK -- TODO
    if ( args.contains( ", new FunctionCall" ) && "Parameter".equals( type ) ) {
      args += ", true";
    }

    ret[ 0 ] = type;
    ret[ 1 ] = parameterTypes;
    ret[ 2 ] = args;
    ret[ 3 ] = valueArg;
    return ret;
  }

  /**
   * Determines the AE translated parameter type, generic parameter type, and
   * arguments.
   * <p>
   * 
   * For example, for p = new Param( "anInt", "int", "17" ), the return value is
   * new Object[]{IntegerParameter.class, null, Arrays.asList(new Object[]{new
   * Expression("anInt"), new Expression(17), currentParameterListener )})};
   * 
   * @param p
   * @param classNameOfParameter
   * @return an array containing the Parameter class, any generic parameter
   *         classes, and arguments as expressions.
   */
  public ClassData.PTA
      convertToEventParameterTypeAndConstructorArguments( ClassData.Param p,
                                                          String classNameOfParameter,
                                                          String enclosingObject ) {
    
    // TODO -- Remove overlap of this and ClassData.convertToParameterTypeAndConstructorArguments()
    
    String[] result = convertToEventParameterTypeAndConstructorArgs( p, classNameOfParameter, enclosingObject );
//    Object ret[] = new Object[ 3 ];
    Class< ? > type = ClassUtils.getClassForName( result[0], null, getClassData().getPackageName(), false );
    Class< ? > parameterType = ClassUtils.getClassForName( result[1], null, getClassData().getPackageName(), false );
    String fooFunc = "fooFunc(" + result[2] + ")";
    Expression expr = parseExpression( fooFunc );
    ArrayList< Object > args = new ArrayList< Object >();
    if ( expr instanceof MethodCallExpr ) {
      List< Expression > argExprs;
      argExprs = ((MethodCallExpr)expr).getArgs();
      for ( Expression arg : argExprs ) {
        args.add( astToAeExpression( arg, false, true, true ) );
      }
    } else {
      Debug.error( true,
                   "Error! convertToEventParameterTypeAndConstructorArguments("
                       + p + ", " + classNameOfParameter + "): Expected "
                       + fooFunc
                       + " to parse as a MethodCallExpr.  Instead, got " + expr );
    }
    ClassData.PTA pta =
        new ClassData.PTA( (Class< ? extends Parameter< ? >>)type,
                           parameterType, args.toArray() );
    return pta;
//    ret[ 0 ] = type;
//    ret[ 1 ] = parameterType;
//    ret[ 2 ] = args;
//    return ret;
  }

  
//  /**
//   * @param className
//   * @param param
//   * @param type
//   * @return a parameter
//   */
//  public < P extends Parameter< ? > > P
//      constructParameter( String className, ClassData.Param param ) {
//    ClassData.PTA ctorArgs =
//        convertToEventParameterTypeAndConstructorArguments( param, className );
//    Class< P > cls = (Class< P >)ctorArgs.paramType;
//    Object[] argumentsA = (Object[])ctorArgs.argArr;
//    ConstructorCall call = new ConstructorCall( null, cls, argumentsA );
//    P parameter = (P)call.evaluate( true );
//    return parameter;
//  }

    /**
     * @param args
     */
    public static void main( String[] args ) {
      // TODO Auto-generated method stub
    }

    /**
     * @return the currentClass
     */
    public String getCurrentClass() {
      if ( getClassData() == null ) return null;
      return getClassData().getCurrentClass();
    }

    /**
     * @return the classData
     */
    public ClassData getClassData() {
        return classData;
    }

    /**
     * @param classData the classData to set
     */
    public void setClassData( ClassData classData ) {
        this.classData = classData;
    }


}
