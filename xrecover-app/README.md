# XRecover v1.5.0

Android 10+ (API 29+) app to compose a support report and keep local status/history.

## Features
- Problem category, optional account number, visible error message, when it began, tried steps, and notes.
- Editable report preview, copy/share actions, local history and manually updated status.
- FAQ, official WhatsApp contact page, dark/light/system theme.
- Manual SMTP using host, port, sender, recipient and app password entered by the user.

## SMTP safety
- No hidden SMTP endpoint or hardcoded recipient.
- Sending happens only after the user taps **Kirim SMTP**.
- Only port 465 (SSL) and 587 (STARTTLS required) are permitted.
- Host/port/sender/recipient are saved locally. SMTP password is not saved and is cleared from UI state after send attempt.
- Use an app password from your mail provider, not your main password. Inspect the recipient before sending.
- XRecover is not an official WhatsApp app and cannot remove a ban or guarantee an appeal result. Prefer WhatsApp's Help > Contact Us or the official contact page.

## Build
JDK 17, Gradle 8.9, Android SDK platform 35 / Build Tools 35. CI creates an installable test release signed with the standard debug key. If the signature differs from an older installation, uninstall the old app first.
