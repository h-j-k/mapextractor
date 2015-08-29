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
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.testng.annotations.Test;

import com.ikueb.mapextractor.MapExtractor.Parser;

public class MapExtractorTest {

    private static final List<String> RECORDS = Arrays.asList(" a=b ", 
            "c : d ", " c : e", "c : f", "g\\=h=i", "j\\:k:l", "xyz", "");
    
    private static final List<String> COMMENTS = Arrays.asList(
            "! this is a comment", " # this is a comment");

    private static final Supplier<Stream<String>> COMBINED = 
            () -> Stream.of(RECORDS, COMMENTS).map(Collection::stream)
                        .reduce(Stream::concat).get();
    
    private static final Map<String, String> EXPECTED = toMap("a", "b ", 
            "c", "d , e, f", "g=h", "i", "j:k", "l", "xyz", "");
    
    private static final Map<String, String> EMPTY_KEY_VALUE = toMap("", "");

    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testAsProperties() throws IOException {
        final Properties props = new Properties();
        props.load(new StringReader(COMBINED.get()
                .collect(Collectors.joining(System.lineSeparator()))));
        assertThat(MapExtractor.asProperties(COMBINED.get()),
                equalTo(props));
    }

    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testGroupingBy() {
        assertThat(MapExtractor.groupingBy(COMBINED.get()),
                equalTo(toMap(asList("a", "c", "g=h", "j:k", "xyz"),
                        asList(asList("b "), asList("d ", "e", "f"),
                                asList("i"), asList("l"), asList("")))));
    }

    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testSimpleMap() {
        assertThat(MapExtractor.simpleMap(Stream.of("key=value")),
                equalTo(toMap("key", "value")));
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testSimpleMapOnDuplicateKeysThrowsException() {
        MapExtractor.simpleMap(Stream.of("a :b1", " a=b2"));
    }

    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testSimpleMapAndJoin() {
        assertThat(MapExtractor.simpleMapAndJoin(COMBINED.get()), equalTo(EXPECTED));
    }

    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testKeyAndValueMapping() {
        assertThat(Stream.of("key1=value1", "key2=value2")
                .collect(MapExtractor.toMap("=",
                        k -> new StringBuilder(k).reverse().toString().toUpperCase(),
                        v -> new StringBuilder(v).reverse().toString())),
                equalTo(toMap("1YEK", "1eulav", "2YEK", "2eulav")));
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testEmptyMapping() {
        Supplier<Stream<String>> testValues = () -> Stream.of("", " ", "[ ]*", "[ ]+");
        testValues.get()
            .map(v -> MapExtractor.toMap(v, Object::toString, Object::toString))
            .forEach(c -> assertThat(Stream.of("").collect(c), equalTo(EMPTY_KEY_VALUE)));
        testValues.get().filter(v -> !v.equals(""))
            .map(v -> MapExtractor.toMap(v, Object::toString, Object::toString))
            .forEach(c -> assertThat(Stream.of(" ").collect(c), equalTo(EMPTY_KEY_VALUE)));
        assertThat(Stream.of(" ").collect(
                MapExtractor.toMap("", Object::toString, Object::toString)), 
            equalTo(toMap(Collections.singletonList(" "), Collections.singletonList(""))));
    }
    
    @Test
    public void testParallelMapping() {
        int n = 3000;
        String join = ", ";
        Stream<String> parallelCopies = IntStream.range(0, n).mapToObj(v -> COMBINED.get())
                                            .reduce(Stream::concat).get().parallel();
        Pattern comments = Pattern.compile("^\\s*([#!]|$)");
        Function<? super CharSequence, String> toKey = k -> 
            k.toString().trim().replace("\\", "");
        Function<? super CharSequence, String> toValue = v -> 
            v.toString().replaceFirst("^\\s+", "");
        BinaryOperator<String> merger = (a, b) -> 
            (a.isEmpty() && b.isEmpty()) || a.equals(b) ? a : String.join(join, a, b);
        Map<String, String> expected = new HashMap<>(EXPECTED);
        expected.computeIfPresent("c", (k, v) -> Collections.nCopies(n, v).stream()
                .collect(Collectors.joining(join)));
        assertThat(parallelCopies.filter(s -> !comments.matcher(s).find()).collect(
                    MapExtractor.toMap("(?<!\\\\)[:=]", toKey, toValue, merger)),
                equalTo(expected));
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testCommaParsing() {
        testReadyParsing(",", () -> MapExtractor.withComma());
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testSemicolonParsing() {
        testReadyParsing(";", () -> MapExtractor.withSemicolon());
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testTabParsing() {
        testReadyParsing("\t", () -> MapExtractor.withTab());
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testNewlineParsing() {
        testReadyParsing(System.lineSeparator(), () -> MapExtractor.withNewline());
    }
    
    private static void testReadyParsing(String rs, 
            Supplier<Parser<String, String>> parserSupplier) {
        Parser<String, String> parser = parserSupplier.get();
        assertThat(parser.parse(RECORDS.stream().collect(Collectors.joining(rs))),
                equalTo(EXPECTED));
        assertThat(parser.parse("a"), equalTo(toMap("a", "")));
        assertThat(parser.parse("", rs, rs + rs), equalTo(emptyMap()));
        assertThat(parser.parse(rs + " " + rs),  equalTo(EMPTY_KEY_VALUE));
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testParsingWithRSFS() {
        assertThat(MapExtractor.with("#", "!").parse("a#a!", "a!b#a!c"), 
                equalTo(toMap("a", "bc")));
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testParsingWithRSFSNullOFS() {
        assertThat(MapExtractor.with("!", "#", null).parse("a#b!a#c"), 
                equalTo(toMap("a", "c")));
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testCustomParsing() {
        assertThat(MapExtractor.with("\\|", "!", String::toLowerCase, 
                    Integer::parseInt, (a, b) -> a + b).parse("A!1|a!2"),
                equalTo(toMap(Collections.singletonList("a"),
                        Collections.singletonList(Integer.valueOf(3)))));
    }

    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testValueMerging() {
        assertThat(Stream.of("key1=1,2", "key2=3,4", "key1=5,6").collect(
                MapExtractor.toMap("=", Object::toString,
                        v -> Arrays.stream(v.toString().split(","))
                                    .mapToInt(Integer::parseInt).sum(),
                        (a, b) -> a + b)).get("key1"), equalTo(Integer.valueOf(14)));
    }
    
    @Test(threadPoolSize = 2, invocationCount = 2)
    public void testEmptyParsing() {
        assertThat(MapExtractor.with("", "").parse(""), equalTo(emptyMap()));
        assertThat(MapExtractor.with("", " ").parse(""), equalTo(emptyMap()));
        assertThat(MapExtractor.with("", "").parse(" "), equalTo(EMPTY_KEY_VALUE));
        assertThat(MapExtractor.with("", " ").parse(" "), equalTo(EMPTY_KEY_VALUE));
        Stream.of(MapExtractor.with(" ", ""),
                    MapExtractor.with(" ", " "),
                    MapExtractor.with("[ ]*", "[ ]*"),
                    MapExtractor.with("[ ]*", "[ ]+"),
                    MapExtractor.with("[ ]+", "[ ]*"),
                    MapExtractor.with("[ ]+", "[ ]+"))
                .forEach(p -> assertThat(p.parse("", " "), equalTo(emptyMap())));
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
