package com.timo.words.algorithm.common;

/**
 * Levenshtein edit distance (insert/delete/substitute = 1).
 *
 * Used by the reverse-recall mode to grant partial credit for single-letter typos —
 * a learner who types "recieve" when the answer is "receive" demonstrably knew the
 * word and deserves a softer grade than someone who left the box blank.
 *
 * Iterative two-row DP: O(m*n) time, O(min(m,n)) extra space.
 * Mirrors the frontend implementation in {@code frontend/src/utils/string.js}.
 */
public final class EditDistance {

    private EditDistance() {}

    /**
     * Compute the Levenshtein distance between two strings.
     *
     * @param a first string (nullable, treated as empty)
     * @param b second string (nullable, treated as empty)
     * @return number of single-character edits required to transform a into b
     */
    public static int levenshtein(String a, String b) {
        String s1 = a == null ? "" : a;
        String s2 = b == null ? "" : b;
        if (s1.equals(s2)) return 0;
        if (s1.isEmpty()) return s2.length();
        if (s2.isEmpty()) return s1.length();

        // Ensure s2 is the shorter to minimize the inner array
        if (s1.length() < s2.length()) {
            String tmp = s1;
            s1 = s2;
            s2 = tmp;
        }

        int n = s1.length();
        int m = s2.length();
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }

    /**
     * Normalize then compare. Trims whitespace, lower-cases both sides.
     * Empty user input always returns a large distance ({@code Integer.MAX_VALUE / 2})
     * so callers cannot accidentally treat blank submissions as near-misses.
     */
    public static int normalizedDistance(String userInput, String target) {
        String u = userInput == null ? "" : userInput.trim().toLowerCase();
        String t = target == null ? "" : target.trim().toLowerCase();
        if (u.isEmpty()) return Integer.MAX_VALUE / 2;
        return levenshtein(u, t);
    }
}
