# Nimma Guru Android App

Nimma Guru is a community mentorship Android application built using Kotlin and Jetpack Compose. The project helps students find local teachers or skilled community members, called Gurus, based on skills, village, availability, and learning interests.

The app is useful for students who want guidance in subjects or practical skills, and for teachers or mentors who want to share their knowledge with the community.

## Features

- Student and Teacher/Guru role-based signup
- Username and password login
- Phone OTP verification using Firebase Authentication
- Student profile creation with interests
- Guru profile creation with skills, village, bio, and availability
- Student dashboard to search and filter Gurus
- Guru detail page with profile information and WhatsApp contact
- Thank You/Appreciation notes for Gurus
- Guru dashboard with impact statistics
- Class/session creation by Gurus
- Calendar screen for upcoming classes
- Wall of Fame ranking based on appreciations
- English and Kannada string support
- Firebase Firestore database integration

## Tech Stack

- Kotlin
- Android Studio
- Jetpack Compose
- Material 3
- Firebase Authentication
- Firebase Firestore
- Gradle

## Setup Instructions

1. Install Android Studio.

2. Clone or download this project.

3. Open the project folder in Android Studio.

4. Let Gradle sync automatically.

5. Create a Firebase project from the Firebase Console.

6. Add an Android app in Firebase using this package name:

app/google-services.json
In Firebase Authentication, enable:
Email/Password
Phone
In Firestore, create a database and apply the rules from:
firestore.rules
Run the app on an Android emulator or a physical Android device.
Run Commands
To build the debug APK:

./gradlew assembleDebug
On Windows:

gradlew.bat assembleDebug
The generated APK will be available inside:

app/build/outputs/apk/debug/
Usage
Open the app.
Create an account as either a Student or Teacher.
Verify the phone number using OTP.
Complete the profile setup.
Students can search for Gurus by name, village, or skill.
Students can open a Guru profile, contact them through WhatsApp, and post appreciation notes.
Gurus can create learning sessions and view appreciation messages.
Users can view upcoming sessions in the Calendar screen.
The Wall of Fame shows Gurus ranked by appreciation count.
Screenshots / Demo
Add project screenshots here.

Project Structure
app/src/main/java/com/mindmatrix/nimmaguru/
Main files:

MainActivity.kt - UI screens and Jetpack Compose components
NimmaGuruViewModel.kt - app state, Firebase Authentication, and Firestore logic
AndroidManifest.xml - Android app configuration
firestore.rules - Firebase Firestore security rules
Minimum Requirements
Android Studio
Android SDK 26 or above
Kotlin support
Internet connection for Firebase services
Conclusion
Nimma Guru provides a simple digital platform for connecting students with local mentors. It supports learning, community participation, session management, and appreciation-based recognition for Gurus.
