package com.xlox.utils;

import java.io.File;

/**
 * Placeholder for ZIP handling used by the lib update flow.
 * The actual extraction is performed by Downtwo using zip4j.
 */
public final class LibZip {

    private LibZip() {}

    public static boolean isZip(File file) {
        return file != null && file.exists()
                && file.getName().toLowerCase().endsWith(".zip");
    }
}
