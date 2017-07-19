package com.chenzhifei.wheelview.view;

import android.content.Context;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by chenzhifei on 2017/6/25.
 * 使用Graphics.Camera来实现3D效果。
 */

public class WheelView extends View {

    private int wheelViewWidth = 0;
    private int wheelViewHeight = 0;
    private int itemWidth = 0;
    private int itemHeight = 0;

    private String[] itemArr = {"lalalala", "lelelele", "chenzhifei", "zhangsan", "lisi", "wangwu", "zhaoliu"};

    private Camera camera = new Camera(); //default location: (0f, 0f, -8.0f), in pixels: -8.0f * 72 = -576f
                                          //will NOT be changed by camera.translateZ

    private Matrix[] matrixArr = {new Matrix(), new Matrix(), new Matrix(), new Matrix(), new Matrix(), new Matrix(), new Matrix()};
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float distanceY = 0;
    private float cameraZtranslate; // 3D rotate radius

    private float distanceToDegree; // cameraZtranslate --> 90度
    private float xDeg;

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

        setPaint(40f);

        animHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                updateY(yVelocity * lastDeltaMilliseconds / 1000f);

                WheelView.this.invalidate();

                if (WheelView.this.stateValueListener != null) {
                    WheelView.this.stateValueListener.stateValue(yVelocity / 1000f, -distanceY, 0f, cameraZtranslate);
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
                    WheelView.this.stateValueListener.stateValue(yVelocity / 1000f, -distanceY, 0f, cameraZtranslate);
                }
                return true;
            }
        });
    }

    public void setPaint(float textSize) {
        paint.setTextSize(textSize);
        paint.setTextAlign(Paint.Align.LEFT);
        setMaxItemSize();
    }

    private void setMaxItemSize() {
        Rect textRect = new Rect();
        for (String item : itemArr) {
            paint.getTextBounds(item, 0, item.length(), textRect);
            if (textRect.width() > itemWidth) {
                itemWidth = textRect.width();
                itemHeight = textRect.height();
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
        if (xDeg - 60f >= 0f) { // 向上滑动到头
            if (movedY > 0) {   // 只能向下滑
                setDistanceY(movedY);
            }

        } else if(xDeg + 60f <= 0f){
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
        this.cameraZtranslate += cameraZtranslate;
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
//        int min = Math.min(w, h) / 2;
//        cameraZtranslate = min * 576f / (float) Math.sqrt(min * min + 576 * 576);
        cameraZtranslate = 168f;
        distanceToDegree = 90f / cameraZtranslate;//NOT changed when cameraZtranslate changed in the future
    }

    @Override
    protected void onDraw(Canvas canvas) {
        setMatrixArr();

        // translate canvas to locate the maxItem in center of the WheelViwe
        canvas.translate((wheelViewWidth - itemWidth) / 2f, (wheelViewHeight - itemHeight) / 2f);

        drawCanvas(canvas);
    }

    private void setMatrixArr() {
        // convert distances in pixels into degrees
        xDeg = -distanceY * distanceToDegree;

        for (int i = 0; i < matrixArr.length; i++) {
            matrixArr[i].reset();

            camera.save(); // save the original state(no any transformation) so you can restore it after any changes
            camera.rotateX(xDeg + (60 - i * 20)); // it will lead to rotate Y and Z axis
            camera.translate(0f, 0f, -cameraZtranslate);
            camera.getMatrix(matrixArr[i]);
            camera.restore(); // restore to the original state after uses for next use

            // translate coordinate origin the camera's transformation depends on to center of the bitmap
            matrixArr[i].preTranslate(-(itemWidth / 2), -(itemHeight / 2));
            matrixArr[i].postTranslate(itemWidth / 2, itemHeight / 2);
        }

    }

    private void drawCanvas(Canvas canvas) {

        for (int i = 0; i < matrixArr.length; i++) {
            canvas.save();
            canvas.concat(matrixArr[i]);
            canvas.drawText(itemArr[i], 0, itemHeight, paint);
            canvas.restore();
        }
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
