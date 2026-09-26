/*  This file is part of DPref project.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see [http://www.gnu.org/licenses/].
 *
 * Copyright (C) 2026 Alexander Bootman <ab.jpref@gmail.com>
 */
package com.ab.droid.jpref;

import com.ab.droid.jpref.config.DConfig;
import com.ab.droid.jpref.config.DMetrics;
import com.ab.droid.jpref.util.DUtil;
import com.ab.jpref.config.Config;
import com.ab.jpref.config.I18n;
import com.ab.jpref.ui.TableLayout;
import com.ab.util.Logger;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SettingsPopup {
    static final boolean DEBUG_LOG = false;

    private final MainActivity host;
    private final DConfig dConfig;
    private final DMetrics dMetrics;
    private final Bitmap buttonImage;
    private final Bitmap radioImage;
    private final Bitmap radioSelectedImage;
    private final int fontSize;
    // section labels/editors only, 30% smaller than the popup's base font
    private final float sectionFontSize;

    private final Dialog dialog;
    private final Button okButton;
    private final Button cancelButton;
    private final List<Runnable> commitActions = new ArrayList<>();
    // re-run whenever the active language changes (onVisualPropertyChanged())
    // to re-localize text that was only set once at construction - section
    // labels and, for Selection properties, each radio button's option text
    // (Swing's JList instead recomputes this on every repaint via its cell
    // renderer, so it never needed an explicit refresh list).
    private final List<Runnable> labelRefreshers = new ArrayList<>();

    public SettingsPopup(MainActivity host) {
        this.host = host;
        this.dConfig = host.config();
        this.dConfig.serialize();
        this.dMetrics = host.getMetrics();
        DUtil dUtil = (DUtil) host.getUtil();
        this.buttonImage = dUtil.loadBitmap("/buttons/button.jpg");
        this.radioImage = dUtil.loadBitmap("/buttons/radio.png");
        this.radioSelectedImage = dUtil.loadBitmap("/buttons/radio-sel.png");
        this.fontSize = (int) (dMetrics.cardW * .24);
        this.sectionFontSize = fontSize / 2f * 0.7f;

        dialog = new Dialog(host);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout settingsPanel = new LinearLayout(host);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        buildSettingsPanel(settingsPanel);
        ScrollView scrollView = new ScrollView(host);
        scrollView.addView(settingsPanel);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout buttonsPanel = new LinearLayout(host);
        buttonsPanel.setOrientation(LinearLayout.HORIZONTAL);
        buttonsPanel.setGravity(Gravity.CENTER);
        okButton = styledButton(I18n.m(TableLayout.ButtonCommand.ok.getName()));
        okButton.setOnClickListener(v -> save());
        okButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        okParams.rightMargin = fontSize / 3;
        buttonsPanel.addView(okButton, okParams);
        cancelButton = styledButton(I18n.m(TableLayout.ButtonCommand.cancel.getName()));
        cancelButton.setOnClickListener(v -> cancel());
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        buttonsPanel.addView(cancelButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(buttonsPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(root);
        dialog.show();
    }

    private void save() {
        for (Runnable commit : commitActions) {
            commit.run();
        }
        dConfig.serialize();
        dialog.dismiss();
    }

    private void cancel() {
        dConfig.refresh();   // restore configuration
        I18n.getInstance().refresh();
        host.repaintAll();
        dialog.dismiss();
    }

    private void buildSettingsPanel(LinearLayout settingsPanel) {
        try {
            Class<? extends DConfig> claz = dConfig.getClass();
            List<Field> fields = new ArrayList<>();
            for (Field field : claz.getFields()) {
                // including ColorProperty
                if (!field.getType().getName().endsWith("Property")) {
                    continue;
                }
                Config.Property<?> property = (Config.Property<?>) field.get(dConfig);
                if (property.getLabel().isEmpty()) {
                    continue;
                }
                fields.add(field);
            }
            // Class.getFields() enumeration order isn't guaranteed by the JLS and
            // isn't preserved by ART - show every property declared directly on
            // Config (shared across platforms) before any declared on a
            // platform-specific subclass (like DConfig); within each of those two
            // groups, fall back to Property's own declaration-order sequence
            // number so a newly added property still lands in the right spot
            // with no separate list to keep in sync. Grouping by declaring class
            // explicitly (rather than relying on Property's order alone) keeps
            // this correct even across a refresh(), which deserializes a
            // previously-saved Config and so can carry stale order values from
            // before a property was moved between classes.
            // List.sort()/Comparator.comparingInt() are API 24+ (as is the
            // java.util.function.ToIntFunction a method reference here would
            // desugar into) - minSdk is 23, so use the API 1-safe
            // Collections.sort() with a plain Comparator lambda instead.
            Collections.sort(fields, (f1, f2) -> {
                boolean base1 = f1.getDeclaringClass() == Config.class;
                boolean base2 = f2.getDeclaringClass() == Config.class;
                if (base1 != base2) {
                    return base1 ? -1 : 1;
                }
                try {
                    Config.Property<?> p1 = (Config.Property<?>) f1.get(dConfig);
                    Config.Property<?> p2 = (Config.Property<?>) f2.get(dConfig);
                    return Integer.compare(p1.getOrder(), p2.getOrder());
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
            for (Field field : fields) {
                Config.Property<?> property = (Config.Property<?>) field.get(dConfig);
                settingsPanel.addView(buildSection(property));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private View buildSection(Config.Property<?> property) {
        String label = property.getLabel();
        Object propValue = property.get();

        LinearLayout section = new LinearLayout(host);
        section.setOrientation(LinearLayout.VERTICAL);
        int pad = fontSize / 3;
        section.setPadding(pad, pad, pad, pad);

        TextView labelView = new TextView(host);
        labelView.setTextSize(sectionFontSize);
        labelView.setTextColor(Color.BLACK);
        labelView.setBackgroundColor(Color.rgb(0xD5, 0xEA, 0xFA));
        labelView.setPadding(pad, pad, pad, pad);
        labelView.setText(I18n.m(label));
        section.addView(labelView);
        labelRefreshers.add(() -> labelView.setText(I18n.m(label)));

        View editor;
        if (propValue instanceof Config.Selection) {
            editor = buildSelectionEditor(property, (Config.Selection<?>) propValue);
        } else if (propValue instanceof Integer) {
            editor = buildIntegerEditor((Config.Property<Integer>) property);
        } else {
            editor = null;
            Logger.printf(DEBUG_LOG, "%s -> %s\n", label, propValue);
        }
        if (editor != null) {
            section.addView(editor);
        }

        View divider = new View(host);
        divider.setBackgroundColor(Color.LTGRAY);
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
        dividerParams.topMargin = pad;
        section.addView(divider, dividerParams);

        return section;
    }

    private View buildSelectionEditor(Config.Property<?> property, Config.Selection<?> selection) {
        RadioGroup radioGroup = new RadioGroup(host);
        radioGroup.setOrientation(RadioGroup.VERTICAL);
        // the rows otherwise sit flush against each other with no breathing room -
        // space them out like the other editors' paddings (pad = fontSize/3)
        int rowGap = fontSize / 3;
        for (int i = 0; i < selection.values.length; ++i) {
            Object value = selection.values[i];
            RadioButton radioButton = new RadioButton(host);
            radioButton.setId(i);
            radioButton.setTextSize(sectionFontSize);
            radioButton.setTextColor(Color.BLACK);
            radioButton.setText(localizeSelectionText(value));
            radioButton.setButtonDrawable(radioDrawable());
            // the option text is only set here, at construction time - without
            // this, switching languages (isVisual()==true) would re-localize
            // the section label (see buildSection()) but leave every radio
            // button's own text frozen in whatever language was active when
            // the popup was built.
            labelRefreshers.add(() -> radioButton.setText(localizeSelectionText(value)));
            RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.topMargin = rowGap;
            }
            radioGroup.addView(radioButton, lp);
        }
        radioGroup.check(selection.getSelected());
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            selection.setSelected(checkedId);
            if (property.isVisual()) {
                onVisualPropertyChanged();
            }
        });
        return radioGroup;
    }

    // String.join() is API 26+ (minSdk is 23) - TextUtils.join() does the
    // same thing and has been available since API 1.
    private String localizeSelectionText(Object value) {
        return I18n.m(TextUtils.join(" ",
                value.toString().split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")));
    }

    private StateListDrawable radioDrawable() {
        int size = fontSize / 2;
        StateListDrawable drawable = new StateListDrawable();
        if (radioSelectedImage != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(radioSelectedImage, size, size, true);
            drawable.addState(new int[]{android.R.attr.state_checked},
                    new BitmapDrawable(host.getResources(), scaled));
        }
        if (radioImage != null) {
            Bitmap scaled = Bitmap.createScaledBitmap(radioImage, size, size, true);
            drawable.addState(new int[]{}, new BitmapDrawable(host.getResources(), scaled));
        }
        return drawable;
    }

    private View buildIntegerEditor(Config.Property<Integer> property) {
        EditText editText = new EditText(host);
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setTextSize(sectionFontSize);
        editText.setTextColor(Color.BLACK);
        // solid background, not just the default underline, so an editable
        // field is visually obvious next to the plain-white dialog and labels
        editText.setBackgroundColor(Color.rgb(0xDF, 0xF5, 0xDF));
        int editPad = fontSize / 4;
        editText.setPadding(editPad, editPad, editPad, editPad);
        editText.setText(String.valueOf(property.get()));
        editText.setOnClickListener(v -> showKeyboard(editText));
        commitActions.add(() -> {
            String text = editText.getText().toString();
            if (!text.isEmpty()) {
                property.set(Integer.parseInt(text));
            }
        });
        return editText;
    }

    private void onVisualPropertyChanged() {
        I18n.getInstance().refresh();
        okButton.setText(I18n.m(TableLayout.ButtonCommand.ok.getName()));
        cancelButton.setText(I18n.m(TableLayout.ButtonCommand.cancel.getName()));
        for (Runnable refresher : labelRefreshers) {
            refresher.run();
        }
        host.repaintAll();
    }

    @SuppressWarnings("deprecation")
    private void showKeyboard(EditText editText) {
        editText.requestFocus();
        InputMethodManager imm = (InputMethodManager) host.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // SHOW_FORCED, not SHOW_IMPLICIT: on emulators/devices that report a
            // hardware keyboard, IMPLICIT leaves the field focused (a bare caret)
            // with no way to bring up the soft keyboard to dismiss it.
            imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED);
        }
    }

    private Button styledButton(String text) {
        Button button = new Button(host);
        if (buttonImage != null) {
            button.setBackground(new BitmapDrawable(host.getResources(), buttonImage));
        }
        button.setTransformationMethod(null);
        button.setTextColor(Color.BLACK);
        button.setTextSize(fontSize / 2f);
        button.setText(text);
        return button;
    }
}
