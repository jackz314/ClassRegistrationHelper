package com.jackz314.classregistrationhelper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Display;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.lang.ref.WeakReference;
import java.util.Objects;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import static com.jackz314.classregistrationhelper.AccountUtils.clearUserInfoFromSharedPreference;
import static com.jackz314.classregistrationhelper.AccountUtils.getProfilePicByteArr;
import static com.jackz314.classregistrationhelper.Constants.LOGOUT_SUCCESS_CODE;

public class AccountActivity extends AppCompatActivity {

    CollapsingToolbarLayout toolbarLayout;

    ImageView userProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account);

        setSupportActionBar(findViewById(R.id.account_toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String userName = sharedPreferences.getString(getString(R.string.user_name), null);
        String username = sharedPreferences.getString(getString(R.string.username), null);
        String userId = sharedPreferences.getString(getString(R.string.user_student_id), null);

        if(userName == null || userId == null || username == null){
            Toast.makeText(this, getString(R.string.toast_unknown_error), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        toolbarLayout = findViewById(R.id.account_toolbar_layout);
        toolbarLayout.setTitle(userName);

        String usernameStr = "UCMNetID: " + username;
        TextView usernameText = findViewById(R.id.username_text);
        usernameText.setText(usernameStr);
        usernameText.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("UCMNetID", username));
            }
            Toast.makeText(AccountActivity.this, "UCMNetID copied to clipboard!", Toast.LENGTH_SHORT).show();
            return false;
        });

        String userIdStr = "Student ID: " + userId;
        TextView userIdText = findViewById(R.id.user_id_text);
        userIdText.setText(userIdStr);
        userIdText.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Student ID", userId));
            }
            Toast.makeText(AccountActivity.this, "Student ID copied to clipboard!", Toast.LENGTH_SHORT).show();
            return false;
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("Are you sure you want to log out?")
                    .setPositiveButton("Yes", (dialog, which) -> logout()).setNegativeButton("No", (dialog, which) -> dialog.dismiss()).setCancelable(true).show();
        });

        userProfile = findViewById(R.id.user_profile_picture);
        new GetUserProfilePicTask(this).execute();
    }

    private void logout(){
        clearUserInfoFromSharedPreference(getApplicationContext());
        Toast.makeText(this, "Successfully signed out!", Toast.LENGTH_LONG).show();
        setResult(LOGOUT_SUCCESS_CODE);//recreate main
        finish();
    }

    private static class GetUserProfilePicTask extends AsyncTask<Void, Void, byte[]> {

        private WeakReference<AccountActivity> weakReference;

        GetUserProfilePicTask(AccountActivity activity){
            weakReference = new WeakReference<>(activity);
        }

        @Override
        protected byte[] doInBackground(Void... voids) {
            return getProfilePicByteArr(weakReference.get());
        }

        @Override
        protected void onPostExecute(byte[] bytes) {
            if(bytes != null){
                AccountActivity activity = weakReference.get();

                try {
                    Glide.with(activity.getApplicationContext())
                            .asBitmap()
                            .load(bytes)
                            .into(activity.userProfile);
                }catch (Exception ignored){}

                Display display = activity.getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                AppBarLayout appBarLayout = activity.findViewById(R.id.account_app_bar);
                appBarLayout.getLayoutParams().height = size.y/2;
            }
        }
    }
}
