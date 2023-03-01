package com.eterocell.nekoegram.translator.popupwrapper;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PopupSwipeBackLayout;

import com.eterocell.nekoegram.translator.Translator;

public class TranslatorSettingsPopupWrapper {

    public ActionBarPopupWindow.ActionBarPopupWindowLayout windowLayout;

    public TranslatorSettingsPopupWrapper(BaseFragment fragment, PopupSwipeBackLayout swipeBackLayout, long dialogId, int topicId, Theme.ResourcesProvider resourcesProvider) {
        var context = fragment.getParentActivity();
        windowLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(context, 0, resourcesProvider, ActionBarPopupWindow.ActionBarPopupWindowLayout.FLAG_USE_SWIPEBACK);
        windowLayout.setFitItems(true);

        if (swipeBackLayout != null) {
            var backItem = ActionBarMenuItem.addItem(windowLayout, R.drawable.msg_arrow_back, LocaleController.getString("Back", R.string.Back), false, resourcesProvider);
            backItem.setOnClickListener(view -> swipeBackLayout.closeForeground());
        }

        var items = new String[]{
                LocaleController.getString("TranslatorType", R.string.TranslatorType),
                LocaleController.getString("TranslationTarget", R.string.TranslationTarget),
                LocaleController.getString("TranslationProvider", R.string.TranslationProvider),
        };
        for (int i = 0; i < items.length; i++) {
            var item = ActionBarMenuItem.addItem(windowLayout, 0, items[i], false, resourcesProvider);
            item.setTag(i);
            item.setOnClickListener(view -> {
                switch ((Integer) view.getTag()) {
                    case 0:
                        Translator.showTranslatorTypeSelector(context, null, null, resourcesProvider);
                        break;
                    case 1:
                        Translator.showTranslationTargetSelector(fragment, null, null, false, resourcesProvider);
                        break;
                    case 2:
                        Translator.showTranslationProviderSelector(context, null, null, resourcesProvider);
                        break;
                }
            });
        }
    }
}
