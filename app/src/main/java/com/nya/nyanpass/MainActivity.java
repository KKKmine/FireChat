package com.nya.nyanpass;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    public static final String VISION = "0.1.0 alpha";
    private static final int RC_SIGN_IN = 1;
    private static final String LOGFLAG = "LOGFLAG";

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    final FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference ref = database.getReference("nyanpass_chat/Chat_room");
    private ChildEventListener mChildEventListener;
    private GoogleApiClient mGoogleApiClient;

    private EditText editText;
    private TextView chatView;
    private SignInButton signinBtn;
    private Button logoutBtn;
    private Button sendBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = (EditText)findViewById(R.id.editText);
        chatView = (TextView)findViewById(R.id.chatView);
        chatView.setMovementMethod(new ScrollingMovementMethod());
        signinBtn = (SignInButton)findViewById(R.id.signinBtn);
        logoutBtn = (Button) findViewById(R.id.logoutBtn);
        sendBtn = (Button) findViewById(R.id.sendBtn);

        // Configure Google Sign In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this, new GoogleApiClient.OnConnectionFailedListener(){
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_LONG).show();
                    }
                })
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        // Set Button Listener
        signinBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                signIn();
            }
        });
        logoutBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                signOut();
            }
        });

        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if (user != null) {
                    // User is signed in
                    Log.d(LOGFLAG, "onAuthStateChanged:signed_in:" + user.getUid());
                    updateUI(mAuth.getCurrentUser());
                    Map<String, Object> users = new HashMap<String, Object>();
                    users.put(new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date()),
                            new Message("System",
                                    mAuth.getCurrentUser().getDisplayName() + " Login",
                                    new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new java.util.Date())));
                    ref.updateChildren(users);
                    editText.setText("");
                } else {
                    // User is signed out
                    Log.d(LOGFLAG, "onAuthStateChanged:signed_out");
                    updateUI(null);
                }
                // ...
            }
        };

        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String prevChildKey) {
                Message data = dataSnapshot.getValue(Message.class);
                chatView.append(data.sender + " (" + data.send_time + ") :\n" + data.content + "\n\n");
            }

            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String prevChildKey) {}

            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String prevChildKey) {}

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(mAuthListener);
        ref.addChildEventListener(mChildEventListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
        if (mChildEventListener != null) {
            ref.removeEventListener(mChildEventListener);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
                updateUI(mAuth.getCurrentUser());
            } else {
                // Google Sign In failed, update UI appropriately
                updateUI(null);
            }
        }
    }

    public void OnSendButtonClick(View view) {
        if (editText.getText().toString() != "") {
            Map<String, Object> users = new HashMap<String, Object>();
            users.put(new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date()),
                    new Message(mAuth.getCurrentUser().getDisplayName(),
                            editText.getText().toString(),
                            new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new java.util.Date())));
            ref.updateChildren(users);
            editText.setText("");
        }
    }

    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut(){
        mAuth.signOut();
    }

    private void updateUI(FirebaseUser user){
        if(user != null) {
            sendBtn.setEnabled(true);
            logoutBtn.setVisibility(View.VISIBLE);
            signinBtn.setVisibility(View.INVISIBLE);
        }
        else {
            sendBtn.setEnabled(false);
            logoutBtn.setVisibility(View.INVISIBLE);
            signinBtn.setVisibility(View.VISIBLE);
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(LOGFLAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUI(user);
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(LOGFLAG, "signInWithCredential:failure", task.getException());
                            Toast.makeText(MainActivity.this, "Authentication failed.",
                                    Toast.LENGTH_SHORT).show();
                            updateUI(null);
                        }
                    }
                });
    }
}

class Message {
    public String sender;
    public String content;
    public String send_time;


    public Message() {}

    public Message(String s, String c, String t) {
        sender = s;
        content = c;
        send_time = t;
    }
}
