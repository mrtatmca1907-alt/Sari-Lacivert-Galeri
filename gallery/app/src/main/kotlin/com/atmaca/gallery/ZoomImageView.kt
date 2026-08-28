package com.atmaca.gallery

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

class ZoomImageView @JvmOverloads constructor(c:Context,a:AttributeSet?=null):ImageView(c,a){
    private val m=Matrix(); private var scale=1f
    private val scaler=ScaleGestureDetector(c,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){override fun onScale(d:ScaleGestureDetector):Boolean{val f=d.scaleFactor; val n=(scale*f).coerceIn(1f,5f); val real=n/scale; scale=n; m.postScale(real,real,d.focusX,d.focusY); imageMatrix=m; return true}})
    private val taps=GestureDetector(c,object:GestureDetector.SimpleOnGestureListener(){override fun onDoubleTap(e:MotionEvent):Boolean{if(scale>1f){scale=1f;m.reset()}else{scale=2.5f;m.postScale(2.5f,2.5f,e.x,e.y)};imageMatrix=m;return true}})
    init{scaleType=ScaleType.MATRIX}
    override fun onTouchEvent(e:MotionEvent):Boolean{scaler.onTouchEvent(e);taps.onTouchEvent(e);return true}
}
