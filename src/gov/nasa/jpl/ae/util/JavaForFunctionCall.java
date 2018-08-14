package gov.nasa.jpl.ae.util;

import gov.nasa.jpl.ae.event.*;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.CompareUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Utils;
import gov.nasa.jpl.mbee.util.Wraps;
import japa.parser.ast.body.*;
import japa.parser.ast.body.Parameter;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.MethodCallExpr;
import japa.parser.ast.expr.ObjectCreationExpr;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * @author bclement
 *
 */
public class JavaForFunctionCall {
  /**
   * 
   */
  // private final EventXmlToJava xmlToJava;
  protected final JavaToConstraintExpression exprXlator;// .expressionTranslator;

  protected Expression expression = null;
  protected MethodCallExpr methodCallExpr = null;
  protected ObjectCreationExpr objectCreationExpr = null;
  protected Boolean methodOrConstructor = null;
  protected String object = null;
  protected gov.nasa.jpl.ae.event.Expression< ? > objectExpr = null;
  protected String objectTypeName;
  protected Class< ? > objectType;
  protected String className = null;
  protected String callName = null;
  protected Constructor< ? > matchingConstructor = null;
  protected ConstructorDeclaration constructorDecl = null;
  protected Method matchingMethod = null;
  protected MethodDeclaration methodDecl = null;
  protected String methodJava = null; // Java text for getting
                                      // java.reflect.Method
  protected Boolean isEffectFunction = null;
  protected String argumentArrayJava = null;
  protected Vector< Object > args = null; // new Vector<Object>();
  protected boolean convertingArgumentsToExpressions = false;
  protected boolean evaluateCall = false;
  protected String preferredPackageName = null;

  protected Class< ? > returnType;

  protected Call call = null;

  protected Boolean isTimeVarying = null;
  protected Boolean timeVaryingCall = null;

  //private Duration dummyForClassLoader = new Duration();

  // /**
  // * When expressions are passed to functions that are expecting parameters, a
  // * dependency can be formed.
  // */
  // public ArrayList<FieldDeclaration> generatedDependencies =
  // new ArrayList< FieldDeclaration >();

  protected Class< ? >[] argTypes = null;

  public JavaForFunctionCall( JavaToConstraintExpression expressionTranslator,
                              Expression expression,
                              boolean convertArgumentsToExpressions,
                              String preferredPackageName,
                              Class< ? > returnType ) {
    this( expressionTranslator, expression, convertArgumentsToExpressions,
          preferredPackageName, false, returnType );
  }


  public JavaForFunctionCall( JavaToConstraintExpression exprTranslator,
                              Expression expression,
                              boolean convertArgumentsToExpressions,
                              String preferredPackageName, boolean evaluateCall,
                              Class< ? > returnType ) {

    // Arguments may be Expressions, Parameters, or other. Method parameter
    // types may also be Expressions, Parameters, or other.
    //
    // If argument is Expression, and parameter is Parameter,
    // see if the expression is a parameter and pass that instead.
    //
    // If argument is Expression, and parameter is other,
    // pass the evaluation of the Expression.
    //
    // If argument is Parameter, and parameter is Expression,
    // wrap the argument in an Expression.
    //
    // If argument is Parameter, and parameter is other,
    // pass the Parameter's value.
    //
    // If argument is other,
    // wrap the argument in an Expression or Parameter according to the type.
    //
    // If there is a choice of methods, prefer matched types to matches through
    // conversion, except when convertArgumentsToExpressions==true. Prefer those
    // that match Expressions to those that match Parameters and Parameters to
    // other.

    this.returnType = returnType;

    assert expression != null;

    setExpression( expression );
    this.exprXlator = exprTranslator;
    // REVIEW -- How do we know when we want to convert args to Expressions?
    // Constructors of events (and probably classes) convert args to
    // Expressions. For now, do not convert args for any other calls.
    setConvertingArgumentsToExpressions( convertArgumentsToExpressions );
    setEvaluateCall( evaluateCall );

    setPreferredPackageName( preferredPackageName );


    // Assemble Java text for finding the java.reflect.Method for callName
    // TODO -- move most of this to ClassData, instantiate this.call, and
    // use it to generate methodJava text.
    // code below replaced by getMethodJava()


    // code below replaced by isEffectFunction()
    // // Determine whether the function is regular or actually an effect.
    // // For example, TimeVaryingMap.setValue(...) is an effect function, but
    // // TimeVaryingMap.getValue(...) is not.
    // // TODO -- REVIEW -- type is never used! -- is it supposed to be used in
  }

  public < T > T getBestArgTypes( Set< T > declarations,
                                  Class< ? >[] argTypesArr,
                                  String preferredPackageName ) {
    Map< T, Pair< Class< ? >[], Boolean > > candidates =
        new TreeMap< T, Pair< Class< ? >[], Boolean > >( new CompareUtils.GenericComparator< T >() );
    for ( T cd : declarations ) {

      List< Parameter > params = null;
      if ( cd instanceof ConstructorDeclaration ) {
        params = ( (ConstructorDeclaration)cd ).getParameters();
      } else if ( cd instanceof MethodDeclaration ) {
        params = ( (MethodDeclaration)cd ).getParameters();
      }
      boolean gotParams = !Utils.isNullOrEmpty( params );
      int size = gotParams ? params.size() : 0;
      Class< ? >[] ctorArgTypes = new Class< ? >[ size ];
      int ct = 0;
      boolean isVarArgs = false;
      if ( gotParams ) {
        isVarArgs = params.get( size - 1 ).isVarArgs();
        for ( Parameter param : params ) {
          Class< ? > c =
              ClassUtils.getClassForName( param.getType().toString(), null,
                                          preferredPackageName, true );
          ctorArgTypes[ ct++ ] = c;
        }
      }
      candidates.put( cd, new Pair< Class< ? >[], Boolean >( ctorArgTypes,
                                                             isVarArgs ) );
    }
    T decl = ClassUtils.getBestArgTypes( candidates, argTypesArr );
    return decl;
  }

  public Expression getScope() {
    if ( isMethodOrConstructor() == true && getMethodCallExpr() != null ) {
      return getMethodCallExpr().getScope();
    } else if ( isMethodOrConstructor() == false
                && getObjectCreationExpr() != null ) {
      return getObjectCreationExpr().getScope();
    } else if ( getMethodCallExpr() != null ) {
      return getMethodCallExpr().getScope();
    } else if ( getObjectCreationExpr() != null ) {
      return getObjectCreationExpr().getScope();
    }
    return null;
  }

  public boolean isStatic() {
    // HACK!  seems to think it knows whether it's static when it doesn't; just added additional code to try and cover up mistake instead of really fixing it.
    Constructor c = getMatchingConstructor();
    if ( c != null ) {
      return ClassUtils.isStatic(c);
    }
    Method m = getMatchingMethod();
    if ( m != null ) {
      return ClassUtils.isStatic(m);
    }
    if ( isMethodOrConstructor() ) {
      if ( exprXlator.getClassData().knowIfStatic( getCallName() ) ) {
        return exprXlator.getClassData().isStatic( getCallName() );
      }
      if ( getMatchingMethod() != null
           && Modifier.isStatic( getMatchingMethod().getModifiers() ) ) {
        return true;
      }
      if ( getMethodDecl() != null
           && ModifierSet.isStatic( getMethodDecl().getModifiers() ) ) {
        return true;
      }
    } else {
      // FIXME -- seems to think it knows whether it's static when it doesn't for a constructor
      if ( exprXlator.getClassData().knowIfClassIsStatic( getCallName() ) ) {
        return exprXlator.getClassData().isClassStatic( getCallName() );
      }
      if ( getMatchingConstructor() != null
           && Modifier.isStatic( getMatchingConstructor().getModifiers() ) ) {
        return true;
      }
      if ( getConstructorDecl() != null
           && ModifierSet.isStatic( getConstructorDecl().getModifiers() ) ) {
        return true;
      }
    }
    return Utils.isNullOrEmpty( getObject() );
  }

  public Call toNewFunctionCall(boolean complainIfNotFound) {
    Call c = getCall( complainIfNotFound );
    if ( Debug.isOn() ) Debug.outln( "JavaForFunctionCall.toNewFunctionCall() --> "
                                     + c );
    return c;
  }

  public String getReturnTypeString() {
    String s = returnType == null ? "(Class<?>)null" : returnType + ".class";
    return s;
  }

  public boolean isATimeVaryingCall() {
    if ( hasUnexpectedTimeVaryingObject() ) {
      //System.out.println("WWWWWWWWWWWWWWWWWWWW    IS TIMEVARYING CALL: " + expression + "   WWWWWWWWWWWWWWWWWWWW");
      return true;
    }
    if ( hasUnexpectedTimeVaryingArgs() ) {
      //System.out.println("WWWWWWWWWWWWWWWWWWWW    IS TIMEVARYING CALL: " + expression + "   WWWWWWWWWWWWWWWWWWWW");
      return true;
    }
    //System.out.println("WWWWWWWWWWWWWWWWWWWW    IS NOT TIMEVARYING CALL: " + expression + "   WWWWWWWWWWWWWWWWWWWW");
    return false;
  }

  public String getCallTypeName() {
    // call getMatchingMethod() to make find out if it's a TimeVaryingFunctionCall
    Method m = getMatchingMethod();
    Constructor c = getMatchingConstructor();

    if ( m == null && c != null ) setMethodOrConstructor( false );
    else if ( c == null && m != null ) setMethodOrConstructor( true );

    //String prefix = (Boolean.TRUE.equals((getTimeVaryingCall())) ? "TimeVarying" : "");
    String prefix = (isATimeVaryingCall() ? "TimeVarying" : "");
    if ( !isMethodOrConstructor() ) {
      return prefix + "ConstructorCall";
    }
    if ( isEffectFunction() ) {
      if ( Boolean.TRUE.equals(timeVaryingCall) ) {
        Debug.error("TimeVarying EffectFunction not supported!!! " + expression);
      }
      return "EffectFunction";
    }
    return prefix + "FunctionCall";
  }

  public String toNewFunctionCallString() {
    String fcnCallStr = null;
    String instance = getObject();
    try {
      if (isStatic()) {
        instance = "null";
      } else if (instance != null && Package.getPackage(instance) != null) {
        instance = "null";
      }
    } catch(Exception e){
      if(Debug.isOn() ) Debug.outln(e.getMessage());
    }
    String mJava = getMethodJava();
    String callTypeName = getCallTypeName();

    fcnCallStr =
        "new " + callTypeName + "( " + instance + ", " + mJava + ", "
                 + getArgumentArrayJava() + ", " + getReturnTypeString() + " )";
    // }
    if ( isEvaluateCall() && !Utils.isNullOrEmpty( fcnCallStr ) ) {
      if ( !isConvertingArgumentsToExpressions() ) {
        fcnCallStr = "Expression.evaluate(" + fcnCallStr + ", null, true)";
      }
    }
    if ( Debug.isOn() ) Debug.outln( "JavaForFunctionCall.toNewFunctionCallString() --> "
                                     + fcnCallStr );
    return fcnCallStr;
  }

  public String toNewExpressionString() {
    String s = "new Expression( " + toNewFunctionCallString() + " )";
    if ( Debug.isOn() ) Debug.outln( "JavaForFunctionCall.toNewExpressionString() --> "
                                     + s );
    return s;
  }

  @Override
  public String toString() {
    return "" + expression; //toNewFunctionCallString();
  }

  public < T > gov.nasa.jpl.ae.event.Expression< T > toNewExpression(boolean complainIfNotFound) {
    gov.nasa.jpl.ae.event.Expression< T > r =
        new gov.nasa.jpl.ae.event.Expression< T >( toNewFunctionCall(complainIfNotFound) );
    if ( Debug.isOn() ) Debug.outln( "JavaForFunctionCall.toNewExpression() --> "
                                     + r );
    return r;
  }

  public Expression getExpression() {
    return expression;
  }

  public void setExpression( Expression expression ) {
    if ( this.expression != expression ) {
      setObjectExpr( null );
      setObjectTypeName( null );
      setObjectType( null );

      this.expression = expression;

      // detect the case where an expression like foo = Bar(a :: b, c :: d)
      // turns into a MethodCall of Bar, when it should have been a constructor
      // WARNING - this will break the case where a class 
//      boolean isConstructorLikeMethod =
//          expression instanceof MethodCallExpr &&
//          exprXlator.getClassData().isClassName( ((MethodCallExpr)expression).getName() );
//      boolean isMethodCall = !isConstructorLikeMethod && expression instanceof MethodCallExpr;
//      boolean isConstructorCall = isConstructorLikeMethod || expression instanceof ObjectCreationExpr;
      boolean isMethodCall = expression instanceof MethodCallExpr;
      boolean isConstructorCall = expression instanceof ObjectCreationExpr;
      setMethodOrConstructor( isMethodCall ? true
                                           : isConstructorCall ? false : null );
      setMethodCallExpr( isMethodCall ? (MethodCallExpr)expression : null );
      setObjectCreationExpr( isConstructorCall ? (ObjectCreationExpr)expression
                                               : null );
      if ( !isMethodCall && !isConstructorCall ) {
        Debug.error( "Expression " + expression
                     + " must be a MethodCallExpr or an ObjectCreationExpr!" );
      }
    }
  }

  /**
   * @return the methodCallExpr
   */
  public MethodCallExpr getMethodCallExpr() {
    return methodCallExpr;
  }

  /**
   * @param methodCallExpr
   *          the methodCallExpr to set
   */
  public void setMethodCallExpr( MethodCallExpr methodCallExpr ) {
    if ( this.methodCallExpr != methodCallExpr ) {
      setCallName( null );
      setArgumentArrayJava( null );
      setCall( null );
      this.methodCallExpr = methodCallExpr;
    }
  }

  /**
   * @return the objectCreationExpr
   */
  public ObjectCreationExpr getObjectCreationExpr() {
    return objectCreationExpr;
  }

  /**
   * @param objectCreationExpr
   *          the objectCreationExpr to set
   */
  public void setObjectCreationExpr( ObjectCreationExpr objectCreationExpr ) {
    if ( this.objectCreationExpr != objectCreationExpr ) {
      setCallName( null );
      setArgumentArrayJava( null );
      setCall( null );
      this.objectCreationExpr = objectCreationExpr;
    }
  }


//  public Boolean getIsTimeVarying() {
//    if ( has)
//    if ( isTimeVarying == null ) {
//      isTimeVarying =
//              ( getObjectType() != null &&
//                TimeVaryingMap.class.isAssignableFrom(getObjectType()) ) ||
//              ( getObjectTypeName() != null &&
//                getObjectTypeName().contains("TimeVarying") &&
//                !getObjectTypeName().contains("Call") );
//      if ( !isTimeVarying ) {
//        isTimeVarying = hasUnexpectedTimeVaryingArgs();
//      }
//    }
//    return isTimeVarying;
//  }

  public boolean hasUnexpectedTimeVaryingObject() {
    boolean mOrC = isMethodOrConstructor();
    Member member = mOrC ? getMatchingMethod() : getMatchingConstructor();
    // TODO -- This only works on pre-existing classes.  Need to make this work for classes being parsed in getExprXlator().getClassData().
    if ( member == null ) {
      return false;
    }
    if ( isStatic() ) {
      return false;
    }
    Class<?> t = getObjectType();
    if ( t == null || !TimeVarying.class.isAssignableFrom( t ) ) {
      return false;
    }
    if ( mOrC ) {
      if ( !TimeVarying.class.isAssignableFrom( member.getDeclaringClass() ) ) {
        return true;
      }
    } else {
      Class<?> cls = getMatchingConstructor().getDeclaringClass();
      Class<?> enclosingClass = cls.getEnclosingClass();
      if ( enclosingClass != null && !TimeVarying.class.isAssignableFrom(enclosingClass) ) {
        return true;
      }
    }
    return false;
  }

  public boolean hasUnexpectedTimeVaryingArgs() {
    Class<?>[] types = getArgTypes();
    boolean mOrC = isMethodOrConstructor();
    Member member = mOrC ? getMatchingMethod() : getMatchingConstructor();
    if ( member == null || ( DurativeEvent.class.isAssignableFrom(member.getDeclaringClass()) &&
                             member.getName().contains("elaborate") ) ) {
      return false;
    }
    Class<?>[] paramTypes = mOrC ? ((Method)member).getParameterTypes() : ((Constructor)member).getParameterTypes();
    int i = 0, j = 0;
    if ( types == null || types.length == 0 || paramTypes == null || paramTypes.length == 0 ) {
      return false;
    }
    while ( i < types.length ) {
      Class<?> type = types[i];
      Class<?> pType = paramTypes[j];
      if ( type != null && pType != null &&
           TimeVarying.class.isAssignableFrom( type ) &&
           !TimeVarying.class.isAssignableFrom( pType ) ) {
        return true;
      }
      ++i;
      if ( j < paramTypes.length - 1 ) ++j;
    }
    return false;

  }

//  public void setTimeVarying(Boolean timeVarying) {
//    isTimeVarying = timeVarying;
//  }

  public Class<?> getWrappedType() {
    if ( getObjectType() != null ) {
      if ( Wraps.class.isAssignableFrom( getObjectType() ) ) {
        Class<?> genericType = ClassUtils.getSingleGenericParameterType( getObjectType() );
        return genericType;
      }
    }
    return null;
  }

//  public Boolean getTimeVaryingCall() {
//    return this.timeVaryingCall;
//  }
//  public void setTimeVaryingCall(Boolean timeVaryingCall) {
//    this.timeVaryingCall = timeVaryingCall;
//  }

  /**
   * @return the methodOrConstructor
   */
  public Boolean isMethodOrConstructor() {
    return methodOrConstructor;
  }

  /**
   * @param methodOrConstructor
   *          the methodOrConstructor to set
   */
  public void setMethodOrConstructor( Boolean methodOrConstructor ) {
    this.methodOrConstructor = methodOrConstructor;
  }

  public MethodDeclaration getMethodDeclInClass( String className ) {
    // Get the list of methods with the same name (callName).
    Set< MethodDeclaration > classMethods =
        this.exprXlator.getClassData().getClassMethodsWithName( getCallName(),
                                                                className );
    // Find alternative classes that have these methods.
    if ( isATimeVaryingCall() ) {
    //if ( getIsTimeVarying() != null && getIsTimeVarying() ) {
      Class<?> cls = getWrappedType();
      if ( cls != null ) {
        String clsName = cls.getCanonicalName();
        if ( clsName == null ) {
          clsName = cls.getName();
        }
        Set<MethodDeclaration> moreClassMethods =
                this.exprXlator.getClassData().getClassMethodsWithName(getCallName(),
                        clsName);
        if ( moreClassMethods != null ) {
          //System.out.println("WWWWWWWWWWWWWW   MORE CLASS METHODS  WWWWWWWWWWWWWW");
          if ( classMethods == null ) {
            classMethods = moreClassMethods;
          } else {
            classMethods.addAll(moreClassMethods);
          }
        }
      }
    }

    // Find the right MethodDeclaration if it exists.
    MethodDeclaration method = null;
    if ( !Utils.isNullOrEmpty( classMethods ) ) {
      method = getBestArgTypes( classMethods, getArgTypes(),
                                getPreferredPackageName() );
      if ( method == null ) {
        // Warning just grabs the first method of this name!
        // if ( classMethods.size() > 1 ) {
        // System.err.println( "Warning! " + classMethods.size()
        // + " methods with name " + getCallName() + " in "
        // + getClassName() + ": just grabbing the first!" );
        // }
        // Add vector of argument types to getMethod() call
      }
    }

    return method;
  }

  public String findClassNameWithMatchingMethod() {
    Debug.out("findClassNameWithMatchingMethod()");

    // First see if what we already have is correct.
    if ( this.matchingMethod != null && this.className != null ) {
      // Do what should be fast memory reference comparisons, and only do string comparisons if they fail.
      if ( this.className == matchingMethod.getDeclaringClass().getSimpleName() ) {
        return this.className;
      }
      if ( this.className == matchingMethod.getDeclaringClass().getName() ) {
        return this.className;
      }
      String cName = matchingMethod.getDeclaringClass().getCanonicalName();
      if ( this.className == cName ) {
        return this.className;
      }
      // The expensive string operation:
      if ( ClassUtils.noParameterName(cName).contains(ClassUtils.noParameterName(className)) ) {
        return className;
      }
    }

    // Now check in parsed class data.
    String s = null;
    // Check if method is in class
    MethodDeclaration mDecl = getMethodDeclInClass( getClassName() );
    if ( mDecl != null ) {
      if ( methodDecl == null ) {
        methodDecl = mDecl;
      }
      String cName = exprXlator.getClassData().getClassNameForMethodDeclaration( mDecl );
      if ( cName != null ) {
        return cName;
      }
    }
    if ( mDecl != null ) {
      // REVIEW -- should never get here
      Debug.out("findClassNameWithMatchingMethod(): returning this");
      return "this";
    }

    // Try lookupMethodByName.
    Map<String, List<Object>> decls = exprXlator.getClassData().lookupMethodByName(className,
                                                                                   getCallName(),
                                                                                   true,
                                                                                   false);
    if ( !Utils.isNullOrEmpty( decls ) ) {
      className = decls.keySet().iterator().next();
      if ( !Utils.isNullOrEmpty(className) ) {
        return className;
      }
    }
    // TODO -- REVIEW -- return here?  Will code below ever find anything that lookupMethodByName() didn't?

    // Try enclosing class
    String prevClassName = getClassName();
    String className =
        exprXlator.getClassData().getEnclosingClassName( prevClassName );
    while ( className != null ) {
      if ( getMethodDeclInClass( className ) != null ) {
        Debug.out("findClassNameWithMatchingMethod(): returning " + className);
        return className;
      }
      prevClassName = className;
      className =
          exprXlator.getClassData().getEnclosingClassName( prevClassName );

    }

    // Now check in Java libraries.
    Collection<Class<?>> types = Utils.arrayAsList(getArgTypes());
    Call c = searchForCall(getCallName(), getArgs(), types);
    if ( c != null && c.getMember() != null ) {
      className = c.getMember().getDeclaringClass().getCanonicalName();
      setClassName(className);
      if ( matchingMethod == null && c.getMember() instanceof Method ) {
        setMatchingMethod((Method)c.getMember());
      }
      if ( this.call == null ) setCall( call );
      return this.className;
    }

    Debug.out("findClassNameWithMatchingMethod(): no match; returning " + s);

    return s;

  }
  
  public String fullClassName( String entityName ) {
    String className = entityName;
    entityName = exprXlator.getClassData().getNestedToEnclosingClassNames()
                           .get( entityName );
    while ( entityName != null ) {
      className = entityName + "." + className;
      entityName = exprXlator.getClassData().getNestedToEnclosingClassNames()
                             .get( entityName );
    }
    return className;

}

  /**
   * @return the object
   */
  public String getObject() {

    if ( Utils.isNullOrEmpty( object ) ) {
      Debug.out("getObject()");

      object =
          ( (JavaToConstraintExpression)this.exprXlator ).getObjectFromScope( getScope() );
      if ( Utils.isNullOrEmpty( object ) ) {
        // if ( isMethodOrConstructor() ||
        // exprXlator.getClassData().isInnerClass( getObjectTypeName() ) ) {
        if ( isMethodOrConstructor() ) {
          String className = findClassNameWithMatchingMethod();
          if (className == null ) {
            // Check and see if it's really a constructor.
            if ( exprXlator.getClassData().isClassName( getCallName() ) ) {
              setMethodOrConstructor( false );
              // Try again now as a constructor.
              return getObject();
            }
          } else if (className.equals("this")) {
            setObject( className );
          } else if ( this.matchingMethod != null && ClassUtils.isStatic(this.matchingMethod) ) {
            setObject("null");
          } else if ( exprXlator.getClassData().isMemberStatic( className, getCallName() ) ) {
            setObject("null");
          } else if ( exprXlator.getClassData().getAllEnclosingClassNames(this.className).contains(className) ) {
              setObject(className + ".this");
          } else {
            // a Java superclass of a parsed class, like DurativeEvent
            setObject("this");
          }

//        } else if ( getMethodCallExpr() != null ) {
//          setObject( "this" );
        } else if ( exprXlator.getClassData() //need to fix this whole stuff
                                 .isInnerClass( getCallName() ) ) {
          String enclosingClass = exprXlator.getClassData()
                  .getEnclosingClassName( getCallName() );
          if ( enclosingClass.equals( getClassName() ) ) {
            setObject( "this" );
          } else {

//            setObject("null");
//            // trim this down to most specific enclosing class:
//            while ( enclosingClass != null && !getClassName().startsWith(enclosingClass) ) {
//              enclosingClass = getExprXlator().getClassData().getEnclosingClassName( enclosingClass );
//            }

// This was a merge conflict.  The commented out lines above were probably brad
// being scared of change, and the ones below are hopefully David fixing the
// problem that scared Brad.

            // walk up this' enclosing chain, looking for the correct enclosing class
            String enclosingThis = exprXlator.getClassData().getEnclosingClassName( getClassName() );
            while (!Utils.isNullOrEmpty( enclosingThis ) && !enclosingThis.equals( enclosingClass )) {
              enclosingThis = exprXlator.getClassData().getEnclosingClassName( enclosingThis );
            }

            if ( Utils.isNullOrEmpty( enclosingClass ) ) {
              setObject("null");
            } else {
              // REVIEW -- This wouldn't work if the enclosing class was part of "this"
              // so we should make sure it's not possible to reach this case.
              setObject( enclosingClass + ".this" );
            }
          }
        } else {
          setObject( "null" );
        }
      }
      Debug.out("getObject(): returning " + object);
    }
    return object;
  }

  /**
   * @param object
   *          the object to set
   */
  public void setObject( String object ) {
    this.object = object;
  }

  /**
   * @return the objectExpr
   */
  public gov.nasa.jpl.ae.event.Expression< ? > getObjectExpr() {
    if ( objectExpr == null ) {
      Object tmp =
          ( (JavaToConstraintExpression)this.exprXlator ).getObjectExpressionFromScope( getScope() );
      if ( tmp instanceof Expression ) {
        setObjectExpr( (gov.nasa.jpl.ae.event.Expression< ? >)tmp );
      } else {
        if ( tmp != null ) {
          setObjectExpr( new gov.nasa.jpl.ae.event.Expression< Object >( tmp ) );
        } else {
          if ( Debug.isOn() ) Debug.errln( "Warning!  JavaToFunctionCall.getCall() is having to parse the object text, \""
                                           + getObject()
                                           + "\" to create the expression of the caller!" );
          setObjectExpr( exprXlator.javaToAeExpression( getObject(),
                                                        getObjectTypeName(),
                                                        false ) );
        }
      }
    }
    return objectExpr;
  }

  /**
   * @param objectExpr
   *          the objectExpr to set
   */
  public void
         setObjectExpr( gov.nasa.jpl.ae.event.Expression< ? > objectExpr ) {
    if ( this.objectExpr != objectExpr ) {
      setCall( null );
      this.objectExpr = objectExpr;
    }
  }

  /**
   * @return the objectTypeName
   */
  public String getObjectTypeName() {
    if ( objectTypeName == null ) {
      String typeName = this.exprXlator.astToAeExprType( getScope(), null,
              true, true );
      setObjectTypeName( typeName );
    }
    return objectTypeName;
  }

  /**
   * @param objectTypeName
   *          the objectTypeName to set
   */
  public void setObjectTypeName( String objectTypeName ) {
    if ( this.objectTypeName != objectTypeName ) {
      setClassName( null );
      setObject( null );
      setEffectFunction( null );

      this.objectTypeName = objectTypeName;
    }
  }

  /**
   * @return the objectType
   */
  public Class< ? > getObjectType() {
    if ( objectType == null ) {
      if ( !Utils.isNullOrEmpty( getClassName() )
           && getClassName() != exprXlator.getCurrentClass() ) {
        setObjectType( ClassUtils.getClassForName( getClassName(),
                                                   getCallName(),
                                                   getPreferredPackageName(),
                                                   true ) );
      }
    }
    return objectType;
  }

  /**
   * @param objectType
   *          the objectType to set
   */
  public void setObjectType( Class< ? > objectType ) {
    this.objectType = objectType;
  }

  /**
   * @return the className
   */
  public String getClassName() {
    if ( Utils.isNullOrEmpty( className ) ) {
      if ( !Utils.isNullOrEmpty( getObjectTypeName() ) ) {
        setClassName( getObjectTypeName() );
      } else {
        setClassName( this.exprXlator.getCurrentClass() );
        boolean complain = false;
        Method m = ClassUtils.getMethodForArgTypes(className, getPreferredPackageName(),
                                                   getCallName(), complain,
                                                   getArgTypes(complain));
        if (m != null && this.matchingMethod == null ) this.matchingMethod = m;
        if ( m == null ) {
          Collection<Class<?>> types = Utils.arrayAsList(getArgTypes());
          Call c = searchForCall(getCallName(), getArgs(), types);
          if ( c != null && c.getMember() != null ) {
            setClassName(c.getMember().getDeclaringClass().getCanonicalName());
            if ( matchingMethod == null && c.getMember() instanceof Method ) {
              setMatchingMethod((Method)c.getMember());
            }
            if ( this.call == null ) setCall( c );
          }
        }
      }
      if ( Utils.isNullOrEmpty( className ) ) {
        System.err.println( "Couldn't find the class for method "
                + callName
                + ( argTypes == null ? "" : Utils.toString( argTypes, false ) ) );
      }
    }
    return className;
  }

  /**
   * @param className
   *          the className to set
   */
  public void setClassName( String className ) {
    if ( this.className != className ) {
      setObjectType( null );
      setMatchingMethod( null );
      setMatchingConstructor( null );
      setMethodDecl( null );
      setConstructorDecl( null );
      this.className = className;
    }
  }

  /**
   * @return the callName
   */
  public String getCallName() {
    if ( callName == null ) {
      setCallName( getMethodCallExpr() != null ? getMethodCallExpr().getName()
                                               : getObjectCreationExpr().getType()
                                                                        .toString() );
    }
    return callName;
  }

  /**
   * @return the callName
   */
  public String getConstructorCallNameWithScope() {
    String c = getCallName();
    if ( Utils.isNullOrEmpty( c ) ) return null;
    String cws = getExprXlator().getClassData().getClassNameWithScope( c );
    if ( !Utils.isNullOrEmpty( cws ) && !cws.equals( c ) ) {
      return cws;
    }
    return c;
  }

  /**
   * @param callName
   *          the callName to set
   */
  public void setCallName( String callName ) {
    if ( this.callName != callName ) {
      setObjectType( null );
      setMatchingMethod( null );
      setMatchingConstructor( null );
      setMethodDecl( null );
      setConstructorDecl( null );
      this.callName = callName;
    }
  }

  /**
   * @return the matchingConstructor
   */
  public Constructor< ? > getMatchingConstructor() {
    if ( matchingConstructor == null ) {
      // Try using reflection to find the method, but class may not exist.
      Constructor<?> c = ClassUtils.getConstructorForArgTypes(getCallName(), getArgTypes(),
              getPreferredPackageName(), false);
      setMatchingConstructor( c );
    }
    return matchingConstructor;
  }

  /**
   * @param matchingConstructor
   *          the matchingConstructor to set
   */
  public void setMatchingConstructor( Constructor< ? > matchingConstructor ) {
    if ( this.matchingConstructor != matchingConstructor ) {
      setCall( null );
      this.matchingConstructor = matchingConstructor;
    }
  }

  /**
   * @return the constructorDecl
   */
  public ConstructorDeclaration getConstructorDecl() {
    if ( constructorDecl == null ) {
      // Find the right ConstructorDeclaration if it exists.
      Set< ConstructorDeclaration > ctors =
          ( exprXlator == null ? null
                               : ( exprXlator.getClassData() == null ? null
                                                                     : exprXlator.getClassData()
                                                                                 .getConstructors( getCallName() ) ) );
      constructorDecl = null;
      if ( !Utils.isNullOrEmpty( ctors ) ) {
//        Class< ? >[] argTypes = getArgTypes();
//        if (argTypes == null) argTypes = new Class< ? >[0];
//        if ( getExprXlator().getClassData().isInnerClass( callName ) ) {
//          Class< ? >[] newArgTypes = new Class< ? >[ argTypes.length + 1 ];
//          String encName = getExprXlator().getClassData().getEnclosingClassName( callName );
////          newArgTypes[0] = getExprXlator().getClassData().getAeClass( encName, false ).getClass();
//          newArgTypes[0] = getExprXlator().getClassData().
//          for ( int i = 0; i < argTypes.length; ++i ) {
//            newArgTypes[i + 1] = argTypes[i];
//          }
//          argTypes = newArgTypes;
//        }
        constructorDecl =
            getBestArgTypes( ctors, getArgTypes(), getPreferredPackageName() );
        if ( constructorDecl == null ) {
          constructorDecl = ctors.iterator().next();
          // Warning just grabs the first constructor!
          if ( ctors.size() > 1 ) {
            System.err.println( "Warning! " + ctors.size()
                                + " constructors for " + getCallName()
                                + ": just grabbing the first!" );
          }
        }
        assert ( constructorDecl != null );
      }
    }
    return constructorDecl;
  }

  /**
   * @param constructorDecl
   *          the constructorDecl to set
   */
  public void setConstructorDecl( ConstructorDeclaration constructorDecl ) {
    if ( this.constructorDecl != constructorDecl ) {
      setMethodJava( null );
      this.constructorDecl = constructorDecl;
    }
  }

  /**
   * @return the matchingMethod
   */
  public Method getMatchingMethod() {
    Debug.out("getMatchingMethod()");
    if ( matchingMethod != null ) {
      Debug.out("getMatchingMethod(): returning " + matchingMethod);
      return matchingMethod;
    }
    // Try using reflection to find the method, but class may not exist.
    Method m1 = ClassUtils.getMethodForArgTypes(getClassName(),
                                               getPreferredPackageName(),
                                               getCallName(),
                                               getArgTypes(false),
                                               false);
    Debug.out("getMatchingMethod(): m1 = " + m1);
    Member mm = m1;

    // Compare
//      if ( getIsTimeVarying() != null && getIsTimeVarying() ) {
      Class<?> cls = getWrappedType();
      String clsName = cls == null ? null : cls.getCanonicalName();
      if ( clsName == null  && cls != null ) {
        clsName = cls.getName();
      }
     // System.out.println("WWWWWWWWWWWWWWWWWWWW    Wrapped Type = " + clsName + "   WWWWWWWWWWWWWWWWWWWW");

      Collection<Class<?>> types = Utils.arrayAsList(getArgTypes());
      Call call = searchForCall(getCallName(), null, types);
      Member m2 = call == null ? null : call.getMember();
//                ClassUtils.getMethodForArgTypes(clsName,
//                getPreferredPackageName(),
//                getCallName(),
//                getArgTypes(),
//                false);
      Debug.out("getMatchingMethod(): m2 = " + m2);
      Member[] methods = new Member[]{m1, m2};
      ClassUtils.ArgTypeCompare atc = new ClassUtils.ArgTypeCompare(null, null, getArgTypes());
      String callNameNoParams = ClassUtils.noParameterName(ClassUtils.simpleName(callName));
      if (methods != null) {
        for (Member m : methods) {
          if (m != null && m.getName() != null ) {
            String mNameNoParams = ClassUtils.noParameterName(ClassUtils.simpleName(m.getName()));

            if ( mNameNoParams.equals(callNameNoParams) || mNameNoParams.endsWith( "$" + m.getName() ) ) {
            //if ( m.getName().equals(callName) ) {
              Class<?>[] params = m instanceof Method ? ((Method)m).getParameterTypes() : ((Constructor<?>)m).getParameterTypes();
              boolean isVarArgs = m instanceof Method ? ((Method)m).isVarArgs() : ((Constructor<?>)m).isVarArgs();
              atc.compare(m, params, isVarArgs);
            }
          } else {
            //System.out.println("WWWWWWWWWWWWWWWWWWWW    method has no name!!! " + m + "    WWWWWWWWWWWWWWWWWWWW");
          }
        }
      }
      if ( atc.best != null ) {
        mm = (Member) atc.best;
      } else if ( m1 != null ) {
        mm = m1;
      } else if ( m2 != null ) {
        mm = m2;
      }
//      if ( mm != m1 && mm == m2 ) {
//        System.out.println("WWWWWWWWWWWWWWWWWWWW    IS TIMEVARYING CALL: " + expression + "   WWWWWWWWWWWWWWWWWWWW");
//        setTimeVaryingCall(true);
//      } else {
//        System.out.println("WWWWWWWWWWWWWWWWWWWW    IS NOT TIMEVARYING CALL: " + expression + "   WWWWWWWWWWWWWWWWWWWW");
//      }
//      if ( getIsTimeVarying() != null && getIsTimeVarying() ) {
//        setTimeVaryingCall(true);
//      }
    //}

    if ( mm instanceof Method ) {
      setMatchingMethod((Method)mm);
    } else {
      setMatchingConstructor((Constructor<?>)mm);
    }
    Debug.out("getMatchingMethod(): returning " + matchingMethod);

    return matchingMethod;
  }

  /**
   * @param matchingMethod
   *          the matchingMethod to set
   */
  public void setMatchingMethod( Method matchingMethod ) {
    Debug.out("setMatchingMethod(" + matchingMethod + ")");

    if ( this.matchingMethod != matchingMethod ) {
      setCall( null );
      this.matchingMethod = matchingMethod;
    }
  }

  /**
   * @return the methodDecl
   */
  public MethodDeclaration getMethodDecl() {
    if ( methodDecl == null ) {
      // Get the list of methods with the same name (callName).
      Set< MethodDeclaration > classMethods =
          this.exprXlator.getClassData()
                         .getClassMethodsWithName( getCallName(),
                                                   getClassName() );
      // Find the right MethodDeclaration if it exists.
      if ( !Utils.isNullOrEmpty( classMethods ) ) {
        methodDecl = getBestArgTypes( classMethods, getArgTypes(),
                                      getPreferredPackageName() );
        if ( methodDecl == null ) {
          // Warning just grabs the first method of this name!
          if ( classMethods.size() > 1 ) {
            System.err.println( "Warning! " + classMethods.size()
                                + " methods with name " + getCallName() + " in "
                                + getClassName()
                                + ": just grabbing the first!" );
          }
          // Add vector of argument types to getMethod() call
          methodDecl = classMethods.iterator().next();
        }
        assert ( methodDecl != null );
      }
    }
    Debug.out("getMethodDecl(): returning " + methodDecl);

    return methodDecl;
  }

  /**
   * @param methodDecl
   *          the methodDecl to set
   */
  public void setMethodDecl( MethodDeclaration methodDecl ) {
    if ( this.methodDecl != methodDecl ) {
      setMethodJava( null );
      this.methodDecl = methodDecl;
    }
  }

  public List< Expression > getArgExpressions() {
    List< Expression > argExprs = null;
    // Can't use isMethodOrConstructor() here because sometimes a constructor is
    // parsed as a method (when there is no new).
    if ( this.expression instanceof MethodCallExpr ) {
      argExprs = getMethodCallExpr().getArgs();
    } else {
      argExprs = getObjectCreationExpr().getArgs();
    }
    return argExprs;
  }

  public Class< ? >[] getArgTypes() {
    return getArgTypes( true );
  }
  public Class< ? >[] getArgTypes(boolean complainIfNotFound) {
    if ( argTypes == null ) initArgs(false);
    if ( argTypes == null && complainIfNotFound && !Utils.isNullOrEmpty( getArgExpressions() )) {
      Debug.error( true, true, "Error! Could not get argument types for Call: " + expression );
    }
    return argTypes;
  }

  protected void initArgs() {
    initArgs( true );
  }

  protected void initArgs(boolean complainIfNotFound) {
    setArgTypes( null );
    setArgs( new Vector< Object >() );

    List< Expression > argExprs = getArgExpressions();
    if ( argExprs != null ) {
      int numArgs = argExprs.size();
      // REVIEW - this is an attempt to make the init args fn smarter at injecting the enclosing object
      // TODO - Actually implement this, or fix all of the places where the enclosing object should have been given to this, rather than injecting it
//      if ( !methodOrConstructor &&
//           exprXlator.getClassData().isInnerClass( callName ) &&
//           exprXlator.getClassData().getAllEnclosingClassNames( callName ).contains( exprXlator.getClassData().getCurrentClass() )) {
//        // Inject enclosing class arg
//        numArgs++;
//        String encName = exprXlator.getClassData().getEnclosingClassName( callName );
//        if (encName.equals( exprXlator.getClassData().getCurrentClass() )) {
//          encName = "this";
//        } else {
//          encName += ".this";
//        }
//        argExprs.add( 0, JavaToConstraintExpression.parseExpression( encName ) );
//      }
      argTypes = new Class< ? >[ numArgs ];
      for ( int i = 0; i < numArgs; ++i ) {
        String argClassName = exprXlator.astToAeExprType( argExprs.get( i ), null, true, complainIfNotFound);
        List< Class<?>> classList = ClassUtils.getClassesForName( argClassName, false);
        if (classList != null && classList.size() > 1) {
          boolean containsJavaPrimitive = false;
          boolean restScala = true;
          Class<?> javaPrimitiveClass = null;
          for (Class<?> c : classList) {
            if (ClassUtils.isPrimitive( c ) && c.toString().contains( "java" )) {
              containsJavaPrimitive = true;
              javaPrimitiveClass = c;
            } else if (!(c.toString().toLowerCase().contains( "scala" ))) {
              restScala = false;
            }
          }
          if (containsJavaPrimitive && restScala) {
            argTypes[ i ] = javaPrimitiveClass;
          }
        } else {
          argTypes[ i ] =
              ClassUtils.getClassForName( argClassName,
                                          null, getPreferredPackageName(),
                                          false );
        }
        Object arg =
            exprXlator.astToAeExpression( argExprs.get( i ),
                                          ClassUtils.toString( argTypes[ i ] ),
                                          null,
                                          isConvertingArgumentsToExpressions(),
                                          true, false, complainIfNotFound, isEvaluateCall() );
        if ( isConvertingArgumentsToExpressions()
             && !( arg instanceof gov.nasa.jpl.ae.event.Expression ) ) {
          arg = new gov.nasa.jpl.ae.event.Expression( arg );
        }
        this.args.add( arg );
      }
    }
  }

  private void setArgTypes( Class< ? >[] argTypes ) {
    if ( this.argTypes != argTypes ) {
      setMethodJava( null );
      setArgumentArrayJava( null );
      setMatchingMethod( null );
      setMatchingConstructor( null );
      setMethodDecl( null );
      setConstructorDecl( null );
      this.argTypes = argTypes;
    }
  }

  /**
   * @return the methodJava
   */
  public String getMethodJava() {
    if ( Utils.isNullOrEmpty( methodJava ) ) {
      Debug.out("getMethodJava()" );

      // call getMatchingMethod() to make find out if it's a TimeVaryingFunctionCall
      Method m = getMatchingMethod();
      Constructor c = getMatchingConstructor();

      if ( m == null && c == null ) {
        if ( getExprXlator().getClassData().isClassName(getCallName()) ) {
          setMethodOrConstructor( false );
        }
      } else if ( m == null && c != null ) setMethodOrConstructor( false );
      else if ( c == null && m != null ) setMethodOrConstructor( true );

      StringBuffer methodJavaSb = new StringBuffer();
      if ( isMethodOrConstructor() ) {
        String classNameString;
        if ( Utils.isNullOrEmpty( getClassName() ) ) {
          classNameString = "null";
        } else {
          if ( isATimeVaryingCall() ) {
          //if ( Boolean.TRUE.equals(getTimeVaryingCall()) ) {
            if ( m != null ) {
              classNameString = m.getDeclaringClass().getCanonicalName();
            } else if ( getWrappedType() != null ) {
              classNameString = getWrappedType().getSimpleName();
            } else {
              classNameString = null;
            }
          } else {
            classNameString = findClassNameWithMatchingMethod();
            if ( classNameString == null ) {
              classNameString = className;
            }
          }
          if ( classNameString == null ) {
            classNameString = "null";
          } else if (classNameString.equals("this")) {
            classNameString = "\"" + getClassName() + "\"";
          } else {
            classNameString = "\"" + classNameString + "\"";
          }
        }
        String preferredPackageNameString;
        if ( Utils.isNullOrEmpty( getPreferredPackageName() ) ) {
          preferredPackageNameString = "null";
        } else {
          preferredPackageNameString = "\"" + getPreferredPackageName() + "\"";
        }
        //Assert.assertFalse( Utils.isNullOrEmpty( getCallName() ) );
        methodJavaSb.append( "ClassUtils.getMethodForArgTypes("
                             + classNameString + ", "
                             + preferredPackageNameString + ", \""
                             + getCallName() + "\"" );

        if ( getMethodDecl() != null ) {

          for ( japa.parser.ast.body.Parameter parameter : getMethodDecl().getParameters() ) {
            methodJavaSb.append( ", " );
            methodJavaSb.append( ClassUtils.noParameterName( parameter.getType()
                                                                      .toString() )
                                 + ".class" );
          }

        } else // if ( !classMethods.isEmpty() ) {
        if ( getMatchingMethod() != null
             && getMatchingMethod().getParameterTypes() != null ) {
          for ( Class< ? > type : getMatchingMethod().getParameterTypes() ) {
            methodJavaSb.append( ", " + ClassUtils.toString( type ) );
            // methodJavaSb.append( ", " );
            // String typeName = type.getName();
            // if ( typeName != null ) typeName = typeName.replace( '$', '.'
            // );
            // methodJavaSb.append( ClassUtils.noParameterName( typeName )
            // + ".class" );
          }
        } else {
          Class< ? >[] args = getArgTypes();
          if ( args != null ) {
            for ( Class< ? > a : args ) {
              methodJavaSb.append( ", " );
              if (a == null) {
                methodJavaSb.append( "Object.class" );
              } else {
                methodJavaSb.append( ClassUtils.noParameterName(a.getCanonicalName()) + ".class" );

              }
            }
          }
        }
        
      } else {
        String callName = getConstructorCallNameWithScope();
        methodJavaSb.append( "ClassUtils.getConstructorForArgTypes("
                             + ClassUtils.noParameterName( callName )
                             + ".class" );
        if ( false ) {
        if ( getExprXlator().getClassData().isInnerClass( callName ) ) {
          methodJavaSb.append( ", " + getExprXlator().getClassData().getEnclosingClassName( callName ) + ".class" );
        }
        }
        ConstructorDeclaration cDecl = getConstructorDecl();
        if ( cDecl != null ) {
          if ( cDecl.getParameters() != null ) {
            for ( japa.parser.ast.body.Parameter parameter : getConstructorDecl().getParameters() ) {
              methodJavaSb.append( ", " );
              methodJavaSb.append( ClassUtils.noParameterName( parameter.getType()
                                                                        .toString() )
                                   + ".class" );
            }
          }
        } else {
          // Try using reflection to find the method, but class may not
          // exist.
          if ( getMatchingConstructor() != null ) {
            for ( Class< ? > type : getMatchingConstructor().getParameterTypes() ) {
              methodJavaSb.append( ", " + ClassUtils.toString( type ) );
              // if ( type.isArray() )
              // String typeName = type.getName();
              // if ( typeName != null ) typeName = typeName.replace( '$', '.'
              // );
              // methodJavaSb.append( ClassUtils.noParameterName( typeName )
              // + ".class" );
            }
          }
        }
      }
      methodJavaSb.append( " )" );
      setMethodJava( methodJavaSb.toString() );
    }
    Debug.out("getMethodJava(): returning " + methodJava);
    return methodJava;
  }

  /**
   * @param methodJava
   *          the methodJava to set
   */
  public void setMethodJava( String methodJava ) {
    this.methodJava = methodJava;
  }

  /**
   * @return the isEffectFunction
   */
  public boolean isEffectFunction() {
    // Determine whether the function is regular or actually an effect.
    // For example, TimeVaryingMap.setValue(...) is an effect function, but
    // TimeVaryingMap.getValue(...) is not.
    // // TODO -- REVIEW -- type is never used! -- is it supposed to be used in

    // Assume it's an effect if the object it's called from is Affectable, and
    // try to prove that the function is not one of the effect functions for
    // that class.
    if ( getObjectType() != null
         && Affectable.class.isAssignableFrom( getObjectType() ) ) {
      if ( isMethodOrConstructor() && getMethodCallExpr() != null
           && getMethodCallExpr().getName() != null
           && !getMethodCallExpr().getName().startsWith( "getValue" )
           && ( getCallName() == null || getObjectType() != TimeVaryingMap.class
                || TimeVaryingMap.effectMethodNames()
                                 .contains( getCallName() ) ) ) {
        setEffectFunction( true );
      } else {
        setEffectFunction( false );
      }
    } else {
      setEffectFunction( false );
    }

    // HACK -- not going to try and prove it isn't.
    // if ( isEffectFunction && type != null ) {
    // Class<?>[] types = new Class<?>[]{ TimeVaryingMap, TimeVaryingList,
    // ObjectFlow, Consumable,
    // isEffectFunction =
    // }
    return isEffectFunction;
  }

  /**
   * @param isEffectFunction
   *          the isEffectFunction to set
   */
  public void setEffectFunction( Boolean isEffectFunction ) {
    if ( this.isEffectFunction != isEffectFunction ) {
      setCall( null );
      this.isEffectFunction = isEffectFunction;
    }
  }

  /**
   * @return the argumentArrayJava
   */
  public String getArgumentArrayJava() {
    if ( argumentArrayJava == null ) {
      // Build Java text to construct an array enclosing the arguments to be
      // passed to the method call.
      StringBuffer argumentArraySb = new StringBuffer();
      argumentArraySb.append( "new Object[]{ " );
      boolean first = true;
      List< Expression > argExprs = getArgExpressions();
      if ( argExprs != null ) {
        for ( Expression a : argExprs ) {
          if ( first ) {
            first = false;
          } else {
            argumentArraySb.append( ", " );
          }
          if ( isConvertingArgumentsToExpressions() ) {
            String e =
                exprXlator.astToAeExpr( a, isConvertingArgumentsToExpressions(),
                                        true, true );
            if ( Utils.isNullOrEmpty( e ) || e.matches( "[(][^()]*[)]null" ) ) {
              argumentArraySb.append( a );
            } else {
              argumentArraySb.append( e );
            }
          } else {
            argumentArraySb.append( a );
          }
        }
      }
      argumentArraySb.append( " } " );
      setArgumentArrayJava( argumentArraySb.toString() );
    }
    return argumentArrayJava;
  }

  /**
   * @param argumentArrayJava
   *          the argumentArrayJava to set
   */
  public void setArgumentArrayJava( String argumentArrayJava ) {
    this.argumentArrayJava = argumentArrayJava;
  }

  /**
   * @return the args
   */
  public Vector< Object > getArgs() {
    if ( Utils.isNullOrEmpty( args ) ) initArgs();
    return args;
  }

  /**
   * @param args
   *          the args to set
   */
  public void setArgs( Vector< Object > args ) {
    if ( this.args != args ) {
      setMethodJava( null );
      setArgumentArrayJava( null );
      setMatchingMethod( null );
      setMatchingConstructor( null );
      this.args = args;
    }
  }

  /**
   * @return the convertingArgumentsToExpressions
   */
  public boolean isConvertingArgumentsToExpressions() {
    return convertingArgumentsToExpressions;
  }

  /**
   * @param convertingArgumentsToExpressions
   *          the convertingArgumentsToExpressions to set
   */
  public void
         setConvertingArgumentsToExpressions( boolean convertingArgumentsToExpressions ) {
    this.convertingArgumentsToExpressions = convertingArgumentsToExpressions;
  }

  /**
   * @return the evaluateCall
   */
  public boolean isEvaluateCall() {
    return evaluateCall;
  }

  /**
   * @param evaluateCall
   *          the evaluateCall to set
   */
  public void setEvaluateCall( boolean evaluateCall ) {
    if ( this.evaluateCall != evaluateCall ) {
      setArgs( null );
      this.evaluateCall = evaluateCall;
    }
  }

  /**
   * @return the call
   */
  public Call getCall(boolean complainIfNotFound) {
    if ( call == null ) {
      // This call causes infinite recursion!
      // The problem is that we may be trying to create a method that does not
      // yet exist!
      // In Call, object and args are expressions; make the method/constructor
      // an expression, too!
      // gov.nasa.jpl.ae.event.Expression< ? > methodExpr =
      // exprXlator.javaToAeExpression( methodJava, null, false );

      // gov.nasa.jpl.ae.event.Expression< ? > argumentArrayExpr =
      // exprXlator.javaToAeExpression( getArgumentArrayJava(), "Object[]",
      // false );

      if ( getMethodCallExpr() == null ) {
        if ( getMatchingConstructor() == null ) {
          Debug.error( true, "Cannot create constructor! " + this );
        } else {
          setCall( new ConstructorCall( expression,
                                        getMatchingConstructor(),
                                        // (Constructor< ?
                                        // >)methodExpr.evaluate( true ),
                                        // (Object[])argumentArrayExpr.evaluate(
                                        // true ) );
                                        getArgs(), this.returnType ) );
        }
      } else {
        if ( getMatchingMethod() == null ) {
          if ( complainIfNotFound ) {
            Debug.error( true, "Cannot create method! " + this );
          }
        } else if ( isEffectFunction() ) {
          setCall( new EffectFunction( expression, getMatchingMethod(),
                                       // (Method)methodExpr.evaluate( true ),
                                       // (Object[])argumentArrayExpr.evaluate(
                                       // true ) );
                                       getArgs(), this.returnType ) );
        } else {
          setCall( new FunctionCall( expression, getMatchingMethod(),
                                     // (Method)methodExpr.evaluate( true ),
                                     // (Object[])argumentArrayExpr.evaluate(
                                     // true ) );
                                     getArgs(), this.returnType ) );
        }
      }
    }
    return call;
  }

  public static Class< ? > getType( Object arg ) {
    if ( arg == null ) return null;
    if ( arg instanceof gov.nasa.jpl.ae.event.Expression ) {
      return ( (gov.nasa.jpl.ae.event.Expression< ? >)arg ).getType();
    } else {
      if ( arg instanceof Wraps ) {
        return ( (Wraps< ? >)arg ).getType();
      }
    }
    return arg.getClass();
  }

  protected static boolean checkedUtilPackages = false;


  public Member bestMethodForArgs( Member m1, Member m2, Collection<Class> argTypes ) {
    if ( argTypes == null ) return null;
    Class[] clss = new Class[argTypes.size()];
    int i = 0;
    for ( Class cls : argTypes ) {
      clss[i] = cls;
      ++i;
    }
    Member result = bestMethodForArgs( m1, m2, clss );
    return result;
  }

  public Member bestMethodForArgs( Member m1, Member m2, Class[] argTypes ) {
    Member[] methods = new Member[]{m1, m2};
    ClassUtils.ArgTypeCompare atc = new ClassUtils.ArgTypeCompare(null, null, argTypes);
    String callNameNoParams = ClassUtils.noParameterName(ClassUtils.simpleName(callName));
    if (methods != null) {
      for (Member m : methods) {
        if (m != null && m.getName() != null ) {
          String mNameNoParams = ClassUtils.noParameterName(ClassUtils.simpleName(m.getName()));

          if ( mNameNoParams.equals(callNameNoParams) ) {
            //if ( m.getName().equals(callName) ) {
            Class<?>[] params = m instanceof Method ? ((Method)m).getParameterTypes() : ((Constructor<?>)m).getParameterTypes();
            boolean isVarArgs = m instanceof Method ? ((Method)m).isVarArgs() : ((Constructor<?>)m).isVarArgs();
            atc.compare(m, params, isVarArgs);
          }
        } else {
          //System.out.println("WWWWWWWWWWWWWWWWWWWW    method has no name!!! " + m + "    WWWWWWWWWWWWWWWWWWWW");
        }
      }
    }
    if ( atc.best != null ) {
      Member mm = (Member)atc.best;
      return mm;
    }
    return null;
  }

  /**
   * Return a Call object based on the passed operation and arguments
   * 
   * @param operationName
   *          The name of operation used to search for the equivalent java call
   * @param argumentss
   *          The arguments for operation
   * @return Call object or null if the operationName is not a java call
   */
  public Call searchForCall( String operationName,
                             Vector< Object > argumentss,
                             Collection<Class<?>> argTypess) {

    Call call = null;
    Method method = null;
    Object object = null;

    /*
     * We will look for the corresponding Constructor or FunctionCall in the
     * following order:
     * 
     * 1. The current model class, ie EmsSystemModel (assume its a FunctionCall)
     * 2. The view_repo.syml package (assume its a ConstructorCall or
     * FunctionCall)
     * 3. The Functions.java and ae.event package (assume its a
     * ConstructorCall)
     * 4. Common Java classes (assume its a FunctionCall)
     * 4. Timepoint class
     * 5. The mbee.util package (assume its a FunctionCall)
     * 6. Check imported classes.
     *
     */
    if ( operationName == null ) return null;

    if ( Utils.isNullOrEmpty(argTypess) ) {
      argTypess = new ArrayList<Class<?>>();
    }

    // Finding out the argument types:
    if ( argumentss != null && Utils.isNullOrEmpty(argTypes))
    for ( Object arg : argumentss ) {
      Class< ? > type = getType( arg );
      if ( type == null ) {
        Debug.error( "Expecting an Expression for the argument: " + arg );
      }
      argTypess.add( type );
    }

    int argSize = argumentss != null ? argumentss.size() : (argTypess != null ? argTypess.size() : 0);
    boolean argsEmpty = Utils.isNullOrEmpty(argumentss);
    boolean argTypesEmpty = Utils.isNullOrEmpty(argTypes);

    // If neither arguments nor types are empty, we need to determine which to use,
    // so keep some stats to help.
    int numTypeObject = 0;
    int argEmptyCount = 0;
    int typeEmptyCount = 0;
    if ( !argsEmpty && !argTypesEmpty ) {
      // If not both empty, is there one that is more empty?
      for (Object o : argumentss) {
        if (o == null || gov.nasa.jpl.ae.event.Expression.valuesEqual(o, null)) {
          ++argEmptyCount;
        }
      }
      for (Class<?> o : argTypess) {
        if (o == null) {
          ++argEmptyCount;
        }
      }
      if (argEmptyCount != argumentss.size() && (argEmptyCount != argumentss.size() &&
          argumentss.size() > argTypess.size())) {
        argEmptyCount += argumentss.size() - argTypess.size();
      }
    }

    Class[] argTypeArray = argTypes == null ? null : Utils.toArrayOfType(argTypes, Class.class);


    // // 1. Search API
    // method = ClassUtils.getMethodForArgTypes( model.getClass(),
    // operationName.toString(),
    // argTypes.toArray(new Class[]{}));
    //
    // if ( method != null ) {
    // object = model;
    // }
    // else {


    if ( method == null ) {
      // 2.
      call = exprXlator.javaCallToEventFunction( operationName,null,
                                                 argumentss, argTypeArray );
    }
    if ( method == null && call == null ) {
      // 3.
      if ( argSize == 1 ) {
        call =
            JavaToConstraintExpression.unaryOpNameToEventFunction( operationName.toString(),
                                                                   null,
                                                                   false );
        if ( call == null ) {
          //call = JavaToConstraintExpression.javaCallToEventFunction()

        }
      } else if ( argSize == 2 ) {
        call =
            JavaToConstraintExpression.binaryOpNameToEventFunction( operationName.toString(),
                                                                    null );
      } else if ( argSize == 3
                  && operationName.toString().equalsIgnoreCase( "if" ) ) {
        call = JavaToConstraintExpression.getIfThenElseConstructorCall( null );
      } else if ( argSize > 3 && operationName.toString().toLowerCase()
                                                       .startsWith( "argm" ) ) {
        call = JavaToConstraintExpression.getArgMinMaxConstructorCall(
                                                                       operationName.toString()
                                                                                    .toLowerCase(),
                                                                       argSize,
                                                                       null );
      }

      if ( call != null ) {
        call.setArguments( argumentss );
      } else if ( method == null ) {
        // 4.
        Method mt = null;
        Method ma = null;
        if ( !argTypesEmpty ) {
          mt = ClassUtils.getJavaMethodForCommonFunction(operationName.toString(),
                  argTypeArray);
        }
        if ( !argsEmpty ) {
          ma = ClassUtils.getJavaMethodForCommonFunction(operationName.toString(),
                                                             argumentss == null ? new Object[]{} : argumentss.toArray());
        }
        if ( mt == null ) method = ma;
        else if ( ma == null ) method = mt;
        else if ( mt.equals(ma) ) method = mt;
        else {
          // need a tie breaker
          if ( argEmptyCount > typeEmptyCount ) {
            method = mt;
          } else {
            method = ma;
          }
        }

        if ( method == null ) {
          // 4.5 Timepoint class
          if ( !argTypesEmpty ) {
            mt = ClassUtils.getMethodForArgTypes(Timepoint.class, operationName.toString(), argTypeArray, false);
          }
          if ( !argsEmpty ) {
            ma = ClassUtils.getMethodForArgs( Timepoint.class, operationName.toString(), false,
                                              argumentss == null ? new Object[]{} : argumentss.toArray(), false );
          }
          if ( mt == null ) method = ma;
          else if ( ma == null ) method = mt;
          else if ( mt.equals(ma) ) method = mt;
          else {
            method = (Method)bestMethodForArgs( mt, ma, argTypeArray );
            if ( method == null ) {
              // need a tie breaker
              if ( argEmptyCount > typeEmptyCount ) {
                method = mt;
              } else {
                method = ma;
              }
            }
          }
        }

        // 5.
        if ( method == null ) {
          // checking the ae.util and mbee.util packages
          String[] packages = new String[]{"gov.nasa.jpl.ae.util", "gov.nasa.jpl.mbee.util", "gov.nasa.jpl.k2mms"};

          // Check to see if they exist.
          if ( !checkedUtilPackages ) {
            checkedUtilPackages = true;
            for (String ps : packages) {
              Package p = Package.getPackage(ps);
              if (p == null) {
                Debug.error(true, false, "Warning! Could not get package: " + ps);
              }
            }
          }

          call = exprXlator.javaCallToCall(packages, operationName,
                                           null, argumentss,
                                           argTypeArray);
        }

        // 6. check imported classes
//        if ( method == null && call == null ) {
//          Set<String> classNames = exprXlator.getClassData().getImportedClassNames();
//          for ( String c : classNames ) {
//            method = ClassUtils.getMethodForArgTypes( c, getPreferredPackageName(),
//                                                      operationName, argTypes, false );
//          }
//        }
      }

    } // Ends call == null

    // }

    if ( call == null && method == null ) {
      // REVIEW -- if it *still* fails, maybe search through all classes of all
      // packages for a method with this name.  Could present security problem.
    }

    // Make the FunctionCall if it was not a ConstructorCall:
    if ( method != null ) {
      call = new FunctionCall( object, method, argumentss != null ? argumentss : new Vector<Object>(), null );
    }

    return call;
  }

  /**
   * @param call
   *          the call to set
   */
  public void setCall( Call call ) {
    this.call = call;
  }

  // /**
  // * @return the generatedDependencies
  // */
  // public ArrayList< FieldDeclaration > getGeneratedDependencies() {
  // return generatedDependencies;
  // }
  //
  // /**
  // * @param generatedDependencies the generatedDependencies to set
  // */
  // public
  // void
  // setGeneratedDependencies( ArrayList< FieldDeclaration >
  // generatedDependencies ) {
  // this.generatedDependencies = generatedDependencies;
  // }

  public void setPreferredPackageName( String preferredPackageName ) {
    if ( this.preferredPackageName != preferredPackageName ) {
      setObjectType( null );
      setMethodJava( null );
      setMatchingMethod( null );
      setMatchingConstructor( null );
      this.preferredPackageName = preferredPackageName;
    }
  }

  public String getPreferredPackageName() {
    return this.preferredPackageName;
  }

  /**
   * @return the exprXlator
   */
  public JavaToConstraintExpression getExprXlator() {
    return exprXlator;
  }

}
// public Date epoch = new Date();
