package com.example.tradelicense.config;

/**
 * Domain constants for Trade License.
 * Centralizes all string literals that represent MDMS data values,
 * notification event types, and billing codes.
 */
public final class TradeLicenseConstants {

    private TradeLicenseConstants() {}

    // ── Application Types ────────────────────────────────────────────────────
    public static final String APPLICATION_TYPE_NEW     = "NEW";
    public static final String APPLICATION_TYPE_RENEWAL = "RENEWAL";

    // ── License Types ────────────────────────────────────────────────────────
    public static final String LICENSE_TYPE_PERMANENT = "PERMANENT";
    public static final String LICENSE_TYPE_TEMPORARY = "TEMPORARY";

    // ── Structure Types ──────────────────────────────────────────────────────
    public static final String STRUCTURE_TYPE_IMMOVABLE = "IMMOVABLE";

    // ── License / Application Statuses ───────────────────────────────────────
    public static final String STATUS_CANCELLED       = "CANCELLED";
    public static final String STATUS_MANUALLY_EXPIRED = "MANUALLYEXPIRED";

    // ── Billing Slab Abbreviations ───────────────────────────────────────────
    public static final String LICENSE_TYPE_ABBR_PERMANENT = "PERM";
    public static final String LICENSE_TYPE_ABBR_TEMPORARY = "TEMP";
    public static final String STRUCTURE_TYPE_ABBR_IMMOVABLE = "IMMOV";
    public static final String STRUCTURE_TYPE_ABBR_MOVABLE   = "MOV";

    // ── Demand Statuses ──────────────────────────────────────────────────────
    public static final String DEMAND_STATUS_ACTIVE        = "ACTIVE";
    public static final String DEMAND_CANCEL_REASON_SYSTEM = "SYSTEM_CANCEL";

    // ── Notification Event Types ─────────────────────────────────────────────
    public static final String EVENT_TL_CREATE      = "TL_CREATE";
    public static final String EVENT_TL_UPDATE      = "TL_UPDATE";
    public static final String EVENT_TL_APPROVE     = "TL_APPROVE";
    public static final String EVENT_TL_PAYMENT_DUE = "TL_PAYMENT_DUE";
    public static final String EVENT_TL_ISSUED      = "TL_ISSUED";
    public static final String EVENT_TL_REJECT      = "TL_REJECT";
    public static final String EVENT_TL_REOPEN      = "TL_REOPEN";
}
