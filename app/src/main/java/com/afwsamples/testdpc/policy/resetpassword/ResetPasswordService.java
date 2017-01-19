/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.afwsamples.testdpc.policy.resetpassword;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.UserManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.afwsamples.testdpc.DeviceAdminReceiver;
import com.afwsamples.testdpc.R;
import com.afwsamples.testdpc.common.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;

import static android.content.Intent.ACTION_USER_UNLOCKED;

// STOPSHIP: remove reflection calls
@TargetApi(Build.VERSION_CODES.O)
public class ResetPasswordService extends Service {
    private static final String TAG = "ResetPasswordService";

    private static final int NOTIFICATION_TAP_TO_RESET = 1;
    private static final int NOTIFICATION_RESET_RESULT = 2;

    private static final String ACTION_RESET_PASSWORD = "com.afwsamples.testdpc.RESET_PASSWORD";
    private DevicePolicyManager mDpm;
    private NotificationManager mNm;

    public static class LockedBootCompletedReceiver extends BroadcastReceiver {
        private static final String TAG = "BootCompletedReceiver";
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: " + intent.toString());
            Intent serviceIntent = new Intent(context, ResetPasswordService.class);
            serviceIntent.setAction(intent.getAction());
            context.startService(serviceIntent);
        }
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: " + intent.toString());
            if (ACTION_USER_UNLOCKED.equals(intent.getAction())) {
                ResetPasswordService.this.dismissNotification();
                ResetPasswordService.this.unregisterReceiver(receiver);
            } else if (ACTION_RESET_PASSWORD.equals(intent.getAction())) {
                ResetPasswordService.this.doResetPassword();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        mDpm = getSystemService(DevicePolicyManager.class);
        mNm = getSystemService(NotificationManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (getSystemService(UserManager.class).isUserUnlocked()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USER_UNLOCKED);
        if (getActiveResetPasswordToken() != null) {
            filter.addAction(ACTION_RESET_PASSWORD);
            showNotification();
        }
        registerReceiver(receiver, filter);
        return START_REDELIVER_INTENT;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private byte[] getActiveResetPasswordToken() {
        byte[] token = ResetPasswordWithTokenFragment.loadPasswordResetTokenFromPreference(this);
        if (token == null) {
            return null;
        }
        if (!dpmIsResetPasswordTokenActive(DeviceAdminReceiver.getComponentName(this))) {
            Log.i(TAG, "Token exists but is not activated.");
            return null;
        }
        return token;
    }

    private void doResetPassword() {
        final String password = "1234";
        byte[] token = getActiveResetPasswordToken();
        boolean result;
        if (token != null) {
            result = dpmResetPasswordWithToken(DeviceAdminReceiver.getComponentName(this), password,
                    token, 0);
        } else {
            Log.e(TAG, "Cannot reset password without token");
            result = false;
        }
        Notification.Builder builder = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_launcher);
        if (result) {
            builder.setContentText(getString(R.string.reset_password_with_token_succeed, password));
            builder.setOngoing(true);
        } else {
            builder.setContentText(getString(R.string.reset_password_with_token_failed));
        }
        mNm.notify(NOTIFICATION_RESET_RESULT, builder.build());
    }

    private void showNotification() {
        PendingIntent intent = PendingIntent.getBroadcast(this, 0,
                new Intent(ACTION_RESET_PASSWORD), 0);
        Notification notification = new Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.reset_password_notification))
                .setContentIntent(intent)
                .setDeleteIntent(intent)
                .setSmallIcon(R.drawable.ic_launcher)
                .build();
        mNm.notify(NOTIFICATION_TAP_TO_RESET, notification);
    }

    private void dismissNotification() {
        mNm.cancel(NOTIFICATION_TAP_TO_RESET);
        mNm.cancel(NOTIFICATION_RESET_RESULT);
    }

    private boolean dpmIsResetPasswordTokenActive(ComponentName admin) {
        return (Boolean) ReflectionUtil.invoke(mDpm, "isResetPasswordTokenActive", admin);
    }

    private boolean dpmResetPasswordWithToken(ComponentName admin, String password, byte[] token,
                                              int flags) {
        return (Boolean) ReflectionUtil.invoke(mDpm, "resetPasswordWithToken",
                new Class<?>[] {ComponentName.class, String.class, byte[].class, int.class},
                admin, password, token, flags);
    }
}
