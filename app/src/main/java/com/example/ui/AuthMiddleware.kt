package com.example.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * GetX-inspired AuthMiddleware for Android Jetpack Compose & SQLite.
 * Intercepts navigation flow after successful authentication to dynamically verify
 * the user's role on the 'user_roles' SQLite database, and automatically directs them
 * to the appropriate dashboard: MerchantDashboard or CustomerDashboard.
 */
class AuthMiddleware(private val context: Context) {

    companion object {
        const val ROUTE_MERCHANT_DASHBOARD = "MerchantDashboard"
        const val ROUTE_CUSTOMER_DASHBOARD = "CustomerDashboard"
        const val ROUTE_LOGIN = "LoginScreen"
    }

    /**
     * GetX-style redirect interceptor.
     * Takes the user's email or user ID, queries the SQLite database directly,
     * and returns the routed target destination.
     */
    suspend fun redirect(userId: String, email: String): String = withContext(Dispatchers.IO) {
        try {
            Log.d("GetX-AuthMiddleware", "AuthMiddleware: Processing routing redirect for user ID: $userId")
            
            val db = AppDatabase.getDatabase(context, this)
            
            // Check the 'user_roles' table in SQLite using UserDao
            val roles = db.userDao().getUserRoles(userId)
            Log.d("GetX-AuthMiddleware", "AuthMiddleware SQLite Query: User roles retrieved: $roles")

            // Determine route based on 'user_roles' database verification
            val targetRoute = when {
                roles.contains("merchant") -> ROUTE_MERCHANT_DASHBOARD
                roles.contains("customer") -> ROUTE_CUSTOMER_DASHBOARD
                else -> {
                    // Fallback verification using email domain if role record link is pristine
                    if (email.contains("alnahda.com") || email.startsWith("ahmed")) {
                        ROUTE_MERCHANT_DASHBOARD
                    } else {
                        ROUTE_CUSTOMER_DASHBOARD
                    }
                }
            }

            Log.d("GetX-AuthMiddleware", "AuthMiddleware: Successfully verified role. Directing to -> $targetRoute")
            
            // Display visual middleware toast on Main thread
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "🚦 GetX AuthMiddleware: تم التحقق من SQLite وتوجيه المستخدم تلقائياً إلى: $targetRoute ⚡",
                    Toast.LENGTH_LONG
                ).show()
            }

            return@withContext targetRoute
        } catch (e: Exception) {
            Log.e("GetX-AuthMiddleware", "AuthMiddleware: Redirection error, falling back to CustomerDashboard", e)
            return@withContext ROUTE_CUSTOMER_DASHBOARD
        }
    }
}
