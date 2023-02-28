package com.eterocell.nekoegram.update

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.*

class TextCheckWithIconCell
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
    @StyleRes defStyleRes: Int = 0,
    isDialog: Boolean = false,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    val textView = TextView(context)
    val imageView = RLottieImageView(context)
    val checkBox = Switch(context)

    var isAnimatingToThumbInsteadOfTouch = false

    private var needDivider: Boolean = false
        set(divider) {
            field = divider
            setWillNotDraw(!divider)
        }
    private var lastTouchX = -1F
        get() = if (isAnimatingToThumbInsteadOfTouch) (if (LocaleController.isRTL) AndroidUtilities.dp(
            22f
        ) else measuredWidth - AndroidUtilities.dp(42f)).toFloat() else field
    private var animatedColorBackground = 0
    private var animationPaint: Paint? = null
    private var animator: ObjectAnimator? = null
    private var animationProgress = 0f

    init {
        with(textView) {
            setTextColor(Theme.getColor(if (isDialog) Theme.key_dialogTextBlack else Theme.key_windowBackgroundWhiteBlackText))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setLines(1)
            maxLines = 1
            isSingleLine = true
            gravity =
                (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL
            ellipsize = TextUtils.TruncateAt.END
        }
        addView(
            textView,
            LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT.toFloat(),
                (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL,
                70f,
                0f,
                70f,
                0f
            )
        )

        with(imageView) {
            scaleType = ImageView.ScaleType.CENTER
            colorFilter = PorterDuffColorFilter(
                Theme.getColor(if (isDialog) Theme.key_dialogIcon else Theme.key_windowBackgroundWhiteGrayIcon),
                PorterDuff.Mode.MULTIPLY
            )
        }
        addView(
            imageView,
            LayoutHelper.createFrame(
                26,
                26f,
                (if (LocaleController.isRTL) Gravity.RIGHT else Gravity.LEFT) or Gravity.CENTER_VERTICAL,
                21f,
                0f,
                21f,
                0f
            )
        )

        checkBox.setColors(
            Theme.key_switchTrack,
            Theme.key_switchTrackChecked,
            Theme.key_windowBackgroundWhite,
            Theme.key_windowBackgroundWhite
        )
        addView(
            checkBox,
            LayoutHelper.createFrame(
                37,
                20f,
                (if (LocaleController.isRTL) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL,
                22f,
                0f,
                22f,
                0f
            )
        )

        clipChildren = false
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.x?.let {
            lastTouchX = it
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.EXACTLY
            ),
            MeasureSpec.makeMeasureSpec(
                AndroidUtilities.dp(height.toFloat()) + if (needDivider) 1 else 0,
                MeasureSpec.EXACTLY
            )
        )
    }

    fun setTextAndCheckAndIcon(text: String, resId: Int, checked: Boolean, divider: Boolean) {
        textView.text = text
        checkBox.setChecked(checked, false)
        needDivider = divider
        imageView.setImageResource(resId)
        imageView.visibility = VISIBLE
        val layoutParams = textView.layoutParams as LayoutParams
        layoutParams.height = LayoutParams.MATCH_PARENT
        layoutParams.topMargin = 0
        textView.layoutParams = layoutParams
        setWillNotDraw(!divider)
    }

    fun setEnabled(value: Boolean, animators: ArrayList<Animator?>?) {
        super.setEnabled(value)
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, "alpha", if (value) 1.0f else 0.5f))
            animators.add(ObjectAnimator.ofFloat(checkBox, "alpha", if (value) 1.0f else 0.5f))
        } else {
            textView.alpha = if (value) 1.0f else 0.5f
            checkBox.alpha = if (value) 1.0f else 0.5f
        }
    }

    fun setChecked(checked: Boolean) {
        checkBox.setChecked(checked, true)
    }

    fun isChecked(): Boolean {
        return checkBox.isChecked
    }

    override fun setBackgroundColor(color: Int) {
        clearAnimation()
        animatedColorBackground = 0
        super.setBackgroundColor(color)
    }

    fun setBackgroundColorAnimated(checked: Boolean, color: Int) {
        animator?.cancel()
        animator = null
        if (animatedColorBackground != 0) {
            setBackgroundColor(animatedColorBackground)
        }
        if (animationPaint == null) {
            animationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        }
        checkBox.setOverrideColor(if (checked) 1 else 2)
        animatedColorBackground = color
        animationPaint!!.color = animatedColorBackground
        animationProgress = 0f
        animator = ObjectAnimator.ofFloat(
            this,
            ANIMATION_PROGRESS,
            0.0f,
            1.0f
        ) as ObjectAnimator
        animator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                setBackgroundColor(animatedColorBackground)
                animatedColorBackground = 0
                invalidate()
            }
        })
        animator!!.interpolator = CubicBezierInterpolator.EASE_OUT
        animator!!.setDuration(240).start()
    }

    private fun setAnimationProgress(value: Float) {
        animationProgress = value
        val tx: Float = lastTouchX
        val rad = tx.coerceAtLeast(measuredWidth - tx) + AndroidUtilities.dp(40f)
        val cy = measuredHeight / 2
        val animatedRad = rad * animationProgress
        checkBox.setOverrideColorProgress(tx, cy.toFloat(), animatedRad)
    }

    fun setBackgroundColorAnimatedReverse(color: Int) {
        if (animator != null) {
            animator!!.cancel()
            animator = null
        }
        val from =
            if (animatedColorBackground != 0) animatedColorBackground else if (background is ColorDrawable) (background as ColorDrawable).color else 0
        if (animationPaint == null) animationPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        animationPaint!!.color = from
        setBackgroundColor(color)
        checkBox.setOverrideColor(1)
        animatedColorBackground = color
        animator = ObjectAnimator.ofFloat(this, ANIMATION_PROGRESS, 1f, 0f).setDuration(240)
        animator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                setBackgroundColor(color)
                animatedColorBackground = 0
                invalidate()
            }
        })
        animator!!.interpolator = CubicBezierInterpolator.EASE_OUT
        animator!!.start()
    }

    override fun onDraw(canvas: Canvas) {
        if (animatedColorBackground != 0) {
            val tx: Float = lastTouchX
            val rad = tx.coerceAtLeast(measuredWidth - tx) + AndroidUtilities.dp(40f)
            val cy = measuredHeight / 2
            val animatedRad = rad * animationProgress
            canvas.drawCircle(tx, cy.toFloat(), animatedRad, animationPaint!!)
        }
        if (needDivider) {
            canvas.drawLine(
                if (LocaleController.isRTL) 0F else AndroidUtilities.dp(70f).toFloat(),
                (measuredHeight - 1).toFloat(),
                (measuredWidth - if (LocaleController.isRTL) AndroidUtilities.dp(70f) else 0).toFloat(),
                (measuredHeight - 1).toFloat(),
                Theme.dividerPaint
            )
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = checkBox.isChecked
        info.contentDescription =
            if (checkBox.isChecked) LocaleController.getString(
                "NotificationsOn",
                org.telegram.messenger.R.string.NotificationsOn
            ) else LocaleController.getString(
                "NotificationsOff",
                org.telegram.messenger.R.string.NotificationsOff
            )
    }

    companion object {
        val ANIMATION_PROGRESS
            get() = object :
                AnimationProperties.FloatProperty<TextCheckWithIconCell>("animationProgress") {
                override fun get(`object`: TextCheckWithIconCell?): Float {
                    TODO("Not yet implemented")
                }

                override fun setValue(`object`: TextCheckWithIconCell?, value: Float) {
                    TODO("Not yet implemented")
                }
            }
    }
}