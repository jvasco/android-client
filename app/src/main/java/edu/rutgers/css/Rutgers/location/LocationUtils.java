/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.rutgers.css.Rutgers.location;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;

import edu.rutgers.css.Rutgers.AppUtil;
import edu.rutgers.css.Rutgers2.R;

/**
 * Defines app-wide constants and utilities
 */
public final class LocationUtils {

    public static final String TAG = "LocationUtils";

    public static class ErrorDialogFragment extends DialogFragment {
        private Dialog mDialog;
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }

        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    // Name of shared preferences repository that stores persistent state
    public static final String SHARED_PREFERENCES =
            AppUtil.PACKAGE_NAME + ".SHARED_PREFERENCES";

    // Key for storing the "updates requested" flag in shared preferences
    public static final String KEY_UPDATES_REQUESTED =
            AppUtil.PACKAGE_NAME + ".KEY_UPDATES_REQUESTED";

    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    public final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    /*
     * Constants for location update parameters
     */
    // Milliseconds per second
    public static final int MILLISECONDS_PER_SECOND = 1000;

    // The update interval
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;

    // A fast interval ceiling
    public static final int FAST_CEILING_IN_SECONDS = 1;

    // Update interval in milliseconds
    public static final long UPDATE_INTERVAL_IN_MILLISECONDS =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;

    // A fast ceiling of update intervals, used when the app is visible
    public static final long FAST_INTERVAL_CEILING_IN_MILLISECONDS =
            MILLISECONDS_PER_SECOND * FAST_CEILING_IN_SECONDS;

    // Create an empty string for initializing strings
    public static final String EMPTY_STRING = new String();

    /**
     * Check if Google Play services is connected.
     * @return True if connected, false if not.
     */
    public static boolean servicesConnected(FragmentActivity activity) {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity);

        if(resultCode == ConnectionResult.SUCCESS) {
            Log.v(TAG, "Google Play services available.");
            return true;
        }
        else {
            showErrorDialog(activity, resultCode);
            Log.w(TAG, LocationServiceErrorMessages.getErrorString(activity, resultCode));
            return false;
        }
    }

    /**
     * Get the latitude and longitude from the Location object returned by
     * Location Services.
     *
     * @param currentLocation A Location object containing the current location
     * @return The latitude and longitude of the current location, or null if no
     * location is available.
     */
    public static String getLatLng(Context context, Location currentLocation) {
        // If the location is valid
        if (currentLocation != null) {

            // Return the latitude and longitude as strings
            return context.getString(
                    R.string.latitude_longitude,
                    currentLocation.getLatitude(),
                    currentLocation.getLongitude());
        } else {

            // Otherwise, return the empty string
            return EMPTY_STRING;
        }
    }

    /**
     * Show Play Services error dialog.
     * @param activity Activity to display dialog for
     * @param errorCode Error code
     */
    public static void showErrorDialog(FragmentActivity activity, int errorCode) {
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                errorCode,
                activity,
                CONNECTION_FAILURE_RESOLUTION_REQUEST
        );

        if(errorDialog != null) {
            ErrorDialogFragment errorDialogFragment = new ErrorDialogFragment();
            errorDialogFragment.setDialog(errorDialog);
            errorDialogFragment.show(
                    activity.getSupportFragmentManager(),
                    activity.getResources().getString(R.string.location_updates)
            );
        }
    }

}
