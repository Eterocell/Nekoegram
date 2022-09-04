package com.eterocell.nekoegram;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;

import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import com.eterocell.nekoegram.helpers.MessageHelper;
import com.eterocell.nekoegram.settings.BaseNekoSettingsActivity;

@SuppressLint({"RtlHardcoded", "NotifyDataSetChanged"})
public class MessageDetailsActivity extends BaseNekoSettingsActivity implements NotificationCenter.NotificationCenterDelegate {

    private final MessageObject messageObject;
    private final boolean noforwards;

    private TLRPC.Chat toChat;
    private TLRPC.User fromUser;
    private TLRPC.Chat fromChat;
    private TLRPC.Peer forwardFromPeer;
    private String filePath;
    private String fileName;
    private int dc;
    private long stickerSetOwner;
    private Runnable unregisterFlagSecure;

    private int idRow;
    private int messageRow;
    private int captionRow;
    private int groupRow;
    private int channelRow;
    private int fromRow;
    private int botRow;
    private int dateRow;
    private int editedRow;
    private int forwardRow;
    private int fileNameRow;
    private int filePathRow;
    private int fileSizeRow;
    private int stickerSetRow;
    private int dcRow;
    private int restrictionReasonRow;
    private int forwardsRow;
    private int sponsoredRow;
    private int shouldBlockMessageRow;
    private int languageRow;
    private int linkOrEmojiOnlyRow;
    private int endRow;

    public MessageDetailsActivity(MessageObject messageObject) {
        this.messageObject = messageObject;

        if (messageObject.messageOwner.peer_id != null) {
            var peer = messageObject.messageOwner.peer_id;
            if (peer.channel_id != 0 || peer.chat_id != 0) {
                toChat = getMessagesController().getChat(peer.channel_id != 0 ? peer.channel_id : peer.chat_id);
            }
        }

        if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.from_id != null) {
            forwardFromPeer = messageObject.messageOwner.fwd_from.from_id;
        }

        if (messageObject.messageOwner.from_id != null) {
            var peer = messageObject.messageOwner.from_id;
            if (peer.channel_id != 0 || peer.chat_id != 0) {
                fromChat = getMessagesController().getChat(peer.channel_id != 0 ? peer.channel_id : peer.chat_id);
            } else if (peer.user_id != 0) {
                fromUser = getMessagesController().getUser(peer.user_id);
            }
        }

        filePath = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(filePath)) {
            File temp = new File(filePath);
            if (!temp.exists()) {
                filePath = null;
            }
        }
        if (TextUtils.isEmpty(filePath)) {
            filePath = getFileLoader().getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(filePath);
            if (!temp.exists()) {
                filePath = null;
            }
        }
        if (TextUtils.isEmpty(filePath)) {
            filePath = getFileLoader().getPathToAttach(messageObject.getDocument(), true).toString();
            File temp = new File(filePath);
            if (!temp.isFile()) {
                filePath = null;
            }
        }

        if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.document != null) {
            for (var attribute : messageObject.messageOwner.media.document.attributes) {
                if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                    fileName = attribute.file_name;
                }
                if (NekoConfig.showHiddenFeature && attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    stickerSetOwner = Extra.getOwnerFromStickerSetId(attribute.stickerset.id);
                }
            }
        }

        if (messageObject.messageOwner.media != null) {
            if (messageObject.messageOwner.media.photo != null && messageObject.messageOwner.media.photo.dc_id > 0) {
                dc = messageObject.messageOwner.media.photo.dc_id;
            } else if (messageObject.messageOwner.media.document != null && messageObject.messageOwner.media.document.dc_id > 0) {
                dc = messageObject.messageOwner.media.document.dc_id;
            } else if (messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.photo != null && messageObject.messageOwner.media.webpage.photo.dc_id > 0) {
                dc = messageObject.messageOwner.media.webpage.photo.dc_id;
            } else if (messageObject.messageOwner.media.webpage != null && messageObject.messageOwner.media.webpage.document != null && messageObject.messageOwner.media.webpage.document.dc_id > 0) {
                dc = messageObject.messageOwner.media.webpage.document.dc_id;
            }
        }

        noforwards = getMessagesController().isChatNoForwards(toChat) || messageObject.messageOwner.noforwards;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        return true;
    }

    @Override
    protected boolean hasWhiteActionBar() {
        return false;
    }

    private void showNoForwards() {
        if (getMessagesController().isChatNoForwards(toChat)) {
            BulletinFactory.of(this).createErrorBulletin(toChat.broadcast ?
                    LocaleController.getString("ForwardsRestrictedInfoChannel", R.string.ForwardsRestrictedInfoChannel) :
                    LocaleController.getString("ForwardsRestrictedInfoGroup", R.string.ForwardsRestrictedInfoGroup)
            ).show();
        } else {
            BulletinFactory.of(this).createErrorBulletin(
                    LocaleController.getString("ForwardsRestrictedInfoBot", R.string.ForwardsRestrictedInfoBot)).show();
        }
    }

    @Override
    public View createView(Context context) {
        View fragmentView = super.createView(context);

        if (noforwards) {
            unregisterFlagSecure = AndroidUtilities.registerFlagSecure(getParentActivity().getWindow());
        }

        return fragmentView;
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == dcRow) {
            int dc = 0;
            if (messageObject.messageOwner.media.photo != null && messageObject.messageOwner.media.photo.dc_id > 0) {
                dc = messageObject.messageOwner.media.photo.dc_id;
            } else if (messageObject.messageOwner.media.document != null && messageObject.messageOwner.media.document.dc_id > 0) {
                dc = messageObject.messageOwner.media.document.dc_id;
            }
            presentFragment(new DatacenterActivity(dc));
        } else if (position != endRow) {
            if (!noforwards || !(position == messageRow || position == captionRow || position == filePathRow)) {
                TextDetailSettingsCell textCell = (TextDetailSettingsCell) view;
                AndroidUtilities.addToClipboard(textCell.getValueTextView().getText());
                BulletinFactory.of(this).createCopyBulletin(LocaleController.formatString("TextCopied", R.string.TextCopied)).show();
            } else {
                showNoForwards();
            }
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        if (position == filePathRow) {
            if (!noforwards) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                var uri = FileProvider.getUriForFile(getParentActivity(), ApplicationLoader.getApplicationId() + ".provider", new File(filePath));
                intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setDataAndType(uri, messageObject.getMimeType());
                startActivityForResult(Intent.createChooser(intent, LocaleController.getString("ShareFile", R.string.ShareFile)), 500);
            } else {
                showNoForwards();
            }
        } else if (position == channelRow || position == groupRow) {
            if (toChat != null) {
                Bundle args = new Bundle();
                args.putLong("chat_id", toChat.id);
                ProfileActivity fragment = new ProfileActivity(args);
                presentFragment(fragment);
            }
        } else if (position == fromRow) {
            Bundle args = new Bundle();
            if (fromChat != null) {
                args.putLong("chat_id", fromChat.id);
            } else if (fromUser != null) {
                args.putLong("user_id", fromUser.id);
            }
            ProfileActivity fragment = new ProfileActivity(args);
            presentFragment(fragment);
        } else if (position == forwardRow) {
            if (forwardFromPeer != null) {
                Bundle args = new Bundle();
                if (forwardFromPeer.channel_id != 0 || forwardFromPeer.chat_id != 0) {
                    args.putLong("chat_id", forwardFromPeer.channel_id != 0 ? forwardFromPeer.channel_id : forwardFromPeer.chat_id);
                } else if (forwardFromPeer.user_id != 0) {
                    args.putLong("user_id", forwardFromPeer.user_id);
                }
                ProfileActivity fragment = new ProfileActivity(args);
                presentFragment(fragment);
            }
        } else if (position == restrictionReasonRow) {
            ArrayList<TLRPC.TL_restrictionReason> reasons = messageObject.messageOwner.restriction_reason;
            LinearLayout ll = new LinearLayout(getParentActivity());
            ll.setOrientation(LinearLayout.VERTICAL);

            AlertDialog dialog = new AlertDialog.Builder(getParentActivity(), resourcesProvider)
                    .setView(ll)
                    .create();

            for (TLRPC.TL_restrictionReason reason : reasons) {
                TextDetailSettingsCell cell = new TextDetailSettingsCell(getParentActivity(), resourcesProvider);
                cell.setBackground(Theme.getSelectorDrawable(false));
                cell.setMultilineDetail(true);
                cell.setOnClickListener(v1 -> {
                    dialog.dismiss();
                    AndroidUtilities.addToClipboard(cell.getValueTextView().getText());
                    BulletinFactory.of(this).createCopyBulletin(LocaleController.formatString("TextCopied", R.string.TextCopied)).show();
                });
                cell.setTextAndValue(reason.reason + "-" + reason.platform, reason.text, false);

                ll.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            }

            showDialog(dialog);
        } else if (position == stickerSetRow) {
            if (stickerSetOwner != 0) {
                Bundle args = new Bundle();
                args.putLong("user_id", stickerSetOwner);
                ProfileActivity fragment = new ProfileActivity(args);
                presentFragment(fragment);
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected String getActionBarTitle() {
        return LocaleController.getString("MessageDetails", R.string.MessageDetails);
    }

    @Override
    protected void updateRows() {
        rowCount = 0;
        idRow = messageObject.isSponsored() ? -1 : rowCount++;
        messageRow = TextUtils.isEmpty(messageObject.messageText) ? -1 : rowCount++;
        captionRow = TextUtils.isEmpty(messageObject.caption) ? -1 : rowCount++;
        groupRow = toChat != null && !toChat.broadcast ? rowCount++ : -1;
        channelRow = toChat != null && toChat.broadcast ? rowCount++ : -1;
        fromRow = fromUser != null || fromChat != null || messageObject.messageOwner.post_author != null ? rowCount++ : -1;
        botRow = fromUser != null && fromUser.bot ? rowCount++ : -1;
        dateRow = messageObject.messageOwner.date != 0 ? rowCount++ : -1;
        editedRow = messageObject.messageOwner.edit_date != 0 ? rowCount++ : -1;
        forwardRow = messageObject.isForwarded() ? rowCount++ : -1;
        fileNameRow = TextUtils.isEmpty(fileName) ? -1 : rowCount++;
        filePathRow = TextUtils.isEmpty(filePath) ? -1 : rowCount++;
        fileSizeRow = messageObject.getSize() != 0 ? rowCount++ : -1;
        stickerSetRow = stickerSetOwner == 0 ? -1 : rowCount++;
        dcRow = dc != 0 ? rowCount++ : -1;
        restrictionReasonRow = messageObject.messageOwner.restriction_reason.isEmpty() ? -1 : rowCount++;
        forwardsRow = messageObject.messageOwner.forwards > 0 ? rowCount++ : -1;
        sponsoredRow = messageObject.isSponsored() ? rowCount++ : -1;
        shouldBlockMessageRow = messageObject.shouldBlockMessage() ? rowCount++ : -1;
        languageRow = TextUtils.isEmpty(getMessageHelper().getMessagePlainText(messageObject)) ? -1 : rowCount++;
        linkOrEmojiOnlyRow = !TextUtils.isEmpty(messageObject.messageOwner.message) && getMessageHelper().isLinkOrEmojiOnlyMessage(messageObject) ? rowCount++ : -1;
        endRow = rowCount++;
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
        if (unregisterFlagSecure != null) {
            unregisterFlagSecure.run();
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
                    holder.itemView.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, getThemedColor(Theme.key_windowBackgroundGrayShadow)));
                    break;
                }
                case 6: {
                    TextDetailSettingsCell textCell = (TextDetailSettingsCell) holder.itemView;
                    textCell.setMultilineDetail(true);
                    boolean divider = position + 1 != endRow;
                    if (position == idRow) {
                        textCell.setTextAndValue("ID", String.valueOf(messageObject.messageOwner.id), divider);
                    } else if (position == messageRow) {
                        textCell.setTextAndValue("Message", messageObject.messageText, divider);
                    } else if (position == captionRow) {
                        textCell.setTextAndValue("Caption", messageObject.caption, divider);
                    } else if (position == channelRow || position == groupRow) {
                        StringBuilder builder = new StringBuilder();
                        appendUserOrChat(toChat, builder);
                        textCell.setTextAndValue(position == channelRow ? "Channel" : "Group", builder.toString(), divider);
                    } else if (position == fromRow) {
                        StringBuilder builder = new StringBuilder();
                        if (fromUser != null) {
                            appendUserOrChat(fromUser, builder);
                        } else if (fromChat != null) {
                            appendUserOrChat(fromChat, builder);
                        } else if (!TextUtils.isEmpty(messageObject.messageOwner.post_author)) {
                            builder.append(messageObject.messageOwner.post_author);
                        }
                        textCell.setTextAndValue("From", builder.toString(), divider);
                    } else if (position == botRow) {
                        textCell.setTextAndValue("Bot", "Yes", divider);
                    } else if (position == dateRow) {
                        textCell.setTextAndValue(messageObject.scheduled ? "Scheduled date" : "Date", formatTime(messageObject.messageOwner.date), divider);
                    } else if (position == editedRow) {
                        textCell.setTextAndValue("Edited", formatTime(messageObject.messageOwner.edit_date), divider);
                    } else if (position == forwardRow) {
                        StringBuilder builder = new StringBuilder();
                        if (forwardFromPeer != null) {
                            if (forwardFromPeer.channel_id != 0 || forwardFromPeer.chat_id != 0) {
                                TLRPC.Chat chat = getMessagesController().getChat(forwardFromPeer.channel_id != 0 ? forwardFromPeer.channel_id : forwardFromPeer.chat_id);
                                appendUserOrChat(chat, builder);
                            } else if (forwardFromPeer.user_id != 0) {
                                TLRPC.User user = getMessagesController().getUser(forwardFromPeer.user_id);
                                appendUserOrChat(user, builder);
                            }
                        } else if (!TextUtils.isEmpty(messageObject.messageOwner.fwd_from.from_name)) {
                            builder.append(messageObject.messageOwner.fwd_from.from_name);
                        }
                        textCell.setTextAndValue("Forward from", builder.toString(), divider);
                    } else if (position == fileNameRow) {
                        textCell.setTextAndValue("File name", fileName, divider);
                    } else if (position == filePathRow) {
                        textCell.setTextAndValue("File path", filePath, divider);
                    } else if (position == fileSizeRow) {
                        textCell.setTextAndValue("File size", AndroidUtilities.formatFileSize(messageObject.getSize()), divider);
                    } else if (position == dcRow) {
                        textCell.setTextAndValue("DC", String.format(Locale.US, "DC%d %s, %s", dc, MessageHelper.getDCName(dc), MessageHelper.getDCLocation(dc)), divider);
                    } else if (position == restrictionReasonRow) {
                        ArrayList<TLRPC.TL_restrictionReason> reasons = messageObject.messageOwner.restriction_reason;
                        StringBuilder value = new StringBuilder();
                        for (TLRPC.TL_restrictionReason reason : reasons) {
                            value.append(reason.reason);
                            value.append("-");
                            value.append(reason.platform);
                            if (reasons.indexOf(reason) != reasons.size() - 1) {
                                value.append(", ");
                            }
                        }
                        textCell.setTextAndValue("Restriction reason", value.toString(), divider);
                    } else if (position == forwardsRow) {
                        textCell.setTextAndValue("Forwards", String.format(Locale.US, "%d", messageObject.messageOwner.forwards), divider);
                    } else if (position == sponsoredRow) {
                        textCell.setTextAndValue("Sponsored", "Yes", divider);
                    } else if (position == shouldBlockMessageRow) {
                        textCell.setTextAndValue("Blocked", "Yes", divider);
                    } else if (position == languageRow) {
                        textCell.setTextAndValue("Language", "Loading...", divider);
                        LanguageDetector.detectLanguage(
                                getMessageHelper().getMessagePlainText(messageObject),
                                lang -> textCell.setTextAndValue("Language", lang, divider),
                                e -> textCell.setTextAndValue("Language", e.getLocalizedMessage(), divider));
                    } else if (position == linkOrEmojiOnlyRow) {
                        textCell.setTextAndValue("Link or emoji only", "Yes", divider);
                    } else if (position == stickerSetRow) {
                        StringBuilder builder = new StringBuilder();
                        TLRPC.User user = getMessagesController().getUser(stickerSetOwner);
                        if (user != null) {
                            appendUserOrChat(user, builder);
                        } else {
                            getMessageHelper().searchUser(stickerSetOwner, user1 -> {
                                StringBuilder builder1 = new StringBuilder();
                                if (user1 != null) {
                                    appendUserOrChat(user1, builder1);
                                } else {
                                    builder1.append(stickerSetOwner);
                                }
                                textCell.setTextAndValue("Sticker Pack creator", builder1.toString(), divider);
                            });
                            builder.append("Loading...");
                            builder.append("\n");
                            builder.append(stickerSetOwner);
                        }
                        textCell.setTextAndValue("Sticker Pack creator", builder.toString(), divider);
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == endRow) {
                return 1;
            } else {
                return 6;
            }
        }

        private String formatTime(int timestamp) {
            if (timestamp == 0x7ffffffe) {
                return "When online";
            } else {
                return timestamp + "\n" + LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime, LocaleController.getInstance().formatterYear.format(new Date(timestamp * 1000L)), LocaleController.getInstance().formatterDayWithSeconds.format(new Date(timestamp * 1000L)));
            }
        }

        private void appendUserOrChat(TLObject object, StringBuilder builder) {
            if (object instanceof TLRPC.User) {
                TLRPC.User user = (TLRPC.User) object;
                builder.append(ContactsController.formatName(user.first_name, user.last_name));
                builder.append("\n");
                if (!TextUtils.isEmpty(user.username)) {
                    builder.append("@");
                    builder.append(user.username);
                    builder.append("\n");
                }
                builder.append(user.id);
            } else if (object instanceof TLRPC.Chat) {
                TLRPC.Chat chat = (TLRPC.Chat) object;
                builder.append(chat.title);
                builder.append("\n");
                if (!TextUtils.isEmpty(chat.username)) {
                    builder.append("@");
                    builder.append(chat.username);
                    builder.append("\n");
                }
                builder.append(chat.id);
            }
        }
    }
}
