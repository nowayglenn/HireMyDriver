# HireMyDriver Android app

Open this project in Android Studio and sync Gradle before running it.

- [Screen and layout map](docs/SCREEN_MAP.md): find customer, driver and shared screens.
- [UI audit and verification](docs/UI_AUDIT.md): fixes, remaining demo behavior and phone checks.
- [Map and booking flow](docs/MAP_BOOKING_FLOW.md): customer pins, driver pickup/service steps, local test instructions and Firebase integration boundary.

All layouts are in `app/src/main/res/layout` (Android Studio: **app → res → layout**).
The screen map identifies which ones belong to customers, drivers, or both.
Shared dimensions remain in `app/src/main/res/values/dimens.xml`.
All original icons, backgrounds and images are in `app/src/main/res/drawable`, so Android Studio Preview and Gradle use the same standard resource folder.
