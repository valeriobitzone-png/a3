package a3.overlay.mac

import a3.overlay.FileProfileStore
import a3.overlay.JvmHostSignals
import a3.overlay.ProfileDecision
import a3.overlay.ProfileOverrideStore
import a3.overlay.ProfileSession
import java.io.File

object OverlayProfileMac {
    fun store(): ProfileOverrideStore =
        FileProfileStore(File(System.getProperty("user.home"), ".a3/perf-override"))

    fun decide(overrideStore: ProfileOverrideStore = store()): ProfileDecision =
        ProfileSession.decide(JvmHostSignals.detect(), overrideStore)
}
