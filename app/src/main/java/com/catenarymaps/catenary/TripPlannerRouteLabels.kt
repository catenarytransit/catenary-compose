package com.catenarymaps.catenary

private val timelineRouteSuffix = Regex("""(?:\s+\b(?:line|branch))+$""", RegexOption.IGNORE_CASE)

/** Trim complete trailing words only; preserve names such as Branchville and Skyline. */
internal fun timelineRouteName(name: String): String =
    name.trim().replace(timelineRouteSuffix, "")
