package com.apollographql.apollo3.api

import com.apollographql.apollo3.exception.ApolloException
import com.benasher44.uuid.Uuid

/**
 * Represents a GraphQL response. GraphQL responses can be be partial responses so it is valid to have both data != null and errors
 */
class ApolloResponse<out D : Operation.Data>(
    val requestUuid: Uuid,

    /**
     * The GraphQL operation this response represents
     */
    val operation: Operation<*>,

    /**
     * Parsed response of GraphQL [operation] execution.
     * Can be `null` in case if [operation] execution failed.
     */
    val data: D?,

    /**
     * GraphQL [operation] execution errors returned by the server to let client know that something has gone wrong.
     * This can either be null or empty depending what you server sends back
     */
    val errors: List<Error>? = null,

    /**
     * Extensions of GraphQL protocol, arbitrary map of key [String] / value [Any] sent by server along with the response.
     */
    val extensions: Map<String, Any?> = emptyMap(),

    /**
     * The context of GraphQL [operation] execution.
     * This can contain additional data contributed by interceptors.
     */
    val executionContext: ExecutionContext = ExecutionContext.Empty
) {

  /**
   * A shorthand property to get a non-nullable if handling partial data is not important
   */
  val dataOrThrow: D
    get() {
      return if (hasErrors()) {
        throw ApolloException("The response has errors")
      } else {
        data ?: throw  ApolloException("The server did not return any data")
      }
    }

  fun hasErrors(): Boolean = !errors.isNullOrEmpty()

    fun copy(
        requestUuid: Uuid = this.requestUuid,
        operation: Operation<*> = this.operation,
        data: Any? = this.data,
        errors: List<Error>? = this.errors,
        extensions: Map<String, Any?> = this.extensions,
        executionContext: ExecutionContext = this.executionContext
    ): ApolloResponse<D> {
        return ApolloResponse(
            requestUuid,
            operation,
            data as D?,
            errors,
            extensions,
            executionContext
        )
    }
}