package com.chenzhifei.wheelview.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
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

    public static final int TEXT_ALIGN_CENTER = 0;
    public static final int TEXT_ALIGN_LEFT = 1;
    public static final int TEXT_ALIGN_RIGHT = 2;
    private int textAlign;

    private static final float DEG_TO_RADIAN = (float) (Math.PI / 180.0f);

    private static final int WHEEL_VIEW_DEG = 120; // wheelview show angle
    // distanceZ will lead to scale up or down the view.
    private static final float PROJECTION_SCALED =
            1/(1-(float)Math.cos((WHEEL_VIEW_DEG>>1)*DEG_TO_RADIAN));
    private int interItemDeg; // deg between two adjacent items。

    /**
     *              | y
     *              |
     *              |  / z
     *              | /
     *     ___-x____|/____x__________
     *             /|(0,0)           |
     *            / |                |
     *    camera *  |                |
     *          /   |     screen     |
     *      -z /    |                |
     *           -y |                |
     *              |________________|
     *
     * camera model:
     * default location: (0f, 0f, -8.0f), in pixels: -8.0f * 72 = -576f
     */
    private static final int CAMERA_LOCATION_Z_UNIT = 72;
    private Camera camera = new Camera();
    private final Matrix cameraMatrix = new Matrix();
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint paintTopLayer = new Paint();
    private static final Paint paintBottomLayer = new Paint();
    private static final int centerLayerColor = Color.parseColor("#00ffffff");
    private static final int edgeLayerColor = Color.parseColor("#ddffffff");

    private float wheelRadius; // wheelView item's distanceZ.
    private float distanceToDeg = -1f; // will be set in onSizeChanged().
    private int initItemIndex = 0;
    private float maxSlideDeg;

    private int wheelViewWidth;
    private int wheelViewHeight;
    private int itemMaxWidth;
    private int itemMaxHeight;

    private String[] itemArr;

    private float distanceY = 0f; // camera's y axis direction is opposite to screen's.

    private static final float MIN_VELOCITY = 50f; // pixels/second
    private float yVelocity = 0f;   // pixels/second
    private boolean isInfinity = false;
    private float yVelocityReduce = 50f; //decrease 50 pixels/second when a message is handled in the loop
                        //loop frequency is 60hz or 120hz when handleMessage(msg) includes UI update code

    private static final int RESISTANCE_FACTOR = 4; // valid updateY become 1/4 when is overscrolling.
    private static final float CLAMP_MAX_MIN_DELTA_DEG = 2.0f; // 2.0°, four times as normal.
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
        if (Math.abs(-distanceY*distanceToDeg - willToDeg) <= clampDeltaDeg) { // complete.
            distanceY = -willToDeg / distanceToDeg;

            if (WheelView.this.stateValueListener != null) {
                int currIndex = (int) (-distanceY * distanceToDeg / interItemDeg);
                int arrIndex = currIndex + ((WHEEL_VIEW_DEG / interItemDeg) >> 1);
                WheelView.this.stateValueListener.stateValue(currIndex, itemArr[arrIndex]);
            }
        } else { // continue clamp.
            if (-distanceY*distanceToDeg < willToDeg) {        // go forward to next item.
                distanceY -= clampDeltaDeg / distanceToDeg; // 'increase' distanceY.
            } else if (-distanceY*distanceToDeg > willToDeg) { // go back to previous item.
                distanceY += clampDeltaDeg / distanceToDeg; // 'decrease' distanceY.
            }
            WheelView.this.animHandler.sendEmptyMessage(clampType);
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
            decelerationSliding(); // 惯性滑动
        }
    }

    private void decelerationSliding() {
        updateY(yVelocity * 0.0167f); // 0.0167 = 1/60/1000 ms

        if (Math.abs(yVelocity) <= MIN_VELOCITY) { // clamp item deg
            yVelocity = 0f;

            float offsetDeg = -distanceY*distanceToDeg % interItemDeg;
            float clampOffsetDeg = offsetDeg >= interItemDeg/2 ? interItemDeg-offsetDeg : -offsetDeg;

            int index = (int)(-distanceY*distanceToDeg / interItemDeg);
            willToDeg = clampOffsetDeg > 0f ? (index+1)*interItemDeg : index*interItemDeg;

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
        int showItems = ta.getInt(R.styleable.WheelView_showItems, 7);
        float wheelTextSize = ta.getDimension(R.styleable.WheelView_wheelTextSize, 32); //32 -> 16sp
        int wheelTextColor = ta.getColor(R.styleable.WheelView_wheelTextColor, Color.parseColor("#333333"));
        int textAlign = ta.getInt(R.styleable.WheelView_wheelTextAlign, TEXT_ALIGN_CENTER);
        ta.recycle();

        init(showItems, wheelTextSize, wheelTextColor, textAlign);
    }

    private void init(int showItems, float wheelTextSize, int wheelTextColor, int textAlign) {
        if (showItems < 0 || showItems > 11 || showItems%2 == 0) {
            throw new IllegalArgumentException("showItems only can be 1, 3, 5, 7, 9, 11");
        }
        interItemDeg = WHEEL_VIEW_DEG / (showItems+1);

        initData(new String[]{"no data"});
        initPaintText(wheelTextSize, wheelTextColor, textAlign);
        getMaxItemSize();
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

    private void initPaintText(float textSize, int textColor, int textAlign) {
        if (textColor != -1) {
            paintText.setColor(textColor);
        }

        if (textSize > 0) {
            paintText.setTextSize(textSize/ PROJECTION_SCALED);
        }

        this.textAlign = textAlign;

        switch (textAlign) {
            case TEXT_ALIGN_CENTER:
                paintText.setTextAlign(Paint.Align.CENTER);
                break;
            case TEXT_ALIGN_LEFT:
                paintText.setTextAlign(Paint.Align.LEFT);
                break;
            case TEXT_ALIGN_RIGHT:
                paintText.setTextAlign(Paint.Align.RIGHT);
                break;
            default: // same as center
                paintText.setTextAlign(Paint.Align.CENTER);
                break;
        }
    }

    /**
     * set textSize, textColorStr, textAlign
     * @param textSize      0表示不设置，单位为像素
     * @param textColorStr  null表示不设置，字符串形式的颜色，如"#00ff00"，注意"#0f0"错误。
     * @param textAlign     小于0的值表示不设置
     */
    private void setPaintText(float textSize, String textColorStr, int textAlign) {
        int textColor = -1;
        if (!TextUtils.isEmpty(textColorStr)) {
            textColor = Color.parseColor(textColorStr);
        }
        if (textAlign < 0) { // 初始化的时候不会小于0，只能是用户后来设置的。
            textAlign = this.textAlign;
        }
        initPaintText(textSize, textColor, textAlign);
        getMaxItemSize();
        invalidate();
    }

    /**
     * 设置字体大小
     * @param textSize 单位为像素
     */
    public void setWheelTextSize(float textSize) {
        setPaintText(textSize, null, -1);
    }

    /**
     * 设置字体颜色
     * @param textColorStr 字符串形式的颜色，如"#00ff00"，注意"#0f0"错误。
     */
    public void setWheelTextColor(String textColorStr) {
        setPaintText(0, textColorStr, -1);
    }

    /**
     * 设置字体对齐方式
     * @param textAlign 取值为 WheelView.TEXT_ALIGN_CENTER,
     *                  WheelView.TEXT_ALIGN_LEFT, WheelView.TEXT_ALIGN_RIGHT
     */
    public void setWheelTextAlign(int textAlign) {
        setPaintText(0, null, textAlign);
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

    private void setPaintLayer() {
        // 尺寸并没有严格按照位置来设置，不影响观感即可
        LinearGradient topLg = new LinearGradient(0, -(wheelViewHeight>>1), 0, 0,
                edgeLayerColor, centerLayerColor, Shader.TileMode.MIRROR);
        LinearGradient bottomLg = new LinearGradient(0, 0, 0, wheelViewHeight>>1,
                centerLayerColor, edgeLayerColor, Shader.TileMode.MIRROR);

        paintTopLayer.setShader(topLg);
        paintBottomLayer.setShader(bottomLg);
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
            initItemIndex = index;
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
        wheelViewWidth = w; // params value is in pixels not dp
        wheelViewHeight = h;
        float projectionY = (h - getPaddingTop() - getPaddingBottom()) / 2f;

        wheelRadius = projectionY * (float) Math.sin((WHEEL_VIEW_DEG>>1) * DEG_TO_RADIAN);
        distanceToDeg = 30f / wheelRadius; // wheelRadius --> 30°

        float cameraLocationZ = PROJECTION_SCALED*wheelRadius / CAMERA_LOCATION_Z_UNIT;
        camera.setLocation(0, 0, -cameraLocationZ);

        setItem(initItemIndex);
        initItemIndex = 0; // 如果想不销毁wheelview重新进行relayout，radius会变化，之前的distanceY将无效。

        setPaintLayer();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float canvasTranslateX, textTranslateX, textOriginX;
        switch (textAlign) {
            case TEXT_ALIGN_CENTER:
                canvasTranslateX = (wheelViewWidth-itemMaxWidth)/2f;
                textTranslateX = itemMaxWidth/2f;
                textOriginX = itemMaxWidth / 2;
                break;
            case TEXT_ALIGN_LEFT:
                canvasTranslateX = getPaddingLeft();
                textTranslateX = 0f;
                textOriginX = 0f;
                break;
            case TEXT_ALIGN_RIGHT:
                canvasTranslateX = wheelViewWidth - itemMaxWidth - getPaddingRight();
                textTranslateX = itemMaxWidth;
                textOriginX = itemMaxWidth;
                break;
            default: // same as center
                canvasTranslateX = (wheelViewWidth-itemMaxWidth)/2f;
                textTranslateX = itemMaxWidth/2f;
                textOriginX = itemMaxWidth / 2;
                break;
        }
        // translate canvas in order to locate the maxItem in the left/center/right of the WheelView
        int wheelViewH = wheelViewHeight - getPaddingTop() - getPaddingBottom();
        float canvasTranslateY = (wheelViewH-itemMaxHeight)/2 + getPaddingTop();
        canvas.translate(canvasTranslateX, canvasTranslateY);

        drawWheelText(canvas, textTranslateX, textOriginX);

        drawLayer(canvas);
    }

    private void drawWheelText(Canvas canvas, float textTranslateX, float textOriginX) {
        float accumDeg = -distanceY * distanceToDeg;
        float driveDeg = accumDeg % interItemDeg; // 0 ~ interItemDeg，当向下滑动到头时，会变为负值。
        /**
         * 每转动x度循环一次。
         * 9个位置，两头的两个位置刚好斜切，不显示。
         *   不显示   60   45   30   15    0   -15  -30  -45  -60  不显示
         *  ----------\----\----\----\----\----\----\----\----\----------
         *   初始化    !    \    \    \    \    \    \    \    !    7个  0 !：刚好斜切，不显示。
         *   驱动范围：
         *           <--  \    \    \    \    \    \    \    \     8个  1
         *          <--  \    \    \    \    \    \    \    \      8个  2
         *         <--  \    \    \    \    \    \    \    \       8个  3
         *        <--  \    \    \    \    \    \    \    \        8个  4
         *   重复：
         *       <--  !    \    \    \    \    \    \    \    !    7个  0
         *           <--  \    \    \    \    \    \    \    \     8个  1
         *           ... ...
         */
        driveDeg += WHEEL_VIEW_DEG>>1; // 60 ~ 60+x
        for (int i = 1, length = WHEEL_VIEW_DEG/interItemDeg; i <= length; i++) {
            setCameraMatrixAtIndex(driveDeg - i*interItemDeg, textTranslateX);
            drawTextAtIndex(canvas, (int)(accumDeg/interItemDeg) + i, textOriginX);
        }
    }

    private void setCameraMatrixAtIndex(float deg, float textTranslateX) {
        cameraMatrix.reset();

        camera.save(); // save the original state(no any transformation) so you
                       // can restore it after any changes
        camera.rotateX(deg); // it will lead to rotate Y and Z axis
        // y,z axis 3D effects.
//        camera.rotateY(-10f); // it will lead to rotate Z axis
//        camera.rotateZ(10f); // it will NOT lead to rotate X,Y axis
        camera.translate(0f, 0f, -wheelRadius);
        camera.getMatrix(cameraMatrix);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin that camera's transformation depends on
        // to left/center/right of the maxItem
        cameraMatrix.preTranslate(-textTranslateX, -(itemMaxHeight / 2));
        cameraMatrix.postTranslate(textTranslateX, itemMaxHeight / 2);
    }

    private void drawTextAtIndex(Canvas canvas, int index, float textOriginX) {
        if (index >= itemArr.length) {
            index = itemArr.length - 1;
        } else if (index < 0) {
            index = 0;
        }
        canvas.save();
        canvas.concat(cameraMatrix);
        canvas.drawText(itemArr[index], textOriginX, itemMaxHeight, paintText);
        canvas.restore();
    }

    private void drawLayer(Canvas canvas) {
        float verticalOffset = itemMaxHeight;
        float newTop = -(PROJECTION_SCALED -1) * itemMaxHeight/2 - verticalOffset;
        float newBottom = (PROJECTION_SCALED +1) * itemMaxHeight/2 + verticalOffset;

        // 尺寸没有严格按照位置设置，但保证了不影响观感。减少精确尺寸计算，可以提高onDraw效率。
        // top layer
        canvas.drawRect(-wheelViewWidth, newTop - (wheelViewHeight>>1),
                wheelViewWidth, newTop, paintTopLayer);
        // bottom layer
        canvas.drawRect(-wheelViewWidth, newBottom, wheelViewWidth,
                newBottom + (wheelViewHeight>>1), paintBottomLayer);
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
     * @param stateValueListener listener
     */
    public void setStateValueListener(StateValueListener stateValueListener) {
        this.stateValueListener = stateValueListener;
    }
}
