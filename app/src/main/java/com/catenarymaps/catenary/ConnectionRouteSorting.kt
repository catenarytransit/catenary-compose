package com.catenarymaps.catenary

// Give every name a fixed category before comparing values. Choosing numeric versus
// lexical ordering per pair creates cycles such as 2 < 10 < 1A < 2, which can crash TimSort.
internal val connectionRouteNameComparator: Comparator<String> =
        compareBy<String> { if (it.toIntOrNull() != null) 0 else 1 }
                .thenBy { it.toIntOrNull() }
                .thenBy { it }
