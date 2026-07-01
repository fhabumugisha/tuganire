package com.tuganire.stt;

import com.tuganire.config.MmsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers the {@link MmsSttClient} declarative HTTP service client.
 *
 * <p>
 * Mirrors the MMS-TTS client setup: the {@link RestClientHttpServiceGroupConfigurer} bean points the {@code mms-stt}
 * group's {@code RestClient} at {@code tuganire.mms.base-url} (the same Python server). Each group configurer filters
 * by name, so this coexists with the {@code mms-tts} configurer.
 */
@Configuration
@EnableConfigurationProperties(MmsProperties.class)
@ImportHttpServices(group = "mms-stt", types = MmsSttClient.class)
public class MmsSttClientConfig {

    private static final String GROUP_NAME = "mms-stt";

    /**
     * Configures the RestClient for the {@code mms-stt} HTTP service group: base URL plus a request factory carrying
     * the MMS connect/read timeouts, so a cold-start wake of the (scale-to-zero) MMS server is not cut short.
     *
     * @param mms
     *            MMS server connection properties (base URL + timeouts)
     * @return configurer that sets the base URL and timeout-aware request factory on the RestClient builder
     */
    @Bean
    public RestClientHttpServiceGroupConfigurer mmsSttGroupConfigurer(MmsProperties mms) {
        return mms.groupConfigurer(GROUP_NAME);
    }
}
