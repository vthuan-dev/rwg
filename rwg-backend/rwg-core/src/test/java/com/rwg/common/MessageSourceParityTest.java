package com.rwg.common;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đảm bảo chất lượng bundle i18n (chạy mặc định trong mvn test):
 * 1. Tập key của 4 bundle (en mặc định / vi / zh / ja) PHẢI giống nhau tuyệt đối.
 * 2. Mọi ErrorCode đều có message (key error.<name()>) trong bundle.
 * 3. Mọi key ErrorCode resolve được message KHÔNG rỗng ở cả 4 locale.
 */
class MessageSourceParityTest {

    private static final String[] BUNDLES = {"messages", "messages_vi", "messages_zh", "messages_ja"};
    private static final List<Locale> LOCALES = List.of(
            Locale.ENGLISH, Locale.forLanguageTag("vi"), Locale.SIMPLIFIED_CHINESE, Locale.JAPANESE);

    private Properties load(String bundle) throws Exception {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/i18n/" + bundle + ".properties")) {
            assertThat(in).as("bundle i18n/%s.properties phải tồn tại".formatted(bundle)).isNotNull();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }

    @Test
    void allFourBundlesShareIdenticalKeySets() throws Exception {
        Set<String> enKeys = load("messages").stringPropertyNames();
        assertThat(enKeys).isNotEmpty();

        for (String bundle : BUNDLES) {
            Set<String> keys = load(bundle).stringPropertyNames();
            assertThat(keys)
                    .as("bundle %s phải có tập key GIỐNG TUYỆT ĐỐI messages.properties", bundle)
                    .isEqualTo(enKeys);
        }

        // Mọi ErrorCode phải có key error.<name()> trong bundle.
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(enKeys).contains(code.messageKey());
        }
    }

    @Test
    void everyErrorCodeResolvesNonEmptyMessageInAllFourLocales() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");

        for (Locale locale : LOCALES) {
            for (ErrorCode code : ErrorCode.values()) {
                String message = source.getMessage(code.messageKey(), null, locale);
                assertThat(message)
                        .as("locale %s phải có message cho %s", locale, code.name())
                        .isNotBlank();
            }
        }
    }

    @Test
    void noBundleContainsEmptyMessageValues() throws Exception {
        for (String bundle : BUNDLES) {
            Properties props = load(bundle);
            for (String key : props.stringPropertyNames()) {
                assertThat(props.getProperty(key).trim())
                        .as("bundle %s key %s không được rỗng", bundle, key)
                        .isNotEmpty();
            }
        }
    }
}
