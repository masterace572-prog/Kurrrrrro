package com.xlox.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
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
                fail(callback, "The sandbox storage is not available — is the game installed in the sandbox?"
                        + detail(t));
                return;
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
        Log.w(TAG, message);
        if (callback != null) main.post(() -> callback.onError(message));
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
