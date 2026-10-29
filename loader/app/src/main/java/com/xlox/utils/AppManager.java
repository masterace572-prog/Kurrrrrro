package com.xlox.utils;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.xlox.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import net_62v.external.MetaPackageManager;
import net_62v.external.MetaStorageManager;

@Obfuscate
public class AppManager extends AppCompatActivity {

    private final Context ctx;

    public static final int INSTALL_APP = 1;
    public static final int COPY_OBB = 3;

    public interface CopyCallback {
        void onCopyCompleted(boolean success);
        default void onProgressUpdate(int progress) {}
        /** Why the copy failed — shown to the user instead of a silent "failed". */
        default void onError(String message) {}
    }

    public AppManager(Context ctx) {
        this.ctx = ctx;
    }

    @SuppressLint("StaticFieldLeak")
    public void appManager(String pkg, int method, CopyCallback callback, boolean showDialog) {

        new AsyncTask<Void, Integer, Boolean>() {

            String errorMessage = "";

            Dialog progressDialog;
            ProgressBar progressBar;
            TextView progressText;

            long totalBytes = 0;
            long copiedBytes = 0;

            @Override
            protected void onPreExecute() {

                if (method == COPY_OBB && showDialog) {

                    progressDialog = new Dialog(ctx);
                    progressDialog.setContentView(R.layout.progress_dialog);
                    progressDialog.setCancelable(false);

                    progressBar = progressDialog.findViewById(R.id.progressBar);
                    progressText = progressDialog.findViewById(R.id.progressText);

                    if (progressBar != null) {
                        progressBar.setMax(100);
                        progressBar.setProgress(0);
                    }

                    if (progressText != null) {
                        progressText.setText("0%");
                    }

                    progressDialog.show();
                }
            }

            @Override
            protected Boolean doInBackground(Void... voids) {

                if (method == COPY_OBB) {
                    return copyObbFolder();
                }

                if (method == INSTALL_APP) {
                    return installApp(pkg);
                }

                return false;
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                if (callback != null) {
                    callback.onProgressUpdate(values[0]);
                }

                if (progressBar != null) {
                    progressBar.setProgress(values[0]);
                }

                if (progressText != null) {
                    progressText.setText(values[0] + "%");
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {

                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }

                if (callback != null) {
                    if (success) {
                        callback.onCopyCompleted(true);
                    } else {
                        callback.onError(errorMessage == null || errorMessage.trim().isEmpty()
                                ? "Unknown error while copying the game data"
                                : errorMessage);
                    }
                } else {
                    showResultDialog(success, errorMessage);
                }
            }

            private boolean installApp(String pkg) {

                try {

                    PackageInfo packageInfo = ctx.getPackageManager().getPackageInfo(pkg, 0);

                    String apkPath = packageInfo.applicationInfo.sourceDir;

                    net_62v.external.MetaApplicationInstaller.installAppByPath(apkPath);

                    boolean result = MetaPackageManager.isInnerAppInstalled(pkg);

                    if (!result) {
                        errorMessage = "Sandbox install failed!";
                        return false;
                    }

                    return true;

                } catch (PackageManager.NameNotFoundException e) {
                    errorMessage = "Package not found!";
                    return false;

                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    return false;
                }
            }

            private boolean copyObbFolder() {

                try {

                    File sourceDir = new File("/storage/emulated/0/Android/obb/" + pkg);

                    if (!sourceDir.exists()) {
                        errorMessage = "OBB folder not found!";
                        return false;
                    }

                    File destDir = getMundoObbDir(pkg);

                    if (sourceDir.getAbsolutePath().equals(destDir.getAbsolutePath())) {
                        return true;
                    }

                    if (!destDir.exists() && !destDir.mkdirs() && !destDir.isDirectory()) {
                        errorMessage = "Cannot create the sandbox OBB folder";
                        return false;
                    }

                    // BGMI ships a main OBB and, on larger builds, a patch OBB.
                    // Both have to be inside the sandbox or the game boots without
                    // its data.
                    List<File> pending = new ArrayList<>();
                    long needed = 0L;
                    for (File obb : findObbFiles(sourceDir, pkg)) {
                        if (obb.length() <= 0) continue;
                        File already = new File(destDir, obb.getName());
                        if (already.isFile() && already.length() == obb.length()) continue;
                        pending.add(obb);
                        needed += obb.length();
                    }

                    if (pending.isEmpty()) {
                        if (findMainObb(sourceDir, pkg) == null) {
                            errorMessage = "BGMI's main OBB is missing — open the game once so it downloads its data";
                            return false;
                        }
                        // both sides already agree
                        return true;
                    }

                    long free = freeBytes(destDir);
                    if (free > 0 && free < needed + (8L * 1024L * 1024L)) {
                        errorMessage = "Not enough free storage for the game data";
                        return false;
                    }

                    totalBytes = needed;
                    copiedBytes = 0;

                    for (File obb : pending) {
                        File target = new File(destDir, obb.getName());
                        if (!copyFile(obb, target)) return false;
                        long written = target.length();
                        if (written != obb.length()) {
                            errorMessage = "The copy stopped early (" + human(written)
                                    + " of " + human(obb.length()) + ")";
                            //noinspection ResultOfMethodCallIgnored
                            target.delete();
                            return false;
                        }
                    }
                    return true;

                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    return false;
                }
            }

            /**
             * Every main/patch OBB BGMI keeps on shared storage, smallest first so
             * the progress bar climbs steadily.
             */
            private List<File> findObbFiles(File sourceDir, String packageName) {
                List<File> out = new ArrayList<>();
                File[] files = sourceDir.listFiles();
                if (files == null) return out;

                Pattern p = Pattern.compile(
                        "^(main|patch)\\.[0-9]+\\." + Pattern.quote(packageName) + "\\.obb$");
                for (File file : files) {
                    if (file.isFile() && p.matcher(file.getName()).matches()) out.add(file);
                }
                Collections.sort(out, (a, b) -> Long.compare(a.length(), b.length()));
                return out;
            }

            private File findMainObb(File sourceDir, String packageName) {
                File[] files = sourceDir.listFiles();
                if (files == null) return null;

                String pattern = "^main\\.[0-9]+\\." + Pattern.quote(packageName) + "\\.obb$";
                File selected = null;
                long selectedVersion = -1L;
                for (File file : files) {
                    if (!file.isFile() || !file.getName().matches(pattern)) continue;
                    String[] parts = file.getName().split("\\.");
                    long version = -1L;
                    if (parts.length > 1) {
                        try {
                            version = Long.parseLong(parts[1]);
                        } catch (NumberFormatException ignored) {}
                    }
                    if (selected == null || version > selectedVersion) {
                        selected = file;
                        selectedVersion = version;
                    }
                }
                return selected;
            }

            private boolean copyFile(File source, File target) {
                FileOutputStream output = null;
                try (FileInputStream input = new FileInputStream(source)) {
                    File parent = target.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();

                    output = new FileOutputStream(target);
                    byte[] buffer = new byte[1024 * 1024];
                    int length;

                    while ((length = input.read(buffer)) > 0) {
                        output.write(buffer, 0, length);
                        copiedBytes += length;
                        int progress = totalBytes > 0 ? (int) ((copiedBytes * 100) / totalBytes) : 100;
                        publishProgress(progress);
                    }

                    output.flush();
                    output.getFD().sync();
                    return true;
                } catch (Exception e) {
                    errorMessage = e.getMessage() == null || e.getMessage().trim().isEmpty()
                            ? "Cannot write the game data (storage full or permission denied)"
                            : e.getMessage();
                    // never leave a half-copied OBB behind
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
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
                return (bytes / (1024 * 1024)) + " MB";
            }

            private long getFolderSize(File dir) {

                long size = 0;
                File[] files = dir.listFiles();

                if (files != null) {
                    for (File file : files) {
                        if (file.isDirectory()) {
                            size += getFolderSize(file);
                        } else {
                            size += file.length();
                        }
                    }
                }

                return size;
            }

            private void showResultDialog(boolean success, String errorMessage) {

                new MaterialAlertDialogBuilder(ctx)
                        .setTitle(success ? "Success" : "Error")
                        .setMessage(success ? (method == INSTALL_APP ? "App installed successfully." : "OBB ready successfully.") : errorMessage)
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .show();
            }

        }.execute();
    }

    private File getMundoObbDir(String pkg) {
        try {
            return new File(
                    MetaStorageManager.obtainAppExternalStorageDir(pkg) + "/Android/obb",
                    pkg
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * True when every OBB the game keeps on shared storage already sits in the
     * sandbox with the same size — i.e. the copy genuinely finished. Without
     * storage access the source cannot be listed, so it falls back to "a main
     * OBB is present".
     */
    public boolean checkObbContainer(String packageName) {
        File destDir;
        try {
            destDir = getMundoObbDir(packageName);
        } catch (Throwable t) {
            return false;
        }
        if (destDir == null || !destDir.exists() || !destDir.isDirectory()) return false;
        File[] local = destDir.listFiles();
        if (local == null) return false;

        Pattern both = Pattern.compile(
                "^(main|patch)\\.[0-9]+\\." + Pattern.quote(packageName) + "\\.obb$");
        File[] source = new File("/storage/emulated/0/Android/obb/" + packageName).listFiles();

        if (source != null) {
            boolean seen = false;
            for (File file : source) {
                if (!file.isFile() || !both.matcher(file.getName()).matches()) continue;
                seen = true;
                File copy = new File(destDir, file.getName());
                if (!copy.isFile() || copy.length() != file.length()) return false;
            }
            if (seen) return true;
        }

        Pattern main = Pattern.compile(
                "^main\\.[0-9]+\\." + Pattern.quote(packageName) + "\\.obb$");
        for (File file : local) {
            if (file.isFile() && main.matcher(file.getName()).matches()) return true;
        }
        return false;
    }

    public void copyObbFolderAsync(String pkg, final CopyCallback callback) {
        copyObbFolderAsync(pkg, callback, true);
    }

    public void copyObbFolderAsync(String pkg, final CopyCallback callback, boolean showDialog) {
        if (checkObbContainer(pkg)) {
            if (callback != null) {
                callback.onCopyCompleted(true);
            }
            return;
        }

        appManager(pkg, COPY_OBB, callback, showDialog);
    }
}
