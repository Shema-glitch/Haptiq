package com.haptiq.app.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class PermissionUiState(
    val hasMediaPermission: Boolean = false,
    val permissionDenied: Boolean = false,
    val permissionDeniedPermanently: Boolean = false
)

sealed interface PermissionUiEvent {
    object NavigateToLibrary : PermissionUiEvent
}

sealed interface PermissionUiAction {
    object GrantPermission : PermissionUiAction
    object DenyPermission : PermissionUiAction
    object PermanentDenyPermission : PermissionUiAction
}

@HiltViewModel
class PermissionViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionUiState())
    val uiState: StateFlow<PermissionUiState> = _uiState.asStateFlow()

    fun handleAction(action: PermissionUiAction) {
        when (action) {
            is PermissionUiAction.GrantPermission -> {
                _uiState.value = _uiState.value.copy(
                    hasMediaPermission = true,
                    permissionDenied = false,
                    permissionDeniedPermanently = false
                )
            }
            is PermissionUiAction.DenyPermission -> {
                _uiState.value = _uiState.value.copy(
                    hasMediaPermission = false,
                    permissionDenied = true,
                    permissionDeniedPermanently = false
                )
            }
            is PermissionUiAction.PermanentDenyPermission -> {
                _uiState.value = _uiState.value.copy(
                    hasMediaPermission = false,
                    permissionDenied = true,
                    permissionDeniedPermanently = true
                )
            }
        }
    }
}
