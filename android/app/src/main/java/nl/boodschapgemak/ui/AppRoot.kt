package nl.boodschapgemak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.boodschapgemak.data.AppViewModel
import nl.boodschapgemak.data.LiveStatus

private enum class Tab(val label: String, val icon: ImageVector) {
    List("Lijst", Icons.Outlined.ShoppingCart),
    Total("Totaal", Icons.Outlined.Payments),
    Recipes("Recepten", Icons.AutoMirrored.Outlined.MenuBook),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val configured by vm.configured.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val liveStatus by vm.live.collectAsStateWithLifecycle()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        val message = error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        vm.dismissError()
    }

    if (!configured || showSettings) {
        SetupScreen(
            vm = vm,
            canCancel = configured,
            onDone = { showSettings = false },
        )
        return
    }

    val tab = Tab.entries[tabIndex]

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tab.label)
                        LiveDot(liveStatus, Modifier.padding(start = 8.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Instellingen")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { index, entry ->
                    NavigationBarItem(
                        selected = index == tabIndex,
                        onClick = { tabIndex = index },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (tab) {
                Tab.List -> ShoppingListScreen(vm)
                Tab.Total -> TotalScreen(vm)
                Tab.Recipes -> RecipesScreen(vm)
            }
        }
    }
}

/** Green when the push socket is up, amber while reconnecting, grey when offline. */
@Composable
private fun LiveDot(status: LiveStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        LiveStatus.Live -> Color(0xFF3DDC84)
        LiveStatus.Connecting -> Color(0xFFFFB300)
        LiveStatus.Offline -> MaterialTheme.colorScheme.outline
    }
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier.size(10.dp).background(color, CircleShape))
    }
}
