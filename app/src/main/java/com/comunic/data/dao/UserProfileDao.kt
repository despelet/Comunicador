package com.comunic.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.comunic.data.entity.UserProfileEntity

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(
        user: UserProfileEntity
    )

    @Query("""
        SELECT *
        FROM user_profiles
        WHERE userId = :userId
        LIMIT 1
    """)
    suspend fun getById(
        userId: String
    ): UserProfileEntity?

    @Query("""
        SELECT *
        FROM user_profiles
        WHERE isActive = 1
        LIMIT 1
    """)
    suspend fun getActiveUser(): UserProfileEntity?

    @Query("""
        UPDATE user_profiles
        SET isActive = 0
    """)
    suspend fun deactivateAll()

    @Query("""
        UPDATE user_profiles
        SET isActive = 1
        WHERE userId = :userId
    """)
    suspend fun activateUser(
        userId: String
    )
}