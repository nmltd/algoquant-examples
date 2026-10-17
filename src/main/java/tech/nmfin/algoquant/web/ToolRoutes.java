package tech.nmfin.algoquant.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/** An app handles its own URL namespace and returns false for routes belonging to other tools. */
@FunctionalInterface
public interface ToolRoutes {
    boolean handle(HttpExchange exchange) throws IOException;
}
