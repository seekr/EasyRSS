/*******************************************************************************
 * Copyright (c) 2012 Pursuer (http://pursuer.me).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Pursuer - initial API and implementation
 ******************************************************************************/

package org.freshrss.easyrss;

import java.io.File;
import org.freshrss.easyrss.R;
import org.freshrss.easyrss.account.ReaderAccountMgr;
import org.freshrss.easyrss.data.DataMgr;
import org.freshrss.easyrss.data.DataUtils;
import org.freshrss.easyrss.data.Setting;
import org.freshrss.easyrss.data.readersetting.SettingSyncMethod;
import org.freshrss.easyrss.data.readersetting.SettingTheme;
import org.freshrss.easyrss.data.readersetting.SettingVolumeKeySwitching;
import org.freshrss.easyrss.network.NetworkUtils;
import org.freshrss.easyrss.network.url.AbsURL;
import org.freshrss.easyrss.view.AbsViewCtrl;
import org.freshrss.easyrss.view.HorizontalSwipeView;
import org.freshrss.easyrss.view.HorizontalSwipeViewListener;
import org.freshrss.easyrss.view.ViewCtrlListener;
import org.freshrss.easyrss.view.ViewManager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;

/**
 * Main activity class for the EasyRSS application.
 * Handles the primary UI flow, view management, and user interactions.
 * Implements ViewCtrlListener for view controller callbacks and HorizontalSwipeViewListener for swipe gestures.
 */
public class Home extends Activity implements ViewCtrlListener, HorizontalSwipeViewListener {
    /**
     * Constants representing different view types in the home screen.
     * Same order as defined in home.xml layout file.
     */
    public static final int VIEW_TYPE_STARRED = 0;
    public static final int VIEW_TYPE_ALL = 1;
    public static final int VIEW_TYPE_UNREAD = 2;

    /**
     * Bundle key for indicating whether to show settings screen on startup.
     */
    private static final String BUNDLE_KEY_SHOW_SETTINGS = "showSettings";

    /**
     * Duration in milliseconds for swipe animations.
     */
    private static final long SWIPE_ANIMATION_TIME = 400;

    /**
     * The horizontal swipe view container that handles swipe gestures.
     */
    private HorizontalSwipeView swipeView;
    
    /**
     * Manager for handling different views in the application.
     */
    private ViewManager viewMgr;
    
    /**
     * Manager for handling application data.
     */
    private DataMgr dataMgr;
    
    /**
     * Tracks the total horizontal distance of the current swipe gesture.
     */
    private int totalSwipeX;

    /**
     * Default constructor.
     * Initializes the totalSwipeX value to zero.
     */
    public Home() {
        super();
        this.totalSwipeX = 0;
    }

    /**
     * Handles the cancellation of a swipe gesture.
     * Determines whether to complete the swipe forward or backward based on the distance swiped.
     */
    @Override
    public void cancelSwipe() {
        final int screenWidth = swipeView.getWidth();
        if ((float) totalSwipeX / screenWidth > 0.20) {
            swipeForward();
        } else {
            swipeBackward();
        }
        totalSwipeX = 0;
    }

    /**
     * Intercepts key events before they are dispatched to the view hierarchy.
     * Handles volume key navigation when enabled and menu key actions.
     * 
     * @param event The key event to be processed
     * @return True if the event was handled, false otherwise
     */
    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        final int action = event.getAction();
        final int keyCode = event.getKeyCode();
        switch (keyCode) {
        case KeyEvent.KEYCODE_VOLUME_UP:
            if (viewMgr.getTopView() instanceof VerticalItemViewCtrl
                    && new SettingVolumeKeySwitching(dataMgr).getData()) {
                if (action == KeyEvent.ACTION_UP) {
                    final VerticalItemViewCtrl viewCtrl = (VerticalItemViewCtrl) viewMgr.getTopView();
                    viewCtrl.showLastItem();

                }
                return true;
            }
            break;
        case KeyEvent.KEYCODE_VOLUME_DOWN:
            if (viewMgr.getTopView() instanceof VerticalItemViewCtrl
                    && new SettingVolumeKeySwitching(dataMgr).getData()) {
                if (action == KeyEvent.ACTION_UP) {
                    final VerticalItemViewCtrl viewCtrl = (VerticalItemViewCtrl) viewMgr.getTopView();
                    viewCtrl.showNextItem();
                }
                return true;
            }
            break;
        case KeyEvent.KEYCODE_MENU:
            if (action == KeyEvent.ACTION_UP) {
                if (viewMgr.getTopView() instanceof HomeViewCtrl) {
                    final SettingsViewCtrl svc = new SettingsViewCtrl(dataMgr, this);
                    svc.setListener(this);
                    viewMgr.pushView(svc, R.anim.bottom_in, R.anim.scale_out);
                    return true;
                } else if (viewMgr.getTopView() instanceof SettingsViewCtrl) {
                    onBackPressed();
                    return true;
                }
            }
            break;
        default:
            break;
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * Called when a back navigation is needed from a view controller.
     * Delegates to onBackPressed().
     */
    @Override
    public void onBackNeeded() {
        onBackPressed();
    }

    /**
     * Handles the back button press.
     * Manages navigation between different views and handles exit from the app.
     */
    @Override
    public void onBackPressed() {
        if (viewMgr.getViewCount() > 1 && viewMgr.getLastViewResId() != R.layout.home) {
            final AbsViewCtrl topView = viewMgr.getTopView();
            if (topView instanceof SettingsViewCtrl || topView instanceof ImageViewCtrl
                    || topView instanceof WebpageItemViewCtrl) {
                viewMgr.popView(R.anim.scale_in, R.anim.bottom_out);
            } else {
                swipeForward();
            }
        } else {
            finish();
        }
        if (viewMgr.getTopView() instanceof HomeViewCtrl) {
            swipeView.setRightSwipeValid(false);
        } else {
            swipeView.setRightSwipeValid(true);
        }
    }

    /**
     * Called when the activity is first created.
     * Initializes the application, sets up the UI, and handles account authentication.
     * 
     * @param savedInstanceState The saved instance state bundle
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Utils.initManagers(this);
        dataMgr = DataMgr.getInstance();
        AbsURL.setServerUrl(dataMgr.getSettingByName(Setting.SETTING_SERVER_URL));
        final SettingTheme settingTheme = new SettingTheme(dataMgr);
        if (settingTheme.getData() == SettingTheme.THEME_NORMAL) {
            setTheme(R.style.Theme_Normal);
        } else {
            setTheme(R.style.Theme_Dark);
        }
        setContentView(R.layout.global);
        viewMgr = new ViewManager(this);
        swipeView = (HorizontalSwipeView) this.findViewById(R.id.GlobalView);
        swipeView.setListener(this);

        if (ReaderAccountMgr.getInstance().hasAccount()) {
            final HomeViewCtrl hvc = new HomeViewCtrl(dataMgr, this);
            hvc.setListener(this);
            viewMgr.pushView(hvc);
            final int sSync = new SettingSyncMethod(dataMgr).getData();
            if (sSync != SettingSyncMethod.SYNC_METHOD_MANUAL) {
                NetworkUtils.doGlobalSyncing(this, sSync);
            }
            if (getIntent().getBooleanExtra(BUNDLE_KEY_SHOW_SETTINGS, false)) {
                final SettingsViewCtrl svc = new SettingsViewCtrl(dataMgr, this);
                svc.setListener(this);
                viewMgr.pushView(svc, -1, -1);
            }
        } else {
            final LoginViewCtrl lvc = new LoginViewCtrl(dataMgr, this);
            lvc.setListener(this);
            viewMgr.pushView(lvc);
        }

        NetworkUtils.startSyncingTimer(this);
    }

    /**
     * Called when the activity is about to be destroyed.
     * Cleans up resources by clearing all views.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        viewMgr.clearViews();
    }

    /**
     * Handles requests to view an image.
     * Creates and pushes an ImageViewCtrl to display the image.
     * 
     * @param imgPath Path to the image to be displayed
     */
    @Override
    public void onImageViewRequired(final String imgPath) {
        final ImageViewCtrl ivc = new ImageViewCtrl(dataMgr, this, imgPath);
        ivc.setListener(this);
        viewMgr.pushView(ivc, R.anim.bottom_in, R.anim.scale_out);
        swipeView.setRightSwipeValid(false);
    }

    /**
     * Handles selection of an item list (feed).
     * Creates and pushes a FeedViewCtrl to display the selected feed.
     * 
     * @param uid Unique identifier of the selected feed
     * @param viewType Type of view to display (STARRED, ALL, or UNREAD)
     */
    @Override
    public void onItemListSelected(final String uid, final int viewType) {
        final FeedViewCtrl fvc = new FeedViewCtrl(dataMgr, this, uid, viewType);
        fvc.setListener(this);
        viewMgr.pushView(fvc, R.anim.right_in, R.anim.scale_out);
        swipeView.setRightSwipeValid(true);
    }

    /**
     * Handles selection of an individual item.
     * Creates and pushes a VerticalItemViewCtrl to display the selected item.
     * 
     * @param uid Unique identifier of the selected item
     */
    @Override
    public void onItemSelected(final String uid) {
        final VerticalItemViewCtrl ivc = new VerticalItemViewCtrl(dataMgr, this, uid,
                (FeedViewCtrl) viewMgr.getTopView());
        ivc.setListener(this);
        viewMgr.pushView(ivc, R.anim.right_in, R.anim.scale_out);
        swipeView.setRightSwipeValid(true);
    }

    /**
     * Handles the result of a login attempt.
     * If successful, displays the home view and initiates syncing if configured.
     * 
     * @param succeeded True if login was successful, false otherwise
     */
    @Override
    public void onLogin(final boolean succeeded) {
        if (succeeded) {
            final HomeViewCtrl hvc = new HomeViewCtrl(dataMgr, this);
            hvc.setListener(this);
            viewMgr.pushView(hvc, R.anim.right_in, R.anim.scale_out);
            final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(hvc.getView().getWindowToken(), 0);

            final int sSync = new SettingSyncMethod(dataMgr).getData();
            if (sSync != SettingSyncMethod.SYNC_METHOD_MANUAL) {
                NetworkUtils.doGlobalSyncing(this, sSync);
            }
        }
    }

    /**
     * Handles logout requests.
     * Displays a confirmation dialog and performs logout if confirmed.
     */
    @Override
    public void onLogoutRequired() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this,
                android.R.style.Theme_DeviceDefault_Dialog));
        builder.setIcon(android.R.drawable.ic_dialog_info);
        builder.setTitle(R.string.TxtConfirmation);
        builder.setMessage(R.string.TxtConfirmationLogout);
        builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @SuppressLint("HandlerLeak")
            @Override
            public void onClick(final DialogInterface dialog, final int which) {
                ProgressDialog.show(new ContextThemeWrapper(Home.this, android.R.style.Theme_DeviceDefault_Dialog),
                        Home.this.getString(R.string.TxtWorking), Home.this.getString(R.string.TxtClearingCache));
                /*final Handler handler = new Handler() {
                    @Override
                    public void handleMessage(final Message msg) {
                        final Intent intent = new Intent(Home.this, Home.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        Home.this.startActivity(intent);
                        finish();
                    }
                };*/
                final Thread thread = new Thread() {
                    @Override
                    public void run() {
                        DataMgr.getInstance().clearAll();
                        ReaderAccountMgr.getInstance().clearLogin();
                        DataUtils.deleteFile(new File(DataUtils.getAppFolderPath()));
                        //handler.sendEmptyMessage(0);
                        System.exit(0);
                    }
                };
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.start();
            }
        });
        builder.setNegativeButton(android.R.string.no, null);
        builder.show();
    }

    /**
     * Handles requests to view a website.
     * Creates and pushes a WebpageItemViewCtrl to display the selected webpage.
     * 
     * @param uid Unique identifier of the item whose webpage should be displayed
     * @param isMobilized True if the mobilized version should be displayed, false otherwise
     */
    @Override
    public void onWebsiteViewSelected(final String uid, final boolean isMobilized) {
        final WebpageItemViewCtrl mivc = new WebpageItemViewCtrl(dataMgr, this, uid, isMobilized);
        mivc.setListener(this);
        viewMgr.pushView(mivc, R.anim.bottom_in, R.anim.scale_out);
        swipeView.setRightSwipeValid(false);
    }

    /**
     * Called when the activity is paused (no longer in foreground).
     * Starts notifications to keep the user informed of updates.
     */
    @Override
    public void onPause() {
        super.onPause();
        NotificationMgr.getInstance().startNotification();
    }

    /**
     * Handles requests to reload the application.
     * Creates a new intent to restart the app, optionally showing settings on restart.
     * 
     * @param showSettings True if settings should be displayed after reload, false otherwise
     */
    @Override
    public void onReloadRequired(final boolean showSettings) {
        final Intent intent = getApplicationContext().getPackageManager().getLaunchIntentForPackage(
                getApplicationContext().getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        if (showSettings) {
            intent.putExtra(BUNDLE_KEY_SHOW_SETTINGS, true);
        }
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
        startActivity(intent);
    }

    /**
     * Called when the activity is resumed (comes to foreground).
     * Stops notifications as they are no longer needed when the app is visible.
     */
    @Override
    public void onResume() {
        super.onResume();
        NotificationMgr.getInstance().stopNotification();
    }

    /**
     * Handles requests to display the settings screen.
     * Creates and pushes a SettingsViewCtrl.
     */
    @Override
    public void onSettingsSelected() {
        final SettingsViewCtrl svc = new SettingsViewCtrl(dataMgr, this);
        svc.setListener(this);
        viewMgr.pushView(svc, R.anim.bottom_in, R.anim.scale_out);
        swipeView.setRightSwipeValid(false);
    }

    /**
     * Handles requests to sync data.
     * Initiates manual syncing of data.
     */
    @Override
    public void onSyncRequired() {
        NetworkUtils.doGlobalSyncing(this, SettingSyncMethod.SYNC_METHOD_MANUAL);
    }

    /**
     * Handles animation for canceling a swipe gesture.
     * Animates the view back to its original position.
     */
    private void swipeBackward() {
        if (totalSwipeX < 0) {
            totalSwipeX = 0;
        }
        final int screenWidth = swipeView.getWidth();
        final float scale = .3f * totalSwipeX / screenWidth + .7f;
        final AnimationSet animIn = new AnimationSet(true);
        animIn.addAnimation(new ScaleAnimation(scale, .7f, scale, .7f, Animation.RELATIVE_TO_PARENT, .5f,
                Animation.RELATIVE_TO_PARENT, .5f));
        animIn.addAnimation(new AlphaAnimation((float) totalSwipeX / screenWidth, 0.0f));
        animIn.setFillAfter(true);
        animIn.setDuration(SWIPE_ANIMATION_TIME * totalSwipeX / screenWidth);
        final Animation animOut = new TranslateAnimation(Animation.ABSOLUTE, totalSwipeX, Animation.ABSOLUTE, 0,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        animOut.setFillAfter(true);
        animOut.setDuration(SWIPE_ANIMATION_TIME * totalSwipeX / screenWidth);
        viewMgr.setStaticAnimation(animIn, animOut);
        viewMgr.restoreTopView();
    }

    /**
     * Handles animation for completing a swipe gesture.
     * Animates the transition to the next view.
     */
    private void swipeForward() {
        if (totalSwipeX < 0) {
            totalSwipeX = 0;
        }
        final int screenWidth = swipeView.getWidth();
        final float scale = .3f * totalSwipeX / screenWidth + .7f;
        final long duration = Math.max(0, SWIPE_ANIMATION_TIME - SWIPE_ANIMATION_TIME * totalSwipeX / screenWidth);
        final AnimationSet animIn = new AnimationSet(true);
        animIn.addAnimation(new ScaleAnimation(scale, 1.0f, scale, 1.0f, Animation.RELATIVE_TO_PARENT, .5f,
                Animation.RELATIVE_TO_PARENT, .5f));
        animIn.addAnimation(new AlphaAnimation((float) totalSwipeX / screenWidth, 1.0f));
        animIn.setDuration(duration);
        final Animation animOut = new TranslateAnimation(Animation.ABSOLUTE, totalSwipeX, Animation.ABSOLUTE,
                screenWidth, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        animOut.setDuration(duration);
        viewMgr.popView(animIn, animOut);
        if (viewMgr.getTopView() instanceof HomeViewCtrl) {
            swipeView.setRightSwipeValid(false);
        } else {
            swipeView.setRightSwipeValid(true);
        }
    }

    /**
     * Handles left swipe gestures.
     * Cancels the current swipe and resets tracking.
     */
    @Override
    public void swipeLeft() {
        swipeBackward();
        totalSwipeX = 0;
    }

    /**
     * Handles right swipe gestures.
     * Completes the current swipe and resets tracking.
     */
    @Override
    public void swipeRight() {
        swipeForward();
        totalSwipeX = 0;
    }

    /**
     * Tracks and animates the view during an ongoing swipe gesture.
     * 
     * @param deltaX The horizontal distance moved in this update
     */
    @Override
    public void swipeTo(final int deltaX) {
        totalSwipeX += -deltaX;
        if (totalSwipeX > 0) {
            final int screenWidth = swipeView.getWidth();
            final float scale = .3f * totalSwipeX / screenWidth + .7f;
            final AnimationSet animIn = new AnimationSet(true);
            animIn.addAnimation(new ScaleAnimation(scale, scale, scale, scale, Animation.RELATIVE_TO_PARENT, .5f,
                    Animation.RELATIVE_TO_PARENT, .5f));
            animIn.addAnimation(new AlphaAnimation((float) totalSwipeX / screenWidth, (float) totalSwipeX / screenWidth));
            animIn.setFillAfter(true);
            animIn.setDuration(Long.MAX_VALUE);
            final Animation animOut = new TranslateAnimation(Animation.ABSOLUTE, totalSwipeX, Animation.ABSOLUTE,
                    totalSwipeX, Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            animOut.setFillAfter(true);
            animOut.setDuration(Long.MAX_VALUE);
            viewMgr.setStaticAnimation(animIn, animOut);
        }
    }
}
