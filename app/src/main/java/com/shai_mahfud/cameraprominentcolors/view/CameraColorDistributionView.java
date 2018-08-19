/*
 All rights reserved to Shai Mahfud.
 */

package com.shai_mahfud.cameraprominentcolors.view;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.shai_mahfud.cameraprominentcolors.R;
import com.shai_mahfud.cameraprominentcolors.services.FrameDiagnosisService;

// The new (non-deprecated) Camera2 API is reported to be broken and people advice to stick with
// the older API
@SuppressWarnings("deprecation")
public class CameraColorDistributionView extends LinearLayout implements Camera.PreviewCallback {
    private class UpdateUIHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (root == null) {
                return;
            }

            Bundle bundle = msg.getData();
            int[] mostProminentColors = bundle.getIntArray(
                    FrameDiagnosisService.MESSAGE_KEY_MOST_PROMINENT_COLORS);
            String[] mostProminentPopDistribution =
                    bundle.getStringArray(
                            FrameDiagnosisService.MESSAGE_KEY_MOST_PROMINENT_POP_DISTRIBUTION);
            if (mostProminentColors == null || mostProminentPopDistribution == null) {
                return;
            }
            for (int i = 0; i < numOfItems; i++) {
                ColorDistributionItem item = (ColorDistributionItem) root.getChildAt(i);
                item.setData(mostProminentColors[i], mostProminentPopDistribution[i]);
            }
        }
    }


    private static final int MAX_POSSIBLE_PROMINENT_COLORS = 16;
    private static final int TIME_BETWEEN_COLOR_PROCESSING = 1000;


    private ViewGroup root;
    private int numOfItems;
    private long prevTime = -1;


    public CameraColorDistributionView(Context context) {
        this(context, null, -1,-1);
    }

    public CameraColorDistributionView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, -1, -1);
    }

    public CameraColorDistributionView(Context context, @Nullable AttributeSet attrs,
                                       int defStyleAttr) {
        this(context, attrs, defStyleAttr, -1);
    }

    public CameraColorDistributionView(Context context, AttributeSet attrs, int defStyleAttr,
                                       int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }


    @Override
    public synchronized void onPreviewFrame(byte[] data, Camera camera) {
        // Avoid refreshing the colors too often:
        long curTime = System.nanoTime();
        if (curTime < prevTime + TIME_BETWEEN_COLOR_PROCESSING ||
                FrameDiagnosisService.data != null) {
            return;
        }
        FrameDiagnosisService.data = data;
        prevTime = curTime;

        Intent intent = new Intent(getContext(), FrameDiagnosisService.class);
        Messenger messenger = new Messenger(new UpdateUIHandler());
        intent.putExtra(FrameDiagnosisService.KEY_MESSENGER, messenger);
        intent.putExtra(FrameDiagnosisService.KEY_NUM_OF_ITEMS, numOfItems);
        Camera.Parameters parameters = camera.getParameters();
        int frameFormat = parameters.getPreviewFormat();
        intent.putExtra(FrameDiagnosisService.KEY_CAMERA_PREVIEW_FRAME_FORMAT, frameFormat);
        int frameWidth = parameters.getPreviewSize().width;
        intent.putExtra(FrameDiagnosisService.KEY_CAMERA_PREVIEW_FRAME_WIDTH, frameWidth);
        int frameHeight = parameters.getPreviewSize().height;
        intent.putExtra(FrameDiagnosisService.KEY_CAMERA_PREVIEW_FRAME_HEIGHT, frameHeight);
        getContext().startService(intent);
    }

    void insertItems(Context ctx, int numOfItems) {
        this.numOfItems = (numOfItems <= MAX_POSSIBLE_PROMINENT_COLORS ? numOfItems :
                MAX_POSSIBLE_PROMINENT_COLORS);
        for (int i = 0; i < this.numOfItems; i++) {
            ColorDistributionItem item = new ColorDistributionItem(ctx);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0);
            lp.weight = 1;
            if (i < this.numOfItems - 1) {
                lp.bottomMargin = (int) ctx.getResources().getDimension(
                        R.dimen.color_distribution_item_margin_bottom);
            }
            item.setLayoutParams(lp);
            root.addView(item);
        }
    }

    private void init(Context ctx) {
        LayoutInflater li = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (li == null) {
            return;
        }
        li.inflate(R.layout.camera_color_distribution_view, this);
        root = (ViewGroup) getChildAt(0);
    }
}
