package com.sway.ringcontrol

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sway.ringcontrol.ui.theme.RingControlTheme

/**
 * Data class representing contact information fetched from the phone.
 */
data class RingControlContactData(val id: String, val name: String, val phoneNumbers: List<String>)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingControlAppRoot(vm: RingControlViewModel = viewModel()) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE) }
    
    var showCustomDialog by remember { mutableStateOf(value = false) }
    var editingContacts by remember { mutableStateOf<List<RingControlContactData>>(emptyList()) }

    var hasContactsPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    var hasPhoneStatePermission by remember {
        mutableStateOf(
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED),
        )
    }
    
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    var hasNotificationPolicyAccess by remember { mutableStateOf(notificationManager.isNotificationPolicyAccessGranted) }
    var hasNotificationListenerAccess by remember {
        mutableStateOf(Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) ?: false)
    }
    
    var setupFinished by remember { mutableStateOf(sharedPrefs.getBoolean("setup_finished", false)) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        hasContactsPermission = permissions[Manifest.permission.READ_CONTACTS] ?: hasContactsPermission
        hasPhoneStatePermission = (permissions[Manifest.permission.READ_PHONE_STATE] ?: false) &&
                                 (permissions[Manifest.permission.READ_CALL_LOG] ?: false) &&
                                 (permissions[Manifest.permission.RECEIVE_SMS] ?: false)
        
        if (hasContactsPermission) vm.refreshContacts()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasContactsPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                hasPhoneStatePermission = (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) &&
                                        (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) &&
                                        (ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
                hasNotificationPolicyAccess = notificationManager.isNotificationPolicyAccessGranted
                hasNotificationListenerAccess = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) ?: false
                
                if (hasContactsPermission && vm.contacts.isEmpty()) vm.refreshContacts()
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
                SettingsSection(sharedPrefs)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                Box(modifier = Modifier.fillMaxSize()) {
                    RingControlContactList(vm) { selectedContacts ->
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
                        ),
                    )
                },
                onRequestDnd = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) },
                onRequestListener = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                onComplete = {
                    sharedPrefs.edit { putBoolean("setup_finished", true) }
                    setupFinished = true
                },
            )
        }
    }

    if (showCustomDialog) {
        ContactCustomizationDialog(
            contacts = editingContacts,
            onDismiss = { 
                showCustomDialog = false 
                editingContacts = emptyList()
            },
            sharedPrefs = sharedPrefs,
        )
    }
}

@Composable
fun SettingsSection(sharedPrefs: android.content.SharedPreferences) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Silence Non-Whitelisted", style = MaterialTheme.typography.titleMedium)
            var isGlobalSilence by remember { mutableStateOf(sharedPrefs.getBoolean("global_silence", false)) }
            Switch(
                checked = isGlobalSilence,
                onCheckedChange = { 
                    isGlobalSilence = it
                    sharedPrefs.edit { putBoolean("global_silence", it) }
                },
                modifier = Modifier.scale(0.8f)
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Decline Spam Calls", style = MaterialTheme.typography.titleMedium)
            var isSpamBlocking by remember { mutableStateOf(sharedPrefs.getBoolean("block_spam", true)) }
            Switch(
                checked = isSpamBlocking,
                onCheckedChange = { 
                    isSpamBlocking = it
                    sharedPrefs.edit { putBoolean("block_spam", it) }
                },
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    hasContacts: Boolean,
    hasPhone: Boolean,
    hasDnd: Boolean,
    hasListener: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDnd: () -> Unit,
    onRequestListener: () -> Unit,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val allGranted = hasContacts && hasPhone && hasDnd && hasListener
    
    LaunchedEffect(hasContacts, hasPhone, hasDnd, hasListener) {
        if ((hasContacts && hasPhone) && (currentStep == 1)) currentStep = 2
        if (hasDnd && (currentStep == 2)) currentStep = 3
        if (hasListener && (currentStep == 3)) currentStep = 4
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (currentStep) {
            0 -> OnboardingStep("Welcome to RingControl", "Prioritize important contacts even on silent.", Icons.Default.NotificationsActive, "Get Started") { currentStep = 1 }
            1 -> OnboardingStep("Contacts & Phone", "We need access to your contacts and call status.", Icons.Default.Call, if (hasContacts && hasPhone) "Continue" else "Grant Access") { if (hasContacts && hasPhone) currentStep = 2 else onRequestPermissions() }
            2 -> OnboardingStep("Silence Override", "Requires 'Notification Policy' access to bypass DND.", Icons.Default.Shield, if (hasDnd) "Continue" else "Open Settings") { if (hasDnd) currentStep = 3 else onRequestDnd() }
            3 -> OnboardingStep("Message Detection", "Allows monitoring RCS and chat apps.", Icons.AutoMirrored.Filled.Message, if (hasListener) "Continue" else "Allow Access") { if (hasListener) currentStep = 4 else onRequestListener() }
            4 -> OnboardingStep("Ready!", "You're all set to pick your priority contacts.", Icons.Default.CheckCircle, "Start Using App", enabled = allGranted, onButtonClick = onComplete)
        }
        
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { index ->
                Card(
                    modifier = Modifier.size(if (index == currentStep) 12.dp else 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = if (index == currentStep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {}
            }
        }
    }
}

@Composable
fun OnboardingStep(title: String, description: String, icon: ImageVector, buttonText: String, enabled: Boolean = true, onButtonClick: () -> Unit) {
    Icon(icon, null, modifier = Modifier.size(100.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(32.dp))
    Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text(description, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(48.dp))
    Button(onButtonClick, Modifier.fillMaxWidth().height(56.dp), enabled, shape = MaterialTheme.shapes.large) { Text(buttonText) }
}

@Composable
fun RingControlContactList(vm: RingControlViewModel, onConfigure: (List<RingControlContactData>) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE) }
    
    var whitelistedNumbers by remember { mutableStateOf(sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet()) }
    var blacklistedNumbers by remember { mutableStateOf(sharedPrefs.getStringSet("blacklisted_numbers", emptySet()) ?: emptySet()) }
    var selectedForBatch by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(vm.contacts, searchQuery, whitelistedNumbers, blacklistedNumbers) {
        val base = if (searchQuery.isEmpty()) vm.contacts 
        else vm.contacts.filter { it.name.contains(searchQuery, true) || it.phoneNumbers.any { n -> n.contains(searchQuery) } }
        
        val whitelisted = base.filter { c -> c.phoneNumbers.any { whitelistedNumbers.contains(it) } }
        val blacklisted = base.filter { c -> c.phoneNumbers.any { blacklistedNumbers.contains(it) } && c.phoneNumbers.none { whitelistedNumbers.contains(it) } }
        val others = base.filter { c -> c.phoneNumbers.none { whitelistedNumbers.contains(it) } && c.phoneNumbers.none { blacklistedNumbers.contains(it) } }
        
        listOf("Whitelisted" to whitelisted, "Blacklisted" to blacklisted, "Other Contacts" to others)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (vm.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                filteredContacts.forEach { (header, list) ->
                    if (list.isNotEmpty()) {
                        item { Text(header, Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary) }
                        items(list) { contact ->
                            ContactRow(contact, whitelistedNumbers, blacklistedNumbers, selectedForBatch.contains(contact.id), 
                                onStateChange = { newState ->
                                    val newW = if (newState == 1) whitelistedNumbers + contact.phoneNumbers else whitelistedNumbers - contact.phoneNumbers.toSet()
                                    val newB = if (newState == -1) blacklistedNumbers + contact.phoneNumbers else blacklistedNumbers - contact.phoneNumbers.toSet()
                                    sharedPrefs.edit {
                                        putStringSet("selected_numbers", newW)
                                        putStringSet("blacklisted_numbers", newB)
                                        contact.phoneNumbers.forEach { putString("name_$it", contact.name) }
                                    }
                                    whitelistedNumbers = newW
                                    blacklistedNumbers = newB
                                },
                                onToggleBatch = { selected ->
                                    selectedForBatch = if (selected) selectedForBatch + contact.id else selectedForBatch - contact.id
                                }
                            )
                        }
                    }
                }
            }
        }

        if (selectedForBatch.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { 
                    onConfigure(vm.contacts.filter { selectedForBatch.contains(it.id) }) 
                    selectedForBatch = emptySet() 
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Tune, null)
                Spacer(Modifier.width(8.dp))
                Text("Configure (${selectedForBatch.size})")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactRow(contact: RingControlContactData, whitelisted: Set<String>, blacklisted: Set<String>, isSelected: Boolean, onStateChange: (Int) -> Unit, onToggleBatch: (Boolean) -> Unit) {
    val isW = contact.phoneNumbers.any { whitelisted.contains(it) }
    val isB = contact.phoneNumbers.any { blacklisted.contains(it) }
    val haptic = LocalHapticFeedback.current
    val currentState = when { isW -> 2; isB -> 0; else -> 1 }
    val options = listOf("Block", "Off", "Ring")

    ListItem(
        modifier = Modifier.combinedClickable(onClick = { if (isSelected) onToggleBatch(false) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onToggleBatch(!isSelected) }),
        headlineContent = { Text(contact.name, style = MaterialTheme.typography.titleMedium, color = if (isW) MaterialTheme.colorScheme.primary else if (isB) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
        supportingContent = {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                options.forEachIndexed { index, label ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        onClick = { onStateChange(index - 1) },
                        selected = index == currentState,
                        icon = {},
                        colors = if (index == 0 && currentState == 0) SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.errorContainer, activeContentColor = MaterialTheme.colorScheme.error) else SegmentedButtonDefaults.colors()
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCustomizationDialog(contacts: List<RingControlContactData>, onDismiss: () -> Unit, sharedPrefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    val isMulti = contacts.size > 1
    var callAlways by remember { mutableStateOf(true) }
    var callVib by remember { mutableStateOf("Default") }
    var callRingName by remember { mutableStateOf("Default") }
    var callRingUri by remember { mutableStateOf<String?>(null) }
    var smsAlways by remember { mutableStateOf(false) }
    var smsVib by remember { mutableStateOf("Default") }
    var smsRingName by remember { mutableStateOf("Default") }
    var smsRingUri by remember { mutableStateOf<String?>(null) }
    var customName by remember { mutableStateOf("") }

    LaunchedEffect(contacts) {
        if (!isMulti) {
            val num = contacts.first().phoneNumbers.firstOrNull() ?: ""
            callAlways = sharedPrefs.getBoolean("always_ring_$num", true)
            callVib = sharedPrefs.getString("vib_$num", "Default") ?: "Default"
            callRingName = sharedPrefs.getString("ring_name_$num", "Default") ?: "Default"
            callRingUri = sharedPrefs.getString("ring_uri_$num", null)
            smsAlways = sharedPrefs.getBoolean("sms_always_ring_$num", false)
            smsVib = sharedPrefs.getString("sms_vib_$num", "Default") ?: "Default"
            smsRingName = sharedPrefs.getString("sms_ring_name_$num", "Default") ?: "Default"
            smsRingUri = sharedPrefs.getString("sms_ring_uri_$num", null)
            customName = sharedPrefs.getString("custom_name_$num", "") ?: ""
        }
    }

    val ringPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let { callRingUri = it.toString(); callRingName = RingtoneManager.getRingtone(context, it).getTitle(context) }
        }
    }
    
    val smsPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            uri?.let { smsRingUri = it.toString(); smsRingName = RingtoneManager.getRingtone(context, it).getTitle(context) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMulti) "Setup ${contacts.size} Contacts" else "Setup ${contacts.first().name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Text("CALL ALERTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Bypass Silence", Modifier.weight(1f)); Switch(callAlways, { callAlways = it }) }
                    ListItem(headlineContent = { Text("Sound") }, supportingContent = { Text(callRingName) }, trailingContent = { TextButton({ ringPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)) }) { Text("Pick") } })
                    VibrationDropdown(callVib) { callVib = it }
                }
                item { HorizontalDivider() }
                item {
                    Text("SMS/RCS ALERTS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Bypass Silence", Modifier.weight(1f)); Switch(smsAlways, { smsAlways = it }) }
                    ListItem(headlineContent = { Text("Sound") }, supportingContent = { Text(smsRingName) }, trailingContent = { TextButton({ smsPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)) }) { Text("Pick") } })
                    VibrationDropdown(smsVib) { smsVib = it }
                }
                if (!isMulti) {
                    item { HorizontalDivider() }
                    item {
                        Text("APP MATCHING", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(customName, { customName = it }, label = { Text("Exact name in notifications") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                sharedPrefs.edit {
                    contacts.forEach { c ->
                        c.phoneNumbers.forEach { n ->
                            putBoolean("always_ring_$n", callAlways); putString("vib_$n", callVib); callRingUri?.let { putString("ring_uri_$n", it) }; putString("ring_name_$n", callRingName)
                            putBoolean("sms_always_ring_$n", smsAlways); putString("sms_vib_$n", smsVib); smsRingUri?.let { putString("sms_ring_uri_$n", it) }; putString("sms_ring_name_$n", smsRingName)
                            putString("name_$n", c.name); putString("custom_name_$n", customName)
                        }
                    }
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationDropdown(current: String, onSelect: (String) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    val pats = listOf("Default", "Pulse", "Heartbeat", "SOS", "Rapid")
    ExposedDropdownMenuBox(exp, { exp = it }) {
        OutlinedTextField(current, {}, readOnly = true, label = { Text("Vibration") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(exp) }, modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth())
        ExposedDropdownMenu(exp, { exp = false }) {
            pats.forEach { p -> DropdownMenuItem({ Text(p) }, { onSelect(p); exp = false }) }
        }
    }
}
