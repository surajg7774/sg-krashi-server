package com.sgkrashi.mandi.dto.response;

import java.time.Instant;

/** "Last updated" info for the public page's disclaimer banner — this is cached, not real-time, data. */
public record MandiSyncMetaResponse(Instant lastSyncedAt, long totalRows, boolean apiConfigured) {
}
