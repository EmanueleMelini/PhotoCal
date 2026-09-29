package it.emanuelemelini.photocal.ui.share

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.data.photo.ProfilePhotoStorage
import it.emanuelemelini.photocal.data.share.ShareBuilder
import it.emanuelemelini.photocal.data.share.ShareOptions
import it.emanuelemelini.photocal.data.share.SharedCard
import it.emanuelemelini.photocal.ui.ShareRoute
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Share options of a day; the preview is the exact card the link will carry. */
class ShareViewModel(
    savedStateHandle: SavedStateHandle,
    private val shareBuilder: ShareBuilder,
    profilePhotoStorage: ProfilePhotoStorage,
) : ViewModel() {

    val date: LocalDate = LocalDate.ofEpochDay(savedStateHandle.toRoute<ShareRoute>().dateEpochDay)

    /** The photo switch is shown only when there is a photo. */
    val hasPhoto: Boolean = profilePhotoStorage.exists

    var options by mutableStateOf(ShareOptions(photo = hasPhoto))
        private set
    var card by mutableStateOf<SharedCard?>(null)
        private set

    /** Decoded from the card itself: the preview shows the thumbnail the receiver gets. */
    var avatar by mutableStateOf<ImageBitmap?>(null)
        private set

    private var buildJob: Job? = null

    init {
        rebuild()
    }

    fun update(transform: (ShareOptions) -> ShareOptions) {
        options = transform(options)
        rebuild()
    }

    /** Link of the current card, or null while it is being built or when nothing is shown. */
    fun link(): String? = card?.takeIf { options.hasContent && it.name.isNotBlank() }?.let(shareBuilder::link)

    private fun rebuild() {
        buildJob?.cancel()
        val current = options
        buildJob = viewModelScope.launch {
            val built = shareBuilder.build(date, current)
            avatar = built.avatar?.let(ProfilePhotoStorage::decodeThumbnail)?.asImageBitmap()
            card = built
        }
    }
}
