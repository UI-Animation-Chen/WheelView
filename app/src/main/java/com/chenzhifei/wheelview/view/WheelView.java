package com.chenzhifei.wheelview.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

import com.chenzhifei.wheelview.R;

/**
 * Created by chenzhifei on 2017/6/25.
 * 使用Graphics.Camera实现WheelView的3D效果。
 */

public class WheelView extends View {

    private static final float RADIAN_TO_DEG = (float) (180.0f / Math.PI);
    private static final float DEG_TO_RADIAN = (float) (Math.PI / 180.0f);

    private static final int WHEEL_VIEW_DEG = 120; // items show angle
    private int interItemDeg;
    private float projectionScaled;

    private static final int CAMERA_LOCATION_Z_UNIT = 72;
    private Camera camera = new Camera(); //default location: (0f, 0f, -8.0f), in pixels: -8.0f * 72 = -576f
                                          //will NOT be changed by camera.translateZ
    private final Matrix cameraMatrix = new Matrix();
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintCenterRect = new Paint();

    private float wheelRadius;
    private float distanceToDeg = -1f; // onSizeChanged里进行设置: wheelRadius --> 30°
    private int initialItemIndex = 0;
    private float maxSlideDeg;

    private int wheelViewWidth;
    private int wheelViewHeight;
    private int itemMaxWidth;
    private int itemMaxHeight;

    private String[] itemArr;

    private float distanceY = 0f; //camera.rotateX()向下为负值，向上为正值，和屏幕y坐标相反。

    private static final float MIN_VELOCITY = 50f; // pixels/second
    private float yVelocity = 0f;   // pixels/second
    private boolean isInfinity = false;
    private float yVelocityReduce = 1f; //decrease 1 pixels/second when a message is handled in the loop
                        //loop frequency is 60hz or 120hz when handleMessage(msg) includes UI update code

    private static final int RESISTANCE_FACTOR = 4; // 滑动到头时，有效滑动变为 4 分之一
    private static final float CLAMP_MAX_MIN_DELTA_DEG = 2.0f; // 2.0°
    private static final float CLAMP_NORMAL_DELTA_DEG = 0.5f; // 0.5°/16.67ms ~ 30°/s, or twice
    private float willToDeg = 0f;

    private Handler animHandler;
    private static final int MSG_HANDLE_SLIDING = 0;
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

        initAttrs(attrs);
        animHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_HANDLE_SLIDING:
                        handleAnimSliding();
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
        if (-distanceY* distanceToDeg < willToDeg) {// 向上滑动，到下一个item
            if (Math.abs(-distanceY*distanceToDeg - willToDeg) <= clampDeltaDeg) {
                distanceY = -willToDeg / distanceToDeg;

                if (WheelView.this.stateValueListener != null) {
                    int currIndex = (int)(-distanceY*distanceToDeg / interItemDeg);
                    int arrIndex = currIndex + (WHEEL_VIEW_DEG/interItemDeg)>>1;
                    WheelView.this.stateValueListener.stateValue(currIndex, itemArr[arrIndex]);
                }

            } else {
                distanceY -= clampDeltaDeg/ distanceToDeg;
                WheelView.this.animHandler.sendEmptyMessage(clampType);
            }

        } else if (-distanceY* distanceToDeg > willToDeg) {//向上滑动，回到上一个item
            if (Math.abs(-distanceY* distanceToDeg - willToDeg) <= clampDeltaDeg) {
                distanceY = -willToDeg/ distanceToDeg;

                if (WheelView.this.stateValueListener != null) {
                    int currIndex = (int)(-distanceY*distanceToDeg / interItemDeg);
                    int arrIndex = currIndex + (WHEEL_VIEW_DEG/interItemDeg)>>1;
                    WheelView.this.stateValueListener.stateValue(currIndex, itemArr[arrIndex]);
                }

            } else {
                distanceY += clampDeltaDeg/ distanceToDeg;
                WheelView.this.animHandler.sendEmptyMessage(clampType);
            }

        }

        invalidate();
    }

    private void handleAnimSliding() {
        // 先判断再updateY，就会有溢出效果：此次事件时判断成立，updateY后下次事件就会溢出。
        if ((-distanceY*distanceToDeg) > maxSlideDeg) {// 向上滑动到头并溢出
            yVelocity = 0f;

            willToDeg = maxSlideDeg; // 向下返回到maxSlideDeg
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
        updateY(yVelocity * 0.016f); // 0.016 = 1/60/1000 ms

        if (Math.abs(yVelocity) <= MIN_VELOCITY) { // clamp item deg
            yVelocity = 0f;

            float offsetDeg = -distanceY*distanceToDeg % interItemDeg;
            float clampOffsetDeg = offsetDeg >= interItemDeg/2 ? interItemDeg - offsetDeg : -offsetDeg;

            int index = (int)(-distanceY*distanceToDeg / interItemDeg);
            willToDeg = clampOffsetDeg > 0f ? (index+1) * interItemDeg : index * interItemDeg;

            WheelView.this.animHandler.sendEmptyMessage(MSG_NORMAL_CLAMP);
            return;
        }

        if (WheelView.this.isInfinity) {
            animHandler.sendEmptyMessage(MSG_HANDLE_SLIDING);

        } else {
            // decrease the velocities.
            // 'Math.abs(yVelocity) <= yVelocityReduce' make sure the yVelocity will be 0 finally.
            yVelocity = Math.abs(yVelocity) <= yVelocityReduce ? 0f :
                    (yVelocity > 0 ? yVelocity - yVelocityReduce : yVelocity + yVelocityReduce);

            animHandler.sendEmptyMessage(MSG_HANDLE_SLIDING);
        }
    }

    private void initAttrs(AttributeSet attrs) {
        TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.WheelView);
        int showItems = ta.getInt(R.styleable.WheelView_showItems, 5);
        float wheelTextSize = ta.getDimension(R.styleable.WheelView_wheelTextSize, 16);
        int wheelTextColor = ta.getColor(R.styleable.WheelView_wheelTextColor, 0);
        ta.recycle();

        init(showItems, wheelTextSize, wheelTextColor);
    }

    private void init(int showItems, float wheelTextSize, int wheelTextColor) {
        if (showItems < 0 || showItems > 11 || showItems%2 == 0) {
            throw new IllegalArgumentException("showItems only can be 1, 3, 5, 7, 9, 11");
        }
        interItemDeg = WHEEL_VIEW_DEG / (showItems+1);

        initData(new String[]{"no data"});
        initPaintText(wheelTextSize, wheelTextColor);
        getMaxItemSize();
        setPaintCenterRect();
    }

    private void initData(String[] dataArr) {
        if (null == dataArr) {
            throw new NullPointerException("dataArr can not be a null");
        }

        if (dataArr.length == 0) {
            dataArr = new String[]{"no data"};
        }

        int extra = WHEEL_VIEW_DEG / interItemDeg;
        itemArr = new String[dataArr.length + extra];
        int offset = extra / 2;
        for (int i = 0; i < offset; i++) {
            itemArr[i] = "";
        }
        for (int i = offset; i < itemArr.length - offset; i++) {
            itemArr[i] = dataArr[i - offset];
        }
        for (int i = itemArr.length - offset; i < itemArr.length; i++) {
            itemArr[i] = "";
        }

        /**
         * itemArr.length-1: item --- item --- item, 3 - 1 = 2
         *  0                                            max deg
         * |-------<--WHEEL_VIEW_DEG-->--------------------|
         */
        maxSlideDeg = interItemDeg*(itemArr.length-1) - WHEEL_VIEW_DEG;
    }

    /**
     * 设置wheelView的显示数据，可以在wheelview显示前设置，也可以在显示后设置。
     * @param dataArr wheelView的数据源
     */
    public void setData(String[] dataArr) {
        initData(dataArr);
        getMaxItemSize();

        yVelocity = 0f;
        if (null != animHandler) {
            animHandler.removeCallbacksAndMessages(null);
        }

        distanceY = 0f;
        invalidate();

        if (WheelView.this.stateValueListener != null) {
            int extra = WHEEL_VIEW_DEG / interItemDeg;
            WheelView.this.stateValueListener.stateValue(0, itemArr[extra/2]);
        }
    }

    private void initPaintText(float textSize, int textColor) {
        if (textColor != -1) {
            paintText.setColor(textColor);
        }
        if (textSize > 0) {
            projectionScaled = 1f / (1f - (float)Math.cos(WHEEL_VIEW_DEG *DEG_TO_RADIAN / 2));
            paintText.setTextSize(textSize/projectionScaled);
        }
        paintText.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * set textSize, textColorStr
     * @param textSize      传入0表示不设置，单位为像素
     * @param textColorStr  传入null表示不设置，字符串形式的颜色，如"#00ff00"，注意"#0f0"错误。
     */
    public void setPaintText(float textSize, String textColorStr) {
        int textColor = -1;
        if (!TextUtils.isEmpty(textColorStr)) {
            textColor = Color.parseColor(textColorStr);
        }
        initPaintText(textSize, textColor);
        getMaxItemSize();
        invalidate();
    }

    private void getMaxItemSize() {
        Rect textRect = new Rect();
        itemMaxWidth = itemMaxHeight = 0;
        for (String item : itemArr) {
            paintText.getTextBounds(item, 0, item.length(), textRect);
            if (textRect.width() > itemMaxWidth) {
                itemMaxWidth = textRect.width();
                itemMaxHeight = textRect.height();
            }
        }
    }

    private void setPaintCenterRect() {
        paintCenterRect.setColor(Color.parseColor("#0000ff"));
        paintCenterRect.setStrokeWidth(1);
    }

    /**
     * 设置显示index位置的item
     * @param index 要居中显示的item的索引。
     */
    public void setItem(int index) {
        int extra = WHEEL_VIEW_DEG / interItemDeg;
        if (index < 0 || index > itemArr.length - extra) {
            throw new ArrayIndexOutOfBoundsException(index);
        }

        yVelocity = 0f;
        if (null != animHandler) {
            animHandler.removeCallbacksAndMessages(null);
        }

        if (distanceToDeg == -1f) {
            //外界在WheelView显示之前setItem时，distanceToDeg还未进行有效设置
            initialItemIndex = index;
        } else {
            distanceY = -index * interItemDeg / distanceToDeg;
            invalidate();
        }

        if (WheelView.this.stateValueListener != null) {
            WheelView.this.stateValueListener.stateValue(index, itemArr[index + extra/2]);
        }
    }

    /**
     * 设置wheelview的滑动速率衰减
     * @param yVelocityReduce 1.3°/s 角度每秒
     */
    public void setYVelocityReduce(float yVelocityReduce) {
        if (yVelocityReduce <= 0f) {
            this.isInfinity = true;
            this.yVelocityReduce = 0f;
        } else {
            this.isInfinity = false;
            this.yVelocityReduce = yVelocityReduce;
        }
    }

    /**
     * 滚动wheelview
     * @param movedY 相邻两次MotionEvent事件之间手指滑动的像素值
     */
    public void updateY(float movedY) {
        if (-distanceY*distanceToDeg > maxSlideDeg || -distanceY < 0f) {
            movedY /= RESISTANCE_FACTOR;
        }

        distanceY += movedY;
        invalidate();
    }

    /**
     * 手指接触屏幕(down事件)时调用。
     */
    public void stopAnim() {
        animHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 手指离开屏幕(up事件)时调用。
     * @param yVelocity 手指离开屏幕时的滑动速度，可为0
     */
    public void startAnim(float yVelocity) {
        this.yVelocity = yVelocity;

        animHandler.sendEmptyMessage(MSG_HANDLE_SLIDING);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        wheelViewWidth = w; //params value is in pixels not dp
        wheelViewHeight = h;
        float projectionY = (h - getPaddingTop() - getPaddingBottom()) / 2f;

        wheelRadius = projectionY * (float) Math.sin((WHEEL_VIEW_DEG>>1) * DEG_TO_RADIAN);
        distanceToDeg = 30f / wheelRadius; // wheelRadius --> 30°

        setItem(initialItemIndex);
        initialItemIndex = 0; // 如果想不销毁wheelview重新进行relayout，radius会变化，之前的distanceY将无效。

        float cameraLocationZ = 2*wheelRadius / CAMERA_LOCATION_Z_UNIT;
        camera.setLocation(0, 0, -cameraLocationZ);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // translate canvas in order to locate the maxItem in the center of the WheelViwe
        canvas.translate((wheelViewWidth - itemMaxWidth) / 2f + getPaddingLeft()/2 - getPaddingRight()/2,
                        (wheelViewHeight - itemMaxHeight) / 2f + getPaddingTop()/2 - getPaddingBottom()/2);

        drawWheelText(canvas);

        drawCenterRect(canvas);
    }

    private void drawWheelText(Canvas canvas) {
        float accumDeg = -distanceY * distanceToDeg;
        float driveDeg = accumDeg % interItemDeg; // 0 ~ x，当向下滑动到头是，会变为负值。

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
        driveDeg += WHEEL_VIEW_DEG / 2; // 60 ~ 60+x
        for (int i = 1, length = WHEEL_VIEW_DEG / interItemDeg; i <= length; i++) {
            setCameraMatrixAtIndex(driveDeg - i * interItemDeg);
            drawTextAtIndex(canvas, (int)(accumDeg / interItemDeg) + i);
        }
    }

    private void setCameraMatrixAtIndex(float deg) {
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
        float verticalOffset = itemMaxHeight;
        float newLeft = -(projectionScaled-1) * itemMaxWidth/2;
        float newTop = -(projectionScaled-1) * itemMaxHeight/2 - verticalOffset;
        float newRight = (projectionScaled+1) * itemMaxWidth/2;
        float newBottom = (projectionScaled+1) * itemMaxHeight/2 + verticalOffset;
        // top line
        canvas.drawLine(newLeft, newTop, newRight, newTop, paintCenterRect);
        // bottom line
        canvas.drawLine(newLeft, newBottom, newRight, newBottom, paintCenterRect);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animHandler != null) {
            animHandler.removeCallbacksAndMessages(null);
        }
    }

    public interface StateValueListener {
        /**
         * wheelview滚动停止调用，提供当前的状态
         * @param currIndex 居中item的索引
         * @param currItem  居中item的值
         */
        void stateValue(int currIndex, String currItem);
    }

    private StateValueListener stateValueListener;

    /**
     * 设置wheelview的状态监听器，获取当前居中item的索引和值
     * @param stateValueListener
     */
    public void setStateValueListener(StateValueListener stateValueListener) {
        this.stateValueListener = stateValueListener;
    }
}
