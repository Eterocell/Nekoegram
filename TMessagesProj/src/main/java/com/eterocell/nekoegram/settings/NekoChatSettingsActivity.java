package com.eterocell.nekoegram.settings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextCheckbox2Cell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;

import com.eterocell.nekoegram.NekoConfig;
import com.eterocell.nekoegram.helpers.EntitiesHelper;
import com.eterocell.nekoegram.helpers.PopupHelper;
import com.eterocell.nekoegram.helpers.VoiceEnhancementsHelper;

public class NekoChatSettingsActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private ActionBarMenuItem resetItem;
    private StickerSizeCell stickerSizeCell;

    private int stickerSizeHeaderRow;
    private int stickerSizeRow;
    private int hideTimeOnStickerRow;
    private int stickerSize2Row;

    private int chatRow;
    private int ignoreBlockedRow;
    private int hideKeyboardOnChatScrollRow;
    private int tryToOpenAllLinksInIVRow;
    private int disableJumpToNextRow;
    private int disableGreetingStickerRow;
    private int doubleTapActionRow;
    private int maxRecentStickersRow;
    private int chat2Row;

    private int markdownRow;
    private int markdownEnableRow;
    private int markdownParserRow;
    private int markdownParseLinksRow;
    private int markdown2Row;

    private int mediaRow;
    private int voiceEnhancementsRow;
    private int disablePhotoSideActionRow;
    private int rearVideoMessagesRow;
    private int confirmAVRow;
    private int disableProximityEventsRow;
    private int disableVoiceMessageAutoPlayRow;
    private int autoPauseVideoRow;
    private int media2Row;

    private int messageMenuRow;
    private int messageMenu2Row;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        return true;
    }

    @Override
    public View createView(Context context) {
        View fragmentView = super.createView(context);

        ActionBarMenu menu = actionBar.createMenu();
        resetItem = menu.addItem(0, R.drawable.msg_reset);
        resetItem.setContentDescription(LocaleController.getString("ResetStickerSize", R.string.ResetStickerSize));
        resetItem.setTag(null);
        resetItem.setOnClickListener(v -> {
            AndroidUtilities.updateViewVisibilityAnimated(resetItem, false, 0.5f, true);
            ValueAnimator animator = ValueAnimator.ofFloat(NekoConfig.stickerSize, 14.0f);
            animator.setDuration(150);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.addUpdateListener(valueAnimator -> {
                NekoConfig.setStickerSize((Float) valueAnimator.getAnimatedValue());
                stickerSizeCell.invalidate();
            });
            animator.start();
        });
        AndroidUtilities.updateViewVisibilityAnimated(resetItem, NekoConfig.stickerSize != 14.0f, 1f, false);

        return fragmentView;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == ignoreBlockedRow) {
            NekoConfig.toggleIgnoreBlocked();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.ignoreBlocked);
            }
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
        } else if (position == rearVideoMessagesRow) {
            NekoConfig.toggleRearVideoMessages();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.rearVideoMessages);
            }
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
            showRestartBulletin();
        } else if (position == tryToOpenAllLinksInIVRow) {
            NekoConfig.toggleTryToOpenAllLinksInIV();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.tryToOpenAllLinksInIV);
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
            PopupHelper.show(arrayList, LocaleController.getString("DoubleTapAction", R.string.DoubleTapAction), types.indexOf(NekoConfig.doubleTapAction), getParentActivity(), view, i -> {
                NekoConfig.setDoubleTapAction(types.get(i));
                listAdapter.notifyItemChanged(doubleTapActionRow, PARTIAL);
            });
        } else if (position == markdownEnableRow) {
            NekoConfig.toggleDisableMarkdownByDefault();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(!NekoConfig.disableMarkdownByDefault);
            }
        } else if (position > messageMenuRow && position < messageMenu2Row) {
            TextCheckbox2Cell cell = ((TextCheckbox2Cell) view);
            int menuPosition = position - messageMenuRow - 1;
            if (menuPosition == 0) {
                NekoConfig.toggleShowDeleteDownloadedFile();
                cell.setChecked(NekoConfig.showDeleteDownloadedFile);
            } else if (menuPosition == 1) {
                NekoConfig.toggleShowNoQuoteForward();
                cell.setChecked(NekoConfig.showNoQuoteForward);
            } else if (menuPosition == 2) {
                NekoConfig.toggleShowAddToSavedMessages();
                cell.setChecked(NekoConfig.showAddToSavedMessages);
            } else if (menuPosition == 3) {
                NekoConfig.toggleShowRepeat();
                cell.setChecked(NekoConfig.showRepeat);
            } else if (menuPosition == 4) {
                NekoConfig.toggleShowPrPr();
                cell.setChecked(NekoConfig.showPrPr);
            } else if (menuPosition == 5) {
                NekoConfig.toggleShowViewHistory();
                cell.setChecked(NekoConfig.showViewHistory);
            } else if (menuPosition == 6) {
                NekoConfig.toggleShowTranslate();
                cell.setChecked(NekoConfig.showTranslate);
            } else if (menuPosition == 7) {
                NekoConfig.toggleShowReport();
                cell.setChecked(NekoConfig.showReport);
            } else if (menuPosition == 8) {
                NekoConfig.toggleShowAdminActions();
                cell.setChecked(NekoConfig.showAdminActions);
            } else if (menuPosition == 9) {
                NekoConfig.toggleShowChangePermissions();
                cell.setChecked(NekoConfig.showChangePermissions);
            } else if (menuPosition == 10) {
                NekoConfig.toggleShowMessageDetails();
                cell.setChecked(NekoConfig.showMessageDetails);
            } else if (menuPosition == 11) {
                NekoConfig.toggleShowCopyPhoto();
                cell.setChecked(NekoConfig.showCopyPhoto);
            } else if (menuPosition == 12) {
                NekoConfig.toggleShowSetReminder();
                cell.setChecked(NekoConfig.showSetReminder);
            }
        } else if (position == voiceEnhancementsRow) {
            NekoConfig.toggleVoiceEnhancements();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.voiceEnhancements);
            }
        } else if (position == maxRecentStickersRow) {
            int[] counts = {20, 30, 40, 50, 80, 100, 120, 150, 180, 200};
            ArrayList<String> types = new ArrayList<>();
            for (int count : counts) {
                if (count <= getMessagesController().maxRecentStickersCount) {
                    types.add(String.valueOf(count));
                }
            }
            PopupHelper.show(types, LocaleController.getString("MaxRecentStickers", R.string.MaxRecentStickers), types.indexOf(String.valueOf(NekoConfig.maxRecentStickers)), getParentActivity(), view, i -> {
                NekoConfig.setMaxRecentStickers(Integer.parseInt(types.get(i)));
                listAdapter.notifyItemChanged(maxRecentStickersRow, PARTIAL);
            });
        } else if (position == hideTimeOnStickerRow) {
            NekoConfig.toggleHideTimeOnSticker();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.hideTimeOnSticker);
            }
            stickerSizeCell.invalidate();
        } else if (position == markdownParserRow) {
            ArrayList<String> arrayList = new ArrayList<>();
            arrayList.add("Nekoegram");
            arrayList.add("Telegram");
            boolean oldParser = NekoConfig.newMarkdownParser;
            PopupHelper.show(arrayList, LocaleController.getString("DoubleTapAction", R.string.DoubleTapAction), NekoConfig.newMarkdownParser ? 0 : 1, getParentActivity(), view, i -> {
                NekoConfig.setNewMarkdownParser(i == 0);
                listAdapter.notifyItemChanged(markdownParserRow, PARTIAL);
                if (oldParser != NekoConfig.newMarkdownParser) {
                    if (oldParser) {
                        listAdapter.notifyItemRemoved(markdownParseLinksRow);
                        updateRows();
                    } else {
                        updateRows();
                        listAdapter.notifyItemInserted(markdownParseLinksRow);
                    }
                    listAdapter.notifyItemChanged(markdown2Row);
                }
            });
        } else if (position == markdownParseLinksRow) {
            NekoConfig.toggleMarkdownParseLinks();
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(NekoConfig.markdownParseLinks);
            }
            listAdapter.notifyItemChanged(markdown2Row);
        }
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("Chat", R.string.Chat);
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        stickerSizeHeaderRow = addRow("stickerSizeHeader");
        stickerSizeRow = addRow("stickerSize");
        hideTimeOnStickerRow = addRow("hideTimeOnSticker");
        stickerSize2Row = addRow();

        chatRow = addRow("chat");
        ignoreBlockedRow = addRow("ignoreBlocked");
        hideKeyboardOnChatScrollRow = addRow("hideKeyboardOnChatScroll");
        tryToOpenAllLinksInIVRow = addRow("tryToOpenAllLinksInIV");
        disableJumpToNextRow = addRow("disableJumpToNext");
        disableGreetingStickerRow = addRow("disableGreetingSticker");
        doubleTapActionRow = addRow("doubleTapAction");
        maxRecentStickersRow = addRow("maxRecentStickers");
        chat2Row = addRow();

        markdownRow = addRow("markdown");
        markdownEnableRow = addRow("markdownEnableRow");
        markdownParserRow = addRow("markdownParserRow");
        markdownParseLinksRow = NekoConfig.newMarkdownParser ? addRow("markdownParseLinksRow") : -1;
        markdown2Row = addRow();

        mediaRow = addRow("media");
        voiceEnhancementsRow = VoiceEnhancementsHelper.isAvailable() ? addRow("voiceEnhancements") : -1;
        disablePhotoSideActionRow = addRow("disablePhotoSideAction");
        rearVideoMessagesRow = addRow("rearVideoMessages");
        confirmAVRow = addRow("confirmAV");
        disableProximityEventsRow = addRow("disableProximityEvents");
        disableVoiceMessageAutoPlayRow = addRow("disableVoiceMessageAutoPlay");
        autoPauseVideoRow = addRow("autoPauseVideo");
        media2Row = addRow();

        messageMenuRow = addRow("messageMenu");
        addRow("showDeleteDownloadedFile");
        addRow("showNoQuoteForward");
        addRow("showAddToSavedMessages");
        addRow("showRepeat");
        addRow("showPrPr");
        addRow("showViewHistory");
        addRow("showTranslate");
        addRow("showReport");
        addRow("showAdminActions");
        addRow("showChangePermissions");
        addRow("showMessageDetails");
        addRow("showCopyPhoto");
        addRow("showSetReminder");
        messageMenu2Row = addRow();
    }

    @Override
    protected String getKey() {
        return "c";
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
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_SETTINGS: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == doubleTapActionRow) {
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
                        textCell.setTextAndValue(LocaleController.getString("DoubleTapAction", R.string.DoubleTapAction), value, partial, true);
                    } else if (position == maxRecentStickersRow) {
                        textCell.setTextAndValue(LocaleController.getString("MaxRecentStickers", R.string.MaxRecentStickers), String.valueOf(NekoConfig.maxRecentStickers), partial, false);
                    } else if (position == markdownParserRow) {
                        textCell.setTextAndValue(LocaleController.getString("MarkdownParser", R.string.MarkdownParser), NekoConfig.newMarkdownParser ? "Nekoegram" : "Telegram", partial, position + 1 != markdown2Row);
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell textCell = (TextCheckCell) holder.itemView;
                    textCell.setEnabled(true, null);
                    if (position == ignoreBlockedRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("IgnoreBlocked", R.string.IgnoreBlocked), LocaleController.getString("IgnoreBlockedAbout", R.string.IgnoreBlockedAbout), NekoConfig.ignoreBlocked, true, true);
                    } else if (position == disablePhotoSideActionRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisablePhotoViewerSideAction", R.string.DisablePhotoViewerSideAction), NekoConfig.disablePhotoSideAction, true);
                    } else if (position == hideKeyboardOnChatScrollRow) {
                        textCell.setTextAndCheck(LocaleController.getString("HideKeyboardOnChatScroll", R.string.HideKeyboardOnChatScroll), NekoConfig.hideKeyboardOnChatScroll, true);
                    } else if (position == rearVideoMessagesRow) {
                        textCell.setTextAndCheck(LocaleController.getString("RearVideoMessages", R.string.RearVideoMessages), NekoConfig.rearVideoMessages, true);
                    } else if (position == confirmAVRow) {
                        textCell.setTextAndCheck(LocaleController.getString("ConfirmAVMessage", R.string.ConfirmAVMessage), NekoConfig.confirmAVMessage, true);
                    } else if (position == disableProximityEventsRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableProximityEvents", R.string.DisableProximityEvents), NekoConfig.disableProximityEvents, true);
                    } else if (position == tryToOpenAllLinksInIVRow) {
                        textCell.setTextAndCheck(LocaleController.getString("OpenAllLinksInInstantView", R.string.OpenAllLinksInInstantView), NekoConfig.tryToOpenAllLinksInIV, true);
                    } else if (position == autoPauseVideoRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("AutoPauseVideo", R.string.AutoPauseVideo), LocaleController.getString("AutoPauseVideoAbout", R.string.AutoPauseVideoAbout), NekoConfig.autoPauseVideo, true, false);
                    } else if (position == disableJumpToNextRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableJumpToNextChannel", R.string.DisableJumpToNextChannel), NekoConfig.disableJumpToNextChannel, true);
                    } else if (position == disableGreetingStickerRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableGreetingSticker", R.string.DisableGreetingSticker), NekoConfig.disableGreetingSticker, true);
                    } else if (position == disableVoiceMessageAutoPlayRow) {
                        textCell.setTextAndCheck(LocaleController.getString("DisableVoiceMessagesAutoPlay", R.string.DisableVoiceMessagesAutoPlay), NekoConfig.disableVoiceMessageAutoPlay, true);
                    } else if (position == voiceEnhancementsRow) {
                        textCell.setTextAndValueAndCheck(LocaleController.getString("VoiceEnhancements", R.string.VoiceEnhancements), LocaleController.getString("VoiceEnhancementsAbout", R.string.VoiceEnhancementsAbout), NekoConfig.voiceEnhancements, true, true);
                    } else if (position == hideTimeOnStickerRow) {
                        textCell.setTextAndCheck(LocaleController.getString("HideTimeOnSticker", R.string.HideTimeOnSticker), NekoConfig.hideTimeOnSticker, false);
                    } else if (position == markdownEnableRow) {
                        textCell.setTextAndCheck(LocaleController.getString("MarkdownEnableByDefault", R.string.MarkdownEnableByDefault), !NekoConfig.disableMarkdownByDefault, true);
                    } else if (position == markdownParseLinksRow) {
                        textCell.setTextAndCheck(LocaleController.getString("MarkdownParseLinks", R.string.MarkdownParseLinks), NekoConfig.markdownParseLinks, false);
                    }
                    break;
                }
                case TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == chatRow) {
                        headerCell.setText(LocaleController.getString("Chat", R.string.Chat));
                    } else if (position == stickerSizeHeaderRow) {
                        headerCell.setText(LocaleController.getString("StickerSize", R.string.StickerSize));
                    } else if (position == messageMenuRow) {
                        headerCell.setText(LocaleController.getString("MessageMenu", R.string.MessageMenu));
                    } else if (position == mediaRow) {
                        headerCell.setText(LocaleController.getString("SharedMediaTab2", R.string.SharedMediaTab2));
                    } else if (position == markdownRow) {
                        headerCell.setText(LocaleController.getString("Markdown", R.string.Markdown));
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == markdown2Row) {
                        cell.getTextView().setMovementMethod(null);
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        cell.setText(TextUtils.expandTemplate(EntitiesHelper.parseMarkdown(NekoConfig.newMarkdownParser && NekoConfig.markdownParseLinks ? LocaleController.getString("MarkdownAbout", R.string.MarkdownAbout) : LocaleController.getString("MarkdownAbout2", R.string.MarkdownAbout2)), "**", "__", "~~", "`", "||", "[", "](", ")"));
                    }
                    break;
                }
                case TYPE_CHECKBOX: {
                    TextCheckbox2Cell cell = (TextCheckbox2Cell) holder.itemView;
                    int menuPosition = position - messageMenuRow - 1;
                    if (menuPosition == 0) {
                        cell.setTextAndCheck(LocaleController.getString("DeleteDownloadedFile", R.string.DeleteDownloadedFile), NekoConfig.showDeleteDownloadedFile, true);
                    } else if (menuPosition == 1) {
                        cell.setTextAndCheck(LocaleController.getString("NoQuoteForward", R.string.NoQuoteForward), NekoConfig.showNoQuoteForward, true);
                    } else if (menuPosition == 2) {
                        cell.setTextAndCheck(LocaleController.getString("AddToSavedMessages", R.string.AddToSavedMessages), NekoConfig.showAddToSavedMessages, true);
                    } else if (menuPosition == 3) {
                        cell.setTextAndCheck(LocaleController.getString("Repeat", R.string.Repeat), NekoConfig.showRepeat, true);
                    } else if (menuPosition == 4) {
                        cell.setTextAndCheck(LocaleController.getString("Prpr", R.string.Prpr), NekoConfig.showPrPr, true);
                    } else if (menuPosition == 5) {
                        cell.setTextAndCheck(LocaleController.getString("ViewHistory", R.string.ViewHistory), NekoConfig.showViewHistory, true);
                    } else if (menuPosition == 6) {
                        cell.setTextAndCheck(LocaleController.getString("TranslateMessage", R.string.TranslateMessage), NekoConfig.showTranslate, true);
                    } else if (menuPosition == 7) {
                        cell.setTextAndCheck(LocaleController.getString("ReportChat", R.string.ReportChat), NekoConfig.showReport, true);
                    } else if (menuPosition == 8) {
                        cell.setTextAndCheck(LocaleController.getString("EditAdminRights", R.string.EditAdminRights), NekoConfig.showAdminActions, true);
                    } else if (menuPosition == 9) {
                        cell.setTextAndCheck(LocaleController.getString("ChangePermissions", R.string.ChangePermissions), NekoConfig.showChangePermissions, true);
                    } else if (menuPosition == 10) {
                        cell.setTextAndCheck(LocaleController.getString("MessageDetails", R.string.MessageDetails), NekoConfig.showMessageDetails, true);
                    } else if (menuPosition == 11) {
                        cell.setTextAndCheck(LocaleController.getString("CopyPhoto", R.string.CopyPhoto), NekoConfig.showCopyPhoto, true);
                    } else if (menuPosition == 12) {
                        cell.setTextAndCheck(LocaleController.getString("SetReminder", R.string.SetReminder), NekoConfig.showSetReminder, false);
                    }
                    break;
                }
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == Integer.MAX_VALUE) {
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
            if (position == chat2Row || position == stickerSize2Row || position == messageMenu2Row || position == media2Row) {
                return TYPE_SHADOW;
            } else if (position == doubleTapActionRow || position == maxRecentStickersRow || position == markdownParserRow) {
                return TYPE_SETTINGS;
            } else if ((position > chatRow && position < doubleTapActionRow) ||
                    (position > mediaRow && position < media2Row) ||
                    (position > markdownRow && position < markdown2Row) ||
                    position == hideTimeOnStickerRow
            ) {
                return TYPE_CHECK;
            } else if (position == chatRow || position == stickerSizeHeaderRow || position == messageMenuRow || position == mediaRow || position == markdownRow) {
                return TYPE_HEADER;
            } else if (position == markdown2Row) {
                return TYPE_INFO_PRIVACY;
            } else if (position > messageMenuRow && position < messageMenu2Row) {
                return TYPE_CHECKBOX;
            } else if (position == stickerSizeRow) {
                return Integer.MAX_VALUE;
            }
            return TYPE_SETTINGS;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = super.getThemeDescriptions();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_PROGRESSBAR, new Class[]{StickerSizeCell.class}, new String[]{"sizeBar"}, null, null, null, Theme.key_player_progress));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, new String[]{"sizeBar"}, null, null, null, Theme.key_player_progressBackground));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, Theme.chat_msgInDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, Theme.chat_msgInMediaDrawable.getShadowDrawables(), null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient1));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleGradient3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubbleShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_messageTextIn));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_messageTextOut));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_inReplyLine));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_outReplyLine));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_inReplyNameText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_outReplyNameText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_inReplyMessageText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_outReplyMessageText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_inTimeText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_outTimeText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_inTimeSelectedText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSizeCell.class}, null, null, null, Theme.key_chat_outTimeSelectedText));
        return themeDescriptions;
    }
}
