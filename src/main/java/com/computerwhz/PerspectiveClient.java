package com.computerwhz;

import com.google.gson.*;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/**
 * Lightweight Java client for Google's Perspective API (single-language).
 *
 * Endpoint: https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze
 */
public class PerspectiveClient {

    public static final String DEFAULT_ENDPOINT =
            "https://commentanalyzer.googleapis.com/v1alpha1/comments:analyze";

    private final String apiKey;
    private final String endpoint;
    private final OkHttpClient http;
    private final Gson gson;

    // ---------- ctor

    public PerspectiveClient(String apiKey) {
        this(apiKey, DEFAULT_ENDPOINT, defaultHttp(), defaultGson());
    }

    public PerspectiveClient(String apiKey, String endpoint, OkHttpClient http, Gson gson) {
        if (apiKey == null || apiKey.isEmpty()) throw new IllegalArgumentException("apiKey is required");
        this.apiKey = apiKey;
        this.endpoint = (endpoint == null || endpoint.isEmpty()) ? DEFAULT_ENDPOINT : endpoint;
        this.http = (http == null) ? defaultHttp() : http;
        this.gson = (gson == null) ? defaultGson() : gson;
    }

    private static OkHttpClient defaultHttp() {
        return new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(30))
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(25))
                .writeTimeout(Duration.ofSeconds(25))
                .build();
    }

    private static Gson defaultGson() {
        return new GsonBuilder().disableHtmlEscaping().create();
    }

    // ---------- public API (single language)

    /** Simplest: analyze toxicity only, language = "en". */
    public PerspectiveScore toxicity(String text) throws IOException {
        return analyze(text, Collections.singletonList(Attribute.TOXICITY), new AnalyzeOptions());
    }

    /** Analyze with common attributes; language defaults to "en". */
    public PerspectiveScore analyze(String text, List<Attribute> attributes) throws IOException {
        return analyze(text, attributes, new AnalyzeOptions());
    }

    /**
     * Analyze with options (single language).
     *
     * @param text        comment text
     * @param attributes  attributes to request (e.g., TOXICITY, INSULT)
     * @param options     language (default "en"), doNotStore (default true), etc.
     * @return PerspectiveScore (immutable)
     */
    public PerspectiveScore analyze(String text, List<Attribute> attributes, AnalyzeOptions options) throws IOException {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("text is required");
        if (attributes == null || attributes.isEmpty()) {
            throw new IllegalArgumentException("at least one attribute is required");
        }
        if (options == null) options = new AnalyzeOptions();

        // Build payload
        JsonObject payload = new JsonObject();

        JsonObject comment = new JsonObject();
        comment.addProperty("text", text);
        payload.add("comment", comment);

        JsonArray langs = new JsonArray();
        langs.add(options.language == null || options.language.isEmpty() ? "en" : options.language);
        payload.add("languages", langs);

        JsonObject reqAttrs = new JsonObject();
        for (Attribute attr : attributes) {
            reqAttrs.add(attr.name(), new JsonObject());
        }
        payload.add("requestedAttributes", reqAttrs);

        if (options.doNotStore != null) payload.addProperty("doNotStore", options.doNotStore);
        if (options.clientToken != null) payload.addProperty("clientToken", options.clientToken);
        if (options.communityId != null) payload.addProperty("communityId", options.communityId);
        if (options.spanAnnotations != null) payload.addProperty("spanAnnotations", options.spanAnnotations);
        if (options.sessionId != null) payload.addProperty("sessionId", options.sessionId);
        if (options.context != null && !options.context.isEmpty()) {
            JsonObject ctx = new JsonObject();
            JsonArray ctxEntries = new JsonArray();
            for (String c : options.context) {
                JsonObject entry = new JsonObject();
                entry.addProperty("text", c);
                ctxEntries.add(entry);
            }
            ctx.add("entries", ctxEntries);
            payload.add("context", ctx);
        }

        // HTTP request
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(endpoint))
                .newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        RequestBody body = RequestBody.create(
                gson.toJson(payload),
                MediaType.parse("application/json; charset=utf-8")
        );

        Request req = new Request.Builder().url(url).post(body).build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                String err = (res.body() != null) ? res.body().string() : ("HTTP " + res.code());
                throw new IOException("Perspective API error: HTTP " + res.code() + " - " + err);
            }
            String json = res.body() != null ? res.body().string() : "{}";
            return parseToScore(text, options.language, json, attributes);
        }
    }

    // ---------- parsing

    private PerspectiveScore parseToScore(String text, String languageOrNull, String json,
                                          List<Attribute> requestedAttrs) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonObject attrScores = root.has("attributeScores") && root.get("attributeScores").isJsonObject()
                ? root.getAsJsonObject("attributeScores") : new JsonObject();

        Map<String, Double> scores = new LinkedHashMap<>();
        List<PerspectiveScore.SpanAnnotation> spans = new ArrayList<>();

        for (Attribute attr : requestedAttrs) {
            String key = attr.name();
            Double val = Double.NaN;

            if (attrScores.has(key)) {
                JsonObject a = attrScores.getAsJsonObject(key);

                // summaryScore
                if (a.has("summaryScore")) {
                    JsonObject s = a.getAsJsonObject("summaryScore");
                    if (s.has("value")) {
                        try { val = s.get("value").getAsDouble(); } catch (Exception ignored) {}
                    }
                }

                // spanScores (if requested)
                if (a.has("spanScores") && a.get("spanScores").isJsonArray()) {
                    for (JsonElement e : a.getAsJsonArray("spanScores")) {
                        if (!e.isJsonObject()) continue;
                        JsonObject ss = e.getAsJsonObject();
                        Integer begin = ss.has("begin") ? ss.get("begin").getAsInt() : null;
                        Integer end   = ss.has("end")   ? ss.get("end").getAsInt()   : null;
                        Double sval   = (ss.has("score") && ss.get("score").isJsonObject())
                                ? ss.getAsJsonObject("score").get("value").getAsDouble() : null;
                        if (begin != null && end != null && sval != null) {
                            spans.add(new PerspectiveScore.SpanAnnotation(begin, end, key, sval));
                        }
                    }
                }
            }
            scores.put(key, val);
        }

        return PerspectiveScore.builder(text)
                .languages(Collections.singletonList(languageOrNull == null || languageOrNull.isEmpty() ? "en" : languageOrNull))
                .putAllScores(scores)
                .addAllSpans(spans)
                .build();
    }

    // ---------- options

    public static class AnalyzeOptions {
        /** Single language to evaluate (default "en"). */
        public String language;

        /** Default true per your request. */
        public Boolean doNotStore = true;

        public String clientToken;
        public String communityId;
        public Boolean spanAnnotations;
        public String sessionId;
        public List<String> context;

        public AnalyzeOptions language(String v) { this.language = v; return this; }
        public AnalyzeOptions doNotStore(boolean v) { this.doNotStore = v; return this; }
        public AnalyzeOptions clientToken(String v) { this.clientToken = v; return this; }
        public AnalyzeOptions communityId(String v) { this.communityId = v; return this; }
        public AnalyzeOptions spanAnnotations(boolean v) { this.spanAnnotations = v; return this; }
        public AnalyzeOptions sessionId(String v) { this.sessionId = v; return this; }
        public AnalyzeOptions context(List<String> v) { this.context = v; return this; }
    }
}
