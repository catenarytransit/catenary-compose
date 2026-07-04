object LayersPerCategory {
    object Bus
    object TrajectoryBus
}

fun test(prefix: Any) {
    val result = when (prefix) {
        is LayersPerCategory.Bus -> "bus"
        is LayersPerCategory.TrajectoryBus -> "traj-bus"
        else -> "none"
    }
    println(result)
}

fun main() {
    test(LayersPerCategory.Bus)
    test(LayersPerCategory.TrajectoryBus)
    test("hello")
}
