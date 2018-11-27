package com.jackz314.classregistrationhelper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.jackz314.classregistrationhelper.MainActivity.getPreferredTerm;


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
    public static final String COURSE_ADD_TO_LIST_CRN_KEY = "course_add_to_list_crn_key";
    public static final int COURSE_DETAIL_REQUEST_CODE = 56174;//the number doesn't matter
    public static final int COURSE_ADD_TO_LIST_CODE = 1857;//the number doesn't matter

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
     * @param param2 Parameter 2.
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
                    .addQueryParameter("validterm", sharedPreferences.getString(getString(R.string.pref_key_term), null))//default value should't be used based on design
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

            catalog = getCatalog(htmlResponse, subjCode, context);
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

    //long running
    public static String getCatalogHtml(String url, String preferredTerm, String major, String onlyOpenClasses){
        OkHttpClient client = new OkHttpClient();
        RequestBody requestBody = new FormBody.Builder()
                .add("validterm", preferredTerm)
                .add("subjcode", major)
                .add("openclasses", onlyOpenClasses)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        Log.i(TAG, "Get catalog Info:" + "\n" + url + "\n" + preferredTerm + "\n" + major + "\n" + onlyOpenClasses);
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
                return htmlResponse;
            }else {
                return null;
            }
        }catch (IOException e){
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    //long running task
    public static List<Course> getCatalog(String catalogHtmlStr, Set<String> subjCode, Context context){
        List<Course> catalog = new LinkedList<>();
        try{
            Document document = Jsoup.parse(catalogHtmlStr);
            Elements courseList = document.select("tr[bgcolor=\"#DDDDDD\"], tr[bgcolor=\"#FFFFFF\"]");
            Set<String> selectionCrnSet = PreferenceManager.getDefaultSharedPreferences(context).getStringSet(context.getString(R.string.my_selection_crn_set), null);
            Set<String> registeredCrnSet = PreferenceManager.getDefaultSharedPreferences(context).getStringSet(context.getString(R.string.my_registered_crn_set), null);
            for (int i = 0, courseListSize = courseList.size(); i < courseListSize; i++) {
                Element courseElement = courseList.get(i);
                /*if(!(course.text().startsWith("EXAM")||
                        course.text().startsWith("LECT")||
                        course.text().startsWith("LAB"))||
                        course.text().startsWith("SEM")){*///exclude separate exam/lect/lab/sem info elements
                if (courseElement.children().size() == 13) {//only include whole information elements
                    CourseBuilder courseBuilder = getCourseBuilderFromElement(courseElement);
                    if(courseBuilder == null) return catalog;//empty. Error

                    //add additional exam/lect/lab/sem info elements
                    int tempIndex = i + 1;
                    while (courseList.size() > tempIndex && courseList.get(tempIndex).children().size() == 12){
                        Element additionalElement = courseList.get(tempIndex);
                        //Log.i(TAG, "Added additional info: " + Arrays.toString(additionalElement.children().eachText().toArray()));
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

                    //multiple subject code support
                    if ((subjCode.size() > 1 && subjCode.contains(course.getMajor())) || subjCode.size() == 1) {
                        course = changeRegisterStatusForCourse(course, context, selectionCrnSet, registeredCrnSet);//set register status to course
                        //Log.i(TAG, "Course info: " + course.toString());
                        catalog.add(course);
                    }
                }
            }
            return catalog;
        }catch (Exception e) {
            e.printStackTrace();
            return catalog;//empty
        }
    }

    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
        if (mListener != null) {
            mListener.catalogOnFragmentInteraction(uri);
        }
    }

    public void startTasks(){
        executeGetCatalog();
    }

    public void refreshCatalogList(String... specificCourseCrnExtra){
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

    @SafeVarargs//will not pass in wrong types
    public static Course changeRegisterStatusForCourse(Course course, Context context, Set<String>... crnSets){
        Set<String> selectionCrnSet, registeredCrnSet;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        if(crnSets != null && crnSets.length > 1){
            selectionCrnSet = crnSets[0];
            registeredCrnSet = crnSets[1];
        }else {
            selectionCrnSet = sharedPreferences.getStringSet(context.getString(R.string.my_selection_crn_set), null);
            registeredCrnSet = sharedPreferences.getStringSet(context.getString(R.string.my_registered_crn_set), null);
        }
        if(selectionCrnSet != null || registeredCrnSet != null){//add register status to the courses
            String registerStatus = null;
            if(selectionCrnSet != null && selectionCrnSet.contains(course.getCrn())){//selection list
                registerStatus = "On my list";//on the list but not registered
            }
            if(registeredCrnSet != null && registeredCrnSet.contains(course.getCrn())){//registered list
                registerStatus = sharedPreferences.getString(context.getString(R.string.my_course_registered_status_list), "");
                int startPos = registerStatus.indexOf(course.getCrn());
                if(startPos == -1) registerStatus = "On my list";//not on the list, error
                else{//registered
                    startPos = registerStatus.indexOf('^', startPos);
                    int endPos = registerStatus.indexOf('-', startPos);
                    if(endPos == -1) endPos = registerStatus.length();
                    registerStatus = registerStatus.substring(startPos, endPos);
                }
            }
            course.setRegisterStatus(registerStatus);
        }else course.setRegisterStatus(null);//no status
        return course;
    }

    public static CourseBuilder getCourseBuilderFromElement(Element courseElement){
        if (courseElement.children().size() == 13) {//only include whole information elements
            return new CourseBuilder()
                    .setCrn(courseElement.child(0).text())
                    .setNumber(courseElement.child(1).text())
                    .setTitle(courseElement.child(2).child(0).ownText())/*remove the requirements in <br> tag*/
                    .setAvailableSeats(courseElement.child(12).text())
            //set other stuff for detail display later
                    .addType(courseElement.child(4).text())
                    .addDays(courseElement.child(5).text())
                    .addTime(courseElement.child(6).text())
                    .addLocation(courseElement.child(7).text())
                    .addPeriod(courseElement.child(8).text())
                    .setInstructor(courseElement.child(9).text());
        }else return null;
    }

    public void executeGetCatalog(){
        new GetCatalogTask(this).execute();
    }

    public void processCatalogData(List<Course> list){
        if(courseCatalogAdapter != null){
            courseCatalogAdapter.swapNewDataSet(list);
        }
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
        if(requestCode == COURSE_DETAIL_REQUEST_CODE && resultCode == COURSE_ADD_TO_LIST_CODE){
            if(data != null){
                String crn = data.getStringExtra(COURSE_ADD_TO_LIST_CRN_KEY);
                if(crn != null){
                    refreshCatalogList(crn);
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
        // TODO: Update argument type and name
        void catalogOnFragmentInteraction(Uri uri);
    }
}
