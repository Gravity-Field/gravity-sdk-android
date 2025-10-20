package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.models.Slot
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

@SuppressLint("ViewConstructor")
class ProductView(context: Context, slot: Slot) : FrameLayout(context) {
    //    private val imageView: ImageView
    private val nameTextView: TextView
    private val priceTextView: TextView
    private var oldPriceTextView: TextView? = null

    init {
        layoutParams = LayoutParams(
            dpToPx(160f),
            dpToPx(210f)
        )
        background = createRoundedRectBackground(4f)

        val columnLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        addView(columnLayout)

//        imageView = ImageView(context).apply {
//            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dpToPx(112f))
//            scaleType = ImageView.ScaleType.FIT_CENTER
//            load(item.imageUrl)
//        }
//        columnLayout.addView(imageView)

        addSpacer(columnLayout, 6f)

        nameTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setHorizontalPadding(12f)
            }
            text = "item.name"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.BLACK)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        columnLayout.addView(nameTextView)

        addSpacer(columnLayout, 8f)

        val priceContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                setHorizontalPadding(12f)
            }
        }
        columnLayout.addView(priceContainer)

        val priceColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }
        priceContainer.addView(priceColumn)

//        if (item.oldPrice.isNotEmpty()) {
//            oldPriceTextView = TextView(context).apply {
//                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
//                text = item.oldPrice
//                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
//                setTextColor(Color.parseColor("#535862"))
//                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
//            }
//            priceColumn.addView(oldPriceTextView)
//        }

        priceTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            text = "item.price"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(Color.BLACK)
            setTypeface(typeface, Typeface.BOLD)
        }
        priceColumn.addView(priceTextView)
    }

    private fun addSpacer(parent: ViewGroup, heightDp: Float) {
        parent.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dpToPx(heightDp))
        })
    }

    private fun LinearLayout.LayoutParams.setHorizontalPadding(paddingDp: Float) {
        val paddingPx = dpToPx(paddingDp)
        setMargins(paddingPx, 0, paddingPx, 0)
    }

    private fun createRoundedRectBackground(cornerRadiusDp: Float): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusDp * resources.displayMetrics.density
            setColor(Color.WHITE)
        }
    }
}

fun View.dpToPx(dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics
    ).toInt()
}
