package org.example.project.features.auth

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.project.core.navigation.ArrowBackIcon
import org.example.project.core.navigation.ArrowForwardIcon
import org.example.project.core.navigation.EmailIcon
import org.example.project.core.navigation.LocationOnIcon
import org.example.project.core.navigation.LockIcon
import org.example.project.core.navigation.PersonIcon
import org.example.project.core.navigation.PhoneIcon
import org.example.project.core.navigation.StoreIcon
import org.example.project.core.theme.AppColors

@Composable
fun VendorRegisterScreen(
    onBack: () -> Unit,
    onRegisterComplete: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = ArrowBackIcon,
                        contentDescription = "Voltar",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(AppColors.Secondary, AppColors.SecondaryDark)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = StoreIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Cadastro Profissional",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = AppColors.Secondary
            )

            Text(
                text = "Preencha seus dados para come\u00e7ar a vender",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(28.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome completo") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = PersonIcon,
                        contentDescription = null,
                        tint = if (name.isNotEmpty()) AppColors.Secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Secondary,
                    focusedLabelColor = AppColors.Secondary,
                    cursorColor = AppColors.Secondary
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = businessName,
                onValueChange = { businessName = it },
                label = { Text("Nome do neg\u00f3cio (opcional)") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = StoreIcon,
                        contentDescription = null,
                        tint = if (businessName.isNotEmpty()) AppColors.Secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Secondary,
                    focusedLabelColor = AppColors.Secondary,
                    cursorColor = AppColors.Secondary
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Telefone (opcional)") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = PhoneIcon,
                        contentDescription = null,
                        tint = if (phone.isNotEmpty()) AppColors.Secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Secondary,
                    focusedLabelColor = AppColors.Secondary,
                    cursorColor = AppColors.Secondary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = EmailIcon,
                        contentDescription = null,
                        tint = if (email.isNotEmpty()) AppColors.Secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Secondary,
                    focusedLabelColor = AppColors.Secondary,
                    cursorColor = AppColors.Secondary
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = LockIcon,
                        contentDescription = null,
                        tint = if (password.isNotEmpty()) AppColors.Secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Secondary,
                    focusedLabelColor = AppColors.Secondary,
                    cursorColor = AppColors.Secondary
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
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    val trimmedEmail = email.trim()
                    val trimmedName = name.trim()
                    val trimmedBusinessName = businessName.trim()
                    val trimmedPhone = phone.trim()

                    if (trimmedName.isBlank()) {
                        errorMessage = "Informe seu nome."
                        return@Button
                    }
                    if (trimmedEmail.isBlank() || !trimmedEmail.contains("@")) {
                        errorMessage = "Digite um email válido."
                        return@Button
                    }
                    if (password.length < 8) {
                        errorMessage = "A senha precisa ter pelo menos 8 caracteres."
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val result = AuthRepository.signUp(
                            emailValue = trimmedEmail,
                            passwordValue = password,
                            nameValue = trimmedName,
                            businessNameValue = trimmedBusinessName.ifBlank { trimmedName },
                            phoneValue = trimmedPhone,
                            roleValue = "vendor"
                        )
                        isLoading = false
                        when (result) {
                            is UiState.Success -> onRegisterComplete()
                            is UiState.Error -> errorMessage = result.message
                            else -> {}
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Secondary
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        "Criar conta profissional",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)
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
        }
    }
}
