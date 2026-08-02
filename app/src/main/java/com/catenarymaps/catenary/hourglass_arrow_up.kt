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
public val hourglass_arrow_up: ImageVector
    get() {
        if (_hourglass_arrow_up != null) {
            return _hourglass_arrow_up!!
        }
        _hourglass_arrow_up =
            ImageVector.Builder(
                name = "hourglass_arrow_up",
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
                        moveToRelative(16f, 0f)
                        verticalLineTo(7.8f)
                        lineTo(17.4f, 8.9f)
                        lineTo(16f, 7.5f)
                        lineTo(19.5f, 4f)
                        lineTo(23f, 7.5f)
                        lineTo(21.58f, 8.9f)
                        lineTo(20.5f, 7.82f)
                        verticalLineTo(20f)
                        horizontalLineToRelative(-2f)
                        close()
                        moveTo(8.5f, 6f)
                        close()
                        moveToRelative(0f, 12f)
                        close()
                    }
                }
                .build()
        return _hourglass_arrow_up!!
    }

private var _hourglass_arrow_up: ImageVector? = null
