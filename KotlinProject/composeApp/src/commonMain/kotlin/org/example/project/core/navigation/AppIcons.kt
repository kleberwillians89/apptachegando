package org.example.project.core.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal val HomeIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Home",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(10f, 20f)
            verticalLineTo(14f)
            horizontalLineTo(14f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(12f)
            horizontalLineTo(22f)
            lineTo(12f, 3f)
            lineTo(2f, 12f)
            horizontalLineTo(5f)
            verticalLineTo(20f)
            close()
        }
    }.build()

internal val LocationOnIcon: ImageVector
    get() = ImageVector.Builder(
        name = "LocationOn",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 2f)
            curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
            curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
            curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
            curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
            close()
            moveTo(12f, 11.5f)
            curveTo(10.62f, 11.5f, 9.5f, 10.38f, 9.5f, 9f)
            curveTo(9.5f, 7.62f, 10.62f, 6.5f, 12f, 6.5f)
            curveTo(13.38f, 6.5f, 14.5f, 7.62f, 14.5f, 9f)
            curveTo(14.5f, 10.38f, 13.38f, 11.5f, 12f, 11.5f)
            close()
        }
    }.build()

internal val SearchIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Search",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(15.5f, 14f)
            horizontalLineToRelative(-0.79f)
            lineToRelative(-0.28f, -0.27f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
            reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
            curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
            lineTo(14f, 14.71f)
            verticalLineTo(15.5f)
            lineTo(19f, 20.49f)
            lineTo(20.49f, 19f)
            lineTo(15.5f, 14f)
            close()
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
            reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
            reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
            close()
        }
    }.build()

internal val NotificationsIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Notifications",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 22f)
            curveTo(13.1f, 22f, 14f, 21.1f, 14f, 20f)
            horizontalLineTo(10f)
            curveTo(10f, 21.1f, 10.9f, 22f, 12f, 22f)
            close()
            moveTo(18f, 16f)
            verticalLineTo(11f)
            curveTo(18f, 7.93f, 16.36f, 5.36f, 13.5f, 4.68f)
            verticalLineTo(4f)
            curveTo(13.5f, 3.17f, 12.83f, 2.5f, 12f, 2.5f)
            curveTo(11.17f, 2.5f, 10.5f, 3.17f, 10.5f, 4f)
            verticalLineTo(4.68f)
            curveTo(7.63f, 5.36f, 6f, 7.92f, 6f, 11f)
            verticalLineTo(16f)
            lineTo(4f, 18f)
            verticalLineTo(19f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            lineTo(18f, 16f)
            close()
        }
    }.build()

internal val PersonIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Person",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 12f)
            curveTo(14.21f, 12f, 16f, 10.21f, 16f, 8f)
            reflectiveCurveTo(14.21f, 4f, 12f, 4f)
            reflectiveCurveTo(8f, 5.79f, 8f, 8f)
            reflectiveCurveTo(9.79f, 12f, 12f, 12f)
            close()
            moveTo(12f, 14f)
            curveTo(9.33f, 14f, 4f, 15.34f, 4f, 18f)
            verticalLineTo(20f)
            horizontalLineTo(20f)
            verticalLineTo(18f)
            curveTo(20f, 15.34f, 14.67f, 14f, 12f, 14f)
            close()
        }
    }.build()

internal val RouteIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Route",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(19f, 15f)
            curveTo(18.17f, 15f, 17.5f, 15.67f, 17.5f, 16.5f)
            verticalLineTo(17.5f)
            curveTo(17.5f, 17.5f, 16.5f, 18.5f, 15.5f, 18.5f)
            curveTo(14.5f, 18.5f, 13.5f, 17.5f, 13.5f, 16.5f)
            verticalLineTo(7.5f)
            curveTo(13.5f, 5.57f, 11.93f, 4f, 10f, 4f)
            curveTo(8.07f, 4f, 6.5f, 5.57f, 6.5f, 7.5f)
            verticalLineTo(8.09f)
            curveTo(5.08f, 8.42f, 4f, 9.69f, 4f, 11.23f)
            curveTo(4f, 13.16f, 5.57f, 14.73f, 7.5f, 14.73f)
            curveTo(9.43f, 14.73f, 11f, 13.16f, 11f, 11.23f)
            curveTo(11f, 9.69f, 9.92f, 8.42f, 8.5f, 8.09f)
            verticalLineTo(7.5f)
            curveTo(8.5f, 6.67f, 9.17f, 6f, 10f, 6f)
            curveTo(10.83f, 6f, 11.5f, 6.67f, 11.5f, 7.5f)
            verticalLineTo(16.5f)
            curveTo(11.5f, 18.43f, 13.07f, 20f, 15f, 20f)
            curveTo(16.93f, 20f, 18.5f, 18.43f, 18.5f, 16.5f)
            curveTo(18.5f, 15.67f, 19.17f, 15f, 20f, 15f)
            curveTo(20.83f, 15f, 21.5f, 15.67f, 21.5f, 16.5f)
            curveTo(21.5f, 17.33f, 20.83f, 18f, 20f, 18f)
            verticalLineTo(20f)
            curveTo(20f, 21.1f, 19.1f, 22f, 18f, 22f)
            curveTo(16.9f, 22f, 16f, 21.1f, 16f, 20f)
            moveTo(7.5f, 12.73f)
            curveTo(6.67f, 12.73f, 6f, 12.06f, 6f, 11.23f)
            curveTo(6f, 10.4f, 6.67f, 9.73f, 7.5f, 9.73f)
            curveTo(8.33f, 9.73f, 9f, 10.4f, 9f, 11.23f)
            curveTo(9f, 12.06f, 8.33f, 12.73f, 7.5f, 12.73f)
            close()
        }
    }.build()

internal val HeatmapIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Heatmap",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveTo(6.48f, 22f, 12f, 22f)
            reflectiveCurveTo(22f, 17.52f, 22f, 12f)
            reflectiveCurveTo(17.52f, 2f, 12f, 2f)
            close()
            moveTo(12f, 20f)
            curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
            reflectiveCurveTo(7.58f, 4f, 12f, 4f)
            reflectiveCurveTo(20f, 7.58f, 20f, 12f)
            reflectiveCurveTo(16.42f, 20f, 12f, 20f)
            close()
            moveTo(12f, 6f)
            curveTo(8.69f, 6f, 6f, 8.69f, 6f, 12f)
            reflectiveCurveTo(8.69f, 18f, 12f, 18f)
            reflectiveCurveTo(18f, 15.31f, 18f, 12f)
            reflectiveCurveTo(15.31f, 6f, 12f, 6f)
            close()
            moveTo(12f, 16f)
            curveTo(9.79f, 16f, 8f, 14.21f, 8f, 12f)
            reflectiveCurveTo(9.79f, 8f, 12f, 8f)
            reflectiveCurveTo(16f, 9.79f, 16f, 12f)
            reflectiveCurveTo(14.21f, 16f, 12f, 16f)
            close()
            moveTo(12f, 10f)
            curveTo(10.9f, 10f, 10f, 10.9f, 10f, 12f)
            reflectiveCurveTo(10.9f, 14f, 12f, 14f)
            reflectiveCurveTo(14f, 13.1f, 14f, 12f)
            reflectiveCurveTo(13.1f, 10f, 12f, 10f)
            close()
        }
    }.build()

internal val SettingsIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Settings",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            stroke = null,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(19.14f, 12.94f)
            curveTo(19.18f, 12.64f, 19.2f, 12.33f, 19.2f, 12f)
            curveTo(19.2f, 11.68f, 19.18f, 11.36f, 19.13f, 11.06f)
            lineTo(21.16f, 9.48f)
            curveTo(21.34f, 9.34f, 21.39f, 9.07f, 21.28f, 8.87f)
            lineTo(19.36f, 5.55f)
            curveTo(19.24f, 5.33f, 18.99f, 5.26f, 18.77f, 5.33f)
            lineTo(16.38f, 6.29f)
            curveTo(15.88f, 5.91f, 15.35f, 5.59f, 14.76f, 5.35f)
            lineTo(14.4f, 2.81f)
            curveTo(14.36f, 2.57f, 14.16f, 2.4f, 13.92f, 2.4f)
            horizontalLineTo(10.08f)
            curveTo(9.84f, 2.4f, 9.65f, 2.57f, 9.61f, 2.81f)
            lineTo(9.25f, 5.35f)
            curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
            lineTo(5.24f, 5.33f)
            curveTo(5.02f, 5.25f, 4.77f, 5.33f, 4.65f, 5.55f)
            lineTo(2.74f, 8.87f)
            curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
            lineTo(4.89f, 11.06f)
            curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12f)
            curveTo(4.8f, 12.31f, 4.82f, 12.64f, 4.87f, 12.94f)
            lineTo(2.85f, 14.52f)
            curveTo(2.67f, 14.66f, 2.62f, 14.93f, 2.73f, 15.13f)
            lineTo(4.65f, 18.45f)
            curveTo(4.77f, 18.67f, 5.02f, 18.74f, 5.24f, 18.67f)
            lineTo(7.63f, 17.71f)
            curveTo(8.13f, 18.09f, 8.66f, 18.41f, 9.25f, 18.65f)
            lineTo(9.61f, 21.19f)
            curveTo(9.65f, 21.43f, 9.84f, 21.6f, 10.08f, 21.6f)
            horizontalLineTo(13.92f)
            curveTo(14.16f, 21.6f, 14.36f, 21.43f, 14.39f, 21.19f)
            lineTo(14.75f, 18.65f)
            curveTo(15.34f, 18.41f, 15.88f, 18.09f, 16.37f, 17.71f)
            lineTo(18.76f, 18.67f)
            curveTo(18.98f, 18.75f, 19.23f, 18.67f, 19.35f, 18.45f)
            lineTo(21.27f, 15.13f)
            curveTo(21.39f, 14.91f, 21.34f, 14.66f, 21.15f, 14.52f)
            lineTo(19.14f, 12.94f)
            close()
            moveTo(12f, 15.6f)
            curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
            curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
            curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
            curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
            close()
        }
    }.build()
