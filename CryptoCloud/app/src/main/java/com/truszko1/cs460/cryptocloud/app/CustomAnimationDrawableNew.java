package com.truszko1.cs460.cryptocloud.app;

import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;

public abstract class CustomAnimationDrawableNew extends AnimationDrawable {

    /**
     * Handles the animation callback.
     */
    Handler mAnimationHandler;

    public CustomAnimationDrawableNew(AnimationDrawable aniDrawable) {
        /* Add each frame to our animation drawable */
        super();
    }

    @Override
    public void start() {
        super.start();
        /*
         * Call super.start() to call the base class start animation method.
         * Then add a handler to call onAnimationFinish() when the total
         * duration for the animation has passed
         */
        mAnimationHandler = new Handler();
        mAnimationHandler.postDelayed(new Runnable() {

            public void run() {
                onAnimationFinish();
            }
        }, getTotalDuration());

    }

    /**
     * Gets the total duration of all frames.
     *
     * @return The total duration.
     */
    public int getTotalDuration() {

        int iDuration = 0;

        for (int i = 0; i < this.getNumberOfFrames(); i++) {
            iDuration += this.getDuration(i);
        }

        return iDuration;
    }

    /**
     * Called when the animation finishes.
     */
    abstract void onAnimationFinish();
}