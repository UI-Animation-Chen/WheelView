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

    private static final float RADIAN_TO_DEGREE = (float) (180.0f / Math.PI);
    private static final float DEGREE_TO_RADIAN = (float) (Math.PI / 180.0f);

    private static final float INTER_ITEM_DEGREE = 15f;
    private static final float WHEEL_VIEW_DEGREE = 120f; // or 90(120 - 2*15)

    private static final float cameraLocationZ = -5;
    private static final float cameraLocationZ_UNIT = 72;

    private int wheelViewWidth = 0;
    private int wheelViewHeight = 0;
    private int itemMaxWidth = 0;
    private int itemMaxHeight = 0;

    private String[] itemArr;

    private Camera camera = new Camera(); //default location: (0f, 0f, -8.0f), in pixels: -8.0f * 72 = -576f
                                          //will NOT be changed by camera.translateZ
    private Matrix cameraMatrix = new Matrix();
    private Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintCenterRect = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float distanceY = 0f; //camera.rotateX()向下为负值，向上为正值，和屏幕y坐标相反。
    private float wheelRadius;

    private float distanceToDegree; // wheelRadius --> 90°

    private boolean isInfinity = false;
    private float yVelocityReduce = 1f; //decrease 1 pixels/second when a message is handled in the loop
                    //loop frequency is 60hz or 120hz when handleMessage(msg) includes UI update code
    private static final float MIN_VELOCITY = 100f;
    private float yVelocity = 0f;
    private long lastDeltaMilliseconds = 0;
    private static final int resistanceFactor = 4; // 滑动到头时，有效滑动变为 4 分之一
    private static final float CLAMP_MAX_MIN_DELTA_DEG = 5.0f * resistanceFactor; // 5.0°
    private static final float CLAMP_NORMAL_DELTA_DEG = 0.3f; // 0.3°
    private float willToDeg = 0f;

    private Handler animHandler;
    private static final int MSG_NORMAL = 0;
    private static final int MSG_CLAMP = 1;

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
                final float maxDeg = INTER_ITEM_DEGREE*(itemArr.length-1) - WHEEL_VIEW_DEGREE;

                switch (msg.what) {
                    case MSG_NORMAL:
                        normalAnimSliding(maxDeg);
                        break;
                    case MSG_CLAMP:
                        clampItemDeg();
                        break;
                }

                return true;
            }
        });
    }

    private void clampMaxMinLastDeg(float maxDeg) {
        // 向上或向下溢出时，这里只处理最后一个delta。
        if (-distanceY >= 0f && -distanceY <= CLAMP_MAX_MIN_DELTA_DEG /distanceToDegree) { //下滑溢出时，校准到0
            distanceY = 0f;

        } else if (-distanceY*distanceToDegree <= maxDeg
                && -distanceY*distanceToDegree >= maxDeg - CLAMP_MAX_MIN_DELTA_DEG) {  //上滑溢出时校准到maxDeg
            distanceY = -maxDeg/distanceToDegree;
        }

        WheelView.this.invalidate();
        if (WheelView.this.stateValueListener != null) {
            int currentIndex = (int)(-distanceY*distanceToDegree/INTER_ITEM_DEGREE);
            WheelView.this.stateValueListener.stateValue(currentIndex, yVelocity, -distanceY, -distanceY * distanceToDegree, wheelRadius);
        }
    }

    private void clampItemDeg() {
        if (-distanceY*distanceToDegree < willToDeg) {// 向上滑动，到下一个item
            if (Math.abs(-distanceY*distanceToDegree - willToDeg) <= CLAMP_NORMAL_DELTA_DEG) {
                distanceY = -willToDeg/distanceToDegree;
            } else {
                distanceY -= CLAMP_NORMAL_DELTA_DEG /distanceToDegree;
                WheelView.this.animHandler.sendEmptyMessage(MSG_CLAMP);
            }

        } else if (-distanceY*distanceToDegree > willToDeg) {//向上滑动，回到上一个item
            if (Math.abs(-distanceY*distanceToDegree - willToDeg) <= CLAMP_NORMAL_DELTA_DEG) {
                distanceY = -willToDeg/distanceToDegree;
            } else {
                distanceY += CLAMP_NORMAL_DELTA_DEG /distanceToDegree;
                WheelView.this.animHandler.sendEmptyMessage(MSG_CLAMP);
            }

        }

        invalidate();
        if (WheelView.this.stateValueListener != null) {
            int currentIndex = (int)(-distanceY*distanceToDegree/INTER_ITEM_DEGREE);
            WheelView.this.stateValueListener.stateValue(currentIndex, yVelocity, -distanceY, -distanceY * distanceToDegree, wheelRadius);
        }
    }

    private void normalAnimSliding(float maxDeg) {
        // 先判断再updateY，就会有溢出效果：判断成立，updateY后溢出。
        // itemArr.length - 1: item --- item --- item --- item, 4 - 1 = 3
        if ((-distanceY*distanceToDegree) > maxDeg) {// 向上滑动到头并溢出
            yVelocity = -1f;
            // 向下滑回到maxDeg
            updateY(CLAMP_MAX_MIN_DELTA_DEG/distanceToDegree, yVelocity);
            WheelView.this.sendMsgForAnim();

        } else if(-distanceY < 0f){ // 向下滑动到头并溢出
            yVelocity = -1f;
            // 向上滑回到0
            updateY(-CLAMP_MAX_MIN_DELTA_DEG/distanceToDegree, yVelocity);
            WheelView.this.sendMsgForAnim();

        } else {
            if (yVelocity == -1f) {  // 只处理上滑或下滑溢出情况下的最后一个delta clamp
                clampMaxMinLastDeg(maxDeg);

            } else { // 惯性滑动
                decelerationSliding();
            }
        }
    }

    private void decelerationSliding() {
        updateY(yVelocity * lastDeltaMilliseconds / 1000f, yVelocity);

        if (Math.abs(yVelocity) <= MIN_VELOCITY) { // clamp item deg
            float offsetDeg = -distanceY*distanceToDegree % INTER_ITEM_DEGREE;
            float clampOffsetDeg = offsetDeg >= INTER_ITEM_DEGREE/2 ? INTER_ITEM_DEGREE - offsetDeg : -offsetDeg;

            int index = (int)(-distanceY*distanceToDegree / INTER_ITEM_DEGREE);
            willToDeg = clampOffsetDeg > 0f ? (index+1) * INTER_ITEM_DEGREE : index * INTER_ITEM_DEGREE;

            WheelView.this.animHandler.sendEmptyMessage(MSG_CLAMP);
            return;
        }

        if (WheelView.this.isInfinity) {
            WheelView.this.sendMsgForAnim();

        } else {
            // decrease the velocities.
            // 'Math.abs(yVelocity) <= yVelocityReduce' make sure the yVelocity will be 0 finally.
            yVelocity = Math.abs(yVelocity) <= yVelocityReduce ? 0f :
                    (yVelocity > 0 ? yVelocity - yVelocityReduce : yVelocity + yVelocityReduce);

            WheelView.this.sendMsgForAnim();
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
        int extra = (int)(WHEEL_VIEW_DEGREE / INTER_ITEM_DEGREE);
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

    public void updateY(float movedY, float yVelocity) {
        this.yVelocity = yVelocity;
        if (-distanceY*distanceToDegree > INTER_ITEM_DEGREE*(itemArr.length-1) - WHEEL_VIEW_DEGREE
                || -distanceY < 0f) {
            movedY /= resistanceFactor;
        }
        distanceY += movedY;
        invalidate();
        if (WheelView.this.stateValueListener != null) {
            int currentIndex = (int)(-distanceY*distanceToDegree/INTER_ITEM_DEGREE);
            WheelView.this.stateValueListener.stateValue(currentIndex, yVelocity, -distanceY, -distanceY * distanceToDegree, wheelRadius);
        }
    }

    public void updateCameraZtranslate(float cameraZtranslate) {
        this.wheelRadius += cameraZtranslate;
        invalidate();
        if (WheelView.this.stateValueListener != null) {
            int currentIndex = (int)(-distanceY*distanceToDegree/INTER_ITEM_DEGREE);
            WheelView.this.stateValueListener.stateValue(currentIndex, yVelocity, -distanceY, -distanceY * distanceToDegree, wheelRadius);
        }
    }

    private void sendMsgForAnim() {
        animHandler.sendEmptyMessage(MSG_NORMAL);
    }

    public void stopAnim() {
        animHandler.removeCallbacksAndMessages(null);
    }

    public void startAnim(long lastDeltaMilliseconds, float yVelocity) {
        this.lastDeltaMilliseconds = lastDeltaMilliseconds;
        this.yVelocity = yVelocity;

        sendMsgForAnim();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        wheelViewWidth = w; //params value is in pixels not dp
        wheelViewHeight = h;
        wheelRadius = -cameraLocationZ_UNIT * cameraLocationZ * (float)Math.cos(WHEEL_VIEW_DEGREE / 2 * DEGREE_TO_RADIAN);
        distanceToDegree = 90f / wheelRadius;//NOT changed when wheelRadius changed in the future
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // translate canvas in order to locate the maxItem in the center of the WheelViwe
        canvas.translate((wheelViewWidth - itemMaxWidth) / 2f, (wheelViewHeight - itemMaxHeight) / 2f);

        drawWheelText(canvas);

        drawCenterRect(canvas);
    }

    private void drawWheelText(Canvas canvas) {
        float accumDeg = -distanceY * distanceToDegree;
        float driveDeg = accumDeg % INTER_ITEM_DEGREE; // 0 ~ 15

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
        driveDeg += WHEEL_VIEW_DEGREE / 2; // 60 ~ 75
        // 循环8次
        for (int i = 1, length = (int)(WHEEL_VIEW_DEGREE / INTER_ITEM_DEGREE); i <= length; i++) {
            setCmaraMatrixAtIndex(driveDeg - i * INTER_ITEM_DEGREE);
            drawTextAtIndex(canvas, (int)(accumDeg / INTER_ITEM_DEGREE) + i);
        }
    }

    private void setCmaraMatrixAtIndex(float deg) {
        cameraMatrix.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(deg); // it will lead to rotate Y and Z axis
//        camera.rotateZ(10f);              // it will NOT lead to rotate X axis
        camera.translate(0f, 0f, -wheelRadius);
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
//        canvas.drawLine(itemMaxWidth / 2f, itemMaxHeight / 2f, itemMaxWidth / 2f, itemMaxHeight / 2f - wheelRadius, paintCenterRect);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animHandler != null) {
            animHandler.removeCallbacksAndMessages(null);
        }
    }

    public interface StateValueListener {
        void stateValue(int currentIndex, float yVelocity, float distanceY, float xDeg, float wheelRadius);
    }

    private StateValueListener stateValueListener;

    public void setStateValueListener(StateValueListener stateValueListener) {
        this.stateValueListener = stateValueListener;
    }
}
