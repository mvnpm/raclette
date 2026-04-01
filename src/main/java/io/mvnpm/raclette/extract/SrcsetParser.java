package io.mvnpm.raclette.extract;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Parses srcset attribute values to extract image URLs.
 * Handles unencoded commas in URLs per the WHATWG spec.
 *
 * Translated from lychee's extract/html/srcset.rs — same state machine algorithm.
 *
 * @see <a href="https://html.spec.whatwg.org/multipage/images.html#parsing-a-srcset-attribute">WHATWG spec</a>
 */
public class SrcsetParser {

    private enum State {
        INSIDE_DESCRIPTOR,
        AFTER_DESCRIPTOR,
        INSIDE_PARENS
    }

    /**
     * Parse a srcset string into a list of URLs.
     */
    public static List<String> parse(String input) {
        List<String> candidates = new ArrayList<>();
        String remaining = input;

        while (!remaining.isEmpty()) {
            String[] result = parseOneUrl(remaining);
            if (result == null) {
                // parse error
                return List.of();
            }
            remaining = result[0];
            if (result[1] != null) {
                candidates.add(result[1]);
            }
        }

        return candidates;
    }

    /**
     * Implements one iteration of the "splitting loop" from the WHATWG reference algorithm.
     *
     * @return [remaining, url] where url may be null, or null on parse error
     */
    private static String[] parseOneUrl(String remaining) {
        // Skip leading commas and whitespace (matches lychee srcset.rs:80-84)
        String[] split = splitAt(remaining, c -> c == ',' || Character.isWhitespace(c));
        String start = split[0];
        remaining = split[1];

        if (start.indexOf(',') >= 0) {
            // parse error: too many commas
            return null;
        }

        if (remaining.isEmpty()) {
            return new String[] { "", null };
        }

        // Collect the URL (non-whitespace characters)
        split = splitAt(remaining, c -> !Character.isWhitespace(c));
        String url = split[0];
        remaining = split[1];

        // Count trailing commas
        int commaCount = 0;
        for (int i = url.length() - 1; i >= 0 && url.charAt(i) == ','; i--) {
            commaCount++;
        }
        if (commaCount > 1) {
            // parse error: trailing commas
            return null;
        }

        // Trim trailing comma from URL
        if (commaCount > 0) {
            url = url.substring(0, url.length() - commaCount);
        }

        // Skip whitespace
        split = splitAt(remaining, Character::isWhitespace);
        remaining = split[1];

        // Skip descriptor
        remaining = skipDescriptor(remaining);

        return new String[] { remaining, url };
    }

    /**
     * Skip over a descriptor. Returns the string remaining after the descriptor
     * (beginning after the next comma, or empty string).
     */
    private static String skipDescriptor(String remaining) {
        State state = State.INSIDE_DESCRIPTOR;

        for (int i = 0; i < remaining.length(); i++) {
            char c = remaining.charAt(i);
            switch (state) {
                case INSIDE_DESCRIPTOR:
                    if (Character.isWhitespace(c)) {
                        state = State.AFTER_DESCRIPTOR;
                    } else if (c == '(') {
                        state = State.INSIDE_PARENS;
                    } else if (c == ',') {
                        return remaining.substring(i + 1);
                    }
                    break;
                case INSIDE_PARENS:
                    if (c == ')') {
                        state = State.INSIDE_DESCRIPTOR;
                    }
                    break;
                case AFTER_DESCRIPTOR:
                    if (!Character.isWhitespace(c)) {
                        state = State.INSIDE_DESCRIPTOR;
                    }
                    break;
            }
        }

        return "";
    }

    /**
     * Split input at the first character for which the predicate returns false.
     * Returns [prefix, remainder].
     */
    static String[] splitAt(String input, Predicate<Character> predicate) {
        for (int i = 0; i < input.length(); i++) {
            if (!predicate.test(input.charAt(i))) {
                return new String[] { input.substring(0, i), input.substring(i) };
            }
        }
        return new String[] { input, "" };
    }
}
