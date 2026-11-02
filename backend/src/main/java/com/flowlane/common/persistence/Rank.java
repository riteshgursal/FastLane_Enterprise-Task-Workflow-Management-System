package com.flowlane.common.persistence;

/**
 * Fractional ordering keys for drag and drop. {@code between(a, b)} returns a string that
 * sorts strictly between {@code a} and {@code b}; either bound may be null.
 */
public final class Rank {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    private Rank() {}

    public static String first() {
        return between(null, null);
    }

    public static String between(String before, String after) {
        String lo = before == null ? "" : before;
        String hi = after;
        if (hi != null && !lo.isEmpty() && lo.compareTo(hi) >= 0) {
            throw new IllegalArgumentException("before must sort before after");
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int l = i < lo.length() ? ALPHABET.indexOf(lo.charAt(i)) : 0;
            int h = hi != null && i < hi.length() ? ALPHABET.indexOf(hi.charAt(i)) : BASE;
            if (h - l > 1) {
                out.append(ALPHABET.charAt((l + h) / 2));
                return out.toString();
            }
            out.append(ALPHABET.charAt(l));
            if (h - l == 1) {
                hi = null;
            }
            i++;
        }
    }
}
