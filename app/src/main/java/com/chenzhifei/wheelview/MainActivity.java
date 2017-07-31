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

    private int textAlign = WheelView.TEXT_ALIGN_CENTER;

    private WheelViewController wheelViewController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WheelView wheelView = (WheelView) findViewById(R.id.wheel_view);
        currItemValue = (TextView) findViewById(R.id.tv_value_curr_item);
        currIndexValue = (TextView) findViewById(R.id.tv_value_curr_index);
        findViewById(R.id.btn_set_data).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wheelView.setData(data);
            }
        });
        findViewById(R.id.btn_set_item).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wheelView.setItem(5);
            }
        });
        findViewById(R.id.btn_set_text_size).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wheelView.setWheelTextSize(40);
            }
        });
        findViewById(R.id.btn_switch_text_align).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (textAlign) {
                    case WheelView.TEXT_ALIGN_CENTER:
                        textAlign = WheelView.TEXT_ALIGN_RIGHT;
                        break;
                    case WheelView.TEXT_ALIGN_LEFT:
                        textAlign = WheelView.TEXT_ALIGN_CENTER;
                        break;
                    case WheelView.TEXT_ALIGN_RIGHT:
                        textAlign = WheelView.TEXT_ALIGN_LEFT;
                        break;
                }
                wheelView.setWheelTextAlign(textAlign);
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
//        wheelView.setData(data);
//        wheelView.setItem(6);
    }

    private void initData() {
        data = new String[100];
        for (int i = 0; i < 100; i++) {
            data[i] = "data-__|||TTLL" + i;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        wheelViewController.inputTouchEvent(event);
        return super.onTouchEvent(event);
    }

}