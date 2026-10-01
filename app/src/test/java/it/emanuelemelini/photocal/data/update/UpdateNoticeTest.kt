package it.emanuelemelini.photocal.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNoticeTest {

    private fun v(text: String) = AppVersion.parse(text)

    @Test
    fun aNewerReleaseIsAnnouncedOnce() {
        assertTrue(UpdateNotice.shouldNotify(v("1.5.0"), v("1.6.0"), lastNotified = null))
        assertFalse(UpdateNotice.shouldNotify(v("1.5.0"), v("1.6.0"), lastNotified = v("1.6.0")))
        // A later release is announced again
        assertTrue(UpdateNotice.shouldNotify(v("1.5.0"), v("1.7.0"), lastNotified = v("1.6.0")))
    }

    @Test
    fun sameOrOlderReleasesAreNotAnnounced() {
        assertFalse(UpdateNotice.shouldNotify(v("1.5.0"), v("1.5.0"), lastNotified = null))
        assertFalse(UpdateNotice.shouldNotify(v("1.5.0"), v("1.4.0"), lastNotified = null))
        assertFalse(UpdateNotice.shouldNotify(v("1.5.0"), latest = null, lastNotified = null))
        assertFalse(UpdateNotice.shouldNotify(current = null, latest = v("1.6.0"), lastNotified = null))
    }
}
