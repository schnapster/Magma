package space.npstr.magma.impl.connections.hax;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.websocket.WebsocketInbound;

/**
 * Allow our {@link space.npstr.magma.impl.connections.AudioWebSocketSessionHandler} us to get its hands on the close code.
 *
 * Plugs in our custom {@link ClosingReactorNettyWebSocketSession}.
 *
 * Rest of the file is copied from the superclass(es) that is necessary to make that work.
 */
public class ClosingReactorNettyWebSocketClient extends ReactorNettyWebSocketClient implements ClosingWebSocketClient {

	private static final Logger logger = LoggerFactory.getLogger(ClosingReactorNettyWebSocketClient.class);

	@Override
	public Mono<Void> execute(URI url, HttpHeaders requestHeaders, WebSocketHandler handler) {
		return getHttpClient()
				.headers(nettyHeaders -> setNettyHeaders(requestHeaders, nettyHeaders))
				.websocket(StringUtils.collectionToCommaDelimitedString(handler.getSubProtocols()))
				.uri(url.toString())
				.handle((inbound, outbound) -> {
					HttpHeaders responseHeaders = toHttpHeaders(inbound);
					String protocol = responseHeaders.getFirst("Sec-WebSocket-Protocol");
					HandshakeInfo info = new HandshakeInfo(url, responseHeaders, Mono.empty(), protocol);
					NettyDataBufferFactory factory = new NettyDataBufferFactory(outbound.alloc());

					// * * * * *
					// plug in our custom websocket session

					WebSocketSession session = new ClosingReactorNettyWebSocketSession(inbound, outbound, info, factory);

					// * * * * *
					if (logger.isDebugEnabled()) {
						logger.debug("Started session '" + session.getId() + "' for " + url);
					}
					return handler.handle(session);
				})
				.doOnRequest(n -> {
					if (logger.isDebugEnabled()) {
						logger.debug("Connecting to " + url);
					}
				})
				.next();
	}

	private void setNettyHeaders(HttpHeaders httpHeaders, io.netty.handler.codec.http.HttpHeaders nettyHeaders) {
		httpHeaders.forEach(nettyHeaders::set);
	}

	private HttpHeaders toHttpHeaders(WebsocketInbound inbound) {
		HttpHeaders headers = new HttpHeaders();
		io.netty.handler.codec.http.HttpHeaders nettyHeaders = inbound.headers();
		nettyHeaders.forEach(entry -> {
			String name = entry.getKey();
			headers.put(name, nettyHeaders.getAll(name));
		});
		return headers;
	}
}
