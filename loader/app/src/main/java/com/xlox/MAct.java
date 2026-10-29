package com.xlox;

import android.Manifest;
import android.animation.ObjectAnimator;
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
    private TextView obbStatusText;
    private ProgressBar obbProgressBar;
    private Button obbRetryButton;
    private boolean launchReady = false;
    private boolean obbWorking = false;

    private boolean isObbCopied = false;
    private boolean isGameRunning = false;
    private boolean isGameInstalled = false;
    private boolean isUpdateAvailable = false;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }

        setContentView(R.layout.activity_main);

        checkPermissions();

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
        obbStatusText = findViewById(R.id.obbStatusText);
        obbProgressBar = findViewById(R.id.obbProgressBar);
        obbRetryButton = findViewById(R.id.obbRetryButton);

        daysValue = findViewById(R.id.daysBox).findViewById(R.id.timeValue);
        hoursValue = findViewById(R.id.hoursBox).findViewById(R.id.timeValue);
        minutesValue = findViewById(R.id.minutesBox).findViewById(R.id.timeValue);
        secondsValue = findViewById(R.id.secondsBox).findViewById(R.id.timeValue);
        label(daysBox, R.string.label_days);
        label(hoursBox, R.string.label_hours);
        label(minutesBox, R.string.label_minutes);
        label(secondsBox, R.string.label_seconds);
        setKeyTimeUnavailable();
        startKeyCountdown();
        startPageAnimation();

        if (obbRetryButton != null) {
            obbRetryButton.setOnClickListener(v -> {
                obbWorking = false;
                if (!hasStorageAccess()) {
                    requestStorageAccess();
                    return;
                }
                ensureObbReady();
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
                    if (subscriptionStatus != null) {
                        subscriptionStatus.setText(R.string.status_expired);
                        subscriptionStatus.setTextColor(Color.parseColor("#C98C8C"));
                    }
                    if (timeRemaining != null) timeRemaining.setText(R.string.status_expired);
                    return;
                }
                if (subscriptionStatus != null) {
                    subscriptionStatus.setText(R.string.status_active);
                    subscriptionStatus.setTextColor(Color.parseColor("#8FB3A0"));
                }
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
     * offered. Runs by itself when the screen opens; every failure is shown on
     * the OBB card with a RETRY button instead of failing silently.
     */
    private void ensureObbReady() {
        if (obbWorking || isFinishing()) return;
        obbWorking = true;
        setLaunchReady(false);

        runOnUiThread(() -> {
            if (obbCard != null) obbCard.setVisibility(View.VISIBLE);
            if (obbRetryButton != null) obbRetryButton.setVisibility(View.GONE);
            setObbStatus(getString(R.string.preparing));
            if (obbProgressBar != null) {
                obbProgressBar.setVisibility(View.VISIBLE);
                obbProgressBar.setIndeterminate(true);
            }
        });

        new Thread(() -> {
            String problem = null;
            try {
                if (!isGameInstalled) {
                    problem = "BGMI is not installed on this device";
                } else if (!hasStorageAccess()) {
                    problem = "All files access is needed to read the game files";
                    runOnUiThread(() -> {
                        if (obbRetryButton != null) {
                            obbRetryButton.setText(R.string.grant_file_access);
                            obbRetryButton.setVisibility(View.VISIBLE);
                        }
                    });
                } else if (isObbInSandbox()) {
                    isObbCopied = true;
                    runOnUiThread(this::onObbReady);
                    return;
                }
            } catch (Throwable t) {
                Log.e("MAct", "OBB check failed", t);
                problem = "Cannot read the game storage: " + brief(t);
            }

            if (problem != null) {
                final String message = problem;
                runOnUiThread(() -> onObbFailed(message));
                return;
            }
            runOnUiThread(this::startObbCopy);
        }).start();
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
        if (obbCard != null) obbCard.setVisibility(View.VISIBLE);
        setObbStatus(getString(R.string.copying_game_data));
        if (obbProgressBar != null) {
            obbProgressBar.setVisibility(View.VISIBLE);
            obbProgressBar.setIndeterminate(false);
            obbProgressBar.setMax(100);
            obbProgressBar.setProgress(0);
        }

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
                    setObbStatus(getString(R.string.copying_game_data) + " " + progress + "%");
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
        if (obbProgressBar != null) {
            obbProgressBar.setIndeterminate(false);
            obbProgressBar.setProgress(100);
        }
        setLaunchReady(true);
        if (obbCard != null) obbCard.setVisibility(View.GONE);
        updateButtonState(isGameRunning);
        updateLibState();
    }

    private void onObbFailed(String message) {
        obbWorking = false;
        setLaunchReady(false);
        String reason = message == null || message.trim().isEmpty() ? "Game data is not ready" : message;
        if (obbCard != null) obbCard.setVisibility(View.VISIBLE);
        setObbStatus(reason);
        if (obbProgressBar != null) {
            obbProgressBar.setIndeterminate(false);
            obbProgressBar.setProgress(0);
        }
        if (obbRetryButton != null) {
            obbRetryButton.setText(hasStorageAccess() ? R.string.retry_copy : R.string.grant_file_access);
            obbRetryButton.setVisibility(View.VISIBLE);
        }
        setLaunchButtonText(R.string.game_data_required);
        Log.w("MAct", "OBB not ready: " + reason);
    }

    private void setObbStatus(String text) {
        if (obbStatusText != null) obbStatusText.setText(text);
    }

    private void setLaunchReady(boolean ready) {
        launchReady = ready;
        runOnUiThread(() -> {
            if (startBgmi == null) return;
            startBgmi.setEnabled(ready);
            startBgmi.setAlpha(ready ? 1f : 0.45f);
            if (!ready) setLaunchButtonText(obbWorking ? R.string.preparing_obb : R.string.game_data_required);
        });
    }

    private boolean hasStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                return Environment.isExternalStorageManager();
            } catch (Throwable t) {
                return false;
            }
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorageAccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
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
                    deviceStatus.setTextColor(Color.parseColor("#8FB3A0"));
                }
                updateButtonState(false);
            }
        } catch (Exception ignored) {
            isGameInstalled = false;
            if (gameStatus != null) gameStatus.setText(R.string.not_installed);
            if (deviceStatus != null) {
                deviceStatus.setText(R.string.not_installed);
                deviceStatus.setTextColor(Color.parseColor("#C7A87C"));
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
            if (running) {
                setLaunchButtonText(R.string.stop_game);
                if (startBgmi != null) {
                    startBgmi.setTextColor(Color.parseColor("#C98C8C"));
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
                        startBgmi.setTextColor(Color.parseColor("#98A2AE"));
                    }
                } else if (isUpdateAvailable) {
                    setLaunchButtonText(R.string.update_available);
                    if (startBgmi != null) startBgmi.setTextColor(Color.parseColor("#0D0F12"));
                } else {
                    setLaunchButtonText(R.string.launch_game);
                    if (startBgmi != null) startBgmi.setTextColor(Color.parseColor("#0D0F12"));
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
                statusToggleText.setTextColor(isError ? Color.parseColor("#C98C8C") : Color.WHITE);
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

    /** Asks once, quietly, for the storage access the OBB copy needs. */
    private void checkPermissions() {
        if (!hasStorageAccess()) {
            requestStorageAccess();
        }
    }
}
