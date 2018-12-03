package com.jackz314.classregistrationhelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AccountUtils {

    private static final String TAG = "AccountUtils";

    //Login related
    /**
     * Reauthorize user with castgcCookie, store the new SESSID.
     * If failed, re-login with service url. Refer to {@link #reauthorizeWithUrl(Context)}
     * Works for a while then the castgc expires, need to use service ticket url or credentials to reauthorize and get a new one
     *
     * @param context context for getting resources
     * @return String of the new SESSID (session id)
     */
    static String reauthorizeWithCastgc(Context context) {
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
    static String reauthorizeWithUrl(Context context){
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
    static String authorize(Context context, String username, String password){
        if(username == null || username.isEmpty() || password == null || password.isEmpty()) return null;//failed since no credentials are present
        OkHttpClient client = new OkHttpClient();
        Request signInRequest = new Request.Builder()
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
            RequestBody signInPostRequestBody = getAuthorizeRequestBodyFromGetResponse(htmlGetResponse, username, password);
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
                    Request jsessionRequest = new Request.Builder()
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
                    RequestBody jsessionRequestBody = getAuthorizeRequestBodyFromGetResponse(htmlJsessionResponse, username, password);
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
                Request.Builder redirectBuilder = new Request.Builder();
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

    static RequestBody getAuthorizeRequestBodyFromGetResponse(String htmlGetResponse, String username, String password){
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

    static String extractSessionIdFromCookies(String cookies){
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

    static String extractCastgcFromCookies(String cookies){
        if(cookies == null || cookies.isEmpty()) return null;
        int castgcStartPos = cookies.indexOf("CASTGC=");
        if(castgcStartPos == -1) return null;//no castgc
        int castgcEndPos = Math.max(cookies.indexOf(';', castgcStartPos), cookies.length());
        return cookies.substring(castgcStartPos, castgcEndPos);
    }

}
