/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codelab.android.datastore.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.DataStore
import androidx.datastore.createDataStore
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import com.codelab.android.datastore.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import java.io.IOException

private const val USER_PREFERENCES_NAME = "user_preferences"
private const val SORT_ORDER_KEY = "sort_order"

//Initial commit
/*enum class SortOrder {
    NONE,
    BY_DEADLINE,
    BY_PRIORITY,
    BY_DEADLINE_AND_PRIORITY
}*/

/**
 * Class that handles saving and retrieving user preferences
 */
class UserPreferencesRepository private constructor(context: Context) {

    private val sharedPrefsMigration = SharedPreferencesMigration(
        context,
        USER_PREFERENCES_NAME
    ) { sharedPrefs: SharedPreferencesView, currentData: UserPreferences ->
        // Define the mapping from SharedPreferences to UserPreferences
        if (currentData.sortOrder == UserPreferences.SortOrder.UNSPECIFIED) {
            currentData.toBuilder().setSortOrder(
                UserPreferences.SortOrder.valueOf(
                    sharedPrefs.getString(
                        SORT_ORDER_KEY,UserPreferences.SortOrder.NONE.name)!!
                )
            ).build()
        } else {
            currentData
        }
    }
    private val dataStore: DataStore<UserPreferences> = context.createDataStore(
        fileName = "user_prefs.pb",
        serializer = UserPreferencesSerializer,
        migrations = listOf(sharedPrefsMigration)
    )
    private val TAG: String = "UserPreferencesRepo"

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Log.e(TAG, "Error reading sort order preferences.", exception)
                emit(UserPreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }
    suspend fun updateShowCompleted(completed: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setShowCompleted(completed).build()
        }
    }


    private val sharedPreferences =
        context.applicationContext.getSharedPreferences(USER_PREFERENCES_NAME, Context.MODE_PRIVATE)

    // Keep the sort order as a stream of changes
    private val _sortOrderFlow = MutableStateFlow(sortOrder)
    val sortOrderFlow: StateFlow<UserPreferences.SortOrder> = _sortOrderFlow

    /**
     * Get the sort order. By default, sort order is None.
     */
    private val sortOrder: UserPreferences.SortOrder
        get() {
            val order = sharedPreferences.getString(SORT_ORDER_KEY, UserPreferences.SortOrder.NONE.name)
            return UserPreferences.SortOrder.valueOf(order ?: UserPreferences.SortOrder.NONE.name)
        }

    /*fun enableSortByDeadline(enable: Boolean) {
        val currentOrder = sortOrderFlow.value
        val newSortOrder =
            if (enable) {
                if (currentOrder == UserPreferences.SortOrder.BY_PRIORITY) {
                    UserPreferences.SortOrder.BY_DEADLINE_AND_PRIORITY
                } else {
                    UserPreferences.SortOrder.BY_DEADLINE
                }
            } else {
                if (currentOrder == UserPreferences.SortOrder.BY_DEADLINE_AND_PRIORITY) {
                    UserPreferences.SortOrder.BY_PRIORITY
                } else {
                    UserPreferences.SortOrder.NONE
                }
            }
        updateSortOrder(newSortOrder)
        _sortOrderFlow.value = newSortOrder
    }*/
    suspend fun enableSortByDeadline(enable: Boolean) {
        // updateData handles data transactionally, ensuring that if the sort is updated at the same
        // time from another thread, we won't have conflicts
        dataStore.updateData { preferences ->
            val currentOrder = preferences.sortOrder
            val newSortOrder =
                if (enable) {
                    if (currentOrder == UserPreferences.SortOrder.BY_PRIORITY) {
                        UserPreferences.SortOrder.BY_DEADLINE_AND_PRIORITY
                    } else {
                        UserPreferences.SortOrder.BY_DEADLINE
                    }
                } else {
                    if (currentOrder == UserPreferences.SortOrder.BY_DEADLINE_AND_PRIORITY) {
                        UserPreferences.SortOrder.BY_PRIORITY
                    } else {
                        UserPreferences.SortOrder.NONE
                    }
                }
            preferences.toBuilder().setSortOrder(newSortOrder).build()
        }
    }
    suspend fun enableSortByPriority(enable: Boolean) {
        dataStore.updateData { preferences ->
            val currentOrder = preferences.sortOrder
            val newSortOrder =
                if (enable) {
                    if (currentOrder == UserPreferences.SortOrder.BY_DEADLINE) {
                        UserPreferences.SortOrder.BY_DEADLINE_AND_PRIORITY
                    } else {
                        UserPreferences.SortOrder.BY_PRIORITY
                    }
                } else {
                    if (currentOrder == UserPreferences.SortOrder.BY_DEADLINE_AND_PRIORITY) {
                        UserPreferences.SortOrder.BY_DEADLINE
                    } else {
                        UserPreferences.SortOrder.NONE
                    }
                }
            preferences.toBuilder().setSortOrder(newSortOrder).build()
        }
    }

    /*fun enableSortByPriority(enable: Boolean) {
        val currentOrder = sortOrderFlow.value
        val newSortOrder =
            if (enable) {
                if (currentOrder == UserPreferences.SortOrder.BY_DEADLINE) {
                    UserPreferences.SortOrder.BY_DEADLINE_AND_PRIORITY
                } else {
                    UserPreferences.SortOrder.BY_PRIORITY
                }
            } else {
                if (currentOrder == UserPreferences.SortOrder.BY_DEADLINE_AND_PRIORITY) {
                    UserPreferences.SortOrder.BY_DEADLINE
                } else {
                    UserPreferences.SortOrder.NONE
                }
            }
        updateSortOrder(newSortOrder)
        _sortOrderFlow.value = newSortOrder
    }*/

    private fun updateSortOrder(sortOrder: UserPreferences.SortOrder) {
        sharedPreferences.edit {
            putString(SORT_ORDER_KEY, sortOrder.name)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(context: Context): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferencesRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
