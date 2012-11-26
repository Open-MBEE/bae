package gov.nasa.jpl.ae.util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * MoreToString adds options to toString() for recursively nested objects.
 *
 */
public interface MoreToString {

  // Constants to be used as options for formatting collections and arrays.
  public static final int NO_FORMAT = -1;
  public static final int SQUARE_BRACES = 0;
  public static final int CURLY_BRACES = 1;
  public static final int PARENTHESES = 2;
  public static final int COMMA = 3;
  public static final int PERIOD = 4;
  public static final int EQUALS = 5;
  
  static final String[] prefixes = new String[]{"[","{","(","(","(","("};
  static final String[] suffixes = new String[]{"]","}",")",")",")",")"};
  static final String[] delimiters = new String[]{",",",",",",",",".","="};
  public static final Map<String, String[]> formatOptions = Helper.initFormatOptions();
  
  /**
   * Write object recursively based on passed options.
   * 
   * @param withHash
   *          whether to include "@" + hasCode() in the returned String.
   * @param deep
   *          whether to include member/child detail and call their
   *          MoreToString.toString() (typically) with the same options.
   * @param seen
   *          whether the object has already been written with deep=true, in
   *          which case it will set deep=false to end the recursion.
   * @return the string representation of the object.
   */
  String toString( boolean withHash, boolean deep,
                   Set<Object> seen );
  /**
   * Write object recursively based on passed options.
   * 
   * @param withHash
   *          whether to include "@" + hasCode() in the returned String.
   * @param deep
   *          whether to include member/child detail and call their
   *          MoreToString.toString() (typically) with the same options.
   * @param seen
   *          whether the object has already been written with deep=true, in
   *          which case it will set deep=false to end the recursion.
   * @param otherOptions
   *          other class or context-specific options with names and values.
   * @return the string representation of the object.
   */
  String toString( boolean withHash, boolean deep,
                   Set<Object> seen, Map<String, Object> otherOptions);
  
  
  
  /**
   * Helper class for MoreToString toString() calls. There are also functions
   * for formatting collections.
   */
  static class Helper {
    /**
     * Helper function for MoreToString.toString() when it is not known whether
     * the input object implements MoreToString.
     * 
     * @return ((MoreToString)object).toString(...) with the same options passed
     *         if the object does implement MoreToString; otherwise return
     *         object.toString().
     */
    public static String toString( Object object, boolean withHash,
                                   boolean deep, Set< Object > seen,
                                   Map< String, Object > otherOptions ) {
      if ( object == null ) return "null";
      
      // We have to make sure we get to the right method since collections
      // require an extra boolean argument.
      if ( object instanceof Collection ) {
        return toString( (Collection<?>)object, withHash, deep, seen, otherOptions, true );
      }
      if ( object instanceof Map ) {
        return toString( (Map<?,?>)object, withHash, deep, seen, otherOptions, true );
      }
      if ( object instanceof Pair ) {
        return toString( (Pair<?,?>)object, withHash, deep, seen, otherOptions, true );
      }
      if ( object instanceof Map.Entry ) {
        return toString( (Map.Entry<?,?>)object, withHash, deep, seen, otherOptions, true );
      }
      
      if ( object instanceof MoreToString ) {
        return ( (MoreToString)object ).toString( withHash, deep, seen,
                                                  otherOptions );
      }
      return object.toString();
    }

    /**
     * Helper function for MoreToString.toString() when it is not known whether
     * the input object implements MoreToString.
     * 
     * @return ((MoreToString)object).toString(...) with the same options passed
     *         if the object does implement MoreToString; otherwise return
     *         object.toString().
     */
    public static String toString( Object object, boolean withHash,
                                   boolean deep, Set< Object > seen ) {
      return toString( object, withHash, deep, seen, null );
    }

    /**
     * Helper function for MoreToString.toString() when it is not known whether
     * the input object implements MoreToString and default parameter values are
     * fine.
     * 
     * @param object
     * @return
     */
    public static String toString( Object object ) {
      // Below works for array or other non-collection object.
      return toString( object, false, false, null, null );
    }

    /**
     * Writes an array to a string with MoreToString options, including an
     * explicit format key. For example, if the format key
     * MoreToString.PARENTHESES is given, the collection will be enclosed in
     * parentheses, "(" and ")" and the default comma will be used as a
     * delimiter between elements.
     * 
     * @param withHash
     *          whether to include "@" + hasCode() in the returned String.
     * @param deep
     *          whether to include member/child detail and call their
     *          MoreToString.toString() (typically) with the same options.
     * @param seen
     *          whether the object has already been written with deep=true, in
     *          which case it will set deep=false to end the recursion.
     * @param otherOptions
     *          other class or context-specific options with names and values.
     * @param formatKey
     *          a MoreToString constant indicating the grouping format of the
     *          elements of the collection, for example,
     *          MoreToString.CURLY_BRACES.
     * @return the string representation of the object.
     */
    public static < T > String toString( final Collection< T > collection,
                                         boolean withHash, boolean deep,
                                         Set< Object > seen,
                                         Map<String,Object> otherOptions,
                                         int formatKey,
                                         boolean checkIfMoreToString ) {
      if ( checkIfMoreToString && collection instanceof MoreToString ) {
        stuffOptionsFromKey( otherOptions, formatKey );
        return ((MoreToString)collection).toString(withHash, deep, seen, otherOptions);
      }
      return toString( collection.toArray(), withHash, deep, seen, otherOptions, formatKey );
    }
    public static < T > String toString( Collection< T > collection,
                                         boolean withHash, boolean deep,
                                         Set< Object > seen,
                                         Map<String,Object> otherOptions,
                                         boolean square,
                                         boolean checkIfMoreToString ) {
      int formatKey = (square ? SQUARE_BRACES : PARENTHESES );
      return toString( collection, withHash, deep, seen, otherOptions,
                       formatKey, checkIfMoreToString );
    }
    public static < T > String toString( Collection< T > collection,
                                         boolean withHash, boolean deep,
                                         Set< Object > seen,
                                         Map< String, Object > otherOptions,
                                         boolean checkIfMoreToString ) {
      int formatKey = PARENTHESES;
      if ( hasFormatOptions( otherOptions ) ) {
        formatKey = NO_FORMAT;
      }
      return toString( collection, withHash, deep, seen, otherOptions,
                       formatKey, checkIfMoreToString );
    }
    public static < T > String toString( Collection< T > collection,
                                         boolean withHash, boolean deep,
                                         Set< Object > seen,
                                         Map<String,Object> otherOptions,
                                         String prefix, String delimiter,
                                         String suffix,
                                         boolean checkIfMoreToString ) {
      if ( checkIfMoreToString && collection instanceof MoreToString ) {
        stuffOptionsFromKey( otherOptions, prefix, delimiter, suffix );
        return ((MoreToString)collection).toString(withHash, deep, seen, otherOptions);
      }
      return toString( collection.toArray(), withHash, deep, seen, otherOptions,
                       prefix, delimiter, suffix );
    }

    public static < K, V > String toString( Map< K, V > map, boolean withHash,
                                            boolean deep, Set< Object > seen,
                                            Map< String, Object > otherOptions,
                                            int formatKey,
                                            boolean checkIfMoreToString ) {
      if ( checkIfMoreToString && map instanceof MoreToString ) {
        stuffOptionsFromKey( otherOptions, formatKey );
        return ( (MoreToString)map ).toString( withHash, deep, seen,
                                               otherOptions );
      }
      return toString( map.entrySet().toArray(), withHash, deep, seen,
                       otherOptions, formatKey );
    }

    public static < K, V > String toString( Map< K, V > map, boolean withHash,
                                            boolean deep, Set< Object > seen,
                                            Map< String, Object > otherOptions,
                                            boolean checkIfMoreToString ) {
      int formatKey = CURLY_BRACES;
      if ( hasFormatOptions( otherOptions ) ) {
        formatKey = NO_FORMAT;
//        if ( checkIfMoreToString && map instanceof MoreToString ) {
//          return ((MoreToString)map).toString(withHash, deep, seen, otherOptions);
//        }
//        return toString( map.entrySet().toArray(), withHash, deep, seen,
//                         otherOptions );
      }
      return toString( map, withHash, deep, seen, otherOptions, formatKey,
                       checkIfMoreToString );
    }

    public static < K, V > String toString( Map< K, V > map, boolean withHash,
                                            boolean deep, Set< Object > seen,
                                            Map< String, Object > otherOptions,
                                            String prefix, String delimiter,
                                            String suffix,
                                            boolean checkIfMoreToString ) {
      if ( checkIfMoreToString && map instanceof MoreToString ) {
        stuffOptionsFromKey( otherOptions, prefix, delimiter, suffix );
        return ( (MoreToString)map ).toString( withHash, deep, seen,
                                               otherOptions );
      }
      return toString( map.entrySet().toArray(), withHash, deep, seen,
                       otherOptions, prefix, delimiter, suffix );
    }

    public static < K, V > String toString( Map.Entry< K, V > entry,
                                            boolean withHash, boolean deep,
                                            Set< Object > seen,
                                            Map<String,Object> otherOptions,
                                            int formatKey,
                                            boolean checkIfMoreToString ) {
      if ( checkIfMoreToString && entry instanceof MoreToString ) {
        stuffOptionsFromKey( otherOptions, formatKey );      
        return ((MoreToString)entry).toString(withHash, deep, seen, otherOptions);
      }
      Pair< K, V > p = new Pair< K, V >( entry.getKey(), entry.getValue() );
      return toString( p, withHash, deep, seen, otherOptions, formatKey, false );
    }
    public static < K, V > String toString( Map.Entry< K, V > entry,
                                            boolean withHash, boolean deep,
                                            Set< Object > seen,
                                            Map<String,Object> otherOptions,
                                            boolean checkIfMoreToString ) {
      int formatKey = EQUALS;
      if ( hasFormatOptions( otherOptions ) ) {
        formatKey = NO_FORMAT;
//        if ( checkIfMoreToString && entry instanceof MoreToString ) {
//          return ((MoreToString)entry).toString(withHash, deep, seen, otherOptions);
//        }
//        Pair< K, V > p = new Pair< K, V >( entry.getKey(), entry.getValue() );
//        return toString( p, withHash, deep, seen, otherOptions, false );
      }// else {
      return toString( entry, withHash, deep, seen, otherOptions, formatKey,
                       checkIfMoreToString );
//      }
    }
    public static < K, V > String toString( Map.Entry< K, V > entry,
                                            boolean withHash, boolean deep,
                                            Set< Object > seen,
                                            Map<String,Object> otherOptions,
                                            String prefix,
                                            String delimiter,
                                            String suffix,
                                            boolean checkIfMoreToString) {
      if ( checkIfMoreToString && entry instanceof MoreToString ) {
        stuffOptionsFromKey( otherOptions, prefix, delimiter, suffix );
        return ((MoreToString)entry).toString(withHash, deep, seen, otherOptions);
      }
      Pair< K, V > p = new Pair< K, V >( entry.getKey(), entry.getValue() );
      return toString( p, withHash, deep, seen, otherOptions,
                       prefix, delimiter, suffix, false );
    }                                            
    public static < K, V > String toString( Pair< K, V > pair,
                                            boolean withHash, boolean deep,
                                            Set< Object > seen,
                                            Map<String,Object> otherOptions,
                                            int formatKey,
                                            boolean checkIfMoreToString ) {
      if ( checkIfMoreToString && pair instanceof MoreToString ) {
        stuffOptionsFromKey( otherOptions, formatKey );
        return ((MoreToString)pair).toString(withHash, deep, seen, otherOptions);
      }
      return toString( new Object[]{pair.first, pair.second}, withHash,
                       deep, seen, otherOptions, formatKey );
    }
    public static < K, V > String toString( Pair< K, V > pair,
                                            boolean withHash, boolean deep,
                                            Set< Object > seen,
                                            Map<String,Object> otherOptions,
                                            boolean checkIfMoreToString ) {
      int formatKey = hasFormatOptions( otherOptions ) ? NO_FORMAT : PARENTHESES;
//      if ( checkIfMoreToString && pair instanceof MoreToString ) {
//        return ((MoreToString)pair).toString(withHash, deep, seen, otherOptions);
//      }
      return toString( pair, withHash, deep, seen, otherOptions, formatKey,
                       checkIfMoreToString );
    }
    public static < K, V > String toString( Pair< K, V > pair,
                                            boolean withHash, boolean deep,
                                            Set< Object > seen,
                                            Map<String,Object> otherOptions,
                                            String prefix,
                                            String delimiter,
                                            String suffix,
                                            boolean checkIfMoreToString ) {
      if ( checkIfMoreToString && pair instanceof MoreToString ) {
        stuffOptionsFromKey( otherOptions, prefix, delimiter, suffix );
        return ((MoreToString)pair).toString(withHash, deep, seen, otherOptions);
      }
      return toString(new Object[]{pair.first, pair.second},
                      withHash, deep, seen, otherOptions,
                      prefix, delimiter, suffix );
    }
//    public static < T > String toString( final T[] collection,
//                                         boolean withHash, boolean deep,
//                                         Set< Object > seen,
//                                         Map<String,Object> otherOptions ) {
//      return toString( collection, withHash, deep, seen, otherOptions, true );
//    }
    
    /**
     * @param map
     * @param key
     * @param cls
     * @return a value in map of type T with the given key or null if not found.
     */
    public static < K ,V, T > T get( Map<K,V> map, K key, Class<T> cls ) {
      if ( key == null ) return null;      
      if ( map == null ) return null;
      V v = map.get( key );
      if ( v == null ) return null;
      T t = null;
      //try {      
      if ( cls.isAssignableFrom( v.getClass() ) ) {
        t = (T)v;
      }
      //} catch ( ClassCastException cce ) {
      //  ;// ignore
      //}
      return t;
    }
    /**
     * @param map
     * @param key
     * @return a value in map of type T with the given key or null if not found.
     */
    public static < K ,V, T > T get( Map<K,V> map, K key ) {
      return get( map, key, null );
    }
    
    private static Map<String, String[]> initFormatOptions() {
      Map<String, String[]> optionMap = new TreeMap<String, String[]>();
      optionMap.put( "prefix", prefixes );
      optionMap.put( "suffix", suffixes);
      optionMap.put( "delimiter", delimiters );
      return optionMap;
    }

    private static String[] getOptions( Map< String, Object > options,
                                        String[] optionNames ) {
      String[] optionValues = new String[ optionNames.length ];
      if ( !Utils.isNullOrEmpty( options )
           && !Utils.isNullOrEmpty( optionNames ) ) {
        // initialize option values to null
        for ( int i = 0; i < optionNames.length; ++i ) {
          optionValues[ i ] = null;
        }
        // get matching values for the name
        for ( int i = 0; i < optionNames.length; ++i ) {
          // look for a matching string value first
          String value = get( options, optionNames[ i ], String.class );
          if ( value != null ) {
            optionValues[ i ] = value;
          } else {
            // look for a format key integer
            Integer optionKey = get( options, optionNames[ i ], Integer.class );
            if ( optionKey != null ) {
              // look up the string value for the format key
              String[] subOptions = formatOptions.get( optionNames[ i ] );
              if ( subOptions != null ) {
                if ( optionKey > 0 && optionKey < subOptions.length ) {
                  value = subOptions[ optionKey ];
                  optionValues[ i ] = value;
                }
              }
              // The format key often applies to all options, so set any that
              // are null to the default string value for the name and key.
              for ( int j = 0; j < optionNames.length; ++j ) {
                // Don't overwrite an assigned value with a default.
                if ( optionValues[ j ] != null ) continue;
                // look up the string value for the format key
                subOptions = formatOptions.get( optionNames[ j ] );
                if ( subOptions != null ) {
                  if ( optionKey > 0 && optionKey < subOptions.length ) {
                    value = subOptions[ optionKey ];
                    optionValues[ j ] = value;
                  }
                }
              }
            }
          }
        }
      }
      return optionValues;
    }

    private static String[] getFormatOptions( Map< String, Object > options ) {
      final Object[] objects = formatOptions.keySet().toArray();
      String[] optionNames = new String[formatOptions.size()];
      Utils.toArrayOfType( objects, optionNames, String.class );
          //new String[] { "prefix", "delimiter", "suffix" };
      return getOptions( options, optionNames );
    }

    public static boolean hasFormatOptions(Map< String, Object > options ) {
      return ( options != null && options.get( "prefix" ) != null );
    }
    
    /**
     * Writes an array to a string with MoreToString options, including an
     * explicit format key. For example, if the format key
     * MoreToString.PARENTHESES is given, the collection will be enclosed in
     * parentheses, "(" and ")" and the default comma will be used as a
     * delimiter between elements.
     * 
     * @param withHash
     *          whether to include "@" + hasCode() in the returned String.
     * @param deep
     *          whether to include member/child detail and call their
     *          MoreToString.toString() (typically) with the same options.
     * @param seen
     *          whether the object has already been written with deep=true, in
     *          which case it will set deep=false to end the recursion.
     * @param otherOptions
     *          other class or context-specific options with names and values.
     * @param formatKey
     *          a MoreToString constant indicating the grouping format of the
     *          elements of the collection, for example,
     *          MoreToString.CURLY_BRACES.
     * @return the string representation of the object.
     */
    public static < T > String toString( final T[] array,
                                         boolean withHash, boolean deep,
                                         Set< Object > seen,
                                         Map<String,Object> otherOptions,
                                         int formatKey ) {
      if ( otherOptions == null ) otherOptions = new TreeMap< String, Object >();
      stuffOptionsFromKey(otherOptions, formatKey);
      return toString( array, withHash, deep, seen, otherOptions );
    }
    
    public static void stuffOptionsFromKey( Map< String, Object > options,
                                            int formatKey ) {
      if ( formatKey == NO_FORMAT ) return;
      if ( options == null ) return;
      for ( String opt : formatOptions.keySet() ) {
        options.put("prefix", formatKey);
      }
    }
    private static void stuffOptionsFromKey( Map< String, Object > options,
                                             String prefix, String delimiter,
                                             String suffix ) {
      if ( options == null ) options = new TreeMap< String, Object >();
      options.put("prefix", prefix);
      options.put("delimiter", delimiter);
      options.put("suffix", suffix);
    }
    
    public static void removeFormatOptions( Map< String, Object > options ) {
      if ( Utils.isNullOrEmpty( options ) ) return;
      for ( String opt : formatOptions.keySet() ) {
        options.remove( opt );
      }
    }

    public static < T > String toString( final T[] array,
                                         boolean withHash, boolean deep,
                                         Set< Object > seen,
                                         Map<String,Object> otherOptions ) {
      String[] options = getFormatOptions(otherOptions);
      String delimiter = options[0]; // ordered by option name
      String prefix = options[1];
      String suffix = options[2];
      if ( prefix != null && !otherOptions.containsKey( "formatDeep" ) ) {
        removeFormatOptions(otherOptions);
      }
      return toString( array, withHash, deep, seen, otherOptions, prefix,
                       delimiter, suffix );
    }
    public static < T > String toString( final T[] array,
                                         boolean withHash, boolean deep,
                                         Set< Object > seen,
                                         Map<String,Object> otherOptions,
                                         String prefix,
                                         String delimiter,
                                         String suffix) {
      StringBuffer sb = new StringBuffer();
      if ( prefix == null ) {
        prefix = "[";
        suffix = "]";
      }
      if ( prefix.equals("{") && !suffix.equals("}") ) {
        suffix = "}";
      }
      if ( delimiter == null ) {
        delimiter = ", ";
      }
      sb.append( prefix );
      boolean first = true;
      for ( Object object : array ) {
        if ( first ) {
          first = false;
        } else {
          sb.append( delimiter );
        }
        sb.append( MoreToString.Helper.toString( object, withHash, deep, seen,
                                                 otherOptions ) );
      }
      sb.append( suffix );
      return sb.toString();
    }
  }
}
