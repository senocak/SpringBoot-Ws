package com.github.senocak.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.senocak.chat.dto.WebsocketIdentifier;
import com.github.senocak.chat.dto.WsRequestBody;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebsocketController extends AbstractWebSocketHandler {
    private static final Map<String, WebsocketIdentifier> userSessionCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(final WebSocketSession session) {
        try {
            if (session.getUri() == null) {
                log.error("Unable to retrieve the websocket session; serious error!");
                return;
            }

            final String uri = session.getUri().getPath();
            final WebsocketIdentifier websocketIdentifier = getWebsocketIdentifier(uri);

            if (websocketIdentifier.getUser() == null) {
                log.error("Unable to  extract the websocketIdentifier; serious error!");
                return;
            }
            websocketIdentifier.setSession(session);
            userSessionCache.put(websocketIdentifier.getUser(), websocketIdentifier);
            broadCastMessage(websocketIdentifier.getUser(), "login");
            broadCastAllUserList(websocketIdentifier.getUser());
            log.debug("Websocket session established: {}", websocketIdentifier);
        } catch (Throwable ex) {
            log.error("A serious error has occurred with websocket post-connection handling. Exception is: ", ex);
        }
    }

    @Override
    public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status) {
        try {
            if (session.getUri() == null) {
                log.error("Unable to retrieve the websocket session; serious error!");
                return;
            }

            final String uri = session.getUri().getPath();
            final WebsocketIdentifier websocketIdentifier = getWebsocketIdentifier(uri);

            if (websocketIdentifier == null) {
                log.error("Unable to extract websocketIdentifier; serious error!");
                return;
            }
            deleteSession(websocketIdentifier.getUser());
            log.debug("Websocket channel {} has been closed", websocketIdentifier.getSession());
        } catch (Throwable ex) {
            log.error("Error occurred while closing websocket channel:", ex);
        }
    }

    @Override
    public void handleTextMessage(final WebSocketSession session, final TextMessage message) {
        try {
            final URI uri = session.getUri();
            if (uri == null){
                log.error("URI is not found, returning...");
                return;
            }
            final WebsocketIdentifier websocketIdentifier = getWebsocketIdentifier(uri.getPath());

            if (websocketIdentifier == null) {
                log.error("Unable to extract websocketIdentifier; serious error!");
                return;
            }

            try {
                WsRequestBody requestBody = objectMapper.readValue(message.getPayload(), WsRequestBody.class);
                requestBody.setFrom(websocketIdentifier.getUser());
                sendPrivateMessage(requestBody);
                log.debug("Websocket message sent: {}", message.getPayload());
            } catch (Exception ex) {
                log.error("Unable to parse request body; serious error!", ex);
            }
        } catch (Throwable ex) {
            log.error("A serious error has occurred with incoming websocket text message handling. Exception is: ", ex);
        }
    }

    public WebsocketIdentifier getWebsocketIdentifier(final String path) {
        if (path == null || path.isEmpty())
            return null;

        final String[] fields = path.split("/");

        if (fields.length == 0)
            return null;

        WebsocketIdentifier websocketIdentifier = new WebsocketIdentifier();
        try {
            String user = fields[2];
            websocketIdentifier.setUser(user);
        } catch (final IndexOutOfBoundsException e) {
            log.error("Cannot find user or channel id from the path!", e);
        }
        return websocketIdentifier;
    }

    private void broadCastMessage(String message, String type) {
        WsRequestBody wsRequestBody = new WsRequestBody();
        wsRequestBody.setContent(message);
        wsRequestBody.setDate(Instant.now().toEpochMilli());
        wsRequestBody.setType(type);
        for (Map.Entry<String, WebsocketIdentifier> entry : userSessionCache.entrySet()) {
            try {
                entry.getValue().getSession()
                        .sendMessage(new TextMessage(objectMapper.writeValueAsString(wsRequestBody)));
            } catch (Exception e) {
                log.error("Exception while broadcasting:", e);
            }
        }
    }
    
    private void broadCastAllUserList(String user) {
        sendMessage("server", user, "online", String.join(",", userSessionCache.keySet()));
    }

    public WebsocketIdentifier getOrDefault(String key) {
        return userSessionCache.getOrDefault(key, null);
    }

    public void sendMessage(String from, String to, String type, String payload) {
        WebsocketIdentifier userTo = getOrDefault(to);
        if (userTo == null || userTo.getSession() == null) {
            log.error("User or Session not found in cache for user: {}, returning...", to);
            return;
        }
        WsRequestBody requestBody = new WsRequestBody();
        requestBody.setFrom(from);
        requestBody.setTo(to);
        requestBody.setDate(Instant.now().toEpochMilli());
        requestBody.setContent(payload);
        requestBody.setType(type);
        try {
            userTo.getSession().sendMessage(new TextMessage(objectMapper.writeValueAsString(requestBody)));
        } catch (IOException e) {
            log.error("Exception while sending message:", e);
        }
    }

    public void deleteSession(String key) {
        WebsocketIdentifier websocketIdentifier = getOrDefault(key);
        if (websocketIdentifier == null || websocketIdentifier.getSession() == null){
            log.error("Unable to remove the websocket session; serious error!");
            return;
        }
        userSessionCache.remove(key);
        broadCastAllUserList(websocketIdentifier.getUser());
        broadCastMessage(websocketIdentifier.getUser(), "logout");
    }

    public void sendPrivateMessage(WsRequestBody requestBody) {
        WebsocketIdentifier userTo = getOrDefault(requestBody.getTo());
        if (userTo == null || userTo.getSession() == null) {
            log.error("User or Session not found in cache for user: {}, returning...", requestBody.getTo());
            return;
        }
        requestBody.setType("private");
        requestBody.setDate(Instant.now().toEpochMilli());
        try {
            userTo.getSession().sendMessage(new TextMessage(objectMapper.writeValueAsString(requestBody)));
        } catch (IOException e) {
            log.error("Exception while sending message:", e);
        }
    }
}
