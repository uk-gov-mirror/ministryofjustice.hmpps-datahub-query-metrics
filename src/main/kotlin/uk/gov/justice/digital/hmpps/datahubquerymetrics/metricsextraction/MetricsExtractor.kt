package uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction

data class QueryInfo(
  val productId: String,
  val productName: String,
  val datasourceName: String,
  val datasourceCatalog: String?,
  val databaseName: String?,
  val reportOrDashboardId: String,
  val hasProbationDatasources: Boolean,
  val queryType: QueryType,
)

interface MetricsExtractor {
  suspend fun extractQueryMetrics(): Collection<SingleQueryMetricsInfo>

  fun takeStringOrNull(str: String): String? = if (str.isNotBlank() && str != "null") str else null

  fun extractQueryInfo(query: String?): QueryInfo {
    if (!query!!.contains("QUERY_INFO")) {
      return QueryInfo("", "", "", "", "", "", false, QueryType.SUMMARY)
    }
    val queryInfo = query.substringAfter("QUERY_INFO|||").substringBefore("|||END").split("|||")

    return QueryInfo(
      queryInfo[0],
      queryInfo[1],
      queryInfo[2],
      takeStringOrNull(queryInfo[3]),
      takeStringOrNull(queryInfo[4]),
      queryInfo[5],
      queryInfo[6].toBoolean(),
      QueryType.valueOf(queryInfo[7]),
    )
  }
}
