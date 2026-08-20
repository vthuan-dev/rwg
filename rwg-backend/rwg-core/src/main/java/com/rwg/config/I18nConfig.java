package com.rwg.config;

import com.rwg.identity.service.UserLocaleService;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Hạ tầng i18n chặng 2 (Phase a):
 * - Bean Validation dùng chung MessageSource: message annotation dạng {key} trong DTO
 *   được resolve theo locale request (bundle i18n/messages, 4 ngôn ngữ en/vi/zh/ja).
 * - LocaleResolver custom (bean tên "localeResolver" để DispatcherServlet tự nhận):
 *   ưu tiên Accept-Language; user đã xác thực fallback về locale lưu trong users.
 */
@Configuration
public class I18nConfig {

    /** Validator dùng MessageSource -> message {validation.*} dịch theo locale request. */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }

    @Bean
    public LocaleResolver localeResolver(UserLocaleService userLocaleService) {
        return new RwgLocaleResolver(userLocaleService);
    }
}
