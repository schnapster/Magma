package space.npstr.magma.connections.hax;

import io.undertow.connector.ByteBufferPool;
import io.undertow.server.DefaultByteBufferPool;
import io.undertow.websockets.client.WebSocketClient;
import io.undertow.websockets.client.WebSocketClientNegotiation;
import io.undertow.websockets.core.WebSocketChannel;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.adapter.ClosingUndertowWebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.adapter.UndertowWebSocketHandlerAdapter;
import org.springframework.web.reactive.socket.adapter.UndertowWebSocketSession;
import org.springframework.web.reactive.socket.client.UndertowWebSocketClient;
import org.xnio.IoFuture;
import org.xnio.XnioWorker;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Created by napster on 22.04.18.
 * <p>
 * Plugs in our custom {@link ClosingUndertowWebSocketHandlerAdapter}, as well as uses a shared bufferpool to avoid
 * leaks. Leaks happen due to the {@link DefaultByteBufferPool} using a threadlocal variable that holds a reference to
 * an instance of an inner class of the pool preventing it from being GCed. Since pools can be shared, we opt for the
 * easy solution of using a single pool in Magma.
 *
 * Rest of the file is copypasta of the superclass(es) that is necessary to make that work.
 */
public class ClosingUndertowWebSocketClient extends UndertowWebSocketClient implements ClosingWebSocketClient {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ClosingUndertowWebSocketClient.class);

    private final DataBufferFactory bufferFactory = new DefaultDataBufferFactory();

    public ClosingUndertowWebSocketClient(final XnioWorker worker,
                                          final ByteBufferPool bufferPool,
                                          final Consumer<WebSocketClient.ConnectionBuilder> builderConsumer) {
        super(worker, bufferPool, builderConsumer);
    }

    @Override
    public Mono<Void> execute(final URI url, final HttpHeaders headers, final WebSocketHandler handler) {
        return this.executeInternalPatched(url, headers, handler);
    }

    private Mono<Void> executeInternalPatched(final URI url, final HttpHeaders headers, final WebSocketHandler handler) {
        final MonoProcessor<Void> completion = MonoProcessor.create();
        return Mono.fromCallable(
                () -> {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Connecting to " + url);
                    }
                    List<String> protocols = handler.getSubProtocols();
                    WebSocketClient.ConnectionBuilder builder = createConnectionBuilder(url);
                    DefaultNegotiation negotiation = new DefaultNegotiation(protocols, headers, builder);
                    builder.setClientNegotiation(negotiation);
                    return builder.connect().addNotifier(
                            new IoFuture.HandlingNotifier<>() {
                                @Override
                                public void handleDone(final WebSocketChannel channel, final Object attachment) {
                                    handleChannelPatched(url, handler, completion, negotiation, channel);
                                }

                                @Override
                                public void handleFailed(final IOException ex, final Object attachment) {
                                    completion.onError(new IllegalStateException("Failed to connect to " + url, ex));
                                }
                            }, null);
                })
                .then(completion);
    }

    private void handleChannelPatched(final URI url, final WebSocketHandler handler, final MonoProcessor<Void> completion,
                                      final DefaultNegotiation negotiation, final WebSocketChannel channel) {

        final HandshakeInfo info = createHandshakeInfo(url, negotiation);
        final UndertowWebSocketSession session = new UndertowWebSocketSession(channel, info, this.bufferFactory, completion);

        // * * * * *
        // plug in our custom websocket handler adapter

        final UndertowWebSocketHandlerAdapter adapter = new ClosingUndertowWebSocketHandlerAdapter(session);

        // * * * * *

        channel.getReceiveSetter().set(adapter);
        channel.resumeReceives();

        handler.handle(session).subscribe(session);
    }

    private HandshakeInfo createHandshakeInfo(final URI url, final DefaultNegotiation negotiation) {
        final HttpHeaders responseHeaders = negotiation.getResponseHeaders();
        final String protocol = responseHeaders.getFirst("Sec-WebSocket-Protocol");
        return new HandshakeInfo(url, responseHeaders, Mono.empty(), protocol);
    }

    private static final class DefaultNegotiation extends WebSocketClientNegotiation {

        private final HttpHeaders requestHeaders;

        private final HttpHeaders responseHeaders = new HttpHeaders();

        @Nullable
        private final WebSocketClientNegotiation delegate;

        public DefaultNegotiation(final List<String> protocols, final HttpHeaders requestHeaders,
                                  final WebSocketClient.ConnectionBuilder connectionBuilder) {

            super(protocols, Collections.emptyList());
            this.requestHeaders = requestHeaders;
            this.delegate = connectionBuilder.getClientNegotiation();
        }

        public HttpHeaders getResponseHeaders() {
            return this.responseHeaders;
        }

        @Override
        public void beforeRequest(final Map<String, List<String>> headers) {
            this.requestHeaders.forEach(headers::put);
            if (this.delegate != null) {
                this.delegate.beforeRequest(headers);
            }
        }

        @Override
        public void afterRequest(final Map<String, List<String>> headers) {
            headers.forEach(this.responseHeaders::put);
            if (this.delegate != null) {
                this.delegate.afterRequest(headers);
            }
        }
    }
}
