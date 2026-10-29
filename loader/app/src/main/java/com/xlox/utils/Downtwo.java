package com.xlox.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import net.lingala.zip4j.ZipFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class Downtwo extends AsyncTask<String, String, String> {
    private static final String TAG = "Downtwo";

    public static native String Version();
    public static native String Link();

    public interface Callback {
        void onComplete(boolean success);
    }

    public interface ProgressListener {
        void onProgress(int percent);
    }

    private final Context context;
    private final Callback callback;
    private ProgressListener progressListener;
    private String serverVersion = "0.0";
    /** Why the last download/extract did not produce a lib ("" when it did). */
    private String lastError = "";
    private static final String PREF_NAME = "com.xlox.download";
    private static final String PREF_VERSION_KEY = "version";
    private static final String PREF_LAST_NOTIFIED = "last_notified_version";
    private static final String HOSTOP_ZIP = "Xlox.zip";
    private static final String LOADER_DIR_NAME = "loader";

    public Downtwo(Context context, Callback callback) {
        this.context = context;
        this.callback = callback;
    }

    public void setProgressListener(ProgressListener listener) {
        this.progressListener = listener;
    }

    public String getFetchedServerVersion() {
        return serverVersion;
    }

    public String getLastError() {
        return lastError;
    }

    @Override
    protected String doInBackground(String... params) {
        try {
            if (params.length == 0 || params[0] == null) {
                return "Invalid URL provided";
            }

            String downloadUrl = params[0];
            serverVersion = getServerVersion();

            if (serverVersion == null) {
                return "Failed to retrieve server version";
            }

            // Check if it's a "check_only" call
            if (params.length > 1 && "check_only".equals(params[1])) {
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                String localVersion = prefs.getString(PREF_VERSION_KEY, "0.0");
                String lastNotified = prefs.getString(PREF_LAST_NOTIFIED, "0.0");

                if (!localVersion.equals(serverVersion)) {
                    // There is an update on the server
                    if (!serverVersion.equals(lastNotified)) {
                        // User has NOT been notified about THIS specific version yet
                        return "Update Available";
                    } else {
                        // User has already been notified about this version
                        return "Already Notified";
                    }
                } else {
                    return "No Update Available";
                }
            }

            return downloadAndExtract(downloadUrl);
        } catch (Exception e) {
            Log.e(TAG, "doInBackground error: ", e);
            return "Background error: " + e.getMessage();
        }
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        // We consider it a success if it's not an error message
        boolean success = result == null || 
                         "No Update Available".equals(result) || 
                         "Update Available".equals(result) || 
                         "Already Notified".equals(result);

        lastError = success ? "" : (result == null ? "unknown error" : result);
        if (!success) Log.e(TAG, "lib update failed: " + lastError);
        
        if (callback != null) {
            callback.onComplete(success);
        }
    }

    public String getServerVersion() {
        try {
            URL url = new URL(Version());
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.connect();

            InputStream inputStream = connection.getInputStream();
            Scanner scanner = new Scanner(inputStream);
            String version = scanner.hasNextLine() ? scanner.nextLine().trim() : "1.0";
            scanner.close();
            inputStream.close();
            connection.disconnect();
            return version;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching server version: ", e);
            return null;
        }
    }

    private String downloadAndExtract(String urlString) {
        try {
            URL url = new URL(urlString);
            Log.i(TAG, "lib download: " + urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.connect();

            int code = connection.getResponseCode();
            Log.i(TAG, "lib download: HTTP " + code
                    + ", " + connection.getContentLength() + " bytes, "
                    + connection.getContentType());
            if (code < 200 || code >= 300) {
                // a 404 here means the panel has no lib published: the body is a
                // JSON message, not a zip, so unzipping it used to fail silently
                return "HTTP " + code + " from " + urlString;
            }

            int totalSize = connection.getContentLength();
            int downloaded = 0;

            InputStream input = connection.getInputStream();
            File pathBase = context.getFilesDir();

            File pathOutput = new File(pathBase, HOSTOP_ZIP);
            FileOutputStream output = new FileOutputStream(pathOutput);
            byte[] data = new byte[8192];
            int count;

            while ((count = input.read(data)) != -1) {
                output.write(data, 0, count);
                downloaded += count;
                if (totalSize > 0 && progressListener != null) {
                    int percent = (int) ((downloaded * 100L) / totalSize);
                    progressListener.onProgress(percent);
                }
            }

            output.close();
            input.close();

            // A panel that answered with an error page instead of a zip would be
            // written out happily and then fail inside the unzipper; say so here.
            if (!looksLikeZip(pathOutput)) {
                Log.e(TAG, "downloaded file is not a zip (" + pathOutput.length() + " bytes)");
                //noinspection ResultOfMethodCallIgnored
                pathOutput.delete();
                return "The server did not return a zip (" + pathOutput.length() + " bytes)";
            }

            File loaderDirectory = new File(pathBase, LOADER_DIR_NAME);
            if (!loaderDirectory.exists()) {
                loaderDirectory.mkdirs();
            }

            // A previous load may have made the old library read-only, and the
            // unzipper cannot overwrite that: clear the file out first.
            File staleLib = new File(loaderDirectory, "libbgmi.so");
            if (staleLib.exists() && !staleLib.delete()) {
                staleLib.setWritable(true, false);
                staleLib.setWritable(true, true);
                //noinspection ResultOfMethodCallIgnored
                staleLib.delete();
            }

            // Extract ZIP
            new ZipFile(pathOutput, "".toCharArray()).extractAll(loaderDirectory.getAbsolutePath());

            // The loader only ever loads <files>/loader/libbgmi.so, so whatever
            // shape the zip has inside, the library is put where it is looked
            // for: a zip packing it under a folder used to extract "successfully"
            // and still never load.
            File lib = new File(loaderDirectory, "libbgmi.so");
            if (!lib.exists()) {
                File found = find(loaderDirectory, "libbgmi.so");
                if (found != null && !found.getAbsolutePath().equals(lib.getAbsolutePath())) {
                    Log.i(TAG, "libbgmi.so was nested at " + found + " — moving it to the root");
                    if (!found.renameTo(lib)) {
                        copy(found, lib);
                    }
                }
            }
            if (!lib.exists()) {
                Log.e(TAG, "the zip does not contain libbgmi.so");
                return "The zip has no libbgmi.so in it";
            }
            Log.i(TAG, "lib ready: " + lib.getAbsolutePath() + " (" + lib.length() + " bytes)");

            setPermissions(loaderDirectory);

            if (pathOutput.exists()) {
                pathOutput.delete();
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                // the game process cannot trust getFilesDir() — the sandbox
                // redirects it — so the real path is remembered here
                .putString("lib_path", lib.getAbsolutePath())
                .putString(PREF_VERSION_KEY, serverVersion)
                .putString(PREF_LAST_NOTIFIED, serverVersion) // Reset notification on successful update
                .apply();

            return null; // Success
        } catch (Exception e) {
            Log.e(TAG, "Error downloading or extracting file: ", e);
            // The caller handles this state silently; do not surface a failure message as a popup.
            return "UPDATE_UNAVAILABLE";
        }
    }

    /** The first file with this name anywhere under `dir`, or null. */
    private File find(File dir, String name) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        for (File f : files) {
            if (f.isFile() && f.getName().equals(name)) return f;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                File hit = find(f, name);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private void copy(File from, File to) {
        try (FileInputStream in = new FileInputStream(from);
             FileOutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "could not copy " + from + " to " + to, e);
        }
    }

    /** A zip starts with "PK\003\004" (or "PK\005\006" when empty). */
    private boolean looksLikeZip(File file) {
        if (!file.isFile() || file.length() < 4) return false;
        try (FileInputStream in = new FileInputStream(file)) {
            int a = in.read();
            int b = in.read();
            return a == 'P' && b == 'K';
        } catch (Exception e) {
            return false;
        }
    }

    private void setPermissions(File directory) {
        if (directory == null) return;
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    setPermissions(file);
                }
            }
        } else {
            directory.setReadable(true, false);
            directory.setWritable(true, false);
            directory.setExecutable(true, false);
        }
    }
}
