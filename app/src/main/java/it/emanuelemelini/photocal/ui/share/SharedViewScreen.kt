package it.emanuelemelini.photocal.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.toRoute
import it.emanuelemelini.photocal.R
import it.emanuelemelini.photocal.data.photo.ProfilePhotoStorage
import it.emanuelemelini.photocal.data.share.ShareBuilder
import it.emanuelemelini.photocal.data.share.ShareCodec
import it.emanuelemelini.photocal.data.share.SharedCard
import it.emanuelemelini.photocal.ui.SharedViewRoute
import it.emanuelemelini.photocal.ui.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SharedViewState {
    data object Loading : SharedViewState
    data class Shown(val card: SharedCard, val avatar: ImageBitmap?) : SharedViewState
    data object Invalid : SharedViewState
    data object NewerVersion : SharedViewState
}

/** Checks and decodes the link; nothing of it is saved. */
class SharedViewViewModel(savedStateHandle: SavedStateHandle, shareBuilder: ShareBuilder) : ViewModel() {

    var state by mutableStateOf<SharedViewState>(SharedViewState.Loading)
        private set

    init {
        val payload = savedStateHandle.toRoute<SharedViewRoute>().payload
        viewModelScope.launch {
            state = withContext(Dispatchers.Default) {
                when (val result = shareBuilder.decode(payload)) {
                    is ShareCodec.Result.Valid -> SharedViewState.Shown(
                        result.card,
                        result.card.avatar?.let(ProfilePhotoStorage::decodeThumbnail)?.asImageBitmap(),
                    )
                    ShareCodec.Result.Invalid -> SharedViewState.Invalid
                    ShareCodec.Result.NewerVersion -> SharedViewState.NewerVersion
                }
            }
        }
    }
}

/** A day or week someone shared with a link: read only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedViewScreen(onClose: () -> Unit) {
    val container = appContainer()
    val viewModel: SharedViewViewModel = viewModel {
        SharedViewViewModel(createSavedStateHandle(), container.shareBuilder)
    }
    val state = viewModel.state
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if ((state as? SharedViewState.Shown)?.card?.week == true) R.string.shared_title_week else R.string.shared_title_day
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onClose)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            SharedViewState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is SharedViewState.Shown -> Column(
                Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                SharedCardView(state.card, state.avatar)
            }
            SharedViewState.Invalid -> Message(stringResource(R.string.shared_invalid), Modifier.padding(padding))
            SharedViewState.NewerVersion -> Message(stringResource(R.string.shared_newer_version), Modifier.padding(padding))
        }
    }
}

@Composable
private fun Message(text: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}
