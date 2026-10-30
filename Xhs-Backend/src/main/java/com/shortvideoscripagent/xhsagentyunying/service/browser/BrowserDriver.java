package com.shortvideoscripagent.xhsagentyunying.service.browser;

import com.shortvideoscripagent.xhsagentyunying.domain.entity.BrowserAssistSession;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAsset;

/**
 * L3: Browser auto-fill driver abstraction.
 *
 * <p>Browser assist (semi-auto publish) requires a real browser driver
 * (Playwright / Selenium etc). When no driver is available the system must
 * degrade honestly to "manual fill required" instead of pretending success.</p>
 */
public interface BrowserDriver {

    /**
     * Whether a usable browser driver is present in the current environment.
     */
    boolean isAvailable();

    /**
     * Auto-fill the content asset into the browser session.
     *
     * @param session browser assist session
     * @param asset   content asset to fill
     * @return true if the asset was actually filled
     */
    boolean fill(BrowserAssistSession session, ContentAsset asset);
}
