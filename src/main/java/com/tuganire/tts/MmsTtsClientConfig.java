package com.tuganire.tts;

import com.tuganire.config.MmsProperties;
import com.tuganire.config.TtsConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Registers the {@link MmsTtsClient} declarative HTTP service client and enables {@link TtsConfig} properties.
 *
 * <p>
 * The {@link RestClientHttpServiceGroupConfigurer} bean configures the underlying {@code RestClient} with the base URL
 * read from {@code tuganire.mms.base-url}. Spring Framework 7 then generates a proxy implementation at startup — no
 * {@code RestTemplate} or manual factory wiring is required.
 */
@Configuration
@EnableConfigurationProperties({TtsConfig.class, MmsProperties.class})
@ImportHttpServices(group = "mms-tts", types = MmsTtsClient.class)
public class MmsTtsClientConfig {

    private static final String GROUP_NAME = "mms-tts";

    /**
     * Configures the RestClient for the {@code mms-tts} HTTP service group: base URL plus a request factory carrying
     * the MMS connect/read timeouts, so a cold-start wake of the (scale-to-zero) MMS server is not cut short.
     *
     * @param mms
     *            MMS server connection properties (base URL + timeouts)
     * @return configurer that sets the base URL and timeout-aware request factory on the RestClient builder
     */
    @Bean
    public RestClientHttpServiceGroupConfigurer mmsTtsGroupConfigurer(MmsProperties mms) {
        return mms.groupConfigurer(GROUP_NAME);
    }
}
