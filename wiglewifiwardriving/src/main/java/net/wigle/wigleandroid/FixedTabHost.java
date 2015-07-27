package net.wigle.wigleandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TabHost;

public class FixedTabHost extends TabHost {
  public FixedTabHost(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public FixedTabHost(Context context) {
    super(context);
  }

  @Override
  public void dispatchWindowFocusChanged(boolean hasFocus) {
    // API LEVEL <7 occasionally throws a NPE here
    if(getCurrentView() != null){
        super.dispatchWindowFocusChanged(hasFocus);
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    // API LEVEL <7 occasionally throws a NPE here
    try {
      super.dispatchDraw(canvas);
    }
    catch (Exception ignored) {
      MainActivity.info("Ignored exception: " + ignored, ignored);
    }
  }
}
