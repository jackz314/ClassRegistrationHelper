package com.jackz314.classregistrationhelper;

import android.animation.LayoutTransition;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.support.v7.widget.SearchView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.common.collect.Lists;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = "MainActivity";
    private RecyclerView courseCatalogRecyclerView;
    private ListRecyclerAdapter courseCatalogAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    SharedPreferences sharedPreferences;
    ProgressBar loadProgress;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());

        loadProgress = findViewById(R.id.load_catalog_progress);
        courseCatalogRecyclerView = findViewById(R.id.course_catalog_recycler_view);
        courseCatalogRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        courseCatalogAdapter = new ListRecyclerAdapter(new ArrayList<>());//initialize first, wait for download to finish and swap in the data
        courseCatalogRecyclerView.setAdapter(courseCatalogAdapter);
        new GetCatalogTask(this).execute();

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(() -> new GetCatalogTask(this).execute());

        RecyclerViewItemClickSupport.addTo(courseCatalogRecyclerView).setOnItemClickListener((recyclerView, position, v) -> {
            Course courseInfo = courseCatalogAdapter.getItemAtPos(position);
            int courseNumberSecondDashIndex = courseInfo.getNumber().indexOf('-', courseInfo.getNumber().indexOf('-', 0));
            if(courseNumberSecondDashIndex == -1){
                courseNumberSecondDashIndex = courseInfo.getNumber().length();
            }
            String urlForCourseStr = getString(R.string.get_course_url);
            HttpUrl urlForCourse = HttpUrl.parse(urlForCourseStr).newBuilder()
                    .addQueryParameter("subjcode", courseInfo.getNumber().substring(0, courseInfo.getNumber().indexOf('-')))
                    .addQueryParameter("crsenumb", courseInfo.getNumber().substring(courseInfo.getNumber().indexOf('-', 0) + 1, courseInfo.getNumber().lastIndexOf('-')))//position of second '-' if exist
                    .addQueryParameter("validterm", sharedPreferences.getString(getString(R.string.pref_key_term), null))//default value should't be used based on design
                    .addQueryParameter("crn", courseInfo.getCrn()).build();
            Intent intent = new Intent(MainActivity.this, CourseActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("URL", urlForCourse.toString());
            bundle.putParcelable("CourseInfo", courseInfo);
            intent.putExtra("bundle", bundle);
            startActivity(intent);
        });

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
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                query(newText);
                return true;
            }

        });

        MenuItem searchMenuIem = menu.findItem(R.id.search_view);
        searchMenuIem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {

                return true;
            }
        });

        return true;
    }

    public void query(String query) {
        ((ListRecyclerAdapter)courseCatalogRecyclerView.getAdapter()).getFilter().filter(query);
        //System.out.println("calledquery" + " " + text);
    }

    private static class GetCatalogTask extends AsyncTask<String[], Void, List<Course>>{

        private WeakReference<MainActivity> activityReference;

        GetCatalogTask(MainActivity context){
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected List<Course> doInBackground(String[]... subjectCode) {
            MainActivity activity = activityReference.get();
            Set<String> subjCode = activity.sharedPreferences.getStringSet(activity.getString(R.string.pref_key_major), new HashSet<>(Collections.singletonList("ALL")));
            if(subjectCode != null && subjectCode.length > 0 && subjectCode[0].length > 0){
                subjCode = new HashSet<>(Collections.singletonList("ALL"));
            }
            List<Course> catalog = new ArrayList<>();
            OkHttpClient client = new OkHttpClient();
            String getCatalogUrl = activity.getString(R.string.get_catalog_url);
            String[] validTermValues = activity.sharedPreferences.getString(activity.getString(R.string.pref_key_valid_term_values), "").split(";");
            if(validTermValues[0].equals("")){
                String[][] validTerms = getValidTerms(activity);
                if(validTerms.length == 0){//internet problems
                    return catalog;//empty
                }else {
                    validTermValues = validTerms[0];
                    String[] validTermNames = validTerms[1];
                    //store defaults into sharedpreference if started for the first time
                    //major values/names as well
                    String[] validMajorValues = getValidMajors(activity)[0];
                    String[] validMajorNames = getValidMajors(activity)[1];
                    storeDefaultCatalogInfo(activity, validTerms[0][0]/*latest valid term for default*/
                            , validTermValues, validTermNames, validMajorValues, validMajorNames);
                }
            }else{
                //check for the last time updated the information
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.MONTH, -3);//set to three months ago from now
                Long lastSyncTime = activity.sharedPreferences.getLong(activity.getString(R.string.last_sync_time), -1);
                if(lastSyncTime == -1){
                    //this shouldn't happen
                }
                if(new Date(lastSyncTime).before(calendar.getTime())){//last sync is before three months
                    //sync again
                    String[] newValidTermValues = getValidTerms(activity)[0];
                    String[] newValidTermNames = getValidTerms(activity)[1];
                    String[] newValidMajorValues = getValidMajors(activity)[0];
                    String[] newValidMajorNames = getValidMajors(activity)[1];
                    storeDefaultCatalogInfo(activity, newValidTermValues[0],/*latest valid term for default*/
                            newValidTermValues, newValidTermNames, newValidMajorValues, newValidMajorNames);
                    validTermValues = newValidTermValues;//update old term values
                }
            }
            String defaultTerm = validTermValues[0];//set the latest valid term as the default term

            String major = "ALL";
            if(subjCode.size() == 1) major = subjCode.iterator().next();
            RequestBody requestBody = new FormBody.Builder()
                    .add("validterm", activity.sharedPreferences.getString(activity.getString(R.string.pref_key_term), defaultTerm))
                    .add("subjcode", major)
                    .add("openclasses",  activity.sharedPreferences.getBoolean(activity.getString(R.string.pref_key_only_show_open_classes), false) ? "Y" : "N")
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
                for (int i = 0, courseListSize = courseList.size(); i < courseListSize; i++) {
                    Element courseElement = courseList.get(i);
                    /*if(!(course.text().startsWith("EXAM")||
                            course.text().startsWith("LECT")||
                            course.text().startsWith("LAB"))||
                            course.text().startsWith("SEM")){*///exclude separate exam/lect/lab/sem info elements
                    if (courseElement.children().size() == 13) {//only include whole information elements
                        CourseBuilder courseBuilder = new CourseBuilder();
                        courseBuilder.setCrn(courseElement.child(0).text())
                                .setNumber(courseElement.child(1).text())
                                .setTitle(courseElement.child(2).child(0).ownText())/*remove the requirements in <br> tag*/
                                .setAvailableSeats(courseElement.child(12).text());
                        //set other stuff for detail display later
                        courseBuilder.addType(courseElement.child(4).text())
                                .addDays(courseElement.child(5).text())
                                .addTime(courseElement.child(6).text())
                                .addLocation(courseElement.child(7).text())
                                .addPeriod(courseElement.child(8).text())
                                .setInstructor(courseElement.child(9).text());

                        //add additional exam/lect/lab/sem info elements
                        int tempIndex = i + 1;
                        while (courseList.size() > tempIndex && courseList.get(tempIndex).children().size() == 12){
                            Element additionalElement = courseList.get(tempIndex);
                            Log.i(TAG, "Added additional info: " + Arrays.toString(additionalElement.children().eachText().toArray()));
                            courseBuilder.addType(additionalElement.child(3).text())
                                    .addDays(additionalElement.child(4).text())
                                    .addTime(additionalElement.child(5).text())
                                    .addLocation(additionalElement.child(6).text())
                                    .addPeriod(additionalElement.child(7).text());
                            i = tempIndex;//skip the additional classes's elements in the for loop for more efficiency
                            tempIndex++;
                        }

                        //build course and filter out not chosen ones
                        Course course = courseBuilder.buildCourse();
                        if (subjCode.size() > 1) {//multiple subject code support
                            //check if course number starts with one of the wanted subject codes
                            if (subjCode.contains(course.getMajor())) {
                                Log.i(TAG, "Course info: " + course.toString());
                                catalog.add(course);
                            }
                        } else {
                            Log.i(TAG, "Course info: " + course.toString());
                            catalog.add(course);
                        }
                    }
                }
                return catalog;
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                e.printStackTrace();
                activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show());
                return catalog;//empty
            } catch (Exception e) {
                e.printStackTrace();
                activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show());
                return catalog;
            }
        }

        //todo internet error

        @Override
        protected void onPostExecute(List<Course> list) {
            MainActivity activity = activityReference.get();
            activity.courseCatalogAdapter.swapNewDataSet(list);
            activity.loadProgress.setVisibility(View.GONE);
            activity.swipeRefreshLayout.setRefreshing(false);
        }
    }

    static String[][] getValidTerms(Context context){
        OkHttpClient client = new OkHttpClient();
        String getRequestCatalogUrl = context.getString(R.string.get_request_catalog_url);
        Request request = new Request.Builder()
                .url(getRequestCatalogUrl)
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
                validTermNames.add(element.parent().parent().text());
            }
            String[] values = validTermValues.toArray(new String[0]);
            String[] names = validTermNames.toArray(new String[0]);
            if(values.length == 0 || names.length == 0){
                return new String[][]{};//empty
            }
            return new String[][]{values, names};
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            Toast.makeText(context, context.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show();
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
        String url = context.getString(R.string.get_request_catalog_url);
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
            e.printStackTrace();
            Toast.makeText(context, context.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show();
            return new String[][]{};//empty
        }
    }

    static void storeDefaultCatalogInfo(Context context, String defaultTerm, String[] validTermValues, String[] validTermNames, String[] validMajorValues, String[] validMajorNames){
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(context.getString(R.string.pref_key_term), defaultTerm);//put the latest valid term as the default term selection
        editor.putStringSet(context.getString(R.string.pref_key_major), new HashSet<>(Collections.singletonList(validMajorValues[0])));//put ALL as default
        editor.putString(context.getString(R.string.pref_key_valid_term_values), TextUtils.join(";", validTermValues));
        editor.putString(context.getString(R.string.pref_key_valid_term_names), TextUtils.join(";", validTermNames));
        editor.putString(context.getString(R.string.pref_key_valid_major_values), TextUtils.join(";", validMajorValues));
        editor.putString(context.getString(R.string.pref_key_valid_major_names), TextUtils.join(";", validMajorNames));

        //update last sync time
        editor.putLong(context.getString(R.string.last_sync_time), new Date().getTime());

        editor.commit();
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
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_account) {

        } else if (id == R.id.nav_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_about) {

        } else if (id == R.id.nav_share) {

        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        if(sharedPreferences != null && sharedPreferences.getBoolean(getString(R.string.changed_settings), false)){
            swipeRefreshLayout.setRefreshing(true);
            new GetCatalogTask(this).execute();
            sharedPreferences.edit().putBoolean(getString(R.string.changed_settings), false).apply();
        }
        super.onResume();
    }
}
