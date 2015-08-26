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

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import com.ikueb.mapextractor.MapExtractor.Parser;

public class MapExtractorTest {

    /**
     * Test entries consist of optional whitespaces, escaped key delimiters, comments
     * and no-value keys.
     */
    private static final Supplier<Stream<String>> TEST_ENTRIES = () -> Stream.of(
            " a=b ", "c : d ", " c : e", "c : f", "g\\=h=i", "j\\:k:l",
            " ! this is a comment", " # this is a comment", "xyz");

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
                equalTo(toMap(asList("a", "c", "g=h", "j:k", "xyz"),
                        asList(asList("b "), asList("d ", "e", "f"),
                                asList("i"), asList("l"), asList("")))));
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
        assertThat(Stream.of("key1=value1", "key2=value2")
                .collect(MapExtractor.toMap("=",
                        k -> new StringBuilder(k).reverse().toString().toUpperCase(),
                        v -> new StringBuilder(v).reverse().toString())),
                equalTo(toMap("1YEK", "1eulav", "2YEK", "2eulav")));
    }
    
    @Test
    public void testNewlineParsing() {
        testParsing(System.lineSeparator(), () -> MapExtractor.withNewline());
    }
    
    @Test
    public void testCommaParsing() {
        testParsing(",", () -> MapExtractor.withComma());
    }
    
    @Test
    public void testSemicolonParsing() {
        testParsing(";", () -> MapExtractor.withSemicolon());
    }
    
    private void testParsing(String recordSeparator, 
            Supplier<Parser<String, String>> parserSupplier) {
        assertThat(parserSupplier.get().parse(
                String.join(recordSeparator, "a=b", "c:d", "e\\=f:g", "h\\:i=j")),
        equalTo(toMap("a", "b", "c", "d", "e=f", "g", "h:i", "j")));
    }
    
    @Test
    public void testParsingWithRSFS() {
        assertThat(MapExtractor.with("\\|", "!").parse("a|a!|a!b|a!c"), 
                equalTo(toMap("a", "bc")));
    }
    
    @Test
    public void testParsingWithRSFSNullOFS() {
        assertThat(MapExtractor.with("\\|", "!", null).parse("a!b|a!c"), 
                equalTo(toMap("a", "c")));
    }
    
    @Test
    public void testCustomParsing() {
        assertThat(MapExtractor.build("\\|", "~", Function.identity(), 
                    Integer::parseInt, (a, b) -> a + b).parse("a~1|a~2"),
                equalTo(toMap(Collections.singletonList("a"),
                        Collections.singletonList(Integer.valueOf(3)))));
    }

    @Test
    public void testValueMerging() {
        assertThat(Stream.of("k1=1,2", "k2=3,4", "k1=5,6").collect(
                MapExtractor.toMap("=", Object::toString,
                        v -> Pattern.compile(",").splitAsStream(v)
                        .mapToInt(Integer::parseInt).sum(),
                        (a, b) -> a + b)).get("k1"), equalTo(Integer.valueOf(14)));
    }

    @SafeVarargs
	private static <T> Map<T, T> toMap(T... inputs) {
        if (inputs.length % 2 != 0) {
            throw new IllegalArgumentException("Unable to map an odd number of inputs.");
        }
        List<T> keys = new ArrayList<>();
        List<T> values = new ArrayList<>();
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
    private static <K, V> Map<K, V> toMap(List<K> keys, List<V> values) {
        if (keys.size() != values.size()) {
            throw new IllegalArgumentException("Keys and values counts do not match.");
        }
        return IntStream.range(0, keys.size()).collect(HashMap::new,
                (map, i) -> map.put(keys.get(i), values.get(i)), Map::putAll);
    }
}
