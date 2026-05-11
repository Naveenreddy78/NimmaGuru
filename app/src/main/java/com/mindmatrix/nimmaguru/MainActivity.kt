package com.mindmatrix.nimmaguru

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.google.android.gms.security.ProviderInstaller
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.Timestamp
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Resolve security provider errors
        try {
            ProviderInstaller.installIfNeeded(this)
        } catch (e: Exception) {
            Log.e("NimmaGuru", "Security provider installation failed", e)
        }

        val firebaseReady = FirebaseApp.initializeApp(this) != null
        setContent {
            NimmaGuruTheme {
                NimmaGuruApp(this, firebaseReady)
            }
        }
    }
}

private enum class UserRole { Teacher, Student }
private enum class AppScreen { SignIn, SignUp, Profile, Dashboard, GuruDetail, Thanks, Calendar, Fame, CreateSession }
private enum class AppTab { Home, Calendar, Fame }
private enum class SignInMethod { UsernamePassword, PhoneOtp }

data class Guru(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
    val skills: List<String> = emptyList(),
    val village: String = "",
    val street: String = "",
    val bio: String = "",
    val availability: List<String> = emptyList(),
    val appreciationCount: Int = 0,
    val showPhone: Boolean = false
)

private data class Student(
    val id: String = "",
    val name: String = "",
    val village: String = "",
    val grade: String = "",
    val interests: List<String> = emptyList()
)

private data class Session(
    val id: String = "",
    val guruId: String = "",
    val guruName: String = "",
    val skill: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: String = "",
    val location: String = "",
    val status: String = ""
)

private data class Appreciation(
    val guruId: String = "",
    val studentName: String = "",
    val message: String = "",
    val rating: Int = 0,
    val timestamp: Timestamp? = null
)

private data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val phone: String = "",
    val email: String = "",
    val role: UserRole = UserRole.Student
)

private data class AppState(
    val screen: AppScreen = AppScreen.SignIn,
    val signInMethod: SignInMethod = SignInMethod.UsernamePassword,
    val firebaseReady: Boolean = true,
    val isLoading: Boolean = false,
    val message: String? = null,
    val userProfile: UserProfile? = null,
    val username: String = "",
    val password: String = "",
    val email: String = "",
    val signupRole: UserRole = UserRole.Student,
    val phone: String = "",
    val verificationId: String? = null,
    val otpSent: Boolean = false,
    val signupVerificationId: String? = null,
    val signupOtpSent: Boolean = false,
    val currentGuru: Guru? = null,
    val currentStudent: Student? = null,
    val selectedGuruId: String? = null,
    val selectedTab: AppTab = AppTab.Home,
    val query: String = "",
    val skillFilter: String? = null,
    val gurus: List<Guru> = emptyList(),
    val students: List<Student> = emptyList(),
    val sessions: List<Session> = emptyList(),
    val appreciations: List<Appreciation> = emptyList()
)

private class NimmaGuruViewModel(firebaseReady: Boolean) : ViewModel() {
    private val auth: FirebaseAuth? = if (firebaseReady) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? = if (firebaseReady) FirebaseFirestore.getInstance() else null
    private val _state = MutableStateFlow(AppState(firebaseReady = firebaseReady))
    val state: StateFlow<AppState> = _state

    init {
        auth?.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                loadUserProfile(user.uid)
            } else {
                _state.update { 
                    it.copy(
                        userProfile = null, 
                        screen = AppScreen.SignIn, 
                        currentGuru = null, 
                        currentStudent = null,
                        message = null
                    ) 
                }
            }
        }
        observeGurus()
        observeSessions()
        observeAppreciations()
    }

    private fun observeGurus() {
        db?.collection("gurus")?.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Guru::class.java)?.copy(id = doc.id)
            } ?: emptyList()
            _state.update { it.copy(gurus = list) }
        }
    }

    private fun observeSessions() {
        db?.collection("sessions")?.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            val list = snapshot?.documents?.mapNotNull { doc ->
                val dateTimestamp = doc.getTimestamp("date")
                val localDate = dateTimestamp?.toDate()?.toInstant()?.atZone(ZoneId.systemDefault())?.toLocalDate() ?: LocalDate.now()
                Session(
                    id = doc.id,
                    guruId = doc.getString("guruId") ?: "",
                    guruName = doc.getString("guruName") ?: "",
                    skill = doc.getString("skill") ?: "",
                    date = localDate,
                    time = doc.getString("time") ?: "",
                    location = doc.getString("location") ?: "",
                    status = doc.getString("status") ?: ""
                )
            } ?: emptyList()
            _state.update { it.copy(sessions = list) }
        }
    }

    private fun observeAppreciations() {
        db?.collection("appreciations")?.addSnapshotListener { snapshot, e ->
            if (e != null) return@addSnapshotListener
            val list = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject(Appreciation::class.java)
            } ?: emptyList()
            val sorted = list.sortedByDescending { it.timestamp?.seconds ?: 0L }
            _state.update { it.copy(appreciations = sorted) }
        }
    }

    fun openSignIn() = _state.update { it.copy(screen = AppScreen.SignIn, message = null) }
    fun openSignUp() = _state.update { it.copy(screen = AppScreen.SignUp, message = null) }
    fun updateUsername(u: String) = _state.update { it.copy(username = u.trim()) }
    fun updatePassword(p: String) = _state.update { it.copy(password = p) }
    fun updatePhone(ph: String) = _state.update { it.copy(phone = ph.filter(Char::isDigit)) }
    fun updateSignupRole(r: UserRole) = _state.update { it.copy(signupRole = r) }
    fun updateQuery(q: String) = _state.update { it.copy(query = q) }
    fun updateSkillFilter(s: String?) = _state.update { it.copy(skillFilter = s) }
    fun openGuru(id: String) = _state.update { it.copy(selectedGuruId = id, screen = AppScreen.GuruDetail) }
    fun openThanks() = _state.update { it.copy(screen = AppScreen.Thanks) }
    fun openCreateSession() = _state.update { it.copy(screen = AppScreen.CreateSession) }
    fun openTab(tab: AppTab) = _state.update { it.copy(selectedTab = tab, screen = when(tab) { AppTab.Calendar -> AppScreen.Calendar; AppTab.Fame -> AppScreen.Fame; else -> AppScreen.Dashboard }) }
    fun backToDashboard() = _state.update { it.copy(screen = AppScreen.Dashboard, selectedTab = AppTab.Home) }

    fun sendSignupOtp(activity: ComponentActivity) {
        val s = _state.value
        if (s.username.isBlank() || s.password.length < 6 || s.phone.length != 10) { setMessage("Enter username, password (6+ chars), and a 10-digit phone."); return }
        setLoading(true)
        db?.collection("usernames")?.document(s.username.lowercase())?.get()
            ?.addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    setMessage("Username already exists.")
                } else {
                    val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                        override fun onVerificationCompleted(c: PhoneAuthCredential) = createAccountWithPhoneCredential(c)
                        override fun onVerificationFailed(e: FirebaseException) = fail(e)
                        override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                            _state.update { it.copy(signupVerificationId = id, signupOtpSent = true, isLoading = false, message = "OTP sent.") }
                        }
                    }
                    val options = PhoneAuthOptions.newBuilder(auth ?: return@addOnSuccessListener)
                        .setPhoneNumber("+91${s.phone}")
                        .setTimeout(60L, TimeUnit.SECONDS)
                        .setActivity(activity)
                        .setCallbacks(callbacks)
                        .build()
                    PhoneAuthProvider.verifyPhoneNumber(options)
                }
            }?.addOnFailureListener { fail(it) }
    }

    fun verifySignupOtp(otp: String) {
        val verificationId = _state.value.signupVerificationId ?: return
        setLoading(true)
        createAccountWithPhoneCredential(PhoneAuthProvider.getCredential(verificationId, otp))
    }

    private fun createAccountWithPhoneCredential(credential: PhoneAuthCredential) {
        val s = _state.value
        auth?.signInWithCredential(credential)?.addOnSuccessListener { result ->
            val user = result.user ?: return@addOnSuccessListener
            val authEmail = usernameAuthEmail(s.username)
            user.linkWithCredential(EmailAuthProvider.getCredential(authEmail, s.password)).addOnSuccessListener {
                saveAccountDocuments(UserProfile(user.uid, s.username, s.phone, authEmail, s.signupRole), s.username.lowercase())
            }.addOnFailureListener { fail(it) }
        }?.addOnFailureListener { fail(it) }
    }

    fun signInWithUsernameOrEmail() {
        val s = _state.value
        if (s.username.isBlank() || s.password.isBlank()) return
        setLoading(true)
        if ("@" in s.username) signInWithEmail(s.username, s.password) else {
            db?.collection("usernames")?.document(s.username.lowercase())?.get()?.addOnSuccessListener { snapshot ->
                val email = snapshot.getString("email")
                if (email.isNullOrBlank()) setMessage("User not found.") else signInWithEmail(email, s.password)
            }?.addOnFailureListener { fail(it) }
        }
    }

    private fun signInWithEmail(email: String, password: String) {
        auth?.signInWithEmailAndPassword(email, password)?.addOnSuccessListener { res -> 
            loadUserProfile(res.user?.uid ?: return@addOnSuccessListener) 
        }?.addOnFailureListener { fail(it) }
    }

    fun signOut() {
        auth?.signOut()
    }

    fun saveProfile(name: String, village: String, gradeOrBio: String, selectedSkills: List<String>, showPhone: Boolean) {
        val profile = _state.value.userProfile ?: return
        val userData = mapOf("uid" to profile.uid, "username" to name, "phone" to profile.phone, "email" to profile.email, "role" to profile.role.name.lowercase())
        db?.collection("users")?.document(profile.uid)?.set(userData, SetOptions.merge())
        
        if (profile.role == UserRole.Teacher) {
            val currentCount = _state.value.currentGuru?.appreciationCount ?: 0
            val guru = Guru(profile.uid, name, profile.phone, selectedSkills, village, "Main St", gradeOrBio, listOf("Mon 5 PM"), currentCount, showPhone)
            db?.collection("gurus")?.document(guru.id)?.set(guru.toFirestore())
            _state.update { it.copy(currentGuru = guru, screen = AppScreen.Dashboard) }
        } else {
            val student = Student(profile.uid, name, village, gradeOrBio, selectedSkills)
            db?.collection("students")?.document(student.id)?.set(student.toFirestore())
            _state.update { it.copy(currentStudent = student, screen = AppScreen.Dashboard) }
        }
    }

    fun postThanks(message: String, rating: Int) {
        val state = _state.value
        val guruId = state.selectedGuruId ?: return
        val studentName = state.currentStudent?.name ?: state.userProfile?.username ?: "Student"
        
        val note = mapOf(
            "guruId" to guruId,
            "studentName" to studentName,
            "message" to message.ifBlank { "Thank you for helping our community learn." },
            "rating" to rating,
            "timestamp" to FieldValue.serverTimestamp()
        )
        
        db?.collection("appreciations")?.add(note)
        db?.collection("gurus")?.document(guruId)?.update("appreciationCount", FieldValue.increment(1))
        
        _state.update { it.copy(screen = AppScreen.GuruDetail) }
    }

    fun createSession(skill: String, date: LocalDate, time: String, location: String) {
        val guru = _state.value.currentGuru ?: return
        val sessionData = mapOf(
            "guruId" to guru.id,
            "guruName" to guru.name,
            "skill" to skill,
            "date" to Timestamp(java.util.Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())),
            "time" to time,
            "location" to location,
            "status" to "Scheduled"
        )
        db?.collection("sessions")?.add(sessionData)?.addOnSuccessListener {
            _state.update { it.copy(screen = AppScreen.Calendar, selectedTab = AppTab.Calendar) }
        }?.addOnFailureListener { fail(it) }
    }

    private fun saveAccountDocuments(profile: UserProfile, normalizedUsername: String) {
        db?.collection("users")?.document(profile.uid)?.set(profile.toFirestore())?.addOnSuccessListener {
            db?.collection("usernames")?.document(normalizedUsername)?.set(mapOf("uid" to profile.uid, "email" to profile.email))
            _state.update { it.copy(userProfile = profile, screen = AppScreen.Profile, isLoading = false) }
        }?.addOnFailureListener { fail(it) }
    }

    private fun loadUserProfile(uid: String) {
        if (auth?.currentUser == null) return
        setLoading(true)
        db?.collection("users")?.document(uid)?.get()?.addOnSuccessListener { snapshot ->
            if (auth.currentUser == null) return@addOnSuccessListener
            
            if (snapshot == null || !snapshot.exists()) {
                val profile = UserProfile(uid, "NG User", auth.currentUser?.phoneNumber.orEmpty(), auth.currentUser?.email.orEmpty(), UserRole.Student)
                _state.update { it.copy(userProfile = profile, screen = AppScreen.Profile, isLoading = false) }
            } else {
                val role = if (snapshot.getString("role") == "teacher") UserRole.Teacher else UserRole.Student
                val profile = UserProfile(uid, snapshot.getString("username") ?: "", snapshot.getString("phone") ?: "", snapshot.getString("email") ?: "", role)
                _state.update { it.copy(userProfile = profile, screen = AppScreen.Dashboard, isLoading = false) }
                
                db?.collection(if (role == UserRole.Teacher) "gurus" else "students")?.document(uid)?.addSnapshotListener { d, _ ->
                    if (auth.currentUser != null) {
                        if (role == UserRole.Teacher) _state.update { it.copy(currentGuru = d?.toObject(Guru::class.java)?.copy(id = uid)) }
                        else _state.update { it.copy(currentStudent = d?.toObject(Student::class.java)?.copy(id = uid)) }
                    }
                }
            }
        }?.addOnFailureListener {
            if (auth.currentUser != null) fail(it)
        }
    }

    private fun setLoading(loading: Boolean) = _state.update { it.copy(isLoading = loading, message = null) }
    private fun setMessage(message: String) = _state.update { it.copy(isLoading = false, message = message) }
    private fun fail(error: Exception) = setMessage(error.localizedMessage ?: "Firebase error.")
    private fun usernameAuthEmail(username: String) = "${username.lowercase()}@nimmaguru.app"

    private fun Guru.toFirestore() = mapOf("name" to name, "phone" to phone, "skills" to skills, "village" to village, "bio" to bio, "appreciationCount" to appreciationCount, "showPhone" to showPhone)
    private fun Student.toFirestore() = mapOf("name" to name, "village" to village, "grade" to grade, "interests" to interests)
    private fun UserProfile.toFirestore() = mapOf("uid" to uid, "username" to username, "phone" to phone, "email" to email, "role" to role.name.lowercase())
}

@Composable
private fun NimmaGuruTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Color(0xFF2E7D32),
        secondary = Color(0xFFF57C00),
        background = Color.White,
        surface = Color(0xFFF8FAF8),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF212121),
        onSurface = Color(0xFF212121)
    )
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, content = content)
}

@Composable
private fun NimmaGuruApp(activity: ComponentActivity, firebaseReady: Boolean) {
    val viewModel = remember { NimmaGuruViewModel(firebaseReady) }
    val state by viewModel.state.collectAsState()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state.screen) {
            AppScreen.SignIn -> SignInScreen(state, viewModel::updateUsername, viewModel::updatePassword, viewModel::signInWithUsernameOrEmail, viewModel::openSignUp)
            AppScreen.SignUp -> SignUpScreen(state, viewModel::updateUsername, viewModel::updatePassword, viewModel::updatePhone, viewModel::updateSignupRole, { viewModel.sendSignupOtp(activity) }, viewModel::verifySignupOtp, viewModel::openSignIn)
            AppScreen.Profile -> ProfileScreen(state.userProfile?.role ?: UserRole.Student, viewModel::saveProfile)
            AppScreen.Dashboard -> DashboardScreen(state, viewModel)
            AppScreen.GuruDetail -> GuruDetailScreen(state, viewModel)
            AppScreen.Thanks -> ThanksScreen(viewModel::postThanks, viewModel::backToDashboard)
            AppScreen.Calendar -> CalendarScreen(state, viewModel::openTab, viewModel::openCreateSession)
            AppScreen.Fame -> FameScreen(state, viewModel::openTab)
            AppScreen.CreateSession -> CreateSessionScreen(state, viewModel::createSession, viewModel::backToDashboard)
        }
    }
}

@Composable
private fun SignInScreen(s: AppState, onU: (String) -> Unit, onP: (String) -> Unit, onSI: () -> Unit, onSU: () -> Unit) {
    Column(Modifier
        .fillMaxSize()
        .padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        AppMark(); Spacer(Modifier.height(24.dp))
        Text("Sign In", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Use your username and password.", color = Color(0xFF5A665A))
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(s.username, onU, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(s.password, onP, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(24.dp))
        Button(onSI, Modifier.fillMaxWidth(), enabled = !s.isLoading && s.username.isNotBlank() && s.password.isNotBlank()) { Text("Sign In") }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("New user?")
            TextButton(onSU) { Text("Create account") }
        }
        if (s.message != null) Text(s.message, color = Color.Red, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SignUpScreen(s: AppState, onU: (String) -> Unit, onP: (String) -> Unit, onPh: (String) -> Unit, onR: (UserRole) -> Unit, onSO: () -> Unit, onVO: (String) -> Unit, onSI: () -> Unit) {
    var otp by remember { mutableStateOf("") }
    LazyColumn(Modifier
        .fillMaxSize()
        .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        item { AppMark(); Spacer(Modifier.height(18.dp)) }
        item { Text("Sign Up", fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("Create your Student or Teacher account.", color = Color(0xFF5A665A)); Spacer(Modifier.height(24.dp)) }
        item { OutlinedTextField(s.username, onU, label = { Text("Username") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(s.password, onP, label = { Text("Password (6+ chars)") }, modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp), visualTransformation = PasswordVisualTransformation()) }
        item { OutlinedTextField(s.phone, onPh, label = { Text("+91 phone number") }, modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)) }
        item { RoleDropdown(s.signupRole, onR) }
        item { Button(onSO, Modifier
            .fillMaxWidth()
            .padding(top = 16.dp), enabled = !s.isLoading && s.username.isNotBlank() && s.password.length >= 6 && s.phone.length == 10) { Text(if (s.signupOtpSent) "Resend OTP" else "Send OTP") } }
        if (s.signupOtpSent) {
            item {
                OutlinedTextField(otp, { otp = it }, label = { Text("6-digit OTP") }, modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp))
                Button({ onVO(otp) }, Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp), enabled = otp.length == 6) { Text("Verify & Create Account") }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { Text("Already have an account?"); TextButton(onSI) { Text("Sign in") } } }
        if (s.message != null) item { Text(s.message, color = Color.Blue, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun RoleDropdown(role: UserRole, onR: (UserRole) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier
        .fillMaxWidth()
        .padding(top = 8.dp)) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text("Role: ${if (role == UserRole.Student) "Student" else "Teacher"}") }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Student") }, onClick = { onR(UserRole.Student); expanded = false })
            DropdownMenuItem(text = { Text("Teacher") }, onClick = { onR(UserRole.Teacher); expanded = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileScreen(role: UserRole, onSave: (String, String, String, List<String>, Boolean) -> Unit) {
    var n by remember { mutableStateOf("") }; var v by remember { mutableStateOf("") }; var d by remember { mutableStateOf("") }; var sp by remember { mutableStateOf(false) }; var sel by remember { mutableStateOf(setOf<String>()) }
    val skills = listOf("Mathematics", "Science", "Kannada", "English", "Carpentry", "Farming", "Health")
    Scaffold(topBar = { TopBar("Create Your Profile") }) { padding ->
        LazyColumn(Modifier
            .padding(padding)
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Box(Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0xFFE7F1E7)), contentAlignment = Alignment.Center) { Text(n.take(1).ifBlank { "NG" }, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32)) } }
            item { OutlinedTextField(n, { n = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(v, { v = it }, label = { Text("Village/City") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(d, { d = it }, label = { Text(if (role == UserRole.Teacher) "Bio" else "Grade") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("Interests / Skills", fontWeight = FontWeight.Bold); FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { skills.forEach { s -> FilterChip(s in sel, { sel = if (s in sel) sel - s else sel + s }, label = { Text(s) }) } } }
            if (role == UserRole.Teacher) item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Show phone to students"); Spacer(Modifier.weight(1f)); Switch(sp, { sp = it }) } }
            item { Button({ onSave(n, v, d, sel.toList(), sp) }, Modifier.fillMaxWidth(), enabled = n.isNotBlank() && v.isNotBlank()) { Text("Save Profile") } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardScreen(state: AppState, viewModel: NimmaGuruViewModel) {
    Scaffold(
        topBar = { TopBar("Dashboard", actions = { TextButton(viewModel::signOut) { Text("Sign out") } }) },
        bottomBar = { BottomNav(state.selectedTab, viewModel::openTab) }
    ) { p ->
        Box(Modifier.padding(p)) { if (state.userProfile?.role == UserRole.Teacher) GuruDashboard(state, viewModel) else StudentDashboard(state, viewModel) }
    }
}

@Composable
private fun GuruDashboard(state: AppState, viewModel: NimmaGuruViewModel) {
    val g = state.currentGuru
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Welcome, ${g?.name ?: "Guru"}", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Your community impact at a glance", color = Color(0xFF5A665A)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatCard("Appreciations", "${g?.appreciationCount ?: 0}", Modifier.weight(1f)); StatCard("Sessions", "${state.sessions.count { it.guruId == g?.id }}", Modifier.weight(1f)); StatCard("Students", "12", Modifier.weight(1f)) } }
        item { Button(viewModel::openCreateSession, Modifier.fillMaxWidth()) { Text("Create New Class Session") } }
        item { Text("Appreciation Wall", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        items(state.appreciations.filter { it.guruId == g?.id }) { AppreciationCard(it) }
    }
}

@Composable
private fun StudentDashboard(state: AppState, viewModel: NimmaGuruViewModel) {
    val filtered = state.gurus.filter { (it.name.contains(state.query, true) || it.village.contains(state.query, true)) && (state.skillFilter == null || state.skillFilter in it.skills) }
    LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Find a Guru", fontSize = 26.sp, fontWeight = FontWeight.Bold); OutlinedTextField(state.query, viewModel::updateQuery, label = { Text("Search gurus") }, modifier = Modifier.fillMaxWidth()) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(state.skillFilter == null, { viewModel.updateSkillFilter(null) }, label = { Text("All") }) }
            val skills = state.gurus.flatMap { it.skills }.distinct().sorted()
            items(skills) { s -> FilterChip(state.skillFilter == s, { viewModel.updateSkillFilter(s) }, label = { Text(s) }) }
        } }
        items(filtered) { g -> GuruListCard(g) { viewModel.openGuru(g.id) } }
    }
}

@Composable
private fun GuruDetailScreen(state: AppState, viewModel: NimmaGuruViewModel) {
    val context = LocalContext.current; val g = state.gurus.find { it.id == state.selectedGuruId } ?: return
    Scaffold(topBar = { TopBar(g.name, viewModel::backToDashboard) }) { p ->
        LazyColumn(Modifier
            .padding(p)
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Avatar(g.name, 76); Spacer(Modifier.width(14.dp)); Column { Text(g.name, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("${g.village} • ${g.appreciationCount} appreciations", color = Color(0xFF5A665A)) } } }
            item { Text(g.bio, fontSize = 16.sp) }
            item { Button({ val url = "https://wa.me/91${g.phone}?text=Namaste%20${Uri.encode(g.name)}"; context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }, Modifier.fillMaxWidth()) { Text("WhatsApp") } }
            item { OutlinedButton(viewModel::openThanks, Modifier.fillMaxWidth()) { Text("Post Thank You Note") } }
            item { Text("Appreciation Wall", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
            items(state.appreciations.filter { it.guruId == g.id }) { AppreciationCard(it) }
        }
    }
}

@Composable
private fun ThanksScreen(onPost: (String, Int) -> Unit, onCancel: () -> Unit) {
    var msg by remember { mutableStateOf("") }
    Scaffold(topBar = { TopBar("Say Thanks", onCancel) }) { p ->
        Column(Modifier
            .padding(p)
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedTextField(msg, { msg = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
            Button({ onPost(msg, 5) }, Modifier.fillMaxWidth(), enabled = msg.isNotBlank()) { Text("Post Appreciation") }
        }
    }
}

@Composable
private fun CreateSessionScreen(state: AppState, onCreate: (String, LocalDate, String, String) -> Unit, onCancel: () -> Unit) {
    var s by remember { mutableStateOf("") }; var t by remember { mutableStateOf("") }; var l by remember { mutableStateOf("") }; var dStr by remember { mutableStateOf(LocalDate.now().toString()) }
    val guru = state.currentGuru ?: return
    Scaffold(topBar = { TopBar("New Session", onCancel) }) { padding ->
        Column(Modifier
            .padding(padding)
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            var ex by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) { OutlinedButton({ ex = true }, Modifier.fillMaxWidth()) { Text(if (s.isEmpty()) "Select Skill" else s) }; DropdownMenu(ex, { ex = false }) { guru.skills.forEach { sk -> DropdownMenuItem(text = { Text(sk) }, onClick = { s = sk; ex = false }) } } }
            OutlinedTextField(dStr, { dStr = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(t, { t = it }, label = { Text("Time") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(l, { l = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
            Button({ try { onCreate(s, LocalDate.parse(dStr), t, l) } catch(e: Exception) {} }, Modifier.fillMaxWidth(), enabled = s.isNotBlank() && t.isNotBlank()) { Text("Publish Session") }
        }
    }
}

@Composable
private fun CalendarScreen(state: AppState, onTab: (AppTab) -> Unit, onCreate: () -> Unit) {
    Scaffold(topBar = { TopBar("Calendar") }, bottomBar = { BottomNav(state.selectedTab, onTab) }, floatingActionButton = { if (state.userProfile?.role == UserRole.Teacher) FloatingActionButton(onCreate, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White) { Text("+", fontSize = 24.sp) } }) { p ->
        LazyColumn(Modifier
            .padding(p)
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Upcoming Classes", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            items(state.sessions.sortedBy { it.date }) { s ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF8))) {
                    Column(Modifier.padding(16.dp)) { 
                        Text(s.skill, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 18.sp)
                        Text("${s.guruName} • ${s.date.format(DateTimeFormatter.ofPattern("dd MMM"))} • ${s.time} • ${s.location}") 
                    }
                }
            }
        }
    }
}

@Composable
private fun FameScreen(state: AppState, onTab: (AppTab) -> Unit) {
    Scaffold(topBar = { TopBar("Wall of Fame") }, bottomBar = { BottomNav(state.selectedTab, onTab) }) { p ->
        LazyColumn(Modifier
            .padding(p)
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Wall of Fame", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            items(state.gurus.sortedByDescending { it.appreciationCount }) { g -> GuruListCard(g) { /* open detail */ } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { if (onBack != null) TextButton(onBack) { Text("Back") } }, actions = { actions() }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White))
}

@Composable
private fun BottomNav(sel: AppTab, onTab: (AppTab) -> Unit) {
    NavigationBar(containerColor = Color.White) {
        NavigationBarItem(sel == AppTab.Home, { onTab(AppTab.Home) }, { Text("H") }, label = { Text("Home") })
        NavigationBarItem(sel == AppTab.Calendar, { onTab(AppTab.Calendar) }, { Text("C") }, label = { Text("Calendar") })
        NavigationBarItem(sel == AppTab.Fame, { onTab(AppTab.Fame) }, { Text("F") }, label = { Text("Fame") })
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color(0xFFF4FBF4)), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun GuruListCard(g: Guru, onClick: () -> Unit) {
    Card(Modifier
        .fillMaxWidth()
        .clickable { onClick() }
        .padding(vertical = 4.dp), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF8))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(g.name, 54); Spacer(Modifier.width(12.dp))
            Column { Text(g.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Text(g.skills.joinToString(", "), maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${g.village} • ${g.appreciationCount} thanks", color = Color(0xFF5A665A), fontSize = 14.sp) }
        }
    }
}

@Composable
private fun AppreciationCard(a: Appreciation) {
    Card(Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE7F1E7))) {
        Column(Modifier.padding(16.dp)) { Text(a.studentName, fontWeight = FontWeight.SemiBold); HorizontalDivider(Modifier.padding(vertical = 8.dp)); Text(a.message) }
    }
}

@Composable
private fun Avatar(name: String, size: Int) {
    Box(Modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(Color(0xFFE7F1E7)), Alignment.Center) { Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = (size/3).sp) }
}

@Composable
private fun AppMark() {
    Box(Modifier
        .size(104.dp)
        .clip(CircleShape)
        .background(Color(0xFF2E7D32)), Alignment.Center) { Text("NG", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
}

fun usernameAuthEmail(name: String) = "${name.lowercase()}@nimmaguru.app"
