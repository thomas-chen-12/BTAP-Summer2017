package com.gatechvip.btap.summer17clean;

import android.app.ActionBar;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.users.FullAccount;

public class ClinicianActivity extends AppCompatActivity {

    private Button btnSetUpDropbox, btnModuleSettings, btnSetID;
    private Context context;
    private String ACCESS_TOKEN;

    @Override
    protected void onResume() {
        super.onResume();
        getAccessToken();
        getUserAccount();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clinician);
        setupActionBar();

        context = this;

        btnSetUpDropbox = (Button) findViewById(R.id.btnSetUpDropbox);
        btnSetUpDropbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Open the Dropbox AuthActivity. In AuthActivity the user must confirm his/her
                // Dropbox account.
                Auth.startOAuth2Authentication(
                        getApplicationContext(), getString(R.string.APP_KEY));
            }
        });

        btnSetID = (Button) findViewById(R.id.btnSetID);
        btnSetID.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);

                // Set Up Title
                builder.setTitle("Set Subject ID");

                // Set Up Input
                final EditText etNewID = new EditText(context);
                etNewID.setInputType(InputType.TYPE_CLASS_TEXT);
                final SharedPreferences subjectIDPref = getSharedPreferences(
                        getString(R.string.preference_subject_id_file_key),
                        Context.MODE_PRIVATE);
                String curSubjectID = subjectIDPref.getString(
                        getString(R.string.preference_subject_id_key), null);
                if (curSubjectID != null) {
                    etNewID.setText(curSubjectID);
                }
                builder.setView(etNewID);

                // Set Up Buttons
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String subjectID = etNewID.getText().toString();
                        SharedPreferences.Editor editor = subjectIDPref.edit();
                        editor.putString(getString(R.string.preference_subject_id_key), subjectID);
                        editor.commit();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });

                builder.show();
            }
        });

        btnModuleSettings = (Button) findViewById(R.id.btnModuleSettings);
        btnModuleSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(context, ModuleSettingsActivity.class));
            }
        });
    }

    private void setupActionBar() {
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void getAccessToken() {
        String accessToken = Auth.getOAuth2Token();
        if (accessToken != null) {
            SharedPreferences pref = getSharedPreferences(
                    getString(R.string.preference_dropbox_pref_file_key),
                    Context.MODE_PRIVATE);
            pref.edit().putString(
                    getString(R.string.preference_dropbox_access_token_key), accessToken).apply();

            // Proceed to main activity??

            //Test
            ACCESS_TOKEN = accessToken;
        }
    }

    // Test
    protected void getUserAccount() {
        if (ACCESS_TOKEN == null) {
            return;
        }
        new UserAccountTask(DropboxClient.getClient(ACCESS_TOKEN), new UserAccountTask.TaskDelegate() {
            @Override
            public void onAccountReceived(FullAccount account) {
                //Print account's info
                Log.d("User email", account.getEmail());
                Log.d("User name", account.getName().getDisplayName());
                Log.d("User account type", account.getAccountType().name());
                //updateUI(account);
            }
            @Override
            public void onError(Exception error) {
                Log.d("User", "Error receiving account details.");
            }
        }).execute();
    }
}
