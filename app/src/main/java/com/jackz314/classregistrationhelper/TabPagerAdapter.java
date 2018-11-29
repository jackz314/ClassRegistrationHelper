package com.jackz314.classregistrationhelper;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

public class TabPagerAdapter extends FragmentPagerAdapter {

    Context context;

    public TabPagerAdapter(Context context, FragmentManager fm) {
        super(fm);
        this.context = context;
    }

    @Override
    public Fragment getItem(int pos) {
        switch (pos){
            case 0:
                MyCoursesFragment myCoursesFragment = new MyCoursesFragment();
                return myCoursesFragment;
            case 1:
                CatalogFragment catalogFragment = new CatalogFragment();
                return catalogFragment;
            default:
                return null;
        }

    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int pos) {
        switch (pos){
            case 0:
                return "My Courses";
            case 1:
                return "Catalog";
            default:
                return null;
        }
    }

    @Override
    public int getCount() {
        //changes based on the number of tabs in use
        return 2;
    }
}
