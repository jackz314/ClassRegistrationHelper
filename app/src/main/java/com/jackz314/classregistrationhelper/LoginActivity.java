package com.jackz314.classregistrationhelper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;

import static com.jackz314.classregistrationhelper.MyCoursesFragment.LOGIN_SUCCESS_CODE;

/**
 * A login screen that offers login via UCMNetID & password.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    // UI references.
    private EditText mUsernameView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Set up the login form.
        mUsernameView = findViewById(R.id.ucm_id);

        mPasswordView = findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener((textView, id, keyEvent) -> {
            if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                attemptLogin();
                return true;
            }
            return false;
        });

        Button mEmailSignInButton = findViewById(R.id.sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form_layout);
        mProgressView = findViewById(R.id.login_progress);
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String ucmId = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid UCMNetID.
        if (TextUtils.isEmpty(ucmId)) {
            mUsernameView.setError(getString(R.string.error_field_required));
            focusView = mUsernameView;
            cancel = true;
        } else if (!isIdValid(ucmId)) {
            mUsernameView.setError(getString(R.string.error_invalid_id));
            focusView = mUsernameView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            if(ucmId.endsWith("@ucmerced.edu")){
                ucmId = ucmId.substring(0, ucmId.length() - 13);
            }
            showProgress(true);
            mAuthTask = new UserLoginTask(ucmId, password);
            mAuthTask.execute();
        }
    }

    private boolean isIdValid(String id) {
        return !id.trim().isEmpty();
    }

    /*private boolean isPasswordValid(String password) {
        return !password.trim().isEmpty();
    }*///not in use cause I have no idea what's valid and what's not.

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mUsername;
        private final String mPassword;

        UserLoginTask(String username, String password) {
            mUsername = username;
            mPassword = password;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            //attempt authentication against UCM server.
            return authorize(getApplicationContext(), mUsername, mPassword) != null;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            mAuthTask = null;
            showProgress(false);
            if (success) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                //todo hash user credentials later
                sharedPreferences.edit().putString(getString(R.string.username), mUsername).apply();
                sharedPreferences.edit().putString(getString(R.string.password), mPassword).apply();
                setResult(LOGIN_SUCCESS_CODE);
                finish();
            } else {
                mPasswordView.setError(getString(R.string.error_login_failed));
                mPasswordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }

    /**
     * Reauthorize user with castgcCookie, store the new SESSID.
     * If failed, re-login with service url. Refer to {@link #reauthorizeWithUrl(Context)}
     * Works for a while then the castgc expires, need to use service ticket url or credentials to reauthorize and get a new one
     *
     * @param context context for getting resources
     * @return String of the new SESSID (session id)
     */
    public static String reauthorizeWithCastgc(Context context) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String castgcCookie = sharedPreferences.getString(context.getString(R.string.castgc_cookie), null);
        if(castgcCookie != null){
            OkHttpClient client = new OkHttpClient();
            Request signInRequest = new Request.Builder()
                    .header("Cookie", castgcCookie)
                    .url(context.getString(R.string.sign_in_request_url))
                    .build();
            try {
                Response response = client.newCall(signInRequest).execute();
                String sessionId = extractSessionIdFromCookies(response.header("set-cookie"));
                if(sessionId == null || sessionId.isEmpty()) sessionId = reauthorizeWithUrl(context);
                sharedPreferences.edit().putString(context.getString(R.string.session_id), sessionId).apply();
                return sessionId;
            } catch (IOException e) {//internet error
                e.printStackTrace();
                return null;
            }
        }else {//no castgc Cookie present, login with credential
            String username = sharedPreferences.getString(context.getString(R.string.username), null);
            String password = sharedPreferences.getString(context.getString(R.string.password), null);
            return authorize(context, username, password);
        }
    }

    /**
     * Reauthorize user with service ticket url and store the new SESSID.
     * If failed, re-login with credentials. Refer to {@link #authorize(Context, String, String)}
     * Works probably permanently
     *
     * @param context context for getting resources
     * @return String of the new SESSID (session id)
     */
    public static String reauthorizeWithUrl(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String sessionUrl = sharedPreferences.getString(context.getString(R.string.perm_get_session_url), null);
        if(sessionUrl != null){
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(sessionUrl)
                    .build();
            try {
                Response response = client.newCall(request).execute();
                String sessionId = extractSessionIdFromCookies(response.header("set-cookie"));
                if(sessionId == null || sessionId.isEmpty()){
                    String username = sharedPreferences.getString(context.getString(R.string.username), null);
                    String password = sharedPreferences.getString(context.getString(R.string.password), null);
                    sessionId = authorize(context, username, password);
                }
                if(sessionId == null) return null;
                sharedPreferences.edit().putString(context.getString(R.string.session_id), sessionId).apply();
                return sessionId;
            } catch (IOException e) {//internet error
                e.printStackTrace();
                return null;
            }
        }else {//no get session id url present, login with credential
            String username = sharedPreferences.getString(context.getString(R.string.username), null);
            String password = sharedPreferences.getString(context.getString(R.string.password), null);
            return authorize(context, username, password);
        }
    }

    /**
     * Authorize user with login credentials and store the session id and castgc cookie in sharedpreference
     *
     * @param context context for getting resources
     * @return String of SESSID (session id)
     */
    public static String authorize(Context context, String username, String password){
        if(username == null || username.isEmpty() || password == null || password.isEmpty()) return null;//failed since no credentials are present
        OkHttpClient client = new OkHttpClient();
        Request signInRequest = new Builder()
                .url(context.getString(R.string.sign_in_request_url))
                .build();
        try {
            //first get
            Response getResponse = client.newCall(signInRequest).execute();
            String htmlGetResponse;
            if (getResponse.body() != null){
                htmlGetResponse = getResponse.body().string();
            }else {
                Log.e(TAG, "Authorize get response null");
                return null;//probably internet problems
            }

            //now post
            RequestBody signInPostRequestBody = getRequestBodyFromGetResponse(htmlGetResponse, username, password);
            if(signInPostRequestBody == null) return null;
            Document tmpDoc = Jsoup.parse(htmlGetResponse);
            String postUrl = tmpDoc.select("[action]").attr("action");
            signInRequest = signInRequest.newBuilder()
                    .url(context.getString(R.string.cas_root_url) + postUrl)
                    .post(signInPostRequestBody)
                    .build();
            Log.i(TAG, signInRequest.toString());
            Response postResponse = client
                    .newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                    .newCall(signInRequest).execute();
            if(postResponse.isRedirect()){
                String nextStopUrl = postResponse.header("location");//next stop of redirection
                if(nextStopUrl == null) {
                    //shouldn't happen (have redirect response without new location)
                    Log.e(TAG, "Authorize post response redirect address not found");
                    return null;
                }
                /*if (context.getString(R.string.sign_in_request_url).contains(nextStopUrl)){
                    //redirected back to the same url, should never happen
                    return null;//failed, not valid lt or execution, problem with post, not necessarily password
                }*///not appearing in practice
                //get CASTGC cookie and follow redirect to get SessionID
                String cookies = postResponse.header("Set-Cookie");
                String castgcCookie = null;
                if((cookies != null && cookies.contains("JSESSIONID=")) && nextStopUrl.contains("jsessionid=")){
                    Log.i(TAG, "got jsession id, initiating second request");
                    //if has jsession id as part of response, get login response again with jsession id attached.
                    OkHttpClient jsessionClient = new OkHttpClient();
                    Request jsessionRequest = new Builder()
                            .header("cookie", nextStopUrl.substring(nextStopUrl.indexOf("jsessionid=") + 11, nextStopUrl.indexOf('?',
                                    nextStopUrl.indexOf("jsessionid=") + 11)))
                            .url(context.getString(R.string.cas_root_url) + nextStopUrl)
                            .build();
                    postResponse = jsessionClient.newCall(jsessionRequest).execute();
                    String htmlJsessionResponse;
                    if (postResponse.body() != null){
                        htmlJsessionResponse = postResponse.body().string();
                    }else {
                        Log.e(TAG, "Authorize get response null");
                        return null;//probably internet problems
                    }
                    //post again
                    RequestBody jsessionRequestBody = getRequestBodyFromGetResponse(htmlJsessionResponse, username, password);
                    if(jsessionRequestBody == null) return null;
                    jsessionRequest = jsessionRequest.newBuilder()
                            .post(jsessionRequestBody)
                            .build();
                    Log.i(TAG, jsessionRequest.toString());
                    postResponse = client.newCall(signInRequest).execute();
                    if(postResponse.isRedirect()){
                        cookies = postResponse.header("Set-Cookie");
                        if(cookies != null){
                            castgcCookie = extractCastgcFromCookies(cookies);
                            nextStopUrl = postResponse.header("location");//update next stop
                            if(nextStopUrl == null) {
                                Log.e(TAG, "jsession second response redirect location invalid");
                                return null;
                            }
                        }
                    }
                }else {
                    castgcCookie = extractCastgcFromCookies(cookies);
                }

                if(castgcCookie == null) {
                    Log.e(TAG, "Castgc cookie extraction failed\n" +
                    "Cookies:\n" + cookies +
                    "\nHeaders:\n" + postResponse.headers().toString() +
                    "\nContent:\n" + (postResponse.body() != null ? postResponse.body().string() : null));
                    return null;
                }
                //store locally
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                sharedPreferences.edit().putString(context.getString(R.string.castgc_cookie), castgcCookie).apply();
                //follow redirect until the last one which returns the proper session id
                OkHttpClient redirectClient = new OkHttpClient();
                Builder redirectBuilder = new Builder();
                Response redirectResponse;
                do {
                    redirectBuilder.url(nextStopUrl).build();
                    redirectResponse =redirectClient.newCall(redirectBuilder.build()).execute();
                    if(redirectResponse.isRedirect()){
                        nextStopUrl = redirectResponse.header("location");
                        Log.e(TAG, "Authorize post response follow up redirect address not found");
                        if(nextStopUrl == null) return null;//shouldn't happen (have redirect response without new location)
                    }else {
                        nextStopUrl = null;
                    }
                }while (nextStopUrl != null);
                //retrieve the final correct session id
                String finalCookies = redirectResponse.header("set-cookie");
                String sessionId = extractSessionIdFromCookies(finalCookies);
                if(sessionId == null) {
                    Log.e(TAG, "Failed to extract Session id from cookie");
                    return null;
                }
                //store session id
                sharedPreferences.edit().putString(context.getString(R.string.session_id), sessionId).apply();
                //store permanent get session id url (with service ticket in url) /*todo looks like permanent, not sure yet*/
                String permanentGetSessionUrl = redirectResponse.request().url().toString();
                //store user info
                if (redirectResponse.body() != null) {
                    String userInfoResponse = redirectResponse.body().string();
                    int userNameStart = userInfoResponse.indexOf("Welcome,+") + 9;
                    if(userNameStart != -1){
                        int userNameEnd = userInfoResponse.indexOf(',', userNameStart);
                        String userName = userInfoResponse.substring(userNameStart, userNameEnd).replace('+', ' ');
                        sharedPreferences.edit().putString(context.getString(R.string.user_name), userName).apply();
                        String userStudentId = permanentGetSessionUrl.substring(permanentGetSessionUrl.lastIndexOf('=') + 1);
                        sharedPreferences.edit().putString(context.getString(R.string.user_student_id), userStudentId).apply();
                    }
                }
                sharedPreferences.edit().putString(context.getString(R.string.perm_get_session_url), permanentGetSessionUrl).apply();
                return sessionId;
            }else {//got 200 ok, probably for wrong password/id combination
                Log.e(TAG, "No redirect to service ticket url found, possible wrong password/id combination\n" +
                "Headers info:\n" + postResponse.headers().toString()
                + "Content:\n" + postResponse.toString()
                + "Body:" + (postResponse.body() != null ? postResponse.body().string() : null));
                return null;
                    /*String htmlPostResponse;
                    if (postResponse.body() != null){
                        htmlPostResponse = postResponse.body().string();
                    }else return false;//probably internet problems
                    Document postDocument = Jsoup.parse(htmlPostResponse);
                    Elements elements = postDocument.select("[class=\"errors\"]");
                    if(!elements.isEmpty()){
                        //pass/id incorrect
                        return false;
                    }*///detailed methods, not needed here
            }

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static RequestBody getRequestBodyFromGetResponse(String htmlGetResponse, String username, String password){
        Document getDocument = Jsoup.parse(htmlGetResponse);
        String lt = getDocument.select("[name=\"lt\"]").val();
        String execution = getDocument.select("[name=\"execution\"]").val();
        String eventId = getDocument.select("[name=\"_eventId\"]").val();
        String submit = getDocument.select("[name=\"submit\"]").val();
        if(lt.isEmpty() || execution.isEmpty() || eventId.isEmpty() || submit.isEmpty()) {
            Log.e(TAG, "Get response doesn't have all needed value");
            return null;//broken
        }

        Log.i(TAG, "\n" + "Request body:" +
                username + "\n" + password + "\n" + lt + "\n" + execution + "\n" + eventId + "\n" + submit);

        return new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("lt", lt)
                .add("execution", execution)
                .add("_eventId", eventId)
                .add("submit", submit)
                .build();
    }

    public static String extractSessionIdFromCookies(String cookies){
        if(cookies == null || cookies.isEmpty()) return null;//should have session id, otherwise invalid
        int sessionIdStartPos = cookies.indexOf("SESSID=");
        if(sessionIdStartPos == -1) return null;//no session id

        int sessionIdEndPos = Math.max(cookies.indexOf(';', sessionIdStartPos), cookies.length());
        if(cookies.indexOf("SESSID=", sessionIdEndPos) != -1){
            //sometimes there are more than one sessid, where the first one is empty and the second one is what we wants
            sessionIdStartPos = cookies.indexOf("SESSID=", sessionIdEndPos);
            sessionIdEndPos = Math.max(cookies.indexOf(';', sessionIdStartPos), cookies.length());
        }//todo maybe switch to lastIndexOf later
        return cookies.substring(sessionIdStartPos, sessionIdEndPos);
    }

    public static String extractCastgcFromCookies(String cookies){
        if(cookies == null || cookies.isEmpty()) return null;
        int castgcStartPos = cookies.indexOf("CASTGC=");
        if(castgcStartPos == -1) return null;//no castgc
        int castgcEndPos = Math.max(cookies.indexOf(';', castgcStartPos), cookies.length());
        return cookies.substring(castgcStartPos, castgcEndPos);
    }

    @Override
    public boolean onNavigateUp() {
        return super.onNavigateUp();
    }
}

