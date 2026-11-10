package yourownai.controller;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/** A controller owns a group of related routes. */
public interface Controller {
    /**
     * @return true if this controller handled the request, false if the route is not theirs.
     */
    boolean handle(HttpExchange ex, String method, String path, String body) throws IOException;
}
