package it.emanuelemelini.photocal.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import it.emanuelemelini.photocal.AppContainer
import it.emanuelemelini.photocal.PhotoCalApp

/** Access to the dependency container to create ViewModels (manual DI). */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as PhotoCalApp).container
