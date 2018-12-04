package com.jackz314.classregistrationhelper;

import android.animation.LayoutTransition;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import okhttp3.HttpUrl;

import static com.jackz314.classregistrationhelper.Constants.COURSE_CHANGE_TO_LIST_CODE;
import static com.jackz314.classregistrationhelper.Constants.COURSE_DETAIL_REQUEST_CODE;
import static com.jackz314.classregistrationhelper.Constants.COURSE_LIST_CHANGE_CRN_KEY;
import static com.jackz314.classregistrationhelper.Constants.COURSE_REGISTER_STATUS_CHANGED;
import static com.jackz314.classregistrationhelper.CourseUtils.changeRegisterStatusForCourse;
import static com.jackz314.classregistrationhelper.CourseUtils.getCatalogFromHtml;
import static com.jackz314.classregistrationhelper.CourseUtils.getCatalogHtml;
import static com.jackz314.classregistrationhelper.CourseUtils.getPreferredTerm;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link CatalogOnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link CatalogFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CatalogFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private static final String TAG = "CatalogFragment";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private RecyclerView courseCatalogRecyclerView;
    private ListRecyclerAdapter courseCatalogAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SharedPreferences sharedPreferences;
    private ProgressBar loadProgress;

    private CatalogOnFragmentInteractionListener mListener;

    public CatalogFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter rootcrt.
     * @return A new instance of fragment CatalogFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static CatalogFragment newInstance(String param1, String param2) {
        CatalogFragment fragment = new CatalogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_catalog, container, false);

        setHasOptionsMenu(true);

        courseCatalogRecyclerView = view.findViewById(R.id.catalog_recycler_view);
        courseCatalogRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        courseCatalogAdapter = new ListRecyclerAdapter(new LinkedList<>());//initialize first, wait for download to finish and swap in the data
        courseCatalogRecyclerView.setAdapter(courseCatalogAdapter);
        //executeGetCatalog(); //wait for MyCoursesFragment to finish

        swipeRefreshLayout = view.findViewById(R.id.catalog_swipe_refresh_layout);
        swipeRefreshLayout.setEnabled(false);

        loadProgress = view.findViewById(R.id.catalog_load_progress);

        RecyclerViewItemClickSupport.addTo(courseCatalogRecyclerView).setOnItemClickListener((recyclerView, position, v) -> {
            Course courseInfo = courseCatalogAdapter.getItemAtPos(position);
            //int courseNumberSecondDashIndex = courseInfo.getNumber().indexOf('-', courseInfo.getNumber().indexOf('-', 0));
            String urlForCourseStr = getString(R.string.get_course_url);
            HttpUrl urlForCourse = Objects.requireNonNull(HttpUrl.parse(urlForCourseStr)).newBuilder()
                    .addQueryParameter("subjcode", courseInfo.getNumber().substring(0, courseInfo.getNumber().indexOf('-')))
                    .addQueryParameter("crsenumb", courseInfo.getNumber().substring(courseInfo.getNumber().indexOf('-', 0) + 1, courseInfo.getNumber().lastIndexOf('-')))//position of second '-' if exist
                    .addQueryParameter("validterm", getPreferredTerm(getContext()))
                    .addQueryParameter("crn", courseInfo.getCrn()).build();
            Intent intent = new Intent(getActivity(), CourseActivity.class);
            Bundle bundle = new Bundle();
            bundle.putString("URL", urlForCourse.toString());
            bundle.putParcelable("CourseInfo", courseInfo);
            intent.putExtra("bundle", bundle);
            startActivityForResult(intent, COURSE_DETAIL_REQUEST_CODE);
        });

        return view;
    }

    @Override
    public void onResume() {
        if(sharedPreferences != null && sharedPreferences.getBoolean(getString(R.string.changed_settings), false)){
            swipeRefreshLayout.setRefreshing(true);
            executeGetCatalog();
            sharedPreferences.edit().putBoolean(getString(R.string.changed_settings), false).apply();
        }
        super.onResume();
    }

    private static class GetCatalogTask extends AsyncTask<String[], Void, List<Course>> {

        WeakReference<CatalogFragment> contextWeakReference;

        GetCatalogTask(CatalogFragment fragment){
            contextWeakReference = new WeakReference<>(fragment);
        }

        @Override
        protected List<Course> doInBackground(String[]... subjCodeOverride) {
            CatalogFragment fragment= contextWeakReference.get();
            if(fragment == null) return null;
            Context context = fragment.getContext();
            if(context == null) return null;
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            Set<String> subjCode = sharedPreferences.getStringSet(context.getString(R.string.pref_key_major), new HashSet<>(Collections.singletonList("ALL")));
            if(subjCodeOverride != null && subjCodeOverride.length > 0 && subjCodeOverride[0].length > 0){
                subjCode = new HashSet<>(Collections.singletonList("ALL"));
            }
            //todo not sure if linked list is the best choice here, depends on the future usages, but for now, it probably is. And really, this doesn't matter
            List<Course> catalog = new LinkedList<>();
            String getCatalogUrl = context.getString(R.string.get_catalog_url);
            String preferredTerm = getPreferredTerm(context);
            if(preferredTerm == null){
                Toast.makeText(context, context.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show();
                return catalog;//empty
            }
            String major = "ALL";
            String onlyOpenClasses = sharedPreferences.getBoolean(context.getString(R.string.pref_key_only_show_open_classes), false) ? "Y" : "N";
            if(subjCode.size() == 1) major = subjCode.iterator().next();

            String htmlResponse = getCatalogHtml(getCatalogUrl, preferredTerm, major, onlyOpenClasses);
            if(htmlResponse == null){
                //network error
                Objects.requireNonNull(fragment.getActivity()).runOnUiThread(() -> Toast.makeText(context, context.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show());
                return catalog;//empty
            }

            catalog = getCatalogFromHtml(htmlResponse, subjCode, context);
            if(catalog.isEmpty()){
                //parsing error or others
                Objects.requireNonNull(fragment.getActivity()).runOnUiThread(() -> Toast.makeText(context, context.getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show());
            }

            return catalog;
        }

        @Override
        protected void onPostExecute(List<Course> list) {
            CatalogFragment fragment = contextWeakReference.get();
            if(fragment != null && list != null && !list.isEmpty()){
                fragment.processCatalogData(list);
                fragment.swipeRefreshLayout.setEnabled(true);
                fragment.swipeRefreshLayout.setOnRefreshListener(fragment::executeGetCatalog);
            }
        }
    }

    void onLoadFinished() {
        if (mListener != null) {
            mListener.catalogOnLoadFinished();
        }
    }

    void onListStatusStatusChanged(boolean refreshAll){
        if (mListener != null) {
            mListener.catalogOnListStatusChanged(refreshAll);
        }
    }

    void startLoading(){
        executeGetCatalog();
    }

    void refreshCatalogListWithStatusChange(String... specificCourseCrnExtra){
        if(courseCatalogAdapter != null){
            List<Course> courses = courseCatalogAdapter.getOriginalList();
            if(courses != null && !courses.isEmpty()){
                if(specificCourseCrnExtra != null && specificCourseCrnExtra.length > 0){
                    for (int i = 0; i < courses.size(); i++) {
                        Course course = courses.get(i);
                        if(course.getCrn().equals(specificCourseCrnExtra[0])){
                            Log.i(TAG, "Course List refreshed with changes in course crn: " + specificCourseCrnExtra[0]);
                            courses.set(i, changeRegisterStatusForCourse(course, getContext()));
                            break;
                        }
                    }
                }else {
                    Log.i(TAG, "Course List refreshed");
                    Set<String> selectionCrnSet = PreferenceManager.getDefaultSharedPreferences(getContext()).getStringSet(getString(R.string.my_selection_crn_set), null);
                    Set<String> registeredCrnSet = PreferenceManager.getDefaultSharedPreferences(getContext()).getStringSet(getString(R.string.my_registered_crn_set), null);
                    for (int i = 0; i < courses.size(); i++) {
                        Course course = courses.get(i);
                        courses.set(i, changeRegisterStatusForCourse(course, getContext(), selectionCrnSet, registeredCrnSet));
                    }
                }
            }
            courseCatalogAdapter.swapNewDataSet(courses);
            /*String existingQuery = courseCatalogAdapter.getQuery();
            if(existingQuery != null){
                courseCatalogAdapter.getFilter().filter(existingQuery);
            }*/
        }else {
            Log.e(TAG, "REFRESH LIST FAILED. CATALOG ADAPTER IS NULL");
        }
    }

    public void executeGetCatalog(){
        new GetCatalogTask(this).execute();
    }

    public void processCatalogData(List<Course> list){
        if(courseCatalogAdapter != null){
            courseCatalogAdapter.swapNewDataSet(list);
        }
        onLoadFinished();
        if(loadProgress != null){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                loadProgress.setProgress(100, true);
            }else{
                loadProgress.setProgress(100);
            }
            new Handler().postDelayed(() -> loadProgress.setVisibility(View.GONE), 100);
        }
        if(swipeRefreshLayout != null){
            swipeRefreshLayout.setRefreshing(false);
        }
    }


    /*public void query(String query, View view) {
        if (courseCatalogAdapter != null){
            courseCatalogAdapter.getFilter().filter(query);
        }else {
            courseCatalogRecyclerView = view.findViewById(R.id.catalog_recycler_view);
            ListRecyclerAdapter adapter = ((ListRecyclerAdapter)courseCatalogRecyclerView.getAdapter());
            if(adapter != null){
                adapter.getFilter().filter(query);
            }
        }
        //System.out.println("calledquery" + " " + text);
    }*///using view is depreciated

    public void query(String query){
        if(courseCatalogAdapter != null){
            courseCatalogAdapter.getFilter().filter(query);
        }else if(courseCatalogRecyclerView != null){
            ListRecyclerAdapter adapter = (ListRecyclerAdapter)courseCatalogRecyclerView.getAdapter();
            if(adapter != null){
                adapter.getFilter().filter(query);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == COURSE_DETAIL_REQUEST_CODE && resultCode == COURSE_CHANGE_TO_LIST_CODE){
            if(data != null){
                String crn = data.getStringExtra(COURSE_LIST_CHANGE_CRN_KEY);
                if(crn != null){
                    refreshCatalogListWithStatusChange(crn);
                    if(data.getBooleanExtra(COURSE_REGISTER_STATUS_CHANGED, false)){
                        onListStatusStatusChanged(true);
                    }else {
                        onListStatusStatusChanged(false);
                    }
                }
            }
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof CatalogOnFragmentInteractionListener) {
            mListener = (CatalogOnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement CatalogOnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_catalog, menu);
        SearchManager searchManager = (SearchManager) Objects.requireNonNull(getActivity()).getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_catalog_search).getActionView();
        searchView.setSearchableInfo(Objects.requireNonNull(searchManager).getSearchableInfo(getActivity().getComponentName()));
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
            public boolean onQueryTextChange(String query) {
                query(query);
                return true;
            }

        });

        MenuItem searchMenuIem = menu.findItem(R.id.action_catalog_search);
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

        super.onCreateOptionsMenu(menu, inflater);
    }


    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface CatalogOnFragmentInteractionListener {

        void catalogOnLoadFinished();

        void catalogOnListStatusChanged(boolean refreshAll);

    }
}
