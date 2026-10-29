package com.xlox;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.xlox.utils.LibLoader;

import net_62v.external.IMundoProcessCallback;
import net_62v.external.MetaActivationManager;
import net_62v.external.MetaCore;

public class BoxApplication extends Application
        implements IMundoProcessCallback {

    static {
        try {
            System.loadLibrary("XloX");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    public static native String getSdkKey();

    private static final String TAG = "BoxApplication";

    public static BoxApplication gApp;


    public static boolean ENABLE_IMGUI = false;

    public static BoxApplication get() {
        return gApp;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MetaCore.setProcessLifecycleCallback(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gApp = this;
        
        try {
            MetaActivationManager.activateSdk(getSdkKey());
        } catch (Exception e) {
            Log.e(TAG, "Activation Error", e);
        }
    }

    /**
     * Called by the sandbox SDK once per guest process — this is the game's own
     * process, which is exactly where a mod library has to be loaded.
     *
     * The path is resolved by LibLoader rather than from getFilesDir(): inside
     * this process the sandbox rewrites the app's paths to the guest's virtual
     * storage, so the old lookup pointed at an empty directory and the library
     * silently never loaded.
     */
    @Override
    public void onCreate(String packageName, String processName) {
        // Depending on the sandbox build these two arrive as
        // (package, process) or the other way round, and sub-processes carry a
        // suffix (com.pubg.imobile:xxx), so match either one as a prefix.
        if (!isGame(packageName) && !isGame(processName)) {
            return;
        }

        ENABLE_IMGUI = false;

        String result = LibLoader.load(this);
        Log.d("Load", "libbgmi.so " + result);
    }

    private static boolean isGame(String name) {
        return name != null && name.startsWith("com.pubg.imobile");
    }
}
