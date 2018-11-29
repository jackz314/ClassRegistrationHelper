package com.jackz314.classregistrationhelper;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
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
import okio.Buffer;

import static com.jackz314.classregistrationhelper.CatalogFragment.COURSE_CHANGE_TO_LIST_CODE;
import static com.jackz314.classregistrationhelper.CatalogFragment.COURSE_DETAIL_REQUEST_CODE;
import static com.jackz314.classregistrationhelper.CatalogFragment.COURSE_LIST_CHANGE_CRN_KEY;
import static com.jackz314.classregistrationhelper.CatalogFragment.COURSE_REGISTER_STATUS_CHANGED;
import static com.jackz314.classregistrationhelper.CatalogFragment.getCatalogHtml;
import static com.jackz314.classregistrationhelper.CatalogFragment.getCourseBuilderFromElement;
import static com.jackz314.classregistrationhelper.LoginActivity.reauthorizeWithCastgc;
import static com.jackz314.classregistrationhelper.MainActivity.getPreferredTerm;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link MyCoursesOnFragmentInteractionListener} interface
 * to handle interaction events.
 * Use the {@link MyCoursesFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MyCoursesFragment extends Fragment {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    static final int LOGIN_REQUEST_CODE = 10232;//the number doesn't matter
    static final int LOGIN_SUCCESS_CODE = 9452;//the number doesn't matter
    private static final String TAG = "MyCoursesFragment";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;
    private String sessionId;
    private boolean loggedIn = false;

    private boolean changedRegisterStatus = false;

    private RecyclerView myCoursesRecyclerView;
    private ProgressBar loadProgress;
    private TextView signInPromptText;
    private TextView emptyMyCoursesText;
    private Button signInButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SharedPreferences sharedPreferences;
    private ListRecyclerAdapter myListAdapter;

    private MyCoursesOnFragmentInteractionListener mListener;

    public MyCoursesFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MyCoursesFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MyCoursesFragment newInstance(String param1, String param2) {
        MyCoursesFragment fragment = new MyCoursesFragment();
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
        View view = inflater.inflate(R.layout.fragment_my_courses, container, false);

        myCoursesRecyclerView = view.findViewById(R.id.my_courses_recycler_view);
        signInButton = view.findViewById(R.id.sign_in_btn);
        signInPromptText = view.findViewById(R.id.sign_in_prompt_text);
        loadProgress = view.findViewById(R.id.my_courses_load_progress);
        swipeRefreshLayout = view.findViewById(R.id.my_courses_swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(this::executeGetMyCourses);
        emptyMyCoursesText = view.findViewById(R.id.empty_my_courses_text);

        myCoursesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        myListAdapter = new ListRecyclerAdapter(new ArrayList<>());//initialize first, wait for download to finish and swap in the data
        myCoursesRecyclerView.setAdapter(myListAdapter);

        sessionId = sharedPreferences.getString(getString(R.string.session_id), null);
        if(sessionId == null){//if no sessionId is found, prompt to login
            signInPromptText.setVisibility(View.VISIBLE);
            signInButton.setVisibility(View.VISIBLE);
            swipeRefreshLayout.setEnabled(false);
            myCoursesRecyclerView.setVisibility(View.GONE);
            signInButton.setOnClickListener(view1 -> {
                //start sign in process
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                startActivityForResult(intent, LOGIN_REQUEST_CODE);
            });
            loggedIn = false;
            onLoadFinished();//no need to load at the moment, so let catalog load now.
        }else {
            loggedIn = true;
            loadProgress.setVisibility(View.VISIBLE);
            executeGetMyCourses();
        }

        RecyclerViewItemClickSupport.addTo(myCoursesRecyclerView).setOnItemClickListener((recyclerView, position, v) -> {
            Course courseInfo = myListAdapter.getItemAtPos(position);
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

    public static class RefreshCoursesLocallyTask extends AsyncTask<List<Course>, Void, List<Course>>{

        WeakReference<MyCoursesFragment> contextWeakReference;

        RefreshCoursesLocallyTask(MyCoursesFragment fragment){
            contextWeakReference = new WeakReference<>(fragment);
        }

        @Override
        protected List<Course> doInBackground(List<Course>... originalCourseExtra) {
            if(originalCourseExtra == null || originalCourseExtra.length == 0) return null;
            MyCoursesFragment fragment = contextWeakReference.get();
            if(fragment == null) return null;
            if(fragment.sessionId == null) return null;
            Context context = fragment.getContext();
            if (context == null) return null;
            if(!fragment.loggedIn) return null;
            List<Course> originalCourses = originalCourseExtra[0];
            //extract the registered courses from original courses, only change the saved courses section
            List<Course> registeredCourses = new LinkedList<>();
            for (Course course : originalCourses) {
                String registerStatus = course.getRegisterStatus();
                if(registerStatus != null){
                    if(!registerStatus.equals(fragment.getString(R.string.register_status_on_my_list))){
                        registeredCourses.add(course);
                    }
                }
            }
            List<Course> savedCourses = getSavedCourseSelectionList(context);
            savedCourses = fillCourseInfo(context, savedCourses);
            if(savedCourses != null){
                List<Course> combinedCourses = new LinkedList<>(savedCourses);
                combinedCourses.addAll(registeredCourses);
                for (Course course :
                        combinedCourses) {
                    Log.i(TAG, "REFRESH: " + course.getNumber());
                }
                return combinedCourses;
            }else {
                return registeredCourses;
            }
        }

        @Override
        protected void onPostExecute(List<Course> courses) {
            MyCoursesFragment fragment = contextWeakReference.get();
            if(fragment.myListAdapter != null){
                fragment.myListAdapter.swapNewDataSet(courses);
            }
            fragment.swipeRefreshLayout.setRefreshing(false);
        }
    }

    public void refreshMyCoursesLocally(){
        List<Course> originalCourses = myListAdapter.getOriginalList();
        if(originalCourses != null){
            swipeRefreshLayout.setRefreshing(true);
            new RefreshCoursesLocallyTask(this).execute(originalCourses);
        }
    }

    public void refreshMyCoursesEntirely(){
        if(!loggedIn) return;
        if(swipeRefreshLayout != null){
            swipeRefreshLayout.setRefreshing(true);
        }
        executeGetMyCourses();
    }

    public void executeGetMyCourses(){
        new GetMyListTask(this).execute();
    }

    private static class GetMyListTask extends AsyncTask<Void, Void, List<Course>> {

        WeakReference<MyCoursesFragment> contextWeakReference;

        GetMyListTask(MyCoursesFragment fragment){
            contextWeakReference = new WeakReference<>(fragment);
        }

        @Override
        protected List<Course> doInBackground(Void... voids) {
            MyCoursesFragment fragment = contextWeakReference.get();
            if(fragment == null) return null;
            if(fragment.sessionId == null) return null;
            Context context = fragment.getContext();
            if (context == null) return null;

            //get my course list
            String coursesHtml = getMyCoursesHtml(context);
            if(coursesHtml == null){
                Objects.requireNonNull(fragment.getActivity()).runOnUiThread(() -> Toast.makeText(context, fragment.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show());
                return null;
            }else if(coursesHtml.equals("UNEXPECTED")) {
                Objects.requireNonNull(fragment.getActivity()).runOnUiThread(() -> Toast.makeText(context, fragment.getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show());
                return null;
            }
            List<Course> myCourses = processAndStoreMyCourses(coursesHtml, context);
            if(myCourses == null){
                Objects.requireNonNull(fragment.getActivity()).runOnUiThread(() -> Toast.makeText(context, fragment.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show());
                return null;
            }
            Objects.requireNonNull(fragment.getActivity()).runOnUiThread(fragment::onRegisterStatusChanged);
            return myCourses;
        }

        @Override
        protected void onPostExecute(List<Course> courses) {
            MyCoursesFragment fragment = contextWeakReference.get();
            if(fragment != null){
                fragment.onLoadFinished();
                if(courses == null){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        fragment.loadProgress.setProgress(100, true);
                    }else{
                        fragment.loadProgress.setProgress(100);
                    }
                    new Handler().postDelayed(() -> fragment.loadProgress.setVisibility(View.GONE), 100);
                    if(fragment.swipeRefreshLayout != null){
                        fragment.swipeRefreshLayout.setRefreshing(false);
                    }
                    Toast.makeText(fragment.getContext(), fragment.getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show();
                }else {
                    fragment.processMyCoursesData(courses);
                }
            }
        }
    }

    static String getMyCoursesHtml(Context context){
        OkHttpClient client = new OkHttpClient();
        String getListUrl = context.getString(R.string.get_my_list_url);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String sessionId = sharedPreferences.getString(context.getString(R.string.session_id), null);
        if(sessionId == null){
            return null;
        }
        String term = getPreferredTerm(context);
        if(term == null){
            term = getRegisterValidTerm(context);
            if(term == null){
                Log.e(TAG, "Initial valid term for null term is null");
                return null;
            }
        }
        RequestBody requestBody = new FormBody.Builder()
                .add("term_in", term)
                .build();
        Request request = new Request.Builder()
                .url(getListUrl)
                .header("cookie", sessionId)
                .post(requestBody)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
            }else {
                Log.e(TAG, "Get my list post response is null");
                return null;
            }
            if(htmlResponse.contains("http-equiv=\"refresh\"")){
                //need to re-verify
                //update session id
                sessionId = reauthorizeWithCastgc(context);
                if(sessionId == null){
                    Log.e(TAG, "Failed to settle on a valid session id for get my list post request");
                    return null;
                }
                //retry get my list with new session id
                request = request.newBuilder()
                        .header("cookie", sessionId)
                        .build();
                response = client.newCall(request).execute();
                if (response.body() != null) {
                    htmlResponse = response.body().string();
                }else {
                    Log.e(TAG, "Retry with new session id get my list post response is null");
                    return null;
                }
            }
            if(htmlResponse.contains("<TITLE>Select a Semester</TITLE>")){
                //term is not valid
                term = getRegisterValidTerm(context);
                if(term == null){
                    Log.e(TAG, "Initial valid term is null");
                    return null;
                }
                //update request body with the new and valid term info
                requestBody = new FormBody.Builder()
                        .add("term_in", term)
                        .build();
                //update request too
                request = request.newBuilder()
                        .post(requestBody)
                        .build();
                //get new response
                response = client.newCall(request).execute();
                if (response.body() != null) {
                    //update html response
                    htmlResponse = response.body().string();
                }else {
                    Log.e(TAG, "Retry with new session id get my list post response is null");
                    return null;
                }
            }
            return htmlResponse;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return "UNEXPECTED";
        }
    }

    static List<Course> processAndStoreMyCourses(String htmlResponse, Context context){
        Document document = Jsoup.parse(htmlResponse);
        //crn part
        Elements registeredCoursesElements = document.select("[summary=\"current schedule\"] [NAME=\"CRN_IN\"]");
        List<Course> registeredCourses = new LinkedList<>();
        Set<String> registeredCrnSet = new HashSet<>();
        for (Element crnElement : registeredCoursesElements) {
            String crn = crnElement.attr("VALUE");
            Course course = new Course();
            course.setCrn(crn);
            registeredCourses.add(course);//or ownText()
            registeredCrnSet.add(crn);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putStringSet(context.getString(R.string.my_registered_crn_set), registeredCrnSet).apply();

        //registered status time part
        registeredCoursesElements = document.select("[summary=\"current schedule\"] [NAME=\"MESG\"]");
        StringBuilder registeredTimeList = new StringBuilder();
        //String originalRegisterTimeList = sharedPreferences.getString(context.getString(R.string.my_course_registered_status_list), null);
        //if(originalRegisterTimeList != null) registeredTimeList.append(originalRegisterTimeList);
        for (int i = 0; i < registeredCoursesElements.size(); i++) {
            Element msgElement = registeredCoursesElements.get(i).parent();
            String registerStatus = msgElement.text();
            int timeStartPos = registerStatus.indexOf("** on ");
            if(timeStartPos != -1){
                timeStartPos += 6;//get the actual start position
                registerStatus = "Registered on: " + registerStatus.substring(timeStartPos);
            }
            Course course = registeredCourses.get(i);
            String combinedRegisterStatus = course.getCrn() + "^" + registerStatus;
            if(registeredTimeList.length() == 0) registeredTimeList.append(combinedRegisterStatus);
            else {
                registeredTimeList.append("-").append(combinedRegisterStatus);
            }
            Log.i(TAG, "REGISTER STATUS: " + registerStatus);
            course.setRegisterStatus(registerStatus);
            registeredCourses.set(i, course);
        }
        sharedPreferences.edit().putString(context.getString(R.string.my_course_registered_status_list), registeredTimeList.toString()).apply();

        //saved course selection list part
       List<Course> savedCourses = getSavedCourseSelectionList(context);
       if(savedCourses != null){
           registeredCourses.addAll(0, savedCourses);
       }

        return fillCourseInfo(context, registeredCourses);
    }

    //Note: returned courses aren't fully filled to be functional yet, only contains crn
    static List<Course> getSavedCourseSelectionList(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String savedList = sharedPreferences.getString(context.getString(R.string.my_course_selection_list), null);
        if(savedList != null && !savedList.isEmpty()){
            List<String> savedCrns = getCrnListFromSavedList(savedList);
            List<Course> savedCourses = new LinkedList<>();
            if(!savedCrns.isEmpty()){
                for (int i = 0; i < savedCrns.size(); i++) {//put saved list at first
                    Course savedCourse = new Course();
                    String crn = savedCrns.get(i);
                    savedCourse.setCrn(crn);
                    savedCourse.setRegisterStatus(context.getString(R.string.register_status_on_my_list));
                    savedCourses.add(savedCourse);
                }
            }
            return savedCourses;
        }
        return null;
    }

    static String getRegisterValidTerm(Context context){
        String getListUrl = context.getString(R.string.get_my_list_url);
        String sessionId = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.session_id), null);
        if(sessionId == null) return null;
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(getListUrl)
                .header("cookie", sessionId)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
            }else {
                Log.e(TAG, "Get term response is null");
                return null;
            }
            if(htmlResponse.contains("http-equiv=\"refresh\"")){
                //need to re-verify
                Log.i(TAG, "Reverifying for getting the list");
                sessionId = reauthorizeWithCastgc(context);
                if(sessionId == null) {
                    Log.e(TAG, "Failed to settle on a valid session id for get term request");
                    return null;
                }
                //retry get my list with new session id
                request = request.newBuilder()
                        .header("cookie", sessionId)
                        .build();
                response = client.newCall(request).execute();
                if (response.body() != null) {
                    htmlResponse = response.body().string();
                }else {
                    Log.e(TAG, "Retry with new session id get term response is null");
                    return null;
                }
            }
            //Log.i(TAG, "Get valid term info:\n" + htmlResponse);
            Document document = Jsoup.parse(htmlResponse);
            Element termElement = document.select("option[value]").first();
            if(termElement != null) return termElement.attr("VALUE");//get term value
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    void processMyCoursesData(List<Course> courses){
        if(courses.isEmpty()){
            if(emptyMyCoursesText != null){
                emptyMyCoursesText.setVisibility(View.VISIBLE);
            }
            //no courses on this guy's list, display empty message
        }else {
            if(emptyMyCoursesText != null){
                emptyMyCoursesText.setVisibility(View.GONE);
            }
            if(myListAdapter != null){
                myListAdapter.swapNewDataSet(courses);
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
    }

    public void query(String query){
        if(myListAdapter != null){
            myListAdapter.getFilter().filter(query);
        }else if(myCoursesRecyclerView != null){
            ListRecyclerAdapter adapter = (ListRecyclerAdapter)myCoursesRecyclerView.getAdapter();
            if(adapter != null){
                adapter.getFilter().filter(query);
            }
        }
    }

    //long running task
    public static List<Course> fillCourseInfo(Context context, List<Course> courses){
        if(context == null || courses == null) return null;
        String url = context.getString(R.string.get_catalog_url);
        String preferredTerm = getPreferredTerm(context);
        String catalogHtml = getCatalogHtml(url, preferredTerm, "ALL", "N");
        if(catalogHtml == null) {
            Log.e(TAG, "Get fill course info html returned null");
            return null;
        }
        //Log.i(TAG, "Catalog html for fill course info:\n" + catalogHtml);
        Document document = Jsoup.parse(catalogHtml);
        List<Course> filledCourses = new LinkedList<>();
        for (Course course : courses) {
            String crn = course.getCrn();
            if (crn == null) {
                Log.e(TAG, "Failed to get crn from course object to fill course info");
                return null;
            }
            try{
                Element courseElement = document.selectFirst("td p small a:containsOwn(" + crn + ")")
                        .parent().parent().parent().parent();//navigate up to td tag
                CourseBuilder courseBuilder = getCourseBuilderFromElement(courseElement, course);
                if (courseBuilder == null) {
                    Log.e(TAG, "Something wrong with the element got from html");
                    return null;//something wrong with the element got from html
                }

                //add additional exam/lect/lab/sem info elements
                Element additionalElement = courseElement.nextElementSibling();
                while (additionalElement != null && additionalElement.children().size() == 12) {
                    //Log.i(TAG, "Added additional info: " + Arrays.toString(additionalElement.children().eachText().toArray()));
                    courseBuilder.addType(additionalElement.child(3).text())
                            .addDays(additionalElement.child(4).text())
                            .addTime(additionalElement.child(5).text())
                            .addLocation(additionalElement.child(6).text())
                            .addPeriod(additionalElement.child(7).text());
                    additionalElement = additionalElement.nextElementSibling();//next
                }
                filledCourses.add(courseBuilder.buildCourse());
            }catch (NullPointerException e){
                Log.e(TAG, "PARENT NULL, html:\n" + catalogHtml);
                e.printStackTrace();
                return null;
            }
        }
        return filledCourses;
    }

    public static List<String> getCrnListFromSavedList(String list){
        String[] savedArray = list.split("-");
        List<String> crnList = new LinkedList<>();
        for (String savedItem : savedArray) {
            int crnStartPos =savedItem.indexOf("crn=");
            if(crnStartPos == -1) continue;
            crnStartPos += 4;
            crnList.add(savedItem.substring(crnStartPos));
        }
        return crnList;
    }

    /**
     * Register courses with lots of variations
     *
     * @param context to get related credentials to verify with the server, and get the course list stored in sharedPreferences
     * @param specificCourses if specified, only register courses in this array, otherwise register the courses on the sharedPreferences course list
     * @return null if something went wrong, empty list if everything worked fine, otherwise return a string list of problems with the registration
     */
    public static List<String[]> registerCourses(Context context, String... specificCourses){
        Set<String> registerCrnSet;
        if(specificCourses != null && specificCourses.length > 0){
            registerCrnSet = new HashSet<>(Arrays.asList(specificCourses));
        }else {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            registerCrnSet = sharedPreferences.getStringSet(context.getString(R.string.my_selection_crn_set), null);
            if(registerCrnSet == null || registerCrnSet.isEmpty()) return null;
        }
        String myCoursesHtml = getMyCoursesHtml(context);
        if(myCoursesHtml == null) return null;
        Document document = Jsoup.parse(myCoursesHtml);
        //need to append these registered classes post requests to a register request
        Elements registeredPostRequests = document.select("[summary=\"current schedule\"] [NAME]");

        //start registering
        //get session id
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String sessionId = sharedPreferences.getString(context.getString(R.string.session_id), null);
        if(sessionId == null) sessionId = reauthorizeWithCastgc(context);
        if(sessionId == null) return null;

        //get term
        String term = getPreferredTerm(context);
        if(term == null){
            term = getRegisterValidTerm(context);
            if(term == null){
                Log.e(TAG, "Get valid term failed for register classes");
                return null;
            }
        }

        //form request
        OkHttpClient client = new OkHttpClient();
        FormBody.Builder requestBodyBuilder = new FormBody.Builder()
                //first, add some non-relevant but necessary DUMMY requests
                //well, this post request will fail if these DUMMY values aren't included :/
                .add("term_in", term)//term is important though
                .add("RSTS_IN", "DUMMY")
                .add("assoc_term_in", "DUMMY")
                .add("CRN_IN", "DUMMY")
                .add("start_date_in", "DUMMY")
                .add("end_date_in", "DUMMY")
                .add("SUBJ", "DUMMY")
                .add("CRSE", "DUMMY")
                .add("SEC", "DUMMY")
                .add("LEVL", "DUMMY")
                .add("CRED", "DUMMY")
                .add("GMOD", "DUMMY")
                .add("TITLE", "DUMMY")
                .add("MESG", "DUMMY")
                .add("REG_BTN", "DUMMY");
        //next add the original post requests got from myCourses html
        for (Element postElement : registeredPostRequests) {
            requestBodyBuilder.add(postElement.attr("NAME"), postElement.attr("VALUE"));
        }
        //then add the course to be registered requests
        for (String crn : registerCrnSet) {
            requestBodyBuilder
                    .add("RSTS_IN", "RW")//register action
                    .add("CRN_IN", crn)
                    .add("assoc_term_in", "")
                    .add("start_date_in", "")
                    .add("end_date_in", "");
        }
        //then add other summary requests
        String totalRegisteredCount = String.valueOf(registeredPostRequests.size()/13);//every registered course has 13 requests. Alternatively, can select all tr elements and use the count of the result minus 1 (the first one is summary) to get the registered count
        String totalRegisterCount = String.valueOf(registerCrnSet.size());
        requestBodyBuilder
                .add("regs_row", totalRegisteredCount)
                .add("wait_row", "0")//not sure how this value is influenced
                .add("add_row", totalRegisterCount)
                .add("REG_BTN", "Submit Changes");
        //it's not over yet, now send the request bundled with these post request parameters
        Request request = new Request.Builder()
                .url(context.getString(R.string.register_courses_url))
                .post(requestBodyBuilder.build())
                .header("cookie", sessionId)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
            }else {
                Log.e(TAG, "Get my list post response is null");
                return null;
            }
            //deal with possible errors
            //session id expired
            if(htmlResponse.contains("http-equiv=\"refresh\"")){
                //need to re-verify
                //update session id
                sessionId = reauthorizeWithCastgc(context);
                if(sessionId == null){
                    Log.e(TAG, "Failed to settle on a valid session id for get my list post request");
                    return null;
                }
                //retry get my list with new session id
                request = request.newBuilder()
                        .header("cookie", sessionId)
                        .build();
                response = client.newCall(request).execute();
                if (response.body() != null) {
                    htmlResponse = response.body().string();
                }else {
                    Log.e(TAG, "Retry with new session id get my list post response is null");
                    return null;
                }
            }
            //term is not valid
            if(htmlResponse.contains("Your Faculty or Advisor is reviewing your registration at this time. Please try again later.")){
                //term is not valid
                term = getRegisterValidTerm(context);
                if(term == null){
                    Log.e(TAG, "Initial valid term is null");
                    return null;
                }
                //update request body with the new and valid term info
                RequestBody requestBody = request.body();
                if(requestBody == null) return null;
                requestBody = modifyRequestBody(requestBody, "term_in", term);
                //update request too
                request = request.newBuilder()
                        .post(requestBody)
                        .build();
                //get new response
                response = client.newCall(request).execute();
                if (response.body() != null) {
                    //update html response
                    htmlResponse = response.body().string();
                }else {
                    Log.e(TAG, "Retry with new session id get my list post response is null");
                    return null;
                }
            }
            if(htmlResponse.contains("Error occurred while processing registration changes.")){//happens if post requests are malformed, for example, unmatched parameter group rows, or invalid crn digits (not 5)
                Log.e(TAG, "Error occurred while processing registration changes.\n" + htmlResponse);
                return null;
            }//I'm doing it the stupid but direct and fast way
            /*Document errorDoc = Jsoup.parse(htmlResponse);
            if(!errorDoc.select("[errortext]").isEmpty()){
                Log.e(TAG, "Error occurred while processing registration changes.\n" + htmlResponse);
                return null;
            }*///not using this as this uses more computation power and time
            /*if(htmlResponse.contains("class=\"errortext\"")){//happens if post requests are malformed, for example, unmatched parameter group rows, or invalid crn digits (not 5)
                Log.e(TAG, "Error occurred while processing registration changes.\n" + htmlResponse);
                return null;
            }*///for some reason this doesn't work, alternatively, determine by text
            //finally, it's time to process the html response and see if the registrations went well
            //note: at this point, after bypassing the checks above, this means that all of the technical errors are eliminated (hopefully), the remaining errors are
            //messages like "Duplicate CRN", "Class is full" or something like that

            List<String[]> errors = getErrorsFromRegistrationResponse(htmlResponse);
            if(errors.isEmpty()){
                //todo update stored lists and registered courses and other stuff
            }
            return errors;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Drop courses. Use CAREFULLY!
     *
     * @param context to get related credentials to verify with the server, and get the course list stored in sharedPreferences
     * @param courseCrns crns of the courses to be dropped
     * @return false if something went wrong, true if course is dropped successfully
     */
    public static boolean dropCourse(Context context, @NonNull List<String> courseCrns){
        String myCoursesHtml = getMyCoursesHtml(context);
        if(myCoursesHtml == null) return false;
        Document document = Jsoup.parse(myCoursesHtml);
        //need to append these registered classes post requests to a register request
        Elements registeredCourseElements = document.select("[summary=\"current schedule\"] tr");
        if(registeredCourseElements.isEmpty()) return false;//something is wrong

        //start dropping
        //get session id
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String sessionId = sharedPreferences.getString(context.getString(R.string.session_id), null);
        if(sessionId == null) sessionId = reauthorizeWithCastgc(context);
        if(sessionId == null) return false;

        //get term
        String term = getPreferredTerm(context);
        if(term == null){
            term = getRegisterValidTerm(context);
            if(term == null){
                Log.e(TAG, "Get valid term failed for register classes");
                return false;
            }
        }

        //form request
        OkHttpClient client = new OkHttpClient();
        FormBody.Builder requestBodyBuilder = new FormBody.Builder()
                //first, add some non-relevant but necessary DUMMY requests
                //well, this post request will fail if these DUMMY values aren't included :/
                .add("term_in", term)//term is important though
                .add("RSTS_IN", "DUMMY")
                .add("assoc_term_in", "DUMMY")
                .add("CRN_IN", "DUMMY")
                .add("start_date_in", "DUMMY")
                .add("end_date_in", "DUMMY")
                .add("SUBJ", "DUMMY")
                .add("CRSE", "DUMMY")
                .add("SEC", "DUMMY")
                .add("LEVL", "DUMMY")
                .add("CRED", "DUMMY")
                .add("GMOD", "DUMMY")
                .add("TITLE", "DUMMY")
                .add("MESG", "DUMMY")
                .add("REG_BTN", "DUMMY");
        //next add the original post requests got from myCourses html
        for (Element courseElement : registeredCourseElements) {
            Elements courseSubElements = courseElement.children();
            boolean drop = false;
            for (Element courseSubElement : courseSubElements) {
                //crn matches one of the courses on the drop list
                if(courseSubElement.attr("NAME").equals("CRN_IN") &&
                        courseCrns.contains(courseSubElement.attr("VALUE"))){
                    drop = true;
                }
                if(drop && courseSubElement.attr("NAME").equals("RSTS_IN")){//we are not restarting the loop because this attribute is after the crn attirbute
                    //set drop attribute
                    courseElement.attr("VALUE", "DW");
                }
                //add all of these attributes into the post request body
                requestBodyBuilder.add(courseElement.attr("NAME"), courseElement.attr("VALUE"));
            }
        }
        //then add other summary requests
        String totalRegisteredCount = String.valueOf(registeredCourseElements.size());
        requestBodyBuilder
                .add("regs_row", totalRegisteredCount)
                .add("wait_row", "0")//not sure how this value is influenced
                .add("add_row", "0")//no adding
                .add("REG_BTN", "Submit Changes");
        //it's not over yet, now send the request bundled with these post request parameters
        Request request = new Request.Builder()
                .url(context.getString(R.string.register_courses_url))
                .post(requestBodyBuilder.build())
                .header("cookie", sessionId)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
            }else {
                Log.e(TAG, "Get my list post response is null");
                return false;
            }
            //deal with possible errors
            //session id expired
            if(htmlResponse.contains("http-equiv=\"refresh\"")){
                //need to re-verify
                //update session id
                sessionId = reauthorizeWithCastgc(context);
                if(sessionId == null){
                    Log.e(TAG, "Failed to settle on a valid session id for get my list post request");
                    return false;
                }
                //retry get my list with new session id
                request = request.newBuilder()
                        .header("cookie", sessionId)
                        .build();
                response = client.newCall(request).execute();
                if (response.body() != null) {
                    htmlResponse = response.body().string();
                }else {
                    Log.e(TAG, "Retry with new session id get my list post response is null");
                    return false;
                }
            }
            //term is not valid
            if(htmlResponse.contains("Your Faculty or Advisor is reviewing your registration at this time. Please try again later.")){
                //term is not valid
                term = getRegisterValidTerm(context);
                if(term == null){
                    Log.e(TAG, "Initial valid term is null");
                    return false;
                }
                //update request body with the new and valid term info
                RequestBody requestBody = request.body();
                if(requestBody == null) return false;
                requestBody = modifyRequestBody(requestBody, "term_in", term);
                //update request too
                request = request.newBuilder()
                        .post(requestBody)
                        .build();
                //get new response
                response = client.newCall(request).execute();
                if (response.body() != null) {
                    //update html response
                    htmlResponse = response.body().string();
                }else {
                    Log.e(TAG, "Retry with new session id get my list post response is null");
                    return false;
                }
            }
            if(htmlResponse.contains("class=\"errortext\"")){//happens if post requests are malformed, for example, unmatched parameter group rows, or invalid crn digits (not 5)
                Log.e(TAG, "Error occurred while processing registration changes.\n" + htmlResponse);
                return false;
            }
            //finally, the course is dropped
            //todo update stored lists and registered courses and other stuff
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Parse errors from registration response html
     *
     * @param htmlResponse the html to parse all the errors from
     * @return List of errors, first element of String[] is crn, second one is error message. Empty if no errors are present
     */
    static List<String[]> getErrorsFromRegistrationResponse(String htmlResponse){
        Document document = Jsoup.parse(htmlResponse);
        List<String[]> errors = new ArrayList<>();
        Element registerResponseElement = document.selectFirst("table + table[class=datadisplaytable] tbody");//second table in the two adjacent tables, which is the one with errors, or [summary="This layout table is used to present Registration Errors."], which is the error table, but could change easier than the method in use
        if(registerResponseElement == null){
            //no error messages
            return errors;//empty
        }
        Elements tmp = registerResponseElement.children();
        if(tmp.isEmpty()) return errors;//empty
        List<Element> errorElements = tmp.subList(1, registerResponseElement.children().size());
        if(errorElements.isEmpty()) return errors;//empty
        for (Element errorElement : errorElements) {
            String[] errorArray = {errorElement.child(1).ownText(), errorElement.child(0).ownText()};
            errors.add(errorArray);
        }
        return errors;
    }

    static RequestBody modifyRequestBody(RequestBody requestBody, String name, String value){
        if(requestBody == null) return null;
        String postBodyString = bodyToString(requestBody);
        if(postBodyString == null) return requestBody;
        int replaceStartPos = postBodyString.indexOf(name + "=");
        if(replaceStartPos == -1){
            postBodyString += ((postBodyString.length() > 0) ? "&" : "") + name + "=" + value;
        }else {
            replaceStartPos += name.length() + 1;
            int replaceEndPos = postBodyString.indexOf(replaceStartPos, '&');
            if(replaceEndPos == -1) replaceEndPos = postBodyString.length() - 1;
            postBodyString = postBodyString.substring(0, replaceStartPos) + value + postBodyString.substring(replaceEndPos);
        }
        return RequestBody.create(requestBody.contentType(), postBodyString);
    }

    public static String bodyToString(final RequestBody request){
        try (Buffer buffer = new Buffer()) {
            if (request != null)
                request.writeTo(buffer);
            else
                return "";
            return buffer.readUtf8();
        } catch (final IOException e) {
            return null;
        }
    }

    public void onLoginSuccess() {
        loggedIn = true;
        if (mListener != null) {
            mListener.myCourseOnLoginSuccess();
        }
    }

    public void onRegisterStatusChanged() {
        if (mListener != null) {
            mListener.myCourseOnRegisterStatusChanged();
        }
    }

    public void onListStatusChanged(String crn) {
        if (mListener != null) {
            mListener.myCourseOnListStatusChanged(crn);
        }
    }

    public void onLoadFinished(){
        if (mListener != null) {
            mListener.myCourseOnLoadFinished();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof MyCoursesOnFragmentInteractionListener) {
            mListener = (MyCoursesOnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement MyCoursesOnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == LOGIN_REQUEST_CODE){
            if(resultCode == LOGIN_SUCCESS_CODE){
                //login success, get the list
                sessionId = sharedPreferences.getString(getString(R.string.session_id), null);
                if(sessionId == null) signInPromptText.setText("Sign in failed, please try again");
                else {
                    signInButton.setVisibility(View.GONE);
                    signInPromptText.setVisibility(View.GONE);
                    loadProgress.setVisibility(View.VISIBLE);
                    swipeRefreshLayout.setEnabled(true);
                    myCoursesRecyclerView.setVisibility(View.VISIBLE);
                    new GetMyListTask(this).execute();
                    onLoginSuccess();
                }
            }
            else {//RESULT_CANCELLED
                signInPromptText.setText("Sign in aborted, please sign in to view your courses");
            }
        }else if(requestCode == COURSE_DETAIL_REQUEST_CODE && resultCode == COURSE_CHANGE_TO_LIST_CODE && data != null){
            String crn = data.getStringExtra(COURSE_LIST_CHANGE_CRN_KEY);
            if(crn != null){
                if(data.getBooleanExtra(COURSE_REGISTER_STATUS_CHANGED, false)){
                    refreshMyCoursesEntirely();
                }else {
                    refreshMyCoursesLocally();
                }
                onListStatusChanged(crn);
            }
        }
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
    public interface MyCoursesOnFragmentInteractionListener {

        void myCourseOnLoginSuccess();

        void myCourseOnRegisterStatusChanged();

        void myCourseOnListStatusChanged(String crn);

        void myCourseOnLoadFinished();

    }
}
