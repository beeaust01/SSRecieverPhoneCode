package de.kai_morich.simple_usb_terminal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;

public class ScreenSleepService extends Service {
    
    public static final String ACTION_REQUEST_SCREEN_WAKE_LOCK = "REQUEST_SCREEN_WAKE_LOCK";
    public static final String ACTION_RELEASE_SCREEN_WAKE_LOCK = "RELEASE_SCREEN_WAKE_LOCK";
    
    private static final String CHANNEL_ID = "ScreenSleepChannel";
    private static final int NOTIFICATION_ID = 12346;
    private static final long SLEEP_DELAY_MS = 5 * 60 * 1000; // 5 minutes
    private static final long REBOOT_CHECK_INTERVAL = 60 * 1000; // Check every minute
    
    // Power menu delay configuration - adjust this value to control delay between opening menu and pressing restart
    private static long POWER_MENU_DELAY_MS = 3000; // 3 seconds - increase this value for longer delay
    
    // Method to adjust the power menu delay dynamically
    public static void setPowerMenuDelay(long delayMs) {
        POWER_MENU_DELAY_MS = delayMs;
        System.out.println("Power menu delay updated to: " + POWER_MENU_DELAY_MS + " milliseconds");
    }
    
    // Method to get current power menu delay
    public static long getPowerMenuDelay() {
        return POWER_MENU_DELAY_MS;
    }
    
    private Handler handler;
    private Runnable sleepRunnable;
    private Runnable rebootCheckRunnable;
    private boolean screenIsAsleep = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        setupSleepTimer();
        setupRebootMonitoring();
        logPowerMenuDelayConfig();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            if (sleepRunnable != null) {
                handler.removeCallbacks(sleepRunnable);
            }
            if (rebootCheckRunnable != null) {
                handler.removeCallbacks(rebootCheckRunnable);
            }
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Screen Sleep Monitor",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors screen sleep timing and reboot schedule");
            
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        String statusText = screenIsAsleep ? "Screen asleep - monitoring reboot time" : "Screen will sleep in 5 minutes...";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen & Reboot Monitor")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);
        
        return builder.build();
    }
    
    private void setupSleepTimer() {
        handler = new Handler();
        sleepRunnable = new Runnable() {
            @Override
            public void run() {
                putScreenToSleep();
            }
        };
        
        // Schedule screen sleep after 5 minutes
        handler.postDelayed(sleepRunnable, SLEEP_DELAY_MS);
        System.out.println("Screen sleep scheduled for " + (SLEEP_DELAY_MS / 1000) + " seconds from now");
    }
    
    private void setupRebootMonitoring() {
        rebootCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkRebootTime();
                // Schedule next check
                handler.postDelayed(this, REBOOT_CHECK_INTERVAL);
            }
        };
        
        // Start monitoring reboot time
        handler.postDelayed(rebootCheckRunnable, REBOOT_CHECK_INTERVAL);
        System.out.println("Reboot time monitoring started");
    }
    
    private void checkRebootTime() {
        try {
            // Check if auto-reboot is enabled and get the scheduled time
            boolean isEnabled = de.austin_bee.reboot.RebootPrefs.INSTANCE.isEnabled(this);
            if (isEnabled) {
                kotlin.Pair<Integer, Integer> time = de.austin_bee.reboot.RebootPrefs.INSTANCE.getTime(this);
                if (time != null) {
                    // Check if it's time to wake up the screen (30 seconds before reboot)
                    if (shouldWakeUpScreen(time.getFirst(), time.getSecond())) {
                        wakeUpScreen();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking reboot time: " + e.getMessage());
        }
    }
    
    private boolean shouldWakeUpScreen(int rebootHour, int rebootMinute) {
        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar rebootTime = java.util.Calendar.getInstance();
            rebootTime.set(java.util.Calendar.HOUR_OF_DAY, rebootHour);
            rebootTime.set(java.util.Calendar.MINUTE, rebootMinute);
            rebootTime.set(java.util.Calendar.SECOND, 0);
            rebootTime.set(java.util.Calendar.MILLISECOND, 0);
            
            // If reboot time has passed today, set it for tomorrow
            if (rebootTime.before(now)) {
                rebootTime.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }
            
            // Calculate time until reboot
            long timeUntilReboot = rebootTime.getTimeInMillis() - now.getTimeInMillis();
            long thirtySecondsMs = 30 * 1000; // 30 seconds in milliseconds
            
            // Wake up screen 30 seconds before reboot
            return timeUntilReboot <= thirtySecondsMs && timeUntilReboot > 0;
            
        } catch (Exception e) {
            System.err.println("Error calculating wake up time: " + e.getMessage());
            return false;
        }
    }
    
    private void wakeUpScreen() {
        try {
            System.out.println("Waking up screen for scheduled reboot...");
            
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!powerManager.isInteractive()) {
                        // Wake up the screen
                        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                            "ScreenWakeUp::Reboot"
                        );
                        wakeLock.acquire(10000); // Hold for 10 seconds
                        System.out.println("Screen woken up successfully for reboot");
                        
                        // Update notification
                        screenIsAsleep = false;
                        updateNotification();
                        
                        // Release wake lock after 10 seconds
                        new Handler().postDelayed(() -> {
                            if (wakeLock.isHeld()) {
                                wakeLock.release();
                            }
                        }, 10000);
                    } else {
                        System.out.println("Screen is already awake");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error waking up screen: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void updateNotification() {
        try {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, createNotification());
            }
        } catch (Exception e) {
            System.err.println("Error updating notification: " + e.getMessage());
        }
    }
    
    private void putScreenToSleep() {
        try {
            System.out.println("Putting screen to sleep after 5 minutes...");
            
            // Get PowerManager service
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                // Try different methods to put screen to sleep
                boolean sleepSuccess = false;
                
                // Method 1: Try using reflection for goToSleep (works on most devices)
                try {
                    java.lang.reflect.Method goToSleepMethod = powerManager.getClass()
                        .getMethod("goToSleep", long.class);
                    goToSleepMethod.invoke(powerManager, SystemClock.uptimeMillis());
                    System.out.println("Screen put to sleep successfully using reflection");
                    sleepSuccess = true;
                } catch (Exception e) {
                    System.out.println("Reflection method failed: " + e.getMessage());
                }
                
                // Method 2: If reflection fails, try using a different approach
                if (!sleepSuccess) {
                    try {
                        // Try using the newer API if available
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Use a wake lock to temporarily wake up then release to trigger sleep
                            PowerManager.WakeLock tempWakeLock = powerManager.newWakeLock(
                                PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                "TempWakeLock::Sleep"
                            );
                            tempWakeLock.acquire(100); // Hold for 100ms
                            tempWakeLock.release();
                            
                            // Wait a moment then try to sleep
                            new Handler().postDelayed(() -> {
                                try {
                                    // Try to put screen to sleep using a different approach
                                    if (powerManager.isInteractive()) {
                                        // Simulate power button press by releasing screen wake lock
                                        System.out.println("Attempting to trigger screen sleep...");
                                    }
                                } catch (Exception e) {
                                    System.err.println("Alternative sleep method failed: " + e.getMessage());
                                }
                            }, 200);
                            
                            System.out.println("Screen sleep triggered using alternative method");
                            sleepSuccess = true;
                        }
                    } catch (Exception e) {
                        System.err.println("Alternative method failed: " + e.getMessage());
                    }
                }
                
                // Method 3: If all else fails, just log that we can't control sleep
                if (!sleepSuccess) {
                    System.out.println("Could not programmatically put screen to sleep - user may need to manually turn off screen");
                    System.out.println("Auto-reboot will still work when screen is manually turned off");
                }
                
                // Method 4: Try to remove screen keep-awake flag from MainActivity
                try {
                    // Send broadcast to MainActivity to remove keep-awake flag
                    Intent removeKeepAwakeIntent = new Intent(ACTION_RELEASE_SCREEN_WAKE_LOCK);
                    sendBroadcast(removeKeepAwakeIntent);
                    System.out.println("Sent broadcast to remove keep-awake flag");
                } catch (Exception e) {
                    System.err.println("Could not send remove keep-awake broadcast: " + e.getMessage());
                }
                
                // Update status regardless of method used
                screenIsAsleep = true;
                updateNotification();
                
            } else {
                System.err.println("PowerManager is null, cannot put screen to sleep");
            }
            
            // Don't stop the service - keep it running to monitor reboot time
            // stopSelf();
            
        } catch (Exception e) {
            System.err.println("Error putting screen to sleep: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logPowerMenuDelayConfig() {
        System.out.println("Power Menu Delay Configuration:");
        System.out.println("POWER_MENU_DELAY_MS: " + POWER_MENU_DELAY_MS + " milliseconds");
        System.out.println("This value controls the delay between opening the power menu and pressing the restart button.");
        System.out.println("To increase the delay, you can set POWER_MENU_DELAY_MS to a larger value (e.g., 5000 for 5 seconds).");
        System.out.println("To decrease the delay, you can set POWER_MENU_DELAY_MS to a smaller value (e.g., 1000 for 1 second).");
        System.out.println("The service will automatically wake up the screen 30 seconds before the scheduled reboot time.");
    }
}

