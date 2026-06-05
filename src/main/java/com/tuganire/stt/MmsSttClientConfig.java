package com.tuganire.stt;

import org.springframework.beans.factory.annotation.Value;
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
@ImportHttpServices(group = "mms-stt", types = MmsSttClient.class)
public class MmsSttClientConfig {

    private static final String GROUP_NAME = "mms-stt";

    /**
     * Configures the RestClient for the {@code mms-stt} HTTP service group.
     *
     * @param mmsBaseUrl
     *            base URL of the Python MMS server
     * @return configurer that sets the base URL on the RestClient builder
     */
    @Bean
    public RestClientHttpServiceGroupConfigurer mmsSttGroupConfigurer(
            @Value("${tuganire.mms.base-url}") String mmsBaseUrl) {
        return groups -> groups.filterByName(GROUP_NAME).forEachClient((group, builder) -> builder.baseUrl(mmsBaseUrl));
    }
}
