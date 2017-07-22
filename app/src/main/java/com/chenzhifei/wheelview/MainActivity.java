package com.chenzhifei.wheelview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.widget.TextView;

import com.chenzhifei.wheelview.controller.WheelViewController;
import com.chenzhifei.wheelview.view.WheelView;

public class MainActivity extends AppCompatActivity {

    private TextView xValue;
    private TextView yValue;
    private TextView xDegValue;
    private TextView cameraZvalue;

    private WheelViewController wheelViewController;

    private VelocityTracker velocityTracker;
    private float moveYVelocity;
    private float upYVelocity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        xValue = (TextView) findViewById(R.id.tv_x_value);
        yValue = (TextView) findViewById(R.id.tv_y_value);
        xDegValue = (TextView) findViewById(R.id.tv_x_deg_value);
        cameraZvalue = (TextView) findViewById(R.id.tv_wheel_radius_value);

        WheelView wheelView = (WheelView) findViewById(R.id.wheel_view);
        wheelView.setStateValueListener(new WheelView.StateValueListener() {
            @Override
            public void stateValue(int currentIndex, float yVelocity, float distanceY, float xDeg, float wheelRadius) {
                String yVelocityStr = "" + yVelocity, distanceYStr = "" + distanceY + "-" + currentIndex, xDegStr = xDeg + "",
                        wheelRadiusStr = "" + wheelRadius;
                xValue.setText(yVelocityStr);
                yValue.setText(distanceYStr);
                xDegValue.setText(xDegStr);
                cameraZvalue.setText(wheelRadiusStr);
            }
        });

        wheelViewController = new WheelViewController(wheelView);

        velocityTracker = VelocityTracker.obtain();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        wheelViewController.inputTouchEvent(event);
//        switch (event.getActionMasked()) {
//            case MotionEvent.ACTION_DOWN:
//                velocityTracker.addMovement(event);
//                break;
//            case MotionEvent.ACTION_MOVE:
//                velocityTracker.addMovement(event);
//                velocityTracker.computeCurrentVelocity(1000);
//                Log.d("--------------", "moveY velocity: " + velocityTracker.getYVelocity());
//                break;
//            case MotionEvent.ACTION_UP:
//                velocityTracker.addMovement(event);
//                velocityTracker.computeCurrentVelocity(1000);
//                Log.d("--------------", "upVelocity: " + velocityTracker.getYVelocity());
//                break;
//        }
        return super.onTouchEvent(event);
    }

}