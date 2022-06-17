package com.example.polardatamanagement;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Collections;
import java.util.List;

public class StartActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private Button signInBtn;
    List<AuthUI.IdpConfig> providers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        mAuth = FirebaseAuth.getInstance();
        signInBtn = findViewById(R.id.signInBtn);
        providers = Collections.singletonList(new AuthUI.IdpConfig.EmailBuilder().build());

        initializeListeners();
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
            AlertDialog.Builder alert = new AlertDialog.Builder(StartActivity.this);
            assert user != null;
            alert.setMessage("Welcome " + user.getDisplayName());
            alert.setPositiveButton("Hi", (dialogInterface, i) -> updateUI(user));
            alert.setOnDismissListener(dialogInterface -> updateUI(user));
            AlertDialog dialog = alert.create();
            dialog.getWindow().getAttributes().windowAnimations = R.style.SlidingDialogAnimation;
            dialog.show();
        } else {
            Toast.makeText(StartActivity.this, "Sign In failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void initializeListeners() {
        signInBtn.setOnClickListener(view -> {
            Intent signInIntent = AuthUI.getInstance()
                    .createSignInIntentBuilder()
                    .setAvailableProviders(providers)
                    .build();
            signInLauncher.launch(signInIntent);
        });
    }

    private void updateUI(FirebaseUser user){
        Intent intent = new Intent(StartActivity.this, MainActivity.class);
        //myIntent.putExtra("key", value); //Optional parameters
        StartActivity.this.startActivity(intent);
        finish();
    }
}