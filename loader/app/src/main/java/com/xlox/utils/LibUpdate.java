package com.xlox.utils;

import java.util.List;

/**
 * "Should this launch download a library, and from where?"
 *
 * Kept apart from the Android code because getting it wrong is invisible: the
 * app simply keeps the old library and the only workaround is clearing the app
 * data. See loader/app/src/test/ElfTest.java for the cases.
 */
public final class LibUpdate {

    /** One place a lib can come from: the panel, or the repo mirror. */
    public static final class Source {
        public final String name;
        public final String version;
        public final String zipUrl;

        public Source(String name, String version, String zipUrl) {
            this.name = name;
            this.version = version;
            this.zipUrl = zipUrl;
        }

        @Override
        public String toString() {
            return name + " " + version;
        }
    }

    private LibUpdate() {}

    /**
     * The first source whose version differs from what is installed, or null
     * when the library on disk is already current.
     *
     * A version string identifies the bytes, not the channel they came from:
     * comparing the channel as well made the loader re-download the file it
     * already had every time the source changed, and made a newer mirror
     * version invisible whenever the panel still published the old one.
     */
    public static Source choose(List<Source> sources, String localVersion, boolean libOnDisk) {
        if (!libOnDisk) {
            // nothing on disk: any source will do, the first one first
            return sources.isEmpty() ? null : sources.get(0);
        }
        for (Source source : sources) {
            if (localVersion == null || !source.version.equals(localVersion)) {
                return source;
            }
        }
        return null;
    }

    /** Same rule, but for the actual download: never fetch what is installed. */
    public static boolean worthDownloading(Source source, String localVersion, boolean libOnDisk) {
        if (!libOnDisk) return true;
        return source != null && (localVersion == null || !source.version.equals(localVersion));
    }

    /** The strings Downtwo's check-only run can end with that mean "newer lib exists". */
    public static boolean isUpdateResult(String result) {
        return "Update Available".equals(result) || "Already Notified".equals(result);
    }
}
