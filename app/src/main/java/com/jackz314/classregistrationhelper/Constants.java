package com.jackz314.classregistrationhelper;

public final class Constants {

    //course detail activity communication constants
    static final String COURSE_LIST_CHANGE_CRN_KEY = "course_add_to_list_crn_key";
    static final String COURSE_REGISTER_STATUS_CHANGED = "course_register_status_changed";
    static final short COURSE_DETAIL_REQUEST_CODE = 5617;//the number doesn't matter
    static final short COURSE_CHANGE_TO_LIST_CODE = 1857;//the number doesn't matter

    //notification constants
    static final String CHANNEL_ID = "CLASS_DEFAULT_CHANNEL_ID";

    //account constants
    static final short LOGIN_REQUEST_CODE = 10232;//the number doesn't matter
    static final short LOGIN_SUCCESS_CODE = 9452;//the number doesn't matter
    static final short LOGOUT_REQUEST_CODE = 23201;//the number doesn't matter
    static final short LOGOUT_SUCCESS_CODE = 2549;//the number doesn't matter

    //worker constants
    static final String WORKER_SHORTER_INTERVAL_DATA = "worker_shorter_interval_data";
    static final String WORKER_IS_SUMMARY_JOB = "worker_is_summary_job";
    static final String COURSE_STUFF_WORKER_NAME = "course_stuff_worker_name";

    //App shortcuts constants
    static final String SHORTCUT_INTENT_EXTRA_KEY = "REGISTER_ALL_COURSES";
    static final String SHORTCUT_INTENT_EXTRA_VALUE = "YES_PLEASE";
    static final String GOOGLECHROME_NAVIGATE_PREFIX = "googlechrome://navigate?url=";

    //background intent constants
    static final String ANDROID_BOOT_COMPLETE_INTENT = "android.intent.action.BOOT_COMPLETED";
    static final String BROADCAST_REGISTER_ACTION = "boradcast_register_action";
    static final int ALARM_MANAGER_INTENT_ID = 9301;//the number doesn't matter


}
