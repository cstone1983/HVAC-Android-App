# Custom Developer Guidelines

- **Over-the-Air (OTA) vs. Reinstall Status**: After every change made to the codebase, always explicitly inform the user whether that specific change can be updated dynamically Over-the-Air (OTA) (e.g., by updating the `layout_config.json` via GitHub updates) or if it requires building and reinstalling the application (due to native Kotlin code, layout resource, manifest, or dependency modifications).
