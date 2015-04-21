package com.ikueb.mapextractor;

import java.util.ArrayList;
import java.util.Arrays;
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
 * <li>A set of helper methods to easily convert {@link Stream}s of {@link CharSequence}
 * s to either {@link Properties} or {@link Map} instances.
 * <ul>
 * <li>These methods operate on streams and reproduces the parsing logic in
 * {@link Properties#load(java.io.Reader)} as closely as possible. The only differences
 * are no support for multi-line values and the default key delimiter can only be
 * escaped with {@code "\"} once.</li>
 * </ul>
 * </li>
 * <li>A set of collectors to be used in {@link Stream#collect(Collector)} as terminal
 * operations.
 * <ul>
 * <li>These methods are meant to be drop-in replacements for the standard JDK
 * {@code Collectors.toMap()} methods, but with a {@code regex} prefixed argument to
 * split each stream element into key-value pairings.</li>
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
     * instance, except that multi-line values are not supported and the default key
     * delimiter can only be escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @return a {@link Properties} instance based on {@code entries}
     * @see Properties#load(java.io.Reader)
     */
    public static Properties asProperties(final Stream<? extends CharSequence> entries) {
        final Properties props = new Properties();
        props.putAll(skipComments(entries).collect(toMap(REGEX_DELIMITER, toKey(), toValue(),
                (a, b) -> { return b; })));
        return props;
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that values for the same key are put into a {@link List} and
     * multi-line values are not supported and the default key delimiter can only be
     * escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @return a {@link Map}
     */
    public static Map<String, List<String>> groupingBy(
            final Stream<? extends CharSequence> entries) {
        return skipComments(entries).collect(groupingBy(REGEX_DELIMITER, toKey(), toValue()));
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that duplicate keys results in an {@link IllegalStateException},
     * multi-line values are not supported and the default key delimiter can only be
     * escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @return a {@link Map}
     * @see #toMap(String, Function, Function)
     */
    public static Map<String, String> simpleMap(
            final Stream<? extends CharSequence> entries) {
        return skipComments(entries).collect(toMap(REGEX_DELIMITER, toKey(), toValue()));
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that values for the same key are joined, multi-line values are
     * not supported and the default key delimiter can only be escaped with {@code "\"}
     * once.
     *
     * @param entries lines to process
     * @return a {@link Map}
     */
    public static Map<String, String> simpleMapAndJoin(
            final Stream<? extends CharSequence> entries) {
        return simpleMapAndJoin(entries, JOIN_DELIMITER);
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that values for the same key are joined using
     * {@code joinDelimiter}, multi-line values are not supported and the default key
     * delimiter can only be escaped with {@code "\"} once.
     *
     * @param entries lines to process
     * @param joinDelimiter the join delimiter to use
     * @return a {@link Map}
     */
    public static Map<String, String> simpleMapAndJoin(
            final Stream<? extends CharSequence> entries, final String joinDelimiter) {
        return skipComments(entries).collect(toMapAndJoin(REGEX_DELIMITER, joinDelimiter));
    }


    /**
     * With the exception of the first argument {@code regex} for splitting each stream
     * element, this method is meant to be used in a similar way as the equivalent in
     * the {@link Collectors} class, except that the third argument {@code valueMapper}
     * exists to do the conversion of map values as well, instead of using the stream
     * elements.
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
            final String regex, final Function<? super CharSequence, K> keyMapper,
            final Function<? super CharSequence, V> valueMapper) {
        return toMap(regex, keyMapper, enlist(valueMapper), joinList());
    }

    /**
     * Handles the stream like the line-oriented format for loading a {@link Properties}
     * instance, except that values for the same key are joined using
     * {@code joinDelimiter}, multi-line values are not supported and the default key
     * delimiter can only be escaped with {@code "\"} once.
     *
     * @param regex the delimiter to use
     * @param joinDelimiter the join delimiter to use
     * @return a {@link Collector}
     */
    public static Collector<CharSequence, ?, Map<String, String>> toMapAndJoin(
            final String regex, final String joinDelimiter) {
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
    public static <K, V> Collector<CharSequence, ?, Map<K, V>> toMap(
            final String regex, final Function<? super CharSequence, K> keyMapper,
            final Function<? super CharSequence, V> valueMapper) {
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
     * @param mergeFunction
     * @return a {@link Map}
     * @see Collectors#toMap(Function, Function, BinaryOperator)
     */
    public static <K, V> Collector<CharSequence, ?, Map<K, V>> toMap(
            final String regex, final Function<? super CharSequence, K> keyMapper,
            final Function<? super CharSequence, V> valueMapper,
            final BinaryOperator<V> mergeFunction) {
        return toMap(regex, keyMapper, valueMapper, mergeFunction, HashMap::new);
    }

    /**
     * With the exception of the first argument {@code regex} for splitting each stream
     * element, this method is meant to be used in the same way as the equivalent in the
     * {@link Collectors} class.
     *
     * @param regex the delimiter to use
     * @param keyMapper
     * @param valueMapper
     * @param mergeFunction
     * @param mapSupplier
     * @return a {@link Map}
     * @see Collectors#toMap(Function, Function, BinaryOperator, Supplier)
     */
    public static <K, V, M extends Map<K, V>> Collector<CharSequence, ?, M> toMap(
            final String regex, final Function<? super CharSequence, K> keyMapper,
            final Function<? super CharSequence, V> valueMapper,
            final BinaryOperator<V> mergeFunction, final Supplier<M> mapSupplier) {
        Stream.of(regex, keyMapper, valueMapper, mergeFunction, mapSupplier)
                .forEach(Objects::requireNonNull);
        return Collector.of(mapSupplier,
                (m, i) -> { CharSequence[] pair = splitWith(regex).apply(i);
                    m.merge(keyMapper.apply(pair[0]), valueMapper.apply(pair[1]),
                            mergeFunction); },
                (a, b) -> { b.entrySet().stream().forEach(
                    entry -> a.merge(entry.getKey(), entry.getValue(), mergeFunction));
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
    private static <T extends CharSequence> Stream<T> skipComments(
            final Stream<T> stream) {
        return stream.filter(comments().negate());
    }

    /**
     * @return a predicate for matching comments
     */
    private static Predicate<? super CharSequence> comments() {
        return v -> Pattern.compile("^\\s*[#!]").matcher(v).find();
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
    private static Function<? super CharSequence, CharSequence[]> splitWith(
            final String regex) {
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
            final Function<? super T, U> mapper) {
        return mapper.andThen(v -> new ArrayList<>(Arrays.asList(v)));
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
    private static BinaryOperator<String> join(final String delimiter) {
        return (a, b) -> String.join(delimiter, a, b);
    }

    /**
     * To be used for cases where we are attempting to merge on duplicate keys, this
     * mirrors the default behavior of {@link Collectors#toMap(Function, Function)}.
     *
     * @return a {@link BinaryOperator} that always throw an
     *         {@link IllegalStateException}
     */
    private static <T> BinaryOperator<T> duplicateKeyMergeThrower() {
        return (a, b) -> {
            throw new IllegalStateException(String.format(
                    "Duplicate key for values \"%s\" and \"%s\".", a, b));
        };
    }
}
