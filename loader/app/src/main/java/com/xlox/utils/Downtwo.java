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
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

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

            File loaderDirectory = new File(pathBase, LOADER_DIR_NAME);
            if (!loaderDirectory.exists()) {
                loaderDirectory.mkdirs();
            }

            // Extract ZIP
            new ZipFile(pathOutput, "".toCharArray()).extractAll(loaderDirectory.getAbsolutePath());

            setPermissions(loaderDirectory);

            if (pathOutput.exists()) {
                pathOutput.delete();
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
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
