package com.justnoise.integrated

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Lists own vertical gestures. A list boundary must never drag/rebound the whole editor.
 * ModalBottomSheet owns IME insets; children must not add a second imePadding.
 * Explicit Cancel/Done, system Back and the scrim retain normal dismissal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IntegratedStableSheet(
    mayDismiss: Boolean,
    onDismiss: () -> Unit,
    tag: String,
    containerColor: Color = Color.White,
    heightFraction: Float? = 0.93f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val canDismiss by rememberUpdatedState(mayDismiss)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || canDismiss })
    ModalBottomSheet(
        onDismissRequest = { if (canDismiss) onDismiss() },
        sheetState = sheet,
        sheetGesturesEnabled = false,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = containerColor,
        contentColor = Color.Black,
        contentWindowInsets = { WindowInsets.safeDrawing },
        properties = ModalBottomSheetProperties(isAppearanceLightStatusBars = false,
            isAppearanceLightNavigationBars = true, shouldDismissOnBackPress = canDismiss),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().then(if (heightFraction != null)
            Modifier.fillMaxHeight(heightFraction) else Modifier).padding(top = 8.dp).testTag(tag),
            content = content)
    }
}
