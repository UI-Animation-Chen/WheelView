package com.chenzhifei.wheelview;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.chenzhifei.wheelview.controller.WheelViewController;
import com.chenzhifei.wheelview.view.WheelView;

public class MainActivity extends AppCompatActivity {

    private TextView currItemValue;
    private TextView currIndexValue;

    private String[] data;

    private WheelViewController wheelViewController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WheelView wheelView = (WheelView) findViewById(R.id.wheel_view);
        currItemValue = (TextView) findViewById(R.id.tv_value_curr_item);
        currIndexValue = (TextView) findViewById(R.id.tv_value_curr_index);
        currItemValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wheelView.setData(data);
            }
        });
        currIndexValue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wheelView.setItem(5);
            }
        });

        wheelView.setStateValueListener(new WheelView.StateValueListener() {
            @Override
            public void stateValue(int currIndex, String currItem) {
                String currIndexStr = "" + currIndex;
                currIndexValue.setText(currIndexStr);
                currItemValue.setText(currItem);
            }
        });

        wheelViewController = new WheelViewController(wheelView);
        initData();
    }

    private void initData() {
        data = new String[25];
        for (int i = 0; i < 25; i++) {
            data[i] = "data-__|||TTLL" + i;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        wheelViewController.inputTouchEvent(event);
        return super.onTouchEvent(event);
    }

}