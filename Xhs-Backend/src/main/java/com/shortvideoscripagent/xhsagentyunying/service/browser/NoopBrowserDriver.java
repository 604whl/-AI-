package com.shortvideoscripagent.xhsagentyunying.service.browser;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.BrowserAssistSession;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;
import lombok.extern.slf4j.Slf4j;

/**
 * L3: default no-op browser driver.
 *
 * <p>
 * No real browser driver (Playwright / Selenium) is wired into this deployment yet, so:
 * <ul>
 *   <li>{@link #isAvailable()} returns false;</li>
 *   <li>{@link #fill(BrowserAssistSession, ContentAsset)} returns false.</li>
 * </ul>
 * The service layer therefore marks the session as failed and asks the caller to fill
 * the content manually, instead of pretending the fill succeeded.
 *
 * <p>
 * This class is intentionally NOT annotated with {@code @Component}: it is instantiated
 * directly by {@code BrowserAssistService} as the fallback whenever no
 * {@link BrowserDriver} bean is registered. Registering a real driver later only
 * requires providing a {@link BrowserDriver} bean; the service picks it up through
 * {@code ObjectProvider} and this fallback is never used.
 * </p>
 */
@Slf4j
public class NoopBrowserDriver implements BrowserDriver {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean fill(BrowserAssistSession session, ContentAsset asset) {
        log.debug("NoopBrowserDriver: browser driver unavailable, session={} requires manual fill",
                session != null ? session.getId() : null);
        return false;
    }
}
