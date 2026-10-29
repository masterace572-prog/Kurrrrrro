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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.xlox.utils.AppManager;
import com.xlox.utils.Downtwo;
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
    
    private TextView startText;
    private ImageView actionIcon;
    private View startBgmi;
    private View startFB;
    private View fbCard;
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

    private View obbButton;
    private TextView checkUpdateButton;
    private TextView downloadSubText;
    private TextView systemReady;

    /** Version the panel/mirror is offering, when it is newer than ours. */
    private String pendingLibVersion = "";
    private boolean libCheckRunning = false;
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
        checkUpdateButton = findViewById(R.id.checkUpdateButton);
        downloadSubText = findViewById(R.id.downloadSubText);
        systemReady = findViewById(R.id.systemReady);

        // Action UI
        startText = findViewById(R.id.startText);
        actionIcon = findViewById(R.id.actionIcon);
        startBgmi = findViewById(R.id.startBgmi);
        startFB = findViewById(R.id.startFB);
        fbCard = findViewById(R.id.fbCard);

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

        obbButton = findViewById(R.id.obbButton);

        daysValue = findViewById(R.id.daysBox).findViewById(R.id.timeValue);
        hoursValue = findViewById(R.id.hoursBox).findViewById(R.id.timeValue);
        minutesValue = findViewById(R.id.minutesBox).findViewById(R.id.timeValue);
        secondsValue = findViewById(R.id.secondsBox).findViewById(R.id.timeValue);
        ((TextView) findViewById(R.id.daysBox).findViewById(R.id.timeLabel)).setText("DAYS");
        ((TextView) findViewById(R.id.hoursBox).findViewById(R.id.timeLabel)).setText("HOURS");
        ((TextView) findViewById(R.id.minutesBox).findViewById(R.id.timeLabel)).setText("MINUTES");
        ((TextView) findViewById(R.id.secondsBox).findViewById(R.id.timeLabel)).setText("SECONDS");
        setKeyTimeUnavailable();
        startKeyCountdown();
        startPageAnimation();

        checkObbApplied();
        updateGameStatus();
        updateVersionDisplay();

        if (obbButton != null) {
            obbButton.setOnClickListener(v -> openExternalUrl("https://www.facebook.com/"));
        }

        if (restartButton != null) {
            restartButton.setOnClickListener(v -> restartApp());
        }

        if (checkUpdateButton != null) {
            checkUpdateButton.setOnClickListener(v -> checkForLibUpdate(true));
        }

        if (startBgmi != null) {
            startBgmi.setOnClickListener(v -> {
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

        if (startFB != null) {
            startFB.setOnClickListener(v -> openExternalUrl("https://twitter.com/"));
        }

        // Start Workflow: Update Check -> Download Files (if needed)
        startWorkflow();

        // The lib is updated outside the APK, so it has to be checked on every
        // start. Without this the app only ever looked for the file, never for a
        // newer one — the only way to get an update was to wipe the app data.
        checkForLibUpdate(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        surfaceLastLoadResult();
    }

    /**
     * The game runs in its own process, so a failure to load the library there
     * cannot reach this screen directly. LibLoader drops a line into
     * loader/load.log and this is where the user gets to read it.
     */
    private void surfaceLastLoadResult() {
        String last = LibLoader.lastResult(this);
        if (!last.startsWith("fail:")) return;

        String detail = last.substring(5).trim();
        // keep the banner to something the layout can hold
        int cut = detail.indexOf(" — looked in");
        if (cut > 0) detail = detail.substring(0, cut);
        if (detail.length() > 90) detail = detail.substring(0, 90) + "…";

        String text = "LIB LOAD FAILED · " + detail;
        showStatusToggle(text, true);
        Log.e("Load", "game process reported: " + last);
    }

    private void startPageAnimation() {
        View logoGlow = findViewById(R.id.logoGlow);
        View mainLogo = findViewById(R.id.mainLogo);
        if (mainLogo != null) {
            ObjectAnimator logoFloat = ObjectAnimator.ofFloat(mainLogo, View.TRANSLATION_Y, 0f, -6f, 0f);
            logoFloat.setDuration(2600);
            logoFloat.setRepeatCount(ObjectAnimator.INFINITE);
            logoFloat.start();
        }
        if (logoGlow != null) {
            ObjectAnimator glowRotation = ObjectAnimator.ofFloat(logoGlow, View.ROTATION, 0f, 360f);
            glowRotation.setDuration(7000);
            glowRotation.setInterpolator(new LinearInterpolator());
            glowRotation.setRepeatCount(ObjectAnimator.INFINITE);
            glowRotation.start();
            logoGlow.animate().translationY(-5f).setDuration(1300)
                    .withEndAction(() -> logoGlow.animate().translationY(0f).setDuration(1300).start()).start();
        }
        View[] views = {findViewById(R.id.brandHeader), findViewById(R.id.mainCard),
                findViewById(R.id.statusToggle), findViewById(R.id.keyTimeRow), findViewById(R.id.fbCard)};
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
        if (daysValue != null) daysValue.setText("--");
        if (hoursValue != null) hoursValue.setText("--");
        if (minutesValue != null) minutesValue.setText("--");
        if (secondsValue != null) secondsValue.setText("--");
    }

    private void startKeyCountdown() {
        final String expiry = exdate();
        if (expiry == null || expiry.trim().isEmpty() || "NULL".equalsIgnoreCase(expiry.trim())) {
            if (expiryValue != null) expiryValue.setText("--");
            if (timeRemaining != null) timeRemaining.setText("--");
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
            if (timeRemaining != null) timeRemaining.setText("--");
            return;
        }

        final Handler timerHandler = new Handler(getMainLooper());
        final Runnable timer = new Runnable() {
            @Override public void run() {
                long remaining = expiryMillis - System.currentTimeMillis();
                if (remaining <= 0L) {
                    setTimeValues(0, 0, 0, 0);
                    if (subscriptionStatus != null) {
                        subscriptionStatus.setText("Expired");
                        subscriptionStatus.setTextColor(Color.parseColor("#FF5252"));
                    }
                    if (timeRemaining != null) timeRemaining.setText("Expired");
                    return;
                }
                if (subscriptionStatus != null) {
                    subscriptionStatus.setText("Active");
                    subscriptionStatus.setTextColor(Color.parseColor("#25C779"));
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
        // version, architecture and size of the library on disk
        updateLibLine();
    }

    private void startWorkflow() {
        runOnUiThread(() -> {
            if (downloadBox != null) downloadBox.setVisibility(View.GONE);
            if (restartButton != null) restartButton.setVisibility(View.VISIBLE);
            if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
        });
        isUpdateAvailable = false;
    }

    /**
     * Asks the panel (and, if it has nothing, the repo mirror) whether a newer
     * library exists. Cheap enough to run on every start: one small GET unless
     * an update is found.
     */
    private void checkForLibUpdate(boolean manual) {
        if (libCheckRunning) return;
        libCheckRunning = true;

        if (manual) {
            if (checkUpdateButton != null) checkUpdateButton.setText("...");
            showStatusToggle("CHECKING FOR LIB UPDATE", false);
        }

        final Downtwo[] ref = new Downtwo[1];
        ref[0] = new Downtwo(this, success -> {
            Downtwo task = ref[0];
            String result = task == null || task.getResult() == null ? "" : task.getResult();
            String version = task == null ? "" : task.getFetchedServerVersion();
            runOnUiThread(() -> {
                libCheckRunning = false;
                if (checkUpdateButton != null) checkUpdateButton.setText("CHECK");

                boolean update = "Update Available".equals(result) || "Already Notified".equals(result);
                isUpdateAvailable = update;
                pendingLibVersion = update ? version : "";
                updateLibLine();

                if (update) {
                    String label = version == null || version.isEmpty() ? "" : " " + version;
                    setLaunchButtonText("INSTALL UPDATE" + label);
                    if (systemReady != null) systemReady.setText("UPDATE READY");
                    showStatusToggle(version == null || version.isEmpty()
                            ? "LIB UPDATE READY · TAP TO INSTALL"
                            : "LIB " + version + " READY · TAP TO INSTALL", false);
                } else if (manual) {
                    if (result.startsWith("No Update") || result.isEmpty()) {
                        showStatusToggle("LIB IS UP TO DATE", false);
                    } else {
                        showStatusToggle("CHECK FAILED · " + result, true);
                    }
                    if (systemReady != null) systemReady.setText("SYSTEM READY");
                }
            });
        });
        ref[0].execute(Downtwo.Link(), Downtwo.MODE_CHECK);
    }

    /** "v1.1.0 · arm64-v8a, 2.9 MB", with the pending version appended when there is one. */
    private void updateLibLine() {
        if (appVersionText == null) return;
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String localVersion = prefs.getString(PREF_VERSION_KEY, "1.0");
        String lib = LibLoader.describe(new File(getFilesDir(), "loader/libbgmi.so"));

        StringBuilder text = new StringBuilder("v").append(localVersion).append(" · ").append(lib);
        if (!pendingLibVersion.isEmpty() && !pendingLibVersion.equals(localVersion)) {
            text.append("  →  v").append(pendingLibVersion);
        }
        appVersionText.setText(text.toString());
    }

    private void startDownload() {
        runOnUiThread(() -> {
            if (downloadBox != null) downloadBox.setVisibility(View.VISIBLE);
            setLaunchButtonText("UPDATING...");
            if (downloadStatusText != null) downloadStatusText.setText("Preparing process...");
            if (downloadSubText != null) downloadSubText.setText("Fetching the library from the panel");
            if (percentText != null) percentText.setText("0%");
            if (downloadProgressBar != null) {
                downloadProgressBar.setIndeterminate(false);
                downloadProgressBar.setMax(100);
                downloadProgressBar.setProgress(0);
            }
            if (restartButton != null) restartButton.setVisibility(View.GONE);
            if (loadingCursor != null) loadingCursor.setVisibility(View.VISIBLE);
        });

        // the callback needs the task's error message, and the task is the thing
        // the callback is handed to — hence the one-element holder
        final Downtwo[] ref = new Downtwo[1];
        ref[0] = new Downtwo(this, success -> {
            if (success) {
                File installedLib = new File(getFilesDir(), "loader/libbgmi.so");
                if (!installedLib.exists()) {
                    runOnUiThread(() -> showStatusToggle("LIB MISSING AFTER UPDATE", true));
                }
                runOnUiThread(() -> {
                    isUpdateAvailable = false;
                    pendingLibVersion = "";
                    updateVersionDisplay();
                    if (systemReady != null) systemReady.setText("LIB READY");
                    if (downloadStatusText != null) downloadStatusText.setText("All process done");
                    if (percentText != null) percentText.setText("100%");
                    if (downloadProgressBar != null) downloadProgressBar.setProgress(100);
                    if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
                    
                    updateVersionDisplay(); // Refresh version text after update
                    setLaunchButtonText(isGameInstalled ? "LAUNCH GAME" : "INSTALL GAME");
                    
                    if (restartButton != null) {
                        restartButton.setVisibility(View.VISIBLE);
                        restartButton.setAlpha(0f);
                        restartButton.animate().alpha(1f).setDuration(500).start();
                    }
                });
            } else {
                runOnUiThread(() -> {
                    // A lib that did not arrive is the reason LAUNCH does
                    // nothing, so it is said out loud instead of hidden.
                    isUpdateAvailable = true;
                    String why = ref[0].getLastError();
                    showStatusToggle(why == null || why.isEmpty()
                            ? "LIB DOWNLOAD FAILED" : "LIB FAILED: " + why, true);
                    if (downloadBox != null) downloadBox.setVisibility(View.GONE);
                    if (loadingCursor != null) loadingCursor.setVisibility(View.GONE);
                    setLaunchButtonText(isGameInstalled ? "LAUNCH GAME" : "INSTALL GAME");
                });
            }
        });

        Downtwo task = ref[0];
        task.setProgressListener(progress -> runOnUiThread(() -> {
            if (downloadProgressBar != null) {
                downloadProgressBar.setIndeterminate(false);
                downloadProgressBar.setMax(100);
                downloadProgressBar.setProgress(progress);
            }
            if (percentText != null) percentText.setText(progress + "%");
            if (downloadStatusText != null) downloadStatusText.setText("Downloading library");
            if (downloadSubText != null) downloadSubText.setText("Extracting after the transfer");
        }));
        
        task.execute(Downtwo.Link());
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Runtime.getRuntime().exit(0);
        }
    }

    private void checkObbApplied() {
        new Thread(() -> {
            try {
                PackageInfo info = getPackageManager().getPackageInfo(PKG_BGMI, 0);
                String gameObb = "main." + info.versionCode + "." + PKG_BGMI + ".obb";
                String destPath = MetaStorageManager.obtainAppExternalStorageDir(PKG_BGMI) + "/Android/obb/" + PKG_BGMI;
                File destFile = new File(destPath, gameObb);
                if (destFile.exists()) {
                    isObbCopied = true;
                    runOnUiThread(() -> { if (obbButton != null) obbButton.setVisibility(View.GONE); });
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void updateGameStatus() {
        isGameInstalled = false;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(PKG_BGMI, 0);
            if (packageInfo != null) {
                isGameInstalled = true;
                if (gameName != null) gameName.setText("BGMI INDIA");
                if (packageName != null) packageName.setText(PKG_BGMI);
                if (gameStatus != null) gameStatus.setText("SAFE");
                if (deviceStatus != null) {
                    deviceStatus.setText("Safe");
                    deviceStatus.setTextColor(Color.parseColor("#4CB98A"));
                }
                updateButtonState(false);
            }
        } catch (Exception ignored) {
            isGameInstalled = false;
            if (gameStatus != null) gameStatus.setText("Not Installed");
            if (deviceStatus != null) {
                deviceStatus.setText("Not installed");
                deviceStatus.setTextColor(Color.parseColor("#D7A363"));
            }
            setLaunchButtonText("INSTALL GAME");
            if (startText != null) {
                startText.setText("INSTALL GAME");
                startText.setTextColor(Color.parseColor("#8FA8CC"));
            }
            if (actionIcon != null) {
                actionIcon.setImageResource(android.R.drawable.ic_menu_save);
                actionIcon.setColorFilter(Color.parseColor("#8FA8CC"));
            }
        }
    }

    private void setLaunchButtonText(String label) {
        if (startText != null) startText.setText(label);
        if (startBgmi instanceof Button) ((Button) startBgmi).setText(label);
    }

    private void updateButtonState(boolean running) {
        isGameRunning = running;
        runOnUiThread(() -> {
            if (running) {
                setLaunchButtonText("STOP GAME");
                if (startText != null) {
                    startText.setText("STOP GAME");
                    startText.setTextColor(Color.parseColor("#E0737C"));
                }
                if (actionIcon != null) {
                    actionIcon.setImageResource(android.R.drawable.ic_delete);
                    actionIcon.setColorFilter(Color.parseColor("#E0737C"));
                }
                if (startBgmi != null) {
                    startBgmi.setBackgroundResource(R.drawable.bg_ref_stop_button);
                    if (startBgmi instanceof Button) {
                        ((Button) startBgmi).setTextColor(Color.parseColor("#E0737C"));
                    }
                }
                if (downloadBox != null) {
                    downloadBox.setVisibility(View.VISIBLE);
                    if (downloadStatusText != null) downloadStatusText.setText("Game Running...");
                    if (percentText != null) percentText.setText("ACTIVE");
                    if (downloadProgressBar != null) {
                        downloadProgressBar.setIndeterminate(true);
                        downloadProgressBar.setProgress(100);
                    }
                    if (restartButton != null) restartButton.setVisibility(View.GONE);
                }
            } else {
                if (isUpdateAvailable) {
                    String label = pendingLibVersion.isEmpty()
                            ? "INSTALL UPDATE" : "INSTALL UPDATE " + pendingLibVersion;
                    setLaunchButtonText(label);
                    if (startText != null) {
                        startText.setText(label);
                        startText.setTextColor(Color.parseColor("#8FA8CC"));
                    }
                } else {
                    setLaunchButtonText("LAUNCH GAME");
                    if (startText != null) {
                        startText.setText("LAUNCH");
                        startText.setTextColor(Color.parseColor("#8FA8CC"));
                    }
                }
                if (actionIcon != null) {
                    actionIcon.setImageResource(android.R.drawable.ic_media_play);
                    actionIcon.setColorFilter(Color.parseColor("#8FA8CC"));
                }
                if (startBgmi != null) {
                    startBgmi.setBackgroundResource(R.drawable.bg_primary_action);
                    if (startBgmi instanceof Button) {
                        ((Button) startBgmi).setTextColor(Color.parseColor("#0B0C0E"));
                    }
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
        try {
            if (!MetaActivationManager.getActivationStatus()) {
                showStatusToggle("LAUNCHING FAILED", true);
                return;
            }
        } catch (Exception ignored) {
            showStatusToggle("LAUNCHING FAILED", true);
            return;
        }

        File libFile = new File(getFilesDir(), "loader/libbgmi.so");
        if (!libFile.exists()) {
            startDownload();
            return;
        }

        if (!isObbCopied) {
            copyObbFiles();
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
                statusToggleText.setTextColor(isError ? Color.parseColor("#FF5252") : Color.WHITE);
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

    private void copyObbFiles() {
        if (obbButton == null) return;
        
        Button btn = (Button) obbButton;
        btn.setEnabled(false);
        if (downloadBox != null) downloadBox.setVisibility(View.VISIBLE);
        if (downloadStatusText != null) downloadStatusText.setText("Preparing process...");
        if (percentText != null) percentText.setText("0%");
        if (downloadProgressBar != null) {
            downloadProgressBar.setIndeterminate(false);
            downloadProgressBar.setMax(100);
            downloadProgressBar.setProgress(0);
        }
        
        AppManager appManager = new AppManager(this);
        appManager.copyObbFolderAsync(PKG_BGMI, new AppManager.CopyCallback() {
            @Override
            public void onCopyCompleted(boolean success) {
                runOnUiThread(() -> {
                    if (success) {
                        isObbCopied = true;
                        btn.setVisibility(View.GONE);
                        if (downloadStatusText != null) downloadStatusText.setText("Facebook access ready");
                        if (percentText != null) percentText.setText("100%");
                        if (downloadProgressBar != null) downloadProgressBar.setProgress(100);
                        new Handler().postDelayed(() -> {
                            if (downloadBox != null) downloadBox.setVisibility(View.GONE);
                        }, 900);
                        Toast.makeText(MAct.this, "OBB Copied Successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        btn.setEnabled(true);
                        btn.setText("APPLY OBB");
                        if (downloadBox != null) downloadBox.setVisibility(View.GONE);
                        Toast.makeText(MAct.this, "OBB Copy Failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onProgressUpdate(int progress) {
                runOnUiThread(() -> {
                    if (downloadStatusText != null) downloadStatusText.setText("Preparing process...");
                    if (percentText != null) percentText.setText(progress + "%");
                    if (downloadProgressBar != null) downloadProgressBar.setProgress(progress);
                });
            }
        }, false); // false to hide the default dialog
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }
}
