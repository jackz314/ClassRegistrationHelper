package com.jackz314.classregistrationhelper;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CourseActivity extends AppCompatActivity {

    CollapsingToolbarLayout toolbarLayout;

    private static String urlForCourse;
    private static final String TAG = "CourseActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course);
        toolbarLayout = findViewById(R.id.course_toolbar_layout);
        FloatingActionButton fab = findViewById(R.id.fab_add_to_list);
        fab.setOnClickListener(view -> {
            addToList();
            Snackbar.make(view, "Added to tracking list", Snackbar.LENGTH_LONG)
                .setAction("Undo", view1 -> undoAddToList()).show();
        });

        urlForCourse = getIntent().getBundleExtra("bundle").getString("URL");
        if(urlForCourse == null) return;
        //tempText.setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlForCourse))));
        toolbarLayout.setTitle("Loading...");
        new GetCourseDetailTask(this).execute();
        setSupportActionBar(findViewById(R.id.course_toolbar));
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private static class GetCourseDetailTask extends AsyncTask<Void, Void, Course>{

        private WeakReference<CourseActivity> activityReference;

        GetCourseDetailTask(CourseActivity context){
            activityReference = new WeakReference<>(context);
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
                //Log.i(TAG, course.getMajor() + course.getDescription() + " HHHHAAAA");
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
                        //todo prerequisite structure is kind of messy, maybe deal with it later in a more organized way, for now, just get all the text
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

    void setUiFromCourse(Course course){
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

        /*ConstraintLayout.LayoutParams zeroMarginParams = new ConstraintLayout.LayoutParams(//param setting for gone views
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        zeroMarginParams.setMargins(0, 0, 0, 0);*/
        boolean requireLabel1 = true, requireLabel2 = true, requireLabel3 = true;

        titleText.setText(course.getTitle());

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

    void addToList(){

    }

    void undoAddToList(){

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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_goto_detail) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlForCourse)));
        }

        return super.onOptionsItemSelected(item);
    }
}
