package com.example.polardatamanagement;

import static android.content.ContentValues.TAG;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.polardatamanagement.Utilities.RecyclerViewAdapter;
import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.polar.sdk.api.PolarBleApi;
import com.polar.sdk.api.PolarBleApiCallback;
import com.polar.sdk.api.PolarBleApiDefaultImpl;
import com.polar.sdk.api.errors.PolarInvalidArgument;
import com.polar.sdk.api.model.PolarDeviceInfo;
import com.polar.sdk.api.model.PolarHrBroadcastData;
import com.polar.sdk.api.model.PolarHrData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Consumer;

public class MainActivity extends AppCompatActivity implements RecyclerViewAdapter.ItemClickListener {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private FirebaseAuth mAuth;
    private FirebaseDatabase database;
    private ProgressBar DevicesStatusProgressBar;
    private Disposable scanDisposable = null;
    private Disposable broadcastDisposable = null;
    private boolean deviceConnected = false;
    //private String deviceId = "73B38923";
    private List<PolarDeviceInfo> availableDevices;

    private Button signInBtn;
    private Button signOutBtn;
    private Button ScanDeviceBtn;
    private ImageButton ArrowButton;

    private TextView appDescriptionTxtView;
    private TextView displayMessageTxtView;
    private TextView heartRateTextView;
    private TextView whatsConnectedIndicator;

    private String email;
    private String password;

    ConstraintLayout userConstraintLayout;
    LinearLayout hiddenView;
    LinearLayout baseView;

    LinearLayout recyclerViewItemLayout;
    RecyclerView recyclerView;
    RecyclerViewAdapter adapter;
    RecyclerView.ViewHolder itemViewClicked;

    PolarBleApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Polar & Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        api = PolarBleApiDefaultImpl.defaultImplementation(getApplicationContext(),  PolarBleApi.ALL_FEATURES);
        // Initialize Buttons
        signInBtn = findViewById(R.id.signInBtn);
        signOutBtn = findViewById(R.id.signOutBtn);
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
        availableDevices = new ArrayList<>();
        //Initialize RecyclerView
        recyclerView = findViewById(R.id.devicesRclView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RecyclerViewAdapter(this, availableDevices);
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);

        database = FirebaseDatabase.getInstance(getString(R.string.database_URL));

        List<AuthUI.IdpConfig> providers = Arrays.asList(
                new AuthUI.IdpConfig.EmailBuilder().build());

        signInBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent signInIntent = AuthUI.getInstance()
                        .createSignInIntentBuilder()
                        .setAvailableProviders(providers)
                        .build();
                signInLauncher.launch(signInIntent);
            }
        });

        signOutBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AuthUI.getInstance()
                        .signOut(MainActivity.this)
                        .addOnCompleteListener(new OnCompleteListener<Void>() {
                            public void onComplete(@NonNull Task<Void> task) {
                                signInBtn.setVisibility(View.VISIBLE);
                                appDescriptionTxtView.setVisibility(View.VISIBLE);
                                signOutBtn.setVisibility(View.INVISIBLE);
                                userConstraintLayout.setVisibility(View.GONE);
                            }
                        });
            }
        });

        initializeApi();

        boolean isBroadCastDisposed;
        if (broadcastDisposable != null){
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
                                if (availableDevices.stream().noneMatch(polarDeviceInfo -> polarDeviceInfo.deviceId.equals(polarHrBroadcastData.polarDeviceInfo.deviceId))){
                                    deviceFoundProcedure(polarHrBroadcastData.polarDeviceInfo);
                                }
                            }
                        }
                    });
        } else {
            broadcastDisposable.dispose();
        }

        ArrowButton.setOnClickListener(view -> {
            if (hiddenView.getVisibility() == View.VISIBLE) {
                // The transition of the hiddenView is carried out
                // by the TransitionManager class.
                // Here we use an object of the AutoTransition
                // Class to create a default transition.
                TransitionManager.beginDelayedTransition(baseView,
                        new AutoTransition());
                hiddenView.setVisibility(View.GONE);
                ArrowButton.setImageResource(R.drawable.ic_baseline_expand_more_24);
            }
            // If the CardView is not expanded, set its visibility
            // to visible and change the expand more icon to expand less.
            else {
                TransitionManager.beginDelayedTransition(baseView,
                        new AutoTransition());
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, 1);
                } else {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                }
            }
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if(currentUser != null){
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
        api.foregroundEntered();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        api.shutDown();
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
            updateUI(user);
        } else {
            Toast.makeText(MainActivity.this, "Sign In failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI(FirebaseUser user){
        signInBtn.setVisibility(View.GONE);
        appDescriptionTxtView.setVisibility(View.GONE);
        signOutBtn.setVisibility(View.VISIBLE);
        userConstraintLayout.setVisibility(View.VISIBLE);
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
                Toast.makeText(MainActivity.this, "BLE power " + powered, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void deviceConnected(@NonNull PolarDeviceInfo polarDeviceInfo) {
                deviceConnected = true;
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
                recyclerViewItemLayout.removeViewAt(1);
                recyclerViewItemLayout.removeViewAt(1);
                heartRateTextView.setText(R.string.emptyHRText);
                whatsConnectedIndicator.setText(R.string.no_connected_devices);
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

            @SuppressLint("SimpleDateFormat")
            @Override
            public void hrNotificationReceived(@NonNull String identifier, @NonNull PolarHrData data) {
                heartRateTextView.setText("");
                heartRateTextView.setText(new StringBuilder().append(data.hr));
                database.getReference()
                        .child("Users")
                        .child(Objects.requireNonNull(mAuth.getCurrentUser()).getDisplayName() + " - " + mAuth.getCurrentUser().getUid())
                        .child("HeartRateData")
                        .child(new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                        .child(Calendar.getInstance().getTime().toString())
                        .setValue(data.hr);
            }

            @Override
            public void polarFtpFeatureReady(@NonNull String s) {
            }
        });
    }
}