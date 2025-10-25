package com.computerwhz;

import java.util.*;

/**
 * Immutable result returned by a Perspective API analysis.
 * - Holds the message, languages, and a map of attribute -> score (0..1).
 * - Provides typed convenience getters for popular attributes.
 * - Optionally carries span-level annotations (when requested).
 */
public final class PerspectiveScore {

    /** Original text that was analyzed. */
    private final String message;

    /** Languages used for the request (ISO codes like "en", "es", ...). */
    private final List<String> languages;

    /** Attribute -> summary score (probability 0..1). */
    private final Map<String, Double> scores;

    /** Optional per-span annotations. */
    private final List<SpanAnnotation> spanAnnotations;

    /** Timestamp of when you computed the result. */
    private final long computedAtEpochMillis;

    private PerspectiveScore(Builder b) {
        this.message = Objects.requireNonNull(b.message, "message");
        this.languages = Collections.unmodifiableList(new ArrayList<>(b.languages));
        this.scores = Collections.unmodifiableMap(new LinkedHashMap<>(b.scores));
        this.spanAnnotations = Collections.unmodifiableList(new ArrayList<>(b.spanAnnotations));
        this.computedAtEpochMillis = b.computedAtEpochMillis != null ? b.computedAtEpochMillis : System.currentTimeMillis();
    }

    // ---------- Accessors

    public String getMessage() { return message; }

    public List<String> getLanguages() { return languages; }

    public Map<String, Double> getScores() { return scores; }

    public List<SpanAnnotation> getSpanAnnotations() { return spanAnnotations; }

    public long getComputedAtEpochMillis() { return computedAtEpochMillis; }

    /** Generic accessor (by attribute name as used by the API). */
    public OptionalDouble scoreOf(String attribute) {
        Double v = scores.get(attribute);
        return (v == null) ? OptionalDouble.empty() : OptionalDouble.of(v);
    }

    /** Typed accessor using the Attribute enum when available. */
    public OptionalDouble scoreOf(Attribute attr) {
        return scoreOf(attr.name());
    }

    // ---------- Convenience getters for common attributes

    public OptionalDouble toxicity()          { return scoreOf(Attribute.TOXICITY); }
    public OptionalDouble severeToxicity()    { return scoreOf(Attribute.SEVERE_TOXICITY); }
    public OptionalDouble identityAttack()    { return scoreOf(Attribute.IDENTITY_ATTACK); }
    public OptionalDouble insult()            { return scoreOf(Attribute.INSULT); }
    public OptionalDouble profanity()         { return scoreOf(Attribute.PROFANITY); }
    public OptionalDouble threat()            { return scoreOf(Attribute.THREAT); }
    public OptionalDouble sexuallyExplicit()  { return scoreOf(Attribute.SEXUALLY_EXPLICIT); }
    public OptionalDouble flirtation()        { return scoreOf(Attribute.FLIRTATION); }

    /** Simple helper: is the text "toxic" under your threshold? */
    public boolean isToxic(double threshold) {
        OptionalDouble t = toxicity();
        return t.isPresent() && t.getAsDouble() >= threshold;
    }

    @Override public String toString() {
        return "PerspectiveScore{" +
                "message.len=" + (message == null ? 0 : message.length()) +
                ", languages=" + languages +
                ", scores=" + scores +
                ", spans=" + spanAnnotations.size() +
                ", computedAt=" + computedAtEpochMillis +
                '}';
    }

    // ---------- Builder

    public static Builder builder(String message) { return new Builder(message); }

    public static final class Builder {
        private final String message;
        private List<String> languages = new ArrayList<>();
        private Map<String, Double> scores = new LinkedHashMap<>();
        private List<SpanAnnotation> spanAnnotations = new ArrayList<>();
        private Long computedAtEpochMillis;

        private Builder(String message) { this.message = message; }

        /** Add/replace a score for an attribute (any name supported by Perspective). */
        public Builder putScore(String attribute, double value) {
            this.scores.put(attribute, value);
            return this;
        }

        /** Add/replace using the typed enum. */
        public Builder putScore(Attribute attr, double value) {
            return putScore(attr.name(), value);
        }

        /** Add all scores from a map. */
        public Builder putAllScores(Map<String, Double> m) {
            this.scores.putAll(m);
            return this;
        }

        /** Set languages used to evaluate (empty list if not specified). */
        public Builder languages(List<String> langs) {
            this.languages = (langs == null) ? new ArrayList<>() : new ArrayList<>(langs);
            return this;
        }

        /** Add a single span annotation. */
        public Builder addSpan(SpanAnnotation span) {
            if (span != null) this.spanAnnotations.add(span);
            return this;
        }

        /** Add multiple span annotations. */
        public Builder addAllSpans(Collection<SpanAnnotation> spans) {
            if (spans != null) this.spanAnnotations.addAll(spans);
            return this;
        }

        /** Override the computed-at timestamp (normally set automatically). */
        public Builder computedAtEpochMillis(long epochMillis) {
            this.computedAtEpochMillis = epochMillis;
            return this;
        }

        public PerspectiveScore build() { return new PerspectiveScore(this); }
    }

    // ---------- Nested types

    /**
     * Per-span annotation (when spanAnnotations=true on the request).
     * begin & end are character offsets in the original message [begin, end).
     */
    public static final class SpanAnnotation {
        private final int begin;
        private final int end;
        private final String attribute;  // keep flexible for experimental names
        private final double score;

        public SpanAnnotation(int begin, int end, String attribute, double score) {
            if (begin < 0 || end < begin) throw new IllegalArgumentException("Invalid span range");
            this.begin = begin;
            this.end = end;
            this.attribute = Objects.requireNonNull(attribute, "attribute");
            this.score = score;
        }

        public int getBegin() { return begin; }
        public int getEnd() { return end; }
        public String getAttribute() { return attribute; }
        public double getScore() { return score; }

        public Optional<Attribute> getAttributeEnum() {
            return Optional.ofNullable(Attribute.fromString(attribute));
        }

        @Override public String toString() {
            return "Span[" + begin + "," + end + ") " + attribute + "=" + score;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SpanAnnotation)) return false;
            SpanAnnotation that = (SpanAnnotation) o;
            return begin == that.begin && end == that.end &&
                    Double.compare(that.score, score) == 0 &&
                    attribute.equals(that.attribute);
        }

        @Override public int hashCode() {
            return Objects.hash(begin, end, attribute, score);
        }
    }
}
