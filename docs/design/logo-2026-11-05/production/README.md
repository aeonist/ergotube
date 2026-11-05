# Approved cloud-sofa production mark

Source: `../clouds/cloud-sofa.png`, generated with the built-in image generator and selected by the user on 5 November 2026. The logo alone is used; no generated lettering or backdrop is embedded.

`trace_logo.py` extracts the original silhouette into even-odd vector contours, retaining the play aperture. Its docstring documents reproduction from a BMP conversion with sips. Native resource geometry is shared across in-app, launcher and notification assets. Launcher foreground width is 64 of 108 units, centered in the adaptive safe zone.

Validation: 136 JVM tests, Debug and minified Release builds pass. `classic.png` shows the actual API37 classic header; `launcher.png` shows the installed icon in the app drawer. The welcome screen reuses CloudMark. Themed icon shape is declared but the launcher themed setting was not exercised.
