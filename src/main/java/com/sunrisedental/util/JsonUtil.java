package com.sunrisedental.util;

public final class JsonUtil {

    private JsonUtil() {
    }

    public static String quote(String value) {

        if (value == null) {
            return "null";
        }

        return "\"" + escape(value) + "\"";
    }

    public static String escape(String value) {

        StringBuilder result = new StringBuilder();

        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");

                default -> {
                    if (character < 32) {
                        result.append(
                                String.format(
                                        "\\u%04x",
                                        (int) character
                                )
                        );
                    } else {
                        result.append(character);
                    }
                }
            }
        }

        return result.toString();
    }
}