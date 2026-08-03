package com.catenarymaps.catenary

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val CATENARY_DONATION_URL =
        "https://opencollective.com/catenarymaps/donate?platformTip=0"

@Composable
fun DonationSupportCard(
        @StringRes titleRes: Int,
        @StringRes messageRes: Int,
        dismissible: Boolean,
        modifier: Modifier = Modifier,
        onDismiss: () -> Unit = {}
) {
        val uriHandler = LocalUriHandler.current

        Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp
        ) {
                Box(modifier = Modifier.padding(12.dp)) {
                        Column(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(end = if (dismissible) 32.dp else 0.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                                Text(
                                        text = stringResource(titleRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                )
                                Text(
                                        text = stringResource(messageRes),
                                        style =
                                                MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 12.sp,
                                                        lineHeight = 15.sp
                                                )
                                )

                                /*
                                 * The fundraising amount counter and progress bar are intentionally
                                 * disabled. Keep this location available if the goal UI returns.
                                 */

                                Button(
                                        onClick = {
                                                runCatching {
                                                        uriHandler.openUri(CATENARY_DONATION_URL)
                                                }
                                        },
                                        modifier =
                                                Modifier.padding(top = 4.dp)
                                                        .heightIn(min = 32.dp),
                                        contentPadding =
                                                PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                        Text(
                                                text = stringResource(R.string.support_donate),
                                                style = MaterialTheme.typography.labelMedium
                                        )
                                }
                        }

                        if (dismissible) {
                                IconButton(
                                        onClick = onDismiss,
                                        modifier =
                                                Modifier.align(Alignment.TopEnd).size(32.dp)
                                ) {
                                        Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription =
                                                        stringResource(
                                                                R.string
                                                                        .support_dismiss_content_description
                                                        ),
                                                modifier = Modifier.size(18.dp)
                                        )
                                }
                        }
                }
        }
}
