/*
 All rights reserved to Shai Mahfud.
 */

package com.shai_mahfud.cameraprominentcolors.view;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.shai_mahfud.cameraprominentcolors.R;

public class ColorDistributionItem extends LinearLayout {
    private TextView colorBar, rgbField;


    public ColorDistributionItem(Context context) {
        this(context, null, -1, -1);
    }

    public ColorDistributionItem(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, -1, -1);
    }

    public ColorDistributionItem(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, -1);
    }

    public ColorDistributionItem(Context context, AttributeSet attrs, int defStyleAttr,
                                 int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }


    void setData(int color, String distribution) {
        String hexColor = String.format("%06X", (0xFFFFFF & color));
        String redHex = hexColor.substring(0, 2);
        String greenHex = hexColor.substring(2, 4);
        String blueHex = hexColor.substring(4);
        int red = Integer.parseInt(redHex, 16);
        int green = Integer.parseInt(greenHex, 16);
        int blue = Integer.parseInt(blueHex, 16);
        // Make the text that appears inside the rectangle differ from the color of the rectangle
        int rectangleTextColor = ((red < 50 && green < 50 && blue < 50) ? Color.WHITE : Color.BLACK);
        if (rectangleTextColor != colorBar.getCurrentTextColor()) {
            colorBar.setTextColor(rectangleTextColor);
        }

        colorBar.setBackgroundColor(color);
        colorBar.setText(distribution);
        rgbField.setText("R: " + red + ", G: " + green + ", B: " + blue);
    }

    private void init(Context ctx) {
        LayoutInflater li = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (li == null) {
            return;
        }
        li.inflate(R.layout.color_distribution_item, this);

        colorBar = findViewById(R.id.color_distribution_item_color_bar);
        rgbField = findViewById(R.id.color_distribution_item_rgb);
    }
}
