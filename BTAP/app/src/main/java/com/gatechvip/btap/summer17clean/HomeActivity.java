package com.gatechvip.btap.summer17clean;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.Image;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

public class HomeActivity extends AppCompatActivity {
    private String curSubjectID;
    private Button btnClinician;
    private ImageButton btnOne, btnTwo;
    private TextView tvWelcome;
    private Context context;
    private String scriptFilename = "ImageryScript.wav";
    private MediaPlayer scriptPlayer;
    private AssetFileDescriptor scriptDescriptor;
    private int scriptProgress;
    private final int REQUEST_STORAGE = 0;
    private final int REQUEST_AUDIO_RECORD = 1;
    private String ACCESS_TOKEN;

    @Override
    protected void onResume() {
        super.onResume();
        if (tvWelcome != null) {
            setWelcomeMessage(tvWelcome);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        context = this;

        // Get required dangerous permissions in run time
        if (!hasStoragePermission()) {
            askStoragePermission(REQUEST_STORAGE);
        }
        if (!hasRecordAudioPermission()) {
            askRecordAudioPermission(REQUEST_AUDIO_RECORD);
        }

        // Get access token
        retrieveAccessToken();
        if (needToUpload()) {
            uploadAllFiles();
        }

        // Prepare descriptor
        try {
            scriptDescriptor = getAssets().openFd(scriptFilename);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(context, scriptFilename + " does not exist", Toast.LENGTH_SHORT).show();
        }

        // Instantiate View Objects
        btnOne = (ImageButton) findViewById(R.id.btnOne);
        btnTwo = (ImageButton) findViewById(R.id.btnTwo);
        btnClinician = (Button) findViewById(R.id.btnClinician);
        tvWelcome = (TextView) findViewById(R.id.tvWelcome);

        // Set Welcome Message: "Welcome" if user ID is assigned; otherwise, warning.
        setWelcomeMessage(tvWelcome);

        // Set Button Actions
        btnOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (curSubjectID == null || curSubjectID.length() == 0) {
                    Toast.makeText(context, "Subject ID not set", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent namingActivityIntent = new Intent(context, NamingActivity.class);
                startActivity(namingActivityIntent);
            }
        });

        btnTwo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final ProgressDialog pd = new ProgressDialog(context);
                pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pd.setTitle("Script Playing");
                pd.setMessage("Please Listen And Don't Close Dialog");
                pd.getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor("#AA9BD5E0")));
                pd.setIndeterminate(false);
                pd.setCancelable(false);
                pd.setMax(100);
                pd.show();
                scriptProgress = 0;
                pd.setProgress(scriptProgress);

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        playScript();
                        // Update Progress Dialog
                        final int sleepDuration = scriptPlayer.getDuration() / 100;
                        while(scriptProgress <= pd.getMax()) {
                            if (scriptProgress == pd.getMax()) {
                                pd.dismiss();
                            }
                            try {
                                Thread.sleep(sleepDuration);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            scriptProgress += 1;
                            pd.setProgress(scriptProgress);
                        }
                    }
                }).start();
            }
        });

        btnClinician.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent logInIntent = new Intent(context, LoginActivity.class);
                startActivity(logInIntent);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (scriptPlayer != null) {
            scriptPlayer.stop();
            scriptPlayer.release();
            scriptPlayer = null;
        }
        super.onBackPressed();
    }

    private boolean hasStoragePermission() {
        int storagePermissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        return (storagePermissionCheck == PackageManager.PERMISSION_GRANTED);
    }

    private boolean hasRecordAudioPermission() {
        int audioPermissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO);
        return (audioPermissionCheck == PackageManager.PERMISSION_GRANTED);
    }

    private void askStoragePermission(final int requestCode) {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle("Permission Necessary");
            alertBuilder.setMessage("BTAP needs to access and write to the external storage in " +
                    "order for you to add more questions to each module.");
            alertBuilder.setPositiveButton("Yes, Grant Permission", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ActivityCompat.requestPermissions((Activity) context,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            requestCode);
                }
            });
            AlertDialog alert = alertBuilder.create();
            alert.show();
        } else {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    requestCode);
        }
    }

    private void askRecordAudioPermission(final int requestCode) {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setCancelable(true);
            alertBuilder.setTitle("Permission Necessary");
            alertBuilder.setMessage("BTAP needs to record audio to the external storage in " +
                    "order to complete the functions.");
            alertBuilder.setPositiveButton("Yes, Grant Permission", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ActivityCompat.requestPermissions((Activity) context,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            requestCode);
                }
            });
            AlertDialog alert = alertBuilder.create();
            alert.show();
        } else {
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    requestCode);
        }
    }

    private void playScript() {
        scriptPlayer = new MediaPlayer();
        try {
            scriptPlayer.setDataSource(scriptDescriptor.getFileDescriptor(),
                    scriptDescriptor.getStartOffset(),
                    scriptDescriptor.getLength());
            scriptPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        scriptPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                mediaPlayer.release();
            }
        });
        scriptPlayer.start();
    }

    private void setWelcomeMessage(TextView tv) {
        SharedPreferences subjectIDPref = getSharedPreferences(
                getString(R.string.preference_subject_id_file_key), Context.MODE_PRIVATE);
        curSubjectID = subjectIDPref.getString(
                getString(R.string.preference_subject_id_key), null);
        if (curSubjectID == null || curSubjectID.equals("")) {
            tv.setText(getString(R.string.title_no_id));
            tv.setTextColor(ContextCompat.getColor(context, R.color.colorWarning));
        } else {
            tv.setText(getString(R.string.title_welcome));
            tv.setTextColor(ContextCompat.getColor(context, R.color.colorBlack));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_STORAGE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!hasRecordAudioPermission()) {
                        askRecordAudioPermission(REQUEST_AUDIO_RECORD);
                    }
                } else {
                    Toast.makeText(context, "Be Nice, Grant Permission",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            case REQUEST_AUDIO_RECORD:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(context, "Be Nice, Grant Permission",
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private void retrieveAccessToken() {
        SharedPreferences accessTokenPref = getSharedPreferences(
                getString(R.string.preference_dropbox_pref_file_key), Context.MODE_PRIVATE);
        ACCESS_TOKEN = accessTokenPref.getString(
                getString(R.string.preference_dropbox_access_token_key), null);
    }

    // Determines whether or not we need to upload stuff to Dropbox at homepage
    //      If last upload was successful: don't upload here
    private boolean needToUpload() {
        SharedPreferences DbxPref = context.getSharedPreferences(
                context.getString(R.string.preference_dropbox_pref_file_key), Context.MODE_PRIVATE);
        return !DbxPref.getBoolean(getString(R.string.preference_dropbox_was_last_upload_successful), true);
    }

    private void uploadAllFiles() {
        if (ACCESS_TOKEN == null) {
            return;
        } else {
            File folder = new File(Environment.getExternalStorageDirectory() + File.separator +
                    "BTAP - Naming Responses");
            File[] files = getAllFilesFromFolder(folder);
            for (File file : files) {
                String folderOfThisFile = file.getParentFile().getPath();
                String f = folderOfThisFile.substring(folderOfThisFile.indexOf("BTAP"));
                new UploadTask(DropboxClient.getClient(ACCESS_TOKEN), file, f, getApplicationContext())
                        .execute();
            }
        }
    }

    private File[] getAllFilesFromFolder(File folder) {
        ArrayList<File> listOfFiles = new ArrayList<>();
        if (!folder.isDirectory()) {
            // "folder" is actually a file!
            File[] toReturn = new File[1];
            toReturn[0] = folder;
            return toReturn;
        } else {
            File[] files = folder.listFiles();
            for (File file : files) {
                listOfFiles.addAll(Arrays.asList(getAllFilesFromFolder(file)));
            }
        }
        return listOfFiles.toArray(new File[listOfFiles.size()]);
    }
}