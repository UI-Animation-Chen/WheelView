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

    private int wheelViewWidth;
    private int wheelViewHeight;
    private int itemWidth;
    private int itemHeight;

    private String[] items = {"lalalala", "lelelele", "chenzhifei", "zhangsan", "lisi", "wangwu", "zhaoliu"};

    private Camera camera = new Camera(); //default location: (0f, 0f, -8.0f), in pixels: -8.0f * 72 = -576f

    private Matrix matrixUp45 = new Matrix();
    private Matrix matrixUp30 = new Matrix();
    private Matrix matrixUp15 = new Matrix();
    private Matrix matrixFront = new Matrix();
    private Matrix matrixDown15 = new Matrix();
    private Matrix matrixDown30 = new Matrix();
    private Matrix matrixDown45 = new Matrix();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float distanceY = 0;
    private float cameraZtranslate; // 3D rotate radius

    private float distanceToDegree; // cameraZtranslate --> 90度

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

        paint.setTextSize(32f);
        Rect textRect = new Rect();
        paint.getTextBounds(items[0], 0, items[0].length(), textRect);
        itemWidth = textRect.width();
        itemHeight = textRect.height();

        animHandler = new Handler(new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                distanceY += (yVelocity * lastDeltaMilliseconds / 1000);

                WheelView.this.invalidate();

                if (WheelView.this.stateValueListener != null) {
                    WheelView.this.stateValueListener.stateValue(0f, -distanceY, 0f, cameraZtranslate);
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
                    WheelView.this.stateValueListener.stateValue(0f, -distanceY, 0f, cameraZtranslate);
                }
                return true;
            }
        });
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

    public void startAnim(long lastDeltaMilliseconds, float xVelocity, float yVelocity, float rotatedVelocity) {
        this.lastDeltaMilliseconds = lastDeltaMilliseconds;
        this.yVelocity = yVelocity;

        sendMsgForAnim();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        wheelViewWidth = w; //params value is in pixels not dp
        wheelViewHeight = h;
        cameraZtranslate = Math.min(w, h) / 2;
        distanceToDegree = 90f / cameraZtranslate;//NOT changed when cameraZtranslate changed in the future
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // convert distances in pixels into degrees
        float xDeg = -distanceY * distanceToDegree;

        setMatrixFrontUp45(xDeg);
        setMatrixFrontUp30(xDeg);
        setMatrixFrontUp15(xDeg);
        setMatrixFront(xDeg);
        setMatrixFrontDown15(xDeg);
        setMatrixFrontDown30(xDeg);
        setMatrixFrontDown45(xDeg);

        // translate canvas to locate the bitmap in center of the ThreeDViwe
        canvas.translate((wheelViewWidth - itemWidth) / 2f, (wheelViewHeight - itemHeight) / 2f);

        drawCanvas(canvas, xDeg);
    }

    private void setMatrixFrontUp45(float xDeg) {
        matrixUp45.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(xDeg + 45); // it will lead to rotate Y and Z axis
        camera.translate(0f, 0f, -cameraZtranslate);
        camera.getMatrix(matrixUp45);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin the camera's transformation depends on to center of the bitmap
        matrixUp45.preTranslate(-(itemWidth / 2), -(itemHeight / 2));
        matrixUp45.postTranslate(itemWidth / 2, itemHeight / 2);
    }

    private void setMatrixFrontUp30(float xDeg) {
        matrixUp30.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(xDeg + 30); // it will lead to rotate Y and Z axis
        camera.translate(0f, 0f, -cameraZtranslate);
        camera.getMatrix(matrixUp30);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin the camera's transformation depends on to center of the bitmap
        matrixUp30.preTranslate(-(itemWidth / 2), -(itemHeight / 2));
        matrixUp30.postTranslate(itemWidth / 2, itemHeight / 2);
    }

    private void setMatrixFrontUp15(float xDeg) {
        matrixUp15.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(xDeg + 15); // it will lead to rotate Y and Z axis
        camera.translate(0f, 0f, -cameraZtranslate);
        camera.getMatrix(matrixUp15);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin the camera's transformation depends on to center of the bitmap
        matrixUp15.preTranslate(-(itemWidth / 2), -(itemHeight / 2));
        matrixUp15.postTranslate(itemWidth / 2, itemHeight / 2);
    }

    private void setMatrixFront(float xDeg) {
        matrixFront.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(xDeg); // it will lead to rotate Y and Z axis
        camera.translate(0f, 0f, -cameraZtranslate);
        camera.getMatrix(matrixFront);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin the camera's transformation depends on to center of the bitmap
        matrixFront.preTranslate(-(itemWidth / 2), -(itemHeight / 2));
        matrixFront.postTranslate(itemWidth / 2, itemHeight / 2);
    }

    private void setMatrixFrontDown15(float xDeg) {
        matrixDown15.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(xDeg - 15); // it will lead to rotate Y and Z axis
        camera.translate(0f, 0f, -cameraZtranslate);
        camera.getMatrix(matrixDown15);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin the camera's transformation depends on to center of the bitmap
        matrixDown15.preTranslate(-(itemWidth / 2), -(itemHeight / 2));
        matrixDown15.postTranslate(itemWidth / 2, itemHeight / 2);
    }

    private void setMatrixFrontDown30(float xDeg) {
        matrixDown30.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(xDeg - 30); // it will lead to rotate Y and Z axis
        camera.translate(0f, 0f, -cameraZtranslate);
        camera.getMatrix(matrixDown30);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin the camera's transformation depends on to center of the bitmap
        matrixDown30.preTranslate(-(itemWidth / 2), -(itemHeight / 2));
        matrixDown30.postTranslate(itemWidth / 2, itemHeight / 2);
    }

    private void setMatrixFrontDown45(float xDeg) {
        matrixDown45.reset();

        camera.save(); // save the original state(no any transformation) so you can restore it after any changes
        camera.rotateX(xDeg - 45); // it will lead to rotate Y and Z axis
        camera.translate(0f, 0f, -cameraZtranslate);
        camera.getMatrix(matrixDown45);
        camera.restore(); // restore to the original state after uses for next use

        // translate coordinate origin the camera's transformation depends on to center of the bitmap
        matrixDown45.preTranslate(-(itemWidth / 2), -(itemHeight / 2));
        matrixDown45.postTranslate(itemWidth / 2, itemHeight / 2);
    }

    private void drawCanvas(Canvas canvas, float xDeg) {

        canvas.save();
        canvas.concat(matrixUp45);
        canvas.drawText(items[0], 0, 0, paint);
        canvas.restore();

        canvas.save();
        canvas.concat(matrixUp30);
        canvas.drawText(items[1], 0, 0, paint);
        canvas.restore();

        canvas.save();
        canvas.concat(matrixUp15);
        canvas.drawText(items[2], 0, 0, paint);
        canvas.restore();

        canvas.save();
        canvas.concat(matrixFront);
        canvas.drawText(items[3], 0, 0, paint);
        canvas.restore();

        canvas.save();
        canvas.concat(matrixDown15);
        canvas.drawText(items[4], 0, 0, paint);
        canvas.restore();

        canvas.save();
        canvas.concat(matrixDown30);
        canvas.drawText(items[5], 0, 0, paint);
        canvas.restore();

        canvas.save();
        canvas.concat(matrixDown45);
        canvas.drawText(items[6], 0, 0, paint);
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animHandler != null) {
            animHandler.removeCallbacksAndMessages(null);
        }
    }

    public interface StateValueListener {
        void stateValue(float distanceX, float distanceY, float rotateDegree, float cameraZtranslate);
    }

    private StateValueListener stateValueListener;

    public void setStateValueListener(StateValueListener stateValueListener) {
        this.stateValueListener = stateValueListener;
    }
}
