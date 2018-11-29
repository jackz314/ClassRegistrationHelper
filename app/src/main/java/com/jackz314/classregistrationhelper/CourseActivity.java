package com.jackz314.classregistrationhelper;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static com.jackz314.classregistrationhelper.CatalogFragment.COURSE_CHANGE_TO_LIST_CODE;
import static com.jackz314.classregistrationhelper.CatalogFragment.COURSE_LIST_CHANGE_CRN_KEY;
import static com.jackz314.classregistrationhelper.CatalogFragment.COURSE_REGISTER_STATUS_CHANGED;
import static com.jackz314.classregistrationhelper.MyCoursesFragment.registerCourses;

public class CourseActivity extends AppCompatActivity {

    CollapsingToolbarLayout toolbarLayout;

    private static String urlForCourse;
    private String courseCrn;
    private static final String TAG = "CourseActivity";

    private enum CourseListStatus{
        NOT_ON_LIST, ON_LIST, REGISTERED
    }
    private boolean selectionNeedToUpdateCatalog = false, registerNeedToUpdateCatalog = false;
    private CourseListStatus courseListStatus = CourseListStatus.NOT_ON_LIST;
    private TextView registerStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course);
        toolbarLayout = findViewById(R.id.course_toolbar_layout);
        registerStatusText = findViewById(R.id.course_register_status);

        urlForCourse = getIntent().getBundleExtra("bundle").getString("URL");
        if(urlForCourse == null) return;
        courseCrn = urlForCourse.substring(urlForCourse.indexOf("crn=") + 4);

        //tempText.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlForCourse))));
        toolbarLayout.setTitle("Loading...");
        new GetCourseDetailTask(this).execute();
        setSupportActionBar(findViewById(R.id.course_toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private static class GetCourseDetailTask extends AsyncTask<Void, Void, Course>{

        private WeakReference<CourseActivity> activityReference;

        GetCourseDetailTask(CourseActivity activity){
            activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Course doInBackground(Void... voids) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(urlForCourse)
                    .build();
            CourseActivity activity = activityReference.get();
            try {
                Response rawResponse = client.newCall(request).execute();
                String htmlResponse;
                if (rawResponse.body() != null) {
                    htmlResponse = rawResponse.body().string();
                }else {
                    return null;//empty
                }
                Course course = activity.getIntent().getBundleExtra("bundle").getParcelable("CourseInfo");
                if(course == null) {
                    //something wrong with the intent to this activity, can't proceed, abort, notify user, and finish activity
                    Toast.makeText(activity, activity.getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show();
                    activity.finish();
                    return null;
                }
                CourseBuilder courseBuilder = course.newBuilder();
                Document document = Jsoup.parse(htmlResponse);
                Element courseElements = document.select("[class=datadisplaytable]").first().child(0);
                courseBuilder
                        /*.setCrn(courseElements.child(0).text().substring(courseElements.child(0).text().length() - 5))
                        .setNumber(courseElements.child(1).child(1).ownText())*/
                        .setTitle(courseElements.child(2).child(1).ownText())//original title might not be perfect
                        .setUnits(courseElements.child(3).child(1).ownText())
                        .setFee(courseElements.child(4).child(1).ownText());
                Element seatElements = document.select("[class=datadisplaytable]").get(1).child(1).child(1);//skip child(0), which is a caption
                courseBuilder.setTotalSeats(seatElements.child(1).ownText())//skip child(0) because it's just a label that says seats
                        .setTakenSeats(seatElements.child(2).ownText())
                        .setAvailableSeats(seatElements.child(3).ownText());
                Elements restrictionElements = document.select("[class=dataentrytable]");
                courseBuilder.setDescription(restrictionElements.first().child(0).child(1).text());
                        //use sublist to remove the first description string
                for(Element restrictionElement : restrictionElements.subList(1, restrictionElements.size())){
                    //get category of restriction
                    String identifier;
                    if(restrictionElement.hasText() && restrictionElement.children() != null && restrictionElement.child(0).children() != null) {
                        identifier = restrictionElement.child(0).child(0).text();
                    } else identifier = "";
                    Log.i(TAG, identifier + "[identifier]");
                    if (identifier.contains("You must have declared one of the following majors")){
                        courseBuilder.setMajorRestrictionYes(restrictionElement.child(0).children().eachText()
                                .subList(1, restrictionElement.child(0).children().eachText().size()));
                    }else if (identifier.contains("You may not take this course if you have declared the following majors")){
                        courseBuilder.setMajorRestrictionNo(restrictionElement.child(0).children().eachText()
                                .subList(1, restrictionElement.child(0).children().eachText().size()));
                    }else if (identifier.contains("You may not be a part of the following colleges")){
                        courseBuilder.setCollegeRestriction(restrictionElement.child(0).children().eachText()
                                .subList(1, restrictionElement.child(0).children().size()));
                    }else if (identifier.contains("Your class level must be one of the following")){
                        courseBuilder.setLevelRestrictionYes(restrictionElement.child(0).children().eachText()
                                .subList(1, restrictionElement.child(0).children().size()));
                    }else if (identifier.contains("Your level status can not be one of the following")){
                        courseBuilder.setLevelRestrictionNo(restrictionElement.child(0).children().eachText()
                                .subList(1, restrictionElement.child(0).children().size()));
                    }else if(identifier.contains("Prerequisites for this course")){
                        //prerequisite structure is kind of messy, maybe deal with it later in a more organized way, for now, just get all the text
                        Element removeFirst = restrictionElement.child(0);
                        removeFirst.child(0).remove();
                        courseBuilder.setPrerequisite(removeFirst.text());
                    }else if(identifier.contains("Offered ")){
                        courseBuilder.setOfferedTerms(restrictionElement.text());
                    }else{//other additional information, like "Additional Course Information"
                        courseBuilder.addAdditionalRestriction(restrictionElement.select("[class=dedefault]").text());
                    }
                }
                       /* .setLevelRestrictionYes(restrictionElements.get(2).child(0).children().eachText().subList(1, restrictionElements.get(2).child(0).children().eachText().size()))//store each level into list
                        .setLevelRestrictionNo(restrictionElements.get(3).child(0).children().eachText().subList(1, restrictionElements.get(3).children().eachText().size()))
                        .setPrerequisite(restrictionElements.get(4).child(0).select("[class=dedefault]").text())
                        .setOfferedTerms(restrictionElements.get(5).text());*/
                return courseBuilder.buildCourse();
            } catch (IOException e) {//internet problem
                activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show());
                e.printStackTrace();
            } catch (Exception e) {//parsing html or other errors
                activity.runOnUiThread(() -> Toast.makeText(activity, activity.getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show());
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Course course) {
            if(course != null){
                CourseActivity activity = activityReference.get();
                if(activity != null){//activity still exist
                    activity.setUiFromCourse(course);
                }
            }
            super.onPostExecute(course);
        }
    }

    private static class GetInstructorPhoto extends AsyncTask<String, Void, String>{

        private WeakReference<CourseActivity> activityReference;

        GetInstructorPhoto(CourseActivity activity){
            activityReference = new WeakReference<>(activity);
        }

        @Override
        protected String doInBackground(String... strings) {
            String instructorName = strings[0];
            //invalid names
            if(instructorName == null || instructorName.isEmpty() || instructorName.equals("Staff")){
                return null;
            }
            CourseActivity activity = activityReference.get();
            if(activity == null) return null;
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(activity.getString(R.string.get_faculty_directory_url))
                    .build();
            try {
                Response response = client.newCall(request).execute();
                String htmlResponse = response.body() != null ? response.body().string() : null;
                if(htmlResponse == null || htmlResponse.isEmpty()) return null;//empty
                Document document = Jsoup.parse(htmlResponse);
                //select ones that contain instructor's last name
                String instructorLastName = instructorName.substring(0, instructorName.indexOf(','));
                String instructorFirstName = instructorName.substring(instructorName.indexOf(' ') + 1);
                Elements elements = document.select("a:contains(" + instructorLastName + ")")//last name
                        .select("a:contains(" + instructorFirstName + ")")//first name
                        .select("a:not(:contains(@))");//remove email elements
                Log.i(TAG, elements.toString());
                Element instructorElement = elements.first();//shouldn't have more than one, but just in case, select the first one
                if(instructorElement == null){
                    return null;
                }
                Element photoElement = instructorElement.parent().previousElementSibling().child(0);
                Log.i(TAG, photoElement.toString());
                return photoElement.attr("src");
            } catch (IOException e) {//internet problems
                e.printStackTrace();
                Toast.makeText(activity, activity.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show();
                return null;
            } catch (Exception e) {//other problems
                e.printStackTrace();
                Toast.makeText(activity, activity.getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String photoUrl) {
            if(photoUrl != null && !photoUrl.isEmpty() && !photoUrl.contains("sealofucmerced")){//has actual profile picture
                CourseActivity activity = activityReference.get();
                if(activity != null){
                    ImageView profilePictureView = activity.toolbarLayout.findViewById(R.id.profile_picture);
                    try{
                        Glide.with(activity)
                                .load(photoUrl)
                                .into(profilePictureView);
                    }catch (Exception ignored){}

                    Display display = activity.getWindowManager().getDefaultDisplay();
                    Point size = new Point();
                    display.getSize(size);
                    AppBarLayout appBarLayout = activity.findViewById(R.id.course_app_bar);
                    appBarLayout.getLayoutParams().height = size.y/3;
                }
            }
            super.onPostExecute(photoUrl);
        }
    }

    void setUiFromCourse(Course course){
        new GetInstructorPhoto(this).execute(course.getInstructor());
        toolbarLayout.setTitle(course.getNumber());
        TextView titleText = findViewById(R.id.course_title);
        TextView crnText = findViewById(R.id.course_crn);
        TextView instructorText = findViewById(R.id.course_instructor);
        TextView daysText = findViewById(R.id.course_days);
        TextView timesText = findViewById(R.id.course_time);
        TextView typeText = findViewById(R.id.course_type);
        TextView unitsText = findViewById(R.id.course_units);
        TextView locationText = findViewById(R.id.course_location);
        TextView periodsText = findViewById(R.id.course_periods);
        TextView takenSeatsText = findViewById(R.id.course_taken_seats);
        TextView totalSeatsText = findViewById(R.id.course_total_seats);
        TextView availableSeatsText = findViewById(R.id.course_available_seats);
        TextView prerequisiteText = findViewById(R.id.course_prerequisites);
        TextView additionalInfoText = findViewById(R.id.course_additional_info);
        TextView academicRestrictionText = findViewById(R.id.course_academic_restriction);
        TextView additionalClassesText = findViewById(R.id.course_additional_classes);
        TextView descriptionText = findViewById(R.id.course_description);
        TextView otherInfoText = findViewById(R.id.course_other_info);

        FloatingActionButton courseFab = findViewById(R.id.fab_add_to_list);

        /*ConstraintLayout.LayoutParams zeroMarginParams = new ConstraintLayout.LayoutParams(//param setting for gone views
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        zeroMarginParams.setMargins(0, 0, 0, 0);*/
        boolean requireLabel1 = true, requireLabel2 = true, requireLabel3 = true;

        titleText.setText(course.getTitle());
        String registerStatusStr = course.getRegisterStatus();
        if(registerStatusStr != null){
            registerStatusText.setVisibility(View.VISIBLE);
            registerStatusText.setText(registerStatusStr);
            courseListStatus = CourseListStatus.ON_LIST;
            courseFab.setImageResource(R.drawable.ic_add_to_list_done);
            if(!registerStatusStr.equals(getString(R.string.register_status_on_my_list))){
                courseListStatus = CourseListStatus.REGISTERED;
                courseFab.setImageResource(R.drawable.ic_register_done);
            }
        }
        courseFab.setOnClickListener(view -> {
            switch (courseListStatus) {
                case ON_LIST:
                    removeFromList();
                    registerStatusText.setVisibility(View.GONE);
                    courseFab.setImageResource(R.drawable.ic_add_to_list);
                    courseListStatus = CourseListStatus.NOT_ON_LIST;
                    Snackbar.make(view, "Removed from my list", Snackbar.LENGTH_LONG)
                            .setAction("Undo", view1 -> {
                                registerStatusText.setVisibility(View.VISIBLE);
                                courseFab.setImageResource(R.drawable.ic_add_to_list_done);
                                courseListStatus = CourseListStatus.ON_LIST;
                                addToList();
                            }).show();
                    break;
                case NOT_ON_LIST: //not on my list
                    addToList();
                    registerStatusText.setVisibility(View.VISIBLE);
                    registerStatusText.setText(R.string.register_status_on_my_list);
                    courseFab.setImageResource(R.drawable.ic_add_to_list_done);
                    courseListStatus = CourseListStatus.ON_LIST;
                    Snackbar.make(view, "Added to my list", Snackbar.LENGTH_LONG)
                            .setAction("Undo", view1 -> {
                                registerStatusText.setVisibility(View.GONE);
                                courseFab.setImageResource(R.drawable.ic_add_to_list);
                                undoAddToList();
                                courseListStatus = CourseListStatus.NOT_ON_LIST;
                            }).show();
                    break;
                case REGISTERED: //registered class
                    courseFab.setImageResource(R.drawable.ic_register_done);
                    new AlertDialog.Builder(this)
                            .setTitle("Are you sure?")
                            .setMessage("Do you really want to drop this class?")
                            .setPositiveButton("Yes", (dialogInterface, i) -> {
                                dropCourse();
                                Snackbar.make(view, "Class dropped", Snackbar.LENGTH_LONG)
                                        .setAction("Undo", view1 -> {
                                            registerCourse();
                                        }).show();
                            })
                            .setNegativeButton("No", (dialogInterface, i) -> {
                                dialogInterface.dismiss();
                            }).show();
                    break;
            }

        });
        courseFab.setOnLongClickListener(view1 -> {
            if(courseListStatus == CourseListStatus.NOT_ON_LIST || courseListStatus == CourseListStatus.ON_LIST){
                registerCourse();
            }//if already registered, do nothing
            return true;
        });

        String crnStr = "CRN: " + course.getCrn();
        crnText.setText(crnStr);

        instructorText.setText(course.getInstructor());

        daysText.setText(course.getDaysLiteral(0));//0 is the main one

        timesText.setText(course.getTimes().get(0));//0 is the main one

        typeText.setText(course.getTypeLiteral(0));//0 is the main one

        String unitsStr = "Units: " + course.getUnits();
        unitsText.setText(unitsStr);

        String locationStr = "at " + course.getLocations().get(0);//0 is the main one
        locationText.setText(locationStr);

        periodsText.setText(course.getPeriodLiteral(0));//0 is the main one

        String availableSeatsStr = "Seats available: " + course.getAvailableSeats();
        availableSeatsText.setText(availableSeatsStr);

        String takenSeatsStr = "Taken: " + course.getTakenSeats();
        takenSeatsText.setText(takenSeatsStr);

        String totalSeatsStr = "Total: " + course.getTotalSeats();
        totalSeatsText.setText(totalSeatsStr);

        //additional classes section
        String additionalClassesStr = course.getAdditionalClassesCombinedText();
        if(additionalClassesStr != null){
            additionalClassesText.setText(additionalClassesStr);
        }else {
//            additionalClassesText.setLayoutParams(zeroMarginParams);
            additionalClassesText.setVisibility(View.GONE);
            //remove label too
            TextView additionalClassesLabel = findViewById(R.id.course_additional_class_label);
//            additionalClassesLabel.setLayoutParams(zeroMarginParams);
            additionalClassesLabel.setVisibility(View.GONE);
        }

        //requirement section
        List<String> additionalInfoList = course.getAdditionalRestrictions();
        if(additionalInfoList != null && !additionalInfoList.isEmpty()){
            additionalInfoText.setText(additionalInfoList.get(0));
        }else {
//            additionalInfoText.setLayoutParams(zeroMarginParams);
            additionalInfoText.setVisibility(View.GONE);

            requireLabel2 = false;
        }

        String academicRestrictionStr = course.getAcademicRestrictionCombinedText();
        if(academicRestrictionStr != null){
            academicRestrictionText.setText(academicRestrictionStr);
        }else {
//            academicRestrictionText.setLayoutParams(zeroMarginParams);
            academicRestrictionText.setVisibility(View.GONE);
            requireLabel3 = false;
        }

        String prerequisiteStr = course.getPrerequisite();
        if(prerequisiteStr != null){
            prerequisiteStr = "Prerequisites:\n" + prerequisiteStr;
            prerequisiteText.setText(prerequisiteStr);
        }else {
//            prerequisiteText.setLayoutParams(zeroMarginParams);
            prerequisiteText.setVisibility(View.GONE);
            requireLabel1 = false;
        }
        //remove requirement label if no requirements exist
        if(!requireLabel1 && !requireLabel2 && !requireLabel3){
            TextView requirementLabel = findViewById(R.id.course_restriction_label);
//            requirementLabel.setLayoutParams(zeroMarginParams);
            requirementLabel.setVisibility(View.GONE);
        }

        //description and other info section
        descriptionText.setText(course.getDescription());
        String otherInfoStr = course.getOtherInfoCombinedText();
        if(otherInfoStr != null){
            otherInfoText.setText(otherInfoStr);
        }else {
//            otherInfoText.setLayoutParams(zeroMarginParams);
            otherInfoText.setVisibility(View.GONE);
            //remove label too
            TextView otherInfoLabel = findViewById(R.id.course_other_info_label);
//            otherInfoLabel.setLayoutParams(zeroMarginParams);
            otherInfoLabel.setVisibility(View.GONE);
        }

        //toolbarLayout.setCollapsedTitleTextAppearance(R.style.CollapsedAppBar);
        //toolbarLayout.setExpandedTitleTextAppearance(R.style.ExpandedAppBar);
    }

    @SuppressLint("ApplySharedPref")//to make sure that undo doesn't delete unnecessary stuff
    void addToList(){
        selectionNeedToUpdateCatalog = !selectionNeedToUpdateCatalog;
        int startPos = urlForCourse.indexOf('?');
        String addToListStr = urlForCourse.substring(startPos);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String myList = sharedPreferences.getString(getString(R.string.my_course_selection_list), null);
        if(myList == null){
            //first time
            myList = addToListStr;
        }else {
            myList = myList + "-" + addToListStr;
        }
        sharedPreferences.edit().putString(getString(R.string.my_course_selection_list), myList).commit();

        Set<String> crnSet = sharedPreferences.getStringSet(getString(R.string.my_selection_crn_set), null);
        if(crnSet == null) crnSet = Collections.singleton(courseCrn);//first one
        else crnSet.add(courseCrn);//add to set
        Log.i(TAG, courseCrn + " added to crnSet");
        /*for (String aCrnSet : crnSet) {
            Log.i(TAG, aCrnSet + ",");
        }*/
        sharedPreferences.edit().putStringSet(getString(R.string.my_selection_crn_set), crnSet).commit();
        if(selectionNeedToUpdateCatalog){
            Intent intent = new Intent();
            intent.putExtra(COURSE_LIST_CHANGE_CRN_KEY, courseCrn);
            setResult(COURSE_CHANGE_TO_LIST_CODE, intent);
        }
    }

    void undoAddToList(){
        selectionNeedToUpdateCatalog = !selectionNeedToUpdateCatalog;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String myList = sharedPreferences.getString(getString(R.string.my_course_selection_list), null);
        if(myList == null) return;
        int startPos = myList.lastIndexOf('-');
        if(startPos == -1){
            myList = null;
        }else {
            myList = myList.substring(0, Math.max(myList.length(), startPos));
        }
        sharedPreferences.edit().putString(getString(R.string.my_course_selection_list), myList).apply();

        Set<String> crnSet = sharedPreferences.getStringSet(getString(R.string.my_selection_crn_set), null);
        if(crnSet == null) return;//not added yet, error
        else crnSet.remove(courseCrn);//remove from set
        sharedPreferences.edit().putStringSet(getString(R.string.my_selection_crn_set), crnSet).apply();
        if(selectionNeedToUpdateCatalog){
            Intent intent = new Intent();
            intent.putExtra(COURSE_LIST_CHANGE_CRN_KEY, courseCrn);
            setResult(COURSE_CHANGE_TO_LIST_CODE, intent);
        }else {
            setResult(RESULT_CANCELED);
        }
    }

    void removeFromList(){
        selectionNeedToUpdateCatalog = !selectionNeedToUpdateCatalog;
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String myList = sharedPreferences.getString(getString(R.string.my_course_selection_list), null);
        if(myList == null) return;
        StringBuilder myListBuilder = new StringBuilder(myList);
        int endPos = myListBuilder.indexOf(courseCrn);
        if(endPos == -1) return;//not on list, shouldn't happen
        int startPos = myListBuilder.lastIndexOf("-", endPos);
        endPos += courseCrn.length();
        if(startPos == -1){
            myList = null;
        }else {
            myListBuilder.delete(startPos, endPos);
            myList = myListBuilder.toString();
        }
        sharedPreferences.edit().putString(getString(R.string.my_course_selection_list), myList).apply();

        Set<String> crnSet = sharedPreferences.getStringSet(getString(R.string.my_selection_crn_set), null);
        if(crnSet == null) return;//nothing added yet, error
        else crnSet.remove(courseCrn);//remove from set
        sharedPreferences.edit().putStringSet(getString(R.string.my_selection_crn_set), crnSet).apply();
        if(selectionNeedToUpdateCatalog){
            Intent intent = new Intent();
            intent.putExtra(COURSE_LIST_CHANGE_CRN_KEY, courseCrn);
            setResult(COURSE_CHANGE_TO_LIST_CODE, intent);
        }else {
            setResult(RESULT_CANCELED);
        }
    }

    void addRegistrationStatus(){
        registerStatusText.setVisibility(View.VISIBLE);
        registerStatusText.setText("Registered Just now");
        Intent intent = new Intent();
        intent.putExtra(COURSE_LIST_CHANGE_CRN_KEY, courseCrn);
        intent.putExtra(COURSE_REGISTER_STATUS_CHANGED, true);
        setResult(COURSE_CHANGE_TO_LIST_CODE, intent);
    }

    void removeRegistrationStatus(){
        registerStatusText.setVisibility(View.GONE);
        Intent intent = new Intent();
        intent.putExtra(COURSE_LIST_CHANGE_CRN_KEY, courseCrn);
        intent.putExtra(COURSE_REGISTER_STATUS_CHANGED, true);
        setResult(COURSE_CHANGE_TO_LIST_CODE, intent);
    }

    void registerCourse(){
        new RegisterCourseTask(this).execute();

    }

    void dropCourse(){
        new DropCourseTask(this).execute();
    }

    //set CollapsingToolbarLayout height
    private void setAppBarOffset(int offsetPx){
        AppBarLayout appBarLayout = findViewById(R.id.course_app_bar);
        CoordinatorLayout coordinatorLayout = findViewById(R.id.course_coordinator_layout);
        CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();
        Objects.requireNonNull(behavior).onNestedPreScroll(coordinatorLayout, appBarLayout, null, 0, offsetPx, new int[]{0, 0}, 0);
        appBarLayout.post(() -> {
            setAppBarOffset(offsetPx);
        });
    }

    private static class RegisterCourseTask extends AsyncTask<Void, Void, List<String[]>>{

        WeakReference<CourseActivity> activityWeakReference;
        AlertDialog progressDialog;

        RegisterCourseTask(CourseActivity activity){
            activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        protected List<String[]> doInBackground(Void... voids) {
            CourseActivity activity = activityWeakReference.get();

            //progress dialog
            LayoutInflater inflater = activity.getLayoutInflater();
            View customView = inflater.inflate(R.layout.progress_dialog_layout, null);
            TextView dialogMsg = customView.findViewById(R.id.loading_msg);
            dialogMsg.setText("Registering...");
            activity.runOnUiThread(() ->
                    progressDialog = new AlertDialog.Builder(activity)
                    .setView(customView)
                    .setCancelable(false)
                    .show());

            return registerCourses(activity, activity.courseCrn);
        }

        @Override
        protected void onPostExecute(List<String[]> errors) {
            CourseActivity activity = activityWeakReference.get();
            if(progressDialog != null){
                progressDialog.dismiss();
            }
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                    .setCancelable(true)
                    .setPositiveButton("OK", (dialogInterface, i) -> progressDialog.dismiss());
            if(errors == null){
                dialogBuilder.setMessage(activity.getString(R.string.toast_register_unknown_error));
                //Toast.makeText(activity, activity.getString(R.string.toast_register_unknown_error), Toast.LENGTH_SHORT).show();
            }else if(errors.isEmpty() || errors.get(0).length == 0){
                dialogBuilder.setMessage(activity.getString(R.string.toast_register_success));
                activity.removeFromList();
                activity.addRegistrationStatus();
                //Toast.makeText(activity, activity.getString(R.string.toast_register_success), Toast.LENGTH_SHORT).show();
            }else{
                String[] errorArr = errors.get(0);
                if(errorArr[0].equals(activity.courseCrn)){
                    dialogBuilder.setMessage(activity.getString(R.string.toast_register_error) + errorArr[1]);
                    //Toast.makeText(activity, activity.getString(R.string.toast_register_error) + errorArr[1], Toast.LENGTH_LONG).show();
                }else {
                    dialogBuilder.setMessage(activity.getString(R.string.toast_register_unknown_error));
                    Toast.makeText(activity, activity.getString(R.string.toast_register_unknown_error), Toast.LENGTH_SHORT).show();
                }
            }
            activity.runOnUiThread(() -> progressDialog = dialogBuilder.show());
        }
    }

    private static class DropCourseTask extends AsyncTask<Void, Void, Boolean>{

        WeakReference<CourseActivity> activityWeakReference;
        AlertDialog progressDialog;

        DropCourseTask(CourseActivity activity){
            activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        protected Boolean doInBackground(Void... voids) {
            CourseActivity activity = activityWeakReference.get();

            //progress dialog
            LayoutInflater inflater = activity.getLayoutInflater();
            View customView = inflater.inflate(R.layout.progress_dialog_layout, null);
            TextView dialogMsg = customView.findViewById(R.id.loading_msg);
            dialogMsg.setText("Dropping...");
            activity.runOnUiThread(() ->
                    progressDialog = new AlertDialog.Builder(activity)
                            .setView(customView)
                            .setCancelable(false)
                            .show());

            return MyCoursesFragment.dropCourse(activity, Collections.singletonList(activity.courseCrn));
        }

        @Override
        protected void onPostExecute(Boolean dropSuccess) {
            CourseActivity activity = activityWeakReference.get();
            if(progressDialog != null){
                progressDialog.dismiss();
            }
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity)
                    .setCancelable(true)
                    .setPositiveButton("OK", (dialogInterface, i) -> progressDialog.dismiss());
            if(dropSuccess) {
                dialogBuilder.setMessage(R.string.toast_drop_success);
                activity.removeRegistrationStatus();
                //Toast.makeText(activity, activity.getString(R.string.toast_drop_success), Toast.LENGTH_SHORT).show();
            }else {
                dialogBuilder.setMessage(R.string.toast_drop_error);
                //Toast.makeText(activity, activity.getString(R.string.toast_drop_error), Toast.LENGTH_SHORT).show();
            }
            activity.runOnUiThread(() -> progressDialog = dialogBuilder.show());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_course, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        switch (id){
            case R.id.action_goto_detail:
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlForCourse)));
                break;
            case R.id.action_force_register:
                registerCourse();
                break;
            case R.id.action_force_drop:
                dropCourse();
                break;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }
}
