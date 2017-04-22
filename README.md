# Mapextractor

[![Build Status](https://travis-ci.org/h-j-k/mapextractor.svg?branch=master)](https://travis-ci.org/h-j-k/mapextractor) 
[![codecov.io](http://codecov.io/github/h-j-k/mapextractor/coverage.svg?branch=master)](http://codecov.io/github/h-j-k/mapextractor?branch=master)

A map extraction utility to convert lines of `"key=value"` to `Map` objects, in the style of `Collections.toMap()`.

[GitHub project page](https://github.com/h-j-k/mapextractor)

[Javadocs](https://h-j-k.github.io/mapextractor/javadoc)

# Motivation

Sometimes, you need to turn `String` inputs in the format of `key=value` into `Map`s.

Traditionally, that can be done something like:

    public static Map<String, String> toMap(String input) {
        Map<String, String> result = new HashMap<>();
        for (String entry : input.split(ENTRY_SEPARATOR)) {
            String[] keyValue = entry.split(KEY_VALUE_SEPARTOR, 2);
            result.put(keyValue[0], keyValue[1]);
        }
        return result;
    }

The *slightly-advanced* reader might opt for `Properties.load()` by wrapping `input` inside a `StringReader`, if it matches the conventions imposed. Even so, doing a "group-by", or asserting for distinct keys, complicates code further.

`MapExtractor` can be used directly on a `Stream` to peform the same operations, in a more fluent manner.

    Map<String, String> map = Pattern.compile(ENTRY_SEPARATOR).splitAsStream(input)
                                .collect(MapExtractor.toMap(KEY_VALUE_SEPARATOR, 
                                        Function.identity(), Function.identity(), 
                                        (a, b) -> b));

Alternatively, assuming we already have a `inputStream` and it can be processed as a `Properties` object:

    Properties props = MapExtractor.asProperties(inputStream);

There is also a `Parser` implementation for facilitating quick, 'one-shot' `CharSequence`-to-`Map` conversion. For example:

    Map<String, String> map = MapExtractor.withComma().parse("k1=v1,k2=v2");
    // result of map.toString()
    {k1=v1, k2=v2}

# Features

Comparison to `Properties.load()`
---

Most `MapExtractor` methods without key-and-value mapping arguments uses the implementation suggested in `Properties.load()`:
* Keys and values are mapped accordingly in the manner described (of the method).
  * Escaping the delimiter, i.e. "\", in the key can only be done once.
* Comment and whitespace-only lines are skipped in the manner described.
* Multi-line values described *spanning* stream elements are *not* supported.

Methods highight list
---

For returning a `Map` or `Properties`:
* `asProperties(Stream)` directly converts to a `Properties` object.
* `groupingBy(Stream)` puts values of duplicate keys into a `List`.
* `simpleMap(Stream)` throws `IllegalStateException` on duplicate keys.
* `simpleMapAndJoin(Stream)` uses `", "` to join values of duplicate keys.

For returning a `Collector`:
* `toMapAndJoin(String, String)` splits key-value pairs using the `String` regex and uses the `joinDelimiter` to join values of duplicate keys.
* `toMap(String, Function, Function)` splits key-value pairs using the `String` regex and throws `IllegalStateException` on duplicate keys.
*  `groupingBy(String, Function, Function)` splits key-value pairs using the `String` regex before applying the key and value mappings, and puts mapped values into a `List`.

For parsing (note that zero-length inputs will be skipped):
* `withComma()` processes records that are comma-delimited first, before deriving the key-value mappings. 
* `withChar(char)` processes records that are delimited by the specified character first, before deriving the key-value mappings.
* `with(String, String, String)` processes records with the specified record, field and output field separators. This is inspired by the handling in [`awk`][1].
* `with(String, String, Function, Function, BinaryOperator)` processes records with the specified record and field separators, before applying the key and value mappings.

# Bugs/feedback

Please make use of the GitHub features to report any bugs, issues, or even pull requests. :)

Enjoy!

[1]: https://en.m.wikipedia.org/wiki/Awk