package io.github.janhurn9400.liquidy.ui

import androidx.compose.ui.text.font.FontFamily
import io.github.janhurn9400.liquidy.data.FontChoice

/**
 * The launcher's font. Named after the default (Inter, SIL OFL, docs/Inter-OFL.txt — a free substitute for the
 * original's system font), but it is whatever the user picked in Settings › Appearance › Font ([FontChoice]); read it
 * where text is built (not into a constant) so a change applies at once.
 */
val Inter: FontFamily get() = FontChoice.family
