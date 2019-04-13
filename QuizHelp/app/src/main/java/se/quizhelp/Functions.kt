package se.quizhelp

import java.io.BufferedReader
import java.io.InputStreamReader

val GetRunningApp: () -> String = {

    var activeProcess = ""

    val process = Runtime.getRuntime().exec(
        arrayOf(
            "su", "-c", "" +
                    "dumpsys window windows | grep -E 'mCurrentFocus|mFocusedApp'"
        )
    )

    val stdout = process.inputStream
    val br = BufferedReader(InputStreamReader(stdout))
    while (br.readLine() != null) {
        activeProcess = br.readLine()
    }
    br.close()
    process.waitFor()
    process.destroy()
    activeProcess
}

val Screenshot: (image_name: String) -> Process = {
    Runtime.getRuntime().exec(
        arrayOf(
            "su", "-c", "" +
                    "screencap -p /sdcard/" +
                    it
        )
    )
}