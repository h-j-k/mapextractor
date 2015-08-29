/*
 * Copyright 2015 h-j-k. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ikueb.mapextractor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collector.Characteristics;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An utility class providing:
 * <ul>
 * <li>A set of helper methods to easily convert {@link Stream}s of {@link CharSequence}s
 * to either {@link Properties} or {@link Map} instances.
 * <ul>
 * <li>These methods operate on streams and reproduces the parsing logic in
 * {@link Properties#load(java.io.Reader)} as closely as possible. The only differences
 * are no support for multi-element values and the default key delimiter can only be
 * escaped with {@code "\"} once.</li>
 * </ul>
 * </li>
 * <li>A set of collectors to be used in {@link Stream#collect(Collector)} as terminal
 * operations.
 * <ul>
 * <li>These methods are meant to be drop-in replacements for the standard JDK
 * {@code Collectors.toMap()} methods, but with a {@code regex} prefixed argument to split
 * each stream element into key-value pairings.</li>
 * </ul>
 * </li>
 * <li>A {@link MapExtractor.Parser} parser implementation for facilitating quick,
 * 'one-shot' {@link CharSequence}-to-{@link Map} conversions.
 * <ul>
 * <li>Whereas the methods and collectors above adhere more closely to line-based
 * processing, where each stream element is a line, this uses the notion of 'records',
 * where a stream element may contain more than one record.</li>
 * </ul>
 * </li>
 * </ul>
 */
public final class MapExtractor {

    /**
     * Regular expression for matching key delimiters (taking care to check if they are
     * escaped).
     */
    private static final String REGEX_DELIMITER = "(?<!\\\\)[:=]";

    private static final String JOIN_DELIMITER = ", ";

    private MapExtractor() {
        // empty
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that multi-element values are not supported and the default key
     * delimiter can only be escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @return a {@link Properties} instance based on {@code entries}
     * @see Properties#load(java.io.Reader)
     */
    public static Properties asProperties(Stream<? extends CharSequence> entries) {
        Properties props = new Properties();
        props.putAll(skipComments(entries).collect(toMap(REGEX_DELIMITER,
                toKey(), toValue(), (a, b) -> b)));
        return props;
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that values for the same key are put into a {@link List} and
     * multi-element values are not supported and the default key delimiter can only be
     * escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @return a {@link Map}
     */
    public static Map<String, List<String>> groupingBy(
            Stream<? extends CharSequence> entries) {
        return skipComments(entries).collect(groupingBy(REGEX_DELIMITER, toKey(), toValue()));
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that duplicate keys results in an {@link IllegalStateException},
     * multi-element values are not supported and the default key delimiter can only be
     * escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @return a {@link Map}
     * @see #toMap(String, Function, Function)
     */
    public static Map<String, String> simpleMap(Stream<? extends CharSequence> entries) {
        return skipComments(entries).collect(toMap(REGEX_DELIMITER, toKey(), toValue()));
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that values for the same key are joined using
     * {@link #JOIN_DELIMITER}, multi-element values are not supported and the default key
     * delimiter can only be escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @return a {@link Map}
     */
    public static Map<String, String> simpleMapAndJoin(
            Stream<? extends CharSequence> entries) {
        return simpleMapAndJoin(entries, JOIN_DELIMITER);
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that values for the same key are joined using
     * {@code joinDelimiter}, multi-element values are not supported and the default key
     * delimiter can only be escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @param joinDelimiter the join delimiter to use
     * @return a {@link Map}
     */
    public static Map<String, String> simpleMapAndJoin(
            Stream<? extends CharSequence> entries, String joinDelimiter) {
        return skipComments(entries).collect(toMapAndJoin(REGEX_DELIMITER, joinDelimiter));
    }


    /**
     * With the exception of the first argument {@code regex} for splitting each stream
     * element, this method is meant to be used in a similar way as the equivalent in the
     * {@link Collectors} class, except that the third argument {@code valueMapper} exists
     * to do the conversion of map values as well, instead of using the stream elements.
     * <p>
     * Implementation note: the aggregation on values is done by mapping each value as a
     * single-element {@link List}, and then calling
     * {@link List#addAll(java.util.Collection)} as the merge function to
     * {@link MapExtractor#toMap(String, Function, Function, BinaryOperator)}.
     *
     * @param regex the delimiter to use
     * @param keyMapper
     * @param valueMapper
     * @return a {@link Map}
     * @see Collectors#groupingBy(Function)
     */
    public static <K, V> Collector<CharSequence, ?, Map<K, List<V>>> groupingBy(
            String regex,
            Function<? super CharSequence, K> keyMapper,
            Function<? super CharSequence, V> valueMapper) {
        return toMap(regex, keyMapper, enlist(valueMapper), joinList());
    }

    /**
     * Handles the stream like the line-oriented format for loading a
     * {@link Properties} instance, except that values for the same key are
     * joined using {@code joinDelimiter}, multi-element values are not
     * supported and the default key delimiter can only be escaped with
     * {@code "\"} once.
     *
     * @param regex the delimiter to use
     * @param joinDelimiter the join delimiter to use
     * @return a {@link Collector}
     */
    public static Collector<CharSequence, ?, Map<String, String>> toMapAndJoin(
            String regex, String joinDelimiter) {
        return toMap(regex, toKey(), toValue(), join(joinDelimiter));
    }

    /**
     * With the exception of the first argument {@code regex} for splitting each stream
     * element, this method is meant to be used in the same way as the equivalent in the
     * {@link Collectors} class.
     *
     * @param regex the delimiter to use
     * @param keyMapper
     * @param valueMapper
     * @return a {@link Map}
     * @see Collectors#toMap(Function, Function)
     */
    public static <K, V> Collector<CharSequence, ?, Map<K, V>> toMap(String regex,
            Function<? super CharSequence, K> keyMapper,
            Function<? super CharSequence, V> valueMapper) {
        return toMap(regex, keyMapper, valueMapper, duplicateKeyMergeThrower());
    }

    /**
     * With the exception of the first argument {@code regex} for splitting each stream
     * element, this method is meant to be used in the same way as the equivalent in the
     * {@link Collectors} class.
     *
     * @param regex the delimiter to use
     * @param keyMapper
     * @param valueMapper
     * @param merger
     * @return a {@link Map}
     * @see Collectors#toMap(Function, Function, BinaryOperator)
     */
    public static <K, V> Collector<CharSequence, ?, Map<K, V>> toMap(String regex,
            Function<? super CharSequence, K> keyMapper,
            Function<? super CharSequence, V> valueMapper,
            BinaryOperator<V> merger) {
        return toMap(regex, keyMapper, valueMapper, merger, HashMap::new);
    }

    /**
     * With the exception of the first argument {@code regex} for splitting each stream
     * element, this method is meant to be used in the same way as the equivalent in the
     * {@link Collectors} class.
     *
     * @param regex the delimiter to use
     * @param keyMapper
     * @param valueMapper
     * @param merger
     * @param mapSupplier
     * @return a {@link Map}
     * @see Collectors#toMap(Function, Function, BinaryOperator, Supplier)
     */
    public static <K, V, M extends Map<K, V>> Collector<CharSequence, ?, M> toMap(
            String regex, Function<? super CharSequence, K> keyMapper,
            Function<? super CharSequence, V> valueMapper,
            BinaryOperator<V> merger, Supplier<M> mapSupplier) {
        Stream.of(regex, keyMapper, valueMapper, merger, mapSupplier)
                .forEach(Objects::requireNonNull);
        return Collector.of(mapSupplier,
                (m, i) -> { String[] pair = splitWith(regex).apply(i);
                    m.merge(keyMapper.apply(pair[0]), valueMapper.apply(pair[1]),
                            merger); },
                (a, b) -> { b.entrySet().forEach(
                    entry -> a.merge(entry.getKey(), entry.getValue(), merger));
                    return a; },
                Characteristics.IDENTITY_FINISH);
    }

    /**
     * Filters the stream for comments, i.e. elements with the first non-whitespace
     * character as {@code "#"} or {@code "!"}.
     *
     * @param stream the stream to filter
     * @return a {@link Predicate} to skip comment lines
     */
    private static <T extends CharSequence> Stream<T> skipComments(Stream<T> stream) {
        return stream.filter(comments().negate());
    }

    /**
     * @return a predicate for matching comments
     */
    private static Predicate<? super CharSequence> comments() {
        return v -> Pattern.compile("^\\s*([#!]|$)").matcher(v).find();
    }

    /**
     * Splits {@code entry} with {@code regex} into a pair of sub-{@link String}s using
     * {@link String#split(String, int)} (the second argument being {@code 2}).
     *
     * @param regex the delimiter to use
     * @return a pair of {@link String}s, the second element will be an empty
     *         {@link String} ({@code ""}) if there is no second sub-{@link String} from
     *         the split
     */
    private static Function<? super CharSequence, String[]>
            splitWith(String regex) {
        return v -> { String[] result = v.toString().split(regex, 2);
            return result.length == 2 ? result : new String[] { result[0], "" };
        };
    }

    /**
     * @return a {@link Function} that treats the input as a key by trimming it and
     *         removing {@code "\"} characters
     */
    private static Function<? super CharSequence, String> toKey() {
        return k -> k.toString().trim().replace("\\", "");
    }

    /**
     * @return a {@link Function} that treats the input as a value by trimming
     *         whitespace characters from the front only
     */
    private static Function<? super CharSequence, String> toValue() {
        return v -> v.toString().replaceFirst("^\\s+", "");
    }

    /**
     * @param mapper the mapper to apply first
     * @return a {@link Function} that wraps an element in an {@link ArrayList}
     */
    private static <T, U> Function<? super T, List<U>> enlist(
            Function<? super T, U> mapper) {
        return mapper.andThen(v -> new ArrayList<>(Collections.singletonList(v)));
    }

    /**
     * @return the first {@link List} appended with the contents of the second one
     */
    private static <T> BinaryOperator<List<T>> joinList() {
        return (a, b) -> { a.addAll(b); return a; };
    }

    /**
     * @param delimiter the delimiter to use for joining {@link String}s
     * @return a joined {@link String} on the {@code delimiter}
     */
    private static BinaryOperator<String> join(String delimiter) {
        return (a, b) -> String.join(delimiter, a, b);
    }

    /**
     * To be used for cases where we are attempting to merge on duplicate keys, this
     * mirrors the default behavior of {@link Collectors#toMap(Function, Function)}.
     *
     * @return a {@link BinaryOperator} that always throw an {@link IllegalStateException}
     */
    private static <T> BinaryOperator<T> duplicateKeyMergeThrower() {
        return (a, b) -> {
            throw new IllegalStateException(String.format(
                    "Duplicate key for values \"%s\" and \"%s\".", a, b));
        };
    }
    
    /**
     * @return a {@link Parser} implementation that uses comma ({@code ,}) as the record
     *         separator
     * @see #withChar(char)
     */
    public static Parser<String, String> withComma() {
        return withChar(',');
    }
    
    /**
     * @return a {@link Parser} implementation that uses semicolon ({@code ;}) as the
     *         record separator
     * @see #withChar(char)
     */
    public static Parser<String, String> withSemicolon() {
        return withChar(';');
    }
    
    /**
     * @return a {@link Parser} implementation that uses tab ({@code \t}) as the record
     *         separator
     * @see #withChar(char)
     */
    public static Parser<String, String> withTab() {
        return withChar('\t');
    }
    
    /**
     * The returning implementation uses {@link #REGEX_DELIMITER} as the field separator,
     * and joins values of duplicate keys using {@link #JOIN_DELIMITER}.
     * 
     * @param rs the record separator character to use
     * @return a {@link Parser} implementation that uses {@code rs} as the record
     *         separator
     * @see #with(String, String, String)
     */
    public static Parser<String, String> withChar(char rs) {
        return with(String.valueOf(rs), REGEX_DELIMITER, JOIN_DELIMITER);
    }
    
    /**
     * The returning implementation uses {@link #REGEX_DELIMITER} as the field separator,
     * and joins values of duplicate keys using {@link #JOIN_DELIMITER}.
     * 
     * @return a {@link Parser} implementation that uses the system's line separator as
     *         the record separator
     * @see #with(String, String, String)
     */
    public static Parser<String, String> withNewline() {
        return with(System.lineSeparator(), REGEX_DELIMITER, JOIN_DELIMITER);
    }
    
    /**
     * The returning implementation uses an empty {@link String} for concatenating values
     * of duplicate keys.
     * 
     * @param rs the record separator to use
     * @param fs the field separator to use
     * @return a {@link Parser} implementation defined with the method arguments
     * @see #with(String, String, String)
     */
    public static Parser<String, String> with(String rs, String fs) {
        return with(rs, fs, "");
    }
    
    /**
     * The returning implementation applies {@link #toKey()} and {@link #toValue()}
     * mappers on the resulting fields as well. Combining values of duplicate keys is done
     * as such:
     * <ol>
     * <li>If the output field separator ({@code ofs}) is passed as {@code null} , the
     * more recent value of duplicate keys will be used.</li>
     * <li>If either value is empty, the other value will be used.</li>
     * <li>Otherwise, {@link String#join(CharSequence, CharSequence...)} is used. This is
     * done so that there will not be any superfluous separators in the final value.</li>
     * </ol>
     * 
     * @param rs the record separator to use
     * @param fs the field separator to use
     * @param ofs the output field separator to use
     * @return a {@link Parser} implementation defined with the method arguments
     * @see #toKey()
     * @see #toValue()
     */
    public static Parser<String, String> with(String rs, String fs, String ofs) {
        return with(rs, fs, (Function<String, String>) toKey().compose(String::toString), 
                (Function<String, String>) toValue().compose(String::toString), 
                ofs == null ? (a, b) -> b : 
                    (a, b) -> a.isEmpty() ? b : b.isEmpty() ? a : String.join(ofs, a, b));
    }
    
    /**
     * @param rs the record separator to use
     * @param fs the field separator to use
     * @param keyMapper
     * @param valueMapper
     * @param merger
     * @return a {@link Parser} implementation defined with the method arguments
     */
    public static <K, V> Parser<K, V> with(String rs, String fs, 
            Function<String, K> keyMapper, Function<String, V> valueMapper, 
            BinaryOperator<V> merger) {
        return new Parser<>(rs, fs, keyMapper, valueMapper, merger);
    }
    
    /**
     * A class meant for parsing {@link CharSequence}s to {@link Map}s. Whereas the main
     * {@link MapExtractor} utility class adheres more closely to line-based processing,
     * where each stream element is a line, this uses the notion of 'records', where a
     * stream element may contain more than one record. This is useful for facilitating
     * quick, 'one-shot' {@link CharSequence}-to-{@link Map} conversions.
     * <p>
     * Implementation notes:
     * <ul>
     * <li>The underlying transformation is done using a {@link Stream#flatMap(Function)},
     * so it does not directly use the {@link Collector} implementations of
     * {@link MapExtractor}. However, certain underlying fields methods are reused and
     * will be pointed out where appropriate.</li>
     * <li>The {@link Function} for performing the flat-mapping and {@link Collector} are
     * defined during instantiation.</li>
     * <li>Owing to how functions and collectors are currently non-comparable (in layman
     * terms), instances of this class should not be used in a similar manner as well.
     * </li>
     * </ul>
     *
     * @param <K> the desired key type
     * @param <V> the desired value type
     */
    public static final class Parser<K, V> {
        private final Function<? super CharSequence, Stream<String[]>> flattener;
        private final Collector<String[], ?, Map<K, V>> collector;
        
        /**
         * @param rs the record separator to use
         * @param fs the field separator to use
         * @param keyMapper
         * @param valueMapper
         * @param merger
         */
        private Parser(String rs, String fs, Function<String, K> keyMapper, 
                Function<String, V> valueMapper, BinaryOperator<V> merger) {
            Stream.of(rs, fs, keyMapper, valueMapper, merger)
                    .forEach(Objects::requireNonNull);
            flattener = s -> Arrays.stream(s.toString().split(rs)).map(splitWith(fs));
            collector = Collectors.toMap(i -> keyMapper.apply(i[0]), 
                    i -> valueMapper.apply(i[1]), merger);
        }
        
        /**
         * @param inputs
         * @return the desired {@link Map}
         * @see #parse(Stream)
         */
        public Map<K, V> parse(CharSequence... inputs) {
            return parse(Arrays.stream(inputs));
        }
        
        /**
         * Parses the {@link Stream} into a combined {@link Map}, skipping zero-length
         * elements.
         * 
         * @param inputs
         * @return the desired {@link Map}
         */
        public Map<K, V> parse(Stream<CharSequence> inputs) {
            return inputs.filter(s -> s.length() > 0)
                    .flatMap(flattener).collect(collector);
        }
    }
}
