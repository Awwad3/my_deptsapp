package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

class DebtRepository(val db: AppDatabase) {

    // DAOs
    private val userDao = db.userDao()
    private val familyDao = db.familyDao()
    private val storeDao = db.storeDao()
    private val accountRequestDao = db.accountRequestDao()
    private val debtDao = db.debtDao()
    private val transactionDao = db.transactionDao()
    private val notificationDao = db.notificationDao()
    private val activityLogDao = db.activityLogDao()
    private val debtPartyDao = db.debtPartyDao()

    // Exposed Flows
    val allStores: Flow<List<Store>> = storeDao.getAllStores()
    val allDebts: Flow<List<DebtWithDetails>> = debtDao.getDebtsWithDetails()
    val allRequests: Flow<List<AccountRequestWithDetails>> = accountRequestDao.getAccountRequestsWithDetails()
    val allNotifications: Flow<List<Notification>> = notificationDao.getAllNotifications()
    val allActivityLogs: Flow<List<ActivityLog>> = activityLogDao.getAllActivityLogs()
    val unsyncedCount: Flow<Int> = transactionDao.getUnsyncedCount()

    // Internet connectivity state & Sync
    val isOnlineState = MutableStateFlow(true)

    fun setOnlineStatus(online: Boolean) {
        isOnlineState.value = online
    }

    fun getTransactionsForDebt(debtId: String): Flow<List<TransactionWithDetails>> {
        return transactionDao.getTransactionsForDebt(debtId)
    }

    fun getFamilyMembers(familyId: String): Flow<List<FamilyMemberWithUser>> {
        return familyDao.getFamilyMembers(familyId)
    }

    // Business Actions - Suspend functions run on Dispatchers.IO automatically via Room
    suspend fun approveAccountRequest(requestId: String, approvedById: String): Boolean {
        val request = accountRequestDao.getById(requestId) ?: return false
        
        // Update request status
        val updatedRequest = request.copy(
            status = "approved",
            approvedBy = approvedById,
            updatedAt = System.currentTimeMillis().toString()
        )
        accountRequestDao.update(updatedRequest)

        // Check if Debt ledger already exists, if not create it
        val existingDebt = debtDao.getByFamilyAndStore(request.familyId, request.storeId)
        if (existingDebt == null) {
            val family = familyDao.getFamilyById(request.familyId)
            val store = storeDao.getStoreById(request.storeId)
            val title = "${family?.name ?: "عائلة جديدة"} - ${store?.name ?: "متجر"}"
            
            val newDebt = Debt(
                id = UUID.randomUUID().toString(),
                title = title,
                debtType = "store",
                familyId = request.familyId,
                storeId = request.storeId,
                creatorId = approvedById,
                totalBalance = 0.0,
                status = "active"
            )
            debtDao.insert(newDebt)

            // Register debt parties
            val creditorId = store?.ownerId ?: approvedById
            debtPartyDao.insert(
                DebtParty(
                    debtId = newDebt.id,
                    userId = creditorId,
                    partyRole = "creditor"
                )
            )

            val familyMemberIds = familyDao.getFamilyMemberUserIds(request.familyId)
            familyMemberIds.forEach { memberId ->
                debtPartyDao.insert(
                    DebtParty(
                        debtId = newDebt.id,
                        userId = memberId,
                        partyRole = "debtor"
                    )
                )
            }
        }

        // Send notification
        val familyCreatorId = familyDao.getFamilyById(request.familyId)?.creatorId ?: request.requestedBy
        val storeName = storeDao.getStoreById(request.storeId)?.name ?: "المتجر"
        notificationDao.insert(
            Notification(
                id = UUID.randomUUID().toString(),
                userId = familyCreatorId,
                type = "request_approved",
                title = "تمت الموافقة على طلب فتح الحساب",
                message = "لقد وافق سوبرماركت $storeName على طلب فتح الحساب الخاص بعائلتك."
            )
        )

        // Log activity
        activityLogDao.insert(
            ActivityLog(
                id = UUID.randomUUID().toString(),
                userId = approvedById,
                action = "الموافقة على طلب فتح حساب",
                entityType = "account_requests",
                entityId = requestId,
                details = "تمت الموافقة على طلب فتح حساب عائلة ${request.familyId} لدى المتجر $storeName."
            )
        )
        return true
    }

    suspend fun rejectAccountRequest(requestId: String, approvedById: String, reason: String? = null): Boolean {
        val request = accountRequestDao.getById(requestId) ?: return false
        val updatedRequest = request.copy(
            status = "rejected",
            approvedBy = approvedById,
            rejectionReason = reason,
            updatedAt = System.currentTimeMillis().toString()
        )
        accountRequestDao.update(updatedRequest)

        // Send notification
        val familyCreatorId = familyDao.getFamilyById(request.familyId)?.creatorId ?: request.requestedBy
        val storeName = storeDao.getStoreById(request.storeId)?.name ?: "المتجر"
        notificationDao.insert(
            Notification(
                id = UUID.randomUUID().toString(),
                userId = familyCreatorId,
                type = "request_rejected",
                title = "تم رفض طلب فتح الحساب",
                message = "تم رفض طلب فتح حساب عائلتك لدى $storeName. سبب الرفض: ${reason ?: ""}"
            )
        )

        // Log activity
        activityLogDao.insert(
            ActivityLog(
                id = UUID.randomUUID().toString(),
                userId = approvedById,
                action = "رفض طلب فتح حساب",
                entityType = "account_requests",
                entityId = requestId,
                details = "تم رفض طلب فتح حساب عائلة ${request.familyId}. السبب: $reason"
            )
        )
        return true
    }

    suspend fun addTransaction(
        debtId: String,
        actorId: String,
        memberId: String?,
        transactionType: String, // charge, payment, refund
        amount: Double,
        description: String?
    ): Boolean {
        val debt = debtDao.getById(debtId) ?: return false

        // Compute new balance
        val balanceDifference = when (transactionType) {
            "charge" -> amount      // increases what client owes
            "payment" -> -amount    // decreases what client owes
            "refund" -> -amount     // decreases what client owes
            else -> 0.0
        }

        val updatedBalance = debt.totalBalance + balanceDifference
        val updatedDebt = debt.copy(
            totalBalance = updatedBalance,
            updatedAt = System.currentTimeMillis().toString()
        )
        debtDao.update(updatedDebt)

        // Insert Transaction
        val transaction = DebtTransaction(
            id = UUID.randomUUID().toString(),
            debtId = debtId,
            storeId = debt.storeId,
            actorId = actorId,
            memberId = memberId,
            transactionType = transactionType,
            amount = amount,
            description = description,
            clientCreatedAt = System.currentTimeMillis(),
            isSynced = if (isOnlineState.value) 1 else 0
        )
        transactionDao.insert(transaction)

        // Send notifications
        val store = storeDao.getStoreById(debt.storeId)
        val family = familyDao.getFamilyById(debt.familyId)

        if (transactionType == "charge" || transactionType == "payment") {
            // Retrieve all parties associated with the debt ledger
            var parties = debtPartyDao.getPartiesForDebt(debtId)
            
            // Dynamic on-the-fly initialization of debt parties for backwards compatibility/testing fallback
            if (parties.isEmpty()) {
                val storeOwnerId = store?.ownerId ?: "user_ahmed"
                debtPartyDao.insert(DebtParty(debtId = debtId, userId = storeOwnerId, partyRole = "creditor"))
                
                val familyMemberIds = familyDao.getFamilyMemberUserIds(debt.familyId)
                val debtorParties = familyMemberIds.map { memberId ->
                    DebtParty(debtId = debtId, userId = memberId, partyRole = "debtor")
                }
                debtPartyDao.insertAll(debtorParties)
                
                parties = debtPartyDao.getPartiesForDebt(debtId)
            }

            val typeText = when (transactionType) {
                "charge" -> "شراء بالدين بقيمة"
                "payment" -> "تسديد مبلغ"
                else -> "حركة حساب بقيمة"
            }

            parties.forEach { party ->
                notificationDao.insert(
                    Notification(
                        id = UUID.randomUUID().toString(),
                        userId = party.userId,
                        type = "transaction_added",
                        title = "حركة جديدة في الحساب لدى ${store?.name ?: "المتجر"}",
                        message = "تنبيه لأطراف الحساب المشترك: تم تسجيل $typeText $amount ريال."
                    )
                )
            }
        } else {
            // For other transaction types (e.g. refund), notify the family admin as before
            family?.creatorId?.let { creatorId ->
                val typeText = when (transactionType) {
                    "refund" -> "استرجاع مبلغ"
                    else -> "حركة حساب بقيمة"
                }
                notificationDao.insert(
                    Notification(
                        id = UUID.randomUUID().toString(),
                        userId = creatorId,
                        type = "transaction_added",
                        title = "تحديث في الحساب لدى ${store?.name}",
                        message = "تم تسجيل $typeText $amount ريال في حسابكم."
                    )
                )
            }
        }

        // Log activity
        activityLogDao.insert(
            ActivityLog(
                id = UUID.randomUUID().toString(),
                userId = actorId,
                action = "قيد حركة مالية جديدة",
                entityType = "debt_transactions",
                entityId = transaction.id,
                details = "تم تسجيل حركة $transactionType بمبلغ $amount ريال. الوصف: $description"
            )
        )
        return true
    }

    suspend fun addAccountRequest(familyId: String, storeId: String, requestedBy: String): Boolean {
        val requestId = UUID.randomUUID().toString()
        val request = AccountRequest(
            id = requestId,
            familyId = familyId,
            storeId = storeId,
            status = "pending",
            requestedBy = requestedBy
        )
        accountRequestDao.insert(request)

        val storeName = storeDao.getStoreById(storeId)?.name ?: "المتجر"
        // Log activity
        activityLogDao.insert(
            ActivityLog(
                id = UUID.randomUUID().toString(),
                userId = requestedBy,
                action = "تقديم طلب فتح حساب",
                entityType = "account_requests",
                entityId = requestId,
                details = "تم تقديم طلب جديد لفتح حساب عائلي لدى متجر: $storeName"
            )
        )
        return true
    }

    suspend fun addFamilyMember(familyId: String, name: String, phone: String, role: String): Boolean {
        val userId = UUID.randomUUID().toString()
        val newUser = User(
            id = userId,
            name = name,
            email = "$userId@family.com",
            phoneNumber = phone,
            isActive = 1
        )
        userDao.insertUser(newUser)

        val member = FamilyMember(
            familyId = familyId,
            userId = userId,
            memberRole = role,
            isActive = 1
        )
        familyDao.insertFamilyMember(member)

        // Log activity
        activityLogDao.insert(
            ActivityLog(
                id = UUID.randomUUID().toString(),
                userId = "user_khaled", // Family creator
                action = "إضافة فرد عائلة جديد",
                entityType = "family_members",
                entityId = newUser.id,
                details = "تمت إضافة العضو الجديد: $name ودوره: $role"
            )
        )
        return true
    }

    suspend fun authenticateUser(email: String): Pair<User?, String?> {
        val user = userDao.getUserByEmail(email.trim()) ?: return Pair(null, null)
        val roles = userDao.getUserRoles(user.id)
        val role = when {
            roles.contains("merchant") -> "merchant"
            roles.contains("customer") -> "customer"
            else -> "customer" // default / fallback
        }
        return Pair(user, role)
    }

    suspend fun registerUser(name: String, email: String, passwordHash: String, selectedRole: String): Pair<Boolean, String> {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || name.trim().isEmpty()) {
            return Pair(false, "الرجاء تعبئة جميع الحقول المطلوبة")
        }
        val existing = userDao.getUserByEmail(trimmedEmail)
        if (existing != null) {
            return Pair(false, "البريد الإلكتروني المدخل مسجل بالفعل في النظام")
        }

        val userId = "user_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        val user = User(
            id = userId,
            name = name.trim(),
            email = trimmedEmail,
            passwordHash = passwordHash
        )
        userDao.insertUser(user)

        val roleId = if (selectedRole == "merchant") "role_merchant" else "role_customer"
        userDao.insertUserRole(UserRole(userId = userId, roleId = roleId))

        logActivity(
            userId = userId,
            action = "تسجيل جديد 📝",
            entityType = "users",
            entityId = userId,
            details = "حساب جديد للمستخدم ${user.name} بدور ${if (selectedRole == "merchant") "تاجر" else "عميل"} في SQLite"
        )
        return Pair(true, "تم تسجيل الحساب بنجاح! يمكنك الآن تسجيل الدخول.")
    }

    suspend fun logActivity(userId: String, action: String, entityType: String, entityId: String, details: String) {
        activityLogDao.insert(
            ActivityLog(
                id = UUID.randomUUID().toString(),
                userId = userId,
                action = action,
                entityType = entityType,
                entityId = entityId,
                details = details
            )
        )
    }

    suspend fun syncUnsyncedTransactions(): Int {
        if (!isOnlineState.value) return 0
        val unsynced = transactionDao.getUnsyncedTransactions()
        if (unsynced.isEmpty()) return 0

        unsynced.forEach { tx ->
            // Simulate cloud server latency/backup sync
            transactionDao.markSynced(tx.id)
            
            // Log sync event
            val store = storeDao.getStoreById(tx.storeId)
            activityLogDao.insert(
                ActivityLog(
                    id = UUID.randomUUID().toString(),
                    userId = tx.actorId,
                    action = "مزامنة العمليات ☁️",
                    entityType = "debt_transactions",
                    entityId = tx.id,
                    details = "تمت مزامنة قيد حركة بقيمة ${tx.amount} ريال لدى متجر ${store?.name ?: "المتجر"} بنجاح مع السيرفر السحابي السريع."
                )
            )
        }
        return unsynced.size
    }
}
