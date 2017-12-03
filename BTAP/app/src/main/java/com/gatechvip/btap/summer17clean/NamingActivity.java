package com.gatechvip.btap.summer17clean;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public class NamingActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    // Parameters from settings
    private String curSubjectID, folderPath;
    private int numQuestions, timer, curQuestion, numHintsPressed;
    private String ACCESS_TOKEN;

    // Private fields related to timer
    private long startTime = 0;
    private Handler timerHandler = new Handler();
    private Runnable updateTimerThread = new Runnable() {
        @Override
        public void run() {
            long timeInMilliseconds = System.currentTimeMillis() - startTime;

            timeInMilliseconds=(timer)*1000-timeInMilliseconds;
            int seconds = (int) (timeInMilliseconds / 1000);
            seconds = seconds % 60;
            int centiseconds = (int) (timeInMilliseconds % 1000) / 10;


            if (seconds ==0 && centiseconds<1) {
                nextQuestion();
            }
            else
            {
                timerHandler.postDelayed(this, 0);
            }

        }
    };

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

    // Other Objects
    private HashMap<String, String> questionCueMap;
    private boolean isMapReady = false;
    private Date startingTime;
    private MediaRecorder recorder;
    private TextToSpeech tts;
    private boolean ttsInProgress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_naming);

        // Initialize TTS
        tts = new TextToSpeech(this, this);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
                ttsInProgress = true;
            }

            @Override
            public void onDone(String s) {
                // This boolean variable is used to prevent stupid user's excessive hacking of the
                // hint button, which results in the same TTS code being executed numerous times.
                ttsInProgress = false;
            }

            @Override
            public void onError(String s) {
                ttsInProgress = false;
            }
        });

        curQuestion = 0;
        new LoadPhonemicCuesTask().execute();

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
                nextQuestion();
            }
        });

        numHintsPressed = 0;
        btnHint = (ImageButton) findViewById(R.id.btnHint);
        btnHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isMapReady && (!ttsInProgress) && (!tts.isSpeaking())) {
                    numHintsPressed++;
                    String question = getNameFromPath(picFilePaths.get(curQuestion));
                    speak(questionCueMap.get(question));
                }
            }
        });

        timerHandler.postDelayed(updateTimerThread, 0);
    }

    private void nextQuestion() {
        if (curQuestion < numQuestions-1) {
            // Reset Timer
            timerHandler.removeCallbacks(updateTimerThread);
            startTime = System.currentTimeMillis();
            timerHandler.postDelayed(updateTimerThread, 0);
            // Record data and update pictures
            curQuestion++;
            hintTimes.add(numHintsPressed);
            numHintsPressed = 0;
            showNextPicture();
            appendTimeStamp();
        } else {
            appendTimeStamp();
            hintTimes.add(numHintsPressed);
            stopRecording();
            saveTimeStamps();
            uploadFilesInThisFolderPath();
            tts.shutdown();
            finish();
        }
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
                new HashSet<String>(
                        Arrays.asList(getResources()
                                .getStringArray(R.array.naming_difficulty_default_values)
                        )
                )
        );
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
                s += "Question #" + (i+1) + ", " +
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
                    String[] filenamesInThisFolder =
                            getFilenamesFromAssetFolder(folder + "/" + path);
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
                new UploadTask(DropboxClient.getClient(ACCESS_TOKEN), file,
                        folderName, getApplicationContext()).execute();
            }
        }
    }

    private void speak(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    // Prevent user from exiting the test.
    @Override
    public void onBackPressed() {

    }

    @Override
    public void onInit(int i) {
        if (i == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
        }
    }

    private class LoadPhonemicCuesTask extends AsyncTask<Void, Void, HashMap<String, String>> {

        @Override
        protected HashMap<String, String> doInBackground(Void... voids) {
            if (isMapReady) {
                return questionCueMap;
            }

            HashMap<String, String> questionCuePairs = new HashMap<>();
            AssetManager am = getAssets();
            InputStream inputStream;
            try {
                inputStream = am.open("CNphon.csv");
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                try {
                    String csvLine;
                    while ((csvLine = reader.readLine()) != null) {
                        String[] row = csvLine.split(",");
                        questionCuePairs.put(row[0], row[1]);
                    }
                inputStream.close();
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            return questionCuePairs;
        }

        @Override
        protected void onPostExecute(HashMap<String, String> stringStringHashMap) {
            questionCueMap = stringStringHashMap;
            isMapReady = true;
        }
    }
}