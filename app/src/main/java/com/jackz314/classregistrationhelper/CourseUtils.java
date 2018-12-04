package com.jackz314.classregistrationhelper;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import static com.jackz314.classregistrationhelper.AccountUtils.reauthorizeWithCastgc;
import static com.jackz314.classregistrationhelper.Constants.CHANNEL_ID;
import static com.jackz314.classregistrationhelper.Constants.COURSE_STUFF_WORKER_NAME;
import static com.jackz314.classregistrationhelper.Constants.WORKER_IS_SUMMARY_JOB;
import static com.jackz314.classregistrationhelper.Constants.WORKER_SHORTER_INTERVAL_DATA;

public class CourseUtils {

    static final String TAG = "CourseUtils";

    //course worker related

    static void addToWorkerQueue(Context context){
        if(isWorkScheduled(COURSE_STUFF_WORKER_NAME)) return;//quit if already scheduled
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        long repeatInterval = sharedPreferences.getLong(context.getString(R.string.pref_key_query_interval), 900);
        long shorterInterval = sharedPreferences.getLong(context.getString(R.string.pref_key_query_interval), -1);

        sharedPreferences.edit().putBoolean(context.getString(R.string.started_course_worker), true).apply();//no repeats

        PeriodicWorkRequest.Builder workRequestBuilder;
        if(shorterInterval != -1){
            workRequestBuilder = new PeriodicWorkRequest.Builder(CourseStuffWorker.class, 900, TimeUnit.SECONDS, (long)15, TimeUnit.SECONDS);
            Data inputData = new Data.Builder()
                    .putLong(WORKER_SHORTER_INTERVAL_DATA, shorterInterval)
                    .build();
            workRequestBuilder.setInputData(inputData);
        }else {
            workRequestBuilder = new PeriodicWorkRequest.Builder(CourseStuffWorker.class, repeatInterval, TimeUnit.SECONDS, (long)60, TimeUnit.SECONDS);
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiresBatteryNotLow(false)
                .build();
        workRequestBuilder.setConstraints(constraints);

        //just to make sure not to do it again
        WorkManager.getInstance().enqueueUniquePeriodicWork(COURSE_STUFF_WORKER_NAME, ExistingPeriodicWorkPolicy.KEEP, workRequestBuilder.build());

        //also add routine summary requests here
        if(sharedPreferences.getLong(context.getString(R.string.pref_key_summary_interval), 24) != -1){
            addRountineSummaryRequest(context);
        }
    }

    static boolean isWorkScheduled(String tag) {
        WorkManager instance = WorkManager.getInstance();
        ListenableFuture<List<WorkInfo>> statuses = instance.getWorkInfosByTag(tag);
        try {
            boolean running = false;
            List<WorkInfo> workInfoList = statuses.get();
            for (WorkInfo workInfo : workInfoList) {
                WorkInfo.State state = workInfo.getState();
                running = state == WorkInfo.State.RUNNING | state == WorkInfo.State.ENQUEUED;
            }
            return running;
        } catch (ExecutionException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    //interval is in seconds unit, not milliseconds
    static void addShorterIntervalRequests(long interval){
        int requestCount = 900/(int)interval;
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiresBatteryNotLow(false)
                .build();
        for (int i = 0; i < requestCount; i++){
            OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(CourseStuffWorker.class)
                    .setInitialDelay(interval, TimeUnit.SECONDS)
                    .setConstraints(constraints)
                    .build();
            WorkManager.getInstance().enqueue(workRequest);
            interval += interval;//increase delay
        }

    }

    static void addRountineSummaryRequest(Context context){

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        long summaryInterval = sharedPreferences.getLong(context.getString(R.string.pref_key_summary_interval), 24);

        Constraints constraints = new Constraints.Builder()
                .setRequiresCharging(false)
                .setRequiresDeviceIdle(false)
                .setRequiresBatteryNotLow(false)
                .build();

        Data inputData = new Data.Builder()
                .putBoolean(WORKER_IS_SUMMARY_JOB, true)
                .build();

        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(CourseStuffWorker.class, summaryInterval, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInputData(inputData)
                .build();

        WorkManager.getInstance().enqueue(workRequest);

    }

    //simple hack that's probably feasible for our use case on notifications
    static int createUUIDFromTimestamp(){
        return (int)System.currentTimeMillis();
    }

    /**
     * Check for available courses, and based on user preference, register the courses if available
     *
     * @param context to get resources
     * @return No courses available: null
     *         Courses are available & only notify: notification for available courses
     *         Registration error: notification for registration error
     *         Registered: notification for registration success information
     * @throws NullPointerException when technical errors happen
     */
    static Notification checkAndRegisterCourses(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String courseUrlsStr = sharedPreferences.getString(context.getString(R.string.my_course_selection_urls), null);
        if(courseUrlsStr == null) return null;
        String[] courseUrls = courseUrlsStr.split("-");//fast split for me haha
        List<String> coursesToRegisterCrns = new LinkedList<>();
        List<String> coursesToRegisterNumbers = new LinkedList<>();
        for (String url :
                courseUrls) {
            String availableSeats = checkForAvailableSeats(context.getString(R.string.get_course_url) + url);
            if (availableSeats == null) throw new NullPointerException("Available seats from webpage is null");
            if(!availableSeats.equals("0")){
                int crnStartPos = url.indexOf("crn=");
                if(crnStartPos == -1) throw new NullPointerException("Can't find CRN in url, shouldn't happen");
                crnStartPos += 4;
                coursesToRegisterCrns.add(url.substring(crnStartPos));
                coursesToRegisterNumbers.add(extractCourseNumberFromURL(url));
            }
        }
        if(coursesToRegisterCrns.isEmpty()){//no courses available to register, sad
            return null;
        }
        if(sharedPreferences.getBoolean(context.getString(R.string.pref_key_auto_reg), true)){
            List<String[]> registerResult = registerCourses(context, coursesToRegisterCrns);
            return getRegisterResultNotification(coursesToRegisterCrns, coursesToRegisterNumbers, registerResult, context);
        }else {
            return getCoursesAvailableNotification(coursesToRegisterCrns, coursesToRegisterNumbers, context);
        }
    }

    static String extractCourseNumberFromURL(String url){
        int subjCodeStartPos = url.indexOf("subjcode=");
        if(subjCodeStartPos == -1) return "";
        subjCodeStartPos += 9;
        int crsENumbStartPos = url.indexOf("crsenumb=");
        if(crsENumbStartPos == -1) return "";
        int subjCodeEndPos = url.indexOf('&', subjCodeStartPos);
        if(subjCodeEndPos == -1) return "";
        int crsENumbEndPos = url.indexOf('&', crsENumbStartPos);
        if(crsENumbEndPos == -1) return "";
        return url.substring(subjCodeStartPos, subjCodeEndPos) + "-" + url.substring(crsENumbStartPos, crsENumbEndPos);
    }

    /**
     * Get all courses on my list
     *
     * @param context to get resources
     * @return A list of two lists. First list: all crns, second list: all course numbers. Null if no error occurred
     */
    static ArrayList<List<String>> getAllSelectionCourseList(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String courseUrlsStr = sharedPreferences.getString(context.getString(R.string.my_course_selection_urls), null);
        if(courseUrlsStr == null) return null;
        String[] courseUrls = courseUrlsStr.split("-");//fast split for me haha
        List<String> crns = new LinkedList<>();
        List<String> courseNumbers = new LinkedList<>();
        for (String url : courseUrls) {
            int crnStartPos = url.indexOf("crn=");
            if(crnStartPos == -1) return null;
            crnStartPos += 4;
            crns.add(url.substring(crnStartPos));
            courseNumbers.add(extractCourseNumberFromURL(url));
        }
        ArrayList<List<String>> list = new ArrayList<>();
        list.add(crns);
        list.add(courseNumbers);
        return list;
    }

    /**
     * Check for available seats from the course detail page
     *
     * @param url url for the course detail page
     * @return string representation of the available seats. Null if something went wrong.
     */
    @Nullable
    static String checkForAvailableSeats(@NonNull String url){

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .build();

        String htmlResponse;
        try {
            Response response;
            response = client.newCall(request).execute();
            ResponseBody responseBody = response.body();
            if(responseBody != null){
                htmlResponse = responseBody.string();
                response.close();
                responseBody.close();
            }else {
                response.close();
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        Document document = Jsoup.parse(htmlResponse);

        try {
            Element seatsElement = document.select("[class=datadisplaytable]").get(1).child(1).child(1);

            String availableSeats = seatsElement.child(3).ownText();
            if(availableSeats.isEmpty()) return null;
            return availableSeats;
        }catch (NullPointerException e){
            e.printStackTrace();
            return null;
        }
    }

    static Notification getRegisterSuccessNotification(List<String> courseCrns, List<String> courseNumbers
            , Context context){

        String[] resultTexts = getRegisterResultTexts(courseCrns, courseNumbers, null);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentTitle(resultTexts[0])
                .setContentText(resultTexts[1])
                .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(resultTexts[2]))
                .build();
    }

    static Notification getCoursesAvailableNotification(List<String> courseCrns, List<String> courseNumbers
            , Context context){
        StringBuilder textBuilder = new StringBuilder("There are ");
        textBuilder.append(Integer.toString(courseCrns.size())).append(" courses available to register");

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX);

        notificationBuilder.setContentTitle(textBuilder.toString());

        textBuilder = new StringBuilder();
        StringBuilder bigTextBuilder = new StringBuilder();
        for (int i = 0; i < courseCrns.size(); i++) {
            String crn = courseCrns.get(i);
            String courseNumber = courseNumbers.get(i);
            textBuilder.append(courseNumber)
                    .append(" (").append(crn).append(')');
            if(courseCrns.size() == 2){
                if(i == 1){
                    textBuilder.append(" and ");
                }
            }else if(courseCrns.size() > 2 && i < courseCrns.size() - 1){
                if(i == courseCrns.size() - 2){
                    textBuilder.append(", and ");
                }else {
                    textBuilder.append(", ");
                }
            }

            bigTextBuilder.append(courseNumber)
                    .append(" (").append(crn).append(")\n");
        }
        notificationBuilder.setContentText(textBuilder.toString());

        bigTextBuilder.deleteCharAt(bigTextBuilder.length() - 1);//remove the last line break
        notificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                .bigText(bigTextBuilder.toString()));

        return notificationBuilder.build();
    }

    static Notification getRegisterErrorNotification(List<String> courseNumbers, List<String[]> registerErrors, Context context){

        String[] resultTexts = getRegisterResultTexts(null, courseNumbers, registerErrors);

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentTitle(resultTexts[0])
                .setContentText(resultTexts[1])
                .setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(resultTexts[2]))
                .build();
    }

    static Notification getRegisterResultNotification(List<String> allCourseCrns, List<String> allCourseNumbers, List<String[]> registerErrors, Context context){
        if(allCourseCrns.size() == registerErrors.size()){
            //all registers failed
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            if(!sharedPreferences.getBoolean(context.getString(R.string.notified_all_errors), false)){
                sharedPreferences.edit().putBoolean(context.getString(R.string.notified_all_errors), true).apply();
                return getRegisterErrorNotification(allCourseNumbers, registerErrors, context);
            }else {
                //update registered courses see if it changed since last try, if changed, notify, otherwise go away and don't make a sound
                processAndStoreRegisteredCourses(getMyCoursesHtml(context), context);
                if(!sharedPreferences.getBoolean(context.getString(R.string.notified_all_errors), false)){
                    sharedPreferences.edit().putBoolean(context.getString(R.string.notified_all_errors), true).apply();
                    return getRegisterErrorNotification(allCourseNumbers, registerErrors, context);
                }
                return null;//already notified of the errors before, don't do it again until user changes something about the courses
            }
        }else if(registerErrors.isEmpty()){
            //all registers succeed
            return getRegisterSuccessNotification(allCourseCrns, allCourseNumbers, context);
        }else {
            String[] resultTexts = getRegisterResultTexts(allCourseCrns, allCourseNumbers, registerErrors);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID);
            notificationBuilder.setAutoCancel(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setContentTitle(resultTexts[0]);

            notificationBuilder.setContentText(resultTexts[1]);
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(resultTexts[2]));

            return notificationBuilder.build();
        }
    }

    static String[] getRegisterResultTexts(List<String> allCourseCrns, List<String> allCourseNumbers, List<String[]> registerErrors){
        if(registerErrors == null || registerErrors.isEmpty()){
            //all registers succeed
            StringBuilder textBuilder = new StringBuilder();
            StringBuilder bigTextBuilder = new StringBuilder();

            for (int i = 0; i < allCourseCrns.size(); i++) {
                String crn = allCourseCrns.get(i);
                String courseNumber = allCourseNumbers.get(i);
                textBuilder.append(courseNumber)
                        .append(" (").append(crn).append(')');
                if(allCourseCrns.size() == 2){
                    if(i == 1){
                        textBuilder.append(" and ");
                    }
                }else if(allCourseCrns.size() > 2 && i < allCourseCrns.size() - 1){
                    if(i == allCourseCrns.size() - 2){
                        textBuilder.append(", and ");
                    }else {
                        textBuilder.append(", ");
                    }
                }

                bigTextBuilder.append(courseNumber)
                        .append(" (").append(crn).append(")\n");
            }
            bigTextBuilder.deleteCharAt(bigTextBuilder.length() - 1);//remove last line break

            return new String[]{"Successfully registered " + Integer.toString(allCourseCrns.size()) + " courses"
                    , textBuilder.toString(), bigTextBuilder.toString()};
        }else if(allCourseCrns == null || allCourseCrns.size() == registerErrors.size()){
            //all registers failed
            StringBuilder textBuilder = new StringBuilder();
            StringBuilder bigTextBuilder = new StringBuilder();
            for (int i = 0; i < registerErrors.size(); i++) {
                String crn = registerErrors.get(i)[0];
                String failedReason = registerErrors.get(i)[1];
                String courseNumber = allCourseNumbers.get(i);
                textBuilder.append(courseNumber)
                        .append(" (").append(crn).append(')')
                        .append(": ").append(failedReason);
                if(registerErrors.size() == 2){
                    if(i == 1){
                        textBuilder.append(" and ");
                    }
                }else if(registerErrors.size() > 2 && i < registerErrors.size() - 1){
                    if(i == registerErrors.size() - 2){
                        textBuilder.append(", and ");
                    }else {
                        textBuilder.append(", ");
                    }
                }

                bigTextBuilder.append(courseNumber)
                        .append(" (").append(crn).append(')')
                        .append(": ").append(failedReason)
                        .append('\n');
            }
            bigTextBuilder.deleteCharAt(bigTextBuilder.length() - 1);//remove the last line break
            return new String[]{"Failed to register " + Integer.toString(registerErrors.size()) + " courses",
                    textBuilder.toString(), bigTextBuilder.toString()};
        }else {
            List<String> successCourseCrns = allCourseCrns;
            List<String> successCourseNumbers = allCourseNumbers;
            List<String> failedCourseCrns = new LinkedList<>();
            List<String> failedCourseNumbers = new LinkedList<>();
            List<String> failedCourseReasons = new LinkedList<>();
            for(int i = 0; i < registerErrors.size(); i++){
                String errorCourseCrn = registerErrors.get(i)[0];
                String errorCourseReason = registerErrors.get(i)[1];
                int errorCourseNumberIndex = allCourseCrns.indexOf(errorCourseCrn);
                if(errorCourseNumberIndex == -1) break;//something went wrong, I'm out
                String errorCourseNumber = allCourseNumbers.get(errorCourseNumberIndex);
                failedCourseCrns.add(errorCourseCrn);
                failedCourseReasons.add(errorCourseReason);
                failedCourseNumbers.add(errorCourseNumber);
                successCourseCrns.remove(errorCourseCrn);
                successCourseNumbers.remove(errorCourseNumberIndex);
            }

            StringBuilder textBuilder = new StringBuilder();
            StringBuilder bigTextBuilder = new StringBuilder();
            for (int i = 0; i < successCourseCrns.size(); i++) {
                String successCourseCrn = successCourseCrns.get(i);
                String successCourseNumber = successCourseNumbers.get(i);
                textBuilder.append(successCourseNumber)
                        .append(" (").append(successCourseCrn)
                        .append("): success");
                if(successCourseCrns.size() == 2){
                    if(i == 1){
                        textBuilder.append(" and ");
                    }
                }else if(successCourseCrns.size() > 2 && i < successCourseCrns.size() - 1){
                    if(i == successCourseCrns.size() - 2){
                        textBuilder.append(", and ");
                    }else {
                        textBuilder.append(", ");
                    }
                }

                bigTextBuilder.append(successCourseNumber)
                        .append(" (").append(successCourseCrn).append("): success\n");
            }

            textBuilder.append(". ");

            for (int i = 0; i < failedCourseCrns.size(); i++) {
                String failedCourseCrn = failedCourseCrns.get(i);
                String failedCourseNumber = failedCourseNumbers.get(i);
                String failedCourseReason = failedCourseReasons.get(i);
                textBuilder.append(failedCourseNumber)
                        .append(" (").append(failedCourseCrn).append(')')
                        .append(": ").append(failedCourseReason);
                if(registerErrors.size() == 2){
                    if(i == 1){
                        textBuilder.append(" and ");
                    }
                }else if(registerErrors.size() > 2 && i < registerErrors.size() - 1){
                    if(i == registerErrors.size() - 2){
                        textBuilder.append(", and ");
                    }else {
                        textBuilder.append(", ");
                    }
                }

                bigTextBuilder.append(failedCourseNumber)
                        .append(" (").append(failedCourseCrn).append(')')
                        .append(": ").append(failedCourseReason)
                        .append('\n');
            }

            bigTextBuilder.deleteCharAt(bigTextBuilder.length() - 1);//remove the last line break

            String titleText = "Successfully registered " +
                    Integer.toString(successCourseCrns.size()) +
                    " courses and failed to register " +
                    Integer.toString(failedCourseCrns.size()) +
                    " courses";
            return new String[]{titleText, textBuilder.toString(), bigTextBuilder.toString()};
        }
    }

    static Notification getUnknownErrorNotification(Context context){
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setAutoCancel(true)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentTitle(context.getString(R.string.notif_unknown_err_title))
                .setContentText(context.getString(R.string.notif_unknown_err_content))
                .build();
    }

    //MyCourses Related
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

    static void storeRegisteredCrns(Set<String> registeredCrnSet, Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        Set<String> oldRegisteredCrnSet = sharedPreferences.getStringSet(context.getString(R.string.my_registered_crn_set), new HashSet<>());
        if(!registeredCrnSet.equals(oldRegisteredCrnSet)){
            //courses registered changed, notify all mistakes again
            sharedPreferences.edit().putBoolean(context.getString(R.string.notified_all_errors), false).apply();
        }

        sharedPreferences.edit().putStringSet(context.getString(R.string.my_registered_crn_set), registeredCrnSet).apply();
    }

    static List<Course> processAndStoreRegisteredCourses(String htmlResponse, Context context){
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

        storeRegisteredCrns(registeredCrnSet, context);

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
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putString(context.getString(R.string.my_course_registered_status_list), registeredTimeList.toString()).apply();

        return registeredCourses;
    }

    static List<Course> processAndStoreMyCourses(String htmlResponse, Context context){

        List<Course> registeredCourses = processAndStoreRegisteredCourses(htmlResponse, context);

        //saved course selection list part
        List<Course> savedCourses = getSavedCoursesList(context);
        if(savedCourses != null){
            registeredCourses.addAll(0, savedCourses);
        }

        return fillCourseInfo(context, registeredCourses);
    }

    //Note: returned courses aren't fully filled to be functional yet, they only contain crn
    static List<Course> getSavedCoursesList(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String savedList = sharedPreferences.getString(context.getString(R.string.my_course_selection_urls), null);
        if(savedList != null && !savedList.isEmpty()){
            List<String> savedCrns = getSavedCourseCrnsList(savedList);
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

    //long running task
    static List<Course> fillCourseInfo(Context context, List<Course> courses){
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

    static List<String> getSavedCourseCrnsList(String list){
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

    static List<String[]> registerCourses(Context context, List<String> specificCourseCrns){
        return registerCourses(context, new HashSet<>(specificCourseCrns));
    }

    static List<String[]> registerCourses(Context context, String... specificCourses){
        Set<String> registerCrnSet;
        if(specificCourses != null && specificCourses.length > 0){
            registerCrnSet = new HashSet<>(Arrays.asList(specificCourses));
        }else {
            registerCrnSet = null;
        }
        return registerCourses(context, registerCrnSet);
    }

    /**
     * Register courses with lots of variations
     *
     * @param context to get related credentials to verify with the server, and get the course list stored in sharedPreferences
     * @param specificCourses if specified, only register courses in this array, otherwise register the courses on the sharedPreferences course list
     * @return null if something went wrong, empty list if everything worked fine, otherwise return a string list of problems with the registration
     */
    @Nullable
    static List<String[]> registerCourses(Context context, Set<String> specificCourses){
        Set<String> registerCrnSet;
        if(specificCourses != null && !specificCourses.isEmpty()){
            registerCrnSet = specificCourses;
        }else {//if not specified, register all courses in the stored selection list
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
            //todo update stored lists and registered courses and other stuff
            List<String> errorCrns = new LinkedList<>();
            for(int i = 0; i < errors.size(); i++){
                errorCrns.add(errors.get(i)[0]);
            }
            for (String registerCrn : registerCrnSet) {
                if(!errorCrns.contains(registerCrn)){
                    //register succeed for this course, remove it from the list
                    removeFromMyList(registerCrn, context);
                }
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
     * @param dropCourseCrns crns of the courses to be dropped
     * @return false if something went wrong, true if course is dropped successfully
     */
    static boolean dropCourses(Context context, @NonNull List<String> dropCourseCrns){
        String myCoursesHtml = getMyCoursesHtml(context);
        if(myCoursesHtml == null) return false;
        Document document = Jsoup.parse(myCoursesHtml);
        //need to append these registered classes post requests to a register request
        Elements registeredCourseElements = document.select("[summary=\"current schedule\"] tr");
        if(registeredCourseElements.isEmpty()) return false;//something is wrong
        List<Element> registeredElements = registeredCourseElements.subList(1, registeredCourseElements.size());//remove the first title element

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
        for (Element courseElement : registeredElements) {
            Elements courseSubElements = courseElement.select("[NAME]");
            for (Element courseSubElement : courseSubElements) {
                //crn matches one of the courses on the drop list
                //add rsts_in here so it works with the crn
                if(courseSubElement.attr("NAME").equals("CRN_IN")){
                    if(dropCourseCrns.contains(courseSubElement.attr("VALUE"))){
                        Log.i(TAG, "DROP CLASS CRN: " + courseSubElement.attr("VALUE"));
                        requestBodyBuilder.add("RSTS_IN", "DW");
                    }else {
                        requestBodyBuilder.add("RSTS_IN", "");
                    }
                }
                //add all of these attributes except for rsts_in into the post request body
                if(!courseSubElement.attr("NAME").equals("RSTS_IN")){
                    //add drop attribute instead of the usual one
                    requestBodyBuilder.add(courseSubElement.attr("NAME"), courseSubElement.attr("VALUE"));
                }
            }
        }
        //then add other summary requests
        String totalRegisteredCount = String.valueOf(registeredElements.size());
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

    static void removeFromMyList(String courseCrn, Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

        Set<String> crnSet = sharedPreferences.getStringSet(context.getString(R.string.my_selection_crn_set), null);
        if(crnSet == null) return;//nothing added yet, error
        else crnSet.remove(courseCrn);//remove from set
        sharedPreferences.edit().putStringSet(context.getString(R.string.my_selection_crn_set), crnSet).apply();

        String myList = sharedPreferences.getString(context.getString(R.string.my_course_selection_urls), null);
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
        sharedPreferences.edit().putString(context.getString(R.string.my_course_selection_urls), myList).apply();
    }

    static RequestBody modifyRequestBody(RequestBody requestBody, String name, String value){
        if(requestBody == null) return null;
        String postBodyString = requestBodyToString(requestBody);
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

    static String requestBodyToString(final RequestBody request){
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

    //catalog related
    //settings
    static String[][] getValidTerms(Context context){
        OkHttpClient client = new OkHttpClient();
        String getRequestCatalogUrl = context.getString(R.string.get_request_catalog_url);
        Request request = new Request.Builder()
                .url(getRequestCatalogUrl)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
            }else {
                return new String[][]{};//empty
            }
            Document document = Jsoup.parse(htmlResponse);
            Elements elements = document.select("input[name=validterm]");
            List<String> validTermValues = new LinkedList<>();
            List<String> validTermNames = new LinkedList<>();
            //place the latest ones on top
            for (Element element: Lists.reverse(elements)) {
                validTermValues.add(element.val());
                validTermNames.add(element.parent().parent().text());
            }
            String[] values = validTermValues.toArray(new String[0]);
            String[] names = validTermNames.toArray(new String[0]);
            if(values.length == 0 || names.length == 0){
                return new String[][]{};//empty
            }
            return new String[][]{values, names};
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            Toast.makeText(context, context.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show();
            return new String[][]{};//empty
        }
    }

    /**
     * Get all valid majors from website
     *
     * @param context run time context
     * @return String[][] with rootcrt sub String[]
     * first one is a String[] with major's values like "CSE"
     * second one is a String[] with major's names like "Computer Science and Engineering"
     */
    static String[][] getValidMajors(Context context){
        OkHttpClient client = new OkHttpClient();
        String url = context.getString(R.string.get_request_catalog_url);
        Request request = new Request.Builder()
                .url(url)
                .build();
        try {
            Response response = client.newCall(request).execute();
            String htmlResponse;
            if (response.body() != null) {
                htmlResponse = response.body().string();
            }else {
                return new String[][]{};//empty
            }
            Document document = Jsoup.parse(htmlResponse);
            Elements majorList = document.select("select[name=subjcode] option");
            List<String> validMajorValues = new LinkedList<>();
            List<String> validMajorNames = new LinkedList<>();
            for (Element major : majorList) {
                validMajorValues.add(major.val());
                validMajorNames.add(major.ownText());
            }
            String[] values = validMajorValues.toArray(new String[0]);
            String[] names = validMajorNames.toArray(new String[0]);
            return new String[][]{values, names};
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            e.printStackTrace();
            Toast.makeText(context, context.getString(R.string.toast_internet_error), Toast.LENGTH_SHORT).show();
            return new String[][]{};//empty
        }
    }

    static void storeDefaultCatalogInfo(Context context, String defaultTerm, String[] validTermValues, String[] validTermNames, String[] validMajorValues, String[] validMajorNames){
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(context.getString(R.string.pref_key_term), defaultTerm);//put the latest valid term as the default term selection
        editor.putStringSet(context.getString(R.string.pref_key_major), new HashSet<>(Collections.singletonList(validMajorValues[0])));//put ALL as default
        editor.putString(context.getString(R.string.pref_key_valid_term_values), TextUtils.join(";", validTermValues));
        editor.putString(context.getString(R.string.pref_key_valid_term_names), TextUtils.join(";", validTermNames));
        editor.putString(context.getString(R.string.pref_key_valid_major_values), TextUtils.join(";", validMajorValues));
        editor.putString(context.getString(R.string.pref_key_valid_major_names), TextUtils.join(";", validMajorNames));

        //update last sync time
        editor.putLong(context.getString(R.string.last_sync_time), new Date().getTime());

        editor.commit();
    }

    static String getPreferredTerm(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String defaultTerm = sharedPreferences.getString(context.getString(R.string.pref_key_term), null);
        if(defaultTerm != null) return defaultTerm;

        String[] validTermValues = sharedPreferences.getString(context.getString(R.string.pref_key_valid_term_values), "").split(";");
        if(validTermValues[0].equals("")){
            String[][] validTerms = getValidTerms(context);
            if(validTerms.length == 0){//internet problems
                return null;
            }else {
                validTermValues = validTerms[0];
                String[] validTermNames = validTerms[1];
                //store defaults into sharedpreference if started for the first time
                //major values/names as well
                String[] validMajorValues = getValidMajors(context)[0];
                String[] validMajorNames = getValidMajors(context)[1];
                storeDefaultCatalogInfo(context, validTerms[0][0]/*store latest valid term as default*/
                        , validTermValues, validTermNames, validMajorValues, validMajorNames);
            }
        }else{
            //check for the last time updated the information
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.MONTH, -3);//set to three months ago from now
            Long lastSyncTime = sharedPreferences.getLong(context.getString(R.string.last_sync_time), -1);
            if(lastSyncTime == -1){
                //this shouldn't happen by design
                return null;
            }
            if(new Date(lastSyncTime).before(calendar.getTime())){//last sync is before three months
                //sync again
                String[] newValidTermValues = getValidTerms(context)[0];
                String[] newValidTermNames = getValidTerms(context)[1];
                String[] newValidMajorValues = getValidMajors(context)[0];
                String[] newValidMajorNames = getValidMajors(context)[1];
                storeDefaultCatalogInfo(context, newValidTermValues[0],/*latest valid term for default*/
                        newValidTermValues, newValidTermNames, newValidMajorValues, newValidMajorNames);
                validTermValues = newValidTermValues;//update old term values
            }
        }
        defaultTerm = validTermValues[0];//set the latest valid term as the default term
        return defaultTerm;
    }

    //long running
    static String getCatalogHtml(String url, String preferredTerm, String major, String onlyOpenClasses){
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
                Log.i(TAG, htmlResponse);
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
    static List<Course> getCatalogFromHtml(String catalogHtmlStr, Set<String> subjCode, Context context){
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

    @SafeVarargs//will not pass in wrong types
    static Course changeRegisterStatusForCourse(Course course, Context context, Set<String>... crnSets){
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
                    startPos = registerStatus.indexOf('^', startPos) + 1;
                    int endPos = registerStatus.indexOf('-', startPos);
                    if(endPos == -1) endPos = registerStatus.length();
                    registerStatus = registerStatus.substring(startPos, endPos);
                }
            }
            if(registerStatus != null){
                Log.i(TAG, "COURSE: " + course.getCrn() + " REGISTER STATUS: " + registerStatus);
            }
            course.setRegisterStatus(registerStatus);
        }else course.setRegisterStatus(null);//no status
        return course;
    }

    static CourseBuilder getCourseBuilderFromElement(Element courseElement, Course... originalCourse){
        CourseBuilder courseBuilder = null;
        if(originalCourse != null && originalCourse.length == 1){
            courseBuilder = originalCourse[0].newBuilder();//add original stuff to this builder
        }
        if (courseElement.children().size() == 13) {//only include whole information elements
            if(courseBuilder == null) {
                courseBuilder = new CourseBuilder();
            }
            courseBuilder
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
        }
        return courseBuilder;
    }

}
