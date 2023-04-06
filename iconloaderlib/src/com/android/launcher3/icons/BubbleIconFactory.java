package com.android.launcher3.icons;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;

/**
 * Factory for creating normalized bubble icons.
 */
public class BubbleIconFactory extends BaseIconFactory {

    public BubbleIconFactory(Context context, int iconSize) {
        super(context, context.getResources().getConfiguration().densityDpi, iconSize);
    }

    /**
     * Returns the drawable that the developer has provided to display in the bubble.
     */
    public Drawable getBubbleDrawable(@NonNull final Context context,
            @Nullable final ShortcutInfo shortcutInfo, @Nullable final Icon ic) {
        if (shortcutInfo != null) {
            LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
            int density = context.getResources().getConfiguration().densityDpi;
            return launcherApps.getShortcutIconDrawable(shortcutInfo, density);
        } else {
            if (ic != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (ic.getType() == Icon.TYPE_URI
                        || ic.getType() == Icon.TYPE_URI_ADAPTIVE_BITMAP) {
                    context.grantUriPermission(context.getPackageName(),
                            ic.getUri(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                return ic.loadDrawable(context);
            }
            return null;
        }
    }

    /**
     * Creates the bitmap for the provided drawable and returns the scale used for
     * drawing the actual drawable.
     */
    public Bitmap createIconBitmap(@NonNull Drawable icon, float[] outScale) {
        if (outScale == null) {
            outScale = new float[1];
        }
        icon = normalizeAndWrapToAdaptiveIcon(icon,
                true /* shrinkNonAdaptiveIcons */,
                null /* outscale */,
                outScale);
        return createIconBitmap(icon, outScale[0], MODE_WITH_SHADOW);
    }
}
