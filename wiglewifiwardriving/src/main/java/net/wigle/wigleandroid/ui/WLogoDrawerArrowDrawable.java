package net.wigle.wigleandroid.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import net.wigle.wigleandroid.R;

/**
 * {@link DrawerArrowDrawable} variant that paints a faint WiGLE "W" logo
 * outline ({@code R.drawable.ic_w_logo_simple_mono_outline}) behind the
 * animated hamburger / back-arrow indicator.
 *
 * <p>The watermark is stroke-only (no fill) and tinted with the theme's
 * {@code UploadColor} text color so it tracks light/dark (and API-level)
 * UploadColor variants independently of the hamburger/arrow color.
 *
 * <p>The watermark also fades out as the drawer opens: at
 * {@link #setProgress(float)} progress {@code 0} (drawer fully closed, plain
 * hamburger) the watermark is at its maximum alpha; at progress {@code 1}
 * (drawer fully open, back-arrow morph) the watermark is invisible. This keeps
 * the arrow reading crisply while the user is navigating.</p>
 */
public class WLogoDrawerArrowDrawable extends DrawerArrowDrawable {

    /**
     * Maximum alpha (0-255) for the watermark when the drawer is fully closed.
     * ~56% of 255 &mdash; faint enough to feel like a watermark, bright enough
     * to be recognizable. Tune here if you want more/less prominence.
     */
    private static final int WATERMARK_MAX_ALPHA = 143;

    /**
     * Scale applied to the DrawerArrowDrawable's own bounds when laying out
     * the watermark. The AppCompat home indicator is ~24dp inside a ~48dp
     * touch target with a ~40dp circular ripple/highlight; scaling by ~1.67
     * makes the W watermark roughly fill that highlight ring while the
     * hamburger/arrow renders at its normal size on top.
     */
    private static final float WATERMARK_SCALE = 1.67f;

    /** Fallback matching values/styles.xml UploadColor when style resolve fails. */
    private static final int UPLOAD_COLOR_FALLBACK = 0xFFEABA44;

    private final Drawable watermark;
    private final Rect watermarkBounds = new Rect();
    private final int uploadColor;

    public WLogoDrawerArrowDrawable(@NonNull final Context context) {
        super(context);
        this.uploadColor = resolveUploadColor(context);
        // Stroke-only outline variant; mutate() so tint/alpha stay local.
        final Drawable src = ContextCompat.getDrawable(context, R.drawable.ic_w_logo_simple_mono_outline);
        this.watermark = (src != null) ? src.mutate() : null;
        if (this.watermark != null) {
            DrawableCompat.setTint(this.watermark, uploadColor);
            this.watermark.setAlpha(WATERMARK_MAX_ALPHA);
        }
    }

    private static int resolveUploadColor(@NonNull final Context context) {
        final TypedArray a = context.obtainStyledAttributes(
                R.style.UploadColor, new int[]{android.R.attr.textColor});
        try {
            return a.getColor(0, UPLOAD_COLOR_FALLBACK);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        // Paint the watermark first, so the hamburger/arrow renders on top.
        if (watermark != null) {
            final Rect base = getBounds();
            final int cx = base.centerX();
            final int cy = base.centerY();
            final int halfW = Math.round((base.width() * WATERMARK_SCALE) / 2f);
            final int halfH = Math.round((base.height() * WATERMARK_SCALE) / 2f);
            watermarkBounds.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH);
            watermark.setBounds(watermarkBounds);
            watermark.draw(canvas);
        }
        super.draw(canvas);
    }

    @Override
    public void setProgress(final float progress) {
        super.setProgress(progress);
        if (watermark != null) {
            // Fade watermark linearly to 0 as the drawer opens.
            final float clamped = Math.max(0f, Math.min(1f, progress));
            final int alpha = Math.round(WATERMARK_MAX_ALPHA * (1f - clamped));
            watermark.setAlpha(alpha);
            // super.setProgress() already invalidates, but be explicit so a
            // color/tint change flushes cleanly.
            invalidateSelf();
        }
    }

}
