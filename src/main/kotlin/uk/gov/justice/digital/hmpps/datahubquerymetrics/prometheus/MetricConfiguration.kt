package uk.gov.justice.digital.hmpps.datahubquerymetrics.prometheus

import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Histogram
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MetricConfiguration {

  @Bean
  fun numQueriesCounter(): Counter = Counter.builder()
    .name("queries")
    .help("Number of queries")
    .labelNames("report_id", "variant_id", "unique_output_port_id", "datasource", "parent_domain", "status", "query_engine", "query_type")
    .unit(null)
    .register()

  @Bean
  fun runtimeHistogram(): Histogram = Histogram.builder()
    .name("single_query_metric")
    .labelNames("report_id", "variant_id", "unique_output_port_id", "datasource", "parent_domain", "state", "query_engine", "query_type")
    // Numbers are in seconds as that is prometheus standard
    .classicUpperBounds(
      0.001, // 1 ms
      0.0025, // 2.5 ms
      0.005, // 5 ms
      0.01, // 10 ms
      0.025, // 25 ms
      0.05, // 50 ms
      0.1, // 100 ms
      0.25, // 250 ms
      0.5, // 500 ms
      1.0,
      2.5,
      5.0,
      10.0,
      20.0,
      30.0,
      60.0,
      90.0,
      120.0,
      240.0,
      300.0,
      450.0,
      600.0,
    )
    .register()
}
