package com.jackz314.classregistrationhelper;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
                    .url(context.getString(R.string.register_cas_request_url))
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
                .url(context.getString(R.string.register_cas_request_url))
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
            String jsessionCookies = getResponse.header("Set-Cookie");
            if((jsessionCookies != null && jsessionCookies.contains("JSESSIONID="))){
                signInRequest = signInRequest.newBuilder()
                        .header("cookie", jsessionCookies)
                        .build();
            }
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
                //just in case, retry with jsessionid if it's present
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

    /**
     * Authorize user with login credentials and store the session_for, session_for url, and castgc cookie in sharedpreference
     * For profile pictures
     *
     * @param context context for getting resources
     * @return String of SESSION_FOR (session (id) for profile.php)
     */
    static String authorizeForProfile(Context context, String username, String password){
        if(username == null || username.isEmpty() || password == null || password.isEmpty()) return null;//failed since no credentials are present
        OkHttpClient client = new OkHttpClient();
        Request signInRequest = new Request.Builder()
                .url(context.getString(R.string.profile_cas_request_url))
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
            String jsessionCookies = getResponse.header("Set-Cookie");
            if((jsessionCookies != null && jsessionCookies.contains("JSESSIONID="))){//add jsession id into post cookie if it's present
                signInRequest = signInRequest.newBuilder()
                        .header("cookie", jsessionCookies)
                        .build();
            }
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
                String ticketSessionUrl = postResponse.header("location");//next stop of redirection
                if(ticketSessionUrl == null) {
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
                //just in case, retry with jsessionid if it's present
                if((cookies != null && cookies.contains("JSESSIONID=")) && ticketSessionUrl.contains("jsessionid=")){
                    Log.i(TAG, "got jsession id, initiating second request");
                    //if has jsession id as part of response, get login response again with jsession id attached.
                    OkHttpClient jsessionClient = new OkHttpClient();
                    Request jsessionRequest = new Request.Builder()
                            .header("cookie", ticketSessionUrl.substring(ticketSessionUrl.indexOf("jsessionid=") + 11, ticketSessionUrl.indexOf('?',
                                    ticketSessionUrl.indexOf("jsessionid=") + 11)))
                            .url(context.getString(R.string.cas_root_url) + ticketSessionUrl)
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
                            ticketSessionUrl = postResponse.header("location");//update next stop
                            if(ticketSessionUrl == null) {
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
                sharedPreferences.edit().putString(context.getString(R.string.profile_castgc_cookie), castgcCookie).apply();
                //follow redirect until the last one which returns the proper session id
                String sessionFor = null;
                if(ticketSessionUrl.contains("?ticket=")){
                    OkHttpClient reClient = new OkHttpClient.Builder()
                            .followRedirects(false)
                            .followSslRedirects(false)
                            .build();
                    Request reRequest = new Request.Builder()
                            .url(ticketSessionUrl)
                            .build();
                    Response reResponse = reClient.newCall(reRequest).execute();
                    if(reResponse.body() == null) return null;
                    sessionFor = extractSessionForFromCookies(reResponse.header("set-cookie"));
                    if(sessionFor == null) sessionFor = extractSessionForFromUrl(ticketSessionUrl);
                    sharedPreferences.edit().putString(context.getString(R.string.perm_get_profile_session_url), ticketSessionUrl).apply();
                    //sessionFor = extractSessionForFromUrl(ticketSessionUrl);
                    Log.i(TAG, "SESSIONFOR" + sessionFor);
                    if(sessionFor != null){
                        sharedPreferences.edit().putString(context.getString(R.string.profile_session_for_id), sessionFor).apply();
                    }
                }
                return sessionFor;
                //steps below aren't needed for the current server set up, but may be needed in the future
                /*OkHttpClient redirectClient = new OkHttpClient();
                Request.Builder redirectBuilder = new Request.Builder();
                Response redirectResponse;
                do {
                    redirectBuilder.url(ticketSessionUrl).build();
                    redirectResponse = redirectClient.newCall(redirectBuilder.build()).execute();
                    if (redirectResponse.isRedirect()) {
                        ticketSessionUrl = redirectResponse.header("location");
                        Log.e(TAG, "Authorize post response follow up redirect address not found");
                        if (ticketSessionUrl == null)
                            return null;//shouldn't happen (have redirect response without new location)
                    } else {
                        ticketSessionUrl = null;
                    }
                } while (ticketSessionUrl != null);
                //retrieve the final correct session id
                String finalCookies = redirectResponse.header("set-cookie");
                String sessionFor = extractSessionForFromCookies(finalCookies);
                if(sessionFor == null) {
                    Log.e(TAG, "Failed to extract Session id from cookie");
                    return null;
                }
                //store session id
                sharedPreferences.edit().putString(context.getString(R.string.profile_session_for), sessionFor).apply();
                //store permanent get session id url (with service ticket in url). Can confirm, is permanent
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
                sharedPreferences.edit().putString(context.getString(R.string.perm_get_session_url), permanentGetSessionUrl).apply();*/
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

    static String extractSessionForFromUrl(String url){
        if(url == null || url.isEmpty()) return null;//error
        int sessionForStartPos = url.indexOf("?ticket=");
        if(sessionForStartPos == -1) return null;//error, no ticket in url
        sessionForStartPos += 8;
        int sessionForEndPos = url.length();

        return "session_for:profile_php=" + url.substring(sessionForStartPos, sessionForEndPos).replace(".", "");
    }

    static String extractSessionForFromCookies(String cookies){
        if(cookies == null || cookies.isEmpty()) return null;//should have session id, otherwise invalid
        int sessionIdStartPos = cookies.lastIndexOf("session_for%3Aprofile_php=");
        if(sessionIdStartPos == -1) return null;//no session id

        int sessionIdEndPos = Math.max(cookies.indexOf(';', sessionIdStartPos), cookies.length());
        return cookies.substring(sessionIdStartPos, sessionIdEndPos);
    }

    static String extractCastgcFromCookies(String cookies){
        if(cookies == null || cookies.isEmpty()) return null;
        int castgcStartPos = cookies.indexOf("CASTGC=");
        if(castgcStartPos == -1) return null;//no castgc
        int castgcEndPos = Math.max(cookies.indexOf(';', castgcStartPos), cookies.length());
        return cookies.substring(castgcStartPos, castgcEndPos);
    }

    //long running, takes about rootcrt seconds, and about 3 MB of memory (probably)
    static byte[] getProfilePicByteArr(Context context){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        String profilePicStr = sharedPreferences.getString(context.getString(R.string.profile_pic_str), null);
        if(profilePicStr != null){
            return Base64.decode(profilePicStr, Base64.NO_WRAP);
        }
        String sessionForCookie = sharedPreferences.getString(context.getString(R.string.profile_session_for_id), null);
        if(sessionForCookie == null){
            String username = sharedPreferences.getString(context.getString(R.string.username), null);
            String password = sharedPreferences.getString(context.getString(R.string.password), null);
            if(username == null|| password == null) return null;//not login yet, don't set anything
            sessionForCookie = authorizeForProfile(context, username, password);
            if(sessionForCookie == null) return null;
        }
        //Object[] sslStuff = getSSLStuff(context);
        //SSLSocketFactory sslSocketFactory = (SSLSocketFactory) sslStuff[0];
        //X509TrustManager trustManager = (X509TrustManager) sslStuff[1];
        //SSLSocketFactory sslSocketFactory = getSSLStuff(context);
        //if(sslSocketFactory != null){
         //   clientBuilder.sslSocketFactory(sslSocketFactory);
        //}
        //SSLContext sslContext = getPinnedCertSslSocketFactory(context);

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(context.getString(R.string.get_profile_url))
                .header("Cookie", sessionForCookie)
                .build();
        try {
            /*Object[] sslStuff = getCustomTrustManagerSSLFactory(context);
            SSLSocketFactory sslFactory = (SSLSocketFactory) sslStuff[0];
            X509TrustManager trustManager = (X509TrustManager) sslStuff[1];
            if(sslFactory != null){
                client = new OkHttpClient.Builder().sslSocketFactory(sslFactory, trustManager).build();
            }*/
            Response response = client.newCall(request).execute();
            if (response.body() != null) {
                String htmlResponse = response.body().string();
                //we are not using jsoup to parse the html this time because the data of the image is directly in the html with almost 3 million characters (depends on original upload quality), which would take forever to parse. So instead, we just directly take it
                int picUrlStartPos = htmlResponse.lastIndexOf("<img src=\"data:image/jpeg;base64,");
                if(picUrlStartPos == -1) {
                    //error, profile pic not in response, reauthorize
                    String username = sharedPreferences.getString(context.getString(R.string.username), null);
                    String password = sharedPreferences.getString(context.getString(R.string.password), null);
                    if(username == null|| password == null) return null;//not login yet, don't set anything
                    sessionForCookie = authorizeForProfile(context, username, password);
                    if(sessionForCookie == null) return null;
                    Request retryRequest = new Request.Builder()
                            .url(context.getString(R.string.get_profile_url))
                            .header("Cookie", sessionForCookie)
                            .build();
                    Response retryResponse = client.newCall(retryRequest).execute();
                    if(retryResponse.body() == null) return null;
                    htmlResponse = retryResponse.body().string();
                    picUrlStartPos = htmlResponse.lastIndexOf("<img src=\"data:image/jpeg;base64,");
                    if(picUrlStartPos == -1) return null;
                }
                picUrlStartPos += 33;
                int picUrlEndPos = htmlResponse.indexOf('\"', picUrlStartPos);
                if(picUrlEndPos == -1) return null;
                profilePicStr = htmlResponse.substring(picUrlStartPos, picUrlEndPos);
                sharedPreferences.edit().putString(context.getString(R.string.profile_pic_str), profilePicStr).apply();
                return Base64.decode(profilePicStr, Base64.NO_WRAP);
            }else return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    static void clearUserInfoFromSharedPreference(Context context){
        SharedPreferences.Editor sfEditor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        sfEditor.putString(context.getString(R.string.username), null)
                .putString(context.getString(R.string.password), null)
                .putString(context.getString(R.string.user_name), null)
                .putString(context.getString(R.string.user_student_id), null)

                .putStringSet(context.getString(R.string.my_registered_crn_set), null)
                .putString(context.getString(R.string.my_course_registered_status_list), null)

                .putString(context.getString(R.string.session_id), null)
                .putString(context.getString(R.string.castgc_cookie), null)
                .putString(context.getString(R.string.perm_get_session_url), null)

                .putString(context.getString(R.string.perm_get_profile_session_url), null)
                .putString(context.getString(R.string.profile_castgc_cookie), null)
                .putString(context.getString(R.string.profile_session_for_id), null)
                .putString(context.getString(R.string.profile_pic_str), null)

                .putBoolean(context.getString(R.string.started_course_worker), false)
                .putBoolean(context.getString(R.string.notified_all_errors), false)

                .apply();
    }

    //the certificate chain has been completed on the server side, so there's no point of doing this all
    @Deprecated
    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                       String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    }).build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //I'm done with all these certificate stuff. It's a server problem after all, so when they fix it, I won't need all these
    //the certificate chain has been completed on the server side, so there's no point of doing this all
//    private static SSLContext getPinnedCertSslSocketFactory(Context context) {
//        try {
//            KeyStore keyStore = KeyStore.getInstance("BKS");
//            InputStream in = context.getResources().openRawResource(R.raw.chainbks);
//            keyStore.load(in, "password".toCharArray());
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
//                    TrustManagerFactory.getDefaultAlgorithm());
//            trustManagerFactory.init(keyStore);
//            sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
//            return sslContext;
//        } catch (Exception e) {
//            Log.e(TAG, e.getMessage(), e);
//        }
//        return null;
//    }
//
//    static InputStream getCertificateChain(Context context){
//        InputStream streamRoot = context.getResources().openRawResource(R.raw.root);
//        InputStream streamInter = context.getResources().openRawResource(R.raw.intermediate);
//        InputStream streamCert = context.getResources().openRawResource(R.raw.certificate);
//        //return new SequenceInputStream(new SequenceInputStream(streamRoot, streamInter), streamCert);
//
//        String rootCert = ""
//                + "-----BEGIN CERTIFICATE-----\n" +
//                "MIIF3jCCA8agAwIBAgIQAf1tMPyjylGoG7xkDjUDLTANBgkqhkiG9w0BAQwFADCB\n" +
//                "iDELMAkGA1UEBhMCVVMxEzARBgNVBAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0pl\n" +
//                "cnNleSBDaXR5MR4wHAYDVQQKExVUaGUgVVNFUlRSVVNUIE5ldHdvcmsxLjAsBgNV\n" +
//                "BAMTJVVTRVJUcnVzdCBSU0EgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTAw\n" +
//                "MjAxMDAwMDAwWhcNMzgwMTE4MjM1OTU5WjCBiDELMAkGA1UEBhMCVVMxEzARBgNV\n" +
//                "BAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0plcnNleSBDaXR5MR4wHAYDVQQKExVU\n" +
//                "aGUgVVNFUlRSVVNUIE5ldHdvcmsxLjAsBgNVBAMTJVVTRVJUcnVzdCBSU0EgQ2Vy\n" +
//                "dGlmaWNhdGlvbiBBdXRob3JpdHkwggIiMA0GCSqGSIb3DQEBAQUAA4ICDwAwggIK\n" +
//                "AoICAQCAEmUXNg7D2wiz0KxXDXbtzSfTTK1Qg2HiqiBNCS1kCdzOiZ/MPans9s/B\n" +
//                "3PHTsdZ7NygRK0faOca8Ohm0X6a9fZ2jY0K2dvKpOyuR+OJv0OwWIJAJPuLodMkY\n" +
//                "tJHUYmTbf6MG8YgYapAiPLz+E/CHFHv25B+O1ORRxhFnRghRy4YUVD+8M/5+bJz/\n" +
//                "Fp0YvVGONaanZshyZ9shZrHUm3gDwFA66Mzw3LyeTP6vBZY1H1dat//O+T23LLb2\n" +
//                "VN3I5xI6Ta5MirdcmrS3ID3KfyI0rn47aGYBROcBTkZTmzNg95S+UzeQc0PzMsNT\n" +
//                "79uq/nROacdrjGCT3sTHDN/hMq7MkztReJVni+49Vv4M0GkPGw/zJSZrM233bkf6\n" +
//                "c0Plfg6lZrEpfDKEY1WJxA3Bk1QwGROs0303p+tdOmw1XNtB1xLaqUkL39iAigmT\n" +
//                "Yo61Zs8liM2EuLE/pDkP2QKe6xJMlXzzawWpXhaDzLhn4ugTncxbgtNMs+1b/97l\n" +
//                "c6wjOy0AvzVVdAlJ2ElYGn+SNuZRkg7zJn0cTRe8yexDJtC/QV9AqURE9JnnV4ee\n" +
//                "UB9XVKg+/XRjL7FQZQnmWEIuQxpMtPAlR1n6BB6T1CZGSlCBst6+eLf8ZxXhyVeE\n" +
//                "Hg9j1uliutZfVS7qXMYoCAQlObgOK6nyTJccBz8NUvXt7y+CDwIDAQABo0IwQDAd\n" +
//                "BgNVHQ4EFgQUU3m/WqorSs9UgOHYm8Cd8rIDZsswDgYDVR0PAQH/BAQDAgEGMA8G\n" +
//                "A1UdEwEB/wQFMAMBAf8wDQYJKoZIhvcNAQEMBQADggIBAFzUfA3P9wF9QZllDHPF\n" +
//                "Up/L+M+ZBn8b2kMVn54CVVeWFPFSPCeHlCjtHzoBN6J2/FNQwISbxmtOuowhT6KO\n" +
//                "VWKR82kV2LyI48SqC/3vqOlLVSoGIG1VeCkZ7l8wXEskEVX/JJpuXior7gtNn3/3\n" +
//                "ATiUFJVDBwn7YKnuHKsSjKCaXqeYalltiz8I+8jRRa8YFWSQEg9zKC7F4iRO/Fjs\n" +
//                "8PRF/iKz6y+O0tlFYQXBl2+odnKPi4w2r78NBc5xjeambx9spnFixdjQg3IM8WcR\n" +
//                "iQycE0xyNN+81XHfqnHd4blsjDwSXWXavVcStkNr/+XeTWYRUc+ZruwXtuhxkYze\n" +
//                "Sf7dNXGiFSeUHM9h4ya7b6NnJSFd5t0dCy5oGzuCr+yDZ4XUmFF0sbmZgIn/f3gZ\n" +
//                "XHlKYC6SQK5MNyosycdiyA5d9zZbyuAlJQG03RoHnHcAP9Dc1ew91Pq7P8yF1m9/\n" +
//                "qS3fuQL39ZeatTXaw2ewh0qpKJ4jjv9cJ2vhsE/zB+4ALtRZh8tSQZXq9EfX7mRB\n" +
//                "VXyNWQKV3WKdwrnuWih0hKWbt5DHDAff9Yk2dDLWKMGwsAvgnEzDHNb842m1R0aB\n" +
//                "L6KCq9NjRHDEjf8tM7qtj3u1cIiuPhnPQCjY/MiQu12ZIvVS5ljFH4gxQ+6IHdfG\n" +
//                "jjxDah2nGN59PRbxYvnKkKj9\n" +
//                "-----END CERTIFICATE-----\n";
//        String interCert = ""
//                +"-----BEGIN CERTIFICATE-----\n" +
//                "MIIF+TCCA+GgAwIBAgIQRyDQ+oVGGn4XoWQCkYRjdDANBgkqhkiG9w0BAQwFADCB\n" +
//                "iDELMAkGA1UEBhMCVVMxEzARBgNVBAgTCk5ldyBKZXJzZXkxFDASBgNVBAcTC0pl\n" +
//                "cnNleSBDaXR5MR4wHAYDVQQKExVUaGUgVVNFUlRSVVNUIE5ldHdvcmsxLjAsBgNV\n" +
//                "BAMTJVVTRVJUcnVzdCBSU0EgQ2VydGlmaWNhdGlvbiBBdXRob3JpdHkwHhcNMTQx\n" +
//                "MDA2MDAwMDAwWhcNMjQxMDA1MjM1OTU5WjB2MQswCQYDVQQGEwJVUzELMAkGA1UE\n" +
//                "CBMCTUkxEjAQBgNVBAcTCUFubiBBcmJvcjESMBAGA1UEChMJSW50ZXJuZXQyMREw\n" +
//                "DwYDVQQLEwhJbkNvbW1vbjEfMB0GA1UEAxMWSW5Db21tb24gUlNBIFNlcnZlciBD\n" +
//                "QTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJwb8bsvf2MYFVFRVA+e\n" +
//                "xU5NEFj6MJsXKZDmMwysE1N8VJG06thum4ltuzM+j9INpun5uukNDBqeso7JcC7v\n" +
//                "HgV9lestjaKpTbOc5/MZNrun8XzmCB5hJ0R6lvSoNNviQsil2zfVtefkQnI/tBPP\n" +
//                "iwckRR6MkYNGuQmm/BijBgLsNI0yZpUn6uGX6Ns1oytW61fo8BBZ321wDGZq0GTl\n" +
//                "qKOYMa0dYtX6kuOaQ80tNfvZnjNbRX3EhigsZhLI2w8ZMA0/6fDqSl5AB8f2IHpT\n" +
//                "eIFken5FahZv9JNYyWL7KSd9oX8hzudPR9aKVuDjZvjs3YncJowZaDuNi+L7RyML\n" +
//                "fzcCAwEAAaOCAW4wggFqMB8GA1UdIwQYMBaAFFN5v1qqK0rPVIDh2JvAnfKyA2bL\n" +
//                "MB0GA1UdDgQWBBQeBaN3j2yW4luHS6a0hqxxAAznODAOBgNVHQ8BAf8EBAMCAYYw\n" +
//                "EgYDVR0TAQH/BAgwBgEB/wIBADAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUH\n" +
//                "AwIwGwYDVR0gBBQwEjAGBgRVHSAAMAgGBmeBDAECAjBQBgNVHR8ESTBHMEWgQ6BB\n" +
//                "hj9odHRwOi8vY3JsLnVzZXJ0cnVzdC5jb20vVVNFUlRydXN0UlNBQ2VydGlmaWNh\n" +
//                "dGlvbkF1dGhvcml0eS5jcmwwdgYIKwYBBQUHAQEEajBoMD8GCCsGAQUFBzAChjNo\n" +
//                "dHRwOi8vY3J0LnVzZXJ0cnVzdC5jb20vVVNFUlRydXN0UlNBQWRkVHJ1c3RDQS5j\n" +
//                "cnQwJQYIKwYBBQUHMAGGGWh0dHA6Ly9vY3NwLnVzZXJ0cnVzdC5jb20wDQYJKoZI\n" +
//                "hvcNAQEMBQADggIBAC0RBjjW29dYaK+qOGcXjeIT16MUJNkGE+vrkS/fT2ctyNMU\n" +
//                "11ZlUp5uH5gIjppIG8GLWZqjV5vbhvhZQPwZsHURKsISNrqOcooGTie3jVgU0W+0\n" +
//                "+Wj8mN2knCVANt69F2YrA394gbGAdJ5fOrQmL2pIhDY0jqco74fzYefbZ/VS29fR\n" +
//                "5jBxu4uj1P+5ZImem4Gbj1e4ZEzVBhmO55GFfBjRidj26h1oFBHZ7heDH1Bjzw72\n" +
//                "hipu47Gkyfr2NEx3KoCGMLCj3Btx7ASn5Ji8FoU+hCazwOU1VX55mKPU1I2250Lo\n" +
//                "RCASN18JyfsD5PVldJbtyrmz9gn/TKbRXTr80U2q5JhyvjhLf4lOJo/UzL5WCXED\n" +
//                "Smyj4jWG3R7Z8TED9xNNCxGBMXnMete+3PvzdhssvbORDwBZByogQ9xL2LUZFI/i\n" +
//                "eoQp0UM/L8zfP527vWjEzuDN5xwxMnhi+vCToh7J159o5ah29mP+aJnvujbXEnGa\n" +
//                "nrNxHzu+AGOePV8hwrGGG7hOIcPDQwkuYwzN/xT29iLp/cqf9ZhEtkGcQcIImH3b\n" +
//                "oJ8ifsCnSbu0GB9L06Yqh7lcyvKDTEADslIaeSEINxhO2Y1fmcYFX/Fqrrp1WnhH\n" +
//                "OjplXuXE0OPa0utaKC25Aplgom88L2Z8mEWcyfoB7zKOfD759AN7JKZWCYwk\n" +
//                "-----END CERTIFICATE-----\n";
//        String endCert = ""
//                + "-----BEGIN CERTIFICATE-----\n" +
//                "MIIFhDCCBGygAwIBAgIQRAN/A9qFGFqyNFAUSZAvLjANBgkqhkiG9w0BAQsFADB2\n" +
//                "MQswCQYDVQQGEwJVUzELMAkGA1UECBMCTUkxEjAQBgNVBAcTCUFubiBBcmJvcjES\n" +
//                "MBAGA1UEChMJSW50ZXJuZXQyMREwDwYDVQQLEwhJbkNvbW1vbjEfMB0GA1UEAxMW\n" +
//                "SW5Db21tb24gUlNBIFNlcnZlciBDQTAeFw0xNzAzMTUwMDAwMDBaFw0yMDAzMTQy\n" +
//                "MzU5NTlaMIG4MQswCQYDVQQGEwJVUzEOMAwGA1UEERMFOTQ3MjAxCzAJBgNVBAgT\n" +
//                "AkNBMREwDwYDVQQHEwhCZXJrZWxleTEjMCEGA1UECQwaMjAwIENhbGlmb3JuaWEg\n" +
//                "SGFsbCBcIzE1MDAxJzAlBgNVBAoTHlRoZSBSZWdlbnRzIG9mIHRoZSBVbml2LiBv\n" +
//                "ZiBDQTELMAkGA1UECxMCSVQxHjAcBgNVBAMTFWljYXRjYXJkLnVjbWVyY2VkLmVk\n" +
//                "dTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBANXketP6uzQMN5QPonra\n" +
//                "ggB5MzEvWRnU14q/p+kGUTs4q1H+wyDBUF2hZ6l6ASkBZv+8T4EgSlh8T9cyJys7\n" +
//                "3C4lO0C6QpQIbCnG81fZDrObWCZYxETrsCZ3v2KSok0igCbVcyiV+mOUgralbrMO\n" +
//                "MdrTmpV2aiRF3Ce/7iDSVP3n+kYsPhEK6ZZ6XExnKGZJXI3J4fpox2oubEFoifLb\n" +
//                "X63Ihi+7soBB+6EC0cvrkDz9OhCRuUa18dmd25t3oFNoyy6jXoM7cCLQArK7UhH+\n" +
//                "9q6ZuVVbVz/Yy8vtuTR3jH4h7WmHMXFR+2QPqqASfi5Sn/KMLOKRqodGeTqpMUZ6\n" +
//                "sYsCAwEAAaOCAckwggHFMB8GA1UdIwQYMBaAFB4Fo3ePbJbiW4dLprSGrHEADOc4\n" +
//                "MB0GA1UdDgQWBBQzvYpTG898isNwGx+x1AYaMumT/jAOBgNVHQ8BAf8EBAMCBaAw\n" +
//                "DAYDVR0TAQH/BAIwADAdBgNVHSUEFjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwZwYD\n" +
//                "VR0gBGAwXjBSBgwrBgEEAa4jAQQDAQEwQjBABggrBgEFBQcCARY0aHR0cHM6Ly93\n" +
//                "d3cuaW5jb21tb24ub3JnL2NlcnQvcmVwb3NpdG9yeS9jcHNfc3NsLnBkZjAIBgZn\n" +
//                "gQwBAgIwRAYDVR0fBD0wOzA5oDegNYYzaHR0cDovL2NybC5pbmNvbW1vbi1yc2Eu\n" +
//                "b3JnL0luQ29tbW9uUlNBU2VydmVyQ0EuY3JsMHUGCCsGAQUFBwEBBGkwZzA+Bggr\n" +
//                "BgEFBQcwAoYyaHR0cDovL2NydC51c2VydHJ1c3QuY29tL0luQ29tbW9uUlNBU2Vy\n" +
//                "dmVyQ0FfMi5jcnQwJQYIKwYBBQUHMAGGGWh0dHA6Ly9vY3NwLnVzZXJ0cnVzdC5j\n" +
//                "b20wIAYDVR0RBBkwF4IVaWNhdGNhcmQudWNtZXJjZWQuZWR1MA0GCSqGSIb3DQEB\n" +
//                "CwUAA4IBAQBhRAjZ3JxunOsTWS3CnzRgfECKkLc16+0NBro8QfljSyHRhSIVhV7C\n" +
//                "ZuvR9sOCrM0wxoI4c+s1fJyOWGH2PFM9bQZmy+g/JbF4BRDz9P8W6fdMACGod/jt\n" +
//                "qq5uZpE7MSs7cb/kxCcGiBmCHeONbqxqCvLMQOCwJQo3E4b6i8fWu7EZ1F+J5Ha3\n" +
//                "hAqW65onJL/HSwJuoD0CyUp7PaBQTz7A7MOyZiK03cF8tLyigUhStnrMFskjFecu\n" +
//                "eqLjeGZmHRYnvUec3hh42Xvw2/O6O3Rkm+Rpaar85G3s6cAkX1IEoRY1zrl5pGfu\n" +
//                "m1W3Yjhmdeu6p8ZqAXFCOY90cKIUkPNV\n" +
//                "-----END CERTIFICATE-----\n";
//        /*return new Buffer()
//                //.writeUtf8(endCert)
//                .writeUtf8(interCert)
//                //.writeUtf8(rootCert)
//                .inputStream();*/
//
//        InputStream chainCert = context.getResources().openRawResource(R.raw.chaincrt);
//        return chainCert;
//    }
//
//    //this is used because UC Merced's server probably didn't include the intermediate certificate chain when handling requests, so we have to manually add the certificate to make it work. Good job!
//    static SSLSocketFactory getSSLFactory(InputStream inputStream){
//        try {
//            // Load CAs from an InputStream
//// (could be from a resource or ByteArrayInputStream or ...)
//            CertificateFactory cf = CertificateFactory.getInstance("X.509");
//            Certificate ca;
//            try (InputStream caInput = new BufferedInputStream(inputStream)) {
//                ca = cf.generateCertificate(caInput);
//                System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());
//            }
//
//// Create a KeyStore containing our trusted CAs
//            String keyStoreType = KeyStore.getDefaultType();
//            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
//            keyStore.load(null, null);
//            keyStore.setCertificateEntry("ca", ca);
//
//// Create a TrustManager that trusts the CAs in our KeyStore
//            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
//            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
//            tmf.init(keyStore);
//
//// Create an SSLContext that uses our TrustManager
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(null, tmf.getTrustManagers(), null);
//            return sslContext.getSocketFactory();
//        } catch (IOException | CertificateException | NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    static SSLSocketFactory getSSLStuff(Context context){
//        X509TrustManager trustManager;
//        SSLSocketFactory sslSocketFactory;
//        try {
//            InputStream in = getCertificateChain(context);
//            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
//            Certificate ca;
//            try {
//                ca = certificateFactory.generateCertificate(in);
//            } finally { in.close(); }
//
//            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
//            keyStore.load(null, null);
//            keyStore.setCertificateEntry("ca", ca);
//
//            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//            tmf.init(keyStore);
//
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(null, tmf.getTrustManagers(), null);
//
//            sslSocketFactory = sslContext.getSocketFactory();
//            trustManager = (X509TrustManager) tmf.getTrustManagers()[0];
//            return sslSocketFactory;
//            //return new Object[]{sslSocketFactory, trustManager};
//            /*trustManager = trustManagerForCertificates(getCertificateChain(context));
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(null, new TrustManager[] { trustManager }, null);
//            sslSocketFactory = sslContext.getSocketFactory();
//            return new Object[]{sslSocketFactory, trustManager};*/
//        } catch (GeneralSecurityException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }
//
//    private static X509TrustManager trustManagerForCertificates(InputStream in)
//            throws GeneralSecurityException {
//        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
//        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(in);
//        if (certificates.isEmpty()) {
//            throw new IllegalArgumentException("expected non-empty set of trusted certificates");
//        }
//
//        // Put the certificates a key store.
//        char[] password = "password".toCharArray(); // Any password will work.
//        KeyStore keyStore = newEmptyKeyStore(password);
//        int index = 0;
//        for (Certificate certificate : certificates) {
//            String certificateAlias = Integer.toString(index++);
//            keyStore.setCertificateEntry(certificateAlias, certificate);
//        }
//
//        // Use it to build an X509 trust manager.
//        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
//                KeyManagerFactory.getDefaultAlgorithm());
//        keyManagerFactory.init(keyStore, password);
//        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
//                TrustManagerFactory.getDefaultAlgorithm());
//        trustManagerFactory.init(keyStore);
//        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
//        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
//            throw new IllegalStateException("Unexpected default trust managers:"
//                    + Arrays.toString(trustManagers));
//        }
//        return (X509TrustManager) trustManagers[0];
//    }
//
//    private static KeyStore newEmptyKeyStore(char[] password) throws GeneralSecurityException {
//        try {
//            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
//            InputStream in = null; // By convention, 'null' creates an empty key store.
//            keyStore.load(in, null);
//            return keyStore;
//        } catch (IOException e) {
//            throw new AssertionError(e);
//        }
//    }

}
