package com.jackz314.classregistrationhelper;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import static com.jackz314.classregistrationhelper.Constants.CHANNEL_ID;

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

        createNotificationChannel();
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

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_catalog, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.search_view).getActionView();
        searchView.setSearchableInfo(Objects.requireNonNull(searchManager).getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(true);
        searchView.setMaxWidth(Integer.MAX_VALUE);
        LinearLayout searchBar = searchView.findViewById(R.id.search_bar);
        searchBar.setLayoutTransition(new LayoutTransition());
        searchView.setQueryHint("Search for courses...");
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
    }*/

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

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
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
