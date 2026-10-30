package com.xlox.utils;

/**
 * Placeholder for lib update logic. The actual download/extract is performed
 * by Downtwo against the panel's /api/version and /api/download endpoints.
 */
public final class LibUpdate {

    private LibUpdate() {}

    public static final String PREF_VERSION = "lib_version";

    public static boolean needsUpdate(String localVersion, String serverVersion) {
        if (serverVersion == null || serverVersion.isEmpty()) return false;
        return !serverVersion.equals(localVersion);
    }
}
