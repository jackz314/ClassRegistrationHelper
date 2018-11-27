package com.jackz314.classregistrationhelper;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class CheckCoursesService extends Service {
    public CheckCoursesService() {
        
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
