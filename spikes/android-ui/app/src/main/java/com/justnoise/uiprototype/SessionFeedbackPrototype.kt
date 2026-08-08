package com.justnoise.uiprototype

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCompleteSheet(
    modeName: String,
    durationText: String,
    signalBoost: Int,
    onDone: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        containerColor = Color.White,
        contentColor = Color.Black,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Black.copy(alpha = 0.18f))
        },
    ) {
        SessionCompleteContent(
            modeName = modeName,
            durationText = durationText,
            signalBoost = signalBoost,
            onDone = onDone,
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, bottom = 28.dp),
        )
    }
}

@Composable
private fun SessionCompleteContent(
    modeName: String,
    durationText: String,
    signalBoost: Int,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = JustnoiseContract.text("session.complete.title").uppercase(),
                color = Color.Black.copy(alpha = 0.52f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = modeName,
                color = Color.Black,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = durationText,
                color = Color.Black,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = JustnoiseContract.text("session.complete.body"),
                color = Color.Black.copy(alpha = 0.52f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.055f), RoundedCornerShape(22.dp))
                .padding(horizontal = 24.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Signal Strengthened",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "+$signalBoost% stronger signal",
                color = Color.Black.copy(alpha = 0.52f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
            ),
        ) {
            Text("Done", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Text(
            text = "UI preview only · no session record was written",
            color = Color.Black.copy(alpha = 0.34f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Session complete · Release", widthDp = 412, heightDp = 500)
@Composable
private fun SessionCompletePreview() {
    JustnoiseTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
        ) {
            SessionCompleteContent(
                modeName = "Deep Work",
                durationText = "25m protected",
                signalBoost = 5,
                onDone = {},
                modifier = Modifier.padding(28.dp),
            )
        }
    }
}
