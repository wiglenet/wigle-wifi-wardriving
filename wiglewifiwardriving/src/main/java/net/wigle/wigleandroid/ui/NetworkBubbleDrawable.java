package net.wigle.wigleandroid.ui;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import net.wigle.wigleandroid.R;

/**
 * Based entirely on MapUtils com.google.maps.android.ui.BubbleDrawable - but because that's inaccessible outside the package, we're stuck making our own copy.
 * TODO: It would be great to ween ourselves off of the 9patch bubble mask/drop shadow, but there doesn't appear to be a vector equivalent at the time of implementation.
 * @see <a href="https://github.com/googlemaps/android-maps-utils">MapUtils</a>
 */
public class NetworkBubbleDrawable extends Drawable {

    private final Drawable mShadow;
    private final Drawable mMask;
    private int mColor = -1;

    @SuppressLint("UseCompatLoadingForDrawables") //ALIBI: lifted from the original
    public NetworkBubbleDrawable(Resources res) {
        this.mMask = res.getDrawable(R.drawable.amu_bubble_mask);
        this.mShadow = res.getDrawable(R.drawable.amu_bubble_shadow);
    }
    public void setColor(int color) {
        this.mColor = color;
    }

    public void draw(@NonNull Canvas canvas) {
        this.mMask.draw(canvas);
        canvas.drawColor(this.mColor, PorterDuff.Mode.SRC_IN);
        this.mShadow.draw(canvas);
    }

    public void setAlpha(int alpha) {
        throw new UnsupportedOperationException();
    }

    public void setColorFilter(ColorFilter cf) {
        throw new UnsupportedOperationException();
    }

    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void setBounds(int left, int top, int right, int bottom) {
        this.mMask.setBounds(left, top, right, bottom);
        this.mShadow.setBounds(left, top, right, bottom);
    }

    public void setBounds(@NonNull Rect bounds) {
        this.mMask.setBounds(bounds);
        this.mShadow.setBounds(bounds);
    }

    public boolean getPadding(@NonNull Rect padding) {
        return this.mMask.getPadding(padding);
    }
}
