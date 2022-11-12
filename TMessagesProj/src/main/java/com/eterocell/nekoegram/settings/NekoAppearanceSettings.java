package com.eterocell.nekoegram.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import com.eterocell.nekoegram.NekoConfig;
import com.eterocell.nekoegram.helpers.PopupHelper;

public class NekoAppearanceSettings extends BaseNekoSettingsActivity {

    private DrawerProfilePreviewCell profilePreviewCell;
    private ValueAnimator statusBarColorAnimator;

    private int drawerRow;
    private int avatarAsDrawerBackgroundRow;
    private int avatarBackgroundBlurRow;
    private int avatarBackgroundDarkenRow;
    private int hidePhoneRow;
    private int drawer2Row;

    private int appearanceRow;
    private int useSystemEmojiRow;
    private int transparentStatusBarRow;
    private int mediaPreviewRow;
    private int appBarShadowRow;
    private int formatTimeWithSecondsRow;
    private int disableNumberRoundingRow;
    private int newYearRow;
    private int tabletModeRow;
    private int eventTypeRow;
    private int appearance2Row;

    private int foldersRow;
    private int showTabsOnForwardRow;
    private int tabsTitleTypeRow;
    private int folders2Row;

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == hidePhoneRow) {
            NekoConfig.toggleHidePhone();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.hidePhone);
            }
            parentLayout.rebuildAllFragmentViews(false, false);
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            listAdapter.notifyItemChanged(drawerRow, PARTIAL);
        } else if (position == tabletModeRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString("TabletModeAuto", R.string.TabletModeAuto));
            types.add(NekoConfig.TABLET_AUTO);
            arrayList.add(LocaleController.getString("Enable", R.string.Enable));
            types.add(NekoConfig.TABLET_ENABLE);
            arrayList.add(LocaleController.getString("Disable", R.string.Disable));
            types.add(NekoConfig.TABLET_DISABLE);
            PopupHelper.show(arrayList, LocaleController.getString("TabletMode", R.string.TabletMode), types.indexOf(NekoConfig.tabletMode), getParentActivity(), view, i -> {
                NekoConfig.setTabletMode(types.get(i));
                listAdapter.notifyItemChanged(tabletModeRow, PARTIAL);
                showRestartBulletin();
            });
        } else if (position == transparentStatusBarRow) {
            SharedConfig.toggleNoStatusBar();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(SharedConfig.noStatusBar);
            }
            int alpha = isLightStatusBar() ? 0x0f : 0x33;
            if (statusBarColorAnimator != null && statusBarColorAnimator.isRunning()) {
                statusBarColorAnimator.end();
            }
            statusBarColorAnimator = SharedConfig.noStatusBar ? ValueAnimator.ofInt(alpha, 0) : ValueAnimator.ofInt(0, alpha);
            statusBarColorAnimator.setDuration(300);
            statusBarColorAnimator.addUpdateListener(animation -> getParentActivity().getWindow().setStatusBarColor(ColorUtils.setAlphaComponent(0, (int) animation.getAnimatedValue())));
            statusBarColorAnimator.start();
        } else if (position == useSystemEmojiRow) {
            NekoConfig.toggleUseSystemEmoji();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.useSystemEmoji);
            }
        } else if (position == eventTypeRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add(LocaleController.getString("DependsOnDate", R.string.DependsOnDate));
            arrayList.add(LocaleController.getString("Christmas", R.string.Christmas));
            arrayList.add(LocaleController.getString("Valentine", R.string.Valentine));
            arrayList.add(LocaleController.getString("Halloween", R.string.Halloween));
            PopupHelper.show(arrayList, LocaleController.getString("EventType", R.string.EventType), NekoConfig.eventType, getParentActivity(), view, i -> {
                NekoConfig.setEventType(i);
                listAdapter.notifyItemChanged(eventTypeRow, PARTIAL);
                getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            });
        } else if (position == newYearRow) {
            NekoConfig.toggleNewYear();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.newYear);
            }
            showRestartBulletin();
        } else if (position == avatarAsDrawerBackgroundRow) {
            NekoConfig.toggleAvatarAsDrawerBackground();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.avatarAsDrawerBackground);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            TransitionManager.beginDelayedTransition(profilePreviewCell);
            listAdapter.notifyItemChanged(drawerRow, PARTIAL);
            if (NekoConfig.avatarAsDrawerBackground) {
                updateRows();
                listAdapter.notifyItemRangeInserted(avatarBackgroundBlurRow, 2);
            } else {
                listAdapter.notifyItemRangeRemoved(avatarBackgroundBlurRow, 2);
                updateRows();
            }
        } else if (position == avatarBackgroundBlurRow) {
            NekoConfig.toggleAvatarBackgroundBlur();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.avatarBackgroundBlur);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            listAdapter.notifyItemChanged(drawerRow, PARTIAL);
        } else if (position == avatarBackgroundDarkenRow) {
            NekoConfig.toggleAvatarBackgroundDarken();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.avatarBackgroundDarken);
            }
            getNotificationCenter().postNotificationName(NotificationCenter.mainUserInfoChanged);
            listAdapter.notifyItemChanged(drawerRow, PARTIAL);
        } else if (position == disableNumberRoundingRow) {
            NekoConfig.toggleDisableNumberRounding();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.disableNumberRounding);
            }
        } else if (position == appBarShadowRow) {
            NekoConfig.toggleDisableAppBarShadow();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.disableAppBarShadow);
            }
            parentLayout.setHeaderShadow(NekoConfig.disableAppBarShadow ? null : parentLayout.getParentActivity().getDrawable(R.drawable.header_shadow).mutate());
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == mediaPreviewRow) {
            NekoConfig.toggleMediaPreview();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.mediaPreview);
            }
        } else if (position == formatTimeWithSecondsRow) {
            NekoConfig.toggleFormatTimeWithSeconds();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.formatTimeWithSeconds);
            }
            LocaleController.getInstance().recreateFormatters();
            parentLayout.rebuildAllFragmentViews(false, false);
        } else if (position == showTabsOnForwardRow) {
            NekoConfig.toggleShowTabsOnForward();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.showTabsOnForward);
            }
        } else if (position == tabsTitleTypeRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            ArrayList<Integer> types = new ArrayList<>();
            arrayList.add(LocaleController.getString("TabTitleTypeText", R.string.TabTitleTypeText));
            types.add(NekoConfig.TITLE_TYPE_TEXT);
            arrayList.add(LocaleController.getString("TabTitleTypeIcon", R.string.TabTitleTypeIcon));
            types.add(NekoConfig.TITLE_TYPE_ICON);
            arrayList.add(LocaleController.getString("TabTitleTypeMix", R.string.TabTitleTypeMix));
            types.add(NekoConfig.TITLE_TYPE_MIX);
            PopupHelper.show(arrayList, LocaleController.getString("TabTitleType", R.string.TabTitleType), types.indexOf(NekoConfig.tabsTitleType), getParentActivity(), view, i -> {
                NekoConfig.setTabsTitleType(types.get(i));
                listAdapter.notifyItemChanged(tabsTitleTypeRow, PARTIAL);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            });
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("Appearance", R.string.Appearance);
    }

    @Override
    protected String getKey() {
        return "a";
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        drawerRow = addRow("drawer");
        avatarAsDrawerBackgroundRow = addRow("avatarAsDrawerBackground");
        if (NekoConfig.avatarAsDrawerBackground) {
            avatarBackgroundBlurRow = addRow("avatarBackgroundBlur");
            avatarBackgroundDarkenRow = addRow("avatarBackgroundDarken");
        } else {
            avatarBackgroundBlurRow = -1;
            avatarBackgroundDarkenRow = -1;
        }
        hidePhoneRow = addRow("hidePhone");
        drawer2Row = addRow();

        appearanceRow = addRow("appearance");
        useSystemEmojiRow = addRow("useSystemEmoji");
        transparentStatusBarRow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? addRow("transparentStatusBar") : -1;
        mediaPreviewRow = addRow("mediaPreview");
        appBarShadowRow = addRow("appBarShadow");
        formatTimeWithSecondsRow = addRow("formatTimeWithSeconds");
        disableNumberRoundingRow = addRow("disableNumberRounding");
        newYearRow = NekoConfig.showHiddenFeature ? addRow("newYear") : -1;
        eventTypeRow = addRow("eventType");
        tabletModeRow = addRow("tabletMode");
        appearance2Row = addRow();

        foldersRow = addRow("folders");
        showTabsOnForwardRow = addRow("showTabsOnForward");
        tabsTitleTypeRow = addRow("tabsTitleType");
        folders2Row = addRow();
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case 2: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == eventTypeRow) {
                        String value;
                        switch (NekoConfig.eventType) {
                            case 1:
                                value = LocaleController.getString("Christmas", R.string.Christmas);
                                break;
                            case 2:
                                value = LocaleController.getString("Valentine", R.string.Valentine);
                                break;
                            case 3:
                                value = LocaleController.getString("Halloween", R.string.Halloween);
                                break;
                            case 0:
                            default:
                                value = LocaleController.getString("DependsOnDate", R.string.DependsOnDate);
                        }
                        textCell.setTextAndValue(LocaleController.getString("EventType", R.string.EventType), value, partial, true);
                    } else if (position == tabsTitleTypeRow) {
                        String value;
                        switch (NekoConfig.tabsTitleType) {
                            case NekoConfig.TITLE_TYPE_TEXT:
                                value = LocaleController.getString("TabTitleTypeText", R.string.TabTitleTypeText);
                                break;
                            case NekoConfig.TITLE_TYPE_ICON:
                                value = LocaleController.getString("TabTitleTypeIcon", R.string.TabTitleTypeIcon);
                                break;
                            case NekoConfig.TITLE_TYPE_MIX:
                            default:
                                value = LocaleController.getString("TabTitleTypeMix", R.string.TabTitleTypeMix);
                        }
                        textCell.setTextAndValue(LocaleController.getString("TabTitleType", R.string.TabTitleType), value, partial, false);
                    } else if (position == tabletModeRow) {
                        String value;
                        switch (NekoConfig.tabletMode) {
                            case NekoConfig.TABLET_AUTO:
                                value = LocaleController.getString("TabletModeAuto", R.string.TabletModeAuto);
                                break;
                            case NekoConfig.TABLET_ENABLE:
                                value = LocaleController.getString("Enable", R.string.Enable);
                                break;
                            case NekoConfig.TABLET_DISABLE:
                            default:
                                value = LocaleController.getString("Disable", R.string.Disable);
                        }
                        textCell.setTextAndValue(LocaleController.getString("TabletMode", R.string.TabletMode), value, partial, false);
                    }
                    break;
                }
                case 3: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    textCell.setEnabled(true, null);
                    if (position == hidePhoneRow) {
                        textCell.setTextAndCheck(LocaleController.getString("HidePhone", R.string.HidePhone), NekoConfig.hidePhone, false);
                    } else if (position == transparentStatusBarRow) {
                        textCell.setTextAndCheck(LocaleController.getString("TransparentStatusBar", R.string.TransparentStatusBar), SharedConfig.noStatusBar, true);
                    } else if (position == useSystemEmojiRow) {
                        textCell.setTextAndCheck(LocaleController.getString("EmojiUseDefault", R.string.EmojiUseDefault), NekoConfig.useSystemEmoji, true);
                    } else if (position == newYearRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ChristmasHat", R.string.ChristmasHat), NekoConfig.newYear, true);
                    } else if (position == avatarAsDrawerBackgroundRow) {
                        textCell.setTextAndCheck(LocaleController.getString("AvatarAsBackground", R.string.AvatarAsBackground), NekoConfig.avatarAsDrawerBackground, true);
                    } else if (position == disableNumberRoundingRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("DisableNumberRounding", R.string.DisableNumberRounding), "4.8K -> 4777", NekoConfig.disableNumberRounding, true, true);
                    } else if (position == appBarShadowRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableAppBarShadow", R.string.DisableAppBarShadow), NekoConfig.disableAppBarShadow, true);
                    } else if (position == mediaPreviewRow) {
                        textCell.setTextAndCheck(LocaleController.getString("MediaPreview", R.string.MediaPreview), NekoConfig.mediaPreview, true);
                    } else if (position == formatTimeWithSecondsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("FormatWithSeconds", R.string.FormatWithSeconds), NekoConfig.formatTimeWithSeconds, true);
                    } else if (position == avatarBackgroundBlurRow) {
                        textCell.setTextAndCheck(LocaleController.getString("BlurAvatarBackground", R.string.BlurAvatarBackground), NekoConfig.avatarBackgroundBlur, true);
                    } else if (position == avatarBackgroundDarkenRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DarkenAvatarBackground", R.string.DarkenAvatarBackground), NekoConfig.avatarBackgroundDarken, true);
                    } else if (position == showTabsOnForwardRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ShowTabsOnForward", R.string.ShowTabsOnForward), NekoConfig.showTabsOnForward, true);
                    }
                    break;
                }
                case 4: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == appearanceRow) {
                        headerCell.setText(LocaleController.getString("Appearance", R.string.Appearance));
                    } else if (position == foldersRow) {
                        headerCell.setText(LocaleController.getString("Filters", R.string.Filters));
                    }
                    break;
                }
                case 7: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == folders2Row) {
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.setText(LocaleController.getString("TabTitleTypeTip", R.string.TabTitleTypeTip));
                    }
                    break;
                }
                case Integer.MAX_VALUE: {
                    DrawerProfilePreviewCell cell = (DrawerProfilePreviewCell) holder.itemView;
                    cell.setUser(getUserConfig().getCurrentUser(), false);
                    break;
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == Integer.MAX_VALUE) {
                profilePreviewCell = new DrawerProfilePreviewCell(mContext);
                profilePreviewCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                profilePreviewCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(profilePreviewCell);
            } else {
                return super.onCreateViewHolder(parent, viewType);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == appearance2Row || position == drawer2Row) {
                return 1;
            } else if (position == eventTypeRow || position == tabsTitleTypeRow || position == tabletModeRow) {
                return 2;
            } else if (position == newYearRow || position == showTabsOnForwardRow ||
                    (position > appearanceRow && position <= disableNumberRoundingRow) ||
                    (position > drawerRow && position < drawer2Row)) {
                return 3;
            } else if (position == appearanceRow || position == foldersRow) {
                return 4;
            } else if (position == folders2Row) {
                return 7;
            } else if (position == drawerRow) {
                return Integer.MAX_VALUE;
            }
            return 2;
        }
    }
}
