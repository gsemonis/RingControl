# RingControl

RingControl is an Android application that ensures you never miss important calls from your inner circle, even when your phone is set to Silent, Vibrate, or Do Not Disturb (DND).

## Features

- **Priority Contact Whitelisting**: Select specific contacts from your phone to bypass silence settings.
- **Smart Audio Override**: Automatically switches the ringer to "Normal" mode and maxes out the volume for whitelisted callers.
- **Automatic State Restoration**: Restores your phone's previous audio and DND settings as soon as the call ends or is answered.
- **Material 3 Design**: A modern, clean, and intuitive interface with full Dark Mode support.
- **Privacy-Focused Onboarding**: Clear, step-by-step guidance on why permissions are needed.
- **Searchable Contact List**: Quickly find the people who matter most.

## How It Works

RingControl uses a background `BroadcastReceiver` that listens for the `PHONE_STATE_CHANGED` intent. When a call arrives:
1. It identifies the incoming number (using Fallback methods for modern Android security).
2. It checks the number against your saved whitelist.
3. If matched, it saves your current Ringer Mode, Volume, and DND filter.
4. It overrides these settings to ensure the phone rings out loud.
5. Once the call is acknowledged (Offhook or Idle), it restores everything to exactly how you had it.

## Permissions

To function correctly, RingControl requires:
- **Read Contacts**: To display your contact list for selection.
- **Read Phone State & Call Log**: To detect incoming calls and identify the caller's number.
- **Modify Audio Settings**: To change the ringer mode and volume.
- **Do Not Disturb Access**: To bypass DND filters for prioritized callers.

*Note: For best results on Pixel and other modern devices, ensure the app is set to "Unrestricted" battery usage.*

## Installation

1. Clone the repository.
2. Open in Android Studio.
3. Build and deploy to your Android device (Android 12+ recommended).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
