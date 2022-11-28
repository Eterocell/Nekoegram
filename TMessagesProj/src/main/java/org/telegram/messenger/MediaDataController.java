/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.ringtone.RingtoneDataStore;
import org.telegram.messenger.ringtone.RingtoneUploader;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Reactions.ReactionsEffectOverlay;
import org.telegram.ui.Components.StickerSetBulletinLayout;
import org.telegram.ui.Components.StickersArchiveAlert;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.eterocell.nekoegram.NekoConfig;
import com.eterocell.nekoegram.helpers.EntitiesHelper;
import com.eterocell.nekoegram.helpers.MessageHelper;

@SuppressWarnings("unchecked")
public class MediaDataController extends BaseController {
    public final static String ATTACH_MENU_BOT_ANIMATED_ICON_KEY = "android_animated",
            ATTACH_MENU_BOT_STATIC_ICON_KEY = "default_static",
            ATTACH_MENU_BOT_PLACEHOLDER_STATIC_KEY = "placeholder_static",
            ATTACH_MENU_BOT_COLOR_LIGHT_ICON = "light_icon",
            ATTACH_MENU_BOT_COLOR_LIGHT_TEXT = "light_text",
            ATTACH_MENU_BOT_COLOR_DARK_ICON = "dark_icon",
            ATTACH_MENU_BOT_COLOR_DARK_TEXT = "dark_text";

    private static Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*"),
            ITALIC_PATTERN = Pattern.compile("__(.+?)__"),
            SPOILER_PATTERN = Pattern.compile("\\|\\|(.+?)\\|\\|"),
            STRIKE_PATTERN = Pattern.compile("~~(.+?)~~");

    public static String SHORTCUT_CATEGORY = "org.telegram.messenger.SHORTCUT_SHARE";

    private static volatile MediaDataController[] Instance = new MediaDataController[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Object[] lockObjects = new Object[UserConfig.MAX_ACCOUNT_COUNT];

    static {
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            lockObjects[i] = new Object();
        }
    }


    public static MediaDataController getInstance(int num) {
        MediaDataController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (lockObjects) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new MediaDataController(num);
                }
            }
        }
        return localInstance;
    }

    public MediaDataController(int num) {
        super(num);

        if (currentAccount == 0) {
            draftPreferences = ApplicationLoader.applicationContext.getSharedPreferences("drafts", Activity.MODE_PRIVATE);
        } else {
            draftPreferences = ApplicationLoader.applicationContext.getSharedPreferences("drafts" + currentAccount, Activity.MODE_PRIVATE);
        }
        Map<String, ?> values = draftPreferences.getAll();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            try {
                String key = entry.getKey();
                long did = Utilities.parseLong(key);
                byte[] bytes = Utilities.hexToBytes((String) entry.getValue());
                SerializedData serializedData = new SerializedData(bytes);
                boolean isThread = false;
                if (key.startsWith("r_") || (isThread = key.startsWith("rt_"))) {
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                    if (message != null) {
                        message.readAttachPath(serializedData, getUserConfig().clientUserId);
                        SparseArray<TLRPC.Message> threads = draftMessages.get(did);
                        if (threads == null) {
                            threads = new SparseArray<>();
                            draftMessages.put(did, threads);
                        }
                        int threadId = isThread ? Utilities.parseInt(key.substring(key.lastIndexOf('_') + 1)) : 0;
                        threads.put(threadId, message);
                    }
                } else {
                    TLRPC.DraftMessage draftMessage = TLRPC.DraftMessage.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                    if (draftMessage != null) {
                        SparseArray<TLRPC.DraftMessage> threads = drafts.get(did);
                        if (threads == null) {
                            threads = new SparseArray<>();
                            drafts.put(did, threads);
                        }
                        int threadId = key.startsWith("t_") ? Utilities.parseInt(key.substring(key.lastIndexOf('_') + 1)) : 0;
                        threads.put(threadId, draftMessage);
                    }
                }
                serializedData.cleanup();
            } catch (Exception e) {
                //igonre
            }
        }

        loadStickersByEmojiOrName(AndroidUtilities.STICKERS_PLACEHOLDER_PACK_NAME, false, true);
        loadEmojiThemes();
        loadRecentAndTopReactions(false);
        ringtoneDataStore = new RingtoneDataStore(currentAccount);
    }

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_MASK = 1;
    public static final int TYPE_FAVE = 2;
    public static final int TYPE_FEATURED = 3;
    public static final int TYPE_EMOJI = 4;
    public static final int TYPE_EMOJIPACKS = 5;
    public static final int TYPE_FEATURED_EMOJIPACKS = 6;
    public static final int TYPE_PREMIUM_STICKERS = 7;

    public static final int TYPE_GREETINGS = 3;

    private long menuBotsUpdateHash;
    private TLRPC.TL_attachMenuBots attachMenuBots = new TLRPC.TL_attachMenuBots();
    private boolean isLoadingMenuBots;
    private int menuBotsUpdateDate;

    private int reactionsUpdateHash;
    private List<TLRPC.TL_availableReaction> reactionsList = new ArrayList<>();
    private List<TLRPC.TL_availableReaction> enabledReactionsList = new ArrayList<>();
    private HashMap<String, TLRPC.TL_availableReaction> reactionsMap = new HashMap<>();
    private String doubleTapReaction;
    private boolean isLoadingReactions;
    private int reactionsUpdateDate;
    private boolean reactionsCacheGenerated;

    private TLRPC.TL_help_premiumPromo premiumPromo;
    private boolean isLoadingPremiumPromo;
    private int premiumPromoUpdateDate;

    private ArrayList<TLRPC.TL_messages_stickerSet>[] stickerSets = new ArrayList[]{new ArrayList<>(), new ArrayList<>(), new ArrayList<>(0), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
    private LongSparseArray<TLRPC.Document>[] stickersByIds = new LongSparseArray[]{new LongSparseArray<>(), new LongSparseArray<>(), new LongSparseArray<>(), new LongSparseArray<>(), new LongSparseArray<>(), new LongSparseArray<>()};
    private LongSparseArray<TLRPC.TL_messages_stickerSet> stickerSetsById = new LongSparseArray<>();
    private LongSparseArray<TLRPC.TL_messages_stickerSet> installedStickerSetsById = new LongSparseArray<>();
    private ArrayList<Long> installedForceStickerSetsById = new ArrayList<>();
    private ArrayList<Long> uninstalledForceStickerSetsById = new ArrayList<>();
    private LongSparseArray<TLRPC.TL_messages_stickerSet> groupStickerSets = new LongSparseArray<>();
    private ConcurrentHashMap<String, TLRPC.TL_messages_stickerSet> stickerSetsByName = new ConcurrentHashMap<>(100, 1.0f, 1);
    private TLRPC.TL_messages_stickerSet stickerSetDefaultStatuses = null;
    private HashMap<String, TLRPC.TL_messages_stickerSet> diceStickerSetsByEmoji = new HashMap<>();
    private LongSparseArray<String> diceEmojiStickerSetsById = new LongSparseArray<>();
    private HashSet<String> loadingDiceStickerSets = new HashSet<>();
    private LongSparseArray<Runnable> removingStickerSetsUndos = new LongSparseArray<>();
    private Runnable[] scheduledLoadStickers = new Runnable[7];
    private boolean[] loadingStickers = new boolean[7];
    private boolean[] stickersLoaded = new boolean[7];
    private long[] loadHash = new long[7];
    private int[] loadDate = new int[7];
    public HashMap<String, RingtoneUploader> ringtoneUploaderHashMap = new HashMap<>();

    private HashMap<String, ArrayList<TLRPC.Message>> verifyingMessages = new HashMap<>();

    private int[] archivedStickersCount = new int[7];

    private LongSparseArray<String> stickersByEmoji = new LongSparseArray<>();
    private HashMap<String, ArrayList<TLRPC.Document>> allStickers = new HashMap<>();
    private HashMap<String, ArrayList<TLRPC.Document>> allStickersFeatured = new HashMap<>();

    private ArrayList<TLRPC.Document>[] recentStickers = new ArrayList[]{
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(),
    };
    private boolean[] loadingRecentStickers = new boolean[9];
    private boolean[] recentStickersLoaded = new boolean[9];

    private ArrayList<TLRPC.Document> recentGifs = new ArrayList<>();
    private boolean loadingRecentGifs;
    private boolean recentGifsLoaded;

    private boolean loadingPremiumGiftStickers;
    private boolean loadingGenericAnimations;
    private boolean loadingDefaultTopicIcons;

    private long loadFeaturedHash[] = new long[2];
    private int loadFeaturedDate[] = new int[2];
    public boolean loadFeaturedPremium;
    private ArrayList<TLRPC.StickerSetCovered>[] featuredStickerSets = new ArrayList[]{new ArrayList<>(), new ArrayList<>()};
    private LongSparseArray<TLRPC.StickerSetCovered>[] featuredStickerSetsById = new LongSparseArray[]{new LongSparseArray<>(), new LongSparseArray<>()};
    private ArrayList<Long> unreadStickerSets[] = new ArrayList[]{new ArrayList<Long>(), new ArrayList<Long>()};
    private ArrayList<Long> readingStickerSets[] = new ArrayList[]{new ArrayList<Long>(), new ArrayList<Long>()};
    private boolean loadingFeaturedStickers[] = new boolean[2];
    private boolean featuredStickersLoaded[] = new boolean[2];

    private TLRPC.Document greetingsSticker;
    public final RingtoneDataStore ringtoneDataStore;
    public final ArrayList<ChatThemeBottomSheet.ChatThemeItem> defaultEmojiThemes = new ArrayList<>();

    public final ArrayList<TLRPC.Document> premiumPreviewStickers = new ArrayList<>();
    boolean previewStickersLoading;

    private long[] emojiStatusesHash = new long[2];
    private ArrayList<TLRPC.EmojiStatus>[] emojiStatuses = new ArrayList[2];
    private Long[] emojiStatusesFetchDate = new Long[2];
    private boolean[] emojiStatusesFromCacheFetched = new boolean[2];
    private boolean[] emojiStatusesFetching = new boolean[2];

    public void cleanup() {
        for (int a = 0; a < recentStickers.length; a++) {
            if (recentStickers[a] != null) {
                recentStickers[a].clear();
            }
            loadingRecentStickers[a] = false;
            recentStickersLoaded[a] = false;
        }
        for (int a = 0; a < 4; a++) {
            loadHash[a] = 0;
            loadDate[a] = 0;
            stickerSets[a].clear();
            loadingStickers[a] = false;
            stickersLoaded[a] = false;
        }
        loadingPinnedMessages.clear();
        loadFeaturedDate[0] = 0;
        loadFeaturedHash[0] = 0;
        loadFeaturedDate[1] = 0;
        loadFeaturedHash[1] = 0;
        allStickers.clear();
        allStickersFeatured.clear();
        stickersByEmoji.clear();
        featuredStickerSetsById[0].clear();
        featuredStickerSets[0].clear();
        featuredStickerSetsById[1].clear();
        featuredStickerSets[1].clear();
        unreadStickerSets[0].clear();
        unreadStickerSets[1].clear();
        recentGifs.clear();
        stickerSetsById.clear();
        installedStickerSetsById.clear();
        stickerSetsByName.clear();
        diceStickerSetsByEmoji.clear();
        diceEmojiStickerSetsById.clear();
        loadingDiceStickerSets.clear();
        loadingFeaturedStickers[0] = false;
        featuredStickersLoaded[0] = false;
        loadingFeaturedStickers[1] = false;
        featuredStickersLoaded[1] = false;
        loadingRecentGifs = false;
        recentGifsLoaded = false;

        currentFetchingEmoji.clear();
        if (Build.VERSION.SDK_INT >= 25) {
            Utilities.globalQueue.postRunnable(() -> {
                try {
                    ShortcutManagerCompat.removeAllDynamicShortcuts(ApplicationLoader.applicationContext);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }
        verifyingMessages.clear();

        loading = false;
        loaded = false;
        hints.clear();
        inlineBots.clear();
        AndroidUtilities.runOnUIThread(() -> {
            getNotificationCenter().postNotificationName(NotificationCenter.reloadHints);
            getNotificationCenter().postNotificationName(NotificationCenter.reloadInlineHints);
        });

        drafts.clear();
        draftMessages.clear();
        draftPreferences.edit().clear().apply();

        botInfos.clear();
        botKeyboards.clear();
        botKeyboardsByMids.clear();
    }

    public boolean areStickersLoaded(int type) {
        return stickersLoaded[type];
    }

    public void checkStickers(int type) {
        if (!loadingStickers[type] && (!stickersLoaded[type] || Math.abs(System.currentTimeMillis() / 1000 - loadDate[type]) >= 60 * 60)) {
            loadStickers(type, true, false);
        }
    }

    public void checkReactions() {
        if (!isLoadingReactions && Math.abs(System.currentTimeMillis() / 1000 - reactionsUpdateDate) >= 60 * 60) {
            loadReactions(true, false);
        }
    }

    public void checkMenuBots() {
        if (!isLoadingMenuBots && Math.abs(System.currentTimeMillis() / 1000 - menuBotsUpdateDate) >= 60 * 60) {
            loadAttachMenuBots(true, false);
        }
    }

    public void checkPremiumPromo() {
        if (!isLoadingPremiumPromo && Math.abs(System.currentTimeMillis() / 1000 - premiumPromoUpdateDate) >= 60 * 60) {
            loadPremiumPromo(true);
        }
    }

    public TLRPC.TL_help_premiumPromo getPremiumPromo() {
        return premiumPromo;
    }

    public TLRPC.TL_attachMenuBots getAttachMenuBots() {
        return attachMenuBots;
    }

    public void loadAttachMenuBots(boolean cache, boolean force) {
        isLoadingMenuBots = true;
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                SQLiteCursor c = null;
                long hash = 0;
                int date = 0;
                TLRPC.TL_attachMenuBots bots = null;
                try {
                    c = getMessagesStorage().getDatabase().queryFinalized("SELECT data, hash, date FROM attach_menu_bots");
                    if (c.next()) {
                        NativeByteBuffer data = c.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.AttachMenuBots attachMenuBots = TLRPC.TL_attachMenuBots.TLdeserialize(data, data.readInt32(false), true);
                            if (attachMenuBots instanceof TLRPC.TL_attachMenuBots) {
                                bots = (TLRPC.TL_attachMenuBots) attachMenuBots;
                            }
                            data.reuse();
                        }
                        hash = c.longValue(1);
                        date = c.intValue(2);
                    }
                } catch (Exception e) {
                    FileLog.e(e, false);
                } finally {
                    if (c != null) {
                        c.dispose();
                    }
                }
                processLoadedMenuBots(bots, hash, date, true);
            });
        } else {
            TLRPC.TL_messages_getAttachMenuBots req = new TLRPC.TL_messages_getAttachMenuBots();
            req.hash = force ? 0 : menuBotsUpdateHash;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                int date = (int) (System.currentTimeMillis() / 1000);
                if (response instanceof TLRPC.TL_attachMenuBotsNotModified) {
                    processLoadedMenuBots(null, 0, date, false);
                } else if (response instanceof TLRPC.TL_attachMenuBots) {
                    TLRPC.TL_attachMenuBots r = (TLRPC.TL_attachMenuBots) response;
                    processLoadedMenuBots(r, r.hash, date, false);
                }
            });
        }
    }

    public void processLoadedMenuBots(TLRPC.TL_attachMenuBots bots, long hash, int date, boolean cache) {
        if (bots != null && date != 0) {
            attachMenuBots = bots;
            menuBotsUpdateHash = hash;
        }
        menuBotsUpdateDate = date;
        if (bots != null) {
            getMessagesController().putUsers(bots.users, cache);
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.attachMenuBotsDidLoad));
        }

        if (!cache) {
            putMenuBotsToCache(bots, hash, date);
        } else if (Math.abs(System.currentTimeMillis() / 1000 - date) >= 60 * 60) {
            loadAttachMenuBots(false, true);
        }
    }

    private void putMenuBotsToCache(TLRPC.TL_attachMenuBots bots, long hash, int date) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (bots != null) {
                    getMessagesStorage().getDatabase().executeFast("DELETE FROM attach_menu_bots").stepThis().dispose();
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO attach_menu_bots VALUES(?, ?, ?)");
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(bots.getObjectSize());
                    bots.serializeToStream(data);
                    state.bindByteBuffer(1, data);
                    state.bindLong(2, hash);
                    state.bindInteger(3, date);
                    state.step();
                    data.reuse();
                    state.dispose();
                } else {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE attach_menu_bots SET date = ?");
                    state.requery();
                    state.bindLong(1, date);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void loadPremiumPromo(boolean cache) {
        isLoadingPremiumPromo = true;
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                SQLiteCursor c = null;
                int date = 0;
                TLRPC.TL_help_premiumPromo premiumPromo = null;
                try {
                    c = getMessagesStorage().getDatabase().queryFinalized("SELECT data, date FROM premium_promo");
                    if (c.next()) {
                        NativeByteBuffer data = c.byteBufferValue(0);
                        if (data != null) {
                            premiumPromo = TLRPC.TL_help_premiumPromo.TLdeserialize(data, data.readInt32(false), true);
                            data.reuse();
                        }
                        date = c.intValue(1);
                    }
                } catch (Exception e) {
                    FileLog.e(e, false);
                } finally {
                    if (c != null) {
                        c.dispose();
                    }
                }
                if (premiumPromo != null) {
                    processLoadedPremiumPromo(premiumPromo, date, true);
                }
            });
        } else {
            TLRPC.TL_help_getPremiumPromo req = new TLRPC.TL_help_getPremiumPromo();
            getConnectionsManager().sendRequest(req, (response, error) -> {
                int date = (int) (System.currentTimeMillis() / 1000);
                if (response instanceof TLRPC.TL_help_premiumPromo) {
                    TLRPC.TL_help_premiumPromo r = (TLRPC.TL_help_premiumPromo) response;
                    processLoadedPremiumPromo(r, date, false);
                }
            });
        }
    }

    public void processLoadedPremiumPromo(TLRPC.TL_help_premiumPromo premiumPromo, int date, boolean cache) {
        this.premiumPromo = premiumPromo;
        premiumPromoUpdateDate = date;
        getMessagesController().putUsers(premiumPromo.users, cache);
        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.premiumPromoUpdated));

        if (!cache) {
            putPremiumPromoToCache(premiumPromo, date);
        } else if (Math.abs(System.currentTimeMillis() / 1000 - date) >= 60 * 60 * 24 || BuildVars.DEBUG_PRIVATE_VERSION) {
            loadPremiumPromo(false);
        }
    }

    private void putPremiumPromoToCache(TLRPC.TL_help_premiumPromo premiumPromo, int date) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (premiumPromo != null) {
                    getMessagesStorage().getDatabase().executeFast("DELETE FROM premium_promo").stepThis().dispose();
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO premium_promo VALUES(?, ?)");
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(premiumPromo.getObjectSize());
                    premiumPromo.serializeToStream(data);
                    state.bindByteBuffer(1, data);
                    state.bindInteger(2, date);
                    state.step();
                    data.reuse();
                    state.dispose();
                } else {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE premium_promo SET date = ?");
                    state.requery();
                    state.bindInteger(1, date);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public List<TLRPC.TL_availableReaction> getReactionsList() {
        return reactionsList;
    }

    public void loadReactions(boolean cache, boolean force) {
        isLoadingReactions = true;
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                SQLiteCursor c = null;
                int hash = 0;
                int date = 0;
                List<TLRPC.TL_availableReaction> reactions = null;
                try {
                    c = getMessagesStorage().getDatabase().queryFinalized("SELECT data, hash, date FROM reactions");
                    if (c.next()) {
                        NativeByteBuffer data = c.byteBufferValue(0);
                        if (data != null) {
                            int count = data.readInt32(false);
                            reactions = new ArrayList<>(count);
                            for (int i = 0; i < count; i++) {
                                TLRPC.TL_availableReaction react = TLRPC.TL_availableReaction.TLdeserialize(data, data.readInt32(false), true);
                                reactions.add(react);
                            }
                            data.reuse();
                        }
                        hash = c.intValue(1);
                        date = c.intValue(2);
                    }
                } catch (Exception e) {
                    FileLog.e(e, false);
                } finally {
                    if (c != null) {
                        c.dispose();
                    }
                }
                List<TLRPC.TL_availableReaction> finalReactions = reactions;
                int finalHash = hash;
                int finalDate = date;
                AndroidUtilities.runOnUIThread(() -> {
                    processLoadedReactions(finalReactions, finalHash, finalDate, true);
                });
            });
        } else {
            TLRPC.TL_messages_getAvailableReactions req = new TLRPC.TL_messages_getAvailableReactions();
            req.hash = force ? 0 : reactionsUpdateHash;
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                int date = (int) (System.currentTimeMillis() / 1000);
                if (response instanceof TLRPC.TL_messages_availableReactionsNotModified) {
                    processLoadedReactions(null, 0, date, false);
                } else if (response instanceof TLRPC.TL_messages_availableReactions) {
                    TLRPC.TL_messages_availableReactions r = (TLRPC.TL_messages_availableReactions) response;
                    processLoadedReactions(r.reactions, r.hash, date, false);
                }
            }));
        }
    }

    public void processLoadedReactions(List<TLRPC.TL_availableReaction> reactions, int hash, int date, boolean cache) {
        if (reactions != null && date != 0) {
            reactionsList.clear();
            reactionsMap.clear();
            enabledReactionsList.clear();
            reactionsList.addAll(reactions);
            for (int i = 0; i < reactionsList.size(); i++) {
                reactionsList.get(i).positionInList = i;
                reactionsMap.put(reactionsList.get(i).reaction, reactionsList.get(i));
                if (!reactionsList.get(i).inactive) {
                    enabledReactionsList.add(reactionsList.get(i));
                }
            }
            reactionsUpdateHash = hash;
        }
        reactionsUpdateDate = date;
        if (reactions != null) {
            AndroidUtilities.runOnUIThread(() -> {
                preloadDefaultReactions();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.reactionsDidLoad);
            });
        }

        isLoadingReactions = false;
        if (!cache) {
            putReactionsToCache(reactions, hash, date);
        } else if (Math.abs(System.currentTimeMillis() / 1000 - date) >= 60 * 60) {
            loadReactions(false, true);
        }
    }

    public void preloadDefaultReactions() {
        if (reactionsList == null || reactionsCacheGenerated) {
            return;
        }
        reactionsCacheGenerated = true;
        ArrayList<TLRPC.TL_availableReaction> arrayList = new ArrayList<>(reactionsList);
        for (int i = 0; i < arrayList.size(); i++) {
            TLRPC.TL_availableReaction reaction = arrayList.get(i);
            preloadImage(ImageLocation.getForDocument(reaction.activate_animation), null);
            preloadImage(ImageLocation.getForDocument(reaction.appear_animation), null);
        }

        for (int i = 0; i < arrayList.size(); i++) {
            TLRPC.TL_availableReaction reaction = arrayList.get(i);
            int size = ReactionsEffectOverlay.sizeForBigReaction();
            preloadImage(ImageLocation.getForDocument(reaction.around_animation), ReactionsEffectOverlay.getFilterForAroundAnimation(), true);
            preloadImage(ImageLocation.getForDocument(reaction.effect_animation), null);
        }
    }

    private void preloadImage(ImageLocation location, String filter) {
        preloadImage(location, filter, false);
    }

    private void preloadImage(ImageLocation location, String filter, boolean log) {
        ImageReceiver imageReceiver = new ImageReceiver();
        imageReceiver.setAllowStartAnimation(false);
        imageReceiver.setAllowStartLottieAnimation(false);
        imageReceiver.setAllowDecodeSingleFrame(false);
        imageReceiver.setDelegate((imageReceiver1, set, thumb, memCache) -> {
            if (set) {
                RLottieDrawable rLottieDrawable = imageReceiver.getLottieAnimation();
                if (rLottieDrawable != null) {
                    rLottieDrawable.checkCache(() -> {
                        imageReceiver.clearImage();
                        imageReceiver.setDelegate(null);
                    });
                } else {
                    imageReceiver.clearImage();
                    imageReceiver.setDelegate(null);
                }
            }
        });
        imageReceiver.setFileLoadingPriority(FileLoader.PRIORITY_LOW);
        imageReceiver.setUniqKeyPrefix("preload");
        imageReceiver.setImage(location, filter, null, null, 0, FileLoader.PRELOAD_CACHE_TYPE);
    }

    private void putReactionsToCache(List<TLRPC.TL_availableReaction> reactions, int hash, int date) {
        ArrayList<TLRPC.TL_availableReaction> reactionsFinal = reactions != null ? new ArrayList<>(reactions) : null;
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (reactionsFinal != null) {
                    getMessagesStorage().getDatabase().executeFast("DELETE FROM reactions").stepThis().dispose();
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO reactions VALUES(?, ?, ?)");
                    state.requery();
                    int size = 4; // Integer.BYTES
                    for (int a = 0; a < reactionsFinal.size(); a++) {
                        size += reactionsFinal.get(a).getObjectSize();
                    }
                    NativeByteBuffer data = new NativeByteBuffer(size);
                    data.writeInt32(reactionsFinal.size());
                    for (int a = 0; a < reactionsFinal.size(); a++) {
                        reactionsFinal.get(a).serializeToStream(data);
                    }
                    state.bindByteBuffer(1, data);
                    state.bindInteger(2, hash);
                    state.bindInteger(3, date);
                    state.step();
                    data.reuse();
                    state.dispose();
                } else {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE reactions SET date = ?");
                    state.requery();
                    state.bindLong(1, date);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void checkFeaturedStickers() {
        if (!loadingFeaturedStickers[0] && (!featuredStickersLoaded[0] || Math.abs(System.currentTimeMillis() / 1000 - loadFeaturedDate[0]) >= 60 * 60)) {
            loadFeaturedStickers(false, true, false);
        }
    }

    public void checkFeaturedEmoji() {
        if (!loadingFeaturedStickers[1] && (!featuredStickersLoaded[1] || Math.abs(System.currentTimeMillis() / 1000 - loadFeaturedDate[1]) >= 60 * 60)) {
            loadFeaturedStickers(true, true, false);
        }
    }

    public ArrayList<TLRPC.Document> getRecentStickers(int type) {
        ArrayList<TLRPC.Document> arrayList = recentStickers[type];
        if (type == TYPE_PREMIUM_STICKERS) {
            return new ArrayList<>(recentStickers[type]);
        }
        return new ArrayList<>(arrayList.subList(0, Math.min(arrayList.size(), NekoConfig.maxRecentStickers)));
    }

    public ArrayList<TLRPC.Document> getRecentStickersNoCopy(int type) {
        return recentStickers[type];
    }

    public boolean isStickerInFavorites(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        for (int a = 0; a < recentStickers[TYPE_FAVE].size(); a++) {
            TLRPC.Document d = recentStickers[TYPE_FAVE].get(a);
            if (d.id == document.id && d.dc_id == document.dc_id) {
                return true;
            }
        }
        return false;
    }

    public void clearRecentStickers() {
        TLRPC.TL_messages_clearRecentStickers req = new TLRPC.TL_messages_clearRecentStickers();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_boolTrue) {
                getMessagesStorage().getStorageQueue().postRunnable(() -> {
                    try {
                        getMessagesStorage().getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE type = " + 3).stepThis().dispose();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });

                recentStickers[TYPE_IMAGE].clear();
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recentDocumentsDidLoad, false, TYPE_IMAGE);
            }
        }));
    }

    public void addRecentSticker(int type, Object parentObject, TLRPC.Document document, int date, boolean remove) {
        if (type == TYPE_GREETINGS || !MessageObject.isStickerDocument(document) && !MessageObject.isAnimatedStickerDocument(document, true)) {
            return;
        }
        boolean found = false;
        for (int a = 0; a < recentStickers[type].size(); a++) {
            TLRPC.Document image = recentStickers[type].get(a);
            if (image.id == document.id) {
                recentStickers[type].remove(a);
                if (!remove) {
                    recentStickers[type].add(0, image);
                }
                found = true;
                break;
            }
        }
        if (!found && !remove) {
            recentStickers[type].add(0, document);
        }
        int maxCount;
        if (type == TYPE_FAVE) {
            if (remove) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_STICKER, document, StickerSetBulletinLayout.TYPE_REMOVED_FROM_FAVORITES);
            } else {
                boolean replace = recentStickers[type].size() > getMessagesController().maxFaveStickersCount;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_STICKER, document, replace ? StickerSetBulletinLayout.TYPE_REPLACED_TO_FAVORITES : StickerSetBulletinLayout.TYPE_ADDED_TO_FAVORITES);
            }
            TLRPC.TL_messages_faveSticker req = new TLRPC.TL_messages_faveSticker();
            req.id = new TLRPC.TL_inputDocument();
            req.id.id = document.id;
            req.id.access_hash = document.access_hash;
            req.id.file_reference = document.file_reference;
            if (req.id.file_reference == null) {
                req.id.file_reference = new byte[0];
            }
            req.unfave = remove;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
                    getFileRefController().requestReference(parentObject, req);
                } else {
                    AndroidUtilities.runOnUIThread(() -> getMediaDataController().loadRecents(MediaDataController.TYPE_FAVE, false, false, true));
                }
            });
            maxCount = getMessagesController().maxFaveStickersCount;
        } else {
            if (type == TYPE_IMAGE && remove) {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_STICKER, document, StickerSetBulletinLayout.TYPE_REMOVED_FROM_RECENT);
                TLRPC.TL_messages_saveRecentSticker req = new TLRPC.TL_messages_saveRecentSticker();
                req.id = new TLRPC.TL_inputDocument();
                req.id.id = document.id;
                req.id.access_hash = document.access_hash;
                req.id.file_reference = document.file_reference;
                if (req.id.file_reference == null) {
                    req.id.file_reference = new byte[0];
                }
                req.unsave = true;
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
                        getFileRefController().requestReference(parentObject, req);
                    }
                });
            }
            maxCount = getMessagesController().maxRecentStickersCount;
        }
        if (recentStickers[type].size() > maxCount || remove) {
            TLRPC.Document old = remove ? document : recentStickers[type].remove(recentStickers[type].size() - 1);
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                int cacheType;
                if (type == TYPE_IMAGE) {
                    cacheType = 3;
                } else if (type == TYPE_MASK) {
                    cacheType = 4;
                } else if (type == TYPE_EMOJIPACKS) {
                    cacheType = 7;
                } else {
                    cacheType = 5;
                }
                try {
                    getMessagesStorage().getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + old.id + "' AND type = " + cacheType).stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }
        if (!remove) {
            ArrayList<TLRPC.Document> arrayList = new ArrayList<>();
            arrayList.add(document);
            processLoadedRecentDocuments(type, arrayList, false, date, false);
        }
        if (type == TYPE_FAVE || type == TYPE_IMAGE && remove) {
            getNotificationCenter().postNotificationName(NotificationCenter.recentDocumentsDidLoad, false, type);
        }
    }

    public ArrayList<TLRPC.Document> getRecentGifs() {
        return new ArrayList<>(recentGifs);
    }

    public void removeRecentGif(TLRPC.Document document) {
        for (int i = 0, N = recentGifs.size(); i < N; i++) {
            if (recentGifs.get(i).id == document.id) {
                recentGifs.remove(i);
                break;
            }
        }
        TLRPC.TL_messages_saveGif req = new TLRPC.TL_messages_saveGif();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = document.id;
        req.id.access_hash = document.access_hash;
        req.id.file_reference = document.file_reference;
        if (req.id.file_reference == null) {
            req.id.file_reference = new byte[0];
        }
        req.unsave = true;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (error != null && FileRefController.isFileRefError(error.text)) {
                getFileRefController().requestReference("gif", req);
            }
        });
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                getMessagesStorage().getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + document.id + "' AND type = 2").stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public boolean hasRecentGif(TLRPC.Document document) {
        for (int a = 0; a < recentGifs.size(); a++) {
            TLRPC.Document image = recentGifs.get(a);
            if (image.id == document.id) {
                recentGifs.remove(a);
                recentGifs.add(0, image);
                return true;
            }
        }
        return false;
    }

    public void addRecentGif(TLRPC.Document document, int date, boolean showReplaceBulletin) {
        if (document == null) {
            return;
        }
        boolean found = false;
        for (int a = 0; a < recentGifs.size(); a++) {
            TLRPC.Document image = recentGifs.get(a);
            if (image.id == document.id) {
                recentGifs.remove(a);
                recentGifs.add(0, image);
                found = true;
                break;
            }
        }
        if (!found) {
            recentGifs.add(0, document);
        }
        if ((recentGifs.size() > getMessagesController().savedGifsLimitDefault && !UserConfig.getInstance(currentAccount).isPremium()) || recentGifs.size() > getMessagesController().savedGifsLimitPremium) {
            TLRPC.Document old = recentGifs.remove(recentGifs.size() - 1);
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                try {
                    getMessagesStorage().getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + old.id + "' AND type = 2").stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
            if (showReplaceBulletin) {
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_STICKER, document, StickerSetBulletinLayout.TYPE_REPLACED_TO_FAVORITES_GIFS);
                });
            }
        }
        ArrayList<TLRPC.Document> arrayList = new ArrayList<>();
        arrayList.add(document);
        processLoadedRecentDocuments(0, arrayList, true, date, false);
    }

    public boolean isLoadingStickers(int type) {
        return loadingStickers[type];
    }

    public void replaceStickerSet(TLRPC.TL_messages_stickerSet set) {
        TLRPC.TL_messages_stickerSet existingSet = stickerSetsById.get(set.set.id);
        String emoji = diceEmojiStickerSetsById.get(set.set.id);
        if (emoji != null) {
            diceStickerSetsByEmoji.put(emoji, set);
            putDiceStickersToCache(emoji, set, (int) (System.currentTimeMillis() / 1000));
        }
        boolean isGroupSet = false;
        if (existingSet == null) {
            existingSet = stickerSetsByName.get(set.set.short_name);
        }
        if (existingSet == null) {
            existingSet = groupStickerSets.get(set.set.id);
            if (existingSet != null) {
                isGroupSet = true;
            }
        }
        if (existingSet == null) {
            return;
        }
        boolean changed = false;
        if ("AnimatedEmojies".equals(set.set.short_name)) {
            changed = true;
            existingSet.documents = set.documents;
            existingSet.packs = set.packs;
            existingSet.set = set.set;
            AndroidUtilities.runOnUIThread(() -> {
                LongSparseArray<TLRPC.Document> stickersById = getStickerByIds(TYPE_EMOJI);
                for (int b = 0; b < set.documents.size(); b++) {
                    TLRPC.Document document = set.documents.get(b);
                    stickersById.put(document.id, document);
                }
            });
        } else {
            LongSparseArray<TLRPC.Document> documents = new LongSparseArray<>();
            for (int a = 0, size = set.documents.size(); a < size; a++) {
                TLRPC.Document document = set.documents.get(a);
                documents.put(document.id, document);
            }
            for (int a = 0, size = existingSet.documents.size(); a < size; a++) {
                TLRPC.Document document = existingSet.documents.get(a);
                TLRPC.Document newDocument = documents.get(document.id);
                if (newDocument != null) {
                    existingSet.documents.set(a, newDocument);
                    changed = true;
                }
            }
        }
        if (changed) {
            if (isGroupSet) {
                putSetToCache(existingSet);
            } else {
                int type = TYPE_IMAGE;
                if (set.set.masks) {
                    type = TYPE_MASK;
                } else if (set.set.emojis) {
                    type = TYPE_EMOJIPACKS;
                }
                putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type]);
                if ("AnimatedEmojies".equals(set.set.short_name)) {
                    type = TYPE_EMOJI;
                    putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type]);
                }
            }
        }
    }

    public TLRPC.TL_messages_stickerSet getStickerSetByName(String name) {
        return stickerSetsByName.get(name);
    }

    public TLRPC.TL_messages_stickerSet getStickerSetByEmojiOrName(String emoji) {
        return diceStickerSetsByEmoji.get(emoji);
    }

    public TLRPC.TL_messages_stickerSet getStickerSetById(long id) {
        return stickerSetsById.get(id);
    }

    public TLRPC.TL_messages_stickerSet getGroupStickerSetById(TLRPC.StickerSet stickerSet) {
        TLRPC.TL_messages_stickerSet set = stickerSetsById.get(stickerSet.id);
        if (set == null) {
            set = groupStickerSets.get(stickerSet.id);
            if (set == null || set.set == null) {
                loadGroupStickerSet(stickerSet, true);
            } else if (set.set.hash != stickerSet.hash) {
                loadGroupStickerSet(stickerSet, false);
            }
        }
        return set;
    }

    public void putGroupStickerSet(TLRPC.TL_messages_stickerSet stickerSet) {
        groupStickerSets.put(stickerSet.set.id, stickerSet);
    }

    public TLRPC.TL_messages_stickerSet getStickerSet(TLRPC.InputStickerSet inputStickerSet, boolean cacheOnly) {
        return getStickerSet(inputStickerSet, cacheOnly, null);
    }

    public TLRPC.TL_messages_stickerSet getStickerSet(TLRPC.InputStickerSet inputStickerSet, boolean cacheOnly, Runnable onNotFound) {
        if (inputStickerSet == null) {
            return null;
        }
        if (inputStickerSet instanceof TLRPC.TL_inputStickerSetID && stickerSetsById.containsKey(inputStickerSet.id)) {
            return stickerSetsById.get(inputStickerSet.id);
        } else if (inputStickerSet instanceof TLRPC.TL_inputStickerSetShortName && inputStickerSet.short_name != null && stickerSetsByName.containsKey(inputStickerSet.short_name.toLowerCase())) {
            return stickerSetsByName.get(inputStickerSet.short_name.toLowerCase());
        } else if (inputStickerSet instanceof TLRPC.TL_inputStickerSetEmojiDefaultStatuses && stickerSetDefaultStatuses != null) {
            return stickerSetDefaultStatuses;
        }
        if (cacheOnly) {
            return null;
        }
        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = inputStickerSet;
        getConnectionsManager().sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                AndroidUtilities.runOnUIThread(() -> {
                    if (set == null || set.set == null) {
                        return;
                    }
                    stickerSetsById.put(set.set.id, set);
                    stickerSetsByName.put(set.set.short_name.toLowerCase(), set);
                    if (inputStickerSet instanceof TLRPC.TL_inputStickerSetEmojiDefaultStatuses) {
                        stickerSetDefaultStatuses = set;
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.groupStickersDidLoad, set.set.id, set);
                });
            } else {
                if (onNotFound != null) {
                    onNotFound.run();
                }
            }
        });
        return null;
    }

    private void loadGroupStickerSet(TLRPC.StickerSet stickerSet, boolean cache) {
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                try {
                    TLRPC.TL_messages_stickerSet set;
                    SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT document FROM web_recent_v3 WHERE id = 's_" + stickerSet.id + "'");
                    if (cursor.next() && !cursor.isNull(0)) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            set = TLRPC.TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                        } else {
                            set = null;
                        }
                    } else {
                        set = null;
                    }
                    cursor.dispose();
                    if (set == null || set.set == null || set.set.hash != stickerSet.hash) {
                        loadGroupStickerSet(stickerSet, false);
                    }
                    if (set != null && set.set != null) {
                        AndroidUtilities.runOnUIThread(() -> {
                            groupStickerSets.put(set.set.id, set);
                            getNotificationCenter().postNotificationName(NotificationCenter.groupStickersDidLoad, set.set.id, set);
                        });
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            });
        } else {
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            req.stickerset = new TLRPC.TL_inputStickerSetID();
            req.stickerset.id = stickerSet.id;
            req.stickerset.access_hash = stickerSet.access_hash;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                    AndroidUtilities.runOnUIThread(() -> {
                        groupStickerSets.put(set.set.id, set);
                        getNotificationCenter().postNotificationName(NotificationCenter.groupStickersDidLoad, set.set.id, set);
                    });
                }
            });
        }
    }

    private void putSetToCache(TLRPC.TL_messages_stickerSet set) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                SQLiteDatabase database = getMessagesStorage().getDatabase();
                SQLitePreparedStatement state = database.executeFast("REPLACE INTO web_recent_v3 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                state.requery();
                state.bindString(1, "s_" + set.set.id);
                state.bindInteger(2, 6);
                state.bindString(3, "");
                state.bindString(4, "");
                state.bindString(5, "");
                state.bindInteger(6, 0);
                state.bindInteger(7, 0);
                state.bindInteger(8, 0);
                state.bindInteger(9, 0);
                NativeByteBuffer data = new NativeByteBuffer(set.getObjectSize());
                set.serializeToStream(data);
                state.bindByteBuffer(10, data);
                state.step();
                data.reuse();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public HashMap<String, ArrayList<TLRPC.Document>> getAllStickers() {
        return allStickers;
    }

    public HashMap<String, ArrayList<TLRPC.Document>> getAllStickersFeatured() {
        return allStickersFeatured;
    }

    public TLRPC.Document getEmojiAnimatedSticker(CharSequence message) {
        if (message == null) {
            return null;
        }
        String emoji = message.toString().replace("\uFE0F", "");
        ArrayList<TLRPC.TL_messages_stickerSet> arrayList = getStickerSets(MediaDataController.TYPE_EMOJI);
        for (int a = 0, N = arrayList.size(); a < N; a++) {
            TLRPC.TL_messages_stickerSet set = arrayList.get(a);
            for (int b = 0, N2 = set.packs.size(); b < N2; b++) {
                TLRPC.TL_stickerPack pack = set.packs.get(b);
                if (!pack.documents.isEmpty() && TextUtils.equals(pack.emoticon, emoji)) {
                    LongSparseArray<TLRPC.Document> stickerByIds = getStickerByIds(MediaDataController.TYPE_EMOJI);
                    return stickerByIds.get(pack.documents.get(0));
                }
            }
        }
        return null;
    }

    public boolean canAddStickerToFavorites() {
        return !stickersLoaded[0] || stickerSets[0].size() >= 5 || !recentStickers[TYPE_FAVE].isEmpty();
    }

    public ArrayList<TLRPC.TL_messages_stickerSet> getStickerSets(int type) {
        if (type == TYPE_FEATURED) {
            return stickerSets[2];
        } else {
            return stickerSets[type];
        }
    }

    public LongSparseArray<TLRPC.Document> getStickerByIds(int type) {
        return stickersByIds[type];
    }

    public ArrayList<TLRPC.StickerSetCovered> getFeaturedStickerSets() {
        return featuredStickerSets[0];
    }

    public ArrayList<TLRPC.StickerSetCovered> getFeaturedEmojiSets() {
        return featuredStickerSets[1];
    }

    public ArrayList<Long> getUnreadStickerSets() {
        return unreadStickerSets[0];
    }

    public ArrayList<Long> getUnreadEmojiSets() {
        return unreadStickerSets[1];
    }

    public boolean areAllTrendingStickerSetsUnread(boolean emoji) {
        for (int a = 0, N = featuredStickerSets[emoji ? 1 : 0].size(); a < N; a++) {
            TLRPC.StickerSetCovered pack = featuredStickerSets[emoji ? 1 : 0].get(a);
            if (isStickerPackInstalled(pack.set.id) || pack.covers.isEmpty() && pack.cover == null) {
                continue;
            }
            if (!unreadStickerSets[emoji ? 1 : 0].contains(pack.set.id)) {
                return false;
            }
        }
        return true;
    }

    public boolean isStickerPackInstalled(long id) {
        return isStickerPackInstalled(id, true);
    }

    public boolean isStickerPackInstalled(long id, boolean countForced) {
        return (installedStickerSetsById.indexOfKey(id) >= 0 || countForced && installedForceStickerSetsById.contains(id)) && (!countForced || !uninstalledForceStickerSetsById.contains(id));
    }

    public boolean isStickerPackUnread(boolean emoji, long id) {
        return unreadStickerSets[emoji ? 1 : 0].contains(id);
    }

    public boolean isStickerPackInstalled(String name) {
        return stickerSetsByName.containsKey(name);
    }

    public String getEmojiForSticker(long id) {
        String value = stickersByEmoji.get(id);
        return value != null ? value : "";
    }

    public static boolean canShowAttachMenuBotForTarget(TLRPC.TL_attachMenuBot bot, String target) {
        for (TLRPC.AttachMenuPeerType peerType : bot.peer_types) {
            if ((peerType instanceof TLRPC.TL_attachMenuPeerTypeSameBotPM || peerType instanceof TLRPC.TL_attachMenuPeerTypeBotPM) && target.equals("bots") ||
                    peerType instanceof TLRPC.TL_attachMenuPeerTypeBroadcast && target.equals("channels") ||
                    peerType instanceof TLRPC.TL_attachMenuPeerTypeChat && target.equals("groups") ||
                    peerType instanceof TLRPC.TL_attachMenuPeerTypePM && target.equals("users")) {
                return true;
            }
        }
        return false;
    }

    public static boolean canShowAttachMenuBot(TLRPC.TL_attachMenuBot bot, TLObject peer) {
        TLRPC.User user = peer instanceof TLRPC.User ? (TLRPC.User) peer : null;
        TLRPC.Chat chat = peer instanceof TLRPC.Chat ? (TLRPC.Chat) peer : null;

        for (TLRPC.AttachMenuPeerType peerType : bot.peer_types) {
            if (peerType instanceof TLRPC.TL_attachMenuPeerTypeSameBotPM && user != null && user.bot && user.id == bot.bot_id ||
                    peerType instanceof TLRPC.TL_attachMenuPeerTypeBotPM && user != null && user.bot && user.id != bot.bot_id ||
                    peerType instanceof TLRPC.TL_attachMenuPeerTypePM && user != null && !user.bot ||
                    peerType instanceof TLRPC.TL_attachMenuPeerTypeChat && chat != null && !ChatObject.isChannelAndNotMegaGroup(chat) ||
                    peerType instanceof TLRPC.TL_attachMenuPeerTypeBroadcast && chat != null && ChatObject.isChannelAndNotMegaGroup(chat)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static TLRPC.TL_attachMenuBotIcon getAnimatedAttachMenuBotIcon(@NonNull TLRPC.TL_attachMenuBot bot) {
        for (TLRPC.TL_attachMenuBotIcon icon : bot.icons) {
            if (icon.name.equals(ATTACH_MENU_BOT_ANIMATED_ICON_KEY)) {
                return icon;
            }
        }
        return null;
    }

    @Nullable
    public static TLRPC.TL_attachMenuBotIcon getStaticAttachMenuBotIcon(@NonNull TLRPC.TL_attachMenuBot bot) {
        for (TLRPC.TL_attachMenuBotIcon icon : bot.icons) {
            if (icon.name.equals(ATTACH_MENU_BOT_STATIC_ICON_KEY)) {
                return icon;
            }
        }
        return null;
    }

    @Nullable
    public static TLRPC.TL_attachMenuBotIcon getPlaceholderStaticAttachMenuBotIcon(@NonNull TLRPC.TL_attachMenuBot bot) {
        for (TLRPC.TL_attachMenuBotIcon icon : bot.icons) {
            if (icon.name.equals(ATTACH_MENU_BOT_PLACEHOLDER_STATIC_KEY)) {
                return icon;
            }
        }
        return null;
    }

    public static long calcDocumentsHash(ArrayList<TLRPC.Document> arrayList) {
        return calcDocumentsHash(arrayList, 200);
    }

    public static long calcDocumentsHash(ArrayList<TLRPC.Document> arrayList, int maxCount) {
        if (arrayList == null) {
            return 0;
        }
        long acc = 0;
        for (int a = 0, N = Math.min(maxCount, arrayList.size()); a < N; a++) {
            TLRPC.Document document = arrayList.get(a);
            if (document == null) {
                continue;
            }
            acc = calcHash(acc, document.id);
        }
        return acc;
    }

    public void loadRecents(int type, boolean gif, boolean cache, boolean force) {
        if (gif) {
            if (loadingRecentGifs) {
                return;
            }
            loadingRecentGifs = true;
            if (recentGifsLoaded) {
                cache = false;
            }
        } else {
            if (loadingRecentStickers[type]) {
                return;
            }
            loadingRecentStickers[type] = true;
            if (recentStickersLoaded[type]) {
                cache = false;
            }
        }
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                try {
                    int cacheType;
                    if (gif) {
                        cacheType = 2;
                    } else if (type == TYPE_IMAGE) {
                        cacheType = 3;
                    } else if (type == TYPE_MASK) {
                        cacheType = 4;
                    } else if (type == TYPE_GREETINGS) {
                        cacheType = 6;
                    } else if (type == TYPE_EMOJIPACKS) {
                        cacheType = 7;
                    } else if (type == TYPE_PREMIUM_STICKERS) {
                        cacheType = 8;
                    } else {
                        cacheType = 5;
                    }
                    SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT document FROM web_recent_v3 WHERE type = " + cacheType + " ORDER BY date DESC");
                    ArrayList<TLRPC.Document> arrayList = new ArrayList<>();
                    while (cursor.next()) {
                        if (!cursor.isNull(0)) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Document document = TLRPC.Document.TLdeserialize(data, data.readInt32(false), false);
                                if (document != null) {
                                    arrayList.add(document);
                                }
                                data.reuse();
                            }
                        }
                    }
                    cursor.dispose();
                    AndroidUtilities.runOnUIThread(() -> {
                        if (gif) {
                            recentGifs = arrayList;
                            loadingRecentGifs = false;
                            recentGifsLoaded = true;
                        } else {
                            recentStickers[type] = arrayList;
                            loadingRecentStickers[type] = false;
                            recentStickersLoaded[type] = true;
                        }
                        if (type == TYPE_GREETINGS) {
                            preloadNextGreetingsSticker();
                        }
                        getNotificationCenter().postNotificationName(NotificationCenter.recentDocumentsDidLoad, gif, type);
                        loadRecents(type, gif, false, false);
                    });
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            });
        } else {
            SharedPreferences preferences = MessagesController.getEmojiSettings(currentAccount);
            if (!force) {
                long lastLoadTime;
                if (gif) {
                    lastLoadTime = preferences.getLong("lastGifLoadTime", 0);
                } else if (type == TYPE_IMAGE) {
                    lastLoadTime = preferences.getLong("lastStickersLoadTime", 0);
                } else if (type == TYPE_MASK) {
                    lastLoadTime = preferences.getLong("lastStickersLoadTimeMask", 0);
                } else if (type == TYPE_GREETINGS) {
                    lastLoadTime = preferences.getLong("lastStickersLoadTimeGreet", 0);
                } else if (type == TYPE_EMOJIPACKS) {
                    lastLoadTime = preferences.getLong("lastStickersLoadTimeEmojiPacks", 0);
                } else if (type == TYPE_PREMIUM_STICKERS) {
                    lastLoadTime = preferences.getLong("lastStickersLoadTimePremiumStickers", 0);
                } else {
                    lastLoadTime = preferences.getLong("lastStickersLoadTimeFavs", 0);
                }
                if (Math.abs(System.currentTimeMillis() - lastLoadTime) < 60 * 60 * 1000) {
                    if (gif) {
                        loadingRecentGifs = false;
                    } else {
                        loadingRecentStickers[type] = false;
                    }
                    return;
                }
            }
            if (gif) {
                TLRPC.TL_messages_getSavedGifs req = new TLRPC.TL_messages_getSavedGifs();
                req.hash = calcDocumentsHash(recentGifs);
                getConnectionsManager().sendRequest(req, (response, error) -> {
                    ArrayList<TLRPC.Document> arrayList = null;
                    if (response instanceof TLRPC.TL_messages_savedGifs) {
                        TLRPC.TL_messages_savedGifs res = (TLRPC.TL_messages_savedGifs) response;
                        arrayList = res.gifs;
                    }
                    processLoadedRecentDocuments(type, arrayList, true, 0, true);
                });
            } else {
                TLObject request;
                if (type == TYPE_FAVE) {
                    TLRPC.TL_messages_getFavedStickers req = new TLRPC.TL_messages_getFavedStickers();
                    req.hash = calcDocumentsHash(recentStickers[type]);
                    request = req;
                } else if (type == TYPE_GREETINGS) {
                    TLRPC.TL_messages_getStickers req = new TLRPC.TL_messages_getStickers();
                    req.emoticon = "\uD83D\uDC4B" + Emoji.fixEmoji("⭐");
                    req.hash = calcDocumentsHash(recentStickers[type]);
                    request = req;
                } else if (type == TYPE_PREMIUM_STICKERS) {
                    TLRPC.TL_messages_getStickers req = new TLRPC.TL_messages_getStickers();
                    req.emoticon = "\uD83D\uDCC2" + Emoji.fixEmoji("⭐");
                    req.hash = calcDocumentsHash(recentStickers[type]);
                    request = req;
                } else {
                    TLRPC.TL_messages_getRecentStickers req = new TLRPC.TL_messages_getRecentStickers();
                    req.hash = calcDocumentsHash(recentStickers[type]);
                    req.attached = type == TYPE_MASK;
                    request = req;
                }
                getConnectionsManager().sendRequest(request, (response, error) -> {
                    ArrayList<TLRPC.Document> arrayList = null;
                    if (type == TYPE_GREETINGS || type == TYPE_PREMIUM_STICKERS) {
                        if (response instanceof TLRPC.TL_messages_stickers) {
                            TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
                            arrayList = res.stickers;
                        }
                    } else if (type == TYPE_FAVE) {
                        if (response instanceof TLRPC.TL_messages_favedStickers) {
                            TLRPC.TL_messages_favedStickers res = (TLRPC.TL_messages_favedStickers) response;
                            arrayList = res.stickers;
                        }
                    } else {
                        if (response instanceof TLRPC.TL_messages_recentStickers) {
                            TLRPC.TL_messages_recentStickers res = (TLRPC.TL_messages_recentStickers) response;
                            arrayList = res.stickers;
                        }
                    }
                    processLoadedRecentDocuments(type, arrayList, false, 0, true);
                });
            }
        }
    }

    private void preloadNextGreetingsSticker() {
        if (recentStickers[TYPE_GREETINGS].isEmpty()) {
            return;
        }
        greetingsSticker = recentStickers[TYPE_GREETINGS].get(Utilities.random.nextInt(recentStickers[TYPE_GREETINGS].size()));
        getFileLoader().loadFile(ImageLocation.getForDocument(greetingsSticker), greetingsSticker, null, 0, 1);
    }

    public TLRPC.Document getGreetingsSticker() {
        TLRPC.Document result = greetingsSticker;
        preloadNextGreetingsSticker();
        return result;
    }

    protected void processLoadedRecentDocuments(int type, ArrayList<TLRPC.Document> documents, boolean gif, int date, boolean replace) {
        if (documents != null) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                try {
                    SQLiteDatabase database = getMessagesStorage().getDatabase();
                    int maxCount;
                    if (gif) {
                        maxCount = getMessagesController().maxRecentGifsCount;
                    } else {
                        if (type == TYPE_GREETINGS || type == TYPE_PREMIUM_STICKERS) {
                            maxCount = 200;
                        } else if (type == TYPE_FAVE) {
                            maxCount = getMessagesController().maxFaveStickersCount;
                        } else {
                            maxCount = getMessagesController().maxRecentStickersCount;
                        }
                    }
                    database.beginTransaction();

                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO web_recent_v3 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                    int count = documents.size();
                    int cacheType;
                    if (gif) {
                        cacheType = 2;
                    } else if (type == TYPE_IMAGE) {
                        cacheType = 3;
                    } else if (type == TYPE_MASK) {
                        cacheType = 4;
                    } else if (type == TYPE_GREETINGS) {
                        cacheType = 6;
                    } else if (type == TYPE_EMOJIPACKS) {
                        cacheType = 7;
                    } else if (type == TYPE_PREMIUM_STICKERS) {
                        cacheType = 8;
                    } else {
                        cacheType = 5;
                    }
                    if (replace) {
                        database.executeFast("DELETE FROM web_recent_v3 WHERE type = " + cacheType).stepThis().dispose();
                    }
                    for (int a = 0; a < count; a++) {
                        if (a == maxCount) {
                            break;
                        }
                        TLRPC.Document document = documents.get(a);
                        state.requery();
                        state.bindString(1, "" + document.id);
                        state.bindInteger(2, cacheType);
                        state.bindString(3, "");
                        state.bindString(4, "");
                        state.bindString(5, "");
                        state.bindInteger(6, 0);
                        state.bindInteger(7, 0);
                        state.bindInteger(8, 0);
                        state.bindInteger(9, date != 0 ? date : count - a);
                        NativeByteBuffer data = new NativeByteBuffer(document.getObjectSize());
                        document.serializeToStream(data);
                        state.bindByteBuffer(10, data);
                        state.step();
                        data.reuse();
                    }
                    state.dispose();
                    database.commitTransaction();
                    if (documents.size() >= maxCount) {
                        database.beginTransaction();
                        for (int a = maxCount; a < documents.size(); a++) {
                            database.executeFast("DELETE FROM web_recent_v3 WHERE id = '" + documents.get(a).id + "' AND type = " + cacheType).stepThis().dispose();
                        }
                        database.commitTransaction();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }
        if (date == 0) {
            AndroidUtilities.runOnUIThread(() -> {
                SharedPreferences.Editor editor = MessagesController.getEmojiSettings(currentAccount).edit();
                if (gif) {
                    loadingRecentGifs = false;
                    recentGifsLoaded = true;
                    editor.putLong("lastGifLoadTime", System.currentTimeMillis()).commit();
                } else {
                    loadingRecentStickers[type] = false;
                    recentStickersLoaded[type] = true;
                    if (type == TYPE_IMAGE) {
                        editor.putLong("lastStickersLoadTime", System.currentTimeMillis()).commit();
                    } else if (type == TYPE_MASK) {
                        editor.putLong("lastStickersLoadTimeMask", System.currentTimeMillis()).commit();
                    } else if (type == TYPE_GREETINGS) {
                        editor.putLong("lastStickersLoadTimeGreet", System.currentTimeMillis()).commit();
                    } else if (type == TYPE_EMOJIPACKS) {
                        editor.putLong("lastStickersLoadTimeEmojiPacks", System.currentTimeMillis()).commit();
                    } else if (type == TYPE_PREMIUM_STICKERS) {
                        editor.putLong("lastStickersLoadTimePremiumStickers", System.currentTimeMillis()).commit();
                    } else {
                        editor.putLong("lastStickersLoadTimeFavs", System.currentTimeMillis()).commit();
                    }

                }
                if (documents != null) {
                    if (gif) {
                        recentGifs = documents;
                    } else {
                        recentStickers[type] = documents;
                    }
                    if (type == TYPE_GREETINGS) {
                        preloadNextGreetingsSticker();
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.recentDocumentsDidLoad, gif, type);
                } else {

                }
            });
        }
    }

    public void reorderStickers(int type, ArrayList<Long> order, boolean forceUpdateUi) {
        Collections.sort(stickerSets[type], (lhs, rhs) -> {
            int index1 = order.indexOf(lhs.set.id);
            int index2 = order.indexOf(rhs.set.id);
            if (index1 > index2) {
                return 1;
            } else if (index1 < index2) {
                return -1;
            }
            return 0;
        });
        loadHash[type] = calcStickersHash(stickerSets[type]);
        getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, forceUpdateUi);
        //loadStickers(type, false, true);
    }

    public void calcNewHash(int type) {
        loadHash[type] = calcStickersHash(stickerSets[type]);
    }

    public void storeTempStickerSet(TLRPC.TL_messages_stickerSet set) {
        stickerSetsById.put(set.set.id, set);
        stickerSetsByName.put(set.set.short_name, set);
    }

    public void addNewStickerSet(TLRPC.TL_messages_stickerSet set) {
        if (stickerSetsById.indexOfKey(set.set.id) >= 0 || stickerSetsByName.containsKey(set.set.short_name)) {
            return;
        }
        int type = TYPE_IMAGE;
        if (set.set.masks) {
            type = TYPE_MASK;
        } else if (set.set.emojis) {
            type = TYPE_EMOJIPACKS;
        }
        stickerSets[type].add(0, set);
        stickerSetsById.put(set.set.id, set);
        installedStickerSetsById.put(set.set.id, set);
        stickerSetsByName.put(set.set.short_name, set);
        LongSparseArray<TLRPC.Document> stickersById = new LongSparseArray<>();
        for (int a = 0; a < set.documents.size(); a++) {
            TLRPC.Document document = set.documents.get(a);
            stickersById.put(document.id, document);
        }
        for (int a = 0; a < set.packs.size(); a++) {
            TLRPC.TL_stickerPack stickerPack = set.packs.get(a);
            stickerPack.emoticon = stickerPack.emoticon.replace("\uFE0F", "");
            ArrayList<TLRPC.Document> arrayList = allStickers.get(stickerPack.emoticon);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                allStickers.put(stickerPack.emoticon, arrayList);
            }
            for (int c = 0; c < stickerPack.documents.size(); c++) {
                Long id = stickerPack.documents.get(c);
                if (stickersByEmoji.indexOfKey(id) < 0) {
                    stickersByEmoji.put(id, stickerPack.emoticon);
                }
                TLRPC.Document sticker = stickersById.get(id);
                if (sticker != null) {
                    arrayList.add(sticker);
                }
            }
        }
        loadHash[type] = calcStickersHash(stickerSets[type]);
        getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, true);
        loadStickers(type, false, true);
    }

    public void loadFeaturedStickers(boolean emoji, boolean cache, boolean force) {
        if (loadingFeaturedStickers[emoji ? 1 : 0] || (getUserConfig().getCurrentUser() != null && getUserConfig().getCurrentUser().bot)) {
            return;
        }
        loadingFeaturedStickers[emoji ? 1 : 0] = true;
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                ArrayList<TLRPC.StickerSetCovered> newStickerArray = null;
                ArrayList<Long> unread = new ArrayList<>();
                int date = 0;
                long hash = 0;
                boolean premium = false;
                SQLiteCursor cursor = null;
                try {
                    cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT data, unread, date, hash, premium FROM stickers_featured WHERE emoji = " + (emoji ? 1 : 0));
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            newStickerArray = new ArrayList<>();
                            int count = data.readInt32(false);
                            for (int a = 0; a < count; a++) {
                                TLRPC.StickerSetCovered stickerSet = TLRPC.StickerSetCovered.TLdeserialize(data, data.readInt32(false), false);
                                newStickerArray.add(stickerSet);
                            }
                            data.reuse();
                        }
                        data = cursor.byteBufferValue(1);
                        if (data != null) {
                            int count = data.readInt32(false);
                            for (int a = 0; a < count; a++) {
                                unread.add(data.readInt64(false));
                            }
                            data.reuse();
                        }
                        date = cursor.intValue(2);
                        hash = calcFeaturedStickersHash(emoji, newStickerArray);
                        premium = cursor.intValue(4) == 1;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
                processLoadedFeaturedStickers(emoji, newStickerArray, unread, premium, true, date, hash);
            });
        } else {
            final long hash;
            TLObject req;
            if (emoji) {
                TLRPC.TL_messages_getFeaturedEmojiStickers request = new TLRPC.TL_messages_getFeaturedEmojiStickers();
                request.hash = hash = force ? 0 : loadFeaturedHash[1];
                req = request;
            } else {
                TLRPC.TL_messages_getFeaturedStickers request = new TLRPC.TL_messages_getFeaturedStickers();
                request.hash = hash = force ? 0 : loadFeaturedHash[0];
                req = request;
            }
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_messages_featuredStickers) {
                    TLRPC.TL_messages_featuredStickers res = (TLRPC.TL_messages_featuredStickers) response;
                    processLoadedFeaturedStickers(emoji, res.sets, res.unread, res.premium, false, (int) (System.currentTimeMillis() / 1000), res.hash);
                } else {
                    processLoadedFeaturedStickers(emoji, null, null, false, false, (int) (System.currentTimeMillis() / 1000), hash);
                }
            }));
        }
    }

    private void processLoadedFeaturedStickers(boolean emoji, ArrayList<TLRPC.StickerSetCovered> res, ArrayList<Long> unreadStickers, boolean premium, boolean cache, int date, long hash) {
        AndroidUtilities.runOnUIThread(() -> {
            loadingFeaturedStickers[emoji ? 1 : 0] = false;
            featuredStickersLoaded[emoji ? 1 : 0] = true;
        });
        Utilities.stageQueue.postRunnable(() -> {
            if (cache && (res == null || Math.abs(System.currentTimeMillis() / 1000 - date) >= 60 * 60) || !cache && res == null && hash == 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (res != null && hash != 0) {
                        loadFeaturedHash[emoji ? 1 : 0] = hash;
                    }
                    loadingFeaturedStickers[emoji ? 1 : 0] = false;
                    loadFeaturedStickers(emoji, false, false);
                }, res == null && !cache ? 1000 : 0);
                if (res == null) {
                    return;
                }
            }
            if (res != null) {
                try {
                    ArrayList<TLRPC.StickerSetCovered> stickerSetsNew = new ArrayList<>();
                    LongSparseArray<TLRPC.StickerSetCovered> stickerSetsByIdNew = new LongSparseArray<>();

                    for (int a = 0; a < res.size(); a++) {
                        TLRPC.StickerSetCovered stickerSet = res.get(a);
                        stickerSetsNew.add(stickerSet);
                        stickerSetsByIdNew.put(stickerSet.set.id, stickerSet);
                    }

                    if (!cache) {
                        putFeaturedStickersToCache(emoji, stickerSetsNew, unreadStickers, date, hash, premium);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        unreadStickerSets[emoji ? 1 : 0] = unreadStickers;
                        featuredStickerSetsById[emoji ? 1 : 0] = stickerSetsByIdNew;
                        featuredStickerSets[emoji ? 1 : 0] = stickerSetsNew;
                        loadFeaturedHash[emoji ? 1 : 0] = hash;
                        loadFeaturedDate[emoji ? 1 : 0] = date;
                        loadFeaturedPremium = premium;
                        loadStickers(emoji ? TYPE_FEATURED_EMOJIPACKS : TYPE_FEATURED, true, false);
                        getNotificationCenter().postNotificationName(emoji ? NotificationCenter.featuredEmojiDidLoad : NotificationCenter.featuredStickersDidLoad);
                    });
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            } else {
                AndroidUtilities.runOnUIThread(() -> loadFeaturedDate[emoji ? 1 : 0] = date);
                putFeaturedStickersToCache(emoji, null, null, date, 0, premium);
            }
        });
    }

    private void putFeaturedStickersToCache(boolean emoji, ArrayList<TLRPC.StickerSetCovered> stickers, ArrayList<Long> unreadStickers, int date, long hash, boolean premium) {
        ArrayList<TLRPC.StickerSetCovered> stickersFinal = stickers != null ? new ArrayList<>(stickers) : null;
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (stickersFinal != null) {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO stickers_featured VALUES(?, ?, ?, ?, ?, ?, ?)");
                    state.requery();
                    int size = 4;
                    for (int a = 0; a < stickersFinal.size(); a++) {
                        size += stickersFinal.get(a).getObjectSize();
                    }
                    NativeByteBuffer data = new NativeByteBuffer(size);
                    NativeByteBuffer data2 = new NativeByteBuffer(4 + unreadStickers.size() * 8);
                    data.writeInt32(stickersFinal.size());
                    for (int a = 0; a < stickersFinal.size(); a++) {
                        stickersFinal.get(a).serializeToStream(data);
                    }
                    data2.writeInt32(unreadStickers.size());
                    for (int a = 0; a < unreadStickers.size(); a++) {
                        data2.writeInt64(unreadStickers.get(a));
                    }
                    state.bindInteger(1, 1);
                    state.bindByteBuffer(2, data);
                    state.bindByteBuffer(3, data2);
                    state.bindInteger(4, date);
                    state.bindLong(5, hash);
                    state.bindInteger(6, premium ? 1 : 0);
                    state.bindInteger(7, emoji ? 1 : 0);
                    state.step();
                    data.reuse();
                    data2.reuse();
                    state.dispose();
                } else {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE stickers_featured SET date = ?");
                    state.requery();
                    state.bindInteger(1, date);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private long calcFeaturedStickersHash(boolean emoji, ArrayList<TLRPC.StickerSetCovered> sets) {
        if (sets == null || sets.isEmpty()) {
            return 0;
        }
        long acc = 0;
        for (int a = 0; a < sets.size(); a++) {
            TLRPC.StickerSet set = sets.get(a).set;
            if (set.archived) {
                continue;
            }
            acc = calcHash(acc, set.id);
            if (unreadStickerSets[emoji ? 1 : 0].contains(set.id)) {
                acc = calcHash(acc, 1);
            }
        }
        return acc;
    }

    public static long calcHash(long hash, long id) {
        hash ^= id >> 21;
        hash ^= id << 35;
        hash ^= id >> 4;
        return hash + id;
    }

    public void markFeaturedStickersAsRead(boolean emoji, boolean query) {
        if (unreadStickerSets[emoji ? 1 : 0].isEmpty()) {
            return;
        }
        unreadStickerSets[emoji ? 1 : 0].clear();
        loadFeaturedHash[emoji ? 1 : 0] = calcFeaturedStickersHash(emoji, featuredStickerSets[emoji ? 1 : 0]);
        getNotificationCenter().postNotificationName(emoji ? NotificationCenter.featuredEmojiDidLoad : NotificationCenter.featuredStickersDidLoad);
        putFeaturedStickersToCache(emoji, featuredStickerSets[emoji ? 1 : 0], unreadStickerSets[emoji ? 1 : 0], loadFeaturedDate[emoji ? 1 : 0], loadFeaturedHash[emoji ? 1 : 0], loadFeaturedPremium);
        if (query) {
            TLRPC.TL_messages_readFeaturedStickers req = new TLRPC.TL_messages_readFeaturedStickers();
            getConnectionsManager().sendRequest(req, (response, error) -> {

            });
        }
    }

    public long getFeaturedStickersHashWithoutUnread(boolean emoji) {
        long acc = 0;
        for (int a = 0; a < featuredStickerSets[emoji ? 1 : 0].size(); a++) {
            TLRPC.StickerSet set = featuredStickerSets[emoji ? 1 : 0].get(a).set;
            if (set.archived) {
                continue;
            }
            acc = calcHash(acc, set.id);
        }
        return acc;
    }

    public void markFeaturedStickersByIdAsRead(boolean emoji, long id) {
        if (!unreadStickerSets[emoji ? 1 : 0].contains(id) || readingStickerSets[emoji ? 1 : 0].contains(id)) {
            return;
        }
        readingStickerSets[emoji ? 1 : 0].add(id);
        TLRPC.TL_messages_readFeaturedStickers req = new TLRPC.TL_messages_readFeaturedStickers();
        req.id.add(id);
        getConnectionsManager().sendRequest(req, (response, error) -> {

        });
        AndroidUtilities.runOnUIThread(() -> {
            unreadStickerSets[emoji ? 1 : 0].remove(id);
            readingStickerSets[emoji ? 1 : 0].remove(id);
            loadFeaturedHash[emoji ? 1 : 0] = calcFeaturedStickersHash(emoji, featuredStickerSets[emoji ? 1 : 0]);
            getNotificationCenter().postNotificationName(emoji ? NotificationCenter.featuredEmojiDidLoad : NotificationCenter.featuredStickersDidLoad);
            putFeaturedStickersToCache(emoji, featuredStickerSets[emoji ? 1 : 0], unreadStickerSets[emoji ? 1 : 0], loadFeaturedDate[emoji ? 1 : 0], loadFeaturedHash[emoji ? 1 : 0], loadFeaturedPremium);
        }, 1000);
    }

    public int getArchivedStickersCount(int type) {
        return archivedStickersCount[type];
    }


    public void verifyAnimatedStickerMessage(TLRPC.Message message) {
        verifyAnimatedStickerMessage(message, false);
    }

    public void verifyAnimatedStickerMessage(TLRPC.Message message, boolean safe) {
        if (message == null) {
            return;
        }
        TLRPC.Document document = MessageObject.getDocument(message);
        String name = MessageObject.getStickerSetName(document);
        if (TextUtils.isEmpty(name)) {
            return;
        }
        TLRPC.TL_messages_stickerSet stickerSet = stickerSetsByName.get(name);
        if (stickerSet != null) {
            for (int a = 0, N = stickerSet.documents.size(); a < N; a++) {
                TLRPC.Document sticker = stickerSet.documents.get(a);
                if (sticker.id == document.id && sticker.dc_id == document.dc_id) {
                    message.stickerVerified = 1;
                    break;
                }
            }
            return;
        }
        if (safe) {
            AndroidUtilities.runOnUIThread(() -> verifyAnimatedStickerMessageInternal(message, name));
        } else {
            verifyAnimatedStickerMessageInternal(message, name);
        }
    }

    private void verifyAnimatedStickerMessageInternal(TLRPC.Message message, String name) {
        ArrayList<TLRPC.Message> messages = verifyingMessages.get(name);
        if (messages == null) {
            messages = new ArrayList<>();
            verifyingMessages.put(name, messages);
        }
        messages.add(message);
        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = MessageObject.getInputStickerSet(message);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            ArrayList<TLRPC.Message> arrayList = verifyingMessages.get(name);
            if (response != null) {
                TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                storeTempStickerSet(set);
                for (int b = 0, N2 = arrayList.size(); b < N2; b++) {
                    TLRPC.Message m = arrayList.get(b);
                    TLRPC.Document d = MessageObject.getDocument(m);
                    for (int a = 0, N = set.documents.size(); a < N; a++) {
                        TLRPC.Document sticker = set.documents.get(a);
                        if (sticker.id == d.id && sticker.dc_id == d.dc_id) {
                            m.stickerVerified = 1;
                            break;
                        }
                    }
                    if (m.stickerVerified == 0) {
                        m.stickerVerified = 2;
                    }
                }
            } else {
                for (int b = 0, N2 = arrayList.size(); b < N2; b++) {
                    arrayList.get(b).stickerVerified = 2;
                }
            }
            getNotificationCenter().postNotificationName(NotificationCenter.didVerifyMessagesStickers, arrayList);
            getMessagesStorage().updateMessageVerifyFlags(arrayList);
        }));
    }

    public void loadArchivedStickersCount(int type, boolean cache) {
        if (cache) {
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            int count = preferences.getInt("archivedStickersCount" + type, -1);
            if (count == -1) {
                loadArchivedStickersCount(type, false);
            } else {
                archivedStickersCount[type] = count;
                getNotificationCenter().postNotificationName(NotificationCenter.archivedStickersCountDidLoad, type);
            }
        } else if (getUserConfig().getCurrentUser() == null || !getUserConfig().getCurrentUser().bot) {
            TLRPC.TL_messages_getArchivedStickers req = new TLRPC.TL_messages_getArchivedStickers();
            req.limit = 0;
            req.masks = type == TYPE_MASK;
            req.emojis = type == TYPE_EMOJIPACKS;
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    TLRPC.TL_messages_archivedStickers res = (TLRPC.TL_messages_archivedStickers) response;
                    archivedStickersCount[type] = res.count;
                    SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                    preferences.edit().putInt("archivedStickersCount" + type, res.count).commit();
                    getNotificationCenter().postNotificationName(NotificationCenter.archivedStickersCountDidLoad, type);
                }
            }));
        }
    }

    private void processLoadStickersResponse(int type, TLRPC.TL_messages_allStickers res) {
        processLoadStickersResponse(type, res, null);
    }

    private void processLoadStickersResponse(int type, TLRPC.TL_messages_allStickers res, Runnable onDone) {
        ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = new ArrayList<>();
        if (res.sets.isEmpty()) {
            processLoadedStickers(type, newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash2, onDone);
        } else {
            LongSparseArray<TLRPC.TL_messages_stickerSet> newStickerSets = new LongSparseArray<>();
            for (int a = 0; a < res.sets.size(); a++) {
                TLRPC.StickerSet stickerSet = res.sets.get(a);

                TLRPC.TL_messages_stickerSet oldSet = stickerSetsById.get(stickerSet.id);
                if (oldSet != null && oldSet.set.hash == stickerSet.hash) {
                    oldSet.set.archived = stickerSet.archived;
                    oldSet.set.installed = stickerSet.installed;
                    oldSet.set.official = stickerSet.official;
                    newStickerSets.put(oldSet.set.id, oldSet);
                    newStickerArray.add(oldSet);

                    if (newStickerSets.size() == res.sets.size()) {
                        processLoadedStickers(type, newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash2);
                    }
                    continue;
                }

                newStickerArray.add(null);
                int index = a;

                TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
                req.stickerset = new TLRPC.TL_inputStickerSetID();
                req.stickerset.id = stickerSet.id;
                req.stickerset.access_hash = stickerSet.access_hash;

                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.TL_messages_stickerSet res1 = (TLRPC.TL_messages_stickerSet) response;
                    newStickerArray.set(index, res1);
                    newStickerSets.put(stickerSet.id, res1);
                    if (newStickerSets.size() == res.sets.size()) {
                        for (int a1 = 0; a1 < newStickerArray.size(); a1++) {
                            if (newStickerArray.get(a1) == null) {
                                newStickerArray.remove(a1);
                                a1--;
                            }
                        }
                        processLoadedStickers(type, newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash2);
                    }
                }));
            }
            if (onDone != null) {
                onDone.run();
            }
        }
    }

    public void checkPremiumGiftStickers() {
        if (getUserConfig().premiumGiftsStickerPack != null) {
            String packName = getUserConfig().premiumGiftsStickerPack;
            TLRPC.TL_messages_stickerSet set = getStickerSetByName(packName);
            if (set == null) {
                set = getStickerSetByEmojiOrName(packName);
            }
            if (set == null) {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(packName, false, true);
            }
        }
        if (loadingPremiumGiftStickers || System.currentTimeMillis() - getUserConfig().lastUpdatedPremiumGiftsStickerPack < 86400000) {
            return;
        }
        loadingPremiumGiftStickers = true;

        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = new TLRPC.TL_inputStickerSetPremiumGifts();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_stickerSet) {
                TLRPC.TL_messages_stickerSet stickerSet = (TLRPC.TL_messages_stickerSet) response;
                getUserConfig().premiumGiftsStickerPack = stickerSet.set.short_name;
                getUserConfig().lastUpdatedPremiumGiftsStickerPack = System.currentTimeMillis();
                getUserConfig().saveConfig(false);

                processLoadedDiceStickers(getUserConfig().premiumGiftsStickerPack, false, stickerSet, false, (int) (System.currentTimeMillis() / 1000));

                getNotificationCenter().postNotificationName(NotificationCenter.didUpdatePremiumGiftStickers);
            }
        }));
    }

    public void checkGenericAnimations() {
        if (getUserConfig().genericAnimationsStickerPack != null) {
            String packName = getUserConfig().genericAnimationsStickerPack;
            TLRPC.TL_messages_stickerSet set = getStickerSetByName(packName);
            if (set == null) {
                set = getStickerSetByEmojiOrName(packName);
            }
            if (set == null) {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(packName, false, true);
            }
        }
        if (loadingGenericAnimations || System.currentTimeMillis() - getUserConfig().lastUpdatedGenericAnimations < 86400000) {
            return;
        }
        loadingGenericAnimations = true;

        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = new TLRPC.TL_inputStickerSetEmojiGenericAnimations();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_stickerSet) {
                TLRPC.TL_messages_stickerSet stickerSet = (TLRPC.TL_messages_stickerSet) response;
                getUserConfig().genericAnimationsStickerPack = stickerSet.set.short_name;
                getUserConfig().lastUpdatedGenericAnimations = System.currentTimeMillis();
                getUserConfig().saveConfig(false);

                processLoadedDiceStickers(getUserConfig().genericAnimationsStickerPack, false, stickerSet, false, (int) (System.currentTimeMillis() / 1000));
                for (int i = 0; i < stickerSet.documents.size(); i++) {
                    preloadImage(ImageLocation.getForDocument(stickerSet.documents.get(i)), null);
                }
            }
        }));
    }

    public void checkDefaultTopicIcons() {
        if (getUserConfig().defaultTopicIcons != null) {
            String packName = getUserConfig().defaultTopicIcons;
            TLRPC.TL_messages_stickerSet set = getStickerSetByName(packName);
            if (set == null) {
                set = getStickerSetByEmojiOrName(packName);
            }
            if (set == null) {
                MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(packName, false, true);
            }
        }
        if (loadingDefaultTopicIcons || System.currentTimeMillis() - getUserConfig().lastUpdatedDefaultTopicIcons < 86400000) {
            return;
        }
        loadingDefaultTopicIcons = true;

        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = new TLRPC.TL_inputStickerSetEmojiDefaultTopicIcons();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.TL_messages_stickerSet) {
                TLRPC.TL_messages_stickerSet stickerSet = (TLRPC.TL_messages_stickerSet) response;
                getUserConfig().defaultTopicIcons = stickerSet.set.short_name;
                getUserConfig().lastUpdatedDefaultTopicIcons = System.currentTimeMillis();
                getUserConfig().saveConfig(false);

                processLoadedDiceStickers(getUserConfig().defaultTopicIcons, false, stickerSet, false, (int) (System.currentTimeMillis() / 1000));
                for (int i = 0; i < stickerSet.documents.size(); i++) {
                    preloadImage(ImageLocation.getForDocument(stickerSet.documents.get(i)), null);
                }
            }
        }));
    }

    public void loadStickersByEmojiOrName(String name, boolean isEmoji, boolean cache) {
        if (loadingDiceStickerSets.contains(name) || isEmoji && diceStickerSetsByEmoji.get(name) != null) {
            return;
        }
        loadingDiceStickerSets.add(name);
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                TLRPC.TL_messages_stickerSet stickerSet = null;
                int date = 0;
                SQLiteCursor cursor = null;
                try {
                    cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT data, date FROM stickers_dice WHERE emoji = ?", name);
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            stickerSet = TLRPC.TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                        }
                        date = cursor.intValue(1);
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
                processLoadedDiceStickers(name, isEmoji, stickerSet, true, date);
            });
        } else {
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            if (Objects.equals(getUserConfig().premiumGiftsStickerPack, name)) {
                req.stickerset = new TLRPC.TL_inputStickerSetPremiumGifts();
            } else if (isEmoji) {
                TLRPC.TL_inputStickerSetDice inputStickerSetDice = new TLRPC.TL_inputStickerSetDice();
                inputStickerSetDice.emoticon = name;
                req.stickerset = inputStickerSetDice;
            } else {
                TLRPC.TL_inputStickerSetShortName inputStickerSetShortName = new TLRPC.TL_inputStickerSetShortName();
                inputStickerSetShortName.short_name = name;
                req.stickerset = inputStickerSetShortName;
            }
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (BuildConfig.DEBUG && error != null) { //supress test backend warning
                    return;
                }
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    processLoadedDiceStickers(name, isEmoji, (TLRPC.TL_messages_stickerSet) response, false, (int) (System.currentTimeMillis() / 1000));
                } else {
                    processLoadedDiceStickers(name, isEmoji, null, false, (int) (System.currentTimeMillis() / 1000));
                }
            }));
        }
    }

    private void processLoadedDiceStickers(String name, boolean isEmoji, TLRPC.TL_messages_stickerSet res, boolean cache, int date) {
        AndroidUtilities.runOnUIThread(() -> loadingDiceStickerSets.remove(name));
        Utilities.stageQueue.postRunnable(() -> {
            if (cache && (res == null || Math.abs(System.currentTimeMillis() / 1000 - date) >= 60 * 60 * 24) || !cache && res == null) {
                AndroidUtilities.runOnUIThread(() -> loadStickersByEmojiOrName(name, isEmoji, false), res == null && !cache ? 1000 : 0);
                if (res == null) {
                    return;
                }
            }
            if (res != null) {
                if (!cache) {
                    putDiceStickersToCache(name, res, date);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    diceStickerSetsByEmoji.put(name, res);
                    diceEmojiStickerSetsById.put(res.set.id, name);
                    getNotificationCenter().postNotificationName(NotificationCenter.diceStickersDidLoad, name);
                });
            } else if (!cache) {
                putDiceStickersToCache(name, null, date);
            }
        });
    }

    private void putDiceStickersToCache(String emoji, TLRPC.TL_messages_stickerSet stickers, int date) {
        if (TextUtils.isEmpty(emoji)) {
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (stickers != null) {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO stickers_dice VALUES(?, ?, ?)");
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(stickers.getObjectSize());
                    stickers.serializeToStream(data);
                    state.bindString(1, emoji);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, date);
                    state.step();
                    data.reuse();
                    state.dispose();
                } else {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE stickers_dice SET date = ?");
                    state.requery();
                    state.bindInteger(1, date);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void markSetInstalling(long id, boolean installing) {
        uninstalledForceStickerSetsById.remove(id);
        if (installing && !installedForceStickerSetsById.contains(id)) {
            installedForceStickerSetsById.add(id);
        }
        if (!installing) {
            installedForceStickerSetsById.remove(id);
        }
    }

    public void markSetUninstalling(long id, boolean uninstalling) {
        installedForceStickerSetsById.remove(id);
        if (uninstalling && !uninstalledForceStickerSetsById.contains(id)) {
            uninstalledForceStickerSetsById.add(id);
        }
        if (!uninstalling) {
            uninstalledForceStickerSetsById.remove(id);
        }
    }

    public void loadStickers(int type, boolean cache, boolean useHash) {
        loadStickers(type, cache, useHash, false, null);
    }

    public void loadStickers(int type, boolean cache, boolean force, boolean scheduleIfLoading) {
        loadStickers(type, cache, force, scheduleIfLoading, null);
    }

    public void loadStickers(int type, boolean cache, boolean force, boolean scheduleIfLoading, Utilities.Callback<ArrayList<TLRPC.TL_messages_stickerSet>> onFinish) {
        if (loadingStickers[type]) {
            if (scheduleIfLoading) {
                scheduledLoadStickers[type] = () -> loadStickers(type, false, force, false, onFinish);
            } else {
                if (onFinish != null) {
                    onFinish.run(null);
                }
            }
            return;
        }
        if (type == TYPE_FEATURED) {
            if (featuredStickerSets[0].isEmpty() || !getMessagesController().preloadFeaturedStickers) {
                if (onFinish != null) {
                    onFinish.run(null);
                }
                return;
            }
        } else if (type == TYPE_FEATURED_EMOJIPACKS) {
            if (featuredStickerSets[1].isEmpty() || !getMessagesController().preloadFeaturedStickers) {
                if (onFinish != null) {
                    onFinish.run(null);
                }
                return;
            }
        } else if (type != TYPE_EMOJI) {
            loadArchivedStickersCount(type, cache);
        }
        loadingStickers[type] = true;
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = new ArrayList<>();
                int date = 0;
                long hash = 0;
                SQLiteCursor cursor = null;
                try {
                    cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT data, date, hash FROM stickers_v2 WHERE id = " + (type + 1));
                    if (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            int count = data.readInt32(false);
                            for (int a = 0; a < count; a++) {
                                TLRPC.TL_messages_stickerSet stickerSet = TLRPC.TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false);
                                newStickerArray.add(stickerSet);
                            }
                            data.reuse();
                        }
                        date = cursor.intValue(1);
                        hash = calcStickersHash(newStickerArray);
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
                processLoadedStickers(type, newStickerArray, true, date, hash, () -> {
                    if (onFinish != null) {
                        onFinish.run(newStickerArray);
                    }
                });
            });
        } else if (getUserConfig().getCurrentUser() == null || !getUserConfig().getCurrentUser().bot) {
            if (type == TYPE_FEATURED || type == TYPE_FEATURED_EMOJIPACKS) {
                final boolean emoji = type == TYPE_FEATURED_EMOJIPACKS;
                TLRPC.TL_messages_allStickers response = new TLRPC.TL_messages_allStickers();
                response.hash2 = loadFeaturedHash[emoji ? 1 : 0];
                for (int a = 0, size = featuredStickerSets[emoji ? 1 : 0].size(); a < size; a++) {
                    response.sets.add(featuredStickerSets[emoji ? 1 : 0].get(a).set);
                }
                processLoadStickersResponse(type, response, () -> {
                    if (onFinish != null) {
                        onFinish.run(null);
                    }
                });
            } else if (type == TYPE_EMOJI) {
                TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
                req.stickerset = new TLRPC.TL_inputStickerSetAnimatedEmoji();

                getConnectionsManager().sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.TL_messages_stickerSet) {
                        ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = new ArrayList<>();
                        newStickerArray.add((TLRPC.TL_messages_stickerSet) response);
                        processLoadedStickers(type, newStickerArray, false, (int) (System.currentTimeMillis() / 1000), calcStickersHash(newStickerArray), () -> {
                            if (onFinish != null) {
                                onFinish.run(null);
                            }
                        });
                    } else {
                        processLoadedStickers(type, null, false, (int) (System.currentTimeMillis() / 1000), 0, () -> {
                            if (onFinish != null) {
                                onFinish.run(null);
                            }
                        });
                    }
                });
            } else {
                TLObject req;
                long hash;
                if (type == TYPE_IMAGE) {
                    req = new TLRPC.TL_messages_getAllStickers();
                    hash = ((TLRPC.TL_messages_getAllStickers) req).hash = force ? 0 : loadHash[type];
                } else if (type == TYPE_EMOJIPACKS) {
                    req = new TLRPC.TL_messages_getEmojiStickers();
                    hash = ((TLRPC.TL_messages_getEmojiStickers) req).hash = force ? 0 : loadHash[type];
                } else {
                    req = new TLRPC.TL_messages_getMaskStickers();
                    hash = ((TLRPC.TL_messages_getMaskStickers) req).hash = force ? 0 : loadHash[type];
                }
                getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (response instanceof TLRPC.TL_messages_allStickers) {
                        processLoadStickersResponse(type, (TLRPC.TL_messages_allStickers) response, () -> {
                            if (onFinish != null) {
                                onFinish.run(null);
                            }
                        });
                    } else {
                        processLoadedStickers(type, null, false, (int) (System.currentTimeMillis() / 1000), hash, () -> {
                            if (onFinish != null) {
                                onFinish.run(null);
                            }
                        });
                    }
                }));
            }
        }
    }

    private void putStickersToCache(int type, ArrayList<TLRPC.TL_messages_stickerSet> stickers, int date, long hash) {
        ArrayList<TLRPC.TL_messages_stickerSet> stickersFinal = stickers != null ? new ArrayList<>(stickers) : null;
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (stickersFinal != null) {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO stickers_v2 VALUES(?, ?, ?, ?)");
                    state.requery();
                    int size = 4;
                    for (int a = 0; a < stickersFinal.size(); a++) {
                        size += stickersFinal.get(a).getObjectSize();
                    }
                    NativeByteBuffer data = new NativeByteBuffer(size);
                    data.writeInt32(stickersFinal.size());
                    for (int a = 0; a < stickersFinal.size(); a++) {
                        stickersFinal.get(a).serializeToStream(data);
                    }
                    state.bindInteger(1, type + 1);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, date);
                    state.bindLong(4, hash);
                    state.step();
                    data.reuse();
                    state.dispose();
                } else {
                    SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE stickers_v2 SET date = ?");
                    state.requery();
                    state.bindLong(1, date);
                    state.step();
                    state.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public String getStickerSetName(long setId) {
        TLRPC.TL_messages_stickerSet stickerSet = stickerSetsById.get(setId);
        if (stickerSet != null) {
            return stickerSet.set.short_name;
        }
        TLRPC.StickerSetCovered stickerSetCovered;
        stickerSetCovered = featuredStickerSetsById[0].get(setId);
        if (stickerSetCovered != null) {
            return stickerSetCovered.set.short_name;
        }
        stickerSetCovered = featuredStickerSetsById[1].get(setId);
        if (stickerSetCovered != null) {
            return stickerSetCovered.set.short_name;
        }
        return null;
    }

    public static long getStickerSetId(TLRPC.Document document) {
        if (document == null) {
            return -1;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker || attribute instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetID) {
                    return attribute.stickerset.id;
                }
                break;
            }
        }
        return -1;
    }

    public static TLRPC.InputStickerSet getInputStickerSet(TLRPC.Document document) {
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetEmpty) {
                    return null;
                }
                return attribute.stickerset;
            }
        }
        return null;
    }

    private static long calcStickersHash(ArrayList<TLRPC.TL_messages_stickerSet> sets) {
        long acc = 0;
        for (int a = 0; a < sets.size(); a++) {
            if (sets.get(a) == null) {
                continue;
            }
            TLRPC.StickerSet set = sets.get(a).set;
            if (set.archived) {
                continue;
            }
            acc = calcHash(acc, set.hash);
        }
        return acc;
    }

    private void processLoadedStickers(int type, ArrayList<TLRPC.TL_messages_stickerSet> res, boolean cache, int date, long hash) {
        processLoadedStickers(type, res, cache, date, hash, null);
    }

    private void processLoadedStickers(int type, ArrayList<TLRPC.TL_messages_stickerSet> res, boolean cache, int date, long hash, Runnable onFinish) {
        AndroidUtilities.runOnUIThread(() -> {
            loadingStickers[type] = false;
            stickersLoaded[type] = true;
            if (scheduledLoadStickers[type] != null) {
                scheduledLoadStickers[type].run();
                scheduledLoadStickers[type] = null;
            }
        });
        Utilities.stageQueue.postRunnable(() -> {
            if (cache && (res == null || BuildVars.DEBUG_PRIVATE_VERSION || Math.abs(System.currentTimeMillis() / 1000 - date) >= 60 * 60) || !cache && res == null && hash == 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (res != null && hash != 0) {
                        loadHash[type] = hash;
                    }
                    loadStickers(type, false, false);
                }, res == null && !cache ? 1000 : 0);
                if (res == null) {
                    if (onFinish != null) {
                        onFinish.run();
                    }
                    return;
                }
            }
            if (res != null) {
                try {
                    ArrayList<TLRPC.TL_messages_stickerSet> stickerSetsNew = new ArrayList<>();
                    LongSparseArray<TLRPC.TL_messages_stickerSet> stickerSetsByIdNew = new LongSparseArray<>();
                    HashMap<String, TLRPC.TL_messages_stickerSet> stickerSetsByNameNew = new HashMap<>();
                    LongSparseArray<String> stickersByEmojiNew = new LongSparseArray<>();
                    LongSparseArray<TLRPC.Document> stickersByIdNew = new LongSparseArray<>();
                    HashMap<String, ArrayList<TLRPC.Document>> allStickersNew = new HashMap<>();

                    for (int a = 0; a < res.size(); a++) {
                        TLRPC.TL_messages_stickerSet stickerSet = res.get(a);
                        if (stickerSet == null || removingStickerSetsUndos.indexOfKey(stickerSet.set.id) >= 0) {
                            continue;
                        }
                        stickerSetsNew.add(stickerSet);
                        stickerSetsByIdNew.put(stickerSet.set.id, stickerSet);
                        stickerSetsByNameNew.put(stickerSet.set.short_name, stickerSet);

                        for (int b = 0; b < stickerSet.documents.size(); b++) {
                            TLRPC.Document document = stickerSet.documents.get(b);
                            if (document == null || document instanceof TLRPC.TL_documentEmpty) {
                                continue;
                            }
                            stickersByIdNew.put(document.id, document);
                        }
                        if (!stickerSet.set.archived) {
                            for (int b = 0; b < stickerSet.packs.size(); b++) {
                                TLRPC.TL_stickerPack stickerPack = stickerSet.packs.get(b);
                                if (stickerPack == null || stickerPack.emoticon == null) {
                                    continue;
                                }
                                stickerPack.emoticon = stickerPack.emoticon.replace("\uFE0F", "");
                                ArrayList<TLRPC.Document> arrayList = allStickersNew.get(stickerPack.emoticon);
                                if (arrayList == null) {
                                    arrayList = new ArrayList<>();
                                    allStickersNew.put(stickerPack.emoticon, arrayList);
                                }
                                for (int c = 0; c < stickerPack.documents.size(); c++) {
                                    Long id = stickerPack.documents.get(c);
                                    if (stickersByEmojiNew.indexOfKey(id) < 0) {
                                        stickersByEmojiNew.put(id, stickerPack.emoticon);
                                    }
                                    TLRPC.Document sticker = stickersByIdNew.get(id);
                                    if (sticker != null) {
                                        arrayList.add(sticker);
                                    }
                                }
                            }
                        }
                    }

                    if (!cache) {
                        putStickersToCache(type, stickerSetsNew, date, hash);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        for (int a = 0; a < stickerSets[type].size(); a++) {
                            TLRPC.StickerSet set = stickerSets[type].get(a).set;
                            stickerSetsById.remove(set.id);
                            stickerSetsByName.remove(set.short_name);
                            if (type != TYPE_FEATURED && type != TYPE_FEATURED_EMOJIPACKS && type != TYPE_EMOJI) {
                                installedStickerSetsById.remove(set.id);
                            }
                        }
                        for (int a = 0; a < stickerSetsByIdNew.size(); a++) {
                            stickerSetsById.put(stickerSetsByIdNew.keyAt(a), stickerSetsByIdNew.valueAt(a));
                            if (type != TYPE_FEATURED && type != TYPE_FEATURED_EMOJIPACKS && type != TYPE_EMOJI) {
                                installedStickerSetsById.put(stickerSetsByIdNew.keyAt(a), stickerSetsByIdNew.valueAt(a));
                            }
                        }
                        stickerSetsByName.putAll(stickerSetsByNameNew);
                        stickerSets[type] = stickerSetsNew;
                        loadHash[type] = hash;
                        loadDate[type] = date;
                        stickersByIds[type] = stickersByIdNew;
                        if (type == TYPE_IMAGE) {
                            allStickers = allStickersNew;
                            stickersByEmoji = stickersByEmojiNew;
                        } else if (type == TYPE_FEATURED) {
                            allStickersFeatured = allStickersNew;
                        }
                        getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, true);
                        if (onFinish != null) {
                            onFinish.run();
                        }
                    });
                } catch (Throwable e) {
                    FileLog.e(e);
                    if (onFinish != null) {
                        onFinish.run();
                    }
                }
            } else if (!cache) {
                AndroidUtilities.runOnUIThread(() -> loadDate[type] = date);
                putStickersToCache(type, null, date, 0);

                if (onFinish != null) {
                    onFinish.run();
                }
            } else {
                if (onFinish != null) {
                    onFinish.run();
                }
            }
        });
    }

    public boolean cancelRemovingStickerSet(long id) {
        Runnable undoAction = removingStickerSetsUndos.get(id);
        if (undoAction != null) {
            undoAction.run();
            return true;
        } else {
            return false;
        }
    }

    public void preloadStickerSetThumb(TLRPC.TL_messages_stickerSet stickerSet) {
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 90);
        if (thumb != null) {
            ArrayList<TLRPC.Document> documents = stickerSet.documents;
            if (documents != null && !documents.isEmpty()) {
                loadStickerSetThumbInternal(thumb, stickerSet, documents.get(0), stickerSet.set.thumb_version);
            }
        }
    }

    public void preloadStickerSetThumb(TLRPC.StickerSetCovered stickerSet) {
        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(stickerSet.set.thumbs, 90);
        if (thumb != null) {
            TLRPC.Document sticker;
            if (stickerSet.cover != null) {
                sticker = stickerSet.cover;
            } else if (!stickerSet.covers.isEmpty()) {
                sticker = stickerSet.covers.get(0);
            } else {
                return;
            }
            loadStickerSetThumbInternal(thumb, stickerSet, sticker, stickerSet.set.thumb_version);
        }
    }

    private void loadStickerSetThumbInternal(TLRPC.PhotoSize thumb, Object parentObject, TLRPC.Document sticker, int thumbVersion) {
        ImageLocation imageLocation = ImageLocation.getForSticker(thumb, sticker, thumbVersion);
        if (imageLocation != null) {
            String ext = imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE ? "tgs" : "webp";
            getFileLoader().loadFile(imageLocation, parentObject, ext, FileLoader.PRIORITY_HIGH, 1);
        }
    }

    /**
     * @param toggle 0 - remove, 1 - archive, 2 - add
     */
    public void toggleStickerSet(Context context, TLObject stickerSetObject, int toggle, BaseFragment baseFragment, boolean showSettings, boolean showTooltip) {
        toggleStickerSet(context, stickerSetObject, toggle, baseFragment, showSettings, showTooltip, null);
    }

    public void toggleStickerSet(Context context, TLObject stickerSetObject, int toggle, BaseFragment baseFragment, boolean showSettings, boolean showTooltip, Runnable onUndo) {
        TLRPC.StickerSet stickerSet;
        TLRPC.TL_messages_stickerSet messages_stickerSet;

        if (stickerSetObject instanceof TLRPC.TL_messages_stickerSet) {
            messages_stickerSet = ((TLRPC.TL_messages_stickerSet) stickerSetObject);
            stickerSet = messages_stickerSet.set;
        } else if (stickerSetObject instanceof TLRPC.StickerSetCovered) {
            stickerSet = ((TLRPC.StickerSetCovered) stickerSetObject).set;
            if (toggle != 2) {
                messages_stickerSet = stickerSetsById.get(stickerSet.id);
                if (messages_stickerSet == null) {
                    return;
                }
            } else {
                messages_stickerSet = null;
            }
        } else {
            throw new IllegalArgumentException("Invalid type of the given stickerSetObject: " + stickerSetObject.getClass());
        }

        int type1 = TYPE_IMAGE;
        if (stickerSet.masks) {
            type1 = TYPE_MASK;
        } else if (stickerSet.emojis) {
            type1 = TYPE_EMOJIPACKS;
        }
        final int type = type1;

        stickerSet.archived = toggle == 1;

        int currentIndex = 0;
        for (int a = 0; a < stickerSets[type].size(); a++) {
            TLRPC.TL_messages_stickerSet set = stickerSets[type].get(a);
            if (set.set.id == stickerSet.id) {
                currentIndex = a;
                stickerSets[type].remove(a);
                if (toggle == 2) {
                    stickerSets[type].add(0, set);
                } else {
                    stickerSetsById.remove(set.set.id);
                    installedStickerSetsById.remove(set.set.id);
                    stickerSetsByName.remove(set.set.short_name);
                }
                break;
            }
        }

        loadHash[type] = calcStickersHash(stickerSets[type]);
        putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type]);

        if (toggle == 2) {
            if (!cancelRemovingStickerSet(stickerSet.id)) {
                toggleStickerSetInternal(context, toggle, baseFragment, showSettings, stickerSetObject, stickerSet, type, showTooltip);
            }
        } else if (!showTooltip || baseFragment == null) {
            toggleStickerSetInternal(context, toggle, baseFragment, showSettings, stickerSetObject, stickerSet, type, false);
        } else {
            StickerSetBulletinLayout bulletinLayout = new StickerSetBulletinLayout(context, stickerSetObject, toggle);
            int finalCurrentIndex = currentIndex;
            boolean[] undoDone = new boolean[1];
            markSetUninstalling(stickerSet.id, true);
            Bulletin.UndoButton undoButton = new Bulletin.UndoButton(context, false).setUndoAction(() -> {
                if (undoDone[0]) {
                    return;
                }
                undoDone[0] = true;
                markSetUninstalling(stickerSet.id, false);
                stickerSet.archived = false;

                stickerSets[type].add(finalCurrentIndex, messages_stickerSet);
                stickerSetsById.put(stickerSet.id, messages_stickerSet);
                installedStickerSetsById.put(stickerSet.id, messages_stickerSet);
                stickerSetsByName.put(stickerSet.short_name, messages_stickerSet);
                removingStickerSetsUndos.remove(stickerSet.id);

                loadHash[type] = calcStickersHash(stickerSets[type]);
                putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type]);
                if (onUndo != null) {
                    onUndo.run();
                }

                getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, true);
            }).setDelayedAction(() -> {
                if (undoDone[0]) {
                    return;
                }
                undoDone[0] = true;
                toggleStickerSetInternal(context, toggle, baseFragment, showSettings, stickerSetObject, stickerSet, type, false);
            });
            bulletinLayout.setButton(undoButton);
            removingStickerSetsUndos.put(stickerSet.id, undoButton::undo);
            Bulletin.make(baseFragment, bulletinLayout, Bulletin.DURATION_LONG).show();
        }

        getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, true);
    }

    public void removeMultipleStickerSets(Context context, BaseFragment fragment, ArrayList<TLRPC.TL_messages_stickerSet> sets) {
        if (sets == null || sets.isEmpty()) {
            return;
        }
        TLRPC.TL_messages_stickerSet messages_stickerSet = sets.get(sets.size() - 1);
        if (messages_stickerSet == null) {
            return;
        }
//        TLRPC.StickerSet stickerSet = messages_stickerSet.set;

        int type1 = TYPE_IMAGE;
        if (messages_stickerSet.set.masks) {
            type1 = TYPE_MASK;
        } else if (messages_stickerSet.set.emojis) {
            type1 = TYPE_EMOJIPACKS;
        }
        final int type = type1;

        for (int i = 0; i < sets.size(); ++i) {
            sets.get(i).set.archived = false;
        }

        final int[] currentIndexes = new int[sets.size()];
        for (int a = 0; a < stickerSets[type].size(); a++) {
            TLRPC.TL_messages_stickerSet set = stickerSets[type].get(a);
            for (int b = 0; b < sets.size(); ++b) {
                if (set.set.id == sets.get(b).set.id) {
                    currentIndexes[b] = a;
                    stickerSets[type].remove(a);
                    stickerSetsById.remove(set.set.id);
                    installedStickerSetsById.remove(set.set.id);
                    stickerSetsByName.remove(set.set.short_name);
                    break;
                }
            }
        }

        putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type] = calcStickersHash(stickerSets[type]));
        getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, true);

        for (int i = 0; i < sets.size(); ++i) {
            markSetUninstalling(sets.get(i).set.id, true);
        }
        StickerSetBulletinLayout bulletinLayout = new StickerSetBulletinLayout(context, messages_stickerSet, sets.size(), StickerSetBulletinLayout.TYPE_REMOVED, null, fragment.getResourceProvider());
        boolean[] undoDone = new boolean[1];
        Bulletin.UndoButton undoButton = new Bulletin.UndoButton(context, false).setUndoAction(() -> {
            if (undoDone[0]) {
                return;
            }
            undoDone[0] = true;

            for (int i = 0; i < sets.size(); ++i) {
                markSetUninstalling(sets.get(i).set.id, false);
                sets.get(i).set.archived = false;

                stickerSets[type].add(currentIndexes[i], sets.get(i));
                stickerSetsById.put(sets.get(i).set.id, sets.get(i));
                installedStickerSetsById.put(sets.get(i).set.id, sets.get(i));
                stickerSetsByName.put(sets.get(i).set.short_name, sets.get(i));
                removingStickerSetsUndos.remove(sets.get(i).set.id);
            }

            putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type] = calcStickersHash(stickerSets[type]));
            getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, true);
        }).setDelayedAction(() -> {
            if (undoDone[0]) {
                return;
            }
            undoDone[0] = true;
            for (int i = 0; i < sets.size(); ++i) {
                toggleStickerSetInternal(context, 0, fragment, true, sets.get(i), sets.get(i).set, type, false);
            }
        });
        bulletinLayout.setButton(undoButton);
        for (int i = 0; i < sets.size(); ++i) {
            removingStickerSetsUndos.put(sets.get(i).set.id, undoButton::undo);
        }
        Bulletin.make(fragment, bulletinLayout, Bulletin.DURATION_LONG).show();
    }

    private void toggleStickerSetInternal(Context context, int toggle, BaseFragment baseFragment, boolean showSettings, TLObject stickerSetObject, TLRPC.StickerSet stickerSet, int type, boolean showTooltip) {
        TLRPC.TL_inputStickerSetID stickerSetID = new TLRPC.TL_inputStickerSetID();
        stickerSetID.access_hash = stickerSet.access_hash;
        stickerSetID.id = stickerSet.id;

        if (toggle != 0) {
            TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
            req.stickerset = stickerSetID;
            req.archived = toggle == 1;
            markSetInstalling(stickerSet.id, true);
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                removingStickerSetsUndos.remove(stickerSet.id);
                if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                    processStickerSetInstallResultArchive(baseFragment, showSettings, type, (TLRPC.TL_messages_stickerSetInstallResultArchive) response);
                }
                loadStickers(type, false, false, true, p -> {
                    markSetInstalling(stickerSet.id, false);
                });
                if (error == null && showTooltip && baseFragment != null) {
                    Bulletin.make(baseFragment, new StickerSetBulletinLayout(context, stickerSetObject, StickerSetBulletinLayout.TYPE_ADDED), Bulletin.DURATION_SHORT).show();
                }
            }));
        } else {
            markSetUninstalling(stickerSet.id, true);
            TLRPC.TL_messages_uninstallStickerSet req = new TLRPC.TL_messages_uninstallStickerSet();
            req.stickerset = stickerSetID;
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                removingStickerSetsUndos.remove(stickerSet.id);
                loadStickers(type, false, true, false, p -> {
                    markSetUninstalling(stickerSet.id, false);
                });
            }));
        }
    }

    /**
     * @param toggle 0 - uninstall, 1 - archive, 2 - unarchive
     */
    public void toggleStickerSets(ArrayList<TLRPC.StickerSet> stickerSetList, int type, int toggle, BaseFragment baseFragment, boolean showSettings) {
        int stickerSetListSize = stickerSetList.size();
        ArrayList<TLRPC.InputStickerSet> inputStickerSets = new ArrayList<>(stickerSetListSize);

        for (int i = 0; i < stickerSetListSize; i++) {
            TLRPC.StickerSet stickerSet = stickerSetList.get(i);
            TLRPC.InputStickerSet inputStickerSet = new TLRPC.TL_inputStickerSetID();
            inputStickerSet.access_hash = stickerSet.access_hash;
            inputStickerSet.id = stickerSet.id;
            inputStickerSets.add(inputStickerSet);
            if (toggle != 0) {
                stickerSet.archived = toggle == 1;
            }
            for (int a = 0, size = stickerSets[type].size(); a < size; a++) {
                TLRPC.TL_messages_stickerSet set = stickerSets[type].get(a);
                if (set.set.id == inputStickerSet.id) {
                    stickerSets[type].remove(a);
                    if (toggle == 2) {
                        stickerSets[type].add(0, set);
                    } else {
                        stickerSetsById.remove(set.set.id);
                        installedStickerSetsById.remove(set.set.id);
                        stickerSetsByName.remove(set.set.short_name);
                    }
                    break;
                }
            }
        }

        loadHash[type] = calcStickersHash(this.stickerSets[type]);
        putStickersToCache(type, this.stickerSets[type], loadDate[type], loadHash[type]);
        getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, true);

        TLRPC.TL_messages_toggleStickerSets req = new TLRPC.TL_messages_toggleStickerSets();
        req.stickersets = inputStickerSets;
        switch (toggle) {
            case 0:
                req.uninstall = true;
                break;
            case 1:
                req.archive = true;
                break;
            case 2:
                req.unarchive = true;
                break;
        }
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (toggle != 0) {
                if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                    processStickerSetInstallResultArchive(baseFragment, showSettings, type, (TLRPC.TL_messages_stickerSetInstallResultArchive) response);
                }
                loadStickers(type, false, false, true);
            } else {
                loadStickers(type, false, true);
            }
        }));
    }

    public void processStickerSetInstallResultArchive(BaseFragment baseFragment, boolean showSettings, int type, TLRPC.TL_messages_stickerSetInstallResultArchive response) {
        for (int i = 0, size = response.sets.size(); i < size; i++) {
            installedStickerSetsById.remove(response.sets.get(i).set.id);
        }
        loadArchivedStickersCount(type, false);
        getNotificationCenter().postNotificationName(NotificationCenter.needAddArchivedStickers, response.sets);
        if (baseFragment != null && baseFragment.getParentActivity() != null) {
            StickersArchiveAlert alert = new StickersArchiveAlert(baseFragment.getParentActivity(), showSettings ? baseFragment : null, response.sets);
            baseFragment.showDialog(alert.create());
        }
    }
    //---------------- STICKERS END ----------------

    private int reqId;
    private int mergeReqId;
    private long lastMergeDialogId;
    private int lastReplyMessageId;
    private long lastDialogId;
    private int lastReqId;
    private int lastGuid;
    private TLRPC.User lastSearchUser;
    private TLRPC.Chat lastSearchChat;
    private int[] messagesSearchCount = new int[]{0, 0};
    private boolean[] messagesSearchEndReached = new boolean[]{false, false};
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private SparseArray<MessageObject>[] searchResultMessagesMap = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    private String lastSearchQuery;
    private int lastReturnedNum;
    private boolean loadingMoreSearchMessages;

    private int getMask() {
        int mask = 0;
        if (lastReturnedNum < searchResultMessages.size() - 1 || !messagesSearchEndReached[0] || !messagesSearchEndReached[1]) {
            mask |= 1;
        }
        if (lastReturnedNum > 0) {
            mask |= 2;
        }
        return mask;
    }

    public ArrayList<MessageObject> getFoundMessageObjects() {
        return searchResultMessages;
    }

    public void clearFoundMessageObjects() {
        searchResultMessages.clear();
    }

    public boolean isMessageFound(int messageId, boolean mergeDialog) {
        return searchResultMessagesMap[mergeDialog ? 1 : 0].indexOfKey(messageId) >= 0;
    }

    public void searchMessagesInChat(String query, long dialogId, long mergeDialogId, int guid, int direction, int replyMessageId, TLRPC.User user, TLRPC.Chat chat) {
        searchMessagesInChat(query, dialogId, mergeDialogId, guid, direction, replyMessageId, false, user, chat, true);
    }

    public void jumpToSearchedMessage(int guid, int index) {
        if (index < 0 || index >= searchResultMessages.size()) {
            return;
        }
        lastReturnedNum = index;
        MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
        getNotificationCenter().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1], true);
    }

    public void loadMoreSearchMessages() {
        if (loadingMoreSearchMessages || messagesSearchEndReached[0] && lastMergeDialogId == 0 && messagesSearchEndReached[1]) {
            return;
        }
        int temp = searchResultMessages.size();
        lastReturnedNum = searchResultMessages.size();
        searchMessagesInChat(null, lastDialogId, lastMergeDialogId, lastGuid, 1, lastReplyMessageId, false, lastSearchUser, lastSearchChat, false);
        lastReturnedNum = temp;
        loadingMoreSearchMessages = true;
    }

    private void searchMessagesInChat(String query, long dialogId, long mergeDialogId, int guid, int direction, int replyMessageId, boolean internal, TLRPC.User user, TLRPC.Chat chat, boolean jumpToMessage) {
        int max_id = 0;
        long queryWithDialog = dialogId;
        boolean firstQuery = !internal;
        if (reqId != 0) {
            getConnectionsManager().cancelRequest(reqId, true);
            reqId = 0;
        }
        if (mergeReqId != 0) {
            getConnectionsManager().cancelRequest(mergeReqId, true);
            mergeReqId = 0;
        }
        if (query == null) {
            if (searchResultMessages.isEmpty()) {
                return;
            }
            if (direction == 1) {
                lastReturnedNum++;
                if (lastReturnedNum < searchResultMessages.size()) {
                    MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                    getNotificationCenter().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1], jumpToMessage);
                    return;
                } else {
                    if (messagesSearchEndReached[0] && mergeDialogId == 0 && messagesSearchEndReached[1]) {
                        lastReturnedNum--;
                        return;
                    }
                    firstQuery = false;
                    query = lastSearchQuery;
                    MessageObject messageObject = searchResultMessages.get(searchResultMessages.size() - 1);
                    if (messageObject.getDialogId() == dialogId && !messagesSearchEndReached[0]) {
                        max_id = messageObject.getId();
                        queryWithDialog = dialogId;
                    } else {
                        if (messageObject.getDialogId() == mergeDialogId) {
                            max_id = messageObject.getId();
                        }
                        queryWithDialog = mergeDialogId;
                        messagesSearchEndReached[1] = false;
                    }
                }
            } else if (direction == 2) {
                lastReturnedNum--;
                if (lastReturnedNum < 0) {
                    lastReturnedNum = 0;
                    return;
                }
                if (lastReturnedNum >= searchResultMessages.size()) {
                    lastReturnedNum = searchResultMessages.size() - 1;
                }
                MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                getNotificationCenter().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1], jumpToMessage);
                return;
            } else {
                return;
            }
        } else if (firstQuery) {
            messagesSearchEndReached[0] = messagesSearchEndReached[1] = false;
            messagesSearchCount[0] = messagesSearchCount[1] = 0;
            searchResultMessages.clear();
            searchResultMessagesMap[0].clear();
            searchResultMessagesMap[1].clear();
            getNotificationCenter().postNotificationName(NotificationCenter.chatSearchResultsLoading, guid);
        }
        if (messagesSearchEndReached[0] && !messagesSearchEndReached[1] && mergeDialogId != 0) {
            queryWithDialog = mergeDialogId;
        }
        if (queryWithDialog == dialogId && firstQuery) {
            if (mergeDialogId != 0) {
                TLRPC.InputPeer inputPeer = getMessagesController().getInputPeer(mergeDialogId);
                if (inputPeer == null) {
                    return;
                }
                TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.peer = inputPeer;
                lastMergeDialogId = mergeDialogId;
                req.limit = 1;
                req.q = query;
                if (user != null) {
                    req.from_id = MessagesController.getInputPeer(user);
                    req.flags |= 1;
                } else if (chat != null) {
                    req.from_id = MessagesController.getInputPeer(chat);
                    req.flags |= 1;
                }
                if (replyMessageId != 0) {
                    req.top_msg_id = replyMessageId;
                    req.flags |= 2;
                }
                req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
                mergeReqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (lastMergeDialogId == mergeDialogId) {
                        mergeReqId = 0;
                        if (response != null) {
                            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                            messagesSearchEndReached[1] = res.messages.isEmpty();
                            messagesSearchCount[1] = res instanceof TLRPC.TL_messages_messagesSlice ? res.count : res.messages.size();
                            searchMessagesInChat(req.q, dialogId, mergeDialogId, guid, direction, replyMessageId, true, user, chat, jumpToMessage);
                        } else {
                            messagesSearchEndReached[1] = true;
                            messagesSearchCount[1] = 0;
                            searchMessagesInChat(req.q, dialogId, mergeDialogId, guid, direction, replyMessageId, true, user, chat, jumpToMessage);
                        }
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
                return;
            } else {
                lastMergeDialogId = 0;
                messagesSearchEndReached[1] = true;
                messagesSearchCount[1] = 0;
            }
        }
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = getMessagesController().getInputPeer(queryWithDialog);
        if (req.peer == null) {
            return;
        }
        lastGuid = guid;
        lastDialogId = dialogId;
        lastSearchUser = user;
        lastSearchChat = chat;
        lastReplyMessageId = replyMessageId;
        req.limit = 21;
        req.q = query != null ? query : "";
        req.offset_id = max_id;
        if (user != null) {
            req.from_id = MessagesController.getInputPeer(user);
            req.flags |= 1;
        } else if (chat != null) {
            req.from_id = MessagesController.getInputPeer(chat);
            req.flags |= 1;
        }
        if (lastReplyMessageId != 0) {
            req.top_msg_id = lastReplyMessageId;
            req.flags |= 2;
        }
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        int currentReqId = ++lastReqId;
        lastSearchQuery = query;
        long queryWithDialogFinal = queryWithDialog;
        String finalQuery = query;
        reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
            ArrayList<MessageObject> messageObjects = new ArrayList<>();

            if (error == null) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                int N = Math.min(res.messages.size(), 20);
                for (int a = 0; a < N; a++) {
                    TLRPC.Message message = res.messages.get(a);
                    MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
                    messageObject.setQuery(finalQuery);
                    messageObjects.add(messageObject);
                }
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (currentReqId == lastReqId) {
                    reqId = 0;
                    if (!jumpToMessage) {
                        loadingMoreSearchMessages = false;
                    }
                    if (response != null) {
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        for (int a = 0; a < res.messages.size(); a++) {
                            TLRPC.Message message = res.messages.get(a);
                            if (message instanceof TLRPC.TL_messageEmpty || message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                                res.messages.remove(a);
                                a--;
                            }
                        }
                        getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                        getMessagesController().putUsers(res.users, false);
                        getMessagesController().putChats(res.chats, false);
                        if (req.offset_id == 0 && queryWithDialogFinal == dialogId) {
                            lastReturnedNum = 0;
                            searchResultMessages.clear();
                            searchResultMessagesMap[0].clear();
                            searchResultMessagesMap[1].clear();
                            messagesSearchCount[0] = 0;
                            getNotificationCenter().postNotificationName(NotificationCenter.chatSearchResultsLoading, guid);
                        }
                        boolean added = false;
                        int N = Math.min(res.messages.size(), 20);
                        for (int a = 0; a < N; a++) {
                            TLRPC.Message message = res.messages.get(a);
                            added = true;
                            MessageObject messageObject = messageObjects.get(a);
                            searchResultMessages.add(messageObject);
                            searchResultMessagesMap[queryWithDialogFinal == dialogId ? 0 : 1].put(messageObject.getId(), messageObject);
                        }
                        messagesSearchEndReached[queryWithDialogFinal == dialogId ? 0 : 1] = res.messages.size() < 21;
                        messagesSearchCount[queryWithDialogFinal == dialogId ? 0 : 1] = res instanceof TLRPC.TL_messages_messagesSlice || res instanceof TLRPC.TL_messages_channelMessages ? res.count : res.messages.size();
                        if (searchResultMessages.isEmpty()) {
                            getNotificationCenter().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, 0, getMask(), (long) 0, 0, 0, jumpToMessage);
                        } else {
                            if (added) {
                                if (lastReturnedNum >= searchResultMessages.size()) {
                                    lastReturnedNum = searchResultMessages.size() - 1;
                                }
                                MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                                getNotificationCenter().postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1], jumpToMessage);
                            }
                        }
                        if (queryWithDialogFinal == dialogId && messagesSearchEndReached[0] && mergeDialogId != 0 && !messagesSearchEndReached[1]) {
                            searchMessagesInChat(lastSearchQuery, dialogId, mergeDialogId, guid, 0, replyMessageId, true, user, chat, jumpToMessage);
                        }
                    }
                }
            });
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public String getLastSearchQuery() {
        return lastSearchQuery;
    }
    //---------------- MESSAGE SEARCH END ----------------


    public final static int MEDIA_PHOTOVIDEO = 0;
    public final static int MEDIA_FILE = 1;
    public final static int MEDIA_AUDIO = 2;
    public final static int MEDIA_URL = 3;
    public final static int MEDIA_MUSIC = 4;
    public final static int MEDIA_GIF = 5;
    public final static int MEDIA_PHOTOS_ONLY = 6;
    public final static int MEDIA_VIDEOS_ONLY = 7;
    public final static int MEDIA_TYPES_COUNT = 8;


    public void loadMedia(long dialogId, int count, int max_id, int min_id, int type, int topicId, int fromCache, int classGuid, int requestIndex) {
        boolean isChannel = DialogObject.isChatDialog(dialogId) && ChatObject.isChannel(-dialogId, currentAccount);

        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load media did " + dialogId + " count = " + count + " max_id " + max_id + " type = " + type + " cache = " + fromCache + " classGuid = " + classGuid);
        }
        if (fromCache != 0 || DialogObject.isEncryptedDialog(dialogId)) {
            loadMediaDatabase(dialogId, count, max_id, min_id, type, topicId, classGuid, isChannel, fromCache, requestIndex);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.limit = count;
            if (min_id != 0) {
                req.offset_id = min_id;
                req.add_offset = -count;
            } else {
                req.offset_id = max_id;
            }

            if (type == MEDIA_PHOTOVIDEO) {
                req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            } else if (type == MEDIA_PHOTOS_ONLY) {
                req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
            } else if (type == MEDIA_VIDEOS_ONLY) {
                req.filter = new TLRPC.TL_inputMessagesFilterVideo();
            } else if (type == MEDIA_FILE) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (type == MEDIA_AUDIO) {
                req.filter = new TLRPC.TL_inputMessagesFilterRoundVoice();
            } else if (type == MEDIA_URL) {
                req.filter = new TLRPC.TL_inputMessagesFilterUrl();
            } else if (type == MEDIA_MUSIC) {
                req.filter = new TLRPC.TL_inputMessagesFilterMusic();
            } else if (type == MEDIA_GIF) {
                req.filter = new TLRPC.TL_inputMessagesFilterGif();
            }
            req.q = "";
            req.peer = getMessagesController().getInputPeer(dialogId);
            if (topicId != 0) {
                req.top_msg_id = topicId;
                req.flags |= 2;
            }
            if (req.peer == null) {
                return;
            }
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    getMessagesController().removeDeletedMessagesFromArray(dialogId, res.messages);
                    boolean topReached;
                    if (min_id != 0) {
                        topReached = res.messages.size() <= 1;
                    } else {
                        topReached = res.messages.size() == 0;
                    }

                    processLoadedMedia(res, dialogId, count, max_id, min_id, type, topicId, 0, classGuid, isChannel, topReached, requestIndex);
                }
            });
            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void getMediaCounts(long dialogId, int topicId, int classGuid) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                int[] counts = new int[]{-1, -1, -1, -1, -1, -1, -1, -1};
                int[] countsFinal = new int[]{-1, -1, -1, -1, -1, -1, -1, -1};
                int[] old = new int[]{0, 0, 0, 0, 0, 0, 0, 0};
                SQLiteCursor cursor;
                if (topicId != 0) {
                    cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT type, count, old FROM media_counts_topics WHERE uid = %d AND topic_id = %d", dialogId, topicId));
                } else {
                    cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT type, count, old FROM media_counts_v2 WHERE uid = %d", dialogId));
                }
                while (cursor.next()) {
                    int type = cursor.intValue(0);
                    if (type >= 0 && type < MEDIA_TYPES_COUNT) {
                        countsFinal[type] = counts[type] = cursor.intValue(1);
                        old[type] = cursor.intValue(2);
                    }
                }
                cursor.dispose();
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    for (int a = 0; a < counts.length; a++) {
                        if (counts[a] == -1) {
                            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM media_v4 WHERE uid = %d AND type = %d LIMIT 1", dialogId, a));
                            if (cursor.next()) {
                                counts[a] = cursor.intValue(0);
                            } else {
                                counts[a] = 0;
                            }
                            cursor.dispose();
                            putMediaCountDatabase(dialogId, topicId, a, counts[a]);
                        }
                    }
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.mediaCountsDidLoad, dialogId, topicId, counts));
                } else {
                    boolean missing = false;
                    TLRPC.TL_messages_getSearchCounters req = new TLRPC.TL_messages_getSearchCounters();
                    req.peer = getMessagesController().getInputPeer(dialogId);
                    if (topicId != 0) {
                        req.top_msg_id = topicId;
                        req.flags |= 1;
                    }
                    for (int a = 0; a < counts.length; a++) {
                        if (req.peer == null) {
                            counts[a] = 0;
                            continue;
                        }
                        if (counts[a] == -1 || old[a] == 1) {
                            if (a == MEDIA_PHOTOVIDEO) {
                                req.filters.add(new TLRPC.TL_inputMessagesFilterPhotoVideo());
                            } else if (a == MEDIA_FILE) {
                                req.filters.add(new TLRPC.TL_inputMessagesFilterDocument());
                            } else if (a == MEDIA_AUDIO) {
                                req.filters.add(new TLRPC.TL_inputMessagesFilterRoundVoice());
                            } else if (a == MEDIA_URL) {
                                req.filters.add(new TLRPC.TL_inputMessagesFilterUrl());
                            } else if (a == MEDIA_MUSIC) {
                                req.filters.add(new TLRPC.TL_inputMessagesFilterMusic());
                            } else if (a == MEDIA_PHOTOS_ONLY) {
                                req.filters.add(new TLRPC.TL_inputMessagesFilterPhotos());
                            } else if (a == MEDIA_VIDEOS_ONLY) {
                                req.filters.add(new TLRPC.TL_inputMessagesFilterVideo());
                            } else {
                                req.filters.add(new TLRPC.TL_inputMessagesFilterGif());
                            }
                            if (counts[a] == -1) {
                                missing = true;
                            } else if (old[a] == 1) {
                                counts[a] = -1;
                            }
                        }
                    }
                    if (!req.filters.isEmpty()) {
                        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                            for (int i = 0; i < counts.length; i++) {
                                if (counts[i] < 0) {
                                    counts[i] = 0;
                                }
                            }
                            if (response != null) {
                                TLRPC.Vector res = (TLRPC.Vector) response;
                                for (int a = 0, N = res.objects.size(); a < N; a++) {
                                    TLRPC.TL_messages_searchCounter searchCounter = (TLRPC.TL_messages_searchCounter) res.objects.get(a);
                                    int type;
                                    if (searchCounter.filter instanceof TLRPC.TL_inputMessagesFilterPhotoVideo) {
                                        type = MEDIA_PHOTOVIDEO;
                                    } else if (searchCounter.filter instanceof TLRPC.TL_inputMessagesFilterDocument) {
                                        type = MEDIA_FILE;
                                    } else if (searchCounter.filter instanceof TLRPC.TL_inputMessagesFilterRoundVoice) {
                                        type = MEDIA_AUDIO;
                                    } else if (searchCounter.filter instanceof TLRPC.TL_inputMessagesFilterUrl) {
                                        type = MEDIA_URL;
                                    } else if (searchCounter.filter instanceof TLRPC.TL_inputMessagesFilterMusic) {
                                        type = MEDIA_MUSIC;
                                    } else if (searchCounter.filter instanceof TLRPC.TL_inputMessagesFilterGif) {
                                        type = MEDIA_GIF;
                                    } else if (searchCounter.filter instanceof TLRPC.TL_inputMessagesFilterPhotos) {
                                        type = MEDIA_PHOTOS_ONLY;
                                    } else if (searchCounter.filter instanceof TLRPC.TL_inputMessagesFilterVideo) {
                                        type = MEDIA_VIDEOS_ONLY;
                                    } else {
                                        continue;
                                    }
                                    counts[type] = searchCounter.count;
                                    putMediaCountDatabase(dialogId, topicId, type, counts[type]);
                                }
                            }
                            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.mediaCountsDidLoad, dialogId, topicId, counts));
                        });
                        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
                    }
                    if (!missing) {
                        AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.mediaCountsDidLoad, dialogId, topicId, countsFinal));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getMediaCount(long dialogId, int topicId, int type, int classGuid, boolean fromCache) {
        if (fromCache || DialogObject.isEncryptedDialog(dialogId)) {
            getMediaCountDatabase(dialogId, topicId, type, classGuid);
        } else {
            TLRPC.TL_messages_getSearchCounters req = new TLRPC.TL_messages_getSearchCounters();
            if (type == MEDIA_PHOTOVIDEO) {
                req.filters.add(new TLRPC.TL_inputMessagesFilterPhotoVideo());
            } else if (type == MEDIA_FILE) {
                req.filters.add(new TLRPC.TL_inputMessagesFilterDocument());
            } else if (type == MEDIA_AUDIO) {
                req.filters.add(new TLRPC.TL_inputMessagesFilterRoundVoice());
            } else if (type == MEDIA_URL) {
                req.filters.add(new TLRPC.TL_inputMessagesFilterUrl());
            } else if (type == MEDIA_MUSIC) {
                req.filters.add(new TLRPC.TL_inputMessagesFilterMusic());
            } else if (type == MEDIA_GIF) {
                req.filters.add(new TLRPC.TL_inputMessagesFilterGif());
            }
            if (topicId != 0) {
                req.top_msg_id = topicId;
                req.flags |= 1;
            }
            req.peer = getMessagesController().getInputPeer(dialogId);
            if (req.peer == null) {
                return;
            }
            int reqId = getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response != null) {
                    TLRPC.Vector res = (TLRPC.Vector) response;
                    if (!res.objects.isEmpty()) {
                        TLRPC.TL_messages_searchCounter counter = (TLRPC.TL_messages_searchCounter) res.objects.get(0);
                        processLoadedMediaCount(counter.count, dialogId, topicId, type, classGuid, false, 0);
                    }
                }
            });
            getConnectionsManager().bindRequestToGuid(reqId, classGuid);
        }
    }

    public static int getMediaType(TLRPC.Message message) {
        if (message == null) {
            return -1;
        }
        if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto) {
            return MEDIA_PHOTOVIDEO;
        } else if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaDocument) {
            TLRPC.Document document = MessageObject.getMedia(message).document;
            if (document == null) {
                return -1;
            }
            boolean isAnimated = false;
            boolean isVideo = false;
            boolean isVoice = false;
            boolean isMusic = false;
            boolean isSticker = false;

            for (int a = 0; a < document.attributes.size(); a++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(a);
                if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                    isVoice = attribute.round_message;
                    isVideo = !attribute.round_message;
                } else if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                    isAnimated = true;
                } else if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                    isVoice = attribute.voice;
                    isMusic = !attribute.voice;
                } else if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    isSticker = true;
                }
            }
            if (isVoice) {
                return MEDIA_AUDIO;
            } else if (isVideo && !isAnimated && !isSticker) {
                return MEDIA_PHOTOVIDEO;
            } else if (isSticker) {
                return -1;
            } else if (isAnimated) {
                return MEDIA_GIF;
            } else if (isMusic) {
                return MEDIA_MUSIC;
            } else {
                return MEDIA_FILE;
            }
        } else if (!message.entities.isEmpty()) {
            for (int a = 0; a < message.entities.size(); a++) {
                TLRPC.MessageEntity entity = message.entities.get(a);
                if (entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityTextUrl || entity instanceof TLRPC.TL_messageEntityEmail) {
                    return MEDIA_URL;
                }
            }
        }
        return -1;
    }

    public static boolean canAddMessageToMedia(TLRPC.Message message) {
        if (message instanceof TLRPC.TL_message_secret && (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto || MessageObject.isVideoMessage(message) || MessageObject.isGifMessage(message)) && MessageObject.getMedia(message).ttl_seconds != 0 && MessageObject.getMedia(message).ttl_seconds <= 60) {
            return false;
        } else if (!(message instanceof TLRPC.TL_message_secret) && message instanceof TLRPC.TL_message && (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto || MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaDocument) && MessageObject.getMedia(message).ttl_seconds != 0) {
            return false;
        } else {
            return getMediaType(message) != -1;
        }
    }

    private void processLoadedMedia(TLRPC.messages_Messages res, long dialogId, int count, int max_id, int min_id, int type, int topicId, int fromCache, int classGuid, boolean isChannel, boolean topReached, int requestIndex) {
        if (BuildVars.LOGS_ENABLED) {
            int messagesCount = 0;
            if (res != null && res.messages != null) {
                messagesCount = res.messages.size();
            }
            FileLog.d("process load media messagesCount " + messagesCount + " did " + dialogId + " topicId " + topicId + " count = " + count + " max_id=" + max_id + " min_id=" + min_id + " type = " + type + " cache = " + fromCache + " classGuid = " + classGuid);
        }
        if (fromCache != 0 && ((res.messages.isEmpty() && min_id == 0) || (res.messages.size() <= 1 && min_id != 0)) && !DialogObject.isEncryptedDialog(dialogId)) {
            if (fromCache == 2) {
                return;
            }
            loadMedia(dialogId, count, max_id, min_id, type, topicId, 0, classGuid, requestIndex);
        } else {
            if (fromCache == 0) {
                ImageLoader.saveMessagesThumbs(res.messages);
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                putMediaDatabase(dialogId, topicId, type, res.messages, max_id, min_id, topReached);
            }

            Utilities.searchQueue.postRunnable(() -> {
                LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
                for (int a = 0; a < res.users.size(); a++) {
                    TLRPC.User u = res.users.get(a);
                    usersDict.put(u.id, u);
                }
                ArrayList<MessageObject> objects = new ArrayList<>();
                for (int a = 0; a < res.messages.size(); a++) {
                    TLRPC.Message message = res.messages.get(a);
                    MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, true, false);
                    messageObject.createStrippedThumb();
                    objects.add(messageObject);
                }
                getFileLoader().checkMediaExistance(objects);

                AndroidUtilities.runOnUIThread(() -> {
                    int totalCount = res.count;
                    getMessagesController().putUsers(res.users, fromCache != 0);
                    getMessagesController().putChats(res.chats, fromCache != 0);
                    getNotificationCenter().postNotificationName(NotificationCenter.mediaDidLoad, dialogId, totalCount, objects, classGuid, type, topReached, min_id != 0, requestIndex);
                });
            });
        }
    }

    private void processLoadedMediaCount(int count, long dialogId, int topicId, int type, int classGuid, boolean fromCache, int old) {
        AndroidUtilities.runOnUIThread(() -> {
            boolean isEncryptedDialog = DialogObject.isEncryptedDialog(dialogId);
            boolean reload = fromCache && (count == -1 || count == 0 && type == 2) && !isEncryptedDialog;
            if (reload || old == 1 && !isEncryptedDialog) {
                getMediaCount(dialogId, topicId, type, classGuid, false);
            }
            if (!reload) {
                if (!fromCache) {
                    putMediaCountDatabase(dialogId, topicId, type, count);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.mediaCountDidLoad, dialogId, topicId, (fromCache && count == -1 ? 0 : count), fromCache, type);
            }
        });
    }

    private void putMediaCountDatabase(long uid, int topicId, int type, int count) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                SQLitePreparedStatement state2;
                if (topicId != 0) {
                    state2 = getMessagesStorage().getDatabase().executeFast("REPLACE INTO media_counts_topics VALUES(?, ?, ?, ?, ?)");
                } else {
                    state2 = getMessagesStorage().getDatabase().executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?, ?)");
                }
                int pointer = 1;
                state2.requery();
                state2.bindLong(pointer++, uid);
                if (topicId != 0) {
                    state2.bindInteger(pointer++, topicId);
                }
                state2.bindInteger(pointer++, type);
                state2.bindInteger(pointer++, count);
                state2.bindInteger(pointer++, 0);
                state2.step();
                state2.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void getMediaCountDatabase(long dialogId, int topicId, int type, int classGuid) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                int count = -1;
                int old = 0;
                SQLiteCursor cursor;
                if (topicId != 0) {
                    cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT count, old FROM media_counts_topics WHERE uid = %d AND topic_id = %d AND type = %d LIMIT 1", dialogId, topicId, type));
                } else {
                    cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT count, old FROM media_counts_v2 WHERE uid = %d AND type = %d LIMIT 1", dialogId, type));
                }
                if (cursor.next()) {
                    count = cursor.intValue(0);
                    old = cursor.intValue(1);
                }
                cursor.dispose();
                if (count == -1 && DialogObject.isEncryptedDialog(dialogId)) {
                    cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM media_v4 WHERE uid = %d AND type = %d LIMIT 1", dialogId, type));
                    if (cursor.next()) {
                        count = cursor.intValue(0);
                    }
                    cursor.dispose();

                    if (count != -1) {
                        putMediaCountDatabase(dialogId, topicId, type, count);
                    }
                }
                processLoadedMediaCount(count, dialogId, topicId, type, classGuid, true, old);
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void loadMediaDatabase(long uid, int count, int max_id, int min_id, int type, int topicId, int classGuid, boolean isChannel, int fromCache, int requestIndex) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                boolean topReached = false;
                TLRPC.TL_messages_messages res = new TLRPC.TL_messages_messages();
                try {
                    ArrayList<Long> usersToLoad = new ArrayList<>();
                    ArrayList<Long> chatsToLoad = new ArrayList<>();
                    int countToLoad = count + 1;

                    SQLiteCursor cursor;
                    SQLiteDatabase database = getMessagesStorage().getDatabase();
                    boolean isEnd = false;
                    boolean reverseMessages = false;

                    if (!DialogObject.isEncryptedDialog(uid)) {
                        if (min_id == 0) {
                            if (topicId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d AND start IN (0, 1)", uid, topicId, type));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM media_holes_v2 WHERE uid = %d AND type = %d AND start IN (0, 1)", uid, type));
                            }
                            if (cursor.next()) {
                                isEnd = cursor.intValue(0) == 1;
                            } else {
                                cursor.dispose();
                                if (topicId != 0) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM media_topics WHERE uid = %d AND topic_id = %d AND type = %d AND mid > 0", uid, topicId, type));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM media_v4 WHERE uid = %d AND type = %d AND mid > 0", uid, type));
                                }
                                if (cursor.next()) {
                                    int mid = cursor.intValue(0);
                                    if (mid != 0) {
                                        SQLitePreparedStatement state;
                                        if (topicId != 0) {
                                            state = database.executeFast("REPLACE INTO media_holes_topics VALUES(?, ?, ?, ?, ?)");
                                        } else {
                                            state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                                        }
                                        int pointer = 1;
                                        state.requery();
                                        state.bindLong(pointer++, uid);
                                        if (topicId != 0) {
                                            state.bindInteger(pointer++, topicId);
                                        }
                                        state.bindInteger(pointer++, type);
                                        state.bindInteger(pointer++, 0);
                                        state.bindInteger(pointer++, mid);
                                        state.step();
                                        state.dispose();
                                    }
                                }
                            }
                            cursor.dispose();
                        }

                        int holeMessageId = 0;
                        if (max_id != 0) {
                            int startHole = 0;
                            if (topicId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d AND start <= %d ORDER BY end DESC LIMIT 1", uid, topicId, type, max_id));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM media_holes_v2 WHERE uid = %d AND type = %d AND start <= %d ORDER BY end DESC LIMIT 1", uid, type, max_id));
                            }
                            if (cursor.next()) {
                                startHole = cursor.intValue(0);
                                holeMessageId = cursor.intValue(1);
                            }
                            cursor.dispose();

                            if (topicId != 0) {
                                if (holeMessageId > 1) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_topics WHERE uid = %d AND topic_id = %d AND mid > 0 AND mid < %d AND mid >= %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, topicId, max_id, holeMessageId, type, countToLoad));
                                    isEnd = false;
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_topics WHERE uid = %d AND topic_id = %d AND mid > 0 AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, topicId, max_id, type, countToLoad));
                                }
                            } else {
                                if (holeMessageId > 1) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND mid < %d AND mid >= %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, max_id, holeMessageId, type, countToLoad));
                                    isEnd = false;
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, max_id, type, countToLoad));
                                }
                            }
                        } else if (min_id != 0) {
                            int startHole = 0;
                            if (topicId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d AND end >= %d ORDER BY end ASC LIMIT 1", uid, topicId, type, min_id));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT start, end FROM media_holes_v2 WHERE uid = %d AND type = %d AND end >= %d ORDER BY end ASC LIMIT 1", uid, type, min_id));
                            }
                            if (cursor.next()) {
                                startHole = cursor.intValue(0);
                                holeMessageId = cursor.intValue(1);
                            }
                            cursor.dispose();
                            reverseMessages = true;
                            if (topicId != 0) {
                                if (startHole > 1) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_topics WHERE uid = %d AND topic_id = %d AND mid > 0 AND mid >= %d AND mid <= %d AND type = %d ORDER BY date ASC, mid ASC LIMIT %d", uid, topicId, min_id, startHole, type, countToLoad));
                                } else {
                                    isEnd = true;
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_topics WHERE uid = %d AND topic_id = %d AND mid > 0 AND mid >= %d AND type = %d ORDER BY date ASC, mid ASC LIMIT %d", uid, topicId, min_id, type, countToLoad));
                                }
                            } else {
                                if (startHole > 1) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND mid >= %d AND mid <= %d AND type = %d ORDER BY date ASC, mid ASC LIMIT %d", uid, min_id, startHole, type, countToLoad));
                                } else {
                                    isEnd = true;
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND mid >= %d AND type = %d ORDER BY date ASC, mid ASC LIMIT %d", uid, min_id, type, countToLoad));
                                }
                            }
                        } else {
                            if (topicId != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(end) FROM media_holes_topics WHERE uid = %d AND topic_id = %d AND type = %d", uid, topicId, type));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(end) FROM media_holes_v2 WHERE uid = %d AND type = %d", uid, type));
                            }
                            if (cursor.next()) {
                                holeMessageId = cursor.intValue(0);
                            }
                            cursor.dispose();
                            if (topicId != 0) {
                                if (holeMessageId > 1) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_topics WHERE uid = %d AND topic_id = %d AND mid >= %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, topicId, holeMessageId, type, countToLoad));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_topics WHERE uid = %d AND topic_id = %d AND mid > 0 AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, topicId, type, countToLoad));
                                }
                            } else {
                                if (holeMessageId > 1) {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid >= %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, holeMessageId, type, countToLoad));
                                } else {
                                    cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > 0 AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, type, countToLoad));
                                }
                            }
                        }
                    } else {
                        isEnd = true;
                        if (topicId != 0) {
                            if (max_id != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_topics as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND m.topic_id = %d AND m.mid > %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, topicId, max_id, type, countToLoad));
                            } else if (min_id != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_topics as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND m.topic_id = %d AND m.mid < %d AND type = %d ORDER BY m.mid DESC LIMIT %d", uid, topicId, min_id, type, countToLoad));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_topics as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND m.topic_id = %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, topicId, type, countToLoad));
                            }
                        } else {
                            if (max_id != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v4 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, max_id, type, countToLoad));
                            } else if (min_id != 0) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v4 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid < %d AND type = %d ORDER BY m.mid DESC LIMIT %d", uid, min_id, type, countToLoad));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v4 as m LEFT JOIN randoms_v2 as r ON r.mid = m.mid WHERE m.uid = %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, type, countToLoad));
                            }
                        }
                    }


                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.dialog_id = uid;
                            if (DialogObject.isEncryptedDialog(uid)) {
                                message.random_id = cursor.longValue(2);
                            }
                            if (reverseMessages) {
                                res.messages.add(0, message);
                            } else {
                                res.messages.add(message);
                            }

                            MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                        }
                    }
                    cursor.dispose();

                    if (!usersToLoad.isEmpty()) {
                        getMessagesStorage().getUsersInternal(TextUtils.join(",", usersToLoad), res.users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        getMessagesStorage().getChatsInternal(TextUtils.join(",", chatsToLoad), res.chats);
                    }
                    if (res.messages.size() > count && min_id == 0) {
                        res.messages.remove(res.messages.size() - 1);
                    } else {
                        if (min_id != 0) {
                            topReached = false;
                        } else {
                            topReached = isEnd;
                        }
                    }
                } catch (Exception e) {
                    res.messages.clear();
                    res.chats.clear();
                    res.users.clear();
                    FileLog.e(e);
                } finally {
                    Runnable task = this;
                    AndroidUtilities.runOnUIThread(() -> getMessagesStorage().completeTaskForGuid(task, classGuid));
                    processLoadedMedia(res, uid, count, max_id, min_id, type, topicId, fromCache, classGuid, isChannel, topReached, requestIndex);
                }
            }
        };
        MessagesStorage messagesStorage = getMessagesStorage();
        messagesStorage.getStorageQueue().postRunnable(runnable);
        messagesStorage.bindTaskToGuid(runnable, classGuid);
    }

    private void putMediaDatabase(long uid, int topicId, int type, ArrayList<TLRPC.Message> messages, int max_id, int min_id, boolean topReached) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (min_id == 0 && (messages.isEmpty() || topReached)) {
                    getMessagesStorage().doneHolesInMedia(uid, max_id, type, topicId);
                    if (messages.isEmpty()) {
                        return;
                    }
                }
                getMessagesStorage().getDatabase().beginTransaction();
                SQLitePreparedStatement state2;
                if (topicId != 0) {
                    state2 = getMessagesStorage().getDatabase().executeFast("REPLACE INTO media_topics VALUES(?, ?, ?, ?, ?, ?)");
                } else {
                    state2 = getMessagesStorage().getDatabase().executeFast("REPLACE INTO media_v4 VALUES(?, ?, ?, ?, ?)");
                }

                for (TLRPC.Message message : messages) {
                    if (canAddMessageToMedia(message)) {
                        state2.requery();
                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);
                        int pointer = 1;
                        state2.bindInteger(pointer++, message.id);
                        state2.bindLong(pointer++, uid);
                        if (topicId != 0) {
                            state2.bindInteger(pointer++, topicId);
                        }
                        state2.bindInteger(pointer++, message.date);
                        state2.bindInteger(pointer++, type);
                        state2.bindByteBuffer(pointer++, data);
                        state2.step();
                        data.reuse();
                    }
                }
                state2.dispose();
                if (!topReached || max_id != 0 || min_id != 0) {
                    int minId = (topReached && min_id == 0) ? 1 : messages.get(messages.size() - 1).id;
                    if (min_id != 0) {
                        getMessagesStorage().closeHolesInMedia(uid, minId, messages.get(0).id, type, topicId);
                    } else if (max_id != 0) {
                        getMessagesStorage().closeHolesInMedia(uid, minId, max_id, type, topicId);
                    } else {
                        getMessagesStorage().closeHolesInMedia(uid, minId, Integer.MAX_VALUE, type, topicId);
                    }
                }
                getMessagesStorage().getDatabase().commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void loadMusic(long dialogId, long maxId, long minId) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            ArrayList<MessageObject> arrayListBegin = new ArrayList<>();
            ArrayList<MessageObject> arrayListEnd = new ArrayList<>();
            try {
                for (int a = 0; a < 2; a++) {
                    ArrayList<MessageObject> arrayList = a == 0 ? arrayListBegin : arrayListEnd;
                    SQLiteCursor cursor;
                    if (a == 0) {
                        if (!DialogObject.isEncryptedDialog(dialogId)) {
                            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", dialogId, maxId, MEDIA_MUSIC));
                        } else {
                            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", dialogId, maxId, MEDIA_MUSIC));
                        }
                    } else {
                        if (!DialogObject.isEncryptedDialog(dialogId)) {
                            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid > %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", dialogId, minId, MEDIA_MUSIC));
                        } else {
                            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v4 WHERE uid = %d AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", dialogId, minId, MEDIA_MUSIC));
                        }
                    }

                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            if (MessageObject.isMusicMessage(message)) {
                                message.id = cursor.intValue(1);
                                message.dialog_id = dialogId;
                                arrayList.add(0, new MessageObject(currentAccount, message, false, true));
                            }
                        }
                    }
                    cursor.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.musicDidLoad, dialogId, arrayListBegin, arrayListEnd));
        });
    }
    //---------------- MEDIA END ----------------

    public ArrayList<TLRPC.TL_topPeer> hints = new ArrayList<>();
    public ArrayList<TLRPC.TL_topPeer> inlineBots = new ArrayList<>();
    boolean loaded;
    boolean loading;

    private static Paint roundPaint, erasePaint;
    private static RectF bitmapRect;
    private static Path roundPath;

    public void buildShortcuts() {
        if (Build.VERSION.SDK_INT < 23) {
            return;
        }
        int maxShortcuts = ShortcutManagerCompat.getMaxShortcutCountPerActivity(ApplicationLoader.applicationContext) - 2;
        if (maxShortcuts <= 0) {
            maxShortcuts = 5;
        }
        ArrayList<TLRPC.TL_topPeer> hintsFinal = new ArrayList<>();
        if (SharedConfig.passcodeHash.length() <= 0) {
            for (int a = 0; a < hints.size(); a++) {
                hintsFinal.add(hints.get(a));
                if (hintsFinal.size() == maxShortcuts - 2) {
                    break;
                }
            }
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (SharedConfig.directShareHash == null) {
                    SharedConfig.directShareHash = UUID.randomUUID().toString();
                    ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit().putString("directShareHash2", SharedConfig.directShareHash).commit();
                }

                List<ShortcutInfoCompat> currentShortcuts = ShortcutManagerCompat.getDynamicShortcuts(ApplicationLoader.applicationContext);
                ArrayList<String> shortcutsToUpdate = new ArrayList<>();
                ArrayList<String> newShortcutsIds = new ArrayList<>();
                ArrayList<String> shortcutsToDelete = new ArrayList<>();

                if (currentShortcuts != null && !currentShortcuts.isEmpty()) {
                    newShortcutsIds.add("compose");
                    for (int a = 0; a < hintsFinal.size(); a++) {
                        TLRPC.TL_topPeer hint = hintsFinal.get(a);
                        newShortcutsIds.add("did3_" + MessageObject.getPeerId(hint.peer));
                    }
                    for (int a = 0; a < currentShortcuts.size(); a++) {
                        String id = currentShortcuts.get(a).getId();
                        if (!newShortcutsIds.remove(id)) {
                            shortcutsToDelete.add(id);
                        }
                        shortcutsToUpdate.add(id);
                    }
                    if (newShortcutsIds.isEmpty() && shortcutsToDelete.isEmpty()) {
                        return;
                    }
                }

                Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
                intent.setAction("new_dialog");
                ArrayList<ShortcutInfoCompat> arrayList = new ArrayList<>();
                arrayList.add(new ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, "compose")
                        .setShortLabel(LocaleController.getString("NewConversationShortcut", R.string.NewConversationShortcut))
                        .setLongLabel(LocaleController.getString("NewConversationShortcut", R.string.NewConversationShortcut))
                        .setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.shortcut_compose))
                        .setIntent(intent)
                        .build());
                if (shortcutsToUpdate.contains("compose")) {
                    ShortcutManagerCompat.updateShortcuts(ApplicationLoader.applicationContext, arrayList);
                } else {
                    ShortcutManagerCompat.addDynamicShortcuts(ApplicationLoader.applicationContext, arrayList);
                }
                arrayList.clear();

                if (!shortcutsToDelete.isEmpty()) {
                    ShortcutManagerCompat.removeDynamicShortcuts(ApplicationLoader.applicationContext, shortcutsToDelete);
                }

                HashSet<String> category = new HashSet<>(1);
                category.add(SHORTCUT_CATEGORY);

                for (int a = 0; a < hintsFinal.size(); a++) {
                    Intent shortcutIntent = new Intent(ApplicationLoader.applicationContext, OpenChatReceiver.class);
                    TLRPC.TL_topPeer hint = hintsFinal.get(a);

                    TLRPC.User user = null;
                    TLRPC.Chat chat = null;
                    long peerId = MessageObject.getPeerId(hint.peer);
                    if (DialogObject.isUserDialog(peerId)) {
                        shortcutIntent.putExtra("userId", peerId);
                        user = getMessagesController().getUser(peerId);
                    } else {
                        chat = getMessagesController().getChat(-peerId);
                        shortcutIntent.putExtra("chatId", -peerId);
                    }
                    if ((user == null || UserObject.isDeleted(user)) && chat == null) {
                        continue;
                    }

                    String name;
                    TLRPC.FileLocation photo = null;

                    if (user != null) {
                        name = ContactsController.formatName(user.first_name, user.last_name);
                        if (user.photo != null) {
                            photo = user.photo.photo_small;
                        }
                    } else {
                        name = chat.title;
                        if (chat.photo != null) {
                            photo = chat.photo.photo_small;
                        }
                    }

                    shortcutIntent.putExtra("currentAccount", currentAccount);
                    shortcutIntent.setAction("com.tmessages.openchat" + peerId);
                    shortcutIntent.putExtra("dialogId", peerId);
                    shortcutIntent.putExtra("hash", SharedConfig.directShareHash);
                    shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    Bitmap bitmap = null;
                    if (photo != null) {
                        try {
                            File path = getFileLoader().getPathToAttach(photo, true);
                            bitmap = BitmapFactory.decodeFile(path.toString());
                            if (bitmap != null) {
                                int size = AndroidUtilities.dp(48);
                                Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                                Canvas canvas = new Canvas(result);
                                if (roundPaint == null) {
                                    roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                                    bitmapRect = new RectF();
                                    erasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                    erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                                    roundPath = new Path();
                                    roundPath.addCircle(size / 2, size / 2, size / 2 - AndroidUtilities.dp(2), Path.Direction.CW);
                                    roundPath.toggleInverseFillType();
                                }
                                bitmapRect.set(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(46), AndroidUtilities.dp(46));
                                canvas.drawBitmap(bitmap, null, bitmapRect, roundPaint);
                                canvas.drawPath(roundPath, erasePaint);
                                try {
                                    canvas.setBitmap(null);
                                } catch (Exception ignore) {

                                }
                                bitmap = result;
                            }
                        } catch (Throwable e) {
                            FileLog.e(e);
                        }
                    }

                    String id = "did3_" + peerId;
                    if (TextUtils.isEmpty(name)) {
                        name = " ";
                    }
                    ShortcutInfoCompat.Builder builder = new ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, id)
                            .setShortLabel(name)
                            .setLongLabel(name)
                            .setIntent(shortcutIntent);
                    if (SharedConfig.directShare) {
                        builder.setCategories(category);
                    }
                    if (bitmap != null) {
                        builder.setIcon(IconCompat.createWithBitmap(bitmap));
                    } else {
                        builder.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.shortcut_user));
                    }
                    arrayList.add(builder.build());
                    if (shortcutsToUpdate.contains(id)) {
                        ShortcutManagerCompat.updateShortcuts(ApplicationLoader.applicationContext, arrayList);
                    } else {
                        ShortcutManagerCompat.addDynamicShortcuts(ApplicationLoader.applicationContext, arrayList);
                    }
                    arrayList.clear();
                }
            } catch (Throwable ignore) {

            }
        });
    }

    public void loadHints(boolean cache) {
        if (loading || !getUserConfig().suggestContacts) {
            return;
        }
        if (cache) {
            if (loaded) {
                return;
            }
            loading = true;
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                ArrayList<TLRPC.TL_topPeer> hintsNew = new ArrayList<>();
                ArrayList<TLRPC.TL_topPeer> inlineBotsNew = new ArrayList<>();
                ArrayList<TLRPC.User> users = new ArrayList<>();
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                long selfUserId = getUserConfig().getClientUserId();
                try {
                    ArrayList<Long> usersToLoad = new ArrayList<>();
                    ArrayList<Long> chatsToLoad = new ArrayList<>();
                    SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT did, type, rating FROM chat_hints WHERE 1 ORDER BY rating DESC");
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        if (did == selfUserId) {
                            continue;
                        }
                        int type = cursor.intValue(1);
                        TLRPC.TL_topPeer peer = new TLRPC.TL_topPeer();
                        peer.rating = cursor.doubleValue(2);
                        if (did > 0) {
                            peer.peer = new TLRPC.TL_peerUser();
                            peer.peer.user_id = did;
                            usersToLoad.add(did);
                        } else {
                            peer.peer = new TLRPC.TL_peerChat();
                            peer.peer.chat_id = -did;
                            chatsToLoad.add(-did);
                        }
                        if (type == 0) {
                            hintsNew.add(peer);
                        } else if (type == 1) {
                            inlineBotsNew.add(peer);
                        }
                    }
                    cursor.dispose();
                    if (!usersToLoad.isEmpty()) {
                        getMessagesStorage().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }

                    if (!chatsToLoad.isEmpty()) {
                        getMessagesStorage().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        getMessagesController().putUsers(users, true);
                        getMessagesController().putChats(chats, true);
                        loading = false;
                        loaded = true;
                        hints = hintsNew;
                        inlineBots = inlineBotsNew;
                        buildShortcuts();
                        getNotificationCenter().postNotificationName(NotificationCenter.reloadHints);
                        getNotificationCenter().postNotificationName(NotificationCenter.reloadInlineHints);
                        if (Math.abs(getUserConfig().lastHintsSyncTime - (int) (System.currentTimeMillis() / 1000)) >= 24 * 60 * 60) {
                            loadHints(false);
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
            loaded = true;
        } else {
            loading = true;
            TLRPC.TL_contacts_getTopPeers req = new TLRPC.TL_contacts_getTopPeers();
            req.hash = 0;
            req.bots_pm = false;
            req.correspondents = true;
            req.groups = false;
            req.channels = false;
            req.bots_inline = true;
            req.offset = 0;
            req.limit = 20;
            getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.TL_contacts_topPeers) {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.TL_contacts_topPeers topPeers = (TLRPC.TL_contacts_topPeers) response;
                        getMessagesController().putUsers(topPeers.users, false);
                        getMessagesController().putChats(topPeers.chats, false);
                        for (int a = 0; a < topPeers.categories.size(); a++) {
                            TLRPC.TL_topPeerCategoryPeers category = topPeers.categories.get(a);
                            if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                                inlineBots = category.peers;
                                getUserConfig().botRatingLoadTime = (int) (System.currentTimeMillis() / 1000);
                            } else {
                                hints = category.peers;
                                long selfUserId = getUserConfig().getClientUserId();
                                for (int b = 0; b < hints.size(); b++) {
                                    TLRPC.TL_topPeer topPeer = hints.get(b);
                                    if (topPeer.peer.user_id == selfUserId) {
                                        hints.remove(b);
                                        break;
                                    }
                                }
                                getUserConfig().ratingLoadTime = (int) (System.currentTimeMillis() / 1000);
                            }
                        }
                        getUserConfig().saveConfig(false);
                        buildShortcuts();
                        getNotificationCenter().postNotificationName(NotificationCenter.reloadHints);
                        getNotificationCenter().postNotificationName(NotificationCenter.reloadInlineHints);
                        getMessagesStorage().getStorageQueue().postRunnable(() -> {
                            try {
                                getMessagesStorage().getDatabase().executeFast("DELETE FROM chat_hints WHERE 1").stepThis().dispose();
                                getMessagesStorage().getDatabase().beginTransaction();
                                getMessagesStorage().putUsersAndChats(topPeers.users, topPeers.chats, false, false);

                                SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)");
                                for (int a = 0; a < topPeers.categories.size(); a++) {
                                    int type;
                                    TLRPC.TL_topPeerCategoryPeers category = topPeers.categories.get(a);
                                    if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                                        type = 1;
                                    } else {
                                        type = 0;
                                    }
                                    for (int b = 0; b < category.peers.size(); b++) {
                                        TLRPC.TL_topPeer peer = category.peers.get(b);
                                        state.requery();
                                        state.bindLong(1, MessageObject.getPeerId(peer.peer));
                                        state.bindInteger(2, type);
                                        state.bindDouble(3, peer.rating);
                                        state.bindInteger(4, 0);
                                        state.step();
                                    }
                                }

                                state.dispose();

                                getMessagesStorage().getDatabase().commitTransaction();
                                AndroidUtilities.runOnUIThread(() -> {
                                    getUserConfig().suggestContacts = true;
                                    getUserConfig().lastHintsSyncTime = (int) (System.currentTimeMillis() / 1000);
                                    getUserConfig().saveConfig(false);
                                });
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        });
                    });
                } else if (response instanceof TLRPC.TL_contacts_topPeersDisabled) {
                    AndroidUtilities.runOnUIThread(() -> {
                        getUserConfig().suggestContacts = false;
                        getUserConfig().lastHintsSyncTime = (int) (System.currentTimeMillis() / 1000);
                        getUserConfig().saveConfig(false);
                        clearTopPeers();
                    });
                }
            });
        }
    }

    public void clearTopPeers() {
        hints.clear();
        inlineBots.clear();
        getNotificationCenter().postNotificationName(NotificationCenter.reloadHints);
        getNotificationCenter().postNotificationName(NotificationCenter.reloadInlineHints);
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                getMessagesStorage().getDatabase().executeFast("DELETE FROM chat_hints WHERE 1").stepThis().dispose();
            } catch (Exception ignore) {

            }
        });
        buildShortcuts();
    }

    public void increaseInlineRaiting(long uid) {
        if (!getUserConfig().suggestContacts) {
            return;
        }
        int dt;
        if (getUserConfig().botRatingLoadTime != 0) {
            dt = Math.max(1, ((int) (System.currentTimeMillis() / 1000)) - getUserConfig().botRatingLoadTime);
        } else {
            dt = 60;
        }

        TLRPC.TL_topPeer peer = null;
        for (int a = 0; a < inlineBots.size(); a++) {
            TLRPC.TL_topPeer p = inlineBots.get(a);
            if (p.peer.user_id == uid) {
                peer = p;
                break;
            }
        }
        if (peer == null) {
            peer = new TLRPC.TL_topPeer();
            peer.peer = new TLRPC.TL_peerUser();
            peer.peer.user_id = uid;
            inlineBots.add(peer);
        }
        peer.rating += Math.exp(dt / getMessagesController().ratingDecay);
        Collections.sort(inlineBots, (lhs, rhs) -> {
            if (lhs.rating > rhs.rating) {
                return -1;
            } else if (lhs.rating < rhs.rating) {
                return 1;
            }
            return 0;
        });
        if (inlineBots.size() > 20) {
            inlineBots.remove(inlineBots.size() - 1);
        }
        savePeer(uid, 1, peer.rating);
        getNotificationCenter().postNotificationName(NotificationCenter.reloadInlineHints);
    }

    public void removeInline(long dialogId) {
        TLRPC.TL_topPeerCategoryPeers category = null;
        for (int a = 0; a < inlineBots.size(); a++) {
            if (inlineBots.get(a).peer.user_id == dialogId) {
                inlineBots.remove(a);
                TLRPC.TL_contacts_resetTopPeerRating req = new TLRPC.TL_contacts_resetTopPeerRating();
                req.category = new TLRPC.TL_topPeerCategoryBotsInline();
                req.peer = getMessagesController().getInputPeer(dialogId);
                getConnectionsManager().sendRequest(req, (response, error) -> {

                });
                deletePeer(dialogId, 1);
                getNotificationCenter().postNotificationName(NotificationCenter.reloadInlineHints);
                return;
            }
        }
    }

    public void removePeer(long uid) {
        for (int a = 0; a < hints.size(); a++) {
            if (hints.get(a).peer.user_id == uid) {
                hints.remove(a);
                getNotificationCenter().postNotificationName(NotificationCenter.reloadHints);
                TLRPC.TL_contacts_resetTopPeerRating req = new TLRPC.TL_contacts_resetTopPeerRating();
                req.category = new TLRPC.TL_topPeerCategoryCorrespondents();
                req.peer = getMessagesController().getInputPeer(uid);
                deletePeer(uid, 0);
                getConnectionsManager().sendRequest(req, (response, error) -> {

                });
                return;
            }
        }
    }

    public void increasePeerRaiting(long dialogId) {
        if (!getUserConfig().suggestContacts) {
            return;
        }
        if (!DialogObject.isUserDialog(dialogId)) {
            return;
        }
        TLRPC.User user = getMessagesController().getUser(dialogId);
        if (user == null || user.bot || user.self) {
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            double dt = 0;
            try {
                int lastTime = 0;
                int lastMid = 0;
                SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT MAX(mid), MAX(date) FROM messages_v2 WHERE uid = %d AND out = 1", dialogId));
                if (cursor.next()) {
                    lastMid = cursor.intValue(0);
                    lastTime = cursor.intValue(1);
                }
                cursor.dispose();
                if (lastMid > 0 && getUserConfig().ratingLoadTime != 0) {
                    dt = (lastTime - getUserConfig().ratingLoadTime);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            double dtFinal = dt;
            AndroidUtilities.runOnUIThread(() -> {
                TLRPC.TL_topPeer peer = null;
                for (int a = 0; a < hints.size(); a++) {
                    TLRPC.TL_topPeer p = hints.get(a);
                    if (p.peer.user_id == dialogId) {
                        peer = p;
                        break;
                    }
                }
                if (peer == null) {
                    peer = new TLRPC.TL_topPeer();
                    peer.peer = new TLRPC.TL_peerUser();
                    peer.peer.user_id = dialogId;
                    hints.add(peer);
                }
                peer.rating += Math.exp(dtFinal / getMessagesController().ratingDecay);
                Collections.sort(hints, (lhs, rhs) -> {
                    if (lhs.rating > rhs.rating) {
                        return -1;
                    } else if (lhs.rating < rhs.rating) {
                        return 1;
                    }
                    return 0;
                });

                savePeer(dialogId, 0, peer.rating);

                getNotificationCenter().postNotificationName(NotificationCenter.reloadHints);
            });
        });
    }

    private void savePeer(long did, int type, double rating) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)");
                state.requery();
                state.bindLong(1, did);
                state.bindInteger(2, type);
                state.bindDouble(3, rating);
                state.bindInteger(4, (int) System.currentTimeMillis() / 1000);
                state.step();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void deletePeer(long dialogId, int type) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                getMessagesStorage().getDatabase().executeFast(String.format(Locale.US, "DELETE FROM chat_hints WHERE did = %d AND type = %d", dialogId, type)).stepThis().dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private Intent createIntrnalShortcutIntent(long dialogId) {
        Intent shortcutIntent = new Intent(ApplicationLoader.applicationContext, OpenChatReceiver.class);

        if (DialogObject.isEncryptedDialog(dialogId)) {
            int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
            shortcutIntent.putExtra("encId", encryptedChatId);
            TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
            if (encryptedChat == null) {
                return null;
            }
        } else if (DialogObject.isUserDialog(dialogId)) {
            shortcutIntent.putExtra("userId", dialogId);
        } else if (DialogObject.isChatDialog(dialogId)) {
            shortcutIntent.putExtra("chatId", -dialogId);
        } else {
            return null;
        }
        shortcutIntent.putExtra("currentAccount", currentAccount);
        shortcutIntent.setAction("com.tmessages.openchat" + dialogId);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return shortcutIntent;
    }

    public void installShortcut(long dialogId) {
        try {
            Intent shortcutIntent = createIntrnalShortcutIntent(dialogId);

            TLRPC.User user = null;
            TLRPC.Chat chat = null;
            if (DialogObject.isEncryptedDialog(dialogId)) {
                int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
                TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
                if (encryptedChat == null) {
                    return;
                }
                user = getMessagesController().getUser(encryptedChat.user_id);
            } else if (DialogObject.isUserDialog(dialogId)) {
                user = getMessagesController().getUser(dialogId);
            } else if (DialogObject.isChatDialog(dialogId)) {
                chat = getMessagesController().getChat(-dialogId);
            } else {
                return;
            }
            if (user == null && chat == null) {
                return;
            }

            String name;
            TLRPC.FileLocation photo = null;

            boolean overrideAvatar = false;

            if (user != null) {
                if (UserObject.isReplyUser(user)) {
                    name = LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
                    overrideAvatar = true;
                } else if (UserObject.isUserSelf(user)) {
                    name = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                    overrideAvatar = true;
                } else {
                    name = ContactsController.formatName(user.first_name, user.last_name);
                    if (user.photo != null) {
                        photo = user.photo.photo_small;
                    }
                }
            } else {
                name = chat.title;
                if (chat.photo != null) {
                    photo = chat.photo.photo_small;
                }
            }

            Bitmap bitmap = null;
            if (overrideAvatar || photo != null) {
                try {
                    if (!overrideAvatar) {
                        File path = getFileLoader().getPathToAttach(photo, true);
                        bitmap = BitmapFactory.decodeFile(path.toString());
                    }
                    if (overrideAvatar || bitmap != null) {
                        int size = AndroidUtilities.dp(58);
                        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        result.eraseColor(Color.TRANSPARENT);
                        Canvas canvas = new Canvas(result);
                        if (overrideAvatar) {
                            AvatarDrawable avatarDrawable = new AvatarDrawable(user);
                            if (UserObject.isReplyUser(user)) {
                                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                            } else {
                                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                            }
                            avatarDrawable.setBounds(0, 0, size, size);
                            avatarDrawable.draw(canvas);
                        } else {
                            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                            if (roundPaint == null) {
                                roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                bitmapRect = new RectF();
                            }
                            float scale = size / (float) bitmap.getWidth();
                            canvas.save();
                            canvas.scale(scale, scale);
                            roundPaint.setShader(shader);
                            bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                            canvas.drawRoundRect(bitmapRect, bitmap.getWidth(), bitmap.getHeight(), roundPaint);
                            canvas.restore();
                        }
                        Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.book_logo);
                        int w = AndroidUtilities.dp(15);
                        int left = size - w - AndroidUtilities.dp(2);
                        int top = size - w - AndroidUtilities.dp(2);
                        drawable.setBounds(left, top, left + w, top + w);
                        drawable.draw(canvas);
                        try {
                            canvas.setBitmap(null);
                        } catch (Exception e) {
                            //don't promt, this will crash on 2.x
                        }
                        bitmap = result;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            if (Build.VERSION.SDK_INT >= 26) {
                ShortcutInfoCompat.Builder pinShortcutInfo =
                        new ShortcutInfoCompat.Builder(ApplicationLoader.applicationContext, "sdid_" + dialogId)
                                .setShortLabel(name)
                                .setIntent(shortcutIntent);

                if (bitmap != null) {
                    pinShortcutInfo.setIcon(IconCompat.createWithBitmap(bitmap));
                } else {
                    if (user != null) {
                        if (user.bot) {
                            pinShortcutInfo.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_bot));
                        } else {
                            pinShortcutInfo.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_user));
                        }
                    } else {
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            pinShortcutInfo.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_channel));
                        } else {
                            pinShortcutInfo.setIcon(IconCompat.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_group));
                        }
                    }
                }

                ShortcutManagerCompat.requestPinShortcut(ApplicationLoader.applicationContext, pinShortcutInfo.build(), null);
            } else {
                Intent addIntent = new Intent();
                if (bitmap != null) {
                    addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
                } else {
                    if (user != null) {
                        if (user.bot) {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_bot));
                        } else {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_user));
                        }
                    } else {
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_channel));
                        } else {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_group));
                        }
                    }
                }

                addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
                addIntent.putExtra("duplicate", false);

                addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
                ApplicationLoader.applicationContext.sendBroadcast(addIntent);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void uninstallShortcut(long dialogId) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ArrayList<String> arrayList = new ArrayList<>();
                arrayList.add("sdid_" + dialogId);
                arrayList.add("ndid_" + dialogId);
                ShortcutManagerCompat.removeDynamicShortcuts(ApplicationLoader.applicationContext, arrayList);
                if (Build.VERSION.SDK_INT >= 30) {
                    ShortcutManager shortcutManager = ApplicationLoader.applicationContext.getSystemService(ShortcutManager.class);
                    shortcutManager.removeLongLivedShortcuts(arrayList);
                }
            } else {
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    int encryptedChatId = DialogObject.getEncryptedChatId(dialogId);
                    TLRPC.EncryptedChat encryptedChat = getMessagesController().getEncryptedChat(encryptedChatId);
                    if (encryptedChat == null) {
                        return;
                    }
                    user = getMessagesController().getUser(encryptedChat.user_id);
                } else if (DialogObject.isUserDialog(dialogId)) {
                    user = getMessagesController().getUser(dialogId);
                } else if (DialogObject.isChatDialog(dialogId)) {
                    chat = getMessagesController().getChat(-dialogId);
                } else {
                    return;
                }
                if (user == null && chat == null) {
                    return;
                }
                String name;

                if (user != null) {
                    name = ContactsController.formatName(user.first_name, user.last_name);
                } else {
                    name = chat.title;
                }

                Intent addIntent = new Intent();
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, createIntrnalShortcutIntent(dialogId));
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
                addIntent.putExtra("duplicate", false);

                addIntent.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT");
                ApplicationLoader.applicationContext.sendBroadcast(addIntent);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
    //---------------- SEARCH END ----------------

    private static Comparator<TLRPC.MessageEntity> entityComparator = (entity1, entity2) -> {
        if (entity1.offset > entity2.offset) {
            return 1;
        } else if (entity1.offset < entity2.offset) {
            return -1;
        }
        return 0;
    };

    private LongSparseArray<Boolean> loadingPinnedMessages = new LongSparseArray<>();

    public void loadPinnedMessages(long dialogId, int maxId, int fallback) {
        if (loadingPinnedMessages.indexOfKey(dialogId) >= 0) {
            return;
        }
        loadingPinnedMessages.put(dialogId, true);
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = getMessagesController().getInputPeer(dialogId);
        req.limit = 40;
        req.offset_id = maxId;
        req.q = "";
        req.filter = new TLRPC.TL_inputMessagesFilterPinned();
        getConnectionsManager().sendRequest(req, (response, error) -> {
            ArrayList<Integer> ids = new ArrayList<>();
            HashMap<Integer, MessageObject> messages = new HashMap<>();
            int totalCount = 0;
            boolean endReached;
            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
                for (int a = 0; a < res.users.size(); a++) {
                    TLRPC.User user = res.users.get(a);
                    usersDict.put(user.id, user);
                }
                LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();
                for (int a = 0; a < res.chats.size(); a++) {
                    TLRPC.Chat chat = res.chats.get(a);
                    chatsDict.put(chat.id, chat);
                }
                getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                for (int a = 0, N = res.messages.size(); a < N; a++) {
                    TLRPC.Message message = res.messages.get(a);
                    if (message instanceof TLRPC.TL_messageService || message instanceof TLRPC.TL_messageEmpty) {
                        continue;
                    }
                    ids.add(message.id);
                    messages.put(message.id, new MessageObject(currentAccount, message, usersDict, chatsDict, false, false));
                }
                if (fallback != 0 && ids.isEmpty()) {
                    ids.add(fallback);
                }
                endReached = res.messages.size() < req.limit;
                totalCount = Math.max(res.count, res.messages.size());
            } else {
                if (fallback != 0) {
                    ids.add(fallback);
                    totalCount = 1;
                }
                endReached = false;
            }
            getMessagesStorage().updatePinnedMessages(dialogId, ids, true, totalCount, maxId, endReached, messages);
            AndroidUtilities.runOnUIThread(() -> loadingPinnedMessages.remove(dialogId));
        });
    }

    public ArrayList<MessageObject> loadPinnedMessages(long dialogId, long channelId, ArrayList<Integer> mids, boolean useQueue) {
        if (useQueue) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> loadPinnedMessageInternal(dialogId, channelId, mids, false));
        } else {
            return loadPinnedMessageInternal(dialogId, channelId, mids, true);
        }
        return null;
    }

    private ArrayList<MessageObject> loadPinnedMessageInternal(long dialogId, long channelId, ArrayList<Integer> mids, boolean returnValue) {
        try {
            ArrayList<Integer> midsCopy = new ArrayList<>(mids);
            CharSequence longIds;
            if (channelId != 0) {
                StringBuilder builder = new StringBuilder();
                for (int a = 0, N = mids.size(); a < N; a++) {
                    Integer messageId = mids.get(a);
                    if (builder.length() != 0) {
                        builder.append(",");
                    }
                    builder.append(messageId);
                }
                longIds = builder;
            } else {
                longIds = TextUtils.join(",", mids);
            }

            ArrayList<TLRPC.Message> results = new ArrayList<>();
            ArrayList<TLRPC.User> users = new ArrayList<>();
            ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            ArrayList<Long> usersToLoad = new ArrayList<>();
            ArrayList<Long> chatsToLoad = new ArrayList<>();

            long selfUserId = getUserConfig().clientUserId;

            SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid, date FROM messages_v2 WHERE mid IN (%s) AND uid = %d", longIds, dialogId));
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.Message result = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (!(result.action instanceof TLRPC.TL_messageActionHistoryClear)) {
                        result.readAttachPath(data, selfUserId);
                        result.id = cursor.intValue(1);
                        result.date = cursor.intValue(2);
                        result.dialog_id = dialogId;
                        MessagesStorage.addUsersAndChatsFromMessage(result, usersToLoad, chatsToLoad, null);
                        results.add(result);
                        midsCopy.remove((Integer) result.id);
                    }
                    data.reuse();
                }
            }
            cursor.dispose();

            if (!midsCopy.isEmpty()) {
                cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data FROM chat_pinned_v2 WHERE uid = %d AND mid IN (%s)", dialogId, TextUtils.join(",", midsCopy)));
                while (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        TLRPC.Message result = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        if (!(result.action instanceof TLRPC.TL_messageActionHistoryClear)) {
                            result.readAttachPath(data, selfUserId);
                            result.dialog_id = dialogId;
                            MessagesStorage.addUsersAndChatsFromMessage(result, usersToLoad, chatsToLoad, null);
                            results.add(result);
                            midsCopy.remove((Integer) result.id);
                        }
                        data.reuse();
                    }
                }
                cursor.dispose();
            }

            if (!midsCopy.isEmpty()) {
                if (channelId != 0) {
                    TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                    req.channel = getMessagesController().getInputChannel(channelId);
                    req.id = midsCopy;
                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        boolean ok = false;
                        if (error == null) {
                            TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                            removeEmptyMessages(messagesRes.messages);
                            if (!messagesRes.messages.isEmpty()) {
                                TLRPC.Chat chat = getMessagesController().getChat(channelId);
                                ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                broadcastPinnedMessage(messagesRes.messages, messagesRes.users, messagesRes.chats, false, false);
                                getMessagesStorage().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                savePinnedMessages(dialogId, messagesRes.messages);
                                ok = true;
                            }
                        }
                        if (!ok) {
                            getMessagesStorage().updatePinnedMessages(dialogId, req.id, false, -1, 0, false, null);
                        }
                    });
                } else {
                    TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                    req.id = midsCopy;
                    getConnectionsManager().sendRequest(req, (response, error) -> {
                        boolean ok = false;
                        if (error == null) {
                            TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                            removeEmptyMessages(messagesRes.messages);
                            if (!messagesRes.messages.isEmpty()) {
                                ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                broadcastPinnedMessage(messagesRes.messages, messagesRes.users, messagesRes.chats, false, false);
                                getMessagesStorage().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                savePinnedMessages(dialogId, messagesRes.messages);
                                ok = true;
                            }
                        }
                        if (!ok) {
                            getMessagesStorage().updatePinnedMessages(dialogId, req.id, false, -1, 0, false, null);
                        }
                    });
                }
            }
            if (!results.isEmpty()) {
                if (!usersToLoad.isEmpty()) {
                    getMessagesStorage().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                }
                if (!chatsToLoad.isEmpty()) {
                    getMessagesStorage().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                }
                if (returnValue) {
                    return broadcastPinnedMessage(results, users, chats, true, true);
                } else {
                    broadcastPinnedMessage(results, users, chats, true, false);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void savePinnedMessages(long dialogId, ArrayList<TLRPC.Message> arrayList) {
        if (arrayList.isEmpty()) {
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                getMessagesStorage().getDatabase().beginTransaction();
                //SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("UPDATE chat_pinned_v2 SET data = ? WHERE uid = ? AND mid = ?");
                SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO chat_pinned_v2 VALUES(?, ?, ?)");
                for (int a = 0, N = arrayList.size(); a < N; a++) {
                    TLRPC.Message message = arrayList.get(a);
                    NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                    message.serializeToStream(data);
                    state.requery();
                    state.bindLong(1, dialogId);
                    state.bindInteger(2, message.id);
                    state.bindByteBuffer(3, data);
                    state.step();
                    data.reuse();
                }
                state.dispose();
                getMessagesStorage().getDatabase().commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private ArrayList<MessageObject> broadcastPinnedMessage(ArrayList<TLRPC.Message> results, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, boolean isCache, boolean returnValue) {
        if (results.isEmpty()) {
            return null;
        }
        LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
        for (int a = 0; a < users.size(); a++) {
            TLRPC.User user = users.get(a);
            usersDict.put(user.id, user);
        }
        LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();
        for (int a = 0; a < chats.size(); a++) {
            TLRPC.Chat chat = chats.get(a);
            chatsDict.put(chat.id, chat);
        }
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        if (returnValue) {
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().putUsers(users, isCache);
                getMessagesController().putChats(chats, isCache);
            });
            int checkedCount = 0;
            for (int a = 0, N = results.size(); a < N; a++) {
                TLRPC.Message message = results.get(a);
                if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaDocument || MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto) {
                    checkedCount++;
                }
                messageObjects.add(new MessageObject(currentAccount, message, usersDict, chatsDict, false, checkedCount < 30));
            }
            return messageObjects;
        } else {
            AndroidUtilities.runOnUIThread(() -> {
                getMessagesController().putUsers(users, isCache);
                getMessagesController().putChats(chats, isCache);
                int checkedCount = 0;
                for (int a = 0, N = results.size(); a < N; a++) {
                    TLRPC.Message message = results.get(a);
                    if (MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaDocument || MessageObject.getMedia(message) instanceof TLRPC.TL_messageMediaPhoto) {
                        checkedCount++;
                    }
                    messageObjects.add(new MessageObject(currentAccount, message, usersDict, chatsDict, false, checkedCount < 30));
                }
                AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.didLoadPinnedMessages, messageObjects.get(0).getDialogId(), null, true, messageObjects, null, 0, -1, false));
            });
        }
        return null;
    }

    private static void removeEmptyMessages(ArrayList<TLRPC.Message> messages) {
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message == null || message instanceof TLRPC.TL_messageEmpty || message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                messages.remove(a);
                a--;
            }
        }
    }

    public void loadReplyMessagesForMessages(ArrayList<MessageObject> messages, long dialogId, boolean scheduled, int threadMessageId, Runnable callback) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            ArrayList<Long> replyMessages = new ArrayList<>();
            LongSparseArray<ArrayList<MessageObject>> replyMessageRandomOwners = new LongSparseArray<>();
            for (int a = 0; a < messages.size(); a++) {
                MessageObject messageObject = messages.get(a);
                if (messageObject == null) {
                    continue;
                }
                if (messageObject.isReply() && messageObject.replyMessageObject == null) {
                    long id = messageObject.messageOwner.reply_to.reply_to_random_id;
                    ArrayList<MessageObject> messageObjects = replyMessageRandomOwners.get(id);
                    if (messageObjects == null) {
                        messageObjects = new ArrayList<>();
                        replyMessageRandomOwners.put(id, messageObjects);
                    }
                    messageObjects.add(messageObject);
                    if (!replyMessages.contains(id)) {
                        replyMessages.add(id);
                    }
                }
            }
            if (replyMessages.isEmpty()) {
                if (callback != null) {
                    callback.run();
                }
                return;
            }

            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                try {
                    ArrayList<MessageObject> loadedMessages = new ArrayList<>();
                    SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, m.date, r.random_id FROM randoms_v2 as r INNER JOIN messages_v2 as m ON r.mid = m.mid AND r.uid = m.uid WHERE r.random_id IN(%s)", TextUtils.join(",", replyMessages)));
                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, getUserConfig().clientUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.date = cursor.intValue(2);
                            message.dialog_id = dialogId;

                            long value = cursor.longValue(3);
                            ArrayList<MessageObject> arrayList = replyMessageRandomOwners.get(value);
                            replyMessageRandomOwners.remove(value);
                            if (arrayList != null) {
                                MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
                                loadedMessages.add(messageObject);
                                for (int b = 0; b < arrayList.size(); b++) {
                                    MessageObject object = arrayList.get(b);
                                    object.replyMessageObject = messageObject;
                                    object.messageOwner.reply_to = new TLRPC.TL_messageReplyHeader();
                                    object.messageOwner.reply_to.reply_to_msg_id = messageObject.getId();
                                }
                            }
                        }
                    }
                    cursor.dispose();
                    if (replyMessageRandomOwners.size() != 0) {
                        for (int b = 0; b < replyMessageRandomOwners.size(); b++) {
                            ArrayList<MessageObject> arrayList = replyMessageRandomOwners.valueAt(b);
                            for (int a = 0; a < arrayList.size(); a++) {
                                TLRPC.Message message = arrayList.get(a).messageOwner;
                                if (message.reply_to != null) {
                                    message.reply_to.reply_to_random_id = 0;
                                }
                            }
                        }
                    }
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.replyMessagesDidLoad, dialogId, loadedMessages, null));
                    if (callback != null) {
                        callback.run();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        } else {
            LongSparseArray<SparseArray<ArrayList<MessageObject>>> replyMessageOwners = new LongSparseArray<>();
            LongSparseArray<ArrayList<Integer>> dialogReplyMessagesIds = new LongSparseArray<>();
            for (int a = 0; a < messages.size(); a++) {
                MessageObject messageObject = messages.get(a);
                if (messageObject == null) {
                    continue;
                }
                if (messageObject.getId() > 0 && messageObject.isReply()) {
                    int messageId = messageObject.messageOwner.reply_to.reply_to_msg_id;
                    if (messageId == threadMessageId) {
                        continue;
                    }
                    long channelId = 0;
                    if (messageObject.messageOwner.reply_to.reply_to_peer_id != null) {
                        if (messageObject.messageOwner.reply_to.reply_to_peer_id.channel_id != 0) {
                            channelId = messageObject.messageOwner.reply_to.reply_to_peer_id.channel_id;
                        }
                    } else if (messageObject.messageOwner.peer_id.channel_id != 0) {
                        channelId = messageObject.messageOwner.peer_id.channel_id;
                    }

                    if (messageObject.replyMessageObject != null) {
                        if (messageObject.replyMessageObject.messageOwner == null || messageObject.replyMessageObject.messageOwner.peer_id == null || messageObject.messageOwner instanceof TLRPC.TL_messageEmpty) {
                            continue;
                        }
                        if (messageObject.replyMessageObject.messageOwner.peer_id.channel_id == channelId) {
                            continue;
                        }
                    }

                    SparseArray<ArrayList<MessageObject>> sparseArray = replyMessageOwners.get(dialogId);
                    ArrayList<Integer> ids = dialogReplyMessagesIds.get(channelId);
                    if (sparseArray == null) {
                        sparseArray = new SparseArray<>();
                        replyMessageOwners.put(dialogId, sparseArray);
                    }
                    if (ids == null) {
                        ids = new ArrayList<>();
                        dialogReplyMessagesIds.put(channelId, ids);
                    }
                    ArrayList<MessageObject> arrayList = sparseArray.get(messageId);
                    if (arrayList == null) {
                        arrayList = new ArrayList<>();
                        sparseArray.put(messageId, arrayList);
                        if (!ids.contains(messageId)) {
                            ids.add(messageId);
                        }
                    }
                    arrayList.add(messageObject);
                }
            }
            if (replyMessageOwners.isEmpty()) {
                if (callback != null) {
                    callback.run();
                }
                return;
            }

            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                try {
                    ArrayList<TLRPC.Message> result = new ArrayList<>();
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                    ArrayList<Long> usersToLoad = new ArrayList<>();
                    ArrayList<Long> chatsToLoad = new ArrayList<>();

                    for (int b = 0, N2 = replyMessageOwners.size(); b < N2; b++) {
                        long did = replyMessageOwners.keyAt(b);
                        SparseArray<ArrayList<MessageObject>> owners = replyMessageOwners.valueAt(b);
                        ArrayList<Integer> ids = dialogReplyMessagesIds.get(did);
                        if (ids == null) {
                            continue;
                        }
                        SQLiteCursor cursor;
                        if (scheduled) {
                            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM scheduled_messages_v2 WHERE mid IN(%s) AND uid = %d", TextUtils.join(",", ids), dialogId));
                        } else {
                            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid, date, uid FROM messages_v2 WHERE mid IN(%s) AND uid = %d", TextUtils.join(",", ids), dialogId));
                        }
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, getUserConfig().clientUserId);
                                data.reuse();
                                message.id = cursor.intValue(1);
                                message.date = cursor.intValue(2);
                                message.dialog_id = dialogId;
                                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad, null);
                                result.add(message);

                                long channelId = message.peer_id != null ? message.peer_id.channel_id : 0;
                                ArrayList<Integer> mids = dialogReplyMessagesIds.get(channelId);
                                if (mids != null) {
                                    mids.remove((Integer) message.id);
                                    if (mids.isEmpty()) {
                                        dialogReplyMessagesIds.remove(channelId);
                                    }
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    if (!usersToLoad.isEmpty()) {
                        getMessagesStorage().getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        getMessagesStorage().getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    broadcastReplyMessages(result, replyMessageOwners, users, chats, dialogId, true);

                    if (!dialogReplyMessagesIds.isEmpty()) {
                        for (int a = 0, N = dialogReplyMessagesIds.size(); a < N; a++) {
                            long channelId = dialogReplyMessagesIds.keyAt(a);
                            if (scheduled) {
                                TLRPC.TL_messages_getScheduledMessages req = new TLRPC.TL_messages_getScheduledMessages();
                                req.peer = getMessagesController().getInputPeer(dialogId);
                                req.id = dialogReplyMessagesIds.valueAt(a);
                                getConnectionsManager().sendRequest(req, (response, error) -> {
                                    if (error == null) {
                                        TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                        for (int i = 0; i < messagesRes.messages.size(); i++) {
                                            TLRPC.Message message = messagesRes.messages.get(i);
                                            if (message.dialog_id == 0) {
                                                message.dialog_id = dialogId;
                                            }
                                        }
                                        MessageObject.fixMessagePeer(messagesRes.messages, channelId);
                                        ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                        broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialogId, false);
                                        getMessagesStorage().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                        saveReplyMessages(replyMessageOwners, messagesRes.messages, scheduled);
                                    }
                                    if (callback != null) {
                                        AndroidUtilities.runOnUIThread(callback);
                                    }
                                });
                            } else if (channelId != 0) {
                                TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                                req.channel = getMessagesController().getInputChannel(channelId);
                                req.id = dialogReplyMessagesIds.valueAt(a);
                                getConnectionsManager().sendRequest(req, (response, error) -> {
                                    if (error == null) {
                                        TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                        for (int i = 0; i < messagesRes.messages.size(); i++) {
                                            TLRPC.Message message = messagesRes.messages.get(i);
                                            if (message.dialog_id == 0) {
                                                message.dialog_id = dialogId;
                                            }
                                        }
                                        MessageObject.fixMessagePeer(messagesRes.messages, channelId);
                                        ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                        broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialogId, false);
                                        getMessagesStorage().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                        saveReplyMessages(replyMessageOwners, messagesRes.messages, scheduled);
                                    }
                                    if (callback != null) {
                                        AndroidUtilities.runOnUIThread(callback);
                                    }
                                });
                            } else {
                                TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                                req.id = dialogReplyMessagesIds.valueAt(a);
                                getConnectionsManager().sendRequest(req, (response, error) -> {
                                    if (error == null) {
                                        TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                        for (int i = 0; i < messagesRes.messages.size(); i++) {
                                            TLRPC.Message message = messagesRes.messages.get(i);
                                            if (message.dialog_id == 0) {
                                                message.dialog_id = dialogId;
                                            }
                                        }
                                        ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                        broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialogId, false);
                                        getMessagesStorage().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                        saveReplyMessages(replyMessageOwners, messagesRes.messages, scheduled);
                                    }
                                    if (callback != null) {
                                        AndroidUtilities.runOnUIThread(callback);
                                    }
                                });
                            }
                        }
                    } else {
                        if (callback != null) {
                            AndroidUtilities.runOnUIThread(callback);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }
    }

    private void saveReplyMessages(LongSparseArray<SparseArray<ArrayList<MessageObject>>> replyMessageOwners, ArrayList<TLRPC.Message> result, boolean scheduled) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                getMessagesStorage().getDatabase().beginTransaction();
                SQLitePreparedStatement state;
                SQLitePreparedStatement topicState = null;
                if (scheduled) {
                    state = getMessagesStorage().getDatabase().executeFast("UPDATE scheduled_messages_v2 SET replydata = ?, reply_to_message_id = ? WHERE mid = ? AND uid = ?");
                } else {
                    state = getMessagesStorage().getDatabase().executeFast("UPDATE messages_v2 SET replydata = ?, reply_to_message_id = ? WHERE mid = ? AND uid = ?");
                    topicState = getMessagesStorage().getDatabase().executeFast("UPDATE messages_topics SET replydata = ?, reply_to_message_id = ? WHERE mid = ? AND uid = ?");
                }
                for (int a = 0; a < result.size(); a++) {
                    TLRPC.Message message = result.get(a);
                    long dialogId = MessageObject.getDialogId(message);
                    SparseArray<ArrayList<MessageObject>> sparseArray = replyMessageOwners.get(dialogId);
                    if (sparseArray == null) {
                        continue;
                    }
                    ArrayList<MessageObject> messageObjects = sparseArray.get(message.id);
                    if (messageObjects != null) {
                        NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                        message.serializeToStream(data);
                        for (int b = 0; b < messageObjects.size(); b++) {
                            MessageObject messageObject = messageObjects.get(b);
                            for (int i = 0; i < 2; i++) {
                                SQLitePreparedStatement localState = i == 0 ? state : topicState;
                                if (localState == null) {
                                    continue;
                                }
                                localState.requery();
                                localState.bindByteBuffer(1, data);
                                localState.bindInteger(2, message.id);
                                localState.bindInteger(3, messageObject.getId());
                                localState.bindLong(4, messageObject.getDialogId());
                                localState.step();
                            }
                        }
                        data.reuse();
                    }
                }
                state.dispose();
                if (topicState != null) {
                    topicState.dispose();
                }
                getMessagesStorage().getDatabase().commitTransaction();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private void broadcastReplyMessages(ArrayList<TLRPC.Message> result, LongSparseArray<SparseArray<ArrayList<MessageObject>>> replyMessageOwners, ArrayList<TLRPC.User> users, ArrayList<TLRPC.Chat> chats, long dialog_id, boolean isCache) {
        LongSparseArray<TLRPC.User> usersDict = new LongSparseArray<>();
        for (int a = 0; a < users.size(); a++) {
            TLRPC.User user = users.get(a);
            usersDict.put(user.id, user);
        }
        LongSparseArray<TLRPC.Chat> chatsDict = new LongSparseArray<>();
        for (int a = 0; a < chats.size(); a++) {
            TLRPC.Chat chat = chats.get(a);
            chatsDict.put(chat.id, chat);
        }
        ArrayList<MessageObject> messageObjects = new ArrayList<>();
        for (int a = 0, N = result.size(); a < N; a++) {
            messageObjects.add(new MessageObject(currentAccount, result.get(a), usersDict, chatsDict, false, false));
        }
        AndroidUtilities.runOnUIThread(() -> {
            getMessagesController().putUsers(users, isCache);
            getMessagesController().putChats(chats, isCache);
            boolean changed = false;
            for (int a = 0, N = messageObjects.size(); a < N; a++) {
                MessageObject messageObject = messageObjects.get(a);
                long dialogId = messageObject.getDialogId();
                SparseArray<ArrayList<MessageObject>> sparseArray = replyMessageOwners.get(dialogId);
                if (sparseArray == null) {
                    continue;
                }
                ArrayList<MessageObject> arrayList = sparseArray.get(messageObject.getId());
                if (arrayList != null) {
                    for (int b = 0; b < arrayList.size(); b++) {
                        MessageObject m = arrayList.get(b);
                        m.replyMessageObject = messageObject;
                        if (m.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                            m.generatePinMessageText(null, null);
                        } else if (m.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                            m.generateGameMessageText(null);
                        } else if (m.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                            m.generatePaymentSentMessageText(null);
                        }
                    }
                    changed = true;
                }
            }
            if (changed) {
                getNotificationCenter().postNotificationName(NotificationCenter.replyMessagesDidLoad, dialog_id, messageObjects, replyMessageOwners);
            }
        });
    }

    public static void sortEntities(ArrayList<TLRPC.MessageEntity> entities) {
        Collections.sort(entities, entityComparator);
    }

    private static boolean checkInclusion(int index, List<TLRPC.MessageEntity> entities, boolean end) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if ((end ? entity.offset < index : entity.offset <= index) && entity.offset + entity.length > index) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkIntersection(int start, int end, List<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset > start && entity.offset + entity.length <= end) {
                return true;
            }
        }
        return false;
    }

    public CharSequence substring(CharSequence source, int start, int end) {
        if (source instanceof SpannableStringBuilder) {
            return source.subSequence(start, end);
        } else if (source instanceof SpannedString) {
            return source.subSequence(start, end);
        } else {
            return TextUtils.substring(source, start, end);
        }
    }

    private static CharacterStyle createNewSpan(CharacterStyle baseSpan, TextStyleSpan.TextStyleRun textStyleRun, TextStyleSpan.TextStyleRun newStyleRun, boolean allowIntersection) {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(textStyleRun);
        if (newStyleRun != null) {
            if (allowIntersection) {
                run.merge(newStyleRun);
            } else {
                run.replace(newStyleRun);
            }
        }
        if (baseSpan instanceof TextStyleSpan) {
            return new TextStyleSpan(run);
        } else if (baseSpan instanceof URLSpanReplacement) {
            URLSpanReplacement span = (URLSpanReplacement) baseSpan;
            return new URLSpanReplacement(span.getURL(), run);
        }
        return null;
    }

    public static void addStyleToText(TextStyleSpan span, int start, int end, Spannable editable, boolean allowIntersection) {
        try {
            CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    CharacterStyle oldSpan = spans[a];
                    TextStyleSpan.TextStyleRun textStyleRun;
                    TextStyleSpan.TextStyleRun newStyleRun = span != null ? span.getTextStyleRun() : new TextStyleSpan.TextStyleRun();
                    if (oldSpan instanceof TextStyleSpan) {
                        TextStyleSpan textStyleSpan = (TextStyleSpan) oldSpan;
                        textStyleRun = textStyleSpan.getTextStyleRun();
                    } else if (oldSpan instanceof URLSpanReplacement) {
                        URLSpanReplacement urlSpanReplacement = (URLSpanReplacement) oldSpan;
                        textStyleRun = urlSpanReplacement.getTextStyleRun();
                        if (textStyleRun == null) {
                            textStyleRun = new TextStyleSpan.TextStyleRun();
                        }
                    } else {
                        continue;
                    }
                    if (textStyleRun == null) {
                        continue;
                    }
                    int spanStart = editable.getSpanStart(oldSpan);
                    int spanEnd = editable.getSpanEnd(oldSpan);
                    editable.removeSpan(oldSpan);
                    if (spanStart > start && end > spanEnd) {
                        editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        if (span != null) {
                            editable.setSpan(new TextStyleSpan(new TextStyleSpan.TextStyleRun(newStyleRun)), spanEnd, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        end = spanStart;
                    } else {
                        int startTemp = start;
                        if (spanStart <= start) {
                            if (spanStart != start) {
                                editable.setSpan(createNewSpan(oldSpan, textStyleRun, null, allowIntersection), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            if (spanEnd > start) {
                                if (span != null) {
                                    editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), start, Math.min(spanEnd, end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                start = spanEnd;
                            }
                        }
                        if (spanEnd >= end) {
                            if (spanEnd != end) {
                                editable.setSpan(createNewSpan(oldSpan, textStyleRun, null, allowIntersection), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                            if (end > spanStart && spanEnd <= startTemp) {
                                if (span != null) {
                                    editable.setSpan(createNewSpan(oldSpan, textStyleRun, newStyleRun, allowIntersection), spanStart, Math.min(spanEnd, end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                                }
                                end = spanStart;
                            }
                        }
                    }
                }
            }
            if (span != null && start < end && start < editable.length()) {
                editable.setSpan(span, start, Math.min(editable.length(), end), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static void addTextStyleRuns(MessageObject msg, Spannable text) {
        addTextStyleRuns(msg, msg.messageText, text, -1);
    }

    public static void addTextStyleRuns(TLRPC.DraftMessage msg, Spannable text, int allowedFlags) {
        addTextStyleRuns(msg.entities, msg.message, text, allowedFlags);
    }

    public static void addTextStyleRuns(MessageObject msg, Spannable text, int allowedFlags) {
        addTextStyleRuns(msg, msg.messageText, text, allowedFlags);
    }

    public static void addTextStyleRuns(MessageObject message, CharSequence messageText, Spannable text) {
        addTextStyleRuns(message, messageText, text, -1);
    }

    public static void addTextStyleRuns(MessageObject message, CharSequence messageText, Spannable text, int allowedFlags) {
        addTextStyleRuns(MessageHelper.checkBlockedUserEntities(message), messageText, text, allowedFlags);
    }

    public static void addTextStyleRuns(ArrayList<TLRPC.MessageEntity> entities, CharSequence messageText, Spannable text, int allowedFlags) {
        for (TextStyleSpan prevSpan : text.getSpans(0, text.length(), TextStyleSpan.class))
            text.removeSpan(prevSpan);
        for (TextStyleSpan.TextStyleRun run : MediaDataController.getTextStyleRuns(entities, messageText, allowedFlags)) {
            MediaDataController.addStyleToText(new TextStyleSpan(run), run.start, run.end, text, true);
        }
    }

    public static void addAnimatedEmojiSpans(ArrayList<TLRPC.MessageEntity> entities, CharSequence messageText, Paint.FontMetricsInt fontMetricsInt) {
        if (!(messageText instanceof Spannable) || entities == null) {
            return;
        }
        Spannable spannable = (Spannable) messageText;
        AnimatedEmojiSpan[] emojiSpans = spannable.getSpans(0, spannable.length(), AnimatedEmojiSpan.class);
        for (int j = 0; j < emojiSpans.length; ++j) {
            AnimatedEmojiSpan span = emojiSpans[j];
            if (span != null) {
                spannable.removeSpan(span);
            }
        }
        for (int i = 0; i < entities.size(); ++i) {
            TLRPC.MessageEntity messageEntity = entities.get(i);
            if (messageEntity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                TLRPC.TL_messageEntityCustomEmoji entity = (TLRPC.TL_messageEntityCustomEmoji) messageEntity;

                int start = messageEntity.offset;
                int end = messageEntity.offset + messageEntity.length;
                if (start < end && end <= spannable.length()) {
                    AnimatedEmojiSpan span;
                    if (entity.document != null) {
                        span = new AnimatedEmojiSpan(entity.document, fontMetricsInt);
                    } else {
                        span = new AnimatedEmojiSpan(entity.document_id, fontMetricsInt);
                    }
                    spannable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
    }

    public static ArrayList<TextStyleSpan.TextStyleRun> getTextStyleRuns(ArrayList<TLRPC.MessageEntity> entities, CharSequence text, int allowedFlags) {
        ArrayList<TextStyleSpan.TextStyleRun> runs = new ArrayList<>();
        ArrayList<TLRPC.MessageEntity> entitiesCopy = new ArrayList<>(entities);

        Collections.sort(entitiesCopy, (o1, o2) -> {
            if (o1.offset > o2.offset) {
                return 1;
            } else if (o1.offset < o2.offset) {
                return -1;
            }
            return 0;
        });
        for (int a = 0, N = entitiesCopy.size(); a < N; a++) {
            TLRPC.MessageEntity entity = entitiesCopy.get(a);
            if (entity == null || entity.length <= 0 || entity.offset < 0 || entity.offset >= text.length()) {
                continue;
            } else if (entity.offset + entity.length > text.length()) {
                entity.length = text.length() - entity.offset;
            }

            if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                continue;
            }

            TextStyleSpan.TextStyleRun newRun = new TextStyleSpan.TextStyleRun();
            newRun.start = entity.offset;
            newRun.end = newRun.start + entity.length;
            TLRPC.MessageEntity urlEntity = null;
            if (entity instanceof TLRPC.TL_messageEntitySpoiler) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_SPOILER;
            } else if (entity instanceof TLRPC.TL_messageEntityStrike) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_STRIKE;
            } else if (entity instanceof TLRPC.TL_messageEntityUnderline) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_UNDERLINE;
            } else if (entity instanceof TLRPC.TL_messageEntityBlockquote) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_QUOTE;
            } else if (entity instanceof TLRPC.TL_messageEntityBold) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_BOLD;
            } else if (entity instanceof TLRPC.TL_messageEntityItalic) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_ITALIC;
            } else if (entity instanceof TLRPC.TL_messageEntityCode || entity instanceof TLRPC.TL_messageEntityPre) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_MONO;
            } else if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_MENTION;
                newRun.urlEntity = entity;
            } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName) {
                newRun.flags = TextStyleSpan.FLAG_STYLE_MENTION;
                newRun.urlEntity = entity;
            } else {
                newRun.flags = TextStyleSpan.FLAG_STYLE_URL;
                newRun.urlEntity = entity;
            }

            newRun.flags &= allowedFlags;

            for (int b = 0, N2 = runs.size(); b < N2; b++) {
                TextStyleSpan.TextStyleRun run = runs.get(b);

                if (newRun.start > run.start) {
                    if (newRun.start >= run.end) {
                        continue;
                    }

                    if (newRun.end < run.end) {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.merge(run);
                        b++;
                        N2++;
                        runs.add(b, r);

                        r = new TextStyleSpan.TextStyleRun(run);
                        r.start = newRun.end;
                        b++;
                        N2++;
                        runs.add(b, r);
                    } else {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.merge(run);
                        r.end = run.end;
                        b++;
                        N2++;
                        runs.add(b, r);
                    }

                    int temp = newRun.start;
                    newRun.start = run.end;
                    run.end = temp;
                } else {
                    if (run.start >= newRun.end) {
                        continue;
                    }
                    int temp = run.start;
                    if (newRun.end == run.end) {
                        run.merge(newRun);
                    } else if (newRun.end < run.end) {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(run);
                        r.merge(newRun);
                        r.end = newRun.end;
                        b++;
                        N2++;
                        runs.add(b, r);

                        run.start = newRun.end;
                    } else {
                        TextStyleSpan.TextStyleRun r = new TextStyleSpan.TextStyleRun(newRun);
                        r.start = run.end;
                        b++;
                        N2++;
                        runs.add(b, r);

                        run.merge(newRun);
                    }
                    newRun.end = temp;
                }
            }
            if (newRun.start < newRun.end) {
                runs.add(newRun);
            }
        }
        return runs;
    }

    public void addStyle(TextStyleSpan.TextStyleRun styleRun, int spanStart, int spanEnd, ArrayList<TLRPC.MessageEntity> entities) {
        int flags = styleRun.flags;
        if ((flags & TextStyleSpan.FLAG_STYLE_SPOILER) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntitySpoiler(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_BOLD) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityBold(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_ITALIC) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityItalic(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_MONO) != 0) {
            if (styleRun.urlEntity != null) {
                entities.add(setEntityStartEnd(styleRun.urlEntity, spanStart, spanEnd));
            } else {
                entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityCode(), spanStart, spanEnd));
            }
        }
        if ((flags & TextStyleSpan.FLAG_STYLE_STRIKE) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityStrike(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_UNDERLINE) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityUnderline(), spanStart, spanEnd));
        if ((flags & TextStyleSpan.FLAG_STYLE_QUOTE) != 0)
            entities.add(setEntityStartEnd(new TLRPC.TL_messageEntityBlockquote(), spanStart, spanEnd));
    }

    private TLRPC.MessageEntity setEntityStartEnd(TLRPC.MessageEntity entity, int spanStart, int spanEnd) {
        entity.offset = spanStart;
        entity.length = spanEnd - spanStart;
        return entity;
    }

    public ArrayList<TLRPC.MessageEntity> getEntities(CharSequence[] message, boolean allowStrike) {
        if (message == null || message[0] == null) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> entities = null;
        int index;
        int start = -1;
        int lastIndex = 0;
        boolean isPre = false;
        final String mono = "`";
        final String pre = "```";
        while (!(NekoConfig.newMarkdownParser || EditTextBoldCursor.disableMarkdown) && (index = TextUtils.indexOf(message[0], !isPre ? mono : pre, lastIndex)) != -1) {
            if (start == -1) {
                isPre = message[0].length() - index > 2 && message[0].charAt(index + 1) == '`' && message[0].charAt(index + 2) == '`';
                start = index;
                lastIndex = index + (isPre ? 3 : 1);
            } else {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int a = index + (isPre ? 3 : 1); a < message[0].length(); a++) {
                    if (message[0].charAt(a) == '`') {
                        index++;
                    } else {
                        break;
                    }
                }
                lastIndex = index + (isPre ? 3 : 1);
                if (isPre) {
                    int firstChar = start > 0 ? message[0].charAt(start - 1) : 0;
                    boolean replacedFirst = firstChar == ' ' || firstChar == '\n';
                    CharSequence startMessage = substring(message[0], 0, start - (replacedFirst ? 1 : 0));
                    CharSequence content = substring(message[0], start + 3, index);
                    firstChar = index + 3 < message[0].length() ? message[0].charAt(index + 3) : 0;
                    CharSequence endMessage = substring(message[0], index + 3 + (firstChar == ' ' || firstChar == '\n' ? 1 : 0), message[0].length());
                    if (startMessage.length() != 0) {
                        startMessage = AndroidUtilities.concat(startMessage, "\n");
                    } else {
                        replacedFirst = true;
                    }
                    if (endMessage.length() != 0) {
                        endMessage = AndroidUtilities.concat("\n", endMessage);
                    }
                    if (!TextUtils.isEmpty(content)) {
                        message[0] = AndroidUtilities.concat(startMessage, content, endMessage);
                        TLRPC.TL_messageEntityPre entity = new TLRPC.TL_messageEntityPre();
                        entity.offset = start + (replacedFirst ? 0 : 1);
                        entity.length = index - start - 3 + (replacedFirst ? 0 : 1);
                        entity.language = "";
                        entities.add(entity);
                        lastIndex -= 6;
                    }
                } else {
                    if (start + 1 != index) {
                        message[0] = AndroidUtilities.concat(substring(message[0], 0, start), substring(message[0], start + 1, index), substring(message[0], index + 1, message[0].length()));
                        TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
                        entity.offset = start;
                        entity.length = index - start - 1;
                        entities.add(entity);
                        lastIndex -= 2;
                    }
                }
                start = -1;
                isPre = false;
            }
        }
        if (start != -1 && isPre) {
            message[0] = AndroidUtilities.concat(substring(message[0], 0, start), substring(message[0], start + 2, message[0].length()));
            if (entities == null) {
                entities = new ArrayList<>();
            }
            TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
            entity.offset = start;
            entity.length = 1;
            entities.add(entity);
        }

        if (!EditTextBoldCursor.disableMarkdown && NekoConfig.newMarkdownParser) EntitiesHelper.parseMarkdown(message, allowStrike);

        if (message[0] instanceof Spanned) {
            Spanned spannable = (Spanned) message[0];
            TextStyleSpan[] spans = spannable.getSpans(0, message[0].length(), TextStyleSpan.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    TextStyleSpan span = spans[a];
                    int spanStart = spannable.getSpanStart(span);
                    int spanEnd = spannable.getSpanEnd(span);
                    if (checkInclusion(spanStart, entities, false) || checkInclusion(spanEnd, entities, true) || checkIntersection(spanStart, spanEnd, entities)) {
                        continue;
                    }
                    if (entities == null) {
                        entities = new ArrayList<>();
                    }
                    addStyle(span.getTextStyleRun(), spanStart, spanEnd, entities);
                }
            }

            URLSpanUserMention[] spansMentions = spannable.getSpans(0, message[0].length(), URLSpanUserMention.class);
            if (spansMentions != null && spansMentions.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < spansMentions.length; b++) {
                    TLRPC.TL_inputMessageEntityMentionName entity = new TLRPC.TL_inputMessageEntityMentionName();
                    entity.user_id = getMessagesController().getInputUser(Utilities.parseLong(spansMentions[b].getURL()));
                    if (entity.user_id != null) {
                        entity.offset = spannable.getSpanStart(spansMentions[b]);
                        entity.length = Math.min(spannable.getSpanEnd(spansMentions[b]), message[0].length()) - entity.offset;
                        if (message[0].charAt(entity.offset + entity.length - 1) == ' ') {
                            entity.length--;
                        }
                        entities.add(entity);
                    }
                }
            }

            URLSpanReplacement[] spansUrlReplacement = spannable.getSpans(0, message[0].length(), URLSpanReplacement.class);
            if (spansUrlReplacement != null && spansUrlReplacement.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < spansUrlReplacement.length; b++) {
                    TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
                    entity.offset = spannable.getSpanStart(spansUrlReplacement[b]);
                    entity.length = Math.min(spannable.getSpanEnd(spansUrlReplacement[b]), message[0].length()) - entity.offset;
                    entity.url = spansUrlReplacement[b].getURL();
                    entities.add(entity);
                    TextStyleSpan.TextStyleRun style = spansUrlReplacement[b].getTextStyleRun();
                    if (style != null) {
                        addStyle(style, entity.offset, entity.offset + entity.length, entities);
                    }
                }
            }

            AnimatedEmojiSpan[] animatedEmojiSpans = spannable.getSpans(0, message[0].length(), AnimatedEmojiSpan.class);
            if (animatedEmojiSpans != null && animatedEmojiSpans.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < animatedEmojiSpans.length; ++b) {
                    AnimatedEmojiSpan span = animatedEmojiSpans[b];
                    if (span != null) {
                        try {
                            TLRPC.TL_messageEntityCustomEmoji entity = new TLRPC.TL_messageEntityCustomEmoji();
                            entity.offset = spannable.getSpanStart(span);
                            entity.length = Math.min(spannable.getSpanEnd(span), message[0].length()) - entity.offset;
                            entity.document_id = span.getDocumentId();
                            entity.document = span.document;
                            entities.add(entity);
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                }
            }

            if (spannable instanceof Spannable) {
                AndroidUtilities.addLinks((Spannable) spannable, Linkify.WEB_URLS, false, false);
                URLSpan[] spansUrl = spannable.getSpans(0, message[0].length(), URLSpan.class);
                if (spansUrl != null && spansUrl.length > 0) {
                    if (entities == null) {
                        entities = new ArrayList<>();
                    }
                    for (int b = 0; b < spansUrl.length; b++) {
                        if (spansUrl[b] instanceof URLSpanReplacement || spansUrl[b] instanceof URLSpanUserMention) {
                            continue;
                        }
                        TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
                        entity.offset = spannable.getSpanStart(spansUrl[b]);
                        entity.length = Math.min(spannable.getSpanEnd(spansUrl[b]), message[0].length()) - entity.offset;
                        entity.url = spansUrl[b].getURL();
                        entities.add(entity);
                    }
                }
            }
        }

        CharSequence cs = message[0];
        if (entities == null) entities = new ArrayList<>();
        if (NekoConfig.newMarkdownParser || EditTextBoldCursor.disableMarkdown) return entities;
        cs = parsePattern(cs, BOLD_PATTERN, entities, obj -> new TLRPC.TL_messageEntityBold());
        cs = parsePattern(cs, ITALIC_PATTERN, entities, obj -> new TLRPC.TL_messageEntityItalic());
        cs = parsePattern(cs, SPOILER_PATTERN, entities, obj -> new TLRPC.TL_messageEntitySpoiler());
        if (allowStrike) {
            cs = parsePattern(cs, STRIKE_PATTERN, entities, obj -> new TLRPC.TL_messageEntityStrike());
        }
        message[0] = cs;

        return entities;
    }

    private CharSequence parsePattern(CharSequence cs, Pattern pattern, List<TLRPC.MessageEntity> entities, GenericProvider<Void, TLRPC.MessageEntity> entityProvider) {
        Matcher m = pattern.matcher(cs);
        int offset = 0;
        while (m.find()) {
            String gr = m.group(1);
            boolean allowEntity = true;
            if (cs instanceof Spannable) {
                // check if it is inside a link: do not convert __ ** to styles inside links
                URLSpan[] spansUrl = ((Spannable) cs).getSpans(m.start() - offset, m.end() - offset, URLSpan.class);
                if (spansUrl != null && spansUrl.length > 0) {
                    allowEntity = false;
                }
            }

            if (allowEntity) {
                cs = cs.subSequence(0, m.start() - offset) + gr + cs.subSequence(m.end() - offset, cs.length());

                TLRPC.MessageEntity entity = entityProvider.provide(null);
                entity.offset = m.start() - offset;
                entity.length = gr.length();
                entities.add(entity);
            }

            offset += m.end() - m.start() - gr.length();
        }
        return cs;
    }

    //---------------- MESSAGES END ----------------

    private LongSparseArray<Integer> draftsFolderIds = new LongSparseArray<>();
    private LongSparseArray<SparseArray<TLRPC.DraftMessage>> drafts = new LongSparseArray<>();
    private LongSparseArray<SparseArray<TLRPC.Message>> draftMessages = new LongSparseArray<>();
    private boolean inTransaction;
    private SharedPreferences draftPreferences;
    private boolean loadingDrafts;

    public void loadDraftsIfNeed() {
        if (getUserConfig().draftsLoaded || loadingDrafts) {
            return;
        }
        loadingDrafts = true;
        getConnectionsManager().sendRequest(new TLRPC.TL_messages_getAllDrafts(), (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> loadingDrafts = false);
            } else {
                getMessagesController().processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> {
                    loadingDrafts = false;
                    UserConfig userConfig = getUserConfig();
                    userConfig.draftsLoaded = true;
                    userConfig.saveConfig(false);
                });
            }
        });
    }

    public int getDraftFolderId(long dialogId) {
        return draftsFolderIds.get(dialogId, 0);
    }

    public void setDraftFolderId(long dialogId, int folderId) {
        draftsFolderIds.put(dialogId, folderId);
    }

    public void clearDraftsFolderIds() {
        draftsFolderIds.clear();
    }

    public LongSparseArray<SparseArray<TLRPC.DraftMessage>> getDrafts() {
        return drafts;
    }

    public TLRPC.DraftMessage getDraft(long dialogId, int threadId) {
        SparseArray<TLRPC.DraftMessage> threads = drafts.get(dialogId);
        if (threads == null) {
            return null;
        }
        return threads.get(threadId);
    }

    public Pair<Integer, TLRPC.DraftMessage> getOneThreadDraft(long dialogId) {
        SparseArray<TLRPC.DraftMessage> threads = drafts.get(dialogId);
        if (threads == null || threads.size() <= 0) {
            return null;
        }
        for (int i = 0; i < threads.size(); ++i) {
            if (threads.keyAt(i) != 0) {
                return new Pair(threads.keyAt(i), threads.valueAt(i));
            }
        }
        return null;
    }

    public TLRPC.Message getDraftMessage(long dialogId, int threadId) {
        SparseArray<TLRPC.Message> threads = draftMessages.get(dialogId);
        if (threads == null) {
            return null;
        }
        return threads.get(threadId);
    }

    public void saveDraft(long dialogId, int threadId, CharSequence message, ArrayList<TLRPC.MessageEntity> entities, TLRPC.Message replyToMessage, boolean noWebpage) {
        saveDraft(dialogId, threadId, message, entities, replyToMessage, noWebpage, false);
    }

    public void saveDraft(long dialogId, int threadId, CharSequence message, ArrayList<TLRPC.MessageEntity> entities, TLRPC.Message replyToMessage, boolean noWebpage, boolean clean) {
        TLRPC.DraftMessage draftMessage;
        if (getMessagesController().isForum(dialogId) && threadId == 0) {
            replyToMessage = null;
        }
        if (!TextUtils.isEmpty(message) || replyToMessage != null) {
            draftMessage = new TLRPC.TL_draftMessage();
        } else {
            draftMessage = new TLRPC.TL_draftMessageEmpty();
        }
        draftMessage.date = (int) (System.currentTimeMillis() / 1000);
        draftMessage.message = message == null ? "" : message.toString();
        draftMessage.no_webpage = noWebpage;
        if (replyToMessage != null) {
            draftMessage.reply_to_msg_id = replyToMessage.id;
            draftMessage.flags |= 1;
        }
        if (entities != null && !entities.isEmpty()) {
            draftMessage.entities = entities;
            draftMessage.flags |= 8;
        }

        SparseArray<TLRPC.DraftMessage> threads = drafts.get(dialogId);
        TLRPC.DraftMessage currentDraft = threads == null ? null : threads.get(threadId);
        if (!clean) {
            if (currentDraft != null && currentDraft.message.equals(draftMessage.message) && currentDraft.reply_to_msg_id == draftMessage.reply_to_msg_id && currentDraft.no_webpage == draftMessage.no_webpage ||
                    currentDraft == null && TextUtils.isEmpty(draftMessage.message) && draftMessage.reply_to_msg_id == 0) {
                return;
            }
        }

        saveDraft(dialogId, threadId, draftMessage, replyToMessage, false);

        if (threadId == 0 || ChatObject.isForum(currentAccount, dialogId)) {
            if (!DialogObject.isEncryptedDialog(dialogId)) {
                TLRPC.TL_messages_saveDraft req = new TLRPC.TL_messages_saveDraft();
                req.peer = getMessagesController().getInputPeer(dialogId);
                if (req.peer == null) {
                    return;
                }
                if (threadId != 0) {
                    req.top_msg_id = threadId;
                }
                req.message = draftMessage.message;
                req.no_webpage = draftMessage.no_webpage;
                req.reply_to_msg_id = draftMessage.reply_to_msg_id;
                req.entities = draftMessage.entities;
                req.flags = draftMessage.flags;
                getConnectionsManager().sendRequest(req, (response, error) -> {

                });
            }
            getMessagesController().sortDialogs(null);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        }
    }

    public void saveDraft(long dialogId, int threadId, TLRPC.DraftMessage draft, TLRPC.Message replyToMessage, boolean fromServer) {
        if (getMessagesController().isForum(dialogId) && threadId == 0 && TextUtils.isEmpty(draft.message)) {
            draft.reply_to_msg_id = 0;
        }
        SharedPreferences.Editor editor = draftPreferences.edit();
        MessagesController messagesController = getMessagesController();
        if (draft == null || draft instanceof TLRPC.TL_draftMessageEmpty) {
            {
                SparseArray<TLRPC.DraftMessage> threads = drafts.get(dialogId);
                if (threads != null) {
                    threads.remove(threadId);
                    if (threads.size() == 0) {
                        drafts.remove(dialogId);
                    }
                }
            }
            {
                SparseArray<TLRPC.Message> threads = draftMessages.get(dialogId);
                if (threads != null) {
                    threads.remove(threadId);
                    if (threads.size() == 0) {
                        draftMessages.remove(dialogId);
                    }
                }
            }
            if (threadId == 0) {
                draftPreferences.edit().remove("" + dialogId).remove("r_" + dialogId).commit();
            } else {
                draftPreferences.edit().remove("t_" + dialogId + "_" + threadId).remove("rt_" + dialogId + "_" + threadId).commit();
            }
            messagesController.removeDraftDialogIfNeed(dialogId);
        } else {
            SparseArray<TLRPC.DraftMessage> threads = drafts.get(dialogId);
            if (threads == null) {
                threads = new SparseArray<>();
                drafts.put(dialogId, threads);
            }
            threads.put(threadId, draft);
            if (threadId == 0) {
                messagesController.putDraftDialogIfNeed(dialogId, draft);
            }
            try {
                SerializedData serializedData = new SerializedData(draft.getObjectSize());
                draft.serializeToStream(serializedData);
                editor.putString(threadId == 0 ? ("" + dialogId) : ("t_" + dialogId + "_" + threadId), Utilities.bytesToHex(serializedData.toByteArray()));
                serializedData.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        SparseArray<TLRPC.Message> threads = draftMessages.get(dialogId);
        if (replyToMessage == null) {
            if (threads != null) {
                threads.remove(threadId);
                if (threads.size() == 0) {
                    draftMessages.remove(dialogId);
                }
            }
            if (threadId == 0) {
                editor.remove("r_" + dialogId);
            } else {
                editor.remove("rt_" + dialogId + "_" + threadId);
            }
        } else {
            if (threads == null) {
                threads = new SparseArray<>();
                draftMessages.put(dialogId, threads);
            }
            threads.put(threadId, replyToMessage);

            SerializedData serializedData = new SerializedData(replyToMessage.getObjectSize());
            replyToMessage.serializeToStream(serializedData);
            editor.putString(threadId == 0 ? ("r_" + dialogId) : ("rt_" + dialogId + "_" + threadId), Utilities.bytesToHex(serializedData.toByteArray()));
            serializedData.cleanup();
        }
        editor.commit();
        if (fromServer && (threadId == 0 || getMessagesController().isForum(dialogId))) {
            if (draft != null && draft.reply_to_msg_id != 0 && replyToMessage == null) {
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                if (DialogObject.isUserDialog(dialogId)) {
                    user = getMessagesController().getUser(dialogId);
                } else {
                    chat = getMessagesController().getChat(-dialogId);
                }
                if (user != null || chat != null) {
                    long channelId = ChatObject.isChannel(chat) ? chat.id : 0;
                    int messageId = draft.reply_to_msg_id;

                    getMessagesStorage().getStorageQueue().postRunnable(() -> {
                        try {
                            TLRPC.Message message = null;
                            SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data FROM messages_v2 WHERE mid = %d and uid = %d", messageId, dialogId));
                            if (cursor.next()) {
                                NativeByteBuffer data = cursor.byteBufferValue(0);
                                if (data != null) {
                                    message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                    message.readAttachPath(data, getUserConfig().clientUserId);
                                    data.reuse();
                                }
                            }
                            cursor.dispose();
                            if (message == null) {
                                if (channelId != 0) {
                                    TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                                    req.channel = getMessagesController().getInputChannel(channelId);
                                    req.id.add(messageId);
                                    getConnectionsManager().sendRequest(req, (response, error) -> {
                                        if (error == null) {
                                            TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                            if (!messagesRes.messages.isEmpty()) {
                                                saveDraftReplyMessage(dialogId, threadId, messagesRes.messages.get(0));
                                            }
                                        }
                                    });
                                } else {
                                    TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                                    req.id.add(messageId);
                                    getConnectionsManager().sendRequest(req, (response, error) -> {
                                        if (error == null) {
                                            TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                            if (!messagesRes.messages.isEmpty()) {
                                                saveDraftReplyMessage(dialogId, threadId, messagesRes.messages.get(0));
                                            }
                                        }
                                    });
                                }
                            } else {
                                saveDraftReplyMessage(dialogId, threadId, message);
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    });
                }
            }
            getNotificationCenter().postNotificationName(NotificationCenter.newDraftReceived, dialogId);
        }
    }

    private void saveDraftReplyMessage(long dialogId, int threadId, TLRPC.Message message) {
        if (message == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            SparseArray<TLRPC.DraftMessage> threads = drafts.get(dialogId);
            TLRPC.DraftMessage draftMessage = threads != null ? threads.get(threadId) : null;
            if (draftMessage != null && draftMessage.reply_to_msg_id == message.id) {
                SparseArray<TLRPC.Message> threads2 = draftMessages.get(dialogId);
                if (threads2 == null) {
                    threads2 = new SparseArray<>();
                    draftMessages.put(dialogId, threads2);
                }
                threads2.put(threadId, message);
                SerializedData serializedData = new SerializedData(message.getObjectSize());
                message.serializeToStream(serializedData);
                draftPreferences.edit().putString(threadId == 0 ? ("r_" + dialogId) : ("rt_" + dialogId + "_" + threadId), Utilities.bytesToHex(serializedData.toByteArray())).commit();
                getNotificationCenter().postNotificationName(NotificationCenter.newDraftReceived, dialogId);
                serializedData.cleanup();
            }
        });
    }

    public void clearAllDrafts(boolean notify) {
        drafts.clear();
        draftMessages.clear();
        draftsFolderIds.clear();
        draftPreferences.edit().clear().commit();
        if (notify) {
            getMessagesController().sortDialogs(null);
            getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
        }
    }

    public void cleanDraft(long dialogId, int threadId, boolean replyOnly) {
        SparseArray<TLRPC.DraftMessage> threads2 = drafts.get(dialogId);
        TLRPC.DraftMessage draftMessage = threads2 != null ? threads2.get(threadId) : null;
        if (draftMessage == null) {
            return;
        }
        if (!replyOnly) {
            {
                SparseArray<TLRPC.DraftMessage> threads = drafts.get(dialogId);
                if (threads != null) {
                    threads.remove(threadId);
                    if (threads.size() == 0) {
                        drafts.remove(dialogId);
                    }
                }
            }
            {
                SparseArray<TLRPC.Message> threads = draftMessages.get(dialogId);
                if (threads != null) {
                    threads.remove(threadId);
                    if (threads.size() == 0) {
                        draftMessages.remove(dialogId);
                    }
                }
            }
            if (threadId == 0) {
                draftPreferences.edit().remove("" + dialogId).remove("r_" + dialogId).commit();
                getMessagesController().sortDialogs(null);
                getNotificationCenter().postNotificationName(NotificationCenter.dialogsNeedReload);
            } else {
                draftPreferences.edit().remove("t_" + dialogId + "_" + threadId).remove("rt_" + dialogId + "_" + threadId).commit();
            }
        } else if (draftMessage.reply_to_msg_id != 0) {
            draftMessage.reply_to_msg_id = 0;
            draftMessage.flags &= ~1;
            saveDraft(dialogId, threadId, draftMessage.message, draftMessage.entities, null, draftMessage.no_webpage, true);
        }
    }

    public void beginTransaction() {
        inTransaction = true;
    }

    public void endTransaction() {
        inTransaction = false;
    }

    //---------------- DRAFT END ----------------

    private HashMap<String, TLRPC.BotInfo> botInfos = new HashMap<>();
    private LongSparseArray<TLRPC.Message> botKeyboards = new LongSparseArray<>();
    private SparseLongArray botKeyboardsByMids = new SparseLongArray();

    public void clearBotKeyboard(long dialogId, ArrayList<Integer> messages) {
        AndroidUtilities.runOnUIThread(() -> {
            if (messages != null) {
                for (int a = 0; a < messages.size(); a++) {
                    long did1 = botKeyboardsByMids.get(messages.get(a));
                    if (did1 != 0) {
                        botKeyboards.remove(did1);
                        botKeyboardsByMids.delete(messages.get(a));
                        getNotificationCenter().postNotificationName(NotificationCenter.botKeyboardDidLoad, null, did1);
                    }
                }
            } else {
                botKeyboards.remove(dialogId);
                getNotificationCenter().postNotificationName(NotificationCenter.botKeyboardDidLoad, null, dialogId);
            }
        });
    }

    public void loadBotKeyboard(long dialogId) {
        TLRPC.Message keyboard = botKeyboards.get(dialogId);
        if (keyboard != null) {
            getNotificationCenter().postNotificationName(NotificationCenter.botKeyboardDidLoad, keyboard, dialogId);
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                TLRPC.Message botKeyboard = null;
                SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT info FROM bot_keyboard WHERE uid = %d", dialogId));
                if (cursor.next()) {
                    NativeByteBuffer data;

                    if (!cursor.isNull(0)) {
                        data = cursor.byteBufferValue(0);
                        if (data != null) {
                            botKeyboard = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            data.reuse();
                        }
                    }
                }
                cursor.dispose();

                if (botKeyboard != null) {
                    TLRPC.Message botKeyboardFinal = botKeyboard;
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.botKeyboardDidLoad, botKeyboardFinal, dialogId));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    private TLRPC.BotInfo loadBotInfoInternal(long uid, long dialogId) throws SQLiteException {
        TLRPC.BotInfo botInfo = null;
        SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT info FROM bot_info_v2 WHERE uid = %d AND dialogId = %d", uid, dialogId));
        if (cursor.next()) {
            NativeByteBuffer data;

            if (!cursor.isNull(0)) {
                data = cursor.byteBufferValue(0);
                if (data != null) {
                    botInfo = TLRPC.BotInfo.TLdeserialize(data, data.readInt32(false), false);
                    data.reuse();
                }
            }
        }
        cursor.dispose();
        return botInfo;
    }

    public void loadBotInfo(long uid, long dialogId, boolean cache, int classGuid) {
        if (cache) {
            TLRPC.BotInfo botInfo = botInfos.get(uid + "_" + dialogId);
            if (botInfo != null) {
                getNotificationCenter().postNotificationName(NotificationCenter.botInfoDidLoad, botInfo, classGuid);
                return;
            }
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                TLRPC.BotInfo botInfo = loadBotInfoInternal(uid, dialogId);
                if (botInfo != null) {
                    AndroidUtilities.runOnUIThread(() -> getNotificationCenter().postNotificationName(NotificationCenter.botInfoDidLoad, botInfo, classGuid));
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void putBotKeyboard(long dialogId, TLRPC.Message message) {
        if (message == null) {
            return;
        }
        try {
            int mid = 0;
            SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US, "SELECT mid FROM bot_keyboard WHERE uid = %d", dialogId));
            if (cursor.next()) {
                mid = cursor.intValue(0);
            }
            cursor.dispose();
            if (mid >= message.id) {
                return;
            }

            SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO bot_keyboard VALUES(?, ?, ?)");
            state.requery();
            NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(data);
            state.bindLong(1, dialogId);
            state.bindInteger(2, message.id);
            state.bindByteBuffer(3, data);
            state.step();
            data.reuse();
            state.dispose();

            AndroidUtilities.runOnUIThread(() -> {
                TLRPC.Message old = botKeyboards.get(dialogId);
                botKeyboards.put(dialogId, message);
                long channelId = MessageObject.getChannelId(message);
                if (channelId == 0) {
                    if (old != null) {
                        botKeyboardsByMids.delete(old.id);
                    }
                    botKeyboardsByMids.put(message.id, dialogId);
                }
                getNotificationCenter().postNotificationName(NotificationCenter.botKeyboardDidLoad, message, dialogId);
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void putBotInfo(long dialogId, TLRPC.BotInfo botInfo) {
        if (botInfo == null) {
            return;
        }
        botInfos.put(botInfo.user_id + "_" + dialogId, botInfo);
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO bot_info_v2 VALUES(?, ?, ?)");
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(botInfo.getObjectSize());
                botInfo.serializeToStream(data);
                state.bindLong(1, botInfo.user_id);
                state.bindLong(2, dialogId);
                state.bindByteBuffer(3, data);
                state.step();
                data.reuse();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void updateBotInfo(long dialogId, TLRPC.TL_updateBotCommands update) {
        TLRPC.BotInfo botInfo = botInfos.get(update.bot_id + "_" + dialogId);
        if (botInfo != null) {
            botInfo.commands = update.commands;
            getNotificationCenter().postNotificationName(NotificationCenter.botInfoDidLoad, botInfo, 0);
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                TLRPC.BotInfo info = loadBotInfoInternal(update.bot_id, dialogId);
                if (info != null) {
                    info.commands = update.commands;
                }
                SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("REPLACE INTO bot_info_v2 VALUES(?, ?, ?)");
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(info.getObjectSize());
                info.serializeToStream(data);
                state.bindLong(1, info.user_id);
                state.bindLong(2, dialogId);
                state.bindByteBuffer(3, data);
                state.step();
                data.reuse();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public HashMap<String, TLRPC.TL_availableReaction> getReactionsMap() {
        return reactionsMap;
    }

    public String getDoubleTapReaction() {
        if (doubleTapReaction != null) {
            return doubleTapReaction;
        }
        if (!getReactionsList().isEmpty()) {
            String savedReaction = MessagesController.getEmojiSettings(currentAccount).getString("reaction_on_double_tap", null);
            if (savedReaction != null && (getReactionsMap().get(savedReaction) != null || savedReaction.startsWith("animated_"))) {
                doubleTapReaction = savedReaction;
                return doubleTapReaction;
            }
            return getReactionsList().get(0).reaction;
        }
        return null;
    }

    public void setDoubleTapReaction(String reaction) {
        MessagesController.getEmojiSettings(currentAccount).edit().putString("reaction_on_double_tap", reaction).apply();
        doubleTapReaction = reaction;
    }

    public List<TLRPC.TL_availableReaction> getEnabledReactionsList() {
        return enabledReactionsList;
    }

    public void uploadRingtone(String filePath) {
        if (ringtoneUploaderHashMap.containsKey(filePath)) {
            return;
        }
        ringtoneUploaderHashMap.put(filePath, new RingtoneUploader(filePath, currentAccount));
        ringtoneDataStore.addUploadingTone(filePath);
    }

    public void onRingtoneUploaded(String filePath, TLRPC.Document document, boolean error) {
        ringtoneUploaderHashMap.remove(filePath);
        ringtoneDataStore.onRingtoneUploaded(filePath, document, error);
    }

    public void checkRingtones() {
        ringtoneDataStore.loadUserRingtones();
    }

    public boolean saveToRingtones(TLRPC.Document document) {
        if (document == null) {
            return false;
        }
        if (ringtoneDataStore.contains(document.id)) {
            return true;
        }
        if (document.size > MessagesController.getInstance(currentAccount).ringtoneSizeMax) {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR_SUBTITLE, LocaleController.formatString("TooLargeError", R.string.TooLargeError), LocaleController.formatString("ErrorRingtoneSizeTooBig", R.string.ErrorRingtoneSizeTooBig, (MessagesController.getInstance(UserConfig.selectedAccount).ringtoneSizeMax / 1024)));
            return false;
        }
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeAudio) {
                if (attribute.duration > MessagesController.getInstance(currentAccount).ringtoneDurationMax) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR_SUBTITLE, LocaleController.formatString("TooLongError", R.string.TooLongError), LocaleController.formatString("ErrorRingtoneDurationTooLong", R.string.ErrorRingtoneDurationTooLong, MessagesController.getInstance(UserConfig.selectedAccount).ringtoneDurationMax));
                    return false;
                }
            }
        }
        TLRPC.TL_account_saveRingtone saveRingtone = new TLRPC.TL_account_saveRingtone();
        saveRingtone.id = new TLRPC.TL_inputDocument();
        saveRingtone.id.id = document.id;
        saveRingtone.id.file_reference = document.file_reference;
        saveRingtone.id.access_hash = document.access_hash;
        ConnectionsManager.getInstance(currentAccount).sendRequest(saveRingtone, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response != null) {
                if (response instanceof TLRPC.TL_account_savedRingtoneConverted) {
                    ringtoneDataStore.addTone(((TLRPC.TL_account_savedRingtoneConverted) response).document);
                } else {
                    ringtoneDataStore.addTone(document);
                }
            }
        }));
        return true;
    }

    public void preloadPremiumPreviewStickers() {
        if (previewStickersLoading || !premiumPreviewStickers.isEmpty()) {
            for (int i = 0; i < Math.min(premiumPreviewStickers.size(), 3); i++) {
                TLRPC.Document document = premiumPreviewStickers.get(i == 2 ? premiumPreviewStickers.size() - 1 : i);
                if (MessageObject.isPremiumSticker(document)) {
                    ImageReceiver imageReceiver = new ImageReceiver();
                    imageReceiver.setImage(ImageLocation.getForDocument(document), null, null, "webp", null, 1);
                    ImageLoader.getInstance().loadImageForImageReceiver(imageReceiver);

                    imageReceiver = new ImageReceiver();
                    imageReceiver.setImage(ImageLocation.getForDocument(MessageObject.getPremiumStickerAnimation(document), document), null, null, null, "tgs", null, 1);
                    ImageLoader.getInstance().loadImageForImageReceiver(imageReceiver);
                }
            }
            return;
        }
        final TLRPC.TL_messages_getStickers req2 = new TLRPC.TL_messages_getStickers();
        req2.emoticon = Emoji.fixEmoji("⭐") + Emoji.fixEmoji("⭐");
        req2.hash = 0;
        previewStickersLoading = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                return;
            }
            previewStickersLoading = false;
            TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
            premiumPreviewStickers.clear();
            premiumPreviewStickers.addAll(res.stickers);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.premiumStickersPreviewLoaded);
        }));
    }

    public void chekAllMedia(boolean force) {
        if (force) {
            reactionsUpdateDate = 0;
            loadFeaturedDate[0] = 0;
            loadFeaturedDate[1] = 0;
        }
        loadRecents(MediaDataController.TYPE_FAVE, false, true, false);
        loadRecents(MediaDataController.TYPE_GREETINGS, false, true, false);
        loadRecents(MediaDataController.TYPE_PREMIUM_STICKERS, false, false, true);
        checkFeaturedStickers();
        checkFeaturedEmoji();
        checkReactions();
        checkMenuBots();
        checkPremiumPromo();
        checkPremiumGiftStickers();
        checkGenericAnimations();
    }

    public void moveStickerSetToTop(long setId, boolean emojis, boolean masks) {
        ArrayList<TLRPC.TL_messages_stickerSet> arrayList = null;
        int type = 0;
        if (emojis) {
            type = TYPE_EMOJIPACKS;
        } else if (masks) {
            type = TYPE_MASK;
        } else {
        }
        arrayList = getStickerSets(type);
        if (arrayList != null) {
            for (int i = 0; i < arrayList.size(); i++) {
                if (arrayList.get(i).set.id == setId) {
                    TLRPC.TL_messages_stickerSet set = arrayList.get(i);
                    arrayList.remove(i);
                    arrayList.add(0, set);
                    getNotificationCenter().postNotificationName(NotificationCenter.stickersDidLoad, type, false);
                    break;
                }
            }
        }
    }

    //---------------- BOT END ----------------

    //---------------- EMOJI START ----------------

    public static class KeywordResult {
        public KeywordResult() {
        }

        public KeywordResult(String emoji, String keyword) {
            this.emoji = emoji;
            this.keyword = keyword;
        }

        public String emoji;
        public String keyword;
    }


    public interface KeywordResultCallback {
        void run(ArrayList<KeywordResult> param, String alias);
    }

    private HashMap<String, Boolean> currentFetchingEmoji = new HashMap<>();

    public void fetchNewEmojiKeywords(String[] langCodes) {
        if (langCodes == null) {
            return;
        }
        for (int a = 0; a < langCodes.length; a++) {
            String langCode = langCodes[a];
            if (TextUtils.isEmpty(langCode)) {
                return;
            }
            if (currentFetchingEmoji.get(langCode) != null) {
                return;
            }
            currentFetchingEmoji.put(langCode, true);
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                int version = -1;
                String alias = null;
                long date = 0;
                try {
                    SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT alias, version, date FROM emoji_keywords_info_v2 WHERE lang = ?", langCode);
                    if (cursor.next()) {
                        alias = cursor.stringValue(0);
                        version = cursor.intValue(1);
                        date = cursor.longValue(2);
                    }
                    cursor.dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (!BuildVars.DEBUG_VERSION && Math.abs(System.currentTimeMillis() - date) < 60 * 60 * 1000) {
                    AndroidUtilities.runOnUIThread(() -> currentFetchingEmoji.remove(langCode));
                    return;
                }
                TLObject request;
                if (version == -1) {
                    TLRPC.TL_messages_getEmojiKeywords req = new TLRPC.TL_messages_getEmojiKeywords();
                    req.lang_code = langCode;
                    request = req;
                } else {
                    TLRPC.TL_messages_getEmojiKeywordsDifference req = new TLRPC.TL_messages_getEmojiKeywordsDifference();
                    req.lang_code = langCode;
                    req.from_version = version;
                    request = req;
                }
                String aliasFinal = alias;
                int versionFinal = version;
                getConnectionsManager().sendRequest(request, (response, error) -> {
                    if (response != null) {
                        TLRPC.TL_emojiKeywordsDifference res = (TLRPC.TL_emojiKeywordsDifference) response;
                        if (versionFinal != -1 && !res.lang_code.equals(aliasFinal)) {
                            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                                try {
                                    SQLitePreparedStatement deleteState = getMessagesStorage().getDatabase().executeFast("DELETE FROM emoji_keywords_info_v2 WHERE lang = ?");
                                    deleteState.bindString(1, langCode);
                                    deleteState.step();
                                    deleteState.dispose();

                                    AndroidUtilities.runOnUIThread(() -> {
                                        currentFetchingEmoji.remove(langCode);
                                        fetchNewEmojiKeywords(new String[]{langCode});
                                    });
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            });
                        } else {
                            putEmojiKeywords(langCode, res);
                        }
                    } else {
                        AndroidUtilities.runOnUIThread(() -> currentFetchingEmoji.remove(langCode));
                    }
                });
            });
        }
    }

    private void putEmojiKeywords(String lang, TLRPC.TL_emojiKeywordsDifference res) {
        if (res == null) {
            return;
        }
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                if (!res.keywords.isEmpty()) {
                    SQLitePreparedStatement insertState = getMessagesStorage().getDatabase().executeFast("REPLACE INTO emoji_keywords_v2 VALUES(?, ?, ?)");
                    SQLitePreparedStatement deleteState = getMessagesStorage().getDatabase().executeFast("DELETE FROM emoji_keywords_v2 WHERE lang = ? AND keyword = ? AND emoji = ?");
                    getMessagesStorage().getDatabase().beginTransaction();
                    for (int a = 0, N = res.keywords.size(); a < N; a++) {
                        TLRPC.EmojiKeyword keyword = res.keywords.get(a);
                        if (keyword instanceof TLRPC.TL_emojiKeyword) {
                            TLRPC.TL_emojiKeyword emojiKeyword = (TLRPC.TL_emojiKeyword) keyword;
                            String key = emojiKeyword.keyword.toLowerCase();
                            for (int b = 0, N2 = emojiKeyword.emoticons.size(); b < N2; b++) {
                                insertState.requery();
                                insertState.bindString(1, res.lang_code);
                                insertState.bindString(2, key);
                                insertState.bindString(3, emojiKeyword.emoticons.get(b));
                                insertState.step();
                            }
                        } else if (keyword instanceof TLRPC.TL_emojiKeywordDeleted) {
                            TLRPC.TL_emojiKeywordDeleted keywordDeleted = (TLRPC.TL_emojiKeywordDeleted) keyword;
                            String key = keywordDeleted.keyword.toLowerCase();
                            for (int b = 0, N2 = keywordDeleted.emoticons.size(); b < N2; b++) {
                                deleteState.requery();
                                deleteState.bindString(1, res.lang_code);
                                deleteState.bindString(2, key);
                                deleteState.bindString(3, keywordDeleted.emoticons.get(b));
                                deleteState.step();
                            }
                        }
                    }
                    getMessagesStorage().getDatabase().commitTransaction();
                    insertState.dispose();
                    deleteState.dispose();
                }

                SQLitePreparedStatement infoState = getMessagesStorage().getDatabase().executeFast("REPLACE INTO emoji_keywords_info_v2 VALUES(?, ?, ?, ?)");
                infoState.bindString(1, lang);
                infoState.bindString(2, res.lang_code);
                infoState.bindInteger(3, res.version);
                infoState.bindLong(4, System.currentTimeMillis());
                infoState.step();
                infoState.dispose();

                AndroidUtilities.runOnUIThread(() -> {
                    currentFetchingEmoji.remove(lang);
                    getNotificationCenter().postNotificationName(NotificationCenter.newEmojiSuggestionsAvailable, lang);
                });
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public void getAnimatedEmojiByKeywords(String query, Utilities.Callback<ArrayList<Long>> onResult) {
        if (query == null) {
            if (onResult != null) {
                onResult.run(new ArrayList<>());
            }
            return;
        }
        final ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = getStickerSets(TYPE_EMOJIPACKS);
        final ArrayList<TLRPC.StickerSetCovered> featuredStickerSets = getFeaturedEmojiSets();
        Utilities.searchQueue.postRunnable(() -> {
            ArrayList<Long> fullMatch = new ArrayList<>();
            ArrayList<Long> halfMatch = new ArrayList<>();
            String queryLowercased = query.toLowerCase();
            for (int i = 0; i < stickerSets.size(); ++i) {
                if (stickerSets.get(i).keywords != null) {
                    ArrayList<TLRPC.TL_stickerKeyword> keywords = stickerSets.get(i).keywords;
                    for (int j = 0; j < keywords.size(); ++j) {
                        for (int k = 0; k < keywords.get(j).keyword.size(); ++k) {
                            String keyword = keywords.get(j).keyword.get(k);
                            if (queryLowercased.equals(keyword)) {
                                fullMatch.add(keywords.get(j).document_id);
                            } else if (queryLowercased.contains(keyword) || keyword.contains(queryLowercased)) {
                                halfMatch.add(keywords.get(j).document_id);
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < featuredStickerSets.size(); ++i) {
                if (featuredStickerSets.get(i) instanceof TLRPC.TL_stickerSetFullCovered &&
                    ((TLRPC.TL_stickerSetFullCovered) featuredStickerSets.get(i)).keywords != null) {
                    ArrayList<TLRPC.TL_stickerKeyword> keywords = ((TLRPC.TL_stickerSetFullCovered) featuredStickerSets.get(i)).keywords;
                    for (int j = 0; j < keywords.size(); ++j) {
                        for (int k = 0; k < keywords.get(j).keyword.size(); ++k) {
                            String keyword = keywords.get(j).keyword.get(k);
                            if (queryLowercased.equals(keyword)) {
                                fullMatch.add(keywords.get(j).document_id);
                            } else if (queryLowercased.contains(keyword) || keyword.contains(queryLowercased)) {
                                halfMatch.add(keywords.get(j).document_id);
                            }
                        }
                    }
                }
            }

            fullMatch.addAll(halfMatch);
            if (onResult != null) {
                onResult.run(fullMatch);
            }
        });
    }

    public void getEmojiSuggestions(String[] langCodes, String keyword, boolean fullMatch, KeywordResultCallback callback, boolean allowAnimated) {
        getEmojiSuggestions(langCodes, keyword, fullMatch, callback, null, allowAnimated, null);
    }

    public void getEmojiSuggestions(String[] langCodes, String keyword, boolean fullMatch, KeywordResultCallback callback, final CountDownLatch sync, boolean allowAnimated) {
        getEmojiSuggestions(langCodes, keyword, fullMatch, callback, sync, allowAnimated, null);
    }

    public void getEmojiSuggestions(String[] langCodes, String keyword, boolean fullMatch, KeywordResultCallback callback, final CountDownLatch sync, boolean allowAnimated, Integer maxAnimatedPerEmoji) {
        if (callback == null) {
            return;
        }
        if (TextUtils.isEmpty(keyword) || langCodes == null) {
            callback.run(new ArrayList<>(), null);
            return;
        }
        ArrayList<String> recentEmoji = new ArrayList<>(Emoji.recentEmoji);
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            ArrayList<KeywordResult> result = new ArrayList<>();
            HashMap<String, Boolean> resultMap = new HashMap<>();
            String alias = null;
            try {
                SQLiteCursor cursor;
                boolean hasAny = false;
                for (int a = 0; a < langCodes.length; a++) {
                    cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT alias FROM emoji_keywords_info_v2 WHERE lang = ?", langCodes[a]);
                    if (cursor.next()) {
                        alias = cursor.stringValue(0);
                    }
                    cursor.dispose();
                    if (alias != null) {
                        hasAny = true;
                    }
                }
                if (!hasAny) {
                    AndroidUtilities.runOnUIThread(() -> {
                        for (int a = 0; a < langCodes.length; a++) {
                            if (currentFetchingEmoji.get(langCodes[a]) != null) {
                                return;
                            }
                        }
                        callback.run(result, null);
                    });
                    return;
                }

                String key = keyword.toLowerCase();
                for (int a = 0; a < 2; a++) {
                    if (a == 1) {
                        String translitKey = LocaleController.getInstance().getTranslitString(key, false, false);
                        if (translitKey.equals(key)) {
                            continue;
                        }
                        key = translitKey;
                    }
                    String key2 = null;
                    StringBuilder nextKey = new StringBuilder(key);
                    int pos = nextKey.length();
                    while (pos > 0) {
                        pos--;
                        char value = nextKey.charAt(pos);
                        value++;
                        nextKey.setCharAt(pos, value);
                        if (value != 0) {
                            key2 = nextKey.toString();
                            break;
                        }
                    }

                    if (fullMatch) {
                        cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT emoji, keyword FROM emoji_keywords_v2 WHERE keyword = ?", key);
                    } else if (key2 != null) {
                        cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT emoji, keyword FROM emoji_keywords_v2 WHERE keyword >= ? AND keyword < ?", key, key2);
                    } else {
                        key += "%";
                        cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT emoji, keyword FROM emoji_keywords_v2 WHERE keyword LIKE ?", key);
                    }
                    while (cursor.next()) {
                        String value = cursor.stringValue(0).replace("\ufe0f", "");
                        if (resultMap.get(value) != null) {
                            continue;
                        }
                        resultMap.put(value, true);
                        KeywordResult keywordResult = new KeywordResult();
                        keywordResult.emoji = value;
                        keywordResult.keyword = cursor.stringValue(1);
                        result.add(keywordResult);
                    }
                    cursor.dispose();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            Collections.sort(result, (o1, o2) -> {
                int idx1 = recentEmoji.indexOf(o1.emoji);
                if (idx1 < 0) {
                    idx1 = Integer.MAX_VALUE;
                }
                int idx2 = recentEmoji.indexOf(o2.emoji);
                if (idx2 < 0) {
                    idx2 = Integer.MAX_VALUE;
                }
                if (idx1 < idx2) {
                    return -1;
                } else if (idx1 > idx2) {
                    return 1;
                } else {
                    int len1 = o1.keyword.length();
                    int len2 = o2.keyword.length();

                    if (len1 < len2) {
                        return -1;
                    } else if (len1 > len2) {
                        return 1;
                    }
                    return 0;
                }
            });
            String aliasFinal = alias;
            if (allowAnimated && SharedConfig.suggestAnimatedEmoji) {
                fillWithAnimatedEmoji(result, maxAnimatedPerEmoji, () -> {
                    if (sync != null) {
                        callback.run(result, aliasFinal);
                        sync.countDown();
                    } else {
                        AndroidUtilities.runOnUIThread(() -> callback.run(result, aliasFinal));
                    }
                });
            } else {
                if (sync != null) {
                    callback.run(result, aliasFinal);
                    sync.countDown();
                } else {
                    AndroidUtilities.runOnUIThread(() -> callback.run(result, aliasFinal));
                }
            }
        });
        if (sync != null) {
            try {
                sync.await();
            } catch (Throwable ignore) {

            }
        }
    }

    private boolean triedLoadingEmojipacks = false;

    public void fillWithAnimatedEmoji(ArrayList<KeywordResult> result, Integer maxAnimatedPerEmojiInput, Runnable onDone) {
        if (result == null || result.isEmpty()) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        final ArrayList<TLRPC.TL_messages_stickerSet>[] emojiPacks = new ArrayList[2];
        emojiPacks[0] = getStickerSets(TYPE_EMOJIPACKS);
        final Runnable fillRunnable = () -> {
            ArrayList<TLRPC.StickerSetCovered> featuredSets = getFeaturedEmojiSets();
            ArrayList<KeywordResult> animatedResult = new ArrayList<>();
            ArrayList<TLRPC.Document> animatedEmoji = new ArrayList<>();
            final int maxAnimatedPerEmoji = maxAnimatedPerEmojiInput == null ? (result.size() > 5 ? 1 : (result.size() > 2 ? 2 : 3)) : maxAnimatedPerEmojiInput;
            int len = maxAnimatedPerEmojiInput == null ? Math.min(15, result.size()) : result.size();
            for (int i = 0; i < len; ++i) {
                String emoji = result.get(i).emoji;
                if (emoji == null) {
                    continue;
                }
                animatedEmoji.clear();
                boolean isPremium = UserConfig.getInstance(currentAccount).isPremium();
                if (Emoji.recentEmoji != null) {
                    for (int j = 0; j < Emoji.recentEmoji.size(); ++j) {
                        if (Emoji.recentEmoji.get(j).startsWith("animated_")) {
                            try {
                                long documentId = Long.parseLong(Emoji.recentEmoji.get(j).substring(9));
                                TLRPC.Document document = AnimatedEmojiDrawable.findDocument(currentAccount, documentId);
                                if (document != null &&
                                        (isPremium || MessageObject.isFreeEmoji(document)) &&
                                        emoji.equals(MessageObject.findAnimatedEmojiEmoticon(document, null))) {
                                    animatedEmoji.add(document);
                                }
                            } catch (Exception ignore) {
                            }
                        }
                        if (animatedEmoji.size() >= maxAnimatedPerEmoji) {
                            break;
                        }
                    }
                }
                if (animatedEmoji.size() < maxAnimatedPerEmoji && emojiPacks[0] != null) {
                    for (int j = 0; j < emojiPacks[0].size(); ++j) {
                        TLRPC.TL_messages_stickerSet set = emojiPacks[0].get(j);
                        if (set != null && set.documents != null) {
                            for (int d = 0; d < set.documents.size(); ++d) {
                                TLRPC.Document document = set.documents.get(d);
                                if (document != null && document.attributes != null && !animatedEmoji.contains(document)) {
                                    TLRPC.TL_documentAttributeCustomEmoji attribute = null;
                                    for (int k = 0; k < document.attributes.size(); ++k) {
                                        TLRPC.DocumentAttribute attr = document.attributes.get(k);
                                        if (attr instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                                            attribute = (TLRPC.TL_documentAttributeCustomEmoji) attr;
                                            break;
                                        }
                                    }

                                    if (attribute != null && emoji.equals(attribute.alt) && (isPremium || attribute.free)) {
                                        boolean duplicate = false;
                                        for (int l = 0; l < animatedEmoji.size(); ++l) {
                                            if (animatedEmoji.get(l).id == document.id) {
                                                duplicate = true;
                                                break;
                                            }
                                        }
                                        if (!duplicate) {
                                            animatedEmoji.add(document);
                                            if (animatedEmoji.size() >= maxAnimatedPerEmoji) {
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (animatedEmoji.size() >= maxAnimatedPerEmoji) {
                            break;
                        }
                    }
                }
                if (animatedEmoji.size() < maxAnimatedPerEmoji && featuredSets != null) {
                    for (int j = 0; j < featuredSets.size(); ++j) {
                        TLRPC.StickerSetCovered set = featuredSets.get(j);
                        if (set == null) {
                            continue;
                        }
                        ArrayList<TLRPC.Document> documents = set instanceof TLRPC.TL_stickerSetFullCovered ? ((TLRPC.TL_stickerSetFullCovered) set).documents : set.covers;
                        if (documents == null) {
                            continue;
                        }
                        for (int d = 0; d < documents.size(); ++d) {
                            TLRPC.Document document = documents.get(d);
                            if (document != null && document.attributes != null && !animatedEmoji.contains(document)) {
                                TLRPC.TL_documentAttributeCustomEmoji attribute = null;
                                for (int k = 0; k < document.attributes.size(); ++k) {
                                    TLRPC.DocumentAttribute attr = document.attributes.get(k);
                                    if (attr instanceof TLRPC.TL_documentAttributeCustomEmoji) {
                                        attribute = (TLRPC.TL_documentAttributeCustomEmoji) attr;
                                        break;
                                    }
                                }

                                if (attribute != null && emoji.equals(attribute.alt) && (isPremium || attribute.free)) {
                                    boolean duplicate = false;
                                    for (int l = 0; l < animatedEmoji.size(); ++l) {
                                        if (animatedEmoji.get(l).id == document.id) {
                                            duplicate = true;
                                            break;
                                        }
                                    }
                                    if (!duplicate) {
                                        animatedEmoji.add(document);
                                        if (animatedEmoji.size() >= maxAnimatedPerEmoji) {
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (animatedEmoji.size() >= maxAnimatedPerEmoji) {
                            break;
                        }
                    }
                }

                if (!animatedEmoji.isEmpty()) {
                    String keyword = result.get(i).keyword;
                    for (int p = 0; p < animatedEmoji.size(); ++p) {
                        TLRPC.Document document = animatedEmoji.get(p);
                        if (document != null) {
                            KeywordResult keywordResult = new KeywordResult();
                            keywordResult.emoji = "animated_" + document.id;
                            keywordResult.keyword = keyword;
                            animatedResult.add(keywordResult);
                        }
                    }
                }
            }
            result.addAll(0, animatedResult);
            if (onDone != null) {
                onDone.run();
            }
        };
        if ((emojiPacks[0] == null || emojiPacks[0].isEmpty()) && !triedLoadingEmojipacks) {
            triedLoadingEmojipacks = true;
            final boolean[] done = new boolean[1];
            AndroidUtilities.runOnUIThread(() -> {
                loadStickers(TYPE_EMOJIPACKS, true, false, false, packs -> {
                    if (!done[0]) {
                        emojiPacks[0] = packs;
                        fillRunnable.run();
                        done[0] = true;
                    }
                });
            });
            AndroidUtilities.runOnUIThread(() -> {
                if (!done[0]) {
                    fillRunnable.run();
                    done[0] = true;
                }
            }, 900);
        } else {
            fillRunnable.run();
        }
    }

    public void loadEmojiThemes() {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("emojithemes_config_" + currentAccount, Context.MODE_PRIVATE);
        int count = preferences.getInt("count", 0);
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> previewItems = new ArrayList<>();
        previewItems.add(new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createHomePreviewTheme()));
        for (int i = 0; i < count; ++i) {
            String value = preferences.getString("theme_" + i, "");
            SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
            try {
                TLRPC.TL_theme theme = TLRPC.Theme.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                EmojiThemes fullTheme = EmojiThemes.createPreviewFullTheme(theme);
                if (fullTheme.items.size() >= 4) {
                    previewItems.add(new ChatThemeBottomSheet.ChatThemeItem(fullTheme));
                }

                ChatThemeController.chatThemeQueue.postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        for (int i = 0; i < previewItems.size(); i++) {
                            if (previewItems.get(i) != null && previewItems.get(i).chatTheme != null) {
                                previewItems.get(i).chatTheme.loadPreviewColors(0);
                            }
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            defaultEmojiThemes.clear();
                            defaultEmojiThemes.addAll(previewItems);
                        });
                    }
                });
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public void generateEmojiPreviewThemes(final ArrayList<TLRPC.TL_theme> emojiPreviewThemes, int currentAccount) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("emojithemes_config_" + currentAccount, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("count", emojiPreviewThemes.size());
        for (int i = 0; i < emojiPreviewThemes.size(); ++i) {
            TLRPC.TL_theme tlChatTheme = emojiPreviewThemes.get(i);
            SerializedData data = new SerializedData(tlChatTheme.getObjectSize());
            tlChatTheme.serializeToStream(data);
            editor.putString("theme_" + i, Utilities.bytesToHex(data.toByteArray()));
        }
        editor.apply();

        if (!emojiPreviewThemes.isEmpty()) {
            final ArrayList<ChatThemeBottomSheet.ChatThemeItem> previewItems = new ArrayList<>();
            previewItems.add(new ChatThemeBottomSheet.ChatThemeItem(EmojiThemes.createHomePreviewTheme()));
            for (int i = 0; i < emojiPreviewThemes.size(); i++) {
                TLRPC.TL_theme theme = emojiPreviewThemes.get(i);
                EmojiThemes chatTheme = EmojiThemes.createPreviewFullTheme(theme);
                ChatThemeBottomSheet.ChatThemeItem item = new ChatThemeBottomSheet.ChatThemeItem(chatTheme);
                if (chatTheme.items.size() >= 4) {
                    previewItems.add(item);
                }
            }
            ChatThemeController.chatThemeQueue.postRunnable(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < previewItems.size(); i++) {
                        previewItems.get(i).chatTheme.loadPreviewColors(currentAccount);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        defaultEmojiThemes.clear();
                        defaultEmojiThemes.addAll(previewItems);
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiPreviewThemesChanged);
                    });
                }
            });
        } else {
            defaultEmojiThemes.clear();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.emojiPreviewThemesChanged);
        }
    }

    //---------------- EMOJI END ----------------

    public ArrayList<TLRPC.EmojiStatus> getDefaultEmojiStatuses() {
        final int type = 1; // default
        if (!emojiStatusesFromCacheFetched[type]) {
            fetchEmojiStatuses(type, true);
        } else if (/*emojiStatusesHash[type] == 0 || */emojiStatusesFetchDate[type] == null || (System.currentTimeMillis() / 1000 - emojiStatusesFetchDate[type]) > 60 * 30) {
            fetchEmojiStatuses(type, false);
        }
        return emojiStatuses[type];
    }

    public ArrayList<TLRPC.EmojiStatus> getRecentEmojiStatuses() {
        final int type = 0; // recent
        if (!emojiStatusesFromCacheFetched[type]) {
            fetchEmojiStatuses(type, true);
        } else if (/*emojiStatusesHash[type] == 0 || */emojiStatusesFetchDate[type] == null || (System.currentTimeMillis() / 1000 - emojiStatusesFetchDate[type]) > 60 * 30) {
            fetchEmojiStatuses(type, false);
        }
        return emojiStatuses[type];
    }

    public ArrayList<TLRPC.EmojiStatus> clearRecentEmojiStatuses() {
        final int type = 0; // recent
        if (emojiStatuses[type] != null) {
            emojiStatuses[type].clear();
        }
        emojiStatusesHash[type] = 0;
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                getMessagesStorage().getDatabase().executeFast("DELETE FROM emoji_statuses WHERE type = " + type).stepThis().dispose();
            } catch (Exception e) {}
        });
        return emojiStatuses[type];
    }

    public void pushRecentEmojiStatus(TLRPC.EmojiStatus status) {
        final int type = 0; // recent
        if (emojiStatuses[type] != null) {
            if (status instanceof TLRPC.TL_emojiStatus) {
                long documentId = ((TLRPC.TL_emojiStatus) status).document_id;
                for (int i = 0; i < emojiStatuses[type].size(); ++i) {
                    if (emojiStatuses[type].get(i) instanceof TLRPC.TL_emojiStatus &&
                        ((TLRPC.TL_emojiStatus) emojiStatuses[type].get(i)).document_id == documentId) {
                        emojiStatuses[type].remove(i--);
                    }
                }
            }
            emojiStatuses[type].add(0, status);
            while (emojiStatuses[type].size() > 50) {
                emojiStatuses[type].remove(emojiStatuses[type].size() - 1);
            }

            TLRPC.TL_account_emojiStatuses statuses = new TLRPC.TL_account_emojiStatuses();
            // todo: calc hash
            statuses.hash = emojiStatusesHash[type];
            statuses.statuses = emojiStatuses[type];
            updateEmojiStatuses(type, statuses);
        }
    }

    public void fetchEmojiStatuses(int type, boolean cache) {
        if (emojiStatusesFetching[type]) {
            return;
        }
        emojiStatusesFetching[type] = true;
        if (cache) {
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                boolean done = false;
                try {
                    SQLiteCursor cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT data FROM emoji_statuses WHERE type = " + type + " LIMIT 1");
                    if (cursor.next() && cursor.getColumnCount() > 0 && !cursor.isNull(0)) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.account_EmojiStatuses response = TLRPC.account_EmojiStatuses.TLdeserialize(data, data.readInt32(false), false);
                            if (response instanceof TLRPC.TL_account_emojiStatuses) {
                                emojiStatusesHash[type] = response.hash;
                                emojiStatuses[type] = response.statuses;
                                done = true;
                            }
                            data.reuse();
                        }
                    }
                    cursor.dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                emojiStatusesFromCacheFetched[type] = true;
                emojiStatusesFetching[type] = false;
                if (done) {
                    AndroidUtilities.runOnUIThread(() -> {
                        getNotificationCenter().postNotificationName(NotificationCenter.recentEmojiStatusesUpdate);
                    });
                } else {
                    fetchEmojiStatuses(type, false);
                }
            });
        } else {
            TLObject req;
            if (type == 0) {
                TLRPC.TL_account_getRecentEmojiStatuses recentReq = new TLRPC.TL_account_getRecentEmojiStatuses();
                recentReq.hash = emojiStatusesHash[type];
                req = recentReq;
            } else {
                TLRPC.TL_account_getDefaultEmojiStatuses defaultReq = new TLRPC.TL_account_getDefaultEmojiStatuses();
                defaultReq.hash = emojiStatusesHash[type];
                req = defaultReq;
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                emojiStatusesFetchDate[type] = System.currentTimeMillis() / 1000;
                if (res instanceof TLRPC.TL_account_emojiStatusesNotModified) {
                    emojiStatusesFetching[type] = false;
                } else if (res instanceof TLRPC.TL_account_emojiStatuses) {
                    TLRPC.TL_account_emojiStatuses response = (TLRPC.TL_account_emojiStatuses) res;
                    emojiStatusesHash[type] = response.hash;
                    emojiStatuses[type] = response.statuses;
                    updateEmojiStatuses(type, response);
                    AndroidUtilities.runOnUIThread(() -> {
                        getNotificationCenter().postNotificationName(NotificationCenter.recentEmojiStatusesUpdate);
                    });
                }
            });
        }
    }

    private void updateEmojiStatuses(int type, TLRPC.TL_account_emojiStatuses response) {
        getMessagesStorage().getStorageQueue().postRunnable(() -> {
            try {
                getMessagesStorage().getDatabase().executeFast("DELETE FROM emoji_statuses WHERE type = " + type).stepThis().dispose();
                SQLitePreparedStatement state = getMessagesStorage().getDatabase().executeFast("INSERT INTO emoji_statuses VALUES(?, ?)");
                state.requery();
                NativeByteBuffer data = new NativeByteBuffer(response.getObjectSize());
                response.serializeToStream(data);
                state.bindByteBuffer(1, data);
                state.bindInteger(2, type);
                state.step();
                data.reuse();
                state.dispose();
            } catch (Exception e) {
                FileLog.e(e);
            }
            emojiStatusesFetching[type] = false;
        });
    }


    ArrayList<TLRPC.Reaction> recentReactions = new ArrayList<>();
    ArrayList<TLRPC.Reaction> topReactions = new ArrayList<>();
    boolean loadingRecentReactions;

    public ArrayList<TLRPC.Reaction> getRecentReactions() {
        return recentReactions;
    }

    public void clearRecentReactions() {
        recentReactions.clear();
        SharedPreferences recentReactionsPref = ApplicationLoader.applicationContext.getSharedPreferences("recent_reactions_" + currentAccount, Context.MODE_PRIVATE);
        recentReactionsPref.edit().clear().apply();
        TLRPC.TL_messages_clearRecentReactions clearRecentReaction = new TLRPC.TL_messages_clearRecentReactions();
        ConnectionsManager.getInstance(currentAccount).sendRequest(clearRecentReaction, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public ArrayList<TLRPC.Reaction> getTopReactions() {
        return topReactions;
    }

    public void loadRecentAndTopReactions(boolean force) {
        if (loadingRecentReactions || !recentReactions.isEmpty() || force) {
            return;
        }
        SharedPreferences recentReactionsPref = ApplicationLoader.applicationContext.getSharedPreferences("recent_reactions_" + currentAccount, Context.MODE_PRIVATE);
        SharedPreferences topReactionsPref = ApplicationLoader.applicationContext.getSharedPreferences("top_reactions_" + currentAccount, Context.MODE_PRIVATE);

        recentReactions.clear();
        topReactions.clear();
        recentReactions.addAll(loadReactionsFromPref(recentReactionsPref));
        topReactions.addAll(loadReactionsFromPref(topReactionsPref));
        loadingRecentReactions = true;

        boolean loadFromServer = true;

        if (loadFromServer) {
            TLRPC.TL_messages_getRecentReactions recentReactionsRequest = new TLRPC.TL_messages_getRecentReactions();
            recentReactionsRequest.hash = recentReactionsPref.getLong("hash", 0);
            recentReactionsRequest.limit = 50;
            ConnectionsManager.getInstance(currentAccount).sendRequest(recentReactionsRequest, (response, error) -> {
                if (error == null) {
                    if (response instanceof TLRPC.TL_messages_reactions) {
                        TLRPC.TL_messages_reactions reactions = (TLRPC.TL_messages_reactions) response;
                        recentReactions.clear();
                        recentReactions.addAll(reactions.reactions);

                        saveReactionsToPref(recentReactionsPref, reactions.hash, reactions.reactions);
                    }
                    if (response instanceof TLRPC.TL_messages_reactionsNotModified) {

                    }
                }
            });

            TLRPC.TL_messages_getTopReactions topReactionsRequest = new TLRPC.TL_messages_getTopReactions();
            topReactionsRequest.hash = topReactionsPref.getLong("hash", 0);
            topReactionsRequest.limit = 100;
            ConnectionsManager.getInstance(currentAccount).sendRequest(topReactionsRequest, (response, error) -> {
                if (error == null) {
                    if (response instanceof TLRPC.TL_messages_reactions) {
                        TLRPC.TL_messages_reactions reactions = (TLRPC.TL_messages_reactions) response;
                        topReactions.clear();
                        topReactions.addAll(reactions.reactions);

                        saveReactionsToPref(topReactionsPref, reactions.hash, reactions.reactions);

                    }
                    if (response instanceof TLRPC.TL_messages_reactionsNotModified) {

                    }
                }
            });
        }
    }

    public static void saveReactionsToPref(SharedPreferences preferences, long hash, ArrayList<? extends TLObject> object) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("count", object.size());
        editor.putLong("hash", hash);
        for (int i = 0; i < object.size(); ++i) {
            TLObject tlObject = object.get(i);
            SerializedData data = new SerializedData(tlObject.getObjectSize());
            tlObject.serializeToStream(data);
            editor.putString("object_" + i, Utilities.bytesToHex(data.toByteArray()));
        }
        editor.apply();
    }

    public static ArrayList<TLRPC.Reaction> loadReactionsFromPref(SharedPreferences preferences) {
        int count = preferences.getInt("count", 0);
        ArrayList<TLRPC.Reaction> objects = new ArrayList<>(count);
        if (count > 0) {
            for (int i = 0; i < count; ++i) {
                String value = preferences.getString("object_" + i, "");
                SerializedData serializedData = new SerializedData(Utilities.hexToBytes(value));
                try {
                    objects.add(TLRPC.Reaction.TLdeserialize(serializedData, serializedData.readInt32(true), true));
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

        }
        return objects;
    }
}
