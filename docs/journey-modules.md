# Journey modularisation

The first extraction separates planner state, routing streaming and preferences from active guidance and notification progress. Both feature libraries depend on core:journey for shared serializable models and saved snapshots. App retains map, navigation, Compose screens, Android service wiring and localized resources until their dependencies are extracted. Package names and persisted keys remain unchanged for compatibility.

Validation: run ./gradlew :app:assembleDebug :app:testDebugUnitTest :feature:journeyplanner:testDebugUnitTest :feature:journeytracking:testDebugUnitTest. Check planner restore, active guidance, and saved journey recall on device.
