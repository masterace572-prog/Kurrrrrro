package com.xlox;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.xlox.utils.AppManager;
import com.xlox.utils.ObbAccess;
import com.xlox.utils.Prefs;
import com.xlox.utils.Downtwo;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import net_62v.external.MetaActivationManager;
import net_62v.external.MetaActivityManager;
import net_62v.external.MetaStorageManager;

@Obfuscate
public class MAct extends AppCompatActivity {

    private static native String exdate();

    static {
        try {
            System.loadLibrary("XloX");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    private static final String PKG_BGMI = "com.pubg.imobile";
    private static final String PREF_NAME = "com.xlox.download";
    private static final String PREF_VERSION_KEY = "version";

    // UI
    private ProgressBar downloadProgressBar;
    private TextView downloadStatusText;
    private TextView percentText;
    private View downloadBox;
    private View restartButton;
    private ProgressBar loadingCursor;
    private TextView appVersionText;
    
    private androidx.appcompat.widget.AppCompatButton startBgmi;
    private View statusToggle;
    private View statusLoader;
    private ImageView statusErrorIcon;
    private TextView statusToggleText;

    private TextView gameName;
    private TextView packageName;
    private TextView gameStatus;
    private TextView subscriptionStatus;
    private TextView expiryValue;
    private TextView timeRemaining;
    private TextView deviceValue;
    private TextView deviceStatus;

    private TextView daysValue;
    private TextView hoursValue;
    private TextView minutesValue;
    private TextView secondsValue;

    // OBB gate
    private View obbCard;
    private TextView obbTitle;
    private TextView obbStatusText;
    private ImageView obbIcon;
    private ImageView obbStateIcon;
    private ProgressBar obbProgressBar;
    private Button obbRetryButton;
    private TextView keyValue;
    private View statusDot;

    // file access states
    private static final int ACCESS_CHECKING = 0;
    private static final int ACCESS_REQUIRED = 1;
    private static final int ACCESS_DENIED = 2;
    private static final int ACCESS_COPYING = 3;
    private static final int ACCESS_READY = 4;
    private static final int ACCESS_FAILED = 5;
    private static final int ACCESS_NO_GAME = 6;
    private static final int ACCESS_NO_DATA = 7;
    private boolean accessAskedOnce = false;
    private boolean launchReady = false;
    private boolean obbWorking = false;

    private boolean licenseRejected = false;
    private String licenseReason = null;

    private boolean isObbCopied = false;
    /** Why the last filesystem probe said the data was not usable. */
    private String obbProblem = "";
    private TextView obbDetails;
    private androidx.appcompat.widget.AppCompatButton obbPickButton;

    private static final int REQ_PICK_OBB = 4711;
    private boolean isGameRunning = false;
    private boolean isGameInstalled = false;
    private boolean isUpdateAvailable = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // dark theme: light icons on the dark status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().setStatusBarColor(col(R.color.bg));
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }

        setContentView(R.layout.activity_main);

        // Download UI
        downloadBox = findViewById(R.id.downloadBox);
        downloadProgressBar = findViewById(R.id.downloadProgressBar);
        downloadStatusText = findViewById(R.id.downloadStatusText);
        percentText = findViewById(R.id.percentText);
        restartButton = findViewById(R.id.restartButton);
        loadingCursor = findViewById(R.id.loadingCursor);
        appVersionText = findViewById(R.id.appVersionText);

        // Action UI
        startBgmi = findViewById(R.id.startBgmi);

        gameName = findViewById(R.id.gameName);
        packageName = findViewById(R.id.packageName);
        gameStatus = findViewById(R.id.gameStatus);
        statusToggle = findViewById(R.id.statusToggle);
        statusLoader = findViewById(R.id.statusLoader);
        statusErrorIcon = findViewById(R.id.statusErrorIcon);
        statusToggleText = findViewById(R.id.statusToggleText);
        subscriptionStatus = findViewById(R.id.subscriptionStatus);
        expiryValue = findViewById(R.id.expiryValue);
        timeRemaining = findViewById(R.id.timeRemaining);
        deviceValue = findViewById(R.id.deviceValue);
        deviceStatus = findViewById(R.id.deviceStatus);
        if (deviceValue != null) {
            deviceValue.setText(Build.MODEL == null ? "Android device" : Build.MODEL);
        }

        obbCard = findViewById(R.id.obbCard);
        obbTitle = findViewById(R.id.obbTitle);
        obbStatusText = findViewById(R.id.obbStatusText);
        obbIcon = findViewById(R.id.obbIcon);
        obbStateIcon = findViewById(R.id.obbStateIcon);
        keyValue = findViewById(R.id.keyValue);
        statusDot = findViewById(R.id.statusDot);
        obbProgressBar = findViewById(R.id.obbProgressBar);
        obbRetryButton = findViewById(R.id.obbRetryButton);

        daysValue = findViewById(R.id.daysBox).findViewById(R.id.timeValue);
        hoursValue = findViewById(R.id.hoursBox).findViewById(R.id.timeValue);
        minutesValue = findViewById(R.id.minutesBox).findViewById(R.id.timeValue);
        secondsValue = findViewById(R.id.secondsBox).findViewById(R.id.timeValue);
        label(findViewById(R.id.daysBox), R.string.label_days);
        label(findViewById(R.id.hoursBox), R.string.label_hours);
        label(findViewById(R.id.minutesBox), R.string.label_minutes);
        label(findViewById(R.id.secondsBox), R.string.label_seconds);
        showMaskedKey();
        setKeyTimeUnavailable();
        startKeyCountdown();
        startPageAnimation();

        obbPickButton = findViewById(R.id.obbPickButton);
        if (obbPickButton != null) {
            obbPickButton.setOnClickListener(v -> pickObbFile());
        }

        obbDetails = findViewById(R.id.obbDetails);
        if (obbDetails != null) {
            obbDetails.setOnClickListener(v -> showObbReport());
        }

        if (obbRetryButton != null) {
            obbRetryButton.setOnClickListener(v -> {
                // The button always re-checks the filesystem first: if the
                // folder is readable the copy runs, otherwise the grant screen
                // is opened. That way a wrong flag can never dead-end here.
                obbWorking = false;
                new Thread(() -> {
                    AppManager.Probe probe = new AppManager(MAct.this).probeAccess(PKG_BGMI);
                    runOnUiThread(() -> {
                        if (probe.state == ObbAccess.READY) {
                            startObbCopy();
                        } else if (probe.state == ObbAccess.NEEDS_PERMISSION) {
                            accessAskedOnce = true;
                            requestStorageAccess();
                        } else {
                            obbProblem = probe.detail;
                            onObbFailed(probe.detail);
                        }
                    });
                }).start();
            });
        }

        updateGameStatus();
        updateVersionDisplay();

        // The game data has to be in place before the launch button is offered.
        ensureObbReady();

        if (restartButton != null) {
            restartButton.setOnClickListener(v -> restartApp());
        }

        if (startBgmi != null) {
            startBgmi.setOnClickListener(v -> {
                if (licenseRejected) {
                    goToLogin();
                    return;
                }
                if (!launchReady) {
                    if (obbWorking) {
                        Toast.makeText(this, "Preparing game data…", Toast.LENGTH_SHORT).show();
                    } else {
                        ensureObbReady();
                    }
                    return;
                }
                if (isUpdateAvailable) {
                    startDownload();
                } else if (!isGameInstalled) {
                    installGame();
                } else if (isGameRunning) {
                    stopGame();
                } else {
                    handleLaunchFlow();
                }
            });
        }

        // The stored key is re-checked with the panel: a revoked or expired key
        // must not be able to launch anything. Offline, the cached state stands.
        verifyLicenseInBackground();

        // Start Workflow: Update Check -> Download Files (if needed)
        startWorkflow();
    }

    private void startPageAnimation() {
        View mainLogo = findViewById(R.id.mainLogo);
        if (mainLogo != null) {
            ObjectAnimator logoFloat = ObjectAnimator.ofFloat(mainLogo, View.TRANSLATION_Y, 0f, -6f, 0f);
            logoFloat.setDuration(2600);
            logoFloat.setRepeatCount(ObjectAnimator.INFINITE);
            logoFloat.start();
        }
        View[] views = {findViewById(R.id.brandHeader), findViewById(R.id.mainCard),
                findViewById(R.id.statusToggle), findViewById(R.id.keyTimeRow)};
        long delay = 0;
        for (View view : views) {
            if (view != null) {
                view.setAlpha(0f);
                view.setTranslationY(34f);
                view.animate().alpha(1f).translationY(0f).setDuration(560).setStartDelay(delay).start();
                delay += 110;
            }
        }
    }

    /**
     * Quietly asks the panel whether this device's key is still good.
     *
     * Deliberately lenient: only an explicit rejection from the panel changes
     * anything, so a flaky connection never locks a paying user out.
     */
    private void verifyLicenseInBackground() {
        final String key = new Prefs(this).getSt("USER", "");
        if (key == null || key.trim().isEmpty()) return;

        new Thread(() -> {
            String result;
            try {
                result = LogAct.Check(this, key);
            } catch (Throwable t) {
                return;                       // offline / native hiccup: keep cached state
            }
            if (result == null || result.trim().isEmpty()) return;

            final String answer = result.trim();
            if ("OK".equalsIgnoreCase(answer)) {
                runOnUiThread(() -> {
                    licenseRejected = false;
                    licenseReason = null;
                });
            } else if (looksLikeRejection(answer)) {
                runOnUiThread(() -> onLicenseRejected(answer));
            }
        }).start();
    }

    /** The panel answers `OK` on success and a short reason otherwise. */
    private boolean looksLikeRejection(String answer) {
        String a = answer.toLowerCase(Locale.US);
        return a.contains("expired") || a.contains("blocked") || a.contains("not registered")
                || a.contains("max device") || a.contains("maintenance") || a.contains("bad parameter");
    }

    private void onLicenseRejected(String reason) {
        licenseRejected = true;
        licenseReason = reason;
        setLicenseState(false);
        if (subscriptionStatus != null) {
            subscriptionStatus.setText(shortReason(reason));
        }
        showStatusToggle("LICENSE CHECK FAILED — " + reason, true);
        setLaunchButtonText(R.string.login_again);
    }

    private String shortReason(String reason) {
        String r = reason == null ? "" : reason.trim();
        if (r.length() > 18) r = r.substring(0, 18).trim() + "…";
        return r.isEmpty() ? getString(R.string.status_expired) : r;
    }

    private void goToLogin() {
        try {
            new Prefs(this).setSt("USER", "");
            Intent intent = new Intent(this, LogAct.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } catch (Throwable t) {
            Log.e("MAct", "cannot return to the login screen", t);
        }
    }

    /** The panel key, quoted back as head •••• tail so a screenshot leaks nothing. */
    private void showMaskedKey() {
        if (keyValue == null) return;
        String key = new Prefs(this).getSt("USER", "");
        keyValue.setText(maskKey(key));
    }

    private String maskKey(String key) {
        if (key == null || key.trim().isEmpty()) return getString(R.string.dash);
        key = key.trim();
        int show = Math.min(4, key.length() / 4);
        if (show == 0) return "••••";
        return key.substring(0, show) + " •••• " + key.substring(key.length() - show);
    }

    /** @color lookup without repeating ContextCompat everywhere. */
    private int col(int colorRes) {
        return ContextCompat.getColor(this, colorRes);
    }

    /** Keeps the little status dot in step with the chip text. */
    private void tintStatusDot(int colorRes) {
        if (statusDot == null) return;
        try {
            statusDot.getBackground().mutate()
                    .setTint(ContextCompat.getColor(this, colorRes));
        } catch (Throwable ignored) {
        }
    }

    private void setKeyTimeUnavailable() {
        if (daysValue != null) daysValue.setText(R.string.dash);
        if (hoursValue != null) hoursValue.setText(R.string.dash);
        if (minutesValue != null) minutesValue.setText(R.string.dash);
        if (secondsValue != null) secondsValue.setText(R.string.dash);
    }

    private void startKeyCountdown() {
        final String expiry = exdate();
        if (expiry == null || expiry.trim().isEmpty() || "NULL".equalsIgnoreCase(expiry.trim())) {
            if (expiryValue != null) expiryValue.setText(R.string.dash);
            if (timeRemaining != null) timeRemaining.setText(R.string.dash);
            return;
        }

        final long expiryMillis = parseExpiryMillis(expiry.trim());
        if (expiryValue != null) {
            if (expiryMillis > 0L) {
                expiryValue.setText(new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                        .format(new Date(expiryMillis)));
            } else {
                expiryValue.setText(expiry.trim());
            }
        }
        if (expiryMillis <= 0L) {
            if (timeRemaining != null) timeRemaining.setText(R.string.dash);
            return;
        }

        final Handler timerHandler = new Handler(getMainLooper());
        final Runnable timer = new Runnable() {
            @Override public void run() {
                long remaining = expiryMillis - System.currentTimeMillis();
                if (remaining <= 0L) {
                    setTimeValues(0, 0, 0, 0);
                    setLicenseState(false);
                    if (timeRemaining != null) timeRemaining.setText(R.string.status_expired);
                    return;
                }
                setLicenseState(true);
                long totalSeconds = TimeUnit.MILLISECONDS.toSeconds(remaining);
                long days = totalSeconds / 86400L;
                long hours = (totalSeconds % 86400L) / 3600L;
                long minutes = (totalSeconds % 3600L) / 60L;
                long seconds = totalSeconds % 60L;
                setTimeValues(days, hours, minutes, seconds);
                timerHandler.postDelayed(this, 1000L);
            }
        };
        timer.run();
    }

    /** Chip text, chip colour and the little dot always move together. */
    private void setLicenseState(boolean active) {
        int labelRes = active ? R.string.status_active : R.string.status_expired;
        int colorRes = active ? R.color.success : R.color.danger;
        if (subscriptionStatus != null) {
            subscriptionStatus.setText(labelRes);
            subscriptionStatus.setTextColor(ContextCompat.getColor(this, colorRes));
        }
        tintStatusDot(colorRes);
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

    @SuppressLint("SetTextI18n")
    private void setTimeValues(long days, long hours, long minutes, long seconds) {
        if (daysValue != null) daysValue.setText(String.format(Locale.US, "%02d", days));
        if (hoursValue != null) hoursValue.setText(String.format(Locale.US, "%02d", hours));
        if (minutesValue != null) minutesValue.setText(String.format(Locale.US, "%02d", minutes));
        if (secondsValue != null) secondsValue.setText(String.format(Locale.US, "%02d", seconds));
        if (timeRemaining != null) {
            timeRemaining.setText(String.format(Locale.US, "%dd %02dh %02dm", days, hours, minutes));
        }
    }

    private void updateVersionDisplay() {
        String localVersion = Downtwo.installedVersion(this);
        if (appVersionText != null) {
            boolean installed = !"0.0".equals(localVersion);
            appVersionText.setText(installed ? "v" + localVersion : getString(R.string.version_unknown));
        }
    }

    private void startWorkflow() {
        runOnUiThread(() -> {
            if (downloadBox != null) downloadBox.setVisibility(View.GONE);
            if (restartButton != null) restartButton.setVisibility(View.VISIBLE);
            if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
        });
        isUpdateAvailable = false;
        checkForUpdates(false);
    }

    /**
     * Asks the panel which lib version is live.
     * @param announceOnly show a toast when an update is found
     */
    private void checkForUpdates(final boolean announceOnly) {
        Downtwo checker = new Downtwo(this, result -> {
            switch (result.status) {
                case UPDATE_AVAILABLE:
                case ALREADY_NOTIFIED: { // still offer the button, just no second toast
                    final boolean fresh = result.status == Downtwo.Status.UPDATE_AVAILABLE;
                    isUpdateAvailable = true;
                    runOnUiThread(() -> {
                        updateButtonState(isGameRunning);
                        setLaunchButtonText(R.string.update_available);
                        if (announceOnly && fresh && !isFinishing()) {
                            Toast.makeText(MAct.this,
                                    "New lib v" + result.version + " is available — tap UPDATE to install",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
                }
                case ERROR:
                    // Unreachable update server must never block the loader.
                    Log.w("MAct", "update check failed: " + result.message);
                    isUpdateAvailable = false;
                    runOnUiThread(() -> {
                        updateButtonState(isGameRunning);
                        if (announceOnly && !isFinishing()) {
                            Toast.makeText(MAct.this, "Update check failed: " + result.message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
                default:
                    isUpdateAvailable = false;
                    runOnUiThread(() -> updateButtonState(isGameRunning));
                    break;
            }
        });
        checker.execute(Downtwo.Link(), Downtwo.MODE_CHECK);
    }

    private void startDownload() {
        runOnUiThread(() -> {
            if (downloadBox != null) downloadBox.setVisibility(View.VISIBLE);
            setLaunchButtonText(R.string.preparing);
            if (downloadStatusText != null) downloadStatusText.setText(R.string.preparing);
            if (percentText != null) percentText.setText("0%");
            if (downloadProgressBar != null) {
                downloadProgressBar.setVisibility(View.VISIBLE);
                downloadProgressBar.setIndeterminate(true);
                downloadProgressBar.setMax(100);
                downloadProgressBar.setProgress(0);
            }
            if (restartButton != null) restartButton.setVisibility(View.GONE);
            if (loadingCursor != null) loadingCursor.setVisibility(View.VISIBLE);
        });

        Downtwo task = new Downtwo(this, result -> {
            if (!result.isSuccess()) {
                // Surface the real reason instead of failing silently, and let the
                // user retry by tapping the button again.
                isUpdateAvailable = true;
                final String message = result.message.isEmpty() ? "Update failed" : result.message;
                Log.e("MAct", "update failed: " + message);
                runOnUiThread(() -> {
                    if (downloadStatusText != null) downloadStatusText.setText(message);
                    if (percentText != null) percentText.setText(R.string.failed);
                    if (downloadProgressBar != null) {
                        downloadProgressBar.setIndeterminate(false);
                        downloadProgressBar.setProgress(0);
                    }
                    if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
                    if (restartButton != null) restartButton.setVisibility(View.VISIBLE);
                    setLaunchButtonText(R.string.retry);
                    if (!isFinishing()) {
                        Toast.makeText(MAct.this, message, Toast.LENGTH_LONG).show();
                    }
                });
                return;
            }

            runOnUiThread(() -> {
                isUpdateAvailable = false;
                if (downloadStatusText != null) downloadStatusText.setText(R.string.done);
                if (percentText != null) percentText.setText("100%");
                if (downloadProgressBar != null) {
                    downloadProgressBar.setIndeterminate(false);
                    downloadProgressBar.setProgress(100);
                }
                if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
                updateVersionDisplay();
                setLaunchButtonText(isGameInstalled ? R.string.launch_game : R.string.install_game);
                if (restartButton != null) {
                    restartButton.setVisibility(View.VISIBLE);
                    restartButton.setAlpha(0f);
                    restartButton.animate().alpha(1f).setDuration(500).start();
                }
            });
        });

        task.setProgressListener((percent, stage) -> runOnUiThread(() -> {
            if (downloadStatusText != null && stage != null && !stage.isEmpty()) {
                downloadStatusText.setText(stage);
            }
            if (downloadProgressBar != null) {
                if (percent < 0) {
                    downloadProgressBar.setIndeterminate(true);
                } else {
                    downloadProgressBar.setIndeterminate(false);
                    downloadProgressBar.setMax(100);
                    downloadProgressBar.setProgress(percent);
                }
            }
            if (percentText != null) {
                percentText.setText(percent < 0 ? "…" : percent + "%");
            }
        }));

        task.execute(Downtwo.Link(), Downtwo.MODE_DOWNLOAD);
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Runtime.getRuntime().exit(0);
        }
    }

    /* ------------------------------------------------------------ OBB gate */

    /**
     * Makes sure the game's OBB sits inside the sandbox before launch is
     * offered. Runs by itself when the screen opens and again whenever the app
     * comes back to the foreground (so granting access in Settings is all the
     * user has to do). Every state — waiting for access, copying, failed — is
     * shown on one card with a single action.
     */
    private void ensureObbReady() {
        if (obbWorking || isFinishing()) return;
        obbWorking = true;
        setLaunchReady(false);
        runOnUiThread(() -> obbState(ACCESS_CHECKING));

        new Thread(() -> {
            boolean installed = isGameInstalled;

            if (!installed) {
                runOnUiThread(() -> {
                    obbWorking = false;
                    obbState(ACCESS_NO_GAME);
                });
                return;
            }

            // Already in the sandbox (copied earlier, or handed over through
            // the picker)? Then nothing else matters.
            if (isObbInSandbox()) {
                runOnUiThread(() -> {
                    obbWorking = false;
                    isObbCopied = true;
                    onObbReady();
                });
                return;
            }

            // Ask the filesystem, not the permission flag: a folder that lists
            // OBBs is copyable even on the ROMs that misreport the grant.
            AppManager.Probe probe = new AppManager(this).probeAccess(PKG_BGMI);
            Log.i("MAct", "OBB gate: all-files flag=" + hasStorageAccess()
                    + ", filesystem state=" + probe.state + " (" + probe.detail + ")");

            if (probe.state != ObbAccess.READY) {
                boolean needsPermission = probe.state == ObbAccess.NEEDS_PERMISSION;
                boolean askedBefore = accessAskedOnce;
                if (needsPermission) accessAskedOnce = true;
                obbProblem = probe.detail;
                runOnUiThread(() -> {
                    obbWorking = false;
                    // No auto-jump to the Settings switch any more: since
                    // Android 11 that switch does not unlock /Android/obb, so
                    // sending people there was a dead end. The card offers the
                    // file picker first and the switch as a secondary action.
                    obbState(needsPermission
                            ? (askedBefore ? ACCESS_DENIED : ACCESS_REQUIRED)
                            : ACCESS_NO_DATA);
                });
                return;
            }

            runOnUiThread(this::startObbCopy);
        }).start();
    }

    /**
     * One place that draws the file-access / game-data card, so the title, the
     * icon and the single action can never disagree with each other.
     */
    private void obbState(int state) {
        if (obbCard == null) return;
        obbCard.setVisibility(View.VISIBLE);
        if (obbProgressBar != null) {
            obbProgressBar.setVisibility(View.GONE);
            obbProgressBar.setIndeterminate(false);
        }
        if (obbStateIcon != null) obbStateIcon.setVisibility(View.GONE);
        if (obbRetryButton != null) obbRetryButton.setVisibility(View.GONE);
        if (obbIcon != null) {
            obbIcon.setImageResource(R.drawable.ic_folder);
            obbIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary));
        }
        if (obbRetryButton != null) obbRetryButton.setText(R.string.retry_copy);
        if (obbDetails != null) obbDetails.setVisibility(View.GONE);
        if (obbPickButton != null) obbPickButton.setVisibility(View.GONE);

        switch (state) {
            case ACCESS_CHECKING:
                setCardText(R.string.game_data_title, getString(R.string.checking_access));
                if (obbProgressBar != null) {
                    obbProgressBar.setVisibility(View.VISIBLE);
                    obbProgressBar.setIndeterminate(true);
                }
                break;

            case ACCESS_REQUIRED:
                setCardText(R.string.access_required_title, getString(R.string.access_required_body));
                tintCardIcon(R.drawable.ic_shield_alert, R.color.warning);
                if (obbDetails != null) obbDetails.setVisibility(View.VISIBLE);
                if (obbPickButton != null) obbPickButton.setVisibility(View.VISIBLE);
                if (obbRetryButton != null) {
                    obbRetryButton.setText(R.string.allow_access);
                    obbRetryButton.setVisibility(View.VISIBLE);
                }
                break;

            case ACCESS_DENIED:
                setCardText(R.string.access_denied_title, getString(R.string.access_denied_body));
                tintCardIcon(R.drawable.ic_shield_alert, R.color.warning);
                if (obbDetails != null) obbDetails.setVisibility(View.VISIBLE);
                if (obbPickButton != null) obbPickButton.setVisibility(View.VISIBLE);
                if (obbRetryButton != null) {
                    obbRetryButton.setText(R.string.open_settings);
                    obbRetryButton.setVisibility(View.VISIBLE);
                }
                break;

            case ACCESS_COPYING:
                setCardText(R.string.copying_game_data, getString(R.string.copying_game_data));
                if (obbProgressBar != null) {
                    obbProgressBar.setVisibility(View.VISIBLE);
                    obbProgressBar.setIndeterminate(false);
                    obbProgressBar.setMax(100);
                    obbProgressBar.setProgress(0);
                }
                break;

            case ACCESS_READY:
                setCardText(R.string.game_data_ready, getString(R.string.access_granted));
                if (obbStateIcon != null) obbStateIcon.setVisibility(View.VISIBLE);
                break;

            case ACCESS_FAILED:
                tintCardIcon(R.drawable.ic_alert, R.color.danger);
                if (obbDetails != null) obbDetails.setVisibility(View.VISIBLE);
                if (obbPickButton != null) obbPickButton.setVisibility(View.VISIBLE);
                if (obbRetryButton != null) {
                    obbRetryButton.setText(R.string.retry_copy);
                    obbRetryButton.setVisibility(View.VISIBLE);
                }
                break;

            case ACCESS_NO_DATA:
                setCardText(R.string.game_data_title, withProblem(getString(R.string.game_data_missing)));
                tintCardIcon(R.drawable.ic_folder, R.color.warning);
                if (obbDetails != null) obbDetails.setVisibility(View.VISIBLE);
                if (obbPickButton != null) obbPickButton.setVisibility(View.VISIBLE);
                if (obbRetryButton != null) {
                    obbRetryButton.setText(R.string.retry_copy);
                    obbRetryButton.setVisibility(View.VISIBLE);
                }
                break;

            case ACCESS_NO_GAME:
                setCardText(R.string.game_data_title, getString(R.string.not_installed));
                tintCardIcon(R.drawable.ic_alert, R.color.warning);
                break;
        }
    }

    /** The message plus, when there is one, the path that was actually checked. */
    private String withProblem(String message) {
        if (obbProblem == null || obbProblem.trim().isEmpty()) return message;
        return message + "\n" + obbProblem;
    }

    private void setCardText(int titleRes, String status) {
        if (obbTitle != null) obbTitle.setText(titleRes);
        if (obbStatusText != null) obbStatusText.setText(status);
    }

    private void tintCardIcon(int drawableRes, int colorRes) {
        if (obbIcon == null) return;
        obbIcon.setImageResource(drawableRes);
        obbIcon.setColorFilter(ContextCompat.getColor(this, colorRes));
    }

    /**
     * The report the card's "Show details" opens. Built off the UI thread
     * because it touches the filesystem and the sandbox API, and copyable so it
     * can be sent on as-is.
     */
    private void showObbReport() {
        Toast.makeText(this, R.string.checking, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String report;
            try {
                report = new AppManager(this).diagnostics(PKG_BGMI);
            } catch (Throwable t) {
                report = "Could not build the report: " + t;
            }
            final String text = report;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.obb_details_title)
                        .setMessage(text)
                        .setPositiveButton(R.string.copy_report, (d, w) -> {
                            android.content.ClipboardManager cb =
                                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (cb != null) {
                                cb.setPrimaryClip(android.content.ClipData.newPlainText("XLOX report", text));
                                Toast.makeText(this, R.string.report_copied, Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton(R.string.close, null)
                        .show();
            });
        }, "obb-report").start();
    }

    /**
     * Opens the system file picker for the game's .obb.
     *
     * On Android 11+ this is the only road left: /Android/obb belongs to the
     * other app, and neither All files access nor the folder picker can reach
     * into it. A file the user picks comes with its own read grant, and the
     * sandbox folder is ours to write.
     */
    private void pickObbFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.pick_obb_title));
        try {
            startActivityForResult(intent, REQ_PICK_OBB);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.pick_obb_hint, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_OBB || resultCode != RESULT_OK || data == null) return;

        java.util.List<Uri> uris = new java.util.ArrayList<>();
        if (data.getClipData() != null) {
            android.content.ClipData clip = data.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) uris.add(uri);
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;

        obbWorking = true;
        obbState(ACCESS_COPYING);
        new AppManager(this).copyFromUris(uris, PKG_BGMI, new AppManager.CopyCallback() {
            @Override
            public void onCopyCompleted(boolean success) {
                runOnUiThread(() -> {
                    obbWorking = false;
                    if (success) {
                        onObbReady();
                    } else {
                        onObbFailed(getString(R.string.obb_pick_failed));
                    }
                });
            }

            @Override
            public void onProgressUpdate(int progress) {
                runOnUiThread(() -> {
                    if (obbStatusText != null) {
                        obbStatusText.setText(getString(R.string.copying_game_data) + " · " + progress + "%");
                    }
                    if (obbProgressBar != null) obbProgressBar.setProgress(progress);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    obbWorking = false;
                    onObbFailed(message);
                });
            }
        });
    }

    private boolean isObbInSandbox() {
        try {
            return new AppManager(this).checkObbContainer(PKG_BGMI);
        } catch (Throwable t) {
            Log.w("MAct", "OBB container check failed: " + t.getMessage());
            return false;
        }
    }

    private void startObbCopy() {
        if (isFinishing()) return;
        obbWorking = true;
        obbState(ACCESS_COPYING);

        AppManager appManager = new AppManager(this);
        appManager.copyObbFolderAsync(PKG_BGMI, new AppManager.CopyCallback() {
            @Override
            public void onCopyCompleted(boolean success) {
                runOnUiThread(() -> {
                    if (success) {
                        onObbReady();
                    } else {
                        onObbFailed("Copying the game data failed");
                    }
                });
            }

            @Override
            public void onProgressUpdate(int progress) {
                runOnUiThread(() -> {
                    if (obbStatusText != null) {
                        obbStatusText.setText(getString(R.string.copying_game_data)
                                + " · " + Math.max(0, Math.min(100, progress)) + "%");
                    }
                    if (obbProgressBar != null) obbProgressBar.setProgress(progress);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> onObbFailed(message));
            }
        }, false);
    }

    private void onObbReady() {
        obbWorking = false;
        isObbCopied = true;
        obbState(ACCESS_READY);
        setLaunchReady(true);
        updateButtonState(isGameRunning);
        updateLibState();

        // the card has said what it needed to say
        new Handler(getMainLooper()).postDelayed(() -> {
            if (obbCard != null && launchReady) {
                obbCard.animate().alpha(0f).setDuration(260).withEndAction(() -> {
                    obbCard.setVisibility(View.GONE);
                    obbCard.setAlpha(1f);
                }).start();
            }
        }, 2200);
    }

    private void onObbFailed(String message) {
        obbWorking = false;
        setLaunchReady(false);
        String reason = message == null || message.trim().isEmpty() ? "Game data is not ready" : message;
        obbState(ACCESS_FAILED);
        if (obbStatusText != null) obbStatusText.setText(withProblem(reason));
        setLaunchButtonText(R.string.game_data_required);
        Log.w("MAct", "OBB not ready: " + reason);
    }

    private void setLaunchReady(boolean ready) {
        launchReady = ready;
        runOnUiThread(() -> {
            if (startBgmi == null) return;
            startBgmi.setEnabled(ready || licenseRejected);
            startBgmi.setAlpha(ready || licenseRejected ? 1f : 0.45f);
            if (licenseRejected) {
                setLaunchButtonText(R.string.login_again);
            } else if (!ready) {
                setLaunchButtonText(obbWorking ? R.string.preparing_obb : R.string.game_data_required);
            }
        });
    }

    /**
     * Whether this ROM reports the "all files" grant. Only used to word a
     * message — the OBB gate itself goes by the filesystem (see
     * AppManager.probeAccess), because this answer is not reliable on every
     * device and a false negative used to stop the copy entirely.
     */
    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return Environment.isExternalStorageManager();
            } catch (Throwable t) {
                Log.w("MAct", "cannot read the all-files state", t);
                return false;
            }
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Sends the user straight to the one switch the OBB copy needs. */
    private void requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                return;
            } catch (Throwable t) {
                // some ROMs do not publish the per-app screen; fall back to the list
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    return;
                } catch (Throwable ignored) {
                }
            }
        }
        try {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        } catch (Throwable t) {
            Log.e("MAct", "cannot open the storage settings", t);
            Toast.makeText(this, "Open Settings → Apps → XLOX LOADER → Permissions", Toast.LENGTH_LONG).show();
        }
    }

    private static String brief(Throwable t) {
        String m = t == null ? null : t.getMessage();
        if (m == null || m.trim().isEmpty()) return t == null ? "unknown error" : t.getClass().getSimpleName();
        return m.length() > 90 ? m.substring(0, 90) + "…" : m;
    }

    /** Shown when the panel has no lib yet — launch would fail without one. */
    private void updateLibState() {
        if (!Downtwo.hasInstalledLib(this) && !isUpdateAvailable) {
            setLaunchButtonText(R.string.no_lib_installed);
        }
    }

    private void updateGameStatus() {
        isGameInstalled = false;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(PKG_BGMI, 0);
            if (packageInfo != null) {
                isGameInstalled = true;
                if (gameName != null) gameName.setText(R.string.game_name);
                if (packageName != null) packageName.setText(R.string.game_package);
                if (gameStatus != null) gameStatus.setText(R.string.installed);
                if (deviceStatus != null) {
                    deviceStatus.setText(getString(R.string.this_device));
                    deviceStatus.setTextColor(col(R.color.success));
                }
                updateButtonState(false);
            }
        } catch (Exception ignored) {
            isGameInstalled = false;
            if (gameStatus != null) gameStatus.setText(R.string.not_installed);
            if (deviceStatus != null) {
                deviceStatus.setText(R.string.not_installed);
                deviceStatus.setTextColor(col(R.color.warning));
            }
            setLaunchButtonText(R.string.install_game);
        }
    }

    /** Button captions live in strings.xml so the text stays consistent. */
    private void setLaunchButtonText(@StringRes int resId) {
        if (startBgmi != null) startBgmi.setText(resId);
    }

    private void label(View box, int resId) {
        if (box == null) return;
        TextView view = box.findViewById(R.id.timeLabel);
        if (view != null) view.setText(resId);
    }

    private void updateButtonState(boolean running) {
        isGameRunning = running;
        runOnUiThread(() -> {
            if (licenseRejected && !running) {
                // a dead key outranks every other button state: the only way
                // forward is signing in again
                setLaunchButtonText(R.string.login_again);
                if (startBgmi != null) {
                    startBgmi.setEnabled(true);
                    startBgmi.setAlpha(1f);
                    startBgmi.setTextColor(col(R.color.danger));
                    startBgmi.setBackgroundResource(R.drawable.bg_btn_quiet);
                }
                return;
            }
            if (running) {
                setLaunchButtonText(R.string.stop_game);
                if (startBgmi != null) {
                    startBgmi.setTextColor(col(R.color.danger));
                }
                if (startBgmi != null) {
                    startBgmi.setBackgroundResource(R.drawable.bg_btn_stop);
                }
                if (downloadBox != null) {
                    downloadBox.setVisibility(View.VISIBLE);
                    if (downloadStatusText != null) downloadStatusText.setText(R.string.game_running);
                    if (percentText != null) percentText.setText(R.string.active_short);
                    if (downloadProgressBar != null) {
                        downloadProgressBar.setIndeterminate(true);
                        downloadProgressBar.setProgress(100);
                    }
                    if (restartButton != null) restartButton.setVisibility(View.GONE);
                }
            } else {
                if (!launchReady) {
                    setLaunchButtonText(obbWorking ? R.string.preparing_obb : R.string.game_data_required);
                    if (startBgmi != null) {
                        startBgmi.setEnabled(false);
                        startBgmi.setAlpha(0.45f);
                        startBgmi.setTextColor(col(R.color.text_secondary));
                    }
                } else if (isUpdateAvailable) {
                    setLaunchButtonText(R.string.update_available);
                    if (startBgmi != null) startBgmi.setTextColor(col(R.color.on_action));
                } else {
                    setLaunchButtonText(R.string.launch_game);
                    if (startBgmi != null) startBgmi.setTextColor(col(R.color.on_action));
                }
                if (startBgmi != null) {
                    startBgmi.setBackgroundResource(R.drawable.bg_btn_primary);
                }
                if (downloadBox != null) {
                    new Handler().postDelayed(() -> {
                        if (!isGameRunning && !isUpdateAvailable) downloadBox.setVisibility(View.GONE);
                    }, 1000);
                }
            }
        });
    }

    private void handleLaunchFlow() {
        if (!launchReady) {
            ensureObbReady();
            showStatusToggle("PREPARING GAME DATA…", false);
            return;
        }

        try {
            if (!MetaActivationManager.getActivationStatus()) {
                showStatusToggle("LAUNCHING FAILED — sandbox is not active", true);
                return;
            }
        } catch (Exception e) {
            showStatusToggle("LAUNCHING FAILED — " + brief(e), true);
            return;
        }

        File libFile = new File(getFilesDir(), "loader/libbgmi.so");
        if (!libFile.exists()) {
            if (Downtwo.hasInstalledLib(this)) {
                showStatusToggle("LIB INCOMPLETE — tap UPDATE to reinstall", true);
                isUpdateAvailable = true;
                updateButtonState(isGameRunning);
            } else {
                showStatusToggle("NO LIB INSTALLED — publish one in the panel", true);
                setLaunchButtonText(R.string.no_lib_installed);
            }
            return;
        }

        if (!isObbCopied) {
            ensureObbReady();
        } else {
            showStatusToggle("LAUNCHING BGMI", false);
            new Handler().postDelayed(() -> {
                hideStatusToggle();
                launchGame();
            }, 2500);
        }
    }

    private void showStatusToggle(String message, boolean isError) {
        if (statusToggle == null) return;
        
        runOnUiThread(() -> {
            statusToggle.setVisibility(View.VISIBLE);
            statusToggle.setAlpha(0f);
            statusToggle.animate().alpha(1f).setDuration(300).start();
            
            if (statusToggleText != null) {
                statusToggleText.setText(message);
                statusToggleText.setTextColor(col(isError ? R.color.danger : R.color.text_primary));
            }
            
            if (statusLoader != null) {
                statusLoader.setVisibility(isError ? View.GONE : View.VISIBLE);
            }
            
            if (statusErrorIcon != null) {
                statusErrorIcon.setVisibility(isError ? View.VISIBLE : View.GONE);
            }
            
            if (isError) {
                new Handler().postDelayed(this::hideStatusToggle, 3000);
            }
        });
    }

    private void hideStatusToggle() {
        if (statusToggle == null) return;
        
        runOnUiThread(() -> {
            statusToggle.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                statusToggle.setVisibility(View.GONE);
            }).start();
        });
    }

    private void launchGame() {
        try {
            Intent intent = MetaActivityManager.obtainSplashLaunchIntent(PKG_BGMI, this);
            if (intent != null) {
                startActivity(intent);
                updateButtonState(true);
            }
        } catch (Exception e) {
            Log.e("MAct", "Launch error", e);
        }
    }

    private void stopGame() {
        try {
            MetaActivityManager.killAppByPkg(PKG_BGMI);
            updateButtonState(false);
        } catch (Exception e) {
            Toast.makeText(this, "Stop Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void installGame() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + PKG_BGMI));
            startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://play.google.com/store/apps/details?id=" + PKG_BGMI));
            startActivity(intent);
        }
    }

    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.e("MAct", "Unable to open social link", e);
        }
    }

    /* ------------------------------------------------------- permissions */

    @Override
    protected void onResume() {
        super.onResume();
        // coming back from the "all files access" settings screen
        if (!launchReady && !obbWorking) {
            ensureObbReady();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (!launchReady && !obbWorking) ensureObbReady();
    }

}
