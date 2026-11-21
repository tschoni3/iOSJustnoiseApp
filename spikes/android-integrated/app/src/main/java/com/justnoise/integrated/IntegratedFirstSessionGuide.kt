package com.justnoise.integrated

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.justnoise.gate0.R

/** Shared How-it-works guide for first Zap connection and Settings replay. */
@Composable
internal fun IntegratedHowItWorksGuide(
    continueLabel: Int = R.string.first_session_guide_done,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 568.dp).fillMaxWidth().fillMaxHeight()
                    .testTag("how-it-works-guide"),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape),
                    ) {
                        Icon(
                            painterResource(R.drawable.guide_close),
                            contentDescription = stringResource(R.string.first_session_close),
                            tint = Color.White,
                        )
                    }
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Text(
                        stringResource(R.string.first_session_guide_title),
                        style = IntegratedGuidanceTypography.title.copy(
                            color = Color.White, fontSize = 34.sp, lineHeight = 41.sp,
                        ),
                        modifier = Modifier.semantics { heading() },
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        IntegratedOnboardingMedia(
                            R.raw.jn_onboarding_intro,
                            isActive = true,
                            description = stringResource(R.string.first_session_guide_video),
                            unavailableCopyResource = R.string.first_session_guide_video_fallback,
                            modifier = Modifier.fillMaxWidth().height(maxWidth / (1350f / 1080f))
                                .clip(RoundedCornerShape(24.dp)),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        GuideStep(1, R.string.first_session_step_choose, R.string.first_session_guide_choose)
                        GuideStep(2, R.string.first_session_step_hold, R.string.first_session_step_hold_body)
                        GuideStep(3, R.string.first_session_step_done, R.string.first_session_step_done_body)
                    }
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 12.dp).heightIn(min = 56.dp),
                    shape = RoundedCornerShape(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White, contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        stringResource(continueLabel),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, title: Int, detail: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Box(
            Modifier.size(32.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = IntegratedGuidanceTypography.instruction.copy(color = Color.White))
            Text(
                stringResource(detail),
                style = IntegratedGuidanceTypography.explanation.copy(color = Color.White.copy(alpha = 0.65f)),
            )
        }
    }
}
