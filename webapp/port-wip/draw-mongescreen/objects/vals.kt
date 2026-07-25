package draw.mongescreen.objects

import androidx.compose.ui.graphics.Color

const val ELLIPSE_EPS = 1e-5f
const val ELLIPSE_REL_DEGENERATE_EPS = 1e-4f
const val POINT_STROKE_WEIGHT = 0.5f
public val EligiblePointGlow = Color(0xFF5B9BF5).copy(alpha = 0.15f)
public val EligiblePointRing = Color(0xFF5B9BF5).copy(alpha = 0.70f)

const val SELECTION_HALO_EXTRA_PX = 4f
const val HOVER_HALO_EXTRA_PX = 2f
const val PENDING_HALO_EXTRA_PX = 6f