package com.jackz314.classregistrationhelper;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.lang.ref.WeakReference;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CourseActivity extends AppCompatActivity {


    private static String urlForCourse;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show());

        urlForCourse = getIntent().getStringExtra("URL");
        if(urlForCourse == null) return;
        new GetCourseDetailTask(this).execute();
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
                Response response = client.newCall(request).execute();
                String htmlResponse;
                if (response.body() != null) {
                    htmlResponse = response.body().string();
                }else {
                    return null;//empty
                }
                CourseBuilder courseBuilder = new CourseBuilder();
                Document document = Jsoup.parse(response.toString());
                Element courseElements = document.select("[class=datadisplaytable]").first().child(0);
                String[] courseInfoFromMain = activity.getIntent().getStringArrayExtra("CourseInfo");
                courseBuilder.setCrn(courseInfoFromMain[0]/*courseElements.child(0).text().substring(courseElements.child(0).text().length() - 5)*/)
                        .setNumber(courseInfoFromMain[1]/*courseElements.child(1).child(1).ownText()*/)
                        .setTitle(courseElements.child(2).child(1).ownText())
                        .setUnits(courseElements.child(3).child(1).ownText())
                        .setFee(courseElements.child(4).child(1).ownText());
                Element seatElements = document.select("[class=datadisplaytable]").get(1).child(0).child(1);
                courseBuilder.setTotalSeats(seatElements.child(0).ownText())
                        .setTakenSeats(seatElements.child(1).ownText())
                        .setAvailableSeats(seatElements.child(2).ownText());
                Elements restrictionElements = document.select("[class=dataentrytable]");
                courseBuilder.setDescription(restrictionElements.first().child(0).child(1).text())
                        //use sublist to remove the first description string
                        .setMajorRestrictionNo(restrictionElements.get(1).child(0).children().eachText().subList(1, restrictionElements.get(1).child(0).children().eachText().size()))
                        .setLevelRestrictionYes(restrictionElements.get(2).child(0).children().eachText().subList(1, restrictionElements.get(2).child(0).children().eachText().size()))//store each level into list
                        .setLevelRestrictionNo(restrictionElements.get(3).child(0).children().eachText().subList(1, restrictionElements.get(3).children().eachText().size()))
                        //todo prerequisite structure is kind of messy, maybe deal with it later in a more organized way, for now, just get all the text
                        .setPrerequisite(restrictionElements.get(4).child(0).select("[class=dedefault]").text())
                        .setOfferedTerms(restrictionElements.get(5).text());
                return courseBuilder.buildCourse();
            } catch (IOException e) {//internet problem
                Toast.makeText(activity, activity.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            } catch (Exception e) {//parsing html or other errors
                Toast.makeText(activity, activity.getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show();
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

    }
}
