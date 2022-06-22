package pagination

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.cache.normalized.api.FieldPolicyApolloResolver
import com.apollographql.apollo3.cache.normalized.api.FieldRecordMerger
import com.apollographql.apollo3.cache.normalized.api.MemoryCacheFactory
import com.apollographql.apollo3.cache.normalized.api.MetadataGenerator
import com.apollographql.apollo3.cache.normalized.api.MetadataGeneratorContext
import com.apollographql.apollo3.cache.normalized.api.NormalizedCacheFactory
import com.apollographql.apollo3.cache.normalized.api.TypePolicyCacheKeyGenerator
import com.apollographql.apollo3.cache.normalized.apolloStore
import com.apollographql.apollo3.cache.normalized.normalizedCache
import com.apollographql.apollo3.cache.normalized.sql.SqlNormalizedCacheFactory
import com.apollographql.apollo3.testing.runTest
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals

class OffsetBasedWithPagePaginationTest {
  @Test
  fun offsetBasedWithPageMemoryCache() {
    offsetBasedWithPage(MemoryCacheFactory())
  }

  @Test
  fun offsetBasedWithPageBlobSqlCache() {
    offsetBasedWithPage(SqlNormalizedCacheFactory(name = "blob", withDates = true))
  }

  @Test
  fun offsetBasedWithPageJsonSqlCache() {
    offsetBasedWithPage(SqlNormalizedCacheFactory(name = "json", withDates = false))
  }

  private fun offsetBasedWithPage(cacheFactory: NormalizedCacheFactory) = runTest {
    val client = ApolloClient.Builder()
        .normalizedCache(
            normalizedCacheFactory = cacheFactory,
            cacheKeyGenerator = TypePolicyCacheKeyGenerator,
            metadataGenerator = OffsetPaginationMetadataGenerator("UserPage"),
            apolloResolver = FieldPolicyApolloResolver,
            recordMerger = FieldRecordMerger(OffsetPaginationRecordMerger())
        )
        .serverUrl("unused")
        .build()
    client.apolloStore.clearAll()

    // First page
    val query1 = UsersOffsetBasedWithPageQuery(Optional.Present(42), Optional.Present(2))
    val data1 = UsersOffsetBasedWithPageQuery.Data(UsersOffsetBasedWithPageQuery.UsersOffsetBasedWithPage(listOf(
        UsersOffsetBasedWithPageQuery.User("42", "John", "john@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("43", "Jane", "jane@a.com", "User"),
    )))
    client.apolloStore.writeOperation(query1, data1)
    var dataFromStore = client.apolloStore.readOperation(query1)
    assertEquals(data1, dataFromStore)

    // Page after
    val query2 = UsersOffsetBasedWithPageQuery(Optional.Present(44), Optional.Present(2))
    val data2 = UsersOffsetBasedWithPageQuery.Data(UsersOffsetBasedWithPageQuery.UsersOffsetBasedWithPage(listOf(
        UsersOffsetBasedWithPageQuery.User("44", "Peter", "peter@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("45", "Alice", "alice@a.com", "User"),
    )))
    client.apolloStore.writeOperation(query2, data2)
    dataFromStore = client.apolloStore.readOperation(query1)
    var expectedData = UsersOffsetBasedWithPageQuery.Data(UsersOffsetBasedWithPageQuery.UsersOffsetBasedWithPage(listOf(
        UsersOffsetBasedWithPageQuery.User("42", "John", "john@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("43", "Jane", "jane@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("44", "Peter", "peter@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("45", "Alice", "alice@a.com", "User"),
    )))
    assertEquals(expectedData, dataFromStore)

    // Page in the middle
    val query3 = UsersOffsetBasedWithPageQuery(Optional.Present(44), Optional.Present(3))
    val data3 = UsersOffsetBasedWithPageQuery.Data(UsersOffsetBasedWithPageQuery.UsersOffsetBasedWithPage(listOf(
        UsersOffsetBasedWithPageQuery.User("44", "Peter", "peter@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("45", "Alice", "alice@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("46", "Bob", "bob@a.com", "User"),
    )))
    client.apolloStore.writeOperation(query3, data3)
    dataFromStore = client.apolloStore.readOperation(query1)
    expectedData = UsersOffsetBasedWithPageQuery.Data(UsersOffsetBasedWithPageQuery.UsersOffsetBasedWithPage(listOf(
        UsersOffsetBasedWithPageQuery.User("42", "John", "john@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("43", "Jane", "jane@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("44", "Peter", "peter@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("45", "Alice", "alice@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("46", "Bob", "bob@a.com", "User"),
    )))
    assertEquals(expectedData, dataFromStore)

    // Page before
    val query4 = UsersOffsetBasedWithPageQuery(Optional.Present(40), Optional.Present(2))
    val data4 = UsersOffsetBasedWithPageQuery.Data(UsersOffsetBasedWithPageQuery.UsersOffsetBasedWithPage(listOf(
        UsersOffsetBasedWithPageQuery.User("40", "Paul", "paul@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("41", "Mary", "mary@a.com", "User"),
    )))
    client.apolloStore.writeOperation(query4, data4)
    dataFromStore = client.apolloStore.readOperation(query1)
    expectedData = UsersOffsetBasedWithPageQuery.Data(UsersOffsetBasedWithPageQuery.UsersOffsetBasedWithPage(listOf(
        UsersOffsetBasedWithPageQuery.User("40", "Paul", "paul@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("41", "Mary", "mary@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("42", "John", "john@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("43", "Jane", "jane@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("44", "Peter", "peter@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("45", "Alice", "alice@a.com", "User"),
        UsersOffsetBasedWithPageQuery.User("46", "Bob", "bob@a.com", "User"),
    )))
    assertEquals(expectedData, dataFromStore)
  }

  private class OffsetPaginationMetadataGenerator(private val typeName: String) : MetadataGenerator {
    override fun metadataForObject(obj: Any?, context: MetadataGeneratorContext): Map<String, Any?> {
      if (context.field.type.leafType().name == typeName) {
        return mapOf(
            "offset" to context.field.resolveArgument("offset", context.variables) as Int,
        )
      }
      return emptyMap()
    }
  }

  private class OffsetPaginationRecordMerger : FieldRecordMerger.FieldMerger {
    override fun mergeFields(existing: FieldRecordMerger.FieldInfo, incoming: FieldRecordMerger.FieldInfo): FieldRecordMerger.FieldInfo {
      val existingOffset = existing.metadata["offset"] as? Int
      val incomingOffset = incoming.arguments["offset"] as? Int
      return if (existingOffset == null || incomingOffset == null) {
        incoming
      } else {
        val existingValue = existing.value as Map<*, *>
        val existingList = existingValue["users"] as List<*>
        val incomingList = (incoming.value as Map<*, *>)["users"] as List<*>
        val mergedList = mergeLists(existingList, incomingList, existingOffset, incomingOffset)
        val mergedFieldValue = existingValue.toMutableMap()
        mergedFieldValue["users"] = mergedList
        FieldRecordMerger.FieldInfo(
            value = mergedFieldValue,
            metadata = mapOf("offset" to min(existingOffset, incomingOffset))
        )
      }
    }

    private fun <T> mergeLists(existing: List<T>, incoming: List<T>, existingOffset: Int, incomingOffset: Int): List<T> {
      if (incomingOffset > existingOffset + existing.size) {
        // Incoming list's first item is further than immediately after the existing list's last item: can't merge. Handle it as a reset.
        return incoming
      }

      if (incomingOffset + incoming.size < existingOffset) {
        // Incoming list's last item is further than immediately before the existing list's first item: can't merge. Handle it as a reset.
        return incoming
      }

      val merged = mutableListOf<T>()
      val startOffset = min(existingOffset, incomingOffset)
      val endOffset = max(existingOffset + existing.size, incomingOffset + incoming.size)
      val incomingRange = incomingOffset until incomingOffset + incoming.size
      for (i in startOffset until endOffset) {
        if (i in incomingRange) {
          merged.add(incoming[i - incomingOffset])
        } else {
          merged.add(existing[i - existingOffset])
        }
      }
      return merged
    }
  }
}
