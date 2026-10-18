package tech.nmfin.algoquant.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * An app handles its own URL namespace and returns false for routes belonging to other tools.
 *
 * <p><b>Using this example.</b>
 * Contract between the shared HTTP host and app-specific route handlers. Return false for
 * paths outside the app namespace so another handler can try them; use shared transport and
 * storage helpers rather than duplicating them in each tool.
 * </p>
 */
@FunctionalInterface
public interface ToolRoutes {
    boolean handle(HttpExchange exchange) throws IOException;
}
