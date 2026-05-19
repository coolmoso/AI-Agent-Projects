package org.example.qaagent.util;

import org.springframework.stereotype.Component;

@Component
public class LanguageDetector {

    /**
     * Detect the primary language of the given text.
     * Uses Unicode character analysis: if a significant portion of characters
     * are CJK (Chinese/Japanese/Korean), classify as "zh"; otherwise "en".
     */
    public String detect(String text) {
        if (text == null || text.isBlank()) return "en";

        int cjkCount = 0;
        int totalAlphanumeric = 0;

        for (char c : text.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                totalAlphanumeric++;
                if (isCjk(c)) {
                    cjkCount++;
                }
            }
        }

        if (totalAlphanumeric == 0) return "en";
        double cjkRatio = (double) cjkCount / totalAlphanumeric;
        return cjkRatio > 0.3 ? "zh" : "en";
    }

    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
            || block == Character.UnicodeBlock.BOPOMOFO
            || block == Character.UnicodeBlock.HANGUL_SYLLABLES
            || block == Character.UnicodeBlock.HIRAGANA
            || block == Character.UnicodeBlock.KATAKANA;
    }
}
