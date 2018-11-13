package com.jackz314.classregistrationhelper;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;

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
        new GetCourseDetailTask().execute();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    private static class GetCourseDetailTask extends AsyncTask<Void, Void, Course>{

        @Override
        protected Course doInBackground(Void... voids) {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(urlForCourse)
                    .build();
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
                Elements elements = document.select("<changeme>");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Course course) {
            if(course != null){

            }
            super.onPostExecute(course);
        }
    }
}
