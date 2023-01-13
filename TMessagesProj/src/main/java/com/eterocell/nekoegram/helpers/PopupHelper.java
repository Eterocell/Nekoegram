package com.eterocell.nekoegram.helpers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.view.View;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Components.AlertsCreator;

import java.util.ArrayList;

import com.eterocell.nekoegram.simplemenu.SimpleMenuPopupWindow;

public class PopupHelper {
    private static SimpleMenuPopupWindow mPopupWindow;

    public static void show(ArrayList<? extends CharSequence> entries, String title, int checkedIndex, Context context, View itemView, SimpleMenuPopupWindow.OnItemClickListener listener) {
        show(entries, title, checkedIndex, context, itemView, listener, null);
    }

    public static void show(ArrayList<? extends CharSequence> entries, String title, int checkedIndex, Context context, View itemView, SimpleMenuPopupWindow.OnItemClickListener listener, Theme.ResourcesProvider resourcesProvider) {
        if (itemView == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
            builder.setTitle(title);
            final LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            builder.setView(linearLayout);

            for (int a = 0; a < entries.size(); a++) {
                RadioColorCell cell = new RadioColorCell(context);
                cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                cell.setTag(a);
                cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, resourcesProvider), Theme.getColor(Theme.key_dialogRadioBackgroundChecked, resourcesProvider));
                cell.setTextAndValue(entries.get(a), checkedIndex == a);
                linearLayout.addView(cell);
                cell.setOnClickListener(v -> {
                    Integer which = (Integer) v.getTag();
                    builder.getDismissRunnable().run();
                    listener.onClick(which);
                });
            }
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.show();
        } else {
            View container = (View) itemView.getParent();
            if (container == null) {
                return;
            }
            if (mPopupWindow != null) {
                try {
                    if (mPopupWindow.isShowing()) mPopupWindow.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            mPopupWindow = new SimpleMenuPopupWindow(context);
            mPopupWindow.setOnItemClickListener(listener);
            mPopupWindow.setEntries(entries.toArray(new CharSequence[0]));
            mPopupWindow.setSelectedIndex(checkedIndex);

            mPopupWindow.show(itemView, container, 0);
        }
    }

    public static void showCopyPopup(BaseFragment fragment, CharSequence title, View anchorView, float x, float y, Runnable callback) {
        Context context = fragment.getParentActivity();
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, R.drawable.popup_fixed_alert, fragment.getResourceProvider()) {
            final Path path = new Path();

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                canvas.save();
                path.rewind();
                AndroidUtilities.rectTmp.set(child.getLeft(), child.getTop(), child.getRight(), child.getBottom());
                path.addRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(6), AndroidUtilities.dp(6), Path.Direction.CW);
                canvas.clipPath(path);
                boolean draw = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
                return draw;
            }
        };
        popupLayout.setFitItems(true);
        ActionBarPopupWindow popupWindow = AlertsCreator.createSimplePopup(fragment, popupLayout, anchorView, x, y);
        ActionBarMenuItem.addItem(popupLayout, R.drawable.msg_copy, title, false, fragment.getResourceProvider()).setOnClickListener(v -> {
            popupWindow.dismiss();
            callback.run();
        });
        popupLayout.setParentWindow(popupWindow);
    }
}
