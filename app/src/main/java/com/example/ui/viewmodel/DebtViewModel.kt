package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class DebtViewModel(application: Application) : AndroidViewModel(application) {

    // Instantiate AppDatabase and Repository
    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = DebtRepository(db)

    // SyncService: GetX-inspired dynamic synchronization controller
    val syncService = SyncService(repository, viewModelScope)

    // Current Merchant: احمد, current customer: خالد
    val currentMerchantUser = User(
        id = "user_ahmed",
        name = "أحمد",
        email = "ahmed@alnahda.com",
        phoneNumber = "+966500000001"
    )

    val currentCustomerUser = User(
        id = "user_khaled",
        name = "خالد",
        email = "khaled@elhusami.com",
        phoneNumber = "+966500000002"
    )

    // Login State
    private val _loggedInUser = MutableStateFlow<User?>(null)
    val loggedInUser: StateFlow<User?> = _loggedInUser.asStateFlow()

    private val _loggedInRole = MutableStateFlow<String?>(null) // "merchant" or "customer"
    val loggedInRole: StateFlow<String?> = _loggedInRole.asStateFlow()

    fun loginWithEmailAndRole(email: String, expectedRole: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val (user, role) = repository.authenticateUser(email)
            if (user == null) {
                onResult(false, "عذراً، البريد الإلكتروني المدخل غير مسجل في النظام")
            } else if (role != expectedRole) {
                onResult(false, "عذراً، هذا الحساب لا يملك صلاحية الدخول كـ ${if (expectedRole == "merchant") "تاجر 🏪" else "عميل 📱"}")
            } else {
                _loggedInUser.value = user
                _loggedInRole.value = role

                // Log the sign in activity to Room SQLite Database
                repository.logActivity(
                    userId = user.id,
                    action = "تسجيل الدخول 🔑",
                    entityType = "users",
                    entityId = user.id,
                    details = "تم تسجيل الدخول بنجاح للمستخدم ${user.name} كـ ${if (role == "merchant") "تاجر" else "عميل"} عبر التحقق من SQLite."
                )
                onResult(true, null)
            }
        }
    }

    fun registerUser(name: String, email: String, passwordHash: String, selectedRole: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val (success, message) = repository.registerUser(name, email, passwordHash, selectedRole)
            onResult(success, message)
        }
    }

    fun logout() {
        val user = _loggedInUser.value
        if (user != null) {
            viewModelScope.launch {
                repository.logActivity(
                    userId = user.id,
                    action = "تسجيل الخروج 🚪",
                    entityType = "users",
                    entityId = user.id,
                    details = "تم تسجيل خروج المستخدم ${user.name} بنجاح."
                )
            }
        }
        _loggedInUser.value = null
        _loggedInRole.value = null
    }

    // Constant primary IDs for demo
    val nahdaStoreId = "store_nahda"
    val husamiFamilyId = "family_husami"

    // UI Observables
    val isOnlineState: StateFlow<Boolean> = repository.isOnlineState
        .asStateFlow()

    private val _syncingState = MutableStateFlow(false)
    val syncingState = _syncingState.asStateFlow()

    fun setOnline(online: Boolean) {
        repository.setOnlineStatus(online)
    }

    fun syncData(onSyncComplete: (Int) -> Unit = {}) {
        viewModelScope.launch {
            _syncingState.value = true
            // brief artificial delay for realism
            kotlinx.coroutines.delay(1000)
            val count = repository.syncUnsyncedTransactions()
            _syncingState.value = false
            onSyncComplete(count)
        }
    }

    val storesState: StateFlow<List<Store>> = repository.allStores
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val debtsState: StateFlow<List<DebtWithDetails>> = repository.allDebts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val requestsState: StateFlow<List<AccountRequestWithDetails>> = repository.allRequests
        .map { list ->
            // Filter pending requests for the store or family
            list.filter { it.request.status == "pending" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val familyMembersState: StateFlow<List<FamilyMemberWithUser>> = repository.getFamilyMembers(husamiFamilyId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notificationsState: StateFlow<List<Notification>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unsyncedCountState: StateFlow<Int> = repository.unsyncedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activityLogsState: StateFlow<List<ActivityLog>> = repository.allActivityLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active ledger / debt transactions
    private val _selectedDebtId = MutableStateFlow<String?>(null)
    val selectedDebtId = _selectedDebtId.asStateFlow()

    val activeTransactions: StateFlow<List<TransactionWithDetails>> = _selectedDebtId
        .flatMapLatest { debtId ->
            if (debtId != null) repository.getTransactionsForDebt(debtId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query for customer
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Filtered stores list for customer search
    val filteredStores: StateFlow<List<Store>> = combine(storesState, searchQuery) { stores, query ->
        if (query.isBlank()) {
            stores
        } else {
            stores.filter { it.name.contains(query, ignoreCase = true) || (it.address?.contains(query, ignoreCase = true) == true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectDebt(debtId: String?) {
        _selectedDebtId.value = debtId
    }

    // Business requests - Merchant Actions
    fun approveRequest(requestId: String) {
        viewModelScope.launch {
            repository.approveAccountRequest(requestId, currentMerchantUser.id)
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            repository.rejectAccountRequest(requestId, currentMerchantUser.id, "تم رفض الطلب بواسطة التاجر")
        }
    }

    fun saveTransaction(
        debtId: String,
        transactionType: String,
        amount: Double,
        description: String,
        selectedMemberId: String? = null
    ) {
        viewModelScope.launch {
            repository.addTransaction(
                debtId = debtId,
                actorId = currentMerchantUser.id,
                memberId = selectedMemberId ?: currentCustomerUser.id,
                transactionType = transactionType,
                amount = amount,
                description = description
            )
        }
    }

    // Business requests - Customer Actions
    fun requestAccount(storeId: String) {
        viewModelScope.launch {
            repository.addAccountRequest(
                familyId = husamiFamilyId,
                storeId = storeId,
                requestedBy = currentCustomerUser.id
            )
        }
    }

    fun addNewFamilyMember(name: String, phone: String, role: String) {
        viewModelScope.launch {
            repository.addFamilyMember(
                familyId = husamiFamilyId,
                name = name,
                phone = phone,
                role = role
            )
        }
    }
}
