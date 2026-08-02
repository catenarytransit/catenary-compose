package com.catenarymaps.catenary

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
public val hourglass_arrow_down: ImageVector
    get() {
        if (_hourglass_arrow_down != null) {
            return _hourglass_arrow_down!!
        }
        _hourglass_arrow_down =
            ImageVector.Builder(
                name = "hourglass_arrow_down",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
                .apply {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.Companion.NonZero,
                    ) {
                        moveTo(8.5f, 11f)
                        quadToRelative(1.05f, 0f, 1.78f, -0.73f)
                        reflectiveQuadTo(11f, 8.5f)
                        verticalLineTo(6f)
                        horizontalLineTo(6f)
                        verticalLineTo(8.5f)
                        quadToRelative(0f, 1.05f, 0.73f, 1.77f)
                        reflectiveQuadTo(8.5f, 11f)
                        close()
                        moveTo(6f, 18f)
                        horizontalLineToRelative(5f)
                        verticalLineTo(15.5f)
                        quadToRelative(0f, -1.05f, -0.72f, -1.78f)
                        reflectiveQuadTo(8.5f, 13f)
                        reflectiveQuadTo(6.73f, 13.73f)
                        reflectiveQuadTo(6f, 15.5f)
                        verticalLineTo(18f)
                        close()
                        moveTo(2.5f, 20f)
                        verticalLineTo(18f)
                        horizontalLineTo(4f)
                        verticalLineTo(15.5f)
                        quadTo(4f, 14.45f, 4.45f, 13.55f)
                        reflectiveQuadTo(5.7f, 12f)
                        quadTo(4.9f, 11.35f, 4.45f, 10.45f)
                        reflectiveQuadTo(4f, 8.5f)
                        verticalLineTo(6f)
                        horizontalLineTo(2.5f)
                        verticalLineTo(4f)
                        horizontalLineToRelative(12f)
                        verticalLineTo(6f)
                        horizontalLineTo(13f)
                        verticalLineTo(8.5f)
                        quadToRelative(0f, 1.05f, -0.45f, 1.95f)
                        reflectiveQuadTo(11.3f, 12f)
                        quadToRelative(0.8f, 0.65f, 1.25f, 1.55f)
                        reflectiveQuadTo(13f, 15.5f)
                        verticalLineTo(18f)
                        horizontalLineToRelative(1.5f)
                        verticalLineToRelative(2f)
                        horizontalLineTo(2.5f)
                        close()
                        moveToRelative(17f, 0f)
                        lineTo(16f, 16.5f)
                        lineToRelative(1.43f, -1.4f)
                        lineToRelative(1.07f, 1.08f)
                        verticalLineTo(4f)
                        horizontalLineToRelative(2f)
                        verticalLineTo(16.2f)
                        lineToRelative(1.1f, -1.1f)
                        lineTo(23f, 16.5f)
                        lineTo(19.5f, 20f)
                        close()
                        moveTo(8.5f, 6f)
                        close()
                        moveToRelative(0f, 12f)
                        close()
                    }
                }
                .build()
        return _hourglass_arrow_down!!
    }

private var _hourglass_arrow_down: ImageVector? = null
