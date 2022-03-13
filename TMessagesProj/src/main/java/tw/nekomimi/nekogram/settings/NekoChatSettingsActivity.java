package tw.nekomimi.nekogram.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.PopupHelper;

public class NekoChatSettingsActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private ActionBarMenuItem resetItem;
    private StickerSizeCell stickerSizeCell;

    private int stickerSizeHeaderRow;
    private int stickerSizeRow;
    private int stickerSize2Row;

    private int chatRow;
    private int ignoreBlockedRow;
    private int disablePhotoSideActionRow;
    private int hideKeyboardOnChatScrollRow;
    private int rearVideoMessagesRow;
    private int confirmAVRow;
    private int tryToOpenAllLinksInIVRow;
    private int disableProximityEventsRow;
    private int swipeToPiPRow;
    private int disableJumpToNextRow;
    private int disableGreetingStickerRow;
    private int disableVoiceMessageAutoPlayRow;
    private int autoPauseVideoRow;
    private int disableMarkdownByDefaultRow;
    private int doubleTapActionRow;
    private int messageMenuRow;
    private int chat2Row;

    private int foldersRow;
    private int showTabsOnForwardRow;
    private int hideAllTabRow;
    private int tabsTitleTypeRow;
    private int folders2Row;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        return true;
    }

    @Override
    public View createView(Context context) {
        View fragmentView = super.createView(context);
        actionBar.setTitle(LocaleController.getString("Chat", R.string.Chat));

        ActionBarMenu menu = actionBar.createMenu();
        resetItem = menu.addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(LocaleController.getString("ResetStickerSize", R.string.ResetStickerSize));
        resetItem.setVisibility(NekoConfig.stickerSize != 14.0f ? View.VISIBLE : View.GONE);
        resetItem.setTag(null);
        resetItem.setOnClickListener(v -> {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, false, 0.5f, true);
            ValueAnimator animator = ValueAnimator.ofFloat(NekoConfig.stickerSize, 14.0f);
            animator.setDuration(150);
            animator.addUpdateListener(valueAnimator -> {
                NekoConfig.setStickerSize((Float) valueAnimator.getAnimatedValue());
                stickerSizeCell.invalidate();
            });
            animator.start();
        });

        listAdapter = new ListAdapter(context);

        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (position == ignoreBlockedRow) {
                NekoConfig.toggleIgnoreBlocked();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.ignoreBlocked);
                }
            } else if (position == messageMenuRow) {
                showMessageMenuAlert();
            } else if (position == disablePhotoSideActionRow) {
                NekoConfig.toggleDisablePhotoSideAction();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.disablePhotoSideAction);
                }
            } else if (position == hideKeyboardOnChatScrollRow) {
                NekoConfig.toggleHideKeyboardOnChatScroll();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.hideKeyboardOnChatScroll);
                }
            } else if (position == showTabsOnForwardRow) {
                NekoConfig.toggleShowTabsOnForward();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.showTabsOnForward);
                }
            } else if (position == rearVideoMessagesRow) {
                NekoConfig.toggleRearVideoMessages();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.rearVideoMessages);
                }
            } else if (position == hideAllTabRow) {
                NekoConfig.toggleHideAllTab();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.hideAllTab);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
            } else if (position == tabsTitleTypeRow) {
                ArrayList<String> arrayList = new ArrayList<>();
                ArrayList<Integer> types = new ArrayList<>();
                arrayList.add(LocaleController.getString("TabTitleTypeText", R.string.TabTitleTypeText));
                types.add(NekoConfig.TITLE_TYPE_TEXT);
                arrayList.add(LocaleController.getString("TabTitleTypeIcon", R.string.TabTitleTypeIcon));
                types.add(NekoConfig.TITLE_TYPE_ICON);
                arrayList.add(LocaleController.getString("TabTitleTypeMix", R.string.TabTitleTypeMix));
                types.add(NekoConfig.TITLE_TYPE_MIX);
                PopupHelper.show(arrayList, LocaleController.getString("TabTitleType", R.string.TabTitleType), types.indexOf(NekoConfig.tabsTitleType), context, view, i -> {
                    NekoConfig.setTabsTitleType(types.get(i));
                    listAdapter.notifyItemChanged(tabsTitleTypeRow);
                    getNotificationCenter().postNotificationName(NotificationCenter.dialogFiltersUpdated);
                });
            } else if (position == confirmAVRow) {
                NekoConfig.toggleConfirmAVMessage();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.confirmAVMessage);
                }
            } else if (position == disableProximityEventsRow) {
                NekoConfig.toggleDisableProximityEvents();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.disableProximityEvents);
                }
                BulletinFactory.of(this).createSimpleBulletin(R.raw.chats_infotip, LocaleController.formatString("RestartAppToTakeEffect", R.string.RestartAppToTakeEffect)).show();
            } else if (position == tryToOpenAllLinksInIVRow) {
                NekoConfig.toggleTryToOpenAllLinksInIV();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.tryToOpenAllLinksInIV);
                }
            } else if (position == swipeToPiPRow) {
                NekoConfig.toggleSwipeToPiP();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.swipeToPiP);
                }
            } else if (position == autoPauseVideoRow) {
                NekoConfig.toggleAutoPauseVideo();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.autoPauseVideo);
                }
            } else if (position == disableJumpToNextRow) {
                NekoConfig.toggleDisableJumpToNextChannel();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.disableJumpToNextChannel);
                }
            } else if (position == disableGreetingStickerRow) {
                NekoConfig.toggleDisableGreetingSticker();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.disableGreetingSticker);
                }
            } else if (position == disableVoiceMessageAutoPlayRow) {
                NekoConfig.toggleDisableVoiceMessageAutoPlay();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.disableVoiceMessageAutoPlay);
                }
            } else if (position == doubleTapActionRow) {
                ArrayList<String> arrayList = new ArrayList<>();
                ArrayList<Integer> types = new ArrayList<>();
                arrayList.add(LocaleController.getString("Disable", R.string.Disable));
                types.add(NekoConfig.DOUBLE_TAP_ACTION_NONE);
                arrayList.add(LocaleController.getString("Reactions", R.string.Reactions));
                types.add(NekoConfig.DOUBLE_TAP_ACTION_REACTION);
                arrayList.add(LocaleController.getString("TranslateMessage", R.string.TranslateMessage));
                types.add(NekoConfig.DOUBLE_TAP_ACTION_TRANSLATE);
                arrayList.add(LocaleController.getString("Reply", R.string.Reply));
                types.add(NekoConfig.DOUBLE_TAP_ACTION_REPLY);
                arrayList.add(LocaleController.getString("AddToSavedMessages", R.string.AddToSavedMessages));
                types.add(NekoConfig.DOUBLE_TAP_ACTION_SAVE);
                arrayList.add(LocaleController.getString("Repeat", R.string.Repeat));
                types.add(NekoConfig.DOUBLE_TAP_ACTION_REPEAT);
                arrayList.add(LocaleController.getString("Edit", R.string.Edit));
                types.add(NekoConfig.DOUBLE_TAP_ACTION_EDIT);
                PopupHelper.show(arrayList, LocaleController.getString("DoubleTapAction", R.string.DoubleTapAction), types.indexOf(NekoConfig.doubleTapAction), context, view, i -> {
                    NekoConfig.setDoubleTapAction(types.get(i));
                    listAdapter.notifyItemChanged(doubleTapActionRow);
                });
            } else if (position == disableMarkdownByDefaultRow) {
                NekoConfig.toggleDisableMarkdownByDefault();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(NekoConfig.disableMarkdownByDefault);
                }
            }
        });

        return fragmentView;
    }

    @Override
    protected void updateRows() {
        rowCount = 0;
        stickerSizeHeaderRow = rowCount++;
        stickerSizeRow = rowCount++;
        stickerSize2Row = rowCount++;

        chatRow = rowCount++;
        ignoreBlockedRow = rowCount++;
        disablePhotoSideActionRow = rowCount++;
        hideKeyboardOnChatScrollRow = rowCount++;
        rearVideoMessagesRow = rowCount++;
        confirmAVRow = rowCount++;
        tryToOpenAllLinksInIVRow = rowCount++;
        disableProximityEventsRow = rowCount++;
        swipeToPiPRow = rowCount++;
        disableJumpToNextRow = rowCount++;
        disableGreetingStickerRow = rowCount++;
        disableVoiceMessageAutoPlayRow = rowCount++;
        autoPauseVideoRow = rowCount++;
        disableMarkdownByDefaultRow = rowCount++;
        doubleTapActionRow = rowCount++;
        messageMenuRow = rowCount++;
        chat2Row = rowCount++;

        foldersRow = rowCount++;
        showTabsOnForwardRow = rowCount++;
        hideAllTabRow = rowCount++;
        tabsTitleTypeRow = rowCount++;
        folders2Row = rowCount++;
    }

    private void showMessageMenuAlert() {
        if (getParentActivity() == null) {
            return;
        }
        Context context = getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("MessageMenu", R.string.MessageMenu));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout linearLayoutInviteContainer = new LinearLayout(context);
        linearLayoutInviteContainer.setOrientation(LinearLayout.VERTICAL);
        linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int count = 12;
        for (int a = 0; a < count; a++) {
            TextCheckCell textCell = new TextCheckCell(context);
            switch (a) {
                case 0: {
                    textCell.setTextAndCheck(LocaleController.getString("DeleteDownloadedFile", R.string.DeleteDownloadedFile), NekoConfig.showDeleteDownloadedFile, false);
                    break;
                }
                case 1: {
                    textCell.setTextAndCheck(LocaleController.getString("NoQuoteForward", R.string.NoQuoteForward), NekoConfig.showNoQuoteForward, false);
                    break;
                }
                case 2: {
                    textCell.setTextAndCheck(LocaleController.getString("AddToSavedMessages", R.string.AddToSavedMessages), NekoConfig.showAddToSavedMessages, false);
                    break;
                }
                case 3: {
                    textCell.setTextAndCheck(LocaleController.getString("Repeat", R.string.Repeat), NekoConfig.showRepeat, false);
                    break;
                }
                case 4: {
                    textCell.setTextAndCheck(LocaleController.getString("Prpr", R.string.Prpr), NekoConfig.showPrPr, false);
                    break;
                }
                case 5: {
                    textCell.setTextAndCheck(LocaleController.getString("ViewHistory", R.string.ViewHistory), NekoConfig.showViewHistory, false);
                    break;
                }
                case 6: {
                    textCell.setTextAndCheck(LocaleController.getString("TranslateMessage", R.string.TranslateMessage), NekoConfig.showTranslate, false);
                    break;
                }
                case 7: {
                    textCell.setTextAndCheck(LocaleController.getString("ReportChat", R.string.ReportChat), NekoConfig.showReport, false);
                    break;
                }
                case 8: {
                    textCell.setTextAndCheck(LocaleController.getString("EditAdminRights", R.string.EditAdminRights), NekoConfig.showAdminActions, false);
                    break;
                }
                case 9: {
                    textCell.setTextAndCheck(LocaleController.getString("ChangePermissions", R.string.ChangePermissions), NekoConfig.showChangePermissions, false);
                    break;
                }
                case 10: {
                    textCell.setTextAndCheck(LocaleController.getString("MessageDetails", R.string.MessageDetails), NekoConfig.showMessageDetails, false);
                    break;
                }
                case 11: {
                    textCell.setTextAndCheck(LocaleController.getString("CopyPhoto", R.string.CopyPhoto), NekoConfig.showCopyPhoto, false);
                    break;
                }
            }
            textCell.setTag(a);
            textCell.setBackground(Theme.getSelectorDrawable(false));
            linearLayoutInviteContainer.addView(textCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            textCell.setOnClickListener(v2 -> {
                Integer tag = (Integer) v2.getTag();
                switch (tag) {
                    case 0: {
                        NekoConfig.toggleShowDeleteDownloadedFile();
                        textCell.setChecked(NekoConfig.showDeleteDownloadedFile);
                        break;
                    }
                    case 1: {
                        NekoConfig.toggleShowNoQuoteForward();
                        textCell.setChecked(NekoConfig.showNoQuoteForward);
                        break;
                    }
                    case 2: {
                        NekoConfig.toggleShowAddToSavedMessages();
                        textCell.setChecked(NekoConfig.showAddToSavedMessages);
                        break;
                    }
                    case 3: {
                        NekoConfig.toggleShowRepeat();
                        textCell.setChecked(NekoConfig.showRepeat);
                        break;
                    }
                    case 4: {
                        NekoConfig.toggleShowPrPr();
                        textCell.setChecked(NekoConfig.showPrPr);
                        break;
                    }
                    case 5: {
                        NekoConfig.toggleShowViewHistory();
                        textCell.setChecked(NekoConfig.showViewHistory);
                        break;
                    }
                    case 6: {
                        NekoConfig.toggleShowTranslate();
                        textCell.setChecked(NekoConfig.showTranslate);
                        break;
                    }
                    case 7: {
                        NekoConfig.toggleShowReport();
                        textCell.setChecked(NekoConfig.showReport);
                        break;
                    }
                    case 8: {
                        NekoConfig.toggleShowAdminActions();
                        textCell.setChecked(NekoConfig.showAdminActions);
                        break;
                    }
                    case 9: {
                        NekoConfig.toggleShowChangePermissions();
                        textCell.setChecked(NekoConfig.showChangePermissions);
                        break;
                    }
                    case 10: {
                        NekoConfig.toggleShowMessageDetails();
                        textCell.setChecked(NekoConfig.showMessageDetails);
                        break;
                    }
                    case 11: {
                        NekoConfig.toggleShowCopyPhoto();
                        textCell.setChecked(NekoConfig.showCopyPhoto);
                        break;
                    }
                }
            });
        }
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setView(linearLayout);
        showDialog(builder.create());
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (listView != null) {
                listView.invalidateViews();
            }
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    private class StickerSizeCell extends FrameLayout {

        private final StickerSizePreviewMessagesCell messagesCell;
        private final SeekBarView sizeBar;
        private final int startStickerSize = 2;
        private final int endStickerSize = 20;

        private final TextPaint textPaint;
        private int lastWidth;

        public StickerSizeCell(Context context) {
            super(context);

            setWillNotDraw(false);

            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setTextSize(AndroidUtilities.dp(16));

            sizeBar = new SeekBarView(context);
            sizeBar.setReportChanges(true);
            sizeBar.setDelegate(new SeekBarView.SeekBarViewDelegate() {
                @Override
                public void onSeekBarDrag(boolean stop, float progress) {
                    sizeBar.getSeekBarAccessibilityDelegate().postAccessibilityEventRunnable(StickerSizeCell.this);
                    NekoConfig.setStickerSize(startStickerSize + (endStickerSize - startStickerSize) * progress);
                    StickerSizeCell.this.invalidate();
                    if (resetItem.getVisibility() != VISIBLE) {
                        AndroidUtilities.updateViewVisibilityAnimated(resetItem, true, 0.5f, true);
                    }
                }

                @Override
                public void onSeekBarPressed(boolean pressed) {

                }
            });
            sizeBar.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            addView(sizeBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.LEFT | Gravity.TOP, 9, 5, 43, 11));

            messagesCell = new StickerSizePreviewMessagesCell(context, parentLayout);
            messagesCell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            addView(messagesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 53, 0, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
            canvas.drawText(String.valueOf(Math.round(NekoConfig.stickerSize)), getMeasuredWidth() - AndroidUtilities.dp(39), AndroidUtilities.dp(28), textPaint);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (lastWidth != width) {
                sizeBar.setProgress((NekoConfig.stickerSize - startStickerSize) / (float) (endStickerSize - startStickerSize));
                lastWidth = width;
            }
        }

        @Override
        public void invalidate() {
            super.invalidate();
            lastWidth = -1;
            messagesCell.invalidate();
            sizeBar.invalidate();
        }

        @Override
        public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(event);
            sizeBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityEvent(this, event);
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            sizeBar.getSeekBarAccessibilityDelegate().onInitializeAccessibilityNodeInfoInternal(this, info);
        }

        @Override
        public boolean performAccessibilityAction(int action, Bundle arguments) {
            return super.performAccessibilityAction(action, arguments) || sizeBar.getSeekBarAccessibilityDelegate().performAccessibilityActionInternal(this, action, arguments);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    break;
                }
                case 2: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == stickerSizeRow) {
                        textCell.setTextAndValue(LocaleController.getString("StickerSize", R.string.StickerSize), String.valueOf(Math.round(NekoConfig.stickerSize)), true);
                    } else if (position == messageMenuRow) {
                        textCell.setText(LocaleController.getString("MessageMenu", R.string.MessageMenu), false);
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
                        textCell.setTextAndValue(LocaleController.getString("TabTitleType", R.string.TabTitleType), value, false);
                    } else if (position == doubleTapActionRow) {
                        String value;
                        switch (NekoConfig.doubleTapAction) {
                            case NekoConfig.DOUBLE_TAP_ACTION_REACTION:
                                value = LocaleController.getString("Reactions", R.string.Reactions);
                                break;
                            case NekoConfig.DOUBLE_TAP_ACTION_TRANSLATE:
                                value = LocaleController.getString("TranslateMessage", R.string.TranslateMessage);
                                break;
                            case NekoConfig.DOUBLE_TAP_ACTION_REPLY:
                                value = LocaleController.getString("Reply", R.string.Reply);
                                break;
                            case NekoConfig.DOUBLE_TAP_ACTION_SAVE:
                                value = LocaleController.getString("AddToSavedMessages", R.string.AddToSavedMessages);
                                break;
                            case NekoConfig.DOUBLE_TAP_ACTION_REPEAT:
                                value = LocaleController.getString("Repeat", R.string.Repeat);
                                break;
                            case NekoConfig.DOUBLE_TAP_ACTION_EDIT:
                                value = LocaleController.getString("Edit", R.string.Edit);
                                break;
                            case NekoConfig.DOUBLE_TAP_ACTION_NONE:
                            default:
                                value = LocaleController.getString("Disable", R.string.Disable);
                        }
                        textCell.setTextAndValue(LocaleController.getString("DoubleTapAction", R.string.DoubleTapAction), value, true);
                    }
                    break;
                }
                case 3: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    textCell.setEnabled(true, null);
                    if (position == ignoreBlockedRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("IgnoreBlocked", R.string.IgnoreBlocked), LocaleController.getString("IgnoreBlockedAbout", R.string.IgnoreBlockedAbout), NekoConfig.ignoreBlocked, true, true);
                    } else if (position == disablePhotoSideActionRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisablePhotoViewerSideAction", R.string.DisablePhotoViewerSideAction), NekoConfig.disablePhotoSideAction, true);
                    } else if (position == hideKeyboardOnChatScrollRow) {
                        textCell.setTextAndCheck(LocaleController.getString("HideKeyboardOnChatScroll", R.string.HideKeyboardOnChatScroll), NekoConfig.hideKeyboardOnChatScroll, true);
                    } else if (position == showTabsOnForwardRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ShowTabsOnForward", R.string.ShowTabsOnForward), NekoConfig.showTabsOnForward, true);
                    } else if (position == rearVideoMessagesRow) {
                        textCell.setTextAndCheck(LocaleController.getString("RearVideoMessages", R.string.RearVideoMessages), NekoConfig.rearVideoMessages, true);
                    } else if (position == hideAllTabRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("HideAllTab", R.string.HideAllTab), LocaleController.getString("HideAllTabAbout", R.string.HideAllTabAbout), NekoConfig.hideAllTab, true, true);
                    } else if (position == confirmAVRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ConfirmAVMessage", R.string.ConfirmAVMessage), NekoConfig.confirmAVMessage, true);
                    } else if (position == disableProximityEventsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableProximityEvents", R.string.DisableProximityEvents), NekoConfig.disableProximityEvents, true);
                    } else if (position == tryToOpenAllLinksInIVRow) {
                        textCell.setTextAndCheck(LocaleController.getString("OpenAllLinksInInstantView", R.string.OpenAllLinksInInstantView), NekoConfig.tryToOpenAllLinksInIV, true);
                    } else if (position == swipeToPiPRow) {
                        textCell.setTextAndCheck(LocaleController.getString("SwipeToPiP", R.string.SwipeToPiP), NekoConfig.swipeToPiP, true);
                    } else if (position == autoPauseVideoRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("AutoPauseVideo", R.string.AutoPauseVideo), LocaleController.getString("AutoPauseVideoAbout", R.string.AutoPauseVideoAbout), NekoConfig.autoPauseVideo, true, true);
                    } else if (position == disableJumpToNextRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableJumpToNextChannel", R.string.DisableJumpToNextChannel), NekoConfig.disableJumpToNextChannel, true);
                    } else if (position == disableGreetingStickerRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableGreetingSticker", R.string.DisableGreetingSticker), NekoConfig.disableGreetingSticker, true);
                    } else if (position == disableVoiceMessageAutoPlayRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableVoiceMessagesAutoPlay", R.string.DisableVoiceMessagesAutoPlay), NekoConfig.disableVoiceMessageAutoPlay, true);
                    } else if (position == disableMarkdownByDefaultRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableMarkdownByDefault", R.string.DisableMarkdownByDefault), NekoConfig.disableMarkdownByDefault, true);
                    }
                    break;
                }
                case 4: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == chatRow) {
                        headerCell.setText(LocaleController.getString("Chat", R.string.Chat));
                    } else if (position == foldersRow) {
                        headerCell.setText(LocaleController.getString("Filters", R.string.Filters));
                    } else if (position == stickerSizeHeaderRow) {
                        headerCell.setText(LocaleController.getString("StickerSize", R.string.StickerSize));
                    }
                    break;
                }
                case 7: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == folders2Row) {
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.setText(LocaleController.getString("TabTitleTypeTip", R.string.TabTitleTypeTip));
                    }

                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 9) {
                stickerSizeCell = new StickerSizeCell(mContext);
                stickerSizeCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                stickerSizeCell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(stickerSizeCell);
            } else {
                return super.onCreateViewHolder(parent, viewType);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == chat2Row || position == stickerSize2Row) {
                return 1;
            } else if (position == messageMenuRow || position == tabsTitleTypeRow || position == doubleTapActionRow) {
                return 2;
            } else if (position == showTabsOnForwardRow || position == hideAllTabRow ||
                    (position > chatRow && position < messageMenuRow)) {
                return 3;
            } else if (position == chatRow || position == foldersRow || position == stickerSizeHeaderRow) {
                return 4;
            } else if (position == folders2Row) {
                return 7;
            } else if (position == stickerSizeRow) {
                return 9;
            }
            return 2;
        }
    }
}
