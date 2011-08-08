/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.TabHost;
import android.widget.TabWidget;
import android.widget.TextView;

import com.android.launcher.R;

public class AppsCustomizeTabHost extends TabHost implements LauncherTransitionable,
        TabHost.OnTabChangeListener  {
    static final String LOG_TAG = "AppsCustomizeTabHost";

    private static final String APPS_TAB_TAG = "APPS";
    private static final String WIDGETS_TAB_TAG = "WIDGETS";

    private final LayoutInflater mLayoutInflater;
    private ViewGroup mTabs;
    private ViewGroup mTabsContainer;
    private AppsCustomizePagedView mAppsCustomizePane;

    public AppsCustomizeTabHost(Context context, AttributeSet attrs) {
        super(context, attrs);
        mLayoutInflater = LayoutInflater.from(context);
    }

    /**
     * Convenience methods to select specific tabs.  We want to set the content type immediately
     * in these cases, but we note that we still call setCurrentTabByTag() so that the tab view
     * reflects the new content (but doesn't do the animation and logic associated with changing
     * tabs manually).
     */
    private void setContentTypeImmediate(AppsCustomizePagedView.ContentType type) {
        onTabChangedStart();
        onTabChangedEnd(type);
    }
    void selectAppsTab() {
        setContentTypeImmediate(AppsCustomizePagedView.ContentType.Applications);
        setCurrentTabByTag(APPS_TAB_TAG);
    }
    void selectWidgetsTab() {
        setContentTypeImmediate(AppsCustomizePagedView.ContentType.Widgets);
        mAppsCustomizePane.setCurrentPageToWidgets();

        setCurrentTabByTag(WIDGETS_TAB_TAG);
    }

    /**
     * Setup the tab host and create all necessary tabs.
     */
    @Override
    protected void onFinishInflate() {
        // Setup the tab host
        setup();

        final ViewGroup tabsContainer = (ViewGroup) findViewById(R.id.tabs_container);
        final TabWidget tabs = (TabWidget) findViewById(com.android.internal.R.id.tabs);
        final AppsCustomizePagedView appsCustomizePane = (AppsCustomizePagedView)
                findViewById(R.id.apps_customize_pane_content);
        mTabs = tabs;
        mTabsContainer = tabsContainer;
        mAppsCustomizePane = appsCustomizePane;
        if (tabs == null || mAppsCustomizePane == null) throw new Resources.NotFoundException();

        // Configure the tabs content factory to return the same paged view (that we change the
        // content filter on)
        TabContentFactory contentFactory = new TabContentFactory() {
            public View createTabContent(String tag) {
                return appsCustomizePane;
            }
        };

        // Create the tabs
        TextView tabView;
        tabView = (TextView) mLayoutInflater.inflate(R.layout.tab_widget_indicator, tabs, false);
        tabView.setText(mContext.getString(R.string.all_apps_button_label));
        addTab(newTabSpec(APPS_TAB_TAG).setIndicator(tabView).setContent(contentFactory));
        tabView = (TextView) mLayoutInflater.inflate(R.layout.tab_widget_indicator, tabs, false);
        tabView.setText(mContext.getString(R.string.widgets_tab_label));
        addTab(newTabSpec(WIDGETS_TAB_TAG).setIndicator(tabView).setContent(contentFactory));
        setOnTabChangedListener(this);

        // Setup the key listener to jump between the last tab view and the market icon
        AppsCustomizeTabKeyEventListener keyListener = new AppsCustomizeTabKeyEventListener();
        View lastTab = tabs.getChildTabViewAt(tabs.getTabCount() - 1);
        lastTab.setOnKeyListener(keyListener);
        View shopButton = findViewById(R.id.market_button);
        shopButton.setOnKeyListener(keyListener);

        // Hide the tab bar until we measure
        mTabsContainer.setAlpha(0f);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        boolean remeasureTabWidth = (mTabs.getLayoutParams().width <= 0);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Set the width of the tab list to the content width
        if (remeasureTabWidth) {
            int contentWidth = mAppsCustomizePane.getPageContentWidth();
            if (contentWidth > 0) {
                // Set the width and show the tab bar (if we have a loading graphic, we can switch
                // it off here)
                mTabs.getLayoutParams().width = contentWidth;
                mTabsContainer.setAlpha(1f);
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Intercept all touch events up to the bottom of the AppsCustomizePane so they do not fall
        // through to the workspace and trigger showWorkspace()
        if (event.getY() < mAppsCustomizePane.getBottom()) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void onTabChangedStart() {
        mAppsCustomizePane.hideScrollingIndicator(false);
    }
    private void onTabChangedEnd(AppsCustomizePagedView.ContentType type) {
        mAppsCustomizePane.setContentType(type);
    }

    @Override
    public void onTabChanged(String tabId) {
        final AppsCustomizePagedView.ContentType type = getContentTypeForTabTag(tabId);
        if (!mAppsCustomizePane.isContentType(type)) {
            // Animate the changing of the tab content by fading pages in and out
            final Resources res = getResources();
            final int duration = res.getInteger(R.integer.config_tabTransitionDuration);

            ObjectAnimator anim = ObjectAnimator.ofFloat(mAppsCustomizePane, "alpha", 0f);
            anim.setDuration(duration);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(android.animation.Animator animation) {
                    onTabChangedStart();
                }
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    onTabChangedEnd(type);

                    ObjectAnimator anim = ObjectAnimator.ofFloat(mAppsCustomizePane, "alpha", 1f);
                    anim.setDuration(duration);
                    anim.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(android.animation.Animator animation) {
                            if (!LauncherApplication.isScreenLarge()) {
                                mAppsCustomizePane.flashScrollingIndicator();
                            }
                        }
                    });
                    anim.start();
                }
            });
            anim.start();
        }
    }

    /**
     * Returns the content type for the specified tab tag.
     */
    public AppsCustomizePagedView.ContentType getContentTypeForTabTag(String tag) {
        if (tag.equals(APPS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Applications;
        } else if (tag.equals(WIDGETS_TAB_TAG)) {
            return AppsCustomizePagedView.ContentType.Widgets;
        }
        return AppsCustomizePagedView.ContentType.Applications;
    }

    /**
     * Returns the tab tag for a given content type.
     */
    public String getTabTagForContentType(AppsCustomizePagedView.ContentType type) {
        if (type == AppsCustomizePagedView.ContentType.Applications) {
            return APPS_TAB_TAG;
        } else if (type == AppsCustomizePagedView.ContentType.Widgets) {
            return WIDGETS_TAB_TAG;
        }
        return APPS_TAB_TAG;
    }

    /**
     * Disable focus on anything under this view in the hierarchy if we are not visible.
     */
    @Override
    public int getDescendantFocusability() {
        if (getVisibility() != View.VISIBLE) {
            return ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        }
        return super.getDescendantFocusability();
    }

    /* LauncherTransitionable overrides */
    @Override
    public void onLauncherTransitionStart(Animator animation, boolean toWorkspace) {
        // isHardwareAccelerated() checks if we're attached to a window and if that
        // window is HW accelerated-- we were sometimes not attached to a window
        // and buildLayer was throwing an IllegalStateException
        if (animation != null && isHardwareAccelerated()) {
            // Turn on hardware layers for performance
            setLayerType(LAYER_TYPE_HARDWARE, null);

            // force building the layer at the beginning of the animation, so you don't get a
            // blip early in the animation
            buildLayer();
        }
        if (!toWorkspace && !LauncherApplication.isScreenLarge()) {
            mAppsCustomizePane.showScrollingIndicator(false);
        }
    }

    @Override
    public void onLauncherTransitionEnd(Animator animation, boolean toWorkspace) {
        if (animation != null) {
            setLayerType(LAYER_TYPE_NONE, null);
        }

        if (!toWorkspace && !LauncherApplication.isScreenLarge()) {
            mAppsCustomizePane.hideScrollingIndicator(false);
        }
    }
}
