package com.xlox.utils;

import java.util.List;

/**
 * Decides whether the game's data can be copied, from what the filesystem
 * actually shows — not from a permission flag.
 *
 * That distinction is the whole point. On Android 11+ a granted
 * MANAGE_EXTERNAL_STORAGE can still report false (some ROMs answer badly, and
 * users often flip the wrong switch in a vendor "permissions" screen), so an
 * app that trusts the flag alone refuses to copy while the folder is perfectly
 * readable. The flag is now only used to choose the wording of the message.
 *
 * No Android imports: see loader/app/src/test/ObbAccessTest.java.
 */
public final class ObbAccess {

    private ObbAccess() {}

    /** OBBs are readable — the copy can start. */
    public static final int READY = 0;

    /** Nothing readable and no "all files" grant: ask for the grant. */
    public static final int NEEDS_PERMISSION = 1;

    /** The folder is readable but holds no OBB: the game has no data yet. */
    public static final int EMPTY_FOLDER = 2;

    /** Access is fine, the folder simply is not there. */
    public static final int NO_FOLDER = 3;

    /** One candidate path and what it looked like when probed. */
    public static final class Look {
        public final String path;
        public final boolean exists;
        public final boolean listable;
        public final int obbCount;

        public Look(String path, boolean exists, boolean listable, int obbCount) {
            this.path = path;
            this.exists = exists;
            this.listable = listable;
            this.obbCount = obbCount < 0 ? 0 : obbCount;
        }
    }

    public static int decide(boolean allFilesGranted, List<Look> looks) {
        if (looks != null) {
            boolean readable = false;
            for (Look look : looks) {
                if (look.obbCount > 0) return READY;
                if (look.exists && look.listable) readable = true;
            }
            if (readable) return EMPTY_FOLDER;
        }
        return allFilesGranted ? NO_FOLDER : NEEDS_PERMISSION;
    }

    /**
     * One short line for the card — the path that matters, and why it did not
     * work. Deliberately terse: it is a hint, not a log dump.
     */
    public static String describe(int state, List<Look> looks) {
        Look first = looks == null || looks.isEmpty() ? null : looks.get(0);
        String path = first == null ? "" : shorten(first.path);
        switch (state) {
            case READY:
                return path;
            case NEEDS_PERMISSION:
                return "No access to " + path;
            case EMPTY_FOLDER:
                return path + " is empty";
            case NO_FOLDER:
            default:
                return path + " does not exist";
        }
    }

    /**
     * The primary volume's three aliases all mean the same folder, so they are
     * printed without their prefix. A second volume keeps its own path, because
     * that is the thing the user has to know about.
     */
    static String shorten(String path) {
        if (path == null) return "";
        for (String prefix : new String[]{"/storage/emulated/0/", "/sdcard/", "/storage/self/primary/"}) {
            if (path.startsWith(prefix)) return path.substring(prefix.length());
        }
        return path;
    }

    /** The first path that held usable OBBs, or null. */
    public static Look firstWithObbs(List<Look> looks) {
        if (looks == null) return null;
        for (Look look : looks) if (look.obbCount > 0) return look;
        return null;
    }
}
