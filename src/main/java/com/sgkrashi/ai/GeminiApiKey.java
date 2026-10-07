package com.sgkrashi.ai;

/**
 * How the Gemini API key is sent. It goes in a request header, never in the URL query
 * ({@code ?key=}): URLs end up in exception messages, proxy and access logs, while a
 * header value does not.
 */
public final class GeminiApiKey {

    public static final String HEADER = "x-goog-api-key";

    private GeminiApiKey() {
    }
}
