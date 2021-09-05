package exh.log

import android.util.Log
import com.elvishew.xlog.XLog
import timber.log.Timber

class XLogTree : Timber.DebugTree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (tag != null) {
            if (t != null) {
                XLog.tag(tag).log(priority.toXLogLevel(), message, t)
            } else {
                XLog.tag(tag).log(priority.toXLogLevel(), message)
            }
        } else {
            if (t != null) {
                XLog.log(priority.toXLogLevel(), message, t)
            } else {
                XLog.log(priority.toXLogLevel(), message)
            }
        }
    }

    private fun Int.toXLogLevel(): Int {
        return when (this) {
            Log.ASSERT -> LogLevel.None.int
            Log.ERROR -> LogLevel.Error.int
            Log.WARN -> LogLevel.Warn.int
            Log.INFO -> LogLevel.Info.int
            Log.DEBUG -> LogLevel.Debug.int
            Log.VERBOSE -> LogLevel.Verbose.int
            else -> LogLevel.All.int
        }
    }
}
