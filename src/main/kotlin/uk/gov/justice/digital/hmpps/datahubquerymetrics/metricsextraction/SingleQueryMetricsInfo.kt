package uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction

import aws.sdk.kotlin.services.athena.model.QueryExecutionState
import java.time.ZonedDateTime

data class SingleQueryMetricsInfo(
  val queryId: String,
  val querySql: String,
  val startTime: ZonedDateTime,
  val endTime: ZonedDateTime,
  val state: QueryExecutionStatus,
  val totalRunTimeMicroseconds: Long,
  val queryEngine: QueryEngine,
  val productId: String,
  val productName: String,
  val datasourceName: String,
  val datasourceCatalog: String?,
  val databaseName: String?,
  val reportOrDashboardId: String,
  val reportOrDashboardName: String,
  val hasProbationDatasources: Boolean,
  val queryType: QueryType,
)

enum class QueryType {
  NORMAL,
  SUMMARY,
}

enum class QueryEngine {
  ATHENA,
  REDSHIFT,
}

enum class QueryExecutionStatus {
  FAILED,
  CANCELLED,
  QUEUED,
  RUNNING,
  SUCCEEDED,
  UNKNOWN,
  ;

  companion object {
    fun fromAthenaState(state: QueryExecutionState) = QueryExecutionStatus.valueOf(state.value)
    fun fromRedshiftState(state: String): QueryExecutionStatus = when (state.trim().lowercase()) {
      "failed" -> FAILED
      "queued" -> QUEUED
      "planning",
      "running",
      -> RUNNING
      "returning" -> RUNNING
      "canceled",
      "cancelled",
      -> CANCELLED
      "success" -> SUCCEEDED
      else -> UNKNOWN
    }
  }
}
