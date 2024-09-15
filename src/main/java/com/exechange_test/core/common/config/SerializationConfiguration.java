package com.exechange_test.core.common.config;

import com.exechange_test.core.processors.journaling.DiskSerializationProcessorConfiguration;
import com.exechange_test.core.processors.journaling.DummySerializationProcessor;
import com.exechange_test.core.processors.journaling.ISerializationProcessor;
import com.exechange_test.core.processors.journaling.DiskSerializationProcessor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.function.Function;

@AllArgsConstructor
@Getter
@Builder
@ToString
public class SerializationConfiguration {

    // no serialization
    public static final SerializationConfiguration DEFAULT = SerializationConfiguration.builder()
            .enableJournaling(false)
            .serializationProcessorFactory(cfg -> DummySerializationProcessor.INSTANCE)
            .build();

    // no journaling, only snapshots
    public static final SerializationConfiguration DISK_SNAPSHOT_ONLY = SerializationConfiguration.builder()
            .enableJournaling(false)
            .serializationProcessorFactory(exchangeCfg -> new DiskSerializationProcessor(exchangeCfg, DiskSerializationProcessorConfiguration.createDefaultConfig()))
            .build();

    // snapshots and journaling
    public static final SerializationConfiguration DISK_JOURNALING = SerializationConfiguration.builder()
            .enableJournaling(true)
            .serializationProcessorFactory(exchangeCfg -> new DiskSerializationProcessor(exchangeCfg, DiskSerializationProcessorConfiguration.createDefaultConfig()))
            .build();

    /*
     * Enables journaling.
     * Set to false for analytics instances.
     */
    private final boolean enableJournaling;

    /*
     * Serialization processor implementations
     */
    private final Function<ExchangeConfiguration, ? extends ISerializationProcessor> serializationProcessorFactory;


}
