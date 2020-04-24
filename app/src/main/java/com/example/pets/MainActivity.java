package com.example.pets;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private List<Pet> petList = new ArrayList<>();
    private String JSONURL;
    private String URL = "https://www.pcs.cnu.edu/~kperkins/pets/";

    private JSONArray jsonArray;

    private OnSharedPreferenceChangeListener preferenceChangeListener;
    private SharedPreferences myPrefs;

    private ImageView petImageview;
    private TextView errorTextView;
    private Spinner spinner;

    /**
     * Simple Pet class for holding pet stuff
     */
    public class Pet {
        String name;
        String file;

        public Pet(String name, String file) {
            this.name = name;
            this.file = file;
        }
    }

    public void processJSON(String string) {
        try {
            petList.clear();
            JSONObject jsonObject = new JSONObject(string);
            jsonArray = jsonObject.getJSONArray(getString(R.string.app_name).toLowerCase());

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject object = jsonArray.getJSONObject(i);
                String name = object.getString("name");
                String file = object.getString("file");

                petList.add(new MainActivity.Pet(name, file));
            }
        }
        catch (Exception e) {
            Log.e("Error", "JSON Produced error");
            spinner.setVisibility(View.INVISIBLE);
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        spinner = (Spinner) findViewById(R.id.spinner);

        petImageview = findViewById(R.id.imageView);
        errorTextView = findViewById(R.id.errorTextView);

        myPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals("URLPref")) {
                    URL = myPrefs.getString(key, getString(R.string.pet_url));
                    Toast.makeText(getApplicationContext(), "URL is " + URL, Toast.LENGTH_SHORT).show();
                    if (!isNetworkConnected()) {
                        petImageview.setImageResource(R.drawable.errorpet);
                        spinner.setVisibility(View.INVISIBLE);
                        errorTextView.setText("Network unreachable! Turn on network to see cuter pets!");
                        errorTextView.setVisibility(View.VISIBLE);
                    }
                    else {
                        spinner.setVisibility(View.VISIBLE);
                        Log.w("Preferences Changed", "trying to download JSON");
                        runDownloadJSON();
                        runDownloadImage("p0.png");
                    }
                }
            }
        };

        myPrefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        if (!isNetworkConnected()) {
            spinner.setVisibility(View.INVISIBLE);
            petImageview.setImageResource(R.drawable.errorpet);
            errorTextView.setText("Network unreachable! Turn on network to see cuter pets!");
            errorTextView.setVisibility(View.VISIBLE);
    }
        else {
            Log.w("OnCreate called", "trying to download JSON");
            spinner.setVisibility(View.VISIBLE);
            runDownloadJSON();
            runDownloadImage("p0.png");
        }
    }

    public boolean isNetworkConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();
        if (info == null) {
            return false;
        }
        return (info.getType() == ConnectivityManager.TYPE_WIFI || info.getState() == NetworkInfo.State.CONNECTED);
    }

    public void runDownloadImage(String imageFile) {
        String fullImageURL = URL + imageFile;
        new DownloadImage().execute(new String[]{fullImageURL});
    }

    public void runDownloadJSON() {
        new DownloadJSON().execute(new String[]{getString(R.string.json)});
    }

    /**
     * Helper method to setup spinner object
     */
    public void setupSpinner() {
        String[] petNames = new String[petList.size()];
        for (int i = 0; i < petList.size(); i++) {
            petNames[i] = petList.get(i).name;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, petNames);

        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public static final int SELECTED_ITEM = 0;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (parent.getChildAt(SELECTED_ITEM) != null) {
                    ((TextView) parent.getChildAt(SELECTED_ITEM)).setTextColor(Color.WHITE);
                    Toast.makeText(MainActivity.this, (String) parent.getItemAtPosition(position), Toast.LENGTH_SHORT).show();
                    if (petList.size() != 0) {
                        runDownloadImage(petList.get(position).file);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, PreferencesActivity.class));
        }
        return super.onOptionsItemSelected(menuItem);
    }


    private class DownloadJSON extends AsyncTask<String, Void, String> {

        private String myURL;
        private int statusCode = 0;
        private String TAG = "DownloadJSON";

        @Override
        protected String doInBackground(String... strings) {
            myURL = strings[0];

            try {
                URL url = new URL(URL + myURL);

                // this does no network IO
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // wrap in finally so that stream bis is sure to close
                // and we disconnect the HttpURLConnection
                BufferedReader in = null;
                try {

                    // this opens a connection, then sends GET & headers
                    connection.connect();

                    // lets see what we got make sure its one of
                    // the 200 codes (there can be 100 of them
                    // http_status / 100 != 2 does integer div any 200 code will = 2
                    statusCode = connection.getResponseCode();
                    if (statusCode / 100 != 2) {
                        Log.e(TAG, "Error-connection.getResponseCode returned "
                                + Integer.toString(statusCode));
                        return null;
                    }

                    in = new BufferedReader(new InputStreamReader(connection.getInputStream()), 8096);

                    // the following buffer will grow as needed
                    String myData;
                    StringBuffer sb = new StringBuffer();

                    while ((myData = in.readLine()) != null) {
                        sb.append(myData);
                        Log.w("Data", myData);
                    }
                    return sb.toString();

                } finally {
                    // close resource no matter what exception occurs
                    in.close();
                    connection.disconnect();
                }
            } catch (Exception exc) {
                return null;
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(String jsonArray) {
            super.onPostExecute(jsonArray);
            processJSON(jsonArray);
            setupSpinner();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
    }


    private class DownloadImage extends AsyncTask<String, Void, Bitmap> {

        private String TAG = "DownloadImage";
        private int statusCode = 0;
        private static final int        DEFAULTBUFFERSIZE = 50;
        private static final int        NODATA = -1;

        @Override
        protected Bitmap doInBackground(String... param) {
            Log.w(TAG, param[0]);
            return downloadBitmap(param[0]);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            super.onPostExecute(result);
            if (result != null) {
                errorTextView.setVisibility(View.INVISIBLE);
                petImageview.setImageBitmap(result);
            }
            else {
                errorTextView.setText("Error 404 " + URL + " is invalid or server is down.");
                errorTextView.setVisibility(View.VISIBLE);
                petImageview.setImageResource(R.drawable.error);
            }
        }

        public Bitmap downloadBitmap(String downloadURL) {
            try {
                java.net.URL url1 = new URL(downloadURL);

                // this does no network IO
                HttpURLConnection connection = (HttpURLConnection) url1.openConnection();

                // this opens a connection, then sends GET & headers
                connection.connect();

                statusCode = connection.getResponseCode();

                if (statusCode / 100 != 2) {
                    Log.e(TAG, "Error-connection.getResponseCode returned "
                            + Integer.toString(statusCode));
                    return null;
                }

                InputStream is = connection.getInputStream();
                BufferedInputStream bis = new BufferedInputStream(is);

                // the following buffer will grow as needed
                ByteArrayOutputStream baf = new ByteArrayOutputStream(DEFAULTBUFFERSIZE);
                int current = 0;

                // wrap in finally so that stream bis is sure to close
                try {
                    while ((current = bis.read()) != NODATA) {
                        baf.write((byte) current);
                    }

                    // convert to a bitmap
                    byte[] imageData = baf.toByteArray();
                    return BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
                } finally {
                    // close resource no matter what exception occurs
                    bis.close();
                }
            } catch (Exception exc) {
                return null;
            }
        }
    }
}
