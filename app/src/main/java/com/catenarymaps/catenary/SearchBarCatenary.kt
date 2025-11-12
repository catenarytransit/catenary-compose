package com.catenarymaps.catenary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em


@Preview
@Composable
fun SearchBarPreview() {
    var searchQuery = ""



    SearchBarCatenary(
        searchQuery = searchQuery, onValueChange = { searchQuery = it })
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Transparent, shape = RoundedCornerShape(100.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
                .padding(vertical = 0.dp)
                .shadow(4.dp, shape = RoundedCornerShape(100.dp))
        )

        TextField(
            value = searchQuery,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 3.5.em, baselineShift = BaselineShift(1f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(color = Color.Transparent)
                .padding(vertical = 0.dp, horizontal = 0.dp)
                .onFocusChanged {
                    isFocused = it.isFocused // 👈 keep local state in sync
                    onFocusChange(it.isFocused)
                },
            shape = RoundedCornerShape(100.dp),
            placeholder = {
                Text(
                    stringResource(id = R.string.searchhere),
                    fontSize = 3.5.em,
                    overflow = TextOverflow.Visible
                )
            },
            leadingIcon = {
                if (isFocused) {
                    IconButton(onClick = { focusManager.clearFocus(force = true) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss search focus")
                    }
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.logo),
                        contentDescription = "Catenary logo",
                        modifier = Modifier
                            .size(32.dp)
                            .padding(start = 4.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            trailingIcon = {
                if (searchQuery.isEmpty() && !isFocused) {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            })
    }


}