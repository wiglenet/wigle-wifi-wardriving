package net.wigle.wigleandroid.ui;

import android.app.Activity;
import android.view.View;
import android.widget.ImageView;

import androidx.fragment.app.Fragment;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import net.wigle.wigleandroid.R;
import net.wigle.wigleandroid.util.Logging;

/**
 * Loading "throbber" image parent class for network-based fragments.
 */
public abstract class ProgressThrobberFragment extends AuthenticatedFragment {
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
            final Activity a = getActivity();
            if (null == loadingAnimation && null != loadingImage && null != a) {
                loadingAnimation = AnimatedVectorDrawableCompat.create(a, R.drawable.animated_w_vis);
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
