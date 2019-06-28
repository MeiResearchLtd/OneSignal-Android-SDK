package com.onesignal;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.v7.widget.CardView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;

import static com.onesignal.OSViewUtils.dpToPx;

/**
 * Layout Documentation
 * ### Modals & Banners ###
 *  - WebView
 *      - width  = MATCH_PARENT
 *      - height = PX height provided via a JS event for the content
 *  - Parent Layouts
 *      - width  = MATCH_PARENT
 *      - height = WRAP_CONTENT - Since the WebView is providing the height.
 * ### Fullscreen ###
 *  - WebView
 *      - width  = MATCH_PARENT
 *      - height = MATCH_PARENT
 *  - Parent Layouts
 *      - width  = MATCH_PARENT
 *      - height = MATCH_PARENT
 */
class InAppMessageView {

    private static final int ACTIVITY_BACKGROUND_COLOR_EMPTY = Color.parseColor("#00000000");
    private static final int ACTIVITY_BACKGROUND_COLOR_FULL = Color.parseColor("#BB000000");

    private static final int IN_APP_BANNER_ANIMATION_DURATION_MS = 1000;
    private static final int IN_APP_CENTER_ANIMATION_DURATION_MS = 1000;
    private static final int IN_APP_BACKGROUND_ANIMATION_DURATION_MS = 400;

    private static final int ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS = 600;
    private static final int ACTIVITY_INIT_DELAY = 200;
    private static final int MARGIN_PX_SIZE = dpToPx(24);

    static abstract class InAppMessageController {
        void onMessageWasShown() {
        }

        void onMessageWasDismissed() {
        }
    }

    private final Handler handler = new Handler();
    private int pageWidth;
    private int pageHeight;
    private double dismissDuration;
    private boolean hasBackground;
    private boolean shouldDismissWhenActive = false;
    private WebViewManager.Position displayLocation;
    private WebView webView;
    private LinearLayout parentLinearLayout;
    private DraggableRelativeLayout draggableRelativeLayout;
    private InAppMessageController messageController;
    private Runnable dismissSchedule;

    InAppMessageView(@NonNull WebView webView, WebViewManager.Position displayLocation, int pageHeight, double dismissDuration) {
        this(webView, displayLocation, pageHeight, ConstraintLayout.LayoutParams.MATCH_PARENT, dismissDuration);
    }

    private InAppMessageView(@NonNull WebView webView, WebViewManager.Position displayLocation, int pageHeight, int pageWidth, double dismissDuration) {
        this.webView = webView;
        this.displayLocation = displayLocation;
        this.pageHeight = pageHeight;
        this.pageWidth = pageWidth;
        this.dismissDuration = dismissDuration;
        this.hasBackground = isBanner();
    }

    void setWebView(WebView webView) {
        this.webView = webView;
    }

    void setMessageController(InAppMessageController messageController) {
        this.messageController = messageController;
    }

    void destroyView(WeakReference<Activity> weakReference) {
        // WeakReference is the Activity when onStop is called
        if (weakReference.get() != null) {
            if (webView != null) {
                webView.removeAllViews();
            }
            if (draggableRelativeLayout != null) {
                draggableRelativeLayout.removeAllViews();
            }
            if (parentLinearLayout != null) {
                removeParentLinearLayout(weakReference.get());
                parentLinearLayout.removeAllViews();
            }
        }
        markAsDismissed();
    }

    void showView(Activity activity) {
        delayShowUntilAvailable(activity);
    }

    void checkIfShouldDismiss() {
        if (shouldDismissWhenActive) {
            shouldDismissWhenActive = false;
            finishAfterDelay(null);
        }
    }

    /**
     * This will fired when the device is rotated for example with a new provided height for the WebView
     * Called to shrink or grow the WebView when it receives a JS resize event with a new height.
     *
     * @param pageHeight the provided height
     */
    void updateHeight(final int pageHeight) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                ViewGroup.LayoutParams layoutParams = webView.getLayoutParams();
                layoutParams.height = pageHeight;
                // We only need to update the WebView size since it's parent layouts are set to
                //   WRAP_CONTENT to always match the height of the WebView. (Expect for fullscreen)
                webView.setLayoutParams(layoutParams);
                draggableRelativeLayout.setParams(createDraggableLayoutParams(pageHeight, displayLocation));
            }
        });
    }

    void showInAppMessageView() {
        DraggableRelativeLayout.LayoutParams webViewLayoutParams = new DraggableRelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                pageHeight
        );
        webViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

        LinearLayout.LayoutParams linearLayoutParams = hasBackground ? createParentLinearLayoutParams() : null;

        showDraggableView(
                displayLocation,
                webViewLayoutParams,
                linearLayoutParams,
                createDraggableLayoutParams(pageHeight, displayLocation),
                createWindowLayout(pageWidth)
        );
    }

    private int getDisplayYSize() {
        Activity currentActivity = ActivityLifecycleHandler.curActivity;
        return OSViewUtils.getUsableWindowRect(currentActivity).height();
    }

    private LinearLayout.LayoutParams createParentLinearLayoutParams() {
        LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(pageWidth, LinearLayout.LayoutParams.MATCH_PARENT);

        switch (displayLocation) {
            case TOP_BANNER:
                linearLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                break;
            case BOTTOM_BANNER:
                linearLayoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                break;
            case CENTER_MODAL:
            case FULL_SCREEN:
                linearLayoutParams.gravity = Gravity.CENTER;
        }

        return linearLayoutParams;
    }

    private DraggableRelativeLayout.Params createDraggableLayoutParams(int pageHeight, WebViewManager.Position displayLocation) {
        DraggableRelativeLayout.Params draggableParams = new DraggableRelativeLayout.Params();
        draggableParams.maxXPos = MARGIN_PX_SIZE;
        draggableParams.maxYPos = MARGIN_PX_SIZE;

        draggableParams.messageHeight = pageHeight;
        draggableParams.height = getDisplayYSize();

        if (displayLocation == WebViewManager.Position.FULL_SCREEN)
            draggableParams.messageHeight = pageHeight = getDisplayYSize() - (MARGIN_PX_SIZE * 2);

        switch (displayLocation) {
            case BOTTOM_BANNER:
                draggableParams.posY = getDisplayYSize() - pageHeight;
                break;
            case CENTER_MODAL:
            case FULL_SCREEN:
                draggableParams.maxYPos = (getDisplayYSize() / 2) - (pageHeight / 2);
                draggableParams.posY = (getDisplayYSize() / 2) - (pageHeight / 2);
                break;
        }

        draggableParams.dragDirection = displayLocation == WebViewManager.Position.TOP_BANNER ?
                DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_UP :
                DraggableRelativeLayout.Params.DRAGGABLE_DIRECTION_DOWN;

        return draggableParams;
    }

    private WindowManager.LayoutParams createWindowLayout(int pageWidth) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                hasBackground ? WindowManager.LayoutParams.MATCH_PARENT : pageWidth,
                hasBackground ? WindowManager.LayoutParams.MATCH_PARENT : WindowManager.LayoutParams.WRAP_CONTENT,
                // Display it on top of other application windows
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                // Don't let it grab the input focus
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        Activity currentActivity = ActivityLifecycleHandler.curActivity;
        if (currentActivity != null) {
            layoutParams.token = currentActivity.getWindow().getDecorView().getApplicationWindowToken();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            layoutParams.rotationAnimation = WindowManager.LayoutParams.ROTATION_ANIMATION_CROSSFADE;
        }
        if (!hasBackground) {
            switch (displayLocation) {
                case TOP_BANNER:
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
                    break;
                case BOTTOM_BANNER:
                    layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
                    break;
            }
        }
        return layoutParams;
    }

    private void showDraggableView(final WebViewManager.Position displayLocation,
                                   final RelativeLayout.LayoutParams relativeLayoutParams,
                                   final LinearLayout.LayoutParams linearLayoutParams,
                                   final DraggableRelativeLayout.Params webViewLayoutParams,
                                   final WindowManager.LayoutParams parentLinearLayoutParams) {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                Activity currentActivity = ActivityLifecycleHandler.curActivity;
                if (currentActivity != null && webView != null) {
                    webView.setLayoutParams(relativeLayoutParams);

                    Context context = currentActivity.getApplicationContext();
                    setUpDraggableLayout(context, linearLayoutParams, webViewLayoutParams);
                    setUpParentLinearLayout(context);

                    WindowManager windowManager = currentActivity.getWindowManager();
                    if (windowManager != null && parentLinearLayout != null && parentLinearLayout.getWindowToken() == null) {
                        windowManager.addView(parentLinearLayout, parentLinearLayoutParams);

                        if (messageController != null) {
                            animateInAppMessage(displayLocation, draggableRelativeLayout, parentLinearLayout);
                            messageController.onMessageWasShown();
                        }

                    }

                    initDismissIfNeeded();
                }
            }
        });
    }

    private void setUpParentLinearLayout(Context context) {
        parentLinearLayout = new LinearLayout(context);
        parentLinearLayout.setBackgroundColor(ACTIVITY_BACKGROUND_COLOR_EMPTY);
        parentLinearLayout.setClipChildren(false);
        parentLinearLayout.setClipToPadding(false);
        parentLinearLayout.addView(draggableRelativeLayout);
    }

    private void setUpDraggableLayout(Context context,
                                      LinearLayout.LayoutParams linearLayoutParams,
                                      DraggableRelativeLayout.Params draggableParams) {
        draggableRelativeLayout = new DraggableRelativeLayout(context);
        if (linearLayoutParams != null) {
            draggableRelativeLayout.setLayoutParams(linearLayoutParams);
        }
        draggableRelativeLayout.setParams(draggableParams);
        draggableRelativeLayout.setListener(new DraggableRelativeLayout.DraggableListener() {
            @Override
            void onDismiss() {
                finishAfterDelay(null);
            }
        });

        if (webView.getParent() != null) {
            ((ViewGroup) webView.getParent()).removeAllViews();
        }

        CardView cardView = createCardView(context);
        cardView.addView(webView);

        draggableRelativeLayout.setPadding(MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE, MARGIN_PX_SIZE);
        draggableRelativeLayout.setClipChildren(false);
        draggableRelativeLayout.setClipToPadding(false);
        draggableRelativeLayout.addView(cardView);
    }

    /**
     * To show drop shadow on WebView
     * Layout container for WebView is needed
     */
    private CardView createCardView(Context context) {
        CardView cardView = new CardView(context);

        int height = displayLocation == WebViewManager.Position.FULL_SCREEN ?
           ViewGroup.LayoutParams.MATCH_PARENT :
           ViewGroup.LayoutParams.WRAP_CONTENT;
        RelativeLayout.LayoutParams cardViewLayoutParams = new RelativeLayout.LayoutParams(
           ViewGroup.LayoutParams.MATCH_PARENT,
           height
        );
        cardViewLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        cardView.setLayoutParams(cardViewLayoutParams);

        cardView.setRadius(dpToPx(8));
        cardView.setCardElevation(dpToPx(5));

        cardView.setClipChildren(false);
        cardView.setClipToPadding(false);
        cardView.setPreventCornerOverlap(false);
        return cardView;
    }

    /**
     * Schedule dismiss behavior
     */
    private void initDismissIfNeeded() {
        if (dismissDuration > 0 && dismissSchedule == null) {
            dismissSchedule = new Runnable() {
                public void run() {
                    Activity currentActivity = ActivityLifecycleHandler.curActivity;
                    if (currentActivity != null) {
                        dismiss();
                        dismissSchedule = null;
                    } else {
                        //for cases when the app is on background and the dismiss is triggered
                        shouldDismissWhenActive = true;
                    }
                }
            };

            handler.postDelayed(dismissSchedule, (long) dismissDuration * 1_000);
        }
    }

    /**
     * Do not add view until activity is ready
     * To check if activity is ready, token must not be null
     *
     * @param currentActivity the activity where to show the view
     */
    private void delayShowUntilAvailable(final Activity currentActivity) {
        if (currentActivity.getWindow().getDecorView().getApplicationWindowToken() != null && parentLinearLayout == null) {
            showInAppMessageView();
        }
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                delayShowUntilAvailable(currentActivity);
            }
        }, ACTIVITY_INIT_DELAY);
    }

    void dismissAndAwaitNextMessage(WebViewManager.OneSignalGenericCallback callback) {
        if (draggableRelativeLayout == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "No host presenter to trigger dismiss animation, counting as dismissed already");
            markAsDismissed();
            callback.onComplete();
            return;
        }

        draggableRelativeLayout.dismiss();
        finishAfterDelay(callback);
    }

    /**
     * Trigger the {@link #draggableRelativeLayout} dismiss animation
     */
    void dismiss() {
        if (draggableRelativeLayout == null) {
            OneSignal.Log(OneSignal.LOG_LEVEL.ERROR, "No host presenter to trigger dismiss animation, counting as dismissed already");
            markAsDismissed();
            return;
        }

        draggableRelativeLayout.dismiss();
        finishAfterDelay(null);
    }

    /**
     * Finishing on a timer as continueSettling does not return false
     * when using smoothSlideViewTo on Android 4.4
     */
    private void finishAfterDelay(final WebViewManager.OneSignalGenericCallback callback) {
        OSUtils.runOnMainThreadDelayed(new Runnable() {
            @Override
            public void run() {
                if (hasBackground && parentLinearLayout != null) {
                    animateAndDismissLayout(parentLinearLayout, callback);
                } else {
                    removeViews(callback);
                }
            }
        }, ACTIVITY_FINISH_AFTER_DISMISS_DELAY_MS);
    }

    private void finish() {
        OSUtils.runOnMainUIThread(new Runnable() {
            @Override
            public void run() {
                removeViews(null);
            }
        });
    }

    /**
     * Remove references from the views
     * @param callback
     */
    private void removeViews(WebViewManager.OneSignalGenericCallback callback) {
        if (dismissSchedule != null) {
            //dismissed before the dismiss delay
            handler.removeCallbacks(dismissSchedule);
            dismissSchedule = null;
        }
        if (draggableRelativeLayout != null) {
            draggableRelativeLayout.removeAllViews();
        }

        Activity currentActivity = ActivityLifecycleHandler.curActivity;
        removeParentLinearLayout(currentActivity);

        if (messageController != null) {
            messageController.onMessageWasDismissed();
        }
        markAsDismissed();

        if (callback != null)
            callback.onComplete();
    }

    private void removeParentLinearLayout(Activity currentActivity) {
        if (currentActivity != null) {
            WindowManager windowManager = currentActivity.getWindowManager();
            if (parentLinearLayout != null && windowManager != null) {
                parentLinearLayout.setVisibility(View.INVISIBLE);
                windowManager.removeView(parentLinearLayout);
            }
        }
    }

    /**
     * Cleans all layout references so this can be cleaned up in the next GC
     */
    private void markAsDismissed() {
        // Dereference so this can be cleaned up in the next GC
        parentLinearLayout = null;
        draggableRelativeLayout = null;
        webView = null;
    }

    /**
     * TOP and BOTTOM display location are for banner cases
     */
    private boolean isBanner() {
        switch (displayLocation) {
            case TOP_BANNER:
            case BOTTOM_BANNER:
                return false;
        }
        return true;
    }

    private void animateInAppMessage(WebViewManager.Position displayLocation, View messageView, View backgroundView) {
        // Based on the location of the in app message apply and animation to match
        switch (displayLocation) {
            case TOP_BANNER:
                View topBannerMessageViewChild = ((ViewGroup) messageView).getChildAt(0);
                animateTop(topBannerMessageViewChild, webView.getHeight());
                break;
            case BOTTOM_BANNER:
                View bottomBannerMessageViewChild = ((ViewGroup) messageView).getChildAt(0);
                animateBottom(bottomBannerMessageViewChild, webView.getHeight());
                break;
            case CENTER_MODAL:
            case FULL_SCREEN:
                animateCenter(messageView, backgroundView);
                break;
        }
    }

    private void animateTop(View messageView, int height) {
        // Animate the message view from above the screen downward to the top
        OneSignalAnimate.animateViewByTranslation(
                messageView,
                -height - MARGIN_PX_SIZE,
                0f,
                IN_APP_BANNER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                null)
                .start();
    }

    private void animateBottom(View messageView, int height) {
        // Animate the message view from under the screen upward to the bottom
        OneSignalAnimate.animateViewByTranslation(
                messageView,
                height + MARGIN_PX_SIZE,
                0f,
                IN_APP_BANNER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                null)
                .start();
    }

    private void animateCenter(View messageView, final View backgroundView) {
        // Animate the message view by scale since it settles at the center of the screen
        Animation messageAnimation = OneSignalAnimate.animateViewSmallToLarge(
                messageView,
                IN_APP_CENTER_ANIMATION_DURATION_MS,
                new OneSignalBounceInterpolator(0.1, 8.0),
                null);

        // Animate background behind the message so it doesn't just show the dark transparency
        ValueAnimator backgroundAnimation = animateBackgroundColor(
                backgroundView,
                IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
                ACTIVITY_BACKGROUND_COLOR_EMPTY,
                ACTIVITY_BACKGROUND_COLOR_FULL,
                null);

        messageAnimation.start();
        backgroundAnimation.start();
    }

    private void animateAndDismissLayout(View backgroundView, final WebViewManager.OneSignalGenericCallback callback) {
        Animator.AnimatorListener animCallback = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeViews(callback);
            }
        };

        // Animate background behind the message so it hides before being removed from the view
        animateBackgroundColor(
                backgroundView,
                IN_APP_BACKGROUND_ANIMATION_DURATION_MS,
                ACTIVITY_BACKGROUND_COLOR_FULL,
                ACTIVITY_BACKGROUND_COLOR_EMPTY,
                animCallback)
                .start();
    }

    private ValueAnimator animateBackgroundColor(View backgroundView, int duration, int startColor, int endColor, Animator.AnimatorListener animCallback) {
        return OneSignalAnimate.animateViewColor(
                backgroundView,
                duration,
                startColor,
                endColor,
                animCallback);
    }
}