package com.computerwhz;

import java.util.Locale;

public enum Attribute {
    TOXICITY,
    SEVERE_TOXICITY,
    IDENTITY_ATTACK,
    INSULT,
    PROFANITY,
    THREAT,
    SEXUALLY_EXPLICIT,
    FLIRTATION;

    /** Convert strings from API/JSON safely to enum if possible. */
    public static Attribute fromString(String s) {
        if (s == null) return null;
        try {
            return Attribute.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null; // unknown/experimental attribute – caller can still access via the generic map
        }
    }
}
