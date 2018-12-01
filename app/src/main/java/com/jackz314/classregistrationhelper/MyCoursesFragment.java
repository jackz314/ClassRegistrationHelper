package com.jackz314.classregistrationhelper;

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
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import okhttp3.HttpUrl;

import static com.jackz314.classregistrationhelper.Constants.COURSE_CHANGE_TO_LIST_CODE;
import static com.jackz314.classregistrationhelper.Constants.COURSE_DETAIL_REQUEST_CODE;
import static com.jackz314.classregistrationhelper.Constants.COURSE_LIST_CHANGE_CRN_KEY;
import static com.jackz314.classregistrationhelper.Constants.COURSE_REGISTER_STATUS_CHANGED;
import static com.jackz314.classregistrationhelper.Constants.LOGIN_REQUEST_CODE;
import static com.jackz314.classregistrationhelper.Constants.LOGIN_SUCCESS_CODE;
import static com.jackz314.classregistrationhelper.CourseUtils.fillCourseInfo;
import static com.jackz314.classregistrationhelper.CourseUtils.getMyCoursesHtml;
import static com.jackz314.classregistrationhelper.CourseUtils.getSavedCourseSelectionList;
import static com.jackz314.classregistrationhelper.CourseUtils.processAndStoreMyCourses;
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

    void onLoginSuccess() {
        loggedIn = true;
        if (mListener != null) {
            mListener.myCourseOnLoginSuccess();
        }
    }

    void onRegisterStatusChanged() {
        if (mListener != null) {
            mListener.myCourseOnRegisterStatusChanged();
        }
    }

    void onListStatusChanged(String crn) {
        if (mListener != null) {
            mListener.myCourseOnListStatusChanged(crn);
        }
    }

    void onLoadFinished(){
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
