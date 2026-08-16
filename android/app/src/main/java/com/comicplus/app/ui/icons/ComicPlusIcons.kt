/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Local subset derived from AndroidX Material Icons 1.7.8.
// Keeping only the vectors Comic Plus uses avoids the unmaintained icon artifacts.

package com.comicplus.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.DefaultFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object ComicPlusIcons {
    object Filled {
        val Favorite: ImageVector
            get() {
                if (_favorite != null) {
                    return _favorite!!
                }
                _favorite = materialIcon(name = "Filled.Favorite") {
                    materialPath {
                        moveTo(12.0f, 21.35f)
                        lineToRelative(-1.45f, -1.32f)
                        curveTo(5.4f, 15.36f, 2.0f, 12.28f, 2.0f, 8.5f)
                        curveTo(2.0f, 5.42f, 4.42f, 3.0f, 7.5f, 3.0f)
                        curveToRelative(1.74f, 0.0f, 3.41f, 0.81f, 4.5f, 2.09f)
                        curveTo(13.09f, 3.81f, 14.76f, 3.0f, 16.5f, 3.0f)
                        curveTo(19.58f, 3.0f, 22.0f, 5.42f, 22.0f, 8.5f)
                        curveToRelative(0.0f, 3.78f, -3.4f, 6.86f, -8.55f, 11.54f)
                        lineTo(12.0f, 21.35f)
                        close()
                    }
                }
                return _favorite!!
            }

        private var _favorite: ImageVector? = null

        val Category: ImageVector
            get() {
                if (_category != null) {
                    return _category!!
                }
                _category = materialIcon(name = "Filled.Category") {
                    materialPath {
                        moveTo(12.0f, 2.0f)
                        lineToRelative(-5.5f, 9.0f)
                        horizontalLineToRelative(11.0f)
                        close()
                    }
                    materialPath {
                        moveTo(17.5f, 17.5f)
                        moveToRelative(-4.5f, 0.0f)
                        arcToRelative(4.5f, 4.5f, 0.0f, true, true, 9.0f, 0.0f)
                        arcToRelative(4.5f, 4.5f, 0.0f, true, true, -9.0f, 0.0f)
                    }
                    materialPath {
                        moveTo(3.0f, 13.5f)
                        horizontalLineToRelative(8.0f)
                        verticalLineToRelative(8.0f)
                        horizontalLineTo(3.0f)
                        close()
                    }
                }
                return _category!!
            }

        private var _category: ImageVector? = null

        val EmojiEvents: ImageVector
            get() {
                if (_emojiEvents != null) {
                    return _emojiEvents!!
                }
                _emojiEvents = materialIcon(name = "Filled.EmojiEvents") {
                    materialPath {
                        moveTo(19.0f, 5.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineTo(3.0f)
                        horizontalLineTo(7.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineTo(5.0f)
                        curveTo(3.9f, 5.0f, 3.0f, 5.9f, 3.0f, 7.0f)
                        verticalLineToRelative(1.0f)
                        curveToRelative(0.0f, 2.55f, 1.92f, 4.63f, 4.39f, 4.94f)
                        curveToRelative(0.63f, 1.5f, 1.98f, 2.63f, 3.61f, 2.96f)
                        verticalLineTo(19.0f)
                        horizontalLineTo(7.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(10.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineToRelative(-4.0f)
                        verticalLineToRelative(-3.1f)
                        curveToRelative(1.63f, -0.33f, 2.98f, -1.46f, 3.61f, -2.96f)
                        curveTo(19.08f, 12.63f, 21.0f, 10.55f, 21.0f, 8.0f)
                        verticalLineTo(7.0f)
                        curveTo(21.0f, 5.9f, 20.1f, 5.0f, 19.0f, 5.0f)
                        close()
                        moveTo(5.0f, 8.0f)
                        verticalLineTo(7.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(3.82f)
                        curveTo(5.84f, 10.4f, 5.0f, 9.3f, 5.0f, 8.0f)
                        close()
                        moveTo(19.0f, 8.0f)
                        curveToRelative(0.0f, 1.3f, -0.84f, 2.4f, -2.0f, 2.82f)
                        verticalLineTo(7.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineTo(8.0f)
                        close()
                    }
                }
                return _emojiEvents!!
            }

        private var _emojiEvents: ImageVector? = null

        val Home: ImageVector
            get() {
                if (_home != null) {
                    return _home!!
                }
                _home = materialIcon(name = "Filled.Home") {
                    materialPath {
                        moveTo(10.0f, 20.0f)
                        verticalLineToRelative(-6.0f)
                        horizontalLineToRelative(4.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(5.0f)
                        verticalLineToRelative(-8.0f)
                        horizontalLineToRelative(3.0f)
                        lineTo(12.0f, 3.0f)
                        lineTo(2.0f, 12.0f)
                        horizontalLineToRelative(3.0f)
                        verticalLineToRelative(8.0f)
                        close()
                    }
                }
                return _home!!
            }

        private var _home: ImageVector? = null

        val Settings: ImageVector
            get() {
                if (_settings != null) {
                    return _settings!!
                }
                _settings = materialIcon(name = "Filled.Settings") {
                    materialPath {
                        moveTo(19.14f, 12.94f)
                        curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                        curveToRelative(0.0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
                        lineToRelative(2.03f, -1.58f)
                        curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                        lineToRelative(-1.92f, -3.32f)
                        curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                        lineToRelative(-2.39f, 0.96f)
                        curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                        lineTo(14.4f, 2.81f)
                        curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                        horizontalLineToRelative(-3.84f)
                        curveToRelative(-0.24f, 0.0f, -0.43f, 0.17f, -0.47f, 0.41f)
                        lineTo(9.25f, 5.35f)
                        curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
                        lineTo(5.24f, 5.33f)
                        curveToRelative(-0.22f, -0.08f, -0.47f, 0.0f, -0.59f, 0.22f)
                        lineTo(2.74f, 8.87f)
                        curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
                        lineToRelative(2.03f, 1.58f)
                        curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12.0f)
                        reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
                        lineToRelative(-2.03f, 1.58f)
                        curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                        lineToRelative(1.92f, 3.32f)
                        curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                        lineToRelative(2.39f, -0.96f)
                        curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                        lineToRelative(0.36f, 2.54f)
                        curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                        horizontalLineToRelative(3.84f)
                        curveToRelative(0.24f, 0.0f, 0.44f, -0.17f, 0.47f, -0.41f)
                        lineToRelative(0.36f, -2.54f)
                        curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                        lineToRelative(2.39f, 0.96f)
                        curveToRelative(0.22f, 0.08f, 0.47f, 0.0f, 0.59f, -0.22f)
                        lineToRelative(1.92f, -3.32f)
                        curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                        lineTo(19.14f, 12.94f)
                        close()
                        moveTo(12.0f, 15.6f)
                        curveToRelative(-1.98f, 0.0f, -3.6f, -1.62f, -3.6f, -3.6f)
                        reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
                        reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
                        reflectiveCurveTo(13.98f, 15.6f, 12.0f, 15.6f)
                        close()
                    }
                }
                return _settings!!
            }

        private var _settings: ImageVector? = null
    }

    object Outlined {
        val ChevronRight: ImageVector
            get() {
                if (_chevronRight != null) {
                    return _chevronRight!!
                }
                _chevronRight = materialIcon(name = "Outlined.ChevronRight") {
                    materialPath {
                        moveTo(10.0f, 6.0f)
                        lineTo(8.59f, 7.41f)
                        lineTo(13.17f, 12.0f)
                        lineToRelative(-4.58f, 4.59f)
                        lineTo(10.0f, 18.0f)
                        lineToRelative(6.0f, -6.0f)
                        lineToRelative(-6.0f, -6.0f)
                        close()
                    }
                }
                return _chevronRight!!
            }

        private var _chevronRight: ImageVector? = null

        val Close: ImageVector
            get() {
                if (_close != null) {
                    return _close!!
                }
                _close = materialIcon(name = "Outlined.Close") {
                    materialPath {
                        moveTo(19.0f, 6.41f)
                        lineTo(17.59f, 5.0f)
                        lineTo(12.0f, 10.59f)
                        lineTo(6.41f, 5.0f)
                        lineTo(5.0f, 6.41f)
                        lineTo(10.59f, 12.0f)
                        lineTo(5.0f, 17.59f)
                        lineTo(6.41f, 19.0f)
                        lineTo(12.0f, 13.41f)
                        lineTo(17.59f, 19.0f)
                        lineTo(19.0f, 17.59f)
                        lineTo(13.41f, 12.0f)
                        lineTo(19.0f, 6.41f)
                        close()
                    }
                }
                return _close!!
            }

        private var _close: ImageVector? = null

        val FavoriteBorder: ImageVector
            get() {
                if (_favoriteBorder != null) {
                    return _favoriteBorder!!
                }
                _favoriteBorder = materialIcon(name = "Outlined.FavoriteBorder") {
                    materialPath {
                        moveTo(16.5f, 3.0f)
                        curveToRelative(-1.74f, 0.0f, -3.41f, 0.81f, -4.5f, 2.09f)
                        curveTo(10.91f, 3.81f, 9.24f, 3.0f, 7.5f, 3.0f)
                        curveTo(4.42f, 3.0f, 2.0f, 5.42f, 2.0f, 8.5f)
                        curveToRelative(0.0f, 3.78f, 3.4f, 6.86f, 8.55f, 11.54f)
                        lineTo(12.0f, 21.35f)
                        lineToRelative(1.45f, -1.32f)
                        curveTo(18.6f, 15.36f, 22.0f, 12.28f, 22.0f, 8.5f)
                        curveTo(22.0f, 5.42f, 19.58f, 3.0f, 16.5f, 3.0f)
                        close()
                        moveTo(12.1f, 18.55f)
                        lineToRelative(-0.1f, 0.1f)
                        lineToRelative(-0.1f, -0.1f)
                        curveTo(7.14f, 14.24f, 4.0f, 11.39f, 4.0f, 8.5f)
                        curveTo(4.0f, 6.5f, 5.5f, 5.0f, 7.5f, 5.0f)
                        curveToRelative(1.54f, 0.0f, 3.04f, 0.99f, 3.57f, 2.36f)
                        horizontalLineToRelative(1.87f)
                        curveTo(13.46f, 5.99f, 14.96f, 5.0f, 16.5f, 5.0f)
                        curveToRelative(2.0f, 0.0f, 3.5f, 1.5f, 3.5f, 3.5f)
                        curveToRelative(0.0f, 2.89f, -3.14f, 5.74f, -7.9f, 10.05f)
                        close()
                    }
                }
                return _favoriteBorder!!
            }

        private var _favoriteBorder: ImageVector? = null

        val Search: ImageVector
            get() {
                if (_search != null) {
                    return _search!!
                }
                _search = materialIcon(name = "Outlined.Search") {
                    materialPath {
                        moveTo(15.5f, 14.0f)
                        horizontalLineToRelative(-0.79f)
                        lineToRelative(-0.28f, -0.27f)
                        curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
                        curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
                        reflectiveCurveTo(3.0f, 5.91f, 3.0f, 9.5f)
                        reflectiveCurveTo(5.91f, 16.0f, 9.5f, 16.0f)
                        curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
                        lineToRelative(0.27f, 0.28f)
                        verticalLineToRelative(0.79f)
                        lineToRelative(5.0f, 4.99f)
                        lineTo(20.49f, 19.0f)
                        lineToRelative(-4.99f, -5.0f)
                        close()
                        moveTo(9.5f, 14.0f)
                        curveTo(7.01f, 14.0f, 5.0f, 11.99f, 5.0f, 9.5f)
                        reflectiveCurveTo(7.01f, 5.0f, 9.5f, 5.0f)
                        reflectiveCurveTo(14.0f, 7.01f, 14.0f, 9.5f)
                        reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                        close()
                    }
                }
                return _search!!
            }

        private var _search: ImageVector? = null

        val Explore: ImageVector
            get() {
                if (_explore != null) {
                    return _explore!!
                }
                _explore = materialIcon(name = "Outlined.Explore") {
                    materialPath {
                        moveTo(12.0f, 2.0f)
                        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                        close()
                        moveTo(12.0f, 20.0f)
                        curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
                        reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
                        reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
                        reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
                        close()
                        moveTo(6.5f, 17.5f)
                        lineToRelative(7.51f, -3.49f)
                        lineTo(17.5f, 6.5f)
                        lineTo(9.99f, 9.99f)
                        lineTo(6.5f, 17.5f)
                        close()
                        moveTo(12.0f, 10.9f)
                        curveToRelative(0.61f, 0.0f, 1.1f, 0.49f, 1.1f, 1.1f)
                        reflectiveCurveToRelative(-0.49f, 1.1f, -1.1f, 1.1f)
                        reflectiveCurveToRelative(-1.1f, -0.49f, -1.1f, -1.1f)
                        reflectiveCurveToRelative(0.49f, -1.1f, 1.1f, -1.1f)
                        close()
                    }
                }
                return _explore!!
            }

        private var _explore: ImageVector? = null

        val Share: ImageVector
            get() {
                if (_share != null) {
                    return _share!!
                }
                _share = materialIcon(name = "Outlined.Share") {
                    materialPath {
                        moveTo(18.0f, 16.08f)
                        curveToRelative(-0.76f, 0.0f, -1.44f, 0.3f, -1.96f, 0.77f)
                        lineTo(8.91f, 12.7f)
                        curveToRelative(0.05f, -0.23f, 0.09f, -0.46f, 0.09f, -0.7f)
                        reflectiveCurveToRelative(-0.04f, -0.47f, -0.09f, -0.7f)
                        lineToRelative(7.05f, -4.11f)
                        curveToRelative(0.54f, 0.5f, 1.25f, 0.81f, 2.04f, 0.81f)
                        curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                        reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                        reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
                        curveToRelative(0.0f, 0.24f, 0.04f, 0.47f, 0.09f, 0.7f)
                        lineTo(8.04f, 9.81f)
                        curveTo(7.5f, 9.31f, 6.79f, 9.0f, 6.0f, 9.0f)
                        curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                        reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                        curveToRelative(0.79f, 0.0f, 1.5f, -0.31f, 2.04f, -0.81f)
                        lineToRelative(7.12f, 4.16f)
                        curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                        curveToRelative(0.0f, 1.61f, 1.31f, 2.92f, 2.92f, 2.92f)
                        reflectiveCurveToRelative(2.92f, -1.31f, 2.92f, -2.92f)
                        curveToRelative(0.0f, -1.61f, -1.31f, -2.92f, -2.92f, -2.92f)
                        close()
                        moveTo(18.0f, 4.0f)
                        curveToRelative(0.55f, 0.0f, 1.0f, 0.45f, 1.0f, 1.0f)
                        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                        reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                        close()
                        moveTo(6.0f, 13.0f)
                        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                        close()
                        moveTo(18.0f, 20.02f)
                        curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                        reflectiveCurveToRelative(0.45f, -1.0f, 1.0f, -1.0f)
                        reflectiveCurveToRelative(1.0f, 0.45f, 1.0f, 1.0f)
                        reflectiveCurveToRelative(-0.45f, 1.0f, -1.0f, 1.0f)
                        close()
                    }
                }
                return _share!!
            }

        private var _share: ImageVector? = null

        val Download: ImageVector
            get() {
                if (_download != null) {
                    return _download!!
                }
                _download = materialIcon(name = "Outlined.Download") {
                    materialPath {
                        moveTo(19.0f, 9.0f)
                        horizontalLineToRelative(-4.0f)
                        lineTo(15.0f, 3.0f)
                        lineTo(9.0f, 3.0f)
                        verticalLineToRelative(6.0f)
                        lineTo(5.0f, 9.0f)
                        lineToRelative(7.0f, 7.0f)
                        lineToRelative(7.0f, -7.0f)
                        close()
                        moveTo(11.0f, 11.0f)
                        lineTo(11.0f, 5.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(1.17f)
                        lineTo(12.0f, 13.17f)
                        lineTo(9.83f, 11.0f)
                        lineTo(11.0f, 11.0f)
                        close()
                        moveTo(5.0f, 18.0f)
                        horizontalLineToRelative(14.0f)
                        verticalLineToRelative(2.0f)
                        lineTo(5.0f, 20.0f)
                        close()
                    }
                }
                return _download!!
            }

        private var _download: ImageVector? = null

        val CheckCircle: ImageVector
            get() {
                if (_checkCircle != null) {
                    return _checkCircle!!
                }
                _checkCircle = materialIcon(name = "Outlined.CheckCircle") {
                    materialPath {
                        moveTo(12.0f, 2.0f)
                        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                        close()
                        moveTo(12.0f, 20.0f)
                        curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
                        reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
                        reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
                        reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
                        close()
                        moveTo(16.59f, 7.58f)
                        lineTo(10.0f, 14.17f)
                        lineToRelative(-2.59f, -2.58f)
                        lineTo(6.0f, 13.0f)
                        lineToRelative(4.0f, 4.0f)
                        lineToRelative(8.0f, -8.0f)
                        close()
                    }
                }
                return _checkCircle!!
            }

        private var _checkCircle: ImageVector? = null

        val ChatBubbleOutline: ImageVector
            get() {
                if (_chatBubbleOutline != null) {
                    return _chatBubbleOutline!!
                }
                _chatBubbleOutline = materialIcon(name = "Outlined.ChatBubbleOutline") {
                    materialPath {
                        moveTo(20.0f, 2.0f)
                        lineTo(4.0f, 2.0f)
                        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                        verticalLineToRelative(18.0f)
                        lineToRelative(4.0f, -4.0f)
                        horizontalLineToRelative(14.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        lineTo(22.0f, 4.0f)
                        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                        close()
                        moveTo(20.0f, 16.0f)
                        lineTo(6.0f, 16.0f)
                        lineToRelative(-2.0f, 2.0f)
                        lineTo(4.0f, 4.0f)
                        horizontalLineToRelative(16.0f)
                        verticalLineToRelative(12.0f)
                        close()
                    }
                }
                return _chatBubbleOutline!!
            }

        private var _chatBubbleOutline: ImageVector? = null

        val Refresh: ImageVector
            get() {
                if (_refresh != null) {
                    return _refresh!!
                }
                _refresh = materialIcon(name = "Outlined.Refresh") {
                    materialPath {
                        moveTo(17.65f, 6.35f)
                        curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
                        curveToRelative(-4.42f, 0.0f, -7.99f, 3.58f, -7.99f, 8.0f)
                        reflectiveCurveToRelative(3.57f, 8.0f, 7.99f, 8.0f)
                        curveToRelative(3.73f, 0.0f, 6.84f, -2.55f, 7.73f, -6.0f)
                        horizontalLineToRelative(-2.08f)
                        curveToRelative(-0.82f, 2.33f, -3.04f, 4.0f, -5.65f, 4.0f)
                        curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                        reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                        curveToRelative(1.66f, 0.0f, 3.14f, 0.69f, 4.22f, 1.78f)
                        lineTo(13.0f, 11.0f)
                        horizontalLineToRelative(7.0f)
                        verticalLineTo(4.0f)
                        lineToRelative(-2.35f, 2.35f)
                        close()
                    }
                }
                return _refresh!!
            }

        private var _refresh: ImageVector? = null

        val SwapVert: ImageVector
            get() {
                if (_swapVert != null) {
                    return _swapVert!!
                }
                _swapVert = materialIcon(name = "Outlined.SwapVert") {
                    materialPath {
                        moveTo(16.0f, 17.01f)
                        lineTo(16.0f, 10.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(7.01f)
                        horizontalLineToRelative(-3.0f)
                        lineTo(15.0f, 21.0f)
                        lineToRelative(4.0f, -3.99f)
                        horizontalLineToRelative(-3.0f)
                        close()
                        moveTo(9.0f, 3.0f)
                        lineTo(5.0f, 6.99f)
                        horizontalLineToRelative(3.0f)
                        lineTo(8.0f, 14.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(10.0f, 6.99f)
                        horizontalLineToRelative(3.0f)
                        lineTo(9.0f, 3.0f)
                        close()
                        moveTo(16.0f, 17.01f)
                        lineTo(16.0f, 10.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(7.01f)
                        horizontalLineToRelative(-3.0f)
                        lineTo(15.0f, 21.0f)
                        lineToRelative(4.0f, -3.99f)
                        horizontalLineToRelative(-3.0f)
                        close()
                        moveTo(9.0f, 3.0f)
                        lineTo(5.0f, 6.99f)
                        horizontalLineToRelative(3.0f)
                        lineTo(8.0f, 14.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(10.0f, 6.99f)
                        horizontalLineToRelative(3.0f)
                        lineTo(9.0f, 3.0f)
                        close()
                    }
                }
                return _swapVert!!
            }

        private var _swapVert: ImageVector? = null

        val ThumbUp: ImageVector
            get() {
                if (_thumbUp != null) {
                    return _thumbUp!!
                }
                _thumbUp = materialIcon(name = "Outlined.ThumbUp") {
                    materialPath {
                        moveTo(9.0f, 21.0f)
                        horizontalLineToRelative(9.0f)
                        curveToRelative(0.83f, 0.0f, 1.54f, -0.5f, 1.84f, -1.22f)
                        lineToRelative(3.02f, -7.05f)
                        curveToRelative(0.09f, -0.23f, 0.14f, -0.47f, 0.14f, -0.73f)
                        verticalLineToRelative(-2.0f)
                        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                        horizontalLineToRelative(-6.31f)
                        lineToRelative(0.95f, -4.57f)
                        lineToRelative(0.03f, -0.32f)
                        curveToRelative(0.0f, -0.41f, -0.17f, -0.79f, -0.44f, -1.06f)
                        lineTo(14.17f, 1.0f)
                        lineTo(7.58f, 7.59f)
                        curveTo(7.22f, 7.95f, 7.0f, 8.45f, 7.0f, 9.0f)
                        verticalLineToRelative(10.0f)
                        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                        close()
                        moveTo(9.0f, 9.0f)
                        lineToRelative(4.34f, -4.34f)
                        lineTo(12.0f, 10.0f)
                        horizontalLineToRelative(9.0f)
                        verticalLineToRelative(2.0f)
                        lineToRelative(-3.0f, 7.0f)
                        horizontalLineTo(9.0f)
                        verticalLineTo(9.0f)
                        close()
                        moveTo(1.0f, 9.0f)
                        horizontalLineToRelative(4.0f)
                        verticalLineToRelative(12.0f)
                        horizontalLineTo(1.0f)
                        close()
                    }
                }
                return _thumbUp!!
            }

        private var _thumbUp: ImageVector? = null

        val Visibility: ImageVector
            get() {
                if (_visibility != null) {
                    return _visibility!!
                }
                _visibility = materialIcon(name = "Outlined.Visibility") {
                    materialPath {
                        moveTo(12.0f, 6.0f)
                        curveToRelative(3.79f, 0.0f, 7.17f, 2.13f, 8.82f, 5.5f)
                        curveTo(19.17f, 14.87f, 15.79f, 17.0f, 12.0f, 17.0f)
                        reflectiveCurveToRelative(-7.17f, -2.13f, -8.82f, -5.5f)
                        curveTo(4.83f, 8.13f, 8.21f, 6.0f, 12.0f, 6.0f)
                        moveToRelative(0.0f, -2.0f)
                        curveTo(7.0f, 4.0f, 2.73f, 7.11f, 1.0f, 11.5f)
                        curveTo(2.73f, 15.89f, 7.0f, 19.0f, 12.0f, 19.0f)
                        reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
                        curveTo(21.27f, 7.11f, 17.0f, 4.0f, 12.0f, 4.0f)
                        close()
                        moveTo(12.0f, 9.0f)
                        curveToRelative(1.38f, 0.0f, 2.5f, 1.12f, 2.5f, 2.5f)
                        reflectiveCurveTo(13.38f, 14.0f, 12.0f, 14.0f)
                        reflectiveCurveToRelative(-2.5f, -1.12f, -2.5f, -2.5f)
                        reflectiveCurveTo(10.62f, 9.0f, 12.0f, 9.0f)
                        moveToRelative(0.0f, -2.0f)
                        curveToRelative(-2.48f, 0.0f, -4.5f, 2.02f, -4.5f, 4.5f)
                        reflectiveCurveTo(9.52f, 16.0f, 12.0f, 16.0f)
                        reflectiveCurveToRelative(4.5f, -2.02f, 4.5f, -4.5f)
                        reflectiveCurveTo(14.48f, 7.0f, 12.0f, 7.0f)
                        close()
                    }
                }
                return _visibility!!
            }

        private var _visibility: ImageVector? = null

        val VisibilityOff: ImageVector
            get() {
                if (_visibilityOff != null) {
                    return _visibilityOff!!
                }
                _visibilityOff = materialIcon(name = "Outlined.VisibilityOff") {
                    materialPath {
                        moveTo(12.0f, 6.0f)
                        curveToRelative(3.79f, 0.0f, 7.17f, 2.13f, 8.82f, 5.5f)
                        curveToRelative(-0.59f, 1.22f, -1.42f, 2.27f, -2.41f, 3.12f)
                        lineToRelative(1.41f, 1.41f)
                        curveToRelative(1.39f, -1.23f, 2.49f, -2.77f, 3.18f, -4.53f)
                        curveTo(21.27f, 7.11f, 17.0f, 4.0f, 12.0f, 4.0f)
                        curveToRelative(-1.27f, 0.0f, -2.49f, 0.2f, -3.64f, 0.57f)
                        lineToRelative(1.65f, 1.65f)
                        curveTo(10.66f, 6.09f, 11.32f, 6.0f, 12.0f, 6.0f)
                        close()
                        moveTo(10.93f, 7.14f)
                        lineTo(13.0f, 9.21f)
                        curveToRelative(0.57f, 0.25f, 1.03f, 0.71f, 1.28f, 1.28f)
                        lineToRelative(2.07f, 2.07f)
                        curveToRelative(0.08f, -0.34f, 0.14f, -0.7f, 0.14f, -1.07f)
                        curveTo(16.5f, 9.01f, 14.48f, 7.0f, 12.0f, 7.0f)
                        curveToRelative(-0.37f, 0.0f, -0.72f, 0.05f, -1.07f, 0.14f)
                        close()
                        moveTo(2.01f, 3.87f)
                        lineToRelative(2.68f, 2.68f)
                        curveTo(3.06f, 7.83f, 1.77f, 9.53f, 1.0f, 11.5f)
                        curveTo(2.73f, 15.89f, 7.0f, 19.0f, 12.0f, 19.0f)
                        curveToRelative(1.52f, 0.0f, 2.98f, -0.29f, 4.32f, -0.82f)
                        lineToRelative(3.42f, 3.42f)
                        lineToRelative(1.41f, -1.41f)
                        lineTo(3.42f, 2.45f)
                        lineTo(2.01f, 3.87f)
                        close()
                        moveTo(9.51f, 11.37f)
                        lineToRelative(2.61f, 2.61f)
                        curveToRelative(-0.04f, 0.01f, -0.08f, 0.02f, -0.12f, 0.02f)
                        curveToRelative(-1.38f, 0.0f, -2.5f, -1.12f, -2.5f, -2.5f)
                        curveToRelative(0.0f, -0.05f, 0.01f, -0.08f, 0.01f, -0.13f)
                        close()
                        moveTo(6.11f, 7.97f)
                        lineToRelative(1.75f, 1.75f)
                        curveToRelative(-0.23f, 0.55f, -0.36f, 1.15f, -0.36f, 1.78f)
                        curveToRelative(0.0f, 2.48f, 2.02f, 4.5f, 4.5f, 4.5f)
                        curveToRelative(0.63f, 0.0f, 1.23f, -0.13f, 1.77f, -0.36f)
                        lineToRelative(0.98f, 0.98f)
                        curveToRelative(-0.88f, 0.24f, -1.8f, 0.38f, -2.75f, 0.38f)
                        curveToRelative(-3.79f, 0.0f, -7.17f, -2.13f, -8.82f, -5.5f)
                        curveToRelative(0.7f, -1.43f, 1.72f, -2.61f, 2.93f, -3.53f)
                        close()
                    }
                }
                return _visibilityOff!!
            }

        private var _visibilityOff: ImageVector? = null

        val DeleteSweep: ImageVector
            get() {
                if (_deleteSweep != null) {
                    return _deleteSweep!!
                }
                _deleteSweep = materialIcon(name = "Outlined.DeleteSweep") {
                    materialPath {
                        moveTo(15.0f, 16.0f)
                        horizontalLineToRelative(4.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(-4.0f)
                        close()
                        moveTo(15.0f, 8.0f)
                        horizontalLineToRelative(7.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(-7.0f)
                        close()
                        moveTo(15.0f, 12.0f)
                        horizontalLineToRelative(6.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(-6.0f)
                        close()
                        moveTo(3.0f, 18.0f)
                        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                        horizontalLineToRelative(6.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        lineTo(13.0f, 8.0f)
                        lineTo(3.0f, 8.0f)
                        verticalLineToRelative(10.0f)
                        close()
                        moveTo(5.0f, 10.0f)
                        horizontalLineToRelative(6.0f)
                        verticalLineToRelative(8.0f)
                        lineTo(5.0f, 18.0f)
                        verticalLineToRelative(-8.0f)
                        close()
                        moveTo(10.0f, 4.0f)
                        lineTo(6.0f, 4.0f)
                        lineTo(5.0f, 5.0f)
                        lineTo(2.0f, 5.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(12.0f)
                        lineTo(14.0f, 5.0f)
                        horizontalLineToRelative(-3.0f)
                        close()
                    }
                }
                return _deleteSweep!!
            }

        private var _deleteSweep: ImageVector? = null

        val FormatListNumbered: ImageVector
            get() {
                if (_formatListNumbered != null) {
                    return _formatListNumbered!!
                }
                _formatListNumbered = materialIcon(name = "Outlined.FormatListNumbered") {
                    materialPath {
                        moveTo(2.0f, 17.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(0.5f)
                        lineTo(3.0f, 17.5f)
                        verticalLineToRelative(1.0f)
                        horizontalLineToRelative(1.0f)
                        verticalLineToRelative(0.5f)
                        lineTo(2.0f, 19.0f)
                        verticalLineToRelative(1.0f)
                        horizontalLineToRelative(3.0f)
                        verticalLineToRelative(-4.0f)
                        lineTo(2.0f, 16.0f)
                        verticalLineToRelative(1.0f)
                        close()
                        moveTo(3.0f, 8.0f)
                        horizontalLineToRelative(1.0f)
                        lineTo(4.0f, 4.0f)
                        lineTo(2.0f, 4.0f)
                        verticalLineToRelative(1.0f)
                        horizontalLineToRelative(1.0f)
                        verticalLineToRelative(3.0f)
                        close()
                        moveTo(2.0f, 11.0f)
                        horizontalLineToRelative(1.8f)
                        lineTo(2.0f, 13.1f)
                        verticalLineToRelative(0.9f)
                        horizontalLineToRelative(3.0f)
                        verticalLineToRelative(-1.0f)
                        lineTo(3.2f, 13.0f)
                        lineTo(5.0f, 10.9f)
                        lineTo(5.0f, 10.0f)
                        lineTo(2.0f, 10.0f)
                        verticalLineToRelative(1.0f)
                        close()
                        moveTo(7.0f, 5.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(14.0f)
                        lineTo(21.0f, 5.0f)
                        lineTo(7.0f, 5.0f)
                        close()
                        moveTo(7.0f, 19.0f)
                        horizontalLineToRelative(14.0f)
                        verticalLineToRelative(-2.0f)
                        lineTo(7.0f, 17.0f)
                        verticalLineToRelative(2.0f)
                        close()
                        moveTo(7.0f, 13.0f)
                        horizontalLineToRelative(14.0f)
                        verticalLineToRelative(-2.0f)
                        lineTo(7.0f, 11.0f)
                        verticalLineToRelative(2.0f)
                        close()
                    }
                }
                return _formatListNumbered!!
            }

        private var _formatListNumbered: ImageVector? = null

        val Tune: ImageVector
            get() {
                if (_tune != null) {
                    return _tune!!
                }
                _tune = materialIcon(name = "Outlined.Tune") {
                    materialPath {
                        moveTo(3.0f, 17.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(6.0f)
                        verticalLineToRelative(-2.0f)
                        lineTo(3.0f, 17.0f)
                        close()
                        moveTo(3.0f, 5.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(10.0f)
                        lineTo(13.0f, 5.0f)
                        lineTo(3.0f, 5.0f)
                        close()
                        moveTo(13.0f, 21.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineToRelative(8.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineToRelative(-8.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(2.0f)
                        close()
                        moveTo(7.0f, 9.0f)
                        verticalLineToRelative(2.0f)
                        lineTo(3.0f, 11.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(4.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(9.0f, 9.0f)
                        lineTo(7.0f, 9.0f)
                        close()
                        moveTo(21.0f, 13.0f)
                        verticalLineToRelative(-2.0f)
                        lineTo(11.0f, 11.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(10.0f)
                        close()
                        moveTo(15.0f, 9.0f)
                        horizontalLineToRelative(2.0f)
                        lineTo(17.0f, 7.0f)
                        horizontalLineToRelative(4.0f)
                        lineTo(21.0f, 5.0f)
                        horizontalLineToRelative(-4.0f)
                        lineTo(17.0f, 3.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(6.0f)
                        close()
                    }
                }
                return _tune!!
            }

        private var _tune: ImageVector? = null

        val AutoAwesome: ImageVector
            get() {
                if (_autoAwesome != null) {
                    return _autoAwesome!!
                }
                _autoAwesome = materialIcon(name = "Outlined.AutoAwesome") {
                    materialPath {
                        moveTo(19.0f, 9.0f)
                        lineToRelative(1.25f, -2.75f)
                        lineToRelative(2.75f, -1.25f)
                        lineToRelative(-2.75f, -1.25f)
                        lineToRelative(-1.25f, -2.75f)
                        lineToRelative(-1.25f, 2.75f)
                        lineToRelative(-2.75f, 1.25f)
                        lineToRelative(2.75f, 1.25f)
                        close()
                    }
                    materialPath {
                        moveTo(19.0f, 15.0f)
                        lineToRelative(-1.25f, 2.75f)
                        lineToRelative(-2.75f, 1.25f)
                        lineToRelative(2.75f, 1.25f)
                        lineToRelative(1.25f, 2.75f)
                        lineToRelative(1.25f, -2.75f)
                        lineToRelative(2.75f, -1.25f)
                        lineToRelative(-2.75f, -1.25f)
                        close()
                    }
                    materialPath {
                        moveTo(11.5f, 9.5f)
                        lineTo(9.0f, 4.0f)
                        lineTo(6.5f, 9.5f)
                        lineTo(1.0f, 12.0f)
                        lineToRelative(5.5f, 2.5f)
                        lineTo(9.0f, 20.0f)
                        lineToRelative(2.5f, -5.5f)
                        lineTo(17.0f, 12.0f)
                        lineTo(11.5f, 9.5f)
                        close()
                        moveTo(9.99f, 12.99f)
                        lineTo(9.0f, 15.17f)
                        lineToRelative(-0.99f, -2.18f)
                        lineTo(5.83f, 12.0f)
                        lineToRelative(2.18f, -0.99f)
                        lineTo(9.0f, 8.83f)
                        lineToRelative(0.99f, 2.18f)
                        lineTo(12.17f, 12.0f)
                        lineTo(9.99f, 12.99f)
                        close()
                    }
                }
                return _autoAwesome!!
            }

        private var _autoAwesome: ImageVector? = null

        val AutoDelete: ImageVector
            get() {
                if (_autoDelete != null) {
                    return _autoDelete!!
                }
                _autoDelete = materialIcon(name = "Outlined.AutoDelete") {
                    materialPath {
                        moveTo(15.0f, 2.0f)
                        lineToRelative(-3.5f, 0.0f)
                        lineToRelative(-1.0f, -1.0f)
                        lineToRelative(-5.0f, 0.0f)
                        lineToRelative(-1.0f, 1.0f)
                        lineToRelative(-3.5f, 0.0f)
                        lineToRelative(0.0f, 2.0f)
                        lineToRelative(14.0f, 0.0f)
                        close()
                    }
                    materialPath {
                        moveTo(16.0f, 9.0f)
                        curveToRelative(-0.7f, 0.0f, -1.37f, 0.1f, -2.0f, 0.29f)
                        verticalLineTo(5.0f)
                        horizontalLineTo(2.0f)
                        verticalLineToRelative(12.0f)
                        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                        horizontalLineToRelative(5.68f)
                        curveToRelative(1.12f, 2.36f, 3.53f, 4.0f, 6.32f, 4.0f)
                        curveToRelative(3.87f, 0.0f, 7.0f, -3.13f, 7.0f, -7.0f)
                        curveTo(23.0f, 12.13f, 19.87f, 9.0f, 16.0f, 9.0f)
                        close()
                        moveTo(9.0f, 16.0f)
                        curveToRelative(0.0f, 0.34f, 0.03f, 0.67f, 0.08f, 1.0f)
                        horizontalLineTo(4.0f)
                        verticalLineTo(7.0f)
                        horizontalLineToRelative(8.0f)
                        verticalLineToRelative(3.26f)
                        curveTo(10.19f, 11.53f, 9.0f, 13.62f, 9.0f, 16.0f)
                        close()
                        moveTo(16.0f, 21.0f)
                        curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                        reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
                        reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                        reflectiveCurveTo(18.76f, 21.0f, 16.0f, 21.0f)
                        close()
                    }
                    materialPath {
                        moveTo(16.5f, 12.0f)
                        lineToRelative(-1.5f, 0.0f)
                        lineToRelative(0.0f, 5.0f)
                        lineToRelative(3.6f, 2.1f)
                        lineToRelative(0.8f, -1.2f)
                        lineToRelative(-2.9f, -1.7f)
                        close()
                    }
                }
                return _autoDelete!!
            }

        private var _autoDelete: ImageVector? = null

        val Cached: ImageVector
            get() {
                if (_cached != null) {
                    return _cached!!
                }
                _cached = materialIcon(name = "Outlined.Cached") {
                    materialPath {
                        moveTo(19.0f, 8.0f)
                        lineToRelative(-4.0f, 4.0f)
                        horizontalLineToRelative(3.0f)
                        curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                        curveToRelative(-1.01f, 0.0f, -1.97f, -0.25f, -2.8f, -0.7f)
                        lineToRelative(-1.46f, 1.46f)
                        curveTo(8.97f, 19.54f, 10.43f, 20.0f, 12.0f, 20.0f)
                        curveToRelative(4.42f, 0.0f, 8.0f, -3.58f, 8.0f, -8.0f)
                        horizontalLineToRelative(3.0f)
                        lineToRelative(-4.0f, -4.0f)
                        close()
                        moveTo(6.0f, 12.0f)
                        curveToRelative(0.0f, -3.31f, 2.69f, -6.0f, 6.0f, -6.0f)
                        curveToRelative(1.01f, 0.0f, 1.97f, 0.25f, 2.8f, 0.7f)
                        lineToRelative(1.46f, -1.46f)
                        curveTo(15.03f, 4.46f, 13.57f, 4.0f, 12.0f, 4.0f)
                        curveToRelative(-4.42f, 0.0f, -8.0f, 3.58f, -8.0f, 8.0f)
                        horizontalLineTo(1.0f)
                        lineToRelative(4.0f, 4.0f)
                        lineToRelative(4.0f, -4.0f)
                        horizontalLineTo(6.0f)
                        close()
                    }
                }
                return _cached!!
            }

        private var _cached: ImageVector? = null

        val Check: ImageVector
            get() {
                if (_check != null) {
                    return _check!!
                }
                _check = materialIcon(name = "Outlined.Check") {
                    materialPath {
                        moveTo(9.0f, 16.17f)
                        lineTo(4.83f, 12.0f)
                        lineToRelative(-1.42f, 1.41f)
                        lineTo(9.0f, 19.0f)
                        lineTo(21.0f, 7.0f)
                        lineToRelative(-1.41f, -1.41f)
                        lineTo(9.0f, 16.17f)
                        close()
                    }
                }
                return _check!!
            }

        private var _check: ImageVector? = null

        val ColorLens: ImageVector
            get() {
                if (_colorLens != null) {
                    return _colorLens!!
                }
                _colorLens = materialIcon(name = "Outlined.ColorLens") {
                    materialPath {
                        moveTo(12.0f, 22.0f)
                        curveTo(6.49f, 22.0f, 2.0f, 17.51f, 2.0f, 12.0f)
                        reflectiveCurveTo(6.49f, 2.0f, 12.0f, 2.0f)
                        reflectiveCurveToRelative(10.0f, 4.04f, 10.0f, 9.0f)
                        curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                        horizontalLineToRelative(-1.77f)
                        curveToRelative(-0.28f, 0.0f, -0.5f, 0.22f, -0.5f, 0.5f)
                        curveToRelative(0.0f, 0.12f, 0.05f, 0.23f, 0.13f, 0.33f)
                        curveToRelative(0.41f, 0.47f, 0.64f, 1.06f, 0.64f, 1.67f)
                        curveToRelative(0.0f, 1.38f, -1.12f, 2.5f, -2.5f, 2.5f)
                        close()
                        moveTo(12.0f, 4.0f)
                        curveToRelative(-4.41f, 0.0f, -8.0f, 3.59f, -8.0f, 8.0f)
                        reflectiveCurveToRelative(3.59f, 8.0f, 8.0f, 8.0f)
                        curveToRelative(0.28f, 0.0f, 0.5f, -0.22f, 0.5f, -0.5f)
                        curveToRelative(0.0f, -0.16f, -0.08f, -0.28f, -0.14f, -0.35f)
                        curveToRelative(-0.41f, -0.46f, -0.63f, -1.05f, -0.63f, -1.65f)
                        curveToRelative(0.0f, -1.38f, 1.12f, -2.5f, 2.5f, -2.5f)
                        lineTo(16.0f, 15.0f)
                        curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                        curveToRelative(0.0f, -3.86f, -3.59f, -7.0f, -8.0f, -7.0f)
                        close()
                    }
                    materialPath {
                        moveTo(6.5f, 11.5f)
                        moveToRelative(-1.5f, 0.0f)
                        arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                        arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
                    }
                    materialPath {
                        moveTo(9.5f, 7.5f)
                        moveToRelative(-1.5f, 0.0f)
                        arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                        arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
                    }
                    materialPath {
                        moveTo(14.5f, 7.5f)
                        moveToRelative(-1.5f, 0.0f)
                        arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                        arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
                    }
                    materialPath {
                        moveTo(17.5f, 11.5f)
                        moveToRelative(-1.5f, 0.0f)
                        arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                        arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
                    }
                }
                return _colorLens!!
            }

        private var _colorLens: ImageVector? = null

        val DarkMode: ImageVector
            get() {
                if (_darkMode != null) {
                    return _darkMode!!
                }
                _darkMode = materialIcon(name = "Outlined.DarkMode") {
                    materialPath {
                        moveTo(9.37f, 5.51f)
                        curveTo(9.19f, 6.15f, 9.1f, 6.82f, 9.1f, 7.5f)
                        curveToRelative(0.0f, 4.08f, 3.32f, 7.4f, 7.4f, 7.4f)
                        curveToRelative(0.68f, 0.0f, 1.35f, -0.09f, 1.99f, -0.27f)
                        curveTo(17.45f, 17.19f, 14.93f, 19.0f, 12.0f, 19.0f)
                        curveToRelative(-3.86f, 0.0f, -7.0f, -3.14f, -7.0f, -7.0f)
                        curveTo(5.0f, 9.07f, 6.81f, 6.55f, 9.37f, 5.51f)
                        close()
                        moveTo(12.0f, 3.0f)
                        curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                        reflectiveCurveToRelative(4.03f, 9.0f, 9.0f, 9.0f)
                        reflectiveCurveToRelative(9.0f, -4.03f, 9.0f, -9.0f)
                        curveToRelative(0.0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                        curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                        curveToRelative(-2.98f, 0.0f, -5.4f, -2.42f, -5.4f, -5.4f)
                        curveToRelative(0.0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                        curveTo(12.92f, 3.04f, 12.46f, 3.0f, 12.0f, 3.0f)
                        lineTo(12.0f, 3.0f)
                        close()
                    }
                }
                return _darkMode!!
            }

        private var _darkMode: ImageVector? = null

        val Info: ImageVector
            get() {
                if (_info != null) {
                    return _info!!
                }
                _info = materialIcon(name = "Outlined.Info") {
                    materialPath {
                        moveTo(11.0f, 7.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(-2.0f)
                        close()
                        moveTo(11.0f, 11.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(-2.0f)
                        close()
                        moveTo(12.0f, 2.0f)
                        curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                        reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                        reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                        reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                        close()
                        moveTo(12.0f, 20.0f)
                        curveToRelative(-4.41f, 0.0f, -8.0f, -3.59f, -8.0f, -8.0f)
                        reflectiveCurveToRelative(3.59f, -8.0f, 8.0f, -8.0f)
                        reflectiveCurveToRelative(8.0f, 3.59f, 8.0f, 8.0f)
                        reflectiveCurveToRelative(-3.59f, 8.0f, -8.0f, 8.0f)
                        close()
                    }
                }
                return _info!!
            }

        private var _info: ImageVector? = null

        val NetworkCheck: ImageVector
            get() {
                if (_networkCheck != null) {
                    return _networkCheck!!
                }
                _networkCheck = materialIcon(name = "Outlined.NetworkCheck") {
                    materialPath {
                        moveTo(15.9f, 5.0f)
                        curveToRelative(-0.17f, 0.0f, -0.32f, 0.09f, -0.41f, 0.23f)
                        lineToRelative(-0.07f, 0.15f)
                        lineToRelative(-5.18f, 11.65f)
                        curveToRelative(-0.16f, 0.29f, -0.26f, 0.61f, -0.26f, 0.96f)
                        curveToRelative(0.0f, 1.11f, 0.9f, 2.01f, 2.01f, 2.01f)
                        curveToRelative(0.96f, 0.0f, 1.77f, -0.68f, 1.96f, -1.59f)
                        lineToRelative(0.01f, -0.03f)
                        lineTo(16.4f, 5.5f)
                        curveToRelative(0.0f, -0.28f, -0.22f, -0.5f, -0.5f, -0.5f)
                        close()
                        moveTo(1.0f, 9.0f)
                        lineToRelative(2.0f, 2.0f)
                        curveToRelative(2.88f, -2.88f, 6.79f, -4.08f, 10.53f, -3.62f)
                        lineToRelative(1.19f, -2.68f)
                        curveTo(9.89f, 3.84f, 4.74f, 5.27f, 1.0f, 9.0f)
                        close()
                        moveTo(21.0f, 11.0f)
                        lineToRelative(2.0f, -2.0f)
                        curveToRelative(-1.64f, -1.64f, -3.55f, -2.82f, -5.59f, -3.57f)
                        lineToRelative(-0.53f, 2.82f)
                        curveToRelative(1.5f, 0.62f, 2.9f, 1.53f, 4.12f, 2.75f)
                        close()
                        moveTo(17.0f, 15.0f)
                        lineToRelative(2.0f, -2.0f)
                        curveToRelative(-0.8f, -0.8f, -1.7f, -1.42f, -2.66f, -1.89f)
                        lineToRelative(-0.55f, 2.92f)
                        curveToRelative(0.42f, 0.27f, 0.83f, 0.59f, 1.21f, 0.97f)
                        close()
                        moveTo(5.0f, 13.0f)
                        lineToRelative(2.0f, 2.0f)
                        curveToRelative(1.13f, -1.13f, 2.56f, -1.79f, 4.03f, -2.0f)
                        lineToRelative(1.28f, -2.88f)
                        curveToRelative(-2.63f, -0.08f, -5.3f, 0.87f, -7.31f, 2.88f)
                        close()
                    }
                }
                return _networkCheck!!
            }

        private var _networkCheck: ImageVector? = null

        val Speed: ImageVector
            get() {
                if (_speed != null) {
                    return _speed!!
                }
                _speed = materialIcon(name = "Outlined.Speed") {
                    materialPath {
                        moveTo(20.38f, 8.57f)
                        lineToRelative(-1.23f, 1.85f)
                        arcToRelative(8.0f, 8.0f, 0.0f, false, true, -0.22f, 7.58f)
                        horizontalLineTo(5.07f)
                        arcTo(8.0f, 8.0f, 0.0f, false, true, 15.58f, 6.85f)
                        lineToRelative(1.85f, -1.23f)
                        arcTo(10.0f, 10.0f, 0.0f, false, false, 3.35f, 19.0f)
                        arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.72f, 1.0f)
                        horizontalLineToRelative(13.85f)
                        arcToRelative(2.0f, 2.0f, 0.0f, false, false, 1.74f, -1.0f)
                        arcToRelative(10.0f, 10.0f, 0.0f, false, false, -0.27f, -10.44f)
                        close()
                    }
                    materialPath {
                        moveTo(10.59f, 15.41f)
                        arcToRelative(2.0f, 2.0f, 0.0f, false, false, 2.83f, 0.0f)
                        lineToRelative(5.66f, -8.49f)
                        lineToRelative(-8.49f, 5.66f)
                        arcToRelative(2.0f, 2.0f, 0.0f, false, false, 0.0f, 2.83f)
                        close()
                    }
                }
                return _speed!!
            }

        private var _speed: ImageVector? = null

        val SystemUpdateAlt: ImageVector
            get() {
                if (_systemUpdateAlt != null) {
                    return _systemUpdateAlt!!
                }
                _systemUpdateAlt = materialIcon(name = "Outlined.SystemUpdateAlt") {
                    materialPath {
                        moveTo(12.0f, 16.0f)
                        lineToRelative(4.0f, -4.0f)
                        horizontalLineToRelative(-3.0f)
                        lineTo(13.0f, 3.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(9.0f)
                        lineTo(8.0f, 12.0f)
                        lineToRelative(4.0f, 4.0f)
                        close()
                        moveTo(21.0f, 3.0f)
                        horizontalLineToRelative(-6.0f)
                        verticalLineToRelative(1.99f)
                        horizontalLineToRelative(6.0f)
                        verticalLineToRelative(14.03f)
                        lineTo(3.0f, 19.02f)
                        lineTo(3.0f, 4.99f)
                        horizontalLineToRelative(6.0f)
                        lineTo(9.0f, 3.0f)
                        lineTo(3.0f, 3.0f)
                        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                        verticalLineToRelative(14.0f)
                        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                        horizontalLineToRelative(18.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        lineTo(23.0f, 5.0f)
                        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                        close()
                        moveTo(12.0f, 16.0f)
                        lineToRelative(4.0f, -4.0f)
                        horizontalLineToRelative(-3.0f)
                        lineTo(13.0f, 3.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(9.0f)
                        lineTo(8.0f, 12.0f)
                        lineToRelative(4.0f, 4.0f)
                        close()
                        moveTo(21.0f, 3.0f)
                        horizontalLineToRelative(-6.0f)
                        verticalLineToRelative(1.99f)
                        horizontalLineToRelative(6.0f)
                        verticalLineToRelative(14.03f)
                        lineTo(3.0f, 19.02f)
                        lineTo(3.0f, 4.99f)
                        horizontalLineToRelative(6.0f)
                        lineTo(9.0f, 3.0f)
                        lineTo(3.0f, 3.0f)
                        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                        verticalLineToRelative(14.0f)
                        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                        horizontalLineToRelative(18.0f)
                        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                        lineTo(23.0f, 5.0f)
                        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                        close()
                    }
                }
                return _systemUpdateAlt!!
            }

        private var _systemUpdateAlt: ImageVector? = null

        val TouchApp: ImageVector
            get() {
                if (_touchApp != null) {
                    return _touchApp!!
                }
                _touchApp = materialIcon(name = "Outlined.TouchApp") {
                    materialPath {
                        moveTo(18.19f, 12.44f)
                        lineToRelative(-3.24f, -1.62f)
                        curveToRelative(1.29f, -1.0f, 2.12f, -2.56f, 2.12f, -4.32f)
                        curveToRelative(0.0f, -3.03f, -2.47f, -5.5f, -5.5f, -5.5f)
                        reflectiveCurveToRelative(-5.5f, 2.47f, -5.5f, 5.5f)
                        curveToRelative(0.0f, 2.13f, 1.22f, 3.98f, 3.0f, 4.89f)
                        verticalLineToRelative(3.26f)
                        curveToRelative(-2.15f, -0.46f, -2.02f, -0.44f, -2.26f, -0.44f)
                        curveToRelative(-0.53f, 0.0f, -1.03f, 0.21f, -1.41f, 0.59f)
                        lineTo(4.0f, 16.22f)
                        lineToRelative(5.09f, 5.09f)
                        curveTo(9.52f, 21.75f, 10.12f, 22.0f, 10.74f, 22.0f)
                        horizontalLineToRelative(6.3f)
                        curveToRelative(0.98f, 0.0f, 1.81f, -0.7f, 1.97f, -1.67f)
                        lineToRelative(0.8f, -4.71f)
                        curveTo(20.03f, 14.32f, 19.38f, 13.04f, 18.19f, 12.44f)
                        close()
                        moveTo(17.84f, 15.29f)
                        lineTo(17.04f, 20.0f)
                        horizontalLineToRelative(-6.3f)
                        curveToRelative(-0.09f, 0.0f, -0.17f, -0.04f, -0.24f, -0.1f)
                        lineToRelative(-3.68f, -3.68f)
                        lineToRelative(4.25f, 0.89f)
                        verticalLineTo(6.5f)
                        curveToRelative(0.0f, -0.28f, 0.22f, -0.5f, 0.5f, -0.5f)
                        curveToRelative(0.28f, 0.0f, 0.5f, 0.22f, 0.5f, 0.5f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(1.76f)
                        lineToRelative(3.46f, 1.73f)
                        curveTo(17.69f, 14.43f, 17.91f, 14.86f, 17.84f, 15.29f)
                        close()
                        moveTo(8.07f, 6.5f)
                        curveToRelative(0.0f, -1.93f, 1.57f, -3.5f, 3.5f, -3.5f)
                        reflectiveCurveToRelative(3.5f, 1.57f, 3.5f, 3.5f)
                        curveToRelative(0.0f, 0.95f, -0.38f, 1.81f, -1.0f, 2.44f)
                        verticalLineTo(6.5f)
                        curveToRelative(0.0f, -1.38f, -1.12f, -2.5f, -2.5f, -2.5f)
                        curveToRelative(-1.38f, 0.0f, -2.5f, 1.12f, -2.5f, 2.5f)
                        verticalLineToRelative(2.44f)
                        curveTo(8.45f, 8.31f, 8.07f, 7.45f, 8.07f, 6.5f)
                        close()
                    }
                }
                return _touchApp!!
            }

        private var _touchApp: ImageVector? = null

        val Category: ImageVector
            get() {
                if (_category != null) {
                    return _category!!
                }
                _category = materialIcon(name = "Outlined.Category") {
                    materialPath {
                        moveTo(12.0f, 2.0f)
                        lineToRelative(-5.5f, 9.0f)
                        horizontalLineToRelative(11.0f)
                        lineTo(12.0f, 2.0f)
                        close()
                        moveTo(12.0f, 5.84f)
                        lineTo(13.93f, 9.0f)
                        horizontalLineToRelative(-3.87f)
                        lineTo(12.0f, 5.84f)
                        close()
                        moveTo(17.5f, 13.0f)
                        curveToRelative(-2.49f, 0.0f, -4.5f, 2.01f, -4.5f, 4.5f)
                        reflectiveCurveToRelative(2.01f, 4.5f, 4.5f, 4.5f)
                        reflectiveCurveToRelative(4.5f, -2.01f, 4.5f, -4.5f)
                        reflectiveCurveToRelative(-2.01f, -4.5f, -4.5f, -4.5f)
                        close()
                        moveTo(17.5f, 20.0f)
                        curveToRelative(-1.38f, 0.0f, -2.5f, -1.12f, -2.5f, -2.5f)
                        reflectiveCurveToRelative(1.12f, -2.5f, 2.5f, -2.5f)
                        reflectiveCurveToRelative(2.5f, 1.12f, 2.5f, 2.5f)
                        reflectiveCurveToRelative(-1.12f, 2.5f, -2.5f, 2.5f)
                        close()
                        moveTo(3.0f, 21.5f)
                        horizontalLineToRelative(8.0f)
                        verticalLineToRelative(-8.0f)
                        lineTo(3.0f, 13.5f)
                        verticalLineToRelative(8.0f)
                        close()
                        moveTo(5.0f, 15.5f)
                        horizontalLineToRelative(4.0f)
                        verticalLineToRelative(4.0f)
                        lineTo(5.0f, 19.5f)
                        verticalLineToRelative(-4.0f)
                        close()
                    }
                }
                return _category!!
            }

        private var _category: ImageVector? = null

        val EmojiEvents: ImageVector
            get() {
                if (_emojiEvents != null) {
                    return _emojiEvents!!
                }
                _emojiEvents = materialIcon(name = "Outlined.EmojiEvents") {
                    materialPath {
                        moveTo(19.0f, 5.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineTo(3.0f)
                        horizontalLineTo(7.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineTo(5.0f)
                        curveTo(3.9f, 5.0f, 3.0f, 5.9f, 3.0f, 7.0f)
                        verticalLineToRelative(1.0f)
                        curveToRelative(0.0f, 2.55f, 1.92f, 4.63f, 4.39f, 4.94f)
                        curveToRelative(0.63f, 1.5f, 1.98f, 2.63f, 3.61f, 2.96f)
                        verticalLineTo(19.0f)
                        horizontalLineTo(7.0f)
                        verticalLineToRelative(2.0f)
                        horizontalLineToRelative(10.0f)
                        verticalLineToRelative(-2.0f)
                        horizontalLineToRelative(-4.0f)
                        verticalLineToRelative(-3.1f)
                        curveToRelative(1.63f, -0.33f, 2.98f, -1.46f, 3.61f, -2.96f)
                        curveTo(19.08f, 12.63f, 21.0f, 10.55f, 21.0f, 8.0f)
                        verticalLineTo(7.0f)
                        curveTo(21.0f, 5.9f, 20.1f, 5.0f, 19.0f, 5.0f)
                        close()
                        moveTo(5.0f, 8.0f)
                        verticalLineTo(7.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(3.82f)
                        curveTo(5.84f, 10.4f, 5.0f, 9.3f, 5.0f, 8.0f)
                        close()
                        moveTo(12.0f, 14.0f)
                        curveToRelative(-1.65f, 0.0f, -3.0f, -1.35f, -3.0f, -3.0f)
                        verticalLineTo(5.0f)
                        horizontalLineToRelative(6.0f)
                        verticalLineToRelative(6.0f)
                        curveTo(15.0f, 12.65f, 13.65f, 14.0f, 12.0f, 14.0f)
                        close()
                        moveTo(19.0f, 8.0f)
                        curveToRelative(0.0f, 1.3f, -0.84f, 2.4f, -2.0f, 2.82f)
                        verticalLineTo(7.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineTo(8.0f)
                        close()
                    }
                }
                return _emojiEvents!!
            }

        private var _emojiEvents: ImageVector? = null

        val Home: ImageVector
            get() {
                if (_home != null) {
                    return _home!!
                }
                _home = materialIcon(name = "Outlined.Home") {
                    materialPath {
                        moveTo(12.0f, 5.69f)
                        lineToRelative(5.0f, 4.5f)
                        verticalLineTo(18.0f)
                        horizontalLineToRelative(-2.0f)
                        verticalLineToRelative(-6.0f)
                        horizontalLineTo(9.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineTo(7.0f)
                        verticalLineToRelative(-7.81f)
                        lineToRelative(5.0f, -4.5f)
                        moveTo(12.0f, 3.0f)
                        lineTo(2.0f, 12.0f)
                        horizontalLineToRelative(3.0f)
                        verticalLineToRelative(8.0f)
                        horizontalLineToRelative(6.0f)
                        verticalLineToRelative(-6.0f)
                        horizontalLineToRelative(2.0f)
                        verticalLineToRelative(6.0f)
                        horizontalLineToRelative(6.0f)
                        verticalLineToRelative(-8.0f)
                        horizontalLineToRelative(3.0f)
                        lineTo(12.0f, 3.0f)
                        close()
                    }
                }
                return _home!!
            }

        private var _home: ImageVector? = null

        val Settings: ImageVector
            get() {
                if (_settings != null) {
                    return _settings!!
                }
                _settings = materialIcon(name = "Outlined.Settings") {
                    materialPath {
                        moveTo(19.43f, 12.98f)
                        curveToRelative(0.04f, -0.32f, 0.07f, -0.64f, 0.07f, -0.98f)
                        curveToRelative(0.0f, -0.34f, -0.03f, -0.66f, -0.07f, -0.98f)
                        lineToRelative(2.11f, -1.65f)
                        curveToRelative(0.19f, -0.15f, 0.24f, -0.42f, 0.12f, -0.64f)
                        lineToRelative(-2.0f, -3.46f)
                        curveToRelative(-0.09f, -0.16f, -0.26f, -0.25f, -0.44f, -0.25f)
                        curveToRelative(-0.06f, 0.0f, -0.12f, 0.01f, -0.17f, 0.03f)
                        lineToRelative(-2.49f, 1.0f)
                        curveToRelative(-0.52f, -0.4f, -1.08f, -0.73f, -1.69f, -0.98f)
                        lineToRelative(-0.38f, -2.65f)
                        curveTo(14.46f, 2.18f, 14.25f, 2.0f, 14.0f, 2.0f)
                        horizontalLineToRelative(-4.0f)
                        curveToRelative(-0.25f, 0.0f, -0.46f, 0.18f, -0.49f, 0.42f)
                        lineToRelative(-0.38f, 2.65f)
                        curveToRelative(-0.61f, 0.25f, -1.17f, 0.59f, -1.69f, 0.98f)
                        lineToRelative(-2.49f, -1.0f)
                        curveToRelative(-0.06f, -0.02f, -0.12f, -0.03f, -0.18f, -0.03f)
                        curveToRelative(-0.17f, 0.0f, -0.34f, 0.09f, -0.43f, 0.25f)
                        lineToRelative(-2.0f, 3.46f)
                        curveToRelative(-0.13f, 0.22f, -0.07f, 0.49f, 0.12f, 0.64f)
                        lineToRelative(2.11f, 1.65f)
                        curveToRelative(-0.04f, 0.32f, -0.07f, 0.65f, -0.07f, 0.98f)
                        curveToRelative(0.0f, 0.33f, 0.03f, 0.66f, 0.07f, 0.98f)
                        lineToRelative(-2.11f, 1.65f)
                        curveToRelative(-0.19f, 0.15f, -0.24f, 0.42f, -0.12f, 0.64f)
                        lineToRelative(2.0f, 3.46f)
                        curveToRelative(0.09f, 0.16f, 0.26f, 0.25f, 0.44f, 0.25f)
                        curveToRelative(0.06f, 0.0f, 0.12f, -0.01f, 0.17f, -0.03f)
                        lineToRelative(2.49f, -1.0f)
                        curveToRelative(0.52f, 0.4f, 1.08f, 0.73f, 1.69f, 0.98f)
                        lineToRelative(0.38f, 2.65f)
                        curveToRelative(0.03f, 0.24f, 0.24f, 0.42f, 0.49f, 0.42f)
                        horizontalLineToRelative(4.0f)
                        curveToRelative(0.25f, 0.0f, 0.46f, -0.18f, 0.49f, -0.42f)
                        lineToRelative(0.38f, -2.65f)
                        curveToRelative(0.61f, -0.25f, 1.17f, -0.59f, 1.69f, -0.98f)
                        lineToRelative(2.49f, 1.0f)
                        curveToRelative(0.06f, 0.02f, 0.12f, 0.03f, 0.18f, 0.03f)
                        curveToRelative(0.17f, 0.0f, 0.34f, -0.09f, 0.43f, -0.25f)
                        lineToRelative(2.0f, -3.46f)
                        curveToRelative(0.12f, -0.22f, 0.07f, -0.49f, -0.12f, -0.64f)
                        lineToRelative(-2.11f, -1.65f)
                        close()
                        moveTo(17.45f, 11.27f)
                        curveToRelative(0.04f, 0.31f, 0.05f, 0.52f, 0.05f, 0.73f)
                        curveToRelative(0.0f, 0.21f, -0.02f, 0.43f, -0.05f, 0.73f)
                        lineToRelative(-0.14f, 1.13f)
                        lineToRelative(0.89f, 0.7f)
                        lineToRelative(1.08f, 0.84f)
                        lineToRelative(-0.7f, 1.21f)
                        lineToRelative(-1.27f, -0.51f)
                        lineToRelative(-1.04f, -0.42f)
                        lineToRelative(-0.9f, 0.68f)
                        curveToRelative(-0.43f, 0.32f, -0.84f, 0.56f, -1.25f, 0.73f)
                        lineToRelative(-1.06f, 0.43f)
                        lineToRelative(-0.16f, 1.13f)
                        lineToRelative(-0.2f, 1.35f)
                        horizontalLineToRelative(-1.4f)
                        lineToRelative(-0.19f, -1.35f)
                        lineToRelative(-0.16f, -1.13f)
                        lineToRelative(-1.06f, -0.43f)
                        curveToRelative(-0.43f, -0.18f, -0.83f, -0.41f, -1.23f, -0.71f)
                        lineToRelative(-0.91f, -0.7f)
                        lineToRelative(-1.06f, 0.43f)
                        lineToRelative(-1.27f, 0.51f)
                        lineToRelative(-0.7f, -1.21f)
                        lineToRelative(1.08f, -0.84f)
                        lineToRelative(0.89f, -0.7f)
                        lineToRelative(-0.14f, -1.13f)
                        curveToRelative(-0.03f, -0.31f, -0.05f, -0.54f, -0.05f, -0.74f)
                        reflectiveCurveToRelative(0.02f, -0.43f, 0.05f, -0.73f)
                        lineToRelative(0.14f, -1.13f)
                        lineToRelative(-0.89f, -0.7f)
                        lineToRelative(-1.08f, -0.84f)
                        lineToRelative(0.7f, -1.21f)
                        lineToRelative(1.27f, 0.51f)
                        lineToRelative(1.04f, 0.42f)
                        lineToRelative(0.9f, -0.68f)
                        curveToRelative(0.43f, -0.32f, 0.84f, -0.56f, 1.25f, -0.73f)
                        lineToRelative(1.06f, -0.43f)
                        lineToRelative(0.16f, -1.13f)
                        lineToRelative(0.2f, -1.35f)
                        horizontalLineToRelative(1.39f)
                        lineToRelative(0.19f, 1.35f)
                        lineToRelative(0.16f, 1.13f)
                        lineToRelative(1.06f, 0.43f)
                        curveToRelative(0.43f, 0.18f, 0.83f, 0.41f, 1.23f, 0.71f)
                        lineToRelative(0.91f, 0.7f)
                        lineToRelative(1.06f, -0.43f)
                        lineToRelative(1.27f, -0.51f)
                        lineToRelative(0.7f, 1.21f)
                        lineToRelative(-1.07f, 0.85f)
                        lineToRelative(-0.89f, 0.7f)
                        lineToRelative(0.14f, 1.13f)
                        close()
                        moveTo(12.0f, 8.0f)
                        curveToRelative(-2.21f, 0.0f, -4.0f, 1.79f, -4.0f, 4.0f)
                        reflectiveCurveToRelative(1.79f, 4.0f, 4.0f, 4.0f)
                        reflectiveCurveToRelative(4.0f, -1.79f, 4.0f, -4.0f)
                        reflectiveCurveToRelative(-1.79f, -4.0f, -4.0f, -4.0f)
                        close()
                        moveTo(12.0f, 14.0f)
                        curveToRelative(-1.1f, 0.0f, -2.0f, -0.9f, -2.0f, -2.0f)
                        reflectiveCurveToRelative(0.9f, -2.0f, 2.0f, -2.0f)
                        reflectiveCurveToRelative(2.0f, 0.9f, 2.0f, 2.0f)
                        reflectiveCurveToRelative(-0.9f, 2.0f, -2.0f, 2.0f)
                        close()
                    }
                }
                return _settings!!
            }

        private var _settings: ImageVector? = null
    }

    object AutoMirrored {
        object Outlined {
            val ArrowBack: ImageVector
                get() {
                    if (_arrowBack != null) {
                        return _arrowBack!!
                    }
                    _arrowBack = materialIcon(name = "AutoMirrored.Outlined.ArrowBack", autoMirror = true) {
                        materialPath {
                            moveTo(20.0f, 11.0f)
                            horizontalLineTo(7.83f)
                            lineToRelative(5.59f, -5.59f)
                            lineTo(12.0f, 4.0f)
                            lineToRelative(-8.0f, 8.0f)
                            lineToRelative(8.0f, 8.0f)
                            lineToRelative(1.41f, -1.41f)
                            lineTo(7.83f, 13.0f)
                            horizontalLineTo(20.0f)
                            verticalLineToRelative(-2.0f)
                            close()
                        }
                    }
                    return _arrowBack!!
                }

            private var _arrowBack: ImageVector? = null

            val ArrowForward: ImageVector
                get() {
                    if (_arrowForward != null) {
                        return _arrowForward!!
                    }
                    _arrowForward = materialIcon(name = "AutoMirrored.Outlined.ArrowForward", autoMirror =
                            true) {
                        materialPath {
                            moveTo(12.0f, 4.0f)
                            lineToRelative(-1.41f, 1.41f)
                            lineTo(16.17f, 11.0f)
                            horizontalLineTo(4.0f)
                            verticalLineToRelative(2.0f)
                            horizontalLineToRelative(12.17f)
                            lineToRelative(-5.58f, 5.59f)
                            lineTo(12.0f, 20.0f)
                            lineToRelative(8.0f, -8.0f)
                            lineToRelative(-8.0f, -8.0f)
                            close()
                        }
                    }
                    return _arrowForward!!
                }

            private var _arrowForward: ImageVector? = null

            val KeyboardArrowLeft: ImageVector
                get() {
                    if (_keyboardArrowLeft != null) {
                        return _keyboardArrowLeft!!
                    }
                    _keyboardArrowLeft = materialIcon(name = "AutoMirrored.Outlined.KeyboardArrowLeft",
                            autoMirror = true) {
                        materialPath {
                            moveTo(15.41f, 16.59f)
                            lineTo(10.83f, 12.0f)
                            lineToRelative(4.58f, -4.59f)
                            lineTo(14.0f, 6.0f)
                            lineToRelative(-6.0f, 6.0f)
                            lineToRelative(6.0f, 6.0f)
                            lineToRelative(1.41f, -1.41f)
                            close()
                        }
                    }
                    return _keyboardArrowLeft!!
                }

            private var _keyboardArrowLeft: ImageVector? = null

            val KeyboardArrowRight: ImageVector
                get() {
                    if (_keyboardArrowRight != null) {
                        return _keyboardArrowRight!!
                    }
                    _keyboardArrowRight = materialIcon(name = "AutoMirrored.Outlined.KeyboardArrowRight",
                            autoMirror = true) {
                        materialPath {
                            moveTo(8.59f, 16.59f)
                            lineTo(13.17f, 12.0f)
                            lineTo(8.59f, 7.41f)
                            lineTo(10.0f, 6.0f)
                            lineToRelative(6.0f, 6.0f)
                            lineToRelative(-6.0f, 6.0f)
                            lineToRelative(-1.41f, -1.41f)
                            close()
                        }
                    }
                    return _keyboardArrowRight!!
                }

            private var _keyboardArrowRight: ImageVector? = null
        }
    }
}

private inline fun materialIcon(
    name: String,
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> ImageVector.Builder,
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = MaterialIconDimension.dp,
    defaultHeight = MaterialIconDimension.dp,
    viewportWidth = MaterialIconDimension,
    viewportHeight = MaterialIconDimension,
    autoMirror = autoMirror,
).block().build()

private inline fun ImageVector.Builder.materialPath(
    fillAlpha: Float = 1f,
    strokeAlpha: Float = 1f,
    pathFillType: PathFillType = DefaultFillType,
    pathBuilder: PathBuilder.() -> Unit,
): ImageVector.Builder = path(
    fill = SolidColor(Color.Black),
    fillAlpha = fillAlpha,
    stroke = null,
    strokeAlpha = strokeAlpha,
    strokeLineWidth = 1f,
    strokeLineCap = StrokeCap.Butt,
    strokeLineJoin = StrokeJoin.Bevel,
    strokeLineMiter = 1f,
    pathFillType = pathFillType,
    pathBuilder = pathBuilder,
)

private const val MaterialIconDimension = 24f
