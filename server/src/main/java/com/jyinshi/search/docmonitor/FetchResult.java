package com.jyinshi.search.docmonitor;

import lombok.Getter;

@Getter
public class FetchResult {
    private final boolean unchanged;
    private final boolean error;
    private final String fingerprint;
    private final String message;
    private final ParsedContent parsed;

    private FetchResult(boolean unchanged, boolean error, String fingerprint, String message, ParsedContent parsed) {
        this.unchanged = unchanged;
        this.error = error;
        this.fingerprint = fingerprint;
        this.message = message;
        this.parsed = parsed;
    }

    public static FetchResult ok(String fingerprint, ParsedContent parsed) {
        return new FetchResult(false, false, fingerprint, null, parsed);
    }

    public static FetchResult unchanged(String fingerprint) {
        return new FetchResult(true, false, fingerprint, "unchanged", null);
    }

    public static FetchResult error(String message) {
        return new FetchResult(false, true, null, message, null);
    }
}
