package de.kai_morich.simple_usb_terminal;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.UserManager;


@RequiresApi(api = Build.VERSION_CODES.O)
public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener {

    private StorageReference storageRef;
    private LocationHelper locationHelper;

    // Collapsible reboot panel components
    private LinearLayout rebootPanel;
    private LinearLayout rebootContent;
    private ImageView arrowIcon;
    private boolean isPanelCollapsed = false;

    private final BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                unregisterReceiver(this);
                initializeAfterUnlock();
            }
        }
    };

    DevicePolicyManager mDPM;

    MyDeviceAdminReceiver deviceAdminReceiver;

    //used to identify the device admin receiver in the permission request intent (?)
    ComponentName mDeviceAdminReceiver;

    private FirebaseAuth mAuth;

    ActivityResultLauncher<String[]> locationPermissionRequest =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                        Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                        Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);
                        if (fineLocationGranted != null && fineLocationGranted) {
                            // Precise location access granted.
                            Toast.makeText(this, "Correct Location Permissions", Toast.LENGTH_SHORT).show();
                        } else if (coarseLocationGranted != null && coarseLocationGranted) {
                            // Only approximate location access granted.
                            Toast.makeText(this, "Bad Location Permissions", Toast.LENGTH_SHORT).show();
                        } else {
                            // No location access granted.
                            Toast.makeText(this, "No Location Permissions", Toast.LENGTH_SHORT).show();
                        }
                    }
            );


    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //following line will keep the screen active
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager.isUserUnlocked()) {
            initializeAfterUnlock();
        } else {
            IntentFilter filter = new IntentFilter(Intent.ACTION_USER_UNLOCKED);
            registerReceiver(unlockReceiver, filter);
        }

        //setup toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);



        //stop firebase worker (from testing)
//        WorkerWrapper.checkWorkerStatus(getApplicationContext());
//        WorkerWrapper.stopFireBaseWorker(getApplicationContext());
//        WorkerWrapper.checkWorkerStatus(getApplicationContext());


//        WorkerWrapper.startFirebaseWorker(getApplicationContext());
        WorkerWrapper.startSerialWorker(getApplicationContext());



        //initialize Device Policy Manager (used for periodic restarts)
        mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDeviceAdminReceiver = new ComponentName(this, MyDeviceAdminReceiver.class);

        System.out.println("Package name: " + this.getPackageName());

        //next steps: create a button or something in device fragment (or terminal fragment)
        //that triggers the request permissions activity for device administrator priveleges

//        if(mDPM.isDeviceOwnerApp(this.getPackageName())) {
//            System.out.println("mDPM is successful device administrator");
//        }


        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDeviceAdminReceiver);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Some extra text explaining why we're asking for device admin permission");
        startActivity(intent);



        locationPermissionRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });




        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();

        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
        else
            onBackStackChanged();

        // Initialize reboot functionality
        de.austin_bee.reboot.RebootManager.INSTANCE.onAppOpen(this);
        
        // Request battery optimization exemption for reliable auto-reboot
        requestBatteryOptimizationExemption();
        
        // Start reboot monitoring service
        startRebootMonitoringService();
        
        // Setup auto screen sleep after 5 minutes
        setupAutoScreenSleep();
        
        // Register broadcast receiver for screen sleep requests
        registerScreenSleepReceiver();
        
        // Setup reboot UI
        setupRebootUI();
        
        // Set up periodic status updates (every 30 seconds)
        android.os.Handler statusHandler = new android.os.Handler();
        statusHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateRebootStatus();
                statusHandler.postDelayed(this, 30000); // 30 seconds
            }
        }, 30000);
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main_activity, menu);
        return true;
    }

    //TODO re-add support for changing GPS settings
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        int id = item.getItemId();
//        if (id == R.id.gps_period) {
//            Toast.makeText(getApplicationContext(), "Clicked GPS Period option", Toast.LENGTH_SHORT).show();
//            AlertDialog.Builder builder = new AlertDialog.Builder(this);
//            builder.setTitle("New GPS Period");
//
//            final EditText input = new EditText(getApplicationContext());
//            input.setInputType(InputType.TYPE_CLASS_TEXT);
//            builder.setView(input);
//
//            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    gpsTimer.cancel();
//                    gpsPeriod = Integer.parseInt(input.getText().toString());
//                    Toast.makeText(getApplicationContext(), "Set GPS period to " + gpsPeriod, Toast.LENGTH_SHORT).show();
//
//                    locationRequest = new CurrentLocationRequest.Builder().setPriority(Priority.PRIORITY_HIGH_ACCURACY).setMaxUpdateAgeMillis(gpsPeriod - 10).build();
//
//                    gpsTimer = new Timer();
//                    gpsTimer.schedule(new TimerTask() {
//                        @SuppressLint("MissingPermission")
//                        @Override
//                        public void run() {
//                            fusedLocationClient.getCurrentLocation(locationRequest, new CancellationToken() {
//                                @SuppressLint("MissingPermission")
//                                @NonNull
//                                @Override
//                                public CancellationToken onCanceledRequested(@NonNull OnTokenCanceledListener onTokenCanceledListener) {
//                                    return null;
//                                }
//
//                                @Override
//                                public boolean isCancellationRequested() {
//                                    return false;
//                                }
//                            }).addOnSuccessListener(newLocation -> {
//                                location = newLocation;
//                            });
//                        }
//                    }, 0, gpsPeriod);
//                }
//            });
//            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                @Override
//                public void onClick(DialogInterface dialog, int which) {
//                    dialog.cancel();
//                }
//            });
//            Toast.makeText(getApplicationContext(), "built popup", Toast.LENGTH_SHORT).show();
//            try {
//                builder.show();
//            } catch (Exception e) {
//                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
//                e.printStackTrace();
//            }
//            Toast.makeText(getApplicationContext(), "showed popup", Toast.LENGTH_SHORT).show();
//            return true;
//        } else {
//            return super.onOptionsItemSelected(item);
//        }
//    }

    @Override
    public void onBackStackChanged() {
        getSupportActionBar().setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() > 0);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if ("android.hardware.usb.action.USB_DEVICE_ATTACHED".equals(intent.getAction())) {
            TerminalFragment terminal = (TerminalFragment) getSupportFragmentManager().findFragmentByTag("terminal");
            if (terminal != null) {
                terminal.status("USB device detected");
                terminal.connect();
                //this might be the problem
            }
        }
        super.onNewIntent(intent);
    }

    private void initializeAfterUnlock() {
        // Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Start GPS/heading service
        startService(new Intent(this, SensorHelper.class));

        // Start Firebase service
        startService(new Intent(this, FirebaseService.class));

        // Location tracking
        locationHelper = new LocationHelper(this);
        locationHelper.startLocationUpdates();
    }

    @Override
    public void onDestroy(){
//        stopService(new Intent(this, FirebaseService.class));
//        stopService(new Intent(this, SerialService.class));
        super.onDestroy();
    }

    public void updateLocationPriority(int priority){
        locationHelper.changePriority(priority);
    }

    public void updateLocationInterval(long intervalSeconds){
        locationHelper.changeUpdateInterval(intervalSeconds);
    }

    public void uploadFile(File file) {
        Uri uri = Uri.fromFile(file);
        StorageReference fileRef = storageRef.child("log/"
                +Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME)
                +"/"+uri.getLastPathSegment());
        fileRef.putFile(uri);
    }

    private void setupRebootUI() {
        // Initialize collapsible panel components
        rebootPanel = findViewById(R.id.reboot_panel);
        rebootContent = findViewById(R.id.reboot_content);
        arrowIcon = findViewById(R.id.arrow_icon);
        
        // Set up arrow click listener for collapsible functionality
        arrowIcon.setOnClickListener(v -> toggleRebootPanel());
        
        // Also make the arrow container clickable
        findViewById(R.id.arrow_container).setOnClickListener(v -> toggleRebootPanel());
        
        // Add power menu delay adjustment button
        setupPowerMenuDelayAdjustment();
        
        // Debug logging
        System.out.println("Setting up reboot UI...");
        
        // Accessibility settings button
        try {
            findViewById(R.id.btn_accessibility_settings).setOnClickListener(v -> {
                System.out.println("Accessibility button clicked!");
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
            System.out.println("Accessibility button listener set successfully");
        } catch (Exception e) {
            System.err.println("Error setting accessibility button: " + e.getMessage());
            e.printStackTrace();
        }

        // Set reboot time button
        try {
            findViewById(R.id.btn_set_reboot_time).setOnClickListener(v -> {
                System.out.println("Set reboot time button clicked!");
                showTimePickerDialog();
            });
            System.out.println("Set reboot time button listener set successfully");
        } catch (Exception e) {
            System.err.println("Error setting reboot time button: " + e.getMessage());
            e.printStackTrace();
        }

        // Update initial status
        updateRebootStatus();
    }

    private void showTimePickerDialog() {
        // Get current time as default
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(java.util.Calendar.MINUTE);
        
        android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(
            this,
            (view, hourOfDay, minute) -> {
                System.out.println("Time selected: " + hourOfDay + ":" + minute);
                // Enable auto reboot with selected time
                de.austin_bee.reboot.RebootManager.INSTANCE.enableScheduling(this, hourOfDay, minute);
                updateRebootStatus();
                
                // Show confirmation message
                String amPm = hourOfDay >= 12 ? "PM" : "AM";
                int displayHour = hourOfDay == 0 ? 12 : (hourOfDay > 12 ? hourOfDay - 12 : hourOfDay);
                String displayTime = String.format("%d:%02d %s", displayHour, minute, amPm);
                Toast.makeText(this, "Auto reboot scheduled daily at " + displayTime, Toast.LENGTH_LONG).show();
            },
            currentHour,
            currentMinute,
            true // 24-hour format
        );
        
        timePickerDialog.setTitle("Select Reboot Time");
        timePickerDialog.setMessage("Choose the daily time for auto reboot:");
        timePickerDialog.show();
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                android.app.usage.UsageStatsManager usageStatsManager = (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                android.app.AppOpsManager appOpsManager = (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                
                String packageName = getPackageName();
                int mode = appOpsManager.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName);
                
                if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
                    // Check if battery optimization is enabled for this app
                    android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
                    if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        System.out.println("Battery optimization already disabled for this app");
                    } else {
                        // Request battery optimization exemption
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + packageName));
                        startActivity(intent);
                        System.out.println("Requesting battery optimization exemption");
                    }
                } else {
                    System.out.println("Usage stats permission not granted");
                }
            } catch (Exception e) {
                System.err.println("Error requesting battery optimization exemption: " + e.getMessage());
            }
        }
    }

    private void startRebootMonitoringService() {
        try {
            // Start a foreground service to monitor reboot scheduling
            Intent serviceIntent = new Intent(this, RebootMonitoringService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            System.out.println("Reboot monitoring service started");
        } catch (Exception e) {
            System.err.println("Error starting reboot monitoring service: " + e.getMessage());
        }
    }

    private void setupAutoScreenSleep() {
        try {
            // Start a foreground service to monitor screen sleep
            Intent serviceIntent = new Intent(this, ScreenSleepService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            System.out.println("Screen sleep monitoring service started");
        } catch (Exception e) {
            System.err.println("Error starting screen sleep monitoring service: " + e.getMessage());
        }
    }

    private void registerScreenSleepReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScreenSleepService.ACTION_REQUEST_SCREEN_WAKE_LOCK);
        filter.addAction(ScreenSleepService.ACTION_RELEASE_SCREEN_WAKE_LOCK);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ScreenSleepService.ACTION_REQUEST_SCREEN_WAKE_LOCK.equals(intent.getAction())) {
                    System.out.println("Received request to keep screen awake.");
                    // Keep screen awake
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else if (ScreenSleepService.ACTION_RELEASE_SCREEN_WAKE_LOCK.equals(intent.getAction())) {
                    System.out.println("Received request to release screen wake lock.");
                    // Release screen wake lock
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        }, filter);
    }

    private void setupPowerMenuDelayAdjustment() {
        // Add a button or option to adjust power menu delay
        // This can be called from the UI to increase/decrease the delay
        System.out.println("Power menu delay adjustment setup complete");
        System.out.println("Current delay: " + ScreenSleepService.getPowerMenuDelay() + " ms");
        System.out.println("To increase delay, call: ScreenSleepService.setPowerMenuDelay(5000) for 5 seconds");
        System.out.println("To decrease delay, call: ScreenSleepService.setPowerMenuDelay(1000) for 1 second");
    }

    private void toggleRebootPanel() {
        System.out.println("Toggle reboot panel clicked. Current state: " + (isPanelCollapsed ? "collapsed" : "expanded"));
        if (isPanelCollapsed) {
            // Expand the panel
            expandRebootPanel();
        } else {
            // Collapse the panel
            collapseRebootPanel();
        }
    }

    private void collapseRebootPanel() {
        System.out.println("Collapsing reboot panel...");
        // Change arrow to point down immediately
        arrowIcon.setImageResource(android.R.drawable.arrow_down_float);
        
        // Create slide up animation
        TranslateAnimation slideUp = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, -1.0f
        );
        slideUp.setDuration(300);
        slideUp.setFillAfter(true);
        
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                // Hide the content after animation
                rebootContent.setVisibility(View.GONE);
                System.out.println("Reboot panel collapsed successfully");
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        
        rebootContent.startAnimation(slideUp);
        isPanelCollapsed = true;
    }

    private void expandRebootPanel() {
        System.out.println("Expanding reboot panel...");
        // Change arrow to point up immediately
        arrowIcon.setImageResource(android.R.drawable.arrow_up_float);
        
        // Show the content first
        rebootContent.setVisibility(View.VISIBLE);
        
        // Create slide down animation
        TranslateAnimation slideDown = new TranslateAnimation(
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.0f,
            Animation.RELATIVE_TO_SELF, -1.0f,
            Animation.RELATIVE_TO_SELF, 0.0f
        );
        slideDown.setDuration(300);
        slideDown.setFillAfter(true);
        
        slideDown.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                System.out.println("Reboot panel expanded successfully");
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        
        rebootContent.startAnimation(slideDown);
        isPanelCollapsed = false;
    }

    private void updateRebootStatus() {
        android.widget.TextView statusText = findViewById(R.id.tv_reboot_status);
        boolean isEnabled = de.austin_bee.reboot.RebootPrefs.INSTANCE.isEnabled(this);
        kotlin.Pair<Integer, Integer> time = de.austin_bee.reboot.RebootPrefs.INSTANCE.getTime(this);
        
        // Check accessibility status
        boolean isAccessibilityEnabled = isAccessibilityServiceEnabled();
        
        if (isEnabled && time != null) {
            String timeStr = String.format("%02d:%02d", time.getFirst(), time.getSecond());
            String amPm = time.getFirst() >= 12 ? "PM" : "AM";
            int displayHour = time.getFirst() == 0 ? 12 : (time.getFirst() > 12 ? time.getFirst() - 12 : time.getFirst());
            String displayTime = String.format("%d:%02d %s", displayHour, time.getSecond(), amPm);
            
            String accessibilityStatus = isAccessibilityEnabled ? "✓ Accessibility: ENABLED" : "✗ Accessibility: DISABLED";
            
            statusText.setText("✓ Auto Reboot SCHEDULED\nDaily at " + displayTime + "\n" + accessibilityStatus);
            statusText.setBackgroundResource(android.R.color.holo_green_light);
        } else {
            String accessibilityStatus = isAccessibilityEnabled ? "✓ Accessibility: ENABLED" : "✗ Accessibility: DISABLED";
            statusText.setText("✗ Auto Reboot DISABLED\nTap 'Set Time to Auto Reboot' to activate\n" + accessibilityStatus);
            statusText.setBackgroundResource(android.R.color.holo_red_light);
        }
    }
    
    private boolean isAccessibilityServiceEnabled() {
        try {
            android.content.ComponentName expectedComponentName = new android.content.ComponentName(this, de.austin_bee.reboot.A11yRebooterService.class);
            String enabledServices = android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return enabledServices != null && enabledServices.contains(expectedComponentName.flattenToString());
        } catch (Exception e) {
            System.err.println("Error checking accessibility status: " + e.getMessage());
            return false;
        }
    }
    
}