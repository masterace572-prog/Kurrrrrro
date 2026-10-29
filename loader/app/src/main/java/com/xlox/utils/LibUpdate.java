package com.xlox.utils;

import java.util.List;

/**
 * "Should this launch download a library, and from where?"
 *
 * Kept apart from the Android code because getting it wrong is invisible: the
 * app simply keeps the old library and the only workaround is clearing the app
 * data — or, when the comparison is too eager, it asks for an update on every
 * single launch even though the newest lib is already installed.
 *
 * The rule is therefore ordered, not "different": a source is only an update
 * when its version is newer than the installed one. See
 * loader/app/src/test/ElfTest.java for the executable cases.
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
     * The source this launch should install, or null when the library on disk
     * is already the newest one offered.
     *
     * Versions are compared, not just matched: the panel and the mirror often
     * disagree by a build or two (one is published a few minutes before the
     * other), and a plain "different" test made the loader re-download on every
     * launch and still say "update available" afterwards, because the version
     * it had just installed was not the one the other channel advertised.
     */
    public static Source choose(List<Source> sources, String localVersion, boolean libOnDisk) {
        if (!libOnDisk) {
            // nothing on disk: any source will do, the first one first
            return sources.isEmpty() ? null : sources.get(0);
        }
        Source best = null;
        for (Source source : sources) {
            if (!isNewer(source.version, localVersion)) continue;
            // prefer the newest of the sources that are actually newer
            if (best == null || compareVersions(source.version, best.version) > 0) {
                best = source;
            }
        }
        return best;
    }

    /** Same rule, but for the actual download: never fetch what is installed. */
    public static boolean worthDownloading(Source source, String localVersion, boolean libOnDisk) {
        if (!libOnDisk) return true;
        return source != null && isNewer(source.version, localVersion);
    }

    /** True when {@code offered} is a later version than {@code installed}. */
    public static boolean isNewer(String offered, String installed) {
        if (installed == null || installed.trim().isEmpty()) return true;
        if (offered == null || offered.trim().isEmpty()) return false;
        return compareVersions(offered, installed) > 0;
    }

    /**
     * Orders version strings: "1.10" is newer than "1.9", "1.2" equals "1.2.0",
     * and anything non-numeric ("build-42") is compared as text so a project
     * that names its builds instead of numbering them still moves forward.
     */
    public static int compareVersions(String a, String b) {
        String[] left = tokenize(a);
        String[] right = tokenize(b);
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            String l = i < left.length ? left[i] : "0";
            String r = i < right.length ? right[i] : "0";
            int cmp;
            if (isNumber(l) && isNumber(r)) {
                cmp = compareNumbers(l, r);
            } else {
                cmp = l.compareToIgnoreCase(r);
            }
            if (cmp != 0) return cmp < 0 ? -1 : 1;
        }
        return 0;
    }

    private static String[] tokenize(String version) {
        if (version == null) return new String[0];
        String cleaned = version.trim();
        // "v1.4" and "1.4" are the same version; only a leading v is a prefix
        if (cleaned.length() > 1 && (cleaned.charAt(0) == 'v' || cleaned.charAt(0) == 'V')
                && Character.isDigit(cleaned.charAt(1))) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) return new String[0];
        return cleaned.split("[._\\-+ ]+");
    }

    private static boolean isNumber(String token) {
        if (token.isEmpty()) return false;
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) return false;
        }
        return true;
    }

    /** Compares digit strings without overflowing on a long build number. */
    private static int compareNumbers(String a, String b) {
        String left = stripZeros(a);
        String right = stripZeros(b);
        if (left.length() != right.length()) return left.length() < right.length() ? -1 : 1;
        return left.compareTo(right);
    }

    private static String stripZeros(String digits) {
        int i = 0;
        while (i < digits.length() - 1 && digits.charAt(i) == '0') i++;
        return digits.substring(i);
    }

    /** The strings Downtwo's check-only run can end with that mean "newer lib exists". */
    public static boolean isUpdateResult(String result) {
        return "Update Available".equals(result) || "Already Notified".equals(result);
    }
}
