package com.sway.ringcontrol

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sway.ringcontrol.ui.theme.RingControlTheme

/**
 * Data class representing contact information fetched from the phone.
 */
data class RingControlContactData(val id: String, val name: String, val phoneNumbers: List<String>)

/**
 * Main Activity of the application. Uses Compose for UI rendering.
 */
class RingControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RingControlTheme {
                RingControlAppRoot()
            }
        }
    }
}

/**
 * Root Composable that manages the high-level state of the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingControlAppRoot() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE) }
    
    var showCustomDialog by remember { mutableStateOf(false) }
    var editingContacts by remember { mutableStateOf<List<RingControlContactData>>(emptyList()) }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasPhoneStatePermission by remember {
        mutableStateOf(
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
        )
    }
    
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notificationPolicyState = remember {
        mutableStateOf(notificationManager.isNotificationPolicyAccessGranted)
    }
    var hasNotificationPolicyAccess by notificationPolicyState
    
    var hasNotificationListenerAccess by remember {
        mutableStateOf(
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) ?: false
        )
    }
    
    var setupFinished by remember { 
        mutableStateOf(sharedPrefs.getBoolean("setup_finished", false)) 
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasContactsPermission = permissions[Manifest.permission.READ_CONTACTS] ?: hasContactsPermission
        val phoneStatus = (permissions[Manifest.permission.READ_PHONE_STATE] ?: false) &&
                          (permissions[Manifest.permission.READ_CALL_LOG] ?: false) &&
                          (permissions[Manifest.permission.RECEIVE_SMS] ?: false)
        hasPhoneStatePermission = phoneStatus
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                
                hasPhoneStatePermission = (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
                
                notificationPolicyState.value = notificationManager.isNotificationPolicyAccessGranted
                hasNotificationListenerAccess = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) ?: false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allGranted = hasContactsPermission && hasPhoneStatePermission && hasNotificationPolicyAccess && hasNotificationListenerAccess

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (setupFinished && allGranted) {
                CenterAlignedTopAppBar(
                    title = { Text("RingControl", style = MaterialTheme.typography.headlineMedium) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                )
            }
        }
    ) { innerPadding ->
        if (setupFinished && allGranted) {
            Column(modifier = Modifier.padding(innerPadding)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Silence Non-Whitelisted", style = MaterialTheme.typography.titleMedium)
                    
                    var isGlobalSilence by remember { 
                        mutableStateOf(sharedPrefs.getBoolean("global_silence", false)) 
                    }
                    Switch(
                        checked = isGlobalSilence,
                        onCheckedChange = { 
                            isGlobalSilence = it
                            sharedPrefs.edit { putBoolean("global_silence", it) }
                        },
                        modifier = Modifier.scale(0.8f)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                Box(modifier = Modifier.fillMaxSize()) {
                    RingControlContactList { selectedContacts ->
                        editingContacts = selectedContacts
                        showCustomDialog = true
                    }
                }
            }
        } else {
            OnboardingScreen(
                hasContacts = hasContactsPermission,
                hasPhone = hasPhoneStatePermission,
                hasDnd = hasNotificationPolicyAccess,
                hasListener = hasNotificationListenerAccess,
                onRequestPermissions = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CALL_LOG,
                            Manifest.permission.RECEIVE_SMS,
                        )
                    )
                },
                onRequestDnd = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                },
                onRequestListener = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onComplete = {
                    sharedPrefs.edit { putBoolean("setup_finished", true) }
                    setupFinished = true
                },
                onRefresh = {
                    hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                    hasPhoneStatePermission = (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) &&
                            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) &&
                            (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
                    notificationPolicyState.value = notificationManager.isNotificationPolicyAccessGranted
                    hasNotificationListenerAccess = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) ?: false
                }
            )
        }
    }

    if (showCustomDialog && editingContacts.isNotEmpty()) {
        ContactCustomizationDialog(
            contacts = editingContacts,
            onDismiss = { 
                showCustomDialog = false 
                editingContacts = emptyList()
            },
            sharedPrefs = sharedPrefs
        )
    }
}

/**
 * Onboarding screen providing step-by-step guidance for granting permissions.
 */
@Composable
fun OnboardingScreen(
    hasContacts: Boolean,
    hasPhone: Boolean,
    hasDnd: Boolean,
    hasListener: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDnd: () -> Unit,
    onRequestListener: () -> Unit,
    onComplete: () -> Unit,
    onRefresh: () -> Unit,
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val allGranted = hasContacts && hasPhone && hasDnd && hasListener
    
    LaunchedEffect(hasContacts, hasPhone, hasDnd, hasListener) {
        if (((hasContacts && hasPhone) && (currentStep == 1))) currentStep = 2
        if ((hasDnd && (currentStep == 2))) currentStep = 3
        if ((hasListener && (currentStep == 3))) currentStep = 4
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (currentStep) {
            0 -> OnboardingStep(
                title = "Welcome to RingControl",
                description = "Never miss an important call again. RingControl allows selected contacts to reach you even when your phone is on silent or Do Not Disturb.",
                icon = Icons.Default.NotificationsActive,
                buttonText = "Get Started",
                onButtonClick = { currentStep = 1 }
            )
            1 -> OnboardingStep(
                title = "Contacts & Communication",
                description = "We need permission to see your contacts, and 'Phone' & 'SMS' access to detect when they are reaching out to you.",
                icon = Icons.Default.Call,
                buttonText = if (hasContacts && hasPhone) "Continue" else "Grant Access",
                onButtonClick = if (hasContacts && hasPhone) { { currentStep = 2 } } else onRequestPermissions,
                secondaryButton = {
                    if (!(hasContacts && hasPhone)) {
                        TextButton(onClick = onRefresh) { Text("Refresh Status") }
                    }
                }
            )
            2 -> OnboardingStep(
                title = "Silence Override",
                description = "To ring out loud during Do Not Disturb, we need 'Notification Policy' access. This lets the app temporarily bypass silence for your chosen contacts.",
                icon = Icons.Default.Shield,
                buttonText = if (hasDnd) "Continue" else "Open Settings",
                onButtonClick = if (hasDnd) { { currentStep = 3 } } else onRequestDnd,
                secondaryButton = {
                    if (!hasDnd) {
                        TextButton(onClick = onRefresh) { Text("Refresh Status") }
                    }
                }
            )
            3 -> OnboardingStep(
                title = "Message Detection",
                description = "To support RCS and modern chat apps, we need 'Notification Access'. This allows us to spot whitelisted messages even when your phone is locked.",
                icon = Icons.AutoMirrored.Filled.Message,
                buttonText = if (hasListener) "Continue" else "Allow Access",
                onButtonClick = if (hasListener) { { currentStep = 4 } } else onRequestListener,
                secondaryButton = {
                    if (!hasListener) {
                        TextButton(onClick = onRefresh) { Text("Refresh Status") }
                    }
                }
            )
            4 -> OnboardingStep(
                title = "You're All Set!",
                description = "All permissions have been granted. You can now go ahead and select the contacts you want to prioritize.",
                icon = Icons.Default.CheckCircle,
                buttonText = "Start Using App",
                enabled = allGranted,
                onButtonClick = onComplete
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { index ->
                Card(
                    modifier = Modifier.size(if (index == currentStep) 12.dp else 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = if (index == currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {}
            }
        }
    }
}

/**
 * Helper Composable for a single onboarding slide.
 */
@Composable
fun OnboardingStep(
    title: String,
    description: String,
    icon: ImageVector,
    buttonText: String,
    enabled: Boolean = true,
    onButtonClick: () -> Unit,
    secondaryButton: @Composable () -> Unit = {}
) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(32.dp))
    Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(48.dp))
    Button(
        onClick = onButtonClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.large
    ) {
        Text(buttonText)
    }
    secondaryButton()
}

/**
 * Screen showing the list of phone contacts with filtering and selection capabilities.
 */
@Composable
fun RingControlContactList(onConfigure: (List<RingControlContactData>) -> Unit) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(emptyList<RingControlContactData>()) }
    val sharedPrefs = remember { context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE) }
    
    var whitelistedNumbers by remember {
        mutableStateOf(sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet())
    }
    var blacklistedNumbers by remember {
        mutableStateOf(sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet())
    }
    var selectedForBatch by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    var whitelistedExpanded by remember { mutableStateOf(true) }
    var blacklistedExpanded by remember { mutableStateOf(true) }
    var othersExpanded by remember { mutableStateOf(true) }

    // Automatically expand groups if searching
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            whitelistedExpanded = true
            blacklistedExpanded = true
            othersExpanded = true
        }
    }

    val whitelistedGroup by remember {
        derivedStateOf {
            val base = if (searchQuery.isEmpty()) contacts
            else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumbers.any { num -> num.contains(searchQuery) } }
            base.filter { contact -> contact.phoneNumbers.any { whitelistedNumbers.contains(it) } }.sortedBy { it.name }
        }
    }
    
    val blacklistedGroup by remember {
        derivedStateOf {
            val base = if (searchQuery.isEmpty()) contacts
            else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumbers.any { num -> num.contains(searchQuery) } }
            base.filter { contact -> 
                contact.phoneNumbers.any { blacklistedNumbers.contains(it) } && 
                contact.phoneNumbers.none { whitelistedNumbers.contains(it) }
            }.sortedBy { it.name }
        }
    }
    
    val otherGroup by remember {
        derivedStateOf {
            val base = if (searchQuery.isEmpty()) contacts
            else contacts.filter { it.name.contains(searchQuery, ignoreCase = true) || it.phoneNumbers.any { num -> num.contains(searchQuery) } }
            base.filter { contact -> 
                contact.phoneNumbers.none { whitelistedNumbers.contains(it) } && 
                contact.phoneNumbers.none { blacklistedNumbers.contains(it) }
            }.sortedBy { it.name }
        }
    }

    LaunchedEffect(Unit) { contacts = fetchPhoneContacts(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                if (whitelistedGroup.isNotEmpty()) {
                    item { 
                        ListHeader(
                            text = "Whitelisted (${whitelistedGroup.size})", 
                            isExpanded = whitelistedExpanded,
                            onToggle = { whitelistedExpanded = !whitelistedExpanded }
                        ) 
                    }
                    if (whitelistedExpanded) {
                        items(whitelistedGroup) { contact ->
                            ContactRowInstance(contact, whitelistedNumbers, blacklistedNumbers, selectedForBatch, sharedPrefs) { newW, newB, newS ->
                                whitelistedNumbers = newW
                                blacklistedNumbers = newB
                                selectedForBatch = newS
                            }
                        }
                    }
                }

                if (blacklistedGroup.isNotEmpty()) {
                    item { 
                        ListHeader(
                            text = "Blacklisted (${blacklistedGroup.size})", 
                            isExpanded = blacklistedExpanded,
                            onToggle = { blacklistedExpanded = !blacklistedExpanded }
                        ) 
                    }
                    if (blacklistedExpanded) {
                        items(blacklistedGroup) { contact ->
                            ContactRowInstance(contact, whitelistedNumbers, blacklistedNumbers, selectedForBatch, sharedPrefs) { newW, newB, newS ->
                                whitelistedNumbers = newW
                                blacklistedNumbers = newB
                                selectedForBatch = newS
                            }
                        }
                    }
                }

                if (otherGroup.isNotEmpty()) {
                    item { 
                        ListHeader(
                            text = "Other Contacts (${otherGroup.size})", 
                            isExpanded = othersExpanded,
                            onToggle = { othersExpanded = !othersExpanded }
                        ) 
                    }
                    if (othersExpanded) {
                        items(otherGroup) { contact ->
                            ContactRowInstance(contact, whitelistedNumbers, blacklistedNumbers, selectedForBatch, sharedPrefs) { newW, newB, newS ->
                                whitelistedNumbers = newW
                                blacklistedNumbers = newB
                                selectedForBatch = newS
                            }
                        }
                    }
                }
                
                if (whitelistedGroup.isEmpty() && blacklistedGroup.isEmpty() && otherGroup.isEmpty() && contacts.isNotEmpty()) {
                    item {
                        Text("No contacts match", modifier = Modifier.fillMaxWidth().padding(32.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }

        if (selectedForBatch.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { 
                    onConfigure(contacts.filter { selectedForBatch.contains(it.id) }) 
                    selectedForBatch = emptySet() 
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Configure (${selectedForBatch.size})")
            }
        }
    }
}

@Composable
fun ListHeader(text: String, isExpanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.secondary
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun ContactRowInstance(
    contact: RingControlContactData,
    whitelistedNumbers: Set<String>,
    blacklistedNumbers: Set<String>,
    selectedForBatch: Set<String>,
    sharedPrefs: android.content.SharedPreferences,
    onUpdate: (Set<String>, Set<String>, Set<String>) -> Unit
) {
    val isWhitelisted = contact.phoneNumbers.any { whitelistedNumbers.contains(it) }
    val isBlacklisted = contact.phoneNumbers.any { blacklistedNumbers.contains(it) }
    val isSelectedForBatch = selectedForBatch.contains(contact.id)

    RingControlContactRow(
        contact = contact,
        isWhitelisted = isWhitelisted,
        isBlacklisted = isBlacklisted,
        isSelectedForBatch = isSelectedForBatch,
        onStateChange = { newState ->
            val newWhitelist = if (newState == 1) whitelistedNumbers + contact.phoneNumbers else whitelistedNumbers - contact.phoneNumbers.toSet()
            val newBlacklist = if (newState == -1) blacklistedNumbers + contact.phoneNumbers else blacklistedNumbers - contact.phoneNumbers.toSet()
            
            sharedPrefs.edit(commit = false) {
                putStringSet("selected_numbers", newWhitelist)
                putStringSet("blacklisted_numbers", newBlacklist)
                contact.phoneNumbers.forEach { putString("name_$it", contact.name) }
            }
            onUpdate(newWhitelist, newBlacklist, selectedForBatch)
        }
    ) { nowSelected ->
        val newBatch = if (nowSelected) selectedForBatch + contact.id else selectedForBatch - contact.id
        onUpdate(whitelistedNumbers, blacklistedNumbers, newBatch)
    }
}


/**
 * Single row in the contact list representing a contact and its current whitelist/blacklist status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingControlContactRow(
    contact: RingControlContactData, 
    isWhitelisted: Boolean,
    isBlacklisted: Boolean,
    isSelectedForBatch: Boolean,
    onStateChange: (Int) -> Unit,
    onBatchSelectChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val currentState = when {
        isWhitelisted -> 2
        isBlacklisted -> 0
        else -> 1
    }
    val options = listOf("Block", "Off", "Ring")

    ListItem(
        modifier = Modifier
            .combinedClickable(
                onClick = {
                    if (isSelectedForBatch) {
                        onBatchSelectChange(false)
                    }
                },
                onLongClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onBatchSelectChange(!isSelectedForBatch) 
                }
            ),
        headlineContent = { 
            Text(
                contact.name, 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isWhitelisted || isBlacklisted) FontWeight.Bold else FontWeight.Normal, 
                color = when {
                    isSelectedForBatch -> MaterialTheme.colorScheme.onTertiaryContainer
                    isWhitelisted -> MaterialTheme.colorScheme.primary
                    isBlacklisted -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
            ) 
        },
        supportingContent = {
            Column {
                Spacer(Modifier.height(4.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                            onClick = { onStateChange(index - 1) },
                            selected = index == currentState,
                            icon = {}, 
                            colors = if ((index == 0) && (currentState == 0)) {
                                SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.errorContainer, activeContentColor = MaterialTheme.colorScheme.error)
                            } else {
                                SegmentedButtonDefaults.colors()
                            }
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isSelectedForBatch) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
        )
    )
}

/**
 * Dialog for customizing ringtones and vibrations for specific contacts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCustomizationDialog(
    contacts: List<RingControlContactData>,
    onDismiss: () -> Unit,
    sharedPrefs: android.content.SharedPreferences
) {
    val context = LocalContext.current
    val isMultiSelect = contacts.size > 1
    
    var callAlwaysRing by remember { mutableStateOf(true) }
    var callVibrationPattern by remember { mutableStateOf("Default") }
    var callRingtoneName by remember { mutableStateOf("System Default") }
    var callRingtoneUri by remember { mutableStateOf<String?>(null) }

    var smsAlwaysRing by remember { mutableStateOf(false) }
    var smsVibrationPattern by remember { mutableStateOf("Default") }
    var smsRingtoneName by remember { mutableStateOf("System Default") }
    var smsRingtoneUri by remember { mutableStateOf<String?>(null) }
    
    var customMatchName by remember { mutableStateOf("") }

    LaunchedEffect(contacts) {
        if (!isMultiSelect) {
            val contact = contacts.first()
            val firstNum = contact.phoneNumbers.firstOrNull() ?: ""
            callAlwaysRing = sharedPrefs.getBoolean("always_ring_$firstNum", true)
            callVibrationPattern = sharedPrefs.getString("vib_$firstNum", "Default") ?: "Default"
            callRingtoneName = sharedPrefs.getString("ring_name_$firstNum", "System Default") ?: "System Default"
            callRingtoneUri = sharedPrefs.getString("ring_uri_$firstNum", null)

            smsAlwaysRing = sharedPrefs.getBoolean("sms_always_ring_$firstNum", false)
            smsVibrationPattern = sharedPrefs.getString("sms_vib_$firstNum", "Default") ?: "Default"
            smsRingtoneName = sharedPrefs.getString("sms_ring_name_$firstNum", "System Default") ?: "System Default"
            smsRingtoneUri = sharedPrefs.getString("sms_ring_uri_$firstNum", null)
            
            customMatchName = sharedPrefs.getString("custom_name_$firstNum", "") ?: ""
        }
    }
    
    val callPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let {
                callRingtoneName = RingtoneManager.getRingtone(context, it).getTitle(context)
                callRingtoneUri = it.toString()
            }
        }
    }

    val smsPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let {
                smsRingtoneName = RingtoneManager.getRingtone(context, it).getTitle(context)
                smsRingtoneUri = it.toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMultiSelect) "Setup ${contacts.size} Contacts" else "Setup ${contacts.first().name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                item {
                    Text("CALL ALERTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bypass Silence & Ring", modifier = Modifier.weight(1f))
                        Switch(checked = callAlwaysRing, onCheckedChange = { callAlwaysRing = it })
                    }
                    ListItem(
                        headlineContent = { Text("Sound") },
                        supportingContent = { Text(callRingtoneName) },
                        trailingContent = { TextButton(onClick = { callPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)) }) { Text("Pick") } }
                    )
                    VibrationDropdown(callVibrationPattern) { callVibrationPattern = it }
                }

                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("SMS/RCS ALERTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bypass Silence & Alert", modifier = Modifier.weight(1f))
                        Switch(checked = smsAlwaysRing, onCheckedChange = { smsAlwaysRing = it })
                    }
                    ListItem(
                        headlineContent = { Text("Sound") },
                        supportingContent = { Text(smsRingtoneName) },
                        trailingContent = { TextButton(onClick = { smsPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)) }) { Text("Pick") } }
                    )
                    VibrationDropdown(smsVibrationPattern) { smsVibrationPattern = it }
                }
                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("APP MATCHING (Facebook, etc.)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customMatchName,
                        onValueChange = { customMatchName = it },
                        label = { Text("Exact name shown in notifications") },
                        placeholder = { Text("e.g. John Smith") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = { Text("Used for matching apps like Messenger that don't use phone numbers.") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    sharedPrefs.edit {
                        contacts.forEach { contact ->
                            contact.phoneNumbers.forEach { num ->
                                putBoolean("always_ring_$num", callAlwaysRing)
                                putString("vib_$num", callVibrationPattern)
                                callRingtoneUri?.let { putString("ring_uri_$num", it) }
                                putString("ring_name_$num", callRingtoneName)

                                putBoolean("sms_always_ring_$num", smsAlwaysRing)
                                putString("sms_vib_$num", smsVibrationPattern)
                                smsRingtoneUri?.let { putString("sms_ring_uri_$num", it) }
                                putString("sms_ring_name_$num", smsRingtoneName)
                                putString("name_$num", contact.name)
                                putString("custom_name_$num", customMatchName)
                            }
                        }
                    }
                    onDismiss()
                }
            ) { Text("Save Settings") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Dropdown for choosing from predefined vibration patterns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationDropdown(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(value = false) }
    val patterns = listOf("Default", "Pulse", "Heartbeat", "SOS", "Rapid")

    Column {
        Text("Vibration", style = MaterialTheme.typography.labelSmall)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            TextField(
                value = current,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                patterns.forEach { pattern ->
                    DropdownMenuItem(text = { Text(pattern) }, onClick = { onSelect(pattern); expanded = false })
                }
            }
        }
    }
}

/**
 * Helper to fetch contacts using ContentResolver.
 */
fun fetchPhoneContacts(context: Context): List<RingControlContactData> {
    val contactMap = mutableMapOf<String, MutableList<String>>()
    val nameMap = mutableMapOf<String, String>()
    val contentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
        null, 
        null, 
        null, 
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    )

    cursor?.use {
        val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)

        while (it.moveToNext()) {
            if ((nameIndex >= 0) && (numberIndex >= 0) && (idIndex >= 0)) {
                val name = it.getString(nameIndex) ?: "Unknown"
                val number = it.getString(numberIndex)?.replace("\\s".toRegex(), "") ?: ""
                val id = it.getString(idIndex) ?: ""
                if (number.isNotEmpty()) {
                    contactMap.getOrPut(id) { mutableListOf() }.add(number)
                    nameMap[id] = name
                }
            }
        }
    }
    return contactMap.map { (id, numbers) -> 
        RingControlContactData(id, nameMap[id] ?: "Unknown", numbers.distinct()) 
    }.sortedBy { it.name }
}
