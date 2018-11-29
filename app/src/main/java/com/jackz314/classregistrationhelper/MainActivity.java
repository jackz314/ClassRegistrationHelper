package com.jackz314.classregistrationhelper;

import android.animation.LayoutTransition;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.collect.Lists;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, CatalogFragment.CatalogOnFragmentInteractionListener, MyCoursesFragment.MyCoursesOnFragmentInteractionListener {

    private static final String TAG = "MainActivity";
    SharedPreferences sharedPreferences;
    CatalogFragment catalogFragment;
    MyCoursesFragment myCoursesFragment;
    TabPagerAdapter tabPagerAdapter;
    ViewPager viewPager;
    TabLayout tabLayout;
    TextView navHeaderTitle;
    TextView navHeaderSubtitle;
    SearchView searchView;
    boolean myCoursesLoadFinished = false, catalogLoadFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

       /* FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());*/

        tabPagerAdapter = new TabPagerAdapter(getApplicationContext(), getSupportFragmentManager());
        viewPager = findViewById(R.id.tab_view_pager);
        viewPager.setAdapter(tabPagerAdapter);
        tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(viewPager);
        viewPager.setCurrentItem(0);//which is My List

        //myCoursesFragment = (MyCoursesFragment)getSupportFragmentManager().getFragments().get(1);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        navHeaderTitle = navigationView.getHeaderView(0).findViewById(R.id.nav_header_title);
        navHeaderSubtitle = navigationView.getHeaderView(0).findViewById(R.id.nav_header_subtitle);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        setNavHeaderInfo();
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
        searchView = (SearchView) menu.findItem(R.id.search_view).getActionView();
        searchView.setSearchableInfo(Objects.requireNonNull(searchManager).getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(true);
        searchView.setMaxWidth(Integer.MAX_VALUE);
        LinearLayout searchBar = searchView.findViewById(R.id.search_bar);
        searchBar.setLayoutTransition(new LayoutTransition());
        searchView.setQueryHint("Search for classes...");
        searchView.setClickable(false);
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
        if(viewPager.getCurrentItem() == 0){
            if(myCoursesLoadFinished){
                Log.i(TAG, "QUERYING: " + query + " in my courses");
                myCoursesFragment.query(query);
            }
        }else if(viewPager.getCurrentItem() == 1){
            if(catalogLoadFinished){
                Log.i(TAG, "QUERYING: " + query + " in catalog");
                catalogFragment.query(query);
            }
        }
    }

    void setNavHeaderInfo(){
        String navTitleStr = sharedPreferences.getString(getString(R.string.user_name), null);
        String navSubStr = sharedPreferences.getString(getString(R.string.username), null);
        if(navTitleStr != null){
            navHeaderTitle.setText(navTitleStr);
        }
        if(navSubStr != null){
            String subText = navSubStr + "@ucmerced.edu";
            navHeaderSubtitle.setText(subText);
        }
    }

    //methods
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
            List<String> validTermValues = new LinkedList<>();
            List<String> validTermNames = new LinkedList<>();
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
            List<String> validMajorValues = new LinkedList<>();
            List<String> validMajorNames = new LinkedList<>();
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

    public static String getPreferredTerm(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String defaultTerm = sharedPreferences.getString(context.getString(R.string.pref_key_term), null);
        if(defaultTerm != null) return defaultTerm;

        String[] validTermValues = sharedPreferences.getString(context.getString(R.string.pref_key_valid_term_values), "").split(";");
        if(validTermValues[0].equals("")){
            String[][] validTerms = getValidTerms(context);
            if(validTerms.length == 0){//internet problems
                return null;
            }else {
                validTermValues = validTerms[0];
                String[] validTermNames = validTerms[1];
                //store defaults into sharedpreference if started for the first time
                //major values/names as well
                String[] validMajorValues = getValidMajors(context)[0];
                String[] validMajorNames = getValidMajors(context)[1];
                storeDefaultCatalogInfo(context, validTerms[0][0]/*store latest valid term as default*/
                        , validTermValues, validTermNames, validMajorValues, validMajorNames);
            }
        }else{
            //check for the last time updated the information
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MONTH, -3);//set to three months ago from now
            Long lastSyncTime = sharedPreferences.getLong(context.getString(R.string.last_sync_time), -1);
            if(lastSyncTime == -1){
                //this shouldn't happen by design
                return null;
            }
            if(new Date(lastSyncTime).before(calendar.getTime())){//last sync is before three months
                //sync again
                String[] newValidTermValues = getValidTerms(context)[0];
                String[] newValidTermNames = getValidTerms(context)[1];
                String[] newValidMajorValues = getValidMajors(context)[0];
                String[] newValidMajorNames = getValidMajors(context)[1];
                storeDefaultCatalogInfo(context, newValidTermValues[0],/*latest valid term for default*/
                        newValidTermValues, newValidTermNames, newValidMajorValues, newValidMajorNames);
                validTermValues = newValidTermValues;//update old term values
            }
        }
        defaultTerm = validTermValues[0];//set the latest valid term as the default term
        return defaultTerm;
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
        /*if(sharedPreferences != null && sharedPreferences.getBoolean(getString(R.string.changed_settings), false)){
            catalogFragment.swipeRefreshLayout.setRefreshing(true);
            catalogFragment.executeGetCatalog();
            sharedPreferences.edit().putBoolean(getString(R.string.changed_settings), false).apply();
        }*/
        super.onResume();
    }

    @Override
    public void catalogOnLoadFinished() {
        catalogFragment = (CatalogFragment)getSupportFragmentManager().getFragments().get(1);
        catalogLoadFinished = true;
    }

    @Override
    public void catalogOnListStatusChanged(boolean refreshAll) {
        if(myCoursesFragment == null || !myCoursesFragment.isAdded()){
            myCoursesFragment = (MyCoursesFragment)getSupportFragmentManager().getFragments().get(0);
        }
        if(myCoursesFragment != null){
            if(refreshAll){
                myCoursesFragment.refreshMyCoursesEntirely();
            }else {
                myCoursesFragment.refreshMyCoursesLocally();
            }
        }
    }

    @Override
    public void myCourseOnLoginSuccess() {
        setNavHeaderInfo();
    }

    @Override
    public void myCourseOnRegisterStatusChanged() {
        catalogFragment = (CatalogFragment)getSupportFragmentManager().getFragments().get(1);
        if(catalogFragment != null){
            catalogFragment.refreshCatalogListWithStatusChange();
        }
    }

    @Override
    public void myCourseOnListStatusChanged(String crn) {
        catalogFragment = (CatalogFragment)getSupportFragmentManager().getFragments().get(1);
        if(catalogFragment != null){
            catalogFragment.refreshCatalogListWithStatusChange(crn);
        }
    }

    @Override
    public void myCourseOnLoadFinished() {
        myCoursesFragment = (MyCoursesFragment)getSupportFragmentManager().getFragments().get(0);
        myCoursesLoadFinished = true;
        if(catalogFragment == null){
            catalogFragment = (CatalogFragment)getSupportFragmentManager().getFragments().get(1);
        }
        if(catalogFragment != null){
            catalogFragment.startLoading();
        }
    }
}
