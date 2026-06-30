package com.haptiq.app.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class PermissionViewModel : ViewModel() {
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
