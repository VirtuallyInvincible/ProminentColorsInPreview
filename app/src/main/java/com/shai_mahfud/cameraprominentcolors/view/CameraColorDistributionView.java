/*
 All rights reserved to Shai Mahfud.
 */

package com.shai_mahfud.cameraprominentcolors.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.shai_mahfud.cameraprominentcolors.R;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

// The new (non-deprecated) Camera2 API is reported to be broken and people advice to stick with
// the older API
@SuppressWarnings("deprecation")
public class CameraColorDistributionView extends LinearLayout implements Camera.PreviewCallback {
    private static final int TIME_BETWEEN_COLOR_PROCESSING = 1000;
    private static final int MAX_POSSIBLE_PROMINENT_COLORS = 16;


    private long prevTime = -1;
    private ViewGroup root;
    private int numOfItems;

    // The onPreviewFrame() callback is called a lot. Better to allocate the data structures only
    // once and then simply clear them with clear() in every iteration instead of allocating a new
    // place in memory every time onPreviewFrame() is invoked and having the GC work continuously
    // to free the previously allocated blocks:
    private int transparent = android.R.color.transparent;
    private Integer[] mostProminentColors = {transparent, transparent, transparent, transparent,
            transparent};
    private String[] mostProminentPopDistribution = {"", "", "", "", ""};
    private Map<Integer, Integer> rgbToPop = new TreeMap<>();
    private double totalPop;
    private Handler updateUIHandler = new Handler(getContext().getMainLooper());
    private Runnable updateUITask = new Runnable() {
        @Override
        public void run() {
            if (root == null) {
                return;
            }

            for (int i = 0; i < numOfItems; i++) {
                ColorDistributionItem item = (ColorDistributionItem) root.getChildAt(i);
                item.setData(mostProminentColors[i], mostProminentPopDistribution[i]);
            }
        }
    };


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
    public void onPreviewFrame(byte[] data, Camera camera) {
        // Avoid refreshing the colors too often:
        long curTime = System.nanoTime();
        if (curTime < prevTime + TIME_BETWEEN_COLOR_PROCESSING) {
            return;
        }
        prevTime = curTime;

        Bitmap bitmap = decodePreviewToBitmap(data, camera);
        if (bitmap == null) {
            return;
        }

        Palette.from(bitmap).generate(new Palette.PaletteAsyncListener() {
            public void onGenerated(Palette palette) {
                initDataStructures();
                calcMostProminentData(palette.getSwatches());
                // TODO: Consider using ViewModel and LiveData to update the RecyclerView data when
                // it changes instead of using a Handler and a Runnable from within this callback.
                // For a neater solution, all the calculations and this callback should perhaps be
                // moved to the ViewModel class. This will also make the data updating process
                // lifecycle aware, because LiveData is lifecycle aware.
                updateUIHandler.post(updateUITask);
            }
        });
    }

    void insertItems(Context ctx, int numOfItems) {
        this.numOfItems = (numOfItems <= MAX_POSSIBLE_PROMINENT_COLORS ? numOfItems :
                MAX_POSSIBLE_PROMINENT_COLORS);
        // TODO: Use RecyclerView instead of creating the Views in code. This will help supporting
        // more than 5 items by scrolling vertically.
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

    private Bitmap decodePreviewToBitmap(byte[] data, Camera camera) {
        Bitmap bitmap;
        if (camera == null) {
            return null;
        }
        Camera.Parameters parameters = camera.getParameters();
        if (parameters != null) {
            int format = parameters.getPreviewFormat();
            if (format == ImageFormat.NV21 || format == ImageFormat.YUY2 ||
                    format == ImageFormat.NV16) {
                int w = parameters.getPreviewSize().width;
                int h = parameters.getPreviewSize().height;
                YuvImage yuvImage = new YuvImage(data, format, w, h, null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, w, h), 100, out);
                byte[] compressed = out.toByteArray();
                bitmap = BitmapFactory.decodeByteArray(compressed, 0, compressed.length);
            }
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
}
