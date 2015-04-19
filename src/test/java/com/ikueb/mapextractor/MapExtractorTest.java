package com.ikueb.mapextractor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.Test;

public class MapExtractorTest {

    /**
     * Test entries consist of optional whitespaces, escaped key delimiters, comments
     * and no-value keys.
     */
    private static final Supplier<Stream<String>> TEST_ENTRIES = () -> {
        return Stream.of(" a=b ", "c : d ", " c : e", "c : f", "g\\=h=i", "j\\:k:l",
                " ! this is a comment", " # this is a comment", "xyz");
    };

    @Test
    public void testAsProperties() throws IOException {
        final Properties props = new Properties();
        props.load(new StringReader(TEST_ENTRIES.get()
                .collect(Collectors.joining("\n"))));
        assertThat(MapExtractor.asProperties(TEST_ENTRIES.get()),
                equalTo(new HashMap<>(props)));
    }

    @Test
    public void testGroupingBy() {
        assertThat(MapExtractor.groupingBy(TEST_ENTRIES.get()),
                equalTo(toMap(enlist("a", "c", "g=h", "j:k", "xyz"),
                        enlist(enlist("b "), enlist("d ", "e", "f"),
                                enlist("i"), enlist("l"), enlist("")))));
    }

    @Test
    public void testSimpleMap() {
        assertThat(MapExtractor.simpleMap(Stream.of("key=value")),
                equalTo(toMap("key", "value")));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testSimpleMapOnDuplicateKeysThrowsException() {
        MapExtractor.simpleMap(Stream.of("a :b1", " a=b2"));
    }

    @Test
    public void testSimpleMapAndJoin() {
        assertThat(MapExtractor.simpleMapAndJoin(TEST_ENTRIES.get()),
                equalTo(toMap("a", "b ", "c", "d , e, f",
                        "g=h", "i", "j:k", "l", "xyz", "")));
    }

    @Test
    public void testKeyAndValueMapping() {
        assertThat(Stream.of(
                new StringBuilder("key1=value1"), new StringBuilder("key2=value2"))
                .collect(MapExtractor.toMap("=",
                        k -> new StringBuilder(k).reverse().toString().toUpperCase(),
                        v -> new StringBuilder(v).reverse().toString())),
                equalTo(toMap("1YEK", "1eulav", "2YEK", "2eulav")));
    }

    @SuppressWarnings("boxing")
    @Test
    public void testValueMerging() {
        assertThat(Stream.of("k1=1,2", "k2=3,4", "k1=5,6").collect(
                MapExtractor.toMap("=", Object::toString,
                        v -> Pattern.compile(",").splitAsStream(v)
                        .mapToInt(Integer::parseInt).sum(),
                        (a, b) -> a + b)).get("k1"), equalTo(14));
    }

    private static <T> List<T> enlist(T... values) {
        return Arrays.asList(values);
    }

    private static <T> Map<T, T> toMap(T... inputs) {
        if (Objects.requireNonNull(inputs).length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Unable to create pairings from an odd number of inputs.");
        }
        final List<T> keys = new ArrayList<>();
        final List<T> values = new ArrayList<>();
        for (int i = 0; i < inputs.length; i++) {
            keys.add(inputs[i]);
            values.add(inputs[++i]);
        }
        return toMap(keys, values);
    }

    /**
     * Creates a new {@link Map} by iterating through both collections.
     *
     * @param keys the keys to use for the map.
     * @param values the values to use for the map, in the same order as the keys.
     * @return a new {@link Map} with the desired mappings
     * @throws IllegalArgumentException if there is a different number of distinct keys
     *             and values
     */
    private static <K, V> Map<K, V> toMap(Collection<K> keys, Collection<V> values) {
        final Map<K, V> result = new HashMap<>();
        final Iterator<K> keyIterator = Objects.requireNonNull(keys).iterator();
        final Iterator<V> valueIterator = Objects.requireNonNull(values).iterator();
        while (keyIterator.hasNext() && valueIterator.hasNext()) {
            result.put(keyIterator.next(), valueIterator.next());
        }
        if (keyIterator.hasNext() || valueIterator.hasNext()) {
            throw new IllegalArgumentException(
                    "Different number of distinct keys and values.");
        }
        return result;
    }
}
