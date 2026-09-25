package com.atmaca.hiosfilemanager;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.VideoView;

public class AspectVideoView extends VideoView {
    private int videoWidth;
    private int videoHeight;

    public AspectVideoView(Context context) { super(context); }
    public AspectVideoView(Context context, AttributeSet attrs) { super(context, attrs); }
    public AspectVideoView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    public void setVideoSize(int width, int height) {
        videoWidth = width;
        videoHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int parentW = MeasureSpec.getSize(widthMeasureSpec);
        int parentH = MeasureSpec.getSize(heightMeasureSpec);

        if (videoWidth <= 0 || videoHeight <= 0 || parentW <= 0 || parentH <= 0) {
            setMeasuredDimension(parentW, parentH);
            return;
        }

        float videoRatio = (float) videoWidth / (float) videoHeight;
        float parentRatio = (float) parentW / (float) parentH;

        int w, h;
        if (parentRatio > videoRatio) {
            h = parentH;
            w = Math.round(h * videoRatio);
        } else {
            w = parentW;
            h = Math.round(w / videoRatio);
        }
        setMeasuredDimension(w, h);
    }
}
