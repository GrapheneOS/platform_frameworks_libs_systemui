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
package com.android.launcher3.icons;

import static com.android.launcher3.icons.GraphicsUtils.getExpectedBitmapSize;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.icons.ThemedIconDrawable.ThemedBitmapInfo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BitmapInfo {

    static final int FLAG_WORK = 1 << 0;
    static final int FLAG_INSTANT = 1 << 1;
    @IntDef(flag = true, value = {
            FLAG_WORK,
            FLAG_INSTANT,
    })
    @interface BitmapInfoFlags {}

    public static final int FLAG_THEMED = 1 << 0;
    public static final int FLAG_NO_BADGE = 1 << 1;
    @IntDef(flag = true, value = {
            FLAG_THEMED,
            FLAG_NO_BADGE,
    })
    public @interface DrawableCreationFlags {}

    public static final Bitmap LOW_RES_ICON = Bitmap.createBitmap(1, 1, Config.ALPHA_8);
    public static final BitmapInfo LOW_RES_INFO = fromBitmap(LOW_RES_ICON);

    public static final String TAG = "BitmapInfo";

    protected static final byte TYPE_DEFAULT = 1;
    protected static final byte TYPE_THEMED = 2;

    public final Bitmap icon;
    public final int color;

    public @BitmapInfoFlags int flags;
    private BitmapInfo badgeInfo;

    public BitmapInfo(Bitmap icon, int color) {
        this.icon = icon;
        this.color = color;
    }

    public BitmapInfo withBadgeInfo(BitmapInfo badgeInfo) {
        BitmapInfo result = new BitmapInfo(icon, color);
        result.flags = flags;
        result.badgeInfo = badgeInfo;
        return result;
    }

    @Override
    public BitmapInfo clone() {
        BitmapInfo result = new BitmapInfo(icon, color);
        result.flags = flags;
        return result;
    }

    /**
     * Ensures that the BitmapInfo represents the provided user
     */
    public BitmapInfo withUser(UserHandle userHandle) {
        if (userHandle == null || Process.myUserHandle().equals(userHandle)) {
            flags &= ~FLAG_WORK;
        } else {
            flags |= FLAG_WORK;
        }
        return this;
    }

    /**
     * Ideally icon should not be null, except in cases when generating hardware bitmap failed
     */
    public final boolean isNullOrLowRes() {
        return icon == null || icon == LOW_RES_ICON;
    }

    public final boolean isLowRes() {
        return LOW_RES_ICON == icon;
    }

    /**
     * Returns a serialized version of BitmapInfo
     */
    @Nullable
    public byte[] toByteArray() {
        if (isNullOrLowRes()) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(getExpectedBitmapSize(icon) + 1);
        try {
            out.write(TYPE_DEFAULT);
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
            return out.toByteArray();
        } catch (IOException e) {
            Log.w(TAG, "Could not write bitmap");
            return null;
        }
    }

    /**
     * Creates a drawable for the provided BitmapInfo
     */
    public FastBitmapDrawable newIcon(Context context) {
        return newIcon(context, 0);
    }

    /**
     * Creates a drawable for the provided BitmapInfo
     */
    public FastBitmapDrawable newIcon(Context context, @DrawableCreationFlags int creationFlags) {
        FastBitmapDrawable drawable = isLowRes()
                ? new PlaceHolderIconDrawable(this, context)
                : new FastBitmapDrawable(this);
        applyFlags(context, drawable, creationFlags);
        return drawable;
    }

    protected void applyFlags(Context context, FastBitmapDrawable drawable,
            @DrawableCreationFlags int creationFlags) {
        drawable.mDisabledAlpha = GraphicsUtils.getFloat(context, R.attr.disabledIconAlpha, 1f);
        if ((creationFlags & FLAG_NO_BADGE) == 0) {
            if (badgeInfo != null) {
                drawable.setBadge(badgeInfo.newIcon(context, creationFlags));
            } else if ((flags & FLAG_INSTANT) != 0) {
                drawable.setBadge(context.getDrawable(R.drawable.ic_instant_app_badge));
            } else if ((flags & FLAG_WORK) != 0) {
                drawable.setBadge(context.getDrawable(R.drawable.ic_work_app_badge));
            }
        }
    }

    /**
     * Returns a BitmapInfo previously serialized using {@link #toByteArray()};
     */
    @NonNull
    public static BitmapInfo fromByteArray(byte[] data, int color, Context context) {
        if (data == null) {
            throw new NullPointerException();
        }
        BitmapFactory.Options decodeOptions;
        if (BitmapRenderer.USE_HARDWARE_BITMAP && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            decodeOptions = new BitmapFactory.Options();
            decodeOptions.inPreferredConfig = Bitmap.Config.HARDWARE;
        } else {
            decodeOptions = null;
        }
        if (data[0] == TYPE_DEFAULT) {
            return BitmapInfo.of(
                    BitmapFactory.decodeByteArray(data, 1, data.length - 1, decodeOptions),
                    color);
        } else if (data[0] == TYPE_THEMED) {
            return ThemedBitmapInfo.decode(data, color, decodeOptions, context);
        } else {
            throw new IllegalArgumentException("Unknown type " + data[0]);
        }
    }

    public static BitmapInfo fromBitmap(@NonNull Bitmap bitmap) {
        return of(bitmap, 0);
    }

    public static BitmapInfo of(@NonNull Bitmap bitmap, int color) {
        return new BitmapInfo(bitmap, color);
    }

    /**
     * Interface to be implemented by drawables to provide a custom BitmapInfo
     */
    public interface Extender {

        /**
         * Called for creating a custom BitmapInfo
         */
        BitmapInfo getExtendedInfo(Bitmap bitmap, int color,
                BaseIconFactory iconFactory, float normalizationScale);

        /**
         * Called to draw the UI independent of any runtime configurations like time or theme
         */
        void drawForPersistence(Canvas canvas);

        /**
         * Returns a new icon with theme applied
         */
        Drawable getThemedDrawable(Context context);
    }
}
