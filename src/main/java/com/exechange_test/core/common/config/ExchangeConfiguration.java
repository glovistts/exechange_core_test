package com.exechange_test.core.common.config;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;


/**
 * Exchange configuration
 */
@AllArgsConstructor
@Getter
@Builder
public final class ExchangeConfiguration {

    private final OrdersProcessingConfiguration ordersProcessingCfg;

    private final PerformanceConfiguration performanceCfg;

    private final InitialStateConfiguration initStateCfg;

    private final ReportsQueriesConfiguration reportsQueriesCfg;

    private final LoggingConfiguration loggingCfg;

    private final SerializationConfiguration serializationCfg;

    @Override
    public String toString() {
        return "ExchangeConfiguration{" +
                "\n  ordersProcessingCfg=" + ordersProcessingCfg +
                "\n  performanceCfg=" + performanceCfg +
                "\n  initStateCfg=" + initStateCfg +
                "\n  reportsQueriesCfg=" + reportsQueriesCfg +
                "\n  loggingCfg=" + loggingCfg +
                "\n  serializationCfg=" + serializationCfg +
                '}';
    }

    public static ExchangeConfiguration.ExchangeConfigurationBuilder defaultBuilder() {
        return ExchangeConfiguration.builder()
                .ordersProcessingCfg(OrdersProcessingConfiguration.DEFAULT)
                .initStateCfg(InitialStateConfiguration.DEFAULT)
                .performanceCfg(PerformanceConfiguration.DEFAULT)
                .reportsQueriesCfg(ReportsQueriesConfiguration.DEFAULT)
                .loggingCfg(LoggingConfiguration.DEFAULT)
                .serializationCfg(SerializationConfiguration.DEFAULT);
    }
}
