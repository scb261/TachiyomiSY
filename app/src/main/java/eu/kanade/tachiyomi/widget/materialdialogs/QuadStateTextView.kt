package eu.kanade.tachiyomi.widget.materialdialogs

import android.content.Context
// import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.getThemeColor

// XZM -->
import android.graphics.PorterDuff
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
// XZM <--

class QuadStateTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatTextView(context, attrs) {

    var state: State = State.UNCHECKED
        set(value) {
            field = value
            updateDrawable()
        }

    private fun updateDrawable() {
        val drawableStartId = when (state) {
            State.UNCHECKED -> R.drawable.ic_check_box_outline_blank_24dp
            State.INDETERMINATE -> R.drawable.ic_indeterminate_check_box_24dp
            State.CHECKED -> R.drawable.ic_check_box_24dp
            State.INVERSED -> R.drawable.ic_check_box_x_24dp
        }

        val tint = if (state == State.UNCHECKED) {
            context.getThemeColor(R.attr.colorControlNormal)
        } else {
            context.getThemeColor(R.attr.colorAccent)
        }

        // compoundDrawableTintList from upstream isn't supported in API < 23
        val drawable = ContextCompat.getDrawable(context, drawableStartId)!!
        DrawableCompat.setTint(drawable, tint)
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_ATOP)

        setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
    }

    enum class State {
        UNCHECKED,
        INDETERMINATE,
        CHECKED,
        INVERSED,
        ;
    }
}
