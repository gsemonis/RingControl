package com.sway.ringcontrol

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioManager
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
            ) == PackageManager.PERMISSION_GRANTED)
        )
    }
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val notificationPolicyState = remember {
        mutableStateOf(notificationManager.isNotificationPolicyAccessGranted)
    }
    var hasNotificationPolicyAccess by notificationPolicyState
    
    var setupFinished by remember { 
        mutableStateOf(sharedPrefs.getBoolean("setup_finished", false)) 
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasContactsPermission = permissions[Manifest.permission.READ_CONTACTS] ?: hasContactsPermission
        hasPhoneStatePermission = (permissions[Manifest.permission.READ_PHONE_STATE] ?: false) &&
                                  (permissions[Manifest.permission.READ_CALL_LOG] ?: false)
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
                ) == PackageManager.PERMISSION_GRANTED)
                
                notificationPolicyState.value = notificationManager.isNotificationPolicyAccessGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val allGranted = hasContactsPermission && hasPhoneStatePermission && hasNotificationPolicyAccess

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (setupFinished && allGranted) {
                CenterAlignedTopAppBar(
                    title = { Text("RingControl", style = MaterialTheme.typography.headlineMedium) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    actions = {
                        Button(
                            onClick = {
                                notificationPolicyState.value = notificationManager.isNotificationPolicyAccessGranted
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Refresh")
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        if (setupFinished && allGranted) {
            Column(modifier = Modifier.padding(innerPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Whitelisted Contacts", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { testAudioOverride(context) }) {
                        Text("Test Audio")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                RingControlContactList()
            }
        } else {
            OnboardingScreen(
                hasContacts = hasContactsPermission,
                hasPhone = hasPhoneStatePermission,
                hasDnd = hasNotificationPolicyAccess,
                onRequestPermissions = {
                    launcher.launch(
                        arrayOf(
                            Manifest.permission.READ_CONTACTS,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.READ_CALL_LOG,
                        )
                    )
                },
                onRequestDnd = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
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
                    ) == PackageManager.PERMISSION_GRANTED)
                    
                    notificationPolicyState.value = notificationManager.isNotificationPolicyAccessGranted
                }
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    hasContacts: Boolean,
    hasPhone: Boolean,
    hasDnd: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestDnd: () -> Unit,
    onComplete: () -> Unit,
    onRefresh: () -> Unit
) {
    var currentStep by remember { mutableIntStateOf(0) }
    val allGranted = hasContacts && hasPhone && hasDnd
    
    // Automatically advance steps if permissions are detected
    LaunchedEffect(hasContacts, hasPhone, hasDnd) {
        if (hasContacts && hasPhone && currentStep == 1) currentStep = 2
        if (hasDnd && currentStep == 2) currentStep = 3
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
                title = "Contacts & Phone",
                description = "We need permission to see your contacts so you can pick who can reach you, and 'Phone' access to detect when they are calling.",
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
            repeat(4) { index ->
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
fun RingControlContactList() {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(emptyList<RingControlContactData>()) }
    val sharedPrefs = remember { context.getSharedPreferences("RingControlPrefs", Context.MODE_PRIVATE) }
    var selectedNumbers by remember {
        mutableStateOf(sharedPrefs.getStringSet("selected_numbers", emptySet()) ?: emptySet())
    }
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
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredContacts) { contact ->
                RingControlContactRow(
                    contact = contact,
                    isSelected = selectedNumbers.contains(contact.phoneNumber)
                ) { isSelected ->
                    val newSelection = if (isSelected) {
                        selectedNumbers + contact.phoneNumber
                    } else {
                        selectedNumbers - contact.phoneNumber
                    }
                    selectedNumbers = newSelection
                    sharedPrefs.edit(commit = false) {
                        putStringSet("selected_numbers", newSelection)
                    }
                }
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
}

@Composable
fun RingControlContactRow(contact: RingControlContactData, isSelected: Boolean, onSelectedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(contact.name) },
        supportingContent = { Text(contact.phoneNumber) },
        leadingContent = {
            Icon(Icons.Default.Person, contentDescription = null)
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectedChange
            )
        }
    )
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

fun testAudioOverride(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // 1. Bypass DND if possible
    if (notificationManager.isNotificationPolicyAccessGranted) {
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    // 2. Set Volume to MAX and Mode to NORMAL
    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
    audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, AudioManager.FLAG_SHOW_UI)

    // Play a short ringtone sound to confirm it's loud
    try {
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val r = RingtoneManager.getRingtone(context, notification)
        r.play()
        // Stop it after 2 seconds so it doesn't annoy the user
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (r.isPlaying) r.stop()
        }, 2000)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
