package com.chenzhifei.wheelview.view;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by chenzhifei on 2017/6/25.
 * 使用Graphics.Camera实现WheelView的3D效果。
 */

public class WheelView extends View {

    private static final float RADIAN_TO_DEG = (float) (180.0f / Math.PI);
    private static final float DEG_TO_RADIAN = (float) (Math.PI / 180.0f);

    private static final int INTER_ITEM_DEG = 15;  // 15°
    private static final int WHEEL_VIEW_DEG = 120; // or 90°(120° - 2*15° = 90°)

    private Camera camera = new Camera(); //default location: (0f, 0f, -8.0f), in pixels: -8.0f * 72 = -576f
                                          //will NOT be changed by camera.translateZ
    private static final int cameraLocationZ = -5;
    private static final int cameraLocationZ_UNIT = 72;

    private static final float WHEEL_RADIUS = -cameraLocationZ * cameraLocationZ_UNIT *
                                             (float)Math.cos(WHEEL_VIEW_DEG / 2 * DEG_TO_RADIAN);

    private static final float DISTANCE_TO_DEG = 45f / WHEEL_RADIUS; // WHEEL_RADIUS --> 45°

    private Matrix cameraMatrix = new Matrix();
    private Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintCenterRect = new Paint();

    private int wheelViewWidth = 0;
    private int wheelViewHeight = 0;
    private int itemMaxWidth = 0;
    private int itemMaxHeight = 0;

    private String[] itemArr;

    private float distanceY = 0f; //camera.rotateX()向下为负值，向上为正值，和屏幕y坐标相反。

    private boolean isInfinity = false;
    private float yVelocityReduce = 1f; //decrease 1 pixels/second when a message is handled in the loop
                    //loop frequency is 60hz or 120hz when handleMessage(msg) includes UI update code

    private static final float MIN_VELOCITY = 50f; // pixels/second
    private float yVelocity = 0f;   // pixels/second
    private long lastDeltaMilliseconds = 0;

    private static final int RESISTANCE_FACTOR = 4; // 滑动到头时，有效滑动变为 4 分之一
    private static final float CLAMP_MAX_MIN_DELTA_DEG = 1.6f; // 1.6°
    private static final float CLAMP_NORMAL_DELTA_DEG = 0.4f; // 0.4°/16.67ms ~ 24°/s, or twice
    private float willToDeg = 0f;

    private Handler animHandler;
    private static final int MSG_NORMAL_SLIDING = 0;
    private static final int MSG_NORMAL_CLAMP = 1;
    private static final int MSG_MAX_MIN_CLAMP = 2;

    public WheelView(Context context) {
        this(context, null);
    }

    public WheelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WheelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init();
        animHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_NORMAL_SLIDING:
                        normalAnimSliding();
                        break;
                    case MSG_NORMAL_CLAMP:
                        clampItemDeg(CLAMP_NORMAL_DELTA_DEG, MSG_NORMAL_CLAMP);
                        break;
                    case MSG_MAX_MIN_CLAMP:
                        clampItemDeg(CLAMP_MAX_MIN_DELTA_DEG, MSG_MAX_MIN_CLAMP);
                        break;
                }

                return true;
            }
        });
    }

    private void clampItemDeg(float clampDeltaDeg, int clampType) {
        if (-distanceY*DISTANCE_TO_DEG < willToDeg) {// 向上滑动，到下一个item
            if (Math.abs(-distanceY* DISTANCE_TO_DEG - willToDeg) <= clampDeltaDeg) {
                distanceY = -willToDeg/ DISTANCE_TO_DEG;

                if (WheelView.this.stateValueListener != null) {
                    int currentIndex = (int)(-distanceY* DISTANCE_TO_DEG / INTER_ITEM_DEG);
                    int arrIndex = currentIndex + WHEEL_VIEW_DEG/INTER_ITEM_DEG/2;
                    WheelView.this.stateValueListener.stateValue(currentIndex, itemArr[arrIndex]);
                }

            } else {
                distanceY -= clampDeltaDeg/DISTANCE_TO_DEG;
                WheelView.this.animHandler.sendEmptyMessage(clampType);
            }

        } else if (-distanceY*DISTANCE_TO_DEG > willToDeg) {//向上滑动，回到上一个item
            if (Math.abs(-distanceY* DISTANCE_TO_DEG - willToDeg) <= clampDeltaDeg) {
                distanceY = -willToDeg/ DISTANCE_TO_DEG;

                if (WheelView.this.stateValueListener != null) {
                    int currentIndex = (int)(-distanceY* DISTANCE_TO_DEG / INTER_ITEM_DEG);
                    int arrIndex = currentIndex + WHEEL_VIEW_DEG/INTER_ITEM_DEG/2;
                    WheelView.this.stateValueListener.stateValue(currentIndex, itemArr[arrIndex]);
                }

            } else {
                distanceY += clampDeltaDeg/DISTANCE_TO_DEG;
                WheelView.this.animHandler.sendEmptyMessage(clampType);
            }

        }

        invalidate();
    }

    private void normalAnimSliding() {
        // itemArr.length-1: item --- item --- item, 3 - 1 = 2
        float maxDeg = INTER_ITEM_DEG*(itemArr.length-1) - WHEEL_VIEW_DEG;

        // 先判断再updateY，就会有溢出效果：此次事件时判断成立，updateY后下次事件就会溢出。
        if ((-distanceY*DISTANCE_TO_DEG) > maxDeg) {// 向上滑动到头并溢出
            yVelocity = 0f;

            willToDeg = maxDeg; // 向下返回到maxDeg
            WheelView.this.animHandler.sendEmptyMessage(MSG_MAX_MIN_CLAMP);

        } else if(-distanceY < 0f){ // 向下滑动到头并溢出
            yVelocity = 0f;

            willToDeg = 0f; // 向上返回到0
            WheelView.this.animHandler.sendEmptyMessage(MSG_MAX_MIN_CLAMP);

        } else {
            // 惯性滑动
            decelerationSliding();
        }
    }

    private void decelerationSliding() {
        updateY(yVelocity * lastDeltaMilliseconds / 1000f);

        if (Math.abs(yVelocity) <= MIN_VELOCITY) { // clamp item deg
            yVelocity = 0f;

            float offsetDeg = -distanceY* DISTANCE_TO_DEG % INTER_ITEM_DEG;
            float clampOffsetDeg = offsetDeg >= INTER_ITEM_DEG /2 ? INTER_ITEM_DEG - offsetDeg : -offsetDeg;

            int index = (int)(-distanceY* DISTANCE_TO_DEG / INTER_ITEM_DEG);
            willToDeg = clampOffsetDeg > 0f ? (index+1) * INTER_ITEM_DEG : index * INTER_ITEM_DEG;

            WheelView.this.animHandler.sendEmptyMessage(MSG_NORMAL_CLAMP);
            return;
        }

        if (WheelView.this.isInfinity) {
            animHandler.sendEmptyMessage(MSG_NORMAL_SLIDING);

        } else {
            // decrease the velocities.
            // 'Math.abs(yVelocity) <= yVelocityReduce' make sure the yVelocity will be 0 finally.
            yVelocity = Math.abs(yVelocity) <= yVelocityReduce ? 0f :
                    (yVelocity > 0 ? yVelocity - yVelocityReduce : yVelocity + yVelocityReduce);

            animHandler.sendEmptyMessage(MSG_NORMAL_SLIDING);
        }
    }

    private void init() {
        initData();

        camera.setLocation(0, 0, cameraLocationZ);

        setPaintText(24f);
        paintCenterRect.setColor(Color.parseColor("#77000000"));
        paintCenterRect.setStrokeWidth(4);
    }

    private void initData() {
        int extra = WHEEL_VIEW_DEG / INTER_ITEM_DEG;
        itemArr = new String[100 + extra];
        for (int i = 0; i < extra/2; i++) {
            itemArr[i] = "";
        }
        for (int i = extra/2; i < itemArr.length - extra/2; i++) {
            itemArr[i] = "chenzhifei" + (i - extra/2);
        }
        for (int i = itemArr.length - extra/2; i < itemArr.length; i++) {
            itemArr[i] = "";
        }
    }

    public void setPaintText(float textSize) {
        paintText.setTextSize(textSize);
        getMaxItemSize();
    }

    private void getMaxItemSize() {
        Rect textRect = new Rect();
        for (String item : itemArr) {
            paintText.getTextBounds(item, 0, item.length(), textRect);
            if (textRect.width() > itemMaxWidth) {
                itemMaxWidth = textRect.width();
                itemMaxHeight = textRect.height();
            }
        }
    }

    public void setYVelocityReduce(float yVelocityReduce) {
        if (yVelocityReduce <= 0f) {
            this.isInfinity = true;
            this.yVelocityReduce = 0f;
        } else {
            this.isInfinity = false;
            this.yVelocityReduce = yVelocityReduce;
        }
    }

    public void updateY(float movedY) {
        if (-distanceY* DISTANCE_TO_DEG > INTER_ITEM_DEG *(itemArr.length-1) - WHEEL_VIEW_DEG
                || -distanceY < 0f) {

            movedY /= RESISTANCE_FACTOR;
        }
        distanceY += movedY;

        invalidate();
    }

    public void stopAnim() {
        animHandler.removeCallbacksAndMessages(null);
    }

    public void startAnim(long lastDeltaMilliseconds, float yVelocity) {
        this.lastDeltaMilliseconds = lastDeltaMilliseconds;
        this.yVelocity = yVelocity;

        animHandler.sendEmptyMessage(MSG_NORMAL_SLIDING);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        wheelViewWidth = w; //params value is in pixels not dp
        wheelViewHeight = h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // translate canvas in order to locate the maxItem in the center of the WheelViwe
        canvas.translate((wheelViewWidth - itemMaxWidth) / 2f, (wheelViewHeight - itemMaxHeight) / 2f);

        drawWheelText(canvas);

        drawCenterRect(canvas);
    }

    private void drawWheelText(Canvas canvas) {
        float accumDeg = -distanceY * DISTANCE_TO_DEG;
        float driveDeg = accumDeg % INTER_ITEM_DEG; // 0 ~ 15，当向下滑动到头是，会变为负值。

        /**
         * 每转动15度循环一次。
         *   不显示   60   45   30   15    0   -15  -30  -45  -60  不显示
         *  ----------\----\----\----\----\----\----\----\----\----------
         *   初始化    !    \    \    \    \    \    \    \    !    7个   0    !：不显示
         *   驱动范围：
         *           <--  \    \    \    \    \    \    \    \     8个   1
         *          <--  \    \    \    \    \    \    \    \      8个   2
         *         <--  \    \    \    \    \    \    \    \       8个   3
         *        <--  \    \    \    \    \    \    \    \        8个   4
         *   重复：
         *       <--  !    \    \    \    \    \    \    \    !    8个   0
         *           <--  \    \    \    \    \    \    \    \     8个   1
         *           ... ...
         */
        driveDeg += WHEEL_VIEW_DEG / 2; // 60 ~ 75
        // 循环8次
        for (int i = 1, length = WHEEL_VIEW_DEG/INTER_ITEM_DEG; i <= length; i++) {
            setCmaraMatrixAtIndex(driveDeg - i * INTER_ITEM_DEG);
            drawTextAtIndex(canvas, (int)(accumDeg / INTER_ITEM_DEG) + i);
        }
    }

    private void setCmaraMatrixAtIndex(float deg) {
        cameraMatrix.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(deg); // it will lead to rotate Y and Z axis
//        camera.rotateZ(10f);              // it will NOT lead to rotate X axis
        camera.translate(0f, 0f, -WHEEL_RADIUS);
        camera.getMatrix(cameraMatrix);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin the camera's transformation depends on to center of the bitmap
        cameraMatrix.preTranslate(-(itemMaxWidth / 2), -(itemMaxHeight / 2));
        cameraMatrix.postTranslate(itemMaxWidth / 2, itemMaxHeight / 2);
    }

    private void drawTextAtIndex(Canvas canvas, int index) {
        if (index >= itemArr.length) {
            index = itemArr.length - 1;
        } else if (index < 0) {
            index = 0;
        }
        canvas.save();
        canvas.concat(cameraMatrix);
        canvas.drawText(itemArr[index], 0, itemMaxHeight, paintText);
        canvas.restore();
    }

    private void drawCenterRect(Canvas canvas) {
        canvas.drawLine(0f, -1.8f* itemMaxHeight, itemMaxWidth, -1.8f* itemMaxHeight, paintCenterRect);
        canvas.drawLine(0f, 2.8f* itemMaxHeight, itemMaxWidth, 2.8f* itemMaxHeight, paintCenterRect);
        // draw vertical radius
//        canvas.drawLine(itemMaxWidth / 2f, itemMaxHeight / 2f, itemMaxWidth / 2f, itemMaxHeight / 2f - WHEEL_RADIUS, paintCenterRect);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animHandler != null) {
            animHandler.removeCallbacksAndMessages(null);
        }
    }

    public interface StateValueListener {
        void stateValue(int currentIndex, String item);
    }

    private StateValueListener stateValueListener;

    public void setStateValueListener(StateValueListener stateValueListener) {
        this.stateValueListener = stateValueListener;
    }
}
