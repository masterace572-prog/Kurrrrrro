package com.xlox;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.xlox.utils.Downtwo;

import org.lsposed.lsparanoid.Obfuscate;

/**
 * Startup gate.
 *
 * The loader cannot be used before the lib is current: this activity checks the
 * panel, downloads/extracts the lib when the version changed, and only then
 * opens the login screen (or the main screen, when we come back here after a
 * successful key check).
 *
 * Failure policy
 *   • a lost connection is retried automatically (with a visible countdown)
 *   • if the automatic retries run out, the user is offered
 *        RETRY  ·  REDOWNLOAD (ignore the cached version)  ·  RESTART
 *   • the app never continues with an outdated / missing lib
 */
@Obfuscate
public class SplashActivity extends AppCompatActivity {

    public static final String EXTRA_NEXT = "next";
    public static final String NEXT_LOGIN = "login";
    public static final String NEXT_MAIN = "main";

    /** how many times we retry on our own before asking the user */
    private static final int AUTO_RETRIES = 4;
    private static final int RETRY_DELAY_MS = 4000;
    /** never flash the splash for less than this */
    private static final long MIN_SPLASH_MS = 700L;

    private TextView statusText;
    private TextView percentText;
    private TextView detailText;
    private ProgressBar progressBar;
    private View actions;
    private Button retryButton;
    private Button redownloadButton;
    private Button restartButton;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int attempt = 0;
    private boolean working = false;
    private boolean finished = false;
    private long startedAt = 0L;
    private ConnectivityManager.NetworkCallback networkCallback;

    private final Runnable retryRunnable = this::startUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        startedAt = System.currentTimeMillis();

        statusText = findViewById(R.id.splashStatus);
        percentText = findViewById(R.id.splashPercent);
        detailText = findViewById(R.id.splashDetail);
        progressBar = findViewById(R.id.splashProgress);
        actions = findViewById(R.id.splashActions);
        retryButton = findViewById(R.id.splashRetry);
        redownloadButton = findViewById(R.id.splashRedownload);
        restartButton = findViewById(R.id.splashRestart);

        if (retryButton != null) retryButton.setOnClickListener(v -> {
            attempt = 0;
            startUpdate();
        });

        if (redownloadButton != null) redownloadButton.setOnClickListener(v -> {
            // forget the installed version so the same lib is fetched again
            Downtwo.forceReinstall(this);
            attempt = 0;
            startUpdate();
        });

        if (restartButton != null) restartButton.setOnClickListener(v -> restartApp());

        startUpdate();
    }

    /* ------------------------------------------------------------- states */

    private void startUpdate() {
        if (isFinishing() || finished) return;
        if (working) return;
        working = true;
        attempt++;

        runOnUiThread(() -> {
            if (actions != null) actions.setVisibility(View.GONE);
            setStatus("Checking for updates…", "");
            if (progressBar != null) {
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setIndeterminate(true);
            }
            if (percentText != null) percentText.setText("");
        });

        Downtwo task = new Downtwo(this, this::onUpdateFinished);
        task.setProgressListener((percent, stage) -> runOnUiThread(() -> {
            if (stage != null && !stage.isEmpty()) setStatus(stage, null);
            if (progressBar != null) {
                if (percent < 0) {
                    progressBar.setIndeterminate(true);
                } else {
                    progressBar.setIndeterminate(false);
                    progressBar.setProgress(percent);
                }
            }
            if (percentText != null && percent >= 0) percentText.setText(percent + "%");
        }));

        try {
            String link = Downtwo.Link();
            task.execute(link, Downtwo.MODE_DOWNLOAD);
        } catch (Throwable t) {
            working = false;
            onFailed("Update endpoint is unavailable in this build");
        }
    }

    private void onUpdateFinished(Downtwo.Result result) {
        working = false;
        if (isFinishing() || finished) return;

        if (result.canProceed()) {
            if (result.status == Downtwo.Status.NO_PAYLOAD) {
                // nothing published yet — the app is usable, the main screen
                // tells the user that no lib is available
                setStatus("No update published yet", "");
            } else {
                setStatus("Up to date", "");
            }
            if (progressBar != null) {
                progressBar.setIndeterminate(false);
                progressBar.setProgress(100);
            }
            if (percentText != null) percentText.setText("100%");
            proceed();
            return;
        }

        onFailed(result.message.isEmpty() ? "Update failed" : result.message);
    }

    private void onFailed(String message) {
        if (isFinishing() || finished) return;

        if (attempt <= AUTO_RETRIES) {
            final int remaining = (AUTO_RETRIES - attempt) + 1;
            setStatus(message, "Retrying automatically — attempt " + attempt + "/" + AUTO_RETRIES);
            if (progressBar != null) progressBar.setIndeterminate(true);
            if (percentText != null) percentText.setText("");
            countdownThenRetry(remaining);
            return;
        }

        setStatus(message, "Check your internet connection, then retry.");
        if (progressBar != null) {
            progressBar.setIndeterminate(false);
            progressBar.setProgress(0);
        }
        if (percentText != null) percentText.setText("");
        if (actions != null) {
            actions.setVisibility(View.VISIBLE);
            actions.setAlpha(0f);
            actions.animate().alpha(1f).setDuration(250).start();
        }
        if (redownloadButton != null) {
            redownloadButton.setVisibility(Downtwo.hasInstalledLib(this) ? View.VISIBLE : View.GONE);
        }
        watchNetwork();
    }

    /** visible countdown, then another attempt */
    private void countdownThenRetry(int seconds) {
        if (isFinishing() || finished) return;
        if (seconds <= 0) {
            handler.postDelayed(retryRunnable, 200);
            return;
        }
        if (detailText != null) {
            detailText.setText("Retrying in " + seconds + "s — attempt " + attempt + "/" + AUTO_RETRIES);
        }
        handler.postDelayed(() -> {
            if (isFinishing() || finished) return;
            if (actions != null && actions.getVisibility() == View.VISIBLE) return;
            countdownThenRetry(seconds - 1);
        }, 1000L);
    }

    /** If the user comes back online while sitting on the error screen, retry at once. */
    private void watchNetwork() {
        if (networkCallback != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    runOnUiThread(() -> {
                        if (isFinishing() || finished || working) return;
                        attempt = 0;
                        startUpdate();
                    });
                }
            };
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            cm.registerNetworkCallback(request, networkCallback);
        } catch (Throwable ignored) {
        }
    }

    private void proceed() {
        if (finished) return;
        finished = true;
        unwatchNetwork();

        long shown = System.currentTimeMillis() - startedAt;
        long wait = Math.max(0L, MIN_SPLASH_MS - shown);
        handler.postDelayed(() -> {
            if (isFinishing()) return;
            Class<? extends Activity> next = NEXT_MAIN.equals(nextTarget())
                    ? MAct.class
                    : LogAct.class;
            try {
                startActivity(new Intent(SplashActivity.this, next));
            } catch (Throwable t) {
                Toast.makeText(this, "Could not open the next screen", Toast.LENGTH_LONG).show();
                finished = false;
                return;
            }
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, wait);
    }

    private String nextTarget() {
        Intent intent = getIntent();
        String value = intent == null ? null : intent.getStringExtra(EXTRA_NEXT);
        return value == null ? NEXT_LOGIN : value;
    }

    private void restartApp() {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Throwable ignored) {
        }
        finishAffinity();
        Runtime.getRuntime().exit(0);
    }

    private void setStatus(String message, String detail) {
        runOnUiThread(() -> {
            if (statusText != null) statusText.setText(message == null ? "" : message);
            if (detailText != null && detail != null) detailText.setText(detail);
        });
    }

    private void unwatchNetwork() {
        if (networkCallback == null) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) cm.unregisterNetworkCallback(networkCallback);
        } catch (Throwable ignored) {
        }
        networkCallback = null;
    }

    /* ------------------------------------------------------------- lifecycle */

    @Override
    public void onBackPressed() {
        // While the update is running the gate must not be dismissed. Once the
        // error screen is up, back simply leaves the app — it still cannot be used.
        if (working) {
            Toast.makeText(this, "Please wait for the update to finish", Toast.LENGTH_SHORT).show();
            return;
        }
        if (actions != null && actions.getVisibility() == View.VISIBLE) {
            finishAffinity();
            Runtime.getRuntime().exit(0);
            return;
        }
        Toast.makeText(this, "Please wait for the update to finish", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unwatchNetwork();
        super.onDestroy();
    }
}
