package com.catenarymaps.catenary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DepartureRouteFilterRow(
    filters: List<DepartureRouteFilter>,
    selected: Set<DepartureRouteFilterKey>,
    onToggle: (DepartureRouteFilterKey) -> Unit,
    onReset: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filters, key = { "${it.key.chateau.length}:${it.key.chateau}${it.key.name.length}:${it.key.name}${it.key.color}" }) { filter ->
                val active = filter.key in selected
                val routeId = filter.routeIds.first()
                val badge = ResolvedRouteBadgeInfo(
                    routeId = routeId,
                    shortName = filter.key.name,
                    longName = filter.route.long_name,
                    color = filter.route.color,
                    textColor = filter.route.text_color,
                    agencyId = filter.route.agency_id,
                    chateauId = filter.key.chateau
                )
                Box(
                    modifier = Modifier
                        .selectable(active, role = Role.Checkbox) { onToggle(filter.key) }
                        .semantics { contentDescription = filter.key.name }
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 4.dp)
                        .alpha(if (selected.isEmpty() || active) 1f else 0.35f),
                    contentAlignment = Alignment.Center
                ) {
                    SelectionRouteBadges(
                        routeIds = listOf(routeId),
                        resolveRouteInfo = { badge },
                        textSize = 12.sp,
                        compact = true
                    )
                }
            }
        }
        if (selected.isNotEmpty()) {
            IconButton(onClick = onReset, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.reset_route_filters),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
