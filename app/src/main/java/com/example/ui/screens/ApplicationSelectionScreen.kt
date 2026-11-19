package com.example.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.domain.InstalledAppInfo
import com.example.ui.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationSelectionScreen(
  uiState: MainUiState,
  initialTab: Int,
  loadAppIcon: (String) -> Drawable?,
  onToggleSourcePackage: (String) -> Unit,
  onSelectDestinationPackage: (String?) -> Unit,
  onRefreshApps: () -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
  var searchQuery by rememberSaveable { mutableStateOf("") }

  val filteredApps =
    remember(uiState.installedApps, searchQuery) {
      val query = searchQuery.trim().lowercase()
      if (query.isEmpty()) {
        uiState.installedApps
      } else {
        uiState.installedApps.filter {
          it.appName.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }
      }
    }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text("Select Applications") },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("back_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
        actions = {
          IconButton(
            onClick = onRefreshApps,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("refresh_apps_button"),
          ) {
            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh Installed Apps")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          ),
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Column(modifier = Modifier.widthIn(max = 680.dp).fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Source Payment Apps (${uiState.settings.selectedSourcePackages.size})") },
            modifier = Modifier.testTag("tab_source_apps"),
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Payment Panel") },
            modifier = Modifier.testTag("tab_destination_app"),
          )
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text =
                  if (selectedTab == 0) {
                    "Select installed receipt apps (e.g. JazzCash, JazzCash Business, Easypaisa, Mashreq, Raqami Bank). Package names are never hardcoded."
                  } else {
                    "Select your separate Payment Panel app where detected IDs will be inserted automatically."
                  },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
              )
              if (selectedTab == 1 && uiState.settings.destinationPackage != null) {
                TextButton(
                  onClick = { onSelectDestinationPackage(null) },
                  modifier = Modifier.testTag("clear_destination_button"),
                ) {
                  Text("Clear")
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed apps...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("app_search_input"),
            shape = MaterialTheme.shapes.medium,
          )
        }

        if (uiState.isLoadingApps) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        } else if (filteredApps.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "No matching installed apps found",
                style = MaterialTheme.typography.titleSmall,
              )
              Text(
                text = "Install your payment apps or Payment Panel, then tap Refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(filteredApps, key = { it.packageName }) { app ->
              val isSelected =
                if (selectedTab == 0) {
                  app.packageName in uiState.settings.selectedSourcePackages
                } else {
                  app.packageName == uiState.settings.destinationPackage
                }

              InstalledAppRow(
                app = app,
                isMultiSelect = selectedTab == 0,
                isSelected = isSelected,
                loadAppIcon = loadAppIcon,
                onClick = {
                  if (selectedTab == 0) {
                    onToggleSourcePackage(app.packageName)
                  } else {
                    onSelectDestinationPackage(app.packageName)
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InstalledAppRow(
  app: InstalledAppInfo,
  isMultiSelect: Boolean,
  isSelected: Boolean,
  loadAppIcon: (String) -> Drawable?,
  onClick: () -> Unit,
) {
  val iconBitmap =
    remember(app.packageName) {
      runCatching { loadAppIcon(app.packageName)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
    }

  Surface(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.medium)
        .clickable(onClick = onClick)
        .testTag("app_item_${app.packageName}"),
    shape = MaterialTheme.shapes.medium,
    color =
      if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      else MaterialTheme.colorScheme.surface,
    border =
      BorderStroke(
        width = if (isSelected) 1.5.dp else 1.dp,
        color =
          if (isSelected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.outlineVariant,
      ),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (iconBitmap != null) {
        Image(
          bitmap = iconBitmap,
          contentDescription = "${app.appName} icon",
          modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
        )
      } else {
        Surface(
          modifier = Modifier.size(40.dp),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Apps, contentDescription = null, modifier = Modifier.size(22.dp))
          }
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = app.appName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (app.isKnownPaymentApp) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = Icons.Default.Verified,
              contentDescription = "Supported payment app",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
          }
        }
        Text(
          text = app.packageName,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (isMultiSelect) {
        Checkbox(
          checked = isSelected,
          onCheckedChange = { onClick() },
          modifier = Modifier.minimumInteractiveComponentSize(),
        )
      } else {
        RadioButton(
          selected = isSelected,
          onClick = onClick,
          modifier = Modifier.minimumInteractiveComponentSize(),
        )
      }
    }
  }
}
