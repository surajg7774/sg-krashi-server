package com.sgkrashi.inquiry.entity;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lead-workflow status for an {@link Inquiry}. {@link #NEW} is the default on
 * creation; {@link #CONVERTED} and {@link #CLOSED} are terminal. Allowed
 * transitions are enforced explicitly via {@link #canTransitionTo} rather than
 * left to callers to get right — see {@code InquiryServiceImpl#updateStatus}.
 */
public enum InquiryStatus {
    NEW,
    CONTACTED,
    CONVERTED,
    CLOSED;

    private static final Map<InquiryStatus, Set<InquiryStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(InquiryStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(NEW, EnumSet.of(CONTACTED, CLOSED));
        ALLOWED_TRANSITIONS.put(CONTACTED, EnumSet.of(CONVERTED, CLOSED));
        ALLOWED_TRANSITIONS.put(CONVERTED, EnumSet.noneOf(InquiryStatus.class));
        ALLOWED_TRANSITIONS.put(CLOSED, EnumSet.noneOf(InquiryStatus.class));
    }

    public boolean canTransitionTo(InquiryStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }
}
