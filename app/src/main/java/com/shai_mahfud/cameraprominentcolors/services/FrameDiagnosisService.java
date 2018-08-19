/*
 All rights reserved to Shai Mahfud.
 */

package com.shai_mahfud.cameraprominentcolors.services;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class FrameDiagnosisService extends IntentService {
    /*
     * A simple class to hold data for the frame. I use it so I can pass a single parameter instead
     * of 3 parameters to my methods.
     */
    private class FrameData {
        private int format;
        private int width;
        private int height;
    }


    public static final String KEY_MESSENGER = "KEY_MESSENGER";
    public static final String KEY_NUM_OF_ITEMS = "KEY_NUM_OF_ITEMS";
    public static final String KEY_FRAME_DATA_KEY = "KEY_FRAME_DATA_KEY";
    public static final String KEY_CAMERA_PREVIEW_FRAME_FORMAT = "KEY_CAMERA_PREVIEW_FRAME_FORMAT";
    public static final String KEY_CAMERA_PREVIEW_FRAME_WIDTH = "KEY_CAMERA_PREVIEW_FRAME_WIDTH";
    public static final String KEY_CAMERA_PREVIEW_FRAME_HEIGHT = "KEY_CAMERA_PREVIEW_FRAME_HEIGHT";
    public static final String MESSAGE_KEY_MOST_PROMINENT_COLORS = "KEY_MOST_PROMINENT_COLORS";
    public static final String MESSAGE_KEY_MOST_PROMINENT_POP_DISTRIBUTION =
            "KEY_MOST_PROMINENT_POP_DISTRIBUTION";


    // The onPreviewFrame() callback is called a lot. Better to allocate the data structures only
    // once and then simply clear them with clear() in every iteration instead of allocating a new
    // place in memory every time onPreviewFrame() is invoked and having the GC work continuously
    // to free the previously allocated blocks:
    private int numOfItems;
    private int transparent = android.R.color.transparent;
    private int[] mostProminentColors = {transparent, transparent, transparent, transparent,
            transparent};
    private String[] mostProminentPopDistribution = {"", "", "", "", ""};
    private Map<Integer, Integer> rgbToPop = new TreeMap<>();
    private double totalPop;

    public static byte[] data;


    public FrameDiagnosisService() {
        super("FrameDiagnosisService");
    }


    @Override
    protected void onHandleIntent(@Nullable final Intent intent) {
        if (intent == null || data == null) {
            data = null;
            return;
        }

        this.numOfItems = intent.getIntExtra(KEY_NUM_OF_ITEMS, 1);

        int frameFormat = intent.getIntExtra(KEY_CAMERA_PREVIEW_FRAME_FORMAT, -1);
        int frameWidth = intent.getIntExtra(KEY_CAMERA_PREVIEW_FRAME_WIDTH, -1);
        int frameHeight = intent.getIntExtra(KEY_CAMERA_PREVIEW_FRAME_HEIGHT, -1);
        FrameData frameData = new FrameData();
        frameData.format = frameFormat;
        frameData.width = frameWidth;
        frameData.height = frameHeight;
        Bitmap bitmap = decodePreviewToBitmap(data, frameData);
        if (bitmap == null) {
            data = null;
            return;
        }

        Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
            public void onGenerated(Palette palette) {
                initDataStructures();
                calcMostProminentData(palette.getSwatches());
                Messenger messenger = intent.getParcelableExtra(KEY_MESSENGER);
                Message message = Message.obtain();
                Bundle bundle = new Bundle();
                bundle.putIntArray(MESSAGE_KEY_MOST_PROMINENT_COLORS, mostProminentColors);
                bundle.putStringArray(MESSAGE_KEY_MOST_PROMINENT_POP_DISTRIBUTION,
                        mostProminentPopDistribution);
                message.setData(bundle);
                try {
                    messenger.send(message);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } finally {
                    data = null;
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        data = null;
        super.onDestroy();
    }

    private Bitmap decodePreviewToBitmap(byte[] data, FrameData frameData) {
        Bitmap bitmap = null;
        if (frameData.format == ImageFormat.NV21 || frameData.format == ImageFormat.YUY2 ||
                frameData.format == ImageFormat.NV16) {
            int w = frameData.width;
            int h = frameData.height;
            YuvImage yuvImage = new YuvImage(data, frameData.format, w, h, null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, w, h), 100, out);
            byte[] compressed = out.toByteArray();
            bitmap = BitmapFactory.decodeByteArray(compressed, 0, compressed.length);
        }
        if (bitmap == null) {
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        return bitmap;
    }

    private void initDataStructures() {
        // Don't allocate new space in memory for the data structures. Simply override the data.
        // The onPreviewFrame() callback is called frequently.
        for (int i = 0; i < mostProminentColors.length; i++) {
            mostProminentColors[i] = transparent;
        }
        for (int i = 0; i < mostProminentPopDistribution.length; i++) {
            mostProminentPopDistribution[i] = "";
        }
        rgbToPop.clear();
        totalPop = 0.0;
    }

    private void calcMostProminentData(List<Palette.Swatch> swatches) {
        for (Palette.Swatch swatch : swatches) {
            int swatchPop = swatch.getPopulation();
            int rgb = swatch.getRgb();
            rgbToPop.put(rgb, (rgbToPop.containsKey(rgb)) ? rgbToPop.get(rgb) + swatchPop :
                    swatchPop);
            totalPop += swatchPop;
        }
        // Now there are no swatches that belong to the same RGB. Revert the mapping:
        int i = 0;
        while (i < numOfItems && !rgbToPop.isEmpty()) {
            int rgb = getMostProminent(rgbToPop);
            int pop = rgbToPop.get(rgb);
            rgbToPop.remove(rgb);
            mostProminentColors[i] = rgb;
            double popDistribution = (pop / totalPop) * 100;
            mostProminentPopDistribution[i] = String.format("%.2f", popDistribution) + "%";
            i++;
        }
    }

    private int getMostProminent(Map<Integer, Integer> rgbToPop) {
        int mostProminentRgb = -1;
        int maxPop = -1;
        Set<Integer> rgbs = rgbToPop.keySet();
        for (Integer rgb : rgbs) {
            int pop = rgbToPop.get(rgb);
            if (pop > maxPop) {
                maxPop = pop;
                mostProminentRgb = rgb;
            }
        }
        return mostProminentRgb;
    }
}
