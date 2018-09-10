package org.apereo.cas.authentication;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.configuration.model.support.mfa.MultifactorAuthenticationProviderBypassProperties;
import org.apereo.cas.services.MultifactorAuthenticationProvider;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.util.CollectionUtils;
import org.apereo.cas.util.HttpUtils;
import org.springframework.http.HttpStatus;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * This is {@link RestMultifactorAuthenticationProviderBypass}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
@Slf4j
public class RestMultifactorAuthenticationProviderBypass extends DefaultMultifactorAuthenticationProviderBypass {

    private static final long serialVersionUID = -7553888418344342672L;

    public RestMultifactorAuthenticationProviderBypass(final MultifactorAuthenticationProviderBypassProperties bypassProperties) {
        super(bypassProperties);
    }

    @Override
    public boolean shouldMultifactorAuthenticationProviderExecute(final Authentication authentication, final RegisteredService registeredService,
                                                                  final MultifactorAuthenticationProvider provider,
                                                                  final HttpServletRequest request) {
        try {
            if (bypassProperties.getRest().isExecuteDefault()) {
                final boolean shouldExecute = super.shouldMultifactorAuthenticationProviderExecute(authentication,
                        registeredService, provider, request);
                if (!shouldExecute) {
                    LOGGER.info("Default bypass provider determined this request may be passed, REST bypass will not" +
                            "be consulted");
                }
                return false;
            }
            final Principal principal = authentication.getPrincipal();
            final MultifactorAuthenticationProviderBypassProperties.Rest rest = bypassProperties.getRest();
            LOGGER.debug("Evaluating multifactor authentication bypass properties for principal [{}], "
                    + "service [{}] and provider [{}] via REST endpoint [{}]",
                principal.getId(), registeredService, provider, rest.getUrl());

            final Map<String, Object> parameters = CollectionUtils.wrap("principal", principal.getId(), "provider", provider.getId());
            if (registeredService != null) {
                parameters.put("service", registeredService.getServiceId());
            }

            final HttpResponse response = HttpUtils.execute(rest.getUrl(), rest.getMethod(),
                rest.getBasicAuthUsername(), rest.getBasicAuthPassword(), parameters, new HashMap<>());
            final boolean shouldExecute = response.getStatusLine().getStatusCode() == HttpStatus.ACCEPTED.value();
            if (shouldExecute) {
                updateAuthenticationToForgetBypass(authentication, provider, principal);
            } else {
                LOGGER.info("REST bypass endpoint response determined [{}] would be passed for [{}]", principal.getId(), provider.getId());
                updateAuthenticationToRememberBypass(authentication, provider, principal);
            }
            return shouldExecute;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return super.shouldMultifactorAuthenticationProviderExecute(authentication, registeredService, provider, request);
    }
}
