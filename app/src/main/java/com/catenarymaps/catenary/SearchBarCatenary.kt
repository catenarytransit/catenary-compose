package com.catenarymaps.catenary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview
@Composable
fun SearchBarPreview() {
    var searchQuery by remember { mutableStateOf("") }
    SearchBarCatenary(
        searchQuery = searchQuery,
        onValueChange = { searchQuery = it }
    )
}

@Composable
fun SearchBarCatenary(
    searchQuery: String = "",
    onValueChange: (String) -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var isFocused by remember { mutableStateOf(false) }

    // Smaller text + no font padding -> clean vertical centering
    val fieldTextStyle = LocalTextStyle.current.merge(
        TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Transparent, shape = RoundedCornerShape(100.dp))
    ) {
        // Shadowed container behind the field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .padding(vertical = 0.dp)
                .shadow(4.dp, shape = RoundedCornerShape(100.dp))
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(100.dp)
                )
        )

        BasicTextField(
            value = searchQuery,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = fieldTextStyle,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp) // keep original height
                .padding(horizontal = 0.dp)
                .onFocusChanged {
                    isFocused = it.isFocused
                    onFocusChange(it.isFocused)
                },
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leading
                    Box(
                        modifier = Modifier.size(48.dp), // Same size as IconButton
                        contentAlignment = Alignment.Center
                    ) {
                        if (isFocused) {
                            IconButton(onClick = { focusManager.clearFocus(force = true) }) {
                                Icon(
                                    Icons.Filled.Close, contentDescription = "Dismiss search focus",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.logo),
                                contentDescription = "Catenary logo",
                                modifier = Modifier
                                    .size(32.dp), // keep your original logo size
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Text / Placeholder (centered vertically by the Row)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = stringResource(id = R.string.searchhere),
                                style = fieldTextStyle.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }

                    // Trailing
                    if (searchQuery.isEmpty() && !isFocused) {
                        IconButton(onClick = onSettingsClick) {
                            Icon(
                                Icons.Filled.Settings,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                contentDescription = "Settings"
                            )
                        }
                    }
                }
            }
        )
    }
}
