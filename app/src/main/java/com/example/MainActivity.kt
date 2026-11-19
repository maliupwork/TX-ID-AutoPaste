package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.service.ReceiptDetectionService
import com.example.ui.MainViewModel
import com.example.ui.navigation.ApplicationSelectionRoute
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.ApplicationSelectionScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.TxIdAutoPasteTheme

class MainActivity : ComponentActivity() {

  private val viewModel: MainViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      TxIdAutoPasteTheme {
        TxIdAutoPasteRoot(
          viewModel = viewModel,
          onOpenAccessibilitySettings = {
            val container = (application as TxIdApplication).container
            startActivity(container.permissionManager.createAccessibilitySettingsIntent())
          },
          onStartMediaProjection = { resultCode, resultData ->
            val serviceIntent =
              Intent(this, ReceiptDetectionService::class.java).apply {
                putExtra(ReceiptDetectionService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ReceiptDetectionService.EXTRA_RESULT_DATA, resultData)
              }
            ContextCompat.startForegroundService(this, serviceIntent)
          },
        )
      }
    }
  }
}

@Composable
private fun TxIdAutoPasteRoot(
  viewModel: MainViewModel,
  onOpenAccessibilitySettings: () -> Unit,
  onStartMediaProjection: (Int, Intent) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val navController = rememberNavController()
  val lifecycleOwner = LocalLifecycleOwner.current
  val context = androidx.compose.ui.platform.LocalContext.current

  // Refresh permission state when returning from Android Accessibility Settings
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri -> uri?.let { viewModel.scanReceiptUri(it) } },
    )

  val mediaProjectionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult(),
      onResult = { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
          onStartMediaProjection(result.resultCode, data)
        }
      },
    )

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { viewModel.refreshPermissions() },
    )

  NavHost(
    navController = navController,
    startDestination = HomeRoute,
  ) {
    composable<HomeRoute> {
      HomeScreen(
        uiState = uiState,
        onToggleMaster = { enabled ->
          if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !uiState.permissionStatus.isNotificationsGranted
          ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
          viewModel.setMasterEnabled(enabled)
        },
        onScanPhotoPicker = {
          photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
          )
        },
        onScanScreenMediaProjection = {
          val manager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
          if (manager != null) {
            mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
          }
        },
        onCopyId = viewModel::copyLatestId,
        onClearId = viewModel::clearLatestId,
        onConfirmCandidate = viewModel::confirmCandidate,
        onDismissCandidates = viewModel::dismissCandidates,
        onOpenAppSelection = { initialTab ->
          navController.navigate(ApplicationSelectionRoute(initialTab = initialTab))
        },
        onOpenSettings = { navController.navigate(SettingsRoute) },
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onConsumeFeedback = viewModel::consumeFeedback,
      )
    }

    composable<ApplicationSelectionRoute> { backStackEntry ->
      val route = backStackEntry.toRoute<ApplicationSelectionRoute>()
      ApplicationSelectionScreen(
        uiState = uiState,
        initialTab = route.initialTab,
        loadAppIcon = viewModel::loadAppIcon,
        onToggleSourcePackage = viewModel::toggleSourcePackage,
        onSelectDestinationPackage = viewModel::setDestinationPackage,
        onRefreshApps = viewModel::refreshInstalledApps,
        onBack = { navController.popBackStack() },
      )
    }

    composable<SettingsRoute> {
      SettingsScreen(
        uiState = uiState,
        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
        onRequestNotificationPermission = {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          }
        },
        onSetRetentionMode = viewModel::setRetentionMode,
        onSetClipboardFallback = viewModel::setClipboardFallbackEnabled,
        onSetMinConfidence = viewModel::setMinConfidenceThreshold,
        onSetMediaProjectionFallback = viewModel::setMediaProjectionFallbackEnabled,
        onPurgeMemoryNow = viewModel::clearLatestId,
        onBack = { navController.popBackStack() },
      )
    }
  }
}
