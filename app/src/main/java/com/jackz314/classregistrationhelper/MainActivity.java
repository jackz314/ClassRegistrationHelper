package com.jackz314.classregistrationhelper;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.viewpager.widget.ViewPager;

import static com.jackz314.classregistrationhelper.AccountUtils.getProfilePicByteArr;
import static com.jackz314.classregistrationhelper.Constants.CHANNEL_ID;
import static com.jackz314.classregistrationhelper.Constants.LOGIN_REQUEST_CODE;
import static com.jackz314.classregistrationhelper.Constants.LOGIN_SUCCESS_CODE;
import static com.jackz314.classregistrationhelper.Constants.LOGOUT_REQUEST_CODE;
import static com.jackz314.classregistrationhelper.Constants.LOGOUT_SUCCESS_CODE;
import static com.jackz314.classregistrationhelper.Constants.SHORTCUT_INTENT_EXTRA_KEY;
import static com.jackz314.classregistrationhelper.Constants.SHORTCUT_INTENT_EXTRA_VALUE;

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
    ImageView navHeaderProfilePic;
    boolean myCoursesLoadFinished = false, catalogLoadFinished = false;
    boolean hasShortCutAction = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.account_toolbar);
        setSupportActionBar(toolbar);

       /* FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());*/

       //get register all shortcut action
        Intent intent = getIntent();
        if(intent != null
                && intent.getExtras() != null
                && intent.getStringExtra(SHORTCUT_INTENT_EXTRA_KEY) != null
                && intent.getStringExtra(SHORTCUT_INTENT_EXTRA_KEY).equals(SHORTCUT_INTENT_EXTRA_VALUE)){
            //register all intent
            Log.i(TAG, "SHORTCUT REGISTER ACTION");
            hasShortCutAction = true;
        }

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
        navHeaderProfilePic = navigationView.getHeaderView(0).findViewById(R.id.nav_header_profile_pic);
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
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode == LOGOUT_REQUEST_CODE && resultCode == LOGOUT_SUCCESS_CODE){
            recreate();
        }
        if(requestCode == LOGIN_REQUEST_CODE && resultCode == LOGIN_SUCCESS_CODE){
            myCoursesFragment = (MyCoursesFragment)getSupportFragmentManager().getFragments().get(0);
            if(myCoursesFragment != null){
                myCoursesFragment.processLoginResult();
            }
            setNavHeaderInfo();
        }
        super.onActivityResult(requestCode, resultCode, data);
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
        navHeaderProfilePic.setOnClickListener(v -> {
            startAccountActivity();
        });
        new GetUserProfilePicTask(this).execute();
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

    private void startAccountActivity(){
        String sessionId = sharedPreferences.getString(getString(R.string.session_id), null);
        if(sessionId != null){
            Intent intent = new Intent(MainActivity.this, AccountActivity.class);
            startActivityForResult(intent, LOGOUT_REQUEST_CODE);
        }else {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivityForResult(intent, LOGIN_REQUEST_CODE);
        }
    }

    private static class GetUserProfilePicTask extends AsyncTask<Void, Void, byte[]> {

        private WeakReference<MainActivity> weakReference;

        GetUserProfilePicTask(MainActivity activity){
            weakReference = new WeakReference<>(activity);
        }

        @Override
        protected byte[] doInBackground(Void... voids) {
            return getProfilePicByteArr(weakReference.get());
        }

        @Override
        protected void onPostExecute(byte[] bytes) {
            if (bytes != null){
                MainActivity activity = weakReference.get();

                RequestOptions requestOptions = new RequestOptions()
                        .placeholder(R.mipmap.ic_launcher)
                        .error(R.mipmap.ic_launcher);

                Glide.with(activity.getApplicationContext())
                        .setDefaultRequestOptions(requestOptions)
                        .asBitmap()
                        .apply(RequestOptions.circleCropTransform())
                        .load(bytes)
                        .into(activity.navHeaderProfilePic);
            }
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_account) {
            startAccountActivity();
        } else if (id == R.id.nav_settings) {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_about) {
            new AlertDialog.Builder(this)
                    .setTitle("About")
                    .setMessage(getString(R.string.about_text))
                    .setPositiveButton("Got It!", (dialog1, which) -> dialog1.dismiss()).show();
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

    @Override
    public boolean checkHasShortcutAction() {
        return hasShortCutAction;
    }
}
