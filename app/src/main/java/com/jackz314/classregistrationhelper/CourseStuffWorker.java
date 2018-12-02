package com.jackz314.classregistrationhelper;

import android.app.Notification;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import static com.jackz314.classregistrationhelper.Constants.WORKER_IS_SUMMARY_JOB;
import static com.jackz314.classregistrationhelper.Constants.WORKER_SHORTER_INTERVAL_DATA;
import static com.jackz314.classregistrationhelper.CourseUtils.addShorterIntervalRequests;
import static com.jackz314.classregistrationhelper.CourseUtils.checkAndRegisterCourses;
import static com.jackz314.classregistrationhelper.CourseUtils.createUUIDFromTimestamp;

public class CourseStuffWorker extends Worker {

    public CourseStuffWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    //todo daily update of registration status
    @NonNull
    @Override
    public Result doWork() {
        if(getInputData().getBoolean(WORKER_IS_SUMMARY_JOB, false)){
            //todo do routine summary stuff
            return Result.SUCCESS;
        }else {
            long shorterInterval = getInputData().getLong(WORKER_SHORTER_INTERVAL_DATA, -1);
            Notification resultNotification;
            try{
                resultNotification = checkAndRegisterCourses(getApplicationContext());
            }catch (NullPointerException e){
                e.printStackTrace();
                return Result.FAILURE;
            }
            if(shorterInterval != -1){
                addShorterIntervalRequests(shorterInterval);
            }
            if(resultNotification == null){
                return Result.SUCCESS;//because there's nothing to do, no courses to register (registered)
            }
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            notificationManager.notify(createUUIDFromTimestamp(), resultNotification);
            return Result.SUCCESS;
        }
    }

}
