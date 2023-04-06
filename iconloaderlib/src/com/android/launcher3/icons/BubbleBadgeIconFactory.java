package com.android.launcher3.icons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

/**
 * Factory for creating app badge icons that are shown on bubbles.
 */
public class BubbleBadgeIconFactory extends BaseIconFactory {

    final int mRingColor;
    final int mRingWidth;

    public BubbleBadgeIconFactory(Context context, int badgeSize, int ringColor, int ringWidth) {
        super(context, context.getResources().getConfiguration().densityDpi, badgeSize);
        mRingColor = ringColor;
        mRingWidth = ringWidth;
    }

    /**
     * Returns a {@link BitmapInfo} for the app-badge that is shown on top of each bubble. This
     * will include the workprofile indicator on the badge if appropriate.
     */
    public BitmapInfo getBadgeBitmap(Drawable userBadgedAppIcon, boolean isImportantConversation) {
        if (userBadgedAppIcon instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) userBadgedAppIcon;
            userBadgedAppIcon = new BubbleBadgeIconFactory.CircularAdaptiveIcon(ad.getBackground(),
                    ad.getForeground());
        }
        if (isImportantConversation) {
            userBadgedAppIcon = new BubbleBadgeIconFactory.CircularRingDrawable(userBadgedAppIcon);
        }
        Bitmap userBadgedBitmap = createIconBitmap(
                userBadgedAppIcon, 1, MODE_WITH_SHADOW);
        return createIconBitmap(userBadgedBitmap);
    }

    private class CircularRingDrawable extends BubbleBadgeIconFactory.CircularAdaptiveIcon {
        final Rect mInnerBounds = new Rect();

        final Drawable mDr;

        CircularRingDrawable(Drawable dr) {
            super(null, null);
            mDr = dr;
        }

        @Override
        public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(getIconMask());
            canvas.drawColor(mRingColor);
            mInnerBounds.set(getBounds());
            mInnerBounds.inset(mRingWidth, mRingWidth);
            canvas.translate(mInnerBounds.left, mInnerBounds.top);
            mDr.setBounds(0, 0, mInnerBounds.width(), mInnerBounds.height());
            mDr.draw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private static class CircularAdaptiveIcon extends AdaptiveIconDrawable {

        final Path mPath = new Path();

        CircularAdaptiveIcon(Drawable bg, Drawable fg) {
            super(bg, fg);
        }

        @Override
        public Path getIconMask() {
            mPath.reset();
            Rect bounds = getBounds();
            mPath.addOval(bounds.left, bounds.top, bounds.right, bounds.bottom, Path.Direction.CW);
            return mPath;
        }

        @Override
        public void draw(Canvas canvas) {
            int save = canvas.save();
            canvas.clipPath(getIconMask());

            Drawable d;
            if ((d = getBackground()) != null) {
                d.draw(canvas);
            }
            if ((d = getForeground()) != null) {
                d.draw(canvas);
            }
            canvas.restoreToCount(save);
        }
    }
}
