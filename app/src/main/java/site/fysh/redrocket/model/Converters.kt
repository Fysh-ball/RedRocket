package site.fysh.redrocket.model

import android.util.Log
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()
    private val recipientListType = object : TypeToken<List<Recipient>>() {}.type
    private val groupListType = object : TypeToken<List<Group>>() {}.type

    @TypeConverter
    fun fromRecipientList(value: List<Recipient>?): String? {
        return try {
            gson.toJson(value)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to serialize recipient list", e)
            null
        }
    }

    @TypeConverter
    fun toRecipientList(value: String?): List<Recipient>? {
        if (value == null) return null
        return try {
            gson.fromJson(value, recipientListType)
        } catch (e: JsonSyntaxException) {
            Log.e("Converters", "Failed to deserialize recipient list: corrupted JSON", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("Converters", "Unexpected error deserializing recipient list", e)
            emptyList()
        }
    }

    @TypeConverter
    fun fromGroupList(value: List<Group>?): String? {
        return try {
            gson.toJson(value)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to serialize group list", e)
            null
        }
    }

    @TypeConverter
    fun toGroupList(value: String?): List<Group>? {
        if (value == null) return null
        return try {
            gson.fromJson(value, groupListType)
        } catch (e: JsonSyntaxException) {
            Log.e("Converters", "Failed to deserialize group list: corrupted JSON", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("Converters", "Unexpected error deserializing group list", e)
            emptyList()
        }
    }
}
