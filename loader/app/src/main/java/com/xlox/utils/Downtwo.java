package com.xlox.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import net.lingala.zip4j.ZipFile;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class Downtwo extends AsyncTask<String, String, String> {
    private static final String TAG = "Downtwo";

    public static native String Version();
    public static native String Link();

    /** GitHub-hosted mirrors, used only when the panel has no release published. */
    public static native String MirrorVersion();
    public static native String MirrorLink();

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
    private static final String PREF_SOURCE_KEY = "lib_source";
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

    /** One place a lib can come from: the panel, or the GitHub mirror. */
    private static final class Source {
        final String name;
        final String version;
        final String zipUrl;

        Source(String name, String version, String zipUrl) {
            this.name = name;
            this.version = version;
            this.zipUrl = zipUrl;
        }
    }

    @Override
    protected String doInBackground(String... params) {
        try {
            List<Source> sources = new ArrayList<>();
            Source panel = panelSource();
            if (panel != null) sources.add(panel);
            Source mirror = mirrorSource();
            if (mirror != null) sources.add(mirror);
            if (sources.isEmpty()) {
                return "Failed to retrieve server version";
            }
            serverVersion = sources.get(0).version;

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String localVersion = prefs.getString(PREF_VERSION_KEY, "0.0");
            String lastNotified = prefs.getString(PREF_LAST_NOTIFIED, "0.0");
            String localSource = prefs.getString(PREF_SOURCE_KEY, "");

            // A lib that never made it to disk is an update even when the
            // version string has not moved: without this, one failed download
            // left the loader convinced it was up to date for good.
            boolean libOnDisk = libFile().isFile();
            Source chosen = null;
            for (Source source : sources) {
                boolean sameVersion = source.version.equals(localVersion)
                        && (localSource.isEmpty() || localSource.equals(source.name));
                if (!libOnDisk || !sameVersion) {
                    chosen = source;
                    break;
                }
            }

            // Check-only pass at launch: nothing is downloaded, it only decides
            // whether the button should say UPDATE AVAILABLE.
            if (params.length > 1 && "check_only".equals(params[1])) {
                if (chosen == null) {
                    return "No Update Available";
                }
                return chosen.version.equals(lastNotified)
                        ? "Already Notified"
                        : "Update Available";
            }

            if (chosen == null) {
                Log.i(TAG, "lib is up to date (" + localVersion + " from " + localSource + ")");
                return null;
            }

            // Try the chosen source, then the others: a missing release in the
            // panel is exactly the case the mirror exists for.
            String firstError = null;
            List<Source> order = new ArrayList<>();
            order.add(chosen);
            for (Source source : sources) {
                if (source != chosen) order.add(source);
            }
            for (Source source : order) {
                if (libOnDisk && source.version.equals(localVersion)) {
                    Log.i(TAG, "lib " + source.version + " is already installed");
                    continue;
                }
                Log.i(TAG, "lib source: " + source.name + " " + source.version + " <- " + source.zipUrl);
                String error = downloadAndExtract(source.zipUrl, source.version, source.name);
                if (error == null) {
                    serverVersion = source.version;
                    return null;
                }
                if (firstError == null) firstError = error;
                Log.e(TAG, "lib from " + source.name + " failed: " + error);
            }
            return firstError;
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

    private File libFile() {
        return new File(new File(context.getFilesDir(), LOADER_DIR_NAME), "libbgmi.so");
    }

    /**
     * The panel, when it actually has a release. `/api/version` answers with a
     * fallback version even when nothing is published, so ask the JSON form,
     * where `file_name` is null in that case.
     */
    private Source panelSource() {
        String json = httpText(Version() + "?format=json");
        if (json == null) {
            // The probe failed (offline, or a hosting error page): keep trusting
            // the panel and let the download itself decide.
            String version = readVersion(Version());
            return version == null ? null : new Source("panel", version, Link());
        }
        try {
            JSONObject object = new JSONObject(json.trim());
            String fileName = object.isNull("file_name") ? "" : object.optString("file_name", "");
            String version = object.optString("version", "");
            if (fileName.isEmpty() || version.isEmpty()) {
                // /api/version answers with a fallback version even when nothing
                // is published — only file_name tells the truth.
                Log.i(TAG, "panel has no lib published, using the mirror");
                return null;
            }
            return new Source("panel", version, Link());
        } catch (Exception e) {
            Log.w(TAG, "panel release probe unreadable: " + e.getMessage());
            return null;
        }
    }

    private Source mirrorSource() {
        String version = readVersion(MirrorVersion());
        return version == null ? null : new Source("mirror", version, MirrorLink());
    }

    /** Body of a small GET, or null. */
    private String httpText(String address) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.connect();
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return null;
            }
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null && out.length() < 4096) {
                    out.append(line).append('\n');
                }
            }
            return out.toString();
        } catch (Exception e) {
            Log.w(TAG, "GET " + address + " failed: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public String getServerVersion() {
        String version = readVersion(Version());
        if (version != null) return version;
        return readVersion(MirrorVersion());
    }

    private String readVersion(String address) {
        try {
            URL url = new URL(address);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.connect();

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.e(TAG, "lib version: HTTP " + code + " from " + address);
                connection.disconnect();
                return null;
            }

            InputStream inputStream = connection.getInputStream();
            Scanner scanner = new Scanner(inputStream);
            String version = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
            scanner.close();
            inputStream.close();
            connection.disconnect();

            if (version.startsWith("\ufeff")) version = version.substring(1).trim();
            // The panel only ever answers with a bare version token. Anything
            // else means a hosting error page or a login redirect, and treating
            // that as a version would make the loader re-download on every launch.
            if (!version.matches("[A-Za-z0-9._-]{1,32}")) {
                Log.e(TAG, "lib version: not a version (" + version + ")");
                return null;
            }
            return version;
        } catch (Exception e) {
            Log.e(TAG, "Error fetching server version: ", e);
            return null;
        }
    }

    private String downloadAndExtract(String urlString, String version, String sourceName) {
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
                List<File> hits = LibZip.findAll(loaderDirectory, "libbgmi.so");
                if (hits.isEmpty()) {
                    Log.e(TAG, "the zip does not contain libbgmi.so");
                    return "The zip has no libbgmi.so in it";
                }

                // A zip that ships several ABIs must not have its 32-bit copy
                // promoted: that one can never load into the game's arm64 process.
                File chosen = LibZip.pickArm64(hits, loaderDirectory);
                File parent = chosen.getParentFile();
                if (parent != null && !parent.equals(loaderDirectory)) {
                    Log.i(TAG, "libbgmi.so was nested at " + chosen + " — moving it to the root");
                    // libraries it links against usually sit next to it
                    File[] siblings = parent.listFiles();
                    if (siblings != null) {
                        for (File sibling : siblings) {
                            String name = sibling.getName();
                            if (!sibling.isFile() || !name.endsWith(".so") || name.equals("libbgmi.so")) {
                                continue;
                            }
                            File dest = new File(loaderDirectory, name);
                            if (!dest.exists() && !sibling.renameTo(dest)) copy(sibling, dest);
                        }
                    }
                }
                if (!chosen.renameTo(lib)) copy(chosen, lib);

                // whatever other ABIs were in there are useless now
                for (File other : hits) {
                    if (!samePath(other, chosen) && other.exists()) {
                        Log.i(TAG, "dropping " + other);
                        //noinspection ResultOfMethodCallIgnored
                        other.delete();
                    }
                }
            }
            if (!lib.exists()) {
                Log.e(TAG, "the zip does not contain libbgmi.so");
                return "The zip has no libbgmi.so in it";
            }

            // Say now, not at launch, when the archive holds the wrong thing.
            Elf elf = Elf.read(lib);
            if (!elf.elf) {
                //noinspection ResultOfMethodCallIgnored
                lib.delete();
                return "The zip's libbgmi.so is not a library (" + lib.length() + " bytes)";
            }
            if (!elf.is64Bit || elf.machine != 0xB7) {
                //noinspection ResultOfMethodCallIgnored
                lib.delete();
                return "The zip's libbgmi.so is " + elf.arch() + ", the game is arm64-v8a";
            }
            Log.i(TAG, "lib ready: " + lib.getAbsolutePath() + " (" + lib.length() + " bytes, "
                    + elf.arch() + ")");

            setPermissions(loaderDirectory);

            if (pathOutput.exists()) {
                pathOutput.delete();
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                // the game process cannot trust getFilesDir() — the sandbox
                // redirects it — so the real path is remembered here
                .putString("lib_path", lib.getAbsolutePath())
                .putString(PREF_VERSION_KEY, version)
                .putString(PREF_SOURCE_KEY, sourceName)
                .putString(PREF_LAST_NOTIFIED, version) // Reset notification on successful update
                .apply();

            return null; // Success
        } catch (Exception e) {
            Log.e(TAG, "Error downloading or extracting file: ", e);
            // The caller handles this state silently; do not surface a failure message as a popup.
            return "UPDATE_UNAVAILABLE";
        }
    }

    /** The first file with this name anywhere under `dir`, or null. */
    private boolean samePath(File a, File b) {
        return a != null && b != null && a.getAbsolutePath().equals(b.getAbsolutePath());
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
