package org.example.project.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.example.project.core.data.AuthRepository
import org.example.project.core.utils.UiState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.core.navigation.ArrowForwardIcon
import org.example.project.core.navigation.EmailIcon
import org.example.project.core.navigation.LocationOnIcon
import org.example.project.core.navigation.LockIcon
import org.example.project.core.navigation.PersonIcon
import org.example.project.core.navigation.VisibilityIcon
import org.example.project.core.navigation.VisibilityOffIcon
import org.example.project.core.theme.AppColors
import kotlinproject.composeapp.generated.resources.Res
import kotlinproject.composeapp.generated.resources.logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(
    onLogin: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPassword: () -> Unit,
    registrationNotice: String? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var completingProfile by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var business by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var completionError by remember { mutableStateOf<String?>(null) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (completingProfile) androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isLoading) completingProfile = false },
        title = { Text("Concluir perfil profissional") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(fullName, { fullName = it.take(160) }, enabled = !isLoading, label = { Text("Seu nome") })
            OutlinedTextField(business, { business = it.take(160) }, enabled = !isLoading, label = { Text("Nome comercial") })
            androidx.compose.foundation.layout.Box {
                TextButton(enabled = !isLoading, onClick = { categoryExpanded = true }) { Text(category.ifBlank { "Escolher categoria" }) }
                androidx.compose.material3.DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    org.example.project.core.domain.models.discoveryCategories.forEach { item ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(item) }, onClick = { category = item; categoryExpanded = false })
                    }
                }
            }
            completionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !isLoading && fullName.isNotBlank() && business.isNotBlank() && category.isNotBlank(), onClick = {
            scope.launch {
                isLoading = true
                try {
                    org.example.project.core.data.completeProfessionalProfile(fullName, business, category)
                    completingProfile = false
                    onLogin("vendor")
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { completionError = "Não foi possível concluir o perfil. Confira a conexão e tente novamente." }
                finally { isLoading = false }
            }
        }) { Text("Concluir") } },
        dismissButton = { TextButton(enabled = !isLoading, onClick = { completingProfile = false }) { Text("Voltar") } }
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(60.dp))

            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = "Logo Tá Chegando",
                modifier = Modifier.size(200.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "T\u00e1 Chegando",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Encontre vendedores pr\u00f3ximos a voc\u00ea",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            registrationNotice?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(36.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = EmailIcon,
                        contentDescription = null,
                        tint = if (email.isNotEmpty()) AppColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Primary,
                    focusedLabelColor = AppColors.Primary,
                    focusedLeadingIconColor = AppColors.Primary,
                    cursorColor = AppColors.Primary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = LockIcon,
                        contentDescription = null,
                        tint = if (password.isNotEmpty()) AppColors.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) VisibilityOffIcon else VisibilityIcon,
                            contentDescription = if (passwordVisible) "Ocultar senha" else "Mostrar senha",
                            tint = AppColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Primary,
                    focusedLabelColor = AppColors.Primary,
                    focusedLeadingIconColor = AppColors.Primary,
                    cursorColor = AppColors.Primary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }

            if (errorMessage == org.example.project.core.data.ProfessionalProfileIncomplete().message) {
                TextButton(enabled = !isLoading, onClick = { completionError = null; completingProfile = true }) { Text("Concluir perfil profissional") }
            }
            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onForgotPassword,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    "Esqueceu a senha?",
                    color = AppColors.Primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            TextButton(enabled = !isLoading, onClick = {
                scope.launch {
                    isLoading = true
                    try {
                        when (val result = AuthRepository.resendConfirmation(email)) {
                            is UiState.Success -> { confirmationMessage = result.data; errorMessage = null }
                            is UiState.Error -> { confirmationMessage = null; errorMessage = result.message }
                            else -> Unit
                        }
                    } finally { isLoading = false }
                }
            }) { Text("Reenviar confirmação de email") }
            confirmationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Preencha todos os campos"
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val result = AuthRepository.signInAndResolveRole(email, password)
                        when (result) {
                            is UiState.Success -> {
                                isLoading = false
                                onLogin(result.data)
                            }
                            is UiState.Error -> {
                                isLoading = false
                                errorMessage = result.message
                            }
                            else -> {}
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        "Entrar",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = ArrowForwardIcon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "  ou  ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(onClick = onRegisterClick) {
                Icon(
                    imageVector = PersonIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = AppColors.Primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = AppColors.OnSurfaceVariant
                            )
                        ) {
                            append("Ainda não tem conta? ")
                        }
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Primary
                            )
                        ) {
                            append("Cadastre-se")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = LocationOnIcon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Geolocaliza\u00e7\u00e3o em tempo real",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
