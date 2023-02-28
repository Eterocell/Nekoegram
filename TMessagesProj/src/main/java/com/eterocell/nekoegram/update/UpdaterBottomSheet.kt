/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @immat0x1, 2022.

*/

package com.eterocell.nekoegram.update

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.eterocell.nekoegram.nekoegramVersion
import com.eterocell.nekoegram.store.NekoeStore
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.SimpleTextView
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.TextCell
import org.telegram.ui.Components.*
import java.util.*

class UpdaterBottomSheet(
    context: Context, isAvailable: Boolean, updateInfo: UpdateInfo
) : BottomSheet(context, false) {

    private val imageView = RLottieImageView(context)
    private val changelogTextView = object : TextView(context) {
        override fun onDraw(canvas: Canvas?) {
            super.onDraw(canvas)
            canvas!!.drawLine(
                0f,
                (measuredHeight - 1).toFloat(),
                measuredWidth.toFloat(),
                (measuredHeight - 1).toFloat(),
                Theme.dividerPaint
            )
        }
    }

    private var isTranslated = false
    private var translatedC: CharSequence = ""

    init {
        setOpenNoDelay(true)
        fixNavigationBar()

        val linearLayout = LinearLayout(context)
        linearLayout.orientation = LinearLayout.VERTICAL

        val header = FrameLayout(context)
        linearLayout.addView(
            header, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT.toFloat(), 0, 21f, 10f, 0f, 10f
            )
        )

        if (isAvailable) {
            with(imageView) {
                setOnClickListener {
                    if (!imageView.isPlaying && imageView.animatedDrawable != null) {
                        imageView.animatedDrawable.currentFrame = 0
                        imageView.playAnimation()
                    }
                }
                setAnimation(R.raw.wallet_congrats, 60, 60, intArrayOf(0x000000, 0x000000))
                scaleType = ImageView.ScaleType.CENTER
            }
            header.addView(
                imageView, LayoutHelper.createFrame(60, 60, Gravity.LEFT or Gravity.CENTER_VERTICAL)
            )
        }

        val nameView = SimpleTextView(context)
        with(nameView) {
            setTextSize(20)
            setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM))
            textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
            setGravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
            setText(
                LocaleController.getString(
                    "Nekoegram", R.string.Nekoegram
                )
            )
        }
        header.addView(
            nameView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                30f,
                Gravity.LEFT,
                if (isAvailable) 75F else 0F,
                5f,
                0f,
                0f
            )
        )

        val timeView = SimpleTextView(context)
        timeView.textColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText)
        timeView.setTextSize(13)
        timeView.setTypeface(AndroidUtilities.getTypeface("fonts/rregular.ttf"))
        timeView.setGravity(Gravity.LEFT or Gravity.CENTER_VERTICAL)
        timeView.text = if (isAvailable) updateInfo.uploadDate else LocaleController.getString(
            "UP_LastCheck", R.string.UP_LastCheck
        ) + ": " + LocaleController.formatDateTime(NekoeStore.getLastUpdateCheckTime(context) / 1000)
        header.addView(
            timeView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                20f,
                Gravity.LEFT,
                if (isAvailable) 75F else 0F,
                35f,
                0f,
                0f
            )
        )

        val versionCell = TextCell(context)
        with(versionCell) {
            background = Theme.createSelectorDrawable(
                Theme.getColor(Theme.key_listSelector), 100, 0
            )
            if (isAvailable) {
                setTextAndValueAndIcon(
                    LocaleController.getString(
                        "UP_Version", R.string.UP_Version
                    ),
                    updateInfo.version.replace("v|-beta".toRegex(), ""),
                    R.drawable.info_outline_28,
                    true
                )
            } else {
                setTextAndValueAndIcon(
                    LocaleController.getString(
                        "UP_CurrentVersion", R.string.UP_CurrentVersion
                    ), nekoegramVersion, R.drawable.info_outline_28, false
                )
            }
            setOnClickListener {
                copyText(
                    getTextView().text.toString() + ": " + getValueTextView().text.toString()
                )
            }
        }
        linearLayout.addView(versionCell)

        if (isAvailable) {
            val sizeCell = TextCell(context)
            with(sizeCell) {
                background = Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector), 100, 0
                )
                setTextAndValueAndIcon(
                    LocaleController.getString(
                        "UP_UpdateSize", R.string.UP_UpdateSize
                    ), updateInfo.size, R.drawable.document_outline_28, true
                )
                setOnClickListener {
                    copyText(
                        getTextView().text.toString() + ": " + getValueTextView().text.toString()
                    )
                }
            }
            linearLayout.addView(sizeCell)

            val changelogCell = TextCell(context)
            with(changelogCell) {
                background = Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector), 100, 0
                )
                setTextAndIcon(
                    LocaleController.getString(
                        "UP_Changelog", R.string.UP_Changelog
                    ), R.drawable.grid_square_outline_28, false
                )
                setOnClickListener {
                    copyText(
                        getTextView().text.toString() + "\n" + if (isTranslated) translatedC else UpdateUtils.replaceTags(
                            updateInfo.changelog
                        )
                    )
                }
            }
            linearLayout.addView(changelogCell)

            with(changelogTextView) {
                setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                movementMethod = AndroidUtilities.LinkMovementMethodMy()
                setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink))
                text = UpdateUtils.replaceTags(updateInfo.changelog)
                setPadding(
                    AndroidUtilities.dp(21f),
                    0,
                    AndroidUtilities.dp(21f),
                    AndroidUtilities.dp(10f)
                )
                gravity = Gravity.LEFT or Gravity.CENTER_VERTICAL
                setOnClickListener {
                    UpdateUtils.translate(updateInfo.changelog, object : OnTranslation {
                        override fun successThen(translated: String) {
                            translatedC = translated
                            animateChangelog(UpdateUtils.replaceTags(if (isTranslated) updateInfo.changelog else translatedC as String))
                            isTranslated = isTranslated xor true
                        }

                        override fun failThen() {}
                    })
                }
            }
            linearLayout.addView(
                changelogTextView,
                LayoutHelper.createFrame(
                    LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT.toFloat()
                )
            )

            val doneButton = TextView(context)
            with(doneButton) {
                setLines(1)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
                background = Theme.AdaptiveRipple.filledRect(
                    Theme.getColor(Theme.key_featuredStickers_addButton),
                    6f
                )
                typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                text = LocaleController.getString(
                    "AppUpdateDownloadNow",
                    R.string.AppUpdateDownloadNow
                )
                setOnClickListener {
                    UpdateUtils.downloadApk(
                        context,
                        updateInfo.downloadUrl,
                        "${R.string.Nekoegram} ${updateInfo.version}"
                    )
                    dismiss()
                }
            }
            linearLayout.addView(
                doneButton,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, 0, 16f, 15f, 16f, 5f)
            )

            val scheduleButton = TextView(context)
            with(scheduleButton) {
                setLines(1)
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton))
                background = null
                typeface = AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
                text = LocaleController.getString(
                    "AppUpdateRemindMeLater",
                    R.string.AppUpdateRemindMeLater
                )
                setOnClickListener {
                    NekoeStore.setUpdateScheduleTimestamp(System.currentTimeMillis(), context)
                    dismiss()
                }
            }
            linearLayout.addView(
                scheduleButton,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, 0, 16f, 1f, 16f, 0f)
            )
        } else {
            val checkOnLaunchView = TextCheckWithIconCell(context)
            with(checkOnLaunchView) {
                setEnabled(true, null)
                background = Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector),
                    100,
                    0
                )
                setTextAndCheckAndIcon(
                    LocaleController.getString(
                        "UP_Auto_OTA",
                        R.string.UP_Auto_OTA
                    ), R.drawable.switch_outline_28, NekoeStore.getAutoOTA(context), false
                )
                setOnClickListener {
                    NekoeStore.toggleAutoOTA(context)
                    setChecked(!isChecked())
                }
            }
            linearLayout.addView(checkOnLaunchView)

            val clearUpdates: TextCell = object : TextCell(context) {
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    canvas.drawLine(
                        0f,
                        (measuredHeight - 1).toFloat(),
                        measuredWidth.toFloat(),
                        (measuredHeight - 1).toFloat(),
                        Theme.dividerPaint
                    )
                }
            }
            clearUpdates.background =
                Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 100, 0)
            clearUpdates.setTextAndIcon(
                LocaleController.getString(
                    "UP_ClearUpdatesCache",
                    R.string.UP_ClearUpdatesCache
                ), R.drawable.clear_data_outline_28, false
            )
            clearUpdates.setOnClickListener {
                if (UpdateUtils.getOtaDirSize()?.replace("\\D+".toRegex(), "") == "0") {
                    BulletinFactory.of(getContainer(), null).createErrorBulletin(
                        LocaleController.getString(
                            "UP_NothingToClear",
                            R.string.UP_NothingToClear
                        )
                    ).show()
                } else {
                    BulletinFactory.of(getContainer(), null).createErrorBulletin(
                        LocaleController.formatString(
                            "UP_ClearedUpdatesCache",
                            R.string.UP_ClearedUpdatesCache,
                            UpdateUtils.getOtaDirSize()
                        )
                    ).show()
                    UpdateUtils.cleanOtaDir()
                }
            }
            linearLayout.addView(clearUpdates)

            val checkUpdatesButton = TextView(context)
            checkUpdatesButton.setLines(1)
            checkUpdatesButton.isSingleLine = true
            checkUpdatesButton.ellipsize = TextUtils.TruncateAt.END
            checkUpdatesButton.gravity = Gravity.CENTER
            checkUpdatesButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText))
            checkUpdatesButton.background = Theme.AdaptiveRipple.filledRect(
                Theme.getColor(Theme.key_featuredStickers_addButton),
                6f
            )
            checkUpdatesButton.typeface =
                AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM)
            checkUpdatesButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            val spannableStringBuilder = SpannableStringBuilder()
            spannableStringBuilder.append(".  ").append(
                LocaleController.getString(
                    "UP_CheckForUpdates",
                    R.string.UP_CheckForUpdates
                )
            )
            spannableStringBuilder.setSpan(
                ColoredImageSpan(
                    Objects.requireNonNull<Drawable?>(
                        ContextCompat.getDrawable(getContext(), R.drawable.sync_outline_28)
                    )
                ), 0, 1, 0
            )
            checkUpdatesButton.text = spannableStringBuilder
            checkUpdatesButton.setOnClickListener {
                UpdateUtils.checkUpdates(context, true, object : OnUpdate {
                    override fun foundThen() {
                        dismiss()
                    }

                    override fun notFoundThen() {
                        BulletinFactory.of(getContainer(), null).createErrorBulletin(
                            LocaleController.getString(
                                "UP_Not_Found",
                                R.string.UP_Not_Found
                            )
                        ).show()
                        timeView.text = LocaleController.getString(
                            "UP_LastCheck",
                            R.string.UP_LastCheck
                        ) + ": " + LocaleController.formatDateTime(
                            NekoeStore.getLastUpdateCheckTime(
                                context
                            ) / 1000
                        )
                    }
                })
            }
            linearLayout.addView(
                checkUpdatesButton,
                LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48f, 0, 16f, 15f, 16f, 16f)
            )
        }

        val scrollView = ScrollView(context)
        scrollView.addView(linearLayout)
        setCustomView(scrollView)
    }

    private fun animateChangelog(text: CharSequence) {
        changelogTextView.text = text
        val animatorSet = AnimatorSet()
        with(animatorSet) {
            duration = 250
            interpolator = CubicBezierInterpolator.EASE_OUT_QUINT
            animatorSet.playTogether(
                ObjectAnimator.ofFloat(changelogTextView, View.ALPHA, 0.0f, 1.0f),
                ObjectAnimator.ofFloat(
                    changelogTextView,
                    View.TRANSLATION_Y,
                    AndroidUtilities.dp(12f).toFloat(),
                    0f
                )
            )
            start()
        }
    }

    private fun copyText(text: CharSequence) {
        AndroidUtilities.addToClipboard(text)
        BulletinFactory.of(getContainer(), null).createCopyBulletin(
            LocaleController.getString(
                "TextCopied", R.string.TextCopied
            )
        ).show()
    }

    override fun show() {
        super.show()
        imageView.playAnimation()
    }
}