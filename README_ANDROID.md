# Nimma Guru Android App

This workspace now contains a Kotlin Android project for the Nimma Guru community mentorship app described in `readme.md`.

## What Is Implemented

- Kotlin Android app module with Jetpack Compose UI
- Role-based onboarding for Guru and Student
- Firebase Email/Password sign-in using username lookup in Firestore
- Firebase Phone Authentication with OTP verification
- Firebase sign-up with username, password, phone OTP, and Student/Teacher role
- Sign out from the dashboard
- Guru and Student profile creation
- Student dashboard with skill/village search and filters
- Guru profile detail screen with availability, appreciation wall, and WhatsApp contact
- Thank You note posting with rating
- Guru dashboard with impact statistics
- Class calendar screen
- Wall of Fame ranking
- Kannada and English string resources
- Firebase dependencies and starter Firestore security rules



## Firebase Setup

The Gradle dependency setup includes Firebase Auth and Firestore. To connect a real Firebase project:

1. Create a Firebase Android app with package name `com.mindmatrix.nimmaguru`.
2. Download `google-services.json`.
3. Place it at `app/google-services.json`.
4. In Firebase Authentication, enable `Email/Password` and `Phone` sign-in providers.
5. In Firestore, create the database and publish or adapt `firestore.rules`.
6. Sync Gradle and run the app.



## Notes

- Minimum SDK is API 26 as required by the SOP.
- Compile SDK is 35 because it is installed on this machine.
- Debug APK output is generated at `.gradle-build-latest/app/outputs/apk/debug/app-debug.apk`.
