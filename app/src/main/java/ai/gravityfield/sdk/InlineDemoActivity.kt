package ai.gravityfield.sdk

import ai.gravityfield.gravity_sdk.GravitySDK
import ai.gravityfield.gravity_sdk.models.ContextType
import ai.gravityfield.gravity_sdk.models.PageContext
import ai.gravityfield.gravity_sdk.ui.GravityInlineView
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class InlineDemoActivity : AppCompatActivity() {
    val pageContext = PageContext(
        type = ContextType.HOMEPAGE,
        data = emptyList(),
        location = "recycler_view",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inline_demo)

        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = RecyclerAdapter(pageContext)
    }

    override fun onDestroy() {
        GravitySDK.instance.resetInlineViewCache(
            "homepage_inline_banner",
            pageContext
        )
        super.onDestroy()
    }
}

class RecyclerAdapter(
    val pageContext: PageContext,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_INLINE = 0
        private const val TYPE_TEXT = 1
        private const val TYPE_RECTANGLE = 2
    }

    override fun getItemCount(): Int = 15

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            TYPE_INLINE
        } else if (position % 2 == 0)
            TYPE_RECTANGLE
        else
            TYPE_TEXT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_INLINE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_inline, parent, false)
                InlineViewHolder(view as GravityInlineView)
            }

            TYPE_TEXT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_text, parent, false)
                TextViewHolder(view)
            }

            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_rect, parent, false)
                RectangleViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TextViewHolder -> holder.bind(position)
            is InlineViewHolder -> holder.bind(pageContext)
        }
    }

    inner class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView: TextView = view.findViewById(R.id.textView)

        fun bind(position: Int) {
            textView.text = "Title $position"
        }
    }

    inner class RectangleViewHolder(view: View) : RecyclerView.ViewHolder(view)
    inner class InlineViewHolder(val view: GravityInlineView) : RecyclerView.ViewHolder(view) {
        fun bind(pageContext: PageContext) {
            view.init(pageContext)
        }
    }
}
