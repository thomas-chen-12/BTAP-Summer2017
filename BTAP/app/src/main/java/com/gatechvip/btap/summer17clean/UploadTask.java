package com.gatechvip.btap.summer17clean;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * Created by Thomas on 5/18/17.
 *
 * Upload tasks happen with the help of InputStream. A specified file is converted to InputStream
 * and with the help of the Core SDK, the stream uploaded to the apps folder in Dropbox.
 */

public class UploadTask extends AsyncTask<Void, Void, Boolean> {

    // Local variables
    private DbxClientV2 dbxClient;
    private String folderName;
    private File file;
    private Context context;

    // Constructor
    public UploadTask(DbxClientV2 dbxClient, File file, String folderName, Context context) {
        this.dbxClient = dbxClient;
        this.folderName = folderName;
        this.file = file;
        this.context = context;
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        try {
            InputStream stream = new FileInputStream(file);
            // Path in the Dropbox
            String destination = "/" + folderName + "/" + file.getName();
            // Upload action
            dbxClient.files().uploadBuilder(destination)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(stream);
            // Save some params
            Date date = new Date();
            SharedPreferences DbxPref = context.getSharedPreferences(
                    context.getString(R.string.preference_dropbox_pref_file_key), Context.MODE_PRIVATE);
            DbxPref.edit()
                    .putBoolean(context.getString(R.string.preference_dropbox_was_last_upload_successful), true)
                    .putLong(context.getString(R.string.preference_dropbox_last_upload_time), date.getTime())
                    .apply();
            return true;
        } catch (DbxException | IOException e) {
            e.printStackTrace();
            SharedPreferences DbxPref = context.getSharedPreferences(
                    context.getString(R.string.preference_dropbox_pref_file_key), Context.MODE_PRIVATE);
            DbxPref.edit()
                    .putBoolean(context.getString(R.string.preference_dropbox_was_last_upload_successful), false)
                    .apply();
            return false;
        }
    }

    // TODO: Remove Toasts when testing is finished
    @Override
    protected void onPostExecute(Boolean b) {
        super.onPostExecute(b);
        if (b) {
            Toast.makeText(context, "Files uploaded", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "Upload failed", Toast.LENGTH_SHORT).show();
        }
    }
}
