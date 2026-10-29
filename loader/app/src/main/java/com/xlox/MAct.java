package com.xlox;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.xlox.utils.AppManager;
import com.xlox.utils.LibGate;
import com.xlox.utils.LibLoader;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import net_62v.external.MetaActivationManager;
import net_62v.external.MetaActivityManager;

/**
 * The one screen that matters: what the key allows, whether the game data is in
 * place, and a single button that does the next right thing.
 *
 * There is deliberately one action at a time — copy the game data, then launch —
 * so the button never asks the user to choose between two states that are only
 * valid in sequence.
 */
@Obfuscate
public class MAct extends AppCompatActivity {

    private static final String PKG_BGMI = "com.pubg.imobile";
    private static final String PREF_NAME = "com.xlox.download";
    private static final String PREF_VERSION_KEY = "version";

    private static native String exdate();

    static {
        try {
            System.loadLibrary("XloX");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    private AppCompatButton actionButton;
    private TextView actionHint;
    private TextView obbState;
    private TextView percentText;
    private ProgressBar obbProgress;

    private TextView subscriptionStatus;
    private TextView expiryValue;
    private TextView timeRemaining;
    private TextView deviceValue;
    private TextView deviceStatus;
    private TextView appVersionText;
    private TextView checkUpdateButton;

    private View statusToggle;
    private View statusLoader;
    private TextView statusToggleText;

    private boolean gameInstalled = false;
    private boolean gameRunning = false;
    private boolean obbCopied = false;
    /** Where the game data was found ("Android/obb", "sandbox", …) for the card. */
    private String obbWhere = "";
    /** Why the last copy did not work, shown verbatim instead of "try again". */
    private String obbProblem = "";
    private boolean busy = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(Color.parseColor("#0B0C0E"));
        setContentView(R.layout.activity_main);

        bindViews();
        requestStorageAccess();
        startKeyCountdown();
        showDevice();
        refreshLibraryLine();

        if (checkUpdateButton != null) {
            checkUpdateButton.setOnClickListener(v -> checkForUpdate());
        }
        actionButton.setOnClickListener(v -> onPrimaryAction());

        fadeIn();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // coming back to the launcher means the game is no longer in front, and
        // the game data may have been copied from somewhere else in the meantime
        gameRunning = false;
        refreshGameState();
    }

    private void bindViews() {
        actionButton = findViewById(R.id.startBgmi);
        actionHint = findViewById(R.id.actionHint);
        obbState = findViewById(R.id.obbState);
        percentText = findViewById(R.id.percentText);
        obbProgress = findViewById(R.id.downloadProgressBar);

        subscriptionStatus = findViewById(R.id.subscriptionStatus);
        expiryValue = findViewById(R.id.expiryValue);
        timeRemaining = findViewById(R.id.timeRemaining);
        deviceValue = findViewById(R.id.deviceValue);
        deviceStatus = findViewById(R.id.deviceStatus);
        appVersionText = findViewById(R.id.appVersionText);
        checkUpdateButton = findViewById(R.id.checkUpdateButton);

        statusToggle = findViewById(R.id.statusToggle);
        statusLoader = findViewById(R.id.statusLoader);
        statusToggleText = findViewById(R.id.statusToggleText);
    }

    private void fadeIn() {
        View[] views = {findViewById(R.id.header), findViewById(R.id.mainCard),
                findViewById(R.id.obbCard), actionButton, actionHint};
        long delay = 0;
        for (View view : views) {
            if (view == null) continue;
            view.setAlpha(0f);
            view.setTranslationY(14f);
            view.animate().alpha(1f).translationY(0f).setDuration(320).setStartDelay(delay).start();
            delay += 60;
        }
    }

    // ------------------------------------------------------------------ state

    private void onPrimaryAction() {
        if (busy) return;
        if (!gameInstalled) {
            openStore();
        } else if (gameRunning) {
            stopGame();
        } else if (!obbCopied) {
            copyGameData();
        } else {
            launchGame();
        }
    }

    private void refreshGameState() {
        new Thread(() -> {
            boolean installed;
            try {
                PackageInfo info = getPackageManager().getPackageInfo(PKG_BGMI, 0);
                installed = info != null;
            } catch (Exception e) {
                installed = false;
            }

            boolean copied = false;
            String where = "";
            String problem = "";
            if (installed) {
                try {
                    AppManager.ObbStatus status = new AppManager(this).obbStatus(PKG_BGMI);
                    copied = status.ready;
                    where = status.where;
                    problem = status.detail;
                } catch (Exception e) {
                    // The folder could not be inspected at all. Asking for a
                    // fresh copy every launch because of that is what this
                    // used to do; trust the record of the copy instead.
                    Log.w("MAct", "could not look for the game data", e);
                    copied = AppManager.hasObbReceipt(this, PKG_BGMI);
                    problem = e.getMessage() == null ? e.toString() : e.getMessage();
                }
            }

            final boolean fInstalled = installed;
            final boolean fCopied = copied;
            final String fWhere = where;
            final String fProblem = problem;
            runOnUiThread(() -> {
                gameInstalled = fInstalled;
                obbCopied = fCopied;
                obbWhere = fWhere;
                obbProblem = fCopied ? "" : fProblem;
                renderState();
            });
        }).start();
    }

    /** The single button, the hint under it and the game-data card. */
    private void renderState() {
        if (deviceStatus != null) {
            deviceStatus.setText(gameInstalled ? "Ready" : "No game");
            deviceStatus.setTextColor(Color.parseColor(gameInstalled ? "#4CB98A" : "#D7A363"));
        }

        if (obbState != null) {
            String text = obbCopied
                    ? (obbWhere.isEmpty() ? "Ready" : "Ready · " + obbWhere)
                    : "Not copied yet";
            obbState.setText(text);
            obbState.setTextColor(Color.parseColor(obbCopied ? "#4CB98A" : "#F2F3F5"));
        }

        if (busy) {
            setAction("Working…", "Please keep the app open.");
            actionButton.setAlpha(0.5f);
            actionButton.setEnabled(false);
            return;
        }

        actionButton.setEnabled(true);
        actionButton.setAlpha(1f);

        if (!gameInstalled) {
            setAction("Install game", "BGMI is not installed on this device.");
        } else if (gameRunning) {
            setAction("Stop game", "The game is running.");
            actionButton.setBackgroundResource(R.drawable.bg_ref_stop_button);
            actionButton.setTextColor(Color.parseColor("#E0737C"));
            return;
        } else if (!obbCopied) {
            // say what the last attempt found instead of a cheerful generic line
            setAction("Copy game data", obbProblem.isEmpty()
                    ? "One-time copy. After this the button just launches."
                    : shorten(obbProblem, 120));
        } else {
            // data is already in place: no copying, straight to the game
            setAction("Launch game", "Game data ready. Nothing else to do.");
        }
        actionButton.setBackgroundResource(R.drawable.bg_primary_action);
        actionButton.setTextColor(Color.parseColor("#0B0C0E"));
    }

    private String shorten(String text, int max) {
        String clean = text.replace('\n', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    private void setAction(String label, String hint) {
        if (actionButton != null) actionButton.setText(label);
        if (actionHint != null) actionHint.setText(hint);
    }

    // ------------------------------------------------------------- game data

    private void copyGameData() {
        busy = true;
        setBusyCopy(true);
        showStatus("Copying game data…", false);

        new AppManager(this).copyObbFolderAsync(PKG_BGMI, new AppManager.CopyCallback() {
            @Override
            public void onCopyCompleted(boolean success) {
                runOnUiThread(() -> {
                    busy = false;
                    setBusyCopy(false);
                    if (success) {
                        obbCopied = true;
                        showStatus("Game data ready. You can launch.", false);
                        hideStatusSoon(2500);
                        refreshGameState();   // fill in where it was found
                    } else {
                        aborted("Copy failed.");
                    }
                    renderState();
                });
            }

            @Override
            public void onProgressUpdate(int progress) {
                runOnUiThread(() -> {
                    if (percentText != null) percentText.setText(progress + "%");
                    if (obbProgress != null) obbProgress.setProgress(progress);
                    if (obbState != null) obbState.setText("Copying…");
                });
            }

            @Override
            public void onCopyFailed(String reason) {
                runOnUiThread(() -> explainCopyFailure(reason));
            }
        }, false);
    }

    /**
     * Says what actually went wrong. "Copy failed. Try again." was true for every
     * possible cause — no permission, no file, a folder that cannot be written —
     * so it never told anybody what to change.
     */
    private void explainCopyFailure(String reason) {
        String text = reason == null || reason.trim().isEmpty()
                ? "The game data could not be copied."
                : reason.trim();
        obbProblem = text;
        if (obbState != null) obbState.setText("Copy failed");
        showStatus(text, true);

        if (isFinishing()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Copy failed")
                .setMessage(text + "\n\nIf the game data is already on the phone "
                        + "(Android/obb) the game can be started as it is.")
                .setPositiveButton("Retry", (dialog, which) -> copyGameData())
                .setNeutralButton("Launch anyway", (dialog, which) -> launchGame())
                .setNegativeButton("Close", null)
                .show();
    }

    private void setBusyCopy(boolean working) {
        if (obbProgress != null) {
            obbProgress.setVisibility(working ? View.VISIBLE : View.GONE);
            obbProgress.setIndeterminate(false);
            obbProgress.setProgress(0);
        }
        if (percentText != null) {
            percentText.setText(working ? "0%" : "");
        }
    }

    private void aborted(String message) {
        busy = false;
        setBusyCopy(false);
        showStatus(message, true);
        renderState();
    }

    // ---------------------------------------------------------- launch / stop

    private void launchGame() {
        // Neither of these is a reason to refuse to start the game. The sandbox
        // reports an inactive container until something has been launched, and
        // older builds launch perfectly well after that check said no.
        try {
            if (!MetaActivationManager.getActivationStatus()) {
                Log.w("MAct", "sandbox reports no activation, launching anyway");
            }
        } catch (Exception e) {
            Log.w("MAct", "activation check failed", e);
        }

        boolean started = false;
        String why = "";

        // 1. the documented way: build the splash intent and start it
        try {
            Intent intent = MetaActivityManager.obtainSplashLaunchIntent(PKG_BGMI, this);
            if (intent != null) {
                startActivity(intent);
                started = true;
            } else {
                why = "the sandbox returned no launch intent";
            }
        } catch (Exception e) {
            Log.e("MAct", "splash launch failed", e);
            why = e.getMessage() == null ? e.toString() : e.getMessage();
        }

        // 2. the direct call, for the builds where the intent comes back empty
        if (!started) {
            try {
                MetaActivityManager.launchAppWithSplash(this, PKG_BGMI);
                started = true;
                why = "";
            } catch (Exception e) {
                Log.e("MAct", "direct launch failed", e);
                if (why.isEmpty()) why = e.getMessage() == null ? e.toString() : e.getMessage();
            }
        }

        // 3. last resort: a plain intent. If the sandbox maps the package this
        //    still lands in the game, and if it does not, nothing else would.
        if (!started) {
            try {
                Intent fallback = getPackageManager().getLaunchIntentForPackage(PKG_BGMI);
                if (fallback != null) {
                    startActivity(fallback);
                    started = true;
                    why = "";
                }
            } catch (Exception e) {
                Log.e("MAct", "plain launch failed", e);
            }
        }

        showStatus("Starting the game…", false);

        // The launch goes through the sandbox and can take a few seconds, so ask
        // whether it actually started instead of trusting the call: reporting
        // "not available" while the game is opening is worse than saying nothing.
        final String failure = why;
        new Handler(getMainLooper()).postDelayed(() -> {
            boolean running = false;
            try {
                running = MetaActivityManager.isAppRunning(PKG_BGMI);
            } catch (Exception e) {
                Log.w("MAct", "could not ask whether the game is running", e);
            }

            if (running) {
                gameRunning = true;
                showStatus("Game started.", false);
                hideStatusSoon(2500);
            } else if (failure.isEmpty()) {
                // A launch was handed to the sandbox and it reports nothing yet.
                // It is on its way up, so say nothing rather than something
                // wrong: "game not available" over a game that is opening is
                // the worst of both answers.
                hideStatusSoon(1200);
            } else {
                showStatus("Launch failed: " + failure, true);
            }
            renderState();
        }, 3500);
    }

    private void stopGame() {
        try {
            MetaActivityManager.killAppByPkg(PKG_BGMI);
            gameRunning = false;
            showStatus("Game stopped.", false);
            renderState();
        } catch (Exception e) {
            Toast.makeText(this, "Could not stop the game", Toast.LENGTH_SHORT).show();
        }
    }

    private void openStore() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + PKG_BGMI)));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + PKG_BGMI)));
        }
    }

    // ---------------------------------------------------------------- library

    private void checkForUpdate() {
        if (checkUpdateButton != null) checkUpdateButton.setText("…");
        showStatus("Checking for updates…", false);

        LibGate.check(this, new LibGate.Listener() {
            @Override
            public void onState(LibGate.State state, String detail) {
                if (checkUpdateButton != null) checkUpdateButton.setText("Check");
                switch (state) {
                    case UP_TO_DATE:
                        showStatus("Library is up to date.", false);
                        hideStatusSoon(2200);
                        refreshLibraryLine();
                        break;
                    case UPDATE_AVAILABLE:
                        // no "later": an update is downloaded as soon as it is known
                        installUpdate(detail);
                        break;
                    case FAILED:
                        showStatus("Could not check: " + detail, true);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void installUpdate(String version) {
        busy = true;
        renderState();
        if (obbProgress != null) {
            obbProgress.setVisibility(View.VISIBLE);
            obbProgress.setIndeterminate(false);
            obbProgress.setProgress(0);
        }
        setAction("Updating…", version.isEmpty() ? "" : "Library " + version);

        LibGate.download(this, new LibGate.Listener() {
            @Override
            public void onState(LibGate.State state, String detail) {
                busy = false;
                if (obbProgress != null) obbProgress.setVisibility(View.GONE);
                if (percentText != null) percentText.setText("");
                if (state == LibGate.State.READY) {
                    showStatus("Library " + detail + " installed.", false);
                    hideStatusSoon(2500);
                } else {
                    showStatus("Update failed: " + detail, true);
                }
                refreshLibraryLine();
                renderState();
            }

            @Override
            public void onProgress(int percent) {
                if (obbProgress != null) obbProgress.setProgress(percent);
                if (percentText != null) percentText.setText(percent + "%");
                setAction("Updating… " + percent + "%", "");
            }
        });
    }

    private void refreshLibraryLine() {
        if (appVersionText == null) return;
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String version = prefs.getString(PREF_VERSION_KEY, "1.0");
        String lib = LibLoader.describe(new File(new File(getFilesDir(), "loader"), "libbgmi.so"));
        appVersionText.setText("Library " + version + " · " + lib);
    }

    // ------------------------------------------------------------- key / device

    private void startKeyCountdown() {
        final String expiry = exdate();
        if (expiry == null || expiry.trim().isEmpty() || "NULL".equalsIgnoreCase(expiry.trim())) {
            if (expiryValue != null) expiryValue.setText("No expiry");
            if (timeRemaining != null) timeRemaining.setText("--");
            return;
        }

        final long expiryMillis = parseExpiryMillis(expiry.trim());
        if (expiryValue != null) {
            expiryValue.setText(expiryMillis > 0L
                    ? new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date(expiryMillis))
                    : expiry.trim());
        }
        if (expiryMillis <= 0L) {
            if (timeRemaining != null) timeRemaining.setText("--");
            return;
        }

        final Handler handler = new Handler(getMainLooper());
        handler.post(new Runnable() {
            @Override public void run() {
                long remaining = expiryMillis - System.currentTimeMillis();
                if (remaining <= 0L) {
                    if (subscriptionStatus != null) {
                        subscriptionStatus.setText("Expired");
                        subscriptionStatus.setTextColor(Color.parseColor("#E0737C"));
                    }
                    if (timeRemaining != null) timeRemaining.setText("Expired");
                    return;
                }
                long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining);
                long days = totalSeconds / 86400L;
                long hours = (totalSeconds % 86400L) / 3600L;
                long minutes = (totalSeconds % 3600L) / 60L;

                if (subscriptionStatus != null) {
                    subscriptionStatus.setText("Active");
                    subscriptionStatus.setTextColor(Color.parseColor("#4CB98A"));
                }
                if (timeRemaining != null) {
                    timeRemaining.setText(days > 0
                            ? String.format(Locale.US, "%dd %02dh %02dm", days, hours, minutes)
                            : String.format(Locale.US, "%02dh %02dm", hours, minutes));
                }
                handler.postDelayed(this, 1000L);
            }
        });
    }

    private long parseExpiryMillis(String value) {
        try {
            long numeric = Long.parseLong(value);
            return numeric < 100000000000L ? numeric * 1000L : numeric;
        } catch (NumberFormatException ignored) {}

        String[] formats = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
                "dd-MM-yyyy HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "MMM dd, yyyy HH:mm:ss"};
        for (String format : formats) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(format, Locale.US);
                parser.setLenient(false);
                Date parsed = parser.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) {}
        }
        return -1L;
    }

    private void showDevice() {
        if (deviceValue != null) {
            String model = Build.MODEL == null ? "Android" : Build.MODEL.trim();
            deviceValue.setText(model.isEmpty() ? "This device" : model);
        }
        // a library that failed inside the game process is worth saying out loud
        String last = LibLoader.lastResult(this);
        if (last.startsWith("fail:")) {
            String detail = last.substring(5).trim();
            int cut = detail.indexOf(" — looked in");
            if (cut > 0) detail = detail.substring(0, cut);
            if (detail.length() > 80) detail = detail.substring(0, 80) + "…";
            showStatus("Library failed in game: " + detail, true);
        }
    }

    // ----------------------------------------------------------------- status

    private void showStatus(String message, boolean error) {
        if (statusToggle == null) return;
        runOnUiThread(() -> {
            statusToggle.setVisibility(View.VISIBLE);
            statusToggle.setAlpha(0f);
            statusToggle.animate().alpha(1f).setDuration(200).start();
            if (statusToggleText != null) {
                statusToggleText.setText(message);
                statusToggleText.setTextColor(Color.parseColor(error ? "#E0737C" : "#8A909B"));
            }
            if (statusLoader != null) {
                statusLoader.setVisibility(error ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void hideStatusSoon(long delay) {
        if (statusToggle == null) return;
        new Handler(getMainLooper()).postDelayed(() -> {
            statusToggle.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                statusToggle.setVisibility(View.GONE);
                if (statusLoader != null) statusLoader.setVisibility(View.GONE);
            }).start();
        }, delay);
    }

    // ------------------------------------------------------------ permissions

    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception ignored) {}
        }
    }
}
