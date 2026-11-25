package com.example.ui.navigation

import android.content.Context
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.audio.AssetClipPlayer
import com.example.audio.DeviceSpeech
import com.example.audio.PcmAudioPlayer
import com.example.auth.AccountSetupGate
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.ai.FirebaseAiRepository
import com.example.data.cache.ProfileSummaryCache
import com.example.data.model.DifficultyLevel
import com.example.data.model.GameType
import com.example.data.model.TargetLanguage
import com.example.data.repository.ProgressRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.games.CalligraphyGameScreen
import com.example.ui.games.CardMatchGameScreen
import com.example.ui.games.QuizGameScreen
import com.example.ui.games.StoryGameScreen
import com.example.ui.games.VoiceQuizGameScreen
import com.example.ui.progress.ProgressScreen
import com.example.ui.speech.SpeechGuideScreen
import com.example.ui.studio.StudioHomeScreen
import com.example.ui.studio.StudioUiState
import com.example.ui.studio.StudioViewModel
import com.example.ui.theme.PolyglotStudioTheme
import com.example.ui.tutor.TutorHubScreen
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.serialization.Serializable

@Serializable data object StudioRoute
@Serializable data object SpeechRoute
@Serializable data object TutorRoute
@Serializable data object ProgressRoute

@Serializable data object GameCardMatchRoute
@Serializable data object GameCalligraphyRoute
@Serializable data object GameQuizRoute
@Serializable data object GameVoiceRoute
@Serializable data object GameStoryRoute

private data class TopTabItem(
    val route: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val tag: String
)

private val TOP_TABS = listOf(
    TopTabItem(StudioRoute, R.string.nav_studio, Icons.Rounded.Home, "tab_studio"),
    TopTabItem(SpeechRoute, R.string.nav_speech, Icons.Rounded.RecordVoiceOver, "tab_speech"),
    TopTabItem(TutorRoute, R.string.nav_tutor, Icons.Rounded.AutoAwesome, "tab_tutor"),
    TopTabItem(ProgressRoute, R.string.nav_progress, Icons.Rounded.BarChart, "tab_progress")
)

private class StudioViewModelFactory(
    private val context: Context,
    private val userId: String,
    private val aiRepository: FirebaseAiRepository,
    private val profileCache: ProfileSummaryCache
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val dbId = context.getString(R.string.firestore_database_id)
        val firestore = Firebase.firestore(dbId)
        return StudioViewModel(
            progressRepository = ProgressRepository(firestore),
            aiRepository = aiRepository,
            audioPlayer = PcmAudioPlayer(),
            clipPlayer = AssetClipPlayer(context),
            deviceSpeech = DeviceSpeech(context),
            currentUserId = userId,
            profileCache = profileCache
        ) as T
    }
}

@Composable
fun PolyglotAppRoot() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val accountSetupPending by AccountSetupGate.isPending.collectAsStateWithLifecycle()

    val user = currentUser
    if (user == null || accountSetupPending) {
        PolyglotStudioTheme {
            AuthScreen(onAuthSuccess = {
                // Auth state flow transitions automatically upon sign-in
            })
        }
    } else {
        key(user.uid) {
            AuthenticatedStudioShell(userId = user.uid)
        }
    }
}

@Composable
private fun AuthenticatedStudioShell(userId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val aiRepository = remember { FirebaseAiRepository() }
    val profileCache = remember(context) { ProfileSummaryCache(context.applicationContext) }

    val studioViewModel: StudioViewModel = viewModel(
        key = "studio_vm_$userId",
        factory = StudioViewModelFactory(context.applicationContext, userId, aiRepository, profileCache)
    )

    val profileState by studioViewModel.profileState.collectAsStateWithLifecycle()
    val masteryState by studioViewModel.masteryState.collectAsStateWithLifecycle()
    val sessionsState by studioViewModel.sessionsState.collectAsStateWithLifecycle()
    val savedPhrasesState by studioViewModel.savedPhrasesState.collectAsStateWithLifecycle()
    val speakingKey by studioViewModel.speakingKey.collectAsStateWithLifecycle()
    val ttsError by studioViewModel.ttsError.collectAsStateWithLifecycle()

    val profile = (profileState as? StudioUiState.Success)?.data
    val selectedLanguage = TargetLanguage.fromCode(profile?.activeLanguage)
    val selectedDifficulty = DifficultyLevel.fromCode(profile?.difficulty)

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val isTopLevel = currentDest == null ||
        currentDest.hasRoute(StudioRoute::class) ||
        currentDest.hasRoute(SpeechRoute::class) ||
        currentDest.hasRoute(TutorRoute::class) ||
        currentDest.hasRoute(ProgressRoute::class)

    fun navigateTopTab(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    PolyglotStudioTheme(activeLanguage = selectedLanguage) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (isTopLevel && !useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            TOP_TABS.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isTopLevel && useRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            TOP_TABS.forEach { tab ->
                                val selected = currentDest?.hasRoute(tab.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTopTab(tab.route) },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = StudioRoute,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable<StudioRoute> {
                            StudioHomeScreen(
                                profileState = profileState,
                                masteryState = masteryState,
                                selectedLanguage = selectedLanguage,
                                selectedDifficulty = selectedDifficulty,
                                onSelectLanguage = studioViewModel::selectLanguage,
                                onSelectDifficulty = studioViewModel::selectDifficulty,
                                onLaunchGame = { game ->
                                    val route: Any = when (game) {
                                        GameType.CARDS -> GameCardMatchRoute
                                        GameType.CALLIGRAPHY -> GameCalligraphyRoute
                                        GameType.QUIZ -> GameQuizRoute
                                        GameType.VOICE -> GameVoiceRoute
                                        GameType.STORY -> GameStoryRoute
                                    }
                                    navController.navigate(route)
                                },
                                onOpenSpeechGuide = { navigateTopTab(SpeechRoute) },
                                onOpenTutorHub = { navigateTopTab(TutorRoute) }
                            )
                        }

                        composable<SpeechRoute> {
                            SpeechGuideScreen(
                                selectedLanguage = selectedLanguage,
                                onSelectLanguage = studioViewModel::selectLanguage,
                                aiRepository = aiRepository,
                                speakingKey = speakingKey,
                                ttsError = ttsError,
                                onSpeak = studioViewModel::speakText,
                                onSavePhrase = studioViewModel::savePhrase
                            )
                        }

                        composable<TutorRoute> {
                            TutorHubScreen(
                                selectedLanguage = selectedLanguage,
                                selectedDifficulty = selectedDifficulty,
                                onSelectLanguage = studioViewModel::selectLanguage,
                                aiRepository = aiRepository,
                                speakingKey = speakingKey,
                                onSpeak = studioViewModel::speakText
                            )
                        }

                        composable<ProgressRoute> {
                            ProgressScreen(
                                profileState = profileState,
                                masteryState = masteryState,
                                sessionsState = sessionsState,
                                savedPhrasesState = savedPhrasesState,
                                selectedLanguage = selectedLanguage,
                                onSelectLanguage = studioViewModel::selectLanguage,
                                speakingKey = speakingKey,
                                onSpeak = studioViewModel::speakText,
                                onDeletePhrase = studioViewModel::deleteSavedPhrase,
                                onSignOut = {
                                    profileCache.clear(userId)
                                    signOut(credentialManager, scope)
                                }
                            )
                        }

                        composable<GameCardMatchRoute> {
                            CardMatchGameScreen(
                                language = selectedLanguage,
                                difficulty = selectedDifficulty,
                                speakingKey = speakingKey,
                                onSpeak = studioViewModel::speakText,
                                onSavePhrase = studioViewModel::savePhrase,
                                onCompleteSession = studioViewModel::recordCompletion,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<GameCalligraphyRoute> {
                            CalligraphyGameScreen(
                                language = selectedLanguage,
                                difficulty = selectedDifficulty,
                                onEvaluate = aiRepository::evaluateCalligraphy,
                                speakingKey = speakingKey,
                                onSpeak = studioViewModel::speakText,
                                onCompleteSession = studioViewModel::recordCompletion,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<GameQuizRoute> {
                            QuizGameScreen(
                                language = selectedLanguage,
                                difficulty = selectedDifficulty,
                                aiRepository = aiRepository,
                                speakingKey = speakingKey,
                                onSpeak = studioViewModel::speakText,
                                onCompleteSession = studioViewModel::recordCompletion,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<GameVoiceRoute> {
                            VoiceQuizGameScreen(
                                language = selectedLanguage,
                                difficulty = selectedDifficulty,
                                aiRepository = aiRepository,
                                speakingKey = speakingKey,
                                onSpeak = studioViewModel::speakText,
                                onSavePhrase = studioViewModel::savePhrase,
                                onCompleteSession = studioViewModel::recordCompletion,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<GameStoryRoute> {
                            StoryGameScreen(
                                language = selectedLanguage,
                                difficulty = selectedDifficulty,
                                aiRepository = aiRepository,
                                speakingKey = speakingKey,
                                onSpeak = studioViewModel::speakText,
                                onSavePhrase = studioViewModel::savePhrase,
                                onCompleteSession = studioViewModel::recordCompletion,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
