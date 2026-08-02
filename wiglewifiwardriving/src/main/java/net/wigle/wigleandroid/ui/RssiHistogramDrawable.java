package net.wigle.wigleandroid.ui;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.wigle.wigleandroid.model.RssiSample;
import net.wigle.wigleandroid.util.RssiHistoryCache;

import java.util.Collections;
import java.util.List;

/**
 * Faint filled sparkline of recent RSSI vs time for list-row backgrounds.
 * X = last {@link RssiHistoryCache#WINDOW_MS}; Y = fixed dBm scale.
 */
public final class RssiHistogramDrawable extends Drawable {
    private static final int MIN_DBM = -100;
    private static final int MAX_DBM = -30;
    /** Area fill under the sparkline (slightly dimmer than the top edge). */
    private static final int FILL_ALPHA = 0x1C;
    /** Top edge stroke — a bit lighter so the silhouette reads clearly. */
    private static final int STROKE_ALPHA = 0x48;
    private static final float STROKE_WIDTH_DP = 1.25f;
    /** Quantize window end so rapid list rebinds share one skip key. */
    private static final long WINDOW_BUCKET_MS = 250L;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fillPath = new Path();
    private final Path strokePath = new Path();

    private List<RssiSample> samples = Collections.emptyList();
    private long windowEndMs;
    private int lastCount = -1;
    private long lastNewestMs = Long.MIN_VALUE;
    private int lastWidth = -1;
    private int lastHeight = -1;
    private boolean pathDirty = true;

    public RssiHistogramDrawable(final int opaqueColor) {
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(withAlpha(opaqueColor, FILL_ALPHA));

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(STROKE_WIDTH_DP * Resources.getSystem().getDisplayMetrics().density);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setColor(withAlpha(opaqueColor, STROKE_ALPHA));
    }

    public void setFillColor(final int opaqueColor) {
        fillPaint.setColor(withAlpha(opaqueColor, FILL_ALPHA));
        strokePaint.setColor(withAlpha(opaqueColor, STROKE_ALPHA));
        invalidateSelf();
    }

    /**
     * Update series. Skips work when samples and bucketed window end are unchanged.
     */
    public void setSamples(@Nullable final List<RssiSample> newSamples, final long nowMs) {
        final List<RssiSample> next = (newSamples == null || newSamples.isEmpty())
                ? Collections.emptyList() : newSamples;
        final long newest = next.isEmpty() ? Long.MIN_VALUE : next.get(next.size() - 1).timeMs;
        final long windowMs = (nowMs / WINDOW_BUCKET_MS) * WINDOW_BUCKET_MS;
        if (next.size() == lastCount && newest == lastNewestMs
                && windowMs == windowEndMs && !pathDirty) {
            return;
        }
        samples = next;
        windowEndMs = windowMs;
        lastCount = next.size();
        lastNewestMs = newest;
        pathDirty = true;
        invalidateSelf();
    }

    public void clear() {
        if (samples.isEmpty() && lastCount == 0) {
            return;
        }
        samples = Collections.emptyList();
        lastCount = 0;
        lastNewestMs = Long.MIN_VALUE;
        pathDirty = true;
        fillPath.reset();
        strokePath.reset();
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull final Canvas canvas) {
        final Rect bounds = getBounds();
        final int width = bounds.width();
        final int height = bounds.height();
        if (width <= 0 || height <= 0 || samples.isEmpty()) {
            return;
        }
        if (pathDirty || width != lastWidth || height != lastHeight) {
            rebuildPaths(bounds);
            lastWidth = width;
            lastHeight = height;
            pathDirty = false;
        }
        if (!fillPath.isEmpty()) {
            canvas.drawPath(fillPath, fillPaint);
        }
        if (!strokePath.isEmpty()) {
            canvas.drawPath(strokePath, strokePaint);
        }
    }

    private void rebuildPaths(final Rect bounds) {
        fillPath.reset();
        strokePath.reset();
        final int n = samples.size();
        if (n == 0) {
            return;
        }
        final float left = bounds.left;
        final float top = bounds.top;
        final float width = bounds.width();
        final float height = bounds.height();
        final float bottom = bounds.bottom;
        final long windowStart = windowEndMs - RssiHistoryCache.WINDOW_MS;

        fillPath.moveTo(left, bottom);
        for (int i = 0; i < n; i++) {
            final RssiSample sample = samples.get(i);
            final float x = left + xForTime(sample.timeMs, windowStart, width);
            final float y = top + yForLevel(sample.level, height);
            if (i == 0) {
                fillPath.lineTo(x, bottom);
                strokePath.moveTo(x, y);
            } else {
                strokePath.lineTo(x, y);
            }
            fillPath.lineTo(x, y);
        }
        final RssiSample last = samples.get(n - 1);
        final float lastX = left + xForTime(last.timeMs, windowStart, width);
        fillPath.lineTo(lastX, bottom);
        fillPath.close();
    }

    private static float xForTime(final long timeMs, final long windowStart, final float width) {
        final long age = timeMs - windowStart;
        if (age <= 0L) {
            return 0f;
        }
        if (age >= RssiHistoryCache.WINDOW_MS) {
            return width;
        }
        return (age / (float) RssiHistoryCache.WINDOW_MS) * width;
    }

    private static float yForLevel(final int level, final float height) {
        final int clamped = Math.max(MIN_DBM, Math.min(MAX_DBM, level));
        final float frac = (clamped - MIN_DBM) / (float) (MAX_DBM - MIN_DBM);
        // Stronger signal → higher on screen (smaller y)
        return height * (1f - frac);
    }

    private static int withAlpha(final int opaqueColor, final int alpha) {
        return (alpha << 24) | (opaqueColor & 0x00FFFFFF);
    }

    @Override
    public void setAlpha(final int alpha) {
        fillPaint.setAlpha((alpha * FILL_ALPHA) / 255);
        strokePaint.setAlpha((alpha * STROKE_ALPHA) / 255);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable final ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
