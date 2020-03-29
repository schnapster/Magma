package space.npstr.magma.impl.connections.hax;

import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.adapter.ReactorNettyWebSocketSession;
import reactor.netty.http.websocket.WebsocketInbound;
import reactor.netty.http.websocket.WebsocketOutbound;

/**
 * Allow our {@link space.npstr.magma.impl.connections.AudioWebSocketSessionHandler} us to get its hands on the close code.
 */
public class ClosingReactorNettyWebSocketSession extends ReactorNettyWebSocketSession {

	public ClosingReactorNettyWebSocketSession(
			WebsocketInbound inbound,
			WebsocketOutbound outbound,
			HandshakeInfo info,
			NettyDataBufferFactory bufferFactory
	) {
		super(inbound, outbound, info, bufferFactory);
	}

	@Override
	public WebSocketConnection getDelegate() {
		return super.getDelegate();
	}
}
