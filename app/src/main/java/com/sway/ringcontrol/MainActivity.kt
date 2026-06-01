package com.sway.ringcontrol

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.RingtoneManager
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sway.ringcontrol.ui.theme.RingControlTheme

data class RingControlContactData(val id: String, val name: String, val phoneNumber: String)

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
fun RingControlAppRoot() {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE) }
    
    // Customization Dialog State
    var showCustomDialog by remember { mutableStateOf(false) }
    var editingContacts by remember { mutableStateOf<List<RingControlContactData>>(emptyList()) }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var hasPhoneStatePermission by remember {
        mutableStateOf(
            (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALL_LOG
            ) == PackageManager.PERMISSION_GRANTED) &&
            (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED)
        )
    }
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        hasPhoneStatePermission = (permissions[Manifest.permission.READ_PHONE_STATE] ?: false) &&
                                  (permissions[Manifest.permission.READ_CALL_LOG] ?: false) &&
                                  (permissions[Manifest.permission.RECEIVE_SMS] ?: false)
    }

    // Automatically refresh permissions when returning to the app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasContactsPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED
                
                hasPhoneStatePermission = (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALL_LOG
                ) == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECEIVE_SMS
                ) == PackageManager.PERMISSION_GRANTED)
                
                notificationPolicyState.value = notificationManager.isNotificationPolicyAccessGranted
                
                hasNotificationListenerAccess = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) ?: false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Whitelisted Contacts", style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                Box(modifier = Modifier.fillMaxSize()) {
                    RingControlContactList(
                        onConfigure = { selectedContacts ->
                            editingContacts = selectedContacts
                            showCustomDialog = true
                        }
                    )
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
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    context.startActivity(intent)
                },
                onRequestListener = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    context.startActivity(intent)
                },
                onComplete = {
                    sharedPrefs.edit { putBoolean("setup_finished", true) }
                    setupFinished = true
                },
                onRefresh = {
                    hasContactsPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED
                    
                    hasPhoneStatePermission = (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_PHONE_STATE
                    ) == PackageManager.PERMISSION_GRANTED) &&
                    (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CALL_LOG
                    ) == PackageManager.PERMISSION_GRANTED) &&
                    (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECEIVE_SMS
                    ) == PackageManager.PERMISSION_GRANTED)
                    
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
    onRefresh: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val allGranted = hasContacts && hasPhone && hasDnd && hasListener
    
    // Automatically advance steps if permissions are detected
    LaunchedEffect(hasContacts, hasPhone, hasDnd, hasListener) {
        if (hasContacts && hasPhone && currentStep == 1) currentStep = 2
        if (hasDnd && currentStep == 2) currentStep = 3
        if (hasListener && currentStep == 3) currentStep = 4
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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
        
        // Step Indicator
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { index ->
                Card(
                    modifier = Modifier.size(if (index == currentStep) 12.dp else 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = if (index == currentStep) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.outlineVariant
                    )
                ) {}
            }
        }
    }
}

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
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(100.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(32.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineLarge,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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

@Composable
fun RingControlContactList(onConfigure: (List<RingControlContactData>) -> Unit) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(emptyList<RingControlContactData>()) }
    val sharedPrefs = remember { context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE) }
    var whitelistedNumbers by remember {
        mutableStateOf(sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet())
    }
    var selectedForBatch by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts by remember {
        derivedStateOf {
            if (searchQuery.isEmpty()) {
                contacts
            } else {
                contacts.filter { 
                    it.name.contains(searchQuery, ignoreCase = true) || 
                    it.phoneNumber.contains(searchQuery) 
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        contacts = fetchPhoneContacts(context)
    }

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

            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp) // Space for FAB
            ) {
                items(filteredContacts) { contact ->
                    val isWhitelisted = whitelistedNumbers.contains(contact.phoneNumber)
                    val isSelectedForBatch = selectedForBatch.contains(contact.phoneNumber)
                    
                    RingControlContactRow(
                        contact = contact,
                        isWhitelisted = isWhitelisted,
                        isSelectedForBatch = isSelectedForBatch,
                        onWhitelistedChange = { nowWhitelisted ->
                            val newWhitelist = if (nowWhitelisted) {
                                whitelistedNumbers + contact.phoneNumber
                            } else {
                                whitelistedNumbers - contact.phoneNumber
                            }
                            whitelistedNumbers = newWhitelist
                            sharedPrefs.edit(commit = false) {
                                putStringSet("selected_numbers", newWhitelist)
                                // Store name mapping for Notification Listener matching
                                putString("name_${contact.phoneNumber}", contact.name)
                            }
                        },
                        onBatchSelectChange = { nowSelected ->
                            selectedForBatch = if (nowSelected) {
                                selectedForBatch + contact.phoneNumber
                            } else {
                                selectedForBatch - contact.phoneNumber
                            }
                        }
                    )
                }
                
                if (filteredContacts.isEmpty() && contacts.isNotEmpty()) {
                    item {
                        Text(
                            "No contacts match your search",
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }

        if (selectedForBatch.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { 
                    val selectedList = contacts.filter { selectedForBatch.contains(it.phoneNumber) }
                    onConfigure(selectedList)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Configure (${selectedForBatch.size})")
            }
        }
    }
}

@Composable
fun RingControlContactRow(
    contact: RingControlContactData, 
    isWhitelisted: Boolean,
    isSelectedForBatch: Boolean,
    onWhitelistedChange: (Boolean) -> Unit,
    onBatchSelectChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                contact.name, 
                fontWeight = if (isWhitelisted) FontWeight.Bold else FontWeight.Normal,
                color = if (isWhitelisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            ) 
        },
        supportingContent = { Text(contact.phoneNumber) },
        leadingContent = {
            Checkbox(
                checked = isSelectedForBatch,
                onCheckedChange = onBatchSelectChange
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isWhitelisted) "Whitelisted" else "Disabled", 
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWhitelisted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = isWhitelisted,
                    onCheckedChange = onWhitelistedChange
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactCustomizationDialog(
    contacts: List<RingControlContactData>,
    onDismiss: () -> Unit,
    sharedPrefs: android.content.SharedPreferences
) {
    val context = LocalContext.current
    val isMultiSelect = contacts.size > 1
    
    // For multi-select, we start with blank/default states
    var callAlwaysRing by remember { mutableStateOf(false) }
    var callVibrationPattern by remember { mutableStateOf("Default") }
    var callRingtoneName by remember { mutableStateOf("System Default") }
    var callRingtoneUri by remember { mutableStateOf<String?>(null) }

    var smsAlwaysRing by remember { mutableStateOf(false) }
    var smsVibrationPattern by remember { mutableStateOf("Default") }
    var smsRingtoneName by remember { mutableStateOf("System Default") }
    var smsRingtoneUri by remember { mutableStateOf<String?>(null) }

    // If single select, load existing values
    LaunchedEffect(contacts) {
        if (!isMultiSelect) {
            val contact = contacts.first()
            callAlwaysRing = sharedPrefs.getBoolean("always_ring_${contact.phoneNumber}", true)
            callVibrationPattern = sharedPrefs.getString("vib_${contact.phoneNumber}", "Default") ?: "Default"
            callRingtoneName = sharedPrefs.getString("ring_name_${contact.phoneNumber}", "System Default") ?: "System Default"
            callRingtoneUri = sharedPrefs.getString("ring_uri_${contact.phoneNumber}", null)

            smsAlwaysRing = sharedPrefs.getBoolean("sms_always_ring_${contact.phoneNumber}", false)
            smsVibrationPattern = sharedPrefs.getString("sms_vib_${contact.phoneNumber}", "Default") ?: "Default"
            smsRingtoneName = sharedPrefs.getString("sms_ring_name_${contact.phoneNumber}", "System Default") ?: "System Default"
            smsRingtoneUri = sharedPrefs.getString("sms_ring_uri_${contact.phoneNumber}", null)
        }
    }
    
    val callRingtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            uri?.let {
                val ringtone = RingtoneManager.getRingtone(context, it)
                callRingtoneName = ringtone.getTitle(context)
                callRingtoneUri = it.toString()
            }
        }
    }

    val smsRingtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == ComponentActivity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            uri?.let {
                val ringtone = RingtoneManager.getRingtone(context, it)
                smsRingtoneName = ringtone.getTitle(context)
                smsRingtoneUri = it.toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isMultiSelect) "Configure ${contacts.size} Contacts" else "Configure ${contacts.first().name}") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                item {
                    Text("CALL SETTINGS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    
                    // Always Ring Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Always Ring (Bypass Silence)", modifier = Modifier.weight(1f))
                        Switch(checked = callAlwaysRing, onCheckedChange = { callAlwaysRing = it })
                    }
                    
                    // Ringtone
                    ListItem(
                        headlineContent = { Text("Ringtone") },
                        supportingContent = { Text(callRingtoneName) },
                        trailingContent = {
                            TextButton(onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                                }
                                callRingtonePicker.launch(intent)
                            }) { Text("Pick") }
                        }
                    )

                    // Vibration
                    VibrationDropdown(callVibrationPattern) { callVibrationPattern = it }
                }

                item {
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("SMS SETTINGS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    // Always Ring Toggle
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Always Alert (Bypass Silence)", modifier = Modifier.weight(1f))
                        Switch(checked = smsAlwaysRing, onCheckedChange = { smsAlwaysRing = it })
                    }

                    // SMS Sound
                    ListItem(
                        headlineContent = { Text("Notification Sound") },
                        supportingContent = { Text(smsRingtoneName) },
                        trailingContent = {
                            TextButton(onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                                }
                                smsRingtonePicker.launch(intent)
                            }) { Text("Pick") }
                        }
                    )

                    // SMS Vibration
                    VibrationDropdown(smsVibrationPattern) { smsVibrationPattern = it }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                sharedPrefs.edit {
                    contacts.forEach { contact ->
                        putBoolean("always_ring_${contact.phoneNumber}", callAlwaysRing)
                        putString("vib_${contact.phoneNumber}", callVibrationPattern)
                        callRingtoneUri?.let { putString("ring_uri_${contact.phoneNumber}", it) }
                        putString("ring_name_${contact.phoneNumber}", callRingtoneName)

                        putBoolean("sms_always_ring_${contact.phoneNumber}", smsAlwaysRing)
                        putString("sms_vib_${contact.phoneNumber}", smsVibrationPattern)
                        smsRingtoneUri?.let { putString("sms_ring_uri_${contact.phoneNumber}", it) }
                        putString("sms_ring_name_${contact.phoneNumber}", smsRingtoneName)
                        
                        // Ensure name is stored for Notification Matching
                        putString("name_${contact.phoneNumber}", contact.name)
                    }
                }
                onDismiss()
            }) { Text("Save Settings") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibrationDropdown(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val patterns = listOf("Default", "Pulse", "Heartbeat", "SOS", "Rapid")

    Column {
        Text("Vibration Pattern", style = MaterialTheme.typography.labelSmall)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                value = current,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = ExposedDropdownMenuDefaults.textFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                patterns.forEach { pattern ->
                    DropdownMenuItem(
                        text = { Text(pattern) },
                        onClick = {
                            onSelect(pattern)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
fun fetchPhoneContacts(context: Context): List<RingControlContactData> {
    val contactList = mutableListOf<RingControlContactData>()
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
                    contactList.add(RingControlContactData(id, name, number))
                }
            }
        }
    }
    return contactList.distinctBy { it.phoneNumber }
}
