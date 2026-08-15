# Custom Developer Guidelines

- **Over-the-Air (OTA) vs. Reinstall Status**: After every change made to the codebase, always explicitly inform the user whether that specific change can be updated dynamically Over-the-Air (OTA) (e.g., by updating the `layout_config.json` via GitHub updates) or if it requires building and reinstalling the application (due to native Kotlin code, layout resource, manifest, or dependency modifications).

- **Architecting OTA-First Customizations**: When implementing or altering features (such as entities, controls, titles, statuses, colors, theme variables, or threshold limits), always avoid hardcoding them directly in Kotlin. Instead, configure and retrieve these properties dynamically from the live `layout_config.json` (accessed via `layoutConfig` state flow). This allows users to tweak, override, or extend these features on-the-fly dynamically OTA without needing a full application rebuild or reinstall.
