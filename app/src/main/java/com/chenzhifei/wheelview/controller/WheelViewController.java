package com.chenzhifei.wheelview.controller;

import android.view.MotionEvent;

import com.chenzhifei.wheelview.gesture.TwoFingersGestureDetector;
import com.chenzhifei.wheelview.view.WheelView;

/**
 * Created by chenzhifei on 2017/6/30.
 * control the WheelView
 */

public class WheelViewController {

    private WheelView wheelView;

    private TwoFingersGestureDetector twoFingersGestureDetector;

    public WheelViewController(WheelView wheelView) {
        this.wheelView = wheelView;
        this.wheelView.setYVelocityReduce(50f);

        twoFingersGestureDetector = new TwoFingersGestureDetector();
        twoFingersGestureDetector.setTwoFingersGestureListener(new TwoFingersGestureDetector.TwoFingersGestureListener() {
            @Override
            public void onDown(float downX, float downY, long downTime) {
                WheelViewController.this.wheelView.stopAnim();
            }

            @Override
            public void onMoved(float deltaMovedX, float deltaMovedY, long deltaMilliseconds) {
                WheelViewController.this.wheelView.updateY(deltaMovedY);
            }

            @Override
            public void onRotated(float deltaRotatedDeg, long deltaMilliseconds) {
            }

            @Override
            public void onScaled(float deltaScaledX, float deltaScaledY, float deltaScaledDistance, long deltaMilliseconds) {
            }

            @Override
            public void onUp(float upX, float upY, long upTime, float xVelocity, float yVelocity) {

                WheelViewController.this.wheelView.startAnim(yVelocity);
            }

            @Override
            public void onCancel() {
                WheelViewController.this.wheelView.startAnim(0);
            }
        });
    }

    public void inputTouchEvent(MotionEvent event) {
        twoFingersGestureDetector.onTouchEvent(event);
    }

}
