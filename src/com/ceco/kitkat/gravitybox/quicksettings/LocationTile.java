/*
 * Copyright (C) 2013 The SlimRoms Project
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.kitkat.gravitybox.quicksettings;

import java.util.HashSet;
import java.util.Set;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;

import com.ceco.kitkat.gravitybox.GravityBoxSettings;
import com.ceco.kitkat.gravitybox.ModQuickSettings;
import com.ceco.kitkat.gravitybox.R;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class LocationTile extends BasicTile {
    private static final String TAG = "GB:LocationTile";
    private static final boolean DEBUG = false;

    private boolean mLocationEnabled;
    private int mLocationMode;
    private boolean mBehaviorOverriden;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public LocationTile(Context context, Context gbContext, Object statusBar, Object panelBar) {
        super(context, gbContext, statusBar, panelBar);

        mLocationMode = getLocationMode();
        mLocationEnabled = isLocationEnabled(); 

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mBehaviorOverriden) {
                    if (mLocationEnabled) {
                        switchLocationMode(mLocationMode);
                    }
                } else {
                    setLocationEnabled(!mLocationEnabled);
                }
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (mBehaviorOverriden) {
                    setLocationEnabled(!mLocationEnabled);
                } else {
                    if (mLocationEnabled) {
                        switchLocationMode(mLocationMode);
                    }
                }
                return true;
            }
        };
    }

    @Override
    protected void onPreferenceInitialize(XSharedPreferences prefs) {
        super.onPreferenceInitialize(prefs);

        Set<String> overridenTileKeys = prefs.getStringSet(
                GravityBoxSettings.PREF_KEY_QS_TILE_BEHAVIOUR_OVERRIDE, new HashSet<String>());
        mBehaviorOverriden = overridenTileKeys.contains("location_tileview");
    }

    @Override
    protected int onGetLayoutId() {
        return R.layout.quick_settings_tile_location;
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        final String action = intent.getAction();
        if (action.equals(LocationManager.MODE_CHANGED_ACTION) || action.equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {
           locationSettingsChanged();
           if (DEBUG) log("mLocationEnabled = " + mLocationEnabled + "; mLocationMode = " + mLocationMode);
           updateResources();
        }
    }

    @Override
    protected synchronized void updateTile() {
        switch (mLocationMode) {
        case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
            mDrawableId = R.drawable.ic_qs_location_on_gps;
            break;
        case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
            mDrawableId = R.drawable.ic_qs_location_on_wifi;
            break;
        case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
            mDrawableId = R.drawable.ic_qs_location_on;
            break;
        case Settings.Secure.LOCATION_MODE_OFF:
            mDrawableId = R.drawable.ic_qs_location_off;
            break;
        }
        mLabel = mLocationEnabled ? mGbContext.getString(R.string.quick_settings_location_on) :
                         mGbContext.getString(R.string.quick_settings_location_off);
        super.updateTile();
    }

    public boolean isLocationEnabled() {
        return getLocationMode() != Settings.Secure.LOCATION_MODE_OFF;
    }

    public boolean setLocationEnabled(boolean enabled) {
        int mode = enabled ? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY : Settings.Secure.LOCATION_MODE_OFF;
        return setLocationMode(mode);
    }

    public int getLocationMode() {
        final ContentResolver resolver = mContext.getContentResolver();
        int mode = Settings.Secure.getInt(resolver, Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        return mode;
    }

    public boolean setLocationMode(int mode) {
        final ContentResolver cr = mContext.getContentResolver();
        return Settings.Secure.putInt(cr, Settings.Secure.LOCATION_MODE, mode);
    }

    public boolean switchLocationMode(int currentMode) {
        switch (currentMode) {
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                currentMode = Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
                break;
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                currentMode = Settings.Secure.LOCATION_MODE_SENSORS_ONLY;
                break;
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                currentMode = Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
                break;
        }
        return setLocationMode(currentMode);
    }

    private void locationSettingsChanged() {
        int locationMode = getLocationMode();
        boolean locationEnabled = isLocationEnabled();

        if (!ModQuickSettings.hasDisableLocationConsent) {
            Runnable collapsePanels = collapsePanels(mLocationMode, locationMode, mLocationEnabled, locationEnabled);
            Handler handler = new Handler();
            handler.post(collapsePanels);
        }
 
        mLocationMode = locationMode;
        mLocationEnabled = locationEnabled;
    }

    private Runnable collapsePanels(final int locationModeOld, final int locationModeNew, 
            final boolean locationEnabledOld, final boolean locationEnabledNew) {
        Runnable collapsePanelsRunnable = new Runnable() {
            @Override
            public void run() {
                if ((locationModeOld == Settings.Secure.LOCATION_MODE_SENSORS_ONLY 
                            && locationModeNew == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY)
                        || (!locationEnabledOld && locationEnabledNew
                            && (locationModeNew == Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                            || locationModeNew == Settings.Secure.LOCATION_MODE_BATTERY_SAVING))) {
                   Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                   mContext.sendBroadcast(closeDialog);
                }
            }
        };

        return collapsePanelsRunnable;
    }
}
