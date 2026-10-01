package it.emanuelemelini.photocal.data.update

/** When the "new version" reminder shows a notification. */
object UpdateNotice {

    /**
     * Only for a release newer than the installed version, and once per version: [lastNotified]
     * is the last one already announced.
     */
    fun shouldNotify(current: AppVersion?, latest: AppVersion?, lastNotified: AppVersion?): Boolean =
        current != null && latest != null && latest > current && (lastNotified == null || latest > lastNotified)
}
