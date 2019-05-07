package org.springframework.web.reactive.socket.adapter;

import io.undertow.websockets.core.BufferedBinaryMessage;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import org.json.JSONObject;
import org.springframework.web.reactive.socket.WebSocketMessage;
import space.npstr.magma.impl.events.audio.ws.OpCode;

import java.nio.charset.StandardCharsets;

/**
 * Created by napster on 22.04.18.
 * <p>
 * Converts close messages into text messages instead of silently swallowing them, allowing our """high level API""" to
 * do lowly things like adjusting behaviour based on the close code received.
 */
public class ClosingUndertowWebSocketHandlerAdapter extends UndertowWebSocketHandlerAdapter {

    private final UndertowWebSocketSession session;

    public ClosingUndertowWebSocketHandlerAdapter(final UndertowWebSocketSession session) {
        super(session);
        this.session = session;
    }

    @Override
    protected void onFullCloseMessage(final WebSocketChannel channel, final BufferedBinaryMessage message) {
        final CloseMessage closeMessage = new CloseMessage(message.getData().getResource());
        this.session.handleMessage(WebSocketMessage.Type.TEXT, this.closedTextMessage(
                new JSONObject()
                        .put("op", OpCode.WEBSOCKET_CLOSE)
                        .put("d", new JSONObject()
                                .put("code", closeMessage.getCode())
                                .put("reason", closeMessage.getReason()))
                        .toString()
        ));
        super.onFullCloseMessage(channel, message);
    }

    private WebSocketMessage closedTextMessage(final String message) {
        final byte[] bytes = (message).getBytes(StandardCharsets.UTF_8);
        return new WebSocketMessage(WebSocketMessage.Type.TEXT, this.session.bufferFactory().wrap(bytes));
    }
}
