package com.ab.droid.jpref;

import com.ab.droid.jpref.util.DUtil;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.widget.Button;

// a Button drawn over buttonImage, the text as it is, black
@SuppressLint("AppCompatCustomView")
public class DButton extends Button {
    public static final String BUTTON_IMAGE = "/buttons/button.jpg";
    public static final int DISABLED_TEXT_COLOR = 0xFFCCCCCC;
    public static final int H_PADDING_DP = 12;
    private static Bitmap buttonImage;

    public DButton(MainActivity host) {
        super(host);
        style(host, this);
    }

    public static Bitmap getButtonImage(MainActivity host) {
        if (buttonImage == null) {
            buttonImage = ((DUtil) host.getUtil()).loadBitmap(BUTTON_IMAGE);
        }
        return buttonImage;
    }

    // for the buttons created elsewhere, e.g. by AlertDialog
    public static void style(MainActivity host, Button button) {
        button.setAllCaps(false);     // the text as it is, no capitals
        button.setTransformationMethod(null);
        button.setTextColor(new ColorStateList(
            new int[][]{{-android.R.attr.state_enabled}, {}},
            new int[]{DISABLED_TEXT_COLOR, Color.BLACK}));
        Bitmap image = getButtonImage(host);
        if (image == null) {
            button.setBackgroundColor(Color.WHITE);
        } else {
            button.setBackground(new BitmapDrawable(host.getResources(), image));
        }
        // a gap between the text and the image borders, the bitmap has no padding of its own
        int hPad = (int) (H_PADDING_DP * host.getResources().getDisplayMetrics().density);
        button.setPadding(hPad, button.getPaddingTop(), hPad, button.getPaddingBottom());
        // theme/AppCompat button styles can carry a default backgroundTint
        // that gets composited over the background Drawable
        button.setBackgroundTintList(null);
    }
}
