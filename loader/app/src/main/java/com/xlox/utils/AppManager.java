package com.xlox.utils;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net_62v.external.MetaPackageManager;
import net_62v.external.MetaStorageManager;

/**
 * Copies the game's OBB files into the sandbox.
 *
 * This used to be an AsyncTask living inside an AppCompatActivity subclass that
 * was created with {@code new AppManager(ctx)} — an Activity that never went
 * through onCreate. It also swallowed the reason a copy failed. Both are gone:
 * this is a plain object, the work runs on its own thread and every failure
 * carries a message that can be shown to the user.
 *
 * The game keeps its data at
 *   /storage/emulated/0/Android/obb/<pkg>/{main,patch}.<version>.<pkg>.obb
 * and the sandbox reads it from
 *   <sandbox external dir for <pkg>>/Android/obb/<pkg>/
 */
@Obfuscate
public class AppManager {

    private static final String TAG = "AppManager";

    public static final int INSTALL_APP = 1;
    public static final int COPY_OBB = 3;

    /** Where the game keeps its shared-storage data, newest-looking first. */
    private static final String[] SOURCE_ROOTS = {
            "/storage/emulated/0",
            "/sdcard",
            "/storage/self/primary",
    };

    /** What the filesystem says about the game's data, right now. */
    public static final class Probe {
        public final int state;          // one of ObbAccess.READY / NEEDS_PERMISSION / …
        public final File sourceDir;     // readable folder holding OBBs, or null
        public final boolean allFilesGranted;
        public final String detail;      // one short line for the card

        Probe(int state, File sourceDir, boolean allFilesGranted, String detail) {
            this.state = state;
            this.sourceDir = sourceDir;
            this.allFilesGranted = allFilesGranted;
            this.detail = detail;
        }
    }

    public interface CopyCallback {
        void onCopyCompleted(boolean success);
        default void onProgressUpdate(int progress) {}
        /** Why the copy failed — shown to the user instead of a silent "failed". */
        default void onError(String message) {}
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** The last thing that went wrong, verbatim — quoted by the details sheet. */
    private String lastError = "";

    public AppManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /* ------------------------------------------------------------ entry points */

    public void copyObbFolderAsync(String pkg, final CopyCallback callback, boolean unusedDialogFlag) {
        new Thread(() -> copyObbFolder(pkg, callback), "obb-copy").start();
    }

    /**
     * Looks at the real files anywhere the game could keep its data and reports
     * what is there. The "all files" flag is recorded but never trusted on its
     * own: a folder that lists OBBs is proof enough to copy, whichever way the
     * flag happens to answer on this ROM.
     */
    public Probe probeAccess(String pkg) {
        boolean granted = allFilesGranted();
        List<ObbAccess.Look> looks = new ArrayList<>();
        List<File> seen = new ArrayList<>();

        for (File dir : sourceCandidates(pkg)) {
            boolean exists = dir.isDirectory();
            String[] names = exists ? dir.list() : null;
            boolean listable = names != null;
            int count = listable ? ObbSelection.list(dir, pkg).size() : 0;
            looks.add(new ObbAccess.Look(dir.getAbsolutePath(), exists, listable, count));
            seen.add(dir);
        }

        int state = ObbAccess.decide(granted, looks);
        File best = null;
        ObbAccess.Look hit = ObbAccess.firstWithObbs(looks);
        if (hit != null) {
            for (File dir : seen) {
                if (dir.getAbsolutePath().equals(hit.path)) {
                    best = dir;
                    break;
                }
            }
        }
        String detail = ObbAccess.describe(state, looks);
        Log.i(TAG, "probe " + pkg + " -> state " + state + ", allFiles=" + granted + ", " + detail);
        return new Probe(state, best, granted, detail);
    }

    /** True when Android thinks this app may read the whole shared storage. */
    public boolean allFilesGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return Environment.isExternalStorageManager();
            } catch (Throwable t) {
                Log.w(TAG, "cannot read the all-files state: " + t.getMessage());
                return false;
            }
        }
        return hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    /**
     * Every place an OBB folder could live: the primary volume under each of
     * its aliases, plus whichever secondary volumes (SD cards) the platform
     * reports. Derived from the app's own external dirs so no path is invented.
     */
    private List<File> sourceCandidates(String pkg) {
        List<String> roots = new ArrayList<>();
        for (String root : SOURCE_ROOTS) addRoot(roots, root);

        try {
            File primary = Environment.getExternalStorageDirectory();
            if (primary != null) addRoot(roots, primary.getAbsolutePath());
        } catch (Throwable ignored) {
        }

        try {
            File[] dirs = ctx.getExternalFilesDirs(null);
            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir == null) continue;
                    String path = dir.getAbsolutePath();       // /storage/XXXX/Android/data/<pkg>/files
                    int cut = path.indexOf("/Android/");
                    if (cut > 0) addRoot(roots, path.substring(0, cut));
                }
            }
        } catch (Throwable ignored) {
        }

        List<File> out = new ArrayList<>();
        for (String root : roots) out.add(new File(root, "Android/obb/" + pkg));
        return out;
    }

    private void addRoot(List<String> roots, String root) {
        if (root == null || root.trim().isEmpty()) return;
        String clean = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        if (!roots.contains(clean)) roots.add(clean);
    }

    /**
     * True when every OBB the game has on shared storage is already the
     * sandbox at the same size. Falls back to "a main OBB exists" when the
     * source cannot be listed (no storage access yet).
     */
    public boolean checkObbContainer(String packageName) {
        File destDir;
        try {
            destDir = getSandboxObbDir(packageName);
        } catch (Throwable t) {
            Log.w(TAG, "sandbox dir unavailable: " + t.getMessage());
            return false;
        }
        if (destDir == null || !destDir.isDirectory()) return false;

        File sourceDir = probeAccess(packageName).sourceDir;
        if (sourceDir != null) {
            List<File> obbs = ObbSelection.list(sourceDir, packageName);
            if (!obbs.isEmpty()) return ObbSelection.matches(obbs, destDir, packageName);
            return false;                    // source readable, but nothing to copy
        }

        // cannot see the source: trust a complete-looking destination
        return ObbSelection.hasMainObb(destDir, packageName);
    }

    /* ------------------------------------------------- copying a picked OBB file */

    /**
     * Copies OBB files the user picked through the system file picker straight
     * into the sandbox.
     *
     * This is the route that works on Android 11+ — and it is the only one.
     * Since Android 11 the shared-storage folders /Android/obb and /Android/data
     * of *other* apps are closed even to an app holding All files access, and
     * since Android 13 the file picker cannot be pointed at them either. What the
     * picker CAN hand over is the .obb file itself, wherever the user keeps it
     * (Downloads, a USB stick, an XAPK they extracted), and that is enough: the
     * read permission travels with the Uri.
     */
    public void copyFromUris(List<Uri> uris, String pkg, CopyCallback callback) {
        new Thread(() -> {
            try {
                File destDir = getSandboxObbDir(pkg);
                if (destDir == null) {
                    fail(callback, "The sandbox storage is not available.");
                    return;
                }
                if (!destDir.isDirectory() && !destDir.mkdirs() && !destDir.isDirectory()) {
                    fail(callback, "Cannot create the game data folder inside the sandbox.");
                    return;
                }

                List<Uri> accepted = new ArrayList<>();
                List<String> rejected = new ArrayList<>();
                long total = 0L;
                for (Uri uri : uris) {
                    String name = displayName(uri);
                    if (name == null || !ObbSelection.obbPattern(pkg).matcher(name).matches()) {
                        rejected.add(name == null ? "an unnamed file" : name);
                        continue;
                    }
                    accepted.add(uri);
                    total += contentLength(uri);
                }

                if (accepted.isEmpty()) {
                    fail(callback, "That is not the game's data file.\n\n"
                            + "Pick main.<version>." + pkg + ".obb or patch.<version>." + pkg + ".obb."
                            + (rejected.isEmpty() ? "" : "\n\nGot: " + rejected.get(0)));
                    return;
                }

                long done = 0L;
                for (Uri uri : accepted) {
                    String name = displayName(uri);
                    File target = new File(destDir, name);
                    long copied = copyUri(uri, target, done, total, callback);
                    if (copied < 0) {
                        //noinspection ResultOfMethodCallIgnored
                        target.delete();
                        return;                       // copyUri already reported
                    }
                    done += copied;
                }

                if (!rejected.isEmpty()) {
                    Log.w(TAG, "skipped not-an-obb: " + rejected);
                }
                ok(callback);

            } catch (Throwable t) {
                Log.e(TAG, "copy from picker failed", t);
                fail(callback, "Copying the picked file failed" + detail(t));
            }
        }, "obb-pick-copy").start();
    }

    /** Copies one picked Uri, reporting progress against the whole selection. */
    private long copyUri(Uri uri, File target, long doneBefore, long total, CopyCallback callback) {
        InputStream input = null;
        FileOutputStream output = null;
        try {
            ContentResolver resolver = ctx.getContentResolver();
            input = resolver.openInputStream(uri);
            if (input == null) {
                fail(callback, "The picked file cannot be read.");
                return -1L;
            }
            output = new FileOutputStream(target);

            byte[] buffer = new byte[1024 * 512];
            int length;
            long written = 0L;
            long lastPush = 0L;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
                written += length;
                long now = System.currentTimeMillis();
                if (callback != null && total > 0 && now - lastPush > 120L) {
                    lastPush = now;
                    final int percent = (int) Math.min(100L, ((doneBefore + written) * 100L) / total);
                    main.post(() -> callback.onProgressUpdate(percent));
                }
            }
            output.flush();
            output.getFD().sync();
            return written;

        } catch (Throwable t) {
            Log.e(TAG, "writing " + target + " from " + uri + " failed", t);
            fail(callback, "Cannot write the game data" + detail(t)
                    + "\n\nFree some space, then try again.");
            return -1L;
        } finally {
            closeQuietly(input);
            closeQuietly(output);
        }
    }

    /** The name the file picker shows, which is the name we must write. */
    private String displayName(Uri uri) {
        try (android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Throwable t) {
            Log.w(TAG, "cannot read the display name: " + t.getMessage());
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? null : fallback.substring(fallback.lastIndexOf('/') + 1);
    }

    private long contentLength(Uri uri) {
        try (android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Throwable ignored) {
        }
        return 0L;
    }

    private void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception ignored) {
        }
    }

    /* ------------------------------------------------------------------ the copy */

    private void copyObbFolder(String pkg, CopyCallback callback) {
        try {
            Probe probe = probeAccess(pkg);
            File sourceDir = probe.sourceDir;
            if (sourceDir == null) {
                switch (probe.state) {
                    case ObbAccess.NEEDS_PERMISSION:
                        fail(callback, "The game's folder cannot be read yet.\n\n"
                                + "Allow All files access, then retry — the copy starts by itself.");
                        break;
                    case ObbAccess.EMPTY_FOLDER:
                        fail(callback, "The game's data folder is empty.\n\n"
                                + "Open the game once so it downloads its data, then retry.");
                        break;
                    default:
                        fail(callback, "The game's data folder was not found.\n\n"
                                + "Open the game once so it downloads its data, then retry.");
                        break;
                }
                return;
            }

            File destDir;
            try {
                destDir = getSandboxObbDir(pkg);
            } catch (Throwable t) {
                // Most sandbox SDKs only hand out a guest's storage once the
                // guest exists. If it has not been installed yet, put it in the
                // sandbox and ask again — that is the difference between "the
                // copy is broken" and "there was nowhere to copy to".
                String installed = installQuietly(pkg);
                try {
                    destDir = getSandboxObbDir(pkg);
                    Log.i(TAG, "sandbox folder came back after install: " + installed);
                } catch (Throwable again) {
                    fail(callback, "The sandbox storage is not available" + detail(again)
                            + "\n\nSandbox install: " + installed);
                    return;
                }
            }
            if (destDir == null) {
                fail(callback, "The sandbox storage is not available.");
                return;
            }
            if (sourceDir.getAbsolutePath().equals(destDir.getAbsolutePath())) {
                ok(callback);
                return;
            }
            if (!destDir.isDirectory() && !destDir.mkdirs() && !destDir.isDirectory()) {
                fail(callback, "Cannot create the game data folder inside the sandbox.");
                return;
            }

            List<File> obbs = ObbSelection.list(sourceDir, pkg);
            List<File> pending = ObbSelection.pending(obbs, destDir, pkg);
            long needed = 0L;
            for (File obb : pending) needed += obb.length();

            if (obbs.isEmpty()) {
                fail(callback, "The game's OBB file is missing.\n\n"
                        + "Open the game once so it downloads its data, then try again.");
                return;
            }
            if (pending.isEmpty()) {
                ok(callback);
                return;
            }

            long free = freeBytes(destDir);
            if (free > 0 && free < needed + (8L * 1024L * 1024L)) {
                fail(callback, "Not enough free storage — "
                        + human(needed) + " of game data still has to be copied.");
                return;
            }

            long done = 0L;
            for (File obb : pending) {
                File target = new File(destDir, obb.getName());
                if (!copyFile(obb, target, done, needed, callback)) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    return;                  // copyFile already reported
                }
                if (target.length() != obb.length()) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                    fail(callback, "The copy stopped early (" + human(target.length())
                            + " of " + human(obb.length()) + "). Free some space and retry.");
                    return;
                }
                done += obb.length();
            }

            ok(callback);

        } catch (Throwable t) {
            Log.e(TAG, "OBB copy failed", t);
            fail(callback, "Copying the game data failed" + detail(t));
        }
    }

    private boolean copyFile(File source, File target, long doneBefore, long total,
                             CopyCallback callback) {
        FileOutputStream output = null;
        try (FileInputStream input = new FileInputStream(source)) {
            File parent = target.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                fail(callback, "Cannot create the sandbox folder for the game data.");
                return false;
            }

            output = new FileOutputStream(target);
            byte[] buffer = new byte[1024 * 512];
            int length;
            long written = 0L;
            long lastPush = 0L;

            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
                written += length;
                long now = System.currentTimeMillis();
                if (callback != null && total > 0 && now - lastPush > 120L) {
                    lastPush = now;
                    final int percent = (int) Math.min(100L, ((doneBefore + written) * 100L) / total);
                    main.post(() -> callback.onProgressUpdate(percent));
                }
            }

            output.flush();
            output.getFD().sync();
            return true;

        } catch (Throwable t) {
            Log.e(TAG, "writing " + target + " failed", t);
            fail(callback, "Cannot write the game data" + detail(t)
                    + "\n\nFree some space or allow All files access, then retry.");
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /* ----------------------------------------------------------------- helpers */

    private File getSandboxObbDir(String pkg) {
        return new File(MetaStorageManager.obtainAppExternalStorageDir(pkg) + "/Android/obb", pkg);
    }

    private long freeBytes(File dir) {
        try {
            android.os.StatFs stat = new android.os.StatFs(dir.getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Throwable t) {
            return -1L;
        }
    }

    private String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024L * 1024L) return (bytes / (1024 * 1024)) + " MB";
        return String.format(java.util.Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String detail(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        if (m == null || m.trim().isEmpty()) return " (" + t.getClass().getSimpleName() + ")";
        return " (" + (m.length() > 90 ? m.substring(0, 90) + "…" : m) + ")";
    }

    private void ok(CopyCallback callback) {
        if (callback != null) main.post(() -> callback.onCopyCompleted(true));
    }

    private void fail(CopyCallback callback, String message) {
        lastError = message;
        Log.w(TAG, message);
        if (callback != null) main.post(() -> callback.onError(message));
    }

    /** Installs the game into the sandbox, swallowing any complaint. */
    private String installQuietly(String pkg) {
        try {
            PackageInfo info = ctx.getPackageManager().getPackageInfo(pkg, 0);
            net_62v.external.MetaApplicationInstaller.installAppByPath(info.applicationInfo.sourceDir);
            return MetaPackageManager.isInnerAppInstalled(pkg) ? "ok" : "still missing";
        } catch (Throwable t) {
            return "failed: " + brief(t);
        }
    }

    /* -------------------------------------------------------------- diagnostics */

    /**
     * Everything the OBB step looked at, in one pasteable block: the platform,
     * the grant, every folder that was probed with what the filesystem answered,
     * the sandbox destination, free space and the last error. This exists
     * because the step is impossible to debug from a screenshot otherwise.
     */
    public String diagnostics(String pkg) {
        StringBuilder out = new StringBuilder();
        out.append("XLOX LOADER\n");
        out.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\n\n");

        boolean granted = allFilesGranted();
        out.append("All files access: ").append(granted ? "granted" : "NOT granted").append('\n');
        out.append("Game installed: ").append(isInstalled(pkg) ? "yes" : "no").append('\n');
        out.append("Game in sandbox: ").append(isInSandbox(pkg) ? "yes" : "no").append("\n\n");

        Probe probe = probeAccess(pkg);
        out.append("Probe: ").append(stateName(probe.state)).append('\n');
        out.append("  ").append(probe.detail).append("\n\n");

        out.append("Folders checked:\n");
        for (File dir : sourceCandidates(pkg)) {
            String[] names = dir.isDirectory() ? dir.list() : null;
            int count = names == null ? -1 : ObbSelection.list(dir, pkg).size();
            out.append("  ").append(dir.getAbsolutePath()).append('\n');
            out.append("    exists=").append(dir.isDirectory())
                    .append(" readable=").append(names != null)
                    .append(" obb=").append(count < 0 ? "?" : String.valueOf(count))
                    .append('\n');
        }

        out.append("\nSandbox folder:\n");
        try {
            File dest = getSandboxObbDir(pkg);
            String[] names = dest.isDirectory() ? dest.list() : null;
            out.append("  ").append(dest.getAbsolutePath()).append('\n');
            out.append("    exists=").append(dest.isDirectory())
                    .append(" readable=").append(names != null)
                    .append(" files=").append(names == null ? "?" : String.valueOf(names.length))
                    .append('\n');
            long free = freeBytes(dest);
            out.append("    free=").append(free < 0 ? "?" : human(free)).append('\n');
        } catch (Throwable t) {
            out.append("  unavailable: ").append(brief(t)).append('\n');
        }

        if (!lastError.isEmpty()) {
            out.append("\nLast error: ").append(lastError).append('\n');
        }
        return out.toString();
    }

    private boolean isInstalled(String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isInSandbox(String pkg) {
        try {
            return MetaPackageManager.isInnerAppInstalled(pkg);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String stateName(int state) {
        switch (state) {
            case ObbAccess.READY:
                return "data found, copy can run";
            case ObbAccess.NEEDS_PERMISSION:
                return "nothing readable, no all-files grant";
            case ObbAccess.EMPTY_FOLDER:
                return "folder readable but empty";
            case ObbAccess.NO_FOLDER:
            default:
                return "folder missing";
        }
    }

    private String brief(Throwable t) {
        if (t == null) return "unknown error";
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m;
    }

    /* ---------------------------------------------------------------- install */

    @SuppressLint("StaticFieldLeak")
    public void installIntoBox(String pkg, CopyCallback callback) {
        new Thread(() -> {
            try {
                PackageInfo info = ctx.getPackageManager().getPackageInfo(pkg, 0);
                String apkPath = info.applicationInfo.sourceDir;
                net_62v.external.MetaApplicationInstaller.installAppByPath(apkPath);
                boolean installed = MetaPackageManager.isInnerAppInstalled(pkg);
                if (callback != null) {
                    main.post(() -> callback.onCopyCompleted(installed));
                }
            } catch (PackageManager.NameNotFoundException e) {
                fail(callback, "The game is not installed on this device.");
            } catch (Throwable t) {
                fail(callback, "Could not install the game into the sandbox" + detail(t));
            }
        }, "sandbox-install").start();
    }

    /** Kept for callers that want the package's checker directly. */
    public boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(ctx, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
