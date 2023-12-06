/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.launcher3.icons;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

import androidx.annotation.NonNull;

/**
 * A drawable used for drawing user badge. It draws a circle around the actual badge,
 * and has support for theming.
 */
public class UserBadgeDrawable extends DrawableWrapper {

    private static final float VIEWPORT_SIZE = 24;
    private static final float CENTER = VIEWPORT_SIZE / 2;

    private static final float BG_RADIUS = 11;
    private static final float SHADOW_RADIUS = 11.5f;
    private static final float SHADOW_OFFSET_Y = 0.25f;

    private static final int SHADOW_COLOR = 0x11000000;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int mBgColor;
    private boolean mShouldDrawBackground = true;

    public UserBadgeDrawable(Context context, int badgeRes, boolean isThemed) {
        super(context.getDrawable(badgeRes));

        if (isThemed) {
            mutate();
            setTint(context.getColor(R.color.themed_badge_icon_color));
            mBgColor = context.getColor(R.color.themed_badge_icon_background_color);
        } else {
            mBgColor = Color.WHITE;
        }
    }

    private UserBadgeDrawable(Drawable base, int bgColor, boolean shouldDrawBackground) {
        super(base);
        mBgColor = bgColor;
        mShouldDrawBackground = shouldDrawBackground;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mShouldDrawBackground) {
            Rect b = getBounds();
            int saveCount = canvas.save();
            canvas.translate(b.left, b.top);
            canvas.scale(b.width() / VIEWPORT_SIZE, b.height() / VIEWPORT_SIZE);

            mPaint.setColor(SHADOW_COLOR);
            canvas.drawCircle(CENTER, CENTER + SHADOW_OFFSET_Y, SHADOW_RADIUS, mPaint);

            mPaint.setColor(mBgColor);
            canvas.drawCircle(CENTER, CENTER, BG_RADIUS, mPaint);

            canvas.restoreToCount(saveCount);
        }
        super.draw(canvas);
    }

    public void setShouldDrawBackground(boolean shouldDrawBackground) {
        mutate();
        mShouldDrawBackground = shouldDrawBackground;
    }

    @Override
    public ConstantState getConstantState() {
        return new MyConstantState(
                getDrawable().getConstantState(), mBgColor, mShouldDrawBackground);
    }

    private static class MyConstantState extends ConstantState {

        private final ConstantState mBase;
        private final int mBgColor;
        private final boolean mShouldDrawBackground;

        public MyConstantState(ConstantState base, int bgColor, boolean shouldDrawBackground) {
            mBase = base;
            mBgColor = bgColor;
            mShouldDrawBackground = shouldDrawBackground;
        }

        @Override
        public int getChangingConfigurations() {
            return mBase.getChangingConfigurations();
        }

        @Override
        @NonNull
        public Drawable newDrawable() {
            return new UserBadgeDrawable(mBase.newDrawable(), mBgColor, mShouldDrawBackground);
        }

        @Override
        @NonNull
        public Drawable newDrawable(Resources res) {
            return new UserBadgeDrawable(mBase.newDrawable(res), mBgColor, mShouldDrawBackground);
        }

        @Override
        @NonNull
        public Drawable newDrawable(Resources res, Theme theme) {
            return new UserBadgeDrawable(mBase.newDrawable(res, theme),
                    mBgColor, mShouldDrawBackground);
        }
    }
}
