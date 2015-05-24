# Mapextractor

A map extraction utility to convert lines of `"key=value"` to `Map` objects, in the style of `Collections.toMap()`.

Motivation
---

Sometimes, you encounter `String` inputs in the format of `key=value` and you need to turn them into `Map`s.

Traditionally, that can be done something like:

    public static Map<String, String> toMap(String input) {
        Map<String, String> result = new HashMap<>();
        for (String entry : input.split(ENTRY_SEPARATOR)) {
            String[] keyValue = entry.split(KEY_VALUE_SEPARTOR, 2);
            result.put(keyValue[0], keyValue[1]);
        }
        return result;
    }

The *slightly-advanced* reader might opt for `Properties.load()` by wrapping `input` inside a `StringReader`, if it
matches the conventions imposed. Even so, doing a "group-by", or asserting for distinct keys, complicates code further.

`MapExtractor` can be used directly on a `Stream` to peform the same operations, in a more fluent manner.

    Map<String, String> map = Pattern.compile(ENTRY_SEPARATOR).splitAsStream(input)
                                .collect(MapExtractor.toMap(KEY_VALUE_SEPARATOR, 
                                        Function.identity(), Function.identity(), 
                                        (a, b) -> b));

Alternatively, assuming we already have a `inputStream` and it can be processed as a `Properties` object:

    Properties props = MapExtractor.asProperties(inputStream);

Other features include:

* Grouping-by or joining values on duplicate keys (instead of throwing an `IllegalStateException`)
* Additional key/value conversion as required

Bugs/feedback
---

Please make use of the GitHub features to report any bugs, issues, or even pull requests. :)

Enjoy!