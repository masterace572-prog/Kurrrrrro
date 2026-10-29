package com.xlox.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.StatFs;
import android.util.Log;

import net.lingala.zip4j.ZipFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads the optional "lib" payload from our own panel.
 *
 *  GET <panel>/api/version   → plain text, first line = version (e.g. "1.4")
 *  GET <panel>/api/download  → a zip, extracted into filesDir/loader/
 *
 * The version from the server is compared with the one we stored after the last
 * successful extraction. If they are identical nothing is downloaded, so the
 * panel only has to change the version string to push an update.
 *
 * Every failure path returns a {@link Result} with a human readable message
 * instead of failing silently.
 */
public class Downtwo extends AsyncTask<String, Object, Downtwo.Result> {

    private static final String TAG = "Downtwo";

    /** native URLs: <panel>/api/version and <panel>/api/download */
    public static native String Version();
    public static native String Link();

    /* ----------------------------------------------------------- constants */

    private static final String PREF_NAME = "com.xlox.download";
    private static final String PREF_VERSION_KEY = "version";
    private static final String PREF_LAST_NOTIFIED = "last_notified_version";
    private static final String PREF_FAILED_VERSION = "failed_version";

    private static final String ZIP_NAME = "Xlox.zip";
    private static final String PART_SUFFIX = ".part";
    private static final String LOADER_DIR_NAME = "loader";
    private static final String TMP_DIR_NAME = "loader.new";

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 30000;
    private static final int MAX_ATTEMPTS = 3;
    private static final long MIN_FREE_BYTES = 12L * 1024L * 1024L; // 12 MB headroom

    /** modes accepted by {@link #execute(Object[])} */
    public static final String MODE_CHECK = "check_only";
    public static final String MODE_DOWNLOAD = "download";

    /* -------------------------------------------------------------- result */

    public enum Status {
        /** server version == installed version */
        UP_TO_DATE,
        /** a newer/different version exists and the user has not been told yet */
        UPDATE_AVAILABLE,
        /** update exists but this exact version was already offered once */
        ALREADY_NOTIFIED,
        /** download + extraction finished */
        INSTALLED,
        /** anything went wrong — see message */
        ERROR
    }

    public static class Result {
        public final Status status;
        public final String message;
        public final String version;

        Result(Status status, String message, String version) {
            this.status = status;
            this.message = message == null ? "" : message;
            this.version = version == null ? "" : version;
        }

        public boolean isSuccess() {
            return status != Status.ERROR;
        }

        public boolean isUpdateAvailable() {
            return status == Status.UPDATE_AVAILABLE;
        }

        @Override
        public String toString() {
            return status + (message.isEmpty() ? "" : " (" + message + ")");
        }
    }

    /* ----------------------------------------------------------- callbacks */

    public interface Callback {
        void onComplete(Result result);
    }

    /** progress 0..100 plus a stage label ("Connecting…", "Downloading…", "Extracting…") */
    public interface ProgressListener {
        void onProgress(int percent, String stage);
    }

    /* --------------------------------------------------------------- state */

    private final Context context;
    private final Callback callback;
    private ProgressListener progressListener;
    private String serverVersion = "";
    private String lastError = "";

    public Downtwo(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public Downtwo setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
        return this;
    }

    public String getFetchedServerVersion() {
        return serverVersion;
    }

    public String getLastError() {
        return lastError;
    }

    public static String installedVersion(Context context) {
        return prefs(context).getString(PREF_VERSION_KEY, "0.0");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /* -------------------------------------------------------------- worker */

    @Override
    protected Result doInBackground(String... params) {
        String downloadUrl = (params.length > 0 && params[0] != null) ? params[0] : Link();
        String mode = (params.length > 1 && params[1] != null) ? params[1] : MODE_DOWNLOAD;

        serverVersion = fetchServerVersion();
        if (serverVersion == null) {
            // The version endpoint being unreachable is not fatal: the cached lib
            // (if any) still works, so report it as an error the caller may ignore.
            return error(lastError.isEmpty() ? "Cannot reach the update server" : lastError);
        }

        SharedPreferences p = prefs(context);
        String installed = p.getString(PREF_VERSION_KEY, "0.0");

        if (MODE_CHECK.equals(mode)) {
            boolean haveLib = loaderDir().isDirectory();
            if (serverVersion.equals(installed) && haveLib) {
                return new Result(Status.UP_TO_DATE, "", serverVersion);
            }
            if (!haveLib) {
                // nothing installed yet — always worth downloading
                return new Result(Status.UPDATE_AVAILABLE, "", serverVersion);
            }
            if (serverVersion.equals(p.getString(PREF_LAST_NOTIFIED, ""))) {
                return new Result(Status.ALREADY_NOTIFIED, "", serverVersion);
            }
            return new Result(Status.UPDATE_AVAILABLE, "", serverVersion);
        }

        // MODE_DOWNLOAD: skip the transfer when nothing changed.
        if (serverVersion.equals(installed) && loaderDir().isDirectory()) {
            return new Result(Status.UP_TO_DATE, "", serverVersion);
        }

        Exception last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                downloadAndExtract(downloadUrl);
                p.edit()
                        .putString(PREF_VERSION_KEY, serverVersion)
                        .putString(PREF_LAST_NOTIFIED, serverVersion)
                        .remove(PREF_FAILED_VERSION)
                        .apply();
                return new Result(Status.INSTALLED, "", serverVersion);
            } catch (Exception e) {
                last = e;
                Log.w(TAG, "attempt " + attempt + "/" + MAX_ATTEMPTS + " failed: " + e.getMessage(), e);
                cleanupTemp();
                if (attempt < MAX_ATTEMPTS) {
                    sleep(1200L * attempt);
                    publishProgress(new Object[]{-1, "Retrying (" + (attempt + 1) + "/" + MAX_ATTEMPTS + ")…"});
                }
            }
        }
        // remember that this version keeps failing so the UI can offer a manual retry
        p.edit().putString(PREF_FAILED_VERSION, serverVersion).apply();
        String msg = last == null ? "Unknown download error" : friendly(last);
        return error(msg);
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        if (progressListener == null || values.length < 2) return;
        int percent = values[0] instanceof Integer ? (Integer) values[0] : -1;
        String stage = String.valueOf(values[1]);
        progressListener.onProgress(percent, stage);
    }

    @Override
    protected void onPostExecute(Result result) {
        if (result.status == Status.ERROR) {
            lastError = result.message;
            Log.e(TAG, "update failed: " + result.message);
        } else if (result.status == Status.UPDATE_AVAILABLE) {
            // mark as offered so we do not nag on every launch
            prefs(context).edit().putString(PREF_LAST_NOTIFIED, result.version).apply();
        }
        if (callback != null) {
            try {
                callback.onComplete(result);
            } catch (Throwable t) {
                Log.e(TAG, "callback crashed", t);
            }
        }
    }

    /* --------------------------------------------------------- networking */

    private HttpURLConnection open(String urlString) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT);
        c.setReadTimeout(READ_TIMEOUT);
        c.setInstanceFollowRedirects(true);
        c.setUseCaches(false);
        c.setRequestProperty("Cache-Control", "no-cache");
        c.setRequestProperty("User-Agent", "XLOX-Loader");
        return c;
    }

    /** @return version string, or null with {@link #lastError} set */
    private String fetchServerVersion() {
        HttpURLConnection connection = null;
        try {
            publishProgress(new Object[]{-1, "Checking for updates…"});
            connection = open(Version());
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                lastError = "Update server returned HTTP " + code;
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String line;
            try {
                line = reader.readLine();
            } finally {
                reader.close();
            }
            if (line == null) {
                lastError = "Update server sent an empty version";
                return null;
            }
            String version = line.trim();
            if (!version.matches("[A-Za-z0-9._-]{1,32}")) {
                lastError = "Unexpected version format: " + abbreviate(version, 40);
                return null;
            }
            return version;
        } catch (Exception e) {
            lastError = friendly(e);
            Log.e(TAG, "version check failed: " + e.getMessage(), e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void downloadAndExtract(String urlString) throws Exception {
        if (urlString == null || urlString.trim().isEmpty()) {
            throw new IOException("No download URL configured");
        }

        File base = context.getFilesDir();
        File zipFile = new File(base, ZIP_NAME);
        File partFile = new File(base, ZIP_NAME + PART_SUFFIX);

        // 1. download to a .part file ------------------------------------
        publishProgress(new Object[]{0, "Connecting…"});
        HttpURLConnection connection = open(urlString);
        try {
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IOException("Download server returned HTTP " + code);
            }

            long total = connection.getContentLength(); // int in the API, widened for clarity
            if (total > 0 && freeBytes(base) < total + MIN_FREE_BYTES) {
                throw new IOException("Not enough free storage for " + human(total));
            }

            if (partFile.exists() && !partFile.delete()) {
                throw new IOException("Cannot replace the previous partial download");
            }

            long downloaded = 0L;
            byte[] buffer = new byte[16 * 1024];
            try (InputStream in = connection.getInputStream();
                 FileOutputStream out = new FileOutputStream(partFile)) {
                int count;
                int lastPercent = -1;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    downloaded += count;
                    int percent = total > 0 ? (int) (downloaded * 100L / total) : -1;
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        publishProgress(new Object[]{percent, "Downloading lib…"});
                    }
                }
                out.flush();
            }

            if (downloaded <= 0) {
                throw new IOException("The downloaded file is empty");
            }
            if (total > 0 && downloaded != total) {
                throw new IOException("Download incomplete (" + human(downloaded) + " of " + human(total) + ")");
            }
            if (partFile.length() > 4 && !isZip(partFile)) {
                throw new IOException("The server did not return a zip file");
            }

            if (zipFile.exists() && !zipFile.delete()) {
                throw new IOException("Cannot replace the previous zip");
            }
            if (!partFile.renameTo(zipFile)) {
                throw new IOException("Cannot store the downloaded zip");
            }
        } finally {
            connection.disconnect();
        }

        // 2. extract into a staging directory ----------------------------
        publishProgress(new Object[]{100, "Extracting…"});
        File target = loaderDir();
        File staging = new File(base, TMP_DIR_NAME);
        deleteRecursive(staging);
        if (!staging.mkdirs() && !staging.isDirectory()) {
            throw new IOException("Cannot create the staging folder");
        }

        try {
            new ZipFile(zipFile, "".toCharArray()).extractAll(staging.getAbsolutePath());
        } catch (Exception e) {
            deleteRecursive(staging);
            throw new IOException("The zip could not be extracted: " + e.getMessage());
        }

        // zip may have a single top-level folder — flatten it
        File source = staging;
        File[] children = staging.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()) {
            source = children[0];
        }

        // 3. swap the old lib for the new one ----------------------------
        File backup = new File(base, LOADER_DIR_NAME + ".old");
        deleteRecursive(backup);
        if (target.exists() && !target.renameTo(backup)) {
            deleteRecursive(target);
        }
        if (!source.renameTo(target) && !copyTree(source, target)) {
            // roll back
            if (backup.exists()) {
                deleteRecursive(target);
                backup.renameTo(target);
            }
            deleteRecursive(staging);
            throw new IOException("Cannot install the new lib files");
        }

        setPermissions(target);
        deleteRecursive(backup);
        deleteRecursive(staging);
        if (zipFile.exists()) zipFile.delete();

        publishProgress(new Object[]{100, "Done"});
    }

    /* ------------------------------------------------------------ helpers */

    private File loaderDir() {
        return new File(context.getFilesDir(), LOADER_DIR_NAME);
    }

    private void cleanupTemp() {
        File base = context.getFilesDir();
        deleteRecursive(new File(base, ZIP_NAME + PART_SUFFIX));
        deleteRecursive(new File(base, TMP_DIR_NAME));
        deleteRecursive(new File(base, LOADER_DIR_NAME + ".old"));
    }

    private static boolean isZip(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] head = new byte[2];
            return in.read(head) == 2 && head[0] == 'P' && head[1] == 'K';
        } catch (IOException e) {
            return false;
        }
    }

    private static long freeBytes(File dir) {
        try {
            StatFs stat = new StatFs(dir.getAbsolutePath());
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Throwable t) {
            return Long.MAX_VALUE;
        }
    }

    private boolean copyTree(File from, File to) {
        try {
            if (from.isDirectory()) {
                if (!to.exists() && !to.mkdirs()) return false;
                File[] kids = from.listFiles();
                if (kids == null) return false;
                for (File kid : kids) {
                    if (!copyTree(kid, new File(to, kid.getName()))) return false;
                }
                return true;
            }
            try (FileInputStream in = new FileInputStream(from);
                 FileOutputStream out = new FileOutputStream(to)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "copy failed: " + from, e);
            return false;
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] kids = file.listFiles();
            if (kids != null) for (File kid : kids) deleteRecursive(kid);
        }
        if (!file.delete()) Log.w(TAG, "could not delete " + file.getAbsolutePath());
    }

    private void setPermissions(File file) {
        if (file == null) return;
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) for (File f : files) setPermissions(f);
        } else {
            //noinspection ResultOfMethodCallIgnored
            file.setReadable(true, false);
            //noinspection ResultOfMethodCallIgnored
            file.setWritable(true, false);
            //noinspection ResultOfMethodCallIgnored
            file.setExecutable(true, false);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private Result error(String message) {
        lastError = message;
        return new Result(Status.ERROR, message, serverVersion);
    }

    /** Turns exceptions into something a user can act on. */
    private static String friendly(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        String lower = m.toLowerCase();
        if (lower.contains("unknownhost") || lower.contains("unable to resolve host")) {
            return "No internet connection (cannot reach the update server)";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "The update server took too long to respond";
        }
        if (lower.contains("ssl") || lower.contains("certificate")) {
            return "Secure connection to the update server failed";
        }
        if (lower.contains("enospc") || lower.contains("no space")) {
            return "Not enough free storage to install the update";
        }
        return abbreviate(m, 120);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Convenience for the UI: "install" or "check" without touching the button states. */
    public static void check(Context context, Callback callback) {
        new Downtwo(context, callback).execute(Link(), MODE_CHECK);
    }

    /** Used by the panel docs / debug screens. */
    public static List<String> endpoints() {
        List<String> urls = new ArrayList<>();
        try {
            urls.add(Version());
            urls.add(Link());
        } catch (Throwable t) {
            Log.e(TAG, "native URLs unavailable", t);
        }
        return urls;
    }
}
