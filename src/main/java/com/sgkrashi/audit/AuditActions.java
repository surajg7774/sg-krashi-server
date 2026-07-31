package com.sgkrashi.audit;

/**
 * Shared vocabulary of audit action names, one constant per auditable
 * mutation across every module wired in Module 17 (Section 3.2). Centralized
 * here rather than scattered as local constants in each service (unlike
 * {@code payableType}/{@code ownerType}) because these values are a genuine
 * shared reference table read by the audit log viewer, not domain-internal
 * detail — a single source of truth avoids typo drift between the writer and
 * anything that later needs to filter/display by action name.
 */
public final class AuditActions {

    private AuditActions() {
    }

    public static final String USER_ACTIVATED = "USER_ACTIVATED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String USER_ROLE_ASSIGNED = "USER_ROLE_ASSIGNED";
    public static final String USER_ROLE_REMOVED = "USER_ROLE_REMOVED";

    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";
    public static final String PRODUCT_UPDATED = "PRODUCT_UPDATED";
    public static final String PRODUCT_DEACTIVATED = "PRODUCT_DEACTIVATED";

    public static final String CROP_LISTING_CREATED = "CROP_LISTING_CREATED";
    public static final String CROP_LISTING_UPDATED = "CROP_LISTING_UPDATED";
    public static final String CROP_LISTING_DEACTIVATED = "CROP_LISTING_DEACTIVATED";

    public static final String EQUIPMENT_CREATED = "EQUIPMENT_CREATED";
    public static final String EQUIPMENT_UPDATED = "EQUIPMENT_UPDATED";
    public static final String EQUIPMENT_DEACTIVATED = "EQUIPMENT_DEACTIVATED";

    public static final String STAY_LISTING_CREATED = "STAY_LISTING_CREATED";
    public static final String STAY_LISTING_UPDATED = "STAY_LISTING_UPDATED";
    public static final String STAY_LISTING_DEACTIVATED = "STAY_LISTING_DEACTIVATED";

    public static final String ORDER_STATUS_UPDATED = "ORDER_STATUS_UPDATED";
    public static final String ORDER_REFUNDED = "ORDER_REFUNDED";

    public static final String BOOKING_STATUS_UPDATED = "BOOKING_STATUS_UPDATED";
    public static final String BOOKING_REFUNDED = "BOOKING_REFUNDED";

    public static final String INQUIRY_STATUS_UPDATED = "INQUIRY_STATUS_UPDATED";

    public static final String CONTENT_BLOCK_CREATED = "CONTENT_BLOCK_CREATED";
    public static final String CONTENT_BLOCK_UPDATED = "CONTENT_BLOCK_UPDATED";
    public static final String CONTENT_BLOCK_DEACTIVATED = "CONTENT_BLOCK_DEACTIVATED";
}
