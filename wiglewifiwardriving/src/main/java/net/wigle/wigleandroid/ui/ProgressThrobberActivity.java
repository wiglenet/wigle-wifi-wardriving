package net.wigle.wigleandroid.ui;

import android.view.View;
import android.widget.ImageView;

import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.Logging;

/**
 * Loading "throbber" image parent class for network-based fragments.
 */
public abstract class ProgressThrobberActivity extends ScreenChildActivity {
    protected ImageView loadingImage;
    protected ImageView errorImage;
    protected boolean animating = false;
    protected AnimatedVectorDrawableCompat loadingAnimation = null;

    protected void startAnimation() {
        if (null != errorImage) {
            errorImage.setVisibility(View.GONE);
        }
        if (null != loadingImage) {
            loadingImage.setVisibility(View.VISIBLE);
        }
        if (!animating) {
            animating = true;
            if (null == loadingAnimation && null != loadingImage) {
                loadingAnimation = AnimatedVectorDrawableCompat.create(this, R.drawable.animated_w_vis);
                loadingImage.setImageDrawable(loadingAnimation);
                loadingImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
            if (null != loadingAnimation) {
                loadingAnimation.start();
            }
        } else {
            Logging.warn("tried to start animation when animation was already going on");
        }
    }

    protected void stopAnimation() {
        if (animating) {
            animating = false;
            if (null != loadingAnimation) {
                loadingAnimation.stop();
            }
        }
        if (null != loadingImage) {
            loadingImage.setVisibility(View.GONE);
        }
    }

    protected void showError() {
        if (null != errorImage) {
            errorImage.setVisibility(View.VISIBLE);
        }
    }
    protected void hideError() {
        if (null != errorImage) {
            errorImage.setVisibility(View.GONE);
        }
    }

}
