package com.eterocell.nekoegram.helpers;

import android.net.Uri;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;

import com.eterocell.nekoegram.settings.BaseNekoSettingsActivity;
import com.eterocell.nekoegram.settings.NekoAppearanceSettings;
import com.eterocell.nekoegram.settings.NekoChatSettingsActivity;
import com.eterocell.nekoegram.settings.NekoDonateActivity;
import com.eterocell.nekoegram.settings.NekoEmojiSettingsActivity;
import com.eterocell.nekoegram.settings.NekoExperimentalSettingsActivity;
import com.eterocell.nekoegram.settings.NekoGeneralSettingsActivity;
import com.eterocell.nekoegram.settings.NekoPasscodeSettingsActivity;
import com.eterocell.nekoegram.settings.NekoSettingsActivity;
import com.eterocell.nekoegram.settings.WsSettingsActivity;

public class SettingsHelper {

    public static void processDeepLink(Uri uri, Callback callback, Runnable unknown) {
        if (uri == null) {
            unknown.run();
            return;
        }
        var segments = uri.getPathSegments();
        if (segments.isEmpty() || segments.size() > 2 || !"nekosettings".equals(segments.get(0))) {
            unknown.run();
            return;
        }
        BaseNekoSettingsActivity fragment;
        if (segments.size() == 1) {
            fragment = new NekoSettingsActivity();
        } else if (PasscodeHelper.getSettingsKey().equals(segments.get(1))) {
            fragment = new NekoPasscodeSettingsActivity();
        } else {
            switch (segments.get(1)) {
                case "appearance":
                case "a":
                    fragment = new NekoAppearanceSettings();
                    break;
                case "chat":
                case "chats":
                case "c":
                    fragment = new NekoChatSettingsActivity();
                    break;
                case "donate":
                case "d":
                    fragment = new NekoDonateActivity();
                    break;
                case "experimental":
                case "e":
                    fragment = new NekoExperimentalSettingsActivity(false, false);
                    break;
                case "emoji":
                    fragment = new NekoEmojiSettingsActivity();
                    break;
                case "general":
                case "g":
                    fragment = new NekoGeneralSettingsActivity();
                    break;
                case "ws":
                case "w":
                    fragment = new WsSettingsActivity();
                    break;
                default:
                    unknown.run();
                    return;
            }
        }
        callback.presentFragment(fragment);
        var row = uri.getQueryParameter("r");
        if (TextUtils.isEmpty(row)) {
            row = uri.getQueryParameter("row");
        }
        if (!TextUtils.isEmpty(row)) {
            var rowFinal = row;
            AndroidUtilities.runOnUIThread(() -> fragment.scrollToRow(rowFinal, unknown));
        }

    }

    public interface Callback {
        void presentFragment(BaseFragment fragment);
    }
}
