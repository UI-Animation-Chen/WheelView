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
    private static final float WHEEL_VIEW_DEGREE = 120f;

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

    private float distanceY = 0; //向下为负值，向上为正值，和屏幕y坐标相反。
    private float wheelRadius;

    private float distanceToDegree; // wheelRadius --> 90度

    private boolean isInfinity = false;
    private float distanceVelocityDecrease = 1f; //decrease 1 pixels/second when a message is handled in the loop
                    //loop frequency is 60hz or 120hz when handleMessage(msg) includes UI update code

    private float yVelocity = 0f;
    private long lastDeltaMilliseconds = 0;

    private Handler animHandler;
    private Handler touchHandler;

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
                updateY(yVelocity * lastDeltaMilliseconds / 1000f);

                WheelView.this.invalidate();

                if (WheelView.this.stateValueListener != null) {
                    WheelView.this.stateValueListener.stateValue(yVelocity / 1000f, -distanceY, 0f, wheelRadius);
                }

                if (yVelocity == 0f) { // anim will stop
                    return true;
                }

                if (WheelView.this.isInfinity) {
                    WheelView.this.sendMsgForAnim();

                } else {
                    // decrease the velocities.
                    // 'Math.abs(yVelocity) <= distanceVelocityDecrease' make sure the yVelocity will be 0 finally.
                    yVelocity = Math.abs(yVelocity) <= distanceVelocityDecrease ? 0f :
                            (yVelocity > 0 ? yVelocity - distanceVelocityDecrease : yVelocity + distanceVelocityDecrease);

                    WheelView.this.sendMsgForAnim();
                }

                return true;
            }
        });

        touchHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                if (WheelView.this.stateValueListener != null) {
                    WheelView.this.stateValueListener.stateValue(yVelocity / 1000f, -distanceY, 0f, wheelRadius);
                }
                return true;
            }
        });
    }

    private void init() {
        itemArr = new String[100];
        for (int i = 0; i < itemArr.length; i++) {
            itemArr[i] = "chenzhifei" + i;
        }

        camera.setLocation(0, 0, cameraLocationZ);

        setPaintText(24f);
        paintCenterRect.setColor(Color.parseColor("#77000000"));
        paintCenterRect.setStrokeWidth(4);
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

    public void setDistanceVelocityDecrease(float distanceVelocityDecrease) {
        if (distanceVelocityDecrease <= 0f) {
            this.isInfinity = true;
            this.distanceVelocityDecrease = 0f;
        } else {
            this.isInfinity = false;
            this.distanceVelocityDecrease = distanceVelocityDecrease;
        }
    }

    public void updateY(float movedY) {
        // itemArr.length - 1: item --- item --- item --- item, 4 - 1 = 3
        if (-distanceY  >= INTER_ITEM_DEGREE / distanceToDegree * (itemArr.length - 1)) { // 向上滑动到头
            if (movedY > 0) {   // 只能向下滑
                setDistanceY(movedY);
            }

        } else if(-distanceY <= 0f){
            if (movedY < 0) {
                setDistanceY(movedY);
            }

        } else {
            setDistanceY(movedY);
        }
    }

    private void setDistanceY(float movedY) {
        this.distanceY += movedY;
        invalidate();
        touchHandler.sendEmptyMessage(0);
    }

    public void updateCameraZtranslate(float cameraZtranslate) {
        this.wheelRadius += cameraZtranslate;
        invalidate();
        touchHandler.sendEmptyMessage(0);
    }

    private void sendMsgForAnim() {
        animHandler.sendEmptyMessage(0);
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
        float deg;
        for (int i = 0; i < itemArr.length; i++) {
            deg = -distanceY * distanceToDegree - i * INTER_ITEM_DEGREE;
            if (deg < -60f || deg > 60f) {
                continue;
            }
            setCmaraMatrixAtIndex(deg);
            drawTextAtIndex(canvas, i);
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
        canvas.save();
        canvas.concat(cameraMatrix);
        canvas.drawText(itemArr[index], 0, itemMaxHeight, paintText);
        canvas.restore();
    }

    private void drawCenterRect(Canvas canvas) {
        canvas.drawLine(0f, -1.8f* itemMaxHeight, itemMaxWidth, -1.8f* itemMaxHeight, paintCenterRect);
        canvas.drawLine(0f, 2.8f* itemMaxHeight, itemMaxWidth, 2.8f* itemMaxHeight, paintCenterRect);
        canvas.drawLine(itemMaxWidth / 2f, itemMaxHeight / 2f, itemMaxWidth / 2f, itemMaxHeight / 2f - wheelRadius, paintCenterRect);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animHandler != null) {
            animHandler.removeCallbacksAndMessages(null);
        }
    }

    public interface StateValueListener {
        void stateValue(float yVelocity, float distanceY, float rotateDegree, float cameraZtranslate);
    }

    private StateValueListener stateValueListener;

    public void setStateValueListener(StateValueListener stateValueListener) {
        this.stateValueListener = stateValueListener;
    }
}
