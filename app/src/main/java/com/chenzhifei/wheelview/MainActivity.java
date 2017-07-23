package com.chenzhifei.wheelview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.TextView;

import com.chenzhifei.wheelview.controller.WheelViewController;
import com.chenzhifei.wheelview.view.WheelView;

public class MainActivity extends AppCompatActivity {

    private TextView curItemValue;
    private TextView curIndexValue;

    private WheelViewController wheelViewController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        curItemValue = (TextView) findViewById(R.id.tv_value_cur_item);
        curIndexValue = (TextView) findViewById(R.id.tv_value_cur_index);

        WheelView wheelView = (WheelView) findViewById(R.id.wheel_view);
        wheelView.setStateValueListener(new WheelView.StateValueListener() {
            @Override
            public void stateValue(int currentIndex, String item) {
                String currentIndexStr = "" + currentIndex;
                curIndexValue.setText(currentIndexStr);
                curItemValue.setText(item);
            }
        });

        wheelViewController = new WheelViewController(wheelView);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        wheelViewController.inputTouchEvent(event);
        return super.onTouchEvent(event);
    }

}