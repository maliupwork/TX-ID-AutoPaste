package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.CandidateIdentifier
import com.example.domain.DetectedTransaction
import com.example.ui.theme.MonospaceCandidateStyle
import com.example.ui.theme.MonospaceIdStyle
import kotlinx.coroutines.delay

@Composable
fun MasterControlHeroCard(
  isMasterEnabled: Boolean,
  selectedSourceCount: Int,
  hasDestinationApp: Boolean,
  onToggle: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val containerStart by
    animateColorAsState(
      targetValue =
        if (isMasterEnabled) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "hero_start_color",
    )
  val containerEnd by
    animateColorAsState(
      targetValue =
        if (isMasterEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surface,
      animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
      label = "hero_end_color",
    )
  val borderColor by
    animateColorAsState(
      targetValue =
        if (isMasterEnabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outline,
      label = "hero_border_color",
    )

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(MaterialTheme.shapes.large)
        .clickable { onToggle(!isMasterEnabled) }
        .testTag("master_switch_card"),
    shape = MaterialTheme.shapes.large,
    border = BorderStroke(1.5.dp, borderColor),
    tonalElevation = if (isMasterEnabled) 4.dp else 1.dp,
  ) {
    Box(
      modifier =
        Modifier.background(
          Brush.linearGradient(colors = listOf(containerStart, containerEnd))
        )
          .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          modifier = Modifier.weight(1f).padding(end = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier =
              Modifier.size(48.dp)
                .clip(CircleShape)
                .background(
                  if (isMasterEnabled) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Default.PowerSettingsNew,
              contentDescription = null,
              tint =
                if (isMasterEnabled) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(26.dp),
            )
          }

          Spacer(modifier = Modifier.width(14.dp))

          Column {
            Text(
              text = if (isMasterEnabled) "Protection & AutoPaste ON" else "Master Switch OFF",
              style = MaterialTheme.typography.titleLarge,
              color =
                if (isMasterEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text =
                if (isMasterEnabled) {
                  val destPart =
                    if (hasDestinationApp) "Panel linked" else "Select Payment Panel"
                  "$selectedSourceCount source app(s) • $destPart"
                } else {
                  "All OCR, Accessibility & screen capture halted. Temporary IDs cleared."
                },
              style = MaterialTheme.typography.bodySmall,
              color =
                if (isMasterEnabled)
                  MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Switch(
          checked = isMasterEnabled,
          onCheckedChange = onToggle,
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("master_switch")
              .semantics {
                contentDescription =
                  if (isMasterEnabled) "Turn TX ID AutoPaste OFF" else "Turn TX ID AutoPaste ON"
              },
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
              checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
      }
    }
  }
}

@Composable
fun LatestDetectedIdCard(
  transaction: DetectedTransaction?,
  pendingCandidates: List<CandidateIdentifier>,
  isMasterEnabled: Boolean,
  onScanClick: () -> Unit,
  onCopyClick: () -> Unit,
  onClearClick: () -> Unit,
  onConfirmCandidate: (CandidateIdentifier) -> Unit,
  onDismissCandidates: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(transaction?.expiresAtMillis) {
    if (transaction?.expiresAtMillis != null) {
      while (true) {
        nowMillis = System.currentTimeMillis()
        delay(1_000L)
      }
    }
  }

  Card(
    modifier = modifier.fillMaxWidth().testTag("latest_id_card"),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector =
              if (pendingCandidates.isNotEmpty()) Icons.Default.WarningAmber
              else Icons.Default.Lock,
            contentDescription = null,
            tint =
              if (pendingCandidates.isNotEmpty()) MaterialTheme.colorScheme.tertiary
              else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              if (pendingCandidates.isNotEmpty()) "Confirm Identifier"
              else "Latest Detected ID (Volatile RAM)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }

        if (transaction != null) {
          val remainingSec =
            transaction.expiresAtMillis?.let { ((it - nowMillis) / 1000L).coerceAtLeast(0L) }
          val badgeText =
            when {
              transaction.wasPasted -> "Pasted"
              remainingSec != null -> "${remainingSec / 60}:${(remainingSec % 60).toString().padStart(2, '0')}"
              else -> "Active"
            }
          Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
          ) {
            Text(
              text = badgeText,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      when {
        pendingCandidates.isNotEmpty() -> {
          Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "Multiple identifiers matched on this receipt. Select the exact one to auto-paste:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
              )
              Spacer(modifier = Modifier.height(10.dp))
              pendingCandidates.forEachIndexed { index, candidate ->
                Surface(
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(vertical = 4.dp)
                      .clip(MaterialTheme.shapes.small)
                      .clickable { onConfirmCandidate(candidate) }
                      .testTag("confirm_candidate_$index"),
                  color = MaterialTheme.colorScheme.surface,
                  shape = MaterialTheme.shapes.small,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = candidate.matchedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                      )
                      Text(
                        text = candidate.value,
                        style = MonospaceCandidateStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                    }
                    Text(
                      text = "${(candidate.confidence * 100).toInt()}%",
                      style = MaterialTheme.typography.labelMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
              ) {
                TextButton(
                  onClick = onDismissCandidates,
                  modifier = Modifier.testTag("dismiss_candidates_button"),
                ) {
                  Text("Dismiss All")
                }
              }
            }
          }
        }

        transaction != null -> {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = transaction.matchedLabel,
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
                Text(
                  text = "${(transaction.confidence * 100).toInt()}% confidence",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = transaction.id,
                style = MonospaceIdStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("latest_detected_id"),
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text =
                  "Source: ${transaction.sourceAppName ?: "Receipt"} • ${transaction.detectionSource.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }

        else -> {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text =
                  if (isMasterEnabled) "Waiting for payment receipt..."
                  else "No active Transaction ID",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text =
                  if (isMasterEnabled)
                    "Open a receipt in a selected payment app or tap Scan Receipt to extract locally."
                  else
                    "Turn Master Switch ON to detect receipts and enable automatic pasting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Button(
          onClick = onScanClick,
          modifier =
            Modifier.weight(1.3f)
              .minimumInteractiveComponentSize()
              .testTag("scan_receipt_button"),
        ) {
          Icon(
            imageVector = Icons.Default.DocumentScanner,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Scan Receipt", maxLines = 1)
        }

        FilledTonalButton(
          onClick = onCopyClick,
          enabled = transaction != null,
          modifier =
            Modifier.weight(1f)
              .minimumInteractiveComponentSize()
              .testTag("copy_id_button"),
        ) {
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text("Copy ID", maxLines = 1)
        }

        OutlinedButton(
          onClick = onClearClick,
          enabled = transaction != null || pendingCandidates.isNotEmpty(),
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("clear_id_button"),
          colors =
            ButtonDefaults.outlinedButtonColors(
              contentColor = MaterialTheme.colorScheme.error
            ),
        ) {
          Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = "Clear ID",
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }
  }
}

@Composable
fun TelemetryStatusCard(
  title: String,
  value: String,
  icon: ImageVector,
  accentColor: Color,
  testTag: String,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
) {
  Card(
    modifier =
      modifier
        .fillMaxWidth()
        .then(
          if (onClick != null) Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick)
          else Modifier
        )
        .testTag(testTag),
    shape = MaterialTheme.shapes.medium,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier =
          Modifier.size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = value,
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
