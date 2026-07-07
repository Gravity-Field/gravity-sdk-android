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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil3.load

@SuppressLint("ViewConstructor")
class ProductView(context: Context, slot: Slot) : FrameLayout(context) {

    private val imageContainer: FrameLayout
    private val nameTextView: TextView
    private val chipRow: LinearLayout

    init {
        val imageUrl = slot.item["image_url"] as? String
        val name = slot.item["name"] as? String ?: ""
        val price = slot.item["price"] as? String ?: ""
        val inStock = slot.item["in_stock"] as? Boolean ?: false

        layoutParams = LayoutParams(dpToPx(164f), dpToPx(250f))

        val columnLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(MATCH_PARENT, dpToPx(250f))
        }
        addView(columnLayout)

        imageContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dpToPx(158f))
            background = roundedRect(cornerDp = 16f, fillColor = Color.WHITE)
            clipToOutline = true
        }
        columnLayout.addView(imageContainer)

        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        imageContainer.addView(imageView)
        imageView.load(imageUrl)

        val borderOverlay = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            background = roundedRect(cornerDp = 16f, fillColor = Color.TRANSPARENT, strokeDp = 1.5f, strokeColor = BORDER_COLOR)
        }
        imageContainer.addView(borderOverlay)

        addSpacer(columnLayout, 8f)

        nameTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            text = name
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.BLACK)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        columnLayout.addView(nameTextView)

        addSpacer(columnLayout, 8f)

        val priceTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            text = "$price ₽"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(if (inStock) PRICE_IN_STOCK_COLOR else PRICE_OUT_OF_STOCK_COLOR)
            setTypeface(typeface, Typeface.BOLD)
        }

        val cartIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(18f), dpToPx(18f)).apply {
                marginStart = dpToPx(8f)
            }
            setImageResource(R.drawable.ic_cart)
            setColorFilter(CART_COLOR)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
            background = roundedRect(cornerDp = 12f, fillColor = if (inStock) CHIP_IN_STOCK_COLOR else CHIP_OUT_OF_STOCK_COLOR)
            setPadding(dpToPx(10f), dpToPx(4f), dpToPx(10f), dpToPx(4f))
            addView(priceTextView)
            addView(cartIcon)
        }

        chipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            addView(chip)
            addView(View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT).apply { weight = 1f }
            })
        }
        columnLayout.addView(chipRow)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val cardWidth = dpToPx(164f)
        val cardHeight = dpToPx(250f)

        val widthSpec = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY)
        val unspecifiedSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        nameTextView.measure(widthSpec, unspecifiedSpec)
        chipRow.measure(widthSpec, unspecifiedSpec)

        val reserved = dpToPx(8f) * 2 + nameTextView.measuredHeight + chipRow.measuredHeight
        imageContainer.layoutParams.height = (cardHeight - reserved).coerceAtLeast(0)

        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY))
    }

    private fun addSpacer(parent: ViewGroup, heightDp: Float) {
        parent.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dpToPx(heightDp))
        })
    }

    private fun roundedRect(
        cornerDp: Float,
        fillColor: Int,
        strokeDp: Float = 0f,
        strokeColor: Int = Color.TRANSPARENT,
    ): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerDp * resources.displayMetrics.density
            setColor(fillColor)
            if (strokeDp > 0f) {
                setStroke(dpToPx(strokeDp), strokeColor)
            }
        }
    }

    private companion object {
        val BORDER_COLOR = Color.rgb(224, 224, 224)
        val PRICE_IN_STOCK_COLOR = Color.rgb(255, 77, 77)
        val PRICE_OUT_OF_STOCK_COLOR = Color.rgb(117, 117, 117)
        val CHIP_IN_STOCK_COLOR = Color.rgb(255, 230, 230)
        val CHIP_OUT_OF_STOCK_COLOR = Color.rgb(237, 237, 237)
        val CART_COLOR = Color.rgb(255, 59, 48)
    }
}

fun View.dpToPx(dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics
    ).toInt()
}
