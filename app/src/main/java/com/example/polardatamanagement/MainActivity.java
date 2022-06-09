package com.example.polardatamanagement;

import static android.content.ContentValues.TAG;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.Fade;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.example.polardatamanagement.Utilities.HrAndRrPlotter;
import com.example.polardatamanagement.Utilities.PlotterListener;
import com.example.polardatamanagement.Utilities.RecyclerViewAdapter;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarHrBroadcastData;
import com.polar.sdk.api.model.PolarHrData;


import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;

public class MainActivity extends AppCompatActivity implements RecyclerViewAdapter.ItemClickListener, PlotterListener, SensorEventListener {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private FirebaseAuth mAuth;
    private FirebaseDatabase database;

    private Disposable scanDisposable = null;

    private Disposable broadcastDisposable = null;
    private boolean deviceConnected = false;
    //private String deviceId = "73B38923";
    private List<PolarDeviceInfo> availableDevices;

    private Button signInBtn;
    private Button signOutBtn;
    private TextView usernameDisplayTextView;
    private Button ScanDeviceBtn;
    private ImageButton ArrowButton;
    private ProgressBar DevicesStatusProgressBar;

    private TextView appDescriptionTxtView;
    private TextView displayMessageTxtView;
    private TextView heartRateTextView;
    private TextView whatsConnectedIndicator;
    private TextView DisplayHRVTxtView;

    private SensorManager sensorManager = null;
    private boolean running = false;
    private float totalSteps = 0;
    private float previousTotalSteps = 0;
    private TextView stepsTxtView;
    private ImageView imageViewSteps;

    private TextView textViewRR;
    private ArrayList<Integer> RRIntervals;
    private Button CalculateHRVBtn;
    private XYPlot plot;

    HrAndRrPlotter plotter;
    ConstraintLayout userConstraintLayout;
    LinearLayout hiddenView;
    LinearLayout baseView;
    List<AuthUI.IdpConfig> providers;
    LinearLayout recyclerViewItemLayout;
    RecyclerView recyclerView;
    RecyclerViewAdapter adapter;
    RecyclerView.ViewHolder itemViewClicked;
    PolarBleApi api;
    private String deviceIDConnected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViewsAndVariables();
        createPlot();
        loadData(); //read previous total steps that were saved on the device
        initializeListeners();
        initializeApi();
        requestPermissions();

        boolean isBroadCastDisposed;
        if (broadcastDisposable != null) {
            isBroadCastDisposed = scanDisposable.isDisposed();
        } else {
            isBroadCastDisposed = true;
        }
        if (isBroadCastDisposed) {
            displayMessageTxtView.setText(R.string.scanDevicesMsg);
            DevicesStatusProgressBar.setVisibility(View.VISIBLE);
            broadcastDisposable = api.startListenForPolarHrBroadcasts(null)
                    .subscribe(new Consumer<PolarHrBroadcastData>() {
                        @RequiresApi(api = Build.VERSION_CODES.N)
                        @Override
                        public void accept(PolarHrBroadcastData polarHrBroadcastData) throws Throwable {
                            if (availableDevices.isEmpty()) {
                                deviceFoundProcedure(polarHrBroadcastData.polarDeviceInfo);
                            } else {
                                if (availableDevices.stream().noneMatch(polarDeviceInfo -> polarDeviceInfo.
                                        deviceId.equals(polarHrBroadcastData.polarDeviceInfo.deviceId))) {
                                    deviceFoundProcedure(polarHrBroadcastData.polarDeviceInfo);
                                }
                            }
                        }
                    });
        } else {
            broadcastDisposable.dispose();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            updateUI(currentUser);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        api.backgroundEntered();
    }

    @Override
    public void onResume() {
        super.onResume();
        running = true;
        Sensor stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (stepSensor == null) {
            // This will give a toast message to the user if there is no sensor in the device
            Toast.makeText(this, "No sensor detected on this device", Toast.LENGTH_SHORT).show();
        } else {
            // Rate suitable for the user interface
            sensorManager.registerListener(MainActivity.this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
        api.foregroundEntered();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        plot.clear();
        api.shutDown();
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
        alert.setTitle("Exit application");
        alert.setMessage(R.string.confirm_exit_msg);
        alert.setPositiveButton("Yes", (dialogInterface, i) -> finishAffinity());
        alert.setNegativeButton("No", (dialogInterface, i) -> dialogInterface.cancel());
        alert.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    blockAppFunctions();
                    Toast.makeText(MainActivity.this, "No sufficient permissions", Toast.LENGTH_SHORT).show();
                    break;
                }
            }
            Log.d(TAG, "Needed permissions are granted");
        }
    }

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new FirebaseAuthUIActivityResultContract(),
            new ActivityResultCallback<FirebaseAuthUIAuthenticationResult>() {
                @Override
                public void onActivityResult(FirebaseAuthUIAuthenticationResult result) {
                    onSignInResult(result);
                }
            }
    );

    private void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        IdpResponse response = result.getIdpResponse();
        if (result.getResultCode() == RESULT_OK) {
            // Successfully signed in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            assert user != null;
            alert.setMessage("Welcome " + user.getDisplayName());
            alert.setPositiveButton("Hi", (dialogInterface, i) -> updateUI(user));
            alert.setOnDismissListener(dialogInterface -> updateUI(user));
            AlertDialog dialog = alert.create();
            dialog.getWindow().getAttributes().windowAnimations = R.style.SlidingDialogAnimation;
            dialog.show();
        } else {
            Toast.makeText(MainActivity.this, "Sign In failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeViewsAndVariables() {
        // Initialize Polar & Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        api = PolarBleApiDefaultImpl.defaultImplementation(getApplicationContext(), PolarBleApi.ALL_FEATURES);
        // Initialize Buttons
        signInBtn = findViewById(R.id.signInBtn);
        signOutBtn = findViewById(R.id.signOutBtn);
        usernameDisplayTextView = findViewById(R.id.user_name_display_txt_view);
        ScanDeviceBtn = findViewById(R.id.ScanDevicesBtn);
        ArrowButton = findViewById(R.id.arrow_btn);
        // Initialize Views & Layouts
        userConstraintLayout = findViewById(R.id.UserConstraintLayout);
        hiddenView = findViewById(R.id.hiddenView);
        baseView = findViewById(R.id.baseView);
        // Initialize everything else
        appDescriptionTxtView = findViewById(R.id.appDescriptionTextView);
        whatsConnectedIndicator = findViewById(R.id.whatIsConnectedIndicatorTxtView);
        DevicesStatusProgressBar = findViewById(R.id.progressBarScanDevices);
        displayMessageTxtView = findViewById(R.id.displayMsgTxtView);
        heartRateTextView = findViewById(R.id.displayHeartRateTxt);
        DisplayHRVTxtView = findViewById(R.id.DisplayHRVTxtView);
        textViewRR = findViewById(R.id.hr_view_rr);
        CalculateHRVBtn = findViewById(R.id.calculateHRVBtn);
        plot = findViewById(R.id.hr_view_plot);
        availableDevices = new ArrayList<>();
        //Initialize RecyclerView
        recyclerView = findViewById(R.id.devicesRclView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerViewAdapter(this, availableDevices);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);

        database = FirebaseDatabase.getInstance(getString(R.string.database_URL));
        providers = Collections.singletonList(new AuthUI.IdpConfig.EmailBuilder().build());

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepsTxtView = findViewById(R.id.displayStepsTxt);
        imageViewSteps = findViewById(R.id.imageViewSteps);

        RRIntervals = new ArrayList<>();
    }

    private void initializeListeners() {
        signInBtn.setOnClickListener(view -> {
            Intent signInIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .build();
            signInLauncher.launch(signInIntent);
        });

        signOutBtn.setOnClickListener(view -> {
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            alert.setTitle("Sign out");
            alert.setMessage("Are you sure you want to sign out?");
            alert.setPositiveButton("Yes", (dialogInterface, i) -> AuthUI.getInstance()
                    .signOut(MainActivity.this)
                    .addOnCompleteListener(task -> {
                        deviceConnected = false;
                        deviceIDConnected = null;
                        recyclerViewItemLayout.removeViewAt(1);
                        recyclerViewItemLayout.removeViewAt(1);
                        heartRateTextView.setText(R.string.emptyHRText);
                        whatsConnectedIndicator.setText(R.string.no_connected_devices);
                        RRIntervals.clear();
                        signInBtn.setVisibility(View.VISIBLE);
                        appDescriptionTxtView.setVisibility(View.VISIBLE);
                        signOutBtn.setVisibility(View.INVISIBLE);
                        userConstraintLayout.setVisibility(View.GONE);
                    }));
            alert.setNegativeButton("No", (dialogInterface, i) -> dialogInterface.cancel());
            alert.show();
        });

        imageViewSteps.setOnClickListener(view -> Toast.makeText(MainActivity.this, "Long tap to reset steps", Toast.LENGTH_SHORT).show());

        imageViewSteps.setOnLongClickListener(view -> {
            previousTotalSteps = totalSteps;
            stepsTxtView.setText("0");
            saveData();
            return true;
        });

        CalculateHRVBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                double RMinusRSquareSum = 0;
                int RMinusRSquareCount = 0;
                if (RRIntervals.size()<=10){
                    Toast.makeText(MainActivity.this, "Please wait until sensor gather enough Heart Rate Data", Toast.LENGTH_SHORT).show();
                } else {
                    //The below code calculates HRV based on RMSSD
                    for (int i=0; i<RRIntervals.size()-1; i++){
                        RMinusRSquareSum += Math.pow(RRIntervals.get(i) - RRIntervals.get(i+1), 2);
                        RMinusRSquareCount += 1;
                    }
                    double RMinusRMean = RMinusRSquareSum/RMinusRSquareCount;
                    double HRV = Math.round(Math.sqrt(RMinusRMean));
                    DisplayHRVTxtView.setText("HRV: " + String.valueOf(HRV));
                    database.getReference()
                            .child("Users")
                            .child(Objects.requireNonNull(mAuth.getCurrentUser()).getDisplayName() + " - " + mAuth.getCurrentUser().getUid())
                            .child(new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                            .child("Device ID - " + deviceIDConnected)
                            .child("HRVData")
                            .child(Calendar.getInstance().getTime().toString())
                            .setValue(HRV);
                }
            }
        });

        ArrowButton.setOnClickListener(view -> {
            Transition transition = new Fade();
            transition.addTarget(hiddenView);
            if (hiddenView.getVisibility() == View.VISIBLE) {
                // The transition of the hiddenView is carried out
                // by the TransitionManager class.
                // Here we use an object of the AutoTransition
                // Class to create a default transition.
                TransitionManager.beginDelayedTransition(baseView, transition);
                hiddenView.setVisibility(View.GONE);
                ArrowButton.setImageResource(R.drawable.ic_baseline_expand_more_24);
            }
            // If the CardView is not expanded, set its visibility
            // to visible and change the expand more icon to expand less.
            else {
                TransitionManager.beginDelayedTransition(baseView, transition);
                hiddenView.setVisibility(View.VISIBLE);
                ArrowButton.setImageResource(R.drawable.ic_baseline_expand_less_24);
            }
        });

        ScanDeviceBtn.setOnClickListener(view -> {
            boolean isDisposed;
            if (scanDisposable != null){
                isDisposed = scanDisposable.isDisposed();
            } else {
                isDisposed = true;
            }
            if (isDisposed) {
                displayMessageTxtView.setText(R.string.scanDevicesMsg);
                DevicesStatusProgressBar.setVisibility(View.VISIBLE);
                scanDisposable = api.searchForDevice()
                        .observeOn(AndroidSchedulers.mainThread())
                        .forEach(new Consumer<PolarDeviceInfo>() {
                            @RequiresApi(api = Build.VERSION_CODES.N)
                            @Override
                            public void accept(PolarDeviceInfo foundPolarDeviceInfo) throws Throwable {
                                if (availableDevices.isEmpty()){
                                    deviceFoundProcedure(foundPolarDeviceInfo);
                                } else {
                                    if (availableDevices.stream().noneMatch(polarDeviceInfo -> polarDeviceInfo.deviceId.equals(foundPolarDeviceInfo.deviceId))){
                                        deviceFoundProcedure(foundPolarDeviceInfo);
                                    }
                                }
                            }
                        });
            } else {
                scanDisposable.dispose();
            }
        });
    }

    private void initializeApi(){
        api.setPolarFilter(false);
        api.setApiLogger(new PolarBleApi.PolarBleApiLogger() {
            @Override
            public void message(@NonNull String s) {
                Log.d("API_LOGGER_TAG", s);
            }
        });

        api.setApiCallback(new PolarBleApiCallback() {

            @Override
            public void blePowerStateChanged(boolean powered) {
                Toast.makeText(MainActivity.this, "Bluetooth activated: " + powered, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                deviceConnected = true;
                deviceIDConnected = polarDeviceInfo.deviceId;
                recyclerViewItemLayout = itemViewClicked.itemView.findViewById(R.id.recyclerViewItemLayout);
                TextView deviceConnectivityIndicatorTxtView = new TextView(MainActivity.this);
                deviceConnectivityIndicatorTxtView.setText(R.string.connected_indicator);
                deviceConnectivityIndicatorTxtView.setTextColor(getResources().getColor(R.color.green));
                recyclerViewItemLayout.addView(deviceConnectivityIndicatorTxtView);
                displayMessageTxtView.setText("");
                DevicesStatusProgressBar.setVisibility(View.INVISIBLE);
                Button disconnectDeviceBtn = new Button(MainActivity.this);
                disconnectDeviceBtn.setText(R.string.disconnect);
                disconnectDeviceBtn.setOnClickListener(view -> {
                    try {
                        api.disconnectFromDevice(polarDeviceInfo.deviceId);
                    } catch (PolarInvalidArgument polarInvalidArgument) {
                        polarInvalidArgument.printStackTrace();
                    }
                });
                recyclerViewItemLayout.addView(disconnectDeviceBtn);
                whatsConnectedIndicator.setText("Device: " + polarDeviceInfo.deviceId);
                //Toast.makeText(MainActivity.this, "Connected to " + polarDeviceInfo.deviceId, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceConnecting(@NonNull PolarDeviceInfo polarDeviceInfo) {
                displayMessageTxtView.setText("Connecting to " + polarDeviceInfo.deviceId);
                DevicesStatusProgressBar.setVisibility(View.VISIBLE);
                //Toast.makeText(MainActivity.this, "Connecting to " + polarDeviceInfo.deviceId, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceDisconnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                deviceConnected = false;
                deviceIDConnected = null;
                recyclerViewItemLayout.removeViewAt(1);
                recyclerViewItemLayout.removeViewAt(1);
                heartRateTextView.setText(R.string.emptyHRText);
                textViewRR.setText("");
                whatsConnectedIndicator.setText(R.string.no_connected_devices);
                RRIntervals.clear();
                Toast.makeText(MainActivity.this, "Disconnected from " + polarDeviceInfo.deviceId, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void streamingFeaturesReady(@NonNull final String identifier,
                                               @NonNull final Set<PolarBleApi.DeviceStreamingFeature> features) {
                for(PolarBleApi.DeviceStreamingFeature feature : features) {
                    Toast.makeText(MainActivity.this, "Streaming feature " + feature.toString() + " is ready", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void hrFeatureReady(@NonNull String identifier) {
                Toast.makeText(MainActivity.this, "HR sensor Ready " + identifier, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void disInformationReceived(@NonNull String identifier, @NonNull UUID uuid, @NonNull String value) {
            }

            @Override
            public void batteryLevelReceived(@NonNull String identifier, int level) {
            }

            @RequiresApi(api = Build.VERSION_CODES.O)
            @SuppressLint("SimpleDateFormat")
            @Override
            public void hrNotificationReceived(@NonNull String identifier, @NonNull PolarHrData data) {
                heartRateTextView.setText("");
                heartRateTextView.setText(new StringBuilder().append(data.hr));
                database.getReference()
                        .child("Users")
                        .child(Objects.requireNonNull(mAuth.getCurrentUser()).getDisplayName() + " - " + mAuth.getCurrentUser().getUid())
                        .child(new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                        .child("Device ID - " + deviceIDConnected)
                        .child("HeartRateData")
                        .child(Calendar.getInstance().getTime().toString())
                        .setValue(data.hr);
                if (!data.rrsMs.isEmpty()) {
                    String rrText = String.join("ms, ", data.rrsMs.toString()) + "ms";
                    textViewRR.setText(rrText);
                    RRIntervals.addAll(data.rrsMs);
                }
                plotter.addValues(data);
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
            }
        });
    }

    private void createPlot(){
        plotter = new HrAndRrPlotter();
        plotter.setListener(MainActivity.this);
        plot.addSeries(plotter.getHrSeries(), plotter.getHrFormatter());
        plot.addSeries(plotter.getRrSeries(), plotter.getRrFormatter());
        plot.setRangeBoundaries(50, 100, BoundaryMode.AUTO);
        plot.setDomainBoundaries(0, 360000, BoundaryMode.AUTO);
        // Left labels will increment by 10
        plot.setRangeStep(StepMode.INCREMENT_BY_VAL, 10.0);
        plot.setDomainStep(StepMode.INCREMENT_BY_VAL, 60000.0);
        // Make left labels be an integer (no decimal places)
        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).setFormat(new DecimalFormat("#"));
        // These don't seem to have an effect
        plot.setLinesPerRangeLabel(2);
    }

    private void requestPermissions(){
        ArrayList<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
                permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
            } else {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
                permissions.add(Manifest.permission.ACTIVITY_RECOGNITION);
            }
        } else {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions.toArray(new String[0]), 1);
        }
    }

    private void updateUI(FirebaseUser user){
        signInBtn.setVisibility(View.GONE);
        appDescriptionTxtView.setVisibility(View.GONE);
        signOutBtn.setVisibility(View.VISIBLE);
        userConstraintLayout.setVisibility(View.VISIBLE);
        usernameDisplayTextView.setText(user.getDisplayName());
    }

    private void blockAppFunctions() {
        ScanDeviceBtn.setActivated(false);
        api.shutDown();
    }

    private void deviceFoundProcedure(PolarDeviceInfo polarDeviceInfo) {
        displayMessageTxtView.setText(R.string.DevicesFoundMsg);
        DevicesStatusProgressBar.setVisibility(View.INVISIBLE);
        availableDevices.add(polarDeviceInfo);
        adapter.notifyItemInserted(availableDevices.size() -1);
    }

    @Override
    public void onItemClick(View view, int position) {
        try {
            if (deviceConnected){
                Toast.makeText(this, "Already Connected", Toast.LENGTH_SHORT).show();
            } else {
                api.connectToDevice(adapter.getItem(position));
                itemViewClicked = recyclerView.findViewHolderForAdapterPosition(position);
            }
        } catch (PolarInvalidArgument polarInvalidArgument) {
            polarInvalidArgument.printStackTrace();
            Toast.makeText(this, "Something went wrong while connecting", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void update() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                plot.redraw();
            }
        });
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (running) {
            totalSteps = sensorEvent.values[0];
            if (previousTotalSteps == 0){
                previousTotalSteps = totalSteps;
                SharedPreferences sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putFloat("key1", previousTotalSteps);
                editor.apply();
            }
            if (totalSteps<previousTotalSteps){
                previousTotalSteps = 0;
                SharedPreferences sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putFloat("key1", previousTotalSteps);
                editor.apply();
            }
            // Current steps are calculated by taking the difference of total step and previous steps
            int currentSteps = (int) totalSteps - (int) previousTotalSteps;
            // It will show the current steps to the user
            stepsTxtView.setText(String.valueOf(currentSteps));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // We do not have to write anything in this function for this app
    }

    private void saveData() {
        // Shared Preferences will allow us to save and retrieve data in the form of key,value pair.
        SharedPreferences sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putFloat("key1", previousTotalSteps);
        editor.apply();
    }

    private void loadData() {
        SharedPreferences sharedPreferences = getSharedPreferences("myPrefs", Context.MODE_PRIVATE);
        if (sharedPreferences.contains("key1")){
            previousTotalSteps = sharedPreferences.getFloat("key1", 0f);
        }
    }
}