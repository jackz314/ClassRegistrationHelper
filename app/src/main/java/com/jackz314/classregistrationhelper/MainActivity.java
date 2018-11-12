package com.jackz314.classregistrationhelper;

import android.animation.LayoutTransition;
import android.app.SearchManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.support.v7.widget.SearchView;
import android.widget.Toast;

import com.google.common.collect.Lists;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    static final String TAG = "MainActivity";
    private RecyclerView courseCatalogRecyclerView;
    private ListRecyclerAdapter courseCatalogAdapter;
    SharedPreferences sharedPreferences;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());

        courseCatalogRecyclerView = findViewById(R.id.course_catalog_recycler_view);
        courseCatalogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        new GetCatalogTask().execute();
        courseCatalogAdapter =new ListRecyclerAdapter(new ArrayList<>());//initialize first, wait for download to finish and swap in the data
        courseCatalogRecyclerView.setAdapter(courseCatalogAdapter);
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.search_menu, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.search_view).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(true);
        searchView.setMaxWidth(Integer.MAX_VALUE);
        LinearLayout searchBar = searchView.findViewById(R.id.search_bar);
        searchBar.setLayoutTransition(new LayoutTransition());
        searchView.setQueryHint("Search for classes...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                query(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                query(newText);
                return false;
            }

        });
        return true;
    }

    public void query(String query) {
        courseCatalogAdapter.getFilter().filter(query);
        //System.out.println("calledquery" + " " + text);
    }

    private class GetCatalogTask extends AsyncTask<String, Void, List>{

        @Override
        protected List doInBackground(String... subjectCode) {
            String subjCode = "ALL";
            if(subjectCode != null && subjectCode.length > 0 && !subjectCode[0].isEmpty()){
                subjCode = subjectCode[0];
            }
            List<String[]> catalog = new ArrayList<>();
            OkHttpClient client = new OkHttpClient();
            String getCatalogUrl = getString(R.string.get_catalog_url);
            Set<String> validTermValues = sharedPreferences.getStringSet(getString(R.string.pref_key_valid_term_values), null);
            if(validTermValues == null){
                String[][] validTerms = getValidTerms(getApplicationContext());
                if(validTerms.length == 0){//internet problems
                    return catalog;//empty
                }else {
                    validTermValues = new HashSet<>(Arrays.asList(validTerms[0]));

                    //store defaults into sharedpreference if started for the first time
                    Set<String> validTermNames = new HashSet<>(Arrays.asList(validTerms[1]));
                    //major values/names as well
                    String[] validMajorValues = getValidMajors(getApplicationContext())[0];
                    String[] validMajorNames = getValidMajors(getApplicationContext())[1];
                    Set<String> validMajorValueSet = new HashSet<>(Arrays.asList(validMajorValues));
                    Set<String> validMajorNameSet = new HashSet<>(Arrays.asList(validMajorNames));
                    storeDefaultCatalogInfo(validTerms[0][0]/*latest valid term for default*/
                            , validTermValues, validTermNames, validMajorValueSet, validMajorNameSet);
                }
            }else{
                //check for the last time updated the information
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MONTH, -3);//set to three months ago from now
                Long lastSyncTime = sharedPreferences.getLong(getString(R.string.last_sync_time), -1);
                if(lastSyncTime == -1){
                    //this shouldn't happen
                }
                if(new Date(lastSyncTime).before(calendar.getTime())){//last sync is before three months
                    //sync again
                    Set<String> newValidTermValues = new HashSet<>(Arrays.asList(getValidTerms(getApplicationContext())[0]));
                    Set<String> newValidTermNames = new HashSet<>(Arrays.asList(getValidTerms(getApplicationContext())[1]));
                    Set<String> newValidMajorValues = new HashSet<>(Arrays.asList(getValidMajors(getApplicationContext())[0]));
                    Set<String> newValidMajorNames = new HashSet<>(Arrays.asList(getValidMajors(getApplicationContext())[1]));
                    storeDefaultCatalogInfo(newValidTermValues.iterator().next(),
                            newValidTermValues, newValidTermNames, newValidMajorValues, newValidMajorNames);
                }
            }
            String defaultTerm = validTermValues.iterator().next();//set the latest valid term as the default term
            RequestBody requestBody = new FormBody.Builder()
                    .add("validterm", sharedPreferences.getString(getString(R.string.pref_key_term), defaultTerm))
                    .add("subjcode", subjCode)
                    .add("openclasses",  sharedPreferences.getBoolean(getString(R.string.pref_key_only_show_open), true) ? "Y" : "N")
                    .build();
            Request request = new Request.Builder()
                    .url(getCatalogUrl)
                    .post(requestBody)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                String htmlResponse;
                if (response.body() != null) {
                    htmlResponse = response.body().string();
                }else {
                    return catalog;//empty
                }
                Document document = Jsoup.parse(htmlResponse);
                Elements courseList = document.select("tr[bgcolor=\"#DDDDDD\"], tr[bgcolor=\"#FFFFFF\"]");
                for (Element course: courseList) {
                    if(!course.text().contains("EXAM")){//exclude seperate exam info elements
                        String[] courseInfo = new String[4];
                        courseInfo[0] = course.child(1).ownText();//course number
                        courseInfo[1] = course.child(2).ownText();//course description/title
                        courseInfo[2] = course.child(0).ownText();//course crn
                        courseInfo[3] = course.child(12).ownText();//course available seats
                        catalog.add(courseInfo);
                    }
                }
                return catalog;
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return catalog;//empty
            }

        }

        @Override
        protected void onPostExecute(List list) {
            courseCatalogAdapter.swapNewDataSet(list);
        }
    }

    static String[][] getValidTerms(Context context){
        OkHttpClient client = new OkHttpClient();
        String url = context.getString(R.string.get_catalog_url);
        Request request = new Request.Builder()
                .url(url)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
            }else {
                return new String[][]{};//empty
            }
            Document document = Jsoup.parse(htmlResponse);
            Elements elements = document.select("input[name=validterm]");
            ArrayList<String> validTermValues = new ArrayList<>();
            ArrayList<String> validTermNames = new ArrayList<>();
            //place the latest ones on top
            for (Element element: Lists.reverse(elements)) {
                validTermValues.add(element.val());
                validTermNames.add(element.parent().child(1).ownText());
            }
            String[] values = validTermValues.toArray(new String[0]);
            String[] names = validTermNames.toArray(new String[0]);
            return new String[][]{values, names};
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(context, "", Toast.LENGTH_SHORT).show();
            return new String[][]{};//empty
        }
    }

    /**
     * Get all valid majors from website
     *
     * @param context run time context
     * @return String[][] with 2 sub String[]
     * first one is a String[] with major's values like "CSE"
     * second one is a String[] with major's names like "Computer Science and Engineering"
     */
    static String[][] getValidMajors(Context context){
        OkHttpClient client = new OkHttpClient();
        String url = context.getString(R.string.get_catalog_url);
        Request request = new Request.Builder()
                .url(url)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
            }else {
                return new String[][]{};//empty
            }
            Document document = Jsoup.parse(htmlResponse);
            Elements majorList = document.select("select[name=subjcode] option");
            ArrayList<String> validMajorValues = new ArrayList<>();
            ArrayList<String> validMajorNames = new ArrayList<>();
            for (Element major : majorList) {
                validMajorValues.add(major.val());
                validMajorNames.add(major.ownText());
            }
            String[] values = validMajorValues.toArray(new String[0]);
            String[] names = validMajorNames.toArray(new String[0]);
            return new String[][]{values, names};
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            Toast.makeText(context, "", Toast.LENGTH_SHORT).show();
            return new String[][]{};//empty
        }
    }

    void storeDefaultCatalogInfo(String defaultTerm, Set<String> validTermValues, Set<String> validTermNames, Set<String> validMajorValues, Set<String> validMajorNames){
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.pref_key_term), defaultTerm);//put the latest valid term as the default term selection
        editor.putStringSet(getString(R.string.pref_key_valid_term_values), validTermValues);
        editor.putStringSet(getString(R.string.pref_key_valid_term_names), validTermNames);
        editor.putStringSet(getString(R.string.pref_key_valid_major_values), validMajorValues);
        editor.putStringSet(getString(R.string.pref_key_valid_major_names), validMajorNames);

        //update last sync time
        editor.putLong(getString(R.string.last_sync_time), new Date().getTime());
        editor.apply();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }
}
