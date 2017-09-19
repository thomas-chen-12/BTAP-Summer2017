package com.gatechvip.btap.summer17clean;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaRecorder;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class NamingActivity extends AppCompatActivity {
    // Parameters from settings
    private String curSubjectID, folderPath;
    private int numQuestions, timer, curQuestion, numHintsPressed;
    private String ACCESS_TOKEN;

    // Filters for file I/O
    private static FilenameFilter csvFilter = new FilenameFilter() {
        @Override
        public boolean accept(File file, String s) {
            int i = s.lastIndexOf('.');
            return i > 0 && s.substring(i + 1).toLowerCase().equals("csv");
        }
    };
    private static FilenameFilter audioFilter = new FilenameFilter() {
        @Override
        public boolean accept(File file, String s) {
            int i = s.lastIndexOf('.');
            return i > 0 && s.substring(i + 1).toLowerCase().equals("3gp");
        }
    };

    // View Objects
    private ArrayList<Long> timeStamps = new ArrayList<>();
    private ArrayList<Integer> hintTimes = new ArrayList<>();
    private ArrayList<String> picFilePaths;
    private ImageButton btnNext, btnHint;
    private ImageView ivPic;
    private ListView lvTest; // TODO: TEST ONLY, remove when finished

    // Other Objects
    private Date startingTime;
    private MediaRecorder recorder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_naming);
        curQuestion = 0;

        retrieveAccessToken();

        retrieveSettings();
        picFilePaths = getPictureFilePathsFromSetting();

        startingTime = new Date();
        timeStamps.add(0L);
        startRecording();

        ivPic = (ImageView) findViewById(R.id.ivNamingPicture);
        showNextPicture();


        btnNext = (ImageButton) findViewById(R.id.btnNextNaming);
        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (curQuestion < numQuestions-1) {
                    curQuestion++;
                    hintTimes.add(numHintsPressed);
                    numHintsPressed = 0;
                    showNextPicture();
                    appendTimeStamp();
                } else {
                    hintTimes.add(numHintsPressed);
                    stopRecording();
                    saveTimeStamps();
                    uploadFilesInThisFolderPath();
                    finish();
                }
            }
        });

        numHintsPressed = 0;
        btnHint = (ImageButton) findViewById(R.id.btnHint);
        btnHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                numHintsPressed++;
            }
        });

//        lvTest = (ListView) findViewById(R.id.lvTest);
//        ArrayList<String> askedMonoLowQuestions = new ArrayList<>((getAskedQuestionsSet(1)));
//        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, askedMonoLowQuestions);
//        lvTest.setAdapter(adapter);
    }

    private void retrieveSettings() {
        // Retrieve Subject ID
        SharedPreferences subjectIDPref = getSharedPreferences(
                getString(R.string.preference_subject_id_file_key), Context.MODE_PRIVATE);
        curSubjectID = subjectIDPref.getString(
                getString(R.string.preference_subject_id_key), null);

        // Retrieve Number of Questions, Timer
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        numQuestions = Integer.parseInt(
                sharedPref.getString(getString(R.string.pref_naming_question_amount_key), "20"));
        timer = Integer.parseInt(
                sharedPref.getString(getString(R.string.pref_naming_timer_key), "30"));
    }

    private void showNextPicture() {
        ivPic.setImageBitmap(getBitmapFromAssetAddress(picFilePaths.get(curQuestion)));
    }

    private void appendTimeStamp() {
        Date now = new Date();
        timeStamps.add((now.getTime() - startingTime.getTime()) / 1000);
    }

    private ArrayList<String> getPictureFilePathsFromSetting() {
        // Initialize the ArrayList to return
        ArrayList<String> picturePaths = new ArrayList<>();
        // Get indexes from settings
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> difficultySelected = sharedPref.getStringSet(
                getString(R.string.pref_naming_difficulty_selected_key),
                new HashSet<String>(Arrays.asList(getResources().getStringArray(R.array.naming_difficulty_default_values))));
        String[] indices = difficultySelected.toArray(new String[difficultySelected.size()]);
        // The number of indexes indicates how many folders to look into
        String[] folderPaths = new String[indices.length];
        ArrayList<Set<String>> askedQuestions = new ArrayList<>();
        for (int i = 0; i < folderPaths.length; i++) {
            int index = Integer.parseInt(indices[i]);
            folderPaths[i] = getResources().getStringArray(R.array.naming_difficulty_paths)[index];
            askedQuestions.add(i, getAskedQuestionsSet(index));
        }
        // Divide
        int questionsInEachFolder = numQuestions / indices.length;

        for (int i = 0; i < folderPaths.length; i++) {
            String thisFolder = folderPaths[i];
            Set<String> askedQuestionsInThisDifficulty = askedQuestions.get(i);
            ArrayList<String> unaskedPathsInThisFolder = new ArrayList<>();
            String[] filenames = getFilenamesFromAssetFolder(thisFolder);

            // Down sampling
            Set<String> askedQuestionsNew = new HashSet<>();
            askedQuestionsNew.addAll(askedQuestionsInThisDifficulty);
            if (filenames.length - askedQuestionsInThisDifficulty.size() < questionsInEachFolder) {
                // If there are less unasked questions than what we need
                askedQuestionsNew.clear();
                unaskedPathsInThisFolder.addAll(Arrays.asList(filenames));
            } else {
                for (String file : filenames) {
                    if (!askedQuestionsNew.contains(file)) {
                        unaskedPathsInThisFolder.add(file);;
                    }
                }
            }

            // Randomly select from unasked questions that are in this folder
            Collections.shuffle(unaskedPathsInThisFolder);
            for (int j = 0; j < questionsInEachFolder; j ++) {
                picturePaths.add(unaskedPathsInThisFolder.get(j));
                askedQuestionsNew.add(unaskedPathsInThisFolder.get(j));
            }

            // Update Preference
            updateAskedQuestionsSet(askedQuestionsNew, Integer.parseInt(indices[i]));
        }
        Collections.shuffle(picturePaths);

        // In case numQuestions cannot be divided evenly by indices.length
        numQuestions = picturePaths.size();

        return picturePaths;
    }

    private void startRecording() {
        folderPath =
                Environment.getExternalStorageDirectory() + File.separator +
                "BTAP - Naming Responses" + File.separator +
                curSubjectID + File.separator +
                getMonthDayYearStamp();
        File folder = new File(folderPath);
        folder.mkdirs();
        String fileName = "Record " + (folder.listFiles(audioFilter).length + 1) + ".3gp";

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setOutputFile(folderPath + File.separator + fileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        try {
            recorder.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        recorder.start();
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }

    private void saveTimeStamps() {
        File folder = new File(folderPath);
        String fileName = "Time Stamp " + (folder.listFiles(csvFilter).length + 1) + ".csv";
        try {
            FileWriter writer = new FileWriter(folderPath + File.separator + fileName);

            String s = "";
            for (int i = 0; i < picFilePaths.size(); i++) {
                String filename = getNameFromPath(picFilePaths.get(i));
                String stamp = timeStamps.get(i).toString();
                s += "Question #" + i + ", " +
                        filename + ", " +
                        stamp + "s, " +
                        hintTimes.get(i) + " hint(s)\n";
            }
            writer.write(s);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Return a time stamp of this format: "05-12-2017"
     */
    private String getMonthDayYearStamp() {
        GregorianCalendar calendar = new GregorianCalendar();
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int year = calendar.get(Calendar.YEAR);
        return month + "-" + day + "-" + year;
    }

    private Bitmap getBitmapFromAssetAddress(@NonNull String address) {
        try {
            InputStream is = getAssets().open(address);
            return BitmapFactory.decodeStream(is);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String[] getFilenamesFromAssetFolder(@NonNull String folder) {
        ArrayList<String> toReturn = new ArrayList<>();
        try {
            String[] list = getAssets().list(folder);
            if (list.length > 0) {
                // This is an actual folder
                for (String path : list) {
                    String[] filenamesInThisFolder = getFilenamesFromAssetFolder(folder + "/" + path);
                    Collections.addAll(toReturn, filenamesInThisFolder);
                }
            } else {
                // This is a filename
                toReturn.add(folder);
                return toReturn.toArray(new String[toReturn.size()]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return toReturn.toArray(new String[toReturn.size()]);
    }

    private String getNameFromPath(@NonNull String path) {
        int i = path.lastIndexOf('/');
        int j = path.lastIndexOf('.');
        return path.substring(i + 1, j);
    }

    private Set<String> getAskedQuestionsSet(int index) {
        SharedPreferences askedQuestionsPref = getSharedPreferences(
            getString(R.string.preference_asked_questions_file_key), Context.MODE_PRIVATE);
        if (index == 0) {
            return askedQuestionsPref.getStringSet(
                    getString(R.string.preference_asked_mono_hi_key), new HashSet<String>());
        } else if (index == 1) {
            return askedQuestionsPref.getStringSet(
                    getString(R.string.preference_asked_mono_lo_key), new HashSet<String>());
        } else if (index == 2) {
            return askedQuestionsPref.getStringSet(
                    getString(R.string.preference_asked_bi_hi_key), new HashSet<String>());
        } else if (index == 3) {
            return askedQuestionsPref.getStringSet(
                    getString(R.string.preference_asked_bi_lo_key), new HashSet<String>());
        } else if (index == 4) {
            return askedQuestionsPref.getStringSet(
                    getString(R.string.preference_asked_multi_key), new HashSet<String>());
        } else {
            return null;
        }
    }

    private void updateAskedQuestionsSet(Set<String> set, int index) {
        SharedPreferences askedQuestionsPref = getSharedPreferences(
                getString(R.string.preference_asked_questions_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = askedQuestionsPref.edit();
        if (index == 0) {
            editor.putStringSet(
                    getString(R.string.preference_asked_mono_hi_key), set);
        } else if (index == 1) {
            editor.putStringSet(
                    getString(R.string.preference_asked_mono_lo_key), set);
        } else if (index == 2) {
            editor.putStringSet(
                    getString(R.string.preference_asked_bi_hi_key), set);
        } else if (index == 3) {
            editor.putStringSet(
                    getString(R.string.preference_asked_bi_lo_key), set);
        } else if (index == 4) {
            editor.putStringSet(
                    getString(R.string.preference_asked_multi_key), set);
        }
        editor.apply();
    }

    private void retrieveAccessToken() {
        SharedPreferences accessTokenPref = getSharedPreferences(
                getString(R.string.preference_dropbox_pref_file_key), Context.MODE_PRIVATE);
        ACCESS_TOKEN = accessTokenPref.getString(
                getString(R.string.preference_dropbox_access_token_key), null);
    }

    private void uploadFilesInThisFolderPath() {
        if (ACCESS_TOKEN == null) {
            return;
        } else {
            File folder = new File(folderPath);
            File[] filesToUpload = folder.listFiles();
            String folderName = folderPath.substring(folderPath.indexOf("BTAP"));
            for (File file : filesToUpload) {
                new UploadTask(DropboxClient.getClient(ACCESS_TOKEN), file, folderName, getApplicationContext())
                        .execute();
            }
        }
    }

    // Prevent user from exiting the test.
    @Override
    public void onBackPressed() {

    }
}