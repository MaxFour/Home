/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.home.ui.widget;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.suitebuilder.annotation.LargeTest;
import android.view.View;

import com.android.home.ItemInfo;
import com.android.home.Launcher;
import com.android.home.LauncherAppWidgetInfo;
import com.android.home.LauncherSettings.Favorites;
import com.android.home.MainThreadExecutor;
import com.android.home.R;
import com.android.home.ShortcutInfo;
import com.android.home.Utilities;
import com.android.home.Workspace.ItemOperator;
import com.android.home.shortcuts.ShortcutKey;
import com.android.home.testcomponent.AppWidgetNoConfig;
import com.android.home.testcomponent.AppWidgetWithConfig;
import com.android.home.testcomponent.RequestPinItemActivity;
import com.android.home.ui.LauncherInstrumentationTestCase;
import com.android.home.util.Condition;
import com.android.home.util.SimpleActivityMonitor;
import com.android.home.util.Wait;
import com.android.home.widget.WidgetCell;

import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Test to verify pin item request flow.
 */
@LargeTest
public class RequestPinItemTest  extends LauncherInstrumentationTestCase {

    private SimpleActivityMonitor mActivityMonitor;
    private MainThreadExecutor mMainThreadExecutor;

    private String mCallbackAction;
    private String mShortcutId;
    private int mAppWidgetId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        grantWidgetPermission();
        setDefaultLauncher();

        mActivityMonitor = new SimpleActivityMonitor();
        ((Application) getInstrumentation().getTargetContext().getApplicationContext())
                .registerActivityLifecycleCallbacks(mActivityMonitor);
        mMainThreadExecutor = new MainThreadExecutor();

        mCallbackAction = UUID.randomUUID().toString();
        mShortcutId = UUID.randomUUID().toString();
    }

    @Override
    protected void tearDown() throws Exception {
        ((Application) getInstrumentation().getTargetContext().getApplicationContext())
                .unregisterActivityLifecycleCallbacks(mActivityMonitor);
        super.tearDown();
    }

    public void testPinWidgetNoConfig() throws Throwable {
        runTest("pinWidgetNoConfig", true, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                        ((LauncherAppWidgetInfo) info).providerName.getClassName()
                                .equals(AppWidgetNoConfig.class.getName());
            }
        });
    }

    public void testPinWidgetNoConfig_customPreview() throws Throwable {
        // Command to set custom preview
        Intent command =  RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setRemoteViewColor").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", Color.RED);

        runTest("pinWidgetNoConfig", true, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                        ((LauncherAppWidgetInfo) info).providerName.getClassName()
                                .equals(AppWidgetNoConfig.class.getName());
            }
        }, command);
    }

    public void testPinWidgetWithConfig() throws Throwable {
        runTest("pinWidgetWithConfig", true, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof LauncherAppWidgetInfo &&
                        ((LauncherAppWidgetInfo) info).appWidgetId == mAppWidgetId &&
                        ((LauncherAppWidgetInfo) info).providerName.getClassName()
                                .equals(AppWidgetWithConfig.class.getName());
            }
        });
    }

    public void testPinShortcut() throws Throwable {
        // Command to set the shortcut id
        Intent command = RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setShortcutId").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", mShortcutId);

        runTest("pinShortcut", false, new ItemOperator() {
            @Override
            public boolean evaluate(ItemInfo info, View view) {
                return info instanceof ShortcutInfo &&
                        info.itemType == Favorites.ITEM_TYPE_DEEP_SHORTCUT &&
                        ShortcutKey.fromItemInfo(info).getId().equals(mShortcutId);
            }
        }, command);
    }

    private void runTest(String activityMethod, boolean isWidget, ItemOperator itemMatcher,
            Intent... commandIntents) throws Throwable {
        if (!Utilities.isAtLeastO()) {
            return;
        }
        lockRotation(true);

        clearHomescreen();
        startLauncher();

        // Open all apps and wait for load complete
        final UiObject2 appsContainer = openAllApps();
        assertTrue(Wait.atMost(Condition.minChildCount(appsContainer, 2), DEFAULT_UI_TIMEOUT));

        // Open Pin item activity
        BlockingBroadcastReceiver openMonitor = new BlockingBroadcastReceiver(
                RequestPinItemActivity.class.getName());
        scrollAndFind(appsContainer, By.text("Test Pin Item")).click();
        assertNotNull(openMonitor.blockingGetExtraIntent());

        // Set callback
        PendingIntent callback = PendingIntent.getBroadcast(mTargetContext, 0,
                new Intent(mCallbackAction), PendingIntent.FLAG_ONE_SHOT);
        mTargetContext.sendBroadcast(RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, "setCallback").putExtra(
                RequestPinItemActivity.EXTRA_PARAM + "0", callback));

        for (Intent command : commandIntents) {
            mTargetContext.sendBroadcast(command);
        }

        // call the requested method to start the flow
        mTargetContext.sendBroadcast(RequestPinItemActivity.getCommandIntent(
                RequestPinItemActivity.class, activityMethod));
        UiObject2 widgetCell = mDevice.wait(
                Until.findObject(By.clazz(WidgetCell.class)), DEFAULT_ACTIVITY_TIMEOUT);
        assertNotNull(widgetCell);

        // Accept confirmation:
        BlockingBroadcastReceiver resultReceiver = new BlockingBroadcastReceiver(mCallbackAction);
        mDevice.wait(Until.findObject(By.text(mTargetContext.getString(
                R.string.place_automatically).toUpperCase())), DEFAULT_UI_TIMEOUT).click();
        Intent result = resultReceiver.blockingGetIntent();
        assertNotNull(result);
        mAppWidgetId = result.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        if (isWidget) {
            assertNotSame(-1, mAppWidgetId);
        }

        // Go back to home
        mTargetContext.startActivity(getHomeIntent());
        assertTrue(Wait.atMost(new ItemSearchCondition(itemMatcher), DEFAULT_ACTIVITY_TIMEOUT));
    }

    /**
     * Condition for for an item
     */
    private class ItemSearchCondition extends Condition implements Callable<Boolean> {

        private final ItemOperator mOp;

        ItemSearchCondition(ItemOperator op) {
            mOp = op;
        }

        @Override
        public boolean isTrue() throws Throwable {
            return mMainThreadExecutor.submit(this).get();
        }

        @Override
        public Boolean call() throws Exception {
            // Find the resumed launcher
            Launcher launcher = null;
            for (Activity a : mActivityMonitor.resumed) {
                if (a instanceof Launcher) {
                    launcher = (Launcher) a;
                }
            }
            if (launcher == null) {
                return false;
            }
            return launcher.getWorkspace().getFirstMatch(mOp) != null;
        }
    }
}