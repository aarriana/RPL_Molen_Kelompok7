/*
The MIT License (MIT)

Copyright (c) 2015-2017 HyperTrack (http://hypertrack.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.molen.rpl.map.view;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.hypertrack.lib.HyperTrack;
import com.hypertrack.lib.callbacks.HyperTrackCallback;
import com.hypertrack.lib.internal.common.util.HTTextUtils;
import com.hypertrack.lib.models.Action;
import com.hypertrack.lib.models.ErrorResponse;
import com.hypertrack.lib.models.HyperTrackError;
import com.hypertrack.lib.models.SuccessResponse;
import com.hypertrack.lib.models.User;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.branch.referral.Branch;
import io.branch.referral.BranchError;
import com.molen.rpl.map.R;
import com.molen.rpl.map.model.AcceptInviteModel;
import com.molen.rpl.map.model.AppDeepLink;
import com.molen.rpl.map.network.retrofit.CallUtils;
import com.molen.rpl.map.network.retrofit.HyperTrackLiveService;
import com.molen.rpl.map.network.retrofit.HyperTrackLiveServiceGenerator;
import com.molen.rpl.map.store.ActionManager;
import com.molen.rpl.map.store.SharedPreferenceManager;
import com.molen.rpl.map.util.AnimationUtils;
import com.molen.rpl.map.util.CrashlyticsWrapper;
import com.molen.rpl.map.util.DeepLinkUtil;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by piyush on 23/07/16.
 */
public class SplashScreen extends BaseActivity {

    private static final String TAG = SplashScreen.class.getSimpleName();

    private AppDeepLink appDeepLink;
    private ProgressBar progressBar;
    private boolean autoAccept;
    private String userID, accountID;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Initialize UI Views
        initUI();

        // Prepare deep-link object based on deeplink parameters received via intent
        handleAppDeepLink();

        // Check for location settings and request in case not available
        requestForLocationSettings();
    }

    public void initUI() {
        // Initialize UI Views
        Button enableLocation = (Button) findViewById(R.id.enable_location);
        TextView permissionText = (TextView) findViewById(R.id.permission_text);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);

        // Initialize button click listeners
        enableLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestForLocationSettings();
            }
        });

        // Ask for enabling location permissions and location services to proceed further
        if (!HyperTrack.checkLocationPermission(this) || !HyperTrack.checkLocationServices(this)) {
            progressBar.setVisibility(View.INVISIBLE);
            permissionText.setVisibility(View.VISIBLE);
            enableLocation.setVisibility(View.VISIBLE);
        }

        // Start animation for ripple view
        final ImageView locationRippleView = (ImageView) findViewById(R.id.location_ripple);
        AnimationUtils.ripple(locationRippleView);
    }

    /**
     * Method to handle deeplink parameters received via intent
     */
    private void handleAppDeepLink() {
        appDeepLink = new AppDeepLink(DeepLinkUtil.DEFAULT);

        // Extract deeplink params from the received intent
        Intent intent = getIntent();
        if (intent != null && !HTTextUtils.isEmpty(intent.getDataString())) {
            Log.d(TAG, "deeplink " + intent.getDataString());
            int flags = intent.getFlags();
            if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
                return;
            }
            appDeepLink = DeepLinkUtil.prepareAppDeepLink(this, intent.getData());
        }
    }

    private void initiateBranch() {
        Branch branch = Branch.getInstance();
        branch.initSession(new Branch.BranchReferralInitListener() {
            @Override
            public void onInitFinished(final JSONObject referringParams, BranchError error) {
                if (error == null) {
                    try {
                        if (referringParams.has(Invite.USER_ID_KEY))
                            userID = referringParams.getString(Invite.USER_ID_KEY);

                        if (referringParams.has(Invite.AUTO_ACCEPT_KEY))
                            autoAccept = referringParams.getBoolean(Invite.AUTO_ACCEPT_KEY);

                        if (referringParams.has(Invite.ACCOUNT_ID_KEY))
                            accountID = referringParams.getString(Invite.ACCOUNT_ID_KEY);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                } else {
                    Log.d(TAG, "onInitFinished: Error " + error.getMessage());
                }
                acceptInviteAndProceed();
            }
        }, this.getIntent().getData(), this);
    }

    private void acceptInviteAndProceed() {
        if (autoAccept) {
            acceptInvite(userID, accountID, new HyperTrackCallback() {
                @Override
                public void onSuccess(@NonNull SuccessResponse successResponse) {
                    proceedToNextScreen();
                }

                @Override
                public void onError(@NonNull ErrorResponse errorResponse) {
                    proceedToNextScreen();
                }
            });
        } else {
            proceedToNextScreen();
        }
    }

    private void proceedToNextScreen() {
        CrashlyticsWrapper.setCrashlyticsKeys(SplashScreen.this);

        // Check if user has signed up
        boolean isHyperTrackUserConfigured = !HTTextUtils.isEmpty(HyperTrack.getUserId());
        if (isHyperTrackUserConfigured) {
            proceedWithAppDeepLink(appDeepLink);
        } else {
            final Intent registerIntent = new Intent(SplashScreen.this, Profile.class);
            registerIntent.putExtra("class_from", SplashScreen.class.getSimpleName());
            registerIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(registerIntent);
            finish();
        }
    }

    // Method to proceed to next screen with deepLink params
    private void proceedWithAppDeepLink(final AppDeepLink appDeepLink) {
        switch (appDeepLink.mId) {
            case DeepLinkUtil.SHORTCUT:
                Intent intent = new Intent(this, Home.class);
                intent.putExtra("shortcut", true);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                TaskStackBuilder.create(this)
                        .addNextIntentWithParentStack(intent)
                        .startActivities();
                break;

            case DeepLinkUtil.TRACK:
                proceedWithTrackingDeepLink(appDeepLink);
                break;

            case DeepLinkUtil.DEFAULT:
            default:
                final ActionManager actionManager = ActionManager.getSharedManager(this);
                //Check if there is any existing task to be restored
                if (actionManager.shouldRestoreState()) {
                    TaskStackBuilder.create(this)
                            .addNextIntentWithParentStack(new Intent(this, Home.class)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                            .startActivities();
                } else {
                    TaskStackBuilder.create(this)
                            .addNextIntentWithParentStack(new Intent(this, Placeline.class)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                            .startActivities();
                }
                finish();
                break;
        }
    }

    private void proceedWithTrackingDeepLink(AppDeepLink appDeepLink) {
        // Check if collection_id is available from deeplink
        if (!HTTextUtils.isEmpty(appDeepLink.collectionId)) {
            handleTrackingDeepLinkSuccess(appDeepLink.collectionId, null, appDeepLink.taskID);
            return;
        }

        // Check if lookup_id is available from deeplink
        if (!HTTextUtils.isEmpty(appDeepLink.lookupId)) {
            handleTrackingDeepLinkSuccess(null, appDeepLink.lookupId, appDeepLink.taskID);
            return;
        }

        // Check if shortCode is empty and taskId is available
        if (HTTextUtils.isEmpty(appDeepLink.shortCode) && !HTTextUtils.isEmpty(appDeepLink.taskID)) {
            handleTrackingDeepLinkSuccess(null, null, appDeepLink.taskID);
            return;
        }

        displayLoader(true);

        // Fetch Action details (collectionId or lookupId) for given short code
        HyperTrack.getActionForShortCode(appDeepLink.shortCode, new HyperTrackCallback() {
            @Override
            public void onSuccess(@NonNull SuccessResponse response) {
                if (SplashScreen.this.isFinishing())
                    return;

                displayLoader(false);

                if (response.getResponseObject() == null) {
                    // Handle getActionForShortCode API error
                    handleTrackingDeepLinkError();
                    return;
                }

                List<Action> actions = (List<Action>) response.getResponseObject();
                if (actions != null && !actions.isEmpty()) {
                    // Handle getActionForShortCode API success
                    handleTrackingDeepLinkSuccess(actions.get(0).getCollectionId(), actions.get(0).getLookupId(), actions.get(0).getId());

                } else {
                    // Handle getActionForShortCode API error
                    handleTrackingDeepLinkError();
                }
            }

            @Override
            public void onError(@NonNull ErrorResponse errorResponse) {
                if (SplashScreen.this.isFinishing())
                    return;
                displayLoader(false);

                // Handle getActionForShortCode API error
                handleTrackingDeepLinkError();
            }
        });
    }

    private void handleTrackingDeepLinkSuccess(String collectionId, String lookupId, String actionId) {
        // Check if current collectionId is same as the one active currently
        if (!HTTextUtils.isEmpty(collectionId) &&
                collectionId.equals(ActionManager.getSharedManager(this).getHyperTrackActionCollectionId())) {
            TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(new Intent(this, Home.class)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    .startActivities();
            finish();
            return;
        }

        // Check if current lookupId is same as the one active currently
        if (!HTTextUtils.isEmpty(lookupId) &&
                lookupId.equals(ActionManager.getSharedManager(this).getHyperTrackActionLookupId())) {
            TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(new Intent(this, Home.class)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    .startActivities();
            finish();
            return;
        }

        // Proceed with deeplink
        ArrayList<String> actionIds = new ArrayList<>();
        actionIds.add(actionId);

        Intent intent = new Intent()
                .putExtra(Track.KEY_ACTION_ID_LIST, actionIds)
                .putExtra(Track.KEY_TRACK_DEEPLINK, true)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Action action = SharedPreferenceManager.getTrackingAction(this);
        // Check if current user is sharing location or not
        if (SharedPreferenceManager.getActionID(this) == null ||
                (action != null && actionId.equalsIgnoreCase(action.getId()))) {
            intent.setClass(SplashScreen.this, Home.class);
            if (!HTTextUtils.isEmpty(collectionId))
                intent.putExtra(Track.KEY_COLLECTION_ID, collectionId);
            else
                intent.putExtra(Track.KEY_LOOKUP_ID, lookupId);
        } else {
            intent.setClass(SplashScreen.this, Track.class);
        }

        // Handle Deeplink on Track Screen with Live Location Sharing disabled
        TaskStackBuilder.create(SplashScreen.this)
                .addNextIntentWithParentStack(intent)
                .startActivities();
        finish();
    }

    private void handleTrackingDeepLinkError() {
        ActionManager actionManager = ActionManager.getSharedManager(this);
        //Check if there is any existing task to be restored
        if (actionManager.shouldRestoreState()) {
            TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(new Intent(this, Home.class)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    .startActivities();
        } else {
            TaskStackBuilder.create(this)
                    .addNextIntentWithParentStack(new Intent(this, Placeline.class)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    .startActivities();
        }
        finish();
        actionManager = null;
    }

    private void acceptInvite(String userID, String accountID, final HyperTrackCallback callback) {
        HyperTrackLiveService service = HyperTrackLiveServiceGenerator.createService(HyperTrackLiveService.class, this);
        Call<User> call = service.acceptInvite(userID, new AcceptInviteModel(accountID, userID));

        CallUtils.enqueueWithRetry(call, new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, Response<User> response) {
                // Handle AcceptInvite API error
                if (response == null || !response.isSuccessful()) {
                    Toast.makeText(getApplicationContext(), HyperTrackError.Message.UNHANDLED_ERROR, Toast.LENGTH_SHORT).show();
                }

                callback.onSuccess(new SuccessResponse(null));
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                t.printStackTrace();
                Toast.makeText(getApplicationContext(), HyperTrackError.Message.UNHANDLED_ERROR, Toast.LENGTH_SHORT).show();
                callback.onError(new ErrorResponse());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (requestCode == HyperTrack.REQUEST_CODE_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Handle Location permission successfully granted response
                requestForLocationSettings();

            } else {
                // Handle Location permission request denied error
                showSnackBar();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == HyperTrack.REQUEST_CODE_LOCATION_SERVICES) {
            if (resultCode == Activity.RESULT_OK) {
                // Handle Location services successfully enabled response
                requestForLocationSettings();

            } else {
                // Handle Location services request denied error
                showSnackBar();
            }
        }
    }

    private void requestForLocationSettings() {
        // Check for Location permission
        if (!HyperTrack.checkLocationPermission(this)) {
            HyperTrack.requestPermissions(this);
            return;
        }

        // Check for Location settings
        if (!HyperTrack.checkLocationServices(this)) {
            HyperTrack.requestLocationServices(this);
            return;
        }

        // Location Permissions and Settings have been enabled
        // Proceed with your app logic here
        if ((appDeepLink.mId == DeepLinkUtil.DEFAULT || appDeepLink.mId == DeepLinkUtil.SHORTCUT )
                && SharedPreferenceManager.getHyperTrackLiveUser(this) != null)
            acceptInviteAndProceed();
        else {
            progressBar.setVisibility(View.VISIBLE);
            initiateBranch();
        }
    }

    private void showSnackBar() {
        if (!HyperTrack.checkLocationPermission(this)) {
            // Handle Location permission request denied error
            Snackbar.make(findViewById(R.id.parent_layout), R.string.location_permission_snackbar_msg,
                    Snackbar.LENGTH_INDEFINITE).setAction("Allow", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestForLocationSettings();
                }
            }).show();

        } else if (HyperTrack.checkLocationServices(this)) {
            // Handle Location services request denied error
            Snackbar.make(findViewById(R.id.parent_layout), R.string.location_services_snackbar_msg,
                    Snackbar.LENGTH_INDEFINITE).setAction("Enable", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestForLocationSettings();
                }
            }).show();
        }
    }
}