# R8 rules for the release (minified) build.
#
# The whole point of minifying is to tree-shake huge unused library code —
# above all androidx.compose.material.icons.** (material-icons-extended ships
# ~10k icon classes; the app references ~17). Our own code is tiny, so we keep
# all of it: this protects the pieces R8 would otherwise break.

# Keep all app classes un-renamed. Room entities/DAOs, WorkManager workers
#    (instantiated reflectively by class name), the VoiceInteractionService
#    session classes depend on stable names. The app
#    package is small, so this costs almost nothing while removing R8 risk.
-keep class com.nothingai.capture.** { *; }
