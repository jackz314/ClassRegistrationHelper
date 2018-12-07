package com.jackz314.classregistrationhelper;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import androidx.core.app.NotificationManagerCompat;

import static com.jackz314.classregistrationhelper.Constants.ANDROID_BOOT_COMPLETE_INTENT;
import static com.jackz314.classregistrationhelper.Constants.BROADCAST_REGISTER_ACTION;
import static com.jackz314.classregistrationhelper.CourseUtils.createUUIDFromTimestamp;
import static com.jackz314.classregistrationhelper.CourseUtils.getAllSelectionCourseList;
import static com.jackz314.classregistrationhelper.CourseUtils.getScheduleRegisterResultNotification;
import static com.jackz314.classregistrationhelper.CourseUtils.getUnknownErrorNotification;
import static com.jackz314.classregistrationhelper.CourseUtils.registerCourses;
import static com.jackz314.classregistrationhelper.CourseUtils.rescheduleRegisterAlarm;

public class ScheduleRegisterReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if(ANDROID_BOOT_COMPLETE_INTENT.equals(intent.getAction())){
            rescheduleRegisterAlarm(context);
        }else {
            if(BROADCAST_REGISTER_ACTION.equals(intent.getAction())){
                new RegisterAllCourseTask(context).execute();
            }
        }
    }

    private static class RegisterAllCourseTask extends AsyncTask<Void, Void, List<String[]>> {

        WeakReference<Context> contextWeakReference;

        RegisterAllCourseTask(Context context){
            contextWeakReference = new WeakReference<>(context);
        }

        @Override
        protected List<String[]> doInBackground(Void... voids) {
            Context context = contextWeakReference.get();

            return registerCourses(context);
        }

        @Override
        protected void onPostExecute(List<String[]> errors) {
            Context context = contextWeakReference.get();
            Notification resultNotification;
            if(errors == null){
                resultNotification = getUnknownErrorNotification(context);
            }else{
                ArrayList<List<String>> courseList = getAllSelectionCourseList(context);
                if(courseList != null){
                    resultNotification = getScheduleRegisterResultNotification(courseList.get(0), courseList.get(1), errors, context);
                }else {
                    resultNotification = getUnknownErrorNotification(context);
                }
            }
            if(resultNotification != null){
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
                notificationManager.notify(createUUIDFromTimestamp(), resultNotification);
            }
        }
    }

}
